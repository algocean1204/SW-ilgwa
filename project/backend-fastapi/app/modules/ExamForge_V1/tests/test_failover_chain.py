"""ExamForge 폴백 체인 테스트.

기본 체인은 qwen27b_modal → claude_sonnet이다. Sonnet → Gemini는
EXAMFORGE_GEMINI_FALLBACK=true일 때만, Gemini → Sonnet은 gemini_flash를 고를 때만 쓴다.

- 요청마다 primary를 먼저 시도한다(요청 사이 상태 없음)
- primary 실패 → 폴백 성공
- AuthError·생성 실패 단계는 이후에도 빼고 다음 단계로 간다
- 전부 실패하면 마지막 에러를 올린다
- 키·플래그에 따른 체인 구성
"""
from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.modules.ExamForge_V1.common import ai_bridge
from app.modules.ExamForge_V1.common._ai_schemas import ChapterAIRequest, ChapterAIResponse
from app.modules.ExamForge_V1.common.errors import AuthError, ConnectorError, RateLimitError


def _req() -> ChapterAIRequest:
    return ChapterAIRequest(system="", user="체인 테스트", max_tokens=128, temperature=0.2)


class _StubConnector:
    """성공/실패를 제어할 수 있는 더미 커넥터(ExamForge generate 시그니처)."""

    def __init__(self, name: str, error: Exception | None = None) -> None:
        self.name = name
        self.error = error
        self.call_count = 0

    async def generate(self, req: ChapterAIRequest, budget=None) -> ChapterAIResponse:
        self.call_count += 1
        if self.error is not None:
            raise self.error
        return ChapterAIResponse(
            text=f"{self.name} 응답", model=self.name, input_tokens=1, output_tokens=1, finish_reason="stop"
        )

    def supports(self, feature: str) -> bool:
        return False


@pytest.mark.asyncio
async def test_sonnet_failure_falls_through_to_gemini() -> None:
    """primary(Claude) 실패 → Gemini 성공."""
    claude = _StubConnector("claude_sonnet", error=RateLimitError("429"))
    gemini = _StubConnector("gemini_flash")
    chain = ai_bridge._OpenAIFailoverConnector(
        claude, fallback_factories=[lambda: gemini]
    )

    response = await chain.generate(_req())

    assert response.text == "gemini_flash 응답"
    assert gemini.call_count == 1


