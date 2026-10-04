'use strict';
/* Sangeet web app: works on iPhone from Safari ("Add to Home Screen"), no App Store needed.
 * Songs come from a catalog built daily by GitHub Actions (data/<language>.json) and stream
 * straight from JioSaavn's CDN. Songs found only on YouTube play in the YouTube player. */

const IMG = 'https://c.saavncdn.com/';
const AAC = 'https://aac.saavncdn.com/';
const ALL_LANGS = ['hindi', 'punjabi', 'english', 'haryanvi', 'bhojpuri', 'tamil', 'telugu', 'marathi', 'bengali', 'gujarati'];
const DEFAULT_YT_KEY = 'AIzaSyB1kFxcI23xi418PgROIaGhT_-p2DG33Pw';

/* ------------------------------------------------------------------ icons */
const ICONS = {
  play: 'M8 5v14l11-7z',
  pause: 'M6 19h4V5H6v14zm8-14v14h4V5h-4z',
  next: 'M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z',
  prev: 'M6 6h2v12H6zm3.5 6l8.5 6V6z',
  heart: 'M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z',
  heartFill: 'M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z',
  more: 'M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z',
  search: 'M15.5 14h-.79l-.28-.27A6.47 6.47 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  library: 'M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 5h-3v5.5a2.5 2.5 0 01-5 0 2.5 2.5 0 012.5-2.5c.57 0 1.08.19 1.5.51V5h4v2zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6z',
  feed: 'M4 6h16V4H4v2zm2-4h12V0H6v2zm14 6H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-10 11V11l6 4-6 4z',
  settings: 'M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z',
  back: 'M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z',
  down: 'M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z',
  lyrics: 'M6 17h3l2-4V7H5v6h3zm8 0h3l2-4V7h-6v6h3z',
  queue: 'M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z',
  sparkles: 'M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z',
  shuffle: 'M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z',
  chart: 'M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z',
  clock: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
  globe: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z',
  list: 'M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z',
};
function icon(name) {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 24 24');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', ICONS[name]);
  s.append(p);
  return s;
}

/* ------------------------------------------------------------------ tiny DOM helper (text is never parsed as HTML) */
function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false || (k === 'src' && !v)) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style') el.style.cssText = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k in el && typeof v !== 'string') el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of kids.flat(Infinity)) {
    if (c == null || c === false) continue;
    el.append(c instanceof Node ? c : String(c));
  }
  return el;
}
const $ = (s) => document.querySelector(s);
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.hidden = true), 2200);
}
function fmt(s) {
  if (!isFinite(s) || s <= 0) return '0:00';
  s = Math.floor(s);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
function norm(s) {
  return (s || '').toLowerCase().replace(/\(.*?\)|\[.*?\]/g, ' ').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}
function splitArtists(s) {
  return (s || '').split(/\s*(?:,|&| and | x | feat\.? | ft\.? )\s*/i).map((x) => x.trim()).filter((x) => x.length > 1);
}
function shuffle(a) {
  a = a.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

/* ------------------------------------------------------------------ saved state */
const S = (() => {
  const d = { liked: [], playlists: [], history: {}, langs: ['hindi', 'punjabi'], ytKey: DEFAULT_YT_KEY, quality: 'high', seen: [] };
  try { return Object.assign(d, JSON.parse(localStorage.getItem('sangeet') || '{}')); } catch { return d; }
})();
let saveTimer;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => { try { localStorage.setItem('sangeet', JSON.stringify(S)); } catch {} }, 300);
}
const seenSet = new Set(S.seen);
function markSeen(ids) {
  for (const id of ids) if (!seenSet.has(id)) { seenSet.add(id); S.seen.push(id); }
  if (S.seen.length > 20000) S.seen.splice(0, S.seen.length - 20000).forEach((x) => seenSet.delete(x));
  save();
}
function slim(t) { const { _k, ...rest } = t; return rest; }
const isLiked = (t) => S.liked.some((x) => x.id === t.id);
function toggleLike(t) {
  const i = S.liked.findIndex((x) => x.id === t.id);
  if (i >= 0) S.liked.splice(i, 1); else S.liked.unshift(slim(t));
  save();
  refreshLikes();
}
function recordPlay(t) {
  const r = S.history[t.id] || { t: slim(t), c: 0, last: 0 };
  r.c++;
  r.last = Date.now();
  S.history[t.id] = r;
  markSeen([t.id]);
}
const recent = () => Object.values(S.history).sort((a, b) => b.last - a.last).map((r) => r.t);

/* ------------------------------------------------------------------ tracks */
function fromRow(r, lang) {
  return {
    id: 'js:' + r[0], src: 'js', sid: r[0], title: r[1], artist: r[2], album: r[3], dur: r[4],
    img: r[5] ? (r[5].startsWith('http') ? r[5] : IMG + r[5]) : '', media: r[6], hq: !!r[7], year: r[8], lang,
  };
}
const art = (t, big) => (t && t.img ? (big ? t.img.replace(/\d+x\d+(?=\.\w+$)/, '500x500') : t.img) : '');
function streamUrl(t) {
  const kb = S.quality === 'low' ? '96' : S.quality === 'medium' ? '160' : t.hq ? '320' : '160';
  return t.media.startsWith('http') ? t.media.replace(/_(96|160|320)\.mp4/, `_${kb}.mp4`) : `${AAC}${t.media}_${kb}.mp4`;
}

/* ------------------------------------------------------------------ catalog (built daily by GitHub Actions) */
const Catalog = {
  data: {}, tracks: [], byId: new Map(), playlists: [], trackPl: new Map(), total: 0,
  async load() {
    await Deep.init();
    await Promise.all(S.langs.map((l) => this.loadLang(l)));
    this.rebuild();
  },
  async loadLang(l) {
    if (this.data[l]) return this.data[l];
    try {
      const r = await fetch(`data/${l}.json${Deep.v()}`);
      if (!r.ok) return null;
      const d = await r.json();
      const tracks = d.songs.map((x) => fromRow(x, l));
      for (const t of tracks) t._k = norm(`${t.title} ${t.artist} ${t.album}`);
      const pls = d.playlists.map((p) => ({
        id: p[0], title: p[1], subtitle: p[2], img: p[3] ? (p[3].startsWith('http') ? p[3] : IMG + p[3]) : '',
        tracks: p[4].map((i) => tracks[i]).filter(Boolean), chart: !!p[5], lang: l,
      }));
      return (this.data[l] = { tracks, pls });
    } catch { return null; }
  },
  rebuild() {
    this.tracks = []; this.byId = new Map(); this.playlists = []; this.trackPl = new Map();
    for (const l of S.langs) {
      const d = this.data[l];
      if (!d) continue;
      for (const t of d.tracks) if (!this.byId.has(t.id)) { this.byId.set(t.id, t); this.tracks.push(t); }
      this.playlists.push(...d.pls);
    }
    this.playlists.forEach((p, pi) => p.tracks.forEach((t) => {
      let a = this.trackPl.get(t.id);
      if (!a) this.trackPl.set(t.id, (a = []));
      a.push(pi);
    }));
    this.total = this.tracks.length;
    this.loaded = true;
  },
  search(q, limit = 80) {
    const words = norm(q).split(' ').filter(Boolean);
    if (!words.length) return [];
    const out = [];
    for (const t of this.tracks) {
      if (!words.every((w) => t._k.includes(w))) continue;
      const title = norm(t.title);
      let s = title === words.join(' ') ? 4 : title.startsWith(words[0]) ? 2 : 0;
      s += (this.trackPl.get(t.id) || []).length * 0.05;
      out.push([t, s]);
    }
    return out.sort((a, b) => b[1] - a[1]).slice(0, limit).map((x) => x[0]);
  },
  /** Same song from YouTube → the library copy (plays in the background). */
  match(t) {
    const want = norm(t.title);
    if (!want) return null;
    let best = null;
    for (const c of this.tracks) {
      const n = norm(c.title);
      if (n === want) {
        if (splitArtists(t.artist).some((a) => norm(c.artist).includes(norm(a)))) return c;
        best = best || c;
      }
    }
    return best;
  },
};

