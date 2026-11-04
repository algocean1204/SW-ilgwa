"""폴백 노드가 게이트에 걸린 페이지만 교정하고, Kanana-2 → Claude → MinerU 원문 순서를 지키는지 확인한다."""
from __future__ import annotations

import asyncio
import types

import pytest

pytest.importorskip("pypdfium2", reason="OCR_v1 패키지 import에 pypdfium2가 필요하다")

from app.modules.OCR_v1.pipeline.nodes import fallback_node  # noqa: E402


class _FakeEngine:
    name = "fake_mineru"

    async def extract(self, pdf_bytes: bytes, pages: list[int]) -> list[dict]:
        return [{"page_num": i + 1, "markdown": f"mineru-{i + 1}", "engine": "mineru"} for i in pages]


class _FakeConnector:
    def __init__(self) -> None:
        self.calls: list[str] = []

    async def polish(self, text: str, *, tone_hint: str = "") -> str:
        self.calls.append(text)
        return f"polished:{text}"


class _FakeClaude:
    def __init__(self, reply: str | Exception) -> None:
        self.reply = reply
        self.reqs: list = []

    async def generate(self, req):
        self.reqs.append(req)
        if isinstance(self.reply, Exception):
            raise self.reply
        return types.SimpleNamespace(text=self.reply)


class _RaisingKanana:
    async def polish(self, text: str, *, tone_hint: str = "") -> str:
        raise TimeoutError("kanana down")


class _EchoKanana:
    """실패하면 원문을 돌려주는 Kanana2Connector 동작을 흉내 낸다."""

    async def polish(self, text: str, *, tone_hint: str = "") -> str:
        return text


@pytest.fixture(autouse=True)
def _no_real_claude(monkeypatch: pytest.MonkeyPatch) -> None:
    # 기본은 Claude 없음. 실제 API를 부르지 않게 막는다.
    monkeypatch.setattr(fallback_node, "_get_claude_connector", lambda: None)


def _state() -> dict:
    return {
        "pdf_bytes": b"%PDF",
        "extractions": [
            {"page_num": 1, "markdown": "marker-1", "engine": "marker"},
            {"page_num": 2, "markdown": "marker-2", "engine": "marker"},
            {"page_num": 3, "markdown": "marker-3", "engine": "marker"},
        ],
        "failed_pages": [2],
        "timings": {},
    }


def test_only_failed_pages_are_polished(monkeypatch: pytest.MonkeyPatch) -> None:
    connector = _FakeConnector()
    monkeypatch.setattr(fallback_node, "get_fallback_engine", lambda: _FakeEngine())
    monkeypatch.setattr(fallback_node, "_get_polish_connector", lambda: connector)

    out = asyncio.run(fallback_node.fallback(_state()))

    assert connector.calls == ["mineru-2"]
    pages = {p["page_num"]: p for p in out["extractions"]}
    assert pages[1]["markdown"] == "marker-1"
    assert pages[3]["markdown"] == "marker-3"
    assert pages[2]["markdown"] == "polished:mineru-2"
    assert pages[2]["llm_corrected"] is True
    assert out["fallback_extractions"][0]["markdown"] == "mineru-2"


def test_all_pages_failed_goes_through_mineru(monkeypatch: pytest.MonkeyPatch) -> None:
    connector = _FakeConnector()
    monkeypatch.setattr(fallback_node, "get_fallback_engine", lambda: _FakeEngine())
    monkeypatch.setattr(fallback_node, "_get_polish_connector", lambda: connector)
    state = _state() | {"failed_pages": [1, 2, 3]}

    out = asyncio.run(fallback_node.fallback(state))

    assert connector.calls == ["mineru-1", "mineru-2", "mineru-3"]
    assert [p["markdown"] for p in out["extractions"]] == [
        "polished:mineru-1", "polished:mineru-2", "polished:mineru-3",
    ]