@pytest.mark.asyncio
async def test_gemini_failure_falls_through_to_claude() -> None:
    """primary(Gemini) 실패 → Claude 성공."""
    gemini = _StubConnector("gemini_flash", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet")
    chain = ai_bridge._OpenAIFailoverConnector(
        gemini, fallback_factories=[lambda: claude]
    )

    response = await chain.generate(_req())

    assert response.text == "claude_sonnet 응답"
    assert claude.call_count == 1


@pytest.mark.asyncio
async def test_per_request_semantics_primary_retried_next_request() -> None:
    """요청 간 sticky 상태 없음: 다음 요청은 다시 primary부터 시도한다."""
    gemini = _StubConnector("gemini_flash", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet")
    chain = ai_bridge._OpenAIFailoverConnector(gemini, fallback_factories=[lambda: claude])

    await chain.generate(_req())
    gemini.error = None  # primary 회복
    response = await chain.generate(_req())

    assert response.text == "gemini_flash 응답"
    assert gemini.call_count == 2
    assert claude.call_count == 1


@pytest.mark.asyncio
async def test_auth_error_stage_is_skipped_permanently() -> None:
    """AuthError 단계는 영구 제외되고 다음 요청부터 호출되지 않는다."""
    gemini = _StubConnector("gemini_flash", error=RateLimitError("429"))
    broken = _StubConnector("claude_sonnet", error=AuthError("invalid key"))
    claude = _StubConnector("claude_sonnet")
    chain = ai_bridge._OpenAIFailoverConnector(
        gemini, fallback_factories=[lambda: broken, lambda: claude]
    )

    first = await chain.generate(_req())
    second = await chain.generate(_req())

    assert first.text == "claude_sonnet 응답"
    assert second.text == "claude_sonnet 응답"
    assert broken.call_count == 1


@pytest.mark.asyncio
async def test_stage_construction_failure_skips_to_next() -> None:
    """폴백 생성자가 RuntimeError(키 누락)를 올리면 그 단계만 건너뛴다."""
    gemini = _StubConnector("gemini_flash", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet")

    def _broken_factory() -> _StubConnector:
        raise RuntimeError("claude_sonnet: ANTHROPIC_API_KEY 필요")

    chain = ai_bridge._OpenAIFailoverConnector(
        gemini, fallback_factories=[_broken_factory, lambda: claude]
    )

    response = await chain.generate(_req())

    assert response.text == "claude_sonnet 응답"


@pytest.mark.asyncio
async def test_all_stages_fail_raises_last_error() -> None:
    """전부 실패하면 마지막 단계의 에러가 그대로 전파된다."""
    gemini = _StubConnector("gemini_flash", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet", error=ConnectorError("claude down"))
    chain = ai_bridge._OpenAIFailoverConnector(
        gemini, fallback_factories=[lambda: claude]
    )

    with pytest.raises(ConnectorError, match="claude down"):
        await chain.generate(_req())


@pytest.mark.asyncio
async def test_skips_exhausted_claude_primary(monkeypatch: pytest.MonkeyPatch) -> None:
    """claude_sonnet 소진 기록이 있으면 primary를 건너뛰고 폴백 체인부터 시도한다."""
    from app.modules.ExamForge_V1.common import _provider_health as health

    health.reset()
    health.mark_exhausted("claude_sonnet", reason="billing depleted", ttl_sec=600)
    claude = _StubConnector("claude_sonnet")
    gemini = _StubConnector("gemini_flash")
    chain = ai_bridge._OpenAIFailoverConnector(claude, fallback_factories=[lambda: gemini])

    response = await chain.generate(_req())

    assert response.text == "gemini_flash 응답"
    assert claude.call_count == 0
    assert gemini.call_count == 1
    health.reset()


@pytest.mark.asyncio
async def test_skips_exhausted_gemini_primary(monkeypatch: pytest.MonkeyPatch) -> None:
    """gemini_flash 소진 기록이 있으면 primary를 건너뛰고 폴백 체인부터 시도한다."""
    from app.modules.ExamForge_V1.common import _provider_health as health

    health.reset()
    health.mark_exhausted("gemini_flash", reason="billing depleted", ttl_sec=600)
    gemini = _StubConnector("gemini_flash")
    claude = _StubConnector("claude_sonnet")
    chain = ai_bridge._OpenAIFailoverConnector(gemini, fallback_factories=[lambda: claude])

    response = await chain.generate(_req())

    assert response.text == "claude_sonnet 응답"
    assert gemini.call_count == 0
    assert claude.call_count == 1
    health.reset()


@pytest.mark.asyncio
async def test_single_factory_signature_still_works() -> None:
    """기존 단일 fallback_factory 시그니처는 길이 1짜리 체인으로 동작한다(하위 호환)."""
    gemini = _StubConnector("gemini_flash", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet")
    chain = ai_bridge._OpenAIFailoverConnector(gemini, lambda: claude)

    response = await chain.generate(_req())

    assert response.text == "claude_sonnet 응답"


@pytest.fixture(autouse=True)
def isolate_fallback_env(monkeypatch: pytest.MonkeyPatch) -> None:
    """폴백 키와 플래그를 지워 테스트마다 직접 넣은 값만 쓰게 한다."""
    for key in (
        "OPENAI_API_KEY",
        "OPENAI_FALLBACK_ENABLED",
        "ANTHROPIC_API_KEY",
        "CLAUDE_SONNET_API_KEY",
        "EXAMFORGE_GEMINI_FALLBACK",
    ):
        monkeypatch.delenv(key, raising=False)


def test_stage_factories_include_claude_only(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")

    factories = ai_bridge._fallback_stage_factories()

    assert len(factories) == 1
    connector = factories[0]()
    assert getattr(connector, "name", "") == "claude_sonnet"


def test_stage_factories_claude_from_sonnet_key_only(monkeypatch: pytest.MonkeyPatch) -> None:
    """ANTHROPIC_API_KEY 없이 CLAUDE_SONNET_API_KEY 만 있어도 Claude 폴백이 포함된다."""
    monkeypatch.setenv("CLAUDE_SONNET_API_KEY", "claude-only-key")

    factories = ai_bridge._fallback_stage_factories()

    assert len(factories) == 1
    connector = factories[0]()
    assert getattr(connector, "name", "") == "claude_sonnet"


def test_stage_factories_empty_without_any_key() -> None:
    assert ai_bridge._fallback_stage_factories() == []


def test_stage_factories_ignores_openai_key(monkeypatch: pytest.MonkeyPatch) -> None:
    """ExamForge 폴백 체인에는 OpenAI 단계가 포함되지 않는다."""
    monkeypatch.setenv("OPENAI_API_KEY", "openai-key")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")

    factories = ai_bridge._fallback_stage_factories()

    assert len(factories) == 1
    connector = factories[0]()
    assert getattr(connector, "name", "") == "claude_sonnet"


class _FakeGeminiModels:
    def generate_content(self, *, model: str, contents: str, config: object) -> object:
        return SimpleNamespace(text="본문", usage_metadata=None, candidates=[])


class _FakeGeminiClient:
    def __init__(self, api_key: str) -> None:
        self.api_key = api_key
        self.models = _FakeGeminiModels()

    def close(self) -> None:
        return None


def test_build_returns_raw_gemini_without_fallback_keys(monkeypatch: pytest.MonkeyPatch) -> None:
    """폴백 키가 없으면 래퍼 없는 Gemini 커넥터를 그대로 쓴다."""
    from app.modules.ExamForge_V1.common import _connector_gemini_genai as gemini_module

    monkeypatch.setattr(gemini_module.genai, "Client", _FakeGeminiClient)
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")

    connector = ai_bridge._build_gemini_genai_connector()

    assert not isinstance(connector, ai_bridge._OpenAIFailoverConnector)


def test_build_wraps_with_chain_when_claude_key_set(monkeypatch: pytest.MonkeyPatch) -> None:
    """Claude 키가 있으면 Gemini 경로를 폴백 래퍼로 감싼다."""
    from app.modules.ExamForge_V1.common import _connector_gemini_genai as gemini_module

    monkeypatch.setattr(gemini_module.genai, "Client", _FakeGeminiClient)
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")

    connector = ai_bridge._build_gemini_genai_connector()

    assert isinstance(connector, ai_bridge._OpenAIFailoverConnector)
    assert len(connector._fallback_factories) == 1


def test_build_claude_wraps_with_gemini_when_google_key_set(monkeypatch: pytest.MonkeyPatch) -> None:
    """EXAMFORGE_GEMINI_FALLBACK=true와 Google 키가 있으면 Sonnet을 Gemini 폴백 래퍼로 감싼다."""
    from app.modules.ExamForge_V1.common import _connector_gemini_genai as gemini_module

    monkeypatch.setattr(gemini_module.genai, "Client", _FakeGeminiClient)
    monkeypatch.setenv("EXAMFORGE_GEMINI_FALLBACK", "true")
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")

    connector = ai_bridge._build_claude_sonnet_connector()

    assert isinstance(connector, ai_bridge._OpenAIFailoverConnector)
    assert connector._primary.name == "claude_sonnet"  # noqa: SLF001
    assert len(connector._fallback_factories) == 1


def test_build_claude_returns_raw_without_google_key(monkeypatch: pytest.MonkeyPatch) -> None:
    """Google 키가 없으면 래퍼 없는 Claude 커넥터를 그대로 쓴다."""
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)

    connector = ai_bridge._build_claude_sonnet_connector()

    assert not isinstance(connector, ai_bridge._OpenAIFailoverConnector)
    assert connector.name == "claude_sonnet"


def test_gemini_fallback_stage_factories_include_gemini_only(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("EXAMFORGE_GEMINI_FALLBACK", "true")
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")

    factories = ai_bridge._gemini_fallback_stage_factories()

    assert len(factories) == 1
    connector = factories[0]()
    assert getattr(connector, "name", "") == "gemini_flash"


def test_active_text_provider_chain_sonnet_then_gemini(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("EXAMFORGE_ACTIVE_TEXT_MODEL", "claude_sonnet")
    monkeypatch.setenv("EXAMFORGE_GEMINI_FALLBACK", "true")
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")

    assert ai_bridge.active_text_provider_chain() == ["claude_sonnet", "gemini_flash"]


def test_active_text_provider_chain_gemini_then_claude_legacy(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("EXAMFORGE_ACTIVE_TEXT_MODEL", "gemini_flash")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")

    assert ai_bridge.active_text_provider_chain() == ["gemini_flash", "claude_sonnet"]