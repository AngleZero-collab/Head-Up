import uuid
from datetime import date, datetime

from sqlalchemy import Date, DateTime, Float, ForeignKey, Integer, String, Uuid, func
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
