#!/usr/bin/env python3
"""Makes one original Hindi YouTube Short, ready for the owner to upload.

Steps: what is trending on YouTube in India -> an AI plan (format, title, scenes) that is new for the channel ->
a picture per scene -> a Hindi voice per scene -> a 1080x1920 video with moving pictures and big captions.
Every step has a fallback, so a video is always made. The title, description and tags are in short-*.json.

Usage: make_short.py <state dir> <out dir>
State dir holds history.json (what was made before, so topics don't repeat).
"""
import json, os, random, re, subprocess, sys, time, urllib.parse, urllib.request
from datetime import datetime, timedelta, timezone

STATE, OUT = sys.argv[1], sys.argv[2]
os.makedirs(STATE, exist_ok=True)
os.makedirs(OUT, exist_ok=True)
WORK = os.path.join(OUT, "work")
os.makedirs(WORK, exist_ok=True)

YT_KEY = os.environ.get("YT_API_KEY", "")
GEMINI_KEY = os.environ.get("GEMINI_API_KEY", "")
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
W, H = 1080, 1920

FORMATS = [
    "cartoon moral story for families (panchatantra-style animals, a twist and a clear lesson at the end)",
    "AI mini movie: suspense / mystery with a shocking last line",
    "AI mini movie: horror story (spooky, not gory) set in an Indian village or old haveli",
    "AI mini movie: emotional story (mother, father, friendship, sacrifice) that makes people share it",
    "motion facts video: 6 amazing facts most Indians don't know (space, history, human body, animals)",
    "Indian mythology story told simply (Ramayana, Mahabharata, Puranas), respectful",
    "motivational story of an ordinary person who never gave up (fictional, not a real person)",
    "funny cartoon story with a desi family, clean comedy and a punchline",
    "what if... video (what if the Earth stopped spinning, what if humans could fly...), scientific",
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
    p = os.path.join(STATE, "history.json")
    try:
        return json.load(open(p, encoding="utf-8"))
    except Exception:
        return []


# ---------- 1. Trends ----------
def trends():
    """Titles of what India watches now: the trending chart and the most viewed new Hindi Shorts."""
    out = []
    if not YT_KEY:
        log("trends: no YouTube key")
        return out
    base = "https://www.googleapis.com/youtube/v3/"
    try:
        for cat in ["", "1", "24", "27"]:  # all, Film & Animation, Entertainment, Education
            q = {"part": "snippet,statistics", "chart": "mostPopular", "regionCode": "IN", "maxResults": "25",
                 "key": YT_KEY}
            if cat:
                q["videoCategoryId"] = cat
            try:
                d = json.loads(http(base + "videos?" + urllib.parse.urlencode(q)))
            except Exception as e:
                log("trends: chart", cat or "all", "failed", e)
                continue
            for v in d.get("items", []):
                out.append((int(v.get("statistics", {}).get("viewCount", 0)), v["snippet"]["title"]))
        since = (datetime.now(timezone.utc) - timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")
        for q in ["kahani #shorts", "cartoon story #shorts", "ai video #shorts", "facts in hindi #shorts",
                  "horror story #shorts"]:
            p = {"part": "snippet", "q": q, "type": "video", "videoDuration": "short", "order": "viewCount",
                 "regionCode": "IN", "relevanceLanguage": "hi", "publishedAfter": since, "maxResults": "15",
                 "key": YT_KEY}
            try:
                d = json.loads(http(base + "search?" + urllib.parse.urlencode(p)))
            except Exception as e:
                log("trends: search", q, "failed", e)
                continue
            for v in d.get("items", []):
                out.append((0, v["snippet"]["title"]))
    except Exception as e:
        log("trends failed:", e)
    seen, titles = set(), []
    for _, t in sorted(out, key=lambda x: -x[0]):
        if t not in seen:
            seen.add(t)
            titles.append(t)
    log("trends:", len(titles), "titles, e.g.", titles[:5])
    return titles[:80]


# ---------- 2. Plan ----------
def gemini_model():
    d = json.loads(http("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=" + GEMINI_KEY))
    names = [m["name"] for m in d.get("models", []) if "generateContent" in m.get("supportedGenerationMethods", [])]

    def ver(n):
        m = re.search(r"gemini-(\d+(?:\.\d+)?)", n)
        return float(m.group(1)) if m else 0

    plain = [n for n in names if re.match(r"^models/gemini-\d+(\.\d+)?-flash(-latest)?$", n)]
    pick = max(plain or [n for n in names if "flash" in n and "image" not in n and "tts" not in n] or names, key=ver)
    return pick


def ask_gemini(prompt):
    model = gemini_model()
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
                                headers={"Authorization": "Bearer unused"}, timeout=120))
            text = d["choices"][0]["message"].get("content") or ""
            if "{" in text:
                return text
            log("plan: LLM7", model, "gave no JSON:", text[:300])
        except Exception as e:
            log("plan: LLM7", model, "failed", e)
    raise RuntimeError("no LLM answered")


