from datetime import timedelta
import hashlib
import secrets

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.config import get_settings
from app.database import get_db_session
from app.dependencies import get_current_user
from app.models import User
from app.schemas import GuestLoginRequest, TokenResponse, UserCreate, UserRead
from app.security import create_access_token, hash_password, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


def token_for_user(user: User) -> TokenResponse:
    settings = get_settings()
    expires = timedelta(minutes=settings.access_token_expire_minutes)
    token = create_access_token(subject=str(user.id), expires_delta=expires)
    return TokenResponse(
        access_token=token,
        expires_in=settings.access_token_expire_minutes * 60,
        user_id=str(user.id),
        subscription_tier=user.subscription_tier,
        role=user.role,
    )


def guest_email_for_device(device_id: str) -> str:
    digest = hashlib.sha256(device_id.strip().lower().encode("utf-8")).hexdigest()[:20]
    return f"guest-{digest}@guest.headup.local"


@router.post("/register", response_model=UserRead, status_code=status.HTTP_201_CREATED)
async def register(
    payload: UserCreate,
    session: AsyncSession = Depends(get_db_session),
) -> User:
    normalized_email = payload.email.lower()
    user = User(
        email=normalized_email,
        hashed_password=hash_password(payload.password),
        subscription_tier="free",
        role="user",
    )
    session.add(user)
    try:
        await session.commit()
        await session.refresh(user)
    except IntegrityError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered") from exc
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Unable to create user") from exc
    return user


@router.post("/login", response_model=TokenResponse)
async def login(
    form_data: OAuth2PasswordRequestForm = Depends(),
    session: AsyncSession = Depends(get_db_session),
) -> TokenResponse:
    result = await session.execute(select(User).where(User.email == form_data.username.lower()))
    user = result.scalar_one_or_none()
    if user is None or not verify_password(form_data.password, user.hashed_password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password",
            headers={"WWW-Authenticate": "Bearer"},
        )

    return token_for_user(user)


@router.post("/guest", response_model=TokenResponse)
async def guest_login(
    payload: GuestLoginRequest,
    session: AsyncSession = Depends(get_db_session),
) -> TokenResponse:
    email = guest_email_for_device(payload.device_id)
    result = await session.execute(select(User).where(User.email == email))
    user = result.scalar_one_or_none()
    if user is not None:
        return token_for_user(user)

    user = User(
        email=email,
        hashed_password=hash_password(secrets.token_urlsafe(48)),
        subscription_tier="guest",
        role="guest",
    )
    session.add(user)
    try:
        await session.commit()
        await session.refresh(user)
    except IntegrityError:
        await session.rollback()
        result = await session.execute(select(User).where(User.email == email))
        user = result.scalar_one_or_none()
        if user is None:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Guest account conflict")
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Unable to create guest") from exc

    return token_for_user(user)


@router.get("/me", response_model=UserRead)
async def read_current_account(
    current_user: User = Depends(get_current_user),
) -> User:
    return current_user
