"""강의 1개의 산출물을 컴포넌트별로 나눠 병렬 생성한다.

connector.supports("batch")가 True인 커넥터(Modal Qwen 등)에서 쓴다. 한 번의 큰 호출에서는
Qwen이 뒤쪽 배열을 적게 만들거나 rubric을 dict로 내는 일이 있어, 작은 guided JSON 스키마로
나눠 동시에 부른다.

동시 호출(asyncio.gather):
    1단계 슬라이드 N장(장별 독립 요청을 generate_batch로) ‖ quizzes ‖ note ‖ assignment
    2단계 생성된 슬라이드 화면 요약을 넣어 voice_scripts를 장마다 병렬 생성
B200 서버는 동시 입력 8개(max_inputs·max-num-seqs 8)를 vLLM 한 배치로 처리한다.

실패 처리:
    - 실패한 컴포넌트나 슬라이드만 1회 다시 요청한다. 그래도 실패하면 ConversionError를 올린다.
    - rubric/steps가 dict면 파서가 list로 바꾼다.
    - 조립 단계의 finalize_payload가 개수와 slide_idx 완전집합을 확인한다.

공개 API:
    - generate_lesson_parallel(connector, state, slide_count) -> GeneratedLessonPayload
"""
from __future__ import annotations

import asyncio
import json
from collections.abc import Callable
from functools import partial
from typing import TypeVar, cast

from pydantic import ValidationError

from app.modules.ChapterStudio_V1.ai_connectors.base import AIConnector
from app.modules.ChapterStudio_V1.ai_connectors.errors import ConnectorError
from app.modules.ChapterStudio_V1.ai_connectors.schemas import ChapterAIRequest
from app.modules.ChapterStudio_V1.common.errors import ConversionError
from app.modules.ChapterStudio_V1.common.logging import logger
from app.modules.ChapterStudio_V1.pipeline import parallel_prompts as pp
from app.modules.ChapterStudio_V1.pipeline.parallel_assemble import (
    ComponentBundle,
    assemble_payload,
)
from app.modules.ChapterStudio_V1.pipeline.parallel_inputs import (
    PersonalizationArgs,
    VoiceTarget,
    build_brief,
    build_outline_text,
    build_personalization_args,
    build_reference_block,
    build_voice_targets,
)
from app.modules.ChapterStudio_V1.pipeline.payload import GeneratedLessonPayload, GeneratedSlide
from app.modules.ChapterStudio_V1.pipeline.slide_plan import build_slide_plan, serialize_slide_plan
from app.modules.ChapterStudio_V1.pipeline.state import ChapterStudioState

_Result = TypeVar("_Result")

# 컴포넌트 생성 실패 시 재시도 횟수(1회).
_RETRY_ATTEMPTS = 1
# 커넥터 실패(timeout/auth 등)와 파싱 실패만 재시도 대상이다. 그 밖의 예외는 그대로 올린다.
_RETRYABLE_ERRORS = (ConnectorError, ConversionError, ValidationError, ValueError, json.JSONDecodeError)


async def generate_lesson_parallel(
    connector: AIConnector, state: ChapterStudioState, slide_count: int
) -> GeneratedLessonPayload:
    """컴포넌트를 병렬 생성하고 조립해 강의 payload를 만든다."""
    brief = build_brief(state)
    outline = build_outline_text(state, slide_count)
    template_key = _template_key(state)
    personalization = build_personalization_args(state)
    # PDF 소스 강의의 참고도서 발췌. 단일 호출 경로(prompt.py)와 같이 병렬 경로에도 넣는다.
    reference_block = build_reference_block(state)

    bundle = await _gather_components(
        connector, state, brief, outline, template_key, slide_count, personalization, reference_block
    )
    return assemble_payload(bundle, slide_count)


