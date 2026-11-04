"""턴 커밋 시 전사와 플러시 정책 적용."""
from __future__ import annotations

import uuid

from app.modules.ASR_V1.connectors.asr_engine import ASREngine
from app.modules.ASR_V1.pipeline.audio_buffer import AudioBuffer
from app.modules.ASR_V1.pipeline.flush_policy import extract_flushable
from app.modules.ASR_V1.schemas.ws_messages import FinalTranscript, FlushSegment


async def execute_commit(
    buffer: AudioBuffer,
    engine: ASREngine,
    lang: str,
    flush_buffer: str,
) -> tuple[FinalTranscript | None, FlushSegment | None, str]:
    """버퍼 비우기 → ASR 전사 → 플러시 정책 적용 순으로 처리한다.

    Returns: (확정 전사 또는 None, 플러시 세그먼트 또는 None, 남은 flush_buffer)
    ai_connectors 예외는 그대로 올린다.
    """
    audio = buffer.consume_speech()
    if len(audio) == 0:
        return None, None, flush_buffer
    response = await engine.transcribe(audio, lang=lang)
    turn_id = str(uuid.uuid4())
    final = FinalTranscript(
        text=response.text,
        turn_id=turn_id,
        duration_sec=response.duration_sec,
        latency_ms=response.latency_ms,
    )
    new_buffer = flush_buffer + response.text
    segments, remaining = extract_flushable(new_buffer)
    flush = FlushSegment(segments=segments) if segments else None
    return final, flush, remaining
