"""OpenAI GPT 텍스트 폴백 커넥터.

gemini_connector.py 와 같은 generate / generate_batch / supports 를 제공한다.
Gemini 를 명시 선택하고 OPENAI_FALLBACK_ENABLED=true 일 때만 FailoverAIConnector 가
이 커넥터로 넘긴다. ClaudeSonnetConnector 처럼 AsyncOpenAI 를 직접 await 한다.
"""
from __future__ import annotations

import asyncio
from collections.abc import Sequence

from openai import APIConnectionError as _OpenAIAPIConnectionError
from openai import APIError as _OpenAIAPIError
from openai import APITimeoutError as _OpenAIAPITimeoutError
from openai import AsyncOpenAI
from openai import AuthenticationError as _OpenAIAuthError
from openai import BadRequestError as _OpenAIBadRequestError
from openai import InternalServerError as _OpenAIInternalServerError
from openai import RateLimitError as _OpenAIRateLimitError

from ai_connectors._openai_common import clamp_openai_max_tokens
from ai_connectors.errors import (
    AuthError,
    ConnectorError,
    ContextLengthExceeded,
    RateLimitError,
)
from ai_connectors.errors import TimeoutError as ConnectorTimeoutError
from ai_connectors.text_schemas import ChapterAIRequest, ChapterAIResponse
from common.text_config import openai_api_key, openai_text_model

try:
    from common.llm_output import strip_thinking as _strip_thinking
except ImportError:

    def _strip_thinking(text: str) -> str:
        """공통 정규화 모듈이 없으면 원문을 그대로 반환한다."""
        return text


# 배치는 Gemini 커넥터처럼 단건 호출을 gather 한다.


class OpenAIConnector:
    """OpenAI GPT 텍스트 생성 커넥터(Gemini 폴백)."""

    name = "openai_text"

    def __init__(self) -> None:
        # API 키가 없으면 호출 때가 아니라 생성할 때 바로 AuthError 를 낸다
        api_key = openai_api_key()
        if api_key is None:
            raise AuthError("OPENAI_API_KEY 가 설정되지 않았다.")
        self._client = AsyncOpenAI(api_key=api_key)
        self._model = openai_text_model()

    async def generate(self, req: ChapterAIRequest) -> ChapterAIResponse:
        """요청 하나를 chat.completions 에 보내고 공통 응답으로 바꿔 돌려준다.

        system/user/temperature/max_tokens 는 Gemini 커넥터와 같은 방식으로 매핑한다
        (system → system role, user → user role).
        """
        try:
            completion = await self._client.chat.completions.create(
                model=self._model,
                messages=_build_messages(req),
                temperature=req.temperature,
                # Gemini 기준의 큰 max_tokens 를 OpenAI 모델 한도로 자른다.
                max_tokens=clamp_openai_max_tokens(req.max_tokens, self._model),
            )
        except _OpenAIAuthError as exc:
            raise AuthError(str(exc)) from exc
        except _OpenAIRateLimitError as exc:
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
        return _to_response(completion, req, self._model)

    async def generate_batch(
        self, reqs: list[ChapterAIRequest]
    ) -> list[ChapterAIResponse]:
        """배치 인터페이스용. 모델 배치가 아니라 단건 호출을 gather 한다."""
        return list(await asyncio.gather(*[self.generate(req) for req in reqs]))

    async def aclose(self) -> None:
        """AsyncOpenAI 클라이언트 연결을 정리한다."""
        await self._client.close()

    def supports(self, feature: str) -> bool:
        return feature in {"long_context", "json_mode", "fallback", "openai"}


def _build_messages(req: ChapterAIRequest) -> list[dict[str, str]]:
    """ChapterAIRequest 를 chat.completions messages 배열로 변환한다.

    system 이 비어 있으면 Gemini(system_instruction=None)처럼 system 메시지를 뺀다.
    """
    messages: list[dict[str, str]] = []
    if req.system:
        messages.append({"role": "system", "content": req.system})
    messages.append({"role": "user", "content": req.user})
    return messages


def _to_response(
    completion: object, req: ChapterAIRequest, model: str
) -> ChapterAIResponse:
    """OpenAI chat.completions 응답을 공통 응답 스키마로 변환한다."""
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
    """첫 번째 choice 의 message.content 를 추출한다."""
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
    """400 BadRequest 를 컨텍스트 초과 여부에 따라 분기한다."""
    lowered = str(exc).lower()
    if "context" in lowered or "token" in lowered or "too long" in lowered:
        return ContextLengthExceeded(str(exc))
    return ConnectorError(str(exc))
