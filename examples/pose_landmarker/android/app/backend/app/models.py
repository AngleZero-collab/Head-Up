import uuid
from datetime import date

from sqlalchemy import Date, Float, ForeignKey, Integer, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    hashed_password: Mapped[str] = mapped_column(String(255), nullable=False)
    subscription_tier: Mapped[str] = mapped_column(String(32), nullable=False, default="free")
    role: Mapped[str] = mapped_column(String(32), nullable=False, default="user")

    daily_reports: Mapped[list["DailyReport"]] = relationship(
        back_populates="user",
        cascade="all, delete-orphan",
    )


class DailyReport(Base):
    __tablename__ = "daily_reports"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        index=True,
        nullable=False,
    )
    record_date: Mapped[date] = mapped_column(Date, index=True, nullable=False)
    slouch_count: Mapped[int] = mapped_column(Integer, nullable=False)
    ai_intercept_rate: Mapped[float] = mapped_column(Float, nullable=False)
    pet_exp: Mapped[int] = mapped_column(Integer, nullable=False, default=0)

    user: Mapped[User] = relationship(back_populates="daily_reports")
