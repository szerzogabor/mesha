# Mobile Release Process

How a new Android version goes from code to users' devices.

## Automated (current): CI publishes on every push to `main`

The `publish-mobile-release` job in [`ci.yml`](../../../.github/workflows/ci.yml) runs on
every push to `main` that touches `platform/mobile/**`:

1. Assembles a **debug-signed** APK (`:app:assembleDebug`) using the keystore committed
   at `platform/mobile/mesha-debug.keystore`, so every published build — CI or local —
   shares one signing certificate. This is required for the in-app updater to work at
   all: Android refuses to install a new build as an "update" over an existing install
   unless the signature matches, otherwise every update requires a full uninstall (which
   also wipes the Clerk login session and any downloaded on-device model). No release
   keystore/R8 minification yet, so debug signing itself is intentional for now, not an
   oversight — a separate concern from signing *consistency*, which the committed
   keystore solves regardless of build type.
2. Sets `versionCode` to `github.run_number` (monotonic, auto-incrementing) and
   `versionName` to `0.1.<run_number>` via the `-Pmesha.versionCode` /
   `-Pmesha.versionName` Gradle properties (see `defaultConfig` in `app/build.gradle.kts`).
3. Publishes the APK as a **GitHub Release asset** (tag `android-<run_number>`) and
   computes its SHA-256/size in CI.
4. POSTs the release **metadata** (not the APK bytes) to `POST /api/releases` against the
   production API (`https://mesha-api.onrender.com`), authenticated with a static CI
   token — `Authorization: Bearer relpub_<token>` — read from the
   `APP_RELEASES_UPLOAD_TOKEN` GitHub Actions secret (validated by
   `ReleaseUploadTokenAuthenticationFilter`, granting `ROLE_CI_RELEASE_PUBLISHER`; the
   same endpoint platform admins can also call manually, see
   [`APK_DISTRIBUTION.md`](./APK_DISTRIBUTION.md)). The build is published immediately
   (`published=true` default). The backend never buffers the APK bytes — it can't, on its
   memory budget — so the binary lives entirely on GitHub.

No human action is required for a normal release: merging to `main` is the release.

### Signing key

The persistent CI/dev debug keystore is committed at
`platform/mobile/mesha-debug.keystore` (alias `mesha-ci-debug`) and read by the
`signingConfigs.debug` block in `app/build.gradle.kts`, so both CI and local
`assembleDebug` builds sign identically with zero setup. It's a debug-only convenience
key, not a Play Store release key, so committing it is intentional rather than routing it
through GitHub Actions secrets. **Never regenerate it** — doing so breaks in-place updates
for everyone already on the previous key, forcing one more uninstall. If it ever needs
regenerating anyway:
```bash
cd platform/mobile
keytool -genkeypair -v -keystore mesha-debug.keystore -alias mesha-ci-debug \
  -keyalg RSA -keysize 2048 -validity 10950 -storepass <password> -keypass <password>
```
then update the hardcoded fallback password/alias in `app/build.gradle.kts` to match and
commit the new keystore file.

`app/build.gradle.kts` also honors `MESHA_DEBUG_KEYSTORE_PATH` /
`MESHA_DEBUG_KEYSTORE_PASSWORD` / `MESHA_DEBUG_KEY_ALIAS` / `MESHA_DEBUG_KEY_PASSWORD` env
vars as an override, in case this ever migrates to a GitHub Actions secret-backed keystore
(e.g. for a real Play Store release key) without touching build config.

### One-time setup (manual, not done by CI)

- `APP_RELEASES_UPLOAD_TOKEN` — a `relpub_`-prefixed secret, set as both a GitHub Actions
  secret and the `APP_RELEASES_UPLOAD_TOKEN` Render env var (already scaffolded in
  `render.yaml` with `sync: false`).
- `MOBILE_CLERK_PUBLISHABLE_KEY_DEBUG` — the Clerk **test** publishable key
  (`pk_test_...`), set as a GitHub Actions secret so `validate-mobile` and
  `publish-mobile-release` can compile against a real Clerk environment.

### One-time transition for existing installs

Anyone with the app already installed from before the stable keystore was introduced has
a different signing certificate on-device and needs **one last manual uninstall +
reinstall** to get onto the new lineage. Every update after that installs in place —
login and any downloaded on-device model survive automatically, since nothing needs to
be uninstalled anymore.

### Switching to a signed release build later

When a real upload keystore exists, swap `:app:assembleDebug` for `:app:assembleRelease`
in both mobile CI jobs, configure a `signingConfig` reading the keystore from a CI secret
(never commit keystores), and point `-Pmesha.api.baseUrl`/`-Pmesha.clerk.publishableKey`
at the release variant's properties instead of the `.debug` ones. Note the `release` build
type has minification/resource-shrinking enabled (`isMinifyEnabled = true`) — it has never
been built or tested, so this needs a full regression pass (login, model
download/inference, voice input) before it can be trusted.

## Manual publish (e.g. backfilling a build, or off the automated path)

As a platform admin (email in `PLATFORM_ADMIN_EMAILS`) or with the CI token, upload the
APK to a GitHub Release yourself first, then publish its metadata — see
[`APK_DISTRIBUTION.md`](./APK_DISTRIBUTION.md#publishing-a-release-curl) for the full curl
example. The server enforces a unique `version_code` and immediately makes the build the
"latest" for `GET /api/releases/android/latest`; it does not compute the checksum itself
since it never receives the file — pass `checksumSha256` yourself (e.g.
`sha256sum app-release.apk`).

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
