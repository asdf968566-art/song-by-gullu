#!/usr/bin/env python3
"""One-time link to the owner's YouTube channel.

Two ways:
- Browser login (needs the "Desktop app" OAuth client in YT_DESKTOP_ID / YT_DESKTOP_SECRET). It may also reply to
  comments (scope youtube.force-ssl), which the TV flow below can't ask for. `youtube_login.py <state>` writes the
  sign-in link to login-code.txt; the owner opens it, taps Allow, and copies the address the browser ends on
  (http://localhost/?code=…, the page itself doesn't load). `youtube_login.py <state> <that address or code>` turns it
  into the login, saved as <state>/token2.enc (encrypted with YT_DESKTOP_SECRET).
- TV login ("TVs and Limited Input devices" client in YT_CLIENT_ID / YT_CLIENT_SECRET): the owner opens
  google.com/device and types a code. Saved as <state>/token.enc. Uploads only; no comment replies.
Only this repo's workflows, which hold the secrets, can read the saved logins.

Usage: youtube_login.py <state dir> [code or http://localhost/?code=… address]
"""
import json, os, subprocess, sys, time, urllib.parse, urllib.request

STATE = sys.argv[1]
GIVEN = sys.argv[2].strip() if len(sys.argv) > 2 else ""
DID, DSECRET = os.environ.get("YT_DESKTOP_ID", ""), os.environ.get("YT_DESKTOP_SECRET", "")
CID, SECRET = os.environ.get("YT_CLIENT_ID", ""), os.environ.get("YT_CLIENT_SECRET", "")
SCOPES = "https://www.googleapis.com/auth/youtube https://www.googleapis.com/auth/youtube.force-ssl"
REDIRECT = "http://localhost"
CODE_FILE = os.path.join(os.path.dirname(STATE.rstrip("/")) or ".", "login-code.txt")


def save(refresh, name, secret_env):
    os.makedirs(STATE, exist_ok=True)
    subprocess.run(["openssl", "enc", "-aes-256-cbc", "-pbkdf2", "-salt", "-a", "-A",
                    "-out", os.path.join(STATE, name), "-pass", "env:" + secret_env],
                   input=refresh, text=True, check=True)


def show_channel(access):
    req = urllib.request.Request("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true",
                                 headers={"Authorization": "Bearer " + access})
    try:
        ch = json.loads(urllib.request.urlopen(req, timeout=30).read()).get("items", [])
        for c in ch:
            print("LINKED CHANNEL:", c["snippet"]["title"])
        if not ch:
            print("LINKED, but this Google account has no YouTube channel yet: make one on youtube.com first")
    except Exception as e:
        print("LINKED (channel check failed:", e, ")")


def post(url, fields):
    req = urllib.request.Request(url, data=urllib.parse.urlencode(fields).encode(),
                                 headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b"{}")


def browser_login():
    if not GIVEN:
        link = "https://accounts.google.com/o/oauth2/v2/auth?" + urllib.parse.urlencode(
            {"client_id": DID, "redirect_uri": REDIRECT, "response_type": "code", "scope": SCOPES,
             "access_type": "offline", "prompt": "consent"})
        print("=" * 50)
        print("YOUTUBE LOGIN LINK:", link)
        print("=" * 50, flush=True)
        open(CODE_FILE, "w").write(f"{link}\n{time.time():.0f}\n")
        return
    code = GIVEN
    if "code=" in code:
        code = urllib.parse.parse_qs(urllib.parse.urlparse(code).query).get("code", [""])[0]
    else:
        code = urllib.parse.unquote(code)
    t = post("https://oauth2.googleapis.com/token",
             {"client_id": DID, "client_secret": DSECRET, "code": code, "redirect_uri": REDIRECT,
              "grant_type": "authorization_code"})
    if "refresh_token" not in t:
        sys.exit(f"Login failed (open the link again for a new one): {t}")
    save(t["refresh_token"], "token2.enc", "YT_DESKTOP_SECRET")
    print("SCOPES:", t.get("scope"))
    show_channel(t["access_token"])


def tv_login():
    d = post("https://oauth2.googleapis.com/device/code",
             {"client_id": CID, "scope": "https://www.googleapis.com/auth/youtube"})
    if "user_code" not in d:
        sys.exit(f"Google refused: {d}")
    print("=" * 50)
    print(f"YOUTUBE LOGIN CODE: {d['user_code']}")
    print(f"Open {d.get('verification_url', 'https://www.google.com/device')} and type the code")
    print("=" * 50, flush=True)
    # A running job's log can't be read through the API, so the code also goes to the file the workflow pushes.
    open(CODE_FILE, "w").write(
        f"{d['user_code']}\n{d.get('verification_url', 'https://www.google.com/device')}\n{time.time():.0f}\n")
    deadline = time.time() + min(int(d.get("expires_in", 1800)), 1700)
    wait = int(d.get("interval", 5))
    while time.time() < deadline:
        time.sleep(wait)
        t = post("https://oauth2.googleapis.com/token",
                 {"client_id": CID, "client_secret": SECRET, "device_code": d["device_code"],
                  "grant_type": "urn:ietf:params:oauth:grant-type:device_code"})
        if "refresh_token" in t:
            save(t["refresh_token"], "token.enc", "YT_CLIENT_SECRET")
            show_channel(t["access_token"])
            sys.exit(0)
        err = t.get("error")
        if err == "slow_down":
            wait += 5
        elif err != "authorization_pending":
            sys.exit(f"Login failed: {t}")
    sys.exit("The code expired; run the login again")


if DID and DSECRET:
    browser_login()
elif CID and SECRET:
    tv_login()
else:
    sys.exit("Add the repo secrets YT_DESKTOP_ID / YT_DESKTOP_SECRET (or YT_CLIENT_ID / YT_CLIENT_SECRET) first")
