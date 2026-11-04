"""Google GenAI SDK 기반 Gemini 텍스트 커넥터."""
from __future__ import annotations

import asyncio
import logging
from collections.abc import Sequence
from concurrent.futures import ThreadPoolExecutor
from typing import Protocol

from google import genai
from google.genai import errors as genai_errors

# 스로틀·백오프·모델 폴백은 루트 ai_connectors와 같은 정책을 쓴다.
from ai_connectors import _gemini_throttle as _throttle
from app.modules.ExamForge_V1.common._ai_schemas import (
    ChapterAIRequest,
    ChapterAIResponse,
    LLMBudgetCounter,
    current_budget,
)
from app.modules.ExamForge_V1.common.config import (
    examforge_gemini_concurrency,
    gemini_text_model,
    google_api_key,
)

# ExamForge 전용 Gemini 레인.
# 모의고사는 독립 Gemini 호출이 수십 개라 전역(채팅·강의) 스로틀
# (GEMINI_REQUEST_INTERVAL_MS=2000, 직렬화)을 쓰면 750초 타임아웃에 걸린다.
# 그래서 ExamForge만 따로 둔다.
# - 레인 이름(전역과 분리된 lock + next_allowed_at)
# - 간격 EXAMFORGE_GEMINI_INTERVAL_MS(기본 600ms)
# - 동시성 EXAMFORGE_GEMINI_CONCURRENCY(기본 3)
# 전역 값은 건드리지 않아 지연에 민감한 채팅·강의 경로는 그대로다.
_EXAMFORGE_THROTTLE_LANE = "examforge_gemini"
_EXAMFORGE_INTERVAL_ENV = "EXAMFORGE_GEMINI_INTERVAL_MS"
# env가 없어도 600ms 간격을 둬 429를 줄인다. EXAMFORGE_GEMINI_INTERVAL_MS=0이면 끈다.
_EXAMFORGE_INTERVAL_DEFAULT_MS = 600

# ExamForge용 Gemini 동시 호출 세마포어. 처음 쓸 때 만든다.
# 전역 ai_connectors·ChapterStudio 세마포어와 공유하지 않는다.
_examforge_genai_semaphore: asyncio.Semaphore | None = None

# 실제 동시 SDK 호출 수를 제한하는 전용 ThreadPoolExecutor.
# asyncio.wait_for(asyncio.to_thread(...))는 타임아웃 때 대기 코루틴만 취소하고
# 세마포어 슬롯을 돌려주지만, blocking SDK 호출은 기본 executor 스레드에서 계속 돈다.
# 그래서 모의고사 두 개를 동시에 만들면 "동시성 3" 밖에서 호출이 쌓였다.
# 워커 수를 EXAMFORGE_GEMINI_CONCURRENCY로 고정한 executor에서만 호출하면,
# 타임아웃 뒤에도 도는 호출이 워커를 잡고 있어 새 호출은 큐에서 기다린다.
# 큐는 무제한이라 이벤트 루프를 막지 않는다.
_examforge_sdk_executor: ThreadPoolExecutor | None = None


def _get_examforge_genai_semaphore() -> asyncio.Semaphore:
    """ExamForge 전용 Gemini 세마포어를 처음 쓸 때 만든다.

    크기는 EXAMFORGE_GEMINI_CONCURRENCY(기본 3)이며 전역 GEMINI_TEXT_MAX_CONCURRENCY(=1)와 따로다.
    """
    global _examforge_genai_semaphore
    if _examforge_genai_semaphore is None:
        _examforge_genai_semaphore = asyncio.Semaphore(examforge_gemini_concurrency())
    return _examforge_genai_semaphore


def _get_examforge_sdk_executor() -> ThreadPoolExecutor:
    """SDK 호출 전용 ThreadPoolExecutor를 처음 쓸 때 만든다.

    워커 수는 EXAMFORGE_GEMINI_CONCURRENCY(기본 3)이고 이것이 실제 동시 SDK 호출 상한이다.
    타임아웃 뒤에도 도는 호출이 워커를 잡고 있어 동시 실행이 워커 수를 넘지 않는다.
    """
    global _examforge_sdk_executor
    if _examforge_sdk_executor is None:
        workers = examforge_gemini_concurrency()
        _examforge_sdk_executor = ThreadPoolExecutor(
            max_workers=workers,
            thread_name_prefix="examforge-gemini-sdk",
        )
    return _examforge_sdk_executor


def reset_examforge_genai_semaphore() -> None:
    """전용 세마포어 캐시를 비운다. 테스트에서 동시성 env를 바꾼 뒤 쓴다."""
    global _examforge_genai_semaphore
    _examforge_genai_semaphore = None


