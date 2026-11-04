"""LaTeX 수식 구분자를 $...$ / $$...$$ 형식으로 통일한다.

Marker와 MinerU는 수식 구분자를 다르게 내보내기도 한다
(\\[...\\], \\(...\\), \\begin{equation} 등). 달러 형식으로 맞춰야
렌더러와 리랭커가 같은 형태로 읽는다.
"""
from __future__ import annotations

import re

# LaTeX 환경 블록 → $$...$$
_LATEX_ENV_RE = re.compile(
    r"\\begin\{(equation|align|gather|multline)\*?\}(.*?)\\end\{\1\*?\}",
    re.DOTALL,
)
# \[...\] 디스플레이 수식
_DISPLAY_BRACKET_RE = re.compile(r"\\\[(.*?)\\\]", re.DOTALL)
# \(...\) 인라인 수식
_INLINE_BRACKET_RE = re.compile(r"\\\((.*?)\\\)")
# 빈 수식 구분자 제거
_EMPTY_DISPLAY_RE = re.compile(r"\$\$\s*\$\$")
_EMPTY_INLINE_RE = re.compile(r"\$\s*\$")
# 점 4개 이상 연속은 OCR 잡음으로 본다
_OCR_ARTIFACT_RE = re.compile(r"(?<!\.)\.{4,}(?!\.)")


def extract_latex_blocks(markdown: str) -> str:
    """마크다운 내 LaTeX 구분자를 표준 달러 형식으로 변환한다.

    변환 순서: 환경 블록 → 대괄호 디스플레이 → 소괄호 인라인 → 빈 수식 제거
    """
    # LaTeX 환경 블록 → 디스플레이 수식
    result = _LATEX_ENV_RE.sub(lambda m: f"$$\n{m.group(2).strip()}\n$$", markdown)

    # \[...\] → $$...$$
    result = _DISPLAY_BRACKET_RE.sub(lambda m: f"$$\n{m.group(1).strip()}\n$$", result)

    # \(...\) → $...$
    result = _INLINE_BRACKET_RE.sub(lambda m: f"${m.group(1).strip()}$", result)

    # 빈 수식 구분자 제거 (OCR 잔재물)
    result = _EMPTY_DISPLAY_RE.sub("", result)
    result = _EMPTY_INLINE_RE.sub("", result)

    # 점 4개 이상 연속(OCR 잡음) → 생략 부호로 교체
    result = _OCR_ARTIFACT_RE.sub("...", result)

    return result
