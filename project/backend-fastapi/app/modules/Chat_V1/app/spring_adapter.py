"""Spring 채팅 요청을 Chat_V1로 넘기는 어댑터.

Spring `ChatService.callFastapi`는 `POST {base}/api/chat`로 snake_case 바디를 보내고
동기 JSON 응답을 기다린다(2xx면 성공, 실패면 CHAT_001).

처리 순서: Spring 바디(dict) → DB 슬라이드 조회 → ChatRequest → answer_question
→ ChatResponse → Spring이 읽는 키 형태.
필드 이름이 서로 달라(message↔user_message, referenced_slides↔citations)
변환은 이 파일에서 모두 하고, 모델 호출은 answer_question에 맡긴다.
"""
from __future__ import annotations

import logging
from typing import Any

from fastapi import APIRouter, Body, HTTPException

from app.modules.Chat_V1.app.schemas import (
    ChatRequest,
    ChatResponse,
    LectureContext,
    SlideContext,
)
from app.modules.Chat_V1.app.scope_guard import _REFUSAL_MARKERS
from app.modules.Chat_V1.app.service import answer_question
from app.modules.Chat_V1.app.slide_context_loader import load_slides

_LOG = logging.getLogger(__name__)

# prefix 없이 데코레이터에 전체 경로를 적어 Spring이 부르는 /api/chat에 연다.
router = APIRouter(tags=["chat-spring-adapter"])


@router.post("/api/chat")
async def chat_from_spring(body: dict[str, Any] = Body(...)) -> dict[str, Any]:
    """Spring 채팅 요청을 Chat_V1 파이프라인에 넘기고 결과를 동기로 돌려준다.

    Spring은 2xx를 성공으로 보므로 내부 실패는 422·500으로 올려 CHAT_001로
    처리되게 한다.
    """
    chat_request = await _to_chat_request(body)
    try:
        result = await answer_question(chat_request)
    except ValueError as exc:
        # 입력 검증 실패
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except RuntimeError as exc:
        # 모델·커넥터 실행 실패
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return _to_spring_response(result)


async def _to_chat_request(body: dict[str, Any]) -> ChatRequest:
    """Spring snake_case 바디를 Chat_V1 ChatRequest로 변환한다.

    lesson_id/slide_id/slide_idx가 있으면 public.slide에서 슬라이드를 읽어
    LectureContext를 만든다. 식별자가 없거나 조회에 실패하면 슬라이드 본문 없이
    폴백하고, 가짜 슬라이드 텍스트는 만들지 않는다.
    """
    message = _as_str(body.get("message")).strip()
    if not message:
        # user_message는 min_length=1이라 빈 메시지는 여기서 422로 거부한다.
        raise HTTPException(status_code=422, detail="message는 비어 있을 수 없어요.")

    context = body.get("context")
    context = context if isinstance(context, dict) else {}

    # 선택 텍스트가 있으면 질문 앞에 인용으로 덧붙여 모델이 맥락을 잡게 한다.
    selected_text = _as_str(body.get("selected_text")).strip()
    user_message = (
        f"[선택한 내용]\n{selected_text}\n\n[질문]\n{message}" if selected_text else message
    )

    # Spring이 전달한 슬라이드 위치 식별자로 실제 슬라이드를 조회한다.
    lesson_id = _as_str(body.get("lesson_id")).strip() or None
    slide_id = _as_str(body.get("slide_id")).strip() or None
    slide_idx_raw = body.get("slide_idx")
    # slide_idx는 int일 때만 쓰고 문자열이면 무시한다.
    slide_idx = int(slide_idx_raw) if isinstance(slide_idx_raw, int) else None

    lecture_context = await _build_lecture_context(context, lesson_id, slide_idx)

    return ChatRequest(
        session_id=_build_session_id(body),
        user_message=user_message,
        lecture_context=lecture_context,
        lesson_id=lesson_id,
        slide_id=slide_id,
        slide_idx=slide_idx,
    )


