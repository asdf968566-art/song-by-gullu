'use strict';

// End-to-end proof of the multi-device dashboard (Milestone 8): two stand-in phones publish over WebRTC, and
// the real built React dashboard shows both live screens in a grid, then a focused view. Needs the web app
// built into server/public (the CI server job builds it first) and Playwright + Chromium.
process.env.DX_ALLOW_NO_AUTH = '1';
process.env.DX_STUN_URLS = '';
process.env.DX_TURN_URLS = '';

const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { createServer } = require('../../src/server');
const { deviceSetup } = require('./harness');

let chromium;
try { ({ chromium } = require('playwright')); } catch (e) { /* reported in the test */ }

const BUILT = fs.existsSync(path.join(__dirname, '..', '..', 'public', 'index.html'));

test('the dashboard shows two phones live, then a focused view', { timeout: 90000 }, async (t) => {
  if (!chromium) { t.skip('playwright not installed'); return; }
  if (!BUILT) { t.skip('web app not built into server/public (run: cd web && npm run build)'); return; }

  const { server, hub } = createServer();
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const wsUrl = `ws://127.0.0.1:${port}/ws`;
  const pageUrl = `http://127.0.0.1:${port}/`;

  const launch = { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream', '--no-sandbox'] };
  if (process.env.PW_CHROMIUM_PATH) launch.executablePath = process.env.PW_CHROMIUM_PATH;
  const browser = await chromium.launch(launch);
  try {
    const ctx = await browser.newContext();

    // Two phones come online and publish.
    const phoneA = await ctx.newPage();
    const phoneB = await ctx.newPage();
    await phoneA.goto(pageUrl);
    await phoneB.goto(pageUrl);
    await phoneA.evaluate(deviceSetup, [wsUrl, 'DX-A', 'Pixel 01']);
    await phoneB.evaluate(deviceSetup, [wsUrl, 'DX-B', 'Pixel 02']);

    // The dashboard signs in and should show both, live.
    const dash = await ctx.newPage();
    await dash.goto(pageUrl);
    await dash.fill('input[type=password]', 'anykey');
    await dash.click('button.primary');

    await dash.waitForFunction(() => {
      const names = [...document.querySelectorAll('.tileName')].map((e) => e.textContent);
      return names.includes('Pixel 01') && names.includes('Pixel 02');
    }, { timeout: 30000 });

    await dash.waitForFunction(() => {
      const vs = [...document.querySelectorAll('.tile video')];
      return vs.length >= 2 && vs.every((v) => v.videoWidth > 0);
    }, { timeout: 30000 });

    const grid = await dash.evaluate(() => [...document.querySelectorAll('.tile video')].map((v) => v.videoWidth));
    assert.ok(grid.length >= 2 && grid.every((w) => w > 0), `both tiles have live video: ${grid}`);

    // Focus the first phone.
    await dash.click('.grid .tile');
    await dash.waitForFunction(() => {
      const v = document.querySelector('.focus .big video');
      return v && v.videoWidth > 0;
    }, { timeout: 15000 });
    await dash.waitForFunction(() => {
      const s = document.querySelector('.stats');
      return s && /fps/.test(s.textContent);
    }, { timeout: 10000 });

    assert.equal(hub.devices.size, 2);
  } finally {
    await browser.close();
    hub.stop();
    server.close();
  }
});