/* ------------------------------------------------------------------ full catalog (lakhs of songs)
 * Only small files are downloaded: a search index file per 3 letters, and the 250-song files that hold the results. */
const Deep = {
  info: null, files: new Map(),
  async init() {
    try { const r = await fetch('data/index.json', { cache: 'no-cache' }); if (r.ok) this.info = await r.json(); } catch {}
    this.cleanCache();
    return this.info;
  },
  v() { return this.info ? `?b=${this.info.build}` : ''; },
  get(url) {
    if (!this.files.has(url)) this.files.set(url, fetch(url).then((r) => (r.ok ? r.json() : null)).catch(() => null));
    return this.files.get(url);
  },
  key(tok) {
    const k = Array.from(tok).slice(0, 3).join('');
    return /^[a-z0-9]+$/.test(k) ? k : 'x' + (Array.from(k).reduce((n, c) => n + c.codePointAt(0), 0) % 256).toString(16).padStart(2, '0');
  },
  async search(q, limit = 60) {
    if (!this.info) return [];
    const all = norm(q).split(' ').filter((w) => Array.from(w).length >= 2);
    const last = all[all.length - 1];
    const words = [...new Set(all)].sort((a, b) => b.length - a.length).slice(0, 3);
    if (!words.length) return [];
    const sets = await Promise.all(words.map(async (w) => {
      const idx = await this.get(`data/i/${this.key(w)}.json${this.v()}`);
      const nums = new Set();
      if (!idx) return nums;
      const prefix = w === last && Array.from(w).length >= 3;
      for (const tok in idx) {
        if (prefix ? tok.startsWith(w) : tok === w) { let x = 0; for (const d of idx[tok]) { x += d; nums.add(x); } }
      }
      return nums;
    }));
    sets.sort((a, b) => a.size - b.size);
    let hits = [...sets[0]].filter((n) => sets.every((st) => st.has(n)));
    hits = hits.sort((a, b) => a - b).slice(0, limit * 2);
    const per = this.info.rows || 250;
    const shards = [...new Set(hits.map((n) => Math.floor(n / per)))];
    const files = await Promise.all(shards.map((k) => this.get(`data/r/${k}.json${this.v()}`)));
    const byShard = new Map(shards.map((k, i) => [k, files[i]]));
    const want = norm(q);
    const out = [];
    for (const n of hits) {
      const row = byShard.get(Math.floor(n / per))?.[n % per];
      if (!row) continue;
      const t = Catalog.byId.get('js:' + row[0]) || fromRow(row, row[9]);
      out.push([t, norm(t.title) === want ? 2 : norm(t.title).startsWith(want) ? 1 : 0, n]);
    }
    return out.sort((a, b) => b[1] - a[1] || a[2] - b[2]).slice(0, limit).map((x) => x[0]);
  },
  /** Finds one song by title + singer (YouTube songs, imported playlists). */
  async findSong(title, artist) {
    const want = norm(title);
    if (!want) return null;
    const singer = splitArtists(artist)[0] || '';
    for (const q of [`${title} ${singer}`, title]) {
      const list = await this.search(q, 30);
      const same = list.filter((t) => norm(t.title) === want);
      const pick = same.find((t) => singer && norm(t.artist).includes(norm(singer))) || same[0];
      if (pick) return pick;
    }
    return null;
  },
  async cleanCache() {
    if (!this.info || !('caches' in window)) return;
    try {
      const c = await caches.open('sangeet-data');
      for (const req of await c.keys()) {
        const b = new URL(req.url).searchParams.get('b');
        if (b && b !== String(this.info.build)) c.delete(req);
      }
    } catch {}
  },
};

/* ------------------------------------------------------------------ recommendations: fresh songs, never repeats */
function suggestions(count = 25, exclude = new Set()) {
  const artists = new Map();
  const addA = (t, w) => splitArtists(t.artist).forEach((a) => artists.set(norm(a), (artists.get(norm(a)) || 0) + w));
  for (const r of Object.values(S.history)) addA(r.t, r.c * (0.5 + 1 / (1 + (Date.now() - r.last) / 864e5 / 7)));
  S.liked.forEach((t) => addA(t, 3));
  // Songs that share playlists with what you play and like ("people who like this also like").
  const plScore = new Map();
  for (const s of [...S.liked.slice(0, 25), ...recent().slice(0, 40)]) {
    for (const pi of Catalog.trackPl.get(s.id) || []) plScore.set(pi, (plScore.get(pi) || 0) + 1);
  }
  const scored = [];
  for (const t of Catalog.tracks) {
    if (seenSet.has(t.id) || exclude.has(t.id)) continue;
    let a = 0;
    for (const x of splitArtists(t.artist)) a = Math.max(a, artists.get(norm(x)) || 0);
    let p = 0, chart = 0;
    for (const pi of Catalog.trackPl.get(t.id) || []) {
      p += plScore.get(pi) || 0;
      if (Catalog.playlists[pi].chart) chart = 0.8;
    }
    scored.push([t, Math.log1p(a) + Math.log1p(p) * 0.8 + chart + Math.random() * 2.5]);
  }
  scored.sort((x, y) => y[1] - x[1]);
  const top = scored.slice(0, count * 4).map((x) => x[0]);
  const out = [];
  while (top.length && out.length < count) {
    const last = out.length ? norm(out[out.length - 1].artist) : '';
    const i = top.findIndex((t) => norm(t.artist) !== last);
    out.push(top.splice(i < 0 ? 0 : i, 1)[0]);
  }
  return out;
}

