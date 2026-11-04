"""ExamForge_V1 비동기 모의고사 생성 라우터.

Spring이 POST /api/exam-forge/mock/generate-async 를 호출하면
202를 즉시 반환하고, BackgroundTask로 ExamForge 파이프라인을 실행한다.

생성 완료 시 Spring /internal/exam-attempts/{attempt_id}/generated 콜백을 전송한다.
실패 시   Spring /internal/exam-attempts/{attempt_id}/generation-failed 콜백을 전송한다.

- /api/exam-forge/generate와 같은 파이프라인 로직을 쓴다.
- AssignmentGrader_V1을 import하지 않고 ExamForge_V1 콜백 모듈만 쓴다.
- question_types는 요청 그대로 넘기며 객관식으로 고정하지 않는다.
"""
from __future__ import annotations

import asyncio
import logging
import time
import uuid

from fastapi import APIRouter, BackgroundTasks
from pydantic import BaseModel, Field

from app.modules.ExamForge_V1.common.ai_bridge import (
    LLMBudgetCounter,
    _current_budget,
    set_current_budget,
)
from app.modules.ExamForge_V1.common.config import (
    active_text_model,
    max_retries,
    pipeline_llm_budget_for,
    pipeline_llm_reserve_for,
    pipeline_timeout_sec,
)
from app.modules.ExamForge_V1.db.mockexam_persistence import (
    build_source_text as build_db_source_text,
)
from app.modules.ExamForge_V1.grading.seal import attach_answer_key_seal
from app.modules.ExamForge_V1.mock_generation_callback import (
    MockGenerationCallbackError,
    send_generation_failed_callback,
    send_generation_success_callback,
)
from app.modules.ExamForge_V1.schemas.request import ExamConfig, ExamForgeRequest

_LOG = logging.getLogger(__name__)

# source_text가 100자보다 짧을 때 채우는 패딩의 최소 길이
_SOURCE_MIN_LEN = 100
# course 기반 시험의 grounded source hard-gate 최소 글자수.
# 이 길이 미만이면 "강의 기반"이라 부르기 어려운 일반론 출제가 되므로,
# 생성은 계속하되 grounding_degraded로 기록한다.
# _SOURCE_MIN_LEN(파이프라인 입력 최소)과 같은 기준이다.
_GROUNDING_HARD_GATE_LEN = 100
# ExamForgeRequest.source_text 상한(50000자). DB 본문을 모을 때 이 길이로 자른다.
_SOURCE_MAX_LEN = 50_000
# Spring 요청 문항 수 하한. 모의고사는 최소 20문항이다(plan_exam_node와 같은 기준).
_MIN_QUESTION_COUNT = 20

# Spring difficulty(easy/medium/hard) → ExamForge 난이도 분포(1~5, 합 1.0)
# spring_adapter와 같은 분포를 써서 두 경로의 난이도를 맞춘다.
_DIFFICULTY_DISTRIBUTIONS: dict[str, dict[int, float]] = {
    "easy": {1: 0.3, 2: 0.35, 3: 0.25, 4: 0.1},
    "medium": {2: 0.2, 3: 0.35, 4: 0.3, 5: 0.15},
    "hard": {2: 0.1, 3: 0.3, 4: 0.35, 5: 0.25},
}

router = APIRouter(prefix="/api/exam-forge/mock", tags=["exam-forge-mock-async"])


class MockGenerateAsyncRequest(BaseModel):
    """Spring이 비동기 모의고사 생성 트리거 시 보내는 바디."""

    attempt_id: str = Field(min_length=1, max_length=120)
    course_id: str = Field(min_length=1, max_length=120)
    subject: str = Field(min_length=1, max_length=200)
    topic: str | None = Field(default=None, max_length=500)
    question_count: int = Field(default=20, ge=1, le=100)
    # Spring이 보낸 템플릿 ID 목록을 그대로 넘긴다.
    # 빈 목록이면 plan_exam_node가 기본 혼합 유형으로 나눈다(최종 모의고사).
    question_types: list[str] = Field(default_factory=list)
    # Spring difficulty(easy/medium/hard). 난이도 분포로 바꿔 계획·프롬프트에 넘긴다.
    difficulty: str = Field(default="medium", max_length=20)
    pass_percentage: float = Field(default=60.0, ge=0.0, le=100.0)


