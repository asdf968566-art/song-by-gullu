#!/usr/bin/env python3
"""Finds public Invidious / Piped servers that currently hand out YouTube songs' audio, for the web app's
"YouTube in background" option (iPhone stops YouTube's own player in the background; plain audio keeps playing).

Writes a JSON list, best first:  [{"type": "invidious", "url": "https://..."}, {"type": "piped", "api": "https://..."}]
Servers come and go, so this runs with every catalog build.
"""
import concurrent.futures as cf
import json
import sys
import urllib.request

VIDEO = "BddP6PYo2gs"  # a well-known song video, to test with
UA = {"User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1"}


def get(url, timeout=12, rng=None):
    headers = dict(UA)
    if rng:
        headers["Range"] = rng
    req = urllib.request.Request(url, headers=headers)
    return urllib.request.urlopen(req, timeout=timeout)


def audio_ok(url):
    """The URL gives the first bytes of an audio file."""
    try:
        with get(url, rng="bytes=0-4095") as r:
            kind = r.headers.get("Content-Type", "")
            body = r.read(4096)
            return r.status in (200, 206) and len(body) > 1000 and ("audio" in kind or "video" in kind or "octet" in kind)
    except Exception:
        return False


def invidious_list():
    try:
        with get("https://api.invidious.io/instances.json?sort_by=health") as r:
            rows = json.load(r)
        return [info["uri"].rstrip("/") for _, info in rows if info.get("type") == "https" and info.get("api")][:20]
    except Exception as e:
        print("invidious list:", e)
        return []


def piped_list():
    try:
        with get("https://piped-instances.kavin.rocks/") as r:
            rows = json.load(r)
        return [x["api_url"].rstrip("/") for x in rows if x.get("api_url")][:20]
    except Exception as e:
        print("piped list:", e)
        return []


def test_invidious(base):
    ok = audio_ok(f"{base}/latest_version?id={VIDEO}&itag=140&local=true")
    return ({"type": "invidious", "url": base} if ok else None), base


def test_piped(api):
    try:
        with get(f"{api}/streams/{VIDEO}") as r:
            data = json.load(r)
        streams = [s for s in data.get("audioStreams", []) if "mp4" in (s.get("mimeType") or "")]
        best = max(streams, key=lambda s: s.get("bitrate", 0)) if streams else None
        ok = bool(best) and audio_ok(best["url"])
    except Exception:
        ok = False
    return ({"type": "piped", "api": api} if ok else None), api


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "yt-servers.json"
    jobs = [(test_invidious, b) for b in invidious_list()] + [(test_piped, a) for a in piped_list()]
    found = []
    with cf.ThreadPoolExecutor(12) as ex:
        for fut in cf.as_completed([ex.submit(fn, x) for fn, x in jobs]):
            server, name = fut.result()
            print(("✅ " if server else "❌ ") + name)
            if server:
                found.append(server)
    # Invidious first (one request per song), then Piped.
    found.sort(key=lambda s: 0 if s["type"] == "invidious" else 1)
    with open(out, "w") as f:
        json.dump(found[:8], f)
    print(f"YT SERVERS: {len(found)} working, kept {min(len(found), 8)}")


if __name__ == "__main__":
    main()
