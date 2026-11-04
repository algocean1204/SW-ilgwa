"""TTS V2 재시도 라우터 노드.

QC 실패 청크의 audio_np 를 비워 재합성 대상으로 돌린다.
재시도 횟수가 max_retries 에 닿은 청크는 failed_chunks 에 넣는다.
"""
from __future__ import annotations

import logging
import time

from app.modules.TTS_V2.schemas.state import AudiobookState, ChunkRecord

_LOG = logging.getLogger(__name__)


def _increment_and_reset(chunk: ChunkRecord) -> ChunkRecord:
    """재시도 횟수를 올리고 오디오를 비워 재합성 대상으로 돌린다."""
    return {
        **chunk,
        "retry_count": chunk.get("retry_count", 0) + 1,
        "audio_np": None,          # None 이면 synthesize_node 가 다시 합성한다
        "qc_passed": False,
        "qc_reason": "pending",
    }


def _process_chunk(
    chunk: ChunkRecord,
    max_retries: int,
    failed_ids: list[str],
) -> ChunkRecord:
    """청크 하나에 재시도 정책을 적용한다."""
    if chunk.get("qc_passed", False):
        return chunk

    if chunk.get("retry_count", 0) >= max_retries:
        # 이전 사이클에서 이미 한도에 닿은 청크
        failed_ids.append(chunk.get("chunk_id", ""))
        return chunk

    next_count = chunk.get("retry_count", 0) + 1
    if next_count >= max_retries:
        # 이번이 한도라 오디오는 남기고 실패로 등록한다
        failed_ids.append(chunk.get("chunk_id", ""))
        return {**chunk, "retry_count": next_count}

    return _increment_and_reset(chunk)


def retry_router_node(state: AudiobookState) -> dict:
    """QC 실패 청크를 골라 재시도하거나 실패로 등록한다."""
    if state.get("pipeline_status") == "error":
        return {}
    t_start = time.monotonic()
    max_retries: int = state.get("max_retries", 3)
    failed_ids: list[str] = list(state.get("failed_chunks", []))

    updated_chunks = [
        _process_chunk(chunk, max_retries, failed_ids)
        for chunk in state.get("chunks", [])
    ]

    elapsed_ms = (time.monotonic() - t_start) * 1000
    timings = {**state.get("timings", {}), "retry_router": elapsed_ms}
    return {
        "chunks": updated_chunks,
        "failed_chunks": failed_ids,
        "current_phase": "retrying",
        "timings": timings,
    }


def route_after_qc(state: AudiobookState) -> str:
    """retry_router 다음 경로를 정한다.

    "passed" 는 전체 통과, "retry" 는 재시도할 실패 청크가 남은 경우,
    "exhausted" 는 재시도를 다 썼거나 청크가 없거나 error 상태인 경우다.
    """
    max_retries: int = state.get("max_retries", 3)
    chunks = state.get("chunks", [])

    if state.get("pipeline_status") == "error":
        _LOG.warning("QC 라우팅: pipeline_status=error — exhausted")
        return "exhausted"

    # 빈 chunks 는 all() 이 True 라 passed 로 가면 빈 WAV 가 200 으로 나간다
    if not chunks:
        _LOG.warning("QC 라우팅: 청크 0건 — exhausted")
        return "exhausted"

    if all(c.get("qc_passed", False) for c in chunks):
        _LOG.info("QC 라우팅: 전체 %d건 통과 — passed", len(chunks))
        return "passed"

    for chunk in chunks:
        if not chunk.get("qc_passed", False) and chunk.get("retry_count", 0) < max_retries:
            _LOG.info("QC 라우팅: 재시도 가능 청크 발견 — retry (max=%d)", max_retries)
            return "retry"

    _LOG.warning("QC 라우팅: 전체 재시도 소진 — exhausted (max=%d)", max_retries)
    return "exhausted"
