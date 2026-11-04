"""LangGraph StateGraph 조립 모듈.

build_fallback_graph() 가 그래프를 조립해 compile() 한다.
compile() 결과는 다시 써도 되므로 get_compiled_graph() 가 lru_cache 로 한 번만 만든다.
"""
from __future__ import annotations

from functools import lru_cache

from langgraph.graph import END, StateGraph

from .nodes import advance_tier_node, asr_attempt_node, quality_check_node, route_next_tier
from .state import ASRState


def build_fallback_graph():
    """폴백 ASR StateGraph 를 조립하고 compile 해 반환한다.

    실행 흐름:
      asr_attempt → quality_check → [route]
        "done"      → END (품질 통과)
        "exhausted" → END (tier 소진, best-effort)
        "retry"     → advance_tier → asr_attempt (루프)
    """
    graph: StateGraph = StateGraph(ASRState)

    graph.add_node("asr_attempt", asr_attempt_node)
    graph.add_node("quality_check", quality_check_node)
    graph.add_node("advance_tier", advance_tier_node)

    graph.set_entry_point("asr_attempt")

    graph.add_edge("asr_attempt", "quality_check")
    graph.add_edge("advance_tier", "asr_attempt")

    # route_next_tier 가 돌려주는 문자열로 분기한다
    graph.add_conditional_edges(
        "quality_check",
        route_next_tier,
        {
            "done": END,
            "exhausted": END,
            "retry": "advance_tier",
        },
    )

    return graph.compile()


@lru_cache(maxsize=1)
def get_compiled_graph():
    """컴파일된 그래프 싱글톤을 반환한다.

    compile() 결과는 상태가 없어서 여러 요청이 같이 써도 된다.
    """
    return build_fallback_graph()
