#!/usr/bin/env python3
"""Makes one original Hindi AI video for the owner's YouTube channel and uploads it once the channel is linked.

Two kinds:
  short  a 9:16 Short of about 50-70 s (7-9 scenes).
  long   a 16:9 video of about 8-12 min: an outline first (characters, 8 chapters), then each chapter's scenes.
         "Bhootiya Haveli" runs as a series (episode N continues from the recap of episode N-1).
The channel is horror / suspense only (SHORT_FORMATS / LONG_FORMATS).

Steps: what India watches now (YouTube Data API) -> a Gemini plan in a format not used lately -> a picture per
scene (Pollinations; a real picture re-cut when one fails) -> a Hindi voice per line, a different voice per kind of
speaker (edge-tts, gTTS) -> pictures that slowly move, captions, a soft drone -> upload (youtube_login.py links
the channel) with a thumbnail for long videos.

Usage: make_video.py <state dir> <out dir> [--long] [--upload]
State dir: history.json (what was made, series episodes) and token.enc (the channel login, encrypted).
"""
import base64, json, os, random, re, subprocess, sys, time, urllib.parse, urllib.request
from datetime import datetime, timedelta, timezone

STATE, OUT = sys.argv[1], sys.argv[2]
LONG = "--long" in sys.argv
KIND = "long" if LONG else "short"
os.makedirs(STATE, exist_ok=True)
os.makedirs(OUT, exist_ok=True)
WORK = os.path.join(OUT, "work")
os.makedirs(WORK, exist_ok=True)

YT_KEY = os.environ.get("YT_API_KEY", "")
GEMINI_KEY = os.environ.get("GEMINI_API_KEY", "")
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
W, H = (1920, 1080) if LONG else (1080, 1920)

# Each format: what to make, and whether it is made for kids (YouTube's rule: say so truthfully).
# The channel is horror / suspense only (owner, Oct 27: one niche, so YouTube knows whom to show it to). The first
# two Short keys and "horror" / "mystery" / "trend" keep their old names so their view counts still count.
SHORT_FORMATS = [
    ("AI mini movie: suspense / mystery with a shocking last line", False),
    ("AI mini movie: horror story (spooky, not gory) set in an Indian village or old haveli", False),
    ("horror story in a city: a lift, a flat, a hostel, a hospital night shift or a metro, with a twist", False),
    ("highway / train / bus horror story at night, told like it really happened to the narrator", False),
    ("Indian folk horror: chudail, bhoot, pishach, daayan or a cursed well / tree from village stories", False),
    ("psychological thriller: something is wrong at home (a phone call, a CCTV clip, a mirror), last-line twist", False),
    ("mystery puzzle: a strange case with clues, and the viewer must guess the answer in the comments", False),
    (TREND_SHORT := "today's trend turned into an original horror or suspense story (use one of today's top searched "
                    "topics in India as the setting, never real people)", False),
]
LONG_FORMATS = [
    ("series:Bhootiya Haveli", "Bhootiya Haveli: an original Hindi horror series about one cursed haveli and the family "
                               "that moved in; each episode a new chilling night, the mystery slowly opening", False),
    ("horror", "Hindi horror movie (spooky, not gory) in an Indian village, haveli or highway, with a twist", False),
    ("mystery", "suspense thriller movie with clues and a twist ending", False),
    ("city-horror", "Hindi horror movie in a city: a new flat, a hostel, a hospital or an office at night", False),
    ("folk-horror", "Indian folk horror movie: chudail, daayan, pishach or a curse from village stories", False),
    ("trend", "an original horror or suspense movie set around what is trending most in the list below", False),
]


def log(*a):
    print(*a, flush=True)


def http(url, data=None, headers=None, timeout=60, method=None):
    h = {"User-Agent": UA}
    h.update(headers or {})
    if isinstance(data, (dict, list)):
        data = json.dumps(data).encode()
        h.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def load_history():
    try:
        return json.load(open(os.path.join(STATE, "history.json"), encoding="utf-8"))
    except Exception:
        return []


# ---------- 1. Trends ----------
TOPICS = []  # what India searches most today (Google Trends), filled by google_trends()


def google_trends():
    """Today's most searched topics in India from Google Trends' public feed (no key), with their search counts."""
    try:
        x = http("https://trends.google.com/trending/rss?geo=IN", timeout=30).decode("utf-8", "replace")
    except Exception as e:
        log("google trends failed:", e)
        return []
    out = []
    for item in re.findall(r"<item>(.*?)</item>", x, re.S):
        title = re.search(r"<title>(.*?)</title>", item, re.S)
        traffic = re.search(r"<ht:approx_traffic>(.*?)</ht:approx_traffic>", item, re.S)
        news = re.findall(r"<ht:news_item_title>(.*?)</ht:news_item_title>", item, re.S)[:1]
        if title:
            t = html_text(title.group(1))
            out.append(f"{t} ({html_text(traffic.group(1)) if traffic else '?'} searches)"
                       + (f": {html_text(news[0])[:90]}" if news else ""))
    log("google trends:", len(out), "topics, e.g.", out[:6])
    return out[:25]


def html_text(s):
    import html
    return html.unescape(re.sub(r"<!\[CDATA\[|\]\]>", "", s)).strip()


