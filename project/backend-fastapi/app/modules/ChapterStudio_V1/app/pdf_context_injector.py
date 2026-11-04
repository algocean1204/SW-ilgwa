"""PDF 소스 강의에 참고도서 컨텍스트를 자동으로 넣는다.

source_mode=pdf이고 reference_book_context가 비어 있는 GenerationContext에
Qdrant RAG 검색 결과를 넣어 돌려준다. 실패하면 원본 context를 그대로 돌려준다.
흐름은 PDF → Qdrant 검색 → ReferenceBookContext 주입이다.
"""
from __future__ import annotations

import logging
import os
import urllib.parse

from app.modules.ChapterStudio_V1.app.generation_context import GenerationContext
from app.modules.ChapterStudio_V1.app.reference_books.retrieval import build_reference_book_context
from app.modules.ChapterStudio_V1.app.reference_books.schemas import ReferenceBookPage

_LOG = logging.getLogger(__name__)


def collection_name_from_source_ref(source_ref: str) -> str:
    """source_ref(S3 URL 또는 bare 파일명)에서 OCR 인제스트와 같은 Qdrant 컬렉션명을 만든다.

    커리큘럼 생성(curriculum_generate)도 이 함수를 쓰며, 컬렉션명 규칙의 기준은 이 함수다.

    OCR 인제스트(connector.py:37)는 bare 파일명에
    `filename.rsplit(".", 1)[0].replace(" ", "_")`를 적용한다.
    source_ref가 S3 URL이면 URL 디코딩 후 basename을 꺼내 같은 변환을 적용하므로
    두 쪽이 같은 문자열을 낸다.
    """
    # URL이면 퍼센트 인코딩을 풀고 경로에서 파일명만 꺼낸다.
    parsed = urllib.parse.urlparse(source_ref)
    if parsed.scheme in ("http", "https", "s3"):
        bare_name = os.path.basename(urllib.parse.unquote(parsed.path))
    else:
        bare_name = os.path.basename(source_ref) or source_ref
    # OCR 인제스트와 같은 정규화: 확장자를 떼고 공백을 언더스코어로 바꾼다.
    return bare_name.rsplit(".", 1)[0].replace(" ", "_")


async def inject_reference_context_if_pdf(context: GenerationContext) -> GenerationContext:
    """source_mode=pdf이고 reference_book_context가 비어 있으면 Qdrant RAG로 채운다.

    OCR 인제스트 시 Qdrant에 저장된 청크를 강의 주제+챕터명으로 검색해 자동 주입한다.
    실패하면 원본 context를 그대로 반환해 강의 생성 플로우가 중단되지 않게 한다.
    """
    if context.source_mode != "pdf" or context.reference_book_context is not None:
        return context
    if not context.pdf_file_name:
        return context

    collection = collection_name_from_source_ref(context.pdf_file_name)
    query = f"{context.topic} {context.chapter_title} {context.chapter_brief}".strip()
    try:
        from app.modules.OCR_v1.search import hybrid_search

        hits = await hybrid_search(query, collection, top_k=20)
        if not hits:
            # 0건이면 컬렉션명 불일치나 OCR 미완료일 수 있어 warning으로 남긴다.
            _LOG.warning(
                "[pdf-injector] PDF 참고서 컨텍스트 0 hit — lesson_id=%s, collection=%s",
                context.lesson_id,
                collection,
            )
            return context

        pages = [
            ReferenceBookPage(
                page=int(h.get("payload", {}).get("page_num", 1) or 1),
                text=str(h.get("text", "")),
                source_title=context.pdf_file_name,
            )
            for h in hits
            if h.get("text")
        ]
        ref_ctx = build_reference_book_context(
            pages,
            [query],
            source_title=context.pdf_file_name,
            max_hits=20,
        )
        if not ref_ctx.has_hits():
            return context

        _LOG.info(
            "[pdf-injector] 참고도서 컨텍스트 주입 완료 — lesson_id=%s, hits=%d",
            context.lesson_id,
            len(ref_ctx.hits),
        )
        return context.model_copy(update={"reference_book_context": ref_ctx})
    except Exception as exc:
        # Qdrant 미연결·컬렉션 없음 등 인프라 예외는 강의 생성 실패로 번지지 않게 경고만 남긴다
        _LOG.warning(
            "[pdf-injector] PDF 참고도서 주입 실패(무시) — lesson_id=%s, error=%s",
            context.lesson_id,
            exc,
        )
        return context
