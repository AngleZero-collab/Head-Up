import statistics
import time
import uuid
from collections import defaultdict
from datetime import date, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import and_, or_, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_db_session
from app.dependencies import get_current_user
from app.models import EducationProfile, PostureAggregate, School, User
from app.schemas import (
    CampusLeaderboardResponse,
    EducationProfileRead,
    EducationProfileUpdate,
    GuardInsightResponse,
    LeaderboardEntry,
    PostureAggregateBatch,
    ReportsSyncResponse,
    SchoolRead,
)

router = APIRouter(tags=["campus-challenge"])

MINIMUM_VALID_DAYS = 3
MINIMUM_VALID_SECONDS = 30 * 60
SCHOOL_MINIMUM_PARTICIPANTS = 5
COMPARISON_MINIMUM_SECONDS = 30 * 60


def posture_score(green: int, yellow: int, red: int) -> float | None:
    valid = green + yellow + red
    if valid <= 0:
        return None
    return max(0.0, min(100.0, 100.0 * (green + 0.5 * yellow) / valid))


def server_challenge_points(payload) -> int:
    valid = payload.green_seconds + payload.yellow_seconds + payload.red_seconds
    if valid <= 0:
        return 0
    cap_scale = min(1.0, 3600.0 / valid)
    multiplier = 1.0
    if payload.longest_green_streak_seconds >= 300:
        multiplier = 2.0
    elif payload.longest_green_streak_seconds >= 180:
        multiplier = 1.5
    elif payload.longest_green_streak_seconds >= 60:
        multiplier = 1.2
    points = (
        (payload.green_seconds // 10) * 10 * multiplier
        - (payload.yellow_seconds // 10) * 5
        - (payload.red_seconds // 10) * 15
    )
    return round(points * cap_scale)


def period_dates(period: str, today: date | None = None) -> tuple[date, date]:
    today = today or date.today()
    if period == "THIS_WEEK":
        return today - timedelta(days=today.weekday()), today
    if period == "LAST_WEEK":
        this_monday = today - timedelta(days=today.weekday())
        return this_monday - timedelta(days=7), this_monday - timedelta(days=1)
    if period == "THIS_MONTH":
        return today.replace(day=1), today
    raise HTTPException(status_code=422, detail="Unsupported leaderboard period")


@router.get("/schools", response_model=list[SchoolRead])
async def list_schools(
    country_code: str = Query(default="TW", min_length=2, max_length=2),
    stage: str | None = None,
    q: str = Query(default="", max_length=100),
    limit: int = Query(default=30, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
    _: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db_session),
) -> list[School]:
    filters = [School.country_code == country_code.upper(), School.active_status == "ACTIVE", School.verified.is_(True)]
    if stage:
        filters.append(School.education_stage == stage)
    if q.strip():
        pattern = f"%{q.strip()}%"
        filters.append(
            or_(
                School.localized_name.ilike(pattern),
                School.school_name.ilike(pattern),
                School.official_school_code.ilike(pattern),
            )
        )
    result = await session.execute(
        select(School).where(and_(*filters)).order_by(School.region, School.localized_name).limit(limit).offset(offset)
    )
    return list(result.scalars())


async def profile_response(profile: EducationProfile, session: AsyncSession) -> EducationProfileRead:
    school_name = None
    if profile.school_id:
        school_name = await session.scalar(select(School.localized_name).where(School.id == profile.school_id))
    return EducationProfileRead(
        user_id=profile.user_id,
        country_code=profile.country_code,
        school_id=profile.school_id,
        school_name=school_name,
        grade_code=profile.grade_code,
        education_stage=profile.education_stage,
        public_alias=profile.public_alias,
        leaderboard_opt_in=profile.leaderboard_opt_in,
        parent_consent_status=profile.parent_consent_status,
    )


@router.get("/profile/education", response_model=EducationProfileRead)
async def get_education_profile(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db_session),
) -> EducationProfileRead:
    profile = await session.get(EducationProfile, current_user.id)
    if profile is None:
        raise HTTPException(status_code=404, detail="Education profile not configured")
    return await profile_response(profile, session)


@router.put("/profile/education", response_model=EducationProfileRead)
async def update_education_profile(
    payload: EducationProfileUpdate,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db_session),
) -> EducationProfileRead:
    if payload.school_id:
        school = await session.get(School, payload.school_id)
        if school is None or not school.verified or school.active_status != "ACTIVE":
            raise HTTPException(status_code=422, detail="School is not in the verified active catalog")
        if school.country_code != payload.country_code:
            raise HTTPException(status_code=422, detail="School country does not match profile")
    profile = await session.get(EducationProfile, current_user.id)
    if profile is None:
        profile = EducationProfile(user_id=current_user.id, **payload.model_dump())
        session.add(profile)
    else:
        for field, value in payload.model_dump().items():
            setattr(profile, field, value)
    await session.commit()
    await session.refresh(profile)
    return await profile_response(profile, session)


@router.post("/posture-aggregates/batch", response_model=ReportsSyncResponse)
async def upload_posture_aggregates(
    payload: PostureAggregateBatch,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db_session),
) -> ReportsSyncResponse:
    inserted = 0
    for item in payload.aggregates:
        replay = await session.scalar(
            select(PostureAggregate.id).where(PostureAggregate.idempotency_key == item.idempotency_key)
        )
        if replay is not None:
            continue
        existing = await session.scalar(
            select(PostureAggregate).where(
                PostureAggregate.user_id == current_user.id,
                PostureAggregate.record_date == item.record_date,
                PostureAggregate.mode == item.mode,
            )
        )
        values = item.model_dump()
        values["client_aggregate_id"] = values.pop("aggregate_id")
        values["posture_score"] = posture_score(item.green_seconds, item.yellow_seconds, item.red_seconds)
        values["challenge_points"] = server_challenge_points(item)
        if existing is None:
            session.add(PostureAggregate(user_id=current_user.id, **values))
            inserted += 1
        else:
            for field, value in values.items():
                setattr(existing, field, value)
    await session.commit()
    return ReportsSyncResponse(inserted=inserted)


