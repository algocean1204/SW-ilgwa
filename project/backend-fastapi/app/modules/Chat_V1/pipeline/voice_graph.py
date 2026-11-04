"""Chat_V1 음성 파이프라인 LangGraph StateGraph 조립 및 실행 진입점."""
from __future__ import annotations

from functools import lru_cache

from langgraph.graph import END, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.modules.Chat_V1.app.schemas import VoiceChatRequest
from app.modules.Chat_V1.pipeline.voice_nodes import (
    format_voice_response,
    generate_voice_answer,
    validate_voice,
)
from app.modules.Chat_V1.pipeline.state import VoiceChatState


def build_voice_graph() -> CompiledStateGraph:
    """음성 파이프라인 StateGraph 를 조립하고 컴파일한다."""
    graph: StateGraph = StateGraph(VoiceChatState)

    graph.add_node("validate_voice", validate_voice)
    graph.add_node("generate_voice_answer", generate_voice_answer)
    graph.add_node("format_voice_response", format_voice_response)

    graph.set_entry_point("validate_voice")
    graph.add_edge("validate_voice", "generate_voice_answer")
    graph.add_edge("generate_voice_answer", "format_voice_response")
    graph.add_edge("format_voice_response", END)

    return graph.compile()


@lru_cache(maxsize=1)
def get_compiled_voice_graph() -> CompiledStateGraph:
    """음성 그래프를 한 번만 컴파일해 재사용한다."""
    return build_voice_graph()


async def run_voice_pipeline(request: VoiceChatRequest) -> VoiceChatState:
    """VoiceChatRequest로 초기 상태를 만들어 그래프를 돌리고 최종 VoiceChatState를 돌려준다.

    build_system_prompt가 service.py에 있어 순환 import를 피하려고 함수 안에서 import한다.
    """
    from app.modules.Chat_V1.app.service import build_system_prompt

    system_prompt = build_system_prompt(request)

    initial_state: VoiceChatState = {
        "session_id": request.session_id,
        "audio_input": request.audio_data,
        "audio_format": request.audio_format,
        "sample_rate": request.sample_rate,
        "system_prompt": system_prompt,
        "error_message": None,
        "pipeline_status": "init",
    }

    graph = get_compiled_voice_graph()
    final_state: VoiceChatState = await graph.ainvoke(initial_state)
    return final_state
