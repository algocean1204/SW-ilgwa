"""Qwen3-4B 기반 낭독 텍스트 재작성 커넥터.

TTS 합성 전에 normalized_text 를 소리 내어 읽기 쉬운 문장으로 바꾼다.
로컬 Mac 은 MLX 4bit Qwen3-4B-Instruct-2507 을 쓰고,
Modal 배포에서는 TTS_V2_LLM_PLANNER_MODEL_PATH 로 원본 가중치 모델을 지정한다.
"""
from __future__ import annotations

import logging
import threading

_LOG = logging.getLogger(__name__)

# 출력 토큰 상한은 입력 글자 수 × 2 + 64, 최대 1024 다.
# 재작성 결과는 원문과 길이가 비슷해 이 정도면 충분하다.
_MAX_TOKENS_RATIO = 2
_MAX_TOKENS_BUFFER = 64
_MAX_TOKENS_CEIL = 1024

# 10자 미만은 고칠 게 거의 없어 LLM 을 부르지 않고 원문을 쓴다.
_SKIP_LEN_THRESHOLD = 10

# 낭독 재작성 시스템 프롬프트. 아직 검증 전 초안이다.
_SYSTEM_PROMPT_KO = (
    "당신은 오디오북 낭독자를 위한 텍스트 전처리 전문가입니다.\n"
    "주어진 텍스트를 낭독하기 쉬운 형태로 변환해주세요.\n\n"
    "규칙:\n"
    "1. 원문의 의미를 절대 변경하지 마세요.\n"
    "2. 쉼표와 마침표를 자연스러운 호흡 위치에 넣어주세요.\n"
    "3. 수식, 기호, 약어는 말로 풀어서 표현해주세요 "
    "(예: \"3x + 2\" → \"삼 엑스 더하기 이\").\n"
    "4. 60자 이상의 긴 문장은 의미 단위로 나눠주세요.\n"
    "5. 원문의 톤(존댓말/반말)을 그대로 유지하세요.\n"
    "6. 결과만 출력하세요. 설명이나 주석은 붙이지 마세요."
)


def _build_messages(text: str, language: str) -> list[dict[str, str]]:
    """chat template 에 넣을 메시지 배열을 만든다.

    지원 언어가 한국어뿐이라 language 와 상관없이 한국어 프롬프트를 쓴다.
    """
    _ = language  # 다국어 분기를 넣을 자리
    return [
        {"role": "system", "content": _SYSTEM_PROMPT_KO},
        {"role": "user", "content": text},
    ]


def _resolve_max_tokens(text: str) -> int:
    """입력 길이로 최대 생성 토큰 수를 정한다. 상한은 1024 토큰이다."""
    estimated = len(text) * _MAX_TOKENS_RATIO + _MAX_TOKENS_BUFFER
    return min(estimated, _MAX_TOKENS_CEIL)


def _run_generate(model: object, tokenizer: object, prompt_text: str, max_tokens: int) -> str:
    """mlx_lm.generate 블로킹 호출.

    mlx_lm 0.31.3 부터 temp, top_k, top_p 를 generate() 에 바로 넘길 수 없어
    make_sampler 로 만든 sampler 를 넘긴다.
    temperature 0.3 은 원문에 가깝게 쓰되 완전한 greedy 는 피하려는 값이다.
    """
    from mlx_lm import generate  # Metal 초기화를 첫 호출까지 미룬다
    from mlx_lm.generate import make_sampler  # 0.31.3 이상에서 필요

    sampler = make_sampler(temp=0.3, top_k=50, top_p=1.0)
    return generate(
        model,
        tokenizer,
        prompt=prompt_text,
        max_tokens=max_tokens,
        sampler=sampler,
        verbose=False,
    )


class ReadingPlanner:
    """Qwen3-4B-Instruct MLX 낭독 텍스트 재작성기.

    모델은 프로세스당 한 번만 로드하고 threading.Lock 으로 중복 로드를 막는다.
    mlx_lm 이 없거나 로드가 실패하면 원문을 그대로 돌려준다.
    """

    _model: object = None
    _tokenizer: object = None
    _load_lock: threading.Lock | None = None
    _unavailable: bool = False  # 한 번 실패하면 재시도하지 않는다

    @classmethod
    def load_model(cls) -> None:
        """Qwen3-4B MLX 모델을 한 번만 로드한다. 이미 로드됐거나 사용 불가면 바로 반환한다."""
        if cls._model is not None or cls._unavailable:
            return
        if cls._load_lock is None:
            cls._load_lock = threading.Lock()
        with cls._load_lock:
            # 락을 기다리는 동안 다른 스레드가 먼저 로드했을 수 있다
            if cls._model is not None or cls._unavailable:
                return
            cls._do_load()

    @classmethod
    def _do_load(cls) -> None:
        """mlx_lm.load 를 호출한다. load_model 의 락 안에서만 부른다."""
        from app.modules.TTS_V2.pipeline.config import TTS_V2_LLM_PLANNER_MODEL_PATH

        model_id = TTS_V2_LLM_PLANNER_MODEL_PATH
        try:
            from mlx_lm import load as mlx_load

            model, tokenizer = mlx_load(model_id)
            cls._model = model
            cls._tokenizer = tokenizer
            _LOG.info("ReadingPlanner 모델 로드 완료: %s", model_id)
        except ImportError:
            _LOG.warning("mlx_lm 미설치 — ReadingPlanner identity fallback 사용")
            cls._unavailable = True
        except Exception as exc:
            _LOG.warning("ReadingPlanner 모델 로드 실패 (%s) — identity fallback: %s", model_id, exc)
            cls._unavailable = True

    @classmethod
    def unload_model(cls) -> None:
        """로드한 모델과 토크나이저를 비운다."""
        cls._model = None
        cls._tokenizer = None
        _LOG.info("ReadingPlanner 모델 언로드 완료")

    @classmethod
    def is_loaded(cls) -> bool:
        """모델이 메모리에 로드된 상태인지 반환한다."""
        return cls._model is not None and cls._tokenizer is not None

    @classmethod
    def rewrite_for_reading(cls, text: str, language: str = "ko") -> str:
        """텍스트를 낭독하기 좋게 재작성한다. 규칙은 _SYSTEM_PROMPT_KO 에 있다.

        10자 미만이거나 모델이 없거나 추론이 실패하면 원문을 그대로 돌려준다.
        """
        if len(text) < _SKIP_LEN_THRESHOLD:
            return text

        cls.load_model()

        if cls._unavailable or cls._model is None or cls._tokenizer is None:
            return text

        return cls._call_model(text, language)

    @classmethod
    def _call_model(cls, text: str, language: str) -> str:
        """모델을 호출한다. 실패하면 원문을 돌려준다."""
        try:
            messages = _build_messages(text, language)
            # mlx_lm.generate() 의 prompt= 는 문자열이라 tokenize=False 로 받는다
            prompt_text = cls._tokenizer.apply_chat_template(
                messages,
                add_generation_prompt=True,
                tokenize=False,
            )
            max_tokens = _resolve_max_tokens(text)
            return _run_generate(cls._model, cls._tokenizer, prompt_text, max_tokens)
        except Exception as exc:
            _LOG.warning("ReadingPlanner 추론 실패 — identity fallback: %s", exc)
            return text
