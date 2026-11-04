"""PaddleOCR PP-OCRv4 Korean OCRConnector 구현 (HTTP 클라이언트).

CNN 기반 OCR 실험용 커넥터다. PaddleOCR-VL(VLM, CER 75.9%)과는 다른 종류의 모델이다.

Nemotron-OCR-v2 커넥터와 같은 구조다.
 - recognize(OCRRequest) -> OCRResponse.
 - 네트워크 호출은 `_paddleocr_ppv4_http_client`, 응답 파싱은 스키마가 같아서
   `_nemotron_response_parser.parse_response` 를 쓴다.
 - 벤더 오류는 `ai_connectors.errors` 예외로 바꾼다.
 - 타임아웃·재시도는 PADDLEOCR_PPV4_TIMEOUT_SEC, PADDLEOCR_PPV4_RETRIES 로 바꾼다.
"""
from __future__ import annotations

import time

from ..errors import AIConnectorError
from ..schemas import OCRRequest, OCRResponse
from ._nemotron_response_parser import parse_response, require_dict
from ._paddleocr_ppv4_http_client import post_recognize

_DEFAULT_MODEL_NAME = "paddleocr-ppv4"


class PaddleOCRPPv4Connector:
    """PaddleOCR PP-OCRv4 Modal HTTP 클라이언트 커넥터.

    가중치는 로컬에 올리지 않는다. 추론은 Modal L4 GPU 에서 하고, 이 클래스는
    `httpx.AsyncClient` 로 POST 만 보낸다.
    """

    name: str = _DEFAULT_MODEL_NAME

    async def recognize(self, req: OCRRequest) -> OCRResponse:
        """OCRRequest 를 받아 Modal 엔드포인트에 POST 하고 OCRResponse 로 돌려준다."""
        t0 = time.perf_counter()
        try:
            raw = await post_recognize(req.image_bytes)
        except AIConnectorError:
            raise
        except Exception as exc:
            raise AIConnectorError(f"PaddleOCR recognize 실패: {exc}") from exc
        latency_ms = (time.perf_counter() - t0) * 1000.0

        normalized = require_dict(raw)
        return parse_response(normalized, latency_ms=latency_ms)

    def supports(self, feature: str) -> bool:
        """지원 기능 플래그."""
        return feature in {
            "korean",
            "english",
            "bbox",
            "modal",
            "http",
            "classical_ocr",
        }
