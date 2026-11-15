'use strict';

const fs = require('fs');
const path = require('path');

// Load server/.env (if present) before reading any variable, without a dependency. Real environment
// variables always win over the file.
(function loadDotEnv() {
  const file = path.join(__dirname, '..', '.env');
  if (!fs.existsSync(file)) return;
  for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
})();

// All configuration comes from environment variables so nothing secret lives in the repo.
// Copy server/.env.example to .env (loaded here) or set these in the service manager.
function env(name, def) {
  const v = process.env[name];
  return v === undefined || v === '' ? def : v;
}

const config = {
  // HTTP/WS port. Behind a TLS reverse proxy (Caddy / nginx / Cloudflare Tunnel) this stays plain HTTP.
  port: parseInt(env('DX_PORT', '8443'), 10),

  // When these two exist the server itself serves HTTPS/WSS (self-signed is fine for a LAN / testing).
  // In production a reverse proxy usually terminates TLS instead and these stay empty.
  tlsCert: env('DX_TLS_CERT', ''),
  tlsKey: env('DX_TLS_KEY', ''),

  // Shared secret every department phone uses to register. Set a long random value.
  deviceToken: env('DX_DEVICE_TOKEN', ''),

  // Secret a viewer (dashboard / browser) needs to watch. Milestone 7 replaces this with per-user login.
  viewerToken: env('DX_VIEWER_TOKEN', ''),

  // STUN servers (free, Google's). Used for discovering the public address; not enough alone behind
  // symmetric NAT (e.g. some Jio connections), which is what TURN is for.
  stunUrls: env('DX_STUN_URLS', 'stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302')
    .split(',').map((s) => s.trim()).filter(Boolean),

  // coturn with `use-auth-secret` / `static-auth-secret`: the server hands out time-limited credentials
  // (no per-user accounts on coturn). Set DX_TURN_URLS (comma list of turn:/turns: URLs) + DX_TURN_SECRET.
  turnUrls: env('DX_TURN_URLS', '').split(',').map((s) => s.trim()).filter(Boolean),
  turnSecret: env('DX_TURN_SECRET', ''),
  turnTtlSeconds: parseInt(env('DX_TURN_TTL', '3600'), 10),

  // How long a device may be silent before it's dropped (heartbeats keep it alive).
  heartbeatMs: parseInt(env('DX_HEARTBEAT_MS', '30000'), 10),

  // In test/dev, allow empty tokens so the test harness doesn't need secrets. Never set this in production.
  allowNoAuth: env('DX_ALLOW_NO_AUTH', '') === '1',
};

module.exports = { config, env };
