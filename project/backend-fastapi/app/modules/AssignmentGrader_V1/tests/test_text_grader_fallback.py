"""Claude 채점이 실패해도 기본으로는 Gemini를 부르지 않는지 확인한다."""
from __future__ import annotations

import pytest

from app.modules.AssignmentGrader_V1 import text_grader

_ARGS = ("과제", None, ["문항"], "답안")
_JSON = '{"score": 80, "feedback": "좋다", "ai_confidence": 0.9}'


def _claude_fails(prompt: str, timeout_seconds: int) -> str:
    raise text_grader.TextGradingError("Claude 채점 호출 실패: down")


def test_claude_is_used(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(text_grader, "_call_claude", lambda p, t: _JSON)
    monkeypatch.setattr(text_grader, "_call_gemini", lambda p, t: pytest.fail("Gemini를 부르면 안 된다"))
    assert text_grader.grade_text_submission(*_ARGS).score == 80


def test_no_gemini_fallback_by_default(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ASSIGNMENT_GRADER_GEMINI_FALLBACK", raising=False)
    monkeypatch.setattr(text_grader, "_call_claude", _claude_fails)
    monkeypatch.setattr(text_grader, "_call_gemini", lambda p, t: pytest.fail("Gemini를 부르면 안 된다"))
    with pytest.raises(text_grader.TextGradingError):
        text_grader.grade_text_submission(*_ARGS)


def test_gemini_fallback_only_when_enabled(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ASSIGNMENT_GRADER_GEMINI_FALLBACK", "true")
    monkeypatch.setattr(text_grader, "_call_claude", _claude_fails)
    monkeypatch.setattr(text_grader, "_call_gemini", lambda p, t: _JSON)
    assert text_grader.grade_text_submission(*_ARGS).score == 80
