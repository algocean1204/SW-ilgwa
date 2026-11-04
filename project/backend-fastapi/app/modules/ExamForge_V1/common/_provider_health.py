"""쿼터가 소진된 프로바이더를 메모리에 기록하는 헬스 캐시.

모든 프로바이더가 소진된 상태로 모의고사 생성(LLM 호출 수십 회)을 시작하면
호출이 전부 400/429로 실패해 시간과 비용만 쓴다. 생성 중 실제로 만난 영구 소진
오류를 기록해 두고, 다음 생성 전 사전점검이 바로 멈출 수 있게 한다.

원칙
- 상태 확인용 호출(프로빙)은 하지 않는다. 그것도 비용이다. 커넥터가 생성 중
  영구 오류(Gemini 결제 429, Anthropic 400 usage-limit, OpenAI 결제 오류 등)를 만났을 때만 기록한다.
- 프로세스 메모리에만 두며 재시작하면 비워진다.
- until이 지나면 다시 시도를 허용한다. 벤더가 준 복구일("regain access on <날짜>")이
  있으면 그때까지, 없으면 기본 TTL 10분까지만 막는다.
- 아직 until 전인 기록만 소진으로 본다. 하나라도 살아 있으면 사전점검은 막지 않는다.

dict set/get만 하고 await를 사이에 둔 read-modify-write가 없어 락 없이 쓴다.
"""
from __future__ import annotations

import datetime as _dt
import logging
import re
import time
from dataclasses import dataclass

_LOG = logging.getLogger(__name__)

# until이 없을 때의 기본 TTL(초). 잘못 막는 시간을 줄이려고 10분으로 짧게 둔다.
_DEFAULT_TTL_SEC = 600.0

# Anthropic 400 "usage limit ... regain access on YYYY-MM-DD"에서 복구일을 읽는다.
# 날짜만 오므로 그날 00:00(UTC)을 복구 시각으로 본다.
_REGAIN_DATE_RE = re.compile(
    r"regain\s+access\s+on\s+(\d{4})-(\d{2})-(\d{2})",
    re.IGNORECASE,
)


@dataclass(frozen=True)
class _Entry:
    """소진 기록 1건. 단조시계 기준 만료 시각과 사유를 담는다."""

    # time.monotonic() 기준 만료 시각(초). 이 시각이 지나면 자동 해제된다.
    expires_monotonic: float
    reason: str


# 프로바이더 이름 → 소진 기록. 메모리 전용(영속 불필요).
_HEALTH: dict[str, _Entry] = {}


def _now() -> float:
    """단조시계 값(초). NTP 조정 같은 벽시계 변화에 영향받지 않는다."""
    return time.monotonic()


def parse_regain_until(message: str) -> float | None:
    """오류 메시지의 복구 시각을 단조시계 기준 만료 초로 바꾼다.

    Anthropic "regain access on YYYY-MM-DD"를 읽는다. 미래 날짜면 남은 시간을 단조시계에
    더해 돌려주고, 패턴이 없거나 지난 날짜면 None을 돌려 호출부가 기본 TTL을 쓰게 한다.
    """
    match = _REGAIN_DATE_RE.search(message or "")
    if match is None:
        return None
    year, month, day = (int(g) for g in match.groups())
    try:
        target = _dt.datetime(year, month, day, tzinfo=_dt.timezone.utc)
    except ValueError:
        return None
    now_wall = _dt.datetime.now(tz=_dt.timezone.utc)
    delta_sec = (target - now_wall).total_seconds()
    if delta_sec <= 0:
        # 지난 복구일이면 None을 돌려 기본 TTL을 쓰게 한다.
        return None
    return _now() + delta_sec


def mark_exhausted(
    provider: str,
    *,
    reason: str = "",
    until_monotonic: float | None = None,
    ttl_sec: float | None = None,
) -> None:
    """프로바이더를 소진 상태로 기록한다. 실제 오류를 만났을 때만 부른다.

    우선순위:
    1. until_monotonic 이 주어지면 그 시각까지 차단(벤더가 알려준 복구일 등).
    2. 아니면 ttl_sec(미지정 시 기본 _DEFAULT_TTL_SEC) 만큼 차단.

    이미 기록이 있으면 만료가 더 늦은 쪽을 남긴다.
    """
    if not provider:
        return
    if until_monotonic is None:
        ttl = _DEFAULT_TTL_SEC if ttl_sec is None else max(0.0, ttl_sec)
        expires = _now() + ttl
    else:
        expires = until_monotonic

    existing = _HEALTH.get(provider)
    if existing is not None and existing.expires_monotonic >= expires:
        # 더 늦은 만료가 이미 있으면 줄이지 않는다.
        return
    _HEALTH[provider] = _Entry(expires_monotonic=expires, reason=reason or "쿼터/사용한도 소진")
    _LOG.warning(
        "[ProviderHealth] '%s' 소진 기록 — %.0f초 차단 (사유: %s)",
        provider,
        max(0.0, expires - _now()),
        reason or "쿼터/사용한도 소진",
    )


def is_exhausted(provider: str) -> bool:
    """프로바이더가 현재 소진 상태인지 반환한다. until 이 지났으면 자동 해제한다."""
    entry = _HEALTH.get(provider)
    if entry is None:
        return False
    if entry.expires_monotonic <= _now():
        # 만료됐으면 기록을 지워 다시 시도하게 한다.
        _HEALTH.pop(provider, None)
        return False
    return True


def all_exhausted(providers: list[str] | tuple[str, ...]) -> bool:
    """주어진 프로바이더 체인이 '비어있지 않고 전부' 소진 상태면 True.

    정상 생성을 잘못 막지 않게 다음 규칙을 따른다.
    - 체인이 비어 있으면(활성 체인을 모름) False.
    - 하나라도 소진 기록이 없거나 until 이 지나 살아있으면 → False(정상 진행).
    - 모든 후보가 현재 소진 상태일 때만 → True(차단).
    """
    names = [p for p in providers if p]
    if not names:
        return False
    return all(is_exhausted(p) for p in names)


def reset() -> None:
    """헬스 캐시를 비운다. 테스트 격리용이다."""
    _HEALTH.clear()


def snapshot() -> dict[str, str]:
    """만료 전 소진 기록의 사유 맵. 진단·로그용이다."""
    out: dict[str, str] = {}
    for provider in list(_HEALTH.keys()):
        if is_exhausted(provider):
            out[provider] = _HEALTH[provider].reason
    return out


__all__ = [
    "mark_exhausted",
    "is_exhausted",
    "all_exhausted",
    "parse_regain_until",
    "reset",
    "snapshot",
]
