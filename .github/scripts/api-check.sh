#!/usr/bin/env bash
# App jin online services pe chalta hai, wo sach mein kaam kar rahi hain ya nahi.
set -euo pipefail
UA="Sangeet/1.0 (Android music player)"

echo "== Audius trending"
curl -fsS -A "$UA" "https://api.audius.co/v1/tracks/trending?app_name=Sangeet&limit=5" -o trending.json
python3 - <<'PY'
import json
d = json.load(open("trending.json"))["data"]
assert d, "Audius trending khaali hai"
for t in d[:5]:
    print(" -", t["title"], "by", t["user"]["name"])
open("track_id", "w").write(d[0]["id"])
PY

echo "== Audius stream (redirect follow karke audio aana chahiye)"
ID=$(cat track_id)
CT=$(curl -fsSL -A "$UA" -r 0-65535 -o sample.bin -w '%{content_type}' "https://api.audius.co/v1/tracks/$ID/stream?app_name=Sangeet")
SIZE=$(stat -c %s sample.bin)
echo "content-type=$CT bytes=$SIZE"
[[ "$CT" == audio/* || "$CT" == application/octet-stream* ]] || { echo "Audio nahi mila"; exit 1; }
[[ "$SIZE" -gt 10000 ]] || { echo "Stream bahut chhota"; exit 1; }

echo "== Audius search"
curl -fsS -A "$UA" "https://api.audius.co/v1/tracks/search?query=lofi&app_name=Sangeet" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin)["data"]; assert d; print(" results:", len(d))'

echo "== LRCLIB lyrics"
curl -fsS -A "$UA" "https://lrclib.net/api/search?track_name=Believer&artist_name=Imagine%20Dragons" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); assert any(x.get("syncedLyrics") for x in d); print(" synced lyrics mile:", len(d))'

echo "== JioSaavn search + asli gaane ka link"
curl -fsS -A "$UA" "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=5&p=1&q=kesariya" -o saavn.json
ENC=$(python3 - <<'PY'
import json
d = json.load(open("saavn.json"))
r = [x for x in d.get("results", []) if x.get("type") == "song"]
assert r, "JioSaavn search khaali"
for x in r[:5]:
    print(" -", x["title"], "|", x.get("language"), file=__import__("sys").stderr)
print(r[0]["more_info"]["encrypted_media_url"])
PY
)
MEDIA=$(echo "$ENC" | base64 -d | openssl enc -d -des-ecb -K 3338333436353931 -provider legacy -provider default 2>/dev/null || true)
echo "media: $MEDIA"
[[ "$MEDIA" == http* ]] || { echo "JioSaavn link decrypt nahi hua"; exit 1; }
URL320=$(echo "$MEDIA" | sed -E 's/_(96|160|320)\./_320./; s#^http://#https://#')
CT=$(curl -fsS -r 0-65535 -o saavn.bin -w '%{content_type}' "$URL320")
echo "320kbps content-type=$CT bytes=$(stat -c %s saavn.bin)"
[[ $(stat -c %s saavn.bin) -gt 10000 ]] || { echo "JioSaavn audio nahi aaya"; exit 1; }

echo "Sab services theek chal rahi hain ✅"
