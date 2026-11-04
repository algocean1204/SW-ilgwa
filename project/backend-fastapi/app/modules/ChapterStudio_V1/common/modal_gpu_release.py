"""Modal GPU 사용을 추적하고 running 컨테이너를 회수한다.

- `modal app stop`(un-deploy) 대신 SDK/CLI `container stop`만 사용한다.
- 강의(scope) 단위로 앱 사용을 기록하고, 전역 refcount가 0일 때만 컨테이너를 내린다.
- Redis(MODAL_GPU_REDIS_URL, 없으면 REDIS_URL)로 멀티 워커·동시 강의를 조율하고, 없으면 프로세스 메모리로 폴백한다.
- Modal Python SDK(TaskList/ContainerStop)를 우선 사용해 Docker에 modal CLI가 없어도 동작한다.
"""
from __future__ import annotations

import asyncio
import contextvars
import json
import threading
from collections.abc import AsyncIterator, Sequence
from contextlib import asynccontextmanager

from app.modules.ChapterStudio_V1.common.config import (
    modal_gpu_redis_url,
    modal_gpu_release_enabled,
)
from app.modules.ChapterStudio_V1.common.logging import logger

_LIST_TIMEOUT_SEC = 30.0
_STOP_TIMEOUT_SEC = 15.0
_SCOPE_TTL_SEC = 86_400
_STOP_LOCK_TTL_SEC = 120
_REDIS_PREFIX = "chapterstudio:modal_gpu"
_CONTAINER_ID_KEY = "Container ID"
_APP_NAME_KEY = "App Name"

_scope_ctx: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "modal_gpu_scope",
    default=None,
)
_redis_client: object | None = None
_redis_init_attempted = False
_memory_lock = threading.Lock()
_memory_scope_apps: dict[str, set[str]] = {}
_memory_refcount: dict[str, int] = {}


def bind_modal_gpu_scope(scope_id: str) -> contextvars.Token[str | None]:
    """현재 비동기 컨텍스트에 강의(작업) scope를 바인딩한다."""
    return _scope_ctx.set(scope_id.strip())


def unbind_modal_gpu_scope(token: contextvars.Token[str | None]) -> None:
    """bind_modal_gpu_scope로 설정한 scope를 해제한다."""
    _scope_ctx.reset(token)


@asynccontextmanager
async def modal_gpu_scope(scope_id: str) -> AsyncIterator[None]:
    """Modal GPU 사용 추적과 회수를 묶는 컨텍스트다(미리보기·단독 백필 등에서 쓴다).

    강의 생성(lessons_generate)은 이미 bind/release를 갖고 있으므로 이 컨텍스트를 쓰지 않는다.
    """
    resolved = scope_id.strip()
    if not resolved:
        yield
        return
    token = bind_modal_gpu_scope(resolved)
    try:
        yield
    finally:
        try:
            await release_modal_gpu_for_scope(resolved)
        except Exception:
            logger.warning(
                "Modal GPU scope release 실패(graceful) — scope={}",
                resolved,
                exc_info=True,
            )
        unbind_modal_gpu_scope(token)


def current_modal_gpu_scope() -> str | None:
    """현재 컨텍스트에 바인딩된 scope id를 반환한다."""
    value = _scope_ctx.get()
    return value.strip() if value else None


def mark_modal_gpu_used(app_name: str, *, scope_id: str | None = None) -> None:
    """Modal 원격 호출 성공 시 앱 사용을 scope에 기록한다(앱당 scope 내 1회만 refcount 증가)."""
    name = app_name.strip()
    if not name:
        return
    resolved_scope = (scope_id or current_modal_gpu_scope() or "").strip()
    if not resolved_scope:
        logger.debug("Modal GPU mark skipped — scope 없음 app={}", name)
        return
    _record_scope_use(resolved_scope, name)


def peek_scope_apps(scope_id: str) -> frozenset[str]:
    """테스트·진단용으로 scope에 기록된 앱 이름을 반환한다."""
    return frozenset(_peek_scope_apps(scope_id))


def peek_app_refcount(app_name: str) -> int:
    """테스트·진단용으로 앱 전역 refcount를 반환한다."""
    return _peek_refcount(app_name.strip())


def clear_modal_gpu_usage() -> None:
    """테스트 격리용으로 메모리 추적 상태를 비운다."""
    with _memory_lock:
        _memory_scope_apps.clear()
        _memory_refcount.clear()


async def release_modal_gpu_for_scope(scope_id: str) -> int:
    """scope 종료 시 사용 앱 refcount를 내리고, 0이 된 앱만 컨테이너 stop한다."""
    if not modal_gpu_release_enabled():
        return 0
    resolved_scope = scope_id.strip()
    if not resolved_scope:
        return 0
    apps_to_stop = _release_scope(resolved_scope)
    if not apps_to_stop:
        return 0
    stopped = 0
    for app_name in apps_to_stop:
        stopped += await _stop_containers_for_app(app_name)
    return stopped


