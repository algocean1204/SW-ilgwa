"""ASR(자동 음성 인식) 커넥터 서브레지스트리.

ASR_CONNECTORS dict 와 get_asr_connector 팩토리를 담는다.
외부 코드는 이 파일을 직접 import 하지 않고 ai_connectors.registry 를 사용한다.
"""
from __future__ import annotations

import logging
from typing import Callable

from .asr.gemini_asr_connector import GeminiASRConnector
from .asr.qwen3_asr_modal_connector import Qwen3ASRModalConnector
from .asr.whisper_modal_connector import WhisperModalConnector
from .base import ASRConnector
from .errors import ModelNotFoundError

import os

_LOG = logging.getLogger(__name__)

_DEFAULT_ASR_MODEL = "whisper-modal"

# ASR 커넥터 팩토리 맵. AI_MODEL_ASR 를 비우면 whisper-modal(Modal GPU Whisper) 이 선택된다.
# qwen3-asr-modal 은 아직 스텁이라 기본 경로와 기본 tier 에 넣지 않는다.
ASR_CONNECTORS: dict[str, Callable[[], ASRConnector]] = {
    "whisper-modal": lambda: WhisperModalConnector(),
    "gemini-asr": lambda: GeminiASRConnector(),
    "qwen3-asr-modal": lambda: Qwen3ASRModalConnector(),
}


def gemini_fallback_enabled() -> bool:
    """Whisper 뒤에 Gemini ASR 폴백을 붙일지 여부. ASR_GEMINI_FALLBACK=true 일 때만 켠다(기본 false)."""
    raw = os.getenv("ASR_GEMINI_FALLBACK", "").strip().lower()
    return bool(raw) and raw not in {"false", "0", "no", "off"}


# FallbackASRConnector. ASR_FALLBACK_TIERS 순서로 tier 를 시도한다(기본 whisper-modal).
# ASR_GEMINI_FALLBACK=true 이면 tier 끝에 gemini-asr 를 붙인다.
# langgraph 가 없어도 개별 ASR 커넥터는 등록되게 ImportError를 넘긴다.
try:
    from .asr.fallback import FallbackASRConnector
    from .asr.fallback.config import resolve_tier_models

    def _make_fallback_asr() -> FallbackASRConnector:
        """.env 의 ASR_FALLBACK_TIERS 를 읽고 tier 팩토리를 넣어 만든다.

        tier 커넥터는 호출할 때 만든다(ASR_CONNECTORS[name]()).
        순환 import 를 피하려고 클로저 안에서 ASR_CONNECTORS 를 참조한다.
        """
        tier_names = list(resolve_tier_models())
        if gemini_fallback_enabled() and "gemini-asr" not in tier_names:
            tier_names.append("gemini-asr")
        # 등록된 커넥터만 tier 에 넣는다(의존성이 빠진 환경 대응)
        valid = [n for n in tier_names if n in ASR_CONNECTORS]
        if not valid:
            raise ModelNotFoundError(
                f"asr-fallback 의 tier 후보 중 등록된 커넥터 없음. "
                f"요청: {tier_names}, 등록: {list(ASR_CONNECTORS.keys())}"
            )
        factories = [ASR_CONNECTORS[n] for n in valid]
        return FallbackASRConnector(
            tier_factories=factories,
            tier_model_names=valid,
        )

    ASR_CONNECTORS["asr-fallback"] = _make_fallback_asr
except ImportError as _fallback_import_err:
    _LOG.warning(
        "asr-fallback 커넥터 등록 건너뜀 (ImportError: %s). "
        "`uv pip install langgraph` 실행 필요",
        _fallback_import_err,
    )


def _wrap_with_openai_failover(connector: ASRConnector) -> ASRConnector:
    """gemini-asr 이고 OpenAI 폴백을 켰으면 폴백 래퍼로 감싼다.

    조건: OPENAI_API_KEY 가 있고 OPENAI_FALLBACK_ENABLED=true(기본 꺼짐).
    openai SDK 가 없으면 원본을 그대로 돌려준다.
    """
    from common.text_config import openai_api_key, openai_fallback_enabled

    if getattr(connector, "name", "") != "gemini-asr":
        return connector
    if not openai_fallback_enabled() or openai_api_key() is None:
        return connector
    try:
        from ._failover import FailoverASRConnector
        from .asr.openai_asr_connector import OpenAIASRConnector
    except ImportError as exc:
        _LOG.warning(
            "OpenAI ASR 폴백 래핑 건너뜀 (ImportError: %s). `uv add openai` 실행 필요",
            exc,
        )
        return connector
    return FailoverASRConnector(connector, lambda: OpenAIASRConnector())


def _wrap_with_gemini_failover(connector: ASRConnector) -> ASRConnector:
    """whisper-modal 이고 ASR_GEMINI_FALLBACK=true 이면 Whisper 실패 때 Gemini ASR 로 넘긴다."""
    if getattr(connector, "name", "") != "whisper-modal" or not gemini_fallback_enabled():
        return connector
    from ._failover import FailoverASRConnector

    return FailoverASRConnector(connector, lambda: GeminiASRConnector())


def get_asr_connector(model_name: str | None = None) -> ASRConnector:
    """model_name 또는 .env 의 AI_MODEL_ASR 로 ASR 커넥터를 만든다(기본 whisper-modal)."""
    name = model_name or os.getenv("AI_MODEL_ASR", _DEFAULT_ASR_MODEL)
    if name not in ASR_CONNECTORS:
        raise ModelNotFoundError(
            f"Unknown ASR model: {name}. Registered: {list(ASR_CONNECTORS.keys())}"
        )
    connector = ASR_CONNECTORS[name]()
    return _wrap_with_gemini_failover(_wrap_with_openai_failover(connector))
