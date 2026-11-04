"""WebSocket 메시지 타입 정의.

업스트림(브라우저→서버): 바이너리 PCM 또는 JSON 제어 커맨드.
다운스트림(서버→브라우저): 아래 Pydantic 모델을 JSON으로 직렬화해 보낸다.
"""
from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class VADStateMessage(BaseModel):
    """음성/침묵이 바뀔 때만 보내는 VAD 상태 알림."""

    type: Literal["vad_state"] = "vad_state"
    speaking: bool = Field(..., description="True=발화 중, False=침묵")


class PartialTranscript(BaseModel):
    """확정 전 중간 전사 결과(UI 전용)."""

    type: Literal["partial"] = "partial"
    text: str = Field(..., description="현재 음성 버퍼의 중간 전사 결과")


class FinalTranscript(BaseModel):
    """턴 커밋 때 보내는 확정 전사 결과."""

    type: Literal["final"] = "final"
    text: str = Field(..., description="확정된 전사 텍스트")
    turn_id: str = Field(..., description="턴 고유 ID (UUID4)")
    duration_sec: float = Field(..., description="해당 턴 오디오 길이(초)")
    latency_ms: float = Field(..., description="ASR 추론 지연 시간(ms)")


class FlushSegment(BaseModel):
    """플러시 정책이 추출한 완결 문장 세그먼트 목록."""

    type: Literal["flush"] = "flush"
    segments: list[str] = Field(..., description="LLM→TTS 전달 준비된 완결 문장들")


class ErrorMessage(BaseModel):
    """추론·연결·검증 실패 알림."""

    type: Literal["error"] = "error"
    detail: str = Field(..., description="오류 상세 메시지")


class SessionAck(BaseModel):
    """세션 start/stop 명령에 대한 응답."""

    type: Literal["session_ack"] = "session_ack"
    session_id: str = Field(..., description="현재 세션 UUID")
    action: Literal["started", "stopped"] = Field(..., description="수행된 동작")
