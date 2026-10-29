#!/usr/bin/env python3
"""Builds Sangeet's song catalog from JioSaavn: charts, playlists, then every singer's full song list.

Browsers can't call JioSaavn (no CORS), so GitHub Actions crawls it and publishes static JSON.
The crawl state is kept between runs (actions/cache), so the catalog grows every night.

Usage: build-web-catalog.py <out dir> <state file> <minutes to crawl>

Output (<out>):
  index.json                 {"build", "total", "rows": ROWS, "languages": {lang: count}}
  artists.json               [[singer, number of songs], ...] most songs first (artist results + spelling fixes)
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
# Haryanvi got more room on Oct 13 (owner: "haryanvi bhi aur add kr").
DEEP = {"hindi": 1.0, "punjabi": 0.55, "haryanvi": 0.6, "english": 0.15}
CORE_CAP = {"hindi": 60000, "punjabi": 30000, "haryanvi": 30000, "english": 10000}
SMALL = {"bhojpuri": 2500, "tamil": 1500, "telugu": 1500, "marathi": 1500, "bengali": 1500, "gujarati": 1200}
LANGS = list(DEEP) + list(SMALL)
MOODS = ["hits", "romantic", "sad", "party", "90s", "latest", "lofi", "workout", "devotional", "wedding",
         "old", "love", "chill", "dance", "top 50", "new", "retro", "drive", "rap", "unplugged",
         "happy", "feel good", "good vibes", "heartbreak", "sleep", "calm", "road trip", "travel", "rain", "gym",
         "motivation", "bhakti", "acoustic"]
# The app's moods are made from playlists with these words in their names, so such playlists are always kept.
# Haryanvi playlist searches beyond the moods ("haryanvi dj", "haryanvi ragni"...).
HARYANVI_TERMS = ["dj", "dj remix", "ragni", "folk", "lok geet", "badmashi", "jaat", "desi", "new", "superhit",
                  "2025", "2026", "bhajan", "dance", "sapna choudhary", "masoom sharma", "amanraj gill", "ajay hooda"]
# Singers whose songs are crawled first (an artist search finds each once a week).
SEED_SINGERS = {
    "haryanvi": ["Masoom Sharma", "Amanraj Gill", "Ajay Hooda", "Renuka Panwar", "Raju Punjabi", "Sapna Choudhary",
                 "Khasa Aala Chahar", "Gulzaar Chhaniwala", "Sumit Goswami", "Diler Kharkiya", "Ndee Kundu",
                 "Narender Bhagana", "Vishvajeet Choudhary", "Amit Saini Rohtakiya", "Raj Mawar", "Ruchika Jangid",
                 "Pranjal Dahiya", "MD KD", "Ankit Baliyan", "Surender Romio", "Biru Kataria", "Mohit Sharma",
                 "Sonika Singh", "Fazilpuriya", "KD Desi Rock", "Mukesh Fouji", "Somvir Kathurwal", "Kay D", "Sandeep Surila",
                 "Anjali Raghav", "Mahi Panchal", "Vijay Varma", "Shiva Choudhary", "Komal Chaudhary"],
    # Pahadi (Garhwali, Kumaoni, Jaunsari, Himachali): JioSaavn lists their songs as Hindi.
    "hindi": ["Narendra Singh Negi", "Gajendra Rana", "Meena Rana", "Pritam Bhartwan", "Kishan Mahipal", "Inder Arya",
              "Saurav Maithani", "Anisha Ranghar", "Rohit Chauhan", "Kuldeep Sharma", "Thakur Das Rathi",
              "Vicky Chauhan", "Basanti Bisht", "Hema Negi Karasi", "Amit Sagar", "Gopal Babu Goswami",
              "Lalit Mohan Joshi", "Pappu Karki", "Mangalesh Dangwal", "Darshan Farswan", "Rajnikant Semwal",
              "Karnail Rana", "Surender Negi", "Sanjeev Dixit", "Jitendra Tomkyal", "Sunil Thapliyal",
              "Kalpana Chauhan", "Manglesh Dangwal", "Virendra Negi Rajpoot", "Inderjeet"],
}
# Garhwali, Kumaoni, Jaunsari and Himachali songs: words and singers in their names (Android LanguageGuess and the
# web app use the same list). Such songs, and the songs of playlists named like that, make data/pahadi.json.
PAHADI = re.compile(
    r"garhwali|garwali|gadwali|garhwal|kumaoni|kumauni|kumaon|jaunsari|himachali|pahadi|pahari|uttarakhand|uttrakhand|utrakhand|"
    r"\bnati\b|गढ़वाली|गढवाली|कुमाऊँनी|कुमाउनी|जौनसारी|पहाड़ी|पहाडी|हिमाचली|"
    r"narendra singh negi|gajendra rana|meena rana|pritam bhartwan|kishan mahipal|inder arya|saurav maithani|"
    r"anisha ranghar|rohit chauhan|kuldeep sharma|thakur das rathi|vicky chauhan|basanti bisht|hema negi karasi|"
    r"gopal babu goswami|lalit mohan joshi|pappu karki|mangalesh dangwal|manglesh dangwal|darshan farswan|"
    r"rajnikant semwal|karnail rana|jitendra tomkyal|sunil thapliyal|virendra negi",
    re.I,
)
# JioSaavn lists most Pahadi songs as Hindi, some under these languages: they are kept as Hindi + Pahadi.
PAHADI_LANGS = {"garhwali", "kumaoni", "kumauni", "jaunsari", "himachali", "pahadi", "pahari"}
PAHADI_TERMS = ["garhwali", "garhwali songs", "new garhwali", "garhwali dj", "kumaoni", "kumaoni songs", "kumaoni dj",
                "pahadi", "pahadi songs", "pahari", "pahari nati", "himachali", "himachali nati", "himachali songs",
                "jaunsari", "uttarakhandi", "uttarakhand", "garhwali bhajan", "jagar"]
PAHADI_CAP = 20000
MOOD_WORDS = {"romantic", "sad", "party", "lofi", "workout", "devotional", "wedding", "love", "chill", "dance", "drive",
              "unplugged", "happy", "feel good", "good vibes", "heartbreak", "sleep", "calm", "road trip", "travel",
              "rain", "gym", "motivation", "bhakti", "acoustic"}


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
        self.searched = {}     # listeners' search words / songs -> when they were last looked up
        self.pahadi = set()    # songs from Pahadi playlists / searches (data/pahadi.json)
        self.seeded = {}       # seed singer -> when an artist search last looked for them

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
        self.searched = d.get("searched", {})
        self.pahadi = set(d.get("pahadi", []))
        self.seeded = d.get("seeded", {})
        print(f"state: {len(self.order)} songs, {len(self.artists)} artists", flush=True)

    def save(self, path):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with gzip.open(path, "wt", encoding="utf-8") as f:
            json.dump({"order": self.order, "songs": [self.songs[i] for i in self.order], "artists": self.artists,
                       "albums_done": sorted(self.albums_done), "album_queue": self.album_queue,
                       "searched": self.searched, "pahadi": sorted(self.pahadi), "seeded": self.seeded},
                      f, ensure_ascii=False, separators=(",", ":"))

    def add(self, o, pop=0, small=True, as_lang=None):
        """Adds a JioSaavn song object. Returns (id, lang, is_new) or None.
        small=False: skip the "few songs only" languages (used by the deep crawl).
        as_lang: keep a song of another language under this one (Pahadi songs JioSaavn files elsewhere)."""
        if not isinstance(o, dict) or o.get("type", "song") != "song" or not o.get("id"):
            return None
        lang = (o.get("language") or "").lower()
        pahadi_lang = lang in PAHADI_LANGS
        if pahadi_lang:
            lang = "hindi"
        if lang not in DEEP and (lang not in SMALL or not small):
            if not as_lang:
                return None
            lang = as_lang
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
        if pahadi_lang:
            with self.lock:
                self.pahadi.add(sid)
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
    if lang == "haryanvi":
        jobs += [("search.getPlaylistResults", {"q": f"haryanvi {t}", "p": str(p), "n": "50"}) for t in HARYANVI_TERMS for p in (1, 2)]
    asked = answered = from_search = 0  # Oct 29: every search answered; ~300 distinct Hindi playlists is all there is
    with cf.ThreadPoolExecutor(16) as ex:
        for (method, params), res in zip(jobs, ex.map(lambda j: call(j[0], j[1], lang), jobs)):
            term = params["q"][len(lang) + 1:] if "q" in params else None
            if method == "search.getPlaylistResults":
                asked += 1
                answered += res is not None
                from_search += len(playlists_of(res))
            kept = 0
            for p in playlists_of(res):
                p = found.setdefault(p["id"], p)
                # The best few playlists named after a mood ("Feel Good Hindi") are always kept.
                if term in MOOD_WORDS and kept < 6 and re.search(rf"\b{re.escape(term)}\b", p["title"], re.I):
                    p["mood"] = 1
                    kept += 1
    print(f"  {lang} playlist searches: {answered} of {asked} answered, {from_search} playlists, {len(found)} found in all", flush=True)
    chosen = [p for p in found.values() if p.get("chart") or p.get("mood")]
    rest = [p for p in found.values() if not (p.get("chart") or p.get("mood"))]
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
            a["checked"] = int(time.time())
    return new


# Singers already crawled are looked at again for their newest songs (Oct 29: every known singer was done, so the
# 150-minute crawl stopped after 5 minutes and new songs came only from charts and playlists: ~130 a day).
# Popular singers every 3 days, the rest less often.
def refresh_every(a):
    w = (1 + a.get("seen", 0)) * DEEP.get(a["lang"], 0.1)
    return 86400 * (3 if w >= 40 else 7 if w >= 10 else 21 if w >= 2 else 60)


def artist_latest(aid):
    """A crawled singer's newest songs (one page). Returns how many were new."""
    a = C.artists[aid]
    res = call("artist.getArtistPageDetails", {"artistId": aid, "n_song": "50", "n_album": "0", "page": "0",
                                               "category": "latest", "sort_order": "desc"})
    new = sum(1 for o in items(res, ["topSongs"]) if (t := C.add(o, pop=1, small=False)) and t[2])
    now = int(time.time())
    with C.lock:
        # No answer: try again in a day.
        a["checked"] = now if res is not None else now - refresh_every(a) + 86400
    return new


