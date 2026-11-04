"""LangGraph 품질 게이트 노드와 그 뒤 분기 라우터.

페이지마다 품질 지표 네 가지를 계산하고 종합 점수로 통과 여부를 정한다.
"""
from __future__ import annotations

import logging
import time

from ...quality.aggregate import aggregate_page_quality
from ...quality.char_corruption import score_char_corruption
from ...quality.formula_check import score_formula_preservation
from ...quality.reading_order import score_reading_order
from ...quality.table_integrity import score_table_integrity
from ...schemas.state import OCRPipelineState, PageExtraction, QualityScore

_LOG = logging.getLogger(__name__)


def _score_page(page: PageExtraction) -> QualityScore:
    """한 페이지의 품질 점수를 계산한다. 지표 함수가 주는 이유 문자열은 버린다."""
    markdown: str = page.get("markdown", "")
    tables: list = page.get("tables", [])
    formulas: list = page.get("formulas", [])

    char, _ = score_char_corruption(markdown)
    reading, _ = score_reading_order(markdown)
    table, _ = score_table_integrity(markdown, tables)
    formula, _ = score_formula_preservation(markdown, formulas)

    overall, passed = aggregate_page_quality(char, reading, table, formula)

    return QualityScore(
        page_num=page.get("page_num", 0),
        char_corruption=char,
        reading_order=reading,
        table_integrity=table,
        formula_preservation=formula,
        overall=overall,
        passed=passed,
    )


async def quality_gate(state: OCRPipelineState) -> dict:
    """전 페이지 품질을 평가해 통과·실패 페이지 목록을 나눈다.

    평가는 가벼운 CPU 연산이라 스레드 없이 바로 돈다.
    한 페이지 평가가 예외를 내면 그 페이지는 0점 실패로 친다.
    """
    # 앞 단계가 실패했으면 아무것도 하지 않는다
    if state.get("pipeline_status") == "error":
        return {}
    t0 = time.perf_counter()
    extractions: list = state.get("extractions", [])
    _LOG.info("Stage 4: 품질 게이트 평가 시작 — %d 페이지", len(extractions))

    # 추출 결과가 0건이면 평가할 것이 없다
    if not extractions:
        _LOG.warning("Stage 4: 추출 결과 0건 — 품질 평가 건너뜀")
        elapsed = round(time.perf_counter() - t0, 3)
        return {
            "quality_scores": [],
            "passed_pages": [],
            "failed_pages": [],
            "current_stage": "quality_gate",
            "pipeline_status": "partial",
            "error_message": "추출 결과 0건 — 품질 평가 입력 없음",
            "timings": {**state.get("timings", {}), "quality_gate": elapsed},
        }

    scores: list[QualityScore] = []
    for page in extractions:
        try:
            scores.append(_score_page(page))
        except Exception as exc:
            _LOG.warning(
                "페이지 %d 품질 평가 실패 — 기본 실패 점수 사용: %s",
                page.get("page_num", 0), exc,
            )
            scores.append(QualityScore(
                page_num=page.get("page_num", 0),
                char_corruption=1.0,
                reading_order=0.0,
                table_integrity=0.0,
                formula_preservation=0.0,
                overall=0.0,
                passed=False,
            ))

    passed_pages = [s.get("page_num", 0) for s in scores if s.get("passed", False)]
    failed_pages = [s.get("page_num", 0) for s in scores if not s.get("passed", False)]

    elapsed = round(time.perf_counter() - t0, 3)
    _LOG.info(
        "Stage 4 완료: 통과=%d, 실패=%d, %.3fs 소요",
        len(passed_pages),
        len(failed_pages),
        elapsed,
    )

    return {
        "quality_scores": scores,
        "passed_pages": passed_pages,
        "failed_pages": failed_pages,
        "current_stage": "quality_gate",
        "timings": {**state.get("timings", {}), "quality_gate": elapsed},
    }


def route_after_quality(state: OCRPipelineState) -> str:
    """품질 게이트 결과로 다음 노드를 고른다.

    - "pass": 전 페이지 통과. postprocess 로 간다
    - "partial": 일부 실패. fallback 에서 실패 페이지만 MinerU 재추출 후 postprocess
    - "fail_all": 전 페이지 실패(스캔본 등). 이것도 fallback 을 거친다.
      앞 단계 오류나 추출 0건도 fail_all 이지만 fallback 은 이때 그냥 통과한다
    """
    # 앞 단계 오류면 정상 분기를 따지지 않는다
    if state.get("pipeline_status") == "error":
        _LOG.info("품질 라우팅: fail_all (pipeline_status=error)")
        return "fail_all"

    passed = state.get("passed_pages", [])
    failed = state.get("failed_pages", [])

    # 추출 결과가 없으면 통과한 페이지도 없으니 전체 실패로 본다
    if not passed and not failed:
        _LOG.info("품질 라우팅: fail_all (추출 결과 없음)")
        return "fail_all"

    if not failed:
        _LOG.info("품질 라우팅: pass (전 페이지 통과)")
        return "pass"

    if not passed:
        _LOG.info("품질 라우팅: fail_all (전 페이지 실패)")
        return "fail_all"

    _LOG.info("품질 라우팅: partial (통과=%d, 실패=%d)", len(passed), len(failed))
    return "partial"
