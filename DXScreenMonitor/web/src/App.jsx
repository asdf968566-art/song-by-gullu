import React, { useEffect, useRef, useState, useCallback } from 'react';
import { ControlConn, DeviceConn, wsUrl } from './conn.js';

const TOKEN_KEY = 'dx_viewer_token';

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY) || '');
  const [authed, setAuthed] = useState(false);
  const [status, setStatus] = useState('idle'); // idle | connected | disconnected | unauthorized
  const [devices, setDevices] = useState([]); // online devices from the server
  const [known, setKnown] = useState({}); // deviceId -> { name, online } (keeps offline phones on the wall)
  const [streams, setStreams] = useState({}); // deviceId -> MediaStream
  const [states, setStates] = useState({}); // deviceId -> connection state string
  const [focusId, setFocusId] = useState(null);
  const [filter, setFilter] = useState('');
  const [hideOffline, setHideOffline] = useState(false);

  const control = useRef(null);
  const conns = useRef(new Map());

  const connect = useCallback((tok) => {
    if (control.current) control.current.stop();
    control.current = new ControlConn({
      url: wsUrl(),
      token: tok,
      onStatus: (s) => {
        setStatus(s);
        if (s === 'unauthorized') { localStorage.removeItem(TOKEN_KEY); setAuthed(false); }
        else if (s === 'connected') setAuthed(true);
      },
      onDevices: (list) => setDevices(list),
    });
    control.current.start();
  }, []);

  useEffect(() => {
    if (token) connect(token);
    return () => { if (control.current) control.current.stop(); };
  }, []); // eslint-disable-line

  // Reconcile live video connections to the set of online devices.
  useEffect(() => {
    const online = new Set(devices.map((d) => d.deviceId));
    setKnown((prev) => {
      const next = { ...prev };
      for (const d of devices) next[d.deviceId] = { name: d.name, online: true, viewers: d.viewers };
      for (const id of Object.keys(next)) if (!online.has(id)) next[id] = { ...next[id], online: false };
      return next;
    });
    // Start a connection for each newly online device.
    for (const d of devices) {
      if (!conns.current.has(d.deviceId)) {
        const c = new DeviceConn({
          url: wsUrl(),
          token,
          deviceId: d.deviceId,
          onStream: (stream) => setStreams((p) => ({ ...p, [d.deviceId]: stream })),
          onStatus: (st) => setStates((p) => ({ ...p, [d.deviceId]: st })),
        });
        conns.current.set(d.deviceId, c);
        c.start();
      }
    }
    // Stop connections to devices that went offline.
    for (const [id, c] of conns.current) {
      if (!online.has(id)) {
        c.stop();
        conns.current.delete(id);
        setStreams((p) => { const n = { ...p }; delete n[id]; return n; });
        setStates((p) => ({ ...p, [id]: 'offline' }));
      }
    }
  }, [devices, token]);

  useEffect(() => () => { for (const c of conns.current.values()) c.stop(); }, []);

  function signIn(tok) {
    const t = tok.trim();
    if (!t) return;
    localStorage.setItem(TOKEN_KEY, t);
    setToken(t);
    connect(t);
  }
  function signOut() {
    localStorage.removeItem(TOKEN_KEY);
    for (const c of conns.current.values()) c.stop();
    conns.current.clear();
    if (control.current) control.current.stop();
    setAuthed(false); setToken(''); setDevices([]); setKnown({}); setStreams({}); setStates({}); setFocusId(null);
  }

  if (!authed) return <Login status={status} onSubmit={signIn} initial={token} />;

  return (
    <Dashboard
      known={known} streams={streams} states={states} status={status}
      focusId={focusId} setFocusId={setFocusId}
      filter={filter} setFilter={setFilter}
      hideOffline={hideOffline} setHideOffline={setHideOffline}
      connFor={(id) => conns.current.get(id)}
      onSignOut={signOut}
    />
  );
}

function Login({ status, onSubmit, initial }) {
  const [value, setValue] = useState(initial || '');
  return (
    <div className="login">
      <h1>DX Screen Monitor</h1>
      <p className="muted">Enter the viewer key to watch the department phones.</p>
      <input
        type="password" placeholder="Viewer key" value={value} autoFocus
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => { if (e.key === 'Enter') onSubmit(value); }}
      />
      {status === 'unauthorized' && <div className="err">That key was not accepted.</div>}
      <button className="primary" onClick={() => onSubmit(value)}>Connect</button>
    </div>
  );
}

