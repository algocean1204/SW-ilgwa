"""수식 보존 점수. $ 짝, 빈 수식, 중괄호 짝, OCR 오염 패턴을 본다.

교재 PDF는 OCR에서 수식이 자주 깨진다. 이 점수를 품질 게이트가 쓴다.
"""
from __future__ import annotations

import re

# 디스플레이 수식: $$...$$ 또는 \[...\]
_DISPLAY_MATH_RE = re.compile(r"\$\$.+?\$\$|\\\[.+?\\\]", re.DOTALL)
# 인라인 수식: $...$
_INLINE_MATH_RE = re.compile(r"\$[^$\n]+\$")
# 비어있는 수식 패턴
_EMPTY_MATH_RE = re.compile(r"\$\$?\s*\$\$?")
# 수식이 깨질 때 나오는 도형 기호 연속(□■● 등)
_CORRUPTED_MATH_RE = re.compile(r"[□■▪▫●○◆◇]{2,}")


def _check_dollar_balance(text: str) -> float:
    """$ 기호 짝이 맞는지 확인한다.

    홀수 개면 짝이 안 맞아 0.5를 깎는다.
    """
    dollar_count = text.count("$")
    if dollar_count == 0:
        return 1.0
    imbalance = dollar_count % 2
    return 1.0 - imbalance * 0.5


def _check_brace_balance(formula: str) -> bool:
    """중괄호가 균형 잡혀 있는지 확인한다."""
    depth = 0
    for ch in formula:
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
        if depth < 0:
            return False
    return depth == 0


def _score_formula_list(formulas: list[str]) -> float:
    """추출된 수식 목록의 품질을 평가한다."""
    if not formulas:
        return 1.0

    valid = 0
    for f in formulas:
        if not f.strip():
            continue
        if _CORRUPTED_MATH_RE.search(f):
            continue
        if _check_brace_balance(f):
            valid += 1

    return valid / len(formulas)


def score_formula_preservation(markdown: str, formulas: list[str]) -> tuple[float, str]:
    """수식 보존 품질 점수와 이유를 반환한다.

    markdown 에서 인라인·디스플레이 수식을 파싱하고,
    추출된 formulas 목록과 교차 검증한다.
    반환: (score 0.0~1.0, reason 문자열)
    """
    # 수식이 없는 문서는 만점
    has_math_in_md = bool(
        _DISPLAY_MATH_RE.search(markdown) or _INLINE_MATH_RE.search(markdown)
    )
    if not has_math_in_md and not formulas:
        return 1.0, "no_formulas"

    balance_score = _check_dollar_balance(markdown)

    empty_count = len(_EMPTY_MATH_RE.findall(markdown))
    total_math = len(_DISPLAY_MATH_RE.findall(markdown)) + len(_INLINE_MATH_RE.findall(markdown))
    empty_ratio = empty_count / max(total_math, 1)

    list_score = _score_formula_list(formulas)

    score = balance_score * 0.4 + (1.0 - empty_ratio) * 0.3 + list_score * 0.3
    score = min(max(score, 0.0), 1.0)
    reason = "ok" if score >= 0.85 else f"bal={balance_score:.2f},empty={empty_ratio:.2f},list={list_score:.2f}"
    return round(score, 4), reason
