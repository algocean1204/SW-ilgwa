"""ASR 폴백 그래프의 LangGraph 노드 함수.

각 함수는 ASRState 를 받아 바꿀 필드만 담은 dict 를 돌려준다.
asr_attempt_node 와 quality_check_node 는 async, 나머지는 sync 이다.
"""
from __future__ import annotations

import logging
import time
from typing import cast

from ai_connectors.errors import AIConnectorError

from .config import quality_pass_threshold
from .quality import aggregate_quality
from .state import ASRState, AttemptRecord

_LOG = logging.getLogger(__name__)


async def asr_attempt_node(state: ASRState) -> dict:
    """현재 tier 의 ASR 커넥터를 호출해 전사를 시도한다.

    성공하면 AttemptRecord 에 결과를 담고, 실패하면 quality_score=0.0 / text="" 로 남긴다.
    예외는 모두 잡아서 quality_reason 에 "error:{msg}" 로 적는다.
    """
    tier = state["current_tier"]
    factory = state["tier_factories"][tier]
    model_name = state["tier_model_names"][tier]

    t0 = time.perf_counter()
    try:
        connector = factory()
        response = await connector.generate(state["request"])
        latency_ms = (time.perf_counter() - t0) * 1000.0
        _LOG.info(
            "[tier %d] %s → \"%s...\" (%.0f ms)",
            tier,
            model_name,
            response.text[:60],
            latency_ms,
        )
        record: AttemptRecord = {
            "tier": tier,
            "model_name": model_name,
            "text": response.text,
            "latency_ms": latency_ms,
            "quality_score": 0.0,      # quality_check_node 가 채운다
            "quality_reason": "pending",
            "response": response,
        }
    except (AIConnectorError, OSError, ValueError, RuntimeError) as exc:
        latency_ms = (time.perf_counter() - t0) * 1000.0
        _LOG.warning("[tier %d] %s 실패: %s", tier, model_name, exc)
        record = {
            "tier": tier,
            "model_name": model_name,
            "text": "",
            "latency_ms": latency_ms,
            "quality_score": 0.0,
            "quality_reason": f"error:{exc}",
            "response": None,
        }

    return {"attempts": state["attempts"] + [record]}


def quality_check_node(state: ASRState) -> dict:
    """마지막 attempt 의 품질을 평가해 quality_score / quality_reason 을 갱신한다.

    에러로 response 가 None 이면 점수는 0.0 그대로 둔다.
    성공했으면 aggregate_quality 로 점수를 매긴다.
    """
    attempts = list(state["attempts"])
    # AttemptRecord 복사본에 품질 필드를 채운다
    last: AttemptRecord = cast(AttemptRecord, dict(attempts[-1]))

    if last["response"] is not None:
        score, reason = aggregate_quality(
            last["text"],
            last["response"].duration_sec,
        )
        last["quality_score"] = score
        last["quality_reason"] = reason

    attempts[-1] = last
    return {"attempts": attempts}


def route_next_tier(state: ASRState) -> str:
    """품질 점수로 다음 경로를 정한다.

    반환값은 graph.py 의 add_conditional_edges 라우트 키와 같아야 한다.
      "done"      → 품질 통과, END 로 전환
      "exhausted" → tier 소진, best-effort 로 END 전환
      "retry"     → 다음 tier 로 재시도
    """
    last = state["attempts"][-1]
    score = last["quality_score"]
    threshold = quality_pass_threshold()

    if score >= threshold:
        _LOG.info("품질 통과 (tier %d, score=%.2f) — 종료", last["tier"], score)
        return "done"

    next_tier = state["current_tier"] + 1
    if next_tier >= len(state["tier_factories"]):
        _LOG.warning("모든 tier 소진 — best-effort 반환")
        return "exhausted"

    _LOG.info(
        "품질 미달 (score=%.2f, reason=%s) — tier %d 로 재시도",
        score,
        last["quality_reason"],
        next_tier,
    )
    return "retry"


def advance_tier_node(state: ASRState) -> dict:
    """current_tier 를 1 증가시켜 다음 tier 로 이동시킨다."""
    return {"current_tier": state["current_tier"] + 1}