function Dashboard(props) {
  const { known, streams, states, status, focusId, setFocusId, filter, setFilter, hideOffline, setHideOffline, connFor, onSignOut } = props;
  const rootRef = useRef(null);

  let list = Object.entries(known).map(([deviceId, k]) => ({ deviceId, ...k }));
  const q = filter.trim().toLowerCase();
  if (q) list = list.filter((d) => d.name.toLowerCase().includes(q) || d.deviceId.toLowerCase().includes(q));
  if (hideOffline) list = list.filter((d) => d.online);
  list.sort((a, b) => (a.online === b.online ? a.name.localeCompare(b.name) : a.online ? -1 : 1));

  const onlineCount = list.filter((d) => d.online).length;
  const focus = focusId && known[focusId] ? { deviceId: focusId, ...known[focusId] } : null;

  return (
    <div className="dash" ref={rootRef}>
      <header>
        <h1><span className={`dot ${status === 'connected' ? 'live' : 'off'}`} />DX Screen Monitor</h1>
        <span className="muted count">{onlineCount} online · {list.length} total</span>
        <input className="search" placeholder="Search phones…" value={filter} onChange={(e) => setFilter(e.target.value)} />
        <label className="muted toggle"><input type="checkbox" checked={hideOffline} onChange={(e) => setHideOffline(e.target.checked)} /> Hide offline</label>
        <button onClick={() => fs(rootRef.current)}>Full screen</button>
        <button onClick={onSignOut}>Sign out</button>
      </header>

      {focus ? (
        <div className="focus">
          <div className="big">
            <Tile device={focus} stream={streams[focus.deviceId]} state={states[focus.deviceId]} big onClick={() => setFocusId(null)} />
            <Stats conn={connFor(focus.deviceId)} />
          </div>
          <div className="strip">
            {list.filter((d) => d.deviceId !== focus.deviceId).map((d) => (
              <Tile key={d.deviceId} device={d} stream={streams[d.deviceId]} state={states[d.deviceId]} small onClick={() => setFocusId(d.deviceId)} />
            ))}
          </div>
        </div>
      ) : list.length === 0 ? (
        <div className="empty muted">No phones online yet. They appear here as soon as they start sharing.</div>
      ) : (
        <div className="grid">
          {list.map((d) => (
            <Tile key={d.deviceId} device={d} stream={streams[d.deviceId]} state={states[d.deviceId]} onClick={() => setFocusId(d.deviceId)} />
          ))}
        </div>
      )}
    </div>
  );
}

function Tile({ device, stream, state, big, small, onClick }) {
  const ref = useRef(null);
  useEffect(() => { if (ref.current) ref.current.srcObject = stream || null; }, [stream]);
  const online = device.online;
  const connected = state === 'connected';
  const label = !online ? 'Offline' : connected ? '' : (state === 'reconnecting' ? 'Reconnecting…' : 'Connecting…');
  return (
    <div className={`tile ${big ? 'big' : ''} ${small ? 'small' : ''} ${online ? '' : 'dim'}`} onClick={onClick} title={device.deviceId}>
      <video ref={ref} autoPlay muted playsInline />
      {label && <div className="tileMsg">{label}</div>}
      <div className="tileBar">
        <span className={`dot ${connected ? 'live' : online ? 'warn' : 'off'}`} />
        <span className="tileName">{device.name}</span>
      </div>
    </div>
  );
}

function Stats({ conn }) {
  const [s, setS] = useState(null);
  useEffect(() => {
    if (!conn) return;
    let last = 0, lastT = 0;
    const id = setInterval(async () => {
      const st = await conn.stats();
      if (!st) return;
      const now = Date.now();
      const kbps = lastT ? Math.round(((st.bytes - last) * 8) / (now - lastT)) : 0;
      last = st.bytes; lastT = now;
      setS({ ...st, kbps });
    }, 1000);
    return () => clearInterval(id);
  }, [conn]);
  if (!s) return <div className="stats muted">Connecting…</div>;
  return (
    <div className="stats muted">
      {s.w}×{s.h} · {s.fps} fps · {s.kbps} kbps{s.lost ? ` · ${s.lost} lost` : ''}
    </div>
  );
}

function fs(el) {
  if (!el) return;
  (el.requestFullscreen || el.webkitRequestFullscreen || (() => {})).call(el);
}