@router.post("/generate-async", status_code=202)
async def generate_mock_exam_async(
    req: MockGenerateAsyncRequest,
    background_tasks: BackgroundTasks,
) -> dict[str, object]:
    """비동기 모의고사 생성을 시작하고 바로 202를 돌려준다.

    생성 작업은 BackgroundTask로 실행되며 결과는 Spring 콜백으로 전달된다.
    인증은 상위 미들웨어(X-API-Key)가 처리한다.
    """
    background_tasks.add_task(_run_async_generation, req)
    return {"accepted": True, "attempt_id": req.attempt_id}


async def _run_async_generation(req: MockGenerateAsyncRequest) -> None:
    """ExamForge 파이프라인을 실행하고 Spring에 콜백을 전송한다.

    성공: send_generation_success_callback 호출
    실패: send_generation_failed_callback 호출(예외를 밖으로 올리지 않는다)
    """
    try:
        response, seal, grounding_degraded = await _execute_exam_forge_pipeline(req)

        # 생성 문항을 Question dict로 바꾼다.
        questions = [q.model_dump() for q in response.questions]

        # pipeline_outcome이 failed_*(failed_no_valid_questions·failed_quality_gate·
        # failed_html_generation 등)이면 유효 문항이 있어도 실패 콜백을 보낸다.
        # 성공 콜백은 passed, passed_partial, exhausted(유효분 포함),
        # needs_more_source(자료가 부족해도 확보한 문항은 출고)일 때만 보낸다.
        outcome = response.pipeline_outcome or ""
        if outcome.startswith("failed_"):
            reason = f"파이프라인 품질 기준 미달(outcome={outcome}) — Spring 출고 불가"
            _LOG.warning(
                "[MockAsyncGen] 실패 출고 차단 — attemptId=%s, outcome=%s",
                req.attempt_id,
                outcome,
            )
            await _safe_send_failed_callback(req.attempt_id, reason)
            return

        # Spring이 "요청 N / 생성 M"과 부분·자료 부족 안내에 쓰는 문항 수.
        # 요청 수는 사용자 요청(최소 20으로 올린 값), 생성 수는 실제 출고 문항 수다.
        requested_question_count = max(_MIN_QUESTION_COUNT, req.question_count)
        actual_question_count = len(questions)

        if not questions:
            reason = "생성된 문항이 없음 — 파이프라인 결과 0문항"
            _LOG.error("[MockAsyncGen] 빈 결과 — attemptId=%s", req.attempt_id)
            await _safe_send_failed_callback(req.attempt_id, reason)
            return

        # seal은 _execute_exam_forge_pipeline이 final_state에서 바로 꺼낸다.
        # response.answer_key_seal(str|None)을 거치면 빈 값이 될 수 있어서다.
        if not seal:
            reason = "answer_key_seal이 비어 있음 — 콜백 전송 불가"
            _LOG.error("[MockAsyncGen] seal 없음 — attemptId=%s", req.attempt_id)
            await _safe_send_failed_callback(req.attempt_id, reason)
            return

        if grounding_degraded:
            # 출고는 하되 degraded임을 기록한다.
            _LOG.warning(
                "[MockAsyncGen] grounding degraded 상태로 출고 — '강의 기반' 아닌 일반론 시험. "
                "attemptId=%s, courseId=%s",
                req.attempt_id,
                req.course_id,
            )

        if outcome == "needs_more_source":
            # 자료 부족. 실패는 아니지만 요청보다 적게 만들었음을 남긴다.
            _LOG.warning(
                "[MockAsyncGen] 자료 부족 출고(needs_more_source) — 요청 %d / 생성 %d, "
                "attemptId=%s, courseId=%s",
                requested_question_count,
                actual_question_count,
                req.attempt_id,
                req.course_id,
            )

        await _safe_send_success_callback(
            attempt_id=req.attempt_id,
            exam_id=response.exam_id,
            answer_key_seal=seal,
            questions=questions,
            grounding_degraded=grounding_degraded,
            requested_question_count=requested_question_count,
            actual_question_count=actual_question_count,
            generation_outcome=outcome,
        )

    except Exception as exc:
        _LOG.error(
            "[MockAsyncGen] 생성 실패 — attemptId=%s, error=%s",
            req.attempt_id,
            exc,
            exc_info=True,
        )
        await _safe_send_failed_callback(req.attempt_id, str(exc)[:500])


