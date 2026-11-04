"""Modal GPU release의 scope refcount, 동시 강의, SDK/CLI stop을 검증한다."""
from __future__ import annotations

import json

import pytest

from app.modules.ChapterStudio_V1.common import modal_gpu_release as release_module

_SUBPROCESS_ATTR = "create_subprocess_" + "exec"


class _FakeProc:
    def __init__(self, returncode: int, stdout: bytes = b"", stderr: bytes = b"") -> None:
        self.returncode = returncode
        self._stdout = stdout
        self._stderr = stderr

    async def communicate(self) -> tuple[bytes, bytes]:
        return self._stdout, self._stderr


@pytest.fixture(autouse=True)
def _clear_usage() -> None:
    release_module.clear_modal_gpu_usage()
    release_module._redis_init_attempted = False
    release_module._redis_client = None


def test_parse_container_ids_filters_by_app_name() -> None:
    payload = json.dumps(
        [
            {"Container ID": "ta-aaa", "App Name": "chapterstudio-qwen27b"},
            {"Container ID": "ta-bbb", "App Name": "qwen3-tts-modal"},
            {"Container ID": "", "App Name": "chapterstudio-qwen27b"},
        ]
    ).encode()
    ids = release_module._parse_container_ids(payload, "chapterstudio-qwen27b")
    assert ids == ["ta-aaa"]


def test_mark_without_scope_is_ignored() -> None:
    release_module.mark_modal_gpu_used("chapterstudio-qwen27b")
    assert release_module.peek_app_refcount("chapterstudio-qwen27b") == 0


@pytest.mark.anyio
async def test_scope_release_stops_only_when_refcount_zero(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MODAL_GPU_RELEASE_ENABLED", "true")
    stop_calls: list[str] = []

    async def fake_stop_containers(app_name: str) -> int:
        stop_calls.append(app_name)
        return 1

    monkeypatch.setattr(release_module, "_stop_containers_for_app", fake_stop_containers)

    release_module.mark_modal_gpu_used("chapterstudio-qwen27b", scope_id="lesson-a")
    release_module.mark_modal_gpu_used("chapterstudio-qwen27b", scope_id="lesson-b")

    assert release_module.peek_app_refcount("chapterstudio-qwen27b") == 2

    assert await release_module.release_modal_gpu_for_scope("lesson-a") == 0
    assert stop_calls == []
    assert release_module.peek_app_refcount("chapterstudio-qwen27b") == 1

    assert await release_module.release_modal_gpu_for_scope("lesson-b") == 1
    assert stop_calls == ["chapterstudio-qwen27b"]
    assert release_module.peek_app_refcount("chapterstudio-qwen27b") == 0


@pytest.mark.anyio
async def test_scope_release_uses_sdk_before_cli(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MODAL_GPU_RELEASE_ENABLED", "true")
    release_module.mark_modal_gpu_used("chapterstudio-qwen27b", scope_id="lesson-1")

    async def fake_sdk_list(app_name: str) -> list[str]:
        assert app_name == "chapterstudio-qwen27b"
        return ["ta-sdk"]

    async def fake_sdk_stop(container_id: str) -> bool:
        assert container_id == "ta-sdk"
        return True

    async def fake_cli_list(app_name: str) -> list[str]:
        raise AssertionError("CLI list should not run when SDK succeeds")

    monkeypatch.setattr(release_module, "_list_container_ids_sdk", fake_sdk_list)
    monkeypatch.setattr(release_module, "_stop_container_sdk", fake_sdk_stop)
    monkeypatch.setattr(release_module, "_list_container_ids_cli", fake_cli_list)

    stopped = await release_module.release_modal_gpu_for_scope("lesson-1")
    assert stopped == 1


@pytest.mark.anyio
async def test_release_disabled_is_noop(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MODAL_GPU_RELEASE_ENABLED", "false")
    release_module.mark_modal_gpu_used("chapterstudio-qwen27b", scope_id="lesson-1")
    called = {"count": 0}

    async def fake_stop(app_name: str) -> int:
        called["count"] += 1
        return 1

    monkeypatch.setattr(release_module, "_stop_containers_for_app", fake_stop)

    assert await release_module.release_modal_gpu_for_scope("lesson-1") == 0
    assert called["count"] == 0
    assert release_module.peek_app_refcount("chapterstudio-qwen27b") == 1


@pytest.mark.anyio
async def test_modal_gpu_scope_binds_and_releases(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("MODAL_GPU_RELEASE_ENABLED", "true")
    release_calls: list[str] = []

    async def fake_release(scope_id: str) -> int:
        release_calls.append(scope_id)
        return 0

    monkeypatch.setattr(release_module, "release_modal_gpu_for_scope", fake_release)
    release_module.mark_modal_gpu_used("qwen3-tts-modal", scope_id="preview:tut-1")

    async with release_module.modal_gpu_scope("preview:tut-1"):
        assert release_module.current_modal_gpu_scope() == "preview:tut-1"

    assert release_calls == ["preview:tut-1"]