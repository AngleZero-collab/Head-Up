from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db_session
from app.dependencies import get_current_admin_user, get_current_user
from app.models import DailyReport, User
from app.schemas import DailyReportAdminRead, DailyReportCreate, DailyReportRead, ReportsSyncResponse

router = APIRouter(prefix="/reports", tags=["reports"])


@router.post("/sync", response_model=ReportsSyncResponse, status_code=status.HTTP_200_OK)
async def sync_daily_reports(
    reports: list[DailyReportCreate],
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> ReportsSyncResponse:
    if not reports:
        return ReportsSyncResponse(inserted=0)
    if len(reports) > 366:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A sync payload can contain at most 366 daily reports.",
        )

    daily_reports = [
        DailyReport(
            user_id=current_user.id,
            record_date=report.record_date,
            slouch_count=report.slouch_count,
            ai_intercept_rate=report.ai_intercept_rate,
            pet_exp=report.pet_exp,
        )
        for report in reports
    ]

    try:
        session.add_all(daily_reports)
        await session.commit()
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Unable to sync daily reports",
        ) from exc

    return ReportsSyncResponse(inserted=len(daily_reports))


@router.get("/me", response_model=list[DailyReportRead])
async def list_my_daily_reports(
    limit: int = Query(default=200, ge=1, le=1000),
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> list[DailyReport]:
    result = await session.execute(
        select(DailyReport)
        .where(DailyReport.user_id == current_user.id)
        .order_by(DailyReport.record_date.desc(), DailyReport.id.desc())
        .limit(limit)
    )
    return list(result.scalars().all())


@router.get("/family", response_model=list[DailyReportAdminRead])
async def list_family_daily_reports(
    limit: int = Query(default=500, ge=1, le=2000),
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> list[DailyReportAdminRead]:
    if current_user.role != "admin" and not (
        current_user.subscription_tier == "family"
        and current_user.role == "family_manager"
        and current_user.family_id is not None
    ):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Family manager privileges required",
        )

    query = (
        select(DailyReport, User)
        .join(User, DailyReport.user_id == User.id)
        .order_by(DailyReport.record_date.desc(), DailyReport.id.desc())
        .limit(limit)
    )
    if current_user.role != "admin":
        query = query.where(User.family_id == current_user.family_id)

    result = await session.execute(query)
    return [
        DailyReportAdminRead(
            id=report.id,
            user_id=report.user_id,
            record_date=report.record_date,
            slouch_count=report.slouch_count,
            ai_intercept_rate=report.ai_intercept_rate,
            pet_exp=report.pet_exp,
            user_email=user.email,
            user_role=user.role,
            subscription_tier=user.subscription_tier,
        )
        for report, user in result.all()
    ]


@router.get("", response_model=list[DailyReportAdminRead])
async def list_all_daily_reports(
    limit: int = Query(default=200, ge=1, le=1000),
    session: AsyncSession = Depends(get_db_session),
    _: User = Depends(get_current_admin_user),
) -> list[DailyReportAdminRead]:
    result = await session.execute(
        select(DailyReport, User)
        .join(User, DailyReport.user_id == User.id)
        .order_by(DailyReport.record_date.desc(), DailyReport.id.desc())
        .limit(limit)
    )

    return [
        DailyReportAdminRead(
            id=report.id,
            user_id=report.user_id,
            record_date=report.record_date,
            slouch_count=report.slouch_count,
            ai_intercept_rate=report.ai_intercept_rate,
            pet_exp=report.pet_exp,
            user_email=user.email,
            user_role=user.role,
            subscription_tier=user.subscription_tier,
        )
        for report, user in result.all()
    ]
