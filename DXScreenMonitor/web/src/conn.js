// WebRTC + signaling for the web viewer / dashboard. The phone is the offerer; the browser answers.
// Each watched device gets its own viewer WebSocket (the server pairs one device per socket), so the
// dashboard can watch many phones at once, each on its own connection.

export function wsUrl() {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${location.host}/ws`;
}

// A control connection just for the live device list (no media).
export class ControlConn {
  constructor({ url, token, onDevices, onStatus }) {
    Object.assign(this, { url, token, onDevices, onStatus });
    this.stopped = false;
  }
  start() {
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => this.ws.send(JSON.stringify({ t: 'hello', role: 'viewer', token: this.token }));
    this.ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data);
      if (m.t === 'welcome') this.onStatus('connected');
      else if (m.t === 'devices') this.onDevices(m.devices || []);
      else if (m.t === 'error' && m.code === 'unauthorized') this.onStatus('unauthorized');
    };
    this.ws.onclose = (ev) => {
      if (ev.code === 4001) { this.onStatus('unauthorized'); return; }
      this.onStatus('disconnected');
      if (!this.stopped) setTimeout(() => this.start(), 1500);
    };
    this.ws.onerror = () => {};
  }
  refresh() { if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify({ t: 'list' })); }
  stop() { this.stopped = true; try { this.ws && this.ws.close(); } catch (e) {} }
}

// One device's live video.
export class DeviceConn {
  constructor({ url, token, deviceId, onStream, onStatus }) {
    Object.assign(this, { url, token, deviceId, onStream, onStatus });
    this.stopped = false;
    this.iceServers = [];
    this.pc = null;
  }
  start() {
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => this.ws.send(JSON.stringify({ t: 'hello', role: 'viewer', token: this.token }));
    this.ws.onmessage = (ev) => this.onMsg(JSON.parse(ev.data));
    this.ws.onclose = () => {
      if (this.stopped) return;
      this.onStatus('reconnecting');
      setTimeout(() => this.start(), 1500);
    };
    this.ws.onerror = () => {};
  }
  async onMsg(m) {
    if (m.t === 'welcome') {
      this.iceServers = m.iceServers || [];
      this.ws.send(JSON.stringify({ t: 'watch', deviceId: this.deviceId }));
    } else if (m.t === 'signal' && m.from === this.deviceId) {
      if (!this.pc) this.makePc();
      try {
        if (m.kind === 'offer') {
          await this.pc.setRemoteDescription({ type: 'offer', sdp: m.data.sdp });
          const answer = await this.pc.createAnswer();
          await this.pc.setLocalDescription(answer);
          this.send('answer', { sdp: answer.sdp });
        } else if (m.kind === 'ice') {
          await this.pc.addIceCandidate(m.data).catch(() => {});
        }
      } catch (e) { /* renegotiation races are harmless */ }
    } else if (m.t === 'device-gone') {
      this.onStatus('offline');
    }
  }
  makePc() {
    this.pc = new RTCPeerConnection({ iceServers: this.iceServers });
    this.pc.onicecandidate = (e) => { if (e.candidate) this.send('ice', e.candidate.toJSON()); };
    this.pc.ontrack = (e) => this.onStream(e.streams[0]);
    this.pc.onconnectionstatechange = () => this.onStatus(this.pc ? this.pc.connectionState : 'closed');
  }
  send(kind, data) { if (this.ws && this.ws.readyState === 1) this.ws.send(JSON.stringify({ t: 'signal', to: this.deviceId, kind, data })); }

  async stats() {
    if (!this.pc) return null;
    const s = await this.pc.getStats().catch(() => null);
    if (!s) return null;
    let out = null;
    s.forEach((r) => {
      if (r.type === 'inbound-rtp' && r.kind === 'video') {
        out = { w: r.frameWidth || 0, h: r.frameHeight || 0, fps: Math.round(r.framesPerSecond || 0), bytes: r.bytesReceived || 0, lost: r.packetsLost || 0 };
      }
    });
    return out;
  }
  stop() {
    this.stopped = true;
    try { this.pc && this.pc.close(); } catch (e) {}
    try { this.ws && this.ws.close(); } catch (e) {}
    this.pc = null;
  }
}
