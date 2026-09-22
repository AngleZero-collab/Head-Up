import uuid
from datetime import date

from pydantic import BaseModel, ConfigDict, EmailStr, Field, model_validator
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


class FamilyRename(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class FamilyJoinRequest(BaseModel):
    invite_code: str = Field(min_length=4, max_length=24)
    display_name: str | None = Field(default=None, min_length=1, max_length=100)


class FamilyRead(BaseModel):
    id: uuid.UUID
    name: str
    invite_code: str | None = None
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
    slouch_count: int | None = None
    ai_intercept_rate: float | None = None
    pet_exp: int | None = None
    report_days: int | None = None
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


class SchoolRead(BaseModel):
    id: uuid.UUID
    official_school_code: str
    localized_name: str
    education_stage: str
    region: str
    district: str
    source: str
    verified: bool

    model_config = ConfigDict(from_attributes=True)


class EducationProfileUpdate(BaseModel):
    country_code: str = Field(default="TW", min_length=2, max_length=2)
    school_id: uuid.UUID | None = None
    grade_code: str | None = Field(default=None, max_length=20)
    education_stage: str | None = Field(default=None, max_length=32)
    public_alias: str = Field(min_length=2, max_length=20)
    leaderboard_opt_in: bool = False
    parent_consent_status: Literal["PENDING", "GRANTED", "REVOKED", "NOT_REQUIRED"] = "PENDING"

    @model_validator(mode="after")
    def validate_ranking_consent(self):
        if self.leaderboard_opt_in and self.school_id is None:
            raise ValueError("school_id is required for leaderboard participation")
        if self.leaderboard_opt_in and self.parent_consent_status not in {"GRANTED", "NOT_REQUIRED"}:
            raise ValueError("parent or guardian consent is required")
        return self


class EducationProfileRead(EducationProfileUpdate):
    user_id: uuid.UUID
    school_name: str | None = None


class PostureAggregateUpload(BaseModel):
    aggregate_id: str = Field(min_length=1, max_length=255)
    record_date: date
    mode: Literal["OBSERVATION", "GUARDING"]
    green_seconds: int = Field(ge=0, le=86400)
    yellow_seconds: int = Field(ge=0, le=86400)
    red_seconds: int = Field(ge=0, le=86400)
    unknown_seconds: int = Field(ge=0, le=86400)
    raw_points: int = Field(ge=-200000, le=200000)
    challenge_points: int = Field(ge=-200000, le=200000)
    longest_green_streak_seconds: int = Field(ge=0, le=86400)
    green_streak_count: int = Field(ge=0, le=10000)
    green_streak_total_seconds: int = Field(ge=0, le=86400)
    reminder_count: int = Field(ge=0, le=10000)
    successful_corrections: int = Field(ge=0, le=10000)
    recovery_seconds_total: int = Field(ge=0, le=8640000)
    scoring_version: Literal[1]
    idempotency_key: str = Field(min_length=8, max_length=255)

    @model_validator(mode="after")
    def validate_daily_seconds(self):
        total = self.green_seconds + self.yellow_seconds + self.red_seconds + self.unknown_seconds
        if total > 86400:
            raise ValueError("aggregate duration exceeds one day")
        if self.successful_corrections > self.reminder_count:
            raise ValueError("successful corrections cannot exceed reminders")
        if self.longest_green_streak_seconds > self.green_seconds:
            raise ValueError("longest green streak cannot exceed green duration")
        if self.green_streak_total_seconds > self.green_seconds:
            raise ValueError("green streak duration cannot exceed green duration")
        if self.mode == "OBSERVATION" and (self.reminder_count or self.successful_corrections):
            raise ValueError("observation mode cannot contain reminders")
        return self


class PostureAggregateBatch(BaseModel):
    aggregates: list[PostureAggregateUpload] = Field(min_length=1, max_length=100)


class LeaderboardEntry(BaseModel):
    rank: int
    public_alias: str
    posture_score: float
    challenge_points: int
    valid_days: int
    valid_minutes: int
    participant_count: int | None = None
    is_current_user: bool = False


class CampusLeaderboardResponse(BaseModel):
    entity_type: str
    scope_type: str
    period: str
    minimum_days: int = 3
    minimum_minutes: int = 30
    entries: list[LeaderboardEntry]
    current_user_window: list[LeaderboardEntry] = Field(default_factory=list)
    current_user_qualified: bool
    generated_at_ms: int


class GuardInsightResponse(BaseModel):
    observation_bad_ratio: float | None
    guarding_bad_ratio: float | None
    absolute_change_percentage_points: float | None
    relative_improvement_percent: float | None
    reminder_correction_rate: float | None
    average_recovery_seconds: float | None
    minimum_minutes_per_mode: int = 30
    is_comparable: bool
