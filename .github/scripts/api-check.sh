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

echo "Sab services theek chal rahi hain ✅"
