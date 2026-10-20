#!/usr/bin/env python3
"""One-time link to the owner's YouTube channel (Google's "sign in on another device" flow).

The log shows a code; the owner opens google.com/device, signs in with the channel's Google account, types the code
and taps Allow. The login (refresh token) is saved encrypted with the OAuth client secret in <state dir>/token.enc,
so only this repo's workflows, which hold that secret, can use it.

Usage: youtube_login.py <state dir>
"""
import json, os, subprocess, sys, time, urllib.parse, urllib.request

STATE = sys.argv[1]
CID, SECRET = os.environ.get("YT_CLIENT_ID", ""), os.environ.get("YT_CLIENT_SECRET", "")
if not (CID and SECRET):
    sys.exit("Add the repo secrets YT_CLIENT_ID and YT_CLIENT_SECRET first")


def post(url, fields):
    req = urllib.request.Request(url, data=urllib.parse.urlencode(fields).encode(),
                                 headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read())
    except urllib.error.HTTPError as e:
        return json.loads(e.read() or b"{}")


d = post("https://oauth2.googleapis.com/device/code",
         {"client_id": CID, "scope": "https://www.googleapis.com/auth/youtube"})
if "user_code" not in d:
    sys.exit(f"Google refused: {d}")
print("=" * 50)
print(f"YOUTUBE LOGIN CODE: {d['user_code']}")
print(f"Open {d.get('verification_url', 'https://www.google.com/device')} and type the code")
print("=" * 50, flush=True)
# A running job's log can't be read through the API, so the code also goes to the file the workflow pushes.
open(os.path.join(os.path.dirname(STATE.rstrip("/")) or ".", "login-code.txt"), "w").write(
    f"{d['user_code']}\n{d.get('verification_url', 'https://www.google.com/device')}\n{time.time():.0f}\n")
deadline = time.time() + min(int(d.get("expires_in", 1800)), 1700)
wait = int(d.get("interval", 5))
while time.time() < deadline:
    time.sleep(wait)
    t = post("https://oauth2.googleapis.com/token",
             {"client_id": CID, "client_secret": SECRET, "device_code": d["device_code"],
              "grant_type": "urn:ietf:params:oauth:grant-type:device_code"})
    if "refresh_token" in t:
        os.makedirs(STATE, exist_ok=True)
        subprocess.run(["openssl", "enc", "-aes-256-cbc", "-pbkdf2", "-salt", "-a", "-A",
                        "-out", os.path.join(STATE, "token.enc"), "-pass", "env:YT_CLIENT_SECRET"],
                       input=t["refresh_token"], text=True, check=True)
        req = urllib.request.Request("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true",
                                     headers={"Authorization": "Bearer " + t["access_token"]})
        try:
            ch = json.loads(urllib.request.urlopen(req, timeout=30).read()).get("items", [])
            for c in ch:
                print("LINKED CHANNEL:", c["snippet"]["title"])
            if not ch:
                print("LINKED, but this Google account has no YouTube channel yet: make one on youtube.com first")
        except Exception as e:
            print("LINKED (channel check failed:", e, ")")
        sys.exit(0)
    err = t.get("error")
    if err == "slow_down":
        wait += 5
    elif err != "authorization_pending":
        sys.exit(f"Login failed: {t}")
sys.exit("The code expired; run the login again")
