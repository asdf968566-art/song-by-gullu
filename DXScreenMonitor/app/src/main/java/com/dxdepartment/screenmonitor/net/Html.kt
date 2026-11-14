package com.dxdepartment.screenmonitor.net

/** Pages of the local viewer. No outside scripts or fonts: it works on a LAN with no internet. */
object Html {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private const val STYLE = """
*{box-sizing:border-box}html,body{margin:0;height:100%;background:#0b1120;color:#e2e8f0;
font:14px/1.4 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
header{display:flex;flex-wrap:wrap;gap:8px 16px;align-items:center;padding:8px 12px;background:#111827;
border-bottom:1px solid #1f2937}
h1{font-size:16px;margin:0;font-weight:600}.dot{display:inline-block;width:9px;height:9px;border-radius:50%;
background:#64748b;margin-right:6px}.live .dot{background:#22c55e}.lost .dot{background:#ef4444}
.stats{color:#94a3b8;font-size:12px;flex:1;min-width:200px}
button{background:#1e293b;color:#e2e8f0;border:1px solid #334155;border-radius:8px;padding:6px 12px;font:inherit;
cursor:pointer}button:hover{background:#334155}
main{position:absolute;top:50px;bottom:0;left:0;right:0;display:flex;align-items:center;justify-content:center}
img{max-width:100%;max-height:100%;object-fit:contain;background:#000}
.msg{position:absolute;color:#94a3b8;text-align:center;padding:16px}
form{max-width:340px;margin:15vh auto;padding:24px;background:#111827;border-radius:12px}
input{width:100%;padding:10px;margin:12px 0;border-radius:8px;border:1px solid #334155;background:#0b1120;
color:#e2e8f0;font:18px monospace;letter-spacing:3px;text-transform:uppercase}
"""

    fun keyPage(wrong: Boolean) = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>DX Screen Monitor</title>
<style>$STYLE</style></head><body><form method="get" action="/">
<h1>DX Screen Monitor</h1>
<p>Enter the viewer key shown in the app on the phone.</p>
${if (wrong) "<p style=\"color:#f87171\">That key is wrong.</p>" else ""}
<input name="key" maxlength="8" autocomplete="off" autofocus required>
<button type="submit">Watch</button></form></body></html>"""

    fun viewer(device: String) = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(device)} · DX Screen Monitor</title>
<style>$STYLE</style></head><body>
<header id="bar"><h1><span class="dot"></span>${esc(device)}</h1>
<span class="stats" id="stats">Connecting…</span>
<button id="fs">Full screen</button><button id="dc">Disconnect</button></header>
<main id="main"><img id="screen" alt="Phone screen"><div class="msg" id="msg"></div></main>
<script>
const key = new URLSearchParams(location.search).get('key') || '';
const img = document.getElementById('screen'), msg = document.getElementById('msg');
const bar = document.getElementById('bar'), stats = document.getElementById('stats');
let on = true, retry = 0, lastOk = 0;
function connect() {
  if (!on) return;
  img.src = '/stream.mjpg?key=' + encodeURIComponent(key) + '&t=' + Date.now();
}
img.onload = () => { retry = 0; lastOk = Date.now(); msg.textContent = ''; };
img.onerror = () => {
  if (!on) return;
  bar.className = 'lost';
  msg.textContent = 'Connection lost. Reconnecting…';
  setTimeout(connect, Math.min(10000, 1000 * Math.pow(2, retry++)));
};
async function poll() {
  if (!on) return;
  try {
    const r = await fetch('/status.json?key=' + encodeURIComponent(key), {cache: 'no-store'});
    const s = await r.json();
    const live = s.phase === 'SHARING';
    bar.className = live ? 'live' : 'lost';
    stats.textContent = (live ? 'Live' : 'Not sharing') + ' · ' + s.width + '×' + s.height + ' · ' +
      s.encodeFps + ' FPS sent · ' + s.frameKb + ' KB/frame · ' + s.viewers + ' viewer(s) · ' + s.quality +
      (s.lowPower ? ' · low battery mode' : '') + (s.battery != null ? ' · battery ' + s.battery + '%' : '') +
      ' · ' + s.network;
    if (!live) msg.textContent = 'The phone is not sharing its screen right now.';
    else if (msg.textContent.startsWith('The phone')) msg.textContent = '';
  } catch (e) {
    bar.className = 'lost';
    stats.textContent = 'Phone not reachable';
  }
  setTimeout(poll, 2000);
}
document.getElementById('fs').onclick = () => {
  const m = document.getElementById('main');
  (m.requestFullscreen || m.webkitRequestFullscreen).call(m);
};
document.getElementById('dc').onclick = (e) => {
  on = !on;
  if (on) { e.target.textContent = 'Disconnect'; connect(); poll(); }
  else { e.target.textContent = 'Connect'; img.removeAttribute('src'); msg.textContent = 'Disconnected.'; bar.className = ''; }
};
connect(); poll();
</script></body></html>"""
}
