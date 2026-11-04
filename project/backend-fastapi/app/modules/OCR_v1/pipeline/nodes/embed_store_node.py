"""Stage 8 노드. 청크를 BGE-M3로 임베딩해 Qdrant에 업서트한다.

배치 분할은 임베더 안에서 한다.
"""
from __future__ import annotations

import logging
import time

from ... import config as cfg
from ...registry import get_embedder, get_store
from ...schemas.state import ChunkRecord, OCRPipelineState

_LOG = logging.getLogger(__name__)


def _build_point(chunk: ChunkRecord, embedding: dict, idx: int) -> dict:
    """청크와 임베딩 벡터를 Qdrant PointStruct 입력 딕셔너리로 변환한다.

    Qdrant 정수 ID로 쓰려고 chunk_id 앞 15자리를 16진수로 읽는다.
    16진수가 아니면 idx를 ID로 쓴다.
    """
    chunk_id = chunk.get("chunk_id", "")
    try:
        point_id = int(chunk_id[:15], 16) if chunk_id else idx
    except ValueError:
        _LOG.warning("chunk_id %r 이 16진수가 아님 — idx=%d 폴백", chunk_id, idx)
        point_id = idx

    payload = {
        "chunk_id": chunk_id,
        "text": chunk.get("text", ""),
        "token_count": chunk.get("token_count", 0),
        "section_title": chunk.get("section_title", ""),
        "section_level": chunk.get("section_level", 0),
        "page_nums": chunk.get("page_nums", []),
        "tables": chunk.get("tables", []),
        "formulas": chunk.get("formulas", []),
    }

    return {
        "id": point_id,
        "dense": embedding.get("dense"),
        "sparse": embedding.get("sparse"),
        "payload": payload,
    }


async def embed_store(state: OCRPipelineState) -> dict:
    """청크를 임베딩하고 벡터 저장소에 업서트한다.

    컬렉션이 없으면 만든다. 실패하면 pipeline_status를 "error"로 둔다.
    """
    # 앞 단계가 실패했으면 아무것도 하지 않는다
    if state.get("pipeline_status") == "error":
        return {}
    t0 = time.perf_counter()

    try:
        chunks: list = state.get("chunks", [])
        collection: str = state.get("collection_name", "")

        if not collection:
            raise ValueError("collection_name 이 비어 있어 업서트를 진행할 수 없다")

        # 청크가 없으면 끝낸다. 앞 단계 실패인지 빈 문서인지 로그로 나눈다
        if not chunks:
            elapsed = round(time.perf_counter() - t0, 3)
            # current_stage는 다음 노드가 덮어쓰므로 quality_scores와 status도 함께 본다
            upstream_stage = state.get("current_stage", "")
            quality_scores = state.get("quality_scores", [])
            failed_pages = [
                p for p in quality_scores
                if isinstance(p, dict) and not p.get("passed", False)
            ] if isinstance(quality_scores, list) else []
            has_upstream_failure = (
                upstream_stage in ("quality_gate", "fail_all", "error")
                or bool(failed_pages)
                or state.get("pipeline_status") == "partial"
            )
            if has_upstream_failure:
                _LOG.warning(
                    "Stage 8: 업스트림 품질 실패 후 청크 0건 — 빈 결과로 완료 "
                    "(stage=%s, failed_pages=%d)",
                    upstream_stage, len(failed_pages),
                )
            else:
                _LOG.warning("Stage 8: 업스트림 실패 없이 청크 0건 — 빈 문서 또는 청킹 이상")
            status = "partial"
            result = {
                "embedded_count": 0,
                "embedding_model": "",
                "current_stage": "embed_store",
                "pipeline_status": status,
                "timings": {**state.get("timings", {}), "embed_store": elapsed},
            }
            if has_upstream_failure:
                result["error_message"] = (
                    f"업스트림 품질 실패 후 청크 0건 — "
                    f"stage={upstream_stage}, failed_pages={len(failed_pages)}"
                )
            return result

        _LOG.info(
            "Stage 8: 임베딩 + 업서트 시작 — %d 청크, 컬렉션=%s",
            len(chunks),
            collection,
        )

        embedder = get_embedder()
        store = get_store()

        await store.ensure_collection(collection)

        texts = [c.get("text", "") for c in chunks]
        embeddings = await embedder.embed(texts)

        # 개수가 다르면 경고하고 짧은 쪽까지만 저장한다
        has_mismatch = len(embeddings) != len(chunks)
        if has_mismatch:
            _LOG.warning(
                "임베딩 수(%d)와 청크 수(%d) 불일치 — 짧은 쪽 기준 처리",
                len(embeddings), len(chunks),
            )

        # 임베딩이 dict가 아니면 빈 dict로 바꾼다
        points = [
            _build_point(chunk, emb if isinstance(emb, dict) else {}, idx)
            for idx, (chunk, emb) in enumerate(zip(chunks, embeddings))
        ]

        upserted = await store.upsert(collection, points)

    except Exception as exc:
        _LOG.exception("임베딩·업서트 중 오류 발생")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "pipeline_status": "error",
            "error_message": f"embed_store 오류: {exc}",
            "current_stage": "embed_store",
            "timings": {**state.get("timings", {}), "embed_store": elapsed},
        }

    elapsed = round(time.perf_counter() - t0, 3)
    try:
        model_name = cfg.embedding_model()
    except Exception as exc:
        _LOG.warning("임베딩 모델명 조회 실패 — 'unknown' 사용: %s", exc)
        model_name = "unknown"
    _LOG.info(
        "Stage 8 완료: %d 청크 업서트, 모델=%s, %.3fs 소요",
        upserted,
        model_name,
        elapsed,
    )

    result: dict = {
        "embedded_count": upserted,
        "embedding_model": model_name,
        "current_stage": "embed_store",
        "pipeline_status": "partial" if has_mismatch else "done",
        "timings": {**state.get("timings", {}), "embed_store": elapsed},
    }
    if has_mismatch:
        result["error_message"] = (
            f"임베딩 수({len(embeddings)})와 청크 수({len(chunks)}) 불일치 — "
            f"짧은 쪽({min(len(embeddings), len(chunks))}) 기준 처리"
        )
    return result
