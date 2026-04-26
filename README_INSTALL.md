Installing and running the debug APK (BlueStacks)
===============================================

This repository produces a debug APK you can install into BlueStacks.

Options
1) Use GitHub Actions (no local Android SDK required)
   - On GitHub: Actions  Android CI  Run workflow (or wait for a push)
   - After the run completes: open the run  Artifacts  download `app-debug-apk`
   - On your machine run:
     adb connect 127.0.0.1:5555
     adb install -r <path-to-downloaded>/app-debug.apk

2) Build locally and install (requires Android SDK & build-tools)
   - Ensure Android SDK with platform 34 and build-tools 34.0.0 is installed
     (use `sdkmanager` and accept licenses).
   - From repo root:
     .\gradlew.bat :app:assembleDebug
     adb connect 127.0.0.1:5555
     adb install -r .\app\build\outputs\apk\debug\app-debug.apk

PowerShell helper
------------------
Use the provided script `scripts\install-apk-bluestacks.ps1` to install the locally-built APK to BlueStacks. The script will:
- verify `adb` is available
- attempt to connect to BlueStacks on 127.0.0.1:5555
- install the debug APK if present at `app\build\outputs\apk\debug\app-debug.apk`

If the APK is not present, build first with Gradle or download the artifact from Actions.
