"""네 품질 지표를 가중 평균해 페이지 점수를 낸다.

가중치는 문자 오염 30%, 읽기 순서 25%, 표 25%, 수식 20%다.
한국어 학술문서 OCR에서 문자 오염 피해가 가장 커서 비중을 가장 크게 뒀다.
"""
from __future__ import annotations

from .. import config as cfg

# 가중치 합계는 1.0이다.
_W_CHAR = 0.30
_W_READ = 0.25
_W_TABLE = 0.25
_W_FORMULA = 0.20


def aggregate_page_quality(
    char: float,
    reading: float,
    table: float,
    formula: float,
) -> tuple[float, bool]:
    """네 품질 지표를 가중 평균해 최종 점수와 통과 여부를 반환한다.

    char 은 오염률(낮을수록 좋음)이므로 1.0 에서 뺀 값을 사용한다.
    나머지 세 지표는 높을수록 좋다.

    반환: (overall_score 0.0~1.0, passed: bool)
    """
    char_quality = 1.0 - char

    overall = (
        char_quality * _W_CHAR
        + reading * _W_READ
        + table * _W_TABLE
        + formula * _W_FORMULA
    )
    overall = min(max(overall, 0.0), 1.0)
    passed = overall >= cfg.quality_pass_threshold()
    return round(overall, 4), passed
