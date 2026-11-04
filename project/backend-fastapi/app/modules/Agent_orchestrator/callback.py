"""Spring callback 전송 유틸리티."""
from __future__ import annotations

import os

import httpx

from .schemas import AgentJobRecord


def callback_timeout_sec() -> float:
    """콜백 HTTP 타임아웃(초). 기본 5초."""
    return float(os.environ.get("AGENT_CALLBACK_TIMEOUT_SEC", "5"))


def callback_max_attempts() -> int:
    """콜백 최대 시도 횟수. 기본 3회, 최소 1회."""
    return max(1, int(os.environ.get("AGENT_CALLBACK_MAX_ATTEMPTS", "3")))


async def post_agent_callback(record: AgentJobRecord) -> tuple[bool, int, str | None]:
    """job 레코드를 Spring callback_url로 POST한다.

    (성공 여부, 시도 횟수, 마지막 오류)를 반환한다. callback_url이 없으면 (True, 0, None).
    """
    if record.callback_url is None:
        return True, 0, None
    last_error: str | None = None
    payload = record.model_dump(mode="json")
    # Spring InternalTokenFilter는 /internal/** 요청에 이 헤더가 없거나 값이 다르면
    # 403(SBM_003)으로 거절한다.
    internal_token = os.environ.get("APP_INTERNAL_TOKEN", "")
    headers = {
        "Content-Type": "application/json",
        "X-FastAPI-Job-Id": record.job_id,
        "X-Internal-Token": internal_token,
    }
    if record.correlation_id:
        headers["X-Correlation-Id"] = record.correlation_id
    async with httpx.AsyncClient(timeout=callback_timeout_sec()) as client:
        for attempt in range(1, callback_max_attempts() + 1):
            try:
                response = await client.post(record.callback_url, json=payload, headers=headers)
                if 200 <= response.status_code < 300:
                    return True, attempt, None
                last_error = f"HTTP {response.status_code}: {response.text[:300]}"
            except httpx.HTTPError as exc:
                last_error = str(exc)
    return False, callback_max_attempts(), last_error
