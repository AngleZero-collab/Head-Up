import uuid
from datetime import date

from sqlalchemy import Date, Float, Integer, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    hashed_password: Mapped[str] = mapped_column(String(255), nullable=False)


class PostureRecord(Base):
    __tablename__ = "posture_records"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[str] = mapped_column(String(128), index=True, nullable=False)
    daily_slouch_count: Mapped[int] = mapped_column(Integer, nullable=False)
    ai_intercept_rate: Mapped[float] = mapped_column(Float, nullable=False)
    record_date: Mapped[date] = mapped_column(Date, index=True, nullable=False)
