import json, time, urllib.parse, urllib.request
PROMPT = ("You are a music DJ for an Indian music app. Reply ONLY with JSON: {\"title\":str,\"languages\":[str],"
          "\"searchQueries\":[str],\"songs\":[\"Song - Artist\"]}. Request: sad punjabi songs for a night drive, no remix")
def go(name, url, data=None, headers=None):
    t = time.time()
    try:
        req = urllib.request.Request(url, data=json.dumps(data).encode() if data else None,
                                     headers={"Content-Type": "application/json", "User-Agent": "Mozilla/5.0", **(headers or {})})
        with urllib.request.urlopen(req, timeout=60) as r:
            body = r.read().decode("utf-8", "replace")
            print(f"### {name}: HTTP {r.status} in {time.time()-t:.1f}s\n{body[:900]}\n")
    except urllib.error.HTTPError as e:
        print(f"### {name}: HTTP {e.code} in {time.time()-t:.1f}s\n{e.read().decode('utf-8','replace')[:400]}\n")
    except Exception as e:
        print(f"### {name}: {e!r}\n")
go("models", "https://text.pollinations.ai/models")
go("GET text", "https://text.pollinations.ai/" + urllib.parse.quote(PROMPT) + "?json=true")
go("POST openai", "https://text.pollinations.ai/openai", {"model": "openai", "messages": [{"role": "user", "content": PROMPT}]})
go("POST gen v1 (no key)", "https://gen.pollinations.ai/v1/chat/completions", {"model": "openai", "messages": [{"role": "user", "content": PROMPT}]})
time.sleep(16)
go("POST openai again", "https://text.pollinations.ai/openai", {"model": "openai", "messages": [{"role": "user", "content": "Reply with the word ok"}]})
