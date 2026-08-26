from collections import defaultdict
from datetime import date, timedelta
import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db_session
from app.dependencies import get_current_user
from app.family_utils import generate_unique_invite_code
from app.models import DailyReport, Family, User
from app.schemas import (
    FamilyAccountRead,
    FamilyCreate,
    FamilyDashboardResponse,
    FamilyJoinRequest,
    FamilyLeaderboardEntry,
    FamilyLeaderboardResponse,
    FamilyMemberDashboard,
    FamilyMemberRead,
    FamilyRead,
    UserRead,
)

router = APIRouter(prefix="/family", tags=["family"])

FAMILY_ROLES = {"family_manager", "family_member"}


def normalized_plan(user: User) -> str:
    return "individual" if user.subscription_tier in {"free", "individual"} else user.subscription_tier


def is_family_manager(user: User) -> bool:
    return user.role == "admin" or (
        user.subscription_tier == "family"
        and user.role == "family_manager"
        and user.family_id is not None
    )


def public_name(user: User) -> str:
    if user.display_name:
        return user.display_name
    if user.role == "guest":
        return f"Guest {str(user.id)[-6:]}"
    return user.email.split("@")[0]


def member_read(user: User) -> FamilyMemberRead:
    return FamilyMemberRead(
        id=user.id,
        email=user.email,
        display_name=user.display_name,
        role=user.role,
        subscription_tier=normalized_plan(user),
        is_manager=user.role in {"admin", "family_manager"},
    )


async def family_for_user(session: AsyncSession, user: User) -> Family | None:
    if user.family_id is None:
        return None
    result = await session.execute(select(Family).where(Family.id == user.family_id))
    return result.scalar_one_or_none()


async def family_members(session: AsyncSession, family_id: uuid.UUID) -> list[User]:
    result = await session.execute(
        select(User)
        .where(User.family_id == family_id)
        .order_by(User.role.desc(), User.display_name, User.email)
    )
    return list(result.scalars().all())


async def summarize_members(
    session: AsyncSession,
    members: list[User],
    days: int,
) -> list[FamilyMemberDashboard]:
    if not members:
        return []

    since = date.today() - timedelta(days=days - 1)
    member_ids = [member.id for member in members]
    result = await session.execute(
        select(DailyReport)
        .where(DailyReport.user_id.in_(member_ids), DailyReport.record_date >= since)
        .order_by(DailyReport.record_date.desc())
    )
    reports_by_user: dict[uuid.UUID, list[DailyReport]] = defaultdict(list)
    for report in result.scalars().all():
        reports_by_user[report.user_id].append(report)

    summaries: list[FamilyMemberDashboard] = []
    for member in members:
        reports = reports_by_user.get(member.id, [])
        slouch_count = sum(report.slouch_count for report in reports)
        pet_exp = sum(report.pet_exp for report in reports)
        ai_intercept_rate = (
            sum(report.ai_intercept_rate for report in reports) / len(reports)
            if reports
            else 0.0
        )
        summaries.append(
            FamilyMemberDashboard(
                user_id=member.id,
                display_name=public_name(member),
                email=member.email,
                role=member.role,
                slouch_count=slouch_count,
                ai_intercept_rate=round(ai_intercept_rate, 4),
                pet_exp=pet_exp,
                report_days=len({report.record_date for report in reports}),
                latest_record_date=max((report.record_date for report in reports), default=None),
            )
        )
    return summaries


def posture_score(summary: FamilyMemberDashboard) -> int:
    if summary.report_days == 0:
        return 0
    posture_quality = summary.pet_exp / max(summary.pet_exp + summary.slouch_count * 5, 1)
    intercept_bonus = summary.ai_intercept_rate * 0.1
    weighted_score = max(0.0, min(1.0, (posture_quality * 0.9) + intercept_bonus))
    return int(round(weighted_score * 100))


