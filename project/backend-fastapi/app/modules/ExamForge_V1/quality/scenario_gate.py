"""시나리오 중복·개념 과대표현을 규칙으로 잡는 게이트(과목 무관).

생성된 문항 세트의 품질 결함을 구조 신호만으로 잡는다. 파이썬·스프링·수학·자격증
어느 과목에도 써야 해서 과목 단어를 하드코딩하지 않고, 숫자 시퀀스·정규화 토큰·
concept_key만 본다.

(1) 시나리오 중복(scenario duplicate)
    같은 삽입 수열/트리/예시 시나리오를 재활용한 문항을 잡는다. 발문 어휘가
    조금 달라도 stem 안의 "숫자 시퀀스 + 정규화 토큰"이 같으면 같은
    시나리오로 보고 두 번째 이후 문항을 교체 대상으로 표시한다.
    deduplicator(토큰·문자 n-gram 유사도)가 놓치는 "수치 예시만 같은" 재활용을 잡는다.

(1.5) 의미 중복(semantic duplicate)
    숫자열 서명이 못 잡는, 숫자 없이 "발문은 다르나 같은 지식을 묻는" 중복을 잡는다.
    stem(+correct_answer)에서 상투어를 뺀 내용 토큰 집합의 Jaccard 유사도가 임계
    이상이면 한 클러스터로 묶고, 보존 한도를 넘는 뒤쪽 문항을 교체 대상으로 표시한다.
    영문 식별자(@SpringBootApplication, Starter 등)는 원형 그대로 강한 중복 신호로 쓴다.
    임베딩·LLM 없이 토큰만 쓴다.

(2) 개념 과대표현(concept over-representation)
    같은 정규화 개념(concept_key 또는 stem 정규화 키)이 전체의 임계 비율을
    넘게 출제되면(예: 20% 초과 또는 2개 초과) 초과분을 교체 대상으로 표시한다.
    좁은 자료에서 같은 개념이나 지엽 개념이 반복 출제되는 것을 막는다.

[IN]  questions: 문항 dict 리스트 (stem, _concept_key/concept_key, draft_id/question_id)
[OUT] {draft_id: [issue 문자열...]} 교체 대상 매핑(빈 매핑이면 통과)

[다른 단계와의 연결]
- validate_node가 결과를 failed_ids에 합친다(이미 있으면 건너뛴다).
- 교체는 repair·재생성 경로(EXAMFORGE_TARGETED_REPAIR / question_repair)가 한다.
- 좁은 자료라 교체가 안 되면 route_after_validation의 재시도 상한
  (max_retries / count_stuck_rounds / missing_retry_cap)에서 멈추고 확보분으로 통과한다.
  이 게이트가 무한 루프를 만들지 않는다.

[오탐 줄이기]
- 문항이 1개 이하면 비교 대상이 없어 빈 매핑을 반환한다.
- 시그니처가 비면(숫자·토큰이 전혀 없는 빈 stem 등) 구조 검증 영역이므로 제외한다.
- 과대표현은 "비율 또는 절대 개수" 중 하나만 넘어도 플래그한다. 전체 문항이 적으면
  개념 종류가 원래 적어서 정상 세트를 잘못 잡지 않게 절대 개수 하한을 둔다.
"""
from __future__ import annotations

import re

from app.modules.ExamForge_V1.common.logger import get_logger

logger = get_logger(__name__)


# 한국어+영어+숫자 토큰. 숫자는 별도 추출하므로 토큰 추출 시에는 글자 위주로 본다.
_WORD_RE = re.compile(r"[가-힣a-z0-9_]+")

# stem 안의 정수·실수 시퀀스. 같은 수치 예시 재활용을 잡는 신호다.
_NUMBER_RE = re.compile(r"-?\d+(?:\.\d+)?")

# 시험 상투어. "다음 중 옳은 것은" 같은 공통 문구가 시그니처에 섞이지 않게 뺀다.
# relevance_gate·deduplicator의 불용어와 맞춰 둔다.
_BOILERPLATE_STOPWORDS: frozenset[str] = frozenset({
    "다음", "중", "가장", "적절한", "옳은", "옳지", "않은", "것은", "것을",
    "대한", "대해", "설명", "으로", "에서", "하고", "하는", "하여", "경우",
    "문제", "문항", "고르시오", "고르면", "쓰시오", "서술하시오", "선택하시오",
    "보기", "정답", "무엇인가", "무엇", "어떤", "어느", "이유", "결과",
    "올바른", "올바르게", "틀린", "잘못된", "아닌", "모두", "각각", "때",
    "the", "is", "are", "what", "which", "of", "to", "in", "a", "an",
    "and", "or", "for", "following", "correct", "answer",
})

