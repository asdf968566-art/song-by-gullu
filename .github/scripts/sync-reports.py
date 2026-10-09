#!/usr/bin/env python3
"""Copies the app's "Report a problem" issues from the report token's own repo (the owner's other account) into
this repo, so every report is here like before; the original is closed with a link.

Env: REPORT_TOKEN (reads/closes there), GH_TOKEN (creates issues here), REPO (this repo).
"""
import json
import os
import urllib.request

API = "https://api.github.com"
MARK = "**What happened:**"  # every app report starts with it (CrashReporter.build)


def call(method, url, token, body=None):
    req = urllib.request.Request(API + url, method=method, data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                                          "Content-Type": "application/json", "User-Agent": "sangeet-reports"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, None


def main():
    rt, gh, here = os.environ.get("REPORT_TOKEN", ""), os.environ["GH_TOKEN"], os.environ["REPO"]
    if not rt:
        print("REPORTS: no REPORT_TOKEN")
        return
    _, repos = call("GET", "/user/repos?affiliation=owner&per_page=100", rt)
    for r in repos or []:
        print(f"REPORTS: {r['full_name']} is {'private' if r.get('private') else 'PUBLIC'}")
    repos = [r["full_name"] for r in (repos or []) if r.get("has_issues") and r["full_name"] != here]
    # Where can the app send reports? (An issue without a title: 422 = allowed, nothing is created.)
    for repo in ["vivekyadav200405-cpu/day1"] + [r for r in repos if r != "vivekyadav200405-cpu/day1"]:
        st, _ = call("POST", f"/repos/{repo}/issues", rt, {})
        print(f"REPORTS: {repo}: {'app can send reports here ✅' if st == 422 else f'no permission to create issues (GitHub said {st})'}")
    moved = 0
    for repo in repos:
        status, issues = call("GET", f"/repos/{repo}/issues?state=open&per_page=50", rt)
        for it in (issues or []) if status == 200 else []:
            if "pull_request" in it or MARK not in (it.get("body") or ""):
                continue
            body = f"{it['body']}\n\n---\n_Sent from the app; copied from {it['html_url']}_"
            st, new = call("POST", f"/repos/{here}/issues", gh, {"title": it["title"], "body": body, "labels": ["app report"]})
            if st != 201:
                st, new = call("POST", f"/repos/{here}/issues", gh, {"title": it["title"], "body": body})
            if st != 201:
                print(f"REPORTS: couldn't copy {it['html_url']} (GitHub said {st})")
                continue
            call("POST", f"/repos/{repo}/issues/{it['number']}/comments", rt, {"body": f"Copied to {new['html_url']}"})
            call("PATCH", f"/repos/{repo}/issues/{it['number']}", rt, {"state": "closed"})
            print(f"REPORTS: {it['html_url']} -> {new['html_url']}")
            moved += 1
    print(f"REPORTS: {moved} copied from {', '.join(repos) or 'no repos'}")


if __name__ == "__main__":
    main()
