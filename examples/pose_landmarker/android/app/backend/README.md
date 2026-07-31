# HeadUp FastAPI Backend

Minimal async FastAPI service for de-identified posture summary sync.

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
`POST /api/v1/records/sync` accepts de-identified daily posture summaries.