async def _gather_components(
    connector: AIConnector,
    state: ChapterStudioState,
    brief: str,
    outline: str,
    template_key: str,
    slide_count: int,
    personalization: PersonalizationArgs,
    reference_block: str = "",
) -> ComponentBundle:
    """슬라이드·퀴즈·노트·과제를 만든 뒤 화면 내용을 보고 음성대본을 만든다."""
    # context_node가 채운 slide_outline(visual_type 포함)을 슬라이드 파서에 slide_plan으로 넘긴다.
    # 넘기지 않으면 _enforce_plan_visual_type·_relabel_chapter_title이 동작하지 않는다.
    slide_plan_rows = _resolve_slide_plan(state, template_key, slide_count)
    slides_task = _gather_slides(
        connector, brief, outline, slide_count, template_key, personalization, slide_plan_rows, reference_block
    )
    quizzes_task = _component(
        connector,
        pp.build_quizzes_request(brief, outline, slide_count, template_key, personalization, reference_block),
        pp.parse_quizzes,
    )
    note_task = _component(
        connector,
        pp.build_note_request(brief, outline, slide_count, template_key, personalization, reference_block),
        pp.parse_note,
    )
    assignment_task = _component(
        connector,
        pp.build_assignment_request(brief, outline, slide_count, template_key, personalization, reference_block),
        pp.parse_assignment,
    )
    slides, quizzes, note_blocks, assignment = await asyncio.gather(slides_task, quizzes_task, note_task, assignment_task)
    voice_targets = build_voice_targets(_state_with_slides(state, slides), slide_count)
    voice_scripts = await _gather_voices(connector, brief, slide_count, voice_targets, personalization, reference_block)
    return ComponentBundle(
        slides=slides,
        quizzes=quizzes,
        note_blocks=note_blocks,
        assignment=assignment,
        voice_scripts=voice_scripts,
    )


async def _gather_slides(
    connector: AIConnector,
    brief: str,
    outline: str,
    slide_count: int,
    template_key: str,
    personalization: PersonalizationArgs,
    slide_plan_rows: list[dict[str, object]],
    reference_block: str = "",
) -> list[GeneratedSlide]:
    """확정된 플랜의 슬라이드를 장마다 독립 요청으로 만들어 generate_batch로 한 번에 보낸다.

    Qwen Modal 커넥터는 요청을 8개씩 동시에 보내고, vLLM이 이를 max-num-seqs 8 슬롯에
    묶어 처리한다. 배치 호출이 실패했거나 파싱에 실패한 장만 장별로 1회 다시 요청한다.
    """
    requests = [
        pp.build_slides_request(
            brief, outline, slide_count, template_key, personalization, slide_plan_rows, reference_block, slide_idx=idx
        )
        for idx in range(slide_count)
    ]
    parsers = [partial(pp.parse_slide, slide_idx=idx, slide_plan=slide_plan_rows) for idx in range(slide_count)]
    texts: list[str | None] = [None] * slide_count
    try:
        texts = [response.text for response in await connector.generate_batch(requests)]
    except ConnectorError as exc:
        logger.warning("parallel_generate: 슬라이드 배치 생성 실패, 장별로 다시 요청: {}", exc)
    return list(
        await asyncio.gather(
            *(_parse_or_retry(connector, req, parse, text) for req, parse, text in zip(requests, parsers, texts, strict=True))
        )
    )


async def _parse_or_retry(
    connector: AIConnector,
    request: ChapterAIRequest,
    parse_fn: Callable[[str], _Result],
    text: str | None,
) -> _Result:
    """배치 응답을 파싱하고, 응답이 없거나 파싱에 실패하면 단건으로 1회 다시 요청한다."""
    if text is not None:
        try:
            return parse_fn(text)
        except _RETRYABLE_ERRORS as exc:
            logger.warning(
                "parallel_generate: 슬라이드({}) 파싱 실패, 1회 다시 요청: {}", request.extra.get("slide_idx"), exc
            )
    return await _component(connector, request, parse_fn, retries=0)


