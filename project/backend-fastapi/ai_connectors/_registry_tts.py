"""TTS(텍스트-음성 변환) 커넥터 서브레지스트리.

TTS_CONNECTORS dict 와 get_tts_connector 팩토리를 담는다.
강의 음성은 ChapterStudio 가 qwen3-tts-modal 을 직접 지정해 쓴다.
AI_MODEL_TTS 를 비우면 gemini-tts 가 선택된다.
외부 코드는 이 파일을 직접 import 하지 않고 ai_connectors.registry 를 사용한다.
"""
from __future__ import annotations

import logging
import os
from typing import Callable

from .base import TTSConnector
from .errors import ModelNotFoundError
from .tts.gemini_tts_connector import GeminiTTSConnector
from .tts.qwen3_tts_modal_connector import Qwen3TTSModalConnector

_LOG = logging.getLogger(__name__)

# TTS 커넥터 팩토리 맵
TTS_CONNECTORS: dict[str, Callable[[], TTSConnector]] = {
    "gemini-tts": lambda: GeminiTTSConnector(),
    "qwen3-tts-modal": lambda: Qwen3TTSModalConnector(),
}

try:
    from .tts.openai_tts_connector import OpenAITTSConnector

    TTS_CONNECTORS["openai-tts"] = lambda: OpenAITTSConnector()
except ImportError:
    pass


def _wrap_with_openai_failover(connector: TTSConnector) -> TTSConnector:
    """gemini-tts 이고 OpenAI 폴백을 켰으면 폴백 래퍼로 감싼다.

    조건: OPENAI_API_KEY 가 있고 OPENAI_FALLBACK_ENABLED=true(기본 꺼짐).
    openai SDK 가 없으면 원본을 그대로 돌려준다.
    """
    from common.text_config import openai_api_key, openai_fallback_enabled

    if getattr(connector, "name", "") != "gemini-tts":
        return connector
    if not openai_fallback_enabled() or openai_api_key() is None:
        return connector
    try:
        from ._failover import FailoverTTSConnector
        from .tts.openai_tts_connector import OpenAITTSConnector
    except ImportError as exc:
        _LOG.warning(
            "OpenAI TTS 폴백 래핑 건너뜀 (ImportError: %s). `uv add openai` 실행 필요",
            exc,
        )
        return connector
    return FailoverTTSConnector(connector, lambda: OpenAITTSConnector())


def get_tts_connector(model_name: str | None = None) -> TTSConnector:
    """model_name 또는 .env 의 AI_MODEL_TTS 로 TTS 커넥터를 만든다."""
    name = model_name or os.getenv("AI_MODEL_TTS", "gemini-tts")
    if name not in TTS_CONNECTORS:
        raise ModelNotFoundError(
            f"Unknown TTS model: {name}. Registered: {list(TTS_CONNECTORS.keys())}"
        )
    return _wrap_with_openai_failover(TTS_CONNECTORS[name]())
