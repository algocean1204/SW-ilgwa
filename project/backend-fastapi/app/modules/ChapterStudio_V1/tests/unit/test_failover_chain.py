"""FailoverAIConnector 다단 폴백 체인(Claude Sonnet → OpenAI)을 검증한다.

- 체인 순회: 주 실패 → 폴백1 실패 → 폴백2 성공
- AuthError 단계는 영구 제외한다(키가 무효인 폴백이 요청마다 실패 로그를 남기지 않게)
- 전부 실패 시 마지막 에러 전파
- registry가 키 유무에 따라 체인을 올바르게 구성하는지
"""
from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.modules.ChapterStudio_V1.ai_connectors import registry as registry_module
from app.modules.ChapterStudio_V1.ai_connectors import gemini_genai_connector as gemini_module
from app.modules.ChapterStudio_V1.ai_connectors.errors import AuthError, ConnectorError, RateLimitError
from app.modules.ChapterStudio_V1.ai_connectors.failover_connector import FailoverAIConnector
from app.modules.ChapterStudio_V1.ai_connectors.schemas import ChapterAIRequest, ChapterAIResponse

pytestmark = pytest.mark.anyio


def _req() -> ChapterAIRequest:
    return ChapterAIRequest(user="체인 테스트", max_tokens=128, temperature=0.2)


class _StubConnector:
    """성공/실패를 제어할 수 있는 더미 커넥터."""

    def __init__(self, name: str, error: ConnectorError | None = None) -> None:
        self.name = name
        self.error = error
        self.call_count = 0

    async def generate(self, req: ChapterAIRequest) -> ChapterAIResponse:
        self.call_count += 1
        if self.error is not None:
            raise self.error
        return ChapterAIResponse(
            text=f"{self.name} 응답", model=self.name, input_tokens=1, output_tokens=1, finish_reason="stop"
        )

    async def generate_batch(self, reqs: list[ChapterAIRequest]) -> list[ChapterAIResponse]:
        return [await self.generate(req) for req in reqs]

    def supports(self, feature: str) -> bool:
        return False


