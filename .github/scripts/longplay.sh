#!/usr/bin/env bash
# One-off probe for report #69 ("15-20 minute chalne ke baad app apne aap band ho jata hai"): plays songs for 28
# minutes (10 on screen, 10 in the background, 8 with the screen off) and logs the app's memory every minute, whether
# its process is still there, and at the end why Android ended it (if it did).
set -u
PKG=com.sangeet.player
mkdir -p out
adb install -r apk/Sangeet.apk >/dev/null && echo "installed"
adb shell am start -W -n $PKG/.MainActivity >/dev/null
sleep 25
adb shell "am start -W -a android.intent.action.VIEW -d 'sangeet://play?q=arijit%20singh&source=jiosaavn' -n $PKG/.MainActivity" >/dev/null
sleep 20

for i in $(seq 1 28); do
  [ "$i" = 11 ] && { adb shell input keyevent KEYCODE_HOME; echo "-- app in the background"; }
  [ "$i" = 21 ] && { adb shell input keyevent KEYCODE_POWER; echo "-- screen off"; }
  pid=$(adb shell pidof $PKG | tr -d '\r')
  mem=$(adb shell dumpsys meminfo $PKG 2>/dev/null | grep -E "Java Heap:|Native Heap:|Graphics:|TOTAL PSS:|TOTAL RSS:" | tr -s ' ' | tr '\n' ';')
  media=$(adb shell dumpsys media_session 2>/dev/null)
  state=$(echo "$media" | grep -m1 -o "state=PlaybackState {state=[0-9]*" | grep -o "[0-9]*$")
  song=$(echo "$media" | grep -m1 -o "description=[^,]*" | cut -c13-50)
  echo "min $i pid=${pid:-none} state=${state:-?} song=${song:-?} |$mem"
  [ -z "$pid" ] && echo "PROCESS GONE at minute $i"
  sleep 60
done

echo "== exit reasons"
adb shell dumpsys activity exit-info $PKG | grep -E "timestamp|reason|description|importance|pss|rss" | head -40
echo "== kills / crashes in logcat"
adb logcat -d | grep -E "lowmemorykiller|Killing [0-9]+:$PKG|$PKG.*(died|crash)|Fatal signal|FATAL EXCEPTION|ANR in $PKG|OutOfMemory" | tail -40
echo "== Sangeet log (last 40)"
adb logcat -d -s Sangeet | tail -40
