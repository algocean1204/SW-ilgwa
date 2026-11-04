"""Stage 1 노드. pypdfium2로 페이지를 born-digital/scanned/mixed로 나눈다.

분류 함수가 동기라 asyncio.to_thread로 돌려 이벤트 루프를 막지 않는다.
"""
from __future__ import annotations

import asyncio
import logging
import time

from ...quality.classifier import classify_pages
from ...schemas.state import OCRPipelineState, PageClassification

_LOG = logging.getLogger(__name__)


async def classify_pdf(state: OCRPipelineState) -> dict:
    """PDF 전체 페이지를 분류하고 분류 결과를 상태에 저장한다.

    실패하거나 페이지가 0건이면 pipeline_status를 "error"로 두고 바로 반환한다.
    """
    # 앞 단계가 실패했으면 아무것도 하지 않는다
    if state.get("pipeline_status") == "error":
        return {}
    t0 = time.perf_counter()
    _LOG.info("Stage 1: PDF 페이지 분류 시작 — 파일명=%s", state.get("pdf_filename", ""))

    try:
        results: list[PageClassification] = await asyncio.to_thread(
            classify_pages, state.get("pdf_bytes", b"")
        )
    except Exception as exc:
        _LOG.exception("PDF 페이지 분류 중 오류 발생")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "pipeline_status": "error",
            "error_message": f"classify_pdf 오류: {exc}",
            "current_stage": "classify",
            "timings": {**state.get("timings", {}), "classify": elapsed},
        }

    elapsed = round(time.perf_counter() - t0, 3)

    if not results:
        _LOG.warning("Stage 1: 분류 결과 0건 — 빈 PDF이거나 손상된 파일")
        return {
            "classifications": [],
            "total_pages": 0,
            "pipeline_status": "error",
            "error_message": "PDF 페이지 분류 결과 0건 — 파일이 비어있거나 손상됨",
            "current_stage": "classify",
            "timings": {**state.get("timings", {}), "classify": elapsed},
        }

    _LOG.info(
        "Stage 1 완료: %d 페이지 분류, %.3fs 소요",
        len(results),
        elapsed,
    )

    return {
        "classifications": results,
        "total_pages": len(results),
        "current_stage": "classify",
        "timings": {**state.get("timings", {}), "classify": elapsed},
    }
