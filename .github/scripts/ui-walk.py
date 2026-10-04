#!/usr/bin/env python3
"""Taps through every tab, button and option of Sangeet on the emulator, like a user would.

After each step it checks that the app is still alive (no crash). At the end it prints a report:
  ✅ worked   ⚠️ button not found on screen   ❌ crash / wrong result
Exit code 1 if anything crashed or a check failed.
"""
import re
import subprocess
import sys
import time

PKG = "com.sangeet.player"
results = []  # (status, step, detail)


def adb(*args, check=False):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    return r.stdout


def sh(cmd):
    return adb("shell", cmd)


_size = None


def screen():
    """(width, height) of the emulator screen, so swipes stay on it."""
    global _size
    if not _size:
        m = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size"))
        _size = (int(m.group(1)), int(m.group(2))) if m else (720, 1280)
    return _size


def swipe_up():
    w, h = screen()
    sh(f"input swipe {w // 2} {int(h * 0.72)} {w // 2} {int(h * 0.32)} 300")


def swipe_down():
    w, h = screen()
    sh(f"input swipe {w // 2} {int(h * 0.3)} {w // 2} {int(h * 0.8)} 200")


def dump():
    sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
    return adb("exec-out", "cat", "/sdcard/ui.xml")


def nodes(xml):
    for m in re.finditer(r"<node [^>]*>", xml):
        n = m.group(0)
        attr = lambda k: (re.search(rf'{k}="([^"]*)"', n) or [None, ""])[1]
        b = list(map(int, re.findall(r"\d+", attr("bounds")))) or [0, 0, 0, 0]
        yield {"text": attr("text"), "desc": attr("content-desc"), "x": (b[0] + b[2]) // 2, "y": (b[1] + b[3]) // 2,
               "w": b[2] - b[0], "h": b[3] - b[1]}


def find(label, contains=False, scroll=0, lowest=False):
    """A node whose text or content-desc is `label` (or contains it). Scrolls down up to `scroll` times.
    lowest=True picks the match nearest the bottom (e.g. the mini player, not a shelf above it)."""
    for i in range(scroll + 1):
        hits = [n for n in nodes(dump()) for v in (n["text"], n["desc"])
                if v and (v == label or (contains and label.lower() in v.lower())) and n["w"] > 0 and n["h"] > 0]
        if hits:
            return max(hits, key=lambda n: n["y"]) if lowest else hits[0]
        if i < scroll:
            swipe_up()
            time.sleep(1)
    return None


def tap(label, contains=False, scroll=0, wait=2.0):
    n = find(label, contains, scroll)
    if not n:
        return False
    sh(f"input tap {n['x']} {n['y']}")
    time.sleep(wait)
    return True


def back(times=1):
    for _ in range(times):
        sh("input keyevent KEYCODE_BACK")
        time.sleep(1.2)


def alive():
    if not sh(f"pidof {PKG}").strip():
        return False
    log = adb("logcat", "-d")
    return "FATAL EXCEPTION" not in log or f"Process: {PKG}" not in log.split("FATAL EXCEPTION")[-1][:400]


def crash_trace():
    log = adb("logcat", "-d")
    i = log.rfind("FATAL EXCEPTION")
    return log[i:i + 3000] if i >= 0 else "(app process is gone)"


def step(name, action, verify=None):
    """Runs one user action. A missing button is a warning; a crash or failed check is an error."""
    adb("logcat", "-c")
    try:
        ok = action()
    except Exception as e:  # noqa: BLE001
        results.append(("❌", name, f"script error {e}"))
        return False
    if not alive():
        results.append(("❌", name, "CRASH\n" + crash_trace()))
        adb("exec-out", "screencap", "-p")
        relaunch()
        return False
    if ok is False:
        results.append(("⚠️", name, "button not found"))
        return False
    if verify:
        good, detail = verify()
        results.append(("✅" if good else "❌", name, detail))
        return good
    results.append(("✅", name, ""))
    return True


def relaunch():
    sh(f"am force-stop {PKG}")
    time.sleep(1)
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(6)


def media():
    return sh("dumpsys media_session")


def playing():
    return re.search(r"state=PlaybackState \{state=(3|PLAYING)", media()) is not None


def now_title():
    m = re.search(r"description=([^,\n]+)", media())
    return m.group(1).strip() if m else ""


def go_tab(name):
    return tap(name, wait=3)


