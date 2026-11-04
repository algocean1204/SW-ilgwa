"""Stage 7 노드. 후처리된 페이지를 한 문서로 합쳐 섹션 단위 ChunkRecord로 나눈다."""
from __future__ import annotations

import logging
import time

from ...registry import get_chunker
from ...schemas.state import ChunkRecord, OCRPipelineState, PageExtraction

_LOG = logging.getLogger(__name__)

# 합본 마크다운에 넣는 페이지 경계 표시
_PAGE_MARKER_TPL = "\n\n<!-- page:{page_num} -->\n\n"


def _combine_pages(processed_pages: list[PageExtraction]) -> str:
    """페이지 마크다운을 페이지 마커와 함께 한 문서로 합친다."""
    parts: list[str] = []
    for page in processed_pages:
        marker = _PAGE_MARKER_TPL.format(page_num=page.get("page_num", 0))
        parts.append(marker + page.get("markdown", ""))
    return "".join(parts)


async def chunk_node(state: OCRPipelineState) -> dict:
    """합본 마크다운을 섹션 단위 청크로 나눈다.

    청커가 실패하면 pipeline_status를 "error"로, 청크가 0건이면 "partial"로 둔다.
    """
    # 앞 단계가 실패했으면 아무것도 하지 않는다
    if state.get("pipeline_status") == "error":
        return {}
    t0 = time.perf_counter()
    processed_pages: list = state.get("processed_pages", [])
    _LOG.info("Stage 7: 청킹 시작 — %d 페이지", len(processed_pages))

    # 앞 단계에서 빈 결과가 넘어온 경우
    if not processed_pages:
        _LOG.warning("Stage 7: 입력 페이지 0건 — 청킹 건너뜀")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "chunks": [],
            "current_stage": "chunk",
            "pipeline_status": "partial",
            "error_message": "후처리된 페이지 0건 — 청킹 입력 없음",
            "timings": {**state.get("timings", {}), "chunk": elapsed},
        }

    try:
        chunker = get_chunker()
        combined_md = _combine_pages(processed_pages)
        # TypedDict는 dict라 PageExtraction 목록을 그대로 넘긴다
        raw_chunks: list[dict] = await chunker.chunk(combined_md, list(processed_pages))
    except Exception as exc:
        _LOG.exception("청킹 중 오류 발생")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "pipeline_status": "error",
            "error_message": f"chunk_node 오류: {exc}",
            "current_stage": "chunk",
            "timings": {**state.get("timings", {}), "chunk": elapsed},
        }

    # 필드가 맞지 않는 청크는 경고만 남기고 건너뛴다
    chunks: list[ChunkRecord] = []
    for c in raw_chunks:
        try:
            chunks.append(ChunkRecord(**c))
        except (TypeError, KeyError) as exc:
            _LOG.warning("청크 캐스팅 실패 — 건너뜀: %s", exc)

    elapsed = round(time.perf_counter() - t0, 3)

    # 페이지는 있는데 청크가 0건이면 partial로 알린다
    if not chunks and processed_pages:
        _LOG.warning(
            "Stage 7: %d 페이지 처리 후 청크 0건 — 품질 저하 가능",
            len(processed_pages),
        )
        return {
            "chunks": [],
            "current_stage": "chunk",
            "pipeline_status": "partial",
            "error_message": f"{len(processed_pages)} 페이지 처리 후 청크 0건 — 텍스트 추출 품질 저하 가능",
            "timings": {**state.get("timings", {}), "chunk": elapsed},
        }

    _LOG.info("Stage 7 완료: %d 청크 생성, %.3fs 소요", len(chunks), elapsed)

    return {
        "chunks": chunks,
        "current_stage": "chunk",
        "timings": {**state.get("timings", {}), "chunk": elapsed},
    }
