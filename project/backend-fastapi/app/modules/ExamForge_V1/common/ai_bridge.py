"""AI 커넥터 팩토리와 공개 API. 커넥터 구현은 _connector_*.py에 있다.

기본 체인: 문제 생성·출제 계획은 qwen27b_modal → claude_sonnet,
검증·채점은 claude_sonnet 단독이다. Gemini·OpenAI는 기본 체인에 없다.
gemini_flash·opus46은 환경 변수로 고를 때만 쓴다.
"""
from __future__ import annotations

import asyncio
import contextvars
import inspect
import logging
from collections.abc import Awaitable, Callable, Sequence
from typing import TypeVar

from app.modules.ExamForge_V1.common._ai_schemas import (
    AIConnector,
    ChapterAIRequest,
    ChapterAIResponse,
    LLMBudgetCounter,
    allow_reserve_budget,
    current_budget,
)
from app.modules.ExamForge_V1.common._connector_anthropic import AnthropicConnector
from app.modules.ExamForge_V1.common.config import (
    active_grading_model,
    active_planner_model,
    active_text_model,
    active_verifier_model,
    claude_sonnet_api_key,
    examforge_gemini_fallback_enabled,
    google_api_key,
)
from app.modules.ExamForge_V1.common.errors import AuthError, ConnectorError

_LOG = logging.getLogger(__name__)

_ANTHROPIC_MODEL_MAP: dict[str, str] = {
    "opus46": "claude-opus-4-6",
    "claude_sonnet": "claude-sonnet-4-6",
}

_CONNECTOR_CACHE: dict[str, AIConnector] = {}
_current_budget = current_budget
_TaskResult = TypeVar("_TaskResult")


class _Qwen27BModalExamForgeAdapter:
    """ChapterStudio Modal 커넥터를 ExamForge 스키마로 감싸는 어댑터."""

    name: str = "qwen27b_modal"

    def __init__(self) -> None:
        from app.modules.ChapterStudio_V1.ai_connectors.qwen27b_modal_connector import (
            Qwen27BModalConnector,
        )

        self._connector: Qwen27BModalConnector = Qwen27BModalConnector()

    async def generate(
        self,
        req: ChapterAIRequest,
        budget: LLMBudgetCounter | None = None,
    ) -> ChapterAIResponse:
        """ChapterStudio 응답을 ExamForge 응답 타입으로 바꾼다.

        다른 커넥터처럼 호출 전 budget.check(), 성공 후 increment()로 시험당 상한을 지킨다.
        budget 인자가 없으면 current_budget contextvar를 쓴다.
        ChapterStudio 예외는 ExamForge 예외로 바꿔 폴백 래퍼가 Claude로 넘어가게 한다.
        """
        from app.modules.ChapterStudio_V1.ai_connectors import errors as studio_errors
        from app.modules.ChapterStudio_V1.ai_connectors.schemas import (
            ChapterAIRequest as StudioAIRequest,
        )

        if budget is None:
            budget = current_budget.get()
        if budget is not None:
            budget.check()

        studio_req = StudioAIRequest(
            system=req.system,
            user=req.user,
            max_tokens=req.max_tokens,
            temperature=req.temperature,
            extra=req.extra,
        )
        try:
            resp = await self._connector.generate(studio_req)
        except studio_errors.AuthError as exc:
            raise AuthError(str(exc)) from exc
        except studio_errors.ConnectorError as exc:
            raise ConnectorError(str(exc)) from exc
        if budget is not None:
            budget.increment()
        return ChapterAIResponse(
            text=resp.text,
            model=resp.model,
            input_tokens=resp.input_tokens,
            output_tokens=resp.output_tokens,
            finish_reason=resp.finish_reason,
        )

    def supports(self, feature: str) -> bool:
        """Modal B200 경로는 문항별 병렬 실행을 허용한다."""
        if feature == "batch":
            return True
        return self._connector.supports(feature)


def set_current_budget(budget: LLMBudgetCounter) -> contextvars.Token:
    """현재 파이프라인 실행의 예산 카운터를 설정한다."""
    return current_budget.set(budget)


def get_current_budget() -> LLMBudgetCounter | None:
    """현재 파이프라인 실행의 예산 카운터를 반환한다."""
    return current_budget.get()


