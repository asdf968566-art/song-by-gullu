# DX Screen Monitor

Android app that shares a phone's live screen so the DX Department can watch its work phones.
Package `com.dxdepartment.screenmonitor`, Kotlin + Jetpack Compose, minSdk 26, target 35.
First target: one **Google Pixel 3 XL on Android 12**.

## Milestones

| # | Milestone | State |
|---|---|---|
| 1 | Android Studio project structure | Done |
| 2 | MediaProjection + foreground capture service | Done |
| 3 | Compiles without errors | Done in CI (`.github/workflows/dx-screen-monitor.yml`) |
| 4 | Real capture test on a Pixel 3 XL | Tested on an Android 12 emulator with the Pixel 3 XL screen (1440×2960, 560 dpi). **Needs a run on the real phone** (see `docs/PIXEL-TESTING.md`) |
| 5 | Installable APK | Done: release `dx-screen-monitor` |
| 6 | WebRTC internet streaming (WSS signalling, STUN/TURN, auth) | Not started |
| 7 | React web viewer | Not started (a built-in LAN viewer page exists for testing) |
| 8 | Multi-device dashboard | Not started |
| 9 | Load test, many devices | Not started |

## What works now

- **Screen capture**: `ScreenCaptureService` (foreground service, type `mediaProjection`) → `MediaProjection` →
  `VirtualDisplay` → `ImageReader` (`CaptureEngine`). The order Android 14 requires is followed (foreground first,
  then the projection, then `registerCallback`, then the virtual display), so it also works on newer phones.
- **1080p source**: the virtual display is made at the output size, so the GPU scales (1440×2960 → 1080×2220).
  Choices: 480p / 720p / 1080p / Native, 10 / 15 / 24 / 30 FPS, Low / Medium / High picture quality.
  Changes apply while sharing.
- **Low CPU**: only the newest frame is read, frames over the FPS target are dropped before any copy, and with no
  viewer at most one picture a second is encoded.
- **Rotation / size changes**: a `DisplayListener` resizes the virtual display and swaps the `ImageReader`
  (also `onCapturedContentResize` on Android 14+).
- **Stopping**: `MediaProjection.Callback.onStop` (status-bar "Stop", another app taking the screen) ends sharing
  cleanly; stop releases the `VirtualDisplay`, `ImageReader`, bitmap, locks, receivers and the server.
- **Main screen**: device name (rename to "Pixel 01"…), device ID (`DX-XXXX-XXXX-XXXX`), connection status
  (viewers), sharing status, video quality, network (Wi-Fi / Mobile data / Offline), last connection time,
  pairing QR, live preview and frame-rate stats.
- **Local viewer (for testing before any server exists)**: the phone serves a page on port 8080. Any browser on
  the same Wi-Fi (Chrome on Android, Chrome / Edge on Windows) opens `http://<phone-ip>:8080/?key=<KEY>` or scans
  the QR. Needs the 8-character viewer key; 10 wrong keys from one address block it for a minute; at most 4 viewers.
  Live picture, full screen, disconnect, stats. Plain HTTP, so use it on a trusted network only.
- **Recovery**: screen off (wake lock + Wi-Fi lock keep it going), network switch (the viewer listens on every
  network), low battery (≤ 15%: 720p / 10 FPS until > 20% or charging), Android killing the app and phone reboot
  (the app opens itself and asks again, see below).

### Recovery and Android's rules (important)

Android never lets an app keep screen-capture permission across a restart. Each time, the "Start now" dialog is
needed. DX Screen Monitor therefore:
1. remembers that sharing was on;
2. after a reboot, an app update or the process being killed, opens itself (if **Display over other apps** is
   allowed) and shows the dialog, or posts a **"Tap to resume screen sharing"** notification;
3. on department phones you can approve the dialog once for good over USB, so resuming needs **no tap**:
   ```
   adb shell appops set com.dxdepartment.screenmonitor PROJECT_MEDIA allow
   adb shell appops set com.dxdepartment.screenmonitor SYSTEM_ALERT_WINDOW allow
   ```
   This works on Android 10–13 (the Pixel 3 XL's Android 12 included). Android 14+ ignores it and always shows the
   dialog. The CI test uses exactly this, and checks that sharing comes back by itself after `kill -9` and after
   a reboot.

## Build

- Windows / Android Studio: see `docs/WINDOWS-SETUP.md`.
- Command line: `cd DXScreenMonitor && ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- CI builds every push and runs `.github/scripts/dx-emulator-test.sh` on an Android 12 emulator (start, picture
  size, wrong key refused, real snapshot, MJPEG frame rate, idle CPU saving, rotation, quality change, screen off,
  network off/on, low battery, process kill, reboot, stop). `main` then publishes the APK:
  https://github.com/asdf968566-art/song-by-gullu/releases/download/dx-screen-monitor/DXScreenMonitor.apk

The signing key `app/dx-debug.jks` is in the repo on purpose: every build has the same signature and installs over
the last one. Make a private key before any public release.

## Code map (`app/src/main/java/com/dxdepartment/screenmonitor/`)

| File | Job |
|---|---|
| `MainActivity.kt` | asks for screen capture, auto-resume, permission screens |
| `capture/ScreenCaptureService.kt` | foreground service: projection, locks, battery, server, notification |
| `capture/CaptureEngine.kt` | VirtualDisplay + ImageReader, rotation, FPS cap, JPEG |
| `capture/FrameHub.kt` | newest picture shared with viewer threads |
| `capture/CaptureState.kt` | live status for the UI / notification / viewer |
| `net/PreviewServer.kt`, `net/Html.kt` | LAN viewer (HTTP, MJPEG, key check) |
| `net/NetworkMonitor.kt` | Wi-Fi ↔ mobile data ↔ offline, local IP |
| `recovery/Recovery.kt`, `recovery/BootReceiver.kt` | resume after kill / reboot / update |
| `settings/AppSettings.kt` | quality, label, viewer key, switches |
| `device/DeviceIdentity.kt` | device ID and name |
| `ui/` | Compose screen, theme, QR |

More: `docs/PIXEL-TESTING.md`, `docs/TROUBLESHOOTING.md`, `docs/ROADMAP.md` (phases 2–5).
