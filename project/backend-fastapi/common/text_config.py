"""텍스트 생성 커넥터 공용 설정.

Claude·Gemini·OpenAI 텍스트 커넥터 환경변수를 여기서만 읽는다.
다른 모듈은 os.getenv() 대신 이 함수들을 쓴다.
"""
from __future__ import annotations

import os


def anthropic_api_key() -> str | None:
    """Anthropic API 키를 반환한다. 없으면 None."""
    return os.getenv("ANTHROPIC_API_KEY") or None


def claude_sonnet_api_key() -> str | None:
    """Claude Sonnet 커넥터 API 키를 반환한다.

    CLAUDE_SONNET_API_KEY 가 우선. 없으면 ANTHROPIC_API_KEY 를 공유한다.
    """
    specific = os.getenv("CLAUDE_SONNET_API_KEY", "").strip()
    return specific if specific else anthropic_api_key()


def claude_sonnet_model() -> str:
    """사용할 Claude Sonnet 모델 식별자를 반환한다."""
    return os.getenv("CLAUDE_SONNET_MODEL", "claude-sonnet-4-6")


def claude_sonnet_timeout_sec() -> float:
    """Claude Sonnet 단일 호출 제한 시간(초)를 반환한다."""
    raw = os.getenv("CLAUDE_SONNET_TIMEOUT_SEC", "300").strip()
    try:
        value = float(raw)
    except ValueError as exc:
        raise RuntimeError("CLAUDE_SONNET_TIMEOUT_SEC는 숫자여야 한다.") from exc
    if value < 30 or value > 1200:
        raise RuntimeError("CLAUDE_SONNET_TIMEOUT_SEC는 30~1200초여야 한다.")
    return value


def google_api_key() -> str | None:
    """Google AI Studio API 키를 반환한다. GEMINI_API_KEY를 대체 키로 허용한다."""
    value = os.getenv("GOOGLE_API_KEY", "").strip()
    if value:
        return value
    fallback = os.getenv("GEMINI_API_KEY", "").strip()
    return fallback if fallback else None


def gemini_text_model() -> str:
    """google-genai 텍스트 커넥터가 사용할 Gemini 모델 ID를 반환한다."""
    return os.getenv("GEMINI_TEXT_MODEL", "gemini-3.5-flash").strip() or "gemini-3.5-flash"


# Gemini 3 계열(gemini-3.5-flash 등)은 thinking_budget 없이 thinking_level 만 받는다(공식 문서).
# 답변에 reasoning 텍스트가 섞이지 않게 기본을 LOW로 둔다.
_GEMINI_THINKING_LEVELS: frozenset[str] = frozenset({"MINIMAL", "LOW", "MEDIUM", "HIGH"})


def gemini_thinking_level() -> str:
    """Gemini 텍스트 생성의 thinking_level을 반환한다(기본 LOW).

    reasoning 텍스트가 답변에 나오지 않게 사고량을 낮게 둔다.
    허용값: MINIMAL / LOW / MEDIUM / HIGH. 그 밖의 값은 LOW로 본다.
    """
    raw = os.getenv("GEMINI_THINKING_LEVEL", "LOW").strip().upper()
    return raw if raw in _GEMINI_THINKING_LEVELS else "LOW"


def claude_sonnet_max_concurrency() -> int:
    """Claude Sonnet 배치 호출 병렬 상한을 반환한다."""
    raw = os.getenv("CLAUDE_SONNET_MAX_CONCURRENCY", "4").strip()
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError("CLAUDE_SONNET_MAX_CONCURRENCY는 정수여야 한다.") from exc
    if value < 1 or value > 8:
        raise RuntimeError("CLAUDE_SONNET_MAX_CONCURRENCY는 1~8이어야 한다.")
    return value


def gemini_text_max_concurrency() -> int:
    """전역(채팅·강의) Gemini 텍스트 커넥터의 동시 SDK 호출 상한을 반환한다.

    blocking google-genai 호출을 돌리는 전용 ThreadPoolExecutor 의 워커 수다.
    wait_for 가 타임아웃해도 blocking 호출은 워커를 잡고 있으므로, 동시 SDK 호출은 이 값을 넘지 않는다.

    기본 1. 전역 레인은 GEMINI_REQUEST_INTERVAL_MS 간격으로 사실상 한 번에 하나씩 돌았다.
    0 이하나 파싱 실패는 1, 최대 8이다.
    """
    raw = os.getenv("GEMINI_TEXT_MAX_CONCURRENCY", "1").strip()
    try:
        value = int(raw)
    except ValueError:
        return 1
    return max(1, min(8, value))


# OpenAI 폴백 설정. Gemini 를 명시 선택하고 OPENAI_FALLBACK_ENABLED=true 일 때만 쓴다.


def openai_api_key() -> str | None:
    """OpenAI API 키를 반환한다. 없으면 None.

    GEMINI_API_KEY 처럼 다른 키를 대신 쓰는 경로는 두지 않는다.
    """
    return os.getenv("OPENAI_API_KEY") or None


def openai_text_model() -> str:
    """OpenAI 텍스트 폴백 커넥터가 사용할 모델 ID를 반환한다(기본 gpt-5.4-nano)."""
    return os.getenv("OPENAI_TEXT_MODEL", "gpt-5.4-nano").strip() or "gpt-5.4-nano"


def openai_fallback_enabled() -> bool:
    """OpenAI 폴백 래핑 허용 여부를 반환한다(기본 false).

    기본 폴백 체인은 Modal → Claude 라서 OpenAI 는 OPENAI_FALLBACK_ENABLED=true 로
    명시할 때만 Gemini 커넥터 뒤에 붙는다. OPENAI_API_KEY 도 있어야 한다.
    """
    raw = os.getenv("OPENAI_FALLBACK_ENABLED", "false").strip().lower()
    return raw in {"1", "true", "yes", "on"}


def text_fallback_after_failures() -> int:
    """텍스트 폴백 전환 임계치(연속 실패 횟수)를 반환한다(기본 3)."""
    raw = os.getenv("TEXT_FALLBACK_AFTER_FAILURES", "3").strip()
    try:
        value = int(raw)
    except ValueError as exc:
        raise RuntimeError("TEXT_FALLBACK_AFTER_FAILURES는 정수여야 한다.") from exc
    if value < 1 or value > 10:
        raise RuntimeError("TEXT_FALLBACK_AFTER_FAILURES는 1~10이어야 한다.")
    return value


