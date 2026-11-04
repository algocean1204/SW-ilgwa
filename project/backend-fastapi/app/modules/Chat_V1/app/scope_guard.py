"""Chat_V1 답변의 강의 범위와 슬라이드 인용을 검사하는 순수 함수 모음.

답변 모델이 강의 자료 밖 일반 지식을 끌어오거나 없는 슬라이드를 인용하면
후처리로 걸러낸다. 부수효과가 없어 단위 테스트로 바로 확인할 수 있다.

공개 API:
    - SCOPE_NOTICE: 강의 범위 밖 답변 뒤에 붙이는 한 줄 안내문
    - OUT_OF_SCOPE_REPLY: 예전 거절문. 과거 대화를 걸러낼 때만 쓴다
    - extract_referenced_slides: '[슬라이드 N]' 인용을 0-based 인덱스로 바꾸고 상한을 검사한다
    - build_lecture_keywords: 강의 컨텍스트에서 매칭용 키워드 어간을 만든다
    - apply_hallucination_guard: 강의와 겹치지 않는 답변 끝에 범위 밖 안내문을 붙인다
"""
from __future__ import annotations

import re

from app.modules.Chat_V1.app.schemas import ChatRequest, VoiceChatRequest

# 강의 범위 밖 답변 끝에 붙이는 안내문. 일반 지식 답변은 거절하지 않고 이 한 줄만 붙인다.
SCOPE_NOTICE: str = "이 내용은 이번 강의 범위 밖이에요."

# 예전 거절문. 새 답변에는 쓰지 않고, Spring recent_qa에 남은 과거 거절 답변을
# 컨텍스트에서 걸러낼 때만 쓴다.
OUT_OF_SCOPE_REPLY: str = (
    "이 강의에서는 다루지 않는 내용이에요. 관련 슬라이드를 함께 확인해 보시겠어요?"
)

# 짧은 답변은 매칭을 믿기 어려워 범위 검사를 건너뛴다.
# 한 글자 토큰(조사·기호)은 노이즈라 키워드에서 뺀다.
_GUARD_MIN_ANSWER_LEN: int = 12
_GUARD_MIN_KEYWORD_LEN: int = 2
# 이 어구가 있으면 거절문 계열로 보고 범위 검사를 건너뛴다.
_REFUSAL_MARKERS: tuple[str, ...] = ("다루지 않는", "강의에서는", "확인해 보시")

# '[슬라이드 N]' 인용에서 N(1-based)을 뽑는다.
_SLIDE_CITE_RE = re.compile(r"\[슬라이드\s+(\d+)\]")
# 한글·영문·숫자가 이어진 구간만 토큰으로 본다.
_TOKEN_RE = re.compile(r"[0-9A-Za-z가-힣]+")

# 토큰 끝에서 떼어낼 조사('벡터는'→'벡터'). 긴 접미사부터 맞춰 과하게 잘리지 않게 한다.
_PARTICLE_SUFFIXES: tuple[str, ...] = (
    "에서는", "으로는", "에게서", "이라는", "라는",
    "에서", "에게", "으로", "처럼", "보다", "까지", "부터", "마다", "이나",
    "은", "는", "이", "가", "을", "를", "의", "에", "와", "과", "도", "로", "나",
)
# 어떤 강의에나 흔히 나오는 일반어라 매칭 기준에서 뺀다.
_GENERIC_STEMS: frozenset[str] = frozenset(
    {"수", "것", "때", "등", "이것", "그것", "내용", "경우", "정도", "여기", "거기"}
)


def extract_referenced_slides(answer: str, slide_count: int | None = None) -> list[int]:
    """답변의 '[슬라이드 N]' 인용을 0-based 인덱스 목록으로 바꾼다.

    중복을 빼고 오름차순으로 돌려준다. slide_count가 주어지면 실제 슬라이드 수를
    넘는 인용(슬라이드 3장에 [슬라이드 7])은 버려, 없는 슬라이드가 Spring·프론트로
    넘어가지 않게 한다. None이면 하한(idx>=0)만 검사한다.
    """
    matches = _SLIDE_CITE_RE.findall(answer)
    seen: set[int] = set()
    result: list[int] = []
    for m in matches:
        idx = int(m) - 1  # 1-based → 0-based
        if idx < 0 or idx in seen:
            continue
        if slide_count is not None and idx >= slide_count:
            continue
        seen.add(idx)
        result.append(idx)
    return sorted(result)


