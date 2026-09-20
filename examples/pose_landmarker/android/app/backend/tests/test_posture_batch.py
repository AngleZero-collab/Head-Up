import unittest
from datetime import datetime, timedelta, timezone

from fastapi import HTTPException
from pydantic import ValidationError
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from starlette.requests import Request

from app.api.v1.posture import router, upload_posture_batch
from app.database import Base
from app.dependencies import get_current_user, oauth2_scheme
from app.models import PostureRecord, User
from app.schemas import BatchPostureUpload, PostureRecordCreate


class PostureBatchSchemaTest(unittest.TestCase):
    @staticmethod
    def record(timestamp: datetime, ratio: float = 0.75) -> PostureRecordCreate:
        return PostureRecordCreate(
            timestamp=timestamp,
            parallax_cosine_ratio=ratio,
            angular_velocity=1.25,
            is_stable=True,
        )

    def test_timestamp_requires_timezone_and_is_normalized_to_utc(self):
        with self.assertRaises(ValidationError):
            self.record(datetime(2026, 9, 20, 10, 0))

        record = self.record(datetime(2026, 9, 20, 18, 0, tzinfo=timezone(timedelta(hours=8))))
        self.assertEqual(record.timestamp, datetime(2026, 9, 20, 10, 0, tzinfo=timezone.utc))

    def test_non_finite_values_are_rejected(self):
        with self.assertRaises(ValidationError):
            self.record(datetime.now(timezone.utc), ratio=float("nan"))

        with self.assertRaises(ValidationError):
            PostureRecordCreate(
                timestamp=datetime.now(timezone.utc),
                parallax_cosine_ratio=0.5,
                angular_velocity=float("inf"),
                is_stable=False,
            )

    def test_batch_accepts_empty_records_and_rejects_more_than_one_thousand(self):
        self.assertEqual(BatchPostureUpload(records=[]).records, [])
        record = self.record(datetime.now(timezone.utc))
        with self.assertRaises(ValidationError):
            BatchPostureUpload(records=[record] * 1001)

    def test_route_depends_on_current_user(self):
        route = next(route for route in router.routes if route.path == "/posture/batch")
        dependency_calls = {dependency.call for dependency in route.dependant.dependencies}
        self.assertIn(get_current_user, dependency_calls)


class PostureBatchAuthenticationTest(unittest.IsolatedAsyncioTestCase):
    async def test_missing_bearer_token_is_rejected(self):
        request = Request({"type": "http", "method": "POST", "path": "/", "headers": []})
        with self.assertRaises(HTTPException) as context:
            await oauth2_scheme(request)
        self.assertEqual(context.exception.status_code, 401)


class PostureBatchIntegrationTest(unittest.IsolatedAsyncioTestCase):
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
            email=f"posture-user-{index}@example.test",
            display_name=f"Posture User {index}",
            hashed_password="not-used-in-this-test",
            subscription_tier="individual",
            role="user",
        )

    @staticmethod
    def batch(*records: PostureRecordCreate) -> BatchPostureUpload:
        return BatchPostureUpload(records=list(records))

    @staticmethod
    def record(
        timestamp: datetime,
        *,
        ratio: float,
        velocity: float = 1.0,
        stable: bool = True,
    ) -> PostureRecordCreate:
        return PostureRecordCreate(
            timestamp=timestamp,
            parallax_cosine_ratio=ratio,
            angular_velocity=velocity,
            is_stable=stable,
        )

    async def test_insert_retry_and_same_batch_duplicates_are_idempotent(self):
        timestamp = datetime(2026, 9, 20, 10, 0, tzinfo=timezone.utc)
        async with self.sessions() as session:
            user = self.user(1)
            session.add(user)
            await session.commit()

            first = await upload_posture_batch(
                self.batch(self.record(timestamp, ratio=0.1)),
                session,
                user,
            )
            second = await upload_posture_batch(
                self.batch(
                    self.record(timestamp, ratio=0.2),
                    self.record(timestamp, ratio=0.9, velocity=3.5, stable=False),
                ),
                session,
                user,
            )

            self.assertEqual(first.synced_count, 1)
            self.assertEqual(second.synced_count, 1)
            self.assertEqual(await session.scalar(select(func.count(PostureRecord.id))), 1)
            stored = await session.scalar(select(PostureRecord))
            self.assertEqual(stored.parallax_cosine_ratio, 0.9)
            self.assertEqual(stored.angular_velocity, 3.5)
            self.assertFalse(stored.is_stable)

    async def test_equivalent_offsets_collide_but_different_users_are_isolated(self):
        utc_timestamp = datetime(2026, 9, 20, 10, 0, tzinfo=timezone.utc)
        taipei_timestamp = datetime(
            2026,
            9,
            20,
            18,
            0,
            tzinfo=timezone(timedelta(hours=8)),
        )
        async with self.sessions() as session:
            first_user = self.user(1)
            second_user = self.user(2)
            session.add_all([first_user, second_user])
            await session.commit()

            await upload_posture_batch(
                self.batch(self.record(utc_timestamp, ratio=0.1)),
                session,
                first_user,
            )
            await upload_posture_batch(
                self.batch(self.record(taipei_timestamp, ratio=0.7)),
                session,
                first_user,
            )
            await upload_posture_batch(
                self.batch(self.record(utc_timestamp, ratio=0.3)),
                session,
                second_user,
            )

            self.assertEqual(await session.scalar(select(func.count(PostureRecord.id))), 2)
            first_stored = await session.scalar(
                select(PostureRecord).where(PostureRecord.patient_id == first_user.id)
            )
            self.assertEqual(first_stored.parallax_cosine_ratio, 0.7)

    async def test_empty_batch_does_not_write(self):
        async with self.sessions() as session:
            user = self.user(1)
            session.add(user)
            await session.commit()

            response = await upload_posture_batch(BatchPostureUpload(records=[]), session, user)

            self.assertEqual(response.synced_count, 0)
            self.assertEqual(await session.scalar(select(func.count(PostureRecord.id))), 0)
