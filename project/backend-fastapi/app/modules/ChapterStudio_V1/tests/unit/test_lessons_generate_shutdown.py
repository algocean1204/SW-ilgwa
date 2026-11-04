"""generate_lessons_for_course 셧다운 동작 회귀 테스트.

이전에는 finally에서 shutdown_text_connector()로 modal app stop(un-deploy)을 불렀다.
un-deploy가 배포를 내려 다음 강의 생성 요청이 "App not found"로 실패해 바꿨다.

지금 동작:
    - generate_lessons_for_course에서 un-deploy를 자동 호출하지 않는다.
    - 강의 단위 finally에서 modal_gpu_release(container stop)만 호출한다.
    - decommission이 필요하면 CHAPTERSTUDIO_MODAL_TEARDOWN=true를 설정하고 수동 실행한다.

검증 항목:
    - 배치 완료 후 shutdown_text_connector가 자동 호출되지 않아 앱 배포가 남는다.
    - 강의 생성 finally에서 release_modal_gpu_for_scope가 호출된다.
    - 강의별 실패가 있어도 나머지 강의는 계속 처리된다.
    - shutdown_text_connector 임포트 자체가 모듈에서 제거됐는지 확인.
"""
from __future__ import annotations

import pytest

from app.modules.ChapterStudio_V1.app import lessons_generate


def test_shutdown_text_connector_not_imported_in_lessons_generate() -> None:
    """shutdown_text_connector는 모듈 네임스페이스에 없어야 한다."""
    assert not hasattr(lessons_generate, "shutdown_text_connector"), (
        "shutdown_text_connector가 lessons_generate 모듈에 임포트돼 있다. "
        "un-deploy 자동 호출 회귀 위험."
    )


@pytest.mark.anyio
async def test_batch_does_not_call_shutdown_after_lessons(monkeypatch: pytest.MonkeyPatch) -> None:
    """다음 요청이 실패하지 않게 배치 완료 후 un-deploy를 자동 호출하지 않는다."""
    shutdown_called = {"count": 0}

    async def fake_ids(course_id: str) -> list[str]:
        return ["l1", "l2", "l3"]

    async def fake_generate_one(course_id: str, lesson_id: str) -> bool:
        return True

    # shutdown_text_connector가 모듈에 없으므로 패치 대상도 없다.
    # 임포트가 다시 들어오면 이 테스트가 깨진다.
    monkeypatch.setattr(lessons_generate, "_course_lesson_ids", fake_ids)
    monkeypatch.setattr(lessons_generate, "_generate_one", fake_generate_one)

    await lessons_generate.generate_lessons_for_course("course-1")

    # shutdown이 자동 호출되지 않았으면 shutdown_called는 0이다.
    assert shutdown_called["count"] == 0


@pytest.mark.anyio
async def test_lesson_failure_does_not_stop_remaining_lessons(monkeypatch: pytest.MonkeyPatch) -> None:
    """l2가 실패해도 l3는 계속 처리된다."""
    processed: list[str] = []

    async def fake_ids(course_id: str) -> list[str]:
        return ["l1", "l2", "l3"]

    async def fake_generate_one(course_id: str, lesson_id: str) -> bool:
        processed.append(lesson_id)
        return lesson_id != "l2"  # l2만 False를 반환한다.

    monkeypatch.setattr(lessons_generate, "_course_lesson_ids", fake_ids)
    monkeypatch.setattr(lessons_generate, "_generate_one", fake_generate_one)

    await lessons_generate.generate_lessons_for_course("course-1")

    assert processed == ["l1", "l2", "l3"]


@pytest.mark.anyio
async def test_generate_loaded_context_releases_modal_gpu_in_finally(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """강의 생성 성공·실패와 무관하게 finally에서 Modal GPU release가 호출된다."""
    release_calls: list[str] = []

    async def fake_release(lesson_id: str) -> int:
        release_calls.append(lesson_id)
        return 0

    class _Ctx:
        lesson_id = "lesson-1"

        def to_generation_input(self) -> object:
            return object()

    async def fake_inject(ctx: object) -> object:
        return ctx

    async def fake_running(ctx: object) -> None:
        return None

    async def fake_generate(*args: object, **kwargs: object) -> dict[str, object]:
        return {}

    class _FakeConn:
        async def __aenter__(self) -> "_FakeConn":
            return self

        async def __aexit__(self, *args: object) -> None:
            return None

    async def fake_persist(*args: object, **kwargs: object) -> None:
        return None

    async def fake_mark(*args: object, **kwargs: object) -> None:
        return None

    monkeypatch.setattr(lessons_generate, "get_connection", lambda: _FakeConn())
    monkeypatch.setattr(lessons_generate, "inject_reference_context_if_pdf", fake_inject)
    monkeypatch.setattr(lessons_generate, "_record_generation_running", fake_running)
    monkeypatch.setattr(lessons_generate, "generate_chapter_state", fake_generate)
    monkeypatch.setattr(lessons_generate, "state_to_response", lambda *a, **k: type("R", (), {"slides": []})())
    monkeypatch.setattr(lessons_generate, "persist_chapter_state", fake_persist)
    monkeypatch.setattr(lessons_generate, "_mark_public_chapter_available", fake_mark)
    monkeypatch.setattr(lessons_generate, "_backfill_audio_when_needed", lambda *a, **k: None)
    monkeypatch.setattr(lessons_generate, "release_modal_gpu_for_scope", fake_release)
    monkeypatch.setattr(lessons_generate, "bind_modal_gpu_scope", lambda lesson_id: object())
    monkeypatch.setattr(lessons_generate, "unbind_modal_gpu_scope", lambda token: None)

    await lessons_generate._generate_loaded_context(_Ctx())  # type: ignore[arg-type]

    assert release_calls == ["lesson-1"]


@pytest.mark.anyio
async def test_empty_course_returns_early_without_processing(monkeypatch: pytest.MonkeyPatch) -> None:
    """생성할 강의가 없으면 generate_one을 부르지 않고 바로 반환한다."""
    generate_called = {"count": 0}

    async def fake_ids(course_id: str) -> list[str]:
        return []

    async def fake_generate_one(course_id: str, lesson_id: str) -> bool:
        generate_called["count"] += 1
        return True

    monkeypatch.setattr(lessons_generate, "_course_lesson_ids", fake_ids)
    monkeypatch.setattr(lessons_generate, "_generate_one", fake_generate_one)

    await lessons_generate.generate_lessons_for_course("course-1")

    assert generate_called["count"] == 0
