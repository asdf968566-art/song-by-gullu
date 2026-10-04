import json, re, urllib.request
for kind, pid in [("playlist", "37i9dQZF1DX0XUfTFmNBRM"), ("album", "4yP0hdKOZPNshxUOjY0cZj")]:
    try:
        req = urllib.request.Request(f"https://open.spotify.com/embed/{kind}/{pid}", headers={"User-Agent": "Mozilla/5.0"})
        page = urllib.request.urlopen(req, timeout=20).read().decode()
        m = re.search(r'<script id="__NEXT_DATA__" type="application/json">(.*?)</script>', page, re.S)
        d = json.loads(m.group(1))
        ent = d["props"]["pageProps"]["state"]["data"]["entity"]
        tl = ent.get("trackList") or []
        print("SPOTIFY", kind, ent.get("name") or ent.get("title"), len(tl), json.dumps(tl[:2])[:400])
    except Exception as e:
        print("SPOTIFY", kind, "ERR", repr(e)[:300])