/* ------------------------------------------------------------------ YouTube (search + songs that aren't in the library) */
const Tube = {
  async search(q) {
    if (!S.ytKey) return [];
    const u = new URL('https://www.googleapis.com/youtube/v3/search');
    Object.entries({ part: 'snippet', type: 'video', videoCategoryId: '10', videoEmbeddable: 'true', regionCode: 'IN', maxResults: '20', q, key: S.ytKey })
      .forEach(([k, v]) => u.searchParams.set(k, v));
    try {
      const d = await (await fetch(u)).json();
      return (d.items || []).map((o) => this.track(o.id.videoId, o.snippet)).filter(Boolean);
    } catch { return []; }
  },
  track(vid, sn) {
    if (!vid || !sn) return null;
    const raw = new DOMParser().parseFromString(sn.title, 'text/html').documentElement.textContent;
    const ch = (sn.channelTitle || '').replace(' - Topic', '');
    const [artist, title] = splitTitle(raw, ch);
    const th = sn.thumbnails || {};
    return { id: 'yt:' + vid, src: 'yt', sid: vid, title, artist, album: 'YouTube', dur: 0, img: (th.high || th.medium || th.default || {}).url || '', lang: '' };
  },
  /** A public YouTube / YouTube Music playlist: {title, tracks}. */
  async playlist(id) {
    if (!S.ytKey) return null;
    try {
      const meta = await (await fetch(`https://www.googleapis.com/youtube/v3/playlists?part=snippet&id=${encodeURIComponent(id)}&key=${S.ytKey}`)).json();
      const tracks = [];
      let token = '';
      for (let page = 0; page < 10; page++) {
        const u = `https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId=${encodeURIComponent(id)}&key=${S.ytKey}${token ? '&pageToken=' + token : ''}`;
        const d = await (await fetch(u)).json();
        if (d.error) return tracks.length ? { title: meta.items?.[0]?.snippet?.title, tracks } : null;
        for (const it of d.items || []) {
          const sn = it.snippet || {};
          const t = this.track(sn.resourceId?.videoId, { ...sn, channelTitle: sn.videoOwnerChannelTitle || '' });
          if (t && !/^(Deleted|Private) video$/.test(sn.title)) tracks.push(t);
        }
        token = d.nextPageToken;
        if (!token) break;
      }
      return { title: meta.items?.[0]?.snippet?.title || 'YouTube playlist', tracks };
    } catch { return null; }
  },
  player: null,
  ready: null,
  ensure() {
    if (this.ready) return this.ready;
    this.ready = new Promise((resolve) => {
      window.onYouTubeIframeAPIReady = () => {
        this.player = new window.YT.Player('yt', {
          playerVars: { playsinline: 1, autoplay: 1, controls: 1, rel: 0, modestbranding: 1 },
          events: {
            onReady: () => resolve(this.player),
            onStateChange: (e) => Player.onYtState(e.data),
            onError: () => Player.failed(),
          },
        });
      };
      document.head.append(h('script', { src: 'https://www.youtube.com/iframe_api' }));
    });
    return this.ready;
  },
};

/** "Kesariya - Brahmastra | Ranbir | Arijit Singh" → ["Arijit Singh", "Kesariya"] */
function splitTitle(raw, channel) {
  let s = raw.replace(/\s*[([][^)\]]*(official|video|lyric|audio|full song|4k|8k|hd|visualizer)[^)\]]*[)\]]/gi, '').replace(/@\S+/g, '').trim();
  if (s.includes(' | ')) {
    const parts = s.split(' | ').map((x) => x.trim());
    const title = parts[0].split(' - ')[0].trim();
    const singers = ['Arijit', 'Shreya', 'Atif', 'Jubin', 'Neha Kakkar', 'Sonu Nigam', 'Honey Singh', 'Badshah', 'Diljit', 'Karan Aujla', 'Sidhu', 'AP Dhillon', 'Shubh', 'B Praak', 'Darshan Raval', 'Armaan Malik'];
    const singer = parts.slice(1).find((p) => singers.some((x) => p.toLowerCase().includes(x.toLowerCase())));
    return [singer || channel, title || s];
  }
  const i = s.indexOf(' - ');
  if (i > 0 && i < s.length - 3) return [s.slice(0, i).trim(), s.slice(i + 3).trim()];
  return [channel, s];
}

/* ------------------------------------------------------------------ player */
const audio = $('#audio');
const Player = {
  queue: [], i: -1, playing: false, loading: false, mode: 'audio', error: '', fetching: false,
  get current() { return this.queue[this.i]; },
  play(list, i = 0, keep = false) {
    if (!list[i]) return;
    this.queue = keep ? list : list.slice();
    this.load(i);
  },
  playNext(t) {
    if (!this.current) return this.play([t]);
    this.queue.splice(this.i + 1, 0, t);
    toast('Plays next');
  },
  addToQueue(t) {
    if (!this.current) return this.play([t]);
    this.queue.push(t);
    toast('Added to queue');
  },
  async load(i) {
    this.i = i;
    let t = this.current;
    this.error = '';
    this.loading = true;
    if (t.src === 'yt' || t.src === 'q') {
      // Same song in the catalog plays in the background, so prefer it.
      UI.trackChanged();
      const m = Catalog.match(t) || (await Deep.findSong(t.title, t.artist));
      if (this.current !== t) return;
      if (m) t = this.queue[i] = m;
      else if (t.src === 'q') {
        const y = (await Tube.search(`${t.title} ${t.artist}`))[0];
        if (this.current !== t) return;
        if (!y) return this.failed();
        t = this.queue[i] = y;
      }
    }
    if (t.src === 'yt') {
      this.mode = 'yt';
      audio.pause();
      $('#ytbox').hidden = false;
      const p = await Tube.ensure();
      if (this.current !== t) return;
      p.loadVideoById(t.sid);
    } else {
      this.mode = 'audio';
      if (Tube.player && Tube.player.stopVideo) Tube.player.stopVideo();
      $('#ytbox').hidden = true;
      audio.src = streamUrl(t);
      audio.play().catch((e) => {
        this.loading = false;
        if (e.name === 'NotAllowedError') this.playing = false; // iPhone needs a tap first
        UI.update();
      });
    }
    recordPlay(t);
    this.updateSession(t);
    UI.trackChanged();
    if (this.queue.length - this.i <= 3) this.autoplay(false);
  },
  next() {
    if (this.i + 1 < this.queue.length) return this.load(this.i + 1);
    this.autoplay(true); // never repeat on its own: fetch fresh songs
  },
  prev() {
    if (this.time() > 3 || this.i <= 0) return this.seek(0);
    this.load(this.i - 1);
  },
  toggle() {
    if (!this.current) return;
    if (this.playing) this.pause(); else this.resume();
  },
  pause() {
    if (this.mode === 'yt') Tube.player?.pauseVideo(); else audio.pause();
  },
  resume() {
    if (this.mode === 'yt') Tube.player?.playVideo();
    else if (!audio.src) this.load(this.i);
    else audio.play().catch(() => {});
  },
  seek(s) {
    if (this.mode === 'yt') Tube.player?.seekTo(s, true); else audio.currentTime = s;
    UI.tick();
  },
  time() { return this.mode === 'yt' ? (Tube.player?.getCurrentTime?.() || 0) : audio.currentTime || 0; },
  duration() {
    const d = this.mode === 'yt' ? Tube.player?.getDuration?.() : audio.duration;
    return isFinite(d) && d > 0 ? d : this.current?.dur || 0;
  },
  autoplay(thenPlay) {
    if (this.fetching) return;
    this.fetching = true;
    const fresh = suggestions(20, new Set(this.queue.map((t) => t.id)));
    this.fetching = false;
    if (!fresh.length) { if (thenPlay) this.pause(); return; }
    markSeen(fresh.map((t) => t.id));
    this.queue.push(...fresh);
    if (this.queue === Feed.list) Feed.renderMore();
    if (thenPlay) this.load(this.i + 1);
  },
  failed() {
    const t = this.current;
    if (!t) return;
    this.error = `Couldn't play "${t.title}"`;
    this.loading = false;
    UI.update();
    setTimeout(() => { if (this.current === t && this.error) this.next(); }, 1500);
  },
  onYtState(s) {
    if (this.mode !== 'yt') return;
    if (s === 0) return this.next();
    this.playing = s === 1;
    this.loading = s === 3 || s === -1;
    UI.update();
  },
  updateSession(t) {
    if (!('mediaSession' in navigator)) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: t.title, artist: t.artist, album: t.album,
      artwork: t.img ? [{ src: art(t, true), sizes: '500x500', type: 'image/jpeg' }] : [],
    });
  },
};
audio.addEventListener('playing', () => { Player.playing = true; Player.loading = false; UI.update(); });
audio.addEventListener('pause', () => { Player.playing = false; UI.update(); });
audio.addEventListener('waiting', () => { Player.loading = true; UI.update(); });
audio.addEventListener('ended', () => Player.next());
audio.addEventListener('error', () => { if (audio.getAttribute('src') && Player.mode === 'audio') Player.failed(); });
if ('mediaSession' in navigator) {
  const ms = navigator.mediaSession;
  ms.setActionHandler('play', () => Player.resume());
  ms.setActionHandler('pause', () => Player.pause());
  ms.setActionHandler('previoustrack', () => Player.prev());
  ms.setActionHandler('nexttrack', () => Player.next());
  try { ms.setActionHandler('seekto', (d) => Player.seek(d.seekTime)); } catch {}
}

