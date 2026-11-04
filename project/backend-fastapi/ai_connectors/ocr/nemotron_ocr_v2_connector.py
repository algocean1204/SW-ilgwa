"""NVIDIA Nemotron-OCR-v2 OCRConnector 구현 (HTTP 클라이언트 형태).

PaddleOCR-VL 이 페이지 CER 75.9% 로 쓸 수 없어서 대신 실험한 OCR 모델이다.
로컬 Mac 은 CUDA 가 없어 Modal GPU 엔드포인트를 HTTP 로 부른다.
글자만 뽑고 후처리(Kanana-2)는 하지 않으며, 정확도는 뒤 LLM 단계에 맡긴다.

 - recognize(OCRRequest) -> OCRResponse.
 - 네트워크 호출은 `_nemotron_http_client`, 응답 파싱은 `_nemotron_response_parser` 에 있다.
 - 벤더 오류는 `ai_connectors.errors` 예외로 바꾼다.
 - 타임아웃·재시도는 NEMOTRON_OCR_TIMEOUT_SEC, NEMOTRON_OCR_RETRIES 로 바꾼다.
"""
from __future__ import annotations

import time

from ..errors import AIConnectorError
from ..schemas import OCRRequest, OCRResponse
from ._nemotron_http_client import post_recognize
from ._nemotron_response_parser import parse_response, require_dict

# 기본 버전 식별자. 실제 model_version 은 벤더 응답의 GPU 등급까지 넣어 정한다.
_DEFAULT_MODEL_NAME = "nemotron-ocr-v2"


class NemotronOCRv2Connector:
    """Nemotron-OCR-v2 Modal HTTP 클라이언트 커넥터.

    가중치는 로컬에 올리지 않는다. 추론은 Modal 엔드포인트에서 하고, 이 클래스는
    `httpx.AsyncClient` 로 POST 만 보낸다. 상태가 없어 다시 만들어도 비용이 거의 없다.
    """

    name: str = _DEFAULT_MODEL_NAME

    async def recognize(self, req: OCRRequest) -> OCRResponse:
        """OCRRequest 를 받아 Modal 엔드포인트에 POST 하고 OCRResponse 로 돌려준다.

        latency_ms 는 네트워크와 벤더 추론을 합친 전체 왕복 시간이다.
        벤더가 latency 를 따로 줘도 비용 추정에는 호출자 기준 시간이 맞아서 이 값을 쓴다.
        """
        t0 = time.perf_counter()
        try:
            raw = await post_recognize(req.image_bytes)
        except AIConnectorError:
            # 이미 공통 예외로 바뀐 오류는 그대로 올린다.
            raise
        except Exception as exc:
            # 그 밖의 예외만 AIConnectorError 로 감싼다.
            raise AIConnectorError(f"Nemotron-OCR recognize 실패: {exc}") from exc
        latency_ms = (time.perf_counter() - t0) * 1000.0

        normalized = require_dict(raw)
        return parse_response(normalized, latency_ms=latency_ms)

    def supports(self, feature: str) -> bool:
        """지원 기능 플래그를 반환한다.

        글자 추출 전용 모델이라 layout_analysis / latex_preserve 는 지원하지 않는다.
        """
        return feature in {
            "korean",
            "english",
            "multilingual",
            "bbox",
            "modal",
            "http",
        }
