"""Nemotron-OCR-v2 응답 파서.

Modal 엔드포인트가 돌려준 JSON dict 를 OCRResponse 로 바꾼다.
파싱에 실패하면 InferenceError 를 낸다.
"""
from __future__ import annotations

from typing import Any

from ..errors import InferenceError
from ..schemas import OCRDetection, OCRResponse


def parse_response(
    raw: dict[str, Any],
    latency_ms: float,
) -> OCRResponse:
    """vendor JSON dict 를 OCRResponse 로 변환한다.

    기대 스키마:
      {
        "text": str,
        "detections": [{"bbox": [x1,y1,x2,y2], "text": str, "confidence": float}],
        "latency_ms": float,
        "model_version": str,
        "gpu_kind": str | None,
      }
    벤더가 latency_ms 를 줘도 네트워크를 포함한 호출자 기준 지연인
    `latency_ms` 인자로 덮어쓴다.
    """
    detections = _parse_detections(raw.get("detections"))
    text = _resolve_text(raw, detections)
    model_version = _resolve_model_version(raw)
    return OCRResponse(
        detections=detections,
        text=text,
        model_version=model_version,
        latency_ms=latency_ms,
        page_count=1,
    )


def _parse_detections(raw_detections: Any) -> list[OCRDetection]:
    """detections 리스트를 OCRDetection 으로 변환한다.

    모델이 detection 을 반환하지 않거나 비어 있으면 빈 리스트를 돌려준다.
    각 항목이 dict 가 아니거나 bbox 길이가 4 가 아니면 건너뛴다.
    """
    if not isinstance(raw_detections, list):
        return []
    result: list[OCRDetection] = []
    for item in raw_detections:
        if not isinstance(item, dict):
            continue
        bbox = item.get("bbox")
        if not isinstance(bbox, list) or len(bbox) != 4:
            continue
        try:
            bbox_int = [int(v) for v in bbox]
        except (TypeError, ValueError):
            continue
        text = _coerce_str(item.get("text", ""))
        confidence = _coerce_confidence(item.get("confidence", 0.0))
        result.append(
            OCRDetection(text=text, bbox=bbox_int, confidence=confidence),
        )
    return result


def _resolve_text(raw: dict[str, Any], detections: list[OCRDetection]) -> str:
    """text 필드가 있으면 그것을, 없으면 detections 를 이어붙여 반환한다.

    nemotron-ocr-v2 는 detection 과 recognition 이 나뉘어 있어서 벤더가 text 를 모아 줄 수도,
    detections 만 줄 수도 있다. 둘 다 처리한다.
    """
    text = raw.get("text")
    if isinstance(text, str) and text:
        return text
    return "\n".join(d.text for d in detections)


def _resolve_model_version(raw: dict[str, Any]) -> str:
    """모델 버전 문자열을 반환한다.

    벤더가 model_version 을 보내면 그 값을, 없으면 GPU 등급을 붙인 고정 식별자를 쓴다.
    벤치 리포트에서 GPU 등급을 구분하려는 것이다.
    """
    explicit = raw.get("model_version")
    if isinstance(explicit, str) and explicit:
        return explicit
    gpu_kind = raw.get("gpu_kind")
    if isinstance(gpu_kind, str) and gpu_kind:
        return f"nemotron-ocr-v2@{gpu_kind}"
    return "nemotron-ocr-v2"


def _coerce_str(value: Any) -> str:
    """값을 str 로 바꾼다."""
    return value if isinstance(value, str) else str(value)


def _coerce_confidence(value: Any) -> float:
    """confidence 를 0~1 float 로 바꾼다.

    바꿀 수 없으면 0.0 을 돌려준다. 벤치에서 0 은 "값 없음"으로 읽는다.
    """
    try:
        conf = float(value)
    except (TypeError, ValueError):
        return 0.0
    if conf < 0.0:
        return 0.0
    if conf > 1.0:
        return 1.0
    return conf


def require_dict(raw: Any) -> dict[str, Any]:
    """벤더 응답이 dict 인지 확인한다. 아니면 InferenceError 를 낸다."""
    if not isinstance(raw, dict):
        raise InferenceError(
            f"Nemotron-OCR Modal 응답이 dict 가 아님: {type(raw).__name__}",
        )
    return raw