def _to_stem(token: str) -> str:
    """토큰 끝의 조사를 떼어 어간만 남긴다.

    답변과 강의 토큰을 어간 기준으로 비교하려고 쓴다. 떼고 나서 한 글자도
    남지 않으면 원래 토큰을 그대로 둔다.
    """
    for suffix in _PARTICLE_SUFFIXES:
        if token.endswith(suffix) and len(token) - len(suffix) >= 1:
            return token[: -len(suffix)]
    return token


def _content_stems(text: str) -> set[str]:
    """텍스트에서 조사를 떼고 일반어를 뺀 어간 집합을 만든다."""
    stems: set[str] = set()
    for token in _TOKEN_RE.findall(text.lower()):
        stem = _to_stem(token)
        if len(stem) >= _GUARD_MIN_KEYWORD_LEN and stem not in _GENERIC_STEMS:
            stems.add(stem)
    return stems


def build_lecture_keywords(request: ChatRequest | VoiceChatRequest) -> list[str]:
    """강의 컨텍스트(제목·슬라이드·음성대본·퀴즈)에서 키워드 어간 목록을 만든다.

    범위 검사에서 답변이 강의 자료와 겹치는지 볼 때 쓴다. 조사를 떼고 일반어를
    빼서 '수를'·'것이다' 같은 토큰이 우연히 맞아 범위 밖 답변을 놓치는 일을 줄인다.
    """
    ctx = request.lecture_context
    parts: list[str] = [ctx.chapter_title]
    for slide in ctx.slides:
        parts.append(slide.title)
        parts.append(slide.content)
    if ctx.voice_scripts:
        parts.extend(ctx.voice_scripts)
    if ctx.quiz_items:
        parts.extend(ctx.quiz_items)

    keywords: set[str] = set()
    for raw in parts:
        keywords |= _content_stems(raw)
    return sorted(keywords)


def _looks_like_refusal(answer: str) -> bool:
    """답변에 거절문 어구가 들어 있는지 본다."""
    return any(marker in answer for marker in _REFUSAL_MARKERS)


def apply_hallucination_guard(
    answer: str, lecture_keywords: list[str]
) -> tuple[str, bool]:
    """강의 자료와 겹치지 않는 답변 끝에 범위 밖 안내문을 붙인다.

    강의 밖 질문도 일반 지식으로 답하게 두고, 답변을 거절문으로 바꾸지 않는다.
    학생이 강의 범위를 알 수 있게 SCOPE_NOTICE 한 줄만 붙인다.

    다음 경우에는 강의에 근거한 답변으로 보고 그대로 둔다.
      - 답변이 비었거나 너무 짧을 때
      - 이미 거절문이나 안내문이 들어 있을 때
      - 비교할 강의 키워드가 없을 때
      - 슬라이드 인용([슬라이드 N])이 하나라도 있을 때
      - 답변 어간이 강의 키워드와 하나라도 겹칠 때

    반환: (최종 답변, 안내문을 붙였는지)
    """
    stripped = answer.strip()
    if len(stripped) < _GUARD_MIN_ANSWER_LEN:
        return answer, False
    if _looks_like_refusal(stripped) or SCOPE_NOTICE in stripped:
        return answer, False
    if not lecture_keywords:
        return answer, False
    if _SLIDE_CITE_RE.search(stripped):
        return answer, False

    # 어간 단위로 비교해서 '벡터가'와 '벡터는'처럼 조사만 다른 형태도 같은 단어로 본다.
    answer_stems = _content_stems(stripped)
    if answer_stems & set(lecture_keywords):
        return answer, False
    # 겹치는 어간이 없으면 일반 지식 답변으로 보고 안내문만 붙인다.
    return f"{stripped}\n\n{SCOPE_NOTICE}", True