def set_allow_reserve(value: bool) -> contextvars.Token:
    """해설 생성 구간에서 reserve 예산 사용을 허용/해제한다."""
    return allow_reserve_budget.set(value)


def get_allow_reserve() -> bool:
    """현재 호출이 reserve 예산을 끌어쓸 수 있는지 반환한다."""
    return allow_reserve_budget.get()


def get_connector(name: str) -> AIConnector:
    """이름으로 커넥터를 만들고 캐시한다."""
    if name in _CONNECTOR_CACHE:
        return _CONNECTOR_CACHE[name]

    connector: AIConnector
    if name == "qwen27b_modal":
        connector = _build_qwen27b_modal_connector()
    elif name == "gemini_flash":
        connector = _build_gemini_genai_connector()
    elif name == "claude_sonnet":
        connector = _build_claude_sonnet_connector()
    elif name in _ANTHROPIC_MODEL_MAP:
        connector = AnthropicConnector(_ANTHROPIC_MODEL_MAP[name], name)
    else:
        raise RuntimeError(f"'{name}' 커넥터가 등록되지 않았다.")

    _CONNECTOR_CACHE[name] = connector
    return connector


async def run_connector_tasks(
    task_factories: Sequence[Callable[[], Awaitable[_TaskResult]]],
    connector: AIConnector,
) -> list[_TaskResult | Exception]:
    """batch 지원 여부와 관계없이 asyncio.gather로 병렬 실행한다.

    동시성 상한은 각 팩토리의 Semaphore(generation_concurrency())가 정한다.
    gather는 입력 순서대로 결과를 돌려준다. 예산 카운터는 단일 이벤트 루프에서
    check/increment 사이에 await가 없어 경합이 없다.
    """
    return list(
        await asyncio.gather(
            *(factory() for factory in task_factories),
            return_exceptions=True,
        )
    )


def connector_supports_batch(connector: AIConnector) -> bool:
    """동기 supports 계약만 batch 지원으로 인정한다."""
    supported = connector.supports("batch")
    if inspect.isawaitable(supported):
        closer = getattr(supported, "close", None)
        if callable(closer):
            closer()
        return False
    return supported is True


