#!/usr/bin/env python3
"""Merges the listening data the apps send (Community: a "sangeet-data" issue per phone per day in a private
repo) into what everyone gets back, keeping only what two or more listeners share:

- together: for a song, the songs that the same listeners like / play / keep in playlists (most shared first)
- top: the songs most listeners play
- chart: Sangeet Top 50, the songs the most listeners played in the last 7 days (the apps fill it up to 50 with
  trending songs while there are few listeners)
- searches: words two or more listeners searched (the catalog crawl looks them up next time)

The website (iPhone) can't hold a token, so it sends a short message to an ntfy.sh topic instead (WEB_TOPIC, kept
there 12 hours); each run reads the last 12 hours. Its song ids are looked up in the catalog state.

Usage: community.py <state.json.gz> <out community.json> <out searches.json> [catalog state.json.gz]
Env: REPORT_TOKEN (reads and closes the issues), DATA_REPO (owner/name; empty = only rebuild from the state),
FOLDER_REPO (a private repo the token may write files in; else DATA_REPO), DATA_TOKEN (optional, CI only).

The searches file also gets the songs listeners play from YouTube and the website's searches with few results, so
the crawl adds them (fewer songs on the iPhone need YouTube, which stops when the phone is locked).
Everything is also kept as sangeet-stats.json, sangeet-listeners.json and sangeet-community.json in a folder that
repo already has (owner, Oct 9: "cms wali repo", "naya folder mat bana, purana hi use kar"), one commit per run with
new data.
"""
import base64
import collections
import gzip
import itertools
import json
import os
import re
import sys
import time
import urllib.request
import zlib

STATE, OUT, SEARCHES = sys.argv[1], sys.argv[2], sys.argv[3]
CATALOG = sys.argv[4] if len(sys.argv) > 4 else ""
MARK = "SANGEET-DATA v1"
API = "https://api.github.com"
KEEP_DAYS = 60
MIN_USERS = 2  # nothing about one listener alone goes out
WEB_TOPIC = os.environ.get("WEB_TOPIC", "https://ntfy.sh/sangeet-web-listens-k4q8")
CHART_DAYS = 7


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
    repo = os.environ.get("DATA_REPO", "")
    if not repo:
        print("COMMUNITY: no private data repo")
        return 0
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
            data["first"] = state["users"].get(uid, {}).get("first", data["seen"])
            data["uploads"] = state["users"].get(uid, {}).get("uploads", 0) + 1
            state["users"][uid] = data  # each upload has the listener's whole picture: the newest wins
            got += 1
            call("PATCH", f"/repos/{repo}/issues/{it['number']}", token, {"state": "closed"})
    return got


IMG = "https://c.saavncdn.com/"
AAC = "https://aac.saavncdn.com/"
WEB_SONG = re.compile(r"js:([\w-]{3,24})")


def catalog_tracks(ids):
    """JioSaavn ids -> the apps' compact track ([source, id, title, singers, language, cover, stream, ms]), from the
    crawl's state (the website sends only ids, its messages are small)."""
    if not ids or not CATALOG or not os.path.exists(CATALOG):
        return {}
    try:
        with gzip.open(CATALOG, "rt", encoding="utf-8") as f:
            songs = json.load(f).get("songs", [])
    except Exception as e:
        print("COMMUNITY: catalog state not readable:", e)
        return {}
    out = {}
    for r in songs:
        if r[0] in ids:
            img = re.sub(r"\d+x\d+(?=\.\w+$)", "500x500", r[5] if r[5].startswith("http") else IMG + r[5])
            media = r[6] if r[6].startswith("http") else f"{AAC}{r[6]}_96.mp4"
            out[r[0]] = ["JIOSAAVN", r[0], r[1], r[2], r[9] if len(r) > 9 else "", img,
                         media + ("#320" if r[7] else ""), int(r[4] or 0) * 1000]
    return out


