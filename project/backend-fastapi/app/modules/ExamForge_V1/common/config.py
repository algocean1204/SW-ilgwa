"""환경 변수 기반 설정을 한 곳에서 관리한다."""
from __future__ import annotations

import os
from functools import cache
from pathlib import Path

from dotenv import load_dotenv


@cache
def _load_env() -> None:
    """환경 파일은 한 번만 로드한다."""
    module_env = Path(__file__).resolve().parents[1] / ".env"
    root_env = Path(__file__).resolve().parents[4] / ".env"
    load_dotenv(root_env)
    load_dotenv(module_env, override=True)


def _optional(key: str) -> str | None:
    """빈 문자열은 미설정으로 취급한다."""
    _load_env()
    value = os.environ.get(key)
    if value is None or value == "":
        return None
    return value


def _required(key: str) -> str:
    """필수 환경값이 없으면 즉시 실패한다."""
    value = _optional(key)
    if value is None:
        raise RuntimeError(f"{key}가 설정되지 않았다.")
    return value


def _int_env(key: str, default: int) -> int:
    """정수 환경 변수를 파싱한다. 정수가 아니면 기본값을 쓴다."""
    raw = _optional(key)
    if raw is None:
        return default
    try:
        return int(raw)
    except ValueError:
        import logging
        logging.getLogger(__name__).warning(
            "%s='%s'는 유효한 정수가 아님 — 기본값 %d 사용", key, raw, default,
        )
        return default


def _bool_env(key: str, default: bool) -> bool:
    """불리언 환경 변수를 여러 표기 방식으로 읽는다."""
    raw = _optional(key)
    if raw is None:
        return default
    return raw.strip().lower() not in {"false", "0", "no", "off"}


def anthropic_api_key() -> str | None:
    return _optional("ANTHROPIC_API_KEY")


def claude_sonnet_api_key() -> str | None:
    """Claude Sonnet 커넥터 키. CLAUDE_SONNET_API_KEY가 없으면 ANTHROPIC_API_KEY를 쓴다.

    루트 common/text_config.py와 같은 규칙이다.
    """
    return _optional("CLAUDE_SONNET_API_KEY") or anthropic_api_key()


def google_api_key() -> str | None:
    """Google AI Studio API 키. GEMINI_API_KEY도 받는다."""
    return _optional("GOOGLE_API_KEY") or _optional("GEMINI_API_KEY")


def gemini_text_model() -> str:
    """google-genai 텍스트 커넥터의 모델 ID."""
    return _optional("GEMINI_TEXT_MODEL") or "gemini-3.5-flash"


def examforge_gemini_concurrency() -> int:
    """ExamForge 전용 Gemini 동시 호출 상한(기본 3, EXAMFORGE_GEMINI_CONCURRENCY).

    전역 GEMINI_TEXT_MAX_CONCURRENCY(=1)는 채팅·강의용이다. 모의고사는 독립 호출이
    수십 개라 전역값을 쓰면 타임아웃이 나서 따로 둔다. 0 이하는 교착을 막으려고 1로 올린다.
    """
    return max(1, _int_env("EXAMFORGE_GEMINI_CONCURRENCY", 3))


def openai_api_key() -> str | None:
    """OpenAI API 키. 없으면 None이다."""
    return _optional("OPENAI_API_KEY")


def openai_text_model() -> str:
    """OpenAI 텍스트 커넥터의 모델 ID(기본 gpt-5.4-nano)."""
    return _optional("OPENAI_TEXT_MODEL") or "gpt-5.4-nano"


def openai_fallback_enabled() -> bool:
    """OpenAI 폴백 허용 여부. OPENAI_FALLBACK_ENABLED=true일 때만 켠다(기본 false)."""
    return _bool_env("OPENAI_FALLBACK_ENABLED", False)


def examforge_gemini_fallback_enabled() -> bool:
    """claude_sonnet 뒤에 Gemini 폴백을 붙일지 여부(기본 false).

    EXAMFORGE_GEMINI_FALLBACK=true이고 Google 키가 있을 때만 Sonnet → Gemini로 묶는다.
    """
    return _bool_env("EXAMFORGE_GEMINI_FALLBACK", False)


def active_text_model() -> str:
    """ExamForge 문제 생성 커넥터 이름. ChapterStudio의 LECTURE_TEXT_MODEL과 따로 둔다.

    기본은 qwen27b_modal(Modal Qwen3.6-27B)이고 실패하면 claude_sonnet으로 넘어간다.
    """
    return _optional("EXAMFORGE_ACTIVE_TEXT_MODEL") or "qwen27b_modal"


