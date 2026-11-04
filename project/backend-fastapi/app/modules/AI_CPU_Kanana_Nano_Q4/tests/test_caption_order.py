"""캡션 폴백 순서(Kanana → Claude Haiku → Jinja2)를 확인한다."""
import pytest

pytest.importorskip("jinja2", reason="jinja2가 설치되지 않음 — uv sync 후 재실행")

from app.modules.AI_CPU_Kanana_Nano_Q4 import caption  # noqa: E402

_GOOD = "민준아, 오늘 리스트 컴프리헨션 같이 해보자! 할 수 있어."
_KW = {"student_name": "김민준", "assignment_name": "리스트 컴프리헨션"}


def test_kanana_ok_skips_haiku(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(caption, "_run_kanana_safely", lambda _p: _GOOD)
    monkeypatch.setattr(caption, "_run_haiku", lambda _p: pytest.fail("Haiku를 부르면 안 된다"))
    assert caption.generate_caption(**_KW).source == "kanana"


def test_invalid_kanana_falls_back_to_haiku(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(caption, "_run_kanana_safely", lambda _p: "짧음")
    monkeypatch.setattr(caption, "_run_haiku", lambda _p: _GOOD)
    result = caption.generate_caption(**_KW)
    assert (result.source, result.text) == ("haiku", _GOOD)


def test_haiku_fail_falls_back_to_template(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(caption, "_run_kanana_safely", lambda _p: None)
    monkeypatch.setattr(caption, "_run_haiku", lambda _p: None)
    assert caption.generate_caption(**_KW).source == "fallback"


def test_haiku_needs_key_and_backend(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    assert caption._should_try_haiku() is False
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    monkeypatch.setenv("CAPTION_BACKEND", "kanana")
    assert caption._should_try_haiku() is False
    monkeypatch.setenv("CAPTION_BACKEND", "auto")
    assert caption._should_try_haiku() is True
