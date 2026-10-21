#!/usr/bin/env bash
# Baatcheet on your own Linux PC (runs 24×7, no public IP or router changes needed).
#
#   curl -fsSL https://raw.githubusercontent.com/asdf968566-art/song-by-gullu/main/Baatcheet/deploy/install-home.sh | sudo bash
#
# The site goes online through a Cloudflare Tunnel: your own domain (free Cloudflare account) or, without
# one, a free random *.trycloudflare.com address. Calls use a hosted TURN service (Metered free plan).
# Run it again any time to update. It never opens ports on your PC or router.
set -euo pipefail

REPO="${REPO:-https://github.com/asdf968566-art/song-by-gullu.git}"
BRANCH="${BRANCH:-main}"
DIR="${DIR:-/opt/baatcheet}"
APP="$DIR/Baatcheet"

say() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
ask() { local v; read -r -p "$2${3:+ [$3]}: " v </dev/tty || true; printf -v "$1" '%s' "${v:-${3:-}}"; }
asks() { local v; read -r -s -p "$2: " v </dev/tty || true; echo >/dev/tty; printf -v "$1" '%s' "$v"; }

[ "$(id -u)" = 0 ] || die "Run with sudo:  curl -fsSL … | sudo bash"
MEM_GB=$(( $(awk '/MemTotal/{print $2}' /proc/meminfo) / 1024 / 1024 ))
[ "$MEM_GB" -ge 3 ] || die "This PC has ${MEM_GB} GB RAM; Baatcheet needs at least 4 GB."

say "Installing Docker and git"
if command -v apt-get >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y >/dev/null && apt-get install -y ca-certificates curl git openssl python3 >/dev/null
fi
command -v git >/dev/null || die "Please install git first."
command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh >/dev/null
systemctl enable --now docker >/dev/null

say "Downloading Baatcheet ($BRANCH)"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" fetch -q origin "$BRANCH" && git -C "$DIR" checkout -q -B "$BRANCH" "origin/$BRANCH"
else
  git clone -q --depth 1 -b "$BRANCH" "$REPO" "$DIR"
fi
cd "$APP"

if [ ! -f .env ]; then
  say "First-time setup"
  echo "1) Address of the site"
  echo "   a) My own domain, added to a free Cloudflare account (stays the same, recommended)"
  echo "   b) No domain: a free random https://….trycloudflare.com address (changes when the PC restarts)"
  ask MODE "Choose a or b" "b"
  TUNNEL_TOKEN=""; DOMAIN=""
  if [ "$MODE" = a ]; then
    echo "   Cloudflare → Zero Trust → Networks → Tunnels → Create a tunnel (Cloudflared) → copy the token from the"
    echo "   install command (the long text after --token). Then in the tunnel's Public hostname add:"
    echo "   your domain → Service  HTTP  caddy:80"
    ask DOMAIN "   Your domain, e.g. chat.example.com"
    asks TUNNEL_TOKEN "   Tunnel token (hidden)"
    [ -n "$DOMAIN" ] && [ -n "$TUNNEL_TOKEN" ] || die "Domain and token are both needed for option a."
  fi
  ask ADMIN_EMAIL "2) Your e-mail (becomes the admin account)"
  echo "3) E-mail sending (verification / password reset). Free: brevo.com → SMTP & API → SMTP."
  ask SMTP_HOST "   SMTP server" "smtp-relay.brevo.com"
  ask SMTP_PORT "   SMTP port" "587"
  ask SMTP_USER "   SMTP login"
  asks SMTP_PASS "   SMTP password / key (hidden)"
  ask MAIL_FROM_ADDR "   Send e-mails from (an address verified at your SMTP provider)" "$ADMIN_EMAIL"
  echo "4) Calls on mobile data need a TURN server. Free: dashboard.metered.ca → sign up → TURN Server →"
  echo "   Credentials → Add credential. Leave empty to skip (calls then work only on simpler networks)."
  ask TURN_USER "   Metered TURN username" ""
  TURN_PASS=""; [ -z "$TURN_USER" ] || asks TURN_PASS "   Metered TURN password (hidden)"
  SMTP_URL="$(python3 -c 'import sys,urllib.parse as u; h,p,us,pw=sys.argv[1:]; print(("smtps" if p=="465" else "smtp")+"://"+u.quote(us,safe="")+":"+u.quote(pw,safe="")+"@"+h+":"+p)' "$SMTP_HOST" "$SMTP_PORT" "$SMTP_USER" "$SMTP_PASS")"
  rnd() { openssl rand -hex "${1:-24}"; }
  DB_PASS="$(rnd 18)"
  TURN_URLS=""
  if [ -n "$TURN_USER" ]; then
    TURN_URLS="turn:standard.relay.metered.ca:80,turn:standard.relay.metered.ca:80?transport=tcp,turn:standard.relay.metered.ca:443,turns:standard.relay.metered.ca:443?transport=tcp"
  fi
  VAPID="$(docker run --rm node:22-alpine npx -y web-push@3.6.7 generate-vapid-keys --json 2>/dev/null || true)"
  VAPID_PUB="$(printf '%s' "$VAPID" | python3 -c 'import json,sys; print(json.load(sys.stdin)["publicKey"])' 2>/dev/null || true)"
  VAPID_PRIV="$(printf '%s' "$VAPID" | python3 -c 'import json,sys; print(json.load(sys.stdin)["privateKey"])' 2>/dev/null || true)"
  URL="https://${DOMAIN:-pending.invalid}"
  umask 077
  cat > .env <<ENV
