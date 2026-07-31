from fastapi import APIRouter

from app.api.v1 import auth, records, reports

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(auth.router)
api_router.include_router(reports.router)
api_router.include_router(records.router)
