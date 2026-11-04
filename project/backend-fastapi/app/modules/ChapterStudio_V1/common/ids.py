"""공용 ID 생성. Spring IdGenerator와 같은 ULID(26자 Crockford Base32 대문자) 형식을 쓴다.

public 스키마 FK 제약(예: chapter.id ~ '^chp_[0-9A-Z]{26}$')을 그대로 통과한다.
Spring과 FastAPI가 같은 강의를 가리키도록 chapter.id 값을 양쪽 스키마가 공유하므로
형식이 같아야 한다.
"""
from __future__ import annotations

import os
import time

# ULID 표준 32진 문자표다. 읽기 쉽게 I, L, O, U를 빼며 모두 [0-9A-Z]에 속한다.
_CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"


def _encode(value: int, length: int) -> str:
    """정수를 Crockford Base32 대문자 문자열(고정 길이)로 변환한다."""
    chars: list[str] = []
    for _ in range(length):
        chars.append(_CROCKFORD[value & 0x1F])
        value >>= 5
    return "".join(reversed(chars))


def generate_ulid() -> str:
    """48비트 밀리초 타임스탬프 + 80비트 랜덤 = 26자 ULID를 만든다."""
    ts = int(time.time() * 1000) & ((1 << 48) - 1)
    rand = int.from_bytes(os.urandom(10), "big")
    return _encode(ts, 10) + _encode(rand, 16)


def new_id(prefix: str) -> str:
    """'chp'/'cur'/'cun' 등 접두사 + '_' + 26자 ULID를 반환한다.

    예) new_id("chp") -> "chp_01J9Z..." (정규식 ^chp_[0-9A-Z]{26}$ 충족)
    """
    return f"{prefix}_{generate_ulid()}"
