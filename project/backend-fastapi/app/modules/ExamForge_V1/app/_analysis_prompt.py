"""AI 총평(약점 진단) 프롬프트를 만들고 보기 텍스트를 복원한다.

Spring과 정한 계약을 받아 총평 모델에 넘길 system/user 프롬프트를 만든다.
- options: 보기 텍스트의 JSON 배열 문자열
- selectedOption / correctOption: 0-based 인덱스 문자열
인덱스 범위·파싱 오류를 확인하며 이 둘을 사람이 읽는 보기 텍스트로 바꾼다.
"""
from __future__ import annotations

import json

# 총평 시스템 프롬프트. 친절한 과외 선생님 해요체로 쓰게 한다.
_SYSTEM_PROMPT = (
    "당신은 학생의 모의고사 결과를 분석하는 친절한 과외 선생님이에요. "
    "채점이 끝난 결과를 보고 개인 맞춤 학습 총평을 한국어로 작성해요. "
    "톤은 따뜻한 과외쌤 해요체(~해요, ~예요)를 일관되게 사용해요. "
    "마크다운 기호(#, *, - 등)는 쓰지 말고, 읽기 쉬운 자연스러운 단락으로 작성해요. "
    "전체 길이는 한국어 350~700자 내외로 핵심만 담아요. "
    "다른 설명이나 머리말 없이 총평 본문만 출력해요.\n"
    # 모델이 사고 과정·글자수 계산·초안 라벨을 본문에 섞은 적이 있어 아래 규칙으로 막는다.
    "절대 금지: 사고 과정, 글자수 계산(예: 'Total is around 750 characters'), "
    "초안/다듬기 메타 주석(예: 'Trimming the Draft', 'Final Draft'), "
    "'**...:**' 같은 작성 단계 라벨, 영어로 된 자기 지시·메타 발화를 출력하지 마세요. "
    "오직 학습자에게 그대로 보여줄 한국어 총평 본문만 곧바로 출력해요. "
    "영어 문장이나 영어 메타 발화는 한 줄도 쓰지 마세요(개념·약어 표기는 허용)."
)

# 틀린 문항이 있는 경우의 작성 지침
# 틀린 문항마다 학생이 고른 보기 텍스트를 인용하고, 그 선택이 왜 그 오개념인지
# 근거를 잇고, 다음 연습 1개를 제시한다. 같은 오개념은 묶어서 다룬다.
_INSTRUCTION_WITH_WEAKNESS = (
    "다음 순서로 한 편의 글처럼 자연스럽게 이어 써요.\n"
    "1) 점수와 등급을 근거로 한두 문장 전체 평가.\n"
    "2) 틀린 문항을 반드시 '문항 번호'로 인용해요. "
    "각 틀린 문항마다: '채점 결과'에 있는 '내가 고른 보기' 텍스트를 짧게 인용하고, "
    "왜 그 선택이 어떤 오개념에서 비롯됐는지를 짧은 오개념 라벨(예: 'FIFO/LIFO 혼동')과 함께 설명해요. "
    "예: 'N번에서 \\'큐\\' 를 골랐는데, 이는 FIFO를 LIFO와 뒤바꾼 경우예요(FIFO/LIFO 혼동).'\n"
    "3) 같은 오개념 라벨이 붙은 문항은 그룹으로 묶어요"
    "(예: '3번·8번은 모두 FIFO/LIFO 혼동이에요'). "
    "맞은 문항이 보여주는 강점도 한 줄 언급해요.\n"
    "4) 오개념 그룹마다 다음 연습 1개씩만 제안해요(과하지 않게).\n"
    "5) 마지막에 격려 한 줄.\n"
    "규칙: 채점 결과에 실제로 있는 문항 번호·보기 텍스트만 인용하고, 없는 내용을 지어내지 마세요. "
    "길이는 700자를 넘지 않도록 핵심만 담아요.\n"
    # 메타 발화 금지를 사용자 프롬프트에도 한 번 더 넣는다.
    "사고 과정·글자수 계산·'Trimming the Draft' 같은 초안 메타·작성 단계 라벨·"
    "영어 메타 발화는 절대 출력하지 말고, 학습자에게 보여줄 한국어 총평 본문만 바로 출력해요.\n"
    "총평 본문이 끝난 뒤, 반드시 맨 마지막 줄에 틀린 문항들의 약점 주제(과목 개념 단위)를 "
    "'약점주제: 주제1 | 주제2 | 주제3' 형식 한 줄로 출력해요(1~5개, 각 주제는 20자 이내). "
    "이 약점주제 줄은 시스템이 파싱하는 필수 줄이라 형식을 정확히 지키고, 어떤 경우에도 빠뜨리지 마세요."
)

