#!/usr/bin/env bash
# One-off probe: get the Instagram reel the owner sent (a "crystal glass" app look) and cut it into pictures,
# because Instagram can't be opened from the coding session.
set -u
REEL="DePG1FFTXJE"
URL="https://www.instagram.com/reel/$REEL/"
UA="Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
mkdir -p out && cd out

unescape() { python3 -c 'import sys,re,html
t=open(sys.argv[1],encoding="utf-8",errors="replace").read()
for _ in range(3):
    t=t.replace("\\\\/","/").replace("\\/","/").replace("\\u0026","&").replace("\\\\u0026","&")
t=html.unescape(t)
vids=sorted(set(re.findall(r"https://[^\"\\\\ <>]+?\.mp4[^\"\\\\ <>]*",t)),key=len)
pics=sorted(set(re.findall(r"https://[^\"\\\\ <>]+?\.(?:jpg|webp|heic)[^\"\\\\ <>]*",t)),key=len,reverse=True)
cap=re.findall(r"(?:og:description|og:title|\"caption\"|class=\"Caption\")[^>]{0,20}?(?:content=\"|>)([^\"<]{5,600})",t)
print("VIDEOS",len(vids)); [print("  V",v[:300]) for v in vids[:5]]
print("PICS",len(pics)); [print("  P",p[:300]) for p in pics[:3]]
for c in cap[:4]: print("  CAPTION:",c[:600])
open(sys.argv[1]+".vid","w").write(vids[0] if vids else "")
open(sys.argv[1]+".pic","w").write(pics[0] if pics else "")' "$1"; }

got() { [ -s reel.mp4 ] && ffprobe -v error -show_entries format=duration -of csv=p=0 reel.mp4 >/dev/null 2>&1; }

echo "== yt-dlp"
pip install -q --break-system-packages -U yt-dlp >/dev/null 2>&1 || pip install -q -U yt-dlp >/dev/null 2>&1
yt-dlp --no-playlist -f "bv*+ba/b" --merge-output-format mp4 -o reel.mp4 --write-info-json "$URL" 2>&1 | tail -8
[ -f reel.info.json ] && python3 -c 'import json;d=json.load(open("reel.info.json"));print("TITLE:",d.get("title"));print("DESC:",(d.get("description") or "")[:1500]);print("UPLOADER:",d.get("uploader"),d.get("channel"))'

for page in "https://www.instagram.com/reel/$REEL/embed/captioned/" "https://www.instagram.com/p/$REEL/embed/captioned/"; do
  echo "== $page"
  curl -sSL -A "$UA" -o embed.html -w "http %{http_code} %{size_download}\n" "$page"
  unescape embed.html
  if ! got && [ -s embed.html.vid ]; then curl -sSL -A "$UA" -o reel.mp4 "$(cat embed.html.vid)"; got && echo "video from embed"; fi
  [ -s embed.html.pic ] && [ ! -s cover.jpg ] && curl -sSL -A "$UA" -o cover.jpg "$(cat embed.html.pic)"
done

echo "== preview tags"
curl -sSL -A "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)" -o og.html -w "http %{http_code} %{size_download}\n" "$URL"
grep -o '<meta[^>]*og:[^>]*>' og.html | head -12
unescape og.html
[ -s og.html.pic ] && [ ! -s cover.jpg ] && curl -sSL -o cover.jpg "$(cat og.html.pic)"
if ! got && [ -s og.html.vid ]; then curl -sSL -o reel.mp4 "$(cat og.html.vid)"; got && echo "video from og"; fi

for host in d.ddinstagram.com kkinstagram.com www.instagramez.com d.vxinstagram.com www.uuinstagram.com; do
  got && break
  echo "== mirror $host"
  curl -sSL -A "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)" -o m.html -w "http %{http_code} %{size_download} %{content_type}\n" "https://$host/reel/$REEL/"
  if file m.html | grep -qi "media\|ISO"; then mv m.html reel.mp4; got && echo "video from $host"; continue; fi
  grep -o '<meta[^>]*og:\(video\|image\|description\|title\)[^>]*>' m.html | head -6
  unescape m.html
  if [ -s m.html.vid ]; then curl -sSL -o reel.mp4 "$(cat m.html.vid)"; got && echo "video from $host"; fi
  v=$(grep -o 'og:video[^>]*content="[^"]*"' m.html | head -1 | sed 's/.*content="//; s/"$//; s/&amp;/\&/g')
  if ! got && [ -n "$v" ]; then case "$v" in /*) v="https://$host$v";; esac; curl -sSL -A "Mozilla/5.0 (compatible; Discordbot/2.0)" -o reel.mp4 "$v"; got && echo "video from $host og:video"; fi
done

which ffmpeg >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg >/dev/null; }
mkdir -p frames
if got; then
  ffprobe -v error -show_entries format=duration:stream=width,height -of default=nw=1 reel.mp4
  ffmpeg -v error -i reel.mp4 -vf "fps=2,scale=540:-2" -q:v 4 frames/f%03d.jpg
  ffmpeg -v error -i reel.mp4 -vf "fps=1,scale=240:-2,tile=6x2" -q:v 5 frames/sheet%02d.jpg
  echo "FRAMES $(ls frames | wc -l)"
else
  echo "NO VIDEO"
fi
rm -f reel.mp4 *.html m.html *.vid *.pic
ls -la . frames | head -80
