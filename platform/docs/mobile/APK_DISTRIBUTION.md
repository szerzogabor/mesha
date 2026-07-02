# APK Distribution & Release Management

The platform serves metadata about the official Android APK; the binary itself is hosted
externally as a **GitHub Release asset**. Backend release management lives in
`backend-api` and is consumed by both the web `/download` page and the in-app updater.

The backend deliberately never touches the APK bytes: `backend-api` runs on a
memory-constrained instance that cannot buffer a ~100MB multipart upload or download
without OOMing. So the binary is uploaded to a GitHub Release by CI, and only a small
metadata record (including the resulting `download_url`) is POSTed to `/api/releases`.

## Data model

`app_releases` (migration `V49__app_releases.sql`, `download_url` added in
`V50__app_releases_external_url.sql`; entity `AppRelease`):

| Column | Notes |
|--------|-------|
| `platform` | `ANDROID` today (enum allows future clients) |
| `version_name` | human semantic version (e.g. `1.2.0`) |
| `version_code` | **monotonic int**; the app compares it against `BuildConfig.VERSION_CODE` |
| `release_notes` | shown on `/download` and Settings |
| `min_sdk` | default 33 |
| `download_url` | absolute URL to the hosted APK (a GitHub Release asset) |
| `content` | legacy inline APK bytes (`bytea`); nullable since V50, unused for new releases |
| `checksum_sha256` | computed by the uploader, surfaced for verification |
| `published` | unpublished releases are hidden from public endpoints |

Unique `(platform, version_code)` prevents duplicate releases.

## Endpoints (`AppReleaseController`, base `/api/releases`)

Public (registered `permitAll` in `SecurityConfig`):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/{platform}/latest` | latest published release metadata (drives updates + download page) |
| GET | `/{platform}` | published release history |

Admin-only (`@platformSecurity.isPlatformAdmin`):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/{platform}` | list incl. unpublished |
| POST | `/` | publish a new release's metadata (no APK bytes — see below) |
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
- `MAX_APK_SIZE_BYTES` (default 200 MB) — rejects an implausibly large `fileSize` at
  publish time; the backend never receives the bytes themselves.

## Publishing a release (curl)

Upload the APK to a GitHub Release yourself first (or let CI do it — see
`RELEASE_PROCESS.md`), then publish its metadata:

```bash
curl -X POST "$API/api/releases" \
  -H "Authorization: Bearer $CLERK_JWT" \
  -F "versionName=1.2.0" \
  -F "versionCode=5" \
  -F "fileName=app-release.apk" \
  -F "fileSize=$(stat -c%s app-release.apk)" \
  -F "checksumSha256=$(sha256sum app-release.apk | cut -d' ' -f1)" \
  -F "downloadUrl=https://github.com/szerzogabor/mesha/releases/download/android-1.2.0/app-release.apk" \
  -F "releaseNotes=Voice input + offline drafts" \
  -F "published=true"
```

## Download experience

The web `/download` page (`platform/frontend/src/app/download/page.tsx`) fetches
`/api/releases/android/latest` via `useLatestRelease` and renders a prominent **Download
APK** button (linking directly to the GitHub-hosted `downloadUrl`) with version, size,
checksum and install steps. The homepage CTA links here.
