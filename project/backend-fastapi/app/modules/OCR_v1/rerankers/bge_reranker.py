"""BGE-Reranker-v2-m3 Cross-Encoder 로컬 리랭커. OCR_V1_EMBED_BACKEND=local일 때 이 프로세스에서 돈다.

FlagReranker로 (query, document) 쌍 점수를 매겨 상위 top_k를 점수 내림차순으로 돌려준다.
모델은 프로세스에서 한 번만 올린다.
"""
from __future__ import annotations

import asyncio
import logging

from ..config import reranker_model, rerank_top_k

_LOG = logging.getLogger(__name__)

# FlagEmbedding은 선택 의존성이다
try:
    from FlagEmbedding import FlagReranker
    _FLAG_AVAILABLE = True
except ImportError:
    _FLAG_AVAILABLE = False
    _LOG.warning("FlagEmbedding 패키지가 설치되지 않았습니다. BGEReranker 비활성화.")

# global 문 없이 모델 싱글턴을 담는 dict
_STATE: dict[str, "FlagReranker | None"] = {"reranker": None}
_reranker_lock = asyncio.Lock()


async def _ensure_reranker() -> "FlagReranker":
    """FlagReranker 모델을 한 번만 로드한다.

    asyncio.Lock과 이중 확인으로 동시 로드를 막는다.
    """
    if _STATE["reranker"] is not None:
        return _STATE["reranker"]

    async with _reranker_lock:
        if _STATE["reranker"] is not None:
            return _STATE["reranker"]

        model_name = reranker_model()
        _LOG.info("BGEReranker 모델 로딩 시작: %s", model_name)
        _STATE["reranker"] = await asyncio.to_thread(_load_reranker_sync, model_name)
        _LOG.info("BGEReranker 모델 로딩 완료: %s", model_name)

    return _STATE["reranker"]


def _load_reranker_sync(model_name: str) -> "FlagReranker":
    """to_thread에서 부르는 동기 모델 로드.

    Mac MPS에서는 fp16 변환이 실패해 use_fp16=False로 로드한다.
    """
    import sys
    if not _FLAG_AVAILABLE:
        raise RuntimeError("FlagEmbedding 패키지가 설치되지 않아 BGEReranker를 로드할 수 없습니다.")
    # darwin MPS에서는 fp16 .half() 호출이 충돌해 fp32로 로드한다
    use_fp16 = sys.platform != "darwin"
    return FlagReranker(model_name, use_fp16=use_fp16)


class BGEReranker:
    """BGE-Reranker-v2-m3 Cross-Encoder 리랭커."""

    name: str = "bge-reranker"

    async def rerank(
        self,
        query: str,
        documents: list[str],
        top_k: int | None = None,
    ) -> list[dict]:
        """쿼리와 문서 리스트를 받아 관련성 점수 기준으로 재정렬한 결과를 반환한다.

        반환 형식: [{"index": int, "text": str, "score": float}, ...]
        """
        if not documents:
            return []

        effective_top_k = top_k if top_k is not None else rerank_top_k()
        reranker = await _ensure_reranker()

        ranked = await asyncio.to_thread(
            _rerank_sync, reranker, query, documents, effective_top_k
        )
        return ranked


def _rerank_sync(
    reranker: "FlagReranker",
    query: str,
    documents: list[str],
    top_k: int,
) -> list[dict]:
    """to_thread에서 부르는 동기 리랭킹.

    compute_score()는 CPU/GPU 연산이라 이벤트 루프를 막지 않게 스레드에서 돌린다.
    """
    pairs = [[query, doc] for doc in documents]
    scores: list[float] = reranker.compute_score(pairs, normalize=True)
    return rank_by_score(documents, scores, top_k)


def rank_by_score(documents: list[str], scores: list[float], top_k: int) -> list[dict]:
    """문서별 점수를 내림차순으로 정렬해 상위 top_k를 돌려준다. Modal 리랭커도 이 함수를 쓴다."""
    # (인덱스, 텍스트, 점수) 튜플로 묶어 점수 내림차순 정렬
    ranked = sorted(
        enumerate(zip(documents, scores)),
        key=lambda x: x[1][1],
        reverse=True,
    )[:top_k]

    return [
        {"index": idx, "text": text, "score": score}
        for idx, (text, score) in ranked
    ]
