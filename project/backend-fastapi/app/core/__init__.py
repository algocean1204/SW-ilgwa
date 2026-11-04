"""모듈 공용 코드: 약점 DB 모델·서비스, 상수, planfirst."""

from app.core.weakness_models import WeaknessProfile, WeaknessSnapshot, metadata as weakness_metadata
from app.core.weakness_service import (
    WeaknessConnection,
    WeaknessEntry,
    get_user_weaknesses,
    resolve_weakness,
    upsert_weakness,
    upsert_weaknesses,
)

__all__ = [
    # DB 모델
    "WeaknessProfile",
    "WeaknessSnapshot",
    "weakness_metadata",
    # 서비스 인터페이스
    "WeaknessConnection",
    "WeaknessEntry",
    "upsert_weakness",
    "upsert_weaknesses",
    "get_user_weaknesses",
    "resolve_weakness",
]
