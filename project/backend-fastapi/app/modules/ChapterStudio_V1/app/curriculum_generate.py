"""커리큘럼 생성 흐름. plan-first 원칙을 따른다.

Spring POST /api/curriculum/generate를 받은 뒤 백그라운드에서 실행된다.
흐름:
  1. blueprint(결정적): N개 슬롯·단계·주제 범위·역할을 AI 없이 정한다
  2. 슬롯 루프 요청: 각 슬롯의 텍스트(title/summary/learning_goal/key_topics)만 AI에 요청한다
  3. Pydantic strict 검증, 슬롯 수 == N 확인
  4. persist_curriculum으로 DB에 저장한다

AI는 고정 슬롯 안의 텍스트만 채운다.
챕터 개수·순서·단계 역할·주제 범위는 블루프린트가 정하며 AI가 바꿀 수 없다.
get_connection은 search_path를 설정하지 않으므로 DB 쓰기는 schema를 명시한다.
"""
from __future__ import annotations

import asyncio
import json
import logging
import re
from pathlib import Path

from pydantic import BaseModel, ConfigDict, Field, ValidationError

from app.modules.ChapterStudio_V1.ai_connectors.registry import get_planner_connector
from app.modules.ChapterStudio_V1.ai_connectors.schemas import ChapterAIRequest
from app.modules.ChapterStudio_V1.app.curriculum_blueprint import (
    ChapterSlot,
    build_curriculum_blueprint,
)
from app.modules.ChapterStudio_V1.common.config import active_planner_model
from app.modules.ChapterStudio_V1.db.curriculum_persistence import persist_curriculum
from common.db import get_connection

_LOG = logging.getLogger(__name__)

# 슬롯 응답 output-schema 파일 경로
_GEN_SCHEMA_PATH = Path(__file__).with_name("codex_curriculum_generate.schema.json")

_PLANNER_SYSTEM = (
    "너는 한국어 AI 과외 커리큘럼 설계자다. "
    "주어진 슬롯 명세에 따라 각 슬롯의 title·summary·learning_goal·key_topics 문구만 한국어로 채운다. "
    "슬롯 개수·순서·단계역할·주제범위는 이미 확정되어 있으므로 절대 변경하지 않는다. "
    "반드시 JSON 배열만 출력하고, 설명·코드펜스·생각과정은 절대 출력하지 않는다."
)

# Qwen3 thinking 출력의 <think>...</think> 블록을 지운다.
_THINK_RE = re.compile(r"<think>.*?</think>", re.DOTALL)
_SPRING_COMMIT_RETRY_ATTEMPTS = 5
_SPRING_COMMIT_RETRY_DELAY_SEC = 1.0
_COURSE_FAILED_STATUS = "CURRICULUM_FAILED"
# 커리큘럼 프롬프트가 발췌로 커지지 않게 PDF 발췌 블록 상한을 둔다.
_PDF_EXCERPT_MAX_HITS = 5
_PDF_EXCERPT_SNIPPET_CHARS = 240


class _SlotResponse(BaseModel):
    """AI가 채운 단일 슬롯 텍스트 응답이다."""

    model_config = ConfigDict(strict=True, frozen=True)

    order: int = Field(ge=1, le=30)
    stage: str = Field(min_length=1, max_length=20)
    title: str = Field(min_length=1, max_length=120)
    summary: str = Field(min_length=1, max_length=300)
    learning_goal: str = Field(min_length=1, max_length=200)
    key_topics: list[str] = Field(min_length=3, max_length=5)


async def generate_and_store_curriculum(
    course_id: str,
    subject: str,
    lesson_count: int,
    source_type: str,
) -> None:
    """커리큘럼을 생성·저장하고, 실패하면 course 상태를 실패로 바꾼다."""
    try:
        course = await _load_course_after_spring_commit(course_id)
        if course is None:
            _LOG.error("[curriculum] course 없음 — courseId=%s", course_id)
            await mark_course_curriculum_failed(course_id, "course not found")
            return

        n = max(1, lesson_count)
        lessons = await _plan_with_blueprint(course, subject, n)
        if not lessons:
            _LOG.error("[curriculum] planner가 유효한 강의를 만들지 못함 — courseId=%s", course_id)
            await mark_course_curriculum_failed(course_id, "planner returned empty lessons")
            return

        async with get_connection() as conn:
            saved = await persist_curriculum(
                conn, course=course, lessons=lessons, planner_model=active_planner_model()
            )
        _LOG.info("[curriculum] 저장 완료 — courseId=%s, 강의 %d개", course_id, saved)
    except Exception as exc:
        _LOG.error("[curriculum] 생성 실패 — courseId=%s, error=%s", course_id, exc, exc_info=True)
        await mark_course_curriculum_failed(course_id, str(exc))


