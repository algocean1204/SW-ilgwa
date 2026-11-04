"""페이지 품질 검사. 분류·점수 집계 함수를 내보낸다."""
from __future__ import annotations

from .aggregate import aggregate_page_quality
from .classifier import classify_pages

__all__ = ["aggregate_page_quality", "classify_pages"]
