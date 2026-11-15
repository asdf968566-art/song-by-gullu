'use strict';

// End-to-end proof that the signaling server carries a real WebRTC video call: two headless Chromium pages
// (a stand-in "device" publishing a fake camera, and a "viewer") connect through the server and the viewer
// actually receives decoded video frames. The Android phone is tested separately on the emulator; this proves
// the server + the browser viewer half of the pipeline.
process.env.DX_ALLOW_NO_AUTH = '1';
process.env.DX_STUN_URLS = ''; // host-local candidates connect over loopback; no external STUN needed.
process.env.DX_TURN_URLS = '';

const test = require('node:test');
const assert = require('node:assert');
const { createServer } = require('../../src/server');
const { deviceSetup } = require('./harness');

let chromium;
try { ({ chromium } = require('playwright')); } catch (e) { /* reported in the test */ }

function viewerSetup([url, deviceId]) {
  const ws = new WebSocket(url);
  let pc = null;
  let iceServers = [];
  window.__gotVideo = new Promise((resolve) => {
    ws.onopen = () => ws.send(JSON.stringify({ t: 'hello', role: 'viewer', token: '' }));
    ws.onmessage = async (ev) => {
      const m = JSON.parse(ev.data);
      if (m.t === 'welcome') { iceServers = m.iceServers; ws.send(JSON.stringify({ t: 'watch', deviceId })); }
      else if (m.t === 'signal' && m.from === deviceId) {
        if (!pc) {
          pc = new RTCPeerConnection({ iceServers });
          pc.onicecandidate = (e) => { if (e.candidate) ws.send(JSON.stringify({ t: 'signal', to: deviceId, kind: 'ice', data: e.candidate.toJSON() })); };
          pc.ontrack = (e) => {
            const v = document.createElement('video');
            v.autoplay = true; v.muted = true; v.playsInline = true;
            v.srcObject = e.streams[0];
            document.body.appendChild(v);
            const tick = () => {
              if (v.videoWidth > 0) resolve({ w: v.videoWidth, h: v.videoHeight });
              else setTimeout(tick, 100);
            };
            tick();
          };
        }
        if (m.kind === 'offer') {
          await pc.setRemoteDescription({ type: 'offer', sdp: m.data.sdp });
          const answer = await pc.createAnswer();
          await pc.setLocalDescription(answer);
          ws.send(JSON.stringify({ t: 'signal', to: deviceId, kind: 'answer', data: { sdp: answer.sdp } }));
        } else if (m.kind === 'ice') {
          await pc.addIceCandidate(m.data).catch(() => {});
        }
      }
    };
  });
}

test('a real WebRTC video call flows device → viewer through the server', { timeout: 60000 }, async (t) => {
  if (!chromium) { t.skip('playwright not installed'); return; }

  const { server, hub } = createServer();
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const wsUrl = `ws://127.0.0.1:${port}/ws`;
  const pageUrl = `http://127.0.0.1:${port}/`; // localhost = a secure context, so getUserMedia works

  const launch = {
    args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--no-sandbox'],
  };
  // In this sandbox Playwright's own browser build may differ; point at the preinstalled one if given.
  if (process.env.PW_CHROMIUM_PATH) launch.executablePath = process.env.PW_CHROMIUM_PATH;
  const browser = await chromium.launch(launch);
  try {
    const ctx = await browser.newContext();
    const devicePage = await ctx.newPage();
    const viewerPage = await ctx.newPage();
    await devicePage.goto(pageUrl);
    await viewerPage.goto(pageUrl);

    const DEVICE_ID = 'DX-E2E-1';
    // The device registers and waits for a viewer; the viewer watches it and the device publishes a fake camera.
    await devicePage.evaluate(deviceSetup, [wsUrl, DEVICE_ID, 'E2E']);
    await viewerPage.evaluate(viewerSetup, [wsUrl, DEVICE_ID]);

    const dims = await viewerPage.evaluate(() => window.__gotVideo);
    assert.ok(dims.w > 0 && dims.h > 0, `viewer received video (${dims.w}x${dims.h})`);

    const connected = await devicePage.evaluate(() => window.__deviceConnected);
    assert.equal(connected, true, 'device peer connection reached connected');

    assert.equal(hub.devices.size >= 1, true);
  } finally {
    await browser.close();
    hub.stop();
    server.close();
  }
});