# 한국어 조사·어미 제거. deduplicator·relevance_gate와 같은 규칙이다.
_JOSA_SUFFIXES: tuple[str, ...] = (
    "으로", "에서", "에게", "하고", "하는", "이다", "이며",
    "을", "를", "은", "는", "이", "가", "의", "에",
)

# 시그니처에 넣을 토큰 최대 개수. 너무 많으면 작은 표현 차이로 시그니처가 어긋나
# 중복을 놓쳐서, 정렬한 상위 N개만 비교한다.
_MAX_SIGNATURE_TOKENS = 8

# 숫자 시퀀스가 이 길이 이상이면 그 자체로 시나리오를 식별하는 지배적 신호로 본다.
# 같은 데이터 예시(삽입 수열·좌표·계수 등)는 어순/발문이 달라도 숫자열이 같다.
# 토큰 비교는 어미·조사 변형(삽입했/삽입한/삽입)에 약해서, 숫자열이 충분히 길면
# 토큰을 섞지 않고 숫자열만으로 서명한다.
_DOMINANT_NUMBER_LEN = 2

# 토큰 시그니처를 믿을 최소 토큰 수. 숫자열이 짧고 토큰도 이보다 적으면
# 구분이 안 된다고 보고 서명을 비운다.
_MIN_TOKEN_SIGNATURE = 3

# 토큰 시그니처에 쓸 접두 길이. 토큰을 앞 N글자로 잘라 "삽입했/삽입한/삽입"을
# 같은 어간으로 묶는다.
_TOKEN_STEM_LEN = 2

# 의미 중복(semantic duplicate) 임계.
# 숫자열 서명은 숫자 없는 의미 중복(예: @SpringBootApplication 자동 설정을 발문만
# 바꿔 4번 출제)을 못 잡는다. stem(+correct_answer) 내용 토큰 집합의 Jaccard 유사도로
# "발문은 다르지만 같은 지식을 묻는" 문항을 묶는다. 토큰 중복 비율만 보므로 과목과 무관하다.

# 두 문항 토큰 집합의 Jaccard 유사도가 이 값 이상이면 의미적 중복으로 본다.
# 0.55로 높게 잡아 삽입·삭제처럼 토큰이 충분히 다른 문항은 묶지 않는다.
# 더 낮추면 정상 세트의 절반 이상을 플래그한다.
_SEMANTIC_JACCARD_THRESHOLD = 0.55

# 의미 중복 클러스터당 남길 문항 수. 같은 지식을 물어도 2개까지는 남기고 그 뒤만
# 교체 대상으로 플래그한다. 시나리오 중복은 1개만 남기지만, 의미 중복은 신호가
# 더 느슨해서 2로 둔다.
_SEMANTIC_CLUSTER_KEEP = 2

# 의미 중복 비교에 필요한 최소 내용 토큰 수. 토큰이 적으면 조금만 겹쳐도
# Jaccard가 커져서, 이보다 적은 문항은 비교에서 뺀다.
_SEMANTIC_MIN_TOKENS = 3

# 영문 식별자(@SpringBootApplication, Starter 등) 정규식. 이런 토큰은 자르지 않고
# 원형 그대로 강한 중복 신호로 쓴다. 2글자 이상 영문 토큰을 식별자로 본다.
_IDENTIFIER_RE = re.compile(r"^[a-z][a-z0-9_]+$")

# 식별자 토큰 가중치. 가중 Jaccard에서 영문 식별자가 겹치면 일반 토큰의 이 배수로 친다.
# 같은 식별자를 공유하는 문항은 같은 지식을 물을 가능성이 크다.
_IDENTIFIER_WEIGHT = 3.0


# 같은 개념이 전체에서 이 비율을 초과하면 과대표현으로 본다.
_OVERREP_RATIO = 0.20
# 비율과 무관하게 같은 개념이 이 절대 개수를 초과하면 과대표현으로 본다.
_OVERREP_ABS_COUNT = 2
# 과대표현 게이트를 켜는 최소 문항 수. 3문항처럼 적으면 개념 종류가 원래 적어
# 잘못 잡으므로 건너뛴다.
_OVERREP_MIN_QUESTIONS = 5