def aggregate_users(rows: list[tuple[PostureAggregate, EducationProfile]]) -> dict:
    users = defaultdict(
        lambda: {
            "green": 0,
            "yellow": 0,
            "red": 0,
            "points": 0,
            "days": set(),
            "longest_green": 0,
            "profile": None,
        }
    )
    for aggregate, profile in rows:
        value = users[aggregate.user_id]
        value["green"] += aggregate.green_seconds
        value["yellow"] += aggregate.yellow_seconds
        value["red"] += aggregate.red_seconds
        value["points"] += aggregate.challenge_points
        value["longest_green"] = max(value["longest_green"], aggregate.longest_green_streak_seconds)
        if aggregate.green_seconds + aggregate.yellow_seconds + aggregate.red_seconds > 0:
            value["days"].add(aggregate.record_date)
        value["profile"] = profile
    return users


@router.get("/leaderboards", response_model=CampusLeaderboardResponse)
async def get_leaderboard(
    entity_type: str = Query(pattern="^(USER|SCHOOL)$"),
    scope_type: str = Query(pattern="^(SCHOOL|GRADE_IN_SCHOOL|GRADE_IN_COUNTRY|EDUCATION_STAGE|COUNTRY|GLOBAL)$"),
    period: str = Query(pattern="^(THIS_WEEK|LAST_WEEK|THIS_MONTH)$"),
    school_id: uuid.UUID | None = None,
    grade_code: str | None = None,
    education_stage: str | None = None,
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db_session),
) -> CampusLeaderboardResponse:
    start, end = period_dates(period)
    current_profile = await session.get(EducationProfile, current_user.id)
    query = (
        select(PostureAggregate, EducationProfile)
        .join(EducationProfile, EducationProfile.user_id == PostureAggregate.user_id)
        .where(
            PostureAggregate.mode == "GUARDING",
            PostureAggregate.record_date.between(start, end),
            EducationProfile.leaderboard_opt_in.is_(True),
            EducationProfile.parent_consent_status.in_(["GRANTED", "NOT_REQUIRED"]),
        )
    )
    profile_school_id = current_profile.school_id if current_profile else None
    profile_grade = current_profile.grade_code if current_profile else None
    profile_stage = current_profile.education_stage if current_profile else None
    profile_country = current_profile.country_code if current_profile else "TW"
    if scope_type == "SCHOOL":
        resolved_school_id = school_id or profile_school_id
        if resolved_school_id is None:
            raise HTTPException(status_code=422, detail="school_id or an education profile school is required")
        query = query.where(EducationProfile.school_id == resolved_school_id)
    elif scope_type == "GRADE_IN_SCHOOL":
        resolved_school_id = school_id or profile_school_id
        resolved_grade = grade_code or profile_grade
        if resolved_school_id is None or not resolved_grade:
            raise HTTPException(status_code=422, detail="school and grade are required for this scope")
        query = query.where(
            EducationProfile.school_id == resolved_school_id,
            EducationProfile.grade_code == resolved_grade,
        )
    elif scope_type == "GRADE_IN_COUNTRY":
        resolved_grade = grade_code or profile_grade
        if not resolved_grade:
            raise HTTPException(status_code=422, detail="grade is required for this scope")
        query = query.where(
            EducationProfile.country_code == profile_country,
            EducationProfile.grade_code == resolved_grade,
        )
    elif scope_type == "EDUCATION_STAGE":
        resolved_stage = education_stage or profile_stage
        if not resolved_stage:
            raise HTTPException(status_code=422, detail="education stage is required for this scope")
        query = query.where(EducationProfile.education_stage == resolved_stage)
    elif scope_type == "COUNTRY":
        query = query.where(EducationProfile.country_code == profile_country)
    rows = list((await session.execute(query)).all())
    grouped = aggregate_users(rows)
    qualified = []
    for user_id, value in grouped.items():
        valid = value["green"] + value["yellow"] + value["red"]
        score = posture_score(value["green"], value["yellow"], value["red"])
        if len(value["days"]) < MINIMUM_VALID_DAYS or valid < MINIMUM_VALID_SECONDS or score is None:
            continue
        qualified.append((user_id, value, score, valid))

    entries = []
    if entity_type == "USER":
        ordered = sorted(
            qualified,
            key=lambda item: (
                -item[2],
                -item[1]["longest_green"],
                -len(item[1]["days"]),
                str(item[0]),
            ),
        )
        entries = [
            LeaderboardEntry(
                rank=index + 1,
                public_alias=item[1]["profile"].public_alias,
                posture_score=round(item[2], 2),
                challenge_points=item[1]["points"],
                valid_days=len(item[1]["days"]),
                valid_minutes=item[3] // 60,
                is_current_user=item[0] == current_user.id,
            )
            for index, item in enumerate(ordered)
        ]
    else:
        by_school = defaultdict(list)
        for item in qualified:
            if item[1]["profile"].school_id:
                by_school[item[1]["profile"].school_id].append(item)
        school_rows = []
        for current_school_id, participants in by_school.items():
            if len(participants) < SCHOOL_MINIMUM_PARTICIPANTS:
                continue
            school_name = await session.scalar(select(School.localized_name).where(School.id == current_school_id))
            school_rows.append(
                (
                    current_school_id,
                    school_name or "Verified school",
                    statistics.median(item[2] for item in participants),
                    sum(item[1]["points"] for item in participants),
                    sum(item[3] for item in participants),
                    len(set().union(*(item[1]["days"] for item in participants))),
                    len(participants),
                    max(item[1]["longest_green"] for item in participants),
                )
            )
        ordered = sorted(school_rows, key=lambda item: (-item[2], -item[7], -item[5], str(item[0])))
        entries = [
            LeaderboardEntry(
                rank=index + 1,
                public_alias=item[1],
                posture_score=round(item[2], 2),
                challenge_points=item[3],
                valid_days=item[5],
                valid_minutes=item[4] // 60,
                participant_count=item[6],
                is_current_user=bool(current_profile and item[0] == current_profile.school_id),
            )
            for index, item in enumerate(ordered)
        ]
    current_qualified = any(item[0] == current_user.id for item in qualified)
    current_index = next((index for index, entry in enumerate(entries) if entry.is_current_user), None)
    current_window = [] if current_index is None else entries[max(0, current_index - 2) : current_index + 3]
    return CampusLeaderboardResponse(
        entity_type=entity_type,
        scope_type=scope_type,
        period=period,
        entries=entries[offset : offset + limit],
        current_user_window=current_window,
        current_user_qualified=current_qualified,
        generated_at_ms=int(time.time() * 1000),
    )


