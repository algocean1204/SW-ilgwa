"""TTS V2 품질 검증 노드.

요청의 qc_engine(기본값 TTS_V2_QC_ENGINE)에 따라 WhisperX, V1 ASR, 또는 둘 다로
청크 품질을 검증하고 ChunkRecord 의 QC 필드를 갱신한다.

"both" 는 WhisperX 를 먼저 돌리고, 통과한 청크만 V1 ASR 로 한 번 더 검증한다.
둘 다 통과해야 통과다.
"""
from __future__ import annotations

import time
import numpy as np

from common.logging import get_logger
from app.modules.TTS_V2.pipeline import config as cfg
from app.modules.TTS_V2.schemas.state import AudiobookState, ChunkRecord
from app.modules.TTS_V2.connectors.whisperx_qc import WhisperXQC, WhisperXQCResult
from app.modules.TTS_V2.connectors.asr_qc import ASRV1QC, ASRV1QCResult

logger = get_logger(__name__)


def qc_node(state: AudiobookState) -> dict:
    """청크별 품질을 검증하고 갱신된 chunks 와 타이밍을 돌려준다.

    audio_np 가 None 인 청크는 QC 실패로 표시한다.
    retry_count 는 retry_router_node 가 올리므로 여기서는 건드리지 않는다.
    """
    if state.get("pipeline_status") == "error":
        return {}
    t_start = time.monotonic()
    engine = state.get("qc_engine", cfg.TTS_V2_QC_ENGINE)

    ref_text: str = state.get("ref_text", "")
    updated_chunks: list[ChunkRecord] = [
        _evaluate_chunk(chunk, engine, ref_text) for chunk in state.get("chunks", [])
    ]

    elapsed_ms = (time.monotonic() - t_start) * 1000
    timings = dict(state.get("timings", {}))
    timings["qc"] = elapsed_ms

    passed = sum(1 for c in updated_chunks if c.get("qc_passed", False))
    total = len(updated_chunks)
    logger.info("QC 완료 — %d/%d 통과 (엔진: %s, %.0fms)", passed, total, engine, elapsed_ms)

    return {
        "chunks": updated_chunks,
        "current_phase": "qc",
        "timings": timings,
    }



def _evaluate_chunk(chunk: ChunkRecord, engine: str, ref_text: str = "") -> ChunkRecord:
    """단일 청크에 QC 를 적용하고 갱신된 ChunkRecord 를 반환한다."""
    audio_np: np.ndarray | None = chunk.get("audio_np")
    if audio_np is None:
        # 오디오가 없는 청크를 통과시키면 QC 가 의미 없어지므로 실패로 둔다
        return {**chunk, "qc_passed": False, "qc_reason": "QC 미실행: audio_np 누락"}

    expected = chunk.get("planned_text") or chunk.get("normalized_text", "")
    sr: int = chunk.get("sample_rate", 22050)

    if engine == "whisperx":
        return _apply_whisperx(chunk, audio_np, sr, expected, ref_text)
    if engine == "v1_asr":
        return _apply_v1asr(chunk, audio_np, sr, expected, ref_text)
    if engine == "both":
        return _apply_both(chunk, audio_np, sr, expected, ref_text)

    logger.warning("알 수 없는 QC 엔진: %r — whisperx 로 폴백", engine)
    return _apply_whisperx(chunk, audio_np, sr, expected, ref_text)