# 만점일 때의 작성 지침. 약점 진단 대신 심화·응용을 제안한다.
_INSTRUCTION_PERFECT = (
    "학생이 만점을 받았어요. 약점 진단 대신 다음을 담아요.\n"
    "1) 만점을 축하하는 전체 평가 한두 문장.\n"
    "2) 이 과목에서 다음 단계로 도전할 심화·응용 학습 제안 2~4개.\n"
    "3) 마지막에 격려 한 줄.\n"
    # 만점 경로에서도 메타 발화 금지를 명시한다.
    "사고 과정·글자수 계산·초안 메타·작성 단계 라벨·영어 메타 발화는 절대 출력하지 말고, "
    "학습자에게 보여줄 한국어 총평 본문만 바로 출력해요.\n"
    "만점이므로 '약점주제:' 줄은 출력하지 않아요."
)


def restore_option_text(options_raw: str, index_raw: str) -> str:
    """보기 JSON 배열 문자열에서 인덱스 문자열에 해당하는 보기 텍스트를 복원한다.

    파싱 실패·인덱스 범위 초과·형식 오류면 사람이 읽을 수 있는 대체 문자열을 돌려준다.
    프롬프트 품질만 조금 떨어지고 흐름은 이어진다.
    """
    index = _parse_index(index_raw)
    if index is None:
        return f"(보기 인덱스 해석 불가: {index_raw!r})"
    options = _parse_options(options_raw)
    if options is None:
        return f"(보기 목록 파싱 불가, 선택 인덱스 {index})"
    if not 0 <= index < len(options):
        return f"(보기 인덱스 {index} 범위 초과, 보기 {len(options)}개)"
    return str(options[index])


def _parse_index(index_raw: str) -> int | None:
    """0-based 인덱스 문자열을 정수로 변환한다. 실패 시 None."""
    try:
        return int(str(index_raw).strip())
    except (ValueError, TypeError):
        return None


def _parse_options(options_raw: str) -> list[object] | None:
    """보기 JSON 배열 문자열을 리스트로 파싱한다. 배열이 아니면 None."""
    try:
        parsed = json.loads(options_raw)
    except (json.JSONDecodeError, TypeError):
        return None
    return parsed if isinstance(parsed, list) else None


def build_user_prompt(
    *,
    subject: str,
    score: int,
    total_points: int,
    grade: str,
    question_lines: list[str],
    is_perfect: bool,
) -> str:
    """총평 모델에 넘길 사용자 프롬프트를 만든다."""
    instruction = _INSTRUCTION_PERFECT if is_perfect else _INSTRUCTION_WITH_WEAKNESS
    header = (
        f"과목: {subject}\n"
        f"점수: {score}/{total_points} (등급 {grade})\n\n"
        f"문항별 채점 결과:\n"
    )
    body = "\n".join(question_lines) if question_lines else "(문항 정보 없음)"
    return f"{header}{body}\n\n{instruction}"


def system_prompt() -> str:
    """총평 작성용 시스템 프롬프트를 반환한다."""
    return _SYSTEM_PROMPT


def split_weak_topics(text: str) -> tuple[str, list[str]]:
    """총평 텍스트에서 마지막 '약점주제:' 줄을 분리해 (본문, 약점 목록)을 반환한다.

    형식이 없거나 깨졌으면 본문을 그대로 두고 빈 목록을 돌려준다.
    호출부가 빈 목록을 보고 로그를 남긴다.
    """
    lines = text.rstrip().splitlines()
    for idx in range(len(lines) - 1, -1, -1):
        line = lines[idx].strip()
        if not line:
            continue
        if line.startswith("약점주제:") or line.startswith("약점 주제:"):
            raw = line.split(":", 1)[1]
            topics = [t.strip()[:60] for t in raw.split("|") if t.strip()]
            body = "\n".join(lines[:idx]).rstrip()
            return (body or text.strip(), topics[:5])
        # 마지막 비어있지 않은 줄이 약점주제 형식이 아니면 본문으로 간주하고 종료
        break
    return (text.strip(), [])
