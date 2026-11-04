"""표 무결성 점수. 행별 열 수, 빈 셀, 구분선을 본다.

OCR이 표를 잘못 읽으면 행마다 열 수가 달라지거나 구분선이 빠진다.
"""
from __future__ import annotations

import re

# 마크다운 표 행. 앞뒤 파이프는 없어도 된다
_TABLE_ROW_RE = re.compile(r"^\|?.+\|.+\|?$")
# 구분선 행 (헤더와 본문 사이)
_SEPARATOR_RE = re.compile(r"^\|?[\s\-:|]+\|[\s\-:|]+\|?$")


def _extract_table_blocks(markdown: str) -> list[list[str]]:
    """마크다운에서 표 블록을 추출한다. 블록 = 연속된 표 행 목록."""
    blocks: list[list[str]] = []
    current: list[str] = []

    for line in markdown.splitlines():
        if _TABLE_ROW_RE.match(line.strip()):
            current.append(line.strip())
        else:
            if current:
                blocks.append(current)
                current = []
    if current:
        blocks.append(current)
    return blocks


def _col_count(row: str) -> int:
    """표 행의 열 수를 계산한다."""
    stripped = row.strip("|")
    return len(stripped.split("|"))


def _score_single_block(block: list[str]) -> float:
    """단일 표 블록의 무결성 점수를 반환한다 (0.0~1.0)."""
    if len(block) < 2:
        # 헤더만 있는 표는 감점하지 않는다
        return 1.0

    # 정규 마크다운 표는 두 번째 행이 구분선이다
    has_separator = _SEPARATOR_RE.match(block[1]) is not None

    # 헤더 행의 열 수를 기준으로 삼는다
    expected_cols = _col_count(block[0])
    if expected_cols == 0:
        return 0.0

    violations = 0
    empty_cells = 0
    total_cells = 0

    for row in block:
        if _SEPARATOR_RE.match(row):
            continue
        cols = row.split("|")
        cols = [c for c in cols if c != ""]  # 앞뒤 빈 요소 제거

        if len(cols) != expected_cols:
            violations += 1

        for cell in cols:
            total_cells += 1
            if not cell.strip():
                empty_cells += 1

    col_consistency = 1.0 - violations / max(len(block), 1)
    empty_ratio = empty_cells / max(total_cells, 1)
    separator_bonus = 0.1 if has_separator else 0.0

    score = col_consistency * 0.6 + (1.0 - empty_ratio) * 0.3 + separator_bonus
    return min(max(score, 0.0), 1.0)


def score_table_integrity(markdown: str, tables: list[dict]) -> tuple[float, str]:
    """표 무결성 점수와 이유를 반환한다.

    markdown 에서 직접 표를 파싱하고 tables 메타데이터와 교차 검증한다.
    반환: (score 0.0~1.0, reason 문자열)
    """
    blocks = _extract_table_blocks(markdown)

    if not blocks and not tables:
        return 1.0, "no_tables"

    if not blocks:
        # 메타데이터에만 표가 있고 마크다운에 없으면 0.7로 깎는다
        return 0.7, "tables_in_meta_not_in_md"

    scores = [_score_single_block(b) for b in blocks]
    avg = sum(scores) / len(scores)
    reason = "ok" if avg >= 0.8 else f"avg={avg:.2f},blocks={len(blocks)}"
    return round(avg, 4), reason