def test_missing_connector_keeps_mineru_output(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(fallback_node, "get_fallback_engine", lambda: _FakeEngine())
    monkeypatch.setattr(fallback_node, "_get_polish_connector", lambda: None)

    out = asyncio.run(fallback_node.fallback(_state()))

    assert [p["markdown"] for p in out["extractions"]] == ["marker-1", "mineru-2", "marker-3"]


def test_flag_off_skips_polish(monkeypatch: pytest.MonkeyPatch) -> None:
    connector = _FakeConnector()
    monkeypatch.setenv("OCR_V1_FALLBACK_KANANA_POLISH", "false")
    monkeypatch.setattr(fallback_node, "get_fallback_engine", lambda: _FakeEngine())
    monkeypatch.setattr(fallback_node, "_get_polish_connector", lambda: connector)

    out = asyncio.run(fallback_node.fallback(_state()))

    assert connector.calls == []
    assert out["extractions"][1]["markdown"] == "mineru-2"


def test_fail_all_routes_to_fallback() -> None:
    pytest.importorskip("langgraph")
    from app.modules.OCR_v1.pipeline import graph

    graph._CACHE.clear()
    compiled = graph.get_compiled_graph()
    edges = {(e.data, e.target) for e in compiled.get_graph().edges if e.source == "quality_gate"}
    assert ("fail_all", "fallback") in edges
    assert ("fail_all", "postprocess") not in edges


def _run_with(monkeypatch: pytest.MonkeyPatch, kanana, claude) -> dict:
    monkeypatch.setattr(fallback_node, "get_fallback_engine", lambda: _FakeEngine())
    monkeypatch.setattr(fallback_node, "_get_polish_connector", lambda: kanana)
    monkeypatch.setattr(fallback_node, "_get_claude_connector", lambda: claude)
    return asyncio.run(fallback_node.fallback(_state()))


def test_kanana_ok_skips_claude(monkeypatch: pytest.MonkeyPatch) -> None:
    claude = _FakeClaude("claude-fixed")
    out = _run_with(monkeypatch, _FakeConnector(), claude)
    assert claude.reqs == []
    assert out["extractions"][1]["markdown"] == "polished:mineru-2"


@pytest.mark.parametrize("kanana", [_RaisingKanana(), _EchoKanana(), None])
def test_kanana_failure_goes_to_claude(monkeypatch: pytest.MonkeyPatch, kanana) -> None:
    from app.modules.ChapterStudio_V1.ai_connectors.kanana2_connector import _instruction

    claude = _FakeClaude("claude-fixed")
    out = _run_with(monkeypatch, kanana, claude)
    assert [r.user for r in claude.reqs] == ["mineru-2"]
    assert claude.reqs[0].system == _instruction("")
    page = out["extractions"][1]
    assert page["markdown"] == "claude-fixed"
    assert page["llm_corrected"] is True


@pytest.mark.parametrize("reply", [RuntimeError("claude down"), "   "])
def test_claude_failure_keeps_mineru(monkeypatch: pytest.MonkeyPatch, reply) -> None:
    out = _run_with(monkeypatch, _RaisingKanana(), _FakeClaude(reply))
    page = out["extractions"][1]
    assert page["markdown"] == "mineru-2"
    assert not page.get("llm_corrected")


def test_claude_flag_off_keeps_mineru(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("OCR_V1_FALLBACK_CLAUDE_POLISH", "false")
    monkeypatch.setattr(fallback_node, "get_fallback_engine", lambda: _FakeEngine())
    monkeypatch.setattr(fallback_node, "_get_polish_connector", lambda: _RaisingKanana())
    # 플래그가 꺼지면 커넥터를 찾지도 않는다
    monkeypatch.setattr(fallback_node, "_get_claude_connector", lambda: pytest.fail("Claude 호출됨"))

    out = asyncio.run(fallback_node.fallback(_state()))

    assert out["extractions"][1]["markdown"] == "mineru-2"
