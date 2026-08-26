import uuid
from datetime import date

from pydantic import BaseModel, ConfigDict, EmailStr, Field
from typing import Literal


SubscriptionTier = Literal["individual", "family", "guest", "admin"]
UserRole = Literal["user", "guest", "admin", "family_manager", "family_member"]


class UserCreate(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str | None = Field(default=None, min_length=1, max_length=100)
    subscription_tier: Literal["individual", "family"] = "individual"
    family_name: str | None = Field(default=None, min_length=1, max_length=120)


class GuestLoginRequest(BaseModel):
    device_id: str = Field(
        min_length=8,
        max_length=128,
        pattern=r"^[A-Za-z0-9._:-]+$",
    )


class UserRead(BaseModel):
    id: uuid.UUID
    email: str
    display_name: str | None = None
    subscription_tier: str
    role: str
    family_id: uuid.UUID | None = None

    model_config = ConfigDict(from_attributes=True)


class UserUpdate(BaseModel):
    email: EmailStr | None = None
    password: str | None = Field(default=None, min_length=8, max_length=128)
    display_name: str | None = Field(default=None, min_length=1, max_length=100)
    subscription_tier: str | None = Field(default=None, min_length=1, max_length=32)
    role: str | None = Field(default=None, min_length=1, max_length=32)
    family_id: uuid.UUID | None = None


class FamilyCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class FamilyJoinRequest(BaseModel):
    invite_code: str = Field(min_length=4, max_length=24)
    display_name: str | None = Field(default=None, min_length=1, max_length=100)


class FamilyRead(BaseModel):
    id: uuid.UUID
    name: str
    invite_code: str
    owner_user_id: uuid.UUID | None = None

    model_config = ConfigDict(from_attributes=True)


class FamilyMemberRead(BaseModel):
    id: uuid.UUID
    email: str
    display_name: str | None = None
    role: str
    subscription_tier: str
    is_manager: bool


class FamilyAccountRead(BaseModel):
    current_user: UserRead
    plan: str
    role: str
    is_family_manager: bool
    can_view_family_dashboard: bool
    family: FamilyRead | None = None
    members: list[FamilyMemberRead] = Field(default_factory=list)


class FamilyLeaderboardEntry(BaseModel):
    rank: int
    user_id: uuid.UUID
    display_name: str
    role: str
    good_posture_score: int
    slouch_count: int
    ai_intercept_rate: float
    pet_exp: int
    report_days: int
    latest_record_date: date | None = None


class FamilyLeaderboardResponse(BaseModel):
    plan: str
    family: FamilyRead | None = None
    leaderboard: list[FamilyLeaderboardEntry]


class FamilyMemberDashboard(BaseModel):
    user_id: uuid.UUID
    display_name: str
    email: str
    role: str
    slouch_count: int
    ai_intercept_rate: float
    pet_exp: int
    report_days: int
    latest_record_date: date | None = None


class FamilyDashboardResponse(BaseModel):
    plan: str
    family: FamilyRead | None = None
    member_count: int
    total_slouch_count: int
    average_ai_intercept_rate: float
    total_pet_exp: int
    members: list[FamilyMemberDashboard]


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
    display_name: str | None = None
    family_id: str | None = None
