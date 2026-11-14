# Testing on the Google Pixel 3 XL (Android 12)

## Prepare (once)
1. Settings → About phone → tap **Build number** 7 times → Developer options → **USB debugging** on.
2. Install the APK (see WINDOWS-SETUP.md) or download DXScreenMonitor.apk on the phone and allow
   "Install unknown apps" for the browser.
3. Optional, for hands-free recovery (department phones):
   ```
   adb shell appops set com.dxdepartment.screenmonitor PROJECT_MEDIA allow
   adb shell appops set com.dxdepartment.screenmonitor SYSTEM_ALERT_WINDOW allow
   ```
4. In the app: **Recovery → Don't optimise battery** → Allow.

## Checklist
| # | Test | Expected |
|---|---|---|
| 1 | Open the app | Device name, ID `DX-…`, Network "Wi-Fi", Not sharing |
| 2 | Start sharing → Start now | Status "Sharing", notification "Screen is being shared", Picture 1080×2220 |
| 3 | On the PC, open the address shown (same Wi-Fi) | Key page if the key is missing; with the key, the live screen |
| 4 | Scroll on the phone | Smooth on the PC; stats show ~20–30 FPS sent |
| 5 | Wrong key in the address | "That key is wrong" |
| 6 | Rotate the phone (auto-rotate on) | Viewer turns to 2220×1080 within a second |
| 7 | Quality → 720p / 15 FPS while watching | Changes at once |
| 8 | Lock the phone (screen off) 2 minutes | Viewer keeps showing (black/lock screen), status still Live |
| 9 | Turn Wi-Fi off, then on | App shows Mobile data / Offline, then Wi-Fi; the viewer reconnects by itself (new IP if it changed) |
| 10 | Pull down quick settings → stop the cast / "Stop sharing" in the notification | Sharing stops, status "Stopped" |
| 11 | Settings → Apps → DX Screen Monitor → Force stop | Sharing ends (force stop is the user's choice; no resume) |
| 12 | `adb shell am kill` / low memory kill while sharing | App opens itself and resumes (with step 3), else "Tap to resume" notification |
| 13 | Restart the phone while sharing | After unlock: resumes by itself (with step 3) or the notification |
| 14 | Battery ≤ 15%, not charging | "Low battery mode (720p, 10 FPS)" |
| 15 | Leave it sharing 1 hour with a viewer | No crash; check phone temperature and battery drop |

Logs for a report: `adb logcat -s DXService DXCapture DXViewer DXRecovery DXMain DXNet`.
