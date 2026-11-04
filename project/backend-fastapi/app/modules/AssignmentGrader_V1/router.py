"""AssignmentGrader_V1 FastAPI 라우터.

POST /api/assignment-grader/v1/grade
  - Spring TelegramWebhookService가 호출한다 (X-Internal-Token 인증)
  - 요청 즉시 202를 반환하고, 백그라운드에서 채점을 실행한다
  - 전역 ApiKeyMiddleware 대신 X-Internal-Token으로 Spring 내부 호출을 검증한다
"""

from __future__ import annotations

import hmac
import logging
import os

from fastapi import APIRouter, BackgroundTasks, Header, HTTPException

from .grading_handler import handle_text_grading_request
from .schemas import GradeRequest, GradeResponse

_LOG = logging.getLogger(__name__)

router = APIRouter(prefix="/api/assignment-grader/v1", tags=["assignment-grader"])


def _verify_internal_token(token: str | None) -> None:
    """X-Internal-Token 헤더로 Spring 내부 호출을 검증한다.

    APP_INTERNAL_TOKEN 이 없으면 로컬 개발용으로 검증을 건너뛴다.
    """
    expected = os.getenv("APP_INTERNAL_TOKEN")
    if not expected:
        return
    if not token or not hmac.compare_digest(token, expected):
        raise HTTPException(status_code=401, detail="내부 서비스 인증 실패")


@router.post("/grade", response_model=GradeResponse, status_code=202)
async def grade_text_submission(
    request: GradeRequest,
    background_tasks: BackgroundTasks,
    x_internal_token: str | None = Header(default=None),
) -> GradeResponse:
    """채점을 백그라운드로 넘기고 202를 바로 돌려준다.

    결과는 Spring /internal/submissions/{id}/ai-result 콜백으로 간다.
    """
    _verify_internal_token(x_internal_token)
    _LOG.info(
        "[AssignmentGrader] 채점 요청 수락 | submissionId=%s", request.submission_id
    )
    background_tasks.add_task(handle_text_grading_request, request)
    return GradeResponse(accepted=True, submission_id=request.submission_id)
