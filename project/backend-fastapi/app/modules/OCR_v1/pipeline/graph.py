"""OCR 파이프라인의 8개 노드를 LangGraph 그래프로 묶어 컴파일한다.

그래프 구조:
  START → classify_pdf → extract_base → llm_correct → quality_gate
  quality_gate → postprocess            ("pass": 전 페이지 통과)
  quality_gate → fallback → postprocess ("partial", "fail_all": 실패 페이지가 있음)
  postprocess → chunk_node → embed_store → END

fallback 은 게이트에 걸린 페이지만 MinerU로 다시 뽑고 그 페이지만 Kanana-2로 교정한다.
전 페이지가 스캔본이라 모두 실패해도 MinerU를 거친다.
"""
from __future__ import annotations

import logging

_LOG = logging.getLogger(__name__)

# 컴파일한 그래프를 재사용한다. global 문 없이 쓰려고 dict에 담는다
_CACHE: dict[str, object] = {}


def get_compiled_graph() -> object:
    """첫 호출에만 그래프를 컴파일하고 이후에는 캐시를 돌려준다."""
    if _CACHE.get("graph") is not None:
        return _CACHE["graph"]

    from langgraph.graph import END, START, StateGraph

    from ..schemas.state import OCRPipelineState
    from .nodes import (
        chunk_node,
        classify_pdf,
        embed_store,
        extract_base,
        fallback,
        llm_correct,
        postprocess,
        quality_gate,
        route_after_quality,
    )

    builder = StateGraph(OCRPipelineState)

    builder.add_node("classify_pdf", classify_pdf)
    builder.add_node("extract_base", extract_base)
    builder.add_node("llm_correct", llm_correct)
    builder.add_node("quality_gate", quality_gate)
    builder.add_node("fallback", fallback)
    builder.add_node("postprocess", postprocess)
    builder.add_node("chunk_node", chunk_node)
    builder.add_node("embed_store", embed_store)

    builder.add_edge(START, "classify_pdf")
    builder.add_edge("classify_pdf", "extract_base")
    builder.add_edge("extract_base", "llm_correct")
    builder.add_edge("llm_correct", "quality_gate")

    builder.add_conditional_edges(
        "quality_gate",
        route_after_quality,
        {
            "pass": "postprocess",
            "partial": "fallback",
            # 전 페이지 실패(스캔본)도 MinerU 재추출을 거친다
            "fail_all": "fallback",
        },
    )

    builder.add_edge("fallback", "postprocess")

    builder.add_edge("postprocess", "chunk_node")
    builder.add_edge("chunk_node", "embed_store")
    builder.add_edge("embed_store", END)

    _CACHE["graph"] = builder.compile()
    _LOG.info("LangGraph OCR 파이프라인 그래프 컴파일 완료")
    return _CACHE["graph"]
