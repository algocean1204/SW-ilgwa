"""캡션 생성 진입점. 3단 폴백: Kanana → Claude Haiku → Jinja2 템플릿."""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from typing import Literal

from common.text_config import anthropic_api_key
from .config import (
    MAX_TOKENS,
    REPEAT_PENALTY,
    SYSTEM_PROMPT,
    TEMPERATURE,
    TOP_P,
)
from .fallback import render_jinja_caption
from .model_loader import get_llama
from .validator import sanitize

_LOG = logging.getLogger(__name__)


# Kanana 출력이 형식 검사에 걸렸을 때만 부르는 폴백 모델
_HAIKU_MODEL = "claude-haiku-4-5"
_HAIKU_TIMEOUT_SEC = 15.0


@dataclass(frozen=True)
class CaptionResult:
    text: str
    source: Literal["kanana", "haiku", "fallback"]
    char_count: int


def generate_caption(
    *,
    student_name: str,
    assignment_name: str,
    weakness: str | None = None,
    difficulty: str | None = None,
    deadline: str | None = None,
) -> CaptionResult:
    """학생·과제 정보로 20~100자 캡션을 만든다.

    Kanana 출력이 validator 형식 검사를 못 넘기면 Claude Haiku로, 그것도 실패하면 Jinja2 템플릿으로 간다.
    """
    user_prompt = _build_user_prompt(
        student_name, assignment_name, weakness, difficulty, deadline
    )

    clean = sanitize(_run_kanana_safely(user_prompt))
    if clean is not None:
        return CaptionResult(text=clean, source="kanana", char_count=len(clean))

    clean = sanitize(_run_haiku(user_prompt))
    if clean is not None:
        return CaptionResult(text=clean, source="haiku", char_count=len(clean))

    fb = render_jinja_caption(
        student_name=student_name,
        assignment_name=assignment_name,
        weakness=weakness,
        deadline=deadline,
    )
    return CaptionResult(text=fb, source="fallback", char_count=len(fb))


def _build_user_prompt(
    name: str,
    assignment: str,
    weakness: str | None,
    difficulty: str | None,
    deadline: str | None,
) -> str:
    """유저 프롬프트 문자열을 조립한다."""
    parts = [f"학생명: {name}", f"과제명: {assignment}"]
    if weakness:
        parts.append(f"약점: {weakness}")
    if difficulty:
        parts.append(f"난이도: {difficulty}")
    if deadline:
        parts.append(f"마감: {deadline}")
    return ", ".join(parts)


def _run_haiku(user_prompt: str) -> str | None:
    """Claude Haiku로 캡션을 만든다. 꺼져 있거나 실패하면 None."""
    if not _should_try_haiku():
        return None
    try:
        from anthropic import Anthropic

        from ai_connectors.text.claude_sonnet_connector import _first_text_block

        client = Anthropic(api_key=anthropic_api_key(), timeout=_HAIKU_TIMEOUT_SEC)
        msg = client.messages.create(
            model=_HAIKU_MODEL,
            max_tokens=MAX_TOKENS,
            temperature=TEMPERATURE,
            system=SYSTEM_PROMPT,
            messages=[{"role": "user", "content": user_prompt}],
        )
        return _first_text_block(msg)
    except Exception as exc:
        # 캡션은 알림 보조 문구라 외부 API 장애보다 템플릿 폴백이 우선이다
        _LOG.warning("[caption] Haiku 캡션 생성 실패, Jinja2 폴백으로 이동 — error=%s", exc)
        return None


def _should_try_haiku() -> bool:
    """CAPTION_BACKEND가 auto/haiku이고 Anthropic 키가 있을 때만 Haiku를 부른다."""
    backend = os.getenv("CAPTION_BACKEND", "auto").strip().lower()
    return backend in {"auto", "haiku"} and anthropic_api_key() is not None


def _run_kanana_safely(user_prompt: str) -> str | None:
    """Kanana 모델이 없거나 실패하면 None을 돌려 다음 폴백으로 넘긴다."""
    try:
        return _run_kanana(user_prompt)
    except Exception as exc:
        # 로컬 GGUF 파일이 없는 것도 정상 폴백 조건이다
        _LOG.warning("[caption] Kanana 캡션 생성 실패, Haiku 폴백으로 이동 — error=%s", exc)
        return None


def _run_kanana(user_prompt: str) -> str:
    """Kanana 모델로 캡션을 생성하고 원시 문자열을 반환한다."""
    llm = get_llama()
    resp = llm.create_chat_completion(
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        temperature=TEMPERATURE,
        top_p=TOP_P,
        max_tokens=MAX_TOKENS,
        repeat_penalty=REPEAT_PENALTY,
    )
    return resp["choices"][0]["message"]["content"]
