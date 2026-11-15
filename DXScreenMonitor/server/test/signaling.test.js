'use strict';

// Signaling server tests: auth, device registry, viewer listing, pairing and relay rules.
// Secrets are set before the server module reads them.
process.env.DX_DEVICE_TOKEN = 'device-secret';
process.env.DX_VIEWER_TOKEN = 'viewer-secret';
process.env.DX_STUN_URLS = 'stun:stun.example.org:3478';
process.env.DX_TURN_URLS = 'turn:turn.example.org:3478';
process.env.DX_TURN_SECRET = 'turnsecret';

const test = require('node:test');
const assert = require('node:assert');
const { createServer } = require('../src/server');
const { Client } = require('./helpers');

let server, hub, base;

test.before(async () => {
  ({ server, hub } = createServer());
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  base = `ws://127.0.0.1:${server.address().port}/ws`;
});

test.after(() => { hub.stop(); server.close(); });

async function device(id = 'DX-TEST-1', name = 'Pixel 01', token = 'device-secret') {
  const c = new Client(base);
  await c.open();
  c.send({ t: 'hello', role: 'device', deviceId: id, name, token });
  const w = await c.until('welcome');
  return { c, w };
}
async function viewer(token = 'viewer-secret') {
  const c = new Client(base);
  await c.open();
  c.send({ t: 'hello', role: 'viewer', token });
  const w = await c.until('welcome');
  return { c, w };
}

test('device with a wrong token is rejected and the socket is closed', async () => {
  const c = new Client(base);
  await c.open();
  c.send({ t: 'hello', role: 'device', deviceId: 'X', token: 'nope' });
  const m = await c.next();
  assert.equal(m.t, 'error');
  assert.equal(m.code, 'unauthorized');
  c.close();
});

test('viewer with a wrong token is rejected', async () => {
  const c = new Client(base);
  await c.open();
  c.send({ t: 'hello', role: 'viewer', token: 'nope' });
  const m = await c.next();
  assert.equal(m.code, 'unauthorized');
  c.close();
});

test('welcome carries STUN and time-limited TURN credentials', async () => {
  const { c, w } = await device('DX-ICE', 'ICE');
  const turn = w.iceServers.find((s) => String(s.urls).includes('turn:'));
  assert.ok(turn, 'a TURN server is offered');
  assert.match(turn.username, /^\d+:/, 'TURN username starts with an expiry');
  assert.ok(turn.credential && turn.credential.length > 10, 'TURN credential present');
  assert.ok(w.iceServers.some((s) => String(s.urls).includes('stun:')), 'STUN is offered');
  c.close();
});

test('a viewer sees an online device and a watch makes the device get viewer-join', async () => {
  const d = await device('DX-WATCH', 'Pixel 02');
  const v = await viewer();
  // The viewer's welcome is followed by a devices list.
  const list = await v.c.until('devices');
  assert.ok(list.devices.some((x) => x.deviceId === 'DX-WATCH' && x.name === 'Pixel 02'));

  v.c.send({ t: 'watch', deviceId: 'DX-WATCH' });
  const join = await d.c.until('viewer-join');
  assert.ok(join.viewerId && join.viewerId.startsWith('v_'));

  d.c.close(); v.c.close();
});

test('offer/answer/ice are relayed only between paired peers', async () => {
  const d = await device('DX-RELAY', 'Pixel 03');
  const v = await viewer();
  await v.c.until('devices');
  v.c.send({ t: 'watch', deviceId: 'DX-RELAY' });
  const join = await d.c.until('viewer-join');
  const viewerId = join.viewerId;

  // Device -> viewer offer.
  d.c.send({ t: 'signal', to: viewerId, kind: 'offer', data: { sdp: 'OFFER_SDP' } });
  const offer = await v.c.until('signal');
  assert.equal(offer.kind, 'offer');
  assert.equal(offer.data.sdp, 'OFFER_SDP');
  assert.equal(offer.from, 'DX-RELAY');

  // Viewer -> device answer.
  v.c.send({ t: 'signal', to: 'DX-RELAY', kind: 'answer', data: { sdp: 'ANSWER_SDP' } });
  const answer = await d.c.until('signal');
  assert.equal(answer.kind, 'answer');
  assert.equal(answer.data.sdp, 'ANSWER_SDP');
  assert.equal(answer.from, viewerId);

  // ICE both ways.
  d.c.send({ t: 'signal', to: viewerId, kind: 'ice', data: { candidate: 'cand-d' } });
  assert.equal((await v.c.until('signal')).data.candidate, 'cand-d');

  d.c.close(); v.c.close();
});

test('an unpaired viewer cannot signal a device', async () => {
  const d = await device('DX-SECURE', 'Pixel 04');
  const v = await viewer();
  await v.c.until('devices');
  // No watch sent, so not paired.
  v.c.send({ t: 'signal', to: 'DX-SECURE', kind: 'offer', data: { sdp: 'x' } });
  const m = await v.c.until('error');
  assert.equal(m.code, 'not_paired');
  d.c.close(); v.c.close();
});

test('when the device disconnects, a watching viewer is told device-gone', async () => {
  const d = await device('DX-GONE', 'Pixel 05');
  const v = await viewer();
  await v.c.until('devices');
  v.c.send({ t: 'watch', deviceId: 'DX-GONE' });
  await d.c.until('viewer-join');
  d.c.close();
  const gone = await v.c.until('device-gone');
  assert.equal(gone.deviceId, 'DX-GONE');
  v.c.close();
});

test('a second device with the same id replaces the first', async () => {
  const d1 = await device('DX-DUP', 'first');
  const d2 = await device('DX-DUP', 'second');
  const replaced = await d1.c.until('replaced');
  assert.equal(replaced.t, 'replaced');
  assert.equal(hub.devices.get('DX-DUP').name, 'second');
  d2.c.close();
});

test('messages before hello are refused', async () => {
  const c = new Client(base);
  await c.open();
  c.send({ t: 'list' });
  const m = await c.next();
  assert.equal(m.code, 'not_authed');
  c.close();
});
