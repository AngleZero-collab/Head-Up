import uuid
from datetime import date

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class UserCreate(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class GuestLoginRequest(BaseModel):
    device_id: str = Field(
        min_length=8,
        max_length=128,
        pattern=r"^[A-Za-z0-9._:-]+$",
    )


class UserRead(BaseModel):
    id: uuid.UUID
    email: str
    subscription_tier: str
    role: str

    model_config = ConfigDict(from_attributes=True)


class UserUpdate(BaseModel):
    email: EmailStr | None = None
    password: str | None = Field(default=None, min_length=8, max_length=128)
    subscription_tier: str | None = Field(default=None, min_length=1, max_length=32)
    role: str | None = Field(default=None, min_length=1, max_length=32)


class DailyReportCreate(BaseModel):
    record_date: date
    slouch_count: int = Field(ge=0)
    ai_intercept_rate: float = Field(ge=0.0, le=1.0)
    pet_exp: int = Field(default=0, ge=0)


class DailyReportRead(DailyReportCreate):
    id: uuid.UUID
    user_id: uuid.UUID

    model_config = ConfigDict(from_attributes=True)


class DailyReportAdminRead(DailyReportRead):
    user_email: str
    user_role: str
    subscription_tier: str


class DailyReportUpdate(BaseModel):
    record_date: date | None = None
    slouch_count: int | None = Field(default=None, ge=0)
    ai_intercept_rate: float | None = Field(default=None, ge=0.0, le=1.0)
    pet_exp: int | None = Field(default=None, ge=0)


class ReportsSyncResponse(BaseModel):
    inserted: int


class PostureRecordCreate(BaseModel):
    user_id: str = Field(min_length=1, max_length=128)
    daily_slouch_count: int = Field(ge=0)
    ai_intercept_rate: float = Field(ge=0.0, le=1.0)
    record_date: date


class RecordsSyncResponse(ReportsSyncResponse):
    pass


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    user_id: str
    subscription_tier: str
    role: str
