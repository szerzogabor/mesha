# Mobile Release Process

How a new Android version goes from code to users' devices.

## 1. Bump versions

In `platform/mobile/app/build.gradle.kts`:

- increment `versionCode` (monotonic int — the updater compares this);
- set the user-facing `versionName` (semantic).

## 2. Build a signed release APK

```bash
cd platform/mobile
./gradlew :app:assembleRelease \
  -Pmesha.api.baseUrl=https://api.mesha.app/
```

Sign with the Mesha upload key (configure a `signingConfig` / keystore in CI; never commit
keystores). Release builds are minified + resource-shrunk (`proguard-rules.pro` keeps
serialization and MediaPipe classes).

## 3. Publish via the release-management API

As a platform admin (email in `PLATFORM_ADMIN_EMAILS`):

```bash
curl -X POST "$API/api/releases" \
  -H "Authorization: Bearer $CLERK_JWT" \
  -F "file=@app/build/outputs/apk/release/app-release.apk" \
  -F "versionName=1.2.0" \
  -F "versionCode=5" \
  -F "releaseNotes=$(cat RELEASE_NOTES.md)" \
  -F "published=true"
```

The server computes and stores the SHA-256, enforces a unique `version_code`, and
immediately makes the build the "latest" for `GET /api/releases/android/latest`.

## 4. Verify rollout

- Web: open `/download` — the new version, size, checksum and notes should appear.
- App: existing installs hit `GET /api/releases/android/latest` on launch /
  **Settings → Check for updates**; when `versionCode` is higher they are prompted to
  **Download & install** (handled by `ApkInstaller` + the system package installer).

## Rollback

Unpublish a bad build so clients fall back to the previous latest:

```bash
curl -X PATCH "$API/api/releases/$RELEASE_ID/published?published=false" \
  -H "Authorization: Bearer $CLERK_JWT"
```

## Keep release notes in sync

The web `/download` page also shows `RELEASE_NOTES` from
`platform/frontend/src/lib/app-version.ts` for the PWA; for native releases the canonical
notes are the `releaseNotes` field uploaded with the APK.
