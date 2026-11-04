"""모델 캡션 출력 검증. 규칙을 어기면 None을 돌려줘 다음 폴백으로 넘긴다."""
import re

from .config import MAX_CHARS, MAX_EMOJIS, MIN_CHARS

# 주요 이모지 유니코드 블록.
_EMOJI_RE = re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U0001F000-\U0001F2FF]"
)


def sanitize(raw: str | None) -> str | None:
    """모델 출력의 첫 줄을 검사해 통과하면 그 줄을, 아니면 None을 돌려준다.

    실패 조건:
    - 빈 문자열이나 None
    - 20자 미만 (캡션으로 쓰기에 너무 짧음)
    - 100자 초과 (캡션 길이 상한)
    - 이모지 2개 이상 (최대 1개)
    """
    if not raw:
        return None
    stripped = raw.strip()
    if not stripped:
        return None
    text = stripped.splitlines()[0].strip()
    if not text:
        return None
    if len(text) < MIN_CHARS or len(text) > MAX_CHARS:
        return None
    if len(_EMOJI_RE.findall(text)) > MAX_EMOJIS:
        return None
    return text
