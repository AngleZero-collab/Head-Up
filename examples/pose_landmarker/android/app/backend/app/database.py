from collections.abc import AsyncGenerator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from app.config import get_settings


class Base(DeclarativeBase):
    pass


def normalize_async_database_url(database_url: str) -> str:
    """將雲端平台常見的 PostgreSQL URL 轉為 SQLAlchemy asyncpg URL。"""
    if database_url.startswith("postgres://"):
        return database_url.replace("postgres://", "postgresql+asyncpg://", 1)
    if database_url.startswith("postgresql://"):
        return database_url.replace("postgresql://", "postgresql+asyncpg://", 1)
    return database_url


settings = get_settings()
database_url = normalize_async_database_url(settings.database_url)

# SQLite 與 PostgreSQL 共用這個建立方式，不傳入 check_same_thread 等 SQLite 專用參數。
# 正式環境只需提供 DATABASE_URL，裸 PostgreSQL URL 會在上方自動切換為 asyncpg。
engine = create_async_engine(database_url, pool_pre_ping=True)
AsyncSessionLocal = async_sessionmaker(engine, expire_on_commit=False, class_=AsyncSession)


async def get_db_session() -> AsyncGenerator[AsyncSession, None]:
    async with AsyncSessionLocal() as session:
        yield session