def new_releases(lang):
    """JioSaavn's new releases in a language (albums and singles), on every run."""
    found, albums = 0, []
    sources = [call("content.getAlbums", {"n": "50", "p": str(p)}, lang) for p in range(1, 6)]
    launch = call("webapi.getLaunchData", {}, lang)
    sources += [launch.get(k) for k in ("new_albums", "new_trending")] if isinstance(launch, dict) else []
    new = 0
    for res in sources:
        for o in items(res, ["data", "results"]):
            if not isinstance(o, dict) or not o.get("id"):
                continue
            found += 1
            kind = o.get("type", "")
            if kind == "song":
                t = C.add(o, pop=3, small=False)
                new += 1 if t and t[2] else 0
            elif kind == "album" and o["id"] not in C.albums_done and o["id"] not in albums:
                albums.append(o["id"])
    with cf.ThreadPoolExecutor(12) as ex:
        new += sum(ex.map(album, albums))
    print(f"new releases {lang}: {found} found, {len(albums)} new albums, +{new} songs", flush=True)


def album(alb):
    res = call("content.getAlbumDetails", {"albumid": alb})
    new = sum(1 for o in items(res, ["list", "songs"]) if (t := C.add(o, small=False)) and t[2])
    with C.lock:
        C.albums_done.add(alb)
        C.album_queue.pop(alb, None)
    return new


