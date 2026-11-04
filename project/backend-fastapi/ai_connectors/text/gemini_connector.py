"""Google GenAI Gemini Flash 텍스트 커넥터.

기본 경로가 아니며 ACTIVE_TEXT_MODEL=gemini_flash 로 명시할 때만 쓴다.
"""
from __future__ import annotations

import asyncio
import logging
from collections.abc import Sequence
from concurrent.futures import ThreadPoolExecutor
from typing import Protocol

from google import genai
from google.genai import errors as genai_errors

from ai_connectors import _gemini_throttle as _throttle
from ai_connectors.errors import (
    AuthError,
    ConnectorError,
    ContextLengthExceeded,
    RateLimitError,
    ServiceUnavailableError,
)
from ai_connectors.errors import TimeoutError as ConnectorTimeoutError
from ai_connectors.text_schemas import ChapterAIRequest, ChapterAIResponse
from common.text_config import (
    gemini_text_max_concurrency,
    gemini_text_model,
    gemini_thinking_level,
    google_api_key,
)

try:
    from common.llm_output import strip_thinking as _strip_thinking
except ImportError:

    def _strip_thinking(text: str) -> str:
        """공통 정규화 모듈이 없으면 원문을 그대로 반환한다."""
        return text


_LOG = logging.getLogger(__name__)

# 재시도·모델 폴백 대상 예외: 429, 503·UNAVAILABLE, 타임아웃
_RETRYABLE_ERRORS = (RateLimitError, ServiceUnavailableError, ConnectorTimeoutError)

# 동시 SDK 호출 수를 제한하는 ThreadPoolExecutor.
# asyncio.to_thread 는 스레드 수 제한이 사실상 없는 기본 executor 를 쓴다. wait_for 가
# 타임아웃해도 기다리던 코루틴만 풀리고 blocking google-genai 호출은 계속 돌아서,
# 타임아웃이 잦으면 SDK 호출이 끝없이 쌓인다.
# 그래서 워커 수가 GEMINI_TEXT_MAX_CONCURRENCY(기본 1)인 전역 executor 에서만 실행한다.
# 타임아웃 뒤에도 도는 호출이 워커를 잡고 있고 새 요청은 큐에서 기다리므로,
# 동시 SDK 호출이 워커 수를 넘지 않는다.
_sdk_executor: ThreadPoolExecutor | None = None


def _get_sdk_executor() -> ThreadPoolExecutor:
    """SDK 호출 전용 executor 를 처음 쓸 때 만들어 돌려준다.

    워커 수는 GEMINI_TEXT_MAX_CONCURRENCY(기본 1)이고, 이 값이 동시 SDK 호출 상한이다.
    """
    global _sdk_executor
    if _sdk_executor is None:
        _sdk_executor = ThreadPoolExecutor(
            max_workers=gemini_text_max_concurrency(),
            thread_name_prefix="gemini-text-sdk",
        )
    return _sdk_executor


def reset_sdk_executor() -> None:
    """전역 SDK executor 를 비운다. 테스트에서 동시성 env 를 바꾼 뒤 다시 만들 때 쓴다.

    기존 executor 는 wait=False 로 닫아서, 아직 도는 스레드가 있어도 멈추지 않는다.
    """
    global _sdk_executor
    if _sdk_executor is not None:
        _sdk_executor.shutdown(wait=False)
    _sdk_executor = None


class _GenerateContentModels(Protocol):
    def generate_content(self, *, model: str, contents: str, config: object) -> object:
        """google-genai 동기 generate_content 호출 형태."""
        ...


class _GenAIClient(Protocol):
    models: _GenerateContentModels

    def close(self) -> None:
        """동기 클라이언트 리소스를 닫는다."""
        ...


