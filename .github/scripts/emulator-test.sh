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
# For You feed khud bhi gaana chala sakta hai, isliye pehle rok do aur phir sirf "stream" naam wala gaana dekho.
adb shell input keyevent KEYCODE_MEDIA_PAUSE || true
sleep 2
adb shell "am start -W -a android.intent.action.VIEW -t audio/mpeg -d '$URL' -n $PKG/.MainActivity" || fail "Play intent fail"

PLAYING=0
for i in $(seq 1 45); do
  D=$(adb shell dumpsys media_session)
  if echo "$D" | grep -Eq "state=PlaybackState \{state=(3|PLAYING)" && echo "$D" | grep -q "description=stream"; then PLAYING=1; break; fi
  sleep 2
done
adb shell dumpsys media_session > out/media_session.txt
[[ $PLAYING == 1 ]] || fail "Gaana PLAYING state mein nahi aaya"
echo "Gaana baj raha hai ✅"

# Thodi der bajne do, position aage badhni chahiye aur app zinda rehna chahiye.
sleep 10
adb shell pidof $PKG >/dev/null || fail "Bajate waqt app crash ho gaya"
adb exec-out screencap -p > out/2-playing.png

# sangeet://play deep link se source ka gaana bajao, aur check karo ki wahi gaana PLAYING hai.
play_source() {
  local src=$1 q=$2 must=$3
  echo "== $src: '$q'"
  adb shell input keyevent KEYCODE_MEDIA_PAUSE || true
  sleep 2
  adb shell "am start -W -a android.intent.action.VIEW -d 'sangeet://play?q=$q&source=$src' -n $PKG/.MainActivity" >/dev/null
  local ok=0
  for i in $(seq 1 45); do
    DUMP=$(adb shell dumpsys media_session)
    if echo "$DUMP" | grep -Eq "state=PlaybackState \{state=(3|PLAYING)" && echo "$DUMP" | grep -iq "description=.*$q"; then ok=1; break; fi
    sleep 2
  done
  echo "$DUMP" > "out/media_session_$src.txt"
  adb exec-out screencap -p > "out/play-$src.png"
  if [[ $ok == 1 ]]; then
    echo "$src gaana baj raha hai ✅ ($(echo "$DUMP" | grep -io 'description=[^,]*,[^,]*' | head -1))"
  elif [[ $must == 1 ]]; then
    fail "$src se gaana nahi baja"
  else
    echo "::warning::$src se gaana nahi baja (CI ke server IP pe block ho sakta hai)"
    grep -E "PlaybackException|ExtractionException|ReCaptcha|IOException" -m 10 <(adb logcat -d) || true
  fi
}
play_source jiosaavn kesariya 1
play_source youtube kesariya 0

echo "== For You feed"
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