async def _execute_exam_forge_pipeline(
    req: MockGenerateAsyncRequest,
) -> "tuple[ExamForgeResponse, str, bool]":  # (응답, answer_key_seal, grounding_degraded)
    """ExamForge 파이프라인을 실행하고 (응답, seal, grounding_degraded) 튜플을 반환한다.

    /api/exam-forge/generate 라우터와 같은 예산·타임아웃 로직을 쓴다.
    question_types는 요청 그대로 넘겨 여러 유형을 섞어 출제한다.

    seal은 ExamForgeResponse.answer_key_seal(str|None)을 거치면 비어 버릴 수 있어
    final_state에서 바로 꺼낸다.

    grounding_degraded는 course 본문 grounding 실패로 스텁 폴백했는지 여부다.
    initial_state에 실어 파이프라인을 통과시키고 final_state에서 다시 읽어,
    스텁 폴백 시에도 상위(콜백)가 degraded 임을 추적할 수 있게 한다.
    """
    from app.modules.ExamForge_V1.common.ai_bridge import (
        active_text_provider_chain,
        text_providers_all_exhausted,
    )
    from app.modules.ExamForge_V1.common.errors import BudgetExceededError
    from app.modules.ExamForge_V1.pipeline.graph import get_compiled_graph
    from app.modules.ExamForge_V1.schemas.response import ExamForgeResponse

    # 사전 점검: 활성 프로바이더 체인이 전부 소진이면 파이프라인을 시작하지 않는다.
    # LLM 호출 수십 회가 모두 400/429로 실패할 것이 뻔해서다. 근거는 이전 생성에서 기록한
    # 영구 소진이며 따로 확인 호출은 하지 않는다. 하나라도 살아 있거나 체인을 모르면 막지 않는다.
    if text_providers_all_exhausted():
        chain = ", ".join(active_text_provider_chain()) or "(unknown)"
        _LOG.warning(
            "[MockAsyncGen] 사전점검 차단 — 활성 텍스트 프로바이더 전부 소진(%s), "
            "attemptId=%s. 생성을 시작하지 않는다.",
            chain,
            req.attempt_id,
        )
        raise RuntimeError(
            f"활성 AI 프로바이더 전부 쿼터/사용한도 소진({chain}) — "
            "복구 시각 이후 다시 시도하라. 생성을 시작하지 않고 사전 차단함."
        )

    total_questions = max(_MIN_QUESTION_COUNT, req.question_count)

    # 문항 비례 LLM 예산과 해설 예약분을 잡는다.
    # max_calls는 주지 않아 LLMBudgetCounter가 예산 풀의 _RUNAWAY_FACTOR배로 정한다.
    # 고정 60을 주면 20문항 1패스(약 81회)도 끊겨 끝 문항 해설·검증이 빠졌다.
    budget = LLMBudgetCounter(
        budget=pipeline_llm_budget_for(total_questions),
        reserve=pipeline_llm_reserve_for(total_questions),
    )
    budget_token = set_current_budget(budget)

    start_time = time.time()
    exam_id = f"exam_{uuid.uuid4().hex[:12]}"

    # 출처 그라운딩: course의 chapter·slide 본문을 DB에서 조립해 출제 근거로 쓴다.
    # DB 조회 실패나 본문 부족(hard-gate 미달)일 때만 subject/topic 스텁을 쓰고
    # grounding_degraded=True로 기록한다. 생성은 막지 않는다.
    source_text, grounding_degraded = await _load_grounded_source_text(req)

    # Spring difficulty를 난이도 분포로 바꾼다. 없거나 잘못된 값은 medium으로 본다.
    difficulty_distribution = _DIFFICULTY_DISTRIBUTIONS.get(
        (req.difficulty or "medium").lower(), _DIFFICULTY_DISTRIBUTIONS["medium"]
    )

    initial_state = {
        "source_text": source_text,
        "subject": req.subject,
        "exam_config": ExamConfig(
            total_questions=total_questions,
            locale="ko",
            category="korean",
            # question_types를 그대로 넘긴다. 빈 목록이면 기본 혼합 유형으로 나눈다.
            question_types=req.question_types,
            difficulty_distribution=dict(difficulty_distribution),
            passing_score=req.pass_percentage,
        ).model_dump(),
        "locale": "ko",
        "category": "korean",
        "retry_count": 0,
        "max_retries": max_retries(),
        "pipeline_status": "parsing",
        "error_message": None,
        "exam_id": exam_id,
        "timings": {"start": start_time},
        "llm_call_count": 0,
        "llm_budget_exceeded": False,
        # grounding 실패(스텁 사용) 플래그. final_state까지 그대로 간다.
        "grounding_degraded": grounding_degraded,
    }

    graph = get_compiled_graph()

    final_state: dict | None = None
    try:
        final_state = await asyncio.wait_for(
            graph.ainvoke(initial_state),
            timeout=float(pipeline_timeout_sec()),
        )
    except (BudgetExceededError, asyncio.TimeoutError, RuntimeError, ValueError) as exc:
        raise RuntimeError(f"파이프라인 실행 실패: {exc}") from exc
    finally:
        # 성공·예외(예산 초과·타임아웃) 모두에서 생성당 비용을 로그로 남긴다.
        # budget.count는 파이프라인 전체의 실제 LLM 호출 횟수다.
        _log_mock_cost(req.attempt_id, budget, final_state)
        # 다음 요청에 남지 않게 예산 컨텍스트 변수를 되돌린다.
        _current_budget.reset(budget_token)

    # attach_answer_key_seal은 answer_key_seal을 넣은 state 사본을 돌려준다.
    # 그 seal을 ExamForgeResponse를 거치지 않고 콜백에 넘기려고 튜플로 돌려준다.
    sealed_state = attach_answer_key_seal(final_state)
    seal: str = str(sealed_state.get("answer_key_seal") or "")
    # grounding_degraded는 final_state에서 다시 읽고, 키가 없으면 시작 때 값을 쓴다.
    # 콜백까지 넘겨 상위가 "강의 기반 아님"을 알게 한다.
    degraded = bool(sealed_state.get("grounding_degraded", grounding_degraded))
    return ExamForgeResponse.from_state(sealed_state), seal, degraded


