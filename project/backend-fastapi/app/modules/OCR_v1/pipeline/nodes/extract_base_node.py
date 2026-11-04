"""Stage 2 노드. 기본 엔진(기본값 Marker)으로 PDF 전체를 추출한다."""
from __future__ import annotations

import logging
import time

from ...registry import get_engine
from ...schemas.state import OCRPipelineState, PageExtraction

_LOG = logging.getLogger(__name__)


async def extract_base(state: OCRPipelineState) -> dict:
    """기본 엔진으로 PDF를 추출해 페이지별 결과를 상태에 넣는다.

    엔진이 없거나 추출에 실패하면 pipeline_status를 "error"로 둔다.
    """
    # 앞 단계가 실패했으면 아무것도 하지 않는다
    if state.get("pipeline_status") == "error":
        return {}
    t0 = time.perf_counter()
    _LOG.info("Stage 2: 기본 OCR 추출 시작")

    try:
        engine = get_engine()
        _LOG.info("사용 엔진: %s", engine.name)
        # pdf_bytes가 없으면 빈 bytes를 넘겨 엔진이 오류를 내게 한다
        results: list[PageExtraction] = await engine.extract(state.get("pdf_bytes", b""))
    except Exception as exc:
        _LOG.exception("기본 OCR 추출 중 오류 발생")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "pipeline_status": "error",
            "error_message": f"extract_base 오류: {exc}",
            "current_stage": "extract",
            "timings": {**state.get("timings", {}), "extract": elapsed},
        }

    elapsed = round(time.perf_counter() - t0, 3)

    if not results:
        _LOG.warning("Stage 2: 추출 결과 0건 — 빈 PDF 또는 엔진 무응답")
        return {
            "extractions": [],
            "pipeline_status": "error",
            "error_message": "OCR 추출 결과 0건 — PDF가 비어있거나 엔진이 페이지를 인식하지 못함",
            "current_stage": "extract",
            "timings": {**state.get("timings", {}), "extract": elapsed},
        }

    _LOG.info(
        "Stage 2 완료: %d 페이지 추출, %.3fs 소요",
        len(results),
        elapsed,
    )

    return {
        "extractions": results,
        "current_stage": "extract",
        "timings": {**state.get("timings", {}), "extract": elapsed},
    }