def active_planner_model() -> str:
    """출제 계획 커넥터 이름. 미설정이면 문제 생성 커넥터를 따른다."""
    return _optional("EXAMFORGE_ACTIVE_PLANNER_MODEL") or active_text_model()


def active_verifier_model() -> str:
    """교차 검증·교정 커넥터 이름. 기본은 claude_sonnet이다."""
    return _optional("EXAMFORGE_ACTIVE_VERIFIER_MODEL") or "claude_sonnet"


def active_grading_model() -> str:
    """모의고사 채점·총평 커넥터 이름(기본 claude_sonnet, ACTIVE_GRADING_MODEL로 교체).

    채점 품질을 한 모델로 맞추려고 문제 생성 경로와 따로 둔다.
    """
    return _optional("ACTIVE_GRADING_MODEL") or "claude_sonnet"


# <think> 추론을 본문 토큰으로 쓰는 모델 이름 패턴. max_tokens 여유가 더 필요하다.
# claude 계열은 본문에 추론을 내지 않아 넣지 않는다.
_REASONING_MODEL_HINTS: tuple[str, ...] = ("qwen", "glm", "deepseek", "r1")


def is_reasoning_model(model_name: str) -> bool:
    """모델 이름이 reasoning(<think> 소비) 계열인지 휴리스틱으로 판별한다."""
    lowered = model_name.lower()
    return any(hint in lowered for hint in _REASONING_MODEL_HINTS)


def distractor_rewrite_enabled() -> bool:
    """오답 재작성 노드를 켤지 정한다(기본 true).

    모델 종류에 따라 조용히 켜지고 꺼지지 않게 명시 플래그로 정한다.
    코드가 많은 보기로 JSON이 자주 깨지면 EXAMFORGE_DISTRACTOR_REWRITE=false로
    끄고 처음 생성한 보기를 그대로 쓴다.
    """
    raw = _optional("EXAMFORGE_DISTRACTOR_REWRITE")
    if raw is None:
        return True
    return raw.strip().lower() not in {"false", "0", "no", "off"}


def targeted_repair_enabled() -> bool:
    """검증 실패 문항만 고치는 표적 교정을 켤지 정한다(기본 true).

    EXAMFORGE_TARGETED_REPAIR=false면 재시도가 바로 blind 재생성으로 간다.
    """
    raw = _optional("EXAMFORGE_TARGETED_REPAIR")
    if raw is None:
        return True
    return raw.strip().lower() not in {"false", "0", "no", "off"}


def verification_advisory_enabled() -> bool:
    """검증 결과를 관측용(advisory)으로만 쓸지 정한다.

    신뢰가 낮은 검증기용이며 EXAMFORGE_VERIFICATION_ADVISORY=true일 때만 켠다.
    """
    return _bool_env("EXAMFORGE_VERIFICATION_ADVISORY", False)


def verifier_max_tokens(base: int) -> int:
    """검증·교정 호출의 max_tokens를 검증 모델에 맞춘다.

    추론 모델이면 <think> 토큰 몫으로 base의 2배(최대 8000)를 주고,
    claude 같은 비추론 모델은 base를 그대로 써서 토큰 비용이 늘지 않게 한다.
    """
    if is_reasoning_model(active_verifier_model()):
        return min(base * 2, 8000)
    return base


def max_retries() -> int:
    """교차 검증 실패 문항의 최대 재시도 횟수(기본 3).

    재시도는 실패 문항만 교정하고, 교정이 안 되면 그 문항만 다시 생성한다.
    횟수를 다 쓰면 확보한 문항으로 passed/passed_partial 출고한다.
    MOCK_EXAM_MAX_RETRIES가 MAX_RETRIES보다 우선한다. 음수는 0으로 맞춘다.
    """
    explicit = _optional("MOCK_EXAM_MAX_RETRIES")
    if explicit is not None:
        return max(0, _int_env("MOCK_EXAM_MAX_RETRIES", 3))
    return max(0, _int_env("MAX_RETRIES", 3))


def mock_exam_max_llm_calls() -> int:
    """시험 1회당 LLM 호출 하드 상한의 하한값(기본 60, MOCK_EXAM_MAX_LLM_CALLS).

    LLMBudgetCounter는 이 값과 "문항 비례 예산 × 1.25" 중 큰 값을 상한으로 쓴다.
    그래서 문항이 많아도 예산보다 낮게 깎이지 않고, 재시도 폭주만 막는다.
    0 이하는 1로 맞춘다.
    """
    return max(1, _int_env("MOCK_EXAM_MAX_LLM_CALLS", 60))


