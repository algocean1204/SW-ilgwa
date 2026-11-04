"""강의 자료를 시스템 프롬프트에 넣고 Claude Sonnet으로 학생 질문에 답한다.

answer_question 은 /ask 용으로 답변 하나를 통째로 돌려준다.
stream_answer 는 /ask/stream 용으로 답변 조각을 이벤트로 차례로 내보낸다.
두 경로는 같은 프롬프트(build_system_prompt, build_text_request)와
같은 마무리(finalize_answer)를 쓴다.
슬라이드 참조 번호는 응답 텍스트에서 파싱한다.
모델은 CHAT_V1_TEXT_MODEL(기본 claude_sonnet)로 고른다. 공용 ACTIVE_TEXT_MODEL 을 따르지 않아
그 값이 gemini_flash 여도 채팅 기본 체인에는 Gemini·OpenAI가 끼지 않는다.
"""
from __future__ import annotations

import logging
import os
from collections.abc import AsyncIterator

from ai_connectors.text_schemas import ChapterAIRequest
from common.llm_output import strip_thinking

from app.modules.Chat_V1.app.schemas import ChatRequest, ChatResponse, SlideContext, VoiceChatRequest
from app.modules.Chat_V1.app.scope_guard import (
    SCOPE_NOTICE,
    apply_hallucination_guard,
    build_lecture_keywords,
    extract_referenced_slides,
)
from app.modules.Chat_V1.pipeline.graph import run_chat_pipeline

# voice_nodes 등이 service 에서 import 하므로 scope_guard 함수를 여기서 다시 내보낸다
__all__ = [
    "ERROR_ANSWER",
    "answer_question",
    "build_system_prompt",
    "build_text_request",
    "call_text_model",
    "finalize_answer",
    "stream_answer",
    "extract_referenced_slides",
    "build_lecture_keywords",
    "apply_hallucination_guard",
]

_MAX_TOKENS: int = 1024
_TEMPERATURE: float = 0.3
# 채팅 전용 텍스트 모델. 다른 모델은 이 env를 명시해야 쓴다
_CHAT_MODEL_ENV = "CHAT_V1_TEXT_MODEL"
_CHAT_MODEL_DEFAULT = "claude_sonnet"
# 답변을 만들지 못했을 때 학생에게 보이는 안내문
ERROR_ANSWER = "죄송해요, 답변을 생성하는 중에 오류가 발생했어요. 잠시 후 다시 시도해 주세요."

logger = logging.getLogger(__name__)


async def answer_question(request: ChatRequest) -> ChatResponse:
    """validate_input → generate_answer → format_response 를 돌려 ChatResponse를 만든다."""
    final_state = await run_chat_pipeline(request)
    return ChatResponse(
        session_id=request.session_id,
        answer=final_state["answer"],
        referenced_slides=final_state["referenced_slides"],
    )


