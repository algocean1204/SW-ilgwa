# /api/chat/v1/ask/stream SSE 엔드포인트 테스트. 커넥터는 가짜로 바꿔 끼운다.
from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient

from ai_connectors.errors import RateLimitError
from ai_connectors.text_schemas import ChapterAIResponse
from app.modules.Chat_V1.app import service

_PAYLOAD = {
    "session_id": "s-1",
    "user_message": "이진 탐색 시간복잡도가 뭐예요?",
    "lecture_context": {
        "chapter_title": "이진 탐색",
        "slides": [
            {"slide_idx": 0, "title": "이진 탐색 개념", "content": "정렬된 배열에서 절반씩 줄여 찾는다."},
            {"slide_idx": 1, "title": "시간복잡도", "content": "이진 탐색은 O(log n)이다."},
        ],
    },
}
_CHUNKS = ["이진 탐색은 ", "O(log n)이에요. ", "[슬라이드 2]"]


class _FakeConnector:
    def __init__(self, fail_at: int | None = None) -> None:
        self.fail_at = fail_at
        self.closed = False
        self.seen = None

    async def generate(self, req):
        self.seen = req
        return ChapterAIResponse(text="".join(_CHUNKS), model="fake", input_tokens=1, output_tokens=1, finish_reason="end_turn")

    async def stream(self, req):
        self.seen = req
        for i, text in enumerate(_CHUNKS):
            if i == self.fail_at:
                raise RateLimitError("429")
            yield text

    async def aclose(self) -> None:
        self.closed = True


def _use(monkeypatch: pytest.MonkeyPatch, connector: _FakeConnector) -> _FakeConnector:
    monkeypatch.setattr(service, "_chat_connector", lambda: connector)
    return connector


def _events(body: str) -> list[tuple[str, dict]]:
    out = []
    for block in body.strip().split("\n\n"):
        event, data = block.split("\n")
        out.append((event.removeprefix("event: "), json.loads(data.removeprefix("data: "))))
    return out


def test_stream_sends_deltas_then_done(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    fake = _use(monkeypatch, _FakeConnector())
    res = client.post("/api/chat/v1/ask/stream", json=_PAYLOAD)

    assert res.status_code == 200
    assert res.headers["content-type"].startswith("text/event-stream")
    events = _events(res.text)
    assert events[:-1] == [("delta", {"text": t}) for t in _CHUNKS]
    assert events[-1] == (
        "done",
        {"session_id": "s-1", "answer": "".join(_CHUNKS), "referenced_slides": [1]},
    )
    # /ask 와 같은 프롬프트로 부른다
    assert fake.seen == service.build_text_request(
        service.build_system_prompt(service.ChatRequest.model_validate(_PAYLOAD)), _PAYLOAD["user_message"]
    )
    assert fake.closed


def test_stream_error_before_any_delta(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    fake = _use(monkeypatch, _FakeConnector(fail_at=0))
    events = _events(client.post("/api/chat/v1/ask/stream", json=_PAYLOAD).text)

    assert events == [("error", {"message": service.ERROR_ANSWER})]
    assert fake.closed


def test_stream_error_after_delta_has_no_done(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    _use(monkeypatch, _FakeConnector(fail_at=1))
    events = _events(client.post("/api/chat/v1/ask/stream", json=_PAYLOAD).text)

    assert [e for e, _ in events] == ["delta", "error"]


def test_stream_schema_error_is_422(client: TestClient) -> None:
    res = client.post("/api/chat/v1/ask/stream", json={**_PAYLOAD, "session_id": ""})
    assert res.status_code == 422


def test_ask_still_returns_one_json(client: TestClient, monkeypatch: pytest.MonkeyPatch) -> None:
    _use(monkeypatch, _FakeConnector())
    res = client.post("/api/chat/v1/ask", json=_PAYLOAD)

    assert res.status_code == 200
    assert res.json() == {"session_id": "s-1", "answer": "".join(_CHUNKS), "referenced_slides": [1]}
