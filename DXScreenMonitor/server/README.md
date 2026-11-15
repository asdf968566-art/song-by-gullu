# DX Screen Monitor — signaling server + web viewer (WebRTC)

This is the internet half of DX Screen Monitor (Milestone 6). The Android app streams its screen over WebRTC;
this small Node.js server does the **signaling** (it introduces a phone and a viewer and passes their WebRTC
offer/answer/ICE messages) and serves the **web viewer**. The video never flows through this server — it goes
peer-to-peer, or through a TURN relay when the network needs one.

## What it does
- **WebSocket signaling** (`/ws`): phones register as *devices*; browsers connect as *viewers*, see the online
  phones, pick one, and the phone sends it a WebRTC offer. One phone can be watched by several viewers at once.
- **Auth**: a phone needs `DX_DEVICE_TOKEN`; a viewer needs `DX_VIEWER_TOKEN` (shared keys for now; per-user login
  is Milestone 7). A viewer can only exchange signaling with the device it is watching.
- **ICE servers**: every peer is handed STUN, plus **time-limited TURN credentials** when a coturn shared secret is
  set (coturn `use-auth-secret` — no accounts stored on coturn).
- **Web viewer** (`/`): sign in with the viewer key, see the device list, click a phone to watch it live, full
  screen, live stats (resolution / fps / bitrate), disconnect.
- `/health` and `/devices.json` for monitoring.

## Run it
```
cd DXScreenMonitor/server
npm install
cp .env.example .env     # set DX_DEVICE_TOKEN and DX_VIEWER_TOKEN
npm start                # http://localhost:8443  (open / for the viewer)
```
Put it behind HTTPS for real use: a reverse proxy (Caddy/nginx) or Cloudflare Tunnel, or set `DX_TLS_CERT` /
`DX_TLS_KEY`. The phone and the browser both need **wss://** (secure) on the internet.

## TURN (for mobile data / Jio)
Peer-to-peer fails behind symmetric NAT, which some mobile networks use. Run **coturn** and point the server at it:
```
# /etc/turnserver.conf (sketch)
listening-port=3478
tls-listening-port=5349
use-auth-secret
static-auth-secret=<long-random-secret>
realm=yourdomain.com
# certificates for turns: ...
```
Then set `DX_TURN_URLS=turn:yourdomain.com:3478,turns:yourdomain.com:5349` and `DX_TURN_SECRET=<the same secret>`.
The server hands each peer a short-lived username/credential; coturn verifies the HMAC.

## Tests
- `npm test` — signaling tests (auth, registry, pairing, relay) over real WebSockets. No browser needed.
- `npm run e2e` — a real WebRTC **video** call between two headless-Chromium pages through the server (needs
  Playwright + Chromium). CI runs both.

## Protocol (WebSocket JSON)
`hello{role,deviceId?,name?,token}` → `welcome{id,iceServers}`. Viewer: `list` → `devices[]`, `watch{deviceId}`.
Device gets `viewer-join{viewerId}` and sends `signal{to,kind:offer|ice,data}`; viewer replies
`signal{to,kind:answer|ice,data}`. See `src/signaling.js`.

Next: the Android WebRTC publisher (in `app/.../webrtc/`) and then the React dashboard (Milestone 7–8).
