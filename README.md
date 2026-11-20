# Keeps – Notes & Moments (Android)

This Android app packages the same HTML app used by the project’s browser version. The screen and its interactions are served from the APK’s bundled assets on Android’s secure local app-assets origin. This keeps the visual layout and app behavior aligned with the existing web app.

The Android wrapper provides local file selection for attachments and requests microphone access when voice recording is used. App data is stored in the WebView’s private browser storage on this device; it does not automatically sync with Chrome or another browser installation.

## Build

Open this folder in Android Studio, or run:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