class _OpenAIFailoverConnector:
    """요청마다 primary를 먼저 쓰고, ConnectorError면 폴백 체인을 순서대로 한 번씩 시도한다.

    요청 사이에 상태를 남기지 않아 다음 요청은 다시 primary부터 시작한다.
    AuthError(키 무효·미설정)가 난 단계는 이후 요청에서도 뺀다.
    budget은 각 커넥터가 check/increment하므로 그대로 넘긴다.
    """

    name: str = "examforge_failover"

    def __init__(
        self,
        primary: AIConnector,
        fallback_factory: Callable[[], AIConnector] | None = None,
        *,
        fallback_factories: Sequence[Callable[[], AIConnector]] | None = None,
    ) -> None:
        # 단일 fallback_factory는 길이 1짜리 체인으로 받는다.
        if fallback_factories is None:
            if fallback_factory is None:
                raise ValueError("fallback_factory 또는 fallback_factories 가 필요하다.")
            fallback_factories = (fallback_factory,)
        elif fallback_factory is not None:
            raise ValueError("fallback_factory 와 fallback_factories 는 동시에 줄 수 없다.")
        self._primary = primary
        self._fallback_factories: list[Callable[[], AIConnector]] = list(fallback_factories)
        self._fallbacks: list[AIConnector | None] = [None] * len(self._fallback_factories)
        # AuthError나 생성 실패로 뺀 폴백 인덱스
        self._skipped: set[int] = set()

    async def generate(
        self,
        req: ChapterAIRequest,
        budget: LLMBudgetCounter | None = None,
    ) -> ChapterAIResponse:
        from app.modules.ExamForge_V1.common import _provider_health as health

        last_error: Exception = ConnectorError("폴백 체인 진입")
        primary_name = getattr(self._primary, "name", "")
        skip_primary = primary_name in {"gemini_flash", "claude_sonnet"} and health.is_exhausted(
            primary_name
        )

        if not skip_primary:
            try:
                return await self._primary.generate(req, budget)
            except ConnectorError as exc:
                last_error = exc
        else:
            _LOG.info(
                "%s: %s 소진 기록 — 폴백 체인으로 바로 진행",
                self.name,
                primary_name,
            )

        for index in range(len(self._fallback_factories)):
            connector = self._connector_at(index)
            if connector is None:
                continue
            stage_name = getattr(connector, "name", "fallback")
            if health.is_exhausted(stage_name):
                _LOG.debug("%s: 폴백 %s 소진 기록 — 단계 건너뜀", self.name, stage_name)
                continue
            try:
                response = await connector.generate(req, budget)
                _LOG.info("%s: 폴백 %s 성공", self.name, stage_name)
                return response
            except AuthError as exc:
                self._skipped.add(index)
                last_error = exc
                _LOG.warning(
                    "%s: 폴백 %s AuthError — 영구 제외하고 다음 단계 진행: %s",
                    self.name,
                    stage_name,
                    exc,
                )
            except ConnectorError as exc:
                last_error = exc
                _LOG.warning(
                    "%s: 폴백 %s 실패 — 다음 단계 시도: %s",
                    self.name,
                    stage_name,
                    exc,
                )
            except RuntimeError as exc:
                # Anthropic 커넥터는 생성·호출 실패를 RuntimeError로 올린다.
                last_error = ConnectorError(str(exc))
                _LOG.warning(
                    "%s: 폴백 %s RuntimeError — 다음 단계 시도: %s",
                    self.name,
                    stage_name,
                    exc,
                )
        # 모든 단계가 실패하면 빈 응답 대신 마지막 에러를 올린다.
        raise last_error

    def _connector_at(self, index: int) -> AIConnector | None:
        """index 단계 커넥터를 처음 쓸 때 만든다. 생성이 실패하면 그 단계만 뺀다."""
        if index in self._skipped:
            return None
        connector = self._fallbacks[index]
        if connector is None:
            try:
                connector = self._fallback_factories[index]()
            except (ConnectorError, RuntimeError) as exc:
                # AnthropicConnector는 키가 없으면 RuntimeError를 올린다.
                self._skipped.add(index)
                _LOG.warning(
                    "%s: 폴백 커넥터 생성 실패(index=%d) — 영구 제외: %s",
                    self.name,
                    index,
                    exc,
                )
                return None
            self._fallbacks[index] = connector
        return connector

    def supports(self, feature: str) -> bool:
        if feature == "fallback":
            return True
        return self._primary.supports(feature)


def _raw_gemini_connector() -> AIConnector:
    """폴백 래퍼 없는 Gemini 커넥터."""
    from app.modules.ExamForge_V1.common._connector_gemini_genai import (
        GeminiGenAIConnector,
    )

    return GeminiGenAIConnector()


def _raw_claude_sonnet_connector() -> AIConnector:
    """폴백 래퍼 없는 Claude Sonnet 커넥터."""
    return AnthropicConnector(_ANTHROPIC_MODEL_MAP["claude_sonnet"], "claude_sonnet")


def _build_qwen27b_modal_connector() -> AIConnector:
    """Modal Qwen3.6-27B → Claude Sonnet 체인. Claude 키가 없으면 Modal 단독이다."""
    qwen = _Qwen27BModalExamForgeAdapter()
    factories = _claude_fallback_stage_factories()
    if not factories:
        return qwen
    return _OpenAIFailoverConnector(qwen, fallback_factories=factories)


def _build_claude_sonnet_connector() -> AIConnector:
    """Claude Sonnet 커넥터. EXAMFORGE_GEMINI_FALLBACK=true일 때만 Gemini 폴백을 붙인다."""
    sonnet = _raw_claude_sonnet_connector()
    factories = _gemini_fallback_stage_factories()
    if not factories:
        return sonnet
    return _OpenAIFailoverConnector(sonnet, fallback_factories=factories)


def _gemini_fallback_stage_factories() -> list[Callable[[], AIConnector]]:
    """Sonnet 뒤 Gemini 폴백 단계. 플래그와 Google 키가 모두 있을 때만 넣는다."""
    factories: list[Callable[[], AIConnector]] = []
    if examforge_gemini_fallback_enabled() and google_api_key() is not None:
        try:
            factories.append(_raw_gemini_connector)
        except ImportError:
            pass
    return factories


