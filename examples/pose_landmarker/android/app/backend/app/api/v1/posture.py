from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.dialects.postgresql import insert as postgresql_insert
from sqlalchemy.dialects.sqlite import insert as sqlite_insert
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db_session
from app.dependencies import get_current_user
from app.models import PostureRecord, User
from app.schemas import BatchPostureUpload, BatchPostureUploadResponse

router = APIRouter(prefix="/posture", tags=["posture"])


def _build_upsert_statement(dialect_name: str, rows: list[dict[str, Any]]):
    """依目前資料庫方言建立原子 UPSERT，支援正式 PostgreSQL 與本機 SQLite。"""
    if dialect_name == "postgresql":
        insert_statement = postgresql_insert(PostureRecord).values(rows)
    elif dialect_name == "sqlite":
        insert_statement = sqlite_insert(PostureRecord).values(rows)
    else:
        # 專案只支援 PostgreSQL 與 SQLite；明確失敗可避免非原子查詢後更新造成競態。
        raise RuntimeError(f"Unsupported database dialect: {dialect_name}")

    return insert_statement.on_conflict_do_update(
        index_elements=[PostureRecord.patient_id, PostureRecord.timestamp],
        set_={
            "parallax_cosine_ratio": insert_statement.excluded.parallax_cosine_ratio,
            "angular_velocity": insert_statement.excluded.angular_velocity,
            "is_stable": insert_statement.excluded.is_stable,
        },
    )


@router.post(
    "/batch",
    response_model=BatchPostureUploadResponse,
    status_code=status.HTTP_200_OK,
)
async def upload_posture_batch(
    batch: BatchPostureUpload,
    session: AsyncSession = Depends(get_db_session),
    current_user: User = Depends(get_current_user),
) -> BatchPostureUploadResponse:
    if not batch.records:
        return BatchPostureUploadResponse(synced_count=0)

    # payload 不接受 patient_id，一律綁定 JWT 登入者，避免竄改請求替其他帳號寫入資料。
    # dict 重複指定相同 UTC timestamp 時會保留最後一筆，符合離線資料的最後寫入優先規則。
    records_by_timestamp = {record.timestamp: record for record in batch.records}
    rows = [
        {
            "patient_id": current_user.id,
            "timestamp": record.timestamp,
            "parallax_cosine_ratio": record.parallax_cosine_ratio,
            "angular_velocity": record.angular_velocity,
            "is_stable": record.is_stable,
        }
        for record in records_by_timestamp.values()
    ]

    try:
        dialect_name = session.get_bind().dialect.name
        await session.execute(_build_upsert_statement(dialect_name, rows))
        # 整批只提交一次；任一資料庫錯誤都不留下部分同步結果。
        await session.commit()
    except (SQLAlchemyError, RuntimeError) as exc:
        await session.rollback()
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Unable to sync posture records",
        ) from exc

    # 回傳去重後實際新增或更新的資料列數，而不是原始輸入陣列長度。
    return BatchPostureUploadResponse(synced_count=len(rows))