def _apply_whisperx(
    chunk: ChunkRecord,
    audio_np: np.ndarray,
    sr: int,
    expected: str,
    ref_text: str = "",
) -> ChunkRecord:
    """WhisperXQC 로 청크를 평가하고 결과를 반영한다."""
    try:
        result: WhisperXQCResult = WhisperXQC.evaluate(
            audio_np=audio_np,
            sample_rate=sr,
            expected_text=expected,
            cer_threshold=cfg.TTS_V2_CER_THRESHOLD,
            wer_threshold=cfg.TTS_V2_WER_THRESHOLD,
            ko_cps_min=cfg.TTS_V2_KO_CHARS_PER_SEC_MIN,
            ko_cps_max=cfg.TTS_V2_KO_CHARS_PER_SEC_MAX,
            silence_max_ratio=cfg.TTS_V2_SILENCE_MAX_RATIO,
            ref_text=ref_text,
        )
    except Exception as exc:
        logger.warning("WhisperXQC 평가 실패: %s", exc)
        return {**chunk, "qc_passed": False, "qc_reason": f"QC 엔진 오류: {exc}"}
    return _merge_result(chunk, result.cer, result.wer, result.passed, result.reason)


def _apply_v1asr(
    chunk: ChunkRecord,
    audio_np: np.ndarray,
    sr: int,
    expected: str,
    ref_text: str = "",
) -> ChunkRecord:
    """ASRV1QC 로 청크를 평가하고 결과를 반영한다."""
    try:
        result: ASRV1QCResult = ASRV1QC.evaluate(
            audio_np=audio_np,
            sample_rate=sr,
            expected_text=expected,
            cer_threshold=cfg.TTS_V2_CER_THRESHOLD,
            ko_cps_min=cfg.TTS_V2_KO_CHARS_PER_SEC_MIN,
            ko_cps_max=cfg.TTS_V2_KO_CHARS_PER_SEC_MAX,
            silence_max_ratio=cfg.TTS_V2_SILENCE_MAX_RATIO,
            ref_text=ref_text,
        )
    except Exception as exc:
        logger.warning("ASRV1QC 평가 실패: %s", exc)
        return {**chunk, "qc_passed": False, "qc_reason": f"QC 엔진 오류: {exc}"}
    # V1 ASR 는 WER 를 계산하지 않으므로 기존 값을 유지
    existing_wer: float | None = chunk.get("wer")
    return _merge_result(chunk, result.cer, existing_wer, result.passed, result.reason)


def _apply_both(
    chunk: ChunkRecord,
    audio_np: np.ndarray,
    sr: int,
    expected: str,
    ref_text: str = "",
) -> ChunkRecord:
    """WhisperX 와 V1 ASR 를 모두 통과해야 통과로 본다.

    WhisperX 가 실패하면 V1 ASR 는 돌리지 않고 실패로 끝낸다.
    """
    wx_chunk = _apply_whisperx(chunk, audio_np, sr, expected, ref_text)
    if not wx_chunk.get("qc_passed", False):
        return wx_chunk
    return _cross_validate_v1(chunk, audio_np, sr, expected, wx_chunk, ref_text)


def _cross_validate_v1(
    chunk: ChunkRecord,
    audio_np: np.ndarray,
    sr: int,
    expected: str,
    wx_chunk: ChunkRecord,
    ref_text: str = "",
) -> ChunkRecord:
    """WhisperX 통과 청크를 V1 ASR 로 교차 검증한다."""
    v1_chunk = _apply_v1asr(chunk, audio_np, sr, expected, ref_text)
    if v1_chunk.get("qc_reason", "") == "asr_unavailable":
        return wx_chunk  # V1 ASR 를 쓸 수 없으면 WhisperX 결과를 쓴다
    if not v1_chunk.get("qc_passed", False):
        # 수치는 WhisperX 값, 실패 사유는 V1 ASR 값을 쓴다
        return _merge_result(
            chunk,
            wx_chunk.get("cer"),
            wx_chunk.get("wer"),
            False,
            v1_chunk.get("qc_reason", "v1_asr_failed"),
        )
    return wx_chunk


def _merge_result(
    chunk: ChunkRecord,
    cer: float | None,
    wer: float | None,
    passed: bool,
    reason: str,
) -> ChunkRecord:
    """QC 결과를 덮어쓴 새 ChunkRecord 를 만든다. 원본 청크는 바꾸지 않는다."""
    merged: dict = {**chunk, "cer": cer, "wer": wer, "qc_passed": passed, "qc_reason": reason}
    return merged
