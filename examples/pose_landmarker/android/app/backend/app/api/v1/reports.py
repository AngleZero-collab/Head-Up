from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db_session
from app.dependencies import get_current_user
from app.models import DailyReport, User
from app.schemas import DailyReportCreate, ReportsSyncResponse

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