def trends():
    """Titles of what India watches now: the trending charts and the most viewed new videos. Searches use the
    channel's own login when there is one (its own quota; the app's key is often used up), else the app's key."""
    global TOPICS
    TOPICS = google_trends()
    out = []
    token = access_token()
    if not (YT_KEY or token):
        log("trends: no YouTube key")
        return out
    base = "https://www.googleapis.com/youtube/v3/"
    auth = {"Authorization": "Bearer " + token} if token else None
    for cat in ["", "1", "24"]:  # all, Film & Animation, Entertainment
        q = {"part": "snippet,statistics", "chart": "mostPopular", "regionCode": "IN", "maxResults": "30"}
        if not token:
            q["key"] = YT_KEY
        if cat:
            q["videoCategoryId"] = cat
        try:
            d = json.loads(http(base + "videos?" + urllib.parse.urlencode(q), headers=auth))
            out += [(int(v.get("statistics", {}).get("viewCount", 0)), v["snippet"]["title"]) for v in d["items"]]
        except Exception as e:
            log("trends: chart", cat or "all", "failed", e)
    since = (datetime.now(timezone.utc) - timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")
    words = (["horror story hindi", "bhootiya kahani", "suspense thriller hindi", "horror movie hindi"] if LONG else
             ["horror story #shorts", "bhoot ki kahani #shorts", "suspense story #shorts", "darawni kahani #shorts"])
    for q in words:
        p = {"part": "snippet", "q": q, "type": "video", "order": "viewCount", "regionCode": "IN",
             "relevanceLanguage": "hi", "publishedAfter": since, "maxResults": "10",
             "videoDuration": "long" if LONG else "short"}
        if not token:
            p["key"] = YT_KEY
        try:
            d = json.loads(http(base + "search?" + urllib.parse.urlencode(p), headers=auth))
            out += [(0, v["snippet"]["title"]) for v in d.get("items", [])]
        except Exception as e:
            log("trends: search", q, "failed", e)
            break  # 429 = the key's daily search quota is used up
    seen, titles = set(), []
    for _, t in sorted(out, key=lambda x: -x[0]):
        if t not in seen:
            seen.add(t)
            titles.append(t)
    log("trends:", len(titles), "titles, e.g.", titles[:5])
    return titles[:70]


# ---------- 2. Plan ----------
_models = None


def gemini_models():
    """Text models to try, best first: the newest plain Flash, then the other Flash / Flash-Lite / Pro ones
    (when one is busy, 503, another often isn't)."""
    global _models
    if _models is None:
        d = json.loads(http("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=" + GEMINI_KEY))
        names = [m["name"] for m in d.get("models", []) if "generateContent" in m.get("supportedGenerationMethods", [])
                 and re.match(r"^models/gemini-\d", m["name"])
                 and not re.search(r"image|tts|audio|live|embed|vision|computer|robotics", m["name"])]

        def rank(n):
            m = re.search(r"gemini-(\d+(?:\.\d+)?)", n)
            v = float(m.group(1)) if m else 0
            plain = bool(re.match(r"^models/gemini-\d+(\.\d+)?-flash(-latest)?$", n))
            kind = 0 if plain else 1 if "flash" in n and "lite" not in n else 2 if "lite" in n else 3
            return (kind, "preview" in n or "exp" in n, -v)

        _models = sorted(names, key=rank)[:6] or ["models/gemini-flash-latest"]
        log("plan: gemini models", [m.split("/")[-1] for m in _models])
    return _models


def ask_gemini(prompt, n=0):
    models = gemini_models()
    model = models[n % len(models)]
    log("plan: asking", model)
    body = {"contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {"responseMimeType": "application/json", "temperature": 1.0}}
    d = json.loads(http(f"https://generativelanguage.googleapis.com/v1beta/{model}:generateContent?key={GEMINI_KEY}",
                        body, timeout=300))
    return "".join(p.get("text", "") for p in d["candidates"][0]["content"]["parts"] if not p.get("thought"))


def ask_llm7(prompt):
    for model in ["glm-5.2", "DeepSeek-V4-Flash-0731", "minimax-m3", "gemma4:31b"]:
        try:
            log("plan: asking LLM7", model)
            d = json.loads(http("https://api.llm7.io/v1/chat/completions",
                                {"model": model, "messages": [{"role": "user", "content": prompt}]},
                                headers={"Authorization": "Bearer unused"}, timeout=180))
            text = d["choices"][0]["message"].get("content") or ""
            if "{" in text:
                return text
            log("plan: LLM7", model, "gave no JSON:", text[:300])
        except Exception as e:
            log("plan: LLM7", model, "failed", e)
    raise RuntimeError("no LLM answered")


def parse_json(text):
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.S)
    return json.loads(re.search(r"\{.*\}", text, re.S).group(0))


def ask_json(prompt, ok):
    """Gemini, 10 tries over its models for up to ~15 minutes (its free tier is often busy, 503), then LLM7;
    `ok` checks the answer."""
    tries = 10 if GEMINI_KEY else 0
    for n in range(tries + 1):
        try:
            p = parse_json(ask_gemini(prompt, n) if n < tries else ask_llm7(prompt))
            if ok(p):
                return p
            log("plan: answer not complete, again")
        except Exception as e:
            log("plan failed:", type(e).__name__, str(e)[:300])
        if n < tries:
            time.sleep(min(20 + 15 * n, 120))
    raise RuntimeError("no plan")


def scenes_ok(n):
    return lambda p: len([s for s in p.get("scenes", []) if s.get("narration") and s.get("image_prompt")]) >= n


SCENE_RULES = """- narration: what is heard, in simple everyday Hindi in Devanagari script (Hinglish words are fine).
- speaker: who says it: "narrator", "man", "woman", "old_man", "old_woman", "boy", "girl" or "god" (a deep calm
  voice for gods and sages). Dialogue lines are said by the character; the narrator tells the rest.
- image_prompt: English, a detailed picture of that moment. Start with the art style words, describe every
  character in it with their fixed look from the character list (clothes, colors, age, hair), the place and the
  light. {shape}, no text, no letters, no watermark in the picture."""


# ---------- What works on the channel ----------
def channel_stats(history):
    """Views, likes and comments of the channel's own videos (1 quota unit per 50), kept in the history."""
    ids = [h["youtube"] for h in history if h.get("youtube")]
    if not ids:
        return
    token = None
    try:
        token = access_token()
    except Exception as e:
        log("stats: login failed", e)
    got = {}
    for k in range(0, len(ids), 50):
        q = "https://www.googleapis.com/youtube/v3/videos?part=statistics&id=" + ",".join(ids[k:k + 50])
        try:
            d = json.loads(http(q + ("" if token else "&key=" + YT_KEY),
                                headers={"Authorization": "Bearer " + token} if token else None))
            got.update({v["id"]: v.get("statistics", {}) for v in d.get("items", [])})
        except Exception as e:
            log("stats failed:", e)
            return
    now = datetime.now(timezone.utc)
    for h in history:
        s = got.get(h.get("youtube"))
        if s is None:
            continue
        h["views"], h["likes"] = int(s.get("viewCount", 0)), int(s.get("likeCount", 0))
        h["comments"] = int(s.get("commentCount", 0))
        made = datetime.strptime(h["date"], "%Y%m%d-%H%M").replace(tzinfo=timezone.utc)
        days = max(1.0, (now - made).total_seconds() / 86400)
        # Likes and comments count extra: they are what YouTube pushes.
        h["score"] = round((h["views"] + 20 * h["likes"] + 40 * h["comments"]) / days, 1)
    for h in history[-12:]:
        if "score" in h:
            log(f"stats: {h['score']:>8}/day  {h.get('views')} views  {h['format'][:40]} | {h['title'][:60]}")


def pick_format(keys, history, kind, avoid):
    """Formats that did well on the channel come more often; untried ones get an average chance, and 1 in 4 picks
    is random so new trends still get tried. `avoid` = how many of the latest formats not to repeat."""
    mine = [h for h in history if h.get("kind", "short") == kind]
    recent = [h["format"] for h in mine][-avoid:] if avoid else []
    keys = [k for k in keys if k not in recent] or keys
    scores = {}
    for h in mine:
        if "score" in h:
            scores.setdefault(h["format"], []).append(h["score"])
    avg = {k: sum(v) / len(v) for k, v in scores.items()}
    trend = [k for k in keys if k in (TREND_SHORT, "trend")]
    if trend and TOPICS and random.random() < 0.3:
        log("format: today's trend")
        return trend[0]
    if random.random() < 0.25 or not avg:
        pick = random.choice(keys)
        log("format: random pick", pick[:50])
        return pick
    mean = sum(avg.values()) / len(avg) or 1
    weights = [0.3 + 2 * avg.get(k, mean) / (mean or 1) for k in keys]
    pick = random.choices(keys, weights)[0]
    log("format: weighted", {k[:30]: round(w, 2) for k, w in zip(keys, weights)}, "->", pick[:50])
    return pick


def best_titles(history, kind, n=5):
    done = sorted([h for h in history if h.get("kind", "short") == kind and h.get("score")], key=lambda h: -h["score"])
    return [f"{h['title']} ({h['views']} views)" for h in done[:n]]


def plan_short(titles, history):
    key = pick_format([f[0] for f in SHORT_FORMATS], history, "short", avoid=2)
    fmt, kids = next(f for f in SHORT_FORMATS if f[0] == key)
    recent = [h.get("title", "") for h in history[-40:]]
    best = best_titles(history, "short")
    prompt = f"""You run a Hindi YouTube channel of ORIGINAL AI-made videos. Make the next Short.

Format: {fmt}

Trending in India now (use it for the topic, mood and hook; never copy a video, never use real people, brands, film
names or copyrighted characters):
{chr(10).join('- ' + t for t in titles[:50]) or '- (no data)'}

Searched most in India today (Google Trends; if the format is about a trend, build the whole video around the
best of these for a family audience; skip politics, crimes and real people's deaths or illness, and don't state
news as fact):
{chr(10).join('- ' + x for x in TOPICS[:20]) or '- (no data)'}

Already made, don't repeat: {'; '.join(recent) or '(nothing yet)'}

Did best on this channel so far (learn what viewers liked: topic, hook, title style):
{chr(10).join('- ' + b for b in best) or '- (no data yet)'}

Rules:
- 7 to 9 scenes, each 1-2 short spoken sentences; all narration together 110-150 words (about 55 seconds).
- Scene 1 is a strong hook in the first 2 seconds. The last scene ends the story and asks viewers to comment or
  subscribe (one short line).
{SCENE_RULES.format(shape="Vertical 9:16")}
- title: catchy, max 70 characters, Hindi in Devanagari or Hinglish, ends with #shorts.
- description: 2-3 lines in Hindi plus 5 hashtags. tags: 10 short tags.

Answer with only this JSON:
{{"title": "...", "description": "...", "tags": ["..."], "style": "...",
 "characters": [{{"name": "...", "look": "..."}}],
 "scenes": [{{"speaker": "narrator", "narration": "...", "image_prompt": "..."}}]}}"""
    p = ask_json(prompt, scenes_ok(5))
    p.update(format=fmt, kids=kids, kind="short")
    return p


def plan_long(titles, history):
    longs = [h for h in history if h.get("kind") == "long"]
    key = pick_format([f[0] for f in LONG_FORMATS], history, "long", avoid=1)
    key, fmt, kids = next(f for f in LONG_FORMATS if f[0] == key)
    best = best_titles(history, "long")
    series, episode, recaps = None, 1, []
    if key.startswith("series:"):
        series = key.split(":", 1)[1]
        past = [h for h in longs if h.get("series") == series]
        episode = len(past) + 1
        recaps = [f"Episode {h.get('episode')}: {h.get('recap', '')}" for h in past[-6:]]
    recent = [h.get("title", "") for h in history[-40:]]
    series_rule = (f"""This is {series} episode {episode}. Continue the story in order from where the last episode
ended (or start from the very beginning for episode 1). Earlier episodes:
{chr(10).join(recaps) or '(none, this is the first episode)'}
Title format: "{series} Episode {episode} | <what happens>" in Hindi.""" if series else
                   "Make an original story (new characters, no real people, no copyrighted characters or film names).")
    outline_prompt = f"""You run a Hindi YouTube channel of AI-made movies. Plan the next long video (8-12 minutes).

Format: {fmt}
{series_rule}

Trending in India now (for mood and topic ideas; never copy a video):
{chr(10).join('- ' + t for t in titles[:40]) or '- (no data)'}

Searched most in India today (Google Trends; if the format is about a trend, build the whole video around the
best of these for a family audience; skip politics, crimes and real people's deaths or illness, and don't state
news as fact):
{chr(10).join('- ' + x for x in TOPICS[:20]) or '- (no data)'}

Already made, don't repeat: {'; '.join(recent) or '(nothing yet)'}

Did best on this channel so far (learn what viewers liked):
{chr(10).join('- ' + b for b in best) or '- (no data yet)'}

Make:
- title: max 90 characters, Hindi in Devanagari (Hinglish is fine), gripping.
- description: 5-6 lines in Hindi that make people watch, plus 6 hashtags. tags: 15 tags.
- style: one art style for every picture (for example "dark cinematic realistic, foggy night, cold blue and amber
  light" or "eerie Indian village at night, lantern light, film grain",
  "dark cinematic realistic, foggy, moody" for horror).
- characters: everyone who appears, each with a fixed look in English (age, face, hair, clothes, colors).
- chapters: exactly 8, each a short English summary of what happens; chapter 1 starts with a hook, the last one
  ends the story (or, for a series, ends on a cliffhanger for the next episode).
- recap: 2 lines in English of what happened in this video (to continue the series later).
- thumbnail_text: 2-4 Hindi words for the thumbnail.

Answer with only this JSON:
{{"title": "...", "description": "...", "tags": ["..."], "style": "...", "thumbnail_text": "...",
 "characters": [{{"name": "...", "look": "..."}}], "chapters": ["..."], "recap": "..."}}"""
    p = ask_json(outline_prompt, lambda o: o.get("title") and len(o.get("chapters", [])) >= 5)
    chars = "\n".join(f"- {c.get('name')}: {c.get('look')}" for c in p.get("characters", []))
    p["scenes"] = []
    for n, ch in enumerate(p["chapters"]):
        done = " ".join(s["narration"] for s in p["scenes"][-3:])
        chap_prompt = f"""Write chapter {n + 1} of {len(p['chapters'])} of the Hindi video "{p['title']}".
Format: {fmt}. Art style for every picture: {p.get('style')}.
Characters (always use these looks in the pictures):
{chars}
Whole plan: {' | '.join(f'{i + 1}. {c}' for i, c in enumerate(p['chapters']))}
This chapter: {ch}
The last lines before it: {done or '(this is the start)'}

Rules:
- 6 to 8 scenes, each 1-3 spoken sentences; this chapter together 150-190 words (about 75 seconds).
- Make it gripping: feelings, dialogue between the characters, a small cliffhanger at the end of the chapter.
{"- Scene 1 is a hook that makes people stay." if n == 0 else ""}
{"- The last scene ends the video and asks viewers to like, comment and subscribe for the next part." if n == len(p['chapters']) - 1 else ""}
{SCENE_RULES.format(shape="Wide 16:9 cinematic")}

Answer with only this JSON:
{{"scenes": [{{"speaker": "narrator", "narration": "...", "image_prompt": "..."}}]}}"""
        c = ask_json(chap_prompt, scenes_ok(4))
        p["scenes"] += [s for s in c["scenes"] if s.get("narration") and s.get("image_prompt")][:9]
        log(f"plan: chapter {n + 1}: {len(c['scenes'])} scenes")
        time.sleep(4)
    p.update(format=key, kids=kids, kind="long", series=series, episode=episode if series else None)
    return p


# ---------- 3. Pictures ----------
def picture_pollinations(prompt, seed, path):
    q = urllib.parse.quote(prompt[:1200])
    gw, gh = (1344, 768) if LONG else (768, 1344)
    url = (f"https://image.pollinations.ai/prompt/{q}?width={gw}&height={gh}&seed={seed}&nologo=true"
           f"&model=flux&enhance=false")
    data = http(url, timeout=100)
    if len(data) < 20000 or data[:3] not in (b"\xff\xd8\xff", b"\x89PN"):
        raise RuntimeError(f"not a picture ({len(data)} bytes)")
    open(path, "wb").write(data)
    # The free tier stamps "pollinations.ai" in the bottom right corner; cut that strip off.
    from PIL import Image
    img = Image.open(path).convert("RGB")
    img.crop((0, 0, img.width, int(img.height * 0.93))).save(path, quality=95)


def picture_gemini(prompt, path):
    d = json.loads(http("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=" + GEMINI_KEY))
    models = [m["name"] for m in d.get("models", []) if "image" in m["name"] and "generateContent" in
              m.get("supportedGenerationMethods", [])]
    for model in sorted(models, reverse=True):
        try:
            body = {"contents": [{"parts": [{"text": prompt}]}], "generationConfig": {"responseModalities": ["IMAGE"]}}
            r = json.loads(http(f"https://generativelanguage.googleapis.com/v1beta/{model}:generateContent?key="
                                + GEMINI_KEY, body, timeout=120))
            for part in r["candidates"][0]["content"]["parts"]:
                if "inlineData" in part:
                    open(path, "wb").write(base64.b64decode(part["inlineData"]["data"]))
                    return
        except Exception as e:
            log("picture: gemini", model, "failed", str(e)[:200])
    raise RuntimeError("no gemini picture")


def picture_card(i, path):
    """Last resort: a soft gradient so the video still works."""
    from PIL import Image, ImageDraw
    rnd = random.Random(i * 7919)
    a = [rnd.randint(20, 120) for _ in range(3)]
    b = [rnd.randint(80, 220) for _ in range(3)]
    img = Image.new("RGB", (W, H))
    dr = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        dr.line([(0, y), (W, y)], fill=tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3)))
    img.save(path, quality=92)


