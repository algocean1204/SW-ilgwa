"""OCR_v1 컴포넌트 Protocol.

구현체는 registry.py에 등록한다. 호출자는 Protocol 타입만 보므로
구현체를 바꿔도 호출부는 그대로다.
"""
from __future__ import annotations

from typing import Any, Protocol, runtime_checkable


@runtime_checkable
class OCREngine(Protocol):
    """OCR 엔진(Marker, MinerU)."""

    name: str  # 레지스트리 등록 키와 동일한 엔진 식별자

    async def extract(
        self,
        pdf_bytes: bytes,
        pages: list[int] | None = None,
        force_ocr: bool = False,
        use_llm: bool = False,
    ) -> list[dict]: ...
    """PDF 에서 페이지별 마크다운·표·수식을 추출한다.

    pages 가 None 이면 전체 페이지를 처리한다.
    force_ocr=True 면 텍스트 레이어가 있어도 이미지 OCR 을 강제한다.
    use_llm=True 면 엔진 내장 LLM 교정을 활성화한다 (지원 엔진만).
    반환: [{"page_num": int, "markdown": str, "tables": [...], "formulas": [...]}]
    """

    def supports(self, feature: str) -> bool: ...
    """엔진이 특정 기능을 지원하는지 확인한다 (예: "llm_correction", "table")."""


@runtime_checkable
class Chunker(Protocol):
    """청킹 전략. 기본은 섹션 기반이다."""

    async def chunk(
        self,
        markdown: str,
        pages: list[dict],
    ) -> list[dict]: ...
    """마크다운을 청크 목록으로 분할한다.

    pages 는 PageExtraction 형식의 딕셔너리 목록이다.
    반환: ChunkRecord 형식의 딕셔너리 목록
    """


@runtime_checkable
class Embedder(Protocol):
    """Dense+Sparse 임베더.

    BGE-M3처럼 한 모델이 dense와 sparse를 함께 내는 경우를 기본으로 본다.
    """

    name: str  # 레지스트리 등록 키

    async def embed(self, texts: list[str]) -> list[dict]: ...
    """텍스트 목록을 벡터 딕셔너리 목록으로 변환한다.

    반환 딕셔너리 형식:
    {
        "dense": list[float],
        "sparse": {"indices": list[int], "values": list[float]},
    }
    """


@runtime_checkable
class VectorStore(Protocol):
    """벡터 DB 저장·검색.

    Qdrant를 다른 DB로 바꿀 때 이 계약만 지키면 된다.
    """

    async def upsert(
        self,
        collection: str,
        points: list[dict],
    ) -> int: ...
    """포인트를 컬렉션에 업서트하고 저장된 포인트 수를 반환한다."""

    async def search(
        self,
        collection: str,
        query_embedding: dict,
        top_k: int = 10,
        filters: dict | None = None,
    ) -> list[dict]: ...
    """하이브리드 검색을 수행하고 관련도 순 결과를 반환한다."""

    async def ensure_collection(self, collection: str) -> None: ...
    """컬렉션이 없으면 생성한다 (이미 있으면 아무 것도 하지 않는다)."""


@runtime_checkable
class Reranker(Protocol):
    """벡터 검색 결과를 다시 정렬하는 cross-encoder 리랭커."""

    name: str  # 레지스트리 등록 키

    async def rerank(
        self,
        query: str,
        documents: list[str],
        top_k: int = 5,
    ) -> list[dict]: ...
    """query 와 documents 를 크로스인코더로 재정렬한다.

    반환 딕셔너리 형식:
    {"index": int, "score": float, "text": str}
    """
