#!/usr/bin/env python3
"""Builds the web app's song catalog from JioSaavn (charts + thousands of playlists).

Browsers can't call JioSaavn directly (no CORS), so CI fetches it and ships JSON next to the site.
Audio itself streams straight from JioSaavn's CDN in the browser.

Output: <out>/<language>.json  and  <out>/index.json
Song row: [id, title, artist, album, seconds, image, media, has320, year]
  image: path after https://c.saavncdn.com/ (150x150 size), or a full URL
  media: path after https://aac.saavncdn.com/ without "_96.mp4", or a full URL
Playlist row: [id, title, subtitle, image, [song indexes], isChart]
"""
import base64
import concurrent.futures as cf
import html
import json
import os
import random
import sys
import time
import urllib.parse
import urllib.request

from Crypto.Cipher import DES

OUT = sys.argv[1] if len(sys.argv) > 1 else "SangeetWeb/data"
API = "https://www.jiosaavn.com/api.php"
IMG = "https://c.saavncdn.com/"
AAC = "https://aac.saavncdn.com/"

LANGS = {  # language: how many playlists to read
    "hindi": 1500, "punjabi": 1000, "english": 300, "haryanvi": 400, "bhojpuri": 400,
    "tamil": 500, "telugu": 500, "marathi": 350, "bengali": 350, "gujarati": 300,
}
MOODS = ["hits", "romantic", "sad", "party", "90s", "latest", "lofi", "workout", "devotional",
         "wedding", "old", "love", "chill", "dance", "top 50", "new", "retro", "drive", "rap", "unplugged"]


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
            if isinstance(root.get(k), list):
                return root[k]
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


def song(o):
    if not isinstance(o, dict) or o.get("type", "song") != "song" or not o.get("id"):
        return None
    info = o.get("more_info") or {}
    media = decrypt(info.get("encrypted_media_url") or o.get("encrypted_media_url") or "")
    if not media:
        return None
    if media.startswith(AAC) and media.endswith("_96.mp4"):
        media = media[len(AAC):-len("_96.mp4")]
    img = (o.get("image") or "").replace("50x50", "150x150").replace("500x500", "150x150")
    if img.startswith(IMG):
        img = img[len(IMG):]
    prim = ((info.get("artistMap") or {}).get("primary_artists")) or []
    artist = ", ".join(a.get("name", "") for a in prim if a.get("name")) or info.get("music") or (o.get("subtitle") or "").split(" - ")[0]
    try:
        dur = int(info.get("duration") or o.get("duration") or 0)
    except ValueError:
        dur = 0
    try:
        year = int(o.get("year") or 0)
    except ValueError:
        year = 0
    return {
        "id": o["id"], "lang": (o.get("language") or "").lower(),
        "row": [o["id"], un(o.get("title")), un(artist) or "Unknown", un(info.get("album") or o.get("album")),
                dur, img, media, 1 if str(info.get("320kbps")) == "true" else 0, year],
    }


def playlists_of(root):
    out = []
    for o in items(root, ["data", "results"]):
        if not isinstance(o, dict) or o.get("type", "playlist") != "playlist":
            continue
        pid = o.get("id") or o.get("listid")
        if pid:
            out.append({"id": pid, "title": un(o.get("title") or o.get("listname")), "subtitle": un(o.get("subtitle")),
                        "image": o.get("image") or ""})
    return out


def build_language(lang, budget):
    t0 = time.time()
    found = {}  # playlist id -> playlist
    charts = playlists_of(call("content.getCharts", {}, lang))
    for p in charts:
        p["chart"] = 1
        found.setdefault(p["id"], p)

    jobs = [("content.getFeaturedPlaylists", {"fetch_from_serialized_files": "true", "p": str(p), "n": "50"}) for p in range(1, 25)]
    jobs += [("search.getPlaylistResults", {"q": f"{lang} {m}", "p": str(p), "n": "40"}) for m in MOODS for p in (1, 2, 3, 4)]
    with cf.ThreadPoolExecutor(10) as ex:
        for res in ex.map(lambda j: call(j[0], j[1], lang), jobs):
            for p in playlists_of(res):
                found.setdefault(p["id"], p)

    chosen = [p for p in found.values() if p.get("chart")]
    rest = [p for p in found.values() if not p.get("chart")]
    random.shuffle(rest)
    chosen += rest[: max(0, budget - len(chosen))]

    trending = [s for s in map(song, items(call("content.getTrending", {"entity_type": "song", "entity_language": lang}), ["data", "results"])) if s]

    def details(p):
        return p, call("playlist.getDetails", {"listid": p["id"], "n": "200", "p": "1"})

    songs, index, out_pl = [], {}, []

    def add(s):
        if s["lang"] and s["lang"] != lang:
            return None
        if s["id"] not in index:
            index[s["id"]] = len(songs)
            songs.append(s["row"])
        return index[s["id"]]

    if trending:
        idx = [i for i in (add(s) for s in trending) if i is not None]
        if idx:
            out_pl.append(["trending", "Trending Now", lang.capitalize(), songs[idx[0]][5], idx, 1])
    with cf.ThreadPoolExecutor(12) as ex:
        for p, res in ex.map(details, chosen):
            idx = []
            for o in items(res, ["list", "songs", "results"]):
                s = song(o)
                if s:
                    i = add(s)
                    if i is not None and i not in idx:
                        idx.append(i)
            if len(idx) >= 5:
                img = p["image"].replace("500x500", "150x150")
                img = img[len(IMG):] if img.startswith(IMG) else img
                out_pl.append([p["id"], p["title"], p["subtitle"], img, idx, p.get("chart", 0)])

    # Then every popular singer's songs (search pages), for depth beyond playlists.
    artist_count = {}
    for row in songs:
        for a in row[2].split(", "):
            if len(a) > 2:
                artist_count[a] = artist_count.get(a, 0) + 1
    top = sorted(artist_count, key=artist_count.get, reverse=True)[: max(30, budget // 8)]
    searches = [(a, p) for a in top for p in (1, 2, 3)]
    with cf.ThreadPoolExecutor(12) as ex:
        for res in ex.map(lambda j: call("search.getResults", {"q": j[0], "p": str(j[1]), "n": "50"}), searches):
            for o in items(res, ["results"]):
                s = song(o)
                if s and s["lang"] == lang:
                    add(s)

    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, f"{lang}.json"), "w", encoding="utf-8") as f:
        json.dump({"lang": lang, "songs": songs, "playlists": out_pl}, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{lang}: {len(songs)} songs, {len(out_pl)} playlists ({len(found)} found) in {time.time() - t0:.0f}s", flush=True)
    return {"lang": lang, "songs": len(songs), "playlists": len(out_pl)}


def main():
    langs = sys.argv[2].split(",") if len(sys.argv) > 2 else list(LANGS)
    stats = [build_language(l, LANGS.get(l, 100)) for l in langs]
    total = sum(s["songs"] for s in stats)
    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump({"built": int(time.time()), "total": total, "languages": stats}, f)
    print(f"TOTAL: {total} songs")
    if total < 1000:
        sys.exit("catalog too small, JioSaavn probably blocked us")


if __name__ == "__main__":
    main()
