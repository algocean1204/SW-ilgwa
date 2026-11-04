from __future__ import annotations

import logging
from typing import Protocol, runtime_checkable

from app.modules.ChapterStudio_V1.ai_connectors.base import AIConnector, TTSConnector

from app.modules.ChapterStudio_V1.ai_connectors.claude_sonnet_connector import ClaudeSonnetConnector
from app.modules.ChapterStudio_V1.ai_connectors.failover_connector import FailoverAIConnector
from app.modules.ChapterStudio_V1.ai_connectors.errors import ModelNotFoundError
from app.modules.ChapterStudio_V1.ai_connectors.kanana2_connector import Kanana2Connector
from app.modules.ChapterStudio_V1.ai_connectors.opus46_connector import Opus46Connector
from app.modules.ChapterStudio_V1.ai_connectors.qwen27b_modal_connector import Qwen27BModalConnector
from app.modules.ChapterStudio_V1.ai_connectors.tts_v1_connector import TTSV1Connector
from app.modules.ChapterStudio_V1.common.config import (
    active_planner_model,
    active_text_model,
    active_tts_model,
    active_verifier_model,
    text_fallback_after_failures,
    text_primary_attempt_timeout_sec,
)

Connector = AIConnector | TTSConnector


class ConnectorFactory(Protocol):
    def __call__(self) -> Connector:
        """테스트에서 mock class를 같은 형태로 주입할 수 있게 한다."""
        ...


@runtime_checkable
class AsyncCloseable(Protocol):
    async def aclose(self) -> None:
        """비동기 네트워크 리소스를 닫는다."""
        ...


# 기본 텍스트 경로는 qwen27b_sonnet_fallback(Modal → Claude Sonnet)이다.
# qwen27b_cloud_fallback(Modal → Gemini → Claude → OpenAI)은 환경변수로 고를 때만 쓴다.
# 튜터 TTS 체인은 tts_routing이 만든다. 기본은 Modal Qwen3-TTS 하나다.
_REGISTRY: dict[str, ConnectorFactory] = {
    "qwen27b_modal": Qwen27BModalConnector,
    "opus46": Opus46Connector,
    "tts_v1": TTSV1Connector,
    "claude_sonnet": ClaudeSonnetConnector,
    "gemini_tts": lambda: _build_gemini_tts_connector(),
    "qwen3_tts_modal": lambda: _build_qwen3_tts_connector(),
}
_CACHE: dict[str, Connector] = {}
_POLISH_CACHE: Kanana2Connector | None = None
_LOG = logging.getLogger(__name__)


def _build_failover_connector() -> FailoverAIConnector:
    """Qwen27B → Claude Sonnet 폴백 래퍼를 생성한다."""
    return FailoverAIConnector(
        primary_factory=Qwen27BModalConnector,
        fallback_factory=ClaudeSonnetConnector,
        name="qwen27b_sonnet_fallback",
        failure_threshold=text_fallback_after_failures(),
        primary_timeout_sec=text_primary_attempt_timeout_sec(),
    )


def _modal_cloud_fallback_factories() -> list[ConnectorFactory]:
    """Modal 주 커넥터 실패 시 Gemini → Claude Sonnet → OpenAI 순으로 시도한다.

    SDK 미설치(ImportError)는 해당 단계만 건너뛴다. AuthError(무효 키)는
    FailoverAIConnector 가 호출 시점에 해당 단계만 영구 제외한다.
    """
    from app.modules.ChapterStudio_V1.common.config import (
        claude_sonnet_api_key,
        google_api_key,
        openai_api_key,
        openai_fallback_enabled,
    )

    factories: list[ConnectorFactory] = []
    if google_api_key() is not None:
        try:
            from app.modules.ChapterStudio_V1.ai_connectors.gemini_genai_connector import (
                GeminiGenAIConnector,
            )

            factories.append(lambda: GeminiGenAIConnector())
        except ImportError:
            pass
    if claude_sonnet_api_key() is not None:
        factories.append(ClaudeSonnetConnector)
    if openai_fallback_enabled() and openai_api_key() is not None:
        try:
            from app.modules.ChapterStudio_V1.ai_connectors.openai_connector import (
                OpenAIConnector,
            )

            factories.append(OpenAIConnector)
        except ImportError:
            pass
    return factories


def _build_modal_cloud_fallback_connector() -> AIConnector:
    """Modal GPU 1순위 + Gemini → Claude → OpenAI 클라우드 폴백 체인을 만든다."""
    factories = _modal_cloud_fallback_factories()
    if not factories:
        _LOG.warning(
            "qwen27b_cloud_fallback: 클라우드 폴백 키가 없어 Modal 단독(qwen27b_modal)으로 동작한다."
        )
        return Qwen27BModalConnector()
    return FailoverAIConnector(
        primary_factory=Qwen27BModalConnector,
        fallback_factories=factories,
        name="qwen27b_cloud_fallback",
        failure_threshold=text_fallback_after_failures(),
        primary_timeout_sec=text_primary_attempt_timeout_sec(),
    )


def _build_gemini_tts_connector() -> TTSConnector:
    """레슨 TTS용 Gemini 어댑터는 선택 시점에만 로드한다."""
    from app.modules.ChapterStudio_V1.ai_connectors.gemini_tts_connector import (
        GeminiTTSChapterConnector,
    )

    return GeminiTTSChapterConnector()


