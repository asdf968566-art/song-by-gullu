#!/usr/bin/env python3
"""One-off probe (removed after): can CI reach Gaana and Wikidata, and what do they return?"""
import base64, json, urllib.parse, urllib.request

UA = {"User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"}


def get(url, data=None, headers=None):
    h = dict(UA, **(headers or {}))
    req = urllib.request.Request(url, data=data, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:300]
    except Exception as e:
        return 0, repr(e)


def show(name, status, body, n=700):
    print(f"\n### {name}: HTTP {status}\n{body[:n]}")


# ---------------- Gaana
s, b = get("https://gaana.com/apiv2?country=IN&page=0&secType=track&type=search&keyword=" + urllib.parse.quote("kesariya"))
show("gaana search (apiv2)", s, b)
seokey = None
try:
    d = json.loads(b)
    groups = d.get("gr") or []
    for g in groups:
        for it in g.get("gd", []):
            seokey = seokey or it.get("seo")
            print(" track:", it.get("ti"), "|", it.get("sti"), "| seo:", it.get("seo"), "| id:", it.get("id"))
except Exception as e:
    print("parse:", e)
if seokey:
    s, b = get(f"https://gaana.com/apiv2?seokey={seokey}&type=songDetail", data=b"", headers={"Content-Type": "application/x-www-form-urlencoded"})
    show("gaana songDetail", s, b, 1500)
    try:
        t = json.loads(b)["tracks"][0]
        urls = t.get("urls") or {}
        print("urls keys:", list(urls))
        from Crypto.Cipher import AES
        for key, iv in [(b"gy1t#b@jl(b$wtme", b"xC4dmVJAq14BfntX"), (b"g@1n!(f1#r.0$)&%", b"asd!@#!@#@!12312")]:
            for q in ("high", "medium", "auto"):
                msg = (urls.get(q) or {}).get("message")
                if not msg:
                    continue
                try:
                    raw = base64.b64decode(msg[16:]) if len(msg) > 16 else b""
                    for data, ivv in [(base64.b64decode(msg), iv), (raw, msg[:16].encode())]:
                        try:
                            out = AES.new(key, AES.MODE_CBC, ivv).decrypt(data)
                            txt = out.rstrip(bytes(range(1, 17))).decode("utf-8", "replace")
                            print(f"decrypt {q} key={key[:4]}..: {txt[:160]}")
                            if txt.startswith("http"):
                                st, _ = get(txt, headers={"Range": "bytes=0-2047"})
                                print("   -> fetch:", st)
                        except Exception as e:
                            print(f"decrypt {q}: {e}")
                except Exception as e:
                    print("b64:", e)
    except Exception as e:
        print("songDetail parse:", e)

# ---------------- Wikidata: Indian films with music, producers, release date
q = """
SELECT ?film ?filmLabel ?date ?composerLabel ?producerLabel ?directorLabel ?langLabel WHERE {
  ?film wdt:P31 wd:Q11424; wdt:P495 wd:Q668; rdfs:label ?l. FILTER(LANG(?l)="en" && STR(?l) IN ("Aashiqui 2","Kabir Singh","Animal","Sholay"))
  OPTIONAL { ?film wdt:P577 ?date } OPTIONAL { ?film wdt:P86 ?composer } OPTIONAL { ?film wdt:P162 ?producer }
  OPTIONAL { ?film wdt:P57 ?director } OPTIONAL { ?film wdt:P364 ?lang }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
} LIMIT 40"""
s, b = get("https://query.wikidata.org/sparql?format=json&query=" + urllib.parse.quote(q), headers={"Accept": "application/sparql-results+json", "User-Agent": "SangeetCI/1.0 (github.com/asdf968566-art/song-by-gullu)"})
show("wikidata sample", s, "", 0)
try:
    for r in json.loads(b)["results"]["bindings"]:
        print(" ", {k: v["value"][:40] for k, v in r.items() if k != "film"})
except Exception as e:
    print(b[:400], e)
q2 = "SELECT (COUNT(DISTINCT ?film) AS ?n) WHERE { ?film wdt:P31 wd:Q11424; wdt:P495 wd:Q668; wdt:P86 ?c. }"
s, b = get("https://query.wikidata.org/sparql?format=json&query=" + urllib.parse.quote(q2), headers={"Accept": "application/sparql-results+json", "User-Agent": "SangeetCI/1.0"})
show("wikidata: Indian films with a composer", s, b, 300)

# ---------------- JioSaavn album details: which fields
s, b = get("https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=aashiqui%202&n=3&p=1")
show("jiosaavn album search", s, b, 1500)
try:
    alb = json.loads(b)["results"][0]["id"]
    s, b = get(f"https://www.jiosaavn.com/api.php?__call=content.getAlbumDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0&albumid={alb}")
    d = json.loads(b)
    print("album keys:", list(d), "\nmore_info:", json.dumps(d.get("more_info"), ensure_ascii=False)[:800])
except Exception as e:
    print("album:", e)

# ---------------- other platforms: reachability
for name, url in [("wynk", "https://www.wynk.in/music"), ("hungama", "https://www.hungama.com/"), ("spotify", "https://open.spotify.com/"),
                  ("soundcloud", "https://soundcloud.com/"), ("audiomack", "https://api.audiomack.com/v1/search?q=arijit")]:
    s, b = get(url)
    print(f"{name}: HTTP {s}")
