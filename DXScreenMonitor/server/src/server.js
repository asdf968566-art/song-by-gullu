'use strict';

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');
const { config } = require('./config');
const { Hub } = require('./signaling');

const PUBLIC = path.join(__dirname, '..', 'public');
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

function log(...a) {
  console.log(new Date().toISOString(), ...a);
}

function serveStatic(req, res) {
  let urlPath = decodeURIComponent((req.url.split('?')[0] || '/'));
  if (urlPath === '/') urlPath = '/index.html';
  // No path traversal.
  const file = path.join(PUBLIC, path.normalize(urlPath).replace(/^(\.\.[/\\])+/, ''));
  if (!file.startsWith(PUBLIC)) {
    res.writeHead(403).end('forbidden');
    return;
  }
  fs.readFile(file, (err, body) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' }).end('not found');
      return;
    }
    const ext = path.extname(file).toLowerCase();
    res.writeHead(200, {
      'Content-Type': TYPES[ext] || 'application/octet-stream',
      'Cache-Control': 'no-cache',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer',
    }).end(body);
  });
}

function requestHandler(hub) {
  return (req, res) => {
    const url = (req.url || '/').split('?')[0];
    if (url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json' })
        .end(JSON.stringify({ ok: true, devices: hub.devices.size, uptime: process.uptime() }));
      return;
    }
    if (url === '/devices.json') {
      // A quick peek (no media, no secrets) — handy for the dashboard and health checks.
      res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(hub.deviceList()));
      return;
    }
    serveStatic(req, res);
  };
}

function createServer() {
  const hub = new Hub(config, log);
  const handler = requestHandler(hub);

  const useTls = config.tlsCert && config.tlsKey && fs.existsSync(config.tlsCert) && fs.existsSync(config.tlsKey);
  const server = useTls
    ? https.createServer({ cert: fs.readFileSync(config.tlsCert), key: fs.readFileSync(config.tlsKey) }, handler)
    : http.createServer(handler);

  const wss = new WebSocketServer({ server, path: '/ws', maxPayload: 256 * 1024 });
  wss.on('connection', (ws, req) => {
    const remote = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '?').toString().split(',')[0].trim();
    hub.add(ws, remote);
  });
  hub.startHeartbeat();

  return { server, wss, hub, useTls };
}

function start() {
  if (!config.allowNoAuth && (!config.deviceToken || !config.viewerToken)) {
    log('WARNING: DX_DEVICE_TOKEN and/or DX_VIEWER_TOKEN are not set. The server will reject every');
    log('         connection until they are set. (Set DX_ALLOW_NO_AUTH=1 only for local testing.)');
  }
  const { server, useTls } = createServer();
  server.listen(config.port, () => {
    log(`DX Screen Monitor signaling server on ${useTls ? 'https' : 'http'}://0.0.0.0:${config.port}`);
    log(`TURN: ${config.turnUrls.length ? config.turnUrls.join(', ') : 'none (STUN only — may fail on some mobile networks)'}`);
  });
  return server;
}

module.exports = { createServer, start };

if (require.main === module) {
  start(); // config.js already loaded .env
}