async def mark_course_curriculum_failed(course_id: str, reason: str) -> None:
    """실패가 조용히 묻히지 않게 public.course 상태를 CURRICULUM_FAILED로 바꾼다."""
    try:
        async with get_connection() as conn:
            await conn.execute(
                "UPDATE public.course SET status = $2, updated_at = NOW() WHERE id = $1",
                course_id,
                _COURSE_FAILED_STATUS,
            )
    except Exception as exc:
        _LOG.error(
            "[curriculum] 실패 상태 기록 실패 — courseId=%s, reason=%s, error=%s",
            course_id,
            reason,
            exc,
        )


async def _plan_with_blueprint(
    course: dict,
    subject: str,
    lesson_count: int,
) -> list[dict]:
    """블루프린트 기반 plan-first 생성 흐름이다.

    1. 결정적 블루프린트 생성 (AI 없음)
    2. 전체 슬롯을 한 번에 AI에 요청 (슬롯별 텍스트만)
    3. 결정적 검증: 개수 == N, 슬롯 1:1 대응, Pydantic strict
    4. N 미달 시 부족 슬롯만 재요청 (최대 1회)
    """
    topic = course.get("topic_text") or course.get("course_name") or subject or "학습 주제"
    subject_name = course.get("subject") or subject or "일반"

    # topic_scope는 관심 과목(subject)이 아니라 사용자가 입력한 학습 주제(topic)를 따른다.
    blueprint = build_curriculum_blueprint(
        subject=subject_name,
        lesson_count=lesson_count,
        detected_topics=_learning_topics_for_blueprint(topic, subject_name),
    )

    # PDF 소스 course면 OCR 인제스트된 발췌를 커리큘럼 프롬프트에 근거로 주입한다.
    # 검색 실패는 빈 블록으로 폴백해 topic 모드와 동일한 흐름을 유지한다.
    pdf_block = await _pdf_excerpt_block(course, topic)

    connector = get_planner_connector()
    prompt = _build_slot_loop_prompt(topic, subject_name, blueprint, pdf_block)
    req = ChapterAIRequest(
        # gemini_flash는 thinking 토큰(~21000)이 출력 예산을 먼저 써서 8000이면 JSON이 잘리기 쉽다.
        # thinking 여유분을 넣어 32000으로 둔다.
        system=_PLANNER_SYSTEM,
        user=prompt,
        max_tokens=32000,
        temperature=0.2,
        extra=_codex_schema_extra(),
    )
    resp = await connector.generate(req)
    slot_responses = _parse_and_validate(resp.text, blueprint)

    # N개에 못 미치면 부족 슬롯만 한 번 다시 요청한다.
    if len(slot_responses) < lesson_count:
        slot_responses = await _repair_missing_slots(
            connector, topic, subject_name, blueprint, slot_responses, pdf_block
        )

    # 재요청 뒤에도 모자라면 예외로 실패시킨다.
    if len(slot_responses) < lesson_count:
        missing = lesson_count - len(slot_responses)
        raise ValueError(
            f"블루프린트 N={lesson_count}개 요구 중 {missing}개 슬롯 미충족 — "
            "AI 응답이 지속적으로 슬롯 수를 맞추지 못했다."
        )

    return [_slot_response_to_lesson(sr, blueprint[sr.order - 1]) for sr in slot_responses]


def _learning_topics_for_blueprint(learning_topic: str, subject: str) -> list[str] | None:
    """사용자 학습 주제를 슬롯 topic_scope 풀로 변환한다.

    관심 과목(예: 토익)과 학습 주제(예: 파이썬 기초)가 다를 때 subject 기본 풀
    ('토익 기초' 등)로 떨어지지 않게 한다. 주제가 비어 있거나 subject와 동일하면
    None을 반환해 기존 subject 폴백을 유지한다.
    """
    resolved_topic = (learning_topic or "").strip()
    resolved_subject = (subject or "").strip()
    if not resolved_topic:
        return None
    if resolved_subject and resolved_topic.casefold() == resolved_subject.casefold():
        return None
    return [resolved_topic]


