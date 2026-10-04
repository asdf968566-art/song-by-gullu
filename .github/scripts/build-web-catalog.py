#!/usr/bin/env python3
"""Builds Sangeet's song catalog from JioSaavn: charts, playlists, then every singer's full song list.

Browsers can't call JioSaavn (no CORS), so GitHub Actions crawls it and publishes static JSON.
The crawl state is kept between runs (actions/cache), so the catalog grows every night.

Usage: build-web-catalog.py <out dir> <state file> <minutes to crawl>

Output (<out>):
  index.json                 {"build", "total", "rows": ROWS, "languages": {lang: count}}
  <lang>.json                popular songs + playlists of one language (the app loads these first)
                             {"songs": [row...], "playlists": [[id, title, subtitle, image, [song indexes], isChart]]}
  r/<n>.json                 every song, ROWS per file, in a fixed order: [row + language]
  i/<key>.json               search index: {token: [song numbers, delta encoded]} for tokens whose first 3 letters map to <key>
Song row: [id, title, artist, album, seconds, image, media, has320, year]
  image: path after https://c.saavncdn.com/ (150x150 size), or a full URL
  media: path after https://aac.saavncdn.com/ without "_96.mp4", or a full URL
"""
import base64
import collections
import concurrent.futures as cf
import gzip
import html
import json
import os
import random
import re
import shutil
import sys
import threading
import time
import urllib.parse
import urllib.request

from Crypto.Cipher import DES

OUT = sys.argv[1] if len(sys.argv) > 1 else "SangeetWeb/data"
STATE = sys.argv[2] if len(sys.argv) > 2 else "catalog-state/state.json.gz"
MINUTES = float(sys.argv[3]) if len(sys.argv) > 3 else 30
API = "https://www.jiosaavn.com/api.php"
IMG = "https://c.saavncdn.com/"
AAC = "https://aac.saavncdn.com/"
ROWS = 250

# Mostly Hindi, then Punjabi and Haryanvi, some English. Other languages: a small popular set only.
DEEP = {"hindi": 1.0, "punjabi": 0.55, "haryanvi": 0.3, "english": 0.15}
CORE_CAP = {"hindi": 60000, "punjabi": 30000, "haryanvi": 15000, "english": 10000}
SMALL = {"bhojpuri": 2500, "tamil": 1500, "telugu": 1500, "marathi": 1500, "bengali": 1500, "gujarati": 1200}
LANGS = list(DEEP) + list(SMALL)
MOODS = ["hits", "romantic", "sad", "party", "90s", "latest", "lofi", "workout", "devotional", "wedding",
         "old", "love", "chill", "dance", "top 50", "new", "retro", "drive", "rap", "unplugged"]


# ------------------------------------------------------------------ http

def call(method, params, lang=None, tries=3):
    q = {"__call": method, "_format": "json", "_marker": "0", "api_version": "4", "ctx": "web6dot0", **params}
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(q), headers={
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124 Safari/537.36",
        **({"Cookie": f"L={lang}"} if lang else {}),
    })
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.loads(r.read().decode("utf-8", "replace"))
        except Exception:
            time.sleep(1.5 * (i + 1))
    return None


def items(root, keys):
    if isinstance(root, list):
        return root
    if isinstance(root, dict):
        for k in keys:
            v = root.get(k)
            if isinstance(v, list):
                return v
            if isinstance(v, dict) and isinstance(v.get("songs"), list):
                return v["songs"]
    return []


_des = DES.new(b"38346591", DES.MODE_ECB)


def decrypt(enc):
    try:
        raw = _des.decrypt(base64.b64decode(enc.strip()))
        raw = raw[: -raw[-1]] if 0 < raw[-1] <= 8 else raw
        return raw.decode().strip().replace("http://", "https://")
    except Exception:
        return None


def un(s):
    return html.unescape(s or "").strip()


def norm(s):
    s = re.sub(r"\(.*?\)|\[.*?\]", " ", (s or "").lower())
    return " ".join(re.findall(r"\w+", s))


# ------------------------------------------------------------------ state (kept between runs)