def seed_singers():
    """Finds the seed singers (artist search) once a week and puts them first in the deep crawl."""
    now = time.time()
    todo = [(lang, name) for lang, names in SEED_SINGERS.items() for name in names if now - C.seeded.get(name, 0) > 7 * 86400]

    def look(job):
        lang, name = job
        return job, call("search.getArtistResults", {"q": name, "p": "1", "n": "5"})

    found = 0
    with cf.ThreadPoolExecutor(8) as ex:
        for (lang, name), res in ex.map(look, todo):
            if res is None:
                continue
            C.seeded[name] = now
            for a in items(res, ["results"])[:2]:
                if not isinstance(a, dict) or not a.get("id") or norm(name) not in norm(un(a.get("name") or a.get("title"))):
                    continue
                with C.lock:
                    st = C.artists.setdefault(a["id"], {"lang": lang, "page": 0, "done": False, "seen": 0})
                    st["seen"] = max(st["seen"], 40)
                    if st["done"] and st["page"] < 40:
                        st["done"] = False  # look again for their new songs
                found += 1
    print(f"seed singers: {len(todo)} looked up, {found} found", flush=True)


def pahadi():
    """Garhwali, Kumaoni, Jaunsari and Himachali songs: playlists named like that, and song searches."""
    found = {}
    jobs = [{"q": t, "p": str(p), "n": "50"} for t in PAHADI_TERMS for p in (1, 2)]
    # Oct 29: every search answered, but none of the playlists JioSaavn gives is named Pahadi any more; the Pahadi
    # songs come from the song searches below.
    answered = raw = 0
    with cf.ThreadPoolExecutor(12) as ex:
        for res in ex.map(lambda q: call("search.getPlaylistResults", q), jobs):
            answered += res is not None
            raw += len(playlists_of(res))
            for p in playlists_of(res):
                # Only playlists whose own name says Pahadi: their songs are trusted to be Pahadi.
                if PAHADI.search(p["title"]):
                    found.setdefault(p["id"], p)
    chosen = list(found.values())[:160]

    def details(p):
        return p, call("playlist.getDetails", {"listid": p["id"], "n": "200", "p": "1"})

    playlists = []
    langs = collections.Counter()
    with cf.ThreadPoolExecutor(12) as ex:
        for p, res in ex.map(details, chosen):
            ids = []
            for o in items(res, ["list", "songs", "results"]):
                if isinstance(o, dict):
                    langs[(o.get("language") or "?").lower()] += 1
                t = C.add(o, pop=3, as_lang="hindi")
                if t and t[0] not in ids:
                    ids.append(t[0])
            if ids:
                with C.lock:
                    C.pahadi.update(ids)
            if len(ids) >= 5:
                p["songs"] = ids
                playlists.append(p)
    # Song searches: only songs whose names or singers say Pahadi.
    searches = [{"q": t, "p": str(p), "n": "40"} for t in PAHADI_TERMS for p in (1, 2, 3)]
    with cf.ThreadPoolExecutor(12) as ex:
        for res in ex.map(lambda q: call("search.getResults", q), searches):
            for o in items(res, ["results"]):
                t = C.add(o, pop=2, as_lang="hindi") if isinstance(o, dict) and PAHADI.search(
                    f"{o.get('title', '')} {o.get('subtitle', '')} {(o.get('more_info') or {}).get('album', '')}") else C.add(o, pop=2)
                if t:
                    row = C.songs.get(t[0])
                    if row and PAHADI.search(f"{row[1]} {row[2]} {row[3]}"):
                        with C.lock:
                            C.pahadi.add(t[0])
    print(f"pahadi: {answered} of {len(jobs)} searches answered, {raw} playlists before the name check", flush=True)
    print(f"pahadi: {len(found)} playlists found, {len(playlists)} kept, {len(C.pahadi)} songs; "
          f"their languages {dict(langs.most_common(6))}", flush=True)
    return playlists