def leaderboard_from_summaries(summaries: list[FamilyMemberDashboard]) -> list[FamilyLeaderboardEntry]:
    sorted_summaries = sorted(
        summaries,
        key=lambda item: (
            posture_score(item),
            item.pet_exp,
            -item.slouch_count,
            item.display_name.lower(),
        ),
        reverse=True,
    )
    return [
        FamilyLeaderboardEntry(
            rank=index + 1,
            user_id=summary.user_id,
            display_name=summary.display_name,
            role=summary.role,
            good_posture_score=posture_score(summary),
            slouch_count=summary.slouch_count,
            ai_intercept_rate=summary.ai_intercept_rate,
            pet_exp=summary.pet_exp,
            report_days=summary.report_days,
            latest_record_date=summary.latest_record_date,
        )
        for index, summary in enumerate(sorted_summaries)
    ]


@router.get("/me", response_model=FamilyAccountRead)
async def read_family_account(
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> FamilyAccountRead:
    family = await family_for_user(session, current_user)
    members = await family_members(session, family.id) if family and is_family_manager(current_user) else [current_user]
    return FamilyAccountRead(
        current_user=UserRead.model_validate(current_user),
        plan=normalized_plan(current_user),
        role=current_user.role,
        is_family_manager=is_family_manager(current_user),
        can_view_family_dashboard=is_family_manager(current_user) or family is None,
        family=FamilyRead.model_validate(family) if family else None,
        members=[member_read(member) for member in members],
    )


@router.post("/create", response_model=FamilyAccountRead, status_code=status.HTTP_201_CREATED)
async def create_family(
    payload: FamilyCreate,
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> FamilyAccountRead:
    if current_user.role == "guest":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Guests must register before creating a family plan.")
    if current_user.family_id is not None:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="User already belongs to a family.")

    family = Family(
        name=payload.name,
        invite_code=await generate_unique_invite_code(session),
        owner_user_id=current_user.id,
    )
    current_user.subscription_tier = "family"
    current_user.role = "family_manager"
    session.add(family)
    try:
        await session.flush()
        current_user.family_id = family.id
        await session.commit()
        await session.refresh(current_user)
        await session.refresh(family)
    except IntegrityError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Family invite code conflict") from exc
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Unable to create family") from exc
    return await read_family_account(session, current_user)


@router.post("/join", response_model=FamilyAccountRead)
async def join_family(
    payload: FamilyJoinRequest,
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> FamilyAccountRead:
    if current_user.role == "guest":
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Guests must register before joining a family plan.")

    result = await session.execute(
        select(Family).where(Family.invite_code == payload.invite_code.strip().upper())
    )
    family = result.scalar_one_or_none()
    if family is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Family invite code not found.")

    current_user.family_id = family.id
    current_user.subscription_tier = "family"
    current_user.role = "family_member"
    if payload.display_name is not None:
        current_user.display_name = payload.display_name
    try:
        await session.commit()
        await session.refresh(current_user)
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Unable to join family") from exc
    return await read_family_account(session, current_user)


@router.get("/leaderboard", response_model=FamilyLeaderboardResponse)
async def read_family_leaderboard(
    days: int = Query(default=7, ge=1, le=90),
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> FamilyLeaderboardResponse:
    family = await family_for_user(session, current_user)
    members = await family_members(session, family.id) if family else [current_user]
    summaries = await summarize_members(session, members, days)
    return FamilyLeaderboardResponse(
        plan=normalized_plan(current_user),
        family=FamilyRead.model_validate(family) if family else None,
        leaderboard=leaderboard_from_summaries(summaries),
    )


@router.get("/dashboard", response_model=FamilyDashboardResponse)
async def read_family_dashboard(
    days: int = Query(default=7, ge=1, le=90),
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> FamilyDashboardResponse:
    family = await family_for_user(session, current_user)
    if family and is_family_manager(current_user):
        members = await family_members(session, family.id)
    else:
        members = [current_user]

    summaries = await summarize_members(session, members, days)
    total_slouch = sum(member.slouch_count for member in summaries)
    total_pet_exp = sum(member.pet_exp for member in summaries)
    average_ai = (
        sum(member.ai_intercept_rate for member in summaries) / len(summaries)
        if summaries
        else 0.0
    )
    return FamilyDashboardResponse(
        plan=normalized_plan(current_user),
        family=FamilyRead.model_validate(family) if family else None,
        member_count=len(summaries),
        total_slouch_count=total_slouch,
        average_ai_intercept_rate=round(average_ai, 4),
        total_pet_exp=total_pet_exp,
        members=summaries,
    )