def generation_concurrency() -> int:
    """문제 생성 병렬 상한(기본 4, GENERATION_CONCURRENCY). 0 이하는 1로 올린다."""
    return max(1, _int_env("GENERATION_CONCURRENCY", 4))


def verification_concurrency() -> int:
    """정답 검증 병렬 상한(기본 3, VERIFICATION_CONCURRENCY). 0 이하는 1로 올린다."""
    return max(1, _int_env("VERIFICATION_CONCURRENCY", 3))


def pipeline_llm_budget() -> int:
    """파이프라인 1회당 LLM 호출 예산 고정값(기본 80).

    문항 수를 모르는 호출부(테스트 더블 등)용이다.
    라우터는 pipeline_llm_budget_for(total_questions)로 문항 수 비례 예산을 쓴다.
    """
    return _int_env("PIPELINE_LLM_BUDGET", 80)


def pipeline_llm_budget_for(total_questions: int) -> int:
    """문항 수에 비례한 LLM 호출 예산(문항당 8회 + 고정 24회).

    문항당 1회 통과는 생성·오답·해설·검증 4회이고, 나머지는 재시도 몫이다.
    PIPELINE_LLM_BUDGET를 주면 그 값을 하한으로 쓴다.

    예: 5문항 → 8*5+24 = 64, 10문항 → 8*10+24 = 104.
    """
    n = max(1, total_questions)
    scaled = n * _int_env("PIPELINE_LLM_BUDGET_PER_QUESTION", 8) + _int_env(
        "PIPELINE_LLM_BUDGET_OVERHEAD", 24
    )
    # 고정값을 주면 둘 중 큰 값을 쓴다.
    explicit = _optional("PIPELINE_LLM_BUDGET")
    if explicit is not None:
        return max(scaled, _int_env("PIPELINE_LLM_BUDGET", 80))
    return scaled


def pipeline_llm_reserve_for(total_questions: int) -> int:
    """해설 생성 전용으로 떼어 둘 호출 수(문항당 2회, EXAMFORGE_ANSWER_RESERVE_PER_QUESTION).

    출고 문항마다 해설이 있어야 해서 문항 수만큼에 파싱 재시도 몫을 더한다.
    """
    n = max(1, total_questions)
    return n * _int_env("EXAMFORGE_ANSWER_RESERVE_PER_QUESTION", 2)


def pipeline_timeout_sec() -> int:
    """모의고사 생성 API 전체 제한 시간(기본 750초, MOCK_EXAM_PIPELINE_TIMEOUT_SEC).

    초기 생성 약 5분, 누락 보충 약 5분, 여유 약 2.5분으로 잡았다. 최소 60초다.
    """
    return max(60, _int_env("MOCK_EXAM_PIPELINE_TIMEOUT_SEC", 750))


def missing_retry_cap() -> int:
    """문항 수 부족(missing_count > 0)일 때의 보충 재시도 상한(기본 3).

    다 쓰면 확보한 유효 문항만으로 passed 출고한다. JSON이 잘려 문항이 빠지는
    경우 1회로는 부족해(실측 3/10 통과) 3회로 둔다. EXAMFORGE_MISSING_RETRY_CAP로 바꾼다.
    """
    return max(0, _int_env("EXAMFORGE_MISSING_RETRY_CAP", 3))


def examforge_answer_key_secret() -> str:
    """정답 키 위변조를 막는 HMAC secret(32자 이상)."""
    secret = _required("EXAMFORGE_ANSWER_KEY_SECRET")
    if len(secret) < 32:
        raise RuntimeError("EXAMFORGE_ANSWER_KEY_SECRET는 32자 이상이어야 한다.")
    return secret


def seal_enforce_mode() -> bool:
    """정답 키 seal 불일치 시 403을 낼지, 경고만 남기고 계속할지 정한다(기본 경고).

    서비스 간 canonical 재현이 자주 어긋나 403→502로 채점이 막혀서 경고를 기본으로 둔다.
    내부 X-API-Key 경로라 외부 변조 위험은 낮다. EXAMFORGE_SEAL_ENFORCE=true면 403을 낸다.
    """
    return _bool_env("EXAMFORGE_SEAL_ENFORCE", default=False)


def exam_forge_port() -> int:
    """샌드박스 서버 포트(기본 8900)."""
    return _int_env("MOCK_EXAM_PORT", 8900)


def log_level() -> str:
    return _optional("LOG_LEVEL") or "INFO"
