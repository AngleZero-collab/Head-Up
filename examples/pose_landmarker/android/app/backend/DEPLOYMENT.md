# Public Deployment

This backend can be published as one public website:

- `/dashboard` shows the admin chart dashboard.
- `/download` shows the APK download page.
- `/api/v1/...` serves the Android app and dashboard API.

## 1. Create A PostgreSQL Database

Use any managed PostgreSQL service. Copy the connection URL and convert it to SQLAlchemy async format:

```text
postgresql+asyncpg://USER:PASSWORD@HOST:PORT/DATABASE
```

Set that value as `DATABASE_URL`.

## 2. Deploy The Backend

Deploy `examples/pose_landmarker/android/app/backend` to a Python web host with either:

- Dockerfile: use `backend/Dockerfile`
- Procfile/start command: `uvicorn app.main:app --host 0.0.0.0 --port $PORT`

Set these environment variables in the hosting dashboard:

```env
DATABASE_URL=postgresql+asyncpg://USER:PASSWORD@HOST:PORT/DATABASE
SECRET_KEY=replace-with-a-long-random-secret
ACCESS_TOKEN_EXPIRE_MINUTES=30
ALLOWED_ORIGINS=https://YOUR_PUBLIC_DOMAIN
ADMIN_EMAIL=admin@headup.local
ADMIN_PASSWORD=replace-with-a-strong-admin-password
ADMIN_SUBSCRIPTION_TIER=admin
APK_DOWNLOAD_URL=https://github.com/YOUR_ACCOUNT/YOUR_REPO/releases/download/v1.0/Head Up.apk
```

`APK_DOWNLOAD_URL` is optional. When it is set, the backend download route redirects users to that hosted APK file. This is better than committing APK files into Git.

## 3. Publish The APK

For demo testing, build a debug APK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
..\gradlew.bat :app:assembleDebug --no-daemon
```

Upload the APK to GitHub Releases or another public file host, then set `APK_DOWNLOAD_URL`.

For a real public launch, build a signed release APK or Android App Bundle. Keep keystores and signing passwords outside Git.

## 4. Point Android To The Public API

Set `headupApiBaseUrl` in `examples/pose_landmarker/android/app/local.properties` before building:

```properties
headupApiBaseUrl=https://YOUR_PUBLIC_DOMAIN/
```

Then rebuild the APK. Users who install that APK will sync to the public backend instead of your local computer.

## 5. Public URLs

After deployment:

- `https://YOUR_PUBLIC_DOMAIN/dashboard`
- `https://YOUR_PUBLIC_DOMAIN/download`
- `https://YOUR_PUBLIC_DOMAIN/docs`

