from contextlib import asynccontextmanager
import uuid

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.api.v1.router import api_router
from app.config import get_settings
from app.database import Base, engine
from app.security import hash_password


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
        await connection.execute(
            text("ALTER TABLE users ADD COLUMN IF NOT EXISTS subscription_tier VARCHAR(32) NOT NULL DEFAULT 'free'")
        )
        await connection.execute(
            text("ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'user'")
        )
        await seed_admin_account(connection)
    yield


async def seed_admin_account(connection) -> None:
    if not settings.admin_email or not settings.admin_password:
        return

    email = settings.admin_email.lower()
    result = await connection.execute(text("SELECT id FROM users WHERE email = :email"), {"email": email})
    existing_user_id = result.scalar_one_or_none()
    if existing_user_id is None:
        await connection.execute(
            text(
                """
                INSERT INTO users (id, email, hashed_password, subscription_tier, role)
                VALUES (:id, :email, :hashed_password, :subscription_tier, 'admin')
                """
            ),
            {
                "id": uuid.uuid4(),
                "email": email,
                "hashed_password": hash_password(settings.admin_password),
                "subscription_tier": settings.admin_subscription_tier,
            },
        )
        return

    await connection.execute(
        text(
            """
            UPDATE users
            SET role = 'admin', subscription_tier = :subscription_tier
            WHERE email = :email
            """
        ),
        {"email": email, "subscription_tier": settings.admin_subscription_tier},
    )


settings = get_settings()
app = FastAPI(title="HeadUp API", version="1.0.0", lifespan=lifespan)

if settings.cors_origins:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

app.include_router(api_router)


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}
