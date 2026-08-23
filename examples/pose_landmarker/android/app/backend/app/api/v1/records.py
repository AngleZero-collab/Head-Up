from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db_session
from app.dependencies import get_current_user
from app.models import DailyReport, User
from app.schemas import PostureRecordCreate, RecordsSyncResponse

router = APIRouter(prefix="/records", tags=["records"])


@router.post("/sync", response_model=RecordsSyncResponse, status_code=status.HTTP_200_OK)
async def sync_records(
    records: list[PostureRecordCreate],
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> RecordsSyncResponse:
    if not records:
        return RecordsSyncResponse(inserted=0)

    posture_records = [
        DailyReport(
            user_id=current_user.id,
            slouch_count=record.daily_slouch_count,
            ai_intercept_rate=record.ai_intercept_rate,
            record_date=record.record_date,
            pet_exp=0,
        )
        for record in records
    ]
    try:
        session.add_all(posture_records)
        await session.commit()
    except SQLAlchemyError as exc:
        await session.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Unable to sync posture records",
        ) from exc
    return RecordsSyncResponse(inserted=len(posture_records))
