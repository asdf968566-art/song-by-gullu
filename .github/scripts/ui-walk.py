#!/usr/bin/env python3
"""Taps through every tab, button and option of Sangeet on the emulator, like a user would.

After each step it checks that the app is still alive (no crash). At the end it prints a report:
  ✅ worked   ⚠️ button not found on screen   ❌ crash / wrong result
Exit code 1 if anything crashed or a check failed.
"""
import html
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
        attr = lambda k: html.unescape((re.search(rf'{k}="([^"]*)"', n) or [None, ""])[1])
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


def toggle_twice(label, scroll=8):
    """Flip a switch and flip it back (settings stay as they were)."""
    return lambda: tap(label, scroll=scroll, wait=1.5) and tap(label, scroll=0, wait=1.5)


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
    step("Home: Top charts → See all", lambda: tap("See all", scroll=4, wait=5) and (back() or True))

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
    def clear_search():
        if not tap("Clear", wait=2):
            return False
        time.sleep(2)
        return True
    step("Search: recent searches after clearing", clear_search,
         lambda: (find("Recent searches") is not None and find("Song •", contains=True) is not None,
                  "picked song and words listed"))
    step("Search: remove one recent search", lambda: tap("Remove from recent", wait=2),
         lambda: (alive(), "removed"))

    def lyric_line():
        # A line from the middle of "Tujhe Dekha To" (not its name).
        tap("Clear", wait=1)
        n = find("What do you want to listen to?", contains=True)
        if not n:
            return False
        sh(f"input tap {n['x']} {n['y']}")
        time.sleep(1)
        sh("input text 'pyaar%shota%shai%sdeewana%ssanam'")
        sh("input keyevent KEYCODE_ENTER")
        time.sleep(15)
        return True
    if step("Search: by a line of the lyrics", lyric_line, lambda: (alive(), "searched")):
        # Genius (the lyrics search) can be blocked from CI servers, so this is only reported.
        hit = find("Songs with these lyrics", contains=True, scroll=2)
        print(("✅" if hit else "⚠️") + " lyrics search section " + ("shown" if hit else "not shown (Genius may be blocked here)"))
    step("Search: voice search button", lambda: tap("Voice search", wait=3), lambda: (alive(), "opens the speech prompt or says it isn't available"))
    back()
    back()

    # ---------------------------------------------------------------- Library
    def wrapped_button():
        fresh()
        go_tab("Your Library")
        if not tap("Your Stats", scroll=3, wait=4):
            return False
        # The Wrapped button shows once there is at least a minute of listening.
        ok = find("Share my Wrapped", contains=True, scroll=1) is not None or find("No stats yet", contains=True) is not None
        back()
        return ok
    step("Library: Your Stats has Share my Wrapped", wrapped_button)
    def blend_dialog():
        fresh()
        go_tab("Your Library")
        if not tap("Blend with a friend", scroll=3, wait=2):
            return False
        ok = find("Send link") is not None
        tap("Cancel", wait=1)
        return ok
    step("Library: Blend with a friend", blend_dialog)
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
    def speed_is(v):
        for _ in range(12):
            if f"speed={v}" in media():
                return True
            time.sleep(1)
        return False
    step("Now Playing: speed 1.25x", speed_125, lambda: (speed_is("1.25"), "player speed is 1.25"))
    step("Now Playing: speed back to 1x", lambda: tap("Speed", wait=2) and tap("1x", wait=1) and tap("Apply", wait=2),
         lambda: (speed_is("1.0"), "player speed is 1.0"))
    step("Now Playing: Sleep timer", lambda: tap("Sleep timer", wait=2) and (back() or True))
    step("Now Playing: Queue", lambda: tap("Queue", scroll=2, wait=2) and (back() or True))
    step("Now Playing: Car mode", lambda: tap("Car mode", wait=2),
         lambda: (find("Close car mode") is not None and find("Play/Pause") is not None, "big buttons shown"))
    tap("Close car mode", wait=2)
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
    def download():
        adb("logcat", "-c")
        if not menu("Download", then_back=False)():
            return False
        for _ in range(60):
            if "download done:" in adb("logcat", "-d"):
                return True
            time.sleep(2)
        return True
    def download_check():
        log = adb("logcat", "-d")
        m = re.search(r"download done: .*", log)
        if m:
            return True, m.group(0)
        # Explain why: the worker's log lines and what the menu showed.
        # Only the app's own lines and WorkManager's (Android's Bluetooth service also logs "com.sangeet.player"
        # every 3 s, which used to push the useful lines out).
        why = [l for l in log.splitlines()
               if re.search(r"\s[VDIWEF] (Sangeet|WM-\S+|DownloadWorker)\s*:", l) or "already downloaded" in l.lower()][-20:]
        return False, "not finished in 2 min; now playing: " + now_title() + "\n" + "\n".join(why)
    step("Song menu: Download", download, download_check)
    for item in ["Like", "Play next", "Add to queue", "Add to playlist", "Start radio", "Share"]:
        step(f"Song menu: {item}", menu(item, then_back=item in ("Add to playlist", "Share")))
    step("Song menu: Go to artist", menu("Go to artist", wait=6))

    back(2)

    # Download status screen: the downloaded song must be listed there.
    fresh()
    go_tab("Your Library")
    step("Downloads screen: open", lambda: tap("Downloads", scroll=3, wait=4),
         lambda: (find("Saved on this phone", contains=True) is not None, "saved songs listed"))
    step("Downloads screen: play downloads", lambda: tap("Play downloads", wait=6), lambda: (playing(), f"playing: {now_title()}"))
    back()

    # ---------------------------------------------------------------- tabs: tapping a tab goes to its own screen
    fresh()
    def home_from_settings():
        go_tab("Home")
        if not tap("Settings", wait=3):
            return False
        return go_tab("Home")
    step("Tab: Home from inside Settings goes back to Home", home_from_settings,
         lambda: (find("Good ", contains=True) is not None and find("Audio quality", contains=True) is None, "Home screen shown"))

    def languages_from_home():
        go_tab("Home")
        return tap("Song languages", wait=3)
    def languages_shown():
        ok = find("Haryanvi", contains=True, scroll=1) is not None
        if not ok:
            print("[languages] on screen: " + ", ".join(sorted({n["text"] or n["desc"] for n in nodes(dump()) if n["text"] or n["desc"]})[:40]))
            with open("out/languages.png", "wb") as f:
                subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f)
        return ok, "language chips shown"
    step("Home: Song languages (outside Music sources)", languages_from_home, languages_shown)
    tap("Done", wait=3)

    # ---------------------------------------------------------------- Settings
    fresh()
    go_tab("Home")
    step("Settings: open", lambda: tap("Settings", wait=3))
    for label in ["Normalize volume", "Skip silence", "Hook preview in For You", "Resume on headphones", "Smart downloads",
                  "Autoplay", "Auto playlists", "Fetch lyrics automatically", "Offline mode", "Download on Wi-Fi only",
                  "New song alerts", "Free online AI", "Update automatically"]:
        far = label in ("Free online AI", "Update automatically")  # further down (AI DJ, App update)
        step(f"Settings switch: {label}", toggle_twice(label, 14 if far else 8))
        for _ in range(6 if far else 2):
            swipe_down()  # back to the top for the next search
    def phone_copy():
        out = sh("content query --uri content://media/external/audio/media --projection _display_name:relative_path")
        hits = [l for l in out.splitlines() if "Music/Sangeet" in l]
        return bool(hits), (hits[0].strip()[:120] if hits else "nothing in Music/Sangeet")
    step("Settings: Save downloads to phone storage", lambda: tap("Save downloads to phone storage", scroll=10, wait=6), phone_copy)
    step("Settings: Save downloads to phone storage (off again)", lambda: tap("Save downloads to phone storage", wait=2))
    swipe_down()
    swipe_down()
    def wrong_password():
        if not tap("Music sources", scroll=10, wait=3):
            return False
        n = find("Password")
        if not n:
            return False
        sh(f"input tap {n['x']} {n['y']}")
        sh("input text 1111")
        time.sleep(1)
        tap("Unlock", wait=4)
        return True
    step("Settings: Music sources is locked (wrong password refused)", wrong_password,
         lambda: (find("Wrong password") is not None, "wrong password refused"))
    back(2)

    def to_top():
        # Settings is long (song languages at the top): scroll all the way up before looking.
        for _ in range(6):
            swipe_down()
    to_top()
    for label in ["Theme", "Equalizer & Bass boost", "Alarm", "Open AI DJ", "Sync with another phone"]:
        step(f"Settings: {label}", open_and_back(label, scroll=10, wait=3))
        to_top()
    step("Settings: Move library to another phone", open_and_back("Move library to another phone", scroll=10, wait=4))
    def report_form():
        if not tap("Report a problem", scroll=10, wait=3):
            return False
        n = find("What went wrong?", contains=True)
        if not n:
            return False
        sh(f"input tap {n['x']} {n['y']}")
        sh("input text test")
        time.sleep(1)
        return True
    step("Settings: Report a problem (in-app form, not sent)", report_form,
         lambda: (find("WhatsApp", contains=True) is not None, "form with send buttons"))
    back(2)
    back(2)

    # ---------------------------------------------------------------- themes: Liquid Glass on every page
    def shot(name):
        path = f"out/theme-{name}.png"
        with open(path, "wb") as f:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=f)
        # Also into the log as a small JPEG (artifacts can't always be opened), between SHOT markers.
        # A screenshot problem never fails the walk.
        try:
            import base64
            import io
            try:
                from PIL import Image
            except ImportError:
                print(f"(screenshot {name} not logged: Pillow missing)")
                return True
            img = Image.open(path).convert("RGB")
            img.thumbnail((300, 700))
            buf = io.BytesIO()
            img.save(buf, "JPEG", quality=45)
            data = base64.b64encode(buf.getvalue()).decode()
            print(f"SHOT-BEGIN {name}")
            for i in range(0, len(data), 3000):
                print("SHOT " + data[i:i + 3000])
            print(f"SHOT-END {name}")
        except Exception as e:  # noqa: BLE001
            print(f"(screenshot {name} not logged: {e})")
        return True

    def on_screen():
        return ", ".join(sorted({n["text"] or n["desc"] for n in nodes(dump()) if n["text"] or n["desc"]})[:40])

    def set_theme(name):
        def act():
            go_tab("Home")
            for label, scroll in [("Settings", 0), ("Theme", 8), (name, 6)]:
                if not tap(label, scroll=scroll, wait=3):
                    print(f"[theme] '{label}' not found; on screen: {on_screen()}")
                    shot(f"missing-{label.replace(' ', '')}")
                    return False
            shot(f"picker-{name.replace(' ', '')}")
            back(2)
            return True
        return act

    fresh(play=True)
    step("Theme: choose Liquid Glass", set_theme("Liquid Glass"))
    for t in ["Home", "Search", "Your Library", "For You"]:
        step(f"Liquid Glass: {t} page", lambda t=t: go_tab(t) and shot(t.replace(" ", "")))
    step("Liquid Glass: Now Playing", lambda: open_now_playing() and shot("NowPlaying"))
    step("Liquid Glass: song menu sheet", lambda: tap("Options", wait=2) and shot("Sheet"))
    back()
    # Close Now Playing too (its down arrow), so the tabs are reachable again.
    if find("Close"):
        tap("Close", wait=2)
    # Light mode of Liquid Glass.
    def mode(name):
        def act():
            go_tab("Home")
            if not tap("Settings", wait=3) or not tap("Theme", scroll=8, wait=3) or not tap(name, scroll=6, wait=3):
                return False
            back(2)
            return True
        return act
    step("Liquid Glass: light mode", mode("Light"))
    for t in ["Home", "Search", "Your Library"]:
        step(f"Liquid Glass light: {t} page", lambda t=t: go_tab(t) and shot("light-" + t.replace(" ", "")))
    step("Liquid Glass light: Now Playing", lambda: open_now_playing() and shot("light-NowPlaying"))
    if find("Close"):
        tap("Close", wait=2)
    step("Liquid Glass: dark mode again", mode("Dark"))
    step("Theme: back to Classic Dark", set_theme("Classic Dark"))

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
