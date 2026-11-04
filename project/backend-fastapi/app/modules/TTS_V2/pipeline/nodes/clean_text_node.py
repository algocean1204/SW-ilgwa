"""TTS V2 텍스트 정제 노드.

sections 내 각 텍스트를 clean_text → normalize_for_tts 순서로 처리한다.
skip=True 인 섹션은 원본을 유지하고 정제를 건너뛴다.
"""
from __future__ import annotations

import logging
import time

from app.modules.TTS_V2.processors.text_cleaner import clean_text
from app.modules.TTS_V2.processors.text_normalizer import normalize_for_tts
from app.modules.TTS_V2.schemas.state import AudiobookState

_LOG = logging.getLogger(__name__)


def _clean_section(section: dict) -> dict:
    """섹션 하나의 text 를 정제·정규화한다. 낭독하지 않는 skip=True 섹션은 그대로 둔다."""
    if section.get("skip", False):
        return section
    cleaned = clean_text(section.get("text", ""))
    normalized = normalize_for_tts(cleaned)
    return {**section, "text": normalized}


def clean_text_node(state: AudiobookState) -> dict:
    """섹션마다 정제·정규화한다. 실패한 섹션은 원본을 유지한다."""
    if state.get("pipeline_status") == "error":
        return {}
    t_start = time.monotonic()
    sections = state.get("sections", [])
    updated_sections: list[dict] = []
    for section in sections:
        try:
            updated_sections.append(_clean_section(section))
        except Exception as exc:
            _LOG.warning(
                "섹션 '%s' 정제 실패 — 원본 유지: %s",
                section.get("title", ""), exc,
            )
            updated_sections.append(section)
    elapsed_ms = (time.monotonic() - t_start) * 1000
    timings = {**state.get("timings", {}), "clean_text": elapsed_ms}
    return {
        "sections": updated_sections,
        "current_phase": "cleaning",
        "timings": timings,
    }