def fit_picture(path):
    """Crop / scale any picture to exactly W x H."""
    from PIL import Image
    img = Image.open(path).convert("RGB")
    r = max(W / img.width, H / img.height)
    img = img.resize((int(img.width * r + 1), int(img.height * r + 1)), Image.LANCZOS)
    x, y = (img.width - W) // 2, (img.height - H) // 2
    img.crop((x, y, x + W, y + H)).save(path, quality=93)


def reuse(src, dst, i):
    """Another part of a picture, zoomed in and mirrored, so the same picture doesn't look the same twice."""
    from PIL import Image, ImageOps
    img = Image.open(src).convert("RGB")
    w, h = int(img.width * 0.72), int(img.height * 0.72)
    x = [0, img.width - w, (img.width - w) // 2][i % 3]
    y = [(img.height - h) // 2, 0, img.height - h][i % 3]
    img = img.crop((x, y, x + w, y + h)).resize((W, H), Image.LANCZOS)
    if i % 2:
        img = ImageOps.mirror(img)
    img.save(dst, quality=93)


def pictures(p):
    """Pollinations' free tier allows about one picture at a time (402 when asked too fast) and is often slow, so
    wait between asks and stop asking when the time budget is used up. A scene with no picture of its own reuses
    the last real one, cut differently, instead of a plain card."""
    seed = random.randint(1, 10 ** 6)
    paths, kinds = [], []
    gemini_ok = bool(GEMINI_KEY)
    budget = time.time() + (150 if LONG else 30) * 60
    for i, s in enumerate(p["scenes"]):
        path = os.path.join(WORK, f"img{i:03d}.jpg")
        prompt = f"{p.get('style', '')}. {s['image_prompt']}. cinematic lighting, highly detailed"
        kind = "card"
        for attempt in range(3):
            if time.time() > budget:
                break
            try:
                picture_pollinations(prompt, seed + i, path)
                kind = "pollinations"
                break
            except Exception as e:
                log(f"picture {i}: pollinations try {attempt + 1} failed:", str(e)[:150])
                time.sleep(20 + attempt * 10)
        if kind == "card" and gemini_ok:
            try:
                picture_gemini(prompt, path)
                kind = "gemini"
            except Exception as e:
                log(f"picture {i}:", e)
                gemini_ok = False  # the free key usually has no picture quota; don't ask again
        if kind == "card":
            real = [k for k in range(i) if kinds[k] in ("pollinations", "gemini")]
            if real:
                reuse(paths[real[-1]], path, i)
                kind = "reused"
            else:
                picture_card(i, path)
        fit_picture(path)
        paths.append(path)
        kinds.append(kind)
        if kind == "pollinations":
            time.sleep(12)
    first = next((k for k, x in enumerate(kinds) if x in ("pollinations", "gemini")), None)
    if first is not None:
        for k in range(first):
            reuse(paths[first], paths[k], k)
            kinds[k] = "reused"
    log("pictures:", {k: kinds.count(k) for k in set(kinds)})
    return paths, kinds


# ---------- 4. Voice ----------
# edge-tts has two Hindi voices; pitch and speed make the other speakers.
VOICES = {
    "narrator": ("hi-IN-MadhurNeural", "-2%", "-4Hz"),
    "man": ("hi-IN-MadhurNeural", "+4%", "+0Hz"),
    "woman": ("hi-IN-SwaraNeural", "+4%", "+0Hz"),
    "old_man": ("hi-IN-MadhurNeural", "-12%", "-14Hz"),
    "old_woman": ("hi-IN-SwaraNeural", "-10%", "-10Hz"),
    "boy": ("hi-IN-MadhurNeural", "+8%", "+30Hz"),
    "girl": ("hi-IN-SwaraNeural", "+8%", "+25Hz"),
    "god": ("hi-IN-MadhurNeural", "-15%", "-20Hz"),
}


def duration(path):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
                                          "csv=p=0", path]).decode().strip())