def parse_json(text):
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.S)
    m = re.search(r"\{.*\}", text, re.S)
    return json.loads(m.group(0))


def plan(titles, history):
    recent = [h.get("title", "") for h in history[-40:]]
    used = [h.get("format", "") for h in history[-len(FORMATS) + 1:]]
    choices = [f for f in FORMATS if f not in used] or FORMATS
    fmt = random.choice(choices)
    prompt = f"""You run a new Hindi YouTube Shorts channel of ORIGINAL AI-made videos. Make the next Short.

Format for this video: {fmt}

What is trending in India right now (use it for the topic, mood and hook; never copy a video, never use real
people, brands, film names or copyrighted characters):
{chr(10).join('- ' + t for t in titles[:60]) or '- (no data)'}

Already made on this channel, don't repeat these topics:
{chr(10).join('- ' + t for t in recent) or '- (nothing yet)'}

Rules:
- 7 to 9 scenes. Each scene's narration is 1-2 short spoken sentences in simple everyday Hindi written in
  Devanagari script (Hinglish words are fine). All narration together is 110-150 words (about 50 seconds).
- Scene 1 is a strong hook in the first 2 seconds (a question or a shocking line). The last scene ends the story
  and asks viewers to comment or follow (one short line).
- image_prompt: English, a detailed picture for that scene in ONE consistent art style (put the style words in
  every prompt), the same characters described the same way in every scene, vertical 9:16, no text in the picture.
- title: catchy, max 70 characters, Hindi/Hinglish in Latin or Devanagari, ends with #shorts.
- description: 2-3 lines in Hindi plus 5 hashtags.
- tags: 10 short tags.
- voice: "female" or "male", whichever fits the story.

Answer with only this JSON:
{{"format": "...", "title": "...", "description": "...", "tags": ["..."], "voice": "male|female",
 "style": "...", "scenes": [{{"narration": "...", "image_prompt": "..."}}]}}"""
    asks = ([ask_gemini] * 4 if GEMINI_KEY else []) + [ask_llm7]
    for n, ask in enumerate(asks):
        try:
            p = parse_json(ask(prompt))
            scenes = [s for s in p.get("scenes", []) if s.get("narration") and s.get("image_prompt")]
            if len(scenes) >= 4 and p.get("title"):
                p["scenes"] = scenes[:10]
                p["format"] = fmt
                return p
            log("plan: answer too short, again")
        except Exception as e:
            log("plan failed:", type(e).__name__, str(e)[:300])
            time.sleep(15 + 20 * n)  # Gemini's free tier is often busy (503) for a minute
    raise RuntimeError("no plan")


# ---------- 3. Pictures ----------
def picture_pollinations(prompt, seed, path):
    q = urllib.parse.quote(prompt[:900])
    url = (f"https://image.pollinations.ai/prompt/{q}?width={W}&height={H}&seed={seed}&nologo=true"
           f"&model=flux&enhance=false")
    data = http(url, timeout=180)
    if len(data) < 20000 or data[:3] not in (b"\xff\xd8\xff", b"\x89PN"):
        raise RuntimeError(f"not a picture ({len(data)} bytes)")
    open(path, "wb").write(data)


