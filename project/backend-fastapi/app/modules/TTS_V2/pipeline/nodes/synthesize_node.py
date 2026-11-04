"""TTS V2 음성 합성 노드.

청크마다 TTSEngineSession 으로 합성한다. ref_audio 디코딩은 세션을 열 때 한 번만 한다.
이미 QC 를 통과한 청크는 건너뛰고, ref_text 가 있는 ICL 모드에서는 합성 직후 레퍼런스 꼬리를 잘라낸다.
"""
from __future__ import annotations

import time

import numpy as np

from common.audio_io import duration_sec
from common.logging import get_logger
from app.modules.TTS_V2.connectors.tts_engine import TTSEngineSession
from app.modules.TTS_V2.connectors._icl_trimmer import trim_icl_prefix
from app.modules.TTS_V2.pipeline.config import TTS_V2_WHISPERX_MODEL
from app.modules.TTS_V2.schemas.state import AudiobookState, ChunkRecord

_LOG = get_logger(__name__)


def _should_skip(chunk: ChunkRecord) -> bool:
    """이미 합성했고 QC 도 통과한 청크면 True."""
    return chunk.get("audio_np") is not None and chunk.get("qc_passed") is True


def _apply_icl_trim(
    audio_np: np.ndarray,
    sr: int,
    expected_text: str,
    chunk_id: str,
) -> np.ndarray:
    """ICL 모드에서 레퍼런스 꼬리를 잘라낸다. 못 자르면 원본을 돌려준다."""
    trimmed, was_trimmed = trim_icl_prefix(
        audio_np, sr, expected_text, TTS_V2_WHISPERX_MODEL
    )
    if was_trimmed:
        _LOG.debug("청크 %s ICL 트리밍 적용 완료", chunk_id)
    return trimmed


def _synthesize_one(
    session: TTSEngineSession,
    chunk: ChunkRecord,
    ref_text: str,
    speed: float,
) -> ChunkRecord:
    """청크 하나를 합성해 결과를 채운다.

    ref_text 가 있으면 ICL 모드라 합성 뒤 레퍼런스 꼬리를 잘라낸다.
    합성이 실패하면 audio_np 를 None, qc_passed 를 False 로 둔다.
    """
    planned_text = chunk.get("planned_text", "")
    chunk_id = chunk.get("chunk_id", "unknown")
    try:
        audio_np, sr = session.synthesize_chunk(planned_text, speed=speed)
        if ref_text:
            audio_np = _apply_icl_trim(audio_np, sr, planned_text, chunk_id)
        dur = duration_sec(audio_np, sr)
        return {
            **chunk,
            "audio_np": audio_np,
            "sample_rate": sr,
            "duration_sec": dur,
        }
    except Exception as exc:
        _LOG.warning(
            "청크 %s 합성 실패: %s — qc_passed=False 로 표기",
            chunk_id,
            exc,
        )
        return {
            **chunk,
            "audio_np": None,
            "qc_passed": False,
            "qc_reason": "synthesis_error",
        }


def synthesize_node(state: AudiobookState) -> dict:
    """각 청크를 순서대로 합성한다. ref_audio 는 TTSEngineSession 이 한 번만 디코딩한다."""
    if state.get("pipeline_status") == "error":
        return {}
    t_start = time.monotonic()

    # 청크가 없으면 세션을 열지 않고 넘어간다
    chunks = state.get("chunks", [])
    if not chunks:
        _LOG.warning("synthesize_node: 청크 0건 — 합성 생략")
        elapsed_ms = (time.monotonic() - t_start) * 1000
        timings = {**state.get("timings", {}), "synthesize": elapsed_ms}
        return {
            "chunks": [],
            "current_phase": "synthesizing",
            "pipeline_status": "synthesizing",
            "timings": timings,
        }

    ref_text = state.get("ref_text", "")
    speed = float(state.get("speed", 1.0))
    updated_chunks: list = []
    try:
        with TTSEngineSession(
            ref_audio_bytes=state.get("ref_audio_bytes", b""),
            ref_text=ref_text,
            ref_sr=state.get("ref_sample_rate", 24000),
            language=state.get("language", "ko"),
        ) as session:
            for chunk in chunks:
                if _should_skip(chunk):
                    _LOG.debug("청크 %s 이미 합성 완료 — 건너뜀", chunk.get("chunk_id", "?"))
                    updated_chunks.append(chunk)
                else:
                    updated_chunks.append(_synthesize_one(session, chunk, ref_text, speed))
    except Exception as exc:
        # 세션 생성이나 합성 루프에서 예외가 나면 파이프라인을 error 로 끝낸다
        _LOG.error("TTSEngineSession 초기화 또는 합성 루프 실패: %s", exc)
        elapsed_ms = (time.monotonic() - t_start) * 1000
        timings = {**state.get("timings", {}), "synthesize": elapsed_ms}
        return {
            "chunks": updated_chunks or chunks,
            "current_phase": "synthesizing",
            "pipeline_status": "error",
            "error_message": f"합성 세션 오류: {exc}",
            "timings": timings,
        }

    elapsed_ms = (time.monotonic() - t_start) * 1000
    timings = {**state.get("timings", {}), "synthesize": elapsed_ms}
    return {
        "chunks": updated_chunks,
        "current_phase": "synthesizing",
        "pipeline_status": "synthesizing",
        "timings": timings,
    }
