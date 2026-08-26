import secrets
import string

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncConnection, AsyncSession

from app.models import Family


ALPHABET = string.ascii_uppercase + string.digits


async def generate_unique_invite_code(session: AsyncSession | AsyncConnection, length: int = 8) -> str:
    for _ in range(20):
        code = "".join(secrets.choice(ALPHABET) for _ in range(length))
        result = await session.execute(select(Family.id).where(Family.invite_code == code))
        if result.scalar_one_or_none() is None:
            return code
    return secrets.token_urlsafe(12).replace("-", "").replace("_", "")[:length].upper()