def _log_mock_cost(
    attempt_id: str,
    budget: "LLMBudgetCounter",
    final_state: dict | None,
) -> None:
    """생성 종료 시 시험당 LLM 비용 텔레메트리를 구조화 INFO 로그로 남긴다.

    LLMBudgetCounter의 누적 카운트(실제 LLM 호출 횟수)를 비용 프록시로 사용한다.
    프로바이더는 활성 텍스트 모델명, 재시도는 final_state의 retry_count(없으면 미상=-1),
    문항은 확보된 calibrated_questions 수로 보고한다. 로그 때문에 결과가 바뀌지 않게
    모든 예외를 삼킨다.
    """
    try:
        provider = active_text_model()
    except Exception:  # noqa: BLE001 - 텔레메트리 때문에 본 흐름이 멈추면 안 된다
        provider = "unknown"
    state = final_state or {}
    retry_count = state.get("retry_count", -1) if isinstance(state, dict) else -1
    questions = state.get("calibrated_questions", []) if isinstance(state, dict) else []
    question_count = len(questions) if isinstance(questions, list) else 0
    _LOG.info(
        "[MockCost] attemptId=%s LLM호출=%d, 프로바이더=%s, 재시도=%s, 문항=%d "
        "(예산상한=%d, 하드상한=%d)",
        attempt_id,
        budget.count,
        provider,
        retry_count,
        question_count,
        budget.budget,
        budget.max_calls,
    )


