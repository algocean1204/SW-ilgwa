"""Marker/MinerU가 내보낸 마크다운 표 형식을 맞춘다.

엔진마다 파이프 정렬과 셀 공백이 조금씩 달라 표준 마크다운 표로 다시 쓴다.
"""
from __future__ import annotations

import re

_TABLE_ROW_RE = re.compile(r"^\|?.+\|.+\|?$")
_SEPARATOR_RE = re.compile(r"^\|?[\s\-:|]+\|[\s\-:|]+\|?$")


def _parse_cells(row: str) -> list[str]:
    """표 행에서 셀 목록을 파싱한다."""
    stripped = row.strip().strip("|")
    return [c.strip() for c in stripped.split("|")]


def _format_row(cells: list[str], widths: list[int]) -> str:
    """셀 목록을 지정 너비에 맞춰 패딩해 표 행 문자열을 생성한다."""
    padded = [cell.ljust(w) for cell, w in zip(cells, widths)]
    return "| " + " | ".join(padded) + " |"


def _normalize_block(block: list[str]) -> list[str]:
    """단일 표 블록을 정규화한다."""
    rows = [row for row in block if not _SEPARATOR_RE.match(row.strip())]
    if not rows:
        return block

    parsed = [_parse_cells(r) for r in rows]
    # 열 수를 최대값으로 통일 (짧은 행에 빈 셀 추가)
    max_cols = max(len(p) for p in parsed)
    normalized: list[list[str]] = []
    for cells in parsed:
        while len(cells) < max_cols:
            cells.append("")
        normalized.append(cells[:max_cols])

    widths = [max(len(row[i]) for row in normalized) for i in range(max_cols)]
    widths = [max(w, 3) for w in widths]  # 구분선 최소 너비 3

    result: list[str] = []
    for i, cells in enumerate(normalized):
        result.append(_format_row(cells, widths))
        if i == 0:
            # 헤더 다음에 구분선 삽입
            separator = "| " + " | ".join("-" * w for w in widths) + " |"
            result.append(separator)
    return result


def normalize_tables(markdown: str) -> str:
    """마크다운 전체에서 표를 찾아 정규화한다."""
    lines = markdown.splitlines()
    result: list[str] = []
    block: list[str] = []

    for line in lines:
        if _TABLE_ROW_RE.match(line.strip()):
            block.append(line)
        else:
            if block:
                result.extend(_normalize_block(block))
                result.append("")
                block = []
            result.append(line)

    if block:
        result.extend(_normalize_block(block))

    return "\n".join(result)