def voices(p):
    out, kinds = [], set()
    for i, s in enumerate(p["scenes"]):
        voice, rate, pitch = VOICES.get(s.get("speaker", "narrator"), VOICES["narrator"])
        if not LONG and s.get("speaker", "narrator") == "narrator" and p.get("kids"):
            voice, rate, pitch = VOICES["woman"]
        path = os.path.join(WORK, f"voice{i:03d}.mp3")
        ok = False
        for attempt in range(3):
            r = subprocess.run(["edge-tts", "--voice", voice, f"--rate={rate}", f"--pitch={pitch}",
                                "--text", s["narration"], "--write-media", path], capture_output=True, text=True)
            if r.returncode == 0 and os.path.exists(path) and os.path.getsize(path) > 2000:
                ok = True
                break
            log(f"voice {i}: edge-tts failed", r.stderr[-300:])
            time.sleep(3)
        if not ok:
            from gtts import gTTS
            gTTS(s["narration"], lang="hi").save(path)
        kinds.add("edge" if ok else "gtts")
        out.append(path)
    log("voice:", kinds)
    return out, "+".join(sorted(kinds))


# ---------- 5. Video ----------
def plain(text):
    """Text for the captions / thumbnail font: no emoji (they show as boxes) and no ASS braces."""
    text = re.sub(r"[\U00010000-\U0010FFFF\u2600-\u27BF\uFE0F\u200D]", "", text)
    return re.sub(r"\s+", " ", text.replace("{", "(").replace("}", ")")).strip()


