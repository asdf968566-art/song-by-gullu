'use strict';

const crypto = require('crypto');
const { iceServers } = require('./turn');

const MAX_MSG = 256 * 1024; // SDP with many candidates is a few KB; cap well above that.

function timingSafeEqual(a, b) {
  const ba = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

/**
 * The signaling hub. It never touches the media — it only authenticates peers, keeps a registry of online
 * devices, and relays SDP offers/answers and ICE candidates between a device and the viewers watching it.
 *
 * Roles:
 *  - device  (a phone): registers under its deviceId, then creates one WebRTC connection per viewer.
 *  - viewer  (a browser / dashboard): lists devices and asks to watch one; the device answers with an offer.
 *
 * A peer may only signal the peers it is paired with (a viewer ↔ the device it watches), so one viewer can
 * never reach another or a device it isn't watching.
 */
class Hub {
  constructor(config, log = () => {}) {
    this.config = config;
    this.log = log;
    this.devices = new Map(); // deviceId -> device peer
    this.peers = new Map(); // id -> any peer (device or viewer)
  }

  deviceList() {
    return [...this.devices.values()].map((d) => ({
      deviceId: d.deviceId,
      name: d.name || d.deviceId,
      viewers: d.viewers.size,
      since: d.since,
    }));
  }

  /** Attach a new WebSocket. `send` is called with a JS object; `close` ends the socket. */
  add(ws, remote) {
    const peer = {
      ws,
      remote: remote || '?',
      role: null,
      id: null,
      deviceId: null,
      name: null,
      watching: null, // viewer: the deviceId it watches
      viewers: new Set(), // device: viewerIds
      authed: false,
      alive: true,
    };

    const send = (obj) => {
      try {
        if (ws.readyState === 1) ws.send(JSON.stringify(obj));
      } catch (e) {
        /* socket going away */
      }
    };
    peer.send = send;

    ws.on('message', (raw, isBinary) => {
      if (isBinary || raw.length > MAX_MSG) {
        send({ t: 'error', code: 'bad_message', message: 'Only small JSON messages are allowed' });
        return;
      }
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch (e) {
        send({ t: 'error', code: 'bad_json', message: 'Message was not JSON' });
        return;
      }
      try {
        this.handle(peer, msg);
      } catch (e) {
        this.log('handler error', e && e.message);
        send({ t: 'error', code: 'server_error', message: 'Could not handle that' });
      }
    });

    ws.on('close', () => this.remove(peer));
    ws.on('error', () => this.remove(peer));
    ws.on('pong', () => { peer.alive = true; });
    return peer;
  }

  handle(peer, msg) {
    const t = msg && msg.t;
    if (!peer.authed && t !== 'hello') {
      peer.send({ t: 'error', code: 'not_authed', message: 'Say hello first' });
      return;
    }
    switch (t) {
      case 'hello': return this.onHello(peer, msg);
      case 'list': return this.onList(peer);
      case 'watch': return this.onWatch(peer, msg);
      case 'stop': return this.onStop(peer, msg);
      case 'signal': return this.onSignal(peer, msg);
      case 'ping': return peer.send({ t: 'pong' });
      default:
        peer.send({ t: 'error', code: 'unknown', message: `Unknown message '${t}'` });
    }
  }

  onHello(peer, msg) {
    if (peer.authed) return;
    const role = msg.role === 'device' ? 'device' : msg.role === 'viewer' ? 'viewer' : null;
    if (!role) return peer.send({ t: 'error', code: 'bad_role', message: 'role must be device or viewer' });

    const want = role === 'device' ? this.config.deviceToken : this.config.viewerToken;
    const ok = this.config.allowNoAuth || (want && timingSafeEqual(msg.token, want));
    if (!ok) {
      peer.send({ t: 'error', code: 'unauthorized', message: 'Wrong or missing token' });
      try { peer.ws.close(4001, 'unauthorized'); } catch (e) { /* */ }
      return;
    }

    peer.role = role;
    peer.authed = true;
    peer.since = Date.now();

    if (role === 'device') {
      const deviceId = String(msg.deviceId || '').trim();
      if (!deviceId) return peer.send({ t: 'error', code: 'no_device_id', message: 'deviceId required' });
      // A reconnecting device with the same id replaces the old socket.
      const old = this.devices.get(deviceId);
      if (old && old !== peer) {
        old.send({ t: 'replaced' });
        try { old.ws.close(4002, 'replaced'); } catch (e) { /* */ }
        this.remove(old);
      }
      peer.id = deviceId;
      peer.deviceId = deviceId;
      peer.name = String(msg.name || '').slice(0, 60) || deviceId;
      this.devices.set(deviceId, peer);
      this.peers.set(deviceId, peer);
      this.log(`device online: ${deviceId} (${peer.name}) from ${peer.remote}`);
    } else {
      peer.id = 'v_' + crypto.randomBytes(8).toString('hex');
      this.peers.set(peer.id, peer);
      this.log(`viewer online: ${peer.id} from ${peer.remote}`);
    }

    peer.send({ t: 'welcome', id: peer.id, role, iceServers: iceServers(this.config, peer.id) });
    if (role === 'viewer') this.onList(peer);
  }

  onList(peer) {
    if (peer.role !== 'viewer') return;
    peer.send({ t: 'devices', devices: this.deviceList() });
  }

  onWatch(peer, msg) {
    if (peer.role !== 'viewer') return peer.send({ t: 'error', code: 'not_viewer', message: 'Only viewers watch' });
    const device = this.devices.get(String(msg.deviceId || ''));
    if (!device) return peer.send({ t: 'error', code: 'no_device', message: 'That device is offline' });
    if (peer.watching && peer.watching !== device.deviceId) this.leave(peer); // one device at a time per socket
    peer.watching = device.deviceId;
    device.viewers.add(peer.id);
    this.log(`viewer ${peer.id} watches ${device.deviceId}`);
    // Tell the device to create a WebRTC connection and send an offer to this viewer.
    device.send({ t: 'viewer-join', viewerId: peer.id });
  }

  onStop(peer, msg) {
    if (peer.role === 'viewer') this.leave(peer);
    else if (peer.role === 'device' && msg.viewerId) {
      const v = this.peers.get(String(msg.viewerId));
      if (v && v.watching === peer.deviceId) this.leave(v);
    }
  }

  onSignal(peer, msg) {
    const target = this.peers.get(String(msg.to || ''));
    if (!target) return peer.send({ t: 'error', code: 'no_peer', message: 'That peer is gone' });
    // Only paired peers may signal each other.
    const paired =
      (peer.role === 'viewer' && peer.watching === target.deviceId) ||
      (peer.role === 'device' && target.watching === peer.deviceId);
    if (!paired) return peer.send({ t: 'error', code: 'not_paired', message: 'Not allowed' });
    const kind = msg.kind;
    if (kind !== 'offer' && kind !== 'answer' && kind !== 'ice') {
      return peer.send({ t: 'error', code: 'bad_signal', message: 'kind must be offer/answer/ice' });
    }
    target.send({ t: 'signal', from: peer.id, kind, data: msg.data });
  }

  /** A viewer stops watching its device (or the device went away). */
  leave(viewer) {
    if (!viewer.watching) return;
    const device = this.devices.get(viewer.watching);
    if (device) {
      device.viewers.delete(viewer.id);
      device.send({ t: 'viewer-left', viewerId: viewer.id });
    }
    viewer.watching = null;
  }

  remove(peer) {
    if (peer.removed) return;
    peer.removed = true;
    if (peer.role === 'viewer') {
      this.leave(peer);
      if (peer.id) this.peers.delete(peer.id);
    } else if (peer.role === 'device' && peer.deviceId) {
      // Only remove if this socket is still the registered one (not an already-replaced old socket).
      if (this.devices.get(peer.deviceId) === peer) {
        this.devices.delete(peer.deviceId);
        this.peers.delete(peer.deviceId);
        // Tell watching viewers the device is gone.
        for (const v of this.peers.values()) {
          if (v.role === 'viewer' && v.watching === peer.deviceId) {
            v.send({ t: 'device-gone', deviceId: peer.deviceId });
            v.watching = null;
          }
        }
        this.log(`device offline: ${peer.deviceId}`);
      }
    }
  }

  /** Ping loop: drop peers that stop answering (also keeps idle sockets through proxies). */
  startHeartbeat() {
    const period = Math.max(5000, Math.floor(this.config.heartbeatMs / 2));
    this.timer = setInterval(() => {
      for (const peer of this.peers.values()) {
        if (!peer.alive) {
          try { peer.ws.terminate(); } catch (e) { /* */ }
          this.remove(peer);
          continue;
        }
        peer.alive = false;
        try { peer.ws.ping(); } catch (e) { /* */ }
      }
    }, period);
    if (this.timer.unref) this.timer.unref();
  }

  stop() {
    if (this.timer) clearInterval(this.timer);
  }
}

module.exports = { Hub, timingSafeEqual };