def reset_examforge_sdk_executor() -> None:
    """전용 SDK executor 캐시를 비운다. 테스트에서 동시성 env를 바꾼 뒤 쓴다.

    남은 스레드가 있어도 막히지 않게 기존 executor는 wait=False로 닫는다.
    """
    global _examforge_sdk_executor
    if _examforge_sdk_executor is not None:
        _examforge_sdk_executor.shutdown(wait=False)
    _examforge_sdk_executor = None
from app.modules.ExamForge_V1.common.errors import (
    AuthError,
    ConnectorError,
    ContextLengthExceeded,
    RateLimitError,
)
from app.modules.ExamForge_V1.common.errors import TimeoutError as ConnectorTimeoutError

try:
    from common.llm_output import strip_thinking as _strip_thinking
except ImportError:

    def _strip_thinking(text: str) -> str:
        """공통 정규화 모듈이 없으면 원문을 그대로 반환한다."""
        return text


_LOG = logging.getLogger(__name__)

# 재시도·모델 폴백 대상 예외: 429, 503·타임아웃 계열(503은 Timeout으로 매핑한다).
_RETRYABLE_ERRORS = (RateLimitError, ConnectorTimeoutError)


class _GenerateContentModels(Protocol):
    def generate_content(self, *, model: str, contents: str, config: object) -> object:
        ...


class _GenAIClient(Protocol):
    models: _GenerateContentModels


class GeminiGenAIConnector:
    """ExamForge용 Gemini Flash google-genai 커넥터."""

    def __init__(self, model_id: str | None = None, connector_name: str = "gemini_flash") -> None:
        api_key = google_api_key()
        if api_key is None:
            raise AuthError(f"{connector_name}: GOOGLE_API_KEY 또는 GEMINI_API_KEY 필요")
        self._client: _GenAIClient = genai.Client(api_key=api_key)
        self._model = model_id or gemini_text_model()
        self.name = connector_name

    async def generate(
        self,
        req: ChapterAIRequest,
        budget: LLMBudgetCounter | None = None,
    ) -> ChapterAIResponse:
        """예산을 확인하고 횟수 제한이 있는 재시도로 Gemini를 호출한다.

        ExamForge 전용 세마포어로 동시 호출 수를 제한해 rate limit 초과를 줄인다.
        """
        budget = budget or current_budget.get()
        if budget is not None:
            budget.check()

        models = _throttle.model_chain(self._model)
        last_error: ConnectorError | None = None
        for index, model in enumerate(models):
            if index > 0:
                _LOG.warning(
                    "%s: 모델 폴백 전환 %s → %s (사유: %s)",
                    self.name,
                    models[index - 1],
                    model,
                    last_error,
                )
            try:
                result = await self._generate_with_retry(req, model)
                if budget is not None:
                    budget.increment()
                return result
            except _RETRYABLE_ERRORS as exc:
                last_error = exc
        if last_error is None:
            raise ConnectorError("gemini_flash: 재시도 루프가 비정상 종료됐다.")
        raise last_error

    async def _generate_with_retry(
        self, req: ChapterAIRequest, model: str
    ) -> ChapterAIResponse:
        """모델 하나에 세마포어·스로틀·지수 백오프 재시도를 적용해 호출한다."""
        semaphore = _get_examforge_genai_semaphore()
        attempts = _throttle.retry_attempts()
        last_error: ConnectorError | None = None
        for attempt in range(attempts):
            try:
                async with semaphore:
                    # 시도마다 시작 간격 스로틀을 건다. 전용 레인이라
                    # 전역(채팅·강의) 2000ms 직렬화에 묶이지 않는다.
                    await _throttle.wait_for_slot(
                        lane=_EXAMFORGE_THROTTLE_LANE,
                        interval_env=_EXAMFORGE_INTERVAL_ENV,
                        default_ms=_EXAMFORGE_INTERVAL_DEFAULT_MS,
                    )
                    response = await self._call_sdk(req, model)
                return _to_response(response, req, model)
            except _RETRYABLE_ERRORS as exc:
                last_error = exc
                if attempt < attempts - 1:
                    delay = _throttle.backoff_delay_sec(attempt)
                    _LOG.warning(
                        "%s[%s]: 시도 %d/%d 실패(%s). %.1f초 후 재시도",
                        self.name,
                        model,
                        attempt + 1,
                        attempts,
                        exc,
                        delay,
                    )
                    await asyncio.sleep(delay)
        if last_error is None:
            raise ConnectorError("gemini_flash: 재시도 루프가 비정상 종료됐다.")
        raise last_error

    async def _call_sdk(self, req: ChapterAIRequest, model: str) -> object:
        """SDK 호출을 전용 executor에서 돌리고 GEMINI_TEXT_TIMEOUT_MS 타임아웃을 건다.

        asyncio.to_thread(기본 executor) 대신 워커 수가 EXAMFORGE_GEMINI_CONCURRENCY인
        executor에 넣는다. 타임아웃 뒤에도 도는 호출이 워커를 잡고 있어 실제 동시
        SDK 호출이 워커 수를 넘지 않는다. 세마포어만으로는 이것을 막지 못한다.
        """
        loop = asyncio.get_running_loop()
        executor = _get_examforge_sdk_executor()
        future = loop.run_in_executor(executor, self._generate_sync, req, model)
        timeout = _throttle.request_timeout_sec()
        if timeout is None:
            return await future
        try:
            # blocking 스레드는 취소할 수 없어서, wait_for 타임아웃이 executor
            # future를 취소하지 않게 shield로 감싼다.
            return await asyncio.wait_for(asyncio.shield(future), timeout=timeout)
        except asyncio.TimeoutError as exc:
            raise ConnectorTimeoutError(
                f"{self.name}[{model}]: {timeout:.0f}초 내 응답 없음 (GEMINI_TEXT_TIMEOUT_MS)"
            ) from exc

    def supports(self, feature: str) -> bool:
        return feature in {"long_context", "json_mode", "google_genai"}

    def _generate_sync(self, req: ChapterAIRequest, model: str | None = None) -> object:
        config_kwargs = dict(
            system_instruction=req.system or None,
            temperature=req.temperature,
            max_output_tokens=req.max_tokens,
        )
        # AI 총평 같은 자유 서술은 gemini-3.x flash의 추론이 본문에 섞인다. JSON 파싱으로
        # 걸러지는 생성 경로와 달리 정제가 어렵다. 호출부가 extra={"thinking_budget": 0}을
        # 주면 thinking을 끈다. 문제 생성 경로는 extra가 없어 thinking을 그대로 쓴다.
        thinking_budget = req.extra.get("thinking_budget")
        if thinking_budget is not None:
            try:
                config_kwargs["thinking_config"] = genai.types.ThinkingConfig(
                    thinking_budget=int(thinking_budget)
                )
            except (TypeError, ValueError, AttributeError):
                pass
        config = genai.types.GenerateContentConfig(**config_kwargs)
        try:
            return self._client.models.generate_content(
                model=model or self._model,
                contents=req.user,
                config=config,
            )
        except genai_errors.APIError as exc:
            raise _map_api_error(exc) from exc
        except (TimeoutError, OSError) as exc:
            raise ConnectorTimeoutError(str(exc)) from exc


