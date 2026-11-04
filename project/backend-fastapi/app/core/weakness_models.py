"""학습 약점 추적 DB 모델.

소크라테스(QAResponder)와 과제 채점(AssignmentGrader) 두 경로에서 갱신된다.
실제 테이블은 chapter_studio 스키마에 있고(V6 마이그레이션), 이 모델은 스키마를 지정하지 않는다.
"""

from __future__ import annotations

import uuid
from datetime import date, datetime

from sqlalchemy import (
    CheckConstraint,
    Date,
    ForeignKey,
    Index,
    Integer,
    MetaData,
    Numeric,
    String,
    UniqueConstraint,
)
from sqlalchemy.dialects.postgresql import TIMESTAMP
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

# 스키마 미지정 MetaData. 서비스 SQL은 chapter_studio 스키마를 직접 쓴다.
metadata = MetaData()


class Base(DeclarativeBase):
    metadata = metadata


def _make_profile_id() -> str:
    """weakness_profile id(wkp_ + uuid4 hex)를 만든다."""
    return f"wkp_{uuid.uuid4().hex}"


def _make_snapshot_id() -> str:
    """weakness_snapshot id(wks_ + uuid4 hex)를 만든다."""
    return f"wks_{uuid.uuid4().hex}"


class WeaknessProfile(Base):
    """사용자별 학습 약점 누적 프로필.

    (user_id, topic, subtopic) UNIQUE 제약으로 UPSERT하고, severity 1~5는 DB CHECK로 막는다.
    user 테이블은 Spring이 관리하며 여기서는 FK로 참조만 한다.
    """

    __tablename__ = "weakness_profile"
    __table_args__ = (
        # ON CONFLICT용 복합 UNIQUE 제약. NULL은 UNIQUE 비교에서 빠지므로
        # subtopic이 NULL인 행은 중복될 수 있다.
        UniqueConstraint("user_id", "topic", "subtopic", name="uq_weakness_user_topic_subtopic"),
        CheckConstraint("severity BETWEEN 1 AND 5", name="ck_weakness_severity_range"),
        Index("idx_weakness_user_topic", "user_id", "topic"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_make_profile_id)
    # Spring이 관리하는 user 테이블을 참조한다
    user_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("user.id", ondelete="CASCADE"),
        nullable=False,
    )
    topic: Mapped[str | None] = mapped_column(String(255), nullable=True)
    subtopic: Mapped[str | None] = mapped_column(String(255), nullable=True)
    error_pattern: Mapped[str | None] = mapped_column(String(50), nullable=True)
    # 약점 발생 횟수. UPSERT마다 1씩 늘어난다
    frequency: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    # 심각도 1~5 (CHECK 제약)
    severity: Mapped[int | None] = mapped_column(Integer, nullable=True)
    first_seen_at: Mapped[datetime | None] = mapped_column(
        TIMESTAMP(timezone=True), nullable=True
    )
    last_seen_at: Mapped[datetime | None] = mapped_column(
        TIMESTAMP(timezone=True), nullable=True
    )
    # 약점을 극복한 시각. NULL이면 미해결
    resolved_at: Mapped[datetime | None] = mapped_column(
        TIMESTAMP(timezone=True), nullable=True
    )


class WeaknessSnapshot(Base):
    """특정 날짜의 토픽별 숙달도 스냅샷.

    (user_id, topic, snapshot_date) UNIQUE로 같은 날 중복 기록을 막는다.
    mastery는 0.0000~1.0000 소수다.
    """

    __tablename__ = "weakness_snapshot"
    __table_args__ = (
        UniqueConstraint(
            "user_id", "topic", "snapshot_date",
            name="uq_weakness_snapshot_user_topic_date",
        ),
        # 사용자·토픽별 최신 스냅샷 조회용 인덱스
        Index("idx_weakness_snapshot_user_topic", "user_id", "topic", "snapshot_date"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=_make_snapshot_id)
    user_id: Mapped[str] = mapped_column(
        String(36),
        ForeignKey("user.id", ondelete="CASCADE"),
        nullable=False,
    )
    topic: Mapped[str] = mapped_column(String(255), nullable=False)
    # 숙달도 0.0000~1.0000, Numeric(5,4)
    mastery: Mapped[float] = mapped_column(Numeric(5, 4), nullable=False)
    snapshot_date: Mapped[date] = mapped_column(Date, nullable=False)