def fetch_web(state):
    """The website's messages of the last 12 hours (ntfy.sh keeps them that long), one per phone every few hours:
    {"v": 1, "id", "p": "web", "app", "m", "langs", "likes": [id], "plays": [[id, n]] (recent first), "week": [id],
    "searches": [words with few results], "miss": [[title, singer]] (YouTube songs not in the catalog)}.
    Song ids are "js:<JioSaavn id>". Each message counts once (its id is kept two days)."""
    try:
        req = urllib.request.Request(f"{WEB_TOPIC}/json?poll=1&since=12h", headers={"User-Agent": "sangeet-community"})
        with urllib.request.urlopen(req, timeout=60) as r:
            lines = r.read().decode("utf-8", "replace").splitlines()
    except Exception as e:
        print("COMMUNITY: website messages not readable:", e)
        return 0
    now = int(time.time())
    done = state.setdefault("web_seen", {})
    msgs = []
    for line in lines:
        try:
            m = json.loads(line)
        except ValueError:
            continue
        if m.get("event") != "message" or not m.get("id") or m["id"] in done:
            continue
        done[m["id"]] = int(m.get("time") or now)
        try:
            d = json.loads(m.get("message") or "")
        except ValueError:
            continue
        if isinstance(d, dict) and d.get("v") == 1 and re.fullmatch(r"[\w-]{8,64}", str(d.get("id", ""))):
            msgs.append(d)
    for k in [k for k, t in done.items() if now - t > 2 * 86400]:
        del done[k]

    def songs(xs, n):
        out = []
        for x in (xs if isinstance(xs, list) else [])[:n]:
            hit = WEB_SONG.fullmatch(str(x[0] if isinstance(x, list) and x else x))
            if hit:
                out.append((hit.group(1), x[1] if isinstance(x, list) and len(x) > 1 and isinstance(x[1], int) else 1))
        return out

    def texts(xs, n, size=80):
        return [x.strip()[:size] for x in (xs if isinstance(xs, list) else [])[:n] if isinstance(x, str) and x.strip()]

    ids = {i for d in msgs for k in ("likes", "plays", "week") for i, _ in songs(d.get(k), 200)}
    rows = catalog_tracks(ids)
    for d in msgs:
        uid = "web-" + str(d["id"])[:60]
        old = state["users"].get(uid, {})
        plays = [rows[i] + [max(1, min(n, 999))] for i, n in songs(d.get("plays"), 200) if i in rows]
        week = [i for i, _ in songs(d.get("week"), 100) if i in rows]
        have = {t[1] for t in plays}
        plays += [rows[i] + [1] for i in week if i not in have]
        miss = [[str(x[0])[:100], str(x[1])[:80]] for x in (d.get("miss") or [])[:20]
                if isinstance(x, list) and len(x) == 2 and all(isinstance(y, str) for y in x)]
        # A message has what happened since the one before: keep the earlier searches and misses too.
        merged = lambda new, before, n: list(dict.fromkeys([json.dumps(x) for x in new + before]))[:n]
        state["users"][uid] = {
            "id": uid, "p": "web", "app": str(d.get("app", ""))[:20], "m": str(d.get("m", ""))[:40],
            "langs": texts(d.get("langs"), 10, 20), "likes": [rows[i] for i, _ in songs(d.get("likes"), 100) if i in rows],
            "plays": plays, "week": [f"JIOSAAVN:{i}" for i in week], "playlists": [],
            "searches": [json.loads(x) for x in merged(texts(d.get("searches"), 20), old.get("searches", []), 40)],
            "miss": [json.loads(x) for x in merged(miss, old.get("miss", []), 40)],
            "seen": now, "first": old.get("first", now), "uploads": old.get("uploads", 0) + 1,
        }
    print(f"COMMUNITY: {len(msgs)} new website messages ({len(lines)} in the last 12 hours), {len(rows)} songs found")
    return len(msgs)


def key(t):
    return f"{t[0]}:{t[1]}"