async def _gather_voices(
    connector: AIConnector,
    brief: str,
    slide_count: int,
    voice_targets: list[VoiceTarget],
    personalization: PersonalizationArgs | None = None,
    reference_block: str = "",
) -> list:
    """슬라이드별 voice_script를 동시에 만든다.

    parse_voice에 partial로 slide_idx를 묶어 두면 JSON 파싱이 실패해 raw 텍스트로
    대체할 때도 올바른 인덱스가 들어간다.
    """
    prompt_personalization = personalization or _default_personalization_args()
    tasks = [
        _component(
            connector,
            pp.build_voice_request(
                brief,
                t.title,
                t.focus,
                t.summary,
                t.previous_title,
                t.slide_idx,
                slide_count,
                prompt_personalization,
                reference_block,
            ),
            partial(pp.parse_voice, slide_idx=t.slide_idx),
        )
        for t in voice_targets
    ]
    return list(await asyncio.gather(*tasks))


async def _component(
    connector: AIConnector,
    request: ChapterAIRequest,
    parse_fn: Callable[[str], _Result],
    retries: int = _RETRY_ATTEMPTS,
) -> _Result:
    """generate와 파싱을 하고 실패하면 retries회 다시 한다(끝까지 실패하면 ConversionError)."""
    last_error: Exception | None = None
    for attempt in range(retries + 1):
        try:
            response = await connector.generate(request)
            return parse_fn(response.text)
        except _RETRYABLE_ERRORS as exc:
            last_error = exc
            schema = str(request.extra.get("schema", "?"))
            logger.warning(
                "parallel_generate: 컴포넌트({}) 생성 실패(시도 {}/{}): {}",
                schema,
                attempt + 1,
                retries + 1,
                exc,
            )
    schema = str(request.extra.get("schema", "?"))
    raise ConversionError(f"컴포넌트({schema}) 병렬 생성이 재시도 후에도 실패했다: {last_error}")


def _template_key(state: ChapterStudioState) -> str:
    value = state.get("template_key")
    if not isinstance(value, str) or value == "":
        raise ConversionError("template_key 문자열이 필요하다.")
    return value


def _resolve_slide_plan(
    state: ChapterStudioState, template_key: str, slide_count: int
) -> list[dict[str, object]]:
    """슬라이드 파서에 넘길 slide_plan 행 목록을 정한다(visual_type/must_have 포함).

    1. state["slide_outline"]의 모든 행에 visual_type이 채워져 있으면 그대로 쓴다.
    2. 없거나 visual_type이 빠진 옛 형식이면 build_slide_plan으로 다시 만들어 직렬화한다.

    반환 행은 slide_idx·visual_type·must_have를 갖춰 _build_plan_map,
    _enforce_plan_visual_type, _relabel_chapter_title이 그대로 대조할 수 있다.
    """
    rows = state.get("slide_outline")
    if isinstance(rows, list):
        usable = [
            row
            for row in rows
            if isinstance(row, dict)
            and isinstance(row.get("slide_idx"), int)
            and isinstance(row.get("visual_type"), str)
            and row.get("visual_type")
        ]
        # slide_count개 슬롯이 모두 visual_type을 갖춘 경우에만 신뢰한다.
        if len(usable) == slide_count:
            return usable
    plans = build_slide_plan(template_key, slide_count)
    return serialize_slide_plan(plans)


def _state_with_slides(state: ChapterStudioState, slides: list[GeneratedSlide]) -> ChapterStudioState:
    """음성대본 대상을 만들 때만 생성된 슬라이드 초안을 state에 넣는다."""
    data = dict(state)
    data["slide_drafts"] = [slide.model_dump() for slide in slides]
    return cast(ChapterStudioState, data)


def _default_personalization_args() -> PersonalizationArgs:
    return PersonalizationArgs(
        weak_points="",
        audience_level="일반 학습자",
        tone=50,
        pace=50,
        tutor_depth=50,
        socratic=70,
        learning_goal="핵심 개념 이해와 실습",
    )


__all__ = ["generate_lesson_parallel"]