class Catalog:
    def __init__(self):
        self.lock = threading.Lock()
        self.songs = {}      # id -> [row..., lang]
        self.order = []      # ids in their permanent order
        self.keys = set()    # "title|artist" to skip duplicate versions
        self.pop = collections.Counter()
        self.artists = {}    # artist id -> {"lang", "page", "done", "seen"}
        self.albums_done = set()
        self.album_queue = {}  # album id -> lang

    def load(self, path):
        if not os.path.exists(path):
            return
        with gzip.open(path, "rt", encoding="utf-8") as f:
            d = json.load(f)
        self.order = d["order"]
        self.songs = {r[0]: r for r in d["songs"]}
        self.keys = {norm(r[1]) + "|" + norm(r[2].split(", ")[0]) for r in d["songs"]}
        self.artists = d.get("artists", {})
        self.albums_done = set(d.get("albums_done", []))
        self.album_queue = d.get("album_queue", {})
        print(f"state: {len(self.order)} songs, {len(self.artists)} artists", flush=True)

    def save(self, path):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with gzip.open(path, "wt", encoding="utf-8") as f:
            json.dump({"order": self.order, "songs": [self.songs[i] for i in self.order], "artists": self.artists,
                       "albums_done": sorted(self.albums_done), "album_queue": self.album_queue},
                      f, ensure_ascii=False, separators=(",", ":"))

    def add(self, o, pop=0, small=True):
        """Adds a JioSaavn song object. Returns (id, lang, is_new) or None.
        small=False: skip the "few songs only" languages (used by the deep crawl)."""
        if not isinstance(o, dict) or o.get("type", "song") != "song" or not o.get("id"):
            return None
        lang = (o.get("language") or "").lower()
        if lang not in DEEP and (lang not in SMALL or not small):
            return None
        sid = o["id"]
        info = o.get("more_info") or {}
        with self.lock:
            known = sid in self.songs
        if not known:
            media = decrypt(info.get("encrypted_media_url") or o.get("encrypted_media_url") or "")
            if not media:
                return None
            if media.startswith(AAC) and media.endswith("_96.mp4"):
                media = media[len(AAC):-len("_96.mp4")]
            img = re.sub(r"\d+x\d+(?=\.\w+$)", "150x150", o.get("image") or "")
            if img.startswith(IMG):
                img = img[len(IMG):]
            am = info.get("artistMap") or {}
            artist = ", ".join(a.get("name", "") for a in (am.get("primary_artists") or []) if a.get("name")) \
                or info.get("music") or (o.get("subtitle") or "").split(" - ")[0] or "Unknown"
            try:
                dur = int(info.get("duration") or o.get("duration") or 0)
            except ValueError:
                dur = 0
            try:
                year = int(o.get("year") or 0)
            except ValueError:
                year = 0
            row = [sid, un(o.get("title")), un(artist), un(info.get("album") or o.get("album")),
                   dur, img, media, 1 if str(info.get("320kbps")) == "true" else 0, year, lang]
            key = norm(row[1]) + "|" + norm(row[2].split(", ")[0])
            with self.lock:
                if sid in self.songs:
                    known = True
                elif key in self.keys:
                    return None  # same song, another version
                else:
                    self.keys.add(key)
                    self.songs[sid] = row
                    self.order.append(sid)
        # Remember singers and albums to crawl next.
        if lang in DEEP:
            am = info.get("artistMap") or {}
            with self.lock:
                for a in (am.get("primary_artists") or []) + (am.get("featured_artists") or []):
                    aid = a.get("id")
                    if aid and aid not in self.artists:
                        self.artists[aid] = {"lang": lang, "page": 0, "done": False, "seen": 0}
                    if aid:
                        self.artists[aid]["seen"] += 1
                alb = info.get("album_id")
                if alb and alb not in self.albums_done:
                    self.album_queue[alb] = lang
        if pop:
            with self.lock:
                self.pop[sid] += pop
        return sid, lang, not known


C = Catalog()


# ------------------------------------------------------------------ crawl