def ass_time(t):
    return f"{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}"


def ass_head(styles):
    return ["[Script Info]", "ScriptType: v4.00+", f"PlayResX: {W}", f"PlayResY: {H}", "", "[V4+ Styles]",
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, "
            "Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
            "Alignment, MarginL, MarginR, MarginV, Encoding"] + styles + [
            "", "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"]


def captions(p, durs, path, gap):
    """Shorts: 3 words at a time, big and yellow in the middle. Long: 7 words at a time at the bottom; the title
    shows for the first 5 seconds."""
    if LONG:
        styles = ["Style: Cap,Noto Sans Devanagari,58,&H00FFFFFF,&H00FFFFFF,&H00000000,&H96000000,-1,0,0,0,100,100,0,0,"
                  "1,4,2,2,120,120,60,1",
                  "Style: Title,Noto Sans Devanagari,96,&H0000E1FF,&H00FFFFFF,&H00000000,&H96000000,-1,0,0,0,100,100,"
                  "0,0,1,6,3,8,100,100,90,1"]
        per = 7
    else:
        styles = ["Style: Cap,Noto Sans Devanagari,92,&H0000F6FF,&H00FFFFFF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,"
                  "1,7,3,2,70,70,560,1"]
        per = 3
    lines = ass_head(styles)
    if LONG:
        title = plain(re.sub(r"\s*#\w+", "", p["title"]))
        lines.append(f"Dialogue: 1,{ass_time(0.3)},{ass_time(5.3)},Title,,0,0,0,,{{\\fad(300,400)}}{title}")
    t = 0.0
    for s, d in zip(p["scenes"], durs):
        words = plain(s["narration"]).split()
        chunks = [" ".join(words[k:k + per]) for k in range(0, len(words), per)] or [""]
        total = sum(len(c) for c in chunks) or 1
        c0 = t
        for c in chunks:
            c1 = c0 + (d - gap) * len(c) / total
            text = c
            lines.append(f"Dialogue: 0,{ass_time(c0)},{ass_time(c1)},Cap,,0,0,0,,{{\\fad(60,60)}}{text}")
            c0 = c1
        t += d
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def video(p, imgs, vox, path):
    gap = 0.45 if LONG else 0.35
    durs = [duration(v) + gap for v in vox]
    fps = 30 if not LONG else 25
    clips = []
    for i, (img, d) in enumerate(zip(imgs, durs)):
        clip = os.path.join(WORK, f"clip{i:03d}.mp4")
        n = int(d * fps) + 1
        # Slow zoom in or out and a little pan, different per scene, on a 2x picture so it stays sharp.
        step = 0.15 / n
        z = f"min(1.0+{step:.6f}*on,1.15)" if i % 2 == 0 else f"max(1.15-{step:.6f}*on,1.0)"
        xexpr = ["iw/2-(iw/zoom/2)", f"(iw-iw/zoom)*on/{n}", f"(iw-iw/zoom)*(1-on/{n})"][i % 3]
        vf = (f"scale={W * 2}:{H * 2},zoompan=z='{z}':x='{xexpr}':y='ih/2-(ih/zoom/2)':d={n}:s={W}x{H}:fps={fps},"
              f"format=yuv420p")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-i", img, "-vf", vf, "-frames:v", str(n),
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", clip], check=True)
        clips.append(clip)
    lst = os.path.join(WORK, "clips.txt")
    open(lst, "w").write("".join(f"file '{os.path.abspath(c)}'\n" for c in clips))
    alst = os.path.join(WORK, "voices.txt")
    pads = []
    for i, v in enumerate(vox):
        pv = os.path.join(WORK, f"pad{i:03d}.wav")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", v, "-af", f"apad=pad_dur={gap}", "-ar", "44100",
                        "-ac", "2", pv], check=True)
        pads.append(pv)
    open(alst, "w").write("".join(f"file '{os.path.abspath(a)}'\n" for a in pads))
    ass = os.path.join(WORK, "captions.ass")
    captions(p, durs, ass, gap)
    total = sum(durs)
    # A soft tanpura-like drone (Sa and Pa) under the voice so the video isn't dead quiet between lines.
    bed = ("aevalsrc='(0.035*sin(2*PI*130.8*t)+0.02*sin(2*PI*196*t)+0.012*sin(2*PI*261.6*t))"
           f"*(0.65+0.35*sin(2*PI*0.08*t))':s=44100:d={total:.2f}")
    subprocess.run(["ffmpeg", "-v", "error", "-y",
                    "-f", "concat", "-safe", "0", "-i", lst,
                    "-f", "concat", "-safe", "0", "-i", alst,
                    "-f", "lavfi", "-i", bed,
                    "-filter_complex",
                    f"[0:v]subtitles='{ass}'[v];[2:a]aformat=channel_layouts=stereo,volume=0.6[b];"
                    f"[1:a][b]amix=inputs=2:duration=first:dropout_transition=0,loudnorm=I=-14:TP=-1.5[a]",
                    "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "medium", "-crf", "21",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", "-shortest",
                    path], check=True)
    log(f"video: {path} {total:.1f} s, {os.path.getsize(path) // 1024} KB")
    return total


