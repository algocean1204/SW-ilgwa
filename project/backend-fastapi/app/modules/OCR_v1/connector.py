"""OCR_v1 진입점. 외부 호출자는 OCRv1Pipeline만 쓴다.

LangGraph 그래프와 검색 모듈은 이 클래스 뒤에 둔다.
"""
from __future__ import annotations

import logging

from .schemas.state import OCRPipelineState

_LOG = logging.getLogger(__name__)


class OCRv1Pipeline:
    """OCR v1 LangGraph 파이프라인 래퍼.

    인스턴스를 만들어 ingest()나 search()를 부른다.
    내부 의존성은 메서드 안에서 import해 모듈을 불러올 때 무거운 로드를 하지 않는다.
    """

    name: str = "ocr-v1-pipeline"

    async def ingest(
        self,
        pdf_bytes: bytes,
        filename: str,
        collection_name: str = "",
    ) -> dict:
        """PDF를 받아 LangGraph 파이프라인 전체를 실행한다.

        반환값은 OCRPipelineState 구조의 딕셔너리다.
        """
        from .pipeline.graph import get_compiled_graph

        # 컬렉션명이 없으면 파일명으로 만든다
        coll = collection_name or filename.rsplit(".", 1)[0].replace(" ", "_")
        _LOG.info("인제스트 시작: %s → 컬렉션=%s", filename, coll)

        initial_state: OCRPipelineState = {
            "pdf_bytes": pdf_bytes,
            "pdf_filename": filename,
            "total_pages": 0,
            "classifications": [],
            "extractions": [],
            "quality_scores": [],
            "passed_pages": [],
            "failed_pages": [],
            "fallback_extractions": [],
            "processed_pages": [],
            "chunks": [],
            "collection_name": coll,
            "embedded_count": 0,
            "embedding_model": "",
            "current_stage": "init",
            "pipeline_status": "running",
            "error_message": None,
            "timings": {},
        }

        graph = get_compiled_graph()
        result = await graph.ainvoke(initial_state)
        _LOG.info("인제스트 완료: 상태=%s, 청크=%d", result["pipeline_status"], len(result["chunks"]))
        return result

    async def search(
        self,
        query: str,
        collection_name: str,
        top_k: int = 5,
    ) -> list[dict]:
        """Dense+Sparse 검색 후 리랭킹한 결과를 돌려준다."""
        from .search import hybrid_search

        _LOG.info("검색 시작: query='%.50s', collection=%s, top_k=%d", query, collection_name, top_k)
        return await hybrid_search(query, collection_name, top_k)

    def supports(self, feature: str) -> bool:
        """파이프라인이 특정 기능을 지원하는지 반환한다."""
        return feature in {"pdf_ingest", "rag_search", "hybrid", "korean"}
