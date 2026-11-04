"""Agent job 유지보수 주기 태스크."""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timedelta, timezone

from app.celery_app import celery_app

from .job_store import job_store
from .callback import post_agent_callback

logger = logging.getLogger(__name__)

# stale 판정(분), 완료 job 보관(시간), 콜백 최대 시도 횟수
_STALE_THRESHOLD_MIN: int = 30
_PURGE_THRESHOLD_HOURS: int = 24
_MAX_CALLBACK_ATTEMPTS: int = 3


def _utc_now() -> datetime:
    """timezone-aware UTC 현재 시각을 반환한다."""
    return datetime.now(timezone.utc)


@celery_app.task(name="agent_orchestrator.cleanup_stale_jobs")
def cleanup_stale_jobs() -> None:
    """updated_at 기준 30분 넘게 running인 job을 STALE_TIMEOUT error로 바꾼다."""
    cutoff: datetime = _utc_now() - timedelta(minutes=_STALE_THRESHOLD_MIN)
    stale_ids: list[str] = job_store.iter_by_status("running", before=cutoff)

    for job_id in stale_ids:
        job_store.update(
            job_id,
            status="error",
            error_code="STALE_TIMEOUT",
            error_message="30분 초과 응답 없음",
        )
        logger.warning("[maintenance] stale job 처리: job_id=%s", job_id)

    logger.info("[maintenance] cleanup_stale_jobs 완료: %d건 처리", len(stale_ids))


@celery_app.task(name="agent_orchestrator.purge_old_completed_jobs")
def purge_old_completed_jobs() -> None:
    """updated_at 기준 24시간 지난 done/error job을 저장소에서 지운다."""
    cutoff: datetime = _utc_now() - timedelta(hours=_PURGE_THRESHOLD_HOURS)
    count = job_store.purge_before(("done", "error"), cutoff)
    logger.info("[maintenance] purge_old_completed_jobs 완료: %d건 삭제", count)


@celery_app.task(name="agent_orchestrator.retry_failed_callbacks")
def retry_failed_callbacks() -> None:
    """콜백 전송에 실패했고 시도 횟수가 3회 미만인 job의 콜백을 다시 보낸다."""
    retry_ids: list[str] = job_store.iter_by_callback_status(
        "failed", _MAX_CALLBACK_ATTEMPTS,
    )

    for job_id in retry_ids:
        try:
            record = job_store.get(job_id)
            success, attempts, error = asyncio.run(post_agent_callback(record))
            new_status = "sent" if success else "failed"
            job_store.update(
                job_id,
                callback_status=new_status,
                callback_attempts=record.callback_attempts + attempts,
                callback_error=error,
            )
            logger.info(
                "[maintenance] callback 재시도: job_id=%s, 결과=%s",
                job_id,
                new_status,
            )
        except Exception as exc:
            logger.error("[maintenance] callback 재시도 오류: job_id=%s, 오류=%s", job_id, exc)

    logger.info("[maintenance] retry_failed_callbacks 완료: %d건 시도", len(retry_ids))