async def _load_grounded_source_text(
    req: MockGenerateAsyncRequest,
) -> "tuple[str, bool]":  # (source_text, grounding_degraded)
    """course의 chapter(+slide 본문)를 DB에서 조립해 출제 근거 텍스트를 만든다.

    문항·정답·해설이 실제 학습 자료에 근거하게 하는 진입점이다.
    spring_adapter와 같은 db.mockexam_persistence.build_source_text를 써서 두 경로를 맞춘다.

    반환값은 (source_text, grounding_degraded) 튜플이다.
    - grounding_degraded=False: course 본문으로 충분히 그라운딩됨(정상 "강의 기반" 시험).
    - grounding_degraded=True: DB 미설정·조회 실패·본문 부재(hard-gate 미달)로
      subject/topic 스텁을 썼다. 사용자는 강의 기반이라 여기지만 실제로는 일반론 출제다.
      생성은 막지 않고 이 플래그로 상위가 알게 한다.

    course 기반 시험은 최소 글자수(_GROUNDING_HARD_GATE_LEN) hard-gate를 둔다.
    미달이면 WARNING을 남기고 degraded=True로 표시한다.
    """
    grounded = ""
    try:
        # common.db가 없는 환경(단위 테스트 등)에서도 라우터 import가 되게 안에서 import한다.
        from common.db import get_connection

        async with get_connection() as conn:
            grounded = await build_db_source_text(conn, req.course_id, req.topic)
    except Exception as exc:
        _LOG.warning(
            "[MockAsyncGen] 출처 본문 DB 조회 실패 — 스텁 폴백, courseId=%s, error=%s",
            req.course_id,
            exc,
        )
    grounded = (grounded or "").strip()
    # 최소 글자수를 넘을 때만 강의 기반 시험으로 본다.
    if len(grounded) >= _GROUNDING_HARD_GATE_LEN:
        # 과목/주제 헤더를 앞에 붙여 파이프라인 주제 추출의 방향을 잡아준다
        header = f"과목: {req.subject}."
        if req.topic:
            header = f"과목: {req.subject}. 주제: {req.topic}."
        return f"{header}\n{grounded}"[:_SOURCE_MAX_LEN], False
    # hard-gate 미달이면 스텁을 쓰고, 강의 기반이 아닌 일반론 출제라고 경고한다.
    _LOG.warning(
        "[MockAsyncGen] grounding degraded — course 본문 부족(%d자 < %d자 hard-gate), "
        "subject/topic 스텁으로 폴백한다. 일반론 출제가 되므로 '강의 기반' 아님. "
        "attemptId=%s, courseId=%s",
        len(grounded),
        _GROUNDING_HARD_GATE_LEN,
        req.attempt_id,
        req.course_id,
    )
    return _build_source_text(req.subject, req.topic), True


