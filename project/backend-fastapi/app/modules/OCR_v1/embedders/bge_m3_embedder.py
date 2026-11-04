"""BGE-M3 로컬 임베더. OCR_V1_EMBED_BACKEND=local일 때 이 프로세스에서 돈다.

BGEM3FlagModel 한 번의 forward로 Dense(1024차원)와 Sparse 벡터를 함께 만든다.
모델은 asyncio.Lock으로 한 번만 로드한다.
"""
from __future__ import annotations

import asyncio
import logging

from ..config import embedding_batch_size, embedding_model

_LOG = logging.getLogger(__name__)

# FlagEmbedding이 없어도 모듈은 불러올 수 있다
try:
    from FlagEmbedding import BGEM3FlagModel
    _FLAG_AVAILABLE = True
except ImportError:
    _FLAG_AVAILABLE = False
    _LOG.warning("FlagEmbedding 패키지가 설치되지 않았습니다. BGEM3Embedder 비활성화.")

# global 문 없이 모델 싱글턴을 담는 dict
_STATE: dict[str, "BGEM3FlagModel | None"] = {"model": None}
_model_lock = asyncio.Lock()


async def _ensure_model() -> "BGEM3FlagModel":
    """BGE-M3 모델을 한 번만 로드한다.

    asyncio.Lock과 이중 확인으로 동시 로드를 막고, 로드는 to_thread에서 한다.
    """
    if _STATE["model"] is not None:
        return _STATE["model"]

    async with _model_lock:
        # 락을 기다리는 동안 다른 코루틴이 로드했을 수 있다
        if _STATE["model"] is not None:
            return _STATE["model"]

        model_name = embedding_model()
        _LOG.info("BGE-M3 모델 로딩 시작: %s", model_name)
        _STATE["model"] = await asyncio.to_thread(_load_model_sync, model_name)
        _LOG.info("BGE-M3 모델 로딩 완료: %s", model_name)

    return _STATE["model"]


def _load_model_sync(model_name: str) -> "BGEM3FlagModel":
    """to_thread에서 부르는 동기 모델 로드."""
    if not _FLAG_AVAILABLE:
        raise RuntimeError("FlagEmbedding 패키지가 설치되지 않아 BGE-M3를 로드할 수 없습니다.")
    return BGEM3FlagModel(model_name, use_fp16=True)


class BGEM3Embedder:
    """BGE-M3 하이브리드 임베더.

    Dense(1024d)와 Sparse 벡터를 Qdrant 하이브리드 검색 입력 형식으로 돌려준다.
    """

    name: str = "bge-m3"

    async def embed(self, texts: list[str]) -> list[dict]:
        """텍스트 목록을 Dense+Sparse 임베딩 딕셔너리 목록으로 바꾼다."""
        if not texts:
            return []

        model = await _ensure_model()
        batch_size = embedding_batch_size()

        # 메모리를 넘기지 않게 embedding_batch_size씩 나눈다
        all_results: list[dict] = []
        for i in range(0, len(texts), batch_size):
            batch = texts[i : i + batch_size]
            batch_embeddings = await asyncio.to_thread(
                _embed_batch_sync, model, batch
            )
            all_results.extend(batch_embeddings)

        return all_results


def _embed_batch_sync(model: "BGEM3FlagModel", texts: list[str]) -> list[dict]:
    """to_thread에서 부르는 배치 임베딩.

    encode()가 CPU/GPU 연산이라 스레드에서 돌린다.
    """
    output = model.encode(
        texts,
        batch_size=len(texts),
        return_dense=True,
        return_sparse=True,
        return_colbert_vecs=False,  # ColBERT 벡터는 쓰지 않는다
    )

    dense_vecs = output["dense_vecs"]          # shape: (N, 1024), numpy array
    lexical_weights = output["lexical_weights"]  # list[dict[str, float]]

    results: list[dict] = []
    for i, (dense, sparse_weights) in enumerate(zip(dense_vecs, lexical_weights)):
        # Qdrant SparseVector 형식으로 변환: {indices: list[int], values: list[float]}
        if sparse_weights:
            indices = [int(k) for k in sparse_weights.keys()]
            values = [float(v) for v in sparse_weights.values()]
        else:
            indices, values = [], []

        results.append({
            "dense": dense.tolist(),
            "sparse": {"indices": indices, "values": values},
        })

    return results