def _normalize_word(token: str) -> str:
    """한국어 조사·어미 차이를 줄여 같은 개념 표현을 맞춘다."""
    for suffix in _JOSA_SUFFIXES:
        if token.endswith(suffix) and len(token) > len(suffix) + 1:
            return token[: -len(suffix)]
    return token


def _content_tokens(text: str) -> list[str]:
    """상투어·1글자·순수 숫자를 제외한 콘텐츠 토큰 목록(중복 포함)을 만든다."""
    tokens: list[str] = []
    for raw in _WORD_RE.findall(text.lower()):
        normalized = _normalize_word(raw)
        if len(normalized) < 2 or normalized.isdigit():
            continue
        if normalized in _BOILERPLATE_STOPWORDS:
            continue
        tokens.append(normalized)
    return tokens


def _number_sequence(text: str) -> list[str]:
    """stem 안의 등장 숫자 시퀀스를 등장 순서대로 추출한다.

    예: "12, 8, 18, 10 순서로 삽입" → ['12', '8', '18', '10']
    같은 수치 예시(삽입 수열·좌표·계수 등)를 재활용하면 이 시퀀스가 같다.
    작은 차이도 구분하려고 소수점·음수를 그대로 둔다.
    """
    return _NUMBER_RE.findall(text or "")


def _token_stems(tokens: list[str]) -> list[str]:
    """토큰을 앞 N글자로 잘라 어미·조사 변형을 같은 어간으로 묶는다.

    "삽입했/삽입한/삽입" → "삽입", "결과로/결과는" → "결과"처럼 활용형 차이를 없앤다.
    정렬하고 중복을 뺀 뒤 상위 N개만 남겨 어순·표현 차이의 영향을 줄인다.
    """
    stems = {tok[:_TOKEN_STEM_LEN] for tok in tokens if tok}
    return sorted(stems)[:_MAX_SIGNATURE_TOKENS]


def scenario_signature(question: dict) -> str:
    """문항 stem에서 시나리오 서명을 뽑는다(과목 무관).

    서명 규칙(구조 신호만 쓰고 과목 단어는 하드코딩하지 않는다):
      1. 숫자 시퀀스가 _DOMINANT_NUMBER_LEN 이상이면 숫자열만으로 서명한다.
         같은 데이터 예시(삽입 수열·트리 값·좌표 등)는 어순·발문이 달라도 숫자열이
         같아서, 활용형에 약한 토큰을 섞지 않는다.
         순서를 보존해 [12,8,18]과 [8,12,18]을 다른 시나리오로 본다.
      2. 숫자 신호가 약하면(짧은 숫자열) 토큰 어간(_token_stems)으로 서명한다.
         단 토큰이 _MIN_TOKEN_SIGNATURE 미만이면 식별력 부족으로 서명을 비운다.
    숫자·토큰이 모두 없으면 빈 문자열을 돌려 게이트에서 뺀다(구조 검증이 다룬다).
    """
    stem = str(question.get("stem", ""))
    code = question.get("code_snippet")
    # 코드 스니펫이 있으면 코드 안의 수치/식별자도 시나리오 구성 요소로 포함한다.
    full_text = f"{stem}\n{code}" if code else stem

    numbers = _number_sequence(full_text)
    # 1) 숫자열이 충분히 길면 그것이 시나리오 식별자다.
    if len(numbers) >= _DOMINANT_NUMBER_LEN:
        return f"N[{','.join(numbers)}]"

    # 2) 숫자 신호가 약하면 토큰 어간으로 서명한다.
    token_stems = _token_stems(_content_tokens(full_text))
    if len(token_stems) < _MIN_TOKEN_SIGNATURE:
        # 토큰이 너무 적으면(짧은 발문) 구분이 안 돼 서명을 비우고 게이트에서 뺀다.
        # 짧은 숫자열이라도 토큰과 함께 있으면 토큰 서명 뒤에 붙여 구분한다.
        return ""
    num_suffix = f"+n{len(numbers)}" if numbers else ""
    return f"T[{'|'.join(token_stems)}]{num_suffix}"


def _draft_id(question: dict, index: int) -> str:
    """검증·초안 어느 단계에서도 같은 문항 식별자를 돌려준다."""
    return str(question.get("draft_id") or question.get("question_id") or index)


