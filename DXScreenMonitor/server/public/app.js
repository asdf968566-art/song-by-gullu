'use strict';
// DX Screen Monitor web viewer (Milestone 6/7, minimal). Connects to the signaling server over WebSocket,
// authenticates with the viewer key, lists the online phones, and plays a chosen phone's live screen over
// WebRTC. The phone is always the offerer; this page answers.

(function () {
  const $ = (id) => document.getElementById(id);
  const KEY = 'dx_viewer_token';

  let ws = null;
  let iceServers = [];
  let pc = null;
  let watching = null; // deviceId
  let devices = [];
  let statsTimer = null;
  let reconnectWanted = false;

  function wsUrl() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${proto}://${location.host}/ws`;
  }

  function setNet(text, cls) {
    const n = $('net');
    n.textContent = text;
    n.parentElement.className = cls || '';
  }

  function connect(token) {
    reconnectWanted = true;
    ws = new WebSocket(wsUrl());
    ws.onopen = () => ws.send(JSON.stringify({ t: 'hello', role: 'viewer', token }));
    ws.onmessage = (ev) => {
      let m; try { m = JSON.parse(ev.data); } catch (e) { return; }
      onMessage(m);
    };
    ws.onclose = (ev) => {
      setNet('Disconnected', 'off');
      teardownPc();
      if (ev.code === 4001) { // unauthorized
        localStorage.removeItem(KEY);
        showLogin('That key was not accepted.');
        reconnectWanted = false;
        return;
      }
      if (reconnectWanted) setTimeout(() => connect(token), 1500);
    };
    ws.onerror = () => {};
  }

  function onMessage(m) {
    switch (m.t) {
      case 'welcome':
        iceServers = m.iceServers || [];
        setNet('Connected', 'live');
        showApp();
        break;
      case 'devices':
        devices = m.devices || [];
        renderDevices();
        break;
      case 'signal':
        onSignal(m);
        break;
      case 'device-gone':
        if (m.deviceId === watching) {
          setHint('That phone went offline. It will reappear here when it is back.');
          teardownPc();
        }
        break;
      case 'error':
        console.warn('server error', m.code, m.message);
        if (m.code === 'unauthorized') { localStorage.removeItem(KEY); showLogin('That key was not accepted.'); }
        break;
    }
  }

  // ---- WebRTC (viewer answers the phone's offer) ----
  async function onSignal(m) {
    if (m.from !== watching) return;
    if (!pc) return;
    try {
      if (m.kind === 'offer') {
        await pc.setRemoteDescription({ type: 'offer', sdp: m.data.sdp });
        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);
        send({ t: 'signal', to: watching, kind: 'answer', data: { sdp: answer.sdp } });
      } else if (m.kind === 'ice' && m.data) {
        await pc.addIceCandidate(m.data).catch(() => {});
      }
    } catch (e) {
      console.warn('signal handling failed', e);
    }
  }

  function watch(deviceId) {
    if (watching === deviceId && pc) return;
    teardownPc();
    watching = deviceId;
    renderDevices();
    setHint('Connecting…');
    pc = new RTCPeerConnection({ iceServers });
    pc.ontrack = (ev) => {
      $('video').srcObject = ev.streams[0];
      setHint('');
      $('full').disabled = false;
      $('disc').disabled = false;
    };
    pc.onicecandidate = (ev) => {
      if (ev.candidate) send({ t: 'signal', to: deviceId, kind: 'ice', data: ev.candidate.toJSON() });
    };
    pc.onconnectionstatechange = () => {
      const s = pc ? pc.connectionState : 'closed';
      if (s === 'failed' || s === 'disconnected') setHint('Connection lost. Reconnecting…');
      if (s === 'connected') setHint('');
    };
    send({ t: 'watch', deviceId });
    startStats();
  }

  function disconnect() {
    if (watching) send({ t: 'stop' });
    teardownPc();
    setHint('Pick a device on the left to watch its screen.');
    renderDevices();
  }

  function teardownPc() {
    if (statsTimer) { clearInterval(statsTimer); statsTimer = null; }
    if (pc) { try { pc.close(); } catch (e) {} pc = null; }
    const v = $('video'); if (v) v.srcObject = null;
    watching = null;
    $('full').disabled = true;
    $('disc').disabled = true;
    $('stats').textContent = '';
  }

  function startStats() {
    let lastBytes = 0, lastTs = 0;
    statsTimer = setInterval(async () => {
      if (!pc) return;
      const stats = await pc.getStats().catch(() => null);
      if (!stats) return;
      let line = '';
      stats.forEach((r) => {
        if (r.type === 'inbound-rtp' && r.kind === 'video') {
          const now = r.timestamp;
          const kbps = lastTs ? Math.round(((r.bytesReceived - lastBytes) * 8) / (now - lastTs)) : 0;
          lastBytes = r.bytesReceived; lastTs = now;
          const w = r.frameWidth || 0, h = r.frameHeight || 0;
          line = `${w}×${h} · ${Math.round(r.framesPerSecond || 0)} fps · ${kbps} kbps` +
            (r.packetsLost ? ` · ${r.packetsLost} lost` : '');
        }
      });
      if (line) $('stats').textContent = line;
    }, 1000);
  }

  function send(obj) { if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj)); }

  // ---- UI ----
  function renderDevices() {
    $('count').textContent = devices.length;
    const box = $('devices');
    box.textContent = '';
    if (!devices.length) {
      const d = document.createElement('div');
      d.className = 'sub'; d.style.padding = '10px';
      d.textContent = 'No phones online yet.';
      box.appendChild(d);
      return;
    }
    for (const dev of devices) {
      const el = document.createElement('div');
      el.className = 'device' + (dev.deviceId === watching ? ' sel' : '');
      const info = document.createElement('div');
      info.style.flex = '1';
      const name = document.createElement('div'); name.className = 'name'; name.textContent = dev.name;
      const sub = document.createElement('div'); sub.className = 'sub';
      sub.textContent = dev.deviceId + (dev.viewers ? ` · ${dev.viewers} watching` : '');
      info.appendChild(name); info.appendChild(sub);
      el.appendChild(info);
      el.onclick = () => watch(dev.deviceId);
      box.appendChild(el);
    }
  }

  function setHint(text) {
    const h = $('hint');
    h.textContent = text || '';
    h.style.display = text ? 'block' : 'none';
  }

  function showLogin(err) {
    $('app').classList.add('hidden');
    $('login').classList.remove('hidden');
    $('loginErr').textContent = err || '';
  }
  function showApp() {
    $('login').classList.add('hidden');
    $('app').classList.remove('hidden');
  }

  $('loginBtn').onclick = () => {
    const token = $('token').value.trim();
    if (!token) { $('loginErr').textContent = 'Enter the viewer key.'; return; }
    localStorage.setItem(KEY, token);
    $('loginErr').textContent = '';
    connect(token);
  };
  $('token').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('loginBtn').click(); });
  $('logout').onclick = () => { reconnectWanted = false; localStorage.removeItem(KEY); if (ws) ws.close(); teardownPc(); showLogin(''); };
  $('disc').onclick = disconnect;
  $('full').onclick = () => {
    const stage = $('video').parentElement;
    (stage.requestFullscreen || stage.webkitRequestFullscreen || (() => {})).call(stage);
  };

  const saved = localStorage.getItem(KEY);
  if (saved) connect(saved); else showLogin('');
})();
