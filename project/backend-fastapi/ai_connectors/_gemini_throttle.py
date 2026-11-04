"""Gemini 호출 안정화 공통 정책 (시연 503/429 대응).

프로세스 전역 스로틀(호출 시작 간 최소 간격)과 지수 백오프·타임아웃·모델 폴백
환경변수를 여기서만 읽는다. 루트 gemini_connector 와 ExamForge/ChapterStudio 의
genai 커넥터가 같이 쓴다.

- GEMINI_REQUEST_INTERVAL_MS : 호출 시작 간 최소 간격(ms). 0/미설정이면 비활성.
- GEMINI_TEXT_RETRY_ATTEMPTS : 모델당 시도 횟수(기본 5).
- GEMINI_TEXT_RETRY_INITIAL_MS / GEMINI_TEXT_RETRY_MAX_MS : 백오프 1s→2s→4s→8s.
- GEMINI_TEXT_TIMEOUT_MS     : 단일 SDK 호출 타임아웃(기본 120000ms).
- GEMINI_TEXT_MODEL_FALLBACKS: Gemini 내부 모델 폴백 순서(쉼표 구분).

상태는 threading.Lock 으로 감싸고 time.monotonic 기준으로 다음 허용 시각을 잡는다.
그래서 이벤트 루프가 여러 개여도(테스트 등) 동작한다.
환경변수는 호출할 때마다 다시 읽는다(테스트 monkeypatch 용).

레인 분리:
채팅·강의처럼 지연에 민감한 경로와 ExamForge처럼 호출이 몰리는 배치 경로가 같은
스로틀 상태를 쓰면 한쪽이 다른 쪽을 굶긴다. 그래서 레인 이름별로 lock 과
next_allowed_at 을 따로 둔다.
- 전역 레인: GEMINI_REQUEST_INTERVAL_MS 를 읽는다. 인자 없이 호출하면 이 레인이다.
- 이름 있는 레인(lane="examforge" 등): interval_env 로 넘긴 전용 환경변수를 읽고,
  전역 레인과 따로 슬롯을 잡는다.
"""
from __future__ import annotations

import asyncio
import os
import threading
import time

_DEFAULT_RETRY_ATTEMPTS = 5
_DEFAULT_RETRY_INITIAL_MS = 1000
_DEFAULT_RETRY_MAX_MS = 8000
_DEFAULT_TIMEOUT_MS = 120000
_DEFAULT_MODEL_FALLBACKS = "gemini-3.5-flash,gemini-3.1-flash,gemini-2.5-flash"

# 전역(기본) 스로틀 레인 식별자. GEMINI_REQUEST_INTERVAL_MS 를 읽는다.
_GLOBAL_LANE = "__global__"

# 레인별 스로틀 상태. 레인마다 (lock, next_allowed_at) 한 쌍을 따로 가진다.
# _registry_lock 은 레인 dict 에 새 레인을 동시에 넣을 때 쓴다(레인별 lock 과 별개).
_registry_lock = threading.Lock()
_lane_locks: dict[str, threading.Lock] = {_GLOBAL_LANE: threading.Lock()}
_lane_next_allowed_at: dict[str, float] = {_GLOBAL_LANE: 0.0}

# 테스트가 throttle._state_lock 으로 전역 레인 lock 을 직접 참조해서 남긴 별칭이다.
_state_lock = _lane_locks[_GLOBAL_LANE]


def _env_int(name: str, default: int) -> int:
    """환경변수 정수 파싱. 실패하면 기본값으로 복구한다."""
    raw = os.getenv(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw.strip())
    except ValueError:
        return default


# 스로틀 간격 상한(ms). 999999999999 같은 잘못된 값으로 요청이 무한 대기하지 않게 한다.
# 정상 범위는 수백 ms~수 초다.
_MAX_INTERVAL_MS = 60_000


def request_interval_sec(
    interval_env: str = "GEMINI_REQUEST_INTERVAL_MS",
    default_ms: int = 0,
) -> float:
    """Gemini 호출 시작 간 최소 간격(초).

    interval_env 가 가리키는 환경변수에서 ms 값을 읽는다(기본 GEMINI_REQUEST_INTERVAL_MS).
    미설정·파싱 실패면 default_ms 를 쓴다. 전역 레인은 0(비활성)이고, ExamForge 레인은
    600 을 넘겨 env 없이도 간격을 둔다. 0 이하면 0, 양수면 60초를 넘지 않게 자른다.
    """
    interval_ms = _env_int(interval_env, default_ms)
    if interval_ms <= 0:
        return 0.0
    return min(interval_ms, _MAX_INTERVAL_MS) / 1000.0