def play_song(q="kesariya", source="jiosaavn"):
    sh(f"am start -W -a android.intent.action.VIEW -d 'sangeet://play?q={q}&source={source}' -n {PKG}/.MainActivity")
    for _ in range(30):
        if playing():
            return True
        time.sleep(2)
    return False


def toggle_twice(label):
    """Flip a switch and flip it back (settings stay as they were)."""
    return lambda: tap(label, scroll=8, wait=1.5) and tap(label, scroll=0, wait=1.5)


def open_and_back(label, contains=False, scroll=0, wait=3):
    def act():
        if not tap(label, contains, scroll, wait):
            return False
        back()
        return True
    return act


def fresh(play=False):
    """Start every section from the same place: app restarted, optionally with a song playing."""
    relaunch()
    if play:
        play_song()
        time.sleep(2)
    return True


def open_now_playing():
    """Tap the mini player (the lowest place showing the current song's title)."""
    title = now_title()
    if not title:
        return False
    go_tab("Home")
    n = find(title, lowest=True) or find(title[:12], contains=True, lowest=True)
    if not n:
        return False
    sh(f"input tap {n['x']} {n['y']}")
    time.sleep(3)
    return True


def main():
    relaunch()

    # ---------------------------------------------------------------- bottom tabs
    for t in ["For You", "Home", "Search", "Your Library"]:
        step(f"Tab: {t}", lambda t=t: go_tab(t))

    # ---------------------------------------------------------------- For You
    go_tab("For You")
    time.sleep(8)
    step("For You: Sad mood", lambda: tap("Sad", contains=True, wait=8))
    step("For You: All moods back", lambda: tap("For You", contains=True, wait=6))
    step("For You: New feed", lambda: tap("New feed", wait=8))
    step("For You: Like", lambda: tap("Like", wait=2) and tap("Like", wait=2))
    step("For You: Play", lambda: tap("Play/Pause", wait=8), lambda: (playing(), f"playing: {now_title()}"))
    step("For You: Next", lambda: tap("Next", wait=10), lambda: (bool(now_title()), f"now: {now_title()}"))
    step("For You: swipe up", lambda: swipe_up() is None and time.sleep(6) is None)

    # ---------------------------------------------------------------- Home
    fresh()
    go_tab("Home")
    for label in ["Liked Songs", "Downloads", "On this phone", "Recently played"]:
        step(f"Home: {label}", open_and_back(label))
    step("Home: Refresh", lambda: tap("Refresh", wait=4))
    step("Home: AI DJ", open_and_back("AI DJ"))
    step("Home: scroll to the end", lambda: [swipe_up() for _ in range(8)] and True)
    step("Home: open a chart / mix", lambda: tap("Mix", contains=True, scroll=0, wait=4) and (back() or True))

    # ---------------------------------------------------------------- Search
    fresh()
    go_tab("Search")

    def search():
        n = find("Search", contains=True)
        if not n:
            return False
        sh(f"input tap {n['x']} {n['y']}")
        time.sleep(1)
        sh("input text arijit")
        sh("input keyevent KEYCODE_ENTER")
        time.sleep(10)
        return True
    step("Search: type 'arijit'", search, lambda: (find("Arijit", contains=True) is not None, "results shown"))
    def play_result():
        # A song row below the search box (not the box or a suggestion chip).
        box = find("Search", contains=True)
        rows = [n for n in nodes(dump()) if "arijit" in (n["text"] + n["desc"]).lower() and box and n["y"] > box["y"] + 150]
        if not rows:
            return False
        sh(f"input tap {rows[0]['x']} {rows[0]['y']}")
        time.sleep(10)
        return True
    step("Search: play a result", play_result, lambda: (playing(), f"playing: {now_title()}"))
    back()

    # ---------------------------------------------------------------- Library
    for label in ["Online Library", "Your Stats", "Liked Songs", "Downloads", "On this phone"]:
        fresh()
        go_tab("Your Library")
        step(f"Library: {label}", open_and_back(label, scroll=3, wait=5))
    fresh()
    go_tab("Your Library")
    step("Library: Import playlist", open_and_back("Import playlist"))
    step("Library: New playlist", lambda: tap("New playlist", wait=2) and (back() or True))
    step("Library: Online Library → playlist", lambda: tap("Online Library", scroll=3, wait=8)
         and sh(f"input tap {screen()[0] // 2} {screen()[1] // 2}") is not None and time.sleep(6) is None and (back(2) or True))

    # ---------------------------------------------------------------- Now Playing
    fresh(play=True)
    step("Now Playing: open", open_now_playing, lambda: (find("Speed") is not None or find("Queue") is not None, "player screen"))
    step("Now Playing: Shuffle on/off", lambda: tap("Shuffle", wait=1) and tap("Shuffle", wait=1))

    def speed_125():
        if not tap("Speed", wait=2):
            return False
        if not tap("1.25x", wait=1):
            return False
        return tap("Apply", wait=2)
    step("Now Playing: speed 1.25x", speed_125, lambda: ("speed=1.25" in media(), "player speed is 1.25"))
    step("Now Playing: speed back to 1x", lambda: tap("Speed", wait=2) and tap("1x", wait=1) and tap("Apply", wait=2),
         lambda: ("speed=1.0" in media(), "player speed is 1.0"))
    step("Now Playing: Sleep timer", lambda: tap("Sleep timer", wait=2) and (back() or True))
    step("Now Playing: Queue", lambda: tap("Queue", scroll=2, wait=2) and (back() or True))
    step("Now Playing: Pause", lambda: tap("Pause", contains=True, wait=2) or tap("Play/Pause", wait=2),
         lambda: (not playing(), "paused"))
    step("Now Playing: Play", lambda: tap("Play", wait=4) or tap("Play/Pause", wait=4), lambda: (playing(), "playing again"))
    before = now_title()
    step("Now Playing: Next", lambda: tap("Next", wait=10), lambda: (now_title() != before, f"{before} → {now_title()}"))
    step("Now Playing: Previous", lambda: tap("Previous", wait=4) and tap("Previous", wait=8), lambda: (bool(now_title()), f"now: {now_title()}"))

    # Song options (3 dots), from a fresh Now Playing screen
    fresh(play=True)
    open_now_playing()
    def menu(item, then_back=True, wait=2):
        def act():
            if not (tap("Options", wait=2) or tap("More", wait=2)):
                return False
            if not tap(item, scroll=2, wait=wait):
                back()
                return False
            if then_back:
                back()
            return True
        return act
    for item in ["Like", "Play next", "Add to queue", "Add to playlist", "Start radio", "Share"]:
        step(f"Song menu: {item}", menu(item, then_back=item in ("Add to playlist", "Share")))
    step("Song menu: Go to artist", menu("Go to artist", wait=6))

    def download():
        adb("logcat", "-c")
        if not menu("Download", then_back=False)():
            return False
        for _ in range(60):
            if "download done:" in adb("logcat", "-d"):
                return True
            time.sleep(2)
        return True
    step("Song menu: Download", download,
         lambda: ("download done:" in adb("logcat", "-d"),
                  (re.search(r"download done: .*", adb("logcat", "-d")) or [""])[0] or "not finished in 2 min"))
    back(2)

    # ---------------------------------------------------------------- Settings
    fresh()
    go_tab("Home")
    step("Settings: open", lambda: tap("Settings", wait=3))
    for label in ["Normalize volume", "Skip silence", "Hook preview in For You", "Resume on headphones", "Smart downloads",
                  "Autoplay", "Auto playlists", "Fetch lyrics automatically", "Offline mode", "Download on Wi-Fi only"]:
        step(f"Settings switch: {label}", toggle_twice(label))
        swipe_down()  # back to the top for the next search
        swipe_down()
    for label in ["Theme", "Equalizer & Bass boost", "Music sources", "Open AI DJ"]:
        step(f"Settings: {label}", open_and_back(label, scroll=8, wait=3))
        swipe_down()
        swipe_down()
    step("Settings: Move library to another phone", open_and_back("Move library to another phone", scroll=10, wait=4))
    step("Settings: Report a problem", lambda: tap("Report a problem", scroll=10, wait=5)
         and sh(f"am start -n {PKG}/.MainActivity") is not None and time.sleep(3) is None)
    back(2)

    # ---------------------------------------------------------------- report
    print("\n================ UI walk report ================")
    for status, name, detail in results:
        print(f"{status} {name}" + (f" — {detail}" if detail else ""))
    ok = sum(1 for r in results if r[0] == "✅")
    warn = sum(1 for r in results if r[0] == "⚠️")
    bad = [r for r in results if r[0] == "❌"]
    print(f"\n{ok} worked, {warn} not found, {len(bad)} failed")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
