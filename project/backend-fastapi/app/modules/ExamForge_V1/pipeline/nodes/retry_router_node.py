"""검증 결과에 따라 재시도/통과를 라우팅하는 노드."""
from __future__ import annotations

import time

from app.modules.ExamForge_V1.pipeline.state import ExamForgeState
from app.modules.ExamForge_V1.common.ai_bridge import get_current_budget
from app.modules.ExamForge_V1.common.config import missing_retry_cap, verification_advisory_enabled
from app.modules.ExamForge_V1.common.logger import get_logger
from app.modules.ExamForge_V1.common.verification_status import (
    advisory_filtered_failed_ids,
    has_distractor_validity_hard_fail,
    has_parse_failed_majority,
    verification_counts,
)

logger = get_logger(__name__)


# 개수 부족 재시도가 진전 없이 반복되는 것을 허용하는 최대 라운드 수.
# 이 횟수만큼 missing_count가 줄지 않으면(dedup 비수렴) 유니크 문항만 출고한다.
_COUNT_STUCK_ROUND_CAP = 2


def _is_missing_retry_cap_reached(state: ExamForgeState) -> bool:
    """개수 부족 전용 재시도가 env 캡에 도달했는지 판단한다.

    missing_retry_count >= missing_retry_cap()이면 True다. 기본 캡은 3회다(config.py).
    캡에 닿으면 유효 문항만으로 passed 출고해 타임아웃을 막는다.
    """
    cap = missing_retry_cap()
    current = state.get("missing_retry_count", 0)
    return isinstance(current, int) and current >= cap


async def retry_router_node(state: ExamForgeState) -> dict:
    """실제 재시도가 필요한 경우에만 카운터를 증가시킨다.

    passed/exhausted 경로에서는 카운터를 변경하지 않아
    off-by-one 보고 오류를 막는다.

    개수 부족 비수렴 추적: missing_count가 직전 라운드 대비 줄지 않으면
    count_stuck_rounds를 증가시키고, 줄면 0으로 리셋한다. 이 값을
    route_after_validation의 비수렴 캡이 참조한다.

    missing_retry_count: missing_count > 0 이 원인인 retry 전용 카운터.
    EXAMFORGE_MISSING_RETRY_CAP(기본 3)에 닿으면 추가 재시도 없이 passed 출고한다.
    """
    # 상위 노드에서 에러가 전파된 경우 즉시 반환
    if state.get("pipeline_status") == "error":
        return {}
    node_start = time.time()
    logger.info("노드 시작: retry_router_node")

    # route를 정하기 전에 개수 부족 stuck 카운터를 갱신한다.
    stuck_update = _track_count_progress(state)

    # langgraph는 이 노드가 state를 갱신한 뒤 route_after_validation을 엣지에서 다시
    # 호출한다. 내부 호출도 같은 갱신본을 봐야 retry 카운터 증분이 엣지 라우팅과 맞는다.
    route_state = {**state, **stuck_update}
    route = route_after_validation(route_state)
    if route == "retry":
        # missing_count > 0이 원인이면 전용 카운터를 올린다
        report = state.get("validation_report", {})
        missing_count = report.get("missing_count")
        missing_retry_update: dict = {}
        if isinstance(missing_count, int) and missing_count > 0:
            missing_retry_update = {
                "missing_retry_count": state.get("missing_retry_count", 0) + 1
            }
        # 실제로 재시도할 때만 retry_count를 올린다
        logger.info("노드 완료: retry_router_node (%.2fs) → retry", time.time() - node_start)
        return {
            "retry_count": state.get("retry_count", 0) + 1,
            **stuck_update,
            **missing_retry_update,
        }
    # 통과나 소진이면 카운터를 그대로 둔다
    logger.info("노드 완료: retry_router_node (%.2fs) → %s", time.time() - node_start, route)
    return stuck_update


def _track_count_progress(state: ExamForgeState) -> dict:
    """개수 부족 재시도의 진전 여부를 추적해 count_stuck_rounds를 갱신한다.

    missing_count가 직전보다 줄면 stuck=0(진전), 같거나 늘면 stuck+1(비수렴).
    missing_count가 0이거나 없으면 추적을 리셋한다.
    """
    report = state.get("validation_report", {})
    missing_count = report.get("missing_count")
    if not isinstance(missing_count, int) or missing_count <= 0:
        return {"count_stuck_rounds": 0, "prev_missing_count": 0}

    prev = state.get("prev_missing_count", -1)
    stuck = state.get("count_stuck_rounds", 0)
    if prev >= 0 and missing_count >= prev:
        # missing_count가 줄지 않았으면 비수렴 카운터를 올린다
        stuck += 1
    else:
        # 줄었거나 첫 관측이면 카운터를 리셋한다
        stuck = 0
    return {"count_stuck_rounds": stuck, "prev_missing_count": missing_count}


