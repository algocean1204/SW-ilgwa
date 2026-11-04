"""마크다운 헤딩 기준 섹션 청킹 (기본 600~1,200 토큰).

섹션이 chunk_max_tokens를 넘으면 단락 경계에서 더 나누고,
chunk_min_tokens보다 작으면 앞 섹션에 붙인다.
표·수식은 속한 섹션 청크에 남겨 검색할 때 문맥이 같이 온다.
"""
from __future__ import annotations

import hashlib
import logging
import re
from dataclasses import dataclass, field

import tiktoken

from .. import config as cfg

_LOG = logging.getLogger(__name__)
_HEADING_RE = re.compile(r"^(#{1,4})\s+(.+)$", re.MULTILINE)
_TABLE_RE = re.compile(r"^\|.+\|$", re.MULTILINE)
_FORMULA_RE = re.compile(r"\$\$.+?\$\$|\$[^$\n]+\$", re.DOTALL)
_TOKENIZER = tiktoken.get_encoding("cl100k_base")


@dataclass
class _Section:
    """청킹 전 섹션 중간 표현."""

    title: str
    level: int
    content: str
    page_nums: list[int] = field(default_factory=list)


def _count_tokens(text: str) -> int:
    """tiktoken cl100k_base 기준 토큰 수."""
    return len(_TOKENIZER.encode(text))


def _extract_page_nums(pages: list[dict], start: int, end: int) -> list[int]:
    """마크다운 오프셋 범위에 해당하는 페이지 번호 목록을 반환한다."""
    # 페이지 markdown 길이의 누적합으로 페이지를 추정한다
    nums: list[int] = []
    offset = 0
    for p in pages:
        md = p.get("markdown", "")
        p_end = offset + len(md)
        if offset <= end and p_end >= start:
            nums.append(p["page_num"])
        offset = p_end
    return sorted(set(nums)) if nums else [pages[0]["page_num"]] if pages else []


def _split_by_paragraphs(text: str, max_tok: int) -> list[str]:
    """텍스트를 단락 경계에서 max_tok 이하 청크로 분할한다."""
    paragraphs = re.split(r"\n\n+", text)
    chunks: list[str] = []
    current = ""
    for para in paragraphs:
        candidate = (current + "\n\n" + para).strip() if current else para
        if _count_tokens(candidate) > max_tok and current:
            chunks.append(current.strip())
            current = para
        else:
            current = candidate
    if current.strip():
        chunks.append(current.strip())
    return chunks if chunks else [text]


def _parse_sections(markdown: str) -> list[_Section]:
    """마크다운을 헤딩 경계로 섹션 목록으로 파싱한다."""
    matches = list(_HEADING_RE.finditer(markdown))
    sections: list[_Section] = []

    if not matches:
        # 헤딩이 없으면 문서 전체를 한 섹션으로 본다
        sections.append(_Section(title="본문", level=1, content=markdown))
        return sections

    for i, m in enumerate(matches):
        level = len(m.group(1))
        title = m.group(2).strip()
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(markdown)
        content = markdown[start:end].strip()
        sections.append(_Section(title=title, level=level, content=content))

    return sections


def _make_chunk_id(filename: str, section_title: str, idx: int) -> str:
    """청크 고유 ID 를 생성한다."""
    raw = f"{filename}:{section_title}:{idx}"
    return hashlib.sha256(raw.encode()).hexdigest()[:16]


class SectionChunker:
    """Chunker Protocol의 섹션 기반 구현체."""

    async def chunk(self, markdown: str, pages: list[dict]) -> list[dict]:
        """마크다운을 청크 딕셔너리 목록으로 분할한다.

        pages 는 PageExtraction 형식 딕셔너리 목록이다.
        """
        min_tok = cfg.chunk_min_tokens()
        max_tok = cfg.chunk_max_tokens()
        filename = pages[0].get("engine", "unknown") if pages else "unknown"

        sections = _parse_sections(markdown)
        merged: list[_Section] = []

        # min_tok보다 작은 섹션은 앞 섹션에 붙인다
        for sec in sections:
            if merged and _count_tokens(sec.content) < min_tok:
                prev = merged[-1]
                prev.content += "\n\n" + sec.content
                prev.page_nums.extend(sec.page_nums)
            else:
                merged.append(sec)

        records: list[dict] = []
        offset = 0
        for sec in merged:
            sub_texts = (
                _split_by_paragraphs(sec.content, max_tok)
                if _count_tokens(sec.content) > max_tok
                else [sec.content]
            )

            for i, text in enumerate(sub_texts):
                pg_nums = _extract_page_nums(pages, offset, offset + len(text))
                tables = [m.group(0) for m in _TABLE_RE.finditer(text)]
                formulas = _FORMULA_RE.findall(text)
                records.append({
                    "chunk_id": _make_chunk_id(filename, sec.title, len(records)),
                    "text": text,
                    "token_count": _count_tokens(text),
                    "section_title": sec.title,
                    "section_level": sec.level,
                    "page_nums": pg_nums,
                    "tables": [{"md": t} for t in tables],
                    "formulas": formulas,
                })
                offset += len(text)

        _LOG.info("청킹 완료: %d개 섹션 → %d개 청크", len(merged), len(records))
        return records