def _build_gemini_genai_connector() -> AIConnector:
    """Gemini primary 경로(명시 선택용). 폴백은 Claude Sonnet 한 단계다."""
    gemini = _raw_gemini_connector()
    factories = _claude_fallback_stage_factories()
    if not factories:
        return gemini
    return _OpenAIFailoverConnector(gemini, fallback_factories=factories)


def _claude_fallback_stage_factories() -> list[Callable[[], AIConnector]]:
    """Claude Sonnet 폴백 단계. Claude 키가 있을 때만 넣는다."""
    factories: list[Callable[[], AIConnector]] = []
    if claude_sonnet_api_key() is not None:
        factories.append(_raw_claude_sonnet_connector)
    return factories


def _fallback_stage_factories() -> list[Callable[[], AIConnector]]:
    """_claude_fallback_stage_factories의 기존 이름."""
    return _claude_fallback_stage_factories()


def active_text_provider_chain() -> list[str]:
    """활성 문제 생성 경로가 실제로 시도하는 프로바이더 이름 목록.

    생성 전 사전점검이 이 목록이 전부 소진됐는지 본다.

    - qwen27b_modal: qwen27b_modal → claude_sonnet(Claude 키가 있을 때)
    - claude_sonnet: claude_sonnet → gemini_flash(Gemini 폴백 플래그와 키가 있을 때)
    - gemini_flash: gemini_flash → claude_sonnet(Claude 키가 있을 때)
    - 그 외(opus46 등)는 한 단계다.

    빌더와 같은 키·플래그 규칙을 써서, 시도하지 않는 단계 때문에 잘못 막지 않게 한다.
    """
    name = active_text_model()
    if name == "qwen27b_modal":
        chain = ["qwen27b_modal"]
        if claude_sonnet_api_key() is not None:
            chain.append("claude_sonnet")
        return chain
    if name == "claude_sonnet":
        chain = ["claude_sonnet"]
        if _gemini_fallback_stage_factories():
            chain.append("gemini_flash")
        return chain
    if name == "gemini_flash":
        chain = ["gemini_flash"]
        if claude_sonnet_api_key() is not None:
            chain.append("claude_sonnet")
        return chain
    return [name]


def text_providers_all_exhausted() -> bool:
    """활성 체인이 비어 있지 않고 전부 소진 상태면 True다.

    하나라도 살아 있거나 체인을 모르면 False를 돌려 정상 생성을 막지 않는다.
    """
    from app.modules.ExamForge_V1.common import _provider_health as health

    return health.all_exhausted(active_text_provider_chain())


def get_text_connector() -> AIConnector:
    """문제 생성 커넥터."""
    return get_connector(active_text_model())


def get_planner_connector() -> AIConnector:
    """출제 계획 커넥터."""
    return get_connector(active_planner_model())


def get_verifier_connector() -> AIConnector:
    """정답 검증 커넥터."""
    return get_connector(active_verifier_model())


# 채점·총평 커넥터 캐시. 문제 생성 캐시(_CONNECTOR_CACHE)와 따로 둔다.
_GRADING_CONNECTOR_CACHE: dict[str, AIConnector] = {}


def get_grading_connector() -> AIConnector:
    """모의고사 채점·총평 커넥터.

    기본은 claude_sonnet이고 Gemini 폴백은 EXAMFORGE_GEMINI_FALLBACK=true일 때만 붙는다.
    ACTIVE_GRADING_MODEL로 다른 커넥터를 고르면 그 커넥터 하나만 쓴다.
    """
    name = active_grading_model()
    cached = _GRADING_CONNECTOR_CACHE.get(name)
    if cached is not None:
        return cached
    if name == "claude_sonnet":
        # get_connector("claude_sonnet") 캐시를 같이 쓴다.
        connector = get_connector("claude_sonnet")
    else:
        connector = get_connector(name)
    _GRADING_CONNECTOR_CACHE[name] = connector
    return connector


__all__ = [
    "ChapterAIRequest",
    "ChapterAIResponse",
    "AIConnector",
    "LLMBudgetCounter",
    "_current_budget",
    "get_connector",
    "get_current_budget",
    "get_grading_connector",
    "get_planner_connector",
    "get_text_connector",
    "get_verifier_connector",
    "active_text_provider_chain",
    "text_providers_all_exhausted",
    "connector_supports_batch",
    "run_connector_tasks",
    "set_current_budget",
    "set_allow_reserve",
    "get_allow_reserve",
]
