# Windows setup

1. Install **Android Studio** (latest stable) from developer.android.com/studio. It brings JDK 17+ (JetBrains
   Runtime) and the Android SDK. In SDK Manager make sure **Android SDK Platform 35** and **Build-Tools** are there.
2. Get the code: `git clone https://github.com/asdf968566-art/song-by-gullu.git` (or GitHub Desktop).
3. Android Studio → **Open** → choose the `DXScreenMonitor` folder (not the repo root). Wait for Gradle sync.
4. Build: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. The APK is in
   `DXScreenMonitor\app\build\outputs\apk\debug\app-debug.apk`.
   Command line instead: `cd DXScreenMonitor` then `gradlew.bat assembleDebug`.
5. Install on the phone: connect the Pixel by USB with USB debugging on, press **Run ▶**, or
   `adb install -r app\build\outputs\apk\debug\app-debug.apk` (adb is in
   `%LOCALAPPDATA%\Android\Sdk\platform-tools`).

No Android Studio? Download the tested APK:
https://github.com/asdf968566-art/song-by-gullu/releases/download/dx-screen-monitor/DXScreenMonitor.apk

Watching from the PC: the PC and the phone must be on the same Wi-Fi. Open the address shown in the app in Chrome
or Edge. If it doesn't load, allow the network as "Private" in Windows and check the router doesn't isolate
clients ("AP isolation" / "guest network").
