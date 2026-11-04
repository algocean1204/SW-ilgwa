"""마크다운 후처리 함수와 전체 실행 run_all.

각 함수는 순수 함수라 따로 써도 된다.
"""
from __future__ import annotations

from .formula_convert import extract_latex_blocks
from .header_footer import remove_headers_footers
from .hyphen_restore import restore_hyphens
from .paragraph_merge import merge_paragraphs
from .table_convert import normalize_tables

__all__ = [
    "remove_headers_footers",
    "restore_hyphens",
    "merge_paragraphs",
    "normalize_tables",
    "extract_latex_blocks",
    "run_all",
]


async def run_all(markdown: str) -> str:
    """헤더·푸터 제거 → 하이픈 복원 → 문단 병합 → 표 → 수식 순으로 정리한다.

    단계는 모두 동기 함수다. LangGraph 노드에서 await로 부르려고 async로 둔다.
    """
    result = remove_headers_footers(markdown)
    result = restore_hyphens(result)
    result = merge_paragraphs(result)
    result = normalize_tables(result)
    result = extract_latex_blocks(result)
    return result