def _build_slot_loop_prompt(
    topic: str,
    subject: str,
    blueprint: list[ChapterSlot],
    pdf_block: str = "",
) -> str:
    """각 슬롯의 명세를 나열하고 텍스트 채우기를 지시하는 프롬프트를 생성한다.

    AI에게 전달하는 내용:
    - 슬롯마다 order/stage/topic_scope/role이 고정되어 있음을 명시
    - 채울 항목: title, summary, learning_goal, key_topics만
    - 구조 변경 금지 명령
    - pdf_block이 있으면 업로드 PDF 발췌를 근거로 제시(없으면 블록 자체를 생략)
    """
    n = len(blueprint)
    slot_lines = "\n".join(
        f'  {{"order":{s.order},"stage":"{s.stage}",'
        f'"topic_scope":"{s.topic_scope}","role":"{s.role}"}}'
        for s in blueprint
    )
    pdf_section = ""
    if pdf_block.strip():
        pdf_section = (
            "참고도서 발췌(업로드 PDF의 OCR 결과, page 번호 유지):\n"
            f"{pdf_block.strip()}\n"
            "각 슬롯의 title·summary·learning_goal·key_topics는 위 발췌 내용에 근거해 구성한다. "
            "발췌에 없는 내용을 추가할 때는 일반 원리 수준만 허용한다.\n\n"
        )
    return (
        "/no_think\n"
        f"과외 커리큘럼 텍스트 채우기 작업이다.\n"
        f"주제: {topic}\n과목: {subject}\n전체 강의 수: {n}개 (고정, 변경 불가)\n\n"
        f"{pdf_section}"
        f"아래 {n}개 슬롯의 order·stage·topic_scope·role은 이미 결정되어 있다.\n"
        "각 슬롯에 대해 title·summary·learning_goal·key_topics(3~5개)만 한국어로 채워라.\n"
        "슬롯 수·순서·stage·topic_scope는 절대 변경하지 마라.\n\n"
        f"슬롯 명세:\n{slot_lines}\n\n"
        "출력 형식 (JSON 배열만, 다른 텍스트·코드펜스 금지):\n"
        '[{"order":1,"stage":"관점 잡기","title":"제목","summary":"한 문장 요약",'
        '"learning_goal":"학습 목표","key_topics":["키1","키2","키3"]}, ...]\n\n'
        f"규칙: 배열 길이 정확히 {n}개. order는 1~{n} 연속. "
        "key_topics 3~5개. title 120자 이내. summary 300자 이내. "
        "learning_goal 200자 이내. 모든 텍스트는 한국어."
    )


async def _pdf_excerpt_block(course: dict, topic: str) -> str:
    """업로드 PDF(OCR 인제스트 완료)의 발췌 블록을 만든다. 실패하면 빈 문자열이다.

    OCR 인제스트가 Qdrant에 저장한 청크를 강의 주제로 검색해 'p.번호: 발췌' 줄 목록을
    만든다. 컬렉션명은 pdf_context_injector와 같은 규칙으로 만든다.
    Qdrant 미연결·컬렉션 없음 등 인프라 예외는 커리큘럼 생성 실패로 번지지 않게
    경고만 남기고 topic 모드와 동일한 빈 블록으로 폴백한다.
    """
    source_ref = course.get("source_pdf_url")
    if not isinstance(source_ref, str) or not source_ref.strip():
        return ""
    try:
        from app.modules.ChapterStudio_V1.app.pdf_context_injector import collection_name_from_source_ref
        from app.modules.OCR_v1.search import hybrid_search

        collection = collection_name_from_source_ref(source_ref)
        hits = await hybrid_search(topic, collection, top_k=_PDF_EXCERPT_MAX_HITS)
    except Exception as exc:
        _LOG.warning(
            "[curriculum] PDF 발췌 검색 실패(무시) — courseId=%s, error=%s",
            course.get("id"),
            exc,
        )
        return ""
    lines: list[str] = []
    for hit in hits[:_PDF_EXCERPT_MAX_HITS]:
        if not isinstance(hit, dict):
            continue
        text = str(hit.get("text", "")).strip()
        if not text:
            continue
        payload = hit.get("payload")
        page = payload.get("page_num", 1) if isinstance(payload, dict) else 1
        snippet = re.sub(r"\s+", " ", text)[:_PDF_EXCERPT_SNIPPET_CHARS]
        lines.append(f"- p.{page}: {snippet}")
    if not lines:
        # 0건이면 컬렉션명 불일치나 OCR 미완료일 수 있어 warning으로 남긴다.
        _LOG.warning(
            "[curriculum] PDF 발췌 0건 — courseId=%s, source_ref=%s",
            course.get("id"),
            source_ref,
        )
        return ""
    return "\n".join(lines)