def _build_qwen3_tts_connector() -> TTSConnector:
    """Modal Qwen3-TTS 어댑터는 선택 시점에만 로드한다."""
    from app.modules.ChapterStudio_V1.ai_connectors.qwen3_tts_connector import Qwen3TTSChapterConnector

    return Qwen3TTSChapterConnector()


def _build_gemini_genai_connector() -> AIConnector:
    """google-genai SDK 커넥터는 선택 시점에만 로드한다.

    폴백 체인은 Claude Sonnet → OpenAI 순이다(키 있는 단계만 포함):
    - Claude : CLAUDE_SONNET_API_KEY 또는 ANTHROPIC_API_KEY 존재 시.
    - OpenAI : OPENAI_API_KEY 존재 + OPENAI_FALLBACK_ENABLED!=false 일 때만.
    후보가 하나도 없으면 Gemini 커넥터를 그대로 돌려준다.
    ACTIVE_TEXT_MODEL=gemini_flash로 명시했을 때만 쓰는 경로다.
    """
    from app.modules.ChapterStudio_V1.ai_connectors.gemini_genai_connector import (
        GeminiGenAIConnector,
    )

    gemini = GeminiGenAIConnector()
    factories = _gemini_fallback_factories()
    if not factories:
        return gemini
    return FailoverAIConnector(
        primary_factory=lambda: gemini,
        fallback_factories=factories,
        name="gemini_openai_fallback",
        failure_threshold=text_fallback_after_failures(),
    )


def _gemini_fallback_factories() -> list[ConnectorFactory]:
    """Gemini 폴백 체인 팩토리 목록을 Claude Sonnet → OpenAI 순으로 만든다.

    SDK 미설치(ImportError)는 해당 단계만 건너뛴다. AuthError(무효 키)는
    FailoverAIConnector 가 호출 시점에 해당 단계만 영구 제외한다.
    """
    from app.modules.ChapterStudio_V1.common.config import (
        claude_sonnet_api_key,
        openai_api_key,
        openai_fallback_enabled,
    )

    factories: list[ConnectorFactory] = []
    if claude_sonnet_api_key() is not None:
        factories.append(ClaudeSonnetConnector)
    if openai_fallback_enabled() and openai_api_key() is not None:
        try:
            from app.modules.ChapterStudio_V1.ai_connectors.openai_connector import (
                OpenAIConnector,
            )

            factories.append(OpenAIConnector)
        except ImportError:
            pass
    return factories


# 폴백 래퍼는 팩토리 함수를 등록한다
_REGISTRY["qwen27b_sonnet_fallback"] = _build_failover_connector
_REGISTRY["qwen27b_cloud_fallback"] = _build_modal_cloud_fallback_connector
_REGISTRY["gemini_flash"] = _build_gemini_genai_connector


def get_connector(name: str) -> Connector:
    """커넥터 인스턴스를 한 번만 생성해 노드 호출 비용을 줄인다."""
    if name in _CACHE:
        return _CACHE[name]
    cls = _REGISTRY.get(name)
    if cls is None:
        raise ModelNotFoundError(f"{name} 커넥터가 등록되지 않았다.")
    instance = cls()
    _CACHE[name] = instance
    return instance


def get_text_connector() -> AIConnector:
    """환경 설정의 활성 텍스트 모델을 Protocol로 검증해 반환한다."""
    return _as_ai_connector(get_connector(active_text_model()))


def get_planner_connector() -> AIConnector:
    """Planner는 Opus 계열 커넥터만 AIConnector로 노출한다."""
    return _as_ai_connector(get_connector(active_planner_model()))


def get_verifier_connector() -> AIConnector:
    """내용 정확성 검증용 커넥터를 반환한다(기본 claude_sonnet, ACTIVE_VERIFIER_MODEL로 변경)."""
    return _as_ai_connector(get_connector(active_verifier_model()))


def get_tts_connector() -> TTSConnector:
    """TTS 합성 커넥터는 텍스트 생성 커넥터와 분리해 반환한다."""
    connector = get_connector(active_tts_model())
    if not isinstance(connector, TTSConnector):
        raise ModelNotFoundError("활성 TTS 커넥터가 TTSConnector가 아니다.")
    return connector


def get_polish_connector() -> Kanana2Connector:
    """Kanana2 교정 커넥터는 생성/검증 모델 레지스트리와 분리해 반환한다."""
    global _POLISH_CACHE
    if _POLISH_CACHE is None:
        _POLISH_CACHE = Kanana2Connector()
    return _POLISH_CACHE


def clear_cache() -> None:
    """테스트 격리를 위해 싱글톤 캐시를 비운다."""
    global _POLISH_CACHE
    _CACHE.clear()
    _POLISH_CACHE = None


async def close_all() -> None:
    """lifespan 종료 시 캐시된 커넥터 리소스를 닫고 캐시를 비운다."""
    for connector in list(_CACHE.values()):
        if isinstance(connector, AsyncCloseable):
            await connector.aclose()
    clear_cache()


def _as_ai_connector(connector: Connector) -> AIConnector:
    if not isinstance(connector, AIConnector):
        raise ModelNotFoundError("활성 커넥터가 AIConnector가 아니다.")
    return connector


__all__ = [
    "_CACHE",
    "_REGISTRY",
    "clear_cache",
    "close_all",
    "get_connector",
    "get_planner_connector",
    "get_polish_connector",
    "get_text_connector",
    "get_tts_connector",
    "get_verifier_connector",
]