def _lane_state(lane: str) -> tuple[threading.Lock, str]:
    """레인 이름에 대응하는 lock 을 반환한다(없으면 생성). 레인 키도 함께 돌려준다."""
    with _registry_lock:
        lock = _lane_locks.get(lane)
        if lock is None:
            lock = threading.Lock()
            _lane_locks[lane] = lock
            _lane_next_allowed_at[lane] = 0.0
        return lock, lane


def _reserve_slot(
    lane: str = _GLOBAL_LANE,
    interval_env: str = "GEMINI_REQUEST_INTERVAL_MS",
    default_ms: int = 0,
) -> float:
    """지정 레인의 다음 호출 슬롯을 예약하고 대기 시간(초)을 반환한다.

    레인마다 lock 과 next_allowed_at 이 따로라 다른 레인과 섞이지 않는다.
    default_ms 는 env 가 없을 때 쓰는 기본 간격(ms)이다.
    """
    interval = request_interval_sec(interval_env, default_ms)
    if interval <= 0.0:
        return 0.0
    lock, key = _lane_state(lane)
    with lock:
        now = time.monotonic()
        start_at = max(now, _lane_next_allowed_at.get(key, 0.0))
        _lane_next_allowed_at[key] = start_at + interval
        return start_at - now


async def wait_for_slot(
    *,
    lane: str = _GLOBAL_LANE,
    interval_env: str = "GEMINI_REQUEST_INTERVAL_MS",
    default_ms: int = 0,
) -> None:
    """비동기 경로용 스로틀. 레인 슬롯을 잡고 필요한 만큼 기다린다.

    인자 없이 호출하면 전역 레인과 GEMINI_REQUEST_INTERVAL_MS(기본 0)를 쓴다.
    ExamForge 등은 lane/interval_env/default_ms 를 넘겨 전용 레인을 쓴다.
    """
    delay = _reserve_slot(lane, interval_env, default_ms)
    if delay > 0.0:
        await asyncio.sleep(delay)


def wait_for_slot_sync(
    *,
    lane: str = _GLOBAL_LANE,
    interval_env: str = "GEMINI_REQUEST_INTERVAL_MS",
    default_ms: int = 0,
) -> None:
    """동기 경로용 스로틀. 별도 스레드처럼 이벤트 루프 밖에서 쓴다."""
    delay = _reserve_slot(lane, interval_env, default_ms)
    if delay > 0.0:
        time.sleep(delay)


def reset_throttle() -> None:
    """모든 레인의 스로틀 상태를 초기화한다(테스트 격리용)."""
    with _registry_lock:
        for key in _lane_next_allowed_at:
            _lane_next_allowed_at[key] = 0.0


def retry_attempts() -> int:
    """모델당 재시도 포함 총 시도 횟수(기본 5, 1~10 범위로 보정)."""
    value = _env_int("GEMINI_TEXT_RETRY_ATTEMPTS", _DEFAULT_RETRY_ATTEMPTS)
    return max(1, min(10, value))


def backoff_delay_sec(attempt: int) -> float:
    """attempt(0부터)번째 실패 뒤 대기 시간(초). 초기값 ×2^attempt 이고 상한을 넘지 않는다."""
    initial_ms = max(0, _env_int("GEMINI_TEXT_RETRY_INITIAL_MS", _DEFAULT_RETRY_INITIAL_MS))
    max_ms = max(0, _env_int("GEMINI_TEXT_RETRY_MAX_MS", _DEFAULT_RETRY_MAX_MS))
    delay_ms = min(initial_ms * (2 ** max(0, attempt)), max_ms)
    return delay_ms / 1000.0


def request_timeout_sec() -> float | None:
    """단일 SDK 호출 타임아웃(초). 0 이하이면 None(타임아웃 비활성)."""
    timeout_ms = _env_int("GEMINI_TEXT_TIMEOUT_MS", _DEFAULT_TIMEOUT_MS)
    return timeout_ms / 1000.0 if timeout_ms > 0 else None


def model_chain(primary: str) -> list[str]:
    """현재 모델을 선두로 한 Gemini 내부 폴백 모델 순서를 반환한다.

    GEMINI_TEXT_MODEL_FALLBACKS 순서를 따르고 현재 모델과 겹치는 항목은 뺀다.
    목록이 비면 현재 모델 하나만 남는다.
    """
    raw = os.getenv("GEMINI_TEXT_MODEL_FALLBACKS", _DEFAULT_MODEL_FALLBACKS)
    fallbacks = [item.strip() for item in raw.split(",") if item.strip()]
    return [primary] + [model for model in fallbacks if model != primary]