def _codex_schema_extra() -> dict[str, str]:
    """output-schema 경로를 extra로 넘긴다.

    기본 planner는 opus46(ACTIVE_PLANNER_MODEL 기본값)이고, Anthropic 커넥터는 extra의
    output_schema_path를 읽지 않아 이 값은 no-op이다. 그래서 opus46 경로의 출력 검증은
    _parse_and_validate의 Pydantic strict 검증과 슬롯 1:1 대응 확인뿐이다. 현재
    ChapterStudio 커넥터 중 이 값을 읽는 것은 없고, 읽는 커넥터를 붙일 때만 추가 검증이 된다.
    """
    if _GEN_SCHEMA_PATH.exists():
        return {"output_schema_path": str(_GEN_SCHEMA_PATH)}
    return {}


def _extract_slots_list(text: str) -> list | None:
    """AI 응답 텍스트에서 슬롯 배열을 꺼낸다.

    우선순위:
    1. output-schema 래핑 형태 {"slots":[...]}: slots 키를 직접 읽는다.
    2. bare array [...](opus 등): 최상위 배열을 그대로 파싱한다.
    3. 둘 다 실패하면 None을 돌려주고 호출자가 빈 리스트로 처리한다.

    스키마가 최상위 array에서 object로 바뀐 뒤에도 find('[')/rfind(']') 방식은
    {"slots":[...]} 안의 배열을 우연히 잡아 동작했다. 키 이름이나 구조가 바뀌면
    조용히 깨질 수 있어 slots 키를 먼저 읽고, 나머지는 substring 방식으로 처리한다.
    """
    # 전체를 JSON으로 파싱해 slots 키나 최상위 배열을 찾는다.
    try:
        obj = json.loads(text)
        if isinstance(obj, dict) and "slots" in obj and isinstance(obj["slots"], list):
            return obj["slots"]
        if isinstance(obj, list):
            return obj
    except json.JSONDecodeError:
        pass

    # 실패하면 첫 '['부터 마지막 ']'까지 잘라 다시 파싱한다(앞뒤에 글이 붙은 배열).
    start = text.find("[")
    end = text.rfind("]")
    if start == -1 or end == -1 or end <= start:
        return None
    try:
        raw_items = json.loads(text[start : end + 1])
        if isinstance(raw_items, list):
            return raw_items
    except json.JSONDecodeError:
        pass
    return None


def _parse_and_validate(
    raw: str,
    blueprint: list[ChapterSlot],
) -> list[_SlotResponse]:
    """AI 출력을 Pydantic strict로 검증하고 블루프린트 슬롯과 1:1로 맞는지 확인한다.

    {"slots":[...]} 래핑 형태와 bare array(opus 경로)를 모두 처리한다.
    order/stage가 블루프린트와 다른 슬롯은 결과에서 뺀다.
    """
    text = _THINK_RE.sub("", raw).strip()
    raw_items = _extract_slots_list(text)

    if raw_items is None:
        _LOG.error("[curriculum] JSON 배열을 찾지 못함: %r", text[:300])
        return []

    if not isinstance(raw_items, list):
        _LOG.error("[curriculum] 파싱 결과가 배열이 아님 — type=%s", type(raw_items).__name__)
        return []

    # order → ChapterSlot 맵으로 1:1 대응을 확인한다.
    bp_map: dict[int, ChapterSlot] = {s.order: s for s in blueprint}
    validated: list[_SlotResponse] = []

    for item in raw_items:
        if not isinstance(item, dict):
            continue
        try:
            sr = _SlotResponse.model_validate(item)
        except ValidationError as exc:
            _LOG.warning("[curriculum] 슬롯 Pydantic 검증 실패 (order=%s): %s", item.get("order"), exc)
            continue

        # AI가 돌려준 stage는 블루프린트 슬롯의 stage와 같아야 한다.
        expected_slot = bp_map.get(sr.order)
        if expected_slot is None:
            _LOG.warning("[curriculum] 블루프린트에 없는 order=%d 슬롯 무시", sr.order)
            continue
        if sr.stage != expected_slot.stage:
            _LOG.warning(
                "[curriculum] stage 불일치 슬롯 제외 — order=%d, 기대=%s, 수신=%s",
                sr.order,
                expected_slot.stage,
                sr.stage,
            )
            continue

        validated.append(sr)

    # order 순으로 정렬하고, 같은 order는 첫 번째만 남긴다.
    seen_orders: set[int] = set()
    deduped: list[_SlotResponse] = []
    for sr in sorted(validated, key=lambda x: x.order):
        if sr.order not in seen_orders:
            seen_orders.add(sr.order)
            deduped.append(sr)

    return deduped


