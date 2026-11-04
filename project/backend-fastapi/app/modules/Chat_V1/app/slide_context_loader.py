"""public.slide에서 강의 슬라이드를 읽어 Chat_V1 SlideContext 목록으로 바꾼다.

lesson_id는 chapter_id와 같다. public.slide.content는 ChapterStudio가 저장한
iframe HTML이라 그대로 쓰면 build_lecture_keywords가 "div", "class" 같은
태그·속성 이름을 강의 키워드로 잡고, apply_hallucination_guard가 정상 답변에도
범위 밖 안내문을 붙인다. 그래서 _html_to_text()로 보이는 텍스트만 꺼내 쓴다.
"""
from __future__ import annotations

import html as _html_module
import logging
import re
from html.parser import HTMLParser

from app.modules.Chat_V1.app.schemas import SlideContext
from common.db import get_connection

_LOG = logging.getLogger(__name__)

# 슬라이드 하나당 프롬프트에 넣는 최대 글자 수.
_SLIDE_CONTENT_MAX_LEN = 800

# iframe srcdoc 속성에서 내부 HTML을 추출하는 패턴
_SRCDOC_RE = re.compile(r"""\bsrcdoc\s*=\s*(["'])(.*?)\1""", re.IGNORECASE | re.DOTALL)
_SPACE_RE = re.compile(r"\s+")


class _VisibleTextParser(HTMLParser):
    """HTML 조각에서 화면에 보이는 텍스트만 모은다. script·style 안은 뺀다."""

    _hidden_tags: frozenset[str] = frozenset({"script", "style"})

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._parts: list[str] = []
        self._hidden_depth: int = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() in self._hidden_tags:
            self._hidden_depth += 1

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() in self._hidden_tags and self._hidden_depth:
            self._hidden_depth -= 1

    def handle_data(self, data: str) -> None:
        if not self._hidden_depth and data.strip():
            self._parts.append(data)

    def visible_text(self) -> str:
        """모은 조각을 공백 하나로 이어 붙인다."""
        raw = " ".join(self._parts)
        return _SPACE_RE.sub(" ", raw).strip()


def _extract_srcdoc(markup: str) -> str | None:
    """srcdoc 속성 값을 꺼낸다. 없으면 None."""
    match = _SRCDOC_RE.search(markup)
    if not match:
        return None
    inner = _html_module.unescape(match.group(2)).strip()
    return inner or None


def _html_to_text(raw: str) -> str:
    """HTML에서 화면에 보이는 텍스트만 꺼낸다.

    iframe srcdoc 형식이면 내부 HTML을 먼저 꺼내서 파싱한다.
    """
    html_fragment = _extract_srcdoc(raw) or raw
    unescaped = _html_module.unescape(html_fragment)
    parser = _VisibleTextParser()
    parser.feed(unescaped)
    parser.close()
    return parser.visible_text()


async def load_slides(
    lesson_id: str,
    current_slide_idx: int | None,
) -> list[SlideContext]:
    """public.slide에서 lesson_id(= chapter_id) 슬라이드를 조회한다.

    current_slide_idx가 있으면 그 슬라이드를 맨 앞에 둬 모델이 먼저 보게 한다.
    DB 오류이거나 슬라이드가 없으면 빈 목록을 돌려줘 호출부가 폴백하게 한다.
    """
    rows = await _fetch_rows(lesson_id)
    if not rows:
        return []

    slides = _rows_to_slide_contexts(rows)
    if not slides:
        return []

    return _prioritize_current(slides, current_slide_idx)


async def _fetch_rows(lesson_id: str) -> list[dict[str, object]]:
    """public.slide에서 lesson_id의 행을 slide_idx 순으로 읽어 dict 목록으로 돌려준다.

    조회에 실패하면 경고 로그를 남기고 빈 목록을 돌려준다.
    """
    try:
        async with get_connection() as conn:
            rows = await conn.fetch(
                "SELECT slide_idx, title, content "
                "FROM public.slide "
                "WHERE chapter_id = $1 "
                "ORDER BY slide_idx ASC",
                lesson_id,
            )
        return [dict(row) for row in rows]
    except Exception as exc:
        _LOG.warning(
            "[slide-loader] public.slide 조회 실패 — lesson_id=%s, error=%s",
            lesson_id,
            exc,
        )
        return []


def _rows_to_slide_contexts(rows: list[dict[str, object]]) -> list[SlideContext]:
    """DB 행 목록을 SlideContext 리스트로 바꾼다.

    title이 없으면 '슬라이드 N'으로 채운다. content는 iframe HTML이라
    _html_to_text()로 보이는 텍스트만 꺼내고, 텍스트가 없으면 그 슬라이드는
    건너뛴다(content min_length=1).
    """
    slides: list[SlideContext] = []
    for row in rows:
        raw_title = row.get("title")
        raw_content = row.get("content")
        raw_idx = row.get("slide_idx")
        # DB 컬럼이 NULL일 수 있어 타입이 맞을 때만 값을 쓴다.
        title = (raw_title if isinstance(raw_title, str) else "").strip()
        html_content = (raw_content if isinstance(raw_content, str) else "").strip()
        slide_idx: int = raw_idx if isinstance(raw_idx, int) else 0
        if not title:
            title = f"슬라이드 {slide_idx + 1}"
        content = _html_to_text(html_content) if html_content else ""
        if not content:
            # 보이는 텍스트가 없으면 HTML을 대신 넣지 않고 건너뛴다.
            _LOG.debug(
                "[slide-loader] 슬라이드 텍스트 추출 실패 — lesson_id 미상, slide_idx=%s",
                slide_idx,
            )
            continue
        slides.append(
            SlideContext(
                slide_idx=slide_idx,
                title=title,
                content=content[:_SLIDE_CONTENT_MAX_LEN],
            )
        )
    return slides


def _prioritize_current(
    slides: list[SlideContext],
    current_slide_idx: int | None,
) -> list[SlideContext]:
    """현재 슬라이드를 목록 앞에 배치해 모델이 현재 위치를 우선 참조하게 한다."""
    if current_slide_idx is None:
        return slides
    current = [s for s in slides if s.slide_idx == current_slide_idx]
    others = [s for s in slides if s.slide_idx != current_slide_idx]
    return current + others
