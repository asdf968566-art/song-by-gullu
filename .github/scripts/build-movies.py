#!/usr/bin/env python3
"""Indian films with their details, from Wikidata (free, no key): release year, languages, music directors,
producers and studios, directors and the main cast. The apps show them as "Movies" with filters, and play each
film's songs (its album on JioSaavn / in the catalog).

Writes a compact JSON list: [[title, year, languages, music, producers, directors, cast], ...] where every
field after the year is a "|"-separated string (empty when Wikidata doesn't know).
Wikidata limits heavy queries, so it asks year by year range, slowly, and retries.
"""
import json
import sys
import time
import urllib.parse
import urllib.request

OUT = sys.argv[1] if len(sys.argv) > 1 else "movies.json"
API = "https://query.wikidata.org/sparql"
UA = "SangeetMusicApp/1.0 (https://github.com/asdf968566-art/song-by-gullu; catalog build)"

DETAILS = """
SELECT ?film ?title (MIN(YEAR(?d)) AS ?year)
  (GROUP_CONCAT(DISTINCT ?ll; separator="|") AS ?langs)
  (GROUP_CONCAT(DISTINCT ?cl; separator="|") AS ?music)
  (GROUP_CONCAT(DISTINCT ?pl; separator="|") AS ?producers)
  (GROUP_CONCAT(DISTINCT ?dl; separator="|") AS ?directors)
WHERE {
  ?film wdt:P31 wd:Q11424; wdt:P495 wd:Q668; wdt:P577 ?d; wdt:P86 ?c.
  FILTER(YEAR(?d) >= %d && YEAR(?d) < %d)
  ?film rdfs:label ?title. FILTER(LANG(?title) = "en")
  ?c rdfs:label ?cl. FILTER(LANG(?cl) = "en")
  OPTIONAL { ?film wdt:P364 ?l. ?l rdfs:label ?ll. FILTER(LANG(?ll) = "en") }
  OPTIONAL { { ?film wdt:P162 ?p } UNION { ?film wdt:P272 ?p } ?p rdfs:label ?pl. FILTER(LANG(?pl) = "en") }
  OPTIONAL { ?film wdt:P57 ?di. ?di rdfs:label ?dl. FILTER(LANG(?dl) = "en") }
} GROUP BY ?film ?title
"""

CAST = """
SELECT ?film (GROUP_CONCAT(DISTINCT ?al; separator="|") AS ?cast)
WHERE {
  ?film wdt:P31 wd:Q11424; wdt:P495 wd:Q668; wdt:P577 ?d; wdt:P86 ?c; wdt:P161 ?a.
  FILTER(YEAR(?d) >= %d && YEAR(?d) < %d)
  ?a rdfs:label ?al. FILTER(LANG(?al) = "en")
} GROUP BY ?film
"""


def ask(query, tries=5):
    url = API + "?format=json&query=" + urllib.parse.quote(query)
    for n in range(tries):
        try:
            req = urllib.request.Request(url, headers={"Accept": "application/sparql-results+json", "User-Agent": UA})
            with urllib.request.urlopen(req, timeout=90) as r:
                return json.load(r)["results"]["bindings"]
        except Exception as e:  # 429 (too many), 5xx, timeouts
            wait = 10 * (n + 1)
            print(f"  wikidata: {e}; again in {wait}s", flush=True)
            time.sleep(wait)
    return None


def val(row, key):
    return row.get(key, {}).get("value", "")


def main():
    films = {}
    ranges = [(1930, 1960)] + [(y, y + 5) for y in range(1960, 2000, 5)] + [(y, y + 2) for y in range(2000, 2030, 2)]
    failed = 0
    for a, b in ranges:
        rows = ask(DETAILS % (a, b))
        if rows is None:
            failed += 1
            continue
        for r in rows:
            fid = val(r, "film")
            year = int(val(r, "year") or 0)
            films[fid] = [val(r, "title"), year, val(r, "langs"), val(r, "music"), val(r, "producers"), val(r, "directors"), ""]
        time.sleep(3)
        cast = ask(CAST % (a, b)) or []
        for r in cast:
            fid = val(r, "film")
            if fid in films:
                films[fid][6] = "|".join(val(r, "cast").split("|")[:6])
        print(f"{a}-{b - 1}: {len(rows)} films, {len(cast)} with cast", flush=True)
        time.sleep(3)
    out = sorted(films.values(), key=lambda f: (-f[1], f[0]))
    # Keep the language names short ("Hindi", not "Hindi language").
    for f in out:
        f[2] = "|".join(l.replace(" language", "") for l in f[2].split("|") if l)
    if failed > len(ranges) // 2 or len(out) < 1000:
        sys.exit(f"MOVIES: only {len(out)} films ({failed} ranges failed); keeping the old list")
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, ensure_ascii=False, separators=(",", ":"))
    print(f"MOVIES: {len(out)} films ({failed} ranges failed)")


if __name__ == "__main__":
    main()