async def release_modal_gpu_credits(
    *,
    app_names: Sequence[str] | None = None,
    scope_id: str | None = None,
    clear_usage: bool = True,  # noqa: ARG001 하위 호환 시그니처라 남긴다.
) -> int:
    """하위 호환 진입점이다. scope_id가 있으면 scope release, 없으면 지정한 app을 강제 stop한다."""
    if scope_id:
        return await release_modal_gpu_for_scope(scope_id)
    if app_names is not None:
        if not modal_gpu_release_enabled():
            return 0
        stopped = 0
        for app_name in app_names:
            if app_name.strip():
                stopped += await _stop_containers_for_app(app_name.strip())
        return stopped
    bound_scope = current_modal_gpu_scope()
    if bound_scope:
        return await release_modal_gpu_for_scope(bound_scope)
    return 0


def qwen3_tts_modal_app_name() -> str:
    """Qwen3-TTS Modal 앱 이름을 환경변수에서 읽는다."""
    import os

    resolved = os.getenv("QWEN3_TTS_MODAL_APP_NAME", "qwen3-tts-modal")
    return resolved.strip() or "qwen3-tts-modal"


def _record_scope_use(scope_id: str, app_name: str) -> None:
    client = _get_redis_client()
    if client is not None:
        scope_key = _scope_key(scope_id)
        ref_key = _ref_key(app_name)
        try:
            added = int(client.sadd(scope_key, app_name))
            if added:
                client.incr(ref_key)
            client.expire(scope_key, _SCOPE_TTL_SEC)
            return
        except Exception as exc:
            logger.warning(
                "Modal GPU mark Redis 실패 — 메모리 폴백 scope={} app={}: {}",
                scope_id,
                app_name,
                exc,
            )
    with _memory_lock:
        apps = _memory_scope_apps.setdefault(scope_id, set())
        if app_name not in apps:
            apps.add(app_name)
            _memory_refcount[app_name] = _memory_refcount.get(app_name, 0) + 1


def _release_scope(scope_id: str) -> list[str]:
    client = _get_redis_client()
    if client is not None:
        scope_key = _scope_key(scope_id)
        try:
            apps = list(client.smembers(scope_key))
            to_stop: list[str] = []
            for app_name in apps:
                ref_key = _ref_key(app_name)
                remaining = int(client.decr(ref_key))
                if remaining <= 0:
                    client.delete(ref_key)
                    to_stop.append(app_name)
            client.delete(scope_key)
            return to_stop
        except Exception as exc:
            logger.warning(
                "Modal GPU release Redis 실패 — 메모리 폴백 scope={}: {}",
                scope_id,
                exc,
            )
    with _memory_lock:
        apps = list(_memory_scope_apps.pop(scope_id, set()))
        to_stop: list[str] = []
        for app_name in apps:
            current = _memory_refcount.get(app_name, 0) - 1
            if current <= 0:
                _memory_refcount.pop(app_name, None)
                to_stop.append(app_name)
            else:
                _memory_refcount[app_name] = current
        return to_stop


def _peek_scope_apps(scope_id: str) -> set[str]:
    client = _get_redis_client()
    if client is not None:
        try:
            return set(client.smembers(_scope_key(scope_id)))
        except Exception:
            pass
    with _memory_lock:
        return set(_memory_scope_apps.get(scope_id, set()))


def _peek_refcount(app_name: str) -> int:
    if not app_name:
        return 0
    client = _get_redis_client()
    if client is not None:
        try:
            value = client.get(_ref_key(app_name))
            return int(value) if value is not None else 0
        except Exception:
            pass
    with _memory_lock:
        return _memory_refcount.get(app_name, 0)


def _get_redis_client() -> object | None:
    global _redis_client, _redis_init_attempted
    if _redis_init_attempted:
        return _redis_client
    _redis_init_attempted = True
    url = modal_gpu_redis_url()
    if not url:
        return None
    try:
        import redis

        client = redis.Redis.from_url(url, decode_responses=True)
        client.ping()
        _redis_client = client
        logger.info("Modal GPU release: Redis 추적 활성화")
    except Exception as exc:
        logger.warning("Modal GPU release: Redis 비활성 — 메모리 폴백 사용: {}", exc)
        _redis_client = None
    return _redis_client


def _scope_key(scope_id: str) -> str:
    return f"{_REDIS_PREFIX}:scope:{scope_id}"


def _ref_key(app_name: str) -> str:
    return f"{_REDIS_PREFIX}:ref:{app_name}"


def _stop_lock_key(app_name: str) -> str:
    return f"{_REDIS_PREFIX}:stop_lock:{app_name}"


async def _stop_containers_for_app(app_name: str) -> int:
    if not await _acquire_stop_lock(app_name):
        logger.info("Modal GPU release: 다른 워커가 stop 중 — skip app={}", app_name)
        return 0
    try:
        container_ids = await _list_container_ids(app_name)
        if not container_ids:
            return 0
        stopped = 0
        for container_id in container_ids:
            if await _stop_container(container_id):
                stopped += 1
        if stopped:
            logger.info(
                "Modal GPU release: app={} stopped {} container(s)",
                app_name,
                stopped,
            )
        return stopped
    finally:
        await _release_stop_lock(app_name)