/* ------------------------------------------------------------------ shared UI pieces */
function trackRow(t, onClick) {
  return h('div', { class: 'row' + (Player.current?.id === t.id ? ' playing' : ''), 'data-id': t.id, onclick: onClick },
    h('img', { class: 'art', src: art(t), loading: 'lazy', alt: '' }),
    h('div', { class: 'meta' }, h('div', { class: 't' }, t.title), h('div', { class: 's' }, t.src === 'yt' ? `${t.artist} · YouTube` : t.artist)),
    h('button', { class: 'icon-btn more', 'aria-label': 'More', onclick: (e) => { e.stopPropagation(); trackMenu(t); } }, icon('more')));
}
/** Long lists render 60 rows at a time as you scroll. */
function trackList(tracks) {
  const box = h('div');
  let shown = 0;
  const more = () => {
    const end = Math.min(tracks.length, shown + 60);
    for (; shown < end; shown++) { const i = shown; box.append(trackRow(tracks[i], () => Player.play(tracks, i))); }
    if (shown < tracks.length) box.append(sentinel);
  };
  const sentinel = h('div', { style: 'height:1px' });
  new IntersectionObserver((es) => { if (es[0].isIntersecting) { sentinel.remove(); more(); } }).observe(sentinel);
  more();
  return box;
}
function menu(title, items) {
  const m = $('#menu');
  m.replaceChildren(h('div', { class: 'box', onclick: (e) => e.stopPropagation() },
    h('div', { class: 'title' }, title),
    items.map(([label, fn]) => h('button', { onclick: () => { m.hidden = true; fn(); } }, label))));
  m.onclick = () => (m.hidden = true);
  m.hidden = false;
}
function trackMenu(t) {
  menu(`${t.title} · ${t.artist}`, [
    ['Play next', () => Player.playNext(t)],
    ['Add to queue', () => Player.addToQueue(t)],
    [isLiked(t) ? 'Remove from Liked' : 'Like', () => toggleLike(t)],
    ['Add to playlist', () => playlistPicker(t)],
    ['Share', () => share(t)],
  ]);
}
function playlistPicker(t) {
  menu('Add to playlist', [
    ['+ New playlist', () => {
      const name = prompt('Playlist name', t.title);
      if (name == null) return;
      S.playlists.unshift({ id: String(Date.now()), name: name || 'My playlist', tracks: [slim(t)] });
      save();
      toast('Playlist created');
    }],
    ...S.playlists.map((p) => [p.name, () => {
      if (!p.tracks.some((x) => x.id === t.id)) p.tracks.push(slim(t));
      save();
      toast(`Added to ${p.name}`);
    }]),
  ]);
}
function share(t) {
  const url = t.src === 'yt' ? `https://music.youtube.com/watch?v=${t.sid}` : `https://www.jiosaavn.com/search/song/${encodeURIComponent(t.title + ' ' + t.artist)}`;
  if (navigator.share) navigator.share({ title: t.title, text: `${t.title} · ${t.artist}`, url }).catch(() => {});
  else navigator.clipboard?.writeText(url).then(() => toast('Link copied'));
}
function seekBar() {
  const range = h('input', { type: 'range', min: 0, max: 1000, value: 0 });
  const a = h('span', null, '0:00'), b = h('span', null, '0:00');
  let dragging = false;
  range.addEventListener('input', () => { dragging = true; a.textContent = fmt(range.value / 1000 * Player.duration()); range.style.setProperty('--p', range.value / 10 + '%'); });
  range.addEventListener('change', () => { Player.seek(range.value / 1000 * Player.duration()); dragging = false; });
  const el = h('div', { class: 'seek' }, range, h('div', { class: 'times' }, a, b));
  el.tick = () => {
    if (dragging) return;
    const d = Player.duration(), t = Player.time();
    const v = d ? Math.min(1000, (t / d) * 1000) : 0;
    range.value = v;
    range.style.setProperty('--p', v / 10 + '%');
    a.textContent = fmt(t);
    b.textContent = fmt(d);
  };
  return el;
}
function controls() {
  const big = h('button', { class: 'big', 'aria-label': 'Play', onclick: () => Player.toggle() }, icon('play'));
  const el = h('div', { class: 'controls' },
    h('button', { 'aria-label': 'Previous', onclick: () => Player.prev() }, icon('prev')),
    big,
    h('button', { 'aria-label': 'Next', onclick: () => Player.next() }, icon('next')));
  el.update = (cur = true) => {
    big.replaceChildren(icon(cur && Player.playing ? 'pause' : 'play'));
    big.classList.toggle('loading', cur && Player.loading);
  };
  el.update();
  return el;
}
function likeBtn(t) {
  const b = h('button', { class: 'icon-btn like', 'data-like': t.id, 'aria-label': 'Like', onclick: (e) => { e.stopPropagation(); toggleLike(t); } });
  b.sync = () => { const on = isLiked(t); b.classList.toggle('on', on); b.replaceChildren(icon(on ? 'heartFill' : 'heart')); };
  b.sync();
  return b;
}
function refreshLikes() { document.querySelectorAll('[data-like]').forEach((b) => b.sync && b.sync()); }

/* ------------------------------------------------------------------ For You feed */
const Feed = {
  list: [], el: $('#feed'), rendered: 0, active: 0, started: false, observer: null,
  init() {
    this.observer = new IntersectionObserver((es) => {
      for (const e of es) if (e.isIntersecting) this.onActive(Number(e.target.dataset.i));
    }, { root: this.el, threshold: 0.6 });
    this.reset();
  },
  reset() {
    this.list = suggestions(25);
    markSeen(this.list.map((t) => t.id));
    this.el.replaceChildren();
    this.rendered = 0;
    if (!this.list.length) {
      this.el.append(h('div', { class: 'empty', style: 'padding-top:40vh' }, Catalog.total ? 'You have heard everything here. Reset suggestions in Settings.' : Catalog.loaded ? "Couldn't load songs. Check your connection." : 'Loading songs…'));
      return;
    }
    this.renderMore();
  },
  renderMore() {
    for (; this.rendered < this.list.length; this.rendered++) {
      const page = this.page(this.list[this.rendered], this.rendered);
      this.el.append(page);
      this.observer.observe(page);
    }
  },
  page(t, i) {
    const seek = seekBar();
    const ctr = controls();
    const p = h('div', { class: 'fp', 'data-i': i },
      h('div', { class: 'bg', style: t.img ? `background-image:url("${art(t, true)}")` : '' }),
      h('img', { class: 'cover', src: art(t, true), alt: '', loading: i < 2 ? 'eager' : 'lazy', onclick: () => this.tap(i) }),
      h('div', { class: 'info' },
        h('div', { class: 'meta', onclick: () => trackMenu(t) }, h('div', { class: 'title' }, t.title), h('div', { class: 'artist' }, t.artist)),
        likeBtn(t)),
      seek, ctr);
    ctr.querySelector('.big').onclick = () => this.tap(i);
    p.seek = seek;
    p.ctr = ctr;
    return p;
  },
  tap(i) {
    if (Player.queue === this.list && Player.i === i) return Player.toggle();
    this.started = true;
    Player.play(this.list, i, true);
  },
  onActive(i) {
    this.active = i;
    if (this.started && Player.queue === this.list && Player.i !== i) Player.play(this.list, i, true);
    if (i >= this.list.length - 5 && Player.queue !== this.list) {
      const more = suggestions(20, new Set(this.list.map((t) => t.id)));
      markSeen(more.map((t) => t.id));
      this.list.push(...more);
      this.renderMore();
    } else if (i >= this.list.length - 5) Player.autoplay(false);
  },
  follow() {
    // Song ended on its own: move the feed along with it.
    if (Player.queue !== this.list || Player.i === this.active) return;
    this.el.children[Player.i]?.scrollIntoView({ behavior: 'smooth' });
  },
  activePage() { return Player.queue === this.list ? this.el.children[Player.i] : null; },
};

