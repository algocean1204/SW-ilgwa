"""텍스트 채점 요청을 받아 Claude Sonnet으로 채점하고 Spring에 콜백한다.

IN : GradeRequest
OUT: 없음 (채점 결과는 Spring 콜백으로 전달된다)

실패 시 Spring에 status=failed 콜백을 보내 submission이 영구 queued 상태로 남지 않도록 한다.
"""

from __future__ import annotations

import asyncio
import logging

from .config import get_grading_timeout_seconds
from .schemas import GradeRequest
from .spring_callback import SpringCallbackError, send_ai_result_callback
from .text_grader import TextGradingError, grade_text_submission

_LOG = logging.getLogger(__name__)

# 네트워크가 잠깐 끊기거나 Spring이 재기동하는 동안 채점 결과를 잃지 않게 재시도한다
_CALLBACK_MAX_ATTEMPTS = 3
_CALLBACK_BACKOFF_BASE_SEC = 1.0


async def handle_text_grading_request(request: GradeRequest) -> None:
    """채점부터 Spring 콜백까지 실행한다. 동기 SDK 채점은 스레드로 돌린다."""
    submission_id = request.submission_id
    _LOG.info("[GradingHandler] 채점 시작 | submissionId=%s", submission_id)

    try:
        timeout = get_grading_timeout_seconds()
        result = await asyncio.to_thread(
            grade_text_submission,
            request.assignment_title,
            request.assignment_description,
            request.assignment_questions,
            request.answer_text,
            timeout,
        )
    except TextGradingError as exc:
        _LOG.error(
            "[GradingHandler] 텍스트 채점 실패 | submissionId=%s, error=%s",
            submission_id,
            exc,
        )
        # 채점 실패도 알려야 submission이 queued로 남지 않는다
        await _send_callback_with_retry(
            submission_id,
            score=0,
            feedback="AI 채점 중 오류가 발생했습니다. 관리자에게 문의하세요.",
            ai_confidence=0.0,
            status_transition="failed",
        )
        return

    await _send_callback_with_retry(
        submission_id,
        score=result.score,
        feedback=result.feedback,
        ai_confidence=result.ai_confidence,
        status_transition="done",
    )


async def _send_callback_with_retry(
    submission_id: str,
    *,
    score: int,
    feedback: str,
    ai_confidence: float,
    status_transition: str,
) -> bool:
    """Spring 콜백을 최대 3회 보낸다. 실패 사이에 1초, 2초를 기다린다.

    세 번 다 실패하면 로그만 남긴다. submission 복구는 Spring 폴링과 운영 모니터링에서 한다.
    """
    for attempt in range(1, _CALLBACK_MAX_ATTEMPTS + 1):
        try:
            await asyncio.to_thread(
                send_ai_result_callback,
                submission_id,
                score,
                feedback,
                ai_confidence,
                status_transition,
            )
            return True
        except SpringCallbackError as exc:
            _LOG.warning(
                "[GradingHandler] Spring 콜백 실패 (%d/%d) | submissionId=%s, status=%s, error=%s",
                attempt,
                _CALLBACK_MAX_ATTEMPTS,
                submission_id,
                status_transition,
                exc,
            )
            if attempt < _CALLBACK_MAX_ATTEMPTS:
                await asyncio.sleep(_CALLBACK_BACKOFF_BASE_SEC * (2 ** (attempt - 1)))
    _LOG.error(
        "[GradingHandler] Spring 콜백 최종 실패 — 채점 결과 미전달 | submissionId=%s, status=%s",
        submission_id,
        status_transition,
    )
    return False
