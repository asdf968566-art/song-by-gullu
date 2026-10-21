#!/usr/bin/env bash
# Starts Baatcheet in home mode (used by install-home.sh and the baatcheet systemd service).
# With the random trycloudflare address it waits for the new address and puts it into .env.
set -euo pipefail
cd "$(dirname "$0")/.."
MODE="$(grep '^HOME_TUNNEL=' .env | cut -d= -f2-)"
dc() { docker compose -f docker-compose.yml -f docker-compose.home.yml --profile "$MODE" "$@"; }

dc up -d --remove-orphans "${@}"

if [ "$MODE" = quick ]; then
  URL=""
  for _ in $(seq 1 60); do
    URL="$(dc logs quicktunnel 2>/dev/null | grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' | tail -1 || true)"
    [ -n "$URL" ] && break
    sleep 3
  done
  [ -n "$URL" ] || { echo "Cloudflare didn't give an address. Check: docker compose logs quicktunnel"; exit 1; }
  if ! grep -qx "APP_URL=$URL" .env; then
    sed -i "s#^APP_URL=.*#APP_URL=$URL#; s#^API_URL=.*#API_URL=$URL#" .env
    dc up -d --force-recreate server
  fi
else
  URL="$(grep '^APP_URL=' .env | cut -d= -f2-)"
fi

for _ in $(seq 1 60); do
  if dc exec -T server node -e "fetch('http://localhost:4000/readyz').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))" 2>/dev/null; then
    echo "$URL" > ../SITE-ADDRESS.txt
    printf '\n\033[1;32mBaatcheet is running:  %s\033[0m\n' "$URL"
    echo "Sign up with $(grep '^ADMIN_EMAILS=' .env | cut -d= -f2-) to get the Admin page."
    [ "$MODE" = quick ] && echo "This address changes when the PC restarts; the current one is always in $(cd .. && pwd)/SITE-ADDRESS.txt"
    exit 0
  fi
  sleep 5
done
dc ps
dc logs --tail=80 server
echo "The API didn't come up. The log above says why."
exit 1
