#!/usr/bin/env bash
# One-off probe: pictures + captions of Instagram posts the owner sent (Instagram can't be opened from the session).
set -u
UA="Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
pip install -q --break-system-packages -U yt-dlp >/dev/null 2>&1
which ffmpeg >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg >/dev/null; }
mkdir -p out
for ID in DeoNBhjE_DA Deb9usJEbaB; do
  mkdir -p out/$ID && cd out/$ID
  echo "===== $ID"
  yt-dlp --no-playlist --write-info-json -o "v%(autonumber)s.%(ext)s" "https://www.instagram.com/p/$ID/" 2>&1 | tail -4
  for j in *.info.json; do [ -f "$j" ] && python3 -c 'import json,sys;d=json.load(open(sys.argv[1]));print("CAPTION:",(d.get("description") or d.get("title") or "")[:2500])' "$j"; done
  for n in 1 2 3 4 5 6 7 8 9 10; do
    curl -sSL -A "$UA" -o e.html "https://www.instagram.com/p/$ID/embed/captioned/?img_index=$n"
    python3 - e.html $n <<'PY'
import sys,re,html
t=open(sys.argv[1],encoding="utf-8",errors="replace").read()
for _ in range(3): t=t.replace("\\\\/","/").replace("\\/","/").replace("\\u0026","&")
t=html.unescape(t)
if sys.argv[2]=="1":
    c=re.findall(r'class="Caption"[^>]*>(.*?)</div>',t,re.S)
    if c: print("EMBED CAPTION:",re.sub(r"<[^>]+>"," ",c[0])[:2500])
urls=[]
for u in re.findall(r'https://[^"\\ <>]+?\.(?:jpg|webp)[^"\\ <>]*',t):
    if "s150x150" in u or "profile" in u or "t51.2885-19" in u: continue
    if u not in urls: urls.append(u)
open("urls.txt","a").write("\n".join(urls)+"\n")
PY
  done
  # keep one file per picture id (the biggest)
  python3 - <<'PY'
import re
seen={}
for u in open("urls.txt").read().split():
    k=re.search(r'/([0-9_]+_n)\.', u)
    k=k.group(1) if k else u
    if k not in seen or len(u)>len(seen[k]): seen[k]=u
open("pick.txt","w").write("\n".join(list(seen.values())[:12]))
PY
  i=0; while read u; do i=$((i+1)); curl -sSL -A "$UA" -o "p$i.jpg" "$u"; done < pick.txt
  for f in v*.mp4; do [ -f "$f" ] && ffmpeg -v error -i "$f" -vf "fps=1,scale=480:-2" -q:v 4 "${f%.mp4}-f%03d.jpg" && rm "$f"; done
  rm -f e.html urls.txt pick.txt
  ls -la; cd ../..
done