/* ------------------------------------------------------------------ pages (Search, Library, Settings) */
const Pages = { stack: { search: [], library: [], settings: [] } };
let tab = 'feed';
function header(title, back) {
  return h('div', { class: 'head' },
    back ? h('button', { class: 'icon-btn', 'aria-label': 'Back', onclick: popPage }, icon('back')) : null,
    back ? h('h2', null, title) : h('h1', null, title));
}
function pushPage(render) { Pages.stack[tab].push(render); showPage(); }
function popPage() { Pages.stack[tab].pop(); showPage(); }
function showPage() {
  const st = Pages.stack[tab];
  const page = $('#page');
  page.replaceChildren(st.length ? st[st.length - 1]() : ROOTS[tab]());
  page.scrollTop = 0;
}
const ROOTS = {
  search: searchPage,
  library: libraryPage,
  settings: settingsPage,
};

function searchPage() {
  const results = h('div');
  const input = h('input', { type: 'search', placeholder: 'Songs, artists, movies', autocomplete: 'off', autocapitalize: 'off', spellcheck: false, enterkeyhint: 'search' });
  let ytTimer, seq = 0;
  const run = () => {
    const q = input.value.trim();
    const my = ++seq;
    clearTimeout(ytTimer);
    if (q.length < 2) { results.replaceChildren(); return; }
    const local = Catalog.search(q, 40);
    const deepBox = h('div'), ytBox = h('div');
    results.replaceChildren(local.length ? trackList(local) : h('div', { class: 'spinner' }), deepBox, ytBox);
    const youtube = async () => {
      ytBox.replaceChildren(h('div', { class: 'spinner' }));
      const yt = (await Tube.search(q)).filter((t) => !shown.has(norm(t.title)));
      if (my !== seq) return;
      ytBox.replaceChildren(yt.length ? [h('div', { class: 'section' }, 'From YouTube'), trackList(yt)] : shown.size ? [] : h('div', { class: 'empty' }, 'No songs found'));
    };
    const shown = new Set(local.map((t) => norm(t.title)));
    // Then the full catalog (lakhs of songs); YouTube only for what isn't there.
    ytTimer = setTimeout(async () => {
      const ids = new Set(local.map((t) => t.id));
      const deep = (await Deep.search(q)).filter((t) => !ids.has(t.id));
      if (my !== seq) return;
      if (!local.length) results.firstChild.remove();
      deep.forEach((t) => shown.add(norm(t.title)));
      if (deep.length) deepBox.replaceChildren(local.length ? h('div', { class: 'section' }, 'More songs') : null, trackList(deep));
      if (shown.size < 8) youtube();
      else ytBox.replaceChildren(h('button', { class: 'chip', style: 'margin:12px 16px', onclick: youtube }, 'Search YouTube too'));
    }, 350);
  };
  input.addEventListener('input', () => { clearTimeout(run.t); run.t = setTimeout(run, 150); });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') input.blur(); });
  return h('div', null, header('Search'), h('div', { class: 'search-box' }, input), results);
}

function libraryPage() {
  const link = (ic, label, count, fn) => h('div', { class: 'link-row', onclick: fn }, icon(ic), label, count != null ? h('span', { class: 'count' }, count) : null);
  const charts = Catalog.playlists.filter((p) => p.chart);
  return h('div', null,
    header('Library'),
    link('sparkles', 'AI DJ', null, () => pushPage(djPage)),
    link('heartFill', 'Liked Songs', S.liked.length, () => pushPage(() => songsPage('Liked Songs', S.liked))),
    link('clock', 'Recently Played', null, () => pushPage(() => songsPage('Recently Played', recent().slice(0, 300)))),
    link('globe', 'Online Library', Catalog.playlists.length || null, () => pushPage(onlineLibraryPage)),
    link('plus', 'Import playlist', null, () => pushPage(importPage)),
    h('div', { class: 'section' }, 'Your playlists'),
    S.playlists.map((p) => link('list', p.name, p.tracks.length, () => pushPage(() => songsPage(p.name, p.tracks, p)))),
    link('plus', 'New playlist', null, () => {
      const name = prompt('Playlist name');
      if (!name) return;
      S.playlists.unshift({ id: String(Date.now()), name, tracks: [] });
      save();
      showPage();
    }),
    charts.length ? h('div', { class: 'section' }, 'Top charts') : null,
    charts.length ? h('div', { class: 'grid' }, charts.slice(0, 12).map(playlistCard)) : null);
}
function playlistCard(p) {
  return h('div', { class: 'card', onclick: () => pushPage(() => songsPage(p.title, p.tracks, null, p)) },
    h('img', { class: 'cover', src: (p.img || '').replace('150x150', '500x500'), loading: 'lazy', alt: '' }),
    h('div', { class: 't' }, p.title));
}
function songsPage(title, tracks, mine, online) {
  const actions = tracks.length ? h('div', { class: 'actions' },
    h('button', { class: 'pill primary', onclick: () => Player.play(tracks) }, icon('play'), 'Play'),
    h('button', { class: 'pill', onclick: () => Player.play(shuffle(tracks)) }, icon('shuffle'), 'Shuffle'),
    online ? h('button', { class: 'pill', onclick: () => { S.playlists.unshift({ id: String(Date.now()), name: title, tracks: tracks.map(slim) }); save(); toast('Saved to your playlists'); } }, icon('plus'), 'Save') : null,
    mine ? h('button', { class: 'pill', onclick: () => { if (confirm(`Delete "${mine.name}"?`)) { S.playlists = S.playlists.filter((x) => x !== mine); save(); popPage(); } } }, 'Delete') : null) : null;
  return h('div', null, header(title, true), actions, tracks.length ? trackList(tracks) : h('div', { class: 'empty' }, 'No songs yet'));
}
function onlineLibraryPage() {
  const grid = h('div', { class: 'grid' });
  const input = h('input', { type: 'search', placeholder: 'Find a playlist', autocomplete: 'off' });
  const sentinel = h('div', { style: 'height:1px' });
  let list = Catalog.playlists, shown = 0;
  const more = () => {
    const end = Math.min(list.length, shown + 40);
    for (; shown < end; shown++) grid.append(playlistCard(list[shown]));
  };
  new IntersectionObserver((es) => { if (es[0].isIntersecting) more(); }).observe(sentinel);
  input.addEventListener('input', () => {
    const q = norm(input.value);
    list = q ? Catalog.playlists.filter((p) => norm(p.title + ' ' + p.subtitle).includes(q)) : Catalog.playlists;
    grid.replaceChildren();
    shown = 0;
    more();
  });
  more();
  return h('div', null, header('Online Library', true), h('div', { class: 'search-box' }, input), grid, sentinel);
}

