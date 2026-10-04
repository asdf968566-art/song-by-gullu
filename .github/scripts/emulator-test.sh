#!/usr/bin/env bash
# Emulator pe: app install -> khulta hai -> asli Audius gaana bajta hai ya nahi.
set -uo pipefail
PKG=com.sangeet.player
APK=$(ls apk/*.apk | head -1)
mkdir -p out

fail() { echo "❌ $1"; adb logcat -d > out/logcat.txt; adb exec-out screencap -p > out/fail.png || true; exit 1; }

adb install -r -g "$APK" || fail "APK install nahi hua"
adb logcat -c

echo "== Baseline profile: app kitni jaldi khulta hai (bina profile vs profile ke saath)"
start_ms() { adb shell am force-stop $PKG; sleep 1; adb shell am start -W -n $PKG/.MainActivity | grep -o "TotalTime: [0-9]*" | grep -o "[0-9]*"; }
adb shell cmd package compile -f -m verify $PKG >/dev/null
start_ms >/dev/null; A=$(start_ms); B=$(start_ms)
adb shell cmd package compile -f -m speed-profile $PKG >/dev/null
start_ms >/dev/null; C=$(start_ms); D=$(start_ms)
echo "Bina profile: ${A} ms, ${B} ms | Profile ke saath: ${C} ms, ${D} ms"
adb shell input keyevent KEYCODE_MEDIA_PAUSE || true
adb shell am force-stop $PKG

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
  adb logcat -c
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
    adb logcat -d > "out/logcat_$src.txt"
    grep -E "Sangeet|ExoPlayerImplInternal|Caused by|Exception" "out/logcat_$src.txt" | grep -v -E "Auth|GCM|Bugle|constellation|Finsky|gms" | head -40 || true
  fi
}
play_source jiosaavn kesariya 1
play_source youtube kesariya 0

echo "== Download: YouTube ka gaana download hona chahiye (pehle JioSaavn se, warna YouTube se)"
adb logcat -c
adb shell "am start -W -a android.intent.action.VIEW -d 'sangeet://play?q=tum%20hi%20ho&source=youtube&download=1' -n $PKG/.MainActivity" >/dev/null
DL=""
for i in $(seq 1 60); do
  DL=$(adb logcat -d | grep -o "download done: .*" | head -1)
  [[ -n "$DL" ]] && break
  sleep 2
done
[[ -n "$DL" ]] || { adb logcat -d | grep -E "Sangeet|WM-|Download" | tail -30; fail "Download 2 minute mein poora nahi hua"; }
echo "$DL ✅"

echo "== For You feed: kuch apne aap nahi bajna chahiye, Play dabane par bajna chahiye"
adb shell input keyevent KEYCODE_MEDIA_PAUSE || true
adb shell am force-stop $PKG
sleep 2
adb shell am start -W -n $PKG/.MainActivity >/dev/null
# Screen ke texts / buttons (uiautomator) -> python se dhoondo
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb exec-out cat /sdcard/ui.xml; }
center() { python3 -c '
import re,sys
x=sys.stdin.read(); want=sys.argv[1]
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    if f"content-desc=\"{want}\"" in n:
        a=list(map(int,re.findall(r"\d+", re.search(r"bounds=\"([^\"]+)\"", n).group(1))))
        print((a[0]+a[2])//2, (a[1]+a[3])//2); break
' "$1"; }
FEED=0
for i in $(seq 1 40); do
  X=$(ui)
  if [[ -n "$(echo "$X" | center "Play/Pause")" ]]; then FEED=1; break; fi
  sleep 3
done
echo "$X" > out/feed-ui.xml
adb exec-out screencap -p > out/3-feed.png
echo "Screen par: $(echo "$X" | grep -o 'text="[^"]\+"' | head -12 | tr '\n' ' ')"
[[ $FEED == 1 ]] || fail "For You feed mein gaane nahi aaye"
D=$(adb shell dumpsys media_session)
if echo "$D" | grep -Eq "state=PlaybackState \{state=(3|PLAYING)"; then fail "Feed ne apne aap gaana chala diya"; fi
echo "Feed khula, kuch apne aap nahi baja ✅"
XY=$(echo "$X" | center "Play/Pause")
adb shell input tap $XY
PLAYING=0
for i in $(seq 1 30); do
  if adb shell dumpsys media_session | grep -Eq "state=PlaybackState \{state=(3|PLAYING)"; then PLAYING=1; break; fi
  sleep 2
done
adb exec-out screencap -p > out/4-feed-playing.png
[[ $PLAYING == 1 ]] || fail "Feed mein Play dabane par gaana nahi baja"
echo "Feed: Play dabate hi gaana baja ✅ ($(adb shell dumpsys media_session | grep -o 'description=[^,]*,[^,]*' | head -1))"
XY=$(ui | center "Next")
adb shell input tap $XY
sleep 12
adb shell dumpsys media_session | grep -Eq "state=PlaybackState \{state=(3|6|PLAYING|BUFFERING)" || fail "Feed mein Next ke baad gaana nahi baja"
echo "Feed: Next ke baad agla gaana ✅ ($(adb shell dumpsys media_session | grep -o 'description=[^,]*,[^,]*' | head -1))"

echo "== Catalog (background download) ke saath dobara kholo"
for i in $(seq 1 30); do
  adb shell run-as $PKG ls files/catalog 2>/dev/null | grep -q "hindi.json.gz" && break
  sleep 2
done
echo "Catalog files: $(adb shell run-as $PKG ls -l files/catalog 2>/dev/null | awk '{print $NF, $(NF-3)}' | tr '\n' ' ')"
adb shell pidof $PKG >/dev/null || fail "Catalog load karte waqt app crash ho gaya"
adb shell input keyevent KEYCODE_MEDIA_PAUSE || true
adb shell am force-stop $PKG
sleep 2
T0=$(date +%s)
adb shell am start -W -n $PKG/.MainActivity >/dev/null
FEED=0
for i in $(seq 1 40); do
  if [[ -n "$(ui | center "Play/Pause")" ]]; then FEED=1; break; fi
  sleep 2
done
adb exec-out screencap -p > out/5-feed-again.png
[[ $FEED == 1 ]] || fail "Catalog ke saath feed nahi khula"
sleep 15
adb shell pidof $PKG >/dev/null || fail "Catalog ke saath app crash ho gaya"
echo "Feed $(( $(date +%s) - T0 ))s mein khula, app zinda ✅ (memory: $(adb shell dumpsys meminfo $PKG | grep -m1 'TOTAL' | awk '{print $2}') KB)"

echo "== Baaki tabs: Home, Search, Library (scroll karke)"
tap_text() { XY=$(ui | python3 -c '
import re,sys
x=sys.stdin.read(); want=sys.argv[1]
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    if f"text=\"{want}\"" in n:
        a=list(map(int,re.findall(r"\d+", re.search(r"bounds=\"([^\"]+)\"", n).group(1))))
        print((a[0]+a[2])//2, (a[1]+a[3])//2); break
' "$1"); [[ -n "$XY" ]] && adb shell input tap $XY; }
crash_check() {
  if ! adb shell pidof $PKG >/dev/null || adb logcat -d | grep -q "FATAL EXCEPTION"; then
    adb logcat -d | grep -A 40 "FATAL EXCEPTION" | head -60
    adb exec-out screencap -p > "out/crash-$1.png"
    fail "$1 kholte hi crash"
  fi
}
for tab in Home Search "Your Library" Home; do
  tap_text "$tab"
  sleep 8
  for k in 1 2 3; do adb shell input swipe 540 1600 540 500 250; sleep 1; done
  for k in 1 2 3; do adb shell input swipe 540 500 540 1600 250; sleep 1; done
  crash_check "$tab"
  echo "$tab: khula, scroll hua, crash nahi ✅"
done
adb exec-out screencap -p > out/6-home.png

echo "== Poora UI walk: har tab, button, option"
python3 .github/scripts/ui-walk.py | tee out/ui-walk.txt
[[ ${PIPESTATUS[0]} == 0 ]] || fail "UI walk mein crash ya galat result (upar report dekho)"

adb logcat -d > out/logcat.txt
# Release build (R8): kuch code hat gaya ho to yahan dikhega, chahe app crash na kare.
R8=$(grep -E "ClassNotFoundException|NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|InvalidDefinitionException" out/logcat.txt | grep -i -E "sangeet|anthropic|newpipe|jackson|serializ|mozilla" | head -20)
if [[ -n "$R8" ]]; then echo "R8 se toota code:"; echo "$R8"; fail "Release build mein class/method missing"; fi
echo "R8: koi missing class nahi ✅"
if grep -q "FATAL EXCEPTION" out/logcat.txt; then
  grep -A 30 "FATAL EXCEPTION" out/logcat.txt | head -60
  fail "Logcat mein crash mila"
fi
echo "Emulator test pass ✅"