def build_system_prompt(request: ChatRequest | VoiceChatRequest) -> str:
    """강의 컨텍스트를 넣은 시스템 프롬프트를 만든다.

    자료에 없는 질문도 일반 지식으로 답하게 하고 거절하지 않는다.
    범위 밖 답변에는 끝에 SCOPE_NOTICE 한 줄만 붙인다.
    """
    ctx = request.lecture_context
    slides_text = _format_slides(ctx.slides)

    optional_sections: list[str] = []
    if ctx.voice_scripts:
        joined = "\n".join(ctx.voice_scripts)
        optional_sections.append(f"[음성대본]\n{joined}")
    if ctx.quiz_items:
        joined = "\n".join(ctx.quiz_items)
        optional_sections.append(f"[퀴즈 항목]\n{joined}")

    extra = ("\n\n" + "\n\n".join(optional_sections)) if optional_sections else ""

    return (
        f"당신은 '{ctx.chapter_title}' 강의의 학습 도우미예요.\n"
        "아래 강의 자료를 바탕으로 학생의 질문에 친절하게 답변해 주세요.\n\n"
        f"[강의 슬라이드]\n{slides_text}{extra}\n\n"
        "답변 규칙:\n"
        "1. 위 강의 자료에 있는 내용이면 강의 자료를 우선 근거로 답변해요. "
        "강의 자료는 참고 자료예요 — 자료에 없는 질문이라도 절대 거절하지 말고 "
        "정확한 일반 지식으로 친절하게 답변해요.\n"
        "2. 강의 자료를 근거로 답할 때는 반드시 '[슬라이드 N]' 형식으로 출처를 표기해요 "
        "(인용이 자연스럽지 않아도 문장 끝에 붙여요). 강의 자료 밖 내용에는 "
        "슬라이드 인용을 붙이지 않아요.\n"
        "3. 두 개념을 비교하거나 학생이 헷갈릴 만한 질문이면, 정의 뒤에 "
        "짧은 대조 예시 1개를 들어 구분을 명확히 해요. 예시는 1~2줄, 장황하지 않게.\n"
        "4. 단순 정의만 나열하지 말고, 핵심 + 짧은 예시로 과외쌤처럼 설명해요. "
        "강의 자료를 인용할 때는 자료에 없는 내용을 자료 내용처럼 꾸며내지 않아요.\n"
        f"5. 강의 자료에 없는 내용으로 답했다면, 답변 끝에 '{SCOPE_NOTICE}' 한 줄을 덧붙여요. "
        "안내문만 보내고 답을 생략하는 것은 금지예요 — 항상 질문에 먼저 답해요.\n"
        "6. 해요체(~해요, ~예요)를 사용해요.\n"
        "7. 반드시 한국어 자연어 문장으로만 답해요. 내부 분석·사고 과정·체크리스트·"
        "영어 메모·'Yes/No' 식 판단 메모를 절대 답변에 노출하지 않아요. 학생에게 보여줄 "
        "최종 답변 문장만 출력해요(추론 흔적, 영어 불릿, 메타 설명 금지).\n\n"
        + _FEW_SHOT_BLOCK
    )


# few-shot 예시 세 개: 개념 질문(슬라이드 인용), 비교 질문(대조 예시),
# 범위 밖 질문(일반 지식으로 답하고 끝에 안내 한 줄)
_FEW_SHOT_BLOCK: str = (
    "[예시]\n"
    "질문: 이 개념이 왜 중요한지 알려줘.\n"
    "답변: 이 개념은 뒤에 나오는 내용을 이해하는 핵심 토대예요. 예를 들어, "
    "이 개념을 모르면 다음 단계에서 막히는 경우가 많아요. [슬라이드 2]\n"
    "질문: A랑 B가 어떻게 달라요?\n"
    "답변: 둘이 헷갈리기 쉽죠! [슬라이드 1]에 따르면 A는 ○○이고, B는 △△예요. "
    "간단히 비교하면, A는 '입력을 받는 이름'이고 B는 '실제로 넣는 값'이에요. "
    "예: def f(x)에서 x가 A, f(3)의 3이 B예요.\n"
    "질문: 환율이 오르면 수입 물가는 왜 올라요?\n"
    "답변: 환율이 오르면 같은 외화 금액을 사는 데 더 많은 원화가 필요해져요. "
    "그래서 해외에서 들여오는 상품의 원화 표시 가격이 올라가고, 수입 물가가 상승해요. "
    f"{SCOPE_NOTICE}"
)


def build_text_request(system_prompt: str, user_message: str) -> ChapterAIRequest:
    """/ask 와 /ask/stream 이 같이 쓰는 모델 요청을 만든다."""
    return ChapterAIRequest(
        system=system_prompt,
        user=user_message,
        max_tokens=_MAX_TOKENS,
        temperature=_TEMPERATURE,
    )