async def test_chain_falls_through_claude_to_openai() -> None:
    """주(429) → 폴백1(타임아웃) → 폴백2(성공) 순으로 체인이 진행된다."""
    primary = _StubConnector("gemini", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet", error=ConnectorError("claude down"))
    openai = _StubConnector("openai_text")
    chain = FailoverAIConnector(
        primary_factory=lambda: primary,
        fallback_factories=[lambda: claude, lambda: openai],
        name="gemini_openai_fallback",
        failure_threshold=1,
    )

    response = await chain.generate(_req())

    assert response.text == "openai_text 응답"
    assert claude.call_count == 1
    assert openai.call_count == 1


async def test_chain_skips_auth_error_stage_permanently() -> None:
    """AuthError 폴백은 영구 제외돼 다음 요청부터 호출되지 않는다."""
    primary = _StubConnector("gemini", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet", error=AuthError("invalid key"))
    openai = _StubConnector("openai_text")
    chain = FailoverAIConnector(
        primary_factory=lambda: primary,
        fallback_factories=[lambda: claude, lambda: openai],
        name="gemini_openai_fallback",
        failure_threshold=1,
    )

    first = await chain.generate(_req())
    second = await chain.generate(_req())

    assert first.text == "openai_text 응답"
    assert second.text == "openai_text 응답"
    assert claude.call_count == 1  # 영구 제외되어 두 번째 요청에서는 다시 호출하지 않는다.
    assert openai.call_count == 2


async def test_chain_skips_stage_whose_construction_raises_auth_error() -> None:
    """폴백 생성자가 AuthError(키 미설정)를 올리면 그 단계만 건너뛴다."""
    primary = _StubConnector("gemini", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet")

    def _broken_factory() -> _StubConnector:
        raise AuthError("OPENAI_API_KEY 필요")

    chain = FailoverAIConnector(
        primary_factory=lambda: primary,
        fallback_factories=[_broken_factory, lambda: claude],
        name="gemini_openai_fallback",
        failure_threshold=1,
    )

    response = await chain.generate(_req())

    assert response.text == "claude_sonnet 응답"


async def test_chain_raises_last_error_when_all_stages_fail() -> None:
    """전 단계 실패 시 마지막 에러가 그대로 전파된다(조용한 빈 응답 금지)."""
    primary = _StubConnector("gemini", error=RateLimitError("429"))
    claude = _StubConnector("claude_sonnet", error=ConnectorError("claude down"))
    openai = _StubConnector("openai_text", error=ConnectorError("openai down"))
    chain = FailoverAIConnector(
        primary_factory=lambda: primary,
        fallback_factories=[lambda: claude, lambda: openai],
        name="gemini_openai_fallback",
        failure_threshold=1,
    )

    with pytest.raises(ConnectorError, match="openai down"):
        await chain.generate(_req())


async def test_modal_cloud_chain_falls_through_gemini_claude_openai() -> None:
    """주(Modal) → 폴백1(Gemini) → 폴백2(Claude) → 폴백3(OpenAI) 순으로 체인이 진행된다."""
    modal = _StubConnector("qwen27b_modal", error=RateLimitError("modal down"))
    gemini = _StubConnector("gemini_flash", error=ConnectorError("gemini down"))
    claude = _StubConnector("claude_sonnet", error=ConnectorError("claude down"))
    openai = _StubConnector("openai_text")
    chain = FailoverAIConnector(
        primary_factory=lambda: modal,
        fallback_factories=[lambda: gemini, lambda: claude, lambda: openai],
        name="qwen27b_cloud_fallback",
        failure_threshold=1,
    )

    response = await chain.generate(_req())

    assert response.text == "openai_text 응답"
    assert gemini.call_count == 1
    assert claude.call_count == 1
    assert openai.call_count == 1


def test_registry_builds_modal_cloud_fallback_chain(
    monkeypatch: pytest.MonkeyPatch, isolated_registry: None
) -> None:
    """Gemini·Claude·OpenAI 키가 모두 있으면 Modal 폴백 체인이 3단으로 구성된다."""
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")
    monkeypatch.setenv("OPENAI_API_KEY", "openai-key")

    connector = registry_module.get_connector("qwen27b_cloud_fallback")

    assert isinstance(connector, FailoverAIConnector)
    assert connector.name == "qwen27b_cloud_fallback"
    assert len(connector._fallback_factories) == 3


def test_registry_modal_cloud_fallback_order_is_gemini_claude_openai(
    monkeypatch: pytest.MonkeyPatch, isolated_registry: None
) -> None:
    """폴백 순서가 Gemini → Claude → OpenAI 인지 인스턴스 이름으로 검증한다."""
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")
    monkeypatch.setenv("CLAUDE_SONNET_API_KEY", "claude-key")
    monkeypatch.setenv("OPENAI_API_KEY", "openai-key")

    connector = registry_module.get_connector("qwen27b_cloud_fallback")
    assert isinstance(connector, FailoverAIConnector)

    names = [fb.name for _index, fb in connector._iter_fallbacks()]
    assert names == ["gemini_flash", "claude_sonnet", "openai_text"]


async def test_single_fallback_factory_signature_still_works() -> None:
    """기존 단일 fallback_factory 시그니처는 길이 1짜리 체인으로 동작한다(하위 호환)."""
    primary = _StubConnector("qwen", error=RateLimitError("429"))
    sonnet = _StubConnector("claude_sonnet")
    chain = FailoverAIConnector(
        primary_factory=lambda: primary,
        fallback_factory=lambda: sonnet,
        name="qwen27b_sonnet_fallback",
        failure_threshold=1,
    )

    response = await chain.generate(_req())

    assert response.text == "claude_sonnet 응답"
    assert chain.fallback_name == "claude_sonnet"


class _FakeGeminiModels:
    def generate_content(self, *, model: str, contents: str, config: object) -> object:
        return SimpleNamespace(text="본문", usage_metadata=None, candidates=[])


class _FakeGeminiClient:
    def __init__(self, api_key: str) -> None:
        self.api_key = api_key
        self.models = _FakeGeminiModels()

    def close(self) -> None:
        return None


@pytest.fixture()
def isolated_registry(monkeypatch: pytest.MonkeyPatch):
    """레지스트리 캐시·폴백 키 환경을 격리하고 Gemini 클라이언트를 가짜로 바꾼다."""
    for key in ("OPENAI_API_KEY", "OPENAI_FALLBACK_ENABLED", "ANTHROPIC_API_KEY", "CLAUDE_SONNET_API_KEY"):
        monkeypatch.delenv(key, raising=False)
    monkeypatch.setattr(gemini_module.genai, "Client", _FakeGeminiClient)
    monkeypatch.setenv("GOOGLE_API_KEY", "gemini-key")
    registry_module.clear_cache()
    yield
    registry_module.clear_cache()


def test_registry_builds_claude_then_openai_chain(
    monkeypatch: pytest.MonkeyPatch, isolated_registry: None
) -> None:
    """Claude·OpenAI 키가 모두 있으면 폴백 체인이 Claude → OpenAI 순으로 구성된다."""
    monkeypatch.setenv("OPENAI_API_KEY", "openai-key")
    monkeypatch.setenv("CLAUDE_SONNET_API_KEY", "claude-key")

    connector = registry_module.get_connector("gemini_flash")

    assert isinstance(connector, FailoverAIConnector)
    assert len(connector._fallback_factories) == 2


def test_registry_wraps_with_claude_only_when_openai_key_unset(
    monkeypatch: pytest.MonkeyPatch, isolated_registry: None
) -> None:
    """OPENAI_API_KEY가 없어도 Claude 키가 있으면 Claude 단독 폴백으로 감싼다."""
    monkeypatch.setenv("ANTHROPIC_API_KEY", "anthropic-key")

    connector = registry_module.get_connector("gemini_flash")

    assert isinstance(connector, FailoverAIConnector)
    assert len(connector._fallback_factories) == 1


def test_registry_returns_raw_gemini_when_no_fallback_keys(
    monkeypatch: pytest.MonkeyPatch, isolated_registry: None
) -> None:
    """폴백 후보 키가 하나도 없으면 raw Gemini 커넥터를 반환한다(기존 동작)."""
    connector = registry_module.get_connector("gemini_flash")

    assert not isinstance(connector, FailoverAIConnector)
    assert getattr(connector, "name", "") == "gemini_flash"


def test_registry_kill_switch_excludes_openai_but_keeps_claude(
    monkeypatch: pytest.MonkeyPatch, isolated_registry: None
) -> None:
    """OPENAI_FALLBACK_ENABLED=false면 OpenAI만 체인에서 빠진다."""
    monkeypatch.setenv("OPENAI_API_KEY", "openai-key")
    monkeypatch.setenv("OPENAI_FALLBACK_ENABLED", "false")
    monkeypatch.setenv("CLAUDE_SONNET_API_KEY", "claude-key")

    connector = registry_module.get_connector("gemini_flash")

    assert isinstance(connector, FailoverAIConnector)
    assert len(connector._fallback_factories) == 1
