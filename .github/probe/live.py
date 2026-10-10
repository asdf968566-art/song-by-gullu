"""One-off probe: Indian live radio from radio-browser (keyless) and ntfy.sh pub/sub (for listen together)."""
import json, time, urllib.parse, urllib.request

ORIGIN = "https://asdf968566-art.github.io"


def go(method, url, data=None, headers=None, timeout=25):
    t = time.time()
    req = urllib.request.Request(url, method=method, data=data, headers={"User-Agent": "Sangeet/1.0", "Origin": ORIGIN, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(), dict(r.headers), time.time() - t
    except urllib.error.HTTPError as e:
        return e.code, e.read(), dict(e.headers), time.time() - t
    except Exception as e:
        return 0, repr(e).encode(), {}, time.time() - t


def cors(h):
    return {k: v for k, v in h.items() if k.lower().startswith("access-control")}


# --- radio-browser: which servers answer, how many Indian stations, how many https / HLS
for host in ["all.api.radio-browser.info", "de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info"]:
    s, body, h, secs = go("GET", f"https://{host}/json/stats")
    print(f"### radio {host}: HTTP {s} {secs:.1f}s cors={cors(h)} {body[:120]!r}")
s, body, h, secs = go("GET", "https://de1.api.radio-browser.info/json/stations/search?" + urllib.parse.urlencode(
    {"countrycode": "IN", "hidebroken": "true", "order": "clickcount", "reverse": "true", "limit": "300"}))
print(f"### IN stations: HTTP {s} {secs:.1f}s cors={cors(h)}")
try:
    st = json.loads(body)
    https = [x for x in st if x.get("url_resolved", "").startswith("https")]
    hls = [x for x in st if x.get("hls") == 1 or ".m3u8" in x.get("url_resolved", "")]
    print(f"  {len(st)} stations, {len(https)} https, {len(hls)} HLS, codecs {sorted({x.get('codec') for x in st})}")
    for x in st[:40]:
        print(f"  {x['name'][:40]!r} | {x.get('language','')[:20]} | {x.get('tags','')[:40]} | {x.get('codec')} {x.get('bitrate')} | {x.get('url_resolved','')[:70]} | clicks {x.get('clickcount')}")
    for want in ["mirchi", "red fm", "vividh", "akashvani", "air ", "city", "radio one", "big fm", "fever", "punjabi", "haryanvi", "bollywood"]:
        hit = [x["name"] for x in st if want in x["name"].lower() or want in x.get("tags", "").lower()]
        print(f"  '{want}': {len(hit)} {hit[:6]}")
    # Does one https stream answer (first bytes)?
    for x in https[:5]:
        s2, b2, h2, secs2 = go("GET", x["url_resolved"], headers={"Range": "bytes=0-2000"}, timeout=10)
        print(f"  stream {x['name'][:30]!r}: HTTP {s2} {secs2:.1f}s type={h2.get('Content-Type')} cors={cors(h2)} {len(b2)} bytes")
except Exception as e:
    print("  parse", e, body[:200])

# --- ntfy.sh: publish, poll, limits, CORS
topic = f"sangeet-probe-{int(time.time())}"
msg = json.dumps({"t": "js:abc", "p": 12345, "playing": True}).encode()
s, body, h, secs = go("POST", f"https://ntfy.sh/{topic}", data=msg, headers={"Content-Type": "text/plain"})
print(f"### ntfy publish: HTTP {s} {secs:.1f}s cors={cors(h)} {body[:200]!r}")
print("  limits:", {k: v for k, v in h.items() if "limit" in k.lower() or "rate" in k.lower()})
s, body, h, secs = go("GET", f"https://ntfy.sh/{topic}/json?poll=1&since=all")
print(f"### ntfy poll: HTTP {s} {secs:.1f}s cors={cors(h)} {body[:300]!r}")
s, body, h, secs = go("OPTIONS", f"https://ntfy.sh/{topic}", headers={"Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "content-type"})
print(f"### ntfy preflight: HTTP {s} cors={cors(h)}")
s, body, h, secs = go("GET", "https://ntfy.sh/v1/account")
print(f"### ntfy account (visitor limits): HTTP {s} {body[:600]!r}")
