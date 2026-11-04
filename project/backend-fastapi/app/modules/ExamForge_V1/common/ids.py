"""Spring IdGenerator와 같은 ULID(26자 Crockford Base32 대문자) ID를 만든다.

Spring은 mock_exam.id를 'mex_' + 26자 ULID, mock_exam_question.id를 'mq_' + 26자 ULID로 만든다.
public 스키마 CHECK 제약(예: mock_exam.id ~ '^mex_[0-9A-Z]{26}$',
mock_exam_question.id ~ '^mq_[0-9A-Z]{26}$')을 맞춰야 어댑터가 넣은 행을 Spring이 읽는다.
"""
from __future__ import annotations

import os
import time

# ULID 32진 문자. 헷갈리는 I, L, O, U를 뺐고 모두 [0-9A-Z] 안에 있다.
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
    """'mq' 등 접두사 + '_' + 26자 ULID를 반환한다.

    예) new_id("mq") -> "mq_01J9Z..." (정규식 ^mq_[0-9A-Z]{26}$ 충족)
    """
    return f"{prefix}_{generate_ulid()}"