def picture_gemini(prompt, path):
    d = json.loads(http("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200&key=" + GEMINI_KEY))
    models = [m["name"] for m in d.get("models", []) if "image" in m["name"] and "generateContent" in
              m.get("supportedGenerationMethods", [])]
    import base64
    for model in sorted(models, reverse=True):
        try:
            body = {"contents": [{"parts": [{"text": prompt + " Vertical 9:16 picture, no text."}]}],
                    "generationConfig": {"responseModalities": ["IMAGE"]}}
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
    random.seed(i * 7919)
    a = [random.randint(20, 120) for _ in range(3)]
    b = [random.randint(80, 220) for _ in range(3)]
    img = Image.new("RGB", (W, H))
    dr = ImageDraw.Draw(img)
    for y in range(H):
        t = y / H
        dr.line([(0, y), (W, y)], fill=tuple(int(a[k] + (b[k] - a[k]) * t) for k in range(3)))
    img.save(path, quality=92)


def fit_picture(path):
    """Crop / scale any picture to exactly 1080x1920."""
    from PIL import Image
    img = Image.open(path).convert("RGB")
    r = max(W / img.width, H / img.height)
    img = img.resize((int(img.width * r + 1), int(img.height * r + 1)), Image.LANCZOS)
    x, y = (img.width - W) // 2, (img.height - H) // 2
    img.crop((x, y, x + W, y + H)).save(path, quality=93)


def pictures(p):
    seed = random.randint(1, 10 ** 6)
    paths, kinds = [], []
    for i, s in enumerate(p["scenes"]):
        path = os.path.join(WORK, f"img{i:02d}.jpg")
        prompt = f"{s['image_prompt']}. {p.get('style', '')}. cinematic lighting, highly detailed, vertical"
        kind = "card"
        for attempt in range(3):
            try:
                picture_pollinations(prompt, seed + i, path)
                kind = "pollinations"
                break
            except Exception as e:
                log(f"picture {i}: pollinations try {attempt + 1} failed:", str(e)[:200])
                time.sleep(8 + attempt * 10)
        if kind == "card" and GEMINI_KEY:
            try:
                picture_gemini(prompt, path)
                kind = "gemini"
            except Exception as e:
                log(f"picture {i}:", e)
        if kind == "card":
            picture_card(i, path)
        fit_picture(path)
        paths.append(path)
        kinds.append(kind)
        time.sleep(3)
    log("pictures:", kinds)
    return paths, kinds


# ---------- 4. Voice ----------
def duration(path):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of",
                                          "csv=p=0", path]).decode().strip())


def voices(p):
    voice = "hi-IN-MadhurNeural" if p.get("voice") == "male" else "hi-IN-SwaraNeural"
    out, kind = [], "edge"
    for i, s in enumerate(p["scenes"]):
        path = os.path.join(WORK, f"voice{i:02d}.mp3")
        ok = False
        for attempt in range(3):
            r = subprocess.run(["edge-tts", "--voice", voice, "--rate", "+6%", "--text", s["narration"],
                                "--write-media", path], capture_output=True, text=True)
            if r.returncode == 0 and os.path.exists(path) and os.path.getsize(path) > 2000:
                ok = True
                break
            log(f"voice {i}: edge-tts failed", r.stderr[-300:])
            time.sleep(3)
        if not ok:
            from gtts import gTTS
            gTTS(s["narration"], lang="hi").save(path)
            kind = "gtts"
        out.append(path)
    log("voice:", kind, voice)
    return out, kind


# ---------- 5. Video ----------
def ass_time(t):
    return f"{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}"


