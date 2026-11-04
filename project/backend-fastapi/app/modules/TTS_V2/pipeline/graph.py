"""TTS V2 LangGraph StateGraph 조립.

컴파일은 한 번만 하도록 get_compiled_graph 가 lru_cache 로 그래프 하나를 재사용한다.
"""
from __future__ import annotations

from functools import lru_cache

from langgraph.graph import END, StateGraph

from app.modules.TTS_V2.pipeline.nodes.chunk_text_node import chunk_text_node
from app.modules.TTS_V2.pipeline.nodes.clean_text_node import clean_text_node
from app.modules.TTS_V2.pipeline.nodes.load_input_node import load_input_node
from app.modules.TTS_V2.pipeline.nodes.merge_node import merge_node
from app.modules.TTS_V2.pipeline.nodes.plan_reading_node import plan_reading_node
from app.modules.TTS_V2.pipeline.nodes.postfx_node import postfx_node
from app.modules.TTS_V2.pipeline.nodes.qc_node import qc_node
from app.modules.TTS_V2.pipeline.nodes.retry_router_node import retry_router_node, route_after_qc
from app.modules.TTS_V2.pipeline.nodes.synthesize_node import synthesize_node
from app.modules.TTS_V2.schemas.state import AudiobookState


def build_tts_v2_graph():
    """AudiobookState StateGraph 를 조립하고 컴파일된 그래프를 반환한다."""
    graph = StateGraph(AudiobookState)

    graph.add_node("load_input", load_input_node)
    graph.add_node("clean_text", clean_text_node)
    graph.add_node("chunk_text", chunk_text_node)
    graph.add_node("plan_reading", plan_reading_node)
    graph.add_node("synthesize", synthesize_node)
    graph.add_node("postfx", postfx_node)
    graph.add_node("qc", qc_node)
    graph.add_node("retry_router", retry_router_node)
    graph.add_node("merge", merge_node)

    graph.set_entry_point("load_input")

    # 전처리 구간 (선형)
    graph.add_edge("load_input", "clean_text")
    graph.add_edge("clean_text", "chunk_text")
    graph.add_edge("chunk_text", "plan_reading")

    # 합성과 QC 구간
    graph.add_edge("plan_reading", "synthesize")
    graph.add_edge("synthesize", "postfx")
    graph.add_edge("postfx", "qc")
    graph.add_edge("qc", "retry_router")

    # QC 결과에 따라 재합성하거나 병합한다
    graph.add_conditional_edges(
        "retry_router",
        route_after_qc,
        {
            "passed": "merge",      # 전체 통과
            "retry": "synthesize",  # 실패 청크만 다시 합성
            "exhausted": "merge",   # 재시도 소진. QC 통과 청크만 병합
        },
    )

    graph.add_edge("merge", END)

    return graph.compile()


@lru_cache(maxsize=1)
def get_compiled_graph():
    """컴파일한 TTS V2 그래프를 돌려준다. 프로세스에서 한 번만 컴파일한다."""
    return build_tts_v2_graph()
