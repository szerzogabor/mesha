# Mesha Mobile (Android)

The official native Android client for Mesha. Built with Kotlin, Jetpack Compose,
Hilt, Retrofit, Room and Coroutines (MVVM). Targets **Android 13+ (minSdk 33)**.

Its headline capability is **on-device AI issue creation**: a local Gemma model
(via Google AI Edge / MediaPipe) turns a natural-language description — typed or
spoken — into a structured issue draft, fully offline, with **no cloud AI provider
required**. Drafts are reviewed, then created through the existing Mesha REST API,
queuing locally when offline and syncing automatically when connectivity returns.

## Quick start

```bash
cd platform/mobile
# Set your Clerk publishable key (get one from the Clerk dashboard → API Keys),
# either via -P below or in a local (untracked) gradle.properties:
#   mesha.clerk.publishableKey.debug=pk_test_...
# Build the debug APK (requires the Android SDK; set sdk.dir in local.properties)
./gradlew :app:assembleDebug -Pmesha.clerk.publishableKey.debug=pk_test_...
# Run JVM unit tests (no device needed)
./gradlew :app:testDebugUnitTest
```

Debug builds point at `http://10.0.2.2:8080` (host loopback from the emulator);
release builds point at `https://mesha-api.onrender.com`. Override via
`-Pmesha.api.baseUrl=` (see `app/build.gradle.kts`).

Sign-in is handled by the [Clerk Android SDK](https://clerk.com/docs/android) — `AuthView`
renders the full hosted auth flow and the SDK persists the session itself, so a
publishable key is required for the app to start (`mesha.clerk.publishableKey` /
`.debug` in `gradle.properties`; CI supplies it as a secret).

## Module layout

```
app/src/main/java/com/mesha/mobile/
├── MeshaApplication.kt / MainActivity.kt
├── di/                  # Hilt modules (network, database, providers)
├── data/
│   ├── remote/          # Retrofit MeshaApi, DTOs, AuthInterceptor
│   ├── local/           # Room (drafts)
│   ├── repository/      # Auth, Mesha (issues/projects/sessions), Draft, Selection
│   └── sync/            # DraftSyncWorker (offline queue drain)
├── domain/
│   ├── ai/              # LocalAiProvider, GemmaLocalAiProvider, IssueDraftParser
│   └── speech/          # SpeechInputProvider, AndroidSpeechInputProvider
├── localai/            # Local AI model management (catalog, download, storage, repo, UI)
├── update/              # UpdateChecker, ApkInstaller (in-app updates)
└── ui/                  # Compose theme, navigation, screens + ViewModels
```

Full documentation lives in [`../docs/mobile/`](../docs/mobile/), including native on-device
model management ([`LOCAL_AI.md`](../docs/mobile/LOCAL_AI.md)) and how
every push to `main` builds and publishes a release ([`RELEASE_PROCESS.md`](../docs/mobile/RELEASE_PROCESS.md)).
If `publish-mobile-release` fails with a 401, check that `APP_RELEASES_UPLOAD_TOKEN`
matches byte-for-byte between the GitHub Actions secret and the Render env var, and
that `mesha-api` has redeployed since the env var was last saved.