def captions(p, durs, path):
    """Viral-style captions: 3-4 words at a time, big, yellow on a black outline."""
    lines = ["[Script Info]", "ScriptType: v4.00+", f"PlayResX: {W}", f"PlayResY: {H}", "",
             "[V4+ Styles]",
             "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, "
             "Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
             "Alignment, MarginL, MarginR, MarginV, Encoding",
             "Style: Cap,Noto Sans Devanagari,92,&H0000F6FF,&H00FFFFFF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,"
             "1,7,3,2,70,70,560,1",
             "", "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"]
    t = 0.0
    for s, d in zip(p["scenes"], durs):
        words = s["narration"].split()
        chunks = [" ".join(words[k:k + 3]) for k in range(0, len(words), 3)] or [""]
        total = sum(len(c) for c in chunks) or 1
        speak = d - 0.35
        c0 = t
        for c in chunks:
            c1 = c0 + speak * len(c) / total
            text = c.replace("{", "(").replace("}", ")")
            lines.append(f"Dialogue: 0,{ass_time(c0)},{ass_time(c1)},Cap,,0,0,0,,{{\\fad(60,60)}}{text}")
            c0 = c1
        t += d
    open(path, "w", encoding="utf-8").write("\n".join(lines) + "\n")


def video(p, imgs, vox, path):
    gap = 0.35
    durs = [duration(v) + gap for v in vox]
    clips = []
    fps = 30
    for i, (img, d) in enumerate(zip(imgs, durs)):
        clip = os.path.join(WORK, f"clip{i:02d}.mp4")
        n = int(d * fps) + 1
        # Slow zoom in or out and a little pan, different per scene, on a 2x picture so it stays sharp.
        if i % 2 == 0:
            z = f"min(1.0+0.0009*on,1.18)"
        else:
            z = f"max(1.18-0.0009*on,1.0)"
        xexpr = ["iw/2-(iw/zoom/2)", "(iw-iw/zoom)*on/" + str(n), "(iw-iw/zoom)*(1-on/" + str(n) + ")"][i % 3]
        vf = (f"scale={W * 2}:{H * 2},zoompan=z='{z}':x='{xexpr}':y='ih/2-(ih/zoom/2)':d={n}:s={W}x{H}:fps={fps},"
              f"format=yuv420p")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-i", img, "-vf", vf, "-frames:v", str(n),
                        "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", clip], check=True)
        clips.append(clip)
    lst = os.path.join(WORK, "clips.txt")
    open(lst, "w").write("".join(f"file '{os.path.abspath(c)}'\n" for c in clips))
    # Voice with a short silence after each scene.
    alst = os.path.join(WORK, "voices.txt")
    pads = []
    for i, v in enumerate(vox):
        pv = os.path.join(WORK, f"pad{i:02d}.wav")
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", v, "-af", f"apad=pad_dur={gap}", "-ar", "44100",
                        "-ac", "2", pv], check=True)
        pads.append(pv)
    open(alst, "w").write("".join(f"file '{os.path.abspath(a)}'\n" for a in pads))
    ass = os.path.join(WORK, "captions.ass")
    captions(p, durs, ass)
    total = sum(durs)
    # A soft low drone under the voice so the video isn't dead quiet between lines.
    bed = "aevalsrc='0.04*sin(2*PI*110*t)*(0.6+0.4*sin(2*PI*0.1*t))+0.03*sin(2*PI*164.8*t)':s=44100:d=" + f"{total:.2f}"
    subprocess.run(["ffmpeg", "-v", "error", "-y",
                    "-f", "concat", "-safe", "0", "-i", lst,
                    "-f", "concat", "-safe", "0", "-i", alst,
                    "-f", "lavfi", "-i", bed,
                    "-filter_complex",
                    f"[0:v]subtitles='{ass}'[v];[2:a]aformat=channel_layouts=stereo,volume=0.6[b];"
                    f"[1:a][b]amix=inputs=2:duration=first:dropout_transition=0,loudnorm=I=-14:TP=-1.5[a]",
                    "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", "-shortest",
                    path], check=True)
    log(f"video: {path} {total:.1f} s, {os.path.getsize(path) // 1024} KB")
    return total


