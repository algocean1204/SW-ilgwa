"""Chat_V1 텍스트 파이프라인의 LangGraph 노드. 각 노드는 상태를 받아 갱신한 상태를 돌려준다."""
from __future__ import annotations

from ai_connectors.errors import AIConnectorError

from app.modules.Chat_V1.pipeline.state import ChatState


def validate_input(state: ChatState) -> ChatState:
    """user_message나 system_prompt가 비어 있으면 error_message를 채운다."""
    if not state.get("user_message", "").strip():
        return {**state, "error_message": "학생 질문이 비어 있어요.", "pipeline_status": "invalid_input"}
    if not state.get("system_prompt", "").strip():
        return {**state, "error_message": "강의 컨텍스트가 없어요.", "pipeline_status": "invalid_input"}
    return {**state, "pipeline_status": "validated"}


async def generate_answer(state: ChatState) -> ChatState:
    """텍스트 모델을 불러 raw_answer를 채운다. 모델 선택은 service.call_text_model이 한다.

    커넥터 오류는 error_message에 남기고 상태를 failed로 둔다.
    """
    if state.get("error_message"):
        # 이전 노드에서 오류가 났으면 그대로 통과
        return state

    from app.modules.Chat_V1.app.service import call_text_model

    try:
        raw = await call_text_model(
            system_prompt=state["system_prompt"],
            user_message=state["user_message"],
        )
    except AIConnectorError as exc:
        return {**state, "error_message": str(exc), "pipeline_status": "failed"}

    return {**state, "raw_answer": raw, "pipeline_status": "answered"}


def format_response(state: ChatState) -> ChatState:
    """raw_answer를 service.finalize_answer로 정리해 최종 answer와 슬라이드 참조를 정한다.

    /ask/stream 도 같은 finalize_answer를 쓴다. 오류 상태면 빈 응답 대신
    안내 문구를 넣는다.
    """
    from app.modules.Chat_V1.app.service import ERROR_ANSWER, finalize_answer

    if state.get("error_message"):
        return {
            **state,
            "answer": ERROR_ANSWER,
            "referenced_slides": [],
            "pipeline_status": "error_handled",
        }

    answer, refs, guarded = finalize_answer(
        state.get("raw_answer", ""),
        state.get("lecture_keywords", []),
        state.get("slide_count"),
    )
    status = "guarded" if guarded else "done"
    return {**state, "answer": answer, "referenced_slides": refs, "pipeline_status": status}