def _count_retry_non_converging(state: ExamForgeState, missing_count: int) -> bool:
    """개수 부족 재시도가 비수렴 상한에 도달했는지 판단한다.

    count_stuck_rounds가 캡 이상이면 True다. 더 재시도해도 dedup이 같은 중복을
    드롭해 수렴하지 않으므로 유니크 문항만 출고한다.
    """
    stuck = state.get("count_stuck_rounds", 0)
    return isinstance(stuck, int) and stuck >= _COUNT_STUCK_ROUND_CAP


def route_after_validation(state: ExamForgeState) -> str:
    """조건부 엣지 라우팅 함수.

    구조적 실패율 외에 품질 지표 게이트(정확률, 중복 점수)도 함께 확인한다.
    게이트에 못 미치면 재시도로 보내 품질 기준 미달 세트가 통과하지 않게 한다.
    LLM 호출 예산을 넘으면 추가 재시도를 하지 않는다.
    """
    # 파이프라인 에러 상태이면 재시도 없이 즉시 종료 경로로 라우팅한다
    if state.get("pipeline_status") == "error":
        logger.warning("route_after_validation: pipeline_status=error — exhausted 경로로 전달")
        return "exhausted"

    # LLM 예산 서킷 브레이커: 예산을 다 쓰면 무조건 종료 경로로 보낸다
    budget = get_current_budget()
    if budget is not None and budget.exceeded:
        logger.warning("LLM 예산 소진 (%d/%d) — 재시도 차단", budget.count, budget.budget)
        return "exhausted"

    retry_count = state.get("retry_count", 0)
    max_retries = state.get("max_retries", 3)
    error_message = state.get("error_message")
    questions = (
        state.get("calibrated_questions", [])
        or state.get("answered_questions", [])
        or state.get("verified_questions", [])
        or state.get("questions", [])
    )
    total = len(questions)
    verification_advisory = _is_verification_advisory(state)
    failed_ids = advisory_filtered_failed_ids(
        state.get("failed_question_ids", []),
        questions,
        verification_advisory=verification_advisory,
    )
    if verification_advisory and verification_counts(questions)["failed"] > 0:
        logger.warning(
            "route_after_validation: 검증 advisory 모드 — genuine fail은 재시도율에서 제외"
        )

    # 에러 메시지가 있고 문제가 비어 있으면 재시도해도 결과가 같다
    if error_message and total == 0:
        logger.warning("결정적 실패 감지 — 재시도 없이 즉시 종료: %s", error_message)
        return "exhausted"

    # 문제가 하나도 없으면 재생성 또는 종료
    if total == 0:
        _dest = "exhausted" if retry_count >= max_retries else "retry"
        logger.info("route_after_validation: 문제 0건 — %s", _dest)
        return _dest

    # 개수 충족 하드 게이트. validate_node가 보고한 missing_count(개수 부족·슬롯 누락)가
    # 1 이상이면 구조 실패율과 상관없이 retry/exhausted로 보낸다.
    # 개수만 모자란 시험이 passed로 나가지 않게 한다.
    validation_report_early = state.get("validation_report", {})
    missing_count = validation_report_early.get("missing_count")
    if isinstance(missing_count, int) and missing_count > 0:
        # EXAMFORGE_MISSING_RETRY_CAP(기본 3)에 닿으면 추가 재시도 없이 유효 문항으로
        # passed 출고한다. Gemini 응답이 잘리는 경우가 있어 보충 재시도를 3회까지 둔다.
        if _is_missing_retry_cap_reached(state):
            logger.warning(
                "route_after_validation: missing_retry_cap(%d) 도달(missing_count=%d) — "
                "유효 문항만으로 passed 출고(타임아웃 방지)",
                missing_retry_cap(),
                missing_count,
            )
            return "passed"
        # 비수렴 캡. dedup이 매번 같은 중복을 드롭해 missing_count가 줄지 않으면 예산만 쓴다.
        # 진전 없이 상한에 닿으면 확보된 유니크 문항으로 passed 처리한다.
        # total_questions는 format_output/response에서 맞춘다. missing_count가 줄면 계속 재시도한다.
        if _count_retry_non_converging(state, missing_count):
            logger.warning(
                "route_after_validation: 개수 부족 재시도 비수렴(missing_count=%d) — "
                "유니크 문항만 출고(passed)로 종료",
                missing_count,
            )
            return "passed"
        _dest = "exhausted" if retry_count >= max_retries else "retry"
        logger.info(
            "route_after_validation: 개수 부족 missing_count=%d — %s",
            missing_count, _dest,
        )
        return _dest

    # 정답 위치 분포가 계획과 다르면 이것도 재시도 게이트로 본다
    if validation_report_early.get("answer_position_mismatch") is True:
        _dest = "exhausted" if retry_count >= max_retries else "retry"
        logger.info("route_after_validation: 정답 위치 분포 불일치 — %s", _dest)
        return _dest

    # 오답 타당성(기준7) hard-fail 게이트. 동치이거나 참인 오답은 콘텐츠 정확성 결함이라
    # advisory 모드나 낮은 실패율과 상관없이 1건이라도 있으면 retry로 보낸다.
    # 예산 소진과 최대 재시도는 위에서 이미 exhausted로 처리했다.
    if has_distractor_validity_hard_fail(questions):
        _dest = "exhausted" if retry_count >= max_retries else "retry"
        logger.warning(
            "route_after_validation: 오답 타당성 hard-fail 감지(advisory 무시) — %s", _dest,
        )
        return _dest

    # 구조적 실패율로 판단한다
    fail_rate = len(failed_ids) / total
    if fail_rate >= 0.1:
        _dest = "exhausted" if retry_count >= max_retries else "retry"
        logger.info("route_after_validation: 실패율 %.1f%% (>= 10%%) — %s", fail_rate * 100, _dest)
        return _dest

    # 품질 지표 게이트: 검증 보고서를 확인한다
    validation_report = state.get("validation_report", {})
    parse_failed_advisory = verification_advisory or (
        has_parse_failed_majority(questions)
        and verification_counts(questions)["failed"] == 0
        and not failed_ids
    )

    # validate_node 이후에는 품질 게이트를 적용하되, 테스트 더블/중간 상태처럼
    # validation_report 자체가 없으면 구조적 실패율만으로 판단한다.
    accuracy_rate = validation_report.get("answer_accuracy_rate")
    if isinstance(accuracy_rate, (int, float)) and accuracy_rate < 0.95 and not parse_failed_advisory:
        _dest = "exhausted" if retry_count >= max_retries else "retry"
        logger.info("route_after_validation: 정확률 %.2f (< 0.95) — %s", accuracy_rate, _dest)
        return _dest

    dedup_score = validation_report.get("dedup_score")
    if isinstance(dedup_score, (int, float)):
        # 블루프린트를 쓰면 concept_key가 미리 유일해 중복이 없어야 하므로 게이트를 1.0으로 둔다.
        # 블루프린트가 없으면(answer_position_plan 없음) 0.8을 쓴다.
        questions = (
            state.get("calibrated_questions", [])
            or state.get("answered_questions", [])
            or state.get("verified_questions", [])
            or state.get("questions", [])
        )
        plan = state.get("exam_plan", {})
        has_blueprint = bool(plan.get("answer_position_plan"))
        dedup_threshold = 1.0 if has_blueprint else 0.8
        if dedup_score < dedup_threshold:
            _dest = "exhausted" if retry_count >= max_retries else "retry"
            logger.info(
                "route_after_validation: 중복점수 %.2f (< %.1f, blueprint=%s) — %s",
                dedup_score, dedup_threshold, has_blueprint, _dest,
            )
            return _dest

    logger.info(
        "route_after_validation: 모든 게이트 통과 — passed (정확률=%s, 중복=%s, 실패율=%.1f%%)",
        accuracy_rate, dedup_score, fail_rate * 100,
    )
    return "passed"


def _is_verification_advisory(state: ExamForgeState) -> bool:
    """상태 플래그나 env 설정으로 advisory 모드인지 확인한다."""
    return state.get("verification_advisory") is True or verification_advisory_enabled()