def thumbnail(p, imgs, kinds, path):
    """1280x720: the first real picture with the thumbnail words big in yellow."""
    best = next((im for im, k in zip(imgs, kinds) if k in ("pollinations", "gemini")), imgs[0])
    text = plain(p.get("thumbnail_text") or p["title"].split("|")[-1])[:40]
    ass = os.path.join(WORK, "thumb.ass")
    lines = ["[Script Info]", "ScriptType: v4.00+", "PlayResX: 1280", "PlayResY: 720", "", "[V4+ Styles]",
             "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, "
             "Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
             "Alignment, MarginL, MarginR, MarginV, Encoding",
             "Style: T,Noto Sans Devanagari,120,&H0000EAFF,&H00FFFFFF,&H00000000,&HA0000000,-1,0,0,0,100,100,0,0,1,"
             "10,6,1,50,50,40,1", "", "[Events]",
             "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text",
             f"Dialogue: 0,0:00:00.00,0:00:05.00,T,,0,0,0,,{text}"]
    open(ass, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", best, "-vf",
                    f"scale=1280:720,eq=saturation=1.25:contrast=1.08,subtitles='{ass}'", "-frames:v", "1",
                    "-q:v", "3", path], check=True)


# ---------- 6. Upload (after the owner linked the channel with youtube_login.py) ----------
_token = [None, 0.0]


