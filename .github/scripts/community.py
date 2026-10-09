#!/usr/bin/env python3
"""Merges the listening data the apps send (Community: a "sangeet-data" issue per phone per day in a private
repo) into what everyone gets back, keeping only what two or more listeners share:

- together: for a song, the songs that the same listeners like / play / keep in playlists (most shared first)
- top: the songs most listeners play
- searches: words two or more listeners searched (the catalog crawl looks them up next time)

Usage: community.py <state.json.gz> <out community.json> <out searches.json>
Env: REPORT_TOKEN (reads and closes the issues), DATA_REPO (owner/name; empty = only rebuild from the state).
"""
import base64
import collections
import gzip
import itertools
import json
import os
import sys
import time
import urllib.request
import zlib

STATE, OUT, SEARCHES = sys.argv[1], sys.argv[2], sys.argv[3]
MARK = "SANGEET-DATA v1"
API = "https://api.github.com"
KEEP_DAYS = 60
MIN_USERS = 2  # nothing about one listener alone goes out


def call(method, url, token, body=None):
    req = urllib.request.Request(API + url, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                                          "Content-Type": "application/json", "User-Agent": "sangeet-community"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception as e:
        print("community:", e)
        return 0, None


def load_state():
    try:
        with gzip.open(STATE, "rt", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {"users": {}}


def fetch(state):
    token = os.environ.get("REPORT_TOKEN", "")
    repo = os.environ.get("DATA_REPO") or "vivekyadav200405-cpu/sangeet-data"
    status, meta = call("GET", f"/repos/{repo}", token) if token else (0, None)
    if status != 200 or not meta:
        print(f"COMMUNITY: data repo {repo} not found (make it as a private repo on that account)")
        return 0
    if not meta.get("private"):
        print(f"COMMUNITY: {repo} is PUBLIC, not reading listening data from it (make it private)")
        return 0
    got = 0
    for page in range(1, 11):
        status, issues = call("GET", f"/repos/{repo}/issues?state=open&per_page=100&page={page}", token)
        if status != 200 or not issues:
            break
        for it in issues:
            body = it.get("body") or ""
            if "pull_request" in it or not body.startswith(MARK):
                continue
            try:
                data = json.loads(zlib.decompress(base64.b64decode(body[len(MARK):].strip()), -15))
                uid = str(data["id"])[:64]
            except Exception as e:
                print("COMMUNITY: bad upload", it["number"], e)
                continue
            data["seen"] = int(time.time())
            state["users"][uid] = data  # each upload has the listener's whole picture: the newest wins
            got += 1
            call("PATCH", f"/repos/{repo}/issues/{it['number']}", token, {"state": "closed"})
    return got


def key(t):
    return f"{t[0]}:{t[1]}"


def main():
    state = load_state()
    got = fetch(state)
    cutoff = time.time() - KEEP_DAYS * 86400
    state["users"] = {u: d for u, d in state["users"].items() if d.get("seen", 0) >= cutoff}
    os.makedirs(os.path.dirname(STATE) or ".", exist_ok=True)
    with gzip.open(STATE, "wt", encoding="utf-8") as f:
        json.dump(state, f, separators=(",", ":"))

    info = {}  # song key -> its compact track (the fullest one seen)
    songs_of = {}  # listener -> their songs
    plays_by = collections.Counter()  # song key -> how many listeners play it
    searched = collections.Counter()
    for uid, d in state["users"].items():
        mine = set()
        for t in d.get("likes", []):
            mine.add(key(t)); info.setdefault(key(t), t[:8])
        for t in d.get("plays", []):
            k = key(t)
            info.setdefault(k, t[:8])
            plays_by[k] += 1
            if (t[8] if len(t) > 8 else 1) >= 2:
                mine.add(k)
        for p in d.get("playlists", []):
            for t in p.get("t", []):
                mine.add(key(t)); info.setdefault(key(t), t[:8])
        songs_of[uid] = mine
        for q in {s.strip().lower() for s in d.get("searches", []) if 2 <= len(s.strip()) <= 60}:
            searched[q] += 1

    pairs = collections.Counter()
    for mine in songs_of.values():
        for a, b in itertools.combinations(sorted(mine)[:400], 2):
            pairs[(a, b)] += 1
    near = collections.defaultdict(list)
    for (a, b), n in pairs.items():
        if n >= MIN_USERS:
            near[a].append((n, b)); near[b].append((n, a))
    top = [k for k, n in plays_by.most_common(300) if n >= MIN_USERS]
    used = sorted(set(top) | set(near) | {b for v in near.values() for _, b in v})
    index = {k: i for i, k in enumerate(used)}
    out = {
        "listeners": len(songs_of),
        "tracks": [info[k] for k in used],
        "together": {str(index[a]): [index[b] for _, b in sorted(v, reverse=True)[:20]] for a, v in near.items()},
        "top": [index[k] for k in top],
        "searches": [q for q, n in searched.most_common(300) if n >= MIN_USERS],
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    # Every listener's searches (not published): the catalog crawl looks these words up on JioSaavn.
    with open(SEARCHES, "w", encoding="utf-8") as f:
        json.dump([q for q, _ in searched.most_common(500)], f, ensure_ascii=False)
    print(f"COMMUNITY: {got} new uploads, {len(songs_of)} listeners, {len(used)} songs shared, {len(out['searches'])} shared searches")


if __name__ == "__main__":
    main()