class GeminiGenAIConnector:
    """Google AI Studio Gemini 텍스트 생성 커넥터."""

    name = "gemini_flash"

    def __init__(self) -> None:
        api_key = google_api_key()
        if api_key is None:
            raise AuthError("GOOGLE_API_KEY 또는 GEMINI_API_KEY가 설정되지 않았다.")
        self._client: _GenAIClient = genai.Client(api_key=api_key)
        self._model = gemini_text_model()
        # gemini-3.5-flash는 thinking 모델이라 사고량이 크면 reasoning 텍스트가 답변에 섞인다.
        # thinking_level을 LOW(기본)로 낮춘다.
        self._thinking_level = gemini_thinking_level()

    async def generate(self, req: ChapterAIRequest) -> ChapterAIResponse:
        """동기 google-genai SDK 호출을 스레드로 감싸 텍스트를 생성한다.

        모델마다 지수 백오프로 다시 시도하고, 429/503/타임아웃으로 시도가 다 떨어지면
        GEMINI_TEXT_MODEL_FALLBACKS 순서대로 다음 모델로 넘어간다.
        모든 모델이 실패하면 마지막 오류를 올려 프로바이더 폴백으로 넘긴다.
        """
        models = _throttle.model_chain(self._model)
        last_error: ConnectorError | None = None
        for index, model in enumerate(models):
            if index > 0:
                _LOG.warning(
                    "gemini_flash: 모델 폴백 전환 %s → %s (사유: %s)",
                    models[index - 1],
                    model,
                    last_error,
                )
            try:
                return await self._generate_with_retry(req, model)
            except _RETRYABLE_ERRORS as exc:
                last_error = exc
        if last_error is None:
            raise ConnectorError("gemini_flash: 재시도 루프가 비정상 종료됐다.")
        raise last_error

    async def _generate_with_retry(
        self, req: ChapterAIRequest, model: str
    ) -> ChapterAIResponse:
        """모델 하나에 스로틀과 지수 백오프 재시도를 적용한다."""
        attempts = _throttle.retry_attempts()
        last_error: ConnectorError | None = None
        for attempt in range(attempts):
            # 시도할 때마다 호출 시작 간격 스로틀을 먼저 건다
            await _throttle.wait_for_slot()
            try:
                response = await self._call_sdk(req, model)
                return _to_response(response, req, model)
            except _RETRYABLE_ERRORS as exc:
                last_error = exc
                if attempt < attempts - 1:
                    delay = _throttle.backoff_delay_sec(attempt)
                    _LOG.warning(
                        "gemini_flash[%s]: 시도 %d/%d 실패(%s). %.1f초 후 재시도",
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
        """SDK 호출을 전용 executor 에서 돌리고 GEMINI_TEXT_TIMEOUT_MS 타임아웃을 건다."""
        loop = asyncio.get_running_loop()
        executor = _get_sdk_executor()
        future = loop.run_in_executor(executor, self._generate_sync, req, model)
        timeout = _throttle.request_timeout_sec()
        if timeout is None:
            return await future
        try:
            # blocking 스레드는 어차피 취소되지 않으므로, shield 로 감싸 wait_for 가
            # executor future 를 취소하려 하지 않게 한다.
            return await asyncio.wait_for(asyncio.shield(future), timeout=timeout)
        except asyncio.TimeoutError as exc:
            raise ConnectorTimeoutError(
                f"gemini_flash[{model}]: {timeout:.0f}초 내 응답 없음 (GEMINI_TEXT_TIMEOUT_MS)"
            ) from exc

    async def generate_batch(
        self, reqs: list[ChapterAIRequest]
    ) -> list[ChapterAIResponse]:
        """배치 인터페이스용. 모델 배치가 아니라 단건 호출을 gather 한다."""
        return list(await asyncio.gather(*[self.generate(req) for req in reqs]))

    async def aclose(self) -> None:
        """google-genai 동기 클라이언트를 스레드에서 닫는다."""
        await asyncio.to_thread(self._client.close)

    def supports(self, feature: str) -> bool:
        return feature in {"long_context", "json_mode", "fallback", "google_genai"}

    def _generate_sync(self, req: ChapterAIRequest, model: str | None = None) -> object:
        """google-genai 동기 API를 정확한 파라미터로 호출한다."""
        config = genai.types.GenerateContentConfig(
            system_instruction=req.system or None,
            temperature=req.temperature,
            max_output_tokens=req.max_tokens,
            # Gemini 3 계열은 thinking_budget 을 받지 않아 thinking_level 로 사고량을 정한다.
            # reasoning 텍스트가 최종 답변에 나오지 않게 한다.
            thinking_config=genai.types.ThinkingConfig(
                thinking_level=self._thinking_level
            ),
        )
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
    """Gemini 응답 객체를 공통 응답 스키마로 변환한다."""
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


def _map_api_error(exc: genai_errors.APIError) -> ConnectorError:
    """google-genai APIError를 공통 커넥터 예외로 변환한다."""
    code = getattr(exc, "code", None)
    message = str(exc)
    if code in {401, 403}:
        return AuthError(message)
    if code == 429:
        # 429는 두 가지다.
        #  - billing_exhausted: 선불 크레딧 소진·결제 중단처럼 계속되는 상태. 다시 시도해도
        #    소용없으니 재시도하지 않는 ConnectorError로 올려 바로 다른 프로바이더로 넘긴다.
        #  - quota/rate_limit: 분당 요청 초과 같은 일시 상태. RateLimitError(재시도 대상).
        # 구조화 필드(status/details.reason/code)를 먼저 보고, 없으면 메시지 문자열로 판단한다.
        if _is_billing_exhausted_429(exc, message):
            return ConnectorError(f"Gemini 결제 크레딧 소진 — 즉시 폴백: {message}")
        return RateLimitError(message)
    # 503/UNAVAILABLE/overloaded 는 과부하라 재시도·모델 폴백 대상으로 구분한다
    if code == 503 or _looks_like_overloaded(message):
        return ServiceUnavailableError(message)
    if code in {408, 500, 502, 504}:
        return ConnectorTimeoutError(message)
    if code == 400 and _looks_like_context_error(message):
        return ContextLengthExceeded(message)
    return ConnectorError(message)


# 계속되는 429(결제 중단·크레딧 소진)를 뜻하는 details[].reason 값.
# status=RESOURCE_EXHAUSTED 는 일시 quota 초과에도 쓰여서 reason 으로 구분한다.
_BILLING_EXHAUSTED_REASONS = frozenset(
    {
        "BILLING_DISABLED",
        "ACCOUNT_SUSPENDED",
        "BILLING_REQUIRED",
        "PREPAYMENT_REQUIRED",
        "CONSUMER_SUSPENDED",
    }
)

# 일시 429(분당 요청·토큰 quota 초과)를 뜻하는 reason 값.
# 이 값이 있으면 재시도 대상으로 보고, 메시지의 billing 문구로 잘못 판정하지 않는다.
_TRANSIENT_QUOTA_REASONS = frozenset(
    {
        "RATE_LIMIT_EXCEEDED",
        "RESOURCE_EXHAUSTED",
        "QUOTA_EXCEEDED",
    }
)

# 구조화 필드가 없을 때만 보는 메시지 문자열 마커.
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
    details 가 최상위에 있든 error 아래에 있든 찾아서 reason 문자열만 모은다.
    """
    details = getattr(exc, "details", None)
    if not isinstance(details, dict):
        return ()
    # 최상위 또는 error 하위 어디에 있든 details 배열을 찾는다
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
    """429가 일시 rate limit 이 아니라 크레딧 소진·결제 중단인지 판별한다.

    판별 순서:
    1) billing reason 이 하나라도 있으면 결제 문제로 본다.
    2) 일시 quota reason 만 있으면 일시 상태로 본다(메시지 문구는 무시).
    3) reason 이 없으면 메시지 문자열 마커로 판단한다.
    """
    reasons = _iter_error_reasons(exc)
    if reasons:
        if any(reason in _BILLING_EXHAUSTED_REASONS for reason in reasons):
            return True
        if all(reason in _TRANSIENT_QUOTA_REASONS for reason in reasons):
            return False
        # 모르는 reason 조합이면 아래 메시지 마커 판정으로 넘어간다.
    return _message_has_billing_marker(message)


def _message_has_billing_marker(message: str) -> bool:
    """구조화 필드가 없을 때 쓰는 메시지 문자열 폴백 판별."""
    low = message.lower()
    return any(marker in low for marker in _BILLING_EXHAUSTED_MARKERS)


def _looks_like_overloaded(message: str) -> bool:
    """503 류 과부하 메시지 패턴(UNAVAILABLE/overloaded)을 식별한다."""
    lowered = message.lower()
    return "unavailable" in lowered or "overloaded" in lowered


def _looks_like_context_error(message: str) -> bool:
    lowered = message.lower()
    return "context" in lowered or "token" in lowered or "too long" in lowered
