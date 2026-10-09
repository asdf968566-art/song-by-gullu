"""One-off probe: free keyless JSON stores (library sync) and hit counters (iPhone users), with browser CORS."""
import json, time, urllib.request

ORIGIN = "https://asdf968566-art.github.io"


def go(method, url, body=None, headers=None):
    t = time.time()
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json", "Accept": "application/json",
                                          "Origin": ORIGIN, "User-Agent": "Mozilla/5.0", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            h = dict(r.headers)
            return r.status, r.read().decode("utf-8", "replace")[:300], h, time.time() - t
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:300], dict(e.headers), time.time() - t
    except Exception as e:
        return 0, repr(e)[:200], {}, time.time() - t


def show(name, res):
    s, body, h, secs = res
    cors = {k: v for k, v in h.items() if k.lower().startswith("access-control")}
    print(f"### {name}: HTTP {s} {secs:.1f}s cors={cors} loc={h.get('Location') or h.get('location')}\n    {body!r}")
    return res


data = {"v": 1, "liked": [["JIOSAAVN", "abc", "Song", "Singer"]], "t": int(time.time())}
# jsonblob.com
s, body, h, _ = show("jsonblob POST", go("POST", "https://jsonblob.com/api/jsonBlob", data))
loc = h.get("Location") or h.get("location") or ""
if loc:
    url = loc if loc.startswith("http") else "https://jsonblob.com" + loc
    show("jsonblob GET", go("GET", url))
    show("jsonblob PUT", go("PUT", url, {**data, "v": 2}))
    show("jsonblob GET2", go("GET", url))
    show("jsonblob OPTIONS", go("OPTIONS", url, headers={"Access-Control-Request-Method": "PUT", "Access-Control-Request-Headers": "content-type"}))
# extendsclass
s, body, h, _ = show("extendsclass POST", go("POST", "https://json.extendsclass.com/bin", data, {"Security-key": "sangeet-probe-key"}))
try:
    bid = json.loads(body).get("id")
except Exception:
    bid = None
if bid:
    show("extendsclass GET", go("GET", f"https://json.extendsclass.com/bin/{bid}"))
    show("extendsclass PUT", go("PUT", f"https://json.extendsclass.com/bin/{bid}", {**data, "v": 2}, {"Security-key": "sangeet-probe-key"}))
    show("extendsclass GET2", go("GET", f"https://json.extendsclass.com/bin/{bid}"))
# restful-api.dev
s, body, h, _ = show("restful-api POST", go("POST", "https://api.restful-api.dev/objects", {"name": "sangeet-probe", "data": data}))
try:
    oid = json.loads(body).get("id")
except Exception:
    oid = None
if oid:
    show("restful-api PUT", go("PUT", f"https://api.restful-api.dev/objects/{oid}", {"name": "sangeet-probe", "data": {**data, "v": 2}}))
    show("restful-api GET", go("GET", f"https://api.restful-api.dev/objects/{oid}"))
# counters
show("abacus hit", go("GET", "https://abacus.jasoncameron.dev/hit/sangeet-probe/users"))
show("abacus hit2", go("GET", "https://abacus.jasoncameron.dev/hit/sangeet-probe/users"))
show("abacus get", go("GET", "https://abacus.jasoncameron.dev/get/sangeet-probe/users"))
show("counterapi v1 up", go("GET", "https://api.counterapi.dev/v1/sangeet-probe/users/up"))
show("counterapi v1 get", go("GET", "https://api.counterapi.dev/v1/sangeet-probe/users"))
show("countapi.xyz", go("GET", "https://api.countapi.xyz/hit/sangeet-probe/users"))