# ---------- 6. Upload (after the owner linked the channel with youtube_login.py) ----------
def access_token():
    tok = os.path.join(STATE, "token.enc")
    cid, secret = os.environ.get("YT_CLIENT_ID", ""), os.environ.get("YT_CLIENT_SECRET", "")
    if not (os.path.exists(tok) and cid and secret):
        return None
    refresh = subprocess.run(["openssl", "enc", "-d", "-aes-256-cbc", "-pbkdf2", "-a", "-A", "-in", tok,
                              "-pass", "env:YT_CLIENT_SECRET"], capture_output=True, text=True).stdout.strip()
    if not refresh:
        log("upload: the saved login can't be read; run the YouTube login again")
        return None
    d = json.loads(http("https://oauth2.googleapis.com/token", urllib.parse.urlencode(
        {"client_id": cid, "client_secret": secret, "refresh_token": refresh,
         "grant_type": "refresh_token"}).encode(), headers={"Content-Type": "application/x-www-form-urlencoded"}))
    return d["access_token"]


def upload(p, path):
    token = access_token()
    if not token:
        log("upload: channel not linked yet (secrets YT_CLIENT_ID / YT_CLIENT_SECRET, then the YouTube login)")
        return None
    desc = p.get("description", "") + "\n\nThis story, its pictures and its voice are made with AI."
    meta = {"snippet": {"title": p["title"][:100], "description": desc[:4900], "tags": p.get("tags", [])[:15],
                        "categoryId": "1", "defaultLanguage": "hi", "defaultAudioLanguage": "hi"},
            # containsSyntheticMedia: YouTube's "altered or synthetic content" label, required for AI-made videos.
            "status": {"privacyStatus": "public", "selfDeclaredMadeForKids": False, "containsSyntheticMedia": True}}
    size = os.path.getsize(path)
    req = urllib.request.Request(
        "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status",
        data=json.dumps(meta).encode(), method="POST",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json; charset=UTF-8",
                 "X-Upload-Content-Type": "video/mp4", "X-Upload-Content-Length": str(size)})
    with urllib.request.urlopen(req, timeout=60) as r:
        loc = r.headers["Location"]
    req = urllib.request.Request(loc, data=open(path, "rb").read(), method="PUT",
                                 headers={"Authorization": "Bearer " + token, "Content-Type": "video/mp4"})
    with urllib.request.urlopen(req, timeout=600) as r:
        v = json.loads(r.read())
    log("UPLOADED https://youtube.com/shorts/" + v["id"], "privacy:", v.get("status", {}).get("privacyStatus"))
    return v["id"]


def main():
    history = load_history()
    titles = trends()
    p = plan(titles, history)
    log("plan:", p["format"], "|", p["title"], "|", len(p["scenes"]), "scenes")
    for s in p["scenes"]:
        log("  -", s["narration"])
    imgs, kinds = pictures(p)
    vox, vkind = voices(p)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M")
    path = os.path.join(OUT, f"short-{stamp}.mp4")
    secs = video(p, imgs, vox, path)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", imgs[0], "-vf", "scale=540:-2",
                    os.path.join(OUT, f"short-{stamp}.jpg")])
    vid = None
    if "--upload" in sys.argv:
        try:
            vid = upload(p, path)
        except urllib.error.HTTPError as e:
            log("upload failed:", e.code, e.read().decode(errors="replace")[:1500])
        except Exception as e:
            log("upload failed:", e)
    entry = {"date": stamp, "format": p["format"], "title": p["title"], "seconds": round(secs, 1),
             "pictures": kinds, "voice": vkind, "youtube": vid, "file": os.path.basename(path)}
    history.append(entry)
    json.dump(history[-500:], open(os.path.join(STATE, "history.json"), "w", encoding="utf-8"), ensure_ascii=False,
              indent=1)
    json.dump(p, open(os.path.join(OUT, f"short-{stamp}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    log("SHORT MADE", json.dumps(entry, ensure_ascii=False))


if __name__ == "__main__":
    main()
