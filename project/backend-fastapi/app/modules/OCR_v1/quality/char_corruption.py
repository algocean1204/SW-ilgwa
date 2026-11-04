"""한국어 텍스트에서 깨진 문자 비율(문자 오염률)을 잰다.

스캔 PDF를 잘못된 코드페이지로 OCR하면 한글이 CJK 한자나 특수문자로 바뀐다.
이 비율을 품질 게이트가 쓴다.
"""
from __future__ import annotations

import re
import unicodedata

from .. import config as cfg

# 정상 한글 유니코드 범위
_HANGUL_SYLLABLES = re.compile(r"[가-힯]")
_HANGUL_JAMO = re.compile(r"[ᄀ-ᇿ]")
_HANGUL_COMPAT = re.compile(r"[㄰-㆏]")

# 한글이 깨질 때 자주 나오는 CJK 통합 한자 범위.
# 실제 한자가 든 문서와 구분하지 못해 '의심 비율'로만 쓴다.
_CJK_UNIFIED = re.compile(r"[一-鿿]")


def _count_hangul(text: str) -> int:
    """정상 한글 문자 수를 반환한다."""
    return (
        len(_HANGUL_SYLLABLES.findall(text))
        + len(_HANGUL_JAMO.findall(text))
        + len(_HANGUL_COMPAT.findall(text))
    )


def _count_suspicious_cjk(text: str) -> int:
    """CJK 통합 한자 수를 반환한다 (오염 의심 지표)."""
    return len(_CJK_UNIFIED.findall(text))


def score_char_corruption(text: str) -> tuple[float, str]:
    """텍스트의 문자 오염률과 이유를 반환한다.

    반환: (corruption_ratio 0.0~1.0, reason 문자열)
    corruption_ratio 가 낮을수록 오염이 없음을 의미한다.
    """
    if not text.strip():
        # 빈 텍스트는 판단할 수 없어 오염 0.0으로 둔다
        return 0.0, "empty"

    # ASCII 밖 문자만 센다
    non_ascii = [c for c in text if ord(c) > 127]
    if not non_ascii:
        return 0.0, "ascii_only"

    hangul_count = _count_hangul(text)
    cjk_count = _count_suspicious_cjk(text)
    total_non_ascii = len(non_ascii)

    # 한글 없이 CJK만 많으면 오염 신호가 강하다
    if hangul_count == 0 and cjk_count > 10:
        ratio = min(cjk_count / total_non_ascii, 1.0)
        return round(ratio, 4), "no_hangul_cjk_dominant"

    # 그 외에는 한글+CJK 중 CJK 비율을 오염률로 본다
    if hangul_count + cjk_count == 0:
        return 0.0, "no_cjk_no_hangul"

    corruption = cjk_count / (hangul_count + cjk_count)
    reason = "ok" if corruption < cfg.char_corruption_max() else "high_cjk_ratio"
    return round(corruption, 4), reason