def access_token():
    """A fresh access token: the browser login (token2.enc, may also reply to comments) first, else the TV login."""
    if _token[0] and time.time() < _token[1]:
        return _token[0]
    for name, id_env, secret_env in (("token2.enc", "YT_DESKTOP_ID", "YT_DESKTOP_SECRET"),
                                     ("token.enc", "YT_CLIENT_ID", "YT_CLIENT_SECRET")):
        tok = os.path.join(STATE, name)
        cid, secret = os.environ.get(id_env, ""), os.environ.get(secret_env, "")
        if not (os.path.exists(tok) and cid and secret):
            continue
        refresh = subprocess.run(["openssl", "enc", "-d", "-aes-256-cbc", "-pbkdf2", "-a", "-A", "-in", tok,
                                  "-pass", "env:" + secret_env], capture_output=True, text=True).stdout.strip()
        if not refresh:
            log(f"upload: the saved login {name} can't be read; run the YouTube login again")
            continue
        try:
            d = json.loads(http("https://oauth2.googleapis.com/token", urllib.parse.urlencode(
                {"client_id": cid, "client_secret": secret, "refresh_token": refresh,
                 "grant_type": "refresh_token"}).encode(),
                headers={"Content-Type": "application/x-www-form-urlencoded"}))
        except urllib.error.HTTPError as e:
            log(f"LOGIN EXPIRED ({name}): run this workflow by hand with 'login' ticked", e.code,
                e.read().decode(errors="replace")[:200])
            continue
        _token[:] = [d["access_token"], time.time() + int(d.get("expires_in", 3600)) - 120]
        return d["access_token"]
    return None


def upload(p, path, thumb):
    token = access_token()
    if not token:
        log("upload: channel not linked yet (secrets YT_CLIENT_ID / YT_CLIENT_SECRET, then the YouTube login)")
        return None
    desc = p.get("description", "") + "\n\nThis story, its pictures and its voices are made with AI."
    meta = {"snippet": {"title": p["title"][:100], "description": desc[:4900], "tags": p.get("tags", [])[:20],
                        "categoryId": "1", "defaultLanguage": "hi", "defaultAudioLanguage": "hi"},
            # containsSyntheticMedia: YouTube's "altered or synthetic content" label, required for AI-made videos.
            "status": {"privacyStatus": "public", "selfDeclaredMadeForKids": bool(p.get("kids")),
                       "containsSyntheticMedia": True}}
    size = os.path.getsize(path)
    req = urllib.request.Request(
        "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status",
        data=json.dumps(meta).encode(), method="POST",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json; charset=UTF-8",
                 "X-Upload-Content-Type": "video/mp4", "X-Upload-Content-Length": str(size)})
    with urllib.request.urlopen(req, timeout=60) as r:
        loc = r.headers["Location"]
    with open(path, "rb") as f:
        req = urllib.request.Request(loc, data=f, method="PUT",
                                     headers={"Authorization": "Bearer " + token, "Content-Type": "video/mp4",
                                              "Content-Length": str(size)})
        with urllib.request.urlopen(req, timeout=1800) as r:
            v = json.loads(r.read())
    url = ("https://youtube.com/shorts/" if not LONG else "https://youtu.be/") + v["id"]
    log("UPLOADED", url, "privacy:", v.get("status", {}).get("privacyStatus"))
    if v.get("status", {}).get("privacyStatus") != "public":
        # Google keeps uploads from an API project it hasn't audited private; ask once more in case it allows it.
        try:
            r = json.loads(http("https://www.googleapis.com/youtube/v3/videos?part=status",
                                {"id": v["id"], "status": {"privacyStatus": "public",
                                                           "selfDeclaredMadeForKids": bool(p.get("kids")),
                                                           "containsSyntheticMedia": True}},
                                headers={"Authorization": "Bearer " + token}, method="PUT"))
            log("privacy now:", r.get("status", {}).get("privacyStatus"))
        except urllib.error.HTTPError as e:
            log("privacy: can't make it public", e.code, e.read().decode(errors="replace")[:300])
    if thumb and os.path.exists(thumb):
        try:
            http(f"https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId={v['id']}",
                 open(thumb, "rb").read(), headers={"Authorization": "Bearer " + token, "Content-Type": "image/jpeg"})
            log("thumbnail: set")
        except urllib.error.HTTPError as e:
            # Custom thumbnails need a phone-verified channel (youtube.com/verify), else 403.
            log("thumbnail: not set", e.code, e.read().decode(errors="replace")[:300])
    return v["id"]


def upload_pending(history):
    """Videos that were made but not uploaded (the login had expired, YouTube was down…) go up now, from their copy
    in the release youtube-videos: at most 3 a run, made in the last 10 days, 3 tries each."""
    now = datetime.now(timezone.utc)
    todo = [h for h in history if not h.get("youtube") and h.get("file") and h.get("tries", 0) < 3
            and (now - datetime.strptime(h["date"], "%Y%m%d-%H%M").replace(tzinfo=timezone.utc)).days <= 10][-3:]
    if not todo or not access_token():
        return
    global LONG
    for h in todo:
        base = h["file"][:-4]
        d = os.path.join(WORK, "pending")
        os.makedirs(d, exist_ok=True)
        r = subprocess.run(["gh", "release", "download", "youtube-videos", "-D", d, "--clobber",
                            "-p", base + ".mp4", "-p", base + ".json", "-p", base + ".jpg"], capture_output=True,
                           text=True)
        if not os.path.exists(os.path.join(d, base + ".json")):
            log("pending:", base, "has no copy in the release", r.stderr[-200:])
            h["tries"] = 3
            continue
        p = json.load(open(os.path.join(d, base + ".json"), encoding="utf-8"))
        h["tries"] = h.get("tries", 0) + 1
        was, LONG = LONG, h.get("kind") == "long"
        try:
            h["youtube"] = upload(p, os.path.join(d, base + ".mp4"), os.path.join(d, base + ".jpg") if LONG else None)
            log("pending: uploaded", base, h["youtube"])
        except urllib.error.HTTPError as e:
            log("pending: upload failed", base, e.code, e.read().decode(errors="replace")[:500])
        except Exception as e:
            log("pending: upload failed", base, e)
        LONG = was


