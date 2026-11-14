#!/usr/bin/env bash
# DX Screen Monitor: end-to-end checks on an Android 12 emulator.
# Screen capture is pre-approved with appops (as on a department phone), so no dialog needs a tap.
set -u
PKG=com.dxdepartment.screenmonitor
OUT=out; mkdir -p $OUT
PASS=0; FAIL=0
ok()   { echo "✅ $*"; PASS=$((PASS+1)); }
bad()  { echo "❌ $*"; FAIL=$((FAIL+1)); }
note() { echo "   $*"; }
log_has() { adb logcat -d -s DXService:* DXCapture:* DXViewer:* DXRecovery:* DXMain:* DXNet:* | grep -q "$1"; }
wait_log() { for _ in $(seq 1 "$2"); do log_has "$1" && return 0; sleep 1; done; return 1; }
key() { adb shell run-as $PKG cat shared_prefs/dx_settings.xml 2>/dev/null | sed -n 's/.*name="viewer_key">\([A-Z0-9]*\)<.*/\1/p'; }
status() { curl -s -m 5 "http://127.0.0.1:8080/status.json?key=$(key)"; }
field() { python3 -c "import json,sys; s=sys.stdin.read().strip(); d=json.loads(s) if s.startswith('{') else {}; print(d.get('$1',''))"; }
tap_once() {  # tap the first node whose text is $1
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b; b=$(adb shell cat /sdcard/ui.xml | python3 -c "
import re,sys
x=sys.stdin.read()
for m in re.finditer(r'<node [^>]*>', x):
    n=m.group(0)
    if 'text=\"$1\"' in n:
        a=list(map(int,re.findall(r'\d+', re.search(r'bounds=\"([^\"]+)\"', n).group(1))))
        print((a[0]+a[2])//2, (a[1]+a[3])//2); break")
  [ -n "$b" ] || return 1
  adb shell input tap $b
}
tap_text() {  # find $1 (scrolling up, then down) and tap it
  tap_once "$1" && return 0
  for _ in 1 2 3; do scroll_up; done
  tap_once "$1" && return 0
  for _ in 1 2 3 4 5 6 7 8; do scroll_down; sleep 0.5; tap_once "$1" && return 0; done
  note "'$1' not on screen"; adb shell screencap -p /sdcard/s.png; adb pull -q /sdcard/s.png "$OUT/missing-$(echo "$1" | tr -c 'a-zA-Z0-9' _).png"
  return 1
}
scroll_down() { adb shell input swipe $((SW/2)) $((SH*3/4)) $((SW/2)) $((SH*2/5)) 300; }
scroll_up() { adb shell input swipe $((SW/2)) $((SH*2/5)) $((SW/2)) $((SH*3/4)) 300; }
img() {  # print a small copy of a picture into the log
  python3 - "$1" "$2" <<'PY'
import sys, io, base64
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); im.thumbnail((int(sys.argv[2]), int(sys.argv[2])*3))
b = io.BytesIO(); im.save(b, "JPEG", quality=60)
print("IMG-BEGIN " + sys.argv[1]); print(base64.b64encode(b.getvalue()).decode()); print("IMG-END")
PY
}

adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
for _ in $(seq 1 30); do adb shell true 2>/dev/null && break; sleep 1; done
# The emulator uses the pixel_3_xl profile (1440x2960, 560 dpi); clear any old override.
adb shell wm size reset; adb shell wm density reset
SW=$(adb shell wm size | tail -1 | grep -o '[0-9]*x[0-9]*' | cut -dx -f1); SH=$(adb shell wm size | tail -1 | grep -o '[0-9]*x[0-9]*' | cut -dx -f2)
SW=${SW:-1440}; SH=${SH:-2960}
adb shell settings put system screen_off_timeout 1800000
echo "Device: $(adb shell getprop ro.product.model) Android $(adb shell getprop ro.build.version.release) $(adb shell wm size | tail -1) $(adb shell wm density | tail -1)"
adb install -r -g apk/debug/app-debug.apk || { bad "install"; exit 1; }
ok "Install debug APK"
adb shell appops set $PKG PROJECT_MEDIA allow
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell dumpsys deviceidle whitelist +$PKG >/dev/null
adb logcat -c

# 1. Start from the button.
adb shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 4
adb shell screencap -p /sdcard/s.png; adb pull -q /sdcard/s.png $OUT/01-main.png
tap_text "Start sharing" || true
if wait_log "sharing started" 20; then ok "Start sharing (MediaProjection + foreground service)"; else bad "Start sharing"; adb logcat -d | tail -80; fi
adb forward tcp:8080 tcp:8080 >/dev/null
sleep 3
S=$(status); echo "status: $S"
[ "$(echo "$S" | field phase)" = SHARING ] && ok "Local viewer /status.json says SHARING" || bad "status.json"
W=$(echo "$S" | field width); H=$(echo "$S" | field height)
[ "$W" = 1080 ] && [ "$H" = 2220 ] && ok "1080p picture from a 1440x2960 screen: ${W}x$H" || bad "picture size ${W}x$H (want 1080x2220)"
adb shell dumpsys activity services $PKG | grep -q "isForeground=true" && ok "Service is in the foreground" || bad "service not foreground"
adb shell dumpsys activity services $PKG | grep -o "foregroundServiceType=0x[0-9a-f]*" | head -1

# 2. Viewer key.
code=$(curl -s -o /dev/null -w '%{http_code}' -m 5 "http://127.0.0.1:8080/status.json?key=WRONGKEY")
[ "$code" = 401 ] && ok "Wrong viewer key is refused (401)" || bad "wrong key gave $code"
code=$(curl -s -o /dev/null -w '%{http_code}' -m 5 "http://127.0.0.1:8080/stream.mjpg")
[ "$code" = 401 ] && ok "No key is refused (401)" || bad "no key gave $code"

# 3. A real picture of the screen.
curl -s -m 10 -o $OUT/snapshot.jpg "http://127.0.0.1:8080/snapshot.jpg?key=$(key)"
python3 - $OUT/snapshot.jpg <<'PY' && ok "Snapshot is a real screen picture" || bad "snapshot"
import sys
from PIL import Image, ImageStat
im = Image.open(sys.argv[1]); s = ImageStat.Stat(im.convert("L"))
print("   snapshot", im.size, "brightness %.0f, spread %.0f" % (s.mean[0], s.stddev[0]))
sys.exit(0 if im.size == (1080, 2220) and s.stddev[0] > 5 else 1)
PY
img $OUT/snapshot.jpg 240

# 4. Live stream while the screen moves; CPU and memory meanwhile.
( for _ in $(seq 1 12); do scroll_down; scroll_up; done ) &
MOVER=$!
sleep 1
curl -s -m 8 "http://127.0.0.1:8080/stream.mjpg?key=$(key)" -o $OUT/stream.mjpg
PID=$(adb shell pidof $PKG | tr -d '\r')
wait $MOVER 2>/dev/null
N=$(grep -c -a -- "--dxframe" $OUT/stream.mjpg)
note "stream: $N frames in 8 s ($(du -k $OUT/stream.mjpg | cut -f1) KB)"
[ "$N" -ge 24 ] && ok "MJPEG stream: $N frames in 8 s while scrolling" || bad "MJPEG stream: only $N frames in 8 s"
( for _ in $(seq 1 6); do scroll_down; scroll_up; done ) &
curl -s -m 6 "http://127.0.0.1:8080/stream.mjpg?key=$(key)" -o /dev/null &
sleep 3
note "CPU while streaming: $(adb shell top -b -n 1 -p $PID | tail -1)"
note "Memory: $(adb shell dumpsys meminfo $PKG | grep -E 'TOTAL PSS|TOTAL:' | head -1)"
wait
S=$(status); note "after stream: viewers=$(echo "$S" | field viewers) encodeFps=$(echo "$S" | field encodeFps) frames=$(echo "$S" | field framesTotal)"
adb logcat -d -s DXViewer:* | grep -q "viewer connected" && ok "Viewer connect / leave is logged" || bad "viewer log"

# 5. Idle: no viewer = about one picture a second.
sleep 4
E=$(status | field encodeFps)
python3 -c "import sys; sys.exit(0 if float('$E' or 9) <= 2.5 else 1)" && ok "No viewer: only $E pictures/s encoded (saves CPU)" || bad "idle encode $E/s"

# 6. Rotation.
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 5
S=$(status); W=$(echo "$S" | field width); H=$(echo "$S" | field height)
[ "$W" = 2220 ] && [ "$H" = 1080 ] && ok "Rotation: picture is ${W}x$H in landscape" || bad "rotation: ${W}x$H"
curl -s -m 10 -o $OUT/landscape.jpg "http://127.0.0.1:8080/snapshot.jpg?key=$(key)"
adb shell settings put system user_rotation 0
sleep 4
S=$(status); W=$(echo "$S" | field width); H=$(echo "$S" | field height)
[ "$W" = 1080 ] && [ "$H" = 2220 ] && ok "Rotation back: ${W}x$H" || bad "rotation back: ${W}x$H"

# 7. Quality from the app while sharing.
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 2
for _ in 1 2 3; do scroll_down; done; sleep 1
if tap_text "720p"; then
  sleep 3
  S=$(status); W=$(echo "$S" | field width); H=$(echo "$S" | field height)
  [ "$W" = 720 ] && [ "$H" = 1480 ] && ok "Quality 720p while sharing: ${W}x$H" || bad "720p: ${W}x$H"
  tap_text "1080p" >/dev/null
else bad "720p chip not found"; fi
adb shell screencap -p /sdcard/s.png; adb pull -q /sdcard/s.png $OUT/02-sharing-settings.png
for _ in 1 2 3 4; do scroll_up; done; sleep 1
adb shell screencap -p /sdcard/s.png; adb pull -q /sdcard/s.png $OUT/03-sharing.png

# 8. Screen off: still sharing, viewer still served.
adb shell input keyevent KEYCODE_SLEEP; sleep 6
S=$(status)
[ "$(echo "$S" | field phase)" = SHARING ] && ok "Screen off: still sharing and reachable" || bad "screen off: $S"
adb shell dumpsys power | grep -q "DXScreenMonitor:sharing" && ok "Wake lock held while sharing" || bad "no wake lock"
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 2

# 9. Network switch: the viewer keeps working (it listens on every network).
adb shell svc wifi disable; adb shell svc data disable; sleep 4
adb shell svc wifi enable; adb shell svc data enable; sleep 6
adb logcat -d -s DXNet:* | tail -4
[ "$(status | field phase)" = SHARING ] && ok "Network off/on: still sharing" || bad "network change"

# 10. Low battery: 720p / 10 FPS.
adb shell dumpsys battery unplug; adb shell dumpsys battery set level 12; sleep 4
S=$(status)
[ "$(echo "$S" | field lowPower)" = True ] && [ "$(echo "$S" | field width)" = 720 ] && ok "Battery 12%: low battery mode at 720p" || bad "low battery: $S"
adb shell dumpsys battery set level 80; sleep 4
[ "$(status | field width)" = 1080 ] && ok "Battery 80%: back to 1080p" || bad "battery back"
adb shell dumpsys battery reset

# 11. Android kills the app: it comes back and shares again by itself
#     (overlay allowed + capture pre-approved; otherwise a "Tap to resume" notification).
adb logcat -c
OLD=$(adb shell pidof $PKG | tr -d '\r')
adb shell kill -9 $OLD
if wait_log "sharing started" 60; then
  ok "Process killed: sharing resumed by itself (pid $OLD -> $(adb shell pidof $PKG | tr -d '\r'))"
else
  bad "no resume after kill"; adb logcat -d -s DXService:* DXRecovery:* DXMain:* ActivityManager:* | tail -40
fi
sleep 3
[ "$(status | field phase)" = SHARING ] && ok "Viewer reachable after the restart" || bad "viewer after restart"

# 12. Reboot: resumes after boot.
adb reboot
adb wait-for-device
for _ in $(seq 1 120); do [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ] && break; sleep 2; done
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard
if wait_log "sharing started" 120; then ok "Phone rebooted: sharing resumed by itself"; else
  if adb shell dumpsys notification | grep -q "Tap to resume"; then bad "after reboot only the 'Tap to resume' notification came"; else bad "no resume after reboot"; fi
  adb logcat -d -s DXRecovery:* DXService:* DXMain:* | tail -30
fi
adb forward tcp:8080 tcp:8080 >/dev/null; sleep 3
[ "$(status | field phase)" = SHARING ] && ok "Viewer reachable after reboot" || bad "viewer after reboot"

# 13. Stop from the app: everything is released.
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3
for _ in 1 2 3 4; do scroll_up; done
adb logcat -c
tap_text "Stop sharing" || true
if wait_log "capture released" 15; then ok "Stop: VirtualDisplay / ImageReader released"; else bad "stop"; fi
sleep 2
code=$(curl -s -o /dev/null -w '%{http_code}' -m 3 "http://127.0.0.1:8080/status.json?key=$(key)")
[ "$code" = 000 ] && ok "Stop: local viewer closed" || bad "viewer still answers ($code)"
adb shell dumpsys activity services $PKG | grep -q ScreenCaptureService && bad "service still running" || ok "Stop: service ended"
adb shell screencap -p /sdcard/s.png; adb pull -q /sdcard/s.png $OUT/04-stopped.png

adb logcat -d > $OUT/logcat.txt
img $OUT/03-sharing.png 360
img $OUT/02-sharing-settings.png 360
img $OUT/landscape.jpg 360
echo "RESULT: $PASS passed, $FAIL failed"
[ "$FAIL" = 0 ]