def playlists_of(root):
    out = []
    for o in items(root, ["data", "results"]):
        if isinstance(o, dict) and o.get("type", "playlist") == "playlist" and (o.get("id") or o.get("listid")):
            out.append({"id": o.get("id") or o.get("listid"), "title": un(o.get("title") or o.get("listname")),
                        "subtitle": un(o.get("subtitle")), "image": o.get("image") or ""})
    return out


def core(lang, n_playlists):
    """Charts, trending and playlists of one language: the popular songs."""
    found = {}
    for p in playlists_of(call("content.getCharts", {}, lang)):
        p["chart"] = 1
        found.setdefault(p["id"], p)
    jobs = [("content.getFeaturedPlaylists", {"fetch_from_serialized_files": "true", "p": str(p), "n": "50"}) for p in range(1, 25)]
    jobs += [("search.getPlaylistResults", {"q": f"{lang} {m}", "p": "1", "n": "50"}) for m in MOODS]
    with cf.ThreadPoolExecutor(16) as ex:
        for res in ex.map(lambda j: call(j[0], j[1], lang), jobs):
            for p in playlists_of(res):
                found.setdefault(p["id"], p)
    chosen = [p for p in found.values() if p.get("chart")]
    rest = [p for p in found.values() if not p.get("chart")]
    random.shuffle(rest)
    chosen += rest[: max(0, n_playlists - len(chosen))]

    playlists = []
    trending = [C.add(o, pop=5) for o in items(call("content.getTrending", {"entity_type": "song", "entity_language": lang}), ["data", "results"])]
    ids = [t[0] for t in trending if t and t[1] == lang]
    if ids:
        playlists.append({"id": "trending", "title": "Trending Now", "subtitle": lang.capitalize(), "image": "", "chart": 1, "songs": ids})

    def details(p):
        return p, call("playlist.getDetails", {"listid": p["id"], "n": "200", "p": "1"})

    with cf.ThreadPoolExecutor(16) as ex:
        for p, res in ex.map(details, chosen):
            ids = []
            for o in items(res, ["list", "songs", "results"]):
                t = C.add(o, pop=6 if p.get("chart") else 3)
                if t and t[1] == lang and t[0] not in ids:
                    ids.append(t[0])
            if len(ids) >= 5:
                p["songs"] = ids
                playlists.append(p)

    # Singers of this language (artist search), so the deep crawl knows them.
    if lang in DEEP:
        for page in range(1, 11):
            for a in items(call("search.getArtistResults", {"q": lang, "p": str(page), "n": "50"}), ["results"]):
                aid = a.get("id") if isinstance(a, dict) else None
                if aid and aid not in C.artists:
                    C.artists[aid] = {"lang": lang, "page": 0, "done": False, "seen": 1}
    return playlists


def artist_page(aid):
    """Crawls one page of a singer's songs. Returns how many new songs it found."""
    a = C.artists[aid]
    page = a["page"]
    res = call("artist.getArtistPageDetails", {"artistId": aid, "n_song": "50", "n_album": "0", "page": str(page),
                                               "category": "", "sort_order": ""})
    songs = items(res, ["topSongs"])
    new = 0
    for o in songs:
        t = C.add(o, pop=2 if page == 0 else 1 if page == 1 else 0, small=False)
        if t and t[2]:
            new += 1
    with C.lock:
        a["page"] = page + 1
        a["dry"] = 0 if new else a.get("dry", 0) + 1
        if not songs or a["dry"] >= 4 or page >= 400:
            a["done"] = True
    return new


def album(alb):
    res = call("content.getAlbumDetails", {"albumid": alb})
    new = sum(1 for o in items(res, ["list", "songs"]) if (t := C.add(o, small=False)) and t[2])
    with C.lock:
        C.albums_done.add(alb)
        C.album_queue.pop(alb, None)
    return new


