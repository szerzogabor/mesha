# APK Distribution & Release Management

The platform hosts and serves the official Android APK. Backend release management lives
in `backend-api` and is consumed by both the web `/download` page and the in-app updater.

## Data model

`app_releases` (migration `V49__app_releases.sql`, entity `AppRelease`):

| Column | Notes |
|--------|-------|
| `platform` | `ANDROID` today (enum allows future clients) |
| `version_name` | human semantic version (e.g. `1.2.0`) |
| `version_code` | **monotonic int**; the app compares it against `BuildConfig.VERSION_CODE` |
| `release_notes` | shown on `/download` and Settings |
| `min_sdk` | default 33 |
| `content` | APK bytes (`bytea`, mirrors `issue_attachments`) |
| `checksum_sha256` | computed on upload, surfaced for verification |
| `published` | unpublished releases are hidden from public endpoints |

Unique `(platform, version_code)` prevents duplicate releases.

## Endpoints (`AppReleaseController`, base `/api/releases`)

Public (registered `permitAll` in `SecurityConfig`):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/{platform}/latest` | latest published release metadata (drives updates + download page) |
| GET | `/{platform}` | published release history |
| GET | `/{platform}/latest/download` | stream latest APK |
| GET | `/{releaseId}/download` | stream a specific APK (`attachment`, with `X-Checksum-SHA256`) |

Admin-only (`@platformSecurity.isPlatformAdmin`):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/{platform}` | list incl. unpublished |
| POST | `/` (multipart) | upload a new APK build |
| PATCH | `/{releaseId}/published` | publish/unpublish |
| DELETE | `/{releaseId}` | delete |

## Admin authorization

There is no platform-admin role in the workspace model, so platform admins are configured
out of band via `PLATFORM_ADMIN_EMAILS` (comma-separated). `PlatformSecurityService`
matches the authenticated user's verified email against that allow-list. Empty list ⇒
nobody can upload.

## Config

`application.yml` / env:

- `PLATFORM_ADMIN_EMAILS` — admins allowed to manage releases.
- `MAX_APK_SIZE_BYTES` (default 200 MB) and `spring.servlet.multipart.max-file-size`
  (`MAX_UPLOAD_FILE_SIZE`, default 200MB) — APK upload limits.

## Uploading a release (curl)

```bash
curl -X POST "$API/api/releases" \
  -H "Authorization: Bearer $CLERK_JWT" \
  -F "file=@app-release.apk" \
  -F "versionName=1.2.0" \
  -F "versionCode=5" \
  -F "releaseNotes=Voice input + offline drafts" \
  -F "published=true"
```

## Download experience

The web `/download` page (`platform/frontend/src/app/download/page.tsx`) fetches
`/api/releases/android/latest` via `useLatestRelease` and renders a prominent **Download
APK** button with version, size, checksum and install steps. The homepage CTA links here.
