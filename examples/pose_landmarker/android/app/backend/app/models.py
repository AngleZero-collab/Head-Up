import uuid
from datetime import date, datetime

from sqlalchemy import BigInteger, Boolean, Date, DateTime, Float, ForeignKey, Integer, String, Uuid, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class Family(Base):
    __tablename__ = "families"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    invite_code: Mapped[str] = mapped_column(String(24), unique=True, index=True, nullable=False)
    owner_user_id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    members: Mapped[list["User"]] = relationship(
        back_populates="family",
        foreign_keys="User.family_id",
    )


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    display_name: Mapped[str] = mapped_column(String(100), nullable=True)
    hashed_password: Mapped[str] = mapped_column(String(255), nullable=False)
    subscription_tier: Mapped[str] = mapped_column(String(32), nullable=False, default="individual")
    role: Mapped[str] = mapped_column(String(32), nullable=False, default="user")
    family_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("families.id", ondelete="SET NULL"),
        index=True,
        nullable=True,
    )

    daily_reports: Mapped[list["DailyReport"]] = relationship(
        back_populates="user",
        cascade="all, delete-orphan",
    )
    family: Mapped[Family] = relationship(
        back_populates="members",
        foreign_keys=[family_id],
    )


class DailyReport(Base):
    __tablename__ = "daily_reports"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        index=True,
        nullable=False,
    )
    record_date: Mapped[date] = mapped_column(Date, index=True, nullable=False)
    slouch_count: Mapped[int] = mapped_column(Integer, nullable=False)
    ai_intercept_rate: Mapped[float] = mapped_column(Float, nullable=False)
    pet_exp: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    user: Mapped[User] = relationship(back_populates="daily_reports")


class School(Base):
    __tablename__ = "schools"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    country_code: Mapped[str] = mapped_column(String(2), index=True, nullable=False)
    official_school_code: Mapped[str] = mapped_column(String(64), unique=True, index=True, nullable=False)
    school_name: Mapped[str] = mapped_column(String(255), nullable=False)
    localized_name: Mapped[str] = mapped_column(String(255), index=True, nullable=False)
    education_stage: Mapped[str] = mapped_column(String(32), index=True, nullable=False)
    region: Mapped[str] = mapped_column(String(100), index=True, nullable=False)
    district: Mapped[str] = mapped_column(String(100), nullable=False, default="")
    active_status: Mapped[str] = mapped_column(String(20), nullable=False, default="ACTIVE")
    source: Mapped[str] = mapped_column(String(255), nullable=False)
    source_version: Mapped[str] = mapped_column(String(64), nullable=False)
    verified: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class EducationProfile(Base):
    __tablename__ = "education_profiles"

    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True
    )
    country_code: Mapped[str] = mapped_column(String(2), nullable=False, default="TW")
    school_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("schools.id", ondelete="SET NULL"), index=True, nullable=True
    )
    grade_code: Mapped[str] = mapped_column(String(20), index=True, nullable=True)
    education_stage: Mapped[str] = mapped_column(String(32), index=True, nullable=True)
    public_alias: Mapped[str] = mapped_column(String(20), nullable=False)
    leaderboard_opt_in: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    parent_consent_status: Mapped[str] = mapped_column(String(32), nullable=False, default="PENDING")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())


class PostureAggregate(Base):
    __tablename__ = "posture_aggregates"
    __table_args__ = (
        UniqueConstraint("user_id", "record_date", "mode", name="uq_posture_aggregate_user_day_mode"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), index=True, nullable=False
    )
    client_aggregate_id: Mapped[str] = mapped_column(String(255), nullable=False)
    record_date: Mapped[date] = mapped_column(Date, index=True, nullable=False)
    mode: Mapped[str] = mapped_column(String(20), index=True, nullable=False)
    green_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False)
    yellow_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False)
    red_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False)
    unknown_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False)
    raw_points: Mapped[int] = mapped_column(Integer, nullable=False)
    challenge_points: Mapped[int] = mapped_column(Integer, nullable=False)
    posture_score: Mapped[float] = mapped_column(Float, nullable=True)
    longest_green_streak_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    green_streak_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    green_streak_total_seconds: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    reminder_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    successful_corrections: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    recovery_seconds_total: Mapped[int] = mapped_column(BigInteger, nullable=False, default=0)
    scoring_version: Mapped[int] = mapped_column(Integer, nullable=False)
    idempotency_key: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now())
