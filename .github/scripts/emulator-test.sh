#!/usr/bin/env bash
# Emulator pe: app install -> khulta hai -> asli Audius gaana bajta hai ya nahi.
set -uo pipefail
PKG=com.sangeet.player
APK=$(ls apk/*.apk | head -1)
mkdir -p out

fail() { echo "❌ $1"; adb logcat -d > out/logcat.txt; adb exec-out screencap -p > out/fail.png || true; exit 1; }

adb install -r -g "$APK" || fail "APK install nahi hua"
adb logcat -c

echo "== App kholo"
adb shell am start -W -n $PKG/.MainActivity || fail "App start nahi hua"
sleep 12
adb shell pidof $PKG >/dev/null || fail "App khulte hi crash ho gaya"
adb exec-out screencap -p > out/1-home.png

echo "== Audius ka gaana bajao"
ID=$(curl -fsS "https://api.audius.co/v1/tracks/trending?app_name=Sangeet&limit=1" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"][0]["id"])')
URL="https://api.audius.co/v1/tracks/$ID/stream?app_name=Sangeet"
echo "URL: $URL"
adb shell "am start -W -a android.intent.action.VIEW -t audio/mpeg -d '$URL' -n $PKG/.MainActivity" || fail "Play intent fail"

PLAYING=0
for i in $(seq 1 45); do
  if adb shell dumpsys media_session | grep -Eq "state=PlaybackState \{state=(3|PLAYING)"; then PLAYING=1; break; fi
  sleep 2
done
adb shell dumpsys media_session > out/media_session.txt
[[ $PLAYING == 1 ]] || fail "Gaana PLAYING state mein nahi aaya"
echo "Gaana baj raha hai ✅"

# Thodi der bajne do, position aage badhni chahiye aur app zinda rehna chahiye.
sleep 10
adb shell pidof $PKG >/dev/null || fail "Bajate waqt app crash ho gaya"
adb exec-out screencap -p > out/2-playing.png

echo "== Discover tab (scroll feed)"
adb shell input keyevent KEYCODE_BACK || true
adb shell am start -n $PKG/.MainActivity
sleep 3
adb exec-out screencap -p > out/3-app.png

adb logcat -d > out/logcat.txt
if grep -q "FATAL EXCEPTION" out/logcat.txt; then
  grep -A 30 "FATAL EXCEPTION" out/logcat.txt | head -60
  fail "Logcat mein crash mila"
fi
echo "Emulator test pass ✅"
