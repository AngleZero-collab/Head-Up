import uuid
from datetime import date

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class PostureRecordCreate(BaseModel):
    user_id: str = Field(min_length=1, max_length=128)
    daily_slouch_count: int = Field(ge=0)
    ai_intercept_rate: float = Field(ge=0.0, le=1.0)
    record_date: date


class PostureRecordRead(PostureRecordCreate):
    id: uuid.UUID

    model_config = ConfigDict(from_attributes=True)


class RecordsSyncResponse(BaseModel):
    inserted: int


class UserCreate(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)


class UserRead(BaseModel):
    id: uuid.UUID
    email: EmailStr

    model_config = ConfigDict(from_attributes=True)


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int
    user_id: str
