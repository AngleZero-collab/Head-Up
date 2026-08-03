# Head Up FastAPI Backend

Minimal async FastAPI service for central Head Up user accounts and de-identified daily report sync.

## Run Locally

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
uvicorn app.main:app --reload
```

For Android Emulator login testing, use the included helper:

```powershell
.\start_dev_backend.ps1
```

The default local `.env.example` uses SQLite so login/register can work before PostgreSQL is installed. The Android debug build calls `http://10.0.2.2:8000/`, which maps the emulator to this backend running on your Windows machine.

If you are testing on a real Android phone or a non-Android-Studio emulator, `10.0.2.2` will usually not work. Add your Windows machine IP to `examples/pose_landmarker/android/app/local.properties`:

```properties
headupApiBaseUrl=http://YOUR_WINDOWS_IP:8000/
```

For example, if `ipconfig` shows your Wi-Fi IPv4 address is `172.20.10.5`, use:

```properties
headupApiBaseUrl=http://172.20.10.5:8000/
```

For a production PostgreSQL deployment, install the PostgreSQL driver as well:

```powershell
pip install -r requirements-postgres.txt
```

## API

`POST /api/v1/auth/register` creates an account.
`POST /api/v1/auth/login` returns a 30 minute JWT token.
`POST /api/v1/auth/guest` creates or reuses a de-identified backend guest account for one device.
`GET /api/v1/auth/me` returns the current authenticated account.
`GET /api/v1/users` lists all registered and guest accounts for admin users.
`POST /api/v1/reports/sync` accepts authenticated daily Head Up reports and stores them in the database.
`GET /api/v1/reports` lists all synced posture reports for admin users.
`POST /api/v1/records/sync` remains as a legacy compatibility endpoint.

Daily reports are tied to the authenticated JWT user on the server. The Android client does not send or choose `user_id`.

## Admin Account

Set these values in `.env` before starting the API:

```env
ADMIN_EMAIL=admin@headup.local
ADMIN_PASSWORD=change-this-admin-password
ADMIN_SUBSCRIPTION_TIER=admin
```

On startup, the backend creates this account if it does not exist. If the email already exists, that account is upgraded to `role=admin`.

## Viewing All User Data

Open `http://127.0.0.1:8000/dashboard` to use the built-in chart dashboard. Log in with the seeded admin account, then review the user distribution chart, seven-day slouch trend, user table, and report table.

Open `http://127.0.0.1:8000/download` to download the current debug demo APK from the backend website. If other phones are on the same Wi-Fi or hotspot, use your Windows IP instead, for example `http://172.20.10.5:8000/download`.

Open `http://127.0.0.1:8000/docs`, call `POST /api/v1/auth/login` with the seeded admin account, click **Authorize**, paste the JWT as `Bearer <token>`, then call:

- `GET /api/v1/users` to see every registered and guest user.
- `GET /api/v1/reports` to see all synced posture reports.

Guest users appear with `role = guest` and an email like `guest-xxxxxxxx@guest.headup.local`.

## APK Distribution

For demos, run `:app:assembleDebug`, keep the backend running, and share:

```text
http://YOUR_WINDOWS_IP:8000/download
```

For public release, create a signed release APK or Android App Bundle. Do not commit keystores or signing passwords to Git.

For a public website deployment, see `DEPLOYMENT.md`.