/* ------------------------------------------------------------------ import a playlist (YouTube link, CSV file or a list) */
function parseCsv(text) {
  const rows = [];
  let row = [], cell = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) {
      if (c === '"' && text[i + 1] === '"') { cell += '"'; i++; } else if (c === '"') q = false; else cell += c;
    } else if (c === '"') q = true;
    else if (c === ',') { row.push(cell); cell = ''; }
    else if (c === '\n' || c === '\r') { if (c === '\r' && text[i + 1] === '\n') i++; row.push(cell); rows.push(row); row = []; cell = ''; }
    else cell += c;
  }
  if (cell || row.length) { row.push(cell); rows.push(row); }
  return rows.filter((r) => r.some((x) => x.trim()));
}
/** [{title, artist}] from a CSV export (Exportify, TuneMyMusic, ...) or lines like "Song - Singer". */
function parseSongList(text) {
  const rows = parseCsv(text);
  const head = rows[0] ? rows[0].map((x) => x.toLowerCase()) : [];
  const ti = head.findIndex((x) => /track name|^title$|song|^name$|track title/.test(x));
  const ai = head.findIndex((x) => /artist/.test(x));
  if (ti >= 0 && rows.length > 1 && rows[0].length > 1) {
    return rows.slice(1).map((r) => ({ title: (r[ti] || '').trim(), artist: ai >= 0 ? (r[ai] || '').split(/[,;]/)[0].trim() : '' })).filter((x) => x.title);
  }
  return text.split(/\r?\n/).map((l) => l.replace(/^\s*\d+[.)]\s*/, '').trim()).filter(Boolean).map((l) => {
    const by = l.match(/^(.*?)\s+by\s+(.*)$/i);
    if (by) return { title: by[1], artist: by[2] };
    const [title, ...rest] = l.split(/\s+[-–—|]\s+/);
    return { title: title.trim(), artist: rest.join(' ').trim() };
  });
}
function importPage() {
  const status = h('div', { class: 'note' });
  const box = h('textarea', { rows: 6, placeholder: 'Paste a YouTube or YouTube Music playlist link, or a list of songs (one per line: Song - Singer)', style: 'width:100%;font:inherit;color:inherit;background:var(--surface);border:1px solid var(--line);border-radius:10px;padding:10px 12px' });
  const file = h('input', { type: 'file', accept: '.csv,.txt,text/csv,text/plain', style: 'display:none' });
  const go = async (text, fallbackName) => {
    text = text.trim();
    if (!text) return;
    if (/spotify\.com|spotify:/.test(text)) {
      status.textContent = "A browser can't open Spotify links. On exportify.net export the playlist as CSV, then choose the file here.";
      return;
    }
    let name = fallbackName, items, tracks = [];
    const list = text.match(/[?&]list=([\w-]+)/);
    status.replaceChildren(h('div', { class: 'spinner' }));
    if (list) {
      const r = await Tube.playlist(list[1]);
      if (!r) { status.textContent = "Couldn't open that playlist. Is it public?"; return; }
      name = r.title;
      tracks = r.tracks;
    } else {
      items = parseSongList(text).slice(0, 500);
      let done = 0;
      const out = new Array(items.length);
      const work = async (k) => {
        const it = items[k];
        out[k] = (await Deep.findSong(it.title, it.artist)) ||
          { id: 'q:' + norm(it.title + ' ' + it.artist), src: 'q', sid: '', title: it.title, artist: it.artist || 'Unknown', album: '', dur: 0, img: '', lang: '' };
        status.textContent = `Matching songs… ${++done}/${items.length}`;
      };
      let next = 0;
      await Promise.all(Array.from({ length: 6 }, async () => { while (next < items.length) await work(next++); }));
      tracks = out;
    }
    if (!tracks.length) { status.textContent = 'No songs found in that.'; return; }
    name = prompt('Playlist name', name || 'Imported playlist') || name || 'Imported playlist';
    S.playlists.unshift({ id: String(Date.now()), name, tracks: tracks.map(slim) });
    save();
    const found = tracks.filter((t) => t.src !== 'q').length;
    toast(`Imported ${tracks.length} songs`);
    status.textContent = items ? `${found} of ${tracks.length} found in the library, the rest will play from YouTube.` : '';
    pushPage(() => songsPage(name, S.playlists[0].tracks, S.playlists[0]));
  };
  file.addEventListener('change', async () => {
    const f = file.files[0];
    if (f) go(await f.text(), f.name.replace(/\.\w+$/, ''));
  });
  return h('div', null, header('Import playlist', true),
    h('div', { style: 'padding:8px 16px' }, box),
    h('div', { class: 'actions' },
      h('button', { class: 'pill primary', onclick: () => go(box.value) }, 'Import'),
      h('button', { class: 'pill', onclick: () => file.click() }, 'Choose CSV file')),
    file, status,
    h('div', { class: 'note' }, 'Spotify: export the playlist as CSV on exportify.net, then choose the file. YouTube and YouTube Music: paste the playlist link.'));
}