def finalize_answer(
    raw: str, lecture_keywords: list[str], slide_count: int | None
) -> tuple[str, list[int], bool]:
    """모델 원문을 최종 답변으로 정리해 (answer, referenced_slides, guarded)를 돌려준다.

    <think> 블록을 지우고, 강의와 겹치지 않는 답변에는 범위 밖 안내문을 붙인 뒤,
    slide_count를 넘지 않는 슬라이드 인용만 뽑는다.
    """
    answer, guarded = apply_hallucination_guard(strip_thinking(raw), lecture_keywords)
    # 안내문이 붙은 답변에는 인용이 없으므로 refs는 빈 목록이 된다.
    return answer, extract_referenced_slides(answer, slide_count), guarded


def _chat_connector() -> object:
    # registry 를 모듈 맨 위에서 불러오면 ChapterStudio_V1 보다 먼저 로드될 때
    # registry → tts 커넥터 → ChapterStudio_V1 → registry 순환 import 가 난다
    from ai_connectors.registry import get_text_connector

    return get_text_connector(os.getenv(_CHAT_MODEL_ENV, _CHAT_MODEL_DEFAULT))


async def call_text_model(system_prompt: str, user_message: str) -> str:
    """채팅 텍스트 모델(기본 Claude Sonnet)을 불러 답변 텍스트를 돌려준다.

    레지스트리는 호출마다 새 커넥터를 만들고 lifespan 이 닫아 주지 않는다.
    그래서 여기서 만든 커넥터는 여기서 닫는다.
    """
    connector = _chat_connector()
    try:
        response = await connector.generate(build_text_request(system_prompt, user_message))
        return response.text
    finally:
        await _aclose_if_supported(connector)


async def stream_answer(request: ChatRequest) -> AsyncIterator[tuple[str, dict]]:
    """답변을 ("delta", {"text"}) 조각으로 내보내고 끝에 ("done", ChatResponse 필드)를 낸다.

    실패하면 ("error", {"message"})를 내고 끝낸다. 조각을 이미 보냈어도 done은 보내지 않는다.
    done의 answer는 finalize_answer를 거친 최종 답변이라 조각을 이어 붙인 것과 다를 수 있다.
    stream 메서드가 없는 커넥터는 generate 결과를 조각 하나로 보낸다.
    """
    if not request.user_message.strip():
        yield "error", {"message": "학생 질문이 비어 있어요."}
        return

    connector = None
    try:
        connector = _chat_connector()
        req = build_text_request(build_system_prompt(request), request.user_message)
        stream = getattr(connector, "stream", None)
        parts: list[str] = []
        if callable(stream):
            async for text in stream(req):
                parts.append(text)
                yield "delta", {"text": text}
        else:
            text = (await connector.generate(req)).text
            parts.append(text)
            yield "delta", {"text": text}

        answer, refs, _ = finalize_answer(
            "".join(parts), build_lecture_keywords(request), len(request.lecture_context.slides)
        )
        yield "done", ChatResponse(
            session_id=request.session_id, answer=answer, referenced_slides=refs
        ).model_dump()
    except Exception:  # 응답 헤더가 이미 나갔으므로 HTTP 오류 대신 error 이벤트로 알린다
        logger.exception("Chat_V1 스트리밍 답변 실패")
        yield "error", {"message": ERROR_ANSWER}
    finally:
        if connector is not None:
            await _aclose_if_supported(connector)


async def _aclose_if_supported(connector: object) -> None:
    """aclose 가 있는 커넥터만 닫는다. codex CLI 처럼 닫을 자원이 없으면 건너뛴다."""
    aclose = getattr(connector, "aclose", None)
    if callable(aclose):
        await aclose()


def _format_slides(slides: list[SlideContext]) -> str:
    """슬라이드 목록을 프롬프트용 텍스트로 변환한다."""
    lines: list[str] = []
    for slide in slides:
        # slide_idx는 0-based라 프롬프트에는 1을 더해 쓴다
        lines.append(
            f"[슬라이드 {slide.slide_idx + 1}] {slide.title}\n{slide.content}"
        )
    return "\n\n".join(lines)
