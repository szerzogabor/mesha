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
# Generate the Gradle wrapper jar if not present (requires a local Gradle):
gradle wrapper --gradle-version 8.9
# Build the debug APK (requires the Android SDK; set sdk.dir in local.properties)
./gradlew :app:assembleDebug
# Run JVM unit tests (no device needed)
./gradlew :app:testDebugUnitTest
```

Debug builds point at `http://10.0.2.2:8080` (host loopback from the emulator);
release builds point at `https://api.mesha.app`. Override via
`-Pmesha.api.baseUrl=` (see `app/build.gradle.kts`).

## Module layout

```
app/src/main/java/com/mesha/mobile/
├── MeshaApplication.kt / MainActivity.kt
├── di/                  # Hilt modules (network, database, providers)
├── data/
│   ├── remote/          # Retrofit MeshaApi, DTOs, AuthInterceptor
│   ├── local/           # Room (drafts), SecureTokenStore (Keystore)
│   ├── repository/      # Auth, Mesha (issues/projects/sessions), Draft, Selection
│   └── sync/            # DraftSyncWorker (offline queue drain)
├── domain/
│   ├── ai/              # LocalAiProvider, GemmaLocalAiProvider, IssueDraftParser
│   └── speech/          # SpeechInputProvider, AndroidSpeechInputProvider
├── update/              # UpdateChecker, ApkInstaller (in-app updates)
└── ui/                  # Compose theme, navigation, screens + ViewModels
```

Full documentation lives in [`../docs/mobile/`](../docs/mobile/).
