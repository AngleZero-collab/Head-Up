import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db_session
from app.dependencies import get_current_admin_user, get_current_user
from app.models import User
from app.schemas import UserRead, UserUpdate
from app.security import hash_password

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/me", response_model=UserRead)
async def read_my_account(
    current_user: User = Depends(get_current_user),
) -> User:
    return current_user


@router.patch("/me", response_model=UserRead)
async def update_my_account(
    payload: UserUpdate,
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> User:
    if payload.email is not None:
        current_user.email = payload.email.lower()
    if payload.password is not None:
        current_user.hashed_password = hash_password(payload.password)
    if payload.display_name is not None:
        current_user.display_name = payload.display_name

    try:
        await session.commit()
        await session.refresh(current_user)
    except IntegrityError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered") from exc
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Unable to update account") from exc

    return current_user


@router.get("", response_model=list[UserRead])
async def list_users(
    session: AsyncSession = Depends(get_db_session),
    _: User = Depends(get_current_admin_user),
) -> list[User]:
    result = await session.execute(select(User).order_by(User.email))
    return list(result.scalars().all())


@router.patch("/{user_id}", response_model=UserRead)
async def admin_update_user(
    user_id: uuid.UUID,
    payload: UserUpdate,
    session: AsyncSession = Depends(get_db_session),
    _: User = Depends(get_current_admin_user),
) -> User:
    result = await session.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    if payload.email is not None:
        user.email = payload.email.lower()
    if payload.password is not None:
        user.hashed_password = hash_password(payload.password)
    if payload.display_name is not None:
        user.display_name = payload.display_name
    if payload.subscription_tier is not None:
        user.subscription_tier = payload.subscription_tier
    if payload.role is not None:
        user.role = payload.role
    if payload.family_id is not None:
        user.family_id = payload.family_id

    try:
        await session.commit()
        await session.refresh(user)
    except IntegrityError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already registered") from exc
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Unable to update user") from exc

    return user
