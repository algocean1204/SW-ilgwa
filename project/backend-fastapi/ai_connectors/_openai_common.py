"""OpenAI 커넥터 공통 헬퍼.

openai SDK 호출에 필요한 인증, 지연 import, 클라이언트 생성을 모았다.
OpenAI 폴백은 OPENAI_FALLBACK_ENABLED=true 로 명시할 때만 켜진다.
커넥터를 만들 때는 API 키를 읽지 않아서, 키가 없어도 오프라인 테스트와 레지스트리 확인이 된다.

PCM/WAV 변환은 _gemini_common 의 헬퍼를 그대로 쓴다.
"""
from __future__ import annotations

import io
import wave

from ._gemini_common import (  # noqa: F401  커넥터들이 _openai_common 에서 import 한다
    pcm16_duration_sec,
    pcm16_mono_to_wav,
)
from .errors import AuthError, InferenceError, ModelLoadError

import os


def openai_api_key() -> str:
    """OPENAI_API_KEY 를 읽는다. Gemini 와 달리 Google 식 대체 키는 두지 않는다."""
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key:
        raise AuthError("OPENAI_API_KEY 가 설정되지 않았습니다.")
    return key


# OpenAI 모델별 completion 토큰 상한. 폴백 요청에는 Gemini 기준의 큰 max_tokens(예: 32000)가
# 그대로 오는데, gpt-4o 는 상한이 16384 라 `400 invalid_value: max_tokens is too large` 가 난다.
# 그래서 모델 한도로 자른다. gpt-4o/4o-mini 의 16384 는 운영 에러 메시지로 확인했다.
# TODO: gpt-5.x 의 공식 상한이 확인되면 갱신한다. 65536 은 추정치이고,
#       모르는 모델은 _OPENAI_DEFAULT_MAX_COMPLETION 으로 제한된다.
_OPENAI_MAX_COMPLETION_TOKENS = {
    "gpt-4o": 16384,
    "gpt-4o-mini": 16384,
    "gpt-5.5": 65536,
    "gpt-5.4-nano": 65536,
    "gpt-5.4-mini": 65536,
}
_OPENAI_DEFAULT_MAX_COMPLETION = 16384  # 모르는 모델은 16384 로 제한


def clamp_openai_max_tokens(requested: int | None, model: str) -> int:
    """요청 max_tokens 를 모델별 completion 토큰 상한으로 클램프한다.

    Gemini 기준으로 크게 잡힌 값이 OpenAI 모델 한도를 넘지 않게 한다.
    requested 가 None 이거나 0 이하면 모델 상한값을 쓴다.
    """
    cap = _OPENAI_MAX_COMPLETION_TOKENS.get(model, _OPENAI_DEFAULT_MAX_COMPLETION)
    if requested is None or requested <= 0:
        return cap
    return min(requested, cap)


def load_openai_module() -> object:
    """openai SDK 를 호출 시점에 import 한다."""
    try:
        import openai
    except ImportError as exc:
        raise ModelLoadError(
            "openai 패키지가 설치되어 있지 않습니다. "
            "`uv add openai` 실행 필요"
        ) from exc
    return openai


def build_openai_client() -> tuple[object, object]:
    """동기 OpenAI 클라이언트와 openai 모듈 객체를 함께 반환한다.

    OCR·ASR·TTS 커넥터는 동기 SDK 를 asyncio.to_thread 로 감싸 쓰므로 동기 클라이언트를 준다.
    """
    openai = load_openai_module()
    client = openai.OpenAI(api_key=openai_api_key())
    return client, openai


def build_async_openai_client() -> tuple[object, object]:
    """비동기 AsyncOpenAI 클라이언트와 openai 모듈 객체를 함께 반환한다.

    텍스트 커넥터는 ClaudeSonnetConnector 처럼 비동기 클라이언트를 직접 await 한다.
    """
    openai = load_openai_module()
    client = openai.AsyncOpenAI(api_key=openai_api_key())
    return client, openai


def wav_duration_sec(wav_bytes: bytes) -> float:
    """WAV 바이트의 헤더를 읽어 재생 시간을 초 단위로 반환한다.

    OpenAI TTS 는 response_format="wav" 로 WAV 바이트를 돌려준다.
    PCM 길이를 모르니 wave 헤더의 프레임 수와 샘플레이트로 길이를 구한다.
    """
    try:
        with wave.open(io.BytesIO(wav_bytes), "rb") as wf:
            frames = wf.getnframes()
            sample_rate = wf.getframerate()
    except (wave.Error, EOFError) as exc:
        raise InferenceError("OpenAI TTS WAV 헤더를 해석하지 못했습니다.") from exc
    if sample_rate <= 0:
        raise InferenceError("OpenAI TTS WAV 샘플레이트가 올바르지 않습니다.")
    return frames / float(sample_rate)
