from __future__ import annotations

from app.modules.ChapterStudio_V1.app.curriculum_preview import build_curriculum_preview
from app.modules.ChapterStudio_V1.app.curriculum_preview_types import CurriculumPreviewRequest


async def test_curriculum_preview_mock_matches_course_detail_contract(monkeypatch) -> None:
    monkeypatch.setenv("ALLOW_MOCK_PREVIEW", "true")
    preview = await build_curriculum_preview(
        CurriculumPreviewRequest(topic="통계 추론", title="통계 과외", lesson_count=10, engine="mock")
    )

    assert preview.status == "CURRICULUM_READY"
    assert preview.confirmed is False
    assert len(preview.lessons) == 10
    assert all(10 <= lesson.slide_count <= 15 for lesson in preview.lessons)


async def test_curriculum_preview_function_returns_contract(monkeypatch) -> None:
    monkeypatch.setenv("ALLOW_MOCK_PREVIEW", "true")
    preview = await build_curriculum_preview(
        CurriculumPreviewRequest(topic="통계 추론", title="통계 과외", lesson_count=10, engine="mock")
    )

    assert preview.status == "CURRICULUM_READY"
    assert len(preview.lessons) == 10
