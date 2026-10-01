#!/usr/bin/env bash
# Which music APIs can a browser (the web app) reach? Prints CORS header + whether data came back.
ORIGIN="https://asdf968566-art.github.io"
JS='https://www.jiosaavn.com/api.php?__call=search.getResults&q=kesariya&p=1&n=5&_format=json&_marker=0&api_version=4&ctx=web6dot0'
ENC=$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1],safe=''))" "$JS")

check() {
  local name=$1 url=$2
  local headers body
  headers=$(curl -s -m 20 -D - -o /tmp/body -H "Origin: $ORIGIN" "$url" | tr -d '\r')
  local status acao size has
  status=$(echo "$headers" | grep -m1 '^HTTP' | awk '{print $2}')
  acao=$(echo "$headers" | grep -i '^access-control-allow-origin' | head -1 | cut -d' ' -f2)
  size=$(wc -c < /tmp/body)
  has=$(grep -qi 'kesariya' /tmp/body && echo yes || echo no)
  echo "$name: http=$status acao=${acao:-none} bytes=$size kesariya=$has"
}

check "jiosaavn-direct" "$JS"
check "corsproxy.io" "https://corsproxy.io/?url=$ENC"
check "allorigins" "https://api.allorigins.win/raw?url=$ENC"
check "codetabs" "https://api.codetabs.com/v1/proxy?quest=$ENC"
check "saavn.dev" "https://saavn.dev/api/search/songs?query=kesariya"
check "saavn.me" "https://saavn.me/search/songs?query=kesariya"
check "jiosaavn-api-privatecvc2" "https://jiosaavn-api-privatecvc2.vercel.app/search/songs?query=kesariya"
check "lrclib" "https://lrclib.net/api/search?q=kesariya"
check "saavn-cdn-image" "https://c.saavncdn.com/871/Kesariya-From-Brahmastragoing-Hindi-2022-20220717092820-500x500.jpg"
