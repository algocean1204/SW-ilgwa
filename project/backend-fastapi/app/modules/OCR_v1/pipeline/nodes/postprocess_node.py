"""Stage 6 노드. 페이지마다 마크다운 후처리(run_all)를 적용한다.

순서는 헤더/푸터 제거, 하이픈 복원, 단락 병합, 표 정규화, 수식 변환이다.
"""
from __future__ import annotations

import logging
import time

from ...postprocess import run_all
from ...schemas.state import OCRPipelineState, PageExtraction

_LOG = logging.getLogger(__name__)


async def _postprocess_page(page: PageExtraction) -> PageExtraction:
    """한 페이지 마크다운에 run_all을 적용한다. 빠진 키는 기본값으로 채운다."""
    cleaned_md = await run_all(page.get("markdown", ""))
    return PageExtraction(
        page_num=page.get("page_num", 0),
        markdown=cleaned_md,
        tables=page.get("tables", []),
        formulas=page.get("formulas", []),
        engine=page.get("engine", ""),
        llm_corrected=page.get("llm_corrected", False),
    )


async def postprocess(state: OCRPipelineState) -> dict:
    """모든 추출 페이지에 후처리를 적용하고 processed_pages 에 저장한다.

    페이지 순서대로 처리한다. 실패한 페이지는 원본을 두고 pipeline_status를 "partial"로 둔다.
    """
    # 앞 단계가 실패했으면 아무것도 하지 않는다
    if state.get("pipeline_status") == "error":
        return {}
    t0 = time.perf_counter()
    extractions: list = state.get("extractions", [])
    _LOG.info("Stage 6: 후처리 시작 — %d 페이지", len(extractions))

    # 앞 단계에서 빈 추출 결과가 넘어온 경우
    if not extractions:
        _LOG.warning("Stage 6: 입력 페이지 0건 — 후처리 건너뜀")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "processed_pages": [],
            "current_stage": "postprocess",
            "pipeline_status": "partial",
            "error_message": "추출된 페이지 0건 — 후처리 입력 없음",
            "timings": {**state.get("timings", {}), "postprocess": elapsed},
        }

    processed: list[PageExtraction] = []
    degraded_count = 0
    for page in extractions:
        try:
            processed_page = await _postprocess_page(page)
            processed.append(processed_page)
        except Exception as exc:
            _LOG.warning(
                "페이지 %d 후처리 실패 — 원본 유지: %s",
                page.get("page_num", 0), exc,
            )
            processed.append(page)
            degraded_count += 1

    elapsed = round(time.perf_counter() - t0, 3)
    if degraded_count > 0:
        _LOG.warning(
            "Stage 6: %d/%d 페이지 후처리 실패 — 원본 유지됨",
            degraded_count, len(extractions),
        )
    _LOG.info("Stage 6 완료: %d 페이지 처리, %.3fs 소요", len(processed), elapsed)

    result: dict = {
        "processed_pages": processed,
        "current_stage": "postprocess",
        "timings": {**state.get("timings", {}), "postprocess": elapsed},
    }
    # 후처리에 실패한 페이지가 있으면 partial로 알린다
    if degraded_count > 0:
        result["pipeline_status"] = "partial"
        result["error_message"] = f"후처리 실패 {degraded_count}/{len(extractions)} 페이지 — 원본 유지"
    return result