async def _acquire_stop_lock(app_name: str) -> bool:
    client = _get_redis_client()
    if client is None:
        return True
    try:
        return bool(
            client.set(_stop_lock_key(app_name), "1", nx=True, ex=_STOP_LOCK_TTL_SEC)
        )
    except Exception as exc:
        logger.warning("Modal GPU stop lock 획득 실패(graceful) app={}: {}", app_name, exc)
        return True


async def _release_stop_lock(app_name: str) -> None:
    client = _get_redis_client()
    if client is None:
        return
    try:
        client.delete(_stop_lock_key(app_name))
    except Exception as exc:
        logger.warning("Modal GPU stop lock 해제 실패(graceful) app={}: {}", app_name, exc)


async def _list_container_ids(app_name: str) -> list[str]:
    ids = await _list_container_ids_sdk(app_name)
    if ids:
        return ids
    return await _list_container_ids_cli(app_name)


async def _stop_container(container_id: str) -> bool:
    if await _stop_container_sdk(container_id):
        return True
    return await _stop_container_cli(container_id)


async def _list_container_ids_sdk(app_name: str) -> list[str]:
    try:
        from modal._object import _get_environment_name
        from modal.client import _Client
        from modal.environments import ensure_env
        from modal_proto import api_pb2
    except ImportError:
        return []
    try:
        client = await _Client.from_env()
        environment_name = _get_environment_name(ensure_env(None))
        response = await client.stub.TaskList(
            api_pb2.TaskListRequest(environment_name=environment_name)
        )
    except Exception as exc:
        logger.warning("Modal GPU release: SDK TaskList 실패 app={}: {}", app_name, exc)
        return []
    ids: list[str] = []
    for task in response.tasks:
        if task.app_description != app_name:
            continue
        if task.task_id:
            ids.append(task.task_id)
    return ids


async def _stop_container_sdk(container_id: str) -> bool:
    try:
        from modal.client import _Client
        from modal_proto import api_pb2
    except ImportError:
        return False
    try:
        client = await _Client.from_env()
        await client.stub.ContainerStop(api_pb2.ContainerStopRequest(task_id=container_id))
        return True
    except Exception as exc:
        logger.warning("Modal GPU release: SDK ContainerStop 실패 id={}: {}", container_id, exc)
        return False


async def _list_container_ids_cli(app_name: str) -> list[str]:
    argv = ["modal", "container", "list", "--json"]
    try:
        proc = await asyncio.create_subprocess_exec(
            *argv,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=_LIST_TIMEOUT_SEC)
    except (OSError, asyncio.TimeoutError) as exc:
        logger.warning(
            "Modal GPU release: CLI container list 실패 app={}: {}",
            app_name,
            exc,
        )
        return []
    if proc.returncode != 0:
        logger.warning(
            "Modal GPU release: CLI container list 비정상 종료(code={}) app={}: {}",
            proc.returncode,
            app_name,
            stderr.decode(errors="replace").strip(),
        )
        return []
    return _parse_container_ids(stdout, app_name)


async def _stop_container_cli(container_id: str) -> bool:
    argv = ["modal", "container", "stop", container_id]
    try:
        proc = await asyncio.create_subprocess_exec(
            *argv,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        _, stderr = await asyncio.wait_for(proc.communicate(), timeout=_STOP_TIMEOUT_SEC)
    except (OSError, asyncio.TimeoutError) as exc:
        logger.warning(
            "Modal GPU release: CLI container stop 실패 id={}: {}",
            container_id,
            exc,
        )
        return False
    if proc.returncode != 0:
        logger.warning(
            "Modal GPU release: CLI container stop 비정상 종료(code={}) id={}: {}",
            proc.returncode,
            container_id,
            stderr.decode(errors="replace").strip(),
        )
        return False
    return True


def _parse_container_ids(stdout: bytes, app_name: str) -> list[str]:
    try:
        rows = json.loads(stdout.decode(errors="replace"))
    except json.JSONDecodeError as exc:
        logger.warning(
            "Modal GPU release: CLI JSON 파싱 실패 app={}: {}",
            app_name,
            exc,
        )
        return []
    if not isinstance(rows, list):
        return []
    ids: list[str] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        if row.get(_APP_NAME_KEY) != app_name:
            continue
        container_id = row.get(_CONTAINER_ID_KEY)
        if isinstance(container_id, str) and container_id:
            ids.append(container_id)
    return ids


__all__ = [
    "bind_modal_gpu_scope",
    "clear_modal_gpu_usage",
    "current_modal_gpu_scope",
    "mark_modal_gpu_used",
    "modal_gpu_scope",
    "peek_app_refcount",
    "peek_scope_apps",
    "qwen3_tts_modal_app_name",
    "release_modal_gpu_credits",
    "release_modal_gpu_for_scope",
    "unbind_modal_gpu_scope",
]