NODE_ENV=production
PORT=4000
HOME_TUNNEL=$([ "$MODE" = a ] && echo named || echo quick)
CLOUDFLARE_TUNNEL_TOKEN=$TUNNEL_TOKEN
POSTGRES_PASSWORD=$DB_PASS
DATABASE_URL=postgresql://baatcheet:$DB_PASS@postgres:5432/baatcheet
SESSION_SECRET=$(rnd 48)
SESSION_DAYS=30
APP_URL=$URL
API_URL=$URL
CORS_ORIGINS=
# Cloudflare Tunnel + Caddy = 2 proxies in front of the API (real visitor IPs for rate limits)
TRUST_PROXY=2
ADMIN_EMAILS=$ADMIN_EMAIL
LOG_LEVEL=info
STORAGE_DRIVER=local
LOCAL_UPLOAD_DIR=/data/uploads
STUN_URLS=stun:stun.l.google.com:19302,stun:stun.relay.metered.ca:80
TURN_URLS=$TURN_URLS
TURN_USERNAME=$TURN_USER
TURN_CREDENTIAL=$TURN_PASS
CALL_RING_SECONDS=45
LIVEKIT_URL=
LIVEKIT_API_KEY=
LIVEKIT_API_SECRET=
SMTP_URL=$SMTP_URL
MAIL_FROM=Baatcheet <$MAIL_FROM_ADDR>
VAPID_PUBLIC_KEY=$VAPID_PUB
VAPID_PRIVATE_KEY=$VAPID_PRIV
VAPID_SUBJECT=mailto:$ADMIN_EMAIL
NEXT_PUBLIC_SOCKET_URL=
ENV
  umask 022
  echo "Saved settings in $APP/.env (keep it private; it is not in git)."
else
  say "Keeping existing settings ($APP/.env)"
fi

# Start on every boot; for the random address this also re-reads the new address after a restart.
cat > /etc/systemd/system/baatcheet.service <<UNIT
[Unit]
Description=Baatcheet
After=docker.service network-online.target
Requires=docker.service
Wants=network-online.target

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=$APP
ExecStart=/bin/bash $APP/deploy/home-up.sh
TimeoutStartSec=1800

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable baatcheet.service >/dev/null

say "Building and starting (first time takes 5–20 minutes)"
bash deploy/home-up.sh --build
