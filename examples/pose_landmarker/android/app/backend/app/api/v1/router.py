from fastapi import APIRouter

from app.api.v1 import auth, family, records, reports, users

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(auth.router)
api_router.include_router(family.router)
api_router.include_router(users.router)
api_router.include_router(reports.router)
api_router.include_router(records.router)