# Channel names that aren't singers ("T-Series", "Speed Records"): not put in a catalog search.
LABEL = re.compile(r"music|records|series|films?\b|studio|entertainment|official|vevo|production|channel|\btv\b|media|"
                   r"label|digital|beats|world|india|desi|company|motion|pictures", re.I)


def wanted_query(title, singer):
    """A song played from YouTube -> words to look it up on JioSaavn ("kesariya arijit singh")."""
    t = re.sub(r"\(.*?\)|\[.*?\]|\{.*?\}", " ", str(title or ""))
    t = re.split(r"\s[|•]\s|\s[-–]\s", t)[0]
    t = re.sub(r"\b(official|video|lyrics?|lyrical|full|song|audio|hd|4k|new|latest|feat|ft)\b\.?", " ", t, flags=re.I)
    t = " ".join(t.split())[:60]
    if len(t) < 2:
        return None
    s = str(singer or "").split(",")[0].strip()
    if s and len(s) <= 30 and not LABEL.search(s):
        t = f"{t} {s}"
    return t.lower()


STOPWORDS = {"songs", "song", "my", "playlist", "mix", "the", "and", "for", "best", "new", "all", "fav", "favourite",
             "favorite", "liked", "music", "gaane", "gane", "list", "part", "vol", "hits", "top"}
STATS_MARK = "SANGEET-STATS v1"


def pack(obj):
    return base64.b64encode(zlib.compress(json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode(), 9)[2:-4]).decode()


def owner_stats(state, plays_by, info, searched):
    """Everything for the owner's admin page (kept private: written only to the private data repo)."""
    now = time.time()
    users = []
    for uid, d in sorted(state["users"].items(), key=lambda kv: -kv[1].get("seen", 0)):
        artists = collections.Counter()
        for t in d.get("plays", []):
            for a in str(t[3]).split(", "):
                if a:
                    artists[a] += t[8] if len(t) > 8 else 1
        for t in d.get("likes", []):
            for a in str(t[3]).split(", "):
                if a:
                    artists[a] += 2
        songs = sorted(d.get("plays", []), key=lambda t: -(t[8] if len(t) > 8 else 1))[:5]
        users.append({
            "id": uid[:8], "first": d.get("first", d.get("seen")), "seen": d.get("seen"), "uploads": d.get("uploads", 1),
            "p": d.get("p", ""), "app": d.get("app", ""), "langs": d.get("langs", []), "m": d.get("m", ""),
            "crash": d.get("crash", 0), "use": sorted((d.get("use") or {}).items(), key=lambda kv: -kv[1])[:6],
            "likes": len(d.get("likes", [])), "plays": len(d.get("plays", [])),
            "artists": [a for a, _ in artists.most_common(6)],
            "songs": [f"{t[2]} — {str(t[3]).split(', ')[0]}" for t in songs],
            "playlists": [{"n": p.get("n", ""), "c": len(p.get("t", []))} for p in d.get("playlists", [])][:20],
            "searches": d.get("searches", [])[:15],
        })
    active = lambda days: sum(1 for d in state["users"].values() if now - d.get("seen", 0) <= days * 86400)
    artist_users = collections.Counter()
    for d in state["users"].values():
        mine = {a for t in d.get("plays", []) + d.get("likes", []) for a in str(t[3]).split(", ") if a}
        artist_users.update(mine)
    prev = state.get("prev_plays", {})
    rising = sorted(((n - prev.get(k, 0), k) for k, n in plays_by.items() if n - prev.get(k, 0) > 0), reverse=True)[:20]
    # What the app's screens are used for, crashes and phones (each upload has this phone's totals so far).
    features = collections.Counter()
    crashes = collections.Counter()
    crash_by_version = collections.Counter()
    crash_by_phone = collections.Counter()
    phones = collections.Counter()
    errors = collections.Counter()
    for d in state["users"].values():
        for k, n in (d.get("use") or {}).items():
            if isinstance(n, int):
                features[k] += n
        n = d.get("crash") or 0
        if isinstance(n, int) and n > 0:
            crashes["total"] += n
            crashes["phones"] += 1
            crash_by_version[d.get("app", "?")] += n
            crash_by_phone[d.get("m", "?")] += n
        for e in d.get("crashes") or []:
            errors[str(e)[:160]] += 1
        if d.get("m"):
            phones[d["m"]] += 1
    stats = {
        "built": int(now),
        "features": features.most_common(30),
        "crashes": {"total": crashes["total"], "phones": crashes["phones"], "by_version": crash_by_version.most_common(8),
                    "by_phone": crash_by_phone.most_common(8), "recent": errors.most_common(10)},
        "phones": phones.most_common(15),
        "users": {"total": len(state["users"]), "today": active(1), "week": active(7), "month": active(30)},
        "versions": collections.Counter(d.get("app", "?") for d in state["users"].values()).most_common(10),
        "languages": collections.Counter(l for d in state["users"].values() for l in d.get("langs", [])).most_common(12),
        "top_songs": [[f"{info[k][2]} — {str(info[k][3]).split(', ')[0]}", n] for k, n in plays_by.most_common(30) if k in info],
        "rising": [[f"{info[k][2]} — {str(info[k][3]).split(', ')[0]}", d] for d, k in rising if k in info],
        "top_artists": artist_users.most_common(30),
        "top_searches": searched.most_common(40),
        "people": users[:200],
    }
    state["prev_plays"] = dict(plays_by)
    return stats


