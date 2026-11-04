"""페이지마다 반복되는 헤더·푸터와 페이지 번호를 지운다.

교재의 페이지 번호, 챕터 제목, 저작권 문구가 여러 페이지에 반복되면
청킹할 때 잡음이 된다.
"""
from __future__ import annotations

import re

# 숫자만 있는 줄이나 - 1 - 형식의 페이지 번호
_PAGE_NUM_RE = re.compile(r"^\s*[-–—]?\s*\d+\s*[-–—]?\s*$", re.MULTILINE)
# 80자 이하 줄만 헤더/푸터 후보로 본다
_SHORT_LINE_RE = re.compile(r"^.{1,80}$")

# 동일 줄이 이 횟수 이상 반복되면 헤더/푸터로 간주
_REPEAT_THRESHOLD = 2


def _find_repeated_lines(text: str) -> set[str]:
    """2회 이상 등장하는 짧은 줄을 헤더/푸터 후보로 반환한다."""
    lines = text.splitlines()
    counts: dict[str, int] = {}
    for line in lines:
        stripped = line.strip()
        if stripped and _SHORT_LINE_RE.match(stripped) and len(stripped) < 80:
            counts[stripped] = counts.get(stripped, 0) + 1
    return {line for line, cnt in counts.items() if cnt >= _REPEAT_THRESHOLD}


def remove_headers_footers(markdown: str) -> str:
    """마크다운에서 헤더·푸터와 페이지 번호를 지운다."""
    result = _PAGE_NUM_RE.sub("", markdown)

    repeated = _find_repeated_lines(result)
    if repeated:
        lines = result.splitlines()
        cleaned = [
            line for line in lines
            if line.strip() not in repeated
        ]
        result = "\n".join(cleaned)

    # 3개 이상 연속 빈 줄을 2개로 압축
    result = re.sub(r"\n{3,}", "\n\n", result)
    return result