def find_duplicate_scenarios(questions: list[dict]) -> dict[str, list[str]]:
    """같은 시나리오 서명을 가진 문항의 2번째 이후를 교체 대상으로 표시한다.

    같은 서명이 2개 이상이면 중복으로 판정한다. 첫 번째 문항은 보존하고
    뒤쪽(중복) 문항만 플래그해 repair 경로로 보낸다(deduplicator와 같은 정책).

    Returns:
        {draft_id: [issue]}. 빈 매핑이면 시나리오 중복이 없다.
    """
    if len(questions) < 2:
        return {}

    seen_first: dict[str, str] = {}  # signature → 첫 번째 draft_id
    flagged: dict[str, list[str]] = {}
    for index, q in enumerate(questions):
        sig = scenario_signature(q)
        if not sig:
            continue
        draft_id = _draft_id(q, index)
        if sig in seen_first:
            flagged[draft_id] = [
                "시나리오 중복 — 동일한 수치 예시/핵심 구조를 재활용한 문항"
                f"(최초 문항 draft_id={seen_first[sig]}). 다른 시나리오·데이터로 재출제 필요"
            ]
        else:
            seen_first[sig] = draft_id

    if flagged:
        logger.warning(
            "시나리오 중복 의심 문항 %d건 — 재시도 경로로 전달: %s",
            len(flagged), ", ".join(flagged.keys()),
        )
    return flagged




def _semantic_token_set(question: dict) -> frozenset[str]:
    """stem(+correct_answer)에서 의미 비교용 내용 토큰 집합을 만든다.

    숫자열 서명이 놓치는 "발문은 다르지만 같은 지식을 묻는" 중복을 잡으려고,
    상투어·불용어·순수 숫자를 뺀 내용 토큰의 집합(중복 무시)을 돌려준다.

    토큰 정규화(과목 무관):
      - 한국어 활용형은 앞 _TOKEN_STEM_LEN 글자로 잘라 "삽입했/삽입한/삽입"을
        같은 어간으로 묶는다(_token_stems와 같다).
      - 영문 식별자(@SpringBootApplication, Starter 등)는 자르지 않고 원형으로 둔다.
        식별자가 겹치면 같은 지식을 물을 가능성이 크다.
    correct_answer는 보통 짧은 명사구·식별자라 같은 지식을 묻는 문항을 묶기 좋아서
    stem과 함께 토큰화한다.
    """
    stem = str(question.get("stem", ""))
    answer = str(question.get("correct_answer", ""))
    code = question.get("code_snippet")
    parts = [stem, answer]
    if code:
        parts.append(str(code))
    text = "\n".join(parts)

    tokens: set[str] = set()
    for raw in _WORD_RE.findall(text.lower()):
        normalized = _normalize_word(raw)
        if len(normalized) < 2 or normalized.isdigit():
            continue
        if normalized in _BOILERPLATE_STOPWORDS:
            continue
        if _IDENTIFIER_RE.match(normalized):
            # 영문 식별자는 자르면 구분 신호가 사라져 원형으로 둔다.
            tokens.add(normalized)
        else:
            # 한국어·일반 토큰은 어간으로 잘라 활용형 차이를 없앤다.
            tokens.add(normalized[:_TOKEN_STEM_LEN])
    return frozenset(tokens)


def _token_weight(tokens: frozenset[str]) -> float:
    """토큰 집합의 가중 합. 영문 식별자는 _IDENTIFIER_WEIGHT배로 친다.

    같은 식별자(@SpringBootApplication, Starter 등)를 공유하면 같은 지식을 물을
    가능성이 커서, 일반 어간 토큰보다 Jaccard에 더 크게 반영한다.
    """
    return sum(
        _IDENTIFIER_WEIGHT if _IDENTIFIER_RE.match(tok) else 1.0
        for tok in tokens
    )


def _jaccard(a: frozenset[str], b: frozenset[str]) -> float:
    """두 토큰 집합의 가중 Jaccard 유사도(가중 교집합 / 가중 합집합).

    영문 식별자는 _token_weight에서 가중돼, 식별자가 겹치면 일반 토큰만 겹칠 때보다
    유사도가 높다. 합집합이 비면 0이다.
    """
    if not a or not b:
        return 0.0
    union = a | b
    union_weight = _token_weight(union)
    if union_weight == 0.0:
        return 0.0
    return _token_weight(a & b) / union_weight


