"""Chat_V1 FastAPI 라우터.

POST /api/chat/v1/ask: 텍스트 채팅 (Claude Sonnet), 답변을 JSON 하나로 돌려준다
POST /api/chat/v1/ask/stream: 텍스트 채팅 스트리밍, 답변을 SSE(text/event-stream)로 나눠 보낸다
POST /api/chat/v1/voice: 음성 채팅 (Gemini Flash Live)
POST /api/chat/v1/health: 헬스체크
"""
from __future__ import annotations

import json
from collections.abc import AsyncIterator

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from app.modules.Chat_V1.app.schemas import (
    ChatRequest,
    ChatResponse,
    VoiceChatRequest,
    VoiceChatResponse,
)
from app.modules.Chat_V1.app.service import answer_question, stream_answer
from app.modules.Chat_V1.app.voice_service import answer_voice_question

router = APIRouter(prefix="/api/chat/v1", tags=["chat-v1"])


@router.post("/ask", response_model=ChatResponse)
async def ask(request: ChatRequest) -> ChatResponse:
    """학생 질문에 강의 컨텍스트를 바탕으로 텍스트로 답한다.

    lecture_context(슬라이드·대본·퀴즈)를 참고해 Claude Sonnet이 답하고,
    참조한 슬라이드 목록을 함께 돌려준다.
    """
    try:
        return await answer_question(request)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/ask/stream")
async def ask_stream(request: ChatRequest) -> StreamingResponse:
    """/ask 와 같은 요청을 받아 답변을 SSE로 나눠 보낸다.

    event: delta  data: {"text": "..."}  답변 조각마다 한 번
    event: done   data: ChatResponse 필드(session_id, answer, referenced_slides)
    event: error  data: {"message": "..."}  실패하면 done 대신 보낸다
    요청 스키마 오류는 스트림을 열기 전에 422로 끝난다.
    """
    return StreamingResponse(_sse(stream_answer(request)), media_type="text/event-stream")


async def _sse(events: AsyncIterator[tuple[str, dict]]) -> AsyncIterator[str]:
    async for event, data in events:
        yield f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("/voice", response_model=VoiceChatResponse)
async def voice_chat(request: VoiceChatRequest) -> VoiceChatResponse:
    """음성 질문에 음성으로 답한다.

    Gemini Flash Live가 base64 음성을 받아 음성 인식·답변 생성·TTS를 한 번의
    호출로 처리한다. WAV(base64) 응답과 트랜스크립트를 함께 돌려준다.
    """
    try:
        return await answer_voice_question(request)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/health")
async def health() -> dict[str, str]:
    """Chat_V1 라우터 헬스체크."""
    return {"status": "ok", "service": "chat-v1"}
