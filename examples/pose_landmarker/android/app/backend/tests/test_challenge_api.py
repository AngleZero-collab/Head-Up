import unittest
import uuid
from datetime import date, timedelta

from fastapi import HTTPException
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from app.api.v1.challenge import get_leaderboard, period_dates, upload_posture_aggregates
from app.database import Base
from app.models import EducationProfile, PostureAggregate, School, User
from app.schemas import PostureAggregateBatch, PostureAggregateUpload


class ChallengeApiIntegrationTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.engine = create_async_engine("sqlite+aiosqlite:///:memory:")
        self.sessions = async_sessionmaker(self.engine, expire_on_commit=False)
        async with self.engine.begin() as connection:
            await connection.run_sync(Base.metadata.create_all)

    async def asyncTearDown(self):
        await self.engine.dispose()

    @staticmethod
    def user(index: int) -> User:
        return User(
            email=f"student{index}@example.test",
            display_name=f"Student {index}",
            hashed_password="not-used-in-this-test",
            subscription_tier="individual",
            role="user",
        )

    async def test_aggregate_upload_is_idempotent_and_server_recalculates_score(self):
        async with self.sessions() as session:
            user = self.user(1)
            session.add(user)
            await session.commit()
            payload = PostureAggregateBatch(
                aggregates=[
                    PostureAggregateUpload(
                        aggregate_id="local-user|2026-09-03|GUARDING",
                        record_date=date(2026, 9, 3),
                        mode="GUARDING",
                        green_seconds=60,
                        yellow_seconds=30,
                        red_seconds=10,
                        unknown_seconds=5,
                        raw_points=99999,
                        challenge_points=99999,
                        longest_green_streak_seconds=60,
                        green_streak_count=1,
                        green_streak_total_seconds=60,
                        reminder_count=1,
                        successful_corrections=1,
                        recovery_seconds_total=8,
                        scoring_version=1,
                        idempotency_key="integration-idempotency-key-001",
                    )
                ]
            )

            first = await upload_posture_aggregates(payload, user, session)
            second = await upload_posture_aggregates(payload, user, session)

            self.assertEqual(first.inserted, 1)
            self.assertEqual(second.inserted, 0)
            self.assertEqual(await session.scalar(select(func.count(PostureAggregate.id))), 1)
            stored = await session.scalar(select(PostureAggregate))
            self.assertAlmostEqual(stored.posture_score, 75.0)
            self.assertNotEqual(stored.challenge_points, 99999)

    async def test_rankings_require_consent_and_include_current_user_neighborhood(self):
        last_week_start, _ = period_dates("LAST_WEEK")
        async with self.sessions() as session:
            school = School(
                country_code="TW",
                official_school_code="TEST001",
                school_name="Integration School",
                localized_name="整合測試學校",
                education_stage="JUNIOR_HIGH",
                region="臺北市",
                district="中正區",
                active_status="ACTIVE",
                source="https://data.gov.tw/dataset/6088",
                source_version="test",
                verified=True,
            )
            users = [self.user(index) for index in range(6)]
            session.add_all([school, *users])
            await session.flush()
            for index, user in enumerate(users):
                session.add(
                    EducationProfile(
                        user_id=user.id,
                        country_code="TW",
                        school_id=school.id,
                        grade_code="JH1",
                        education_stage="JUNIOR_HIGH",
                        public_alias=f"Learner {index}",
                        leaderboard_opt_in=index < 5,
                        parent_consent_status="GRANTED" if index < 5 else "REVOKED",
                    )
                )
                for day_offset in range(3):
                    green = 600 - index * 50
                    yellow = index * 25
                    red = index * 25
                    session.add(
                        PostureAggregate(
                            user_id=user.id,
                            client_aggregate_id=f"{user.id}-{day_offset}",
                            record_date=last_week_start + timedelta(days=day_offset),
                            mode="GUARDING",
                            green_seconds=green,
                            yellow_seconds=yellow,
                            red_seconds=red,
                            unknown_seconds=0,
                            raw_points=100,
                            challenge_points=100 - index,
                            posture_score=None,
                            longest_green_streak_seconds=300 - index,
                            green_streak_count=1,
                            green_streak_total_seconds=green,
                            reminder_count=0,
                            successful_corrections=0,
                            recovery_seconds_total=0,
                            scoring_version=1,
                            idempotency_key=f"rank-{user.id}-{day_offset}",
                        )
                    )
            await session.commit()

            user_board = await get_leaderboard(
                entity_type="USER",
                scope_type="SCHOOL",
                period="LAST_WEEK",
                school_id=school.id,
                grade_code=None,
                education_stage=None,
                limit=1,
                offset=0,
                current_user=users[4],
                session=session,
            )
            school_board = await get_leaderboard(
                entity_type="SCHOOL",
                scope_type="EDUCATION_STAGE",
                period="LAST_WEEK",
                school_id=None,
                grade_code=None,
                education_stage="JUNIOR_HIGH",
                limit=50,
                offset=0,
                current_user=users[4],
                session=session,
            )

            self.assertEqual(len(user_board.entries), 1)
            self.assertEqual([entry.rank for entry in user_board.current_user_window], [3, 4, 5])
            self.assertTrue(user_board.current_user_window[-1].is_current_user)
            self.assertNotIn("Learner 5", {entry.public_alias for entry in user_board.current_user_window})
            self.assertEqual(len(school_board.entries), 1)
            self.assertEqual(school_board.entries[0].participant_count, 5)
            self.assertEqual(school_board.entries[0].valid_days, 3)

    async def test_school_scope_without_profile_or_school_is_rejected(self):
        async with self.sessions() as session:
            user = self.user(9)
            session.add(user)
            await session.commit()
            with self.assertRaises(HTTPException) as error:
                await get_leaderboard(
                    entity_type="USER",
                    scope_type="SCHOOL",
                    period="LAST_WEEK",
                    school_id=None,
                    grade_code=None,
                    education_stage=None,
                    limit=50,
                    offset=0,
                    current_user=user,
                    session=session,
                )
            self.assertEqual(error.exception.status_code, 422)


if __name__ == "__main__":
    unittest.main()