def find_semantic_duplicates(
    questions: list[dict],
    *,
    threshold: float = _SEMANTIC_JACCARD_THRESHOLD,
    keep: int = _SEMANTIC_CLUSTER_KEEP,
) -> dict[str, list[str]]:
    """토큰 Jaccard 유사도로 의미 중복 문항을 묶고 한도를 넘는 문항을 플래그한다.

    숫자열 시나리오 서명(find_duplicate_scenarios)은 숫자 없는 의미 중복
    (@SpringBootApplication 자동 설정을 발문만 바꿔 여러 번 출제 등)을 놓쳐서 따로 잡는다.

    알고리즘(한 번 훑는 single-link 그리디 클러스터링, 무한 루프 없음):
      0. 긴 숫자열(≥_DOMINANT_NUMBER_LEN)이 있는 문항은 뺀다. 숫자열 게이트가 다루며,
         서로 다른 수치 예시를 의미 중복으로 잘못 묶지 않게 한다.
      1. 나머지 문항의 내용 토큰 집합(_semantic_token_set)을 만든다.
         토큰이 _SEMANTIC_MIN_TOKENS보다 적으면 뺀다.
      2. 순서대로 보며 기존 클러스터의 어느 멤버와든 가중 Jaccard가 threshold 이상이면
         그 클러스터에 넣는다(single-link). 발문이 조금씩 바뀌어 대표와 멀어진 문항도
         중간 멤버를 거쳐 묶인다. 들어갈 곳이 없으면 새 클러스터를 만든다(O(n²)).
      3. 클러스터마다 앞쪽 keep개는 남기고 그 뒤 문항을 교체 대상으로 플래그한다.

    오탐 줄이기:
      - threshold를 0.55로 높게 둬 삽입·삭제처럼 토큰이 충분히 다른 문항은 묶지 않는다.
      - 토큰이 적은 문항은 빼서 조금만 겹쳐도 Jaccard가 커지는 것을 막는다.

    Returns:
        {draft_id: [issue]}. 빈 매핑이면 의미 중복이 없다.
    """
    if len(questions) < keep + 1:
        # keep개 이하면 초과분이 없어 비교하지 않는다.
        return {}

    # 클러스터: 멤버 [(draft_id, 토큰 집합) ...] 목록. 어느 멤버와든 threshold 이상이면 합류한다.
    clusters: list[list[tuple[str, frozenset[str]]]] = []
    for index, q in enumerate(questions):
        # 긴 숫자열로 구분되는 문항은 find_duplicate_scenarios가 다룬다. 서로 다른 수치
        # 예시 문항을 의미 중복으로 잘못 묶지 않게 여기서 뺀다.
        stem = str(q.get("stem", ""))
        code = q.get("code_snippet")
        num_text = f"{stem}\n{code}" if code else stem
        if len(_number_sequence(num_text)) >= _DOMINANT_NUMBER_LEN:
            continue

        token_set = _semantic_token_set(q)
        if len(token_set) < _SEMANTIC_MIN_TOKENS:
            # 토큰이 너무 적으면(짧은 발문) 구분이 안 돼 클러스터링에서 뺀다.
            continue
        draft_id = _draft_id(q, index)

        joined = False
        for members in clusters:
            if any(_jaccard(token_set, other) >= threshold for _id, other in members):
                members.append((draft_id, token_set))
                joined = True
                break
        if not joined:
            clusters.append([(draft_id, token_set)])

    flagged: dict[str, list[str]] = {}
    for members in clusters:
        if len(members) <= keep:
            continue
        first_id = members[0][0]
        # 앞쪽 keep개는 남기고 뒤쪽 문항만 교체 대상이다.
        for dup_id, _ts in members[keep:]:
            flagged[dup_id] = [
                "의미 중복 — 발문은 다르나 같은 지식을 묻는 문항"
                f"(유사 클러스터 대표 draft_id={first_id}, 토큰 중복 ≥{threshold:.0%}). "
                "다른 핵심 지식으로 재출제 필요"
            ]

    if flagged:
        logger.warning(
            "의미 중복 의심 문항 %d건 — 재시도 경로로 전달: %s",
            len(flagged), ", ".join(flagged.keys()),
        )
    return flagged


