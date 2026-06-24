# Mobile Release Process

How a new Android version goes from code to users' devices.

## Automated (current): CI publishes on every push to `main`

The `publish-mobile-release` job in [`ci.yml`](../../../.github/workflows/ci.yml) runs on
every push to `main` that touches `platform/mobile/**`:

1. Assembles a **debug-signed** APK (`:app:assembleDebug`) — no release keystore exists
   yet, so debug signing is intentional for now, not an oversight.
2. Sets `versionCode` to `github.run_number` (monotonic, auto-incrementing) and
   `versionName` to `0.1.<run_number>` via the `-Pmesha.versionCode` /
   `-Pmesha.versionName` Gradle properties (see `defaultConfig` in `app/build.gradle.kts`).
3. Uploads the APK to `POST /api/releases` against the production API
   (`https://api.mesha.app`), authenticated with a static CI token —
   `Authorization: Bearer relpub_<token>` — read from the `APP_RELEASES_UPLOAD_TOKEN`
   GitHub Actions secret (validated by `ReleaseUploadTokenAuthenticationFilter`, granting
   `ROLE_CI_RELEASE_PUBLISHER`; the same endpoint platform admins can also call manually,
   see below). The build is published immediately (`published=true` default).

No human action is required for a normal release: merging to `main` is the release.

### One-time setup (manual, not done by CI)

- `APP_RELEASES_UPLOAD_TOKEN` — a `relpub_`-prefixed secret, set as both a GitHub Actions
  secret and the `APP_RELEASES_UPLOAD_TOKEN` Render env var (already scaffolded in
  `render.yaml` with `sync: false`).
- `MOBILE_CLERK_PUBLISHABLE_KEY_DEBUG` — the Clerk **test** publishable key
  (`pk_test_...`), set as a GitHub Actions secret so `validate-mobile` and
  `publish-mobile-release` can compile against a real Clerk environment.

### Switching to a signed release build later

When a real upload keystore exists, swap `:app:assembleDebug` for `:app:assembleRelease`
in both mobile CI jobs, configure a `signingConfig` reading the keystore from a CI secret
(never commit keystores), and point `-Pmesha.api.baseUrl`/`-Pmesha.clerk.publishableKey`
at the release variant's properties instead of the `.debug` ones.

## Manual publish (e.g. backfilling a build, or off the automated path)

As a platform admin (email in `PLATFORM_ADMIN_EMAILS`) or with the CI token:

```bash
curl -X POST "$API/api/releases" \
  -H "Authorization: Bearer $CLERK_JWT" \
  -F "file=@app/build/outputs/apk/debug/app-debug.apk" \
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
