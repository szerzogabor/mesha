# Installing Mesha Mobile

## For users

1. On your Android 13+ phone, open the Mesha site and go to **Download** (or tap
   *Download the App* on the homepage).
2. Tap **Download APK**.
3. The first time, Android asks to allow **Install unknown apps** for your browser —
   enable it.
4. Open the downloaded `.apk` and tap **Install**.
5. Launch Mesha and sign in.

### Enabling on-device AI (optional but recommended)

To create issues with the local Gemma model:

1. Open **Settings → On-device AI** in the app to see the target model folder.
2. Provide a compatible Gemma `.task` model by either installing/exporting it from the
   **Google AI Edge Gallery** app, or copying a `.task` file into that folder.
3. Tap **Refresh status** — once detected, *Create Issue with AI* is enabled.

Without a model, the app still works fully for browsing/creating issues, sessions and
agents; only local draft generation is disabled.

## For developers

Prerequisites: Android Studio (Koala+), Android SDK 35, JDK 17.

```bash
cd platform/mobile
gradle wrapper --gradle-version 8.9      # once, if gradlew jar absent
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug             # build debug APK
./gradlew :app:installDebug              # install to a connected device/emulator
./gradlew :app:testDebugUnitTest        # JVM unit tests
./gradlew :app:connectedDebugAndroidTest # instrumented + UI tests (device required)
```

Debug builds target `http://10.0.2.2:8080` (the emulator's view of host `localhost`),
matching the locally-running `backend-api`. Override with
`-Pmesha.api.baseUrl.debug=http://<host>:8080/`.
