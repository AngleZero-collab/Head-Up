# Taiwan school catalog

Head Up accepts only CSV or JSON downloaded from an official Taiwan government open-data dataset. The importer stores the dataset URL and school-year version on every row and does not scrape search engines or community-maintained lists.

Official Ministry of Education catalog datasets:

- Elementary schools: `https://data.gov.tw/dataset/6087`
- Junior high schools: `https://data.gov.tw/dataset/6088`
- Senior high schools: `https://data.gov.tw/dataset/6089`
- Colleges and universities: `https://data.gov.tw/dataset/6091`

Example:

```powershell
python scripts/import_taiwan_schools.py C:\data\elementary-114.csv `
  --stage ELEMENTARY `
  --source https://data.gov.tw/dataset/6087 `
  --version 114-school-year
```

Run one import for each education stage. Closed schools must be reviewed and marked `CLOSED` in the catalog before production publication. An unverified manually entered school must never be inserted through this importer or included in ranking scopes.
