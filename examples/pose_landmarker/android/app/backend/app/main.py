from contextlib import asynccontextmanager
import uuid

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import inspect, select, text, update

from app.api.v1.router import api_router
from app.config import get_settings
from app.dashboard import router as dashboard_router
from app.database import Base, engine
from app.models import User
from app.security import hash_password


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
        await connection.run_sync(ensure_user_account_columns)
        await seed_admin_account(connection)
    yield


def ensure_user_account_columns(connection) -> None:
    columns = {column["name"] for column in inspect(connection).get_columns("users")}
    if "subscription_tier" not in columns:
        connection.execute(
            text("ALTER TABLE users ADD COLUMN subscription_tier VARCHAR(32) NOT NULL DEFAULT 'free'")
        )
    if "role" not in columns:
        connection.execute(
            text("ALTER TABLE users ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'user'")
        )


async def seed_admin_account(connection) -> None:
    if not settings.admin_email or not settings.admin_password:
        return

    email = settings.admin_email.lower()
    result = await connection.execute(select(User.id).where(User.email == email))
    existing_user_id = result.scalar_one_or_none()
    if existing_user_id is None:
        await connection.execute(
            User.__table__.insert().values(
                id=uuid.uuid4(),
                email=email,
                hashed_password=hash_password(settings.admin_password),
                subscription_tier=settings.admin_subscription_tier,
                role="admin",
            )
        )
        return

    await connection.execute(
        update(User)
        .where(User.email == email)
        .values(role="admin", subscription_tier=settings.admin_subscription_tier)
    )


settings = get_settings()
app = FastAPI(title="Head Up API", version="1.0.0", lifespan=lifespan)

if settings.cors_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(api_router)
app.include_router(dashboard_router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