def _build_source_text(subject: str, topic: str | None) -> str:
    """DB grounding이 실패했을 때 쓰는 최소 source_text(100자)를 만든다.

    topic이 있으면 topic 기반 학습 범위 텍스트를 만들고,
    없으면 subject 기반 패딩으로 최소 길이를 채운다.
    정상 경로는 _load_grounded_source_text가 chapter·slide 본문을 조립한다.
    시험 메타 정보(문항 수·배점 등)는 메타 문항 생성을 유발하므로 포함하지 않는다.
    """
    base = f"과목: {subject}."
    if topic:
        base = f"과목: {subject}. 주제: {topic}."

    if len(base) >= _SOURCE_MIN_LEN:
        return base

    # 100자를 채우는 패딩. 문항 수·배점·시험 구성 같은 메타 정보는 넣지 않는다.
    # LLM이 이를 학습 자료로 읽고 "이 모의고사는 몇 문항인가" 같은 문항을 만든다.
    padding = (
        f" {subject} 과목의 핵심 개념과 원리, 주요 이론, 실제 적용 사례를 다룬다."
        f" 기초 개념의 정의와 동작 원리부터 심화 응용·비교 분석까지 학습 범위에 포함한다."
        f" 개념 간 관계, 대표적인 오개념, 실전 적용 시 주의점을 함께 다룬다."
    )
    return (base + padding)[:50000]


async def _safe_send_success_callback(
    attempt_id: str,
    exam_id: str,
    answer_key_seal: str,
    questions: list[dict],
    grounding_degraded: bool = False,
    requested_question_count: int | None = None,
    actual_question_count: int | None = None,
    generation_outcome: str | None = None,
) -> None:
    """성공 콜백을 보낸다. 식별자 검사가 실패하면 실패 콜백으로 바꿔 Spring을 FAILED로 둔다.

    MockGenerationCallbackError는 template_id·question_id 누락 같은 내용 결함에서 난다.
    이때는 성공 콜백을 못 보냈으므로 실패 콜백을 보내 GENERATING에 머물지 않게 한다.
    콜백은 블로킹 I/O(urllib + 백오프 time.sleep, 최대 수십 초)라 to_thread로 돌린다.
    이벤트 루프가 멈추면 다른 요청(분석·생성)도 같이 멈춘다.

    grounding_degraded는 성공 콜백에 실어 Spring이 강의 기반이 아님을 알게 한다.
    Spring DTO에 필드가 없으면 ignoreUnknown으로 무시된다.

    requested_question_count·actual_question_count·generation_outcome은 "요청 N / 생성 M"과
    outcome(passed/passed_partial/needs_more_source/exhausted) 안내용이다. 이 필드도
    Spring DTO에 없으면 무시된다.
    """
    try:
        await asyncio.to_thread(
            send_generation_success_callback,
            attempt_id=attempt_id,
            exam_id=exam_id,
            answer_key_seal=answer_key_seal,
            questions=questions,
            grounding_degraded=grounding_degraded,
            requested_question_count=requested_question_count,
            actual_question_count=actual_question_count,
            generation_outcome=generation_outcome,
        )
    except MockGenerationCallbackError as exc:
        _LOG.error(
            "[MockAsyncGen] 성공 콜백 전송 실패 — 실패 콜백으로 전환 "
            "attemptId=%s, error=%s",
            attempt_id,
            exc,
        )
        # 성공 콜백을 못 보냈으니 Spring이 GENERATING에 머물지 않게 실패 콜백을 보낸다.
        # 이것도 실패하면 로그만 남긴다.
        await _safe_send_failed_callback(attempt_id, f"성공 콜백 차단: {exc}")


async def _safe_send_failed_callback(attempt_id: str, reason: str) -> None:
    """실패 콜백을 보낸다. 전송이 실패하면 로그만 남기고 예외는 올리지 않는다.

    재시도 백오프(time.sleep)를 포함한 블로킹 호출이므로 to_thread로 감싼다.
    """
    try:
        await asyncio.to_thread(
            send_generation_failed_callback, attempt_id=attempt_id, reason=reason
        )
    except MockGenerationCallbackError as exc:
        _LOG.error(
            "[MockAsyncGen] 실패 콜백 전송 실패 — attemptId=%s, error=%s",
            attempt_id,
            exc,
        )
