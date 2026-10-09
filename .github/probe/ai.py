"""One-off probe: which free AI knows real Indian songs (each suggested song is checked on JioSaavn), and what the
report token may do (GitHub Models, a folder in the CMS repo). Prints no secrets and nothing from the private repo."""
import json, os, re, time, urllib.parse, urllib.request

ASKS = ["sad punjabi songs for a night drive, no remix", "haryanvi gym songs", "90s kumar sanu romantic hits"]
SYSTEM = ("You are the DJ of an Indian music app (JioSaavn catalogue). Reply ONLY with JSON: {\"title\":str,"
          "\"languages\":[str],\"searchQueries\":[str],\"songs\":[\"Song - Artist\"]}. songs: 10 real, well-known songs "
          "that fit (exact titles and main singer). searchQueries: 6 short catalogue searches.")


def go(url, data=None, headers=None, method=None, timeout=90):
    t = time.time()
    req = urllib.request.Request(url, data=json.dumps(data).encode() if data is not None else None, method=method,
                                 headers={"Content-Type": "application/json", "User-Agent": "Mozilla/5.0", **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace"), time.time() - t, dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), time.time() - t, dict(e.headers)
    except Exception as e:
        return 0, repr(e), time.time() - t, {}


def norm(s):
    s = re.sub(r"\(.*?\)|\[.*?\]", " ", s.lower())
    return " ".join(re.sub(r"[^\w]+", " ", s).split())


def saavn(q):
    p = {"__call": "search.getResults", "q": q, "p": "1", "n": "6", "_format": "json", "_marker": "0",
         "api_version": "4", "ctx": "web6dot0"}
    s, body, _, _ = go("https://www.jiosaavn.com/api.php?" + urllib.parse.urlencode(p), timeout=25)
    try:
        return json.loads(body).get("results") or []
    except Exception:
        return []


def real(song):
    parts = re.split(r"\s+[-–—]\s+|\s+by\s+", song, maxsplit=1)
    name, artist = norm(parts[0]), norm(parts[1]) if len(parts) > 1 else ""
    if len(name) < 2:
        return False
    for r in saavn(f"{parts[0]} {parts[1] if len(parts) > 1 else ''}"):
        title = norm(r.get("title", ""))
        who = norm(r.get("subtitle", "") + " " + json.dumps(r.get("more_info", {}).get("artistMap", {}).get("primary_artists", []), ensure_ascii=False))
        same = title == name or title.startswith(name) or (len(name) >= 5 and name in title)
        first = artist.split(" ")[0] if artist else ""
        if same and (not first or len(first) < 3 or first in who):
            return True
    return False


def score(label, text, secs):
    m = re.search(r"\{.*\}", text or "", re.S)
    try:
        plan = json.loads(m.group(0)) if m else None
    except Exception:
        plan = None
    if not plan:
        print(f"  {label}: no JSON ({secs:.1f}s): {(text or '')[:200]!r}")
        return 0, 0
    songs = [s for s in plan.get("songs", []) if isinstance(s, str)][:10]
    ok = [s for s in songs if real(s)]
    print(f"  {label}: {len(ok)}/{len(songs)} real in {secs:.1f}s | queries {plan.get('searchQueries', [])[:6]}")
    for s in songs:
        print(f"     {'✓' if s in ok else '✗'} {s}")
    return len(ok), len(songs)


def chat(url, model, ask, key):
    body = {"model": model, "messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": "Request: " + ask}],
            "temperature": 0.4, "max_tokens": 1200}
    s, text, secs, h = go(url, body, {"Authorization": f"Bearer {key}"} if key else {})
    content = ""
    if s == 200:
        try:
            content = json.loads(text)["choices"][0]["message"]["content"]
        except Exception:
            content = text
    return s, content or text, secs, h


totals = {}


def test(label, url, model, key):
    print(f"### {label} {model}")
    for ask in ASKS:
        s, content, secs, h = chat(url, model, ask, key)
        if s != 200:
            print(f"  {ask}: HTTP {s} {content[:250]!r}")
            break
        lim = {k: v for k, v in h.items() if "ratelimit" in k.lower()}
        if lim:
            print("  limits:", lim)
        ok, n = score(ask, content, secs)
        a, b = totals.get(f"{label} {model}", (0, 0))
        totals[f"{label} {model}"] = (a + ok, b + n)
        time.sleep(5)


GH = "https://models.github.ai"
tok = os.environ.get("REPORT_TOKEN", "")  # the app's own token: what the app would use
hdr = {"Authorization": f"Bearer {tok}", "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
s, body, _, _ = go(GH + "/catalog/models", headers=hdr)
print("### GitHub Models catalog:", s, len(body))
ids = []
try:
    for m in json.loads(body):
        ids.append(m["id"])
        print(f"  {m['id']}  tier={m.get('rate_limit_tier')}")
except Exception:
    print(body[:300])
want = ["deepseek/deepseek-v3", "openai/gpt-4.1", "openai/gpt-5-chat", "openai/gpt-4o", "meta/llama-4-maverick",
        "xai/grok-3", "openai/gpt-5-mini", "deepseek/deepseek-r1", "microsoft/mai-ds-r1", "openai/gpt-4.1-mini"]
picked = []
for w in want:
    hit = next((i for i in ids if i.lower().startswith(w) and i not in picked), None)
    if hit:
        picked.append(hit)
if not ids:
    picked = ["deepseek/DeepSeek-V3-0324", "openai/gpt-4.1", "openai/gpt-4.1-mini", "meta/Llama-4-Maverick-17B-128E-Instruct-FP8"]
for mid in picked[:9]:
    test("github-models", GH + "/inference/chat/completions", mid, tok)

print("\n### TOTAL real songs per model")
for k, (a, b) in sorted(totals.items(), key=lambda kv: -(kv[1][0] / max(1, kv[1][1]))):
    print(f"  {k}: {a}/{b}")

