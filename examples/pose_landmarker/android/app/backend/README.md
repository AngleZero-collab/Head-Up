# HeadUp FastAPI Backend

Minimal async FastAPI service for central HeadUp user accounts and de-identified daily report sync.

## Run locally

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload
```

`POST /api/v1/auth/register` creates an account.
`POST /api/v1/auth/login` returns a 30 minute JWT token.
`POST /api/v1/reports/sync` accepts authenticated daily HeadUp reports and stores them in PostgreSQL.
`POST /api/v1/records/sync` remains as a legacy compatibility endpoint.

Daily reports are tied to the authenticated JWT user on the server. The Android client does not send or choose `user_id`.
