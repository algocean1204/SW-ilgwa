"""스트리밍 ASR 세션 상태 머신.

WebSocket 연결 하나당 하나의 StreamingASRSession 인스턴스를 생성한다.
바이너리 PCM 프레임과 JSON 제어 커맨드를 처리하고,
VAD 상태 및 턴 커밋 결과를 클라이언트에게 전송한다.
"""
from __future__ import annotations

import asyncio
import json
import uuid

from fastapi import WebSocket
from starlette.websockets import WebSocketDisconnect

from ai_connectors.errors import InferenceError, ModelLoadError, ModelNotFoundError
from app.modules.ASR_V1.connectors.asr_engine import ASREngine
from app.modules.ASR_V1.pipeline.audio_buffer import AudioBuffer
from app.modules.ASR_V1.pipeline.audio_processor import decode_pcm_int16, detect_speech_in_frame
from app.modules.ASR_V1.pipeline.commit_handler import execute_commit
from app.modules.ASR_V1.pipeline.config import (
    ASR_MODEL,
    BUFFER_MAX_SEC,
    PARTIAL_INTERVAL_MS,
    SAMPLE_RATE,
)
from app.modules.ASR_V1.pipeline.turn_manager import TurnManager
from app.modules.ASR_V1.pipeline.vad import SileroVAD
from app.modules.ASR_V1.schemas.config import SessionConfig
from app.modules.ASR_V1.schemas.ws_messages import (
    ErrorMessage,
    FinalTranscript,
    FlushSegment,
    PartialTranscript,
    SessionAck,
    VADStateMessage,
)


class StreamingASRSession:
    """WebSocket 세션 하나의 ASR 파이프라인 상태 머신."""

    def __init__(self, websocket: WebSocket, config: SessionConfig) -> None:
        self._ws = websocket
        self._config = config
        self._session_id = str(uuid.uuid4())
        self._vad = SileroVAD(threshold=config.vad_threshold or 0.5)
        self._buffer = AudioBuffer(sample_rate=SAMPLE_RATE)
        self._turn = TurnManager(
            min_silence_ms=config.vad_min_silence_ms or 800,
            turn_min_chars=config.turn_min_chars or 2,
        )
        self._engine = ASREngine(model_name=config.model or ASR_MODEL)
        # 확정 전사를 모아 두는 플러시 버퍼
        self._flush_buffer: str = ""
        # 부분 전사가 겹쳐 돌지 않게 하는 세마포어
        self._partial_sem = asyncio.Semaphore(1)
        self._partial_task: asyncio.Task[None] | None = None
        self._speaking: bool = False
        self._running: bool = False
        self._elapsed_ms: float = 0.0

    async def run(self) -> None:
        """연결이 닫힐 때까지 WS 메시지를 받아 처리한다."""
        self._running = True
        await self._send(SessionAck(session_id=self._session_id, action="started"))
        self._partial_task = asyncio.create_task(self._partial_timer())
        try:
            while self._running:
                message = await self._ws.receive()
                if "bytes" in message and message["bytes"]:
                    await self._handle_audio_chunk(message["bytes"])
                elif "text" in message and message["text"]:
                    await self._handle_control(json.loads(message["text"]))
        except (WebSocketDisconnect, asyncio.CancelledError):
            # 연결 종료와 태스크 취소는 정상 종료로 본다
            pass
        finally:
            await self.cleanup()

    async def _handle_audio_chunk(self, raw: bytes) -> None:
        """PCM 프레임을 디코딩해 VAD, 버퍼 적재, 턴 판정 순으로 처리한다."""
        audio_f32 = decode_pcm_int16(raw)
        is_speech = detect_speech_in_frame(audio_f32, self._vad)

        # VAD 상태 전환 알림
        if is_speech != self._speaking:
            self._speaking = is_speech
            await self._send(VADStateMessage(speaking=is_speech))

        if is_speech:
            self._buffer.append(audio_f32)

        # 버퍼가 BUFFER_MAX_SEC(기본 20초)에 닿으면 바로 커밋한다
        if self._buffer.duration_sec() >= BUFFER_MAX_SEC:
            await self._commit_turn()
            return

        self._elapsed_ms += len(audio_f32) / SAMPLE_RATE * 1000
        decision = self._turn.update(is_speech, self._elapsed_ms, self._flush_buffer)
        if decision["commit"]:
            await self._commit_turn()

    async def _handle_control(self, msg: dict[str, str]) -> None:
        """JSON 제어 커맨드를 처리한다. 현재는 stop만 받는다."""
        action = msg.get("action", "")
        if action == "stop":
            self._running = False
            await self._send(SessionAck(session_id=self._session_id, action="stopped"))

    async def _commit_turn(self) -> None:
        """버퍼를 커밋하고 확정 전사·플러시 결과를 보낸다."""
        self._turn.reset()
        try:
            final, flush, self._flush_buffer = await execute_commit(
                self._buffer, self._engine, self._config.language, self._flush_buffer
            )
        except Exception as exc:
            await self._send(ErrorMessage(detail=str(exc)))
            return
        if final:
            await self._send(final)
        if flush:
            await self._send(flush)

    async def _partial_timer(self) -> None:
        """PARTIAL_INTERVAL_MS(기본 500ms)마다 부분 전사를 보내는 백그라운드 태스크."""
        interval = PARTIAL_INTERVAL_MS / 1000.0
        while self._running:
            await asyncio.sleep(interval)
            if self._buffer.is_empty() or not self._speaking:
                continue
            # 이전 부분 전사가 아직 돌고 있으면 건너뛴다
            if self._partial_sem.locked():
                continue
            async with self._partial_sem:
                audio = self._buffer.extract_speech()
                try:
                    response = await self._engine.transcribe(audio, lang=self._config.language)
                    await self._send(PartialTranscript(text=response.text))
                except (ModelNotFoundError, ModelLoadError, InferenceError):
                    # 부분 전사는 UI 표시용이라 알려진 ASR 오류는 무시한다
                    pass

    async def _send(self, msg: VADStateMessage | PartialTranscript | FinalTranscript | FlushSegment | ErrorMessage | SessionAck) -> None:
        """WebSocket으로 Pydantic 모델을 JSON 직렬화해 전송한다."""
        try:
            await self._ws.send_text(msg.model_dump_json())
        except WebSocketDisconnect:
            # 전송이 실패하면 연결이 끊긴 것으로 보고 루프를 멈춘다
            self._running = False

    async def cleanup(self) -> None:
        """부분 전사 태스크를 취소하고 버퍼와 VAD 상태를 비운다."""
        self._running = False
        if self._partial_task and not self._partial_task.done():
            self._partial_task.cancel()
        self._buffer.clear()
        self._vad.reset_state()