/* ------------------------------------------------------------------ AI DJ: "sad punjabi songs for a night drive" */
const MOODS = [
  [['sad', 'dukh', 'udaas', 'breakup', 'heartbreak', 'dard', 'emotional'], ['sad', 'heartbreak', 'broken', 'dard', 'emotional', 'judaai']],
  [['happy', 'khush', 'cheerful', 'good mood'], ['happy', 'feel good', 'good vibes']],
  [['party', 'dance', 'club', 'naach', 'dj'], ['party', 'dance', 'club', 'dj', 'bhangra']],
  [['romantic', 'love', 'pyaar', 'ishq', 'date'], ['romantic', 'love', 'romance', 'ishq', 'pyaar']],
  [['chill', 'lofi', 'relax', 'calm', 'sukoon', 'study'], ['lofi', 'chill', 'relax', 'calm', 'acoustic', 'unplugged']],
  [['sleep', 'neend', 'night', 'raat'], ['night', 'sleep', 'soft', 'calm']],
  [['gym', 'workout', 'running', 'exercise'], ['workout', 'gym', 'power', 'motivation']],
  [['bhakti', 'bhajan', 'devotional', 'god', 'mandir', 'aarti'], ['bhakti', 'devotional', 'bhajan', 'aarti', 'spiritual']],
  [['drive', 'road trip', 'travel', 'safar'], ['drive', 'road', 'travel', 'trip']],
  [['rain', 'barish', 'baarish', 'monsoon'], ['rain', 'monsoon', 'barish', 'baarish']],
  [['wedding', 'shaadi', 'sangeet', 'mehendi', 'baraat'], ['wedding', 'shaadi', 'sangeet', 'mehendi', 'baraat']],
  [['rap', 'hip hop', 'hiphop'], ['rap', 'hip hop', 'hip-hop']],
];
async function aiDj(text) {
  const q = ' ' + norm(text) + ' ';
  let langs = ALL_LANGS.filter((l) => q.includes(' ' + l + ' '));
  if (!langs.length) langs = S.langs;
  const datas = (await Promise.all(langs.map((l) => Catalog.loadLang(l)))).filter(Boolean);
  const tracks = datas.flatMap((d) => d.tracks);
  const pls = datas.flatMap((d) => d.pls);
  const mood = MOODS.find(([keys]) => keys.some((k) => q.includes(' ' + k + ' ') || q.includes(' ' + k)));
  const year = new Date().getFullYear();
  const era = /\b(90s|nineties|purane|old|retro|classic)\b/.test(q) ? 'old' : /\b(new|naye|latest|20\d\d)\b/.test(q) ? 'new' : '';
  // Singers named in the request (matched against the library's own artists).
  const artistSet = new Set();
  for (const t of tracks) for (const a of splitArtists(t.artist)) { const n = norm(a); if (n.length > 3 && q.includes(' ' + n + ' ')) artistSet.add(n); }
  const moodTracks = new Set();
  if (mood) for (const p of pls) { const n = ' ' + norm(p.title) + ' '; if (mood[1].some((k) => n.includes(k))) p.tracks.forEach((t) => moodTracks.add(t.id)); }
  const stop = new Set(['songs', 'song', 'for', 'a', 'the', 'and', 'with', 'of', 'me', 'my', 'music', 'gaane', 'gane', 'play', 'some', 'best', 'hits', ...langs]);
  const words = norm(text).split(' ').filter((w) => w.length > 2 && !stop.has(w));
  const scored = [];
  for (const t of tracks) {
    let s = 0;
    if (artistSet.size) { if (splitArtists(t.artist).some((a) => artistSet.has(norm(a)))) s += 5; else continue; }
    if (mood) s += moodTracks.has(t.id) ? 4 : 0;
    if (era === 'old') { if (t.year && t.year <= 2005) s += 3; else if (t.year > 2012) continue; }
    if (era === 'new') { if (t.year >= year - 2) s += 3; else if (t.year && t.year < year - 5) continue; }
    for (const w of words) if (t._k.includes(w)) s += 1;
    if (s <= 0 && (mood || words.length)) continue;
    scored.push([t, s + Math.random() * 2]);
  }
  scored.sort((a, b) => b[1] - a[1]);
  const title = text.trim().replace(/^./, (c) => c.toUpperCase()).slice(0, 40);
  return { title, tracks: scored.slice(0, 60).map((x) => x[0]) };
}
function djPage() {
  const out = h('div');
  const input = h('input', { type: 'text', placeholder: 'What do you want to hear?', enterkeyhint: 'go' });
  const run = async (text) => {
    if (!text.trim()) return;
    input.value = text;
    input.blur();
    out.replaceChildren(h('div', { class: 'spinner' }));
    const r = await aiDj(text);
    if (!r.tracks.length) return out.replaceChildren(h('div', { class: 'empty' }, 'Nothing found. Try different words.'));
    out.replaceChildren(
      h('div', { class: 'section' }, r.title),
      h('div', { class: 'actions' },
        h('button', { class: 'pill primary', onclick: () => Player.play(r.tracks) }, icon('play'), 'Play'),
        h('button', { class: 'pill', onclick: () => { S.playlists.unshift({ id: String(Date.now()), name: r.title, tracks: r.tracks.map(slim) }); save(); toast('Saved to your playlists'); } }, icon('plus'), 'Save')),
      trackList(r.tracks));
  };
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') run(input.value); });
  const examples = ['Sad Punjabi songs for a night drive', '90s Bollywood romantic', 'Arijit Singh latest', 'Gym workout Hindi', 'Rainy day chill'];
  return h('div', null, header('AI DJ', true), h('div', { class: 'search-box' }, input),
    h('div', { class: 'chips' }, examples.map((e) => h('button', { class: 'chip', onclick: () => run(e) }, e))), out);
}

function settingsPage() {
  const langChips = h('div', { class: 'chips' }, ALL_LANGS.map((l) => {
    const b = h('button', { class: 'chip' + (S.langs.includes(l) ? ' on' : '') }, l[0].toUpperCase() + l.slice(1));
    b.onclick = async () => {
      if (S.langs.includes(l)) { if (S.langs.length === 1) return; S.langs = S.langs.filter((x) => x !== l); }
      else S.langs.push(l);
      b.classList.toggle('on');
      save();
      await Catalog.load();
      Feed.reset();
      count.textContent = `${Catalog.total.toLocaleString()} songs in your languages`;
    };
    return b;
  }));
  const count = h('div', { class: 'note' }, `${Catalog.total.toLocaleString()} songs in your languages`);
  const quality = h('select', { onchange: (e) => { S.quality = e.target.value; save(); } },
    [['low', '96 kbps'], ['medium', '160 kbps'], ['high', '320 kbps']].map(([v, l]) => h('option', { value: v, selected: S.quality === v }, l)));
  const key = h('input', { type: 'text', value: S.ytKey, autocapitalize: 'off', spellcheck: false, style: 'font-family:ui-monospace,monospace;font-size:13px' });
  key.addEventListener('change', () => { S.ytKey = key.value.trim(); save(); });
  const standalone = navigator.standalone || matchMedia('(display-mode: standalone)').matches;
  return h('div', null,
    header('Settings'),
    h('div', { class: 'section' }, 'Languages'), langChips, count,
    h('div', { class: 'section' }, 'Audio'),
    h('div', { class: 'setting' }, h('label', null, 'Streaming quality'), quality),
    h('div', { class: 'section' }, 'YouTube Data API key'),
    h('div', { class: 'setting' }, key),
    h('div', { class: 'note' }, 'Used for search results. Leave empty to turn off.'),
    h('div', { class: 'section' }, 'Suggestions'),
    h('button', { class: 'danger', onclick: () => { S.seen = []; seenSet.clear(); save(); Feed.reset(); toast('Done'); } }, 'Reset suggestions'),
    h('div', { class: 'note' }, "Songs you've heard are never suggested twice."),
    standalone ? null : h('div', null, h('div', { class: 'section' }, 'Install on iPhone'),
      h('div', { class: 'note' }, 'In Safari tap Share, then "Add to Home Screen". Sangeet opens full screen like an app.')),
    h('div', { class: 'note' }, `Version ${window.SANGEET_BUILD}`));
}

