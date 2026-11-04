"""검색 결과를 인용 딕셔너리로 바꾼다. 외부 의존성 없는 순수 함수다."""
from __future__ import annotations

_TEXT_PREVIEW_MAX = 200  # 텍스트 미리보기 최대 글자 수


def build_citations(hits: list[dict]) -> list[dict]:
    """검색 결과 히트 리스트를 인용 딕셔너리 리스트로 변환한다.

    각 인용 형식:
    {
        chunk_id: str,          # 청크 고유 식별자
        text_preview: str,      # 텍스트 앞 200자 미리보기
        page_nums: list[int],   # 원본 PDF 페이지 번호 목록
        section_title: str,     # 청크가 속한 섹션 제목
        score: float,           # 관련성 점수 (리랭커 출력)
    }

    hits 리스트는 hybrid_search()의 반환값을 그대로 전달한다.
    """
    citations: list[dict] = []

    for hit in hits:
        payload = hit.get("payload", {})
        text = hit.get("text", "") or payload.get("text", "")
        preview = _truncate_text(text, _TEXT_PREVIEW_MAX)

        citations.append({
            "chunk_id": hit.get("chunk_id", ""),
            "text_preview": preview,
            "page_nums": payload.get("page_nums", []),
            "section_title": payload.get("section_title", ""),
            "score": round(float(hit.get("score", 0.0)), 6),
        })

    return citations


def _truncate_text(text: str, max_chars: int) -> str:
    """max_chars에서 자르고 말줄임표를 붙인다.

    미리보기 길이를 일정하게 하려고 단어 경계와 상관없이 글자 수로 자른다.
    """
    if len(text) <= max_chars:
        return text
    return text[:max_chars] + "..."
