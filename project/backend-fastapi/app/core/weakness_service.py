"""약점 프로필 갱신 서비스.

(user_id, topic, subtopic)이 같은 행이 있으면 frequency를 올리고, 없으면 새로 넣는다.
소크라테스 답변과 과제 채점 두 경로에서 호출하며, DB 연결은 asyncpg 호환
WeaknessConnection으로 받는다.
"""

from __future__ import annotations

import uuid
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Protocol


class WeaknessConnection(Protocol):
    """asyncpg AsyncConnection과 호환되는 DB 연결 프로토콜이다."""

    async def fetchrow(
        self, query: str, *args: object
    ) -> Mapping[str, object] | None: ...

    async def fetch(
        self, query: str, *args: object
    ) -> list[Mapping[str, object]]: ...

    async def execute(self, query: str, *args: object) -> str: ...


@dataclass(frozen=True)
class WeaknessEntry:
    """약점 갱신 입력 1건."""

    user_id: str
    topic: str
    subtopic: str
    error_pattern: str
    # 심각도 1~5 (DB CHECK 제약)
    severity: int


def _new_profile_id() -> str:
    """weakness_profile id(wkp_ + uuid4 hex)를 만든다."""
    return f"wkp_{uuid.uuid4().hex}"


# PostgreSQL ON CONFLICT UPSERT. NULL subtopic은 UNIQUE 비교에서 빠지므로
# 충돌 대상을 WHERE subtopic IS NOT NULL로 한정한다.
_UPSERT_SQL = """
INSERT INTO chapter_studio.weakness_profile AS wp
    (id, user_id, topic, subtopic, error_pattern, frequency, severity, first_seen_at, last_seen_at)
VALUES
    ($1, $2, $3, $4, $5, 1, $6, NOW(), NOW())
ON CONFLICT (user_id, topic, subtopic)
    WHERE subtopic IS NOT NULL
DO UPDATE SET
    frequency   = wp.frequency + 1,
    last_seen_at = NOW(),
    severity    = GREATEST(wp.severity, EXCLUDED.severity)
RETURNING id
"""


async def upsert_weakness(conn: WeaknessConnection, entry: WeaknessEntry) -> str:
    """약점 1건을 UPSERT하고 weakness_profile id를 반환한다.

    기존 행이 있으면 frequency +1, last_seen_at 갱신, severity는 큰 값으로 바꾼다.
    """
    new_id = _new_profile_id()
    row = await conn.fetchrow(
        _UPSERT_SQL,
        new_id,
        entry.user_id,
        entry.topic,
        entry.subtopic,
        entry.error_pattern,
        entry.severity,
    )
    # RETURNING id는 항상 오지만 None이면 예외로 처리한다
    if row is None:
        raise RuntimeError(f"weakness_profile UPSERT 실패: entry={entry}")
    returned_id = row.get("id") if hasattr(row, "get") else row["id"]
    return str(returned_id)


async def upsert_weaknesses(
    conn: WeaknessConnection,
    entries: list[WeaknessEntry],
) -> list[str]:
    """여러 건을 순서대로 UPSERT하고 id 목록을 반환한다.

    트랜잭션은 호출자가 관리한다.
    """
    ids: list[str] = []
    for entry in entries:
        profile_id = await upsert_weakness(conn, entry)
        ids.append(profile_id)
    return ids


_SELECT_UNRESOLVED_SQL = """
SELECT
    id, user_id, topic, subtopic, error_pattern,
    frequency, severity, first_seen_at, last_seen_at
FROM chapter_studio.weakness_profile
WHERE user_id = $1
  AND resolved_at IS NULL
ORDER BY frequency DESC, last_seen_at DESC
"""


async def get_user_weaknesses(
    conn: WeaknessConnection,
    user_id: str,
) -> list[dict[str, object]]:
    """사용자의 미해결(resolved_at IS NULL) 약점을 frequency, last_seen_at 내림차순으로 반환한다."""
    rows = await conn.fetch(_SELECT_UNRESOLVED_SQL, user_id)
    return [dict(row) for row in rows]


_RESOLVE_SQL = """
UPDATE chapter_studio.weakness_profile
SET resolved_at = NOW()
WHERE id = $1
"""


async def resolve_weakness(
    conn: WeaknessConnection,
    weakness_id: str,
) -> None:
    """resolved_at을 현재 시각으로 바꿔 극복 처리한다.

    이미 극복된 행이면 resolved_at만 다시 찍힌다.
    """
    await conn.execute(_RESOLVE_SQL, weakness_id)