def _build_session_id(body: dict[str, Any]) -> str:
    """tutoring_id와 lesson_id를 이어 세션 식별자를 만든다.

    Spring이 session_id를 보내지 않아 과외·강의 식별자로 만든다. 둘 다 없으면
    'unknown'을 써서 min_length=1 검증을 통과시킨다.
    """
    tutoring_id = _as_str(body.get("tutoring_id")).strip()
    lesson_id = _as_str(body.get("lesson_id")).strip()
    parts = [p for p in (tutoring_id, lesson_id) if p]
    return ":".join(parts) if parts else "unknown"


async def _build_lecture_context(
    context: dict[str, Any],
    lesson_id: str | None,
    slide_idx: int | None,
) -> LectureContext:
    """DB 슬라이드로 LectureContext를 만든다.

    lesson_id가 있으면 public.slide에서 슬라이드를 읽는다. 없거나 비어 있으면
    과목·강의 제목만 담은 슬라이드 하나로 폴백하고, 가짜 본문은 만들지 않는다.
    """
    lesson_title = _as_str(context.get("lesson_title")).strip()
    course_subject = _as_str(context.get("course_subject")).strip()
    chapter_title = lesson_title or course_subject or "학습 대화"
    voice_scripts = _format_recent_qa(context.get("recent_qa"))

    if lesson_id:
        slides = await load_slides(lesson_id, slide_idx)
        if slides:
            return LectureContext(
                chapter_title=chapter_title,
                slides=slides,
                voice_scripts=voice_scripts,
                quiz_items=None,
            )
        _LOG.warning("[chat-adapter] lesson_id=%s 슬라이드 없음 — 최소 폴백 사용", lesson_id)

    fallback_content = f"과목: {course_subject or '미지정'} / 강의: {lesson_title or '미지정'}"
    return LectureContext(
        chapter_title=chapter_title,
        slides=[SlideContext(slide_idx=0, title=chapter_title, content=fallback_content)],
        voice_scripts=voice_scripts,
        quiz_items=None,
    )


def _format_recent_qa(recent_qa: Any) -> list[str] | None:
    """Spring recent_qa([{question, answer}, ...])를 Q/A 텍스트 목록으로 바꾼다.

    voice_scripts 자리에 넣어 모델이 직전 대화를 참고하게 한다. 형식이 맞지
    않으면 None을 돌려준다. 예전 거절 답변이 섞여 오면 _REFUSAL_MARKERS 어구로
    걸러, 모델이 거절문을 정상 대화로 보고 따라 하지 않게 한다.
    """
    if not isinstance(recent_qa, list) or not recent_qa:
        return None
    lines: list[str] = []
    for qa in recent_qa:
        if not isinstance(qa, dict):
            continue
        question = _as_str(qa.get("question")).strip()
        answer = _as_str(qa.get("answer")).strip()
        if answer and any(marker in answer for marker in _REFUSAL_MARKERS):
            continue
        if question or answer:
            lines.append(f"Q: {question}\nA: {answer}")
    return lines or None


def _to_spring_response(result: ChatResponse) -> dict[str, Any]:
    """ChatResponse를 Spring이 읽는 키 형태로 바꾼다.

    Spring은 extractString("response_text")와 extractStringList("citations")로 읽는다.
    referenced_slides(0-based int)는 Spring 표시 형식인 '[슬라이드 N]'(1-based)로 바꾼다.
    """
    citations = [f"[슬라이드 {idx + 1}]" for idx in result.referenced_slides]
    return {
        "response_text": result.answer,
        "citations": citations,
        # 추적용 부가 필드. Spring은 읽지 않는다.
        "session_id": result.session_id,
    }


def _as_str(value: Any) -> str:
    """None은 빈 문자열로, 나머지는 str()로 바꾼다."""
    return "" if value is None else str(value)