@router.get("/insights/comparison", response_model=GuardInsightResponse)
async def guard_comparison(
    from_date: date,
    to_date: date,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_db_session),
) -> GuardInsightResponse:
    if to_date < from_date or (to_date - from_date).days > 366:
        raise HTTPException(status_code=422, detail="Invalid comparison date range")
    rows = list(
        (
            await session.execute(
                select(PostureAggregate).where(
                    PostureAggregate.user_id == current_user.id,
                    PostureAggregate.record_date.between(from_date, to_date),
                )
            )
        ).scalars()
    )
    totals = defaultdict(lambda: defaultdict(int))
    for row in rows:
        for field in ("green_seconds", "yellow_seconds", "red_seconds", "reminder_count", "successful_corrections", "recovery_seconds_total"):
            totals[row.mode][field] += getattr(row, field)

    def bad_ratio(mode: str) -> float | None:
        values = totals[mode]
        valid = values["green_seconds"] + values["yellow_seconds"] + values["red_seconds"]
        return None if valid == 0 else (values["yellow_seconds"] + values["red_seconds"]) / valid

    observation_valid = sum(totals["OBSERVATION"][key] for key in ("green_seconds", "yellow_seconds", "red_seconds"))
    guarding_valid = sum(totals["GUARDING"][key] for key in ("green_seconds", "yellow_seconds", "red_seconds"))
    observation_bad = bad_ratio("OBSERVATION")
    guarding_bad = bad_ratio("GUARDING")
    comparable = observation_valid >= COMPARISON_MINIMUM_SECONDS and guarding_valid >= COMPARISON_MINIMUM_SECONDS
    reminders = totals["GUARDING"]["reminder_count"]
    corrections = totals["GUARDING"]["successful_corrections"]
    return GuardInsightResponse(
        observation_bad_ratio=observation_bad,
        guarding_bad_ratio=guarding_bad,
        absolute_change_percentage_points=(guarding_bad - observation_bad) * 100 if comparable and observation_bad is not None and guarding_bad is not None else None,
        relative_improvement_percent=(observation_bad - guarding_bad) / observation_bad * 100 if comparable and observation_bad and guarding_bad is not None else None,
        reminder_correction_rate=corrections / reminders if reminders else None,
        average_recovery_seconds=totals["GUARDING"]["recovery_seconds_total"] / corrections if corrections else None,
        is_comparable=comparable,
    )