/* ------------------------------------------------------------------ mini player + now playing */
const UI = {
  np: null,
  update() {
    const t = Player.current;
    const mini = $('#mini');
    mini.hidden = !t || tab === 'feed';
    document.body.classList.toggle('has-mini', !mini.hidden);
    if (t) this.renderMini(t);
    const ap = Feed.activePage();
    for (const p of Feed.el.children) p.ctr?.update(p === ap);
    this.np?.ctr.update();
    if (this.np) this.np.err.textContent = Player.error;
  },
  trackChanged() {
    document.querySelectorAll('.row.playing').forEach((r) => r.classList.remove('playing'));
    document.querySelectorAll(`.row[data-id="${CSS.escape(Player.current?.id || '')}"]`).forEach((r) => r.classList.add('playing'));
    Feed.follow();
    if (this.np) this.openNowPlaying(true);
    this.update();
  },
  renderMini(t) {
    const mini = $('#mini');
    if (mini.dataset.id !== t.id) {
      mini.dataset.id = t.id;
      mini.replaceChildren(
        h('div', { class: 'bar' }),
        h('img', { class: 'art', src: art(t), alt: '' }),
        h('div', { class: 'meta' }, h('div', { class: 't' }, t.title), h('div', { class: 's' }, t.artist)),
        h('button', { class: 'icon-btn toggle', 'aria-label': 'Play', onclick: (e) => { e.stopPropagation(); Player.toggle(); } }),
        h('button', { class: 'icon-btn', 'aria-label': 'Next', onclick: (e) => { e.stopPropagation(); Player.next(); } }, icon('next')));
      mini.onclick = () => this.openNowPlaying();
    }
    mini.querySelector('.toggle').replaceChildren(icon(Player.playing ? 'pause' : 'play'));
  },
  tick() {
    const d = Player.duration(), p = d ? (Player.time() / d) * 100 : 0;
    const bar = $('#mini .bar');
    if (bar) bar.style.width = p + '%';
    Feed.activePage()?.seek.tick();
    if (UI.np) { UI.np.seek.tick(); UI.np.lyricsTick?.(); }
    if ('mediaSession' in navigator && navigator.mediaSession.setPositionState && d && Player.mode === 'audio') {
      try { navigator.mediaSession.setPositionState({ duration: d, position: Math.min(Player.time(), d), playbackRate: 1 }); } catch {}
    }
  },
  openNowPlaying(refresh) {
    const t = Player.current;
    if (!t) return;
    const np = $('#np');
    const view = refresh && this.np ? this.np.view : 'art';
    const body = h('div', { class: 'np-body' });
    const seek = seekBar(), ctr = controls(), err = h('div', { class: 'err' });
    const lyricsBtn = h('button', { class: 'icon-btn', 'aria-label': 'Lyrics' }, icon('lyrics'));
    const queueBtn = h('button', { class: 'icon-btn', 'aria-label': 'Up next' }, icon('queue'));
    np.replaceChildren(
      h('div', { class: 'bg', style: t.img ? `background-image:url("${art(t, true)}")` : '' }),
      h('div', { class: 'np-top' },
        h('button', { class: 'icon-btn', 'aria-label': 'Close', onclick: () => this.closeNowPlaying() }, icon('down')),
        h('button', { class: 'icon-btn', 'aria-label': 'More', onclick: () => trackMenu(t) }, icon('more'))),
      body,
      h('div', { class: 'np-bottom' },
        h('div', { class: 'info', style: 'display:flex;align-items:center;gap:12px' },
          h('div', { class: 'meta', style: 'flex:1;min-width:0' },
            h('div', { style: 'font-size:22px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis' }, t.title),
            h('div', { style: 'color:rgba(255,255,255,.7);white-space:nowrap;overflow:hidden;text-overflow:ellipsis' }, t.artist)),
          likeBtn(t)),
        seek, ctr, err,
        h('div', { class: 'row-icons' }, lyricsBtn, queueBtn)));
    const state = { seek, ctr, err, view, lyricsTick: null };
    const show = (v) => {
      state.view = v;
      state.lyricsTick = null;
      lyricsBtn.classList.toggle('on', v === 'lyrics');
      queueBtn.classList.toggle('on', v === 'queue');
      if (v === 'lyrics') return showLyrics(t, body, state);
      if (v === 'queue') {
        const up = Player.queue.slice(Player.i);
        return body.replaceChildren(h('div', { class: 'queue' }, up.map((x, k) => trackRow(x, () => Player.load(Player.i + k)))));
      }
      body.replaceChildren(Player.mode === 'yt' ? h('div', { style: 'width:100%;aspect-ratio:16/9' }) : h('img', { class: 'cover', src: art(t, true), alt: '' }));
    };
    lyricsBtn.onclick = () => show(state.view === 'lyrics' ? 'art' : 'lyrics');
    queueBtn.onclick = () => show(state.view === 'queue' ? 'art' : 'queue');
    this.np = state;
    show(view);
    np.hidden = false;
    document.body.classList.add('np-open');
    this.update();
    this.tick();
  },
  closeNowPlaying() {
    $('#np').hidden = true;
    document.body.classList.remove('np-open');
    this.np = null;
  },
};
setInterval(() => UI.tick(), 500);

/* ------------------------------------------------------------------ synced lyrics (LRCLIB, free) */
const lyricsCache = new Map();
async function getLyrics(t) {
  if (lyricsCache.has(t.id)) return lyricsCache.get(t.id);
  const artist = splitArtists(t.artist)[0] || t.artist;
  const q = new URLSearchParams({ track_name: t.title, artist_name: artist });
  if (t.dur) q.set('duration', t.dur);
  let lrc = null;
  try { const r = await fetch('https://lrclib.net/api/get?' + q); if (r.ok) lrc = (await r.json()).syncedLyrics; } catch {}
  if (!lrc) {
    try {
      const r = await fetch('https://lrclib.net/api/search?' + new URLSearchParams({ track_name: t.title, artist_name: artist }));
      if (r.ok) lrc = ((await r.json()).find((x) => x.syncedLyrics) || {}).syncedLyrics;
    } catch {}
  }
  const lines = [];
  for (const raw of (lrc || '').split('\n')) {
    const tags = [...raw.matchAll(/\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g)];
    if (!tags.length) continue;
    const text = raw.replace(/\[[^\]]*\]/g, '').trim();
    for (const m of tags) lines.push({ time: +m[1] * 60 + +m[2] + (m[3] ? +m[3] / 10 ** m[3].length : 0), text });
  }
  lines.sort((a, b) => a.time - b.time);
  lyricsCache.set(t.id, lines);
  return lines;
}
async function showLyrics(t, body, state) {
  body.replaceChildren(h('div', { class: 'spinner' }));
  const lines = await getLyrics(t);
  if (state.view !== 'lyrics' || Player.current?.id !== t.id) return;
  if (!lines.length) return body.replaceChildren(h('div', { class: 'empty' }, 'No lyrics found'));
  const box = h('div', { class: 'lyrics' }, lines.map((l) => h('p', { onclick: () => Player.seek(l.time) }, l.text || '♪')));
  body.replaceChildren(box);
  let last = -1;
  state.lyricsTick = () => {
    const now = Player.time() + 0.3;
    let i = -1;
    for (let k = 0; k < lines.length && lines[k].time <= now; k++) i = k;
    if (i === last) return;
    box.children[last]?.classList.remove('on');
    last = i;
    const el = box.children[i];
    if (el) { el.classList.add('on'); el.scrollIntoView({ block: 'center', behavior: 'smooth' }); }
  };
  state.lyricsTick();
}

/* ------------------------------------------------------------------ tabs + start */
const TAB_INFO = { feed: ['feed', 'For You'], search: ['search', 'Search'], library: ['library', 'Library'], settings: ['settings', 'Settings'] };
document.querySelectorAll('#tabs button').forEach((b) => {
  const [ic, label] = TAB_INFO[b.dataset.tab];
  b.append(icon(ic), label);
  b.onclick = () => {
    const t = b.dataset.tab;
    if (t === tab && t !== 'feed') { Pages.stack[t] = []; showPage(); return; }
    tab = t;
    document.querySelectorAll('#tabs button').forEach((x) => x.classList.toggle('on', x === b));
    $('#feed').hidden = t !== 'feed';
    $('#page').hidden = t === 'feed';
    if (t !== 'feed') showPage();
    UI.update();
  };
});

(async () => {
  Feed.init();
  await Catalog.load();
  Feed.reset();
  UI.update();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
