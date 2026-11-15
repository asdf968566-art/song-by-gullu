'use strict';

// Browser-side harness functions used by the end-to-end tests. They run inside a page (via page.evaluate),
// so they may only use browser globals. A "device" here is a stand-in for the Android phone: it registers with
// the signaling server and publishes a fake camera over WebRTC exactly as the app would.

function deviceSetup([url, deviceId, name]) {
  const ws = new WebSocket(url);
  const pcs = {};
  let iceServers = [];
  window.__deviceConnected = new Promise((resolve) => {
    ws.onopen = () => ws.send(JSON.stringify({ t: 'hello', role: 'device', deviceId, name: name || deviceId, token: '' }));
    ws.onmessage = async (ev) => {
      const m = JSON.parse(ev.data);
      if (m.t === 'welcome') { iceServers = m.iceServers; }
      else if (m.t === 'viewer-join') {
        const stream = await navigator.mediaDevices.getUserMedia({ video: true });
        const pc = new RTCPeerConnection({ iceServers });
        pcs[m.viewerId] = pc;
        stream.getTracks().forEach((t) => pc.addTrack(t, stream));
        pc.onicecandidate = (e) => { if (e.candidate) ws.send(JSON.stringify({ t: 'signal', to: m.viewerId, kind: 'ice', data: e.candidate.toJSON() })); };
        pc.onconnectionstatechange = () => { if (pc.connectionState === 'connected') resolve(true); };
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        ws.send(JSON.stringify({ t: 'signal', to: m.viewerId, kind: 'offer', data: { sdp: offer.sdp } }));
      } else if (m.t === 'signal') {
        const pc = pcs[m.from];
        if (!pc) return;
        if (m.kind === 'answer') await pc.setRemoteDescription({ type: 'answer', sdp: m.data.sdp });
        else if (m.kind === 'ice') await pc.addIceCandidate(m.data).catch(() => {});
      }
    };
  });
}

module.exports = { deviceSetup };