def _to_response(
    response: object, req: ChapterAIRequest, model: str
) -> ChapterAIResponse:
    raw_text = getattr(response, "text", None)
    if not isinstance(raw_text, str) or not raw_text.strip():
        raise ConnectorError("gemini_flash: 응답 text가 비어 있다.")
    text = _strip_thinking(raw_text)
    return ChapterAIResponse(
        text=text,
        model=model,
        input_tokens=_input_tokens(response, req),
        output_tokens=_output_tokens(response, text),
        finish_reason=_finish_reason(response),
    )


def _input_tokens(response: object, req: ChapterAIRequest) -> int:
    usage = getattr(response, "usage_metadata", None)
    value = _usage_int(usage, ("prompt_token_count", "input_token_count"))
    return value if value is not None else max(len(req.user) // 4, 0)


def _output_tokens(response: object, text: str) -> int:
    usage = getattr(response, "usage_metadata", None)
    value = _usage_int(usage, ("candidates_token_count", "output_token_count"))
    return value if value is not None else max(len(text) // 4, 0)


def _usage_int(usage: object, names: tuple[str, ...]) -> int | None:
    for name in names:
        value = getattr(usage, name, None)
        if isinstance(value, int):
            return value
    return None


def _finish_reason(response: object) -> str:
    candidates = getattr(response, "candidates", None)
    if isinstance(candidates, Sequence) and candidates:
        reason = getattr(candidates[0], "finish_reason", None)
        if reason is not None:
            return str(reason)
    return "stop"


# 영구 429(선불 크레딧 소진·결제 중단). 일시적 rate limit과 구분한다.
# 같은 결제 계정이라 재시도·모델 폴백을 해도 실패해서, 바로 다음 프로바이더로 넘겨
# 약 45초를 아낀다.
# RESOURCE_EXHAUSTED는 결제 소진과 분당 quota 초과에 같이 쓰여서
# details[].reason을 먼저 보고 영구·일시를 가른다(루트 gemini_connector와 같은 규칙).
_BILLING_EXHAUSTED_REASONS = frozenset(
    {
        "BILLING_DISABLED",
        "ACCOUNT_SUSPENDED",
        "BILLING_REQUIRED",
        "PREPAYMENT_REQUIRED",
        "CONSUMER_SUSPENDED",
    }
)

# 일시 429(분당 요청·토큰 quota 초과) reason. 재시도 대상이다.
_TRANSIENT_QUOTA_REASONS = frozenset(
    {
        "RATE_LIMIT_EXCEEDED",
        "RESOURCE_EXHAUSTED",
        "QUOTA_EXCEEDED",
    }
)

# reason 필드가 없을 때만 쓰는 메시지 문자열 마커.
_BILLING_EXHAUSTED_MARKERS = (
    "credits are depleted",
    "prepayment",
    "billing",
    "manage your project",
)


def _iter_error_reasons(exc: object) -> tuple[str, ...]:
    """APIError.details(원본 response_json)에서 details[].reason 값들을 추출한다.

    google 429 payload 형태:
        {"error": {"code": 429, "status": "RESOURCE_EXHAUSTED",
                   "details": [{"reason": "BILLING_DISABLED", ...}, ...]}}
    details가 최상위에 있든 error 아래에 있든 reason 문자열만 모은다.
    """
    details = getattr(exc, "details", None)
    if not isinstance(details, dict):
        return ()
    candidates = [details]
    error_obj = details.get("error")
    if isinstance(error_obj, dict):
        candidates.append(error_obj)
    reasons: list[str] = []
    for obj in candidates:
        detail_list = obj.get("details")
        if isinstance(detail_list, list):
            for item in detail_list:
                if isinstance(item, dict):
                    reason = item.get("reason")
                    if isinstance(reason, str) and reason:
                        reasons.append(reason.upper())
    return tuple(reasons)


def _is_billing_exhausted_429(exc: object, message: str) -> bool:
    """크레딧 소진·결제 중단 같은 영구 429인지 판별한다.

    판별 순서:
    1) billing_exhausted reason이 하나라도 있으면 영구다.
    2) transient quota reason만 있으면 일시다.
    3) reason이 없으면 메시지 문자열 마커로 본다.
    """
    reasons = _iter_error_reasons(exc)
    if reasons:
        if any(reason in _BILLING_EXHAUSTED_REASONS for reason in reasons):
            return True
        if all(reason in _TRANSIENT_QUOTA_REASONS for reason in reasons):
            return False
    return _message_has_billing_marker(message)


def _message_has_billing_marker(message: str) -> bool:
    """구조화 필드가 없을 때 쓰는 메시지 문자열 폴백 판별."""
    low = message.lower()
    return any(marker in low for marker in _BILLING_EXHAUSTED_MARKERS)


def _record_provider_exhausted(provider: str, message: str) -> None:
    """헬스 캐시에 소진을 기록한다. 다음 생성 사전점검용 보조 신호다.

    기록이 실패해도 생성 경로가 멈추지 않게 예외를 넓게 삼킨다.
    """
    try:
        from app.modules.ExamForge_V1.common import _provider_health as health

        until = health.parse_regain_until(message)
        health.mark_exhausted(provider, reason=message[:200], until_monotonic=until)
    except Exception:  # noqa: BLE001 - 헬스 기록 실패로 생성이 멈추면 안 된다
        _LOG.debug("[ProviderHealth] '%s' 소진 기록 실패(무시)", provider, exc_info=True)


def _map_api_error(exc: genai_errors.APIError) -> ConnectorError:
    code = getattr(exc, "code", None)
    message = str(exc)
    if code in {401, 403}:
        return AuthError(message)
    if code == 429:
        # 결제성 429는 재시도 없이 ConnectorError로 올려 모델 폴백을 건너뛰고
        # 폴백 래퍼가 바로 다음 프로바이더로 넘기게 한다.
        if _is_billing_exhausted_429(exc, message):
            # 같은 계정이라 재시도해도 실패하므로 gemini_flash를 소진으로 기록해
            # 다음 생성 사전점검이 바로 멈추게 한다.
            _record_provider_exhausted("gemini_flash", message)
            return ConnectorError(f"Gemini 결제 크레딧 소진 — 즉시 폴백: {message}")
        return RateLimitError(message)
    if code in {408, 500, 502, 503, 504}:
        return ConnectorTimeoutError(message)
    if code == 400 and _looks_like_context_error(message):
        return ContextLengthExceeded(message)
    return ConnectorError(message)


def _looks_like_context_error(message: str) -> bool:
    lowered = message.lower()
    return "context" in lowered or "token" in lowered or "too long" in lowered
