"""채점·총평 커넥터 선택 테스트.

모의고사 채점과 피드백(서술형 루브릭 채점 + AI 총평)은 Claude Sonnet을 기본으로 쓰고
ACTIVE_GRADING_MODEL 하나로 바꿀 수 있어야 한다. Gemini 폴백은
EXAMFORGE_GEMINI_FALLBACK=true일 때만 붙는다. 라이브 AI 호출 없이 스텁만 쓴다.
"""
from __future__ import annotations

from app.modules.ExamForge_V1.common import ai_bridge
from app.modules.ExamForge_V1.common import config


def test_active_grading_model_defaults_to_claude_sonnet(monkeypatch) -> None:
    """미설정이면 채점 모델은 claude_sonnet 이다."""
    monkeypatch.delenv("ACTIVE_GRADING_MODEL", raising=False)
    assert config.active_grading_model() == "claude_sonnet"


def test_active_grading_model_env_override(monkeypatch) -> None:
    """ACTIVE_GRADING_MODEL 한 값으로 다른 커넥터로 교체된다."""
    monkeypatch.setenv("ACTIVE_GRADING_MODEL", "opus46")
    assert config.active_grading_model() == "opus46"


class _StubConnector:
    def __init__(self, name: str) -> None:
        self.name = name

    def supports(self, _feature: str) -> bool:
        return False


def test_grading_connector_claude_primary_with_gemini_fallback(monkeypatch) -> None:
    """기본 채점 커넥터는 텍스트 생성과 동일한 claude_sonnet 체인을 재사용한다."""
    monkeypatch.delenv("ACTIVE_GRADING_MODEL", raising=False)
    ai_bridge._GRADING_CONNECTOR_CACHE.clear()

    sentinel = _StubConnector("claude_sonnet_grading_chain")
    sentinel.supports = lambda feature: feature == "fallback"  # type: ignore[method-assign]

    monkeypatch.setattr(ai_bridge, "get_connector", lambda name: sentinel if name == "claude_sonnet" else _StubConnector(name))

    conn = ai_bridge.get_grading_connector()

    assert conn is sentinel
    assert conn.supports("fallback") is True


def test_grading_connector_env_override_single_chain(monkeypatch) -> None:
    """ACTIVE_GRADING_MODEL 지정 시 폴백 래핑 없이 그 커넥터를 단일 체인으로 쓴다."""
    monkeypatch.setenv("ACTIVE_GRADING_MODEL", "opus46")
    ai_bridge._GRADING_CONNECTOR_CACHE.clear()
    monkeypatch.setattr(ai_bridge, "get_connector", lambda name: _StubConnector(name))

    conn = ai_bridge.get_grading_connector()
    assert conn.name == "opus46"
    assert conn.supports("fallback") is False
