"""Import an official Taiwan Ministry of Education school catalog CSV.

Use only files downloaded from the government open-data datasets documented in
backend/SCHOOL_CATALOG.md. The importer requires an explicit source URL and
version so unverified scraped data cannot be presented as official catalog data.
"""

import argparse
import asyncio
import csv
import json
import re
import sys
from pathlib import Path

from sqlalchemy import select, update

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.database import AsyncSessionLocal, Base, engine  # noqa: E402
from app.models import School  # noqa: E402


def first(row: dict[str, object], *names: str) -> str:
    normalized = {
        str(key).strip().lower(): str(value or "").strip()
        for key, value in row.items()
    }
    for name in names:
        value = normalized.get(name.lower())
        if value:
            return value
    return ""


def without_code_prefix(value: str) -> str:
    return re.sub(r"^\[[^]]+\]", "", value).strip()


def district_from_address(address: str) -> str:
    normalized = without_code_prefix(address)
    match = re.search(r"(?:縣|市)([^縣市區鄉鎮]{1,6}(?:市|區|鄉|鎮))", normalized)
    return match.group(1) if match else ""


def latest_school_year_rows(rows: list[dict[str, object]]) -> list[dict[str, object]]:
    years = []
    for row in rows:
        value = first(row, "學年度", "school_year")
        if value.isdigit():
            years.append(int(value))
    if not years:
        return rows
    latest_year = max(years)
    return [row for row in rows if first(row, "學年度", "school_year") == str(latest_year)]


async def import_catalog(path: Path, stage: str, source: str, version: str) -> tuple[int, int]:
    if "data.gov.tw/dataset/" not in source:
        raise ValueError("source must be an official data.gov.tw dataset URL")
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        rows = json.load(handle) if path.suffix.lower() == ".json" else list(csv.DictReader(handle))
    rows = latest_school_year_rows(rows)
    inserted = updated = 0
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    async with AsyncSessionLocal() as session:
        await session.execute(
            update(School)
            .where(School.country_code == "TW", School.education_stage == stage)
            .values(active_status="INACTIVE")
        )
        for row in rows:
            code = first(row, "代碼", "學校代碼", "code", "school_code")
            name = first(row, "學校名稱", "學校中文名稱", "name", "school_name")
            region = without_code_prefix(first(row, "縣市名稱", "縣市", "所在地", "region"))
            address = first(row, "地址", "address")
            if not code or not name or not region:
                continue
            existing = await session.scalar(select(School).where(School.official_school_code == code))
            values = dict(
                country_code="TW",
                official_school_code=code,
                school_name=name,
                localized_name=name,
                education_stage=stage,
                region=region,
                district=first(row, "鄉鎮市區", "行政區", "district") or district_from_address(address),
                active_status="ACTIVE",
                source=source,
                source_version=version,
                verified=True,
            )
            if existing is None:
                session.add(School(**values))
                inserted += 1
            else:
                for key, value in values.items():
                    setattr(existing, key, value)
                updated += 1
        await session.commit()
    return inserted, updated


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("catalog", type=Path, help="Official CSV or JSON catalog file")
    parser.add_argument("--stage", required=True, choices=["ELEMENTARY", "JUNIOR_HIGH", "SENIOR_HIGH", "UNIVERSITY"])
    parser.add_argument("--source", required=True)
    parser.add_argument("--version", required=True, help="For example 114-school-year")
    args = parser.parse_args()
    inserted, updated = asyncio.run(import_catalog(args.catalog, args.stage, args.source, args.version))
    print(f"Imported {inserted} new schools and updated {updated} schools")


if __name__ == "__main__":
    main()