def deep_crawl(deadline):
    """Every singer's songs, most important first; then the newest songs of singers due a look, until the time is up."""
    requests = new = rounds = 0
    looked = from_looks = 0
    due = None  # finished singers due a look, most important first (made once, when unfinished ones run short)
    with cf.ThreadPoolExecutor(24) as ex:
        while time.time() < deadline:
            with C.lock:
                todo = [aid for aid, a in C.artists.items() if not a["done"]]
                todo.sort(key=lambda aid: -DEEP.get(C.artists[aid]["lang"], 0) * (1 + C.artists[aid]["seen"]) / (1 + C.artists[aid]["page"]))
                albums = list(C.album_queue)[:200]
                batch = todo[:240]
                if len(batch) < 240 and due is None:
                    now = time.time()
                    due = [aid for aid, a in C.artists.items() if a["done"] and now - a.get("checked", 0) > refresh_every(a)]
                    due.sort(key=lambda aid: -DEEP.get(C.artists[aid]["lang"], 0) * (1 + C.artists[aid]["seen"]))
                    print(f"  {len(due)} crawled singers are due a look for new songs", flush=True)
            looks = due[: 240 - len(batch)] if due else []
            if due:
                del due[: len(looks)]
            if not batch and not albums and not looks:
                break
            jobs = [ex.submit(artist_page, aid) for aid in batch] + [ex.submit(album, alb) for alb in albums[:60]]
            look_jobs = [ex.submit(artist_latest, aid) for aid in looks]
            for j in cf.as_completed(jobs + look_jobs):
                requests += 1
                try:
                    n = j.result()
                except Exception:
                    continue
                new += n
                if j in look_jobs:
                    looked += 1
                    from_looks += n
            rounds += 1
            if rounds % 10 == 0:
                print(f"  crawl: {requests} requests, +{new} songs, total {len(C.order)}", flush=True)
    print(f"crawl: {requests} requests, +{new} new songs; {looked} singers looked at again, +{from_looks} of them", flush=True)


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

    # Singers with how many songs they have here, so the app can show artists and fix misspelt names.
    singers = collections.Counter()
    for sid in order:
        for a in C.songs[sid][2].split(", "):
            if a and a != "Unknown":
                singers[a] += 1
    with open(os.path.join(OUT, "artists.json"), "w", encoding="utf-8") as f:
        json.dump([[a, n] for a, n in singers.most_common(40000) if n >= 2], f, ensure_ascii=False, separators=(",", ":"))

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

    # Pahadi isn't a JioSaavn language: songs from Pahadi playlists and searches, and songs whose names or singers
    # say Pahadi, most popular first.
    mine = [s for s in order if s in C.pahadi or PAHADI.search(f"{C.songs[s][1]} {C.songs[s][2]} {C.songs[s][3]}")]
    mine.sort(key=lambda s: (-C.pop[s], number[s]))
    chosen = mine[:PAHADI_CAP]
    pos = {s: i for i, s in enumerate(chosen)}
    pls = []
    for p in playlists_by_lang.get("pahadi", []):
        idx = [pos[s] for s in p["songs"] if s in pos]
        if len(idx) >= 5:
            img = re.sub(r"\d+x\d+(?=\.\w+$)", "150x150", p["image"] or C.songs[chosen[idx[0]]][5])
            img = img[len(IMG):] if img.startswith(IMG) else img
            pls.append([p["id"], p["title"], p["subtitle"], img, idx, 0])
    with open(os.path.join(OUT, "pahadi.json"), "w", encoding="utf-8") as f:
        json.dump({"lang": "pahadi", "songs": [C.songs[s][:9] for s in chosen], "playlists": pls},
                  f, ensure_ascii=False, separators=(",", ":"))
    counts["pahadi"] = len(mine)
    print(f"pahadi: {len(mine)} songs in catalog, {len(chosen)} in pahadi.json, {len(pls)} playlists", flush=True)

    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump({"build": int(time.time()), "total": len(order), "rows": ROWS, "languages": dict(counts)}, f)
    print(f"TOTAL: {len(order)} songs, {len(index)} index files", flush=True)


