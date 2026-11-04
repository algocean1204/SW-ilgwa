"""음성 네이티브(Voice) 커넥터 서브레지스트리.

VOICE_CONNECTORS dict 와 get_voice_connector 팩토리를 담는다.
외부 코드는 이 파일을 직접 import 하지 않고 ai_connectors.registry 를 사용한다.
"""
from __future__ import annotations

import logging
import os
from typing import Callable

from .errors import ModelNotFoundError

_LOG = logging.getLogger(__name__)

# 음성 네이티브 커넥터 팩토리 맵.
# Gemini Flash Live 는 google-genai 가 없으면 등록하지 않는다.
VOICE_CONNECTORS: dict[str, Callable[[], object]] = {}

try:
    from .voice.gemini_flash_live.connector import GeminiFlashLiveConnector
    VOICE_CONNECTORS["gemini-flash-live"] = lambda: GeminiFlashLiveConnector()
except ImportError as _gemini_import_err:
    _LOG.warning(
        "Gemini Flash Live 커넥터 등록 건너뜀 (ImportError: %s). "
        "`uv pip install google-genai` 실행 필요",
        _gemini_import_err,
    )


def get_voice_connector(model_name: str | None = None) -> object:
    """model_name 또는 .env 의 AI_MODEL_VOICE 로 음성 커넥터를 만든다."""
    name = model_name or os.getenv("AI_MODEL_VOICE", "gemini-flash-live")
    if name not in VOICE_CONNECTORS:
        raise ModelNotFoundError(
            f"Unknown voice model: {name}. "
            f"Registered: {list(VOICE_CONNECTORS.keys())}. "
            f"AI_MODEL_VOICE 환경변수와 google-genai 패키지 설치를 확인하세요."
        )
    return VOICE_CONNECTORS[name]()
