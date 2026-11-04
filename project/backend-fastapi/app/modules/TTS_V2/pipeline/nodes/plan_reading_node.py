"""TTS V2 낭독 재작성 노드.

청크마다 ReadingPlanner(Qwen3-4B MLX)로 normalized_text 를 낭독하기 좋은 planned_text 로 바꾼다.
플래너를 쓸 수 없거나 skip_planner 면 normalized_text 를 그대로 복사하고 계속 진행한다.
"""
from __future__ import annotations

import time
import logging

from app.modules.TTS_V2.schemas.state import AudiobookState, ChunkRecord
from app.modules.TTS_V2.connectors.llm_planner import ReadingPlanner

_LOG = logging.getLogger(__name__)


def _rewrite_chunk(chunk: ChunkRecord, language: str) -> ChunkRecord:
    """청크 하나의 normalized_text 를 planned_text 로 재작성한다. 실패하면 원문을 쓴다."""
    source = chunk.get("normalized_text", "")
    try:
        planned = ReadingPlanner.rewrite_for_reading(source, language=language)
    except Exception as exc:
        _LOG.warning("청크 %s 재작성 실패 — identity fallback: %s", chunk.get("chunk_id", "?"), exc)
        planned = source
    return {**chunk, "planned_text": planned}


def _is_planner_available() -> bool:
    """ReadingPlanner 가 사용 불가로 표시됐는지 본다. 모델 로드는 하지 않는다."""
    try:
        if ReadingPlanner._unavailable:
            return False
        return True
    except Exception:
        _LOG.debug("ReadingPlanner 가용 확인 실패", exc_info=True)
        return False


def plan_reading_node(state: AudiobookState) -> dict:
    """각 청크의 normalized_text 를 낭독용 텍스트로 재작성한다."""
    if state.get("pipeline_status") == "error":
        return {}
    t_start = time.monotonic()
    language = state.get("language", "ko")

    # 청크가 없으면 재작성을 건너뛴다
    chunks = state.get("chunks", [])
    if not chunks:
        _LOG.warning("plan_reading_node: 청크 0건 — 계획 생략")
        elapsed_ms = (time.monotonic() - t_start) * 1000
        timings = {**state.get("timings", {}), "plan_reading": elapsed_ms}
        return {
            "chunks": [],
            "current_phase": "planning",
            "timings": timings,
        }

    if state.get("skip_planner", False) or not _is_planner_available():
        _LOG.warning("ReadingPlanner 건너뜀 — normalized_text 를 planned_text 로 복사")
        updated_chunks = [
            {**chunk, "planned_text": chunk.get("normalized_text", "")}
            for chunk in chunks
        ]
        elapsed_ms = (time.monotonic() - t_start) * 1000
        timings = {**state.get("timings", {}), "plan_reading": elapsed_ms}
        return {
            "chunks": updated_chunks,
            "current_phase": "planning",
            "timings": timings,
        }

    updated_chunks = []
    for chunk in chunks:
        updated_chunks.append(_rewrite_chunk(chunk, language))

    elapsed_ms = (time.monotonic() - t_start) * 1000
    timings = {**state.get("timings", {}), "plan_reading": elapsed_ms}
    return {
        "chunks": updated_chunks,
        "current_phase": "planning",
        "timings": timings,
    }
