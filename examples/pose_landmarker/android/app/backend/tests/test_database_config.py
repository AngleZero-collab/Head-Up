import unittest

from app.config import Settings
from app.database import normalize_async_database_url


class DatabaseConfigurationTest(unittest.TestCase):
    def test_normalizes_common_postgresql_urls_to_asyncpg(self):
        self.assertEqual(
            normalize_async_database_url("postgresql://user:password@db.example.com/headup"),
            "postgresql+asyncpg://user:password@db.example.com/headup",
        )
        self.assertEqual(
            normalize_async_database_url("postgres://user:password@db.example.com/headup"),
            "postgresql+asyncpg://user:password@db.example.com/headup",
        )

    def test_preserves_explicit_asyncpg_and_sqlite_urls(self):
        asyncpg_url = "postgresql+asyncpg://user:password@db.example.com/headup"
        sqlite_url = "sqlite+aiosqlite:///./headup-dev.db"
        self.assertEqual(normalize_async_database_url(asyncpg_url), asyncpg_url)
        self.assertEqual(normalize_async_database_url(sqlite_url), sqlite_url)

    def test_allowed_origins_are_read_from_comma_separated_setting(self):
        settings = Settings(
            DATABASE_URL="sqlite+aiosqlite:///:memory:",
            SECRET_KEY="test-only-secret",
            ALLOWED_ORIGINS="https://app.example.com, https://admin.example.com",
        )
        self.assertEqual(
            settings.cors_origins,
            ["https://app.example.com", "https://admin.example.com"],
        )
