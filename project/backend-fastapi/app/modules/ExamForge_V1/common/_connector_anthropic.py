"""Anthropic API 기반 커넥터."""
from __future__ import annotations

import asyncio
from collections.abc import Sequence

from anthropic import APIStatusError, AsyncAnthropic
from anthropic import RateLimitError as AnthropicRateLimitError

from app.modules.ExamForge_V1.common._ai_schemas import (
    AIConnector,
    ChapterAIRequest,
    ChapterAIResponse,
    LLMBudgetCounter,
    current_budget,
)
from app.modules.ExamForge_V1.common.config import claude_sonnet_api_key
from app.modules.ExamForge_V1.common.errors import ConnectorError, RateLimitError

_MAX_RETRIES = 3
_RETRY_DELAYS = [2, 5, 10]

# Anthropic 400 사용한도 소진 마커. 일시적 400(잘못된 요청)과 구분한다.
# 실제 응답: "You have reached your specified API usage limits. ... regain access on <날짜>"
# 이 400은 복구일 전까지 재시도해도 실패해서, 재시도 없이 ConnectorError로 올려
# 폴백 래퍼가 바로 다음 프로바이더로 넘기게 하고 헬스 캐시에 소진을 기록한다.
_ANTHROPIC_USAGE_LIMIT_MARKERS = (
    "usage limit",
    "usage limits",
    "regain access",
    "reached your specified api usage",
)


class AnthropicConnector:
    """Anthropic Claude API 텍스트 커넥터."""

    def __init__(self, model_id: str, connector_name: str) -> None:
        key = claude_sonnet_api_key()
        if not key:
            raise RuntimeError(
                f"{connector_name}: CLAUDE_SONNET_API_KEY 또는 ANTHROPIC_API_KEY 필요"
            )
        self._client = AsyncAnthropic(api_key=key)
        self._model = model_id
        self.name = connector_name

    async def generate(
        self,
        req: ChapterAIRequest,
        budget: LLMBudgetCounter | None = None,
    ) -> ChapterAIResponse:
        """메시지 하나를 생성한다(최대 3회 시도)."""
        if budget is None:
            budget = current_budget.get()
        if budget is not None:
            budget.check()

        last_error: BaseException | None = None
        for attempt in range(_MAX_RETRIES):
            try:
                result = await self._call_api(req)
                if budget is not None:
                    budget.increment()
                return result
            except AnthropicRateLimitError as e:
                last_error = e
                if attempt < _MAX_RETRIES - 1:
                    await asyncio.sleep(_RETRY_DELAYS[attempt])
            except APIStatusError as e:
                if e.status_code >= 500:
                    last_error = e
                    if attempt < _MAX_RETRIES - 1:
                        await asyncio.sleep(_RETRY_DELAYS[attempt])
                elif _is_usage_limit_error(e):
                    # 사용한도 소진 400은 재시도해도 소용없다. 기록만 하고
                    # ConnectorError로 올려 폴백이 바로 다음 단계로 넘기게 한다.
                    message = str(e)
                    _record_provider_exhausted(self.name, message)
                    raise ConnectorError(
                        f"{self.name} 사용한도 소진 — 즉시 폴백: {message}"
                    ) from e
                else:
                    raise
            except asyncio.TimeoutError as e:
                last_error = e
                if attempt < _MAX_RETRIES - 1:
                    await asyncio.sleep(_RETRY_DELAYS[attempt])
        if isinstance(last_error, AnthropicRateLimitError):
            raise RateLimitError(f"API 호출 {_MAX_RETRIES}회 실패: {last_error}") from last_error
        raise ConnectorError(f"API 호출 {_MAX_RETRIES}회 실패: {last_error}") from last_error

    async def _call_api(self, req: ChapterAIRequest) -> ChapterAIResponse:
        """Anthropic API를 한 번 호출한다."""
        msg = await self._client.messages.create(
            model=self._model,
            max_tokens=req.max_tokens,
            temperature=min(req.temperature, 1.0),
            system=req.system or "",
            messages=[{"role": "user", "content": req.user}],
            timeout=60.0,
        )
        text = _extract_text(msg)
        if not text:
            raise ValueError("빈 응답")
        usage = getattr(msg, "usage", None)
        return ChapterAIResponse(
            text=text,
            model=self._model,
            input_tokens=getattr(usage, "input_tokens", 0),
            output_tokens=getattr(usage, "output_tokens", 0),
            finish_reason=getattr(msg, "stop_reason", "unknown") or "unknown",
        )

    def supports(self, feature: str) -> bool:
        return feature in {"long_context", "json_mode"}


def _is_usage_limit_error(exc: object) -> bool:
    """Anthropic 4xx가 복구일 전까지 계속 실패하는 '사용한도 소진'인지 메시지로 본다.

    일반 400과 구분하려고 마커가 있을 때만 True다. 없으면 그대로 raise한다.
    """
    low = str(exc).lower()
    return any(marker in low for marker in _ANTHROPIC_USAGE_LIMIT_MARKERS)


def _record_provider_exhausted(provider: str, message: str) -> None:
    """헬스 캐시에 소진을 기록한다. 따로 상태를 확인하는 호출은 하지 않는다.

    기록이 실패해도 생성 경로가 멈추지 않게 예외를 넓게 삼킨다.
    "regain access on <date>"가 있으면 그 시각까지, 없으면 기본 TTL 동안 막는다.
    """
    try:
        from app.modules.ExamForge_V1.common import _provider_health as health

        until = health.parse_regain_until(message)
        health.mark_exhausted(provider, reason=message[:200], until_monotonic=until)
    except Exception:  # noqa: BLE001 - 헬스 기록 실패로 생성이 멈추면 안 된다
        pass


def _extract_text(message: object) -> str:
    """응답 첫 텍스트 블록을 추출한다."""
    content = getattr(message, "content", ())
    if not isinstance(content, Sequence) or not content:
        return ""
    first = content[0]
    text = getattr(first, "text", None)
    return text if isinstance(text, str) else ""