async def _repair_missing_slots(
    connector: object,
    topic: str,
    subject: str,
    blueprint: list[ChapterSlot],
    received: list[_SlotResponse],
    pdf_block: str = "",
) -> list[_SlotResponse]:
    """N 미달 시 부족한 슬롯만 재요청한다 (최대 1회).

    부족 슬롯의 블루프린트 명세만 다시 보내 AI 호출을 줄인다.
    PDF 발췌 블록이 있으면 재요청 프롬프트에도 동일하게 전달한다.
    """
    received_orders = {sr.order for sr in received}
    missing_slots = [s for s in blueprint if s.order not in received_orders]

    if not missing_slots:
        return received

    _LOG.warning(
        "[curriculum] 슬롯 미달 — 부족 슬롯 재요청: %s",
        [s.order for s in missing_slots],
    )

    repair_prompt = _build_slot_loop_prompt(topic, subject, missing_slots, pdf_block)
    req = ChapterAIRequest(
        # gemini_flash thinking 토큰(~21000)이 예산을 먼저 써서 4000이면 보충 JSON이 잘리기 쉽다.
        # 출력은 부분 슬롯이라 작지만 thinking 여유분을 넣어 24000으로 둔다.
        system=_PLANNER_SYSTEM,
        user=repair_prompt,
        max_tokens=24000,
        temperature=0.15,
        extra=_codex_schema_extra(),
    )
    from app.modules.ChapterStudio_V1.ai_connectors.base import AIConnector

    if not isinstance(connector, AIConnector):
        return received

    resp = await connector.generate(req)
    repaired = _parse_and_validate(resp.text, missing_slots)

    merged = list(received) + repaired
    merged.sort(key=lambda x: x.order)
    # 재요청 결과가 기존 응답과 겹치면 먼저 받은 것을 남긴다.
    seen: set[int] = set()
    result: list[_SlotResponse] = []
    for sr in merged:
        if sr.order not in seen:
            seen.add(sr.order)
            result.append(sr)
    return result


def _slot_response_to_lesson(
    sr: _SlotResponse,
    slot: ChapterSlot,
) -> dict:
    """_SlotResponse + ChapterSlot을 persist_curriculum이 기대하는 dict로 변환한다.

    블루프린트에서 결정된 slide_count·estimated_minutes를 우선 사용하고
    key_topics는 AI가 채운 문구를 사용한다 (3~5개 범위 clamp).
    """
    key_topics = list(sr.key_topics)[:5]
    if len(key_topics) < 3:
        # key_topics가 3개 미만이면 블루프린트 힌트로 채운다.
        hints = slot.key_topics[: 3 - len(key_topics)]
        key_topics = key_topics + hints

    return {
        "order": sr.order,
        "title": sr.title,
        "description": sr.summary,
        # slide_count는 블루프린트(slide_count_from_minutes)가 정한 값이다(AI 미관여).
        "slide_count": _clamp_int(slot.slide_count, 12, 10, 15),
        "estimated_minutes": _clamp_int(slot.estimated_minutes, 30, 20, 60),
        "learning_goal": sr.learning_goal,
        "key_topics": key_topics,
    }


async def _load_course_after_spring_commit(course_id: str) -> dict | None:
    """Spring course INSERT 커밋 직후 호출되는 경우를 위해 짧게 재조회한다."""
    for attempt in range(1, _SPRING_COMMIT_RETRY_ATTEMPTS + 1):
        course = await _load_course(course_id)
        if course is not None:
            return course
        if attempt < _SPRING_COMMIT_RETRY_ATTEMPTS:
            _LOG.info(
                "[curriculum] course 커밋 대기 — courseId=%s, retry=%d/%d",
                course_id,
                attempt,
                _SPRING_COMMIT_RETRY_ATTEMPTS,
            )
            await asyncio.sleep(_SPRING_COMMIT_RETRY_DELAY_SEC)
    return None


async def _load_course(course_id: str) -> dict | None:
    """public.course에서 커리큘럼 생성에 필요한 메타를 읽는다."""
    async with get_connection() as conn:
        row = await conn.fetchrow(
            "SELECT id, user_id, course_name, subject, source_type, source_pdf_url, topic_text "
            "FROM public.course WHERE id = $1",
            course_id,
        )
    return dict(row) if row is not None else None


def _clamp_int(value: object, default: int, low: int, high: int) -> int:
    """정수로 변환하고 [low, high] 범위로 제한한다.

    `value`는 int·float·str 등 int() 변환이 가능한 값을 받는다.
    변환 불가 시 TypeError/ValueError를 잡아 default를 반환한다.
    `str(value)`를 거쳐 mypy arg-type 경고 없이 변환한다.
    """
    try:
        n = int(str(value))
    except (TypeError, ValueError):
        return default
    return max(low, min(high, n))
