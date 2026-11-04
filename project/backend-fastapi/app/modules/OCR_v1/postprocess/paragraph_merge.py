"""페이지 경계에서 나뉜 단락을 다시 잇는다.

OCR이 페이지를 따로 처리하면 한 문단이 두 페이지로 나뉜다.
헤딩, 목록, 표, 코드, 수식 블록은 그대로 두고 일반 텍스트만 잇는다.
"""
from __future__ import annotations

import re

# 병합에서 제외해야 하는 특수 블록 시작 패턴
_HEADING_RE = re.compile(r"^#{1,4}\s")
_LIST_RE = re.compile(r"^\s*[-*+]\s|^\s*\d+\.\s")
_TABLE_RE = re.compile(r"^\|")
_CODE_FENCE_RE = re.compile(r"^```")
_FORMULA_BLOCK_RE = re.compile(r"^\$\$")


def _is_special_line(line: str) -> bool:
    """특수 블록 시작 줄인지 판별한다."""
    stripped = line.strip()
    return bool(
        _HEADING_RE.match(stripped)
        or _LIST_RE.match(stripped)
        or _TABLE_RE.match(stripped)
        or _CODE_FENCE_RE.match(stripped)
        or _FORMULA_BLOCK_RE.match(stripped)
        or not stripped  # 빈 줄은 단락 구분자로 유지
    )


def merge_paragraphs(markdown: str) -> str:
    """페이지 경계에서 분리된 일반 텍스트 단락을 병합한다.

    . ? ! : ; 로 끝나지 않은 줄 뒤의 일반 텍스트는 같은 단락으로 보고
    줄바꿈을 공백으로 바꾼다.
    """
    lines = markdown.splitlines()
    result: list[str] = []
    in_code = False

    for i, line in enumerate(lines):
        # 코드 블록 안은 건드리지 않는다
        if _CODE_FENCE_RE.match(line.strip()):
            in_code = not in_code

        if in_code or _is_special_line(line):
            result.append(line)
            continue

        # 앞 줄이 문장 끝이 아닌 일반 텍스트면 이어 붙인다
        if (
            result
            and result[-1].strip()
            and not _is_special_line(result[-1])
            and not result[-1].rstrip().endswith((".", "?", "!", ":", ";"))
        ):
            result[-1] = result[-1].rstrip() + " " + line.lstrip()
        else:
            result.append(line)

    return "\n".join(result)
