import json, urllib.parse, urllib.request
def call(m, p):
    q = {"__call": m, "_format": "json", "_marker": "0", "api_version": "4", "ctx": "web6dot0", **p}
    req = urllib.request.Request("https://www.jiosaavn.com/api.php?" + urllib.parse.urlencode(q), headers={"User-Agent": "Mozilla/5.0"})
    try:
        return json.loads(urllib.request.urlopen(req, timeout=25).read())
    except Exception as e:
        return {"ERR": str(e)}
def show(label, r):
    if isinstance(r, dict):
        desc = {k: (f"list[{len(v)}]" if isinstance(v, list) else (("dict:" + ",".join(list(v)[:8])) if isinstance(v, dict) else str(v)[:40])) for k, v in list(r.items())[:25]}
    else:
        desc = f"{type(r).__name__}[{len(r)}]" if isinstance(r, list) else str(r)[:100]
    print("PROBE", label, json.dumps(desc)[:900])
a = "459320"  # Arijit Singh
for p in ("0", "1", "2"):
    r = call("artist.getArtistPageDetails", {"artistId": a, "n_song": "50", "n_album": "0", "page": p, "category": "", "sort_order": ""})
    show(f"artistPage page={p}", r)
    ts = r.get("topSongs") if isinstance(r, dict) else None
    if isinstance(ts, list) and ts: print("PROBE  first", ts[0].get("title"), ts[0].get("language"), len(ts))
    if isinstance(ts, dict): show("  topSongs", ts)
for p in ("1", "2", "3"):
    r = call("artist.getArtistMoreSong", {"artistId": a, "p": p, "n_song": "50", "category": "", "sort_order": ""})
    show(f"moreSong p={p}", r)
    ts = r.get("topSongs") if isinstance(r, dict) else None
    if isinstance(ts, dict): show("  topSongs", ts); ts = ts.get("songs")
    if isinstance(ts, list) and ts: print("PROBE  first", ts[0].get("title"), len(ts))
r = call("search.getResults", {"q": "kesariya", "n": "3", "p": "1"})
s = r["results"][0]
print("PROBE song more_info keys", list(s.get("more_info", {}).keys()))
print("PROBE album_id", s.get("more_info", {}).get("album_id"), "artistMap ids", [x.get("id") for x in s.get("more_info", {}).get("artistMap", {}).get("primary_artists", [])])
alb = s.get("more_info", {}).get("album_id")
r = call("content.getAlbumDetails", {"albumid": alb})
show("album", r)
r = call("search.getPlaylistResults", {"q": "hindi romantic", "p": "2", "n": "50"})
show("plsearch", r)
r = call("search.getArtistResults", {"q": "haryanvi", "p": "1", "n": "50"})
show("artistsearch", r)
