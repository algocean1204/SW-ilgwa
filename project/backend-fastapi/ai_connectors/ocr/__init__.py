"""OCR 커넥터 서브 패키지.

런타임(Gemini OCR, Nemotron-OCR-v2 Modal HTTP, PaddleOCR PP-OCRv4 등)마다 파일 하나를 둔다.
등록은 `ai_connectors/_registry_ocr.py` 의 OCR_CONNECTORS 에서 한다.

AI_MODEL_OCR 를 비우면 gemini-ocr 이고, nemotron-ocr-v2 같은 다른 경로를 고를 수 있다.
교재 OCR 본 경로(Marker + MinerU)는 app/modules/OCR_v1 에 있다.
"""

from .nemotron_ocr_v2_connector import NemotronOCRv2Connector

__all__ = [
    "NemotronOCRv2Connector",
]
