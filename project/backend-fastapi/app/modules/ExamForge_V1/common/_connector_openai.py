"""ExamForge OpenAI GPT 텍스트 커넥터. 기본 폴백 체인에는 들어 있지 않다.

_connector_gemini_genai.py와 같은 인터페이스(generate(req, budget), supports)와
ChapterAIRequest/Response 스키마를 쓰고, 예산은 check → 호출 → increment 순서로 센다.
"""
from __future__ import annotations

from collections.abc import Sequence

from openai import APIConnectionError as _OpenAIAPIConnectionError
from openai import APIError as _OpenAIAPIError
from openai import APITimeoutError as _OpenAIAPITimeoutError
from openai import AsyncOpenAI
from openai import AuthenticationError as _OpenAIAuthError
from openai import BadRequestError as _OpenAIBadRequestError
from openai import InternalServerError as _OpenAIInternalServerError
from openai import RateLimitError as _OpenAIRateLimitError

# 모델별 completion 토큰 상한은 루트 공통 헬퍼로 맞춘다.
from ai_connectors._openai_common import clamp_openai_max_tokens
from app.modules.ExamForge_V1.common._ai_schemas import (
    ChapterAIRequest,
    ChapterAIResponse,
    LLMBudgetCounter,
    current_budget,
)
from app.modules.ExamForge_V1.common.config import (
    openai_api_key,
    openai_text_model,
)
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


class OpenAIConnector:
    """ExamForge용 OpenAI GPT 텍스트 커넥터."""

    def __init__(self, connector_name: str = "openai_text") -> None:
        api_key = openai_api_key()
        if api_key is None:
            raise AuthError(f"{connector_name}: OPENAI_API_KEY 필요")
        # SDK 자동 재시도(수 초 대기)는 다음 폴백 전환을 늦춰서 끈다.
        self._client = AsyncOpenAI(api_key=api_key, max_retries=0)
        self._model = openai_text_model()
        self.name = connector_name

    async def generate(
        self,
        req: ChapterAIRequest,
        budget: LLMBudgetCounter | None = None,
    ) -> ChapterAIResponse:
        """예산을 확인하고 OpenAI chat.completions를 호출한다."""
        budget = budget or current_budget.get()
        if budget is not None:
            budget.check()

        try:
            completion = await self._client.chat.completions.create(
                model=self._model,
                messages=_build_messages(req),
                temperature=req.temperature,
                # 다른 모델용 큰 max_tokens가 OpenAI 모델 한도를 넘지 않게 줄인다.
                max_tokens=clamp_openai_max_tokens(req.max_tokens, self._model),
            )
        except _OpenAIAuthError as exc:
            raise AuthError(str(exc)) from exc
        except _OpenAIRateLimitError as exc:
            # insufficient_quota(크레딧·한도 소진)는 재시도해도 복구되지 않아 헬스에 기록한다.
            # 예외는 그대로 RateLimitError로 올린다.
            if _is_billing_exhausted(str(exc)):
                _record_provider_exhausted(self.name, str(exc))
            raise RateLimitError(str(exc)) from exc
        except (
            _OpenAIAPITimeoutError,
            _OpenAIAPIConnectionError,
            _OpenAIInternalServerError,
        ) as exc:
            raise ConnectorTimeoutError(str(exc)) from exc
        except _OpenAIBadRequestError as exc:
            raise _map_bad_request(exc) from exc
        except _OpenAIAPIError as exc:
            raise ConnectorError(str(exc)) from exc
        result = _to_response(completion, req, self._model)
        if budget is not None:
            budget.increment()
        return result

    def supports(self, feature: str) -> bool:
        return feature in {"long_context", "json_mode", "openai"}


def _build_messages(req: ChapterAIRequest) -> list[dict[str, str]]:
    """ChapterAIRequest를 chat.completions messages 배열로 바꾼다."""
    messages: list[dict[str, str]] = []
    if req.system:
        messages.append({"role": "system", "content": req.system})
    messages.append({"role": "user", "content": req.user})
    return messages


def _to_response(
    completion: object, req: ChapterAIRequest, model: str
) -> ChapterAIResponse:
    raw_text = _first_message_text(completion)
    if not raw_text.strip():
        raise ConnectorError("openai_text: 응답 text가 비어 있다.")
    text = _strip_thinking(raw_text)
    return ChapterAIResponse(
        text=text,
        model=model,
        input_tokens=_input_tokens(completion, req),
        output_tokens=_output_tokens(completion, text),
        finish_reason=_finish_reason(completion),
    )


def _first_message_text(completion: object) -> str:
    choices = getattr(completion, "choices", None)
    if not isinstance(choices, Sequence) or not choices:
        raise ConnectorError("openai_text: 응답 choices가 비어 있다.")
    message = getattr(choices[0], "message", None)
    content = getattr(message, "content", None)
    if not isinstance(content, str):
        raise ConnectorError("openai_text: 응답 message.content가 문자열이 아니다.")
    return content


def _input_tokens(completion: object, req: ChapterAIRequest) -> int:
    usage = getattr(completion, "usage", None)
    value = getattr(usage, "prompt_tokens", None)
    return value if isinstance(value, int) else max(len(req.user) // 4, 0)


def _output_tokens(completion: object, text: str) -> int:
    usage = getattr(completion, "usage", None)
    value = getattr(usage, "completion_tokens", None)
    return value if isinstance(value, int) else max(len(text) // 4, 0)


def _finish_reason(completion: object) -> str:
    choices = getattr(completion, "choices", None)
    if isinstance(choices, Sequence) and choices:
        reason = getattr(choices[0], "finish_reason", None)
        if isinstance(reason, str):
            return reason
    return "stop"


def _map_bad_request(exc: _OpenAIBadRequestError) -> ConnectorError:
    lowered = str(exc).lower()
    if "context" in lowered or "token" in lowered or "too long" in lowered:
        return ContextLengthExceeded(str(exc))
    return ConnectorError(str(exc))


# OpenAI 영구 소진 마커. 분당 rate limit(일시)과 구분한다.
# 'billing' 한 단어만 보면 일시 오류도 영구 소진으로 잘못 봐서 넣지 않는다.
# insufficient_quota 응답에 실제로 나오는 문구만 둔다.
_OPENAI_BILLING_MARKERS = (
    "insufficient_quota",
    "insufficient quota",
    "exceeded your current quota",
    "check your plan and billing",
    "billing_hard_limit_reached",
    "billing_not_active",
)


def _is_billing_exhausted(message: str) -> bool:
    """OpenAI 오류 메시지가 영구 소진(insufficient_quota 등)인지 본다."""
    low = message.lower()
    return any(marker in low for marker in _OPENAI_BILLING_MARKERS)


def _record_provider_exhausted(provider: str, message: str) -> None:
    """헬스 캐시에 소진을 기록한다. 기록이 실패해도 생성이 멈추지 않게 예외를 삼킨다."""
    try:
        from app.modules.ExamForge_V1.common import _provider_health as health

        until = health.parse_regain_until(message)
        health.mark_exhausted(provider, reason=message[:200], until_monotonic=until)
    except Exception:  # noqa: BLE001 - 헬스 기록 실패로 생성이 멈추면 안 된다
        pass
