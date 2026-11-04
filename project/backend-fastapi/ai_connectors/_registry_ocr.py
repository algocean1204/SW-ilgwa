"""OCR(광학 문자 인식) 커넥터 서브레지스트리.

OCR_CONNECTORS dict, 별칭 해석(_resolve_ocr_name), get_ocr_connector 팩토리를 담는다.
외부 코드는 이 파일을 직접 import 하지 않고 ai_connectors.registry 를 사용한다.
교재 OCR 본 경로(Marker + MinerU)는 app/modules/OCR_v1 에 있고, 여기 커넥터는 쓰지 않는다.
"""
from __future__ import annotations

import logging
import os
from typing import Callable

from .base import OCRConnector
from .errors import ModelNotFoundError

_LOG = logging.getLogger(__name__)

# OCR 커넥터 팩토리 맵
OCR_CONNECTORS: dict[str, Callable[[], OCRConnector]] = {}

try:
    from .ocr.gemini_ocr_connector import GeminiOCRConnector
    OCR_CONNECTORS["gemini-ocr"] = lambda: GeminiOCRConnector()
except ImportError as _gemini_ocr_import_err:
    _LOG.warning(
        "Gemini OCR 커넥터 등록 건너뜀 (ImportError: %s). "
        "`uv pip install google-genai` 실행 필요",
        _gemini_ocr_import_err,
    )

# Nemotron-OCR-v2 Modal HTTP 커넥터. httpx 만 쓰므로 기본 venv 에서도 import 된다.
try:
    from .ocr.nemotron_ocr_v2_connector import NemotronOCRv2Connector
    OCR_CONNECTORS["nemotron-ocr-v2"] = lambda: NemotronOCRv2Connector()
except ImportError as _nemotron_import_err:
    _LOG.warning(
        "Nemotron-OCR-v2 커넥터 등록 건너뜀 (ImportError: %s). "
        "`httpx` 패키지 설치 여부를 확인하세요.",
        _nemotron_import_err,
    )

# PaddleOCR PP-OCRv4 Korean Modal HTTP 커넥터(실험용).
# CNN det+rec 모델이며 PaddleOCR-VL(0.9B VLM)과는 다른 모델이다.
try:
    from .ocr.paddleocr_ppv4_connector import PaddleOCRPPv4Connector
    OCR_CONNECTORS["paddleocr-ppv4"] = lambda: PaddleOCRPPv4Connector()
except ImportError as _ppv4_import_err:
    _LOG.warning(
        "PaddleOCR PP-OCRv4 커넥터 등록 건너뜀 (ImportError: %s). "
        "`httpx` 패키지 설치 여부를 확인하세요.",
        _ppv4_import_err,
    )

# OCR 커넥터 짧은 별칭. AI_OCR 환경변수와 짧은 키를 모두 받는다.
# paddleocr-vl-mlx 를 지운 뒤로 예전 paddleocr_vl 축약어는 gemini-ocr 로 보낸다.
_OCR_ALIASES: dict[str, str] = {
    "nemotron_v2": "nemotron-ocr-v2",
    "nemotron-v2": "nemotron-ocr-v2",
    "paddleocr_vl": "gemini-ocr",
    "paddleocr-vl": "gemini-ocr",
    # PP-OCRv4 축약어
    "paddleocr_ppv4": "paddleocr-ppv4",
    "paddleocr-pp-v4": "paddleocr-ppv4",
    "pp-ocrv4": "paddleocr-ppv4",
    "gemini_ocr": "gemini-ocr",
}


def _resolve_ocr_name(model_name: str | None) -> str:
    """model_name > AI_MODEL_OCR > AI_OCR(축약) > 기본값 순서로 이름을 정한다.
    """
    if model_name:
        return _OCR_ALIASES.get(model_name, model_name)
    primary = os.getenv("AI_MODEL_OCR", "").strip()
    if primary:
        return _OCR_ALIASES.get(primary, primary)
    shorthand = os.getenv("AI_OCR", "").strip()
    if shorthand:
        return _OCR_ALIASES.get(shorthand, shorthand)
    # 기본값
    return "gemini-ocr"


def _wrap_with_openai_failover(connector: OCRConnector) -> OCRConnector:
    """gemini-ocr 이고 OpenAI 폴백을 켰으면 폴백 래퍼로 감싼다.

    조건: OPENAI_API_KEY 가 있고 OPENAI_FALLBACK_ENABLED=true(기본 꺼짐).
    openai SDK 가 없으면 원본을 그대로 돌려준다.
    """
    from common.text_config import openai_api_key, openai_fallback_enabled

    if getattr(connector, "name", "") != "gemini-ocr":
        return connector
    if not openai_fallback_enabled() or openai_api_key() is None:
        return connector
    try:
        from ._failover import FailoverOCRConnector
        from .ocr.openai_ocr_connector import OpenAIOCRConnector
    except ImportError as exc:
        _LOG.warning(
            "OpenAI OCR 폴백 래핑 건너뜀 (ImportError: %s). `uv add openai` 실행 필요",
            exc,
        )
        return connector
    return FailoverOCRConnector(connector, lambda: OpenAIOCRConnector())


def get_ocr_connector(model_name: str | None = None) -> OCRConnector:
    """model_name 또는 .env 의 AI_MODEL_OCR(AI_OCR)로 OCR 커넥터를 만든다."""
    name = _resolve_ocr_name(model_name)
    if name not in OCR_CONNECTORS:
        raise ModelNotFoundError(
            f"Unknown OCR model: {name}. "
            f"Registered: {list(OCR_CONNECTORS.keys())}. "
            f"AI_MODEL_OCR(또는 AI_OCR) 환경변수와 venv 활성화 여부를 확인하세요.",
        )
    return _wrap_with_openai_failover(OCR_CONNECTORS[name]())