def deep_crawl(deadline):
    """Every singer's songs, most important first, until the time is up."""
    requests = new = rounds = 0
    with cf.ThreadPoolExecutor(24) as ex:
        while time.time() < deadline:
            with C.lock:
                todo = [aid for aid, a in C.artists.items() if not a["done"]]
                todo.sort(key=lambda aid: -DEEP.get(C.artists[aid]["lang"], 0) * (1 + C.artists[aid]["seen"]) / (1 + C.artists[aid]["page"]))
                albums = list(C.album_queue)[:200]
            batch = todo[:240]
            if not batch and not albums:
                break
            jobs = [ex.submit(artist_page, aid) for aid in batch] + [ex.submit(album, alb) for alb in albums[:60]]
            for j in cf.as_completed(jobs):
                requests += 1
                try:
                    new += j.result()
                except Exception:
                    pass
            rounds += 1
            if rounds % 10 == 0:
                print(f"  crawl: {requests} requests, +{new} songs, total {len(C.order)}", flush=True)
    print(f"crawl: {requests} requests, +{new} new songs", flush=True)


# ------------------------------------------------------------------ output

def tokens(row):
    words = set(norm(f"{row[1]} {row[2]} {row[3]}").split())
    return {w for w in words if len(w) >= 2}


def key_of(token):
    k = token[:3]
    return k if re.fullmatch(r"[a-z0-9]+", k) else "x" + format(sum(map(ord, k)) % 256, "02x")


def write(playlists_by_lang):
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, "r"))
    os.makedirs(os.path.join(OUT, "i"))
    order = C.order
    number = {sid: n for n, sid in enumerate(order)}

    for n in range(0, len(order), ROWS):
        with open(os.path.join(OUT, "r", f"{n // ROWS}.json"), "w", encoding="utf-8") as f:
            json.dump([C.songs[s] for s in order[n:n + ROWS]], f, ensure_ascii=False, separators=(",", ":"))

    index = collections.defaultdict(lambda: collections.defaultdict(list))
    for n, sid in enumerate(order):
        for t in tokens(C.songs[sid]):
            index[key_of(t)][t].append(n)
    for k, toks in index.items():
        enc = {}
        for t, nums in toks.items():
            prev, d = 0, []
            for x in nums:
                d.append(x - prev)
                prev = x
            enc[t] = d
        with open(os.path.join(OUT, "i", f"{k}.json"), "w", encoding="utf-8") as f:
            json.dump(enc, f, ensure_ascii=False, separators=(",", ":"))

    counts = collections.Counter(r[9] for r in C.songs.values())
    for lang in LANGS:
        cap = CORE_CAP.get(lang) or SMALL.get(lang, 1000)
        mine = [s for s in order if C.songs[s][9] == lang]
        mine.sort(key=lambda s: (-C.pop[s], number[s]))
        chosen = mine[:cap]
        pos = {s: i for i, s in enumerate(chosen)}
        pls = []
        for p in playlists_by_lang.get(lang, []):
            idx = [pos[s] for s in p["songs"] if s in pos]
            if len(idx) >= 5:
                img = re.sub(r"\d+x\d+(?=\.\w+$)", "150x150", p["image"] or C.songs[chosen[idx[0]]][5])
                img = img[len(IMG):] if img.startswith(IMG) else img
                pls.append([p["id"], p["title"], p["subtitle"], img, idx, p.get("chart", 0)])
        with open(os.path.join(OUT, f"{lang}.json"), "w", encoding="utf-8") as f:
            json.dump({"lang": lang, "songs": [C.songs[s][:9] for s in chosen], "playlists": pls},
                      f, ensure_ascii=False, separators=(",", ":"))
        print(f"{lang}: {counts[lang]} songs in catalog, {len(chosen)} popular, {len(pls)} playlists", flush=True)

    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump({"build": int(time.time()), "total": len(order), "rows": ROWS, "languages": dict(counts)}, f)
    print(f"TOTAL: {len(order)} songs, {len(index)} index files", flush=True)


def main():
    t0 = time.time()
    C.load(STATE)
    playlists = {}
    for lang in LANGS:
        n = {"hindi": 1500, "punjabi": 1000, "haryanvi": 500, "english": 400}.get(lang, 120)
        playlists[lang] = core(lang, n)
        print(f"core {lang}: {len(playlists[lang])} playlists, total {len(C.order)}", flush=True)
    C.save(STATE)
    deep_crawl(t0 + MINUTES * 60)
    C.save(STATE)
    write(playlists)
    if len(C.order) < 1000:
        sys.exit("catalog too small, JioSaavn probably blocked us")


if __name__ == "__main__":
    main()
