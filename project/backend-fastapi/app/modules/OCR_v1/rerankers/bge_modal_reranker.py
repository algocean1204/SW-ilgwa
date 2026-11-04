"""BGE 원격 리랭커. deploy/modal_embed_app.py의 BGEM3Server.rerank로 점수만 받고 정렬은 여기서 한다.

반환 모양은 BGEReranker와 같다. OCR_V1_EMBED_BACKEND=modal(기본)일 때 registry가 고른다.
"""
from __future__ import annotations

from ..config import modal_embed_app_name, rerank_top_k
from .bge_reranker import rank_by_score

SERVER_CLASS = "BGEM3Server"


class BGEModalReranker:
    """Modal GPU에 배포한 bge-reranker-v2-m3로 다시 정렬한다."""

    name: str = "bge-reranker-modal"

    def __init__(self) -> None:
        import modal

        self._server = modal.Cls.from_name(modal_embed_app_name(), SERVER_CLASS)()

    async def rerank(
        self,
        query: str,
        documents: list[str],
        top_k: int | None = None,
    ) -> list[dict]:
        """반환 형식: [{"index": int, "text": str, "score": float}, ...]"""
        if not documents:
            return []
        scores = await self._server.rerank.remote.aio(query, documents)
        return rank_by_score(documents, scores, top_k if top_k is not None else rerank_top_k())