# ---------- 7. Replies to comments ----------
def reply_text(title, comment):
    """A short friendly reply in the commenter's own language, or None for spam / abuse / links."""
    prompt = f"""You run the Hindi YouTube channel of AI-made stories. A viewer commented on the video "{title}".
Comment: {comment[:600]}

Write the channel's reply: warm, natural, 1-2 short sentences (max 25 words), in the same language and script as
the comment (Hindi, Hinglish or English). Thank them or answer their question; if they ask for a part 2 or a topic,
say it is coming soon. No links, no hashtags, at most one emoji, don't ask for likes or subscriptions.
If the comment is spam, abuse, a link, an ad or makes no sense, the reply is "SKIP".
Answer with only this JSON: {{"reply": "..."}}"""
    p = ask_json(prompt, lambda o: isinstance(o.get("reply"), str) and o["reply"].strip())
    r = p["reply"].strip()
    if r.upper().startswith("SKIP") or "http" in r.lower() or len(r) > 300:
        return None
    return r


def reply_comments(history, limit=60):
    """Answers new top-level comments on the channel's videos that have no reply from the channel yet."""
    token = access_token()
    if not token:
        return
    auth = {"Authorization": "Bearer " + token}
    path = os.path.join(STATE, "replied.json")
    try:
        replied = set(json.load(open(path)))
    except Exception:
        replied = set()
    try:
        me = json.loads(http("https://www.googleapis.com/youtube/v3/channels?part=id&mine=true", headers=auth))
        mine = me["items"][0]["id"]
        d = json.loads(http("https://www.googleapis.com/youtube/v3/commentThreads?part=snippet,replies&maxResults=100"
                            "&order=time&allThreadsRelatedToChannelId=" + mine, headers=auth))
    except Exception as e:
        log("comments: can't read", getattr(e, "code", ""), e.read().decode(errors="replace")[:300]
            if hasattr(e, "read") else e)
        return
    titles = {h.get("youtube"): h.get("title", "") for h in history}
    done = 0
    for th in d.get("items", []):
        top = th["snippet"]["topLevelComment"]["snippet"]
        if th["id"] in replied or top.get("authorChannelId", {}).get("value") == mine:
            continue
        if any(c["snippet"].get("authorChannelId", {}).get("value") == mine
               for c in th.get("replies", {}).get("comments", [])):
            replied.add(th["id"])
            continue
        if done >= limit:
            break
        text = top.get("textOriginal") or top.get("textDisplay") or ""
        try:
            r = reply_text(titles.get(th["snippet"].get("videoId"), ""), text)
        except Exception as e:
            log("comments: no reply text", e)
            break
        replied.add(th["id"])
        if not r:
            log("comments: skipped", text[:80])
            continue
        try:
            http("https://www.googleapis.com/youtube/v3/comments?part=snippet",
                 {"snippet": {"parentId": th["id"], "textOriginal": r}}, headers=auth)
            done += 1
            log(f"comments: replied  {text[:70]!r}  ->  {r!r}")
        except urllib.error.HTTPError as e:
            log("comments: reply failed", e.code, e.read().decode(errors="replace")[:400])
            replied.discard(th["id"])
            break
        time.sleep(3)
    json.dump(sorted(replied)[-3000:], open(path, "w"))
    log(f"comments: {done} replies, {len(d.get('items', []))} threads read")


def main():
    history = load_history()
    if "--comments" in sys.argv:
        reply_comments(history)
        return
    if "--upload" in sys.argv:
        upload_pending(history)
    channel_stats(history)
    titles = trends()
    p = plan_long(titles, history) if LONG else plan_short(titles, history)
    log("plan:", KIND, p["format"], "|", p["title"], "|", len(p["scenes"]), "scenes")
    for s in p["scenes"]:
        log(f"  [{s.get('speaker', 'narrator')}]", s["narration"])
    imgs, kinds = pictures(p)
    vox, vkind = voices(p)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M")
    base = os.path.join(OUT, f"{KIND}-{stamp}")
    secs = video(p, imgs, vox, base + ".mp4")
    if LONG:
        thumbnail(p, imgs, kinds, base + ".jpg")
    else:
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", imgs[0], "-vf", "scale=540:-2", base + ".jpg"])
    vid = None
    if "--upload" in sys.argv:
        try:
            vid = upload(p, base + ".mp4", base + ".jpg" if LONG else None)
        except urllib.error.HTTPError as e:
            log("upload failed:", e.code, e.read().decode(errors="replace")[:1500])
        except Exception as e:
            log("upload failed:", e)
    entry = {"date": stamp, "kind": KIND, "format": p["format"], "title": p["title"], "seconds": round(secs, 1),
             "pictures": {k: kinds.count(k) for k in set(kinds)}, "voice": vkind, "youtube": vid,
             "file": os.path.basename(base) + ".mp4"}
    if p.get("series"):
        entry.update(series=p["series"], episode=p["episode"], recap=p.get("recap", ""))
    # A series episode that didn't upload is not counted, so the next run makes it again.
    if not (p.get("series") and "--upload" in sys.argv and not vid):
        history.append(entry)
    json.dump(history[-500:], open(os.path.join(STATE, "history.json"), "w", encoding="utf-8"), ensure_ascii=False,
              indent=1)
    json.dump(p, open(base + ".json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    log("VIDEO MADE", json.dumps(entry, ensure_ascii=False))


if __name__ == "__main__":
    main()
