#!/usr/bin/env python3
"""Prints the PRIVATE repo the app's listening data goes to (Community), or nothing.

Owner's rule (Oct 9): the private "CMS" repo (owner: "cms wali repo use kar"), else "sangeet-data", else any private
repo of the report token's account where the token can create issues. A public repo is never used: the data is the
listeners' searches, likes and playlists.
With --files: the first private repo (same order) where the token may write files; the catalog build keeps
sangeet-*.json files in a folder that repo already has (community.py).
Env: REPORT_TOKEN.
"""
import json
import os
import sys
import urllib.request

API = "https://api.github.com"
TOKEN = os.environ.get("REPORT_TOKEN", "")


def call(method, url, body=None):
    req = urllib.request.Request(API + url, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": f"Bearer {TOKEN}", "Accept": "application/vnd.github+json",
                                          "Content-Type": "application/json", "User-Agent": "sangeet-data-repo"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception:
        return 0, None


def main():
    if not TOKEN:
        return
    files = "--files" in sys.argv
    _, repos = call("GET", "/user/repos?affiliation=owner&per_page=100")
    private = [r for r in (repos or []) if r.get("private") and (files or r.get("has_issues"))]
    def rank(r):
        n = r["name"].lower()
        return (0 if n == "cms" else 1 if n == "sangeet-data" else 2 if any(w in n for w in ("sangeet", "song", "data")) else 3, n)
    private.sort(key=rank)
    for r in private:
        if files:
            # --files: the first private repo the token may write files in (owner, Oct 9: "jo bhi folder mile usse use
            # karle"). An empty PUT is refused with 422 when allowed and 403/404 when not; nothing is written.
            status, _ = call("PUT", f"/repos/{r['full_name']}/contents/sangeet-stats.json", {})
        else:
            status, _ = call("POST", f"/repos/{r['full_name']}/issues", {})  # 422 = may create issues (nothing is created)
        if status == 422:
            print(r["full_name"])
            return


if __name__ == "__main__":
    main()
