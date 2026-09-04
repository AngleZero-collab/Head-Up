import unittest
from datetime import date
from types import SimpleNamespace

from pydantic import ValidationError

from app.api.v1.challenge import period_dates, posture_score, server_challenge_points
from app.schemas import PostureAggregateUpload
from scripts.import_taiwan_schools import latest_school_year_rows


class ChallengeDomainTest(unittest.TestCase):
    def test_posture_score_formula(self):
        self.assertEqual(posture_score(60, 0, 0), 100.0)
        self.assertEqual(posture_score(0, 60, 0), 50.0)
        self.assertEqual(posture_score(0, 0, 60), 0.0)
        self.assertIsNone(posture_score(0, 0, 0))

    def test_server_points_apply_combo_and_daily_cap(self):
        payload = SimpleNamespace(
            green_seconds=7200,
            yellow_seconds=0,
            red_seconds=0,
            longest_green_streak_seconds=301,
        )
        self.assertEqual(server_challenge_points(payload), 7200)

    def test_period_dates_are_monday_based(self):
        start, end = period_dates("THIS_WEEK", date(2026, 9, 3))
        self.assertEqual(start, date(2026, 8, 31))
        self.assertEqual(end, date(2026, 9, 3))

    def test_observation_cannot_upload_reminders(self):
        with self.assertRaises(ValidationError):
            PostureAggregateUpload(
                aggregate_id="user|2026-09-03|OBSERVATION",
                record_date="2026-09-03",
                mode="OBSERVATION",
                green_seconds=100,
                yellow_seconds=0,
                red_seconds=0,
                unknown_seconds=0,
                raw_points=100,
                challenge_points=100,
                longest_green_streak_seconds=100,
                green_streak_count=1,
                green_streak_total_seconds=100,
                reminder_count=1,
                successful_corrections=0,
                recovery_seconds_total=0,
                scoring_version=1,
                idempotency_key="observation-reminder-invalid",
            )

    def test_school_import_only_keeps_latest_academic_year(self):
        rows = [
            {"學年度": 113, "代碼": "OLD"},
            {"學年度": "115", "代碼": "CURRENT"},
            {"學年度": 114, "代碼": "OLDER"},
        ]
        self.assertEqual(latest_school_year_rows(rows), [rows[1]])


if __name__ == "__main__":
    unittest.main()
