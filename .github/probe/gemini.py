#!/usr/bin/env python3
"""One-off: how fast Gemini answers a DJ request with and without "think less" settings. Never prints the key."""
import json
import os
import re
import time
import urllib.request

KEY = os.environ.get("GEMINI_API_KEY", "")
BASE = "https://generativelanguage.googleapis.com/v1beta"
SYSTEM = ("You are the DJ of Sangeet, an Indian music app. Reply ONLY with JSON: "
          '{"title":str,"languages":[str],"songs":["Song - Singer"]}. songs: 12 real, released songs that fit.')
USER = "Listener's languages: hindi, haryanvi. Request: haryanvi gym songs"


def call(url, body=None, timeout=60):
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body else None,
                                 headers={"Content-Type": "application/json"}, method="POST" if body else "GET")
    t = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.loads(r.read()), time.time() - t
    except urllib.error.HTTPError as e:
        return e.code, (e.read() or b"")[:300].decode(errors="replace"), time.time() - t
    except Exception as e:
        return 0, str(e)[:200], time.time() - t


if not KEY:
    print("PROBE: no GEMINI_API_KEY")
    raise SystemExit
st, models, dt = call(f"{BASE}/models?pageSize=200&key={KEY}")
names = [m["name"] for m in (models.get("models", []) if isinstance(models, dict) else [])
         if "generateContent" in m.get("supportedGenerationMethods", [])]
ver = lambda n: float((re.search(r"gemini-(\d+(?:\.\d+)?)", n) or [0, 0])[1] or 0)
flash = sorted([n for n in names if re.match(r"^models/gemini-\d+(\.\d+)?-flash(-latest)?$", n)], key=ver, reverse=True)
print(f"PROBE models {st} in {dt:.1f}s: flash = {flash[:4]}")
for model in flash[:2]:
    for label, think in [("default", None), ("level low", {"thinkingLevel": "low"}),
                         ("level minimal", {"thinkingLevel": "minimal"}), ("budget 0", {"thinkingBudget": 0})]:
        cfg = {"temperature": 0.4, "maxOutputTokens": 4000, "responseMimeType": "application/json"}
        if think:
            cfg["thinkingConfig"] = think
        body = {"systemInstruction": {"parts": [{"text": SYSTEM}]},
                "contents": [{"role": "user", "parts": [{"text": USER}]}], "generationConfig": cfg}
        st, out, dt = call(f"{BASE}/{model}:generateContent?key={KEY}", body)
        if st == 200:
            text = "".join(p.get("text", "") for p in out["candidates"][0]["content"]["parts"])
            songs = json.loads(text[text.find("{"):text.rfind("}") + 1]).get("songs", [])
            usage = out.get("usageMetadata", {})
            print(f"PROBE {model} {label}: 200 in {dt:.1f}s, {len(songs)} songs, thoughts={usage.get('thoughtsTokenCount')}, first={songs[:3]}")
        else:
            print(f"PROBE {model} {label}: HTTP {st} in {dt:.1f}s {str(out)[:200]}")
