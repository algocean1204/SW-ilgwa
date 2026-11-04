"""worker와 beat가 함께 쓰는 Celery 앱.

브로커/백엔드 URL은 환경변수에서 읽는다.
"""

import os

from celery import Celery

broker_url = os.environ.get("CELERY_BROKER_URL", "redis://localhost:6379/1")
result_backend = os.environ.get("CELERY_RESULT_BACKEND", "redis://localhost:6379/2")

celery_app = Celery(
    "backend_fastapi",
    broker=broker_url,
    backend=result_backend,
    include=[
        "app.modules.Agent_orchestrator.tasks",
        "app.modules.Agent_orchestrator.maintenance",
    ],
)

# beat가 실행하는 주기 태스크
celery_app.conf.beat_schedule = {
    # 10분마다: 30분 넘게 running인 job을 error로 바꾼다
    "cleanup-stale-jobs": {
        "task": "agent_orchestrator.cleanup_stale_jobs",
        "schedule": 600,
    },
    # 1시간마다: 24시간 지난 done/error job을 저장소에서 지운다
    "purge-old-jobs": {
        "task": "agent_orchestrator.purge_old_completed_jobs",
        "schedule": 3600,
    },
    # 5분마다: 콜백 전송에 실패한 job을 다시 보낸다
    "retry-failed-callbacks": {
        "task": "agent_orchestrator.retry_failed_callbacks",
        "schedule": 300,
    },
}
celery_app.conf.timezone = "Asia/Seoul"
