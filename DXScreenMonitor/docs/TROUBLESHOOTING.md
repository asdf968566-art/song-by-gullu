# Troubleshooting

| Problem | Cause / fix |
|---|---|
| The "Start now" dialog comes every time | Android's rule. Pre-approve with `adb shell appops set com.dxdepartment.screenmonitor PROJECT_MEDIA allow` (Android 10–13). |
| Sharing stops when the screen is off for long | Battery optimisation. App → Recovery → "Don't optimise battery". |
| PC can't open the viewer address | Same Wi-Fi? Router "AP isolation" / guest Wi-Fi blocks phone↔PC. Windows network must be Private. Try the phone's hotspot with the PC on it. |
| "Not on Wi-Fi" in the viewer card | On mobile data the phone has no address the PC can reach. Internet viewing comes with WebRTC (Phase 2). |
| 401 / key page | The key in the address is old: press the QR again or type the key shown in the app. "New viewer key" changes it. |
| 429 "Too many wrong keys" | 10 wrong tries from that address: wait a minute. |
| "Too many viewers" | 4 at once on the local viewer. |
| Black picture | Some apps (banking, DRM video) block capture with FLAG_SECURE; Android shows them black. Expected. |
| No resume after reboot | Allow "Display over other apps" (app → Recovery) or tap the "Tap to resume" notification. Force stop disables boot events until the app is opened once. |
| Low FPS | Lower to 720p / 15 FPS; the Pixel 3 XL encodes JPEG on the CPU. WebRTC (Phase 2) will use the hardware H.264 encoder. |
| Install fails "App not installed" | An APK signed with another key is installed. Uninstall it once; builds from this repo then update in place. |