def main():
    t0 = time.time()
    C.load(STATE)
    playlists = {}
    for lang in LANGS:
        n = {"hindi": 1500, "punjabi": 1000, "haryanvi": 900, "english": 400}.get(lang, 120)
        playlists[lang] = core(lang, n)
        print(f"core {lang}: {len(playlists[lang])} playlists, total {len(C.order)}", flush=True)
    before = len(C.order)
    playlists["pahadi"] = pahadi()
    print(f"core pahadi: +{len(C.order) - before} songs, total {len(C.order)}", flush=True)
    seed_singers()
    # Words the app's listeners searched, and songs they played from YouTube or didn't find on the website
    # (Community): their songs join the catalog. Each is looked up at most every 3 days.
    try:
        with open(os.path.join(os.path.dirname(STATE), "community-searches.json"), encoding="utf-8") as f:
            wanted = [q for q in json.load(f) if isinstance(q, str)][:800]
    except Exception:
        wanted = []
    now = time.time()
    C.searched = {q: t for q, t in C.searched.items() if now - t < 30 * 86400}
    wanted = [q for q in wanted if now - C.searched.get(q, 0) > 3 * 86400]
    if wanted:
        before = len(C.order)
        with cf.ThreadPoolExecutor(8) as ex:
            for q, res in zip(wanted, ex.map(lambda q: call("search.getResults", {"q": q, "n": "30", "p": "1"}), wanted)):
                if res is not None:
                    C.searched[q] = now
                for o in items(res, ["results"]):
                    C.add(o, pop=2)
        print(f"community searches: {len(wanted)} looked up, +{len(C.order) - before} songs, total {len(C.order)}", flush=True)
    for lang in DEEP:
        new_releases(lang)
    C.save(STATE)
    deep_crawl(t0 + MINUTES * 60)
    C.save(STATE)
    write(playlists)
    if len(C.order) < 1000:
        sys.exit("catalog too small, JioSaavn probably blocked us")


if __name__ == "__main__":
    main()