def write_stats(stats):
    token, repo = os.environ.get("REPORT_TOKEN", ""), os.environ.get("DATA_REPO", "")
    if not token or not repo:
        return
    while True:
        body = f"{STATS_MARK}\n{pack(stats)}"
        if len(body) <= 64000 or len(stats["people"]) <= 5:
            break
        stats["people"] = stats["people"][: len(stats["people"]) * 2 // 3]
    status, issues = call("GET", f"/repos/{repo}/issues?state=open&per_page=100", token)
    old = next((i for i in (issues or []) if i.get("title") == "sangeet-stats"), None) if status == 200 else None
    if old:
        call("PATCH", f"/repos/{repo}/issues/{old['number']}", token, {"body": body})
    else:
        call("POST", f"/repos/{repo}/issues", token, {"title": "sangeet-stats", "body": body})
    print(f"COMMUNITY: owner stats updated ({stats['users']['total']} listeners, {stats['users']['week']} this week)")


# Owner, Oct 9: "naya folder mat bana, kisi purane ko hi use karle". The files go in a folder the repo already has
# (a data-like name first, else the first one a website wouldn't serve; else the top level), named "sangeet-..."
# so they never touch the project's own files. The chosen folder is kept in the state so it stays the same.
PREFER = ["data", "database", "db", "backup", "backups", "storage", "logs", "files"]
# Folders a website often serves to everyone (or tooling folders): never put listeners' data there.
NOT_THESE = {"public", "static", "assets", "uploads", "www", "dist", "build", "out", "docs", "html", "media", "images",
             "node_modules", "vendor", "github", "git"}
NO_WRITE = ("COMMUNITY: can't write files in {repo} (HTTP {status}); data stays in its issues. To keep the files, the "
            "token needs Contents: Read and write on {repo}")


def pick_folder(dirs, kept):
    if kept is not None and (kept == "" or kept in dirs):
        return kept
    usable = sorted(d for d in dirs if d.lower() not in NOT_THESE and not d.startswith("."))
    for want in PREFER:
        hit = next((d for d in usable if d.lower() == want), None)
        if hit:
            return hit
    return usable[0] if usable else ""


def save_folder(files, got, state):
    """Puts [files] ({name: text}) in an existing folder of a private repo as one commit (Git Data API)."""
    token = os.environ.get("DATA_TOKEN") or os.environ.get("REPORT_TOKEN", "")
    # FOLDER_REPO: a private repo the token may write files in (data-repo.py --files); else the data repo.
    repo = os.environ.get("FOLDER_REPO") or os.environ.get("DATA_REPO", "")
    if not token or not repo:
        return
    status, meta = call("GET", f"/repos/{repo}", token)
    if status != 200 or not meta or not meta.get("private"):
        return
    branch = meta.get("default_branch", "main")
    status, ref = call("GET", f"/repos/{repo}/git/ref/heads/{branch}", token)
    status2, top = call("GET", f"/repos/{repo}/contents?ref={branch}", token)
    if status != 200 or status2 != 200 or not isinstance(top, list):
        print(NO_WRITE.format(repo=repo, status=status if status != 200 else status2))
        return
    kept = state.get("folder", {}).get(repo)
    folder = pick_folder([x["name"] for x in top if x.get("type") == "dir"], kept)
    where = f"{repo}/{folder + '/' if folder else ''}"
    path = lambda name: f"{folder}/{name}" if folder else name
    status, _ = call("GET", f"/repos/{repo}/contents/{path('sangeet-stats.json')}?ref={branch}", token)
    if status == 200 and got == 0:
        print(f"COMMUNITY: no new uploads, {where}sangeet-*.json unchanged")
        return
    head = ref["object"]["sha"]
    _, commit = call("GET", f"/repos/{repo}/git/commits/{head}", token)
    tree = []
    for name, text in files.items():
        status, blob = call("POST", f"/repos/{repo}/git/blobs", token, {"content": text, "encoding": "utf-8"})
        if status != 201:
            print(NO_WRITE.format(repo=repo, status=status))
            return
        tree.append({"path": path(name), "mode": "100644", "type": "blob", "sha": blob["sha"]})
    status, new_tree = call("POST", f"/repos/{repo}/git/trees", token, {"base_tree": commit["tree"]["sha"], "tree": tree})
    if status != 201:
        print(NO_WRITE.format(repo=repo, status=status))
        return
    status, new = call("POST", f"/repos/{repo}/git/commits", token,
                       {"message": f"Sangeet listening data: {got} new uploads", "tree": new_tree["sha"], "parents": [head]})
    if status == 201:
        status, _ = call("PATCH", f"/repos/{repo}/git/refs/heads/{branch}", token, {"sha": new["sha"]})
    if status == 200:
        state.setdefault("folder", {})[repo] = folder
    print(f"COMMUNITY: files in {where} (sangeet-stats.json, sangeet-listeners.json, sangeet-community.json) "
          + ("updated" if status == 200 else f"not updated (HTTP {status})"))


def main():
    state = load_state()
    got = fetch(state)
    got += fetch_web(state)
    # Until build 159 the CI emulator uploaded too; every listener before this reset was a test phone (Oct 9:
    # no owner phone had a build with Community yet). Real listeners re-upload within 6 hours.
    if state.get("v", 1) < 2:
        print(f"COMMUNITY: dropped {len(state['users'])} test-phone listeners (one-time reset)")
        state["users"], state["prev_plays"], state["v"] = {}, {}, 2
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

    # What listeners call their playlists ("gym", "drive", "sad vibes"): songs two or more of them put under the
    # same word. The DJ learns new words from it.
    word_songs = collections.defaultdict(collections.Counter)
    for d in state["users"].values():
        seen_pairs = set()
        for p in d.get("playlists", []):
            words = {w for w in re.findall(r"[a-z]{3,}", str(p.get("n", "")).lower()) if w not in STOPWORDS}
            for t in p.get("t", []):
                for w in words:
                    if (w, key(t)) not in seen_pairs:
                        seen_pairs.add((w, key(t)))
                        word_songs[w][key(t)] += 1
                        info.setdefault(key(t), t[:8])
    learned = {w: [k for k, n in c.most_common(40) if n >= MIN_USERS] for w, c in word_songs.items()}
    learned = {w: ks for w, ks in learned.items() if ks}

    pairs = collections.Counter()
    for mine in songs_of.values():
        for a, b in itertools.combinations(sorted(mine)[:400], 2):
            pairs[(a, b)] += 1
    near = collections.defaultdict(list)
    for (a, b), n in pairs.items():
        if n >= MIN_USERS:
            near[a].append((n, b)); near[b].append((n, a))
    top = [k for k, n in plays_by.most_common(300) if n >= MIN_USERS]

    # Sangeet Top 50: how many listeners played a song in the last 7 days, then how often. "week" = the songs a
    # listener played this week; older app builds don't send it, so their latest plays count instead.
    now = time.time()
    week_by, week_plays = collections.Counter(), collections.Counter()
    for d in state["users"].values():
        if now - d.get("seen", 0) > CHART_DAYS * 86400:
            continue
        counts = {key(t): (t[8] if len(t) > 8 else 1) for t in d.get("plays", [])}
        keys = d.get("week")
        if not isinstance(keys, list):
            keys = [key(t) for t in d.get("plays", [])[:60]]
        for k in set(map(str, keys)):
            if k in info:
                week_by[k] += 1
                week_plays[k] += counts.get(k, 1)
    chart = sorted((k for k, n in week_by.items() if n >= MIN_USERS), key=lambda k: (-week_by[k], -week_plays[k]))[:200]

    used = sorted(set(top) | set(chart) | set(near) | {b for v in near.values() for _, b in v} | {k for ks in learned.values() for k in ks})
    index = {k: i for i, k in enumerate(used)}
    out = {
        "listeners": len(songs_of),
        "tracks": [info[k] for k in used],
        "together": {str(index[a]): [index[b] for _, b in sorted(v, reverse=True)[:20]] for a, v in near.items()},
        "top": [index[k] for k in top],
        "chart": [index[k] for k in chart],
        "searches": [q for q, n in searched.most_common(300) if n >= MIN_USERS],
        "words": {w: [index[k] for k in ks] for w, ks in learned.items()},
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    # Songs that aren't in the catalog yet: what listeners play from YouTube, and the website's YouTube songs.
    wanted = collections.Counter()
    for d in state["users"].values():
        for t in d.get("plays", [])[:200]:
            if t[0] == "YOUTUBE" and (q := wanted_query(t[2], t[3])):
                wanted[q] += 1
        for t in d.get("miss", []):
            if isinstance(t, list) and len(t) == 2 and (q := wanted_query(t[0], t[1])):
                wanted[q] += 2
    # Every listener's searches and those songs (not published): the catalog crawl looks them up on JioSaavn.
    looks = [q for q, _ in searched.most_common(500)]
    looks += [q for q, _ in wanted.most_common(300) if q not in searched]
    with open(SEARCHES, "w", encoding="utf-8") as f:
        json.dump(looks, f, ensure_ascii=False)
    print(f"COMMUNITY: Top 50 has {len(chart)} songs from listeners; the crawl looks up {len(looks)} searches and songs"
          f" ({len(wanted)} played from YouTube or missing on the website)")
    stats = owner_stats(state, plays_by, info, searched)
    files = {
        "sangeet-stats.json": json.dumps(stats, ensure_ascii=False, indent=1),
        "sangeet-listeners.json": json.dumps(state["users"], ensure_ascii=False, separators=(",", ":")),
        "sangeet-community.json": json.dumps(out, ensure_ascii=False, separators=(",", ":")),
    }
    write_stats(stats)
    save_folder(files, got, state)
    with gzip.open(STATE, "wt", encoding="utf-8") as f:
        json.dump(state, f, separators=(",", ":"))
    print(f"COMMUNITY: {got} new uploads, {len(songs_of)} listeners, {len(used)} songs shared, {len(out['searches'])} shared searches")


if __name__ == "__main__":
    main()