def _concept_label(question: dict) -> str:
    """과대표현 집계에 쓸 정규화 개념 라벨을 만든다(과목 무관).

    우선순위:
      1. _concept_key / concept_key: 블루프린트가 붙인 슬롯 개념(가장 믿을 만하다)
      2. 없으면 stem 상위 토큰을 정규화한 키를 쓴다.
    concept_key 는 (chapter::topic::concept::diff::reasoning) 형태라 diff/reasoning
    suffix 가 달라도 같은 개념이면 묶이도록 앞 3토막(개념 식별부)만 사용한다.
    """
    raw_key = str(question.get("_concept_key") or question.get("concept_key") or "")
    if raw_key:
        parts = raw_key.split("::")
        # chapter::topic::concept까지만 쓴다. d{diff}::reasoning::v{n}은 같은 개념의 변형이라 묶는다.
        return "::".join(parts[:3]) if len(parts) >= 3 else raw_key

    # 개념 메타가 없으면 stem 토큰 어간 키를 쓴다.
    # scenario_signature와 같은 어간 정규화로 활용형 차이를 없앤다.
    return "|".join(_token_stems(_content_tokens(str(question.get("stem", "")))))


def find_overrepresented_concepts(
    questions: list[dict],
    *,
    ratio: float = _OVERREP_RATIO,
    abs_count: int = _OVERREP_ABS_COUNT,
    min_questions: int = _OVERREP_MIN_QUESTIONS,
) -> dict[str, list[str]]:
    """같은 개념이 임계를 초과해 출제됐는지 검사해 초과분을 교체 대상으로 표시한다.

    임계: (해당 개념 수 / 전체) > ratio  또는  해당 개념 수 > abs_count.
    초과분은 등장 순서상 뒤쪽 문항부터 플래그한다(앞쪽 = 첫 출제 보존).

    전체 문항이 min_questions 미만이면 개념 종류 자체가 적어 오탐하므로 생략한다.

    Returns:
        {draft_id: [issue]}. 빈 매핑이면 과대표현이 없다.
    """
    total = len(questions)
    if total < min_questions:
        return {}

    # 개념별로 문항 인덱스를 등장 순서대로 모은다.
    by_concept: dict[str, list[int]] = {}
    for index, q in enumerate(questions):
        label = _concept_label(q)
        if not label:
            continue
        by_concept.setdefault(label, []).append(index)

    # 한도 = max(abs_count, floor(total * ratio)). 한도를 넘는 만큼 뒤쪽을 플래그한다.
    threshold_count = max(abs_count, int(total * ratio))
    flagged: dict[str, list[str]] = {}
    for label, indices in by_concept.items():
        if len(indices) <= threshold_count:
            continue
        share = len(indices) / total
        # 앞쪽 threshold_count개는 남기고 뒤쪽 문항만 교체 대상이다.
        for index in indices[threshold_count:]:
            q = questions[index]
            draft_id = _draft_id(q, index)
            flagged[draft_id] = [
                f"개념 과대표현 — 동일 개념이 {len(indices)}문항({share:.0%}, 전체 {total}문항)"
                f"으로 임계(>{threshold_count}개)를 초과. 다른 핵심 개념으로 분산 출제 필요"
            ]

    if flagged:
        logger.warning(
            "개념 과대표현 의심 문항 %d건 — 재시도 경로로 전달: %s",
            len(flagged), ", ".join(flagged.keys()),
        )
    return flagged


def check_scenario_quality(questions: list[dict]) -> dict[str, list[str]]:
    """시나리오 중복·의미 중복·개념 과대표현을 한 번에 검사해 교체 대상을 돌려준다.

    세 결과를 draft_id 기준으로 합친다(한 문항이 여러 번 걸리면 issue를 합친다).
      1. find_duplicate_scenarios: 숫자열 시나리오 서명(수치 예시 재활용) 중복
      2. find_semantic_duplicates: 토큰 Jaccard 의미 중복(숫자 없는 발문 변형)
      3. find_overrepresented_concepts: 같은 개념 과대표현(챕터 편중·반복 출제)
    failed_ids에 합칠 때 중복은 빠지므로 세 결과가 겹쳐도 된다.

    Returns:
        {draft_id: [issue...]}. 빈 매핑이면 통과다.
    """
    merged: dict[str, list[str]] = {}
    for partial in (
        find_duplicate_scenarios(questions),
        find_semantic_duplicates(questions),
        find_overrepresented_concepts(questions),
    ):
        for draft_id, issues in partial.items():
            merged.setdefault(draft_id, []).extend(issues)
    return merged
