"""하이브리드 검색. 쿼리 임베딩(BGE-M3) → Qdrant Dense+Sparse 검색 → BGE-Reranker 재정렬.

구현체는 registry에서 가져오므로 바꿔도 이 파일은 그대로다.
"""
from __future__ import annotations

import logging

from ..config import rerank_top_k

_LOG = logging.getLogger(__name__)


async def hybrid_search(
    query: str,
    collection_name: str,
    top_k: int = 5,
) -> list[dict]:
    """하이브리드 검색 후 재정렬한 상위 top_k에 원본 payload를 붙여 돌려준다.

    Qdrant 검색은 Dense와 Sparse 결과를 RRF로 합친다.
    """
    from ..registry import get_embedder, get_reranker, get_store

    embedder = get_embedder()
    store = get_store()
    reranker = get_reranker()

    query_embeddings = await embedder.embed([query])
    if not query_embeddings:
        _LOG.warning("쿼리 임베딩 실패: 빈 결과 반환")
        return []

    query_embedding = query_embeddings[0]

    # 리랭킹 후보라 top_k보다 넉넉히(rerank_top_k) 가져온다
    search_top_k = rerank_top_k()
    raw_hits = await store.search(
        collection=collection_name,
        query_embedding=query_embedding,
        top_k=search_top_k,
    )

    if not raw_hits:
        return []

    documents = [hit.get("payload", {}).get("text", "") for hit in raw_hits]
    reranked = await reranker.rerank(query=query, documents=documents, top_k=top_k)

    results: list[dict] = []
    for item in reranked:
        original_idx = item["index"]
        if original_idx < len(raw_hits):
            hit = raw_hits[original_idx]
            results.append({
                "chunk_id": hit.get("id", ""),
                "score": item["score"],
                "text": item["text"],
                "payload": hit.get("payload", {}),
            })

    return results
