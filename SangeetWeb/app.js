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
  download: 'M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z',
  car: 'M18.92 6.01C18.72 5.42 18.16 5 17.5 5h-11c-.66 0-1.21.42-1.42 1.01L3 12v8c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-1h12v1c0 .55.45 1 1 1h1c.55 0 1-.45 1-1v-8l-2.08-5.99zM6.5 16c-.83 0-1.5-.67-1.5-1.5S5.67 13 6.5 13s1.5.67 1.5 1.5S7.33 16 6.5 16zm11 0c-.83 0-1.5-.67-1.5-1.5s.67-1.5 1.5-1.5 1.5.67 1.5 1.5-.67 1.5-1.5 1.5zM5 11l1.5-4.5h11L19 11H5z',
  repeat: 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z',
  repeatOne: 'M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z',
  speed: 'M20.38 8.57l-1.23 1.85a8 8 0 01-.22 7.58H5.07A8 8 0 0115.58 6.85l1.85-1.23A10 10 0 003.35 19a2 2 0 001.72 1h13.85a2 2 0 001.74-1 10 10 0 00-.27-10.44zm-9.79 6.84a2 2 0 002.83 0l5.66-8.49-8.49 5.66a2 2 0 000 2.83z',
  moon: 'M12.34 2.02C6.59 1.82 2 6.42 2 12c0 5.52 4.48 10 10 10 3.71 0 6.93-2.02 8.66-5.02-7.51-.25-12.09-8.43-8.32-14.96z',
  files: 'M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z',
  close: 'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  mic: 'M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z',
  clock: 'M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z',
  radio: 'M3.24 6.15C2.51 6.43 2 7.17 2 8v12c0 1.1.89 2 2 2h16c1.11 0 2-.9 2-2V8c0-1.11-.89-2-2-2H8.3l8.26-3.34L15.88 1 3.24 6.15zM7 20c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm13-8h-2v-2h-2v2H4V8h16v4z',
  people: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  film: 'M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z',
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
/** replaceChildren that, like h(), accepts lists and skips empty parts (a plain array would show as text). */
function fill(el, ...kids) {
  el.replaceChildren(...kids.flat(Infinity).filter((c) => c != null && c !== false));
}
function toast(msg, ms = 2200) {
  const t = $('#toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.hidden = true), ms);
}
function fmt(s) {
  if (!isFinite(s) || s <= 0) return '0:00';
  s = Math.floor(s);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}
function norm(s) {
  return (s || '').toLowerCase().replace(/\(.*?\)|\[.*?\]/g, ' ').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
}
/* Hindi (Devanagari) and Punjabi (Gurmukhi) typed or spoken in their own script, written the way the catalog
 * spells songs: "तुम ही हो" -> "tum hi ho", "ਕਿੰਨੀ ਸੋਹਣੀ" -> "kinni sohni". Two spellings, since names keep long vowels
 * or not ("raabta" / "rabta"); the same rules as Android's Transliterate. */
const Translit = {
  RE: /[ऀ-ॿ਀-੿]/,
  has(s) { return this.RE.test(s || ''); },
  C: {
    'क': 'k', 'ख': 'kh', 'ग': 'g', 'घ': 'gh', 'ङ': 'n', 'च': 'ch', 'छ': 'chh', 'ज': 'j', 'झ': 'jh', 'ञ': 'n', 'ट': 't', 'ठ': 'th',
    'ड': 'd', 'ढ': 'dh', 'ण': 'n', 'त': 't', 'थ': 'th', 'द': 'd', 'ध': 'dh', 'न': 'n', 'प': 'p', 'फ': 'ph', 'ब': 'b', 'भ': 'bh',
    'म': 'm', 'य': 'y', 'र': 'r', 'ल': 'l', 'व': 'v', 'श': 'sh', 'ष': 'sh', 'स': 's', 'ह': 'h', 'ळ': 'l',
    'क़': 'q', 'ख़': 'kh', 'ग़': 'g', 'ज़': 'z', 'ड़': 'd', 'ढ़': 'dh', 'फ़': 'f', 'य़': 'y',
    'ਕ': 'k', 'ਖ': 'kh', 'ਗ': 'g', 'ਘ': 'gh', 'ਙ': 'n', 'ਚ': 'ch', 'ਛ': 'chh', 'ਜ': 'j', 'ਝ': 'jh', 'ਞ': 'n', 'ਟ': 't', 'ਠ': 'th',
    'ਡ': 'd', 'ਢ': 'dh', 'ਣ': 'n', 'ਤ': 't', 'ਥ': 'th', 'ਦ': 'd', 'ਧ': 'dh', 'ਨ': 'n', 'ਪ': 'p', 'ਫ': 'ph', 'ਬ': 'b', 'ਭ': 'bh',
    'ਮ': 'm', 'ਯ': 'y', 'ਰ': 'r', 'ਲ': 'l', 'ਵ': 'v', 'ਸ': 's', 'ਹ': 'h', 'ੜ': 'd', 'ਸ਼': 'sh', 'ਖ਼': 'kh', 'ਗ਼': 'g', 'ਜ਼': 'z',
    'ਫ਼': 'f', 'ਲ਼': 'l',
  },
  NUKTA: { 'क': 'क़', 'ख': 'ख़', 'ग': 'ग़', 'ज': 'ज़', 'ड': 'ड़', 'ढ': 'ढ़', 'फ': 'फ़',
    'ਸ': 'ਸ਼', 'ਖ': 'ਖ਼', 'ਗ': 'ਗ਼', 'ਜ': 'ਜ਼', 'ਫ': 'ਫ਼', 'ਲ': 'ਲ਼' },
  V: { 'अ': 'a', 'आ': 'A', 'इ': 'i', 'ई': 'I', 'उ': 'u', 'ऊ': 'U', 'ऋ': 'ri', 'ए': 'e', 'ऐ': 'ai', 'ओ': 'o', 'औ': 'au', 'ऑ': 'o',
    'ਅ': 'a', 'ਆ': 'A', 'ਇ': 'i', 'ਈ': 'I', 'ਉ': 'u', 'ਊ': 'U', 'ਏ': 'e', 'ਐ': 'ai', 'ਓ': 'o', 'ਔ': 'au' },
  M: { 'ा': 'A', 'ि': 'i', 'ी': 'I', 'ु': 'u', 'ू': 'U', 'ृ': 'ri', 'े': 'e', 'ै': 'ai', 'ो': 'o', 'ौ': 'au', 'ॉ': 'o', 'ॅ': 'e',
    'ਾ': 'A', 'ਿ': 'i', 'ੀ': 'I', 'ੁ': 'u', 'ੂ': 'U', 'ੇ': 'e', 'ੈ': 'ai', 'ੋ': 'o', 'ੌ': 'au' },
  /** One word as letters: {c: consonant, v: vowel ('' = none), inh: the vowel is the unwritten a, n: nasal after}. */
  units(w) {
    const out = [];
    let double = false;
    const chars = Array.from(w);
    for (let i = 0; i < chars.length; i++) {
      let ch = chars[i];
      if ((chars[i + 1] === '़' || chars[i + 1] === '਼') && this.NUKTA[ch]) { ch = this.NUKTA[ch]; i++; }
      const c = this.C[ch];
      if (c) {
        const u = { c: double && c !== 'ch' ? c[0] + c : c, v: 'a', inh: true, n: '' };
        double = false;
        const next = chars[i + 1];
        if (next && this.M[next]) { u.v = this.M[next]; u.inh = false; i++; } else if (next === '्' || next === '੍') { u.v = ''; u.inh = false; i++; }
        out.push(u);
      } else if (this.V[ch]) out.push({ c: '', v: this.V[ch], inh: false, n: '' });
      else if ('ंँਂੰ'.includes(ch)) { if (out.length) out[out.length - 1].n = 'n'; else out.push({ c: 'n', v: '', inh: false, n: '' }); }
      else if (ch === 'ः') out.push({ c: 'h', v: '', inh: false, n: '' });
      else if (ch === 'ੱ') double = true; // addak: the next letter is said twice (ਜੱਟ = jatt)
      else if (/[०-९]/.test(ch)) out.push({ c: String(ch.charCodeAt(0) - 0x966), v: '', inh: false, n: '' });
      else if (/[੦-੯]/.test(ch)) out.push({ c: String(ch.charCodeAt(0) - 0xA66), v: '', inh: false, n: '' });
      else if (!this.RE.test(ch)) out.push({ c: ch, v: '', inh: false, n: '' });
    }
    return out;
  },
  /** Letters as Latin. long: aa/ee/oo for long vowels (not at the end); drop: the unsaid a inside a word too
   * (sohani -> sohni, dhadakan -> dhadkan). The a at a word's end is never said (tum, not tuma). */
  render(us, long, drop) {
    const has = (u) => u && u.v !== '';
    return us.map((u, i) => {
      let v = u.v;
      const last = i === us.length - 1;
      if (u.inh && us.length > 1 && last && !u.n) v = '';
      else if (u.inh && drop && i > 0 && !last && !u.n && has(us[i - 1]) && us[i + 1].c && has(us[i + 1]) && !(us[i + 1].inh && i + 1 === us.length - 1 && !us[i + 1].n)) v = '';
      if (long && !last) v = v.replace('A', 'aa').replace('I', 'ee').replace('U', 'oo');
      return u.c + v.toLowerCase() + u.n;
    }).join('');
  },
  /** ["tum hi ho", …]: the usual spelling first, then with long vowels, then without the unsaid a's. */
  variants(text) {
    const words = String(text || '').split(/\s+/).filter(Boolean).map((w) => this.units(w));
    const as = (long, drop) => words.map((us) => this.render(us, long, drop)).join(' ').trim();
    return [...new Set([as(false, false), as(true, false), as(false, true)])].filter(Boolean);
  },
};
/* Matching a YouTube video to the same song in the catalog (which plays in the background on iPhone).
 * YouTube names carry extras ("Official Video", "Full Song", "| Movie | Actor", hashtags, years). Each part of the
 * name is reduced to the song's own words; a catalog song counts as the same when its name matches one part AND
 * its singer or film is named somewhere in the video too (a famous name alone could be someone else's song), and
 * it is the same kind of version (not a remix / lofi / female version of it). */
const NOISE = /\b(official|music|video|audio|lyrical|lyrics?|full|song|songs|hd|hq|4k|8k|1080p|new|latest|title|track|version|feat|ft|prod|starring|from|movie|film|with)\b/g;
// Kinds of versions (a Dance Mix is not the Lofi version, nor the original).
const VERSIONS = [['mix', /\b(re)?mix\b/], ['lofi', /\blo ?fi\b/], ['slowed', /\b(slowed|reverb|sped)\b/], ['acoustic', /\b(acoustic|unplugged)\b/],
  ['reprise', /\breprise\b/], ['female', /\bfemale\b/], ['male', /\bmale\b/], ['cover', /\bcover\b/], ['8d', /\b8d\b/],
  ['karaoke', /\b(karaoke|instrumental)\b/], ['live', /\blive\b/], ['mashup', /\bmash ?up\b/], ['jhankar', /\bjhankar\b/]];
const versionOf = (x) => VERSIONS.filter(([, re]) => re.test(x)).map(([k]) => k).join(',');
const plain = (s) => norm(String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, ''));
function songKey(s) {
  return plain(s).replace(/\b(19|20)\d\d\b/g, ' ').replace(NOISE, ' ').replace(/\s+/g, ' ').trim();
}
/** The video's name in parts (split at | : - …), each as a song key. */
function ytKeys(yt) {
  if (yt._keys) return yt._keys;
  const parts = String(yt.raw || yt.title || '').replace(/#\S+/g, ' ').split(/\s*[|•:–—]\s*|\s-\s/).slice(0, 4);
  return (yt._keys = [...new Set([yt.title, ...parts].map(songKey).filter(Boolean))]);
}
/** 1 = same name; 0.9 = same words in another order; 0.85 = the catalog name is inside it; else the share of words. */
function keyMatch(c, y) {
  if (!c || !y) return 0;
  if (c === y) return 1;
  const A = new Set(c.split(' ')), B = new Set(y.split(' '));
  let common = 0;
  A.forEach((w) => { if (B.has(w)) common++; });
  const share = common / Math.max(A.size, B.size);
  if (share === 1) return 0.9;
  if (common === A.size && A.size >= 2) return 0.85;
  return share;
}
/** 0 = not this song; 1.75+ = the same song (name + singer or film, same version). */
function fitScore(yt, c, rank = 0) {
  const ck = c._sk || (c._sk = songKey(c.title));
  let sim = 0;
  for (const k of ytKeys(yt)) sim = Math.max(sim, keyMatch(ck, k));
  if (sim < 0.75) return 0;
  const raw = plain(`${yt.raw || ''} ${yt.title || ''} ${yt.artist || ''}`);
  const singer = splitArtists(c.artist).map(plain).some((a) => a.length > 2 && raw.includes(a));
  // The film: the album, or the name in the catalog title's 'From "Jawan"'.
  const films = [songKey(c.album), songKey((c.title.match(/from\s+["“'‘]?([^"”'’)]+)/i) || [])[1])];
  const film = films.some((f) => f.length > 3 && f !== ck && raw.includes(f));
  if (!singer && !film) return 0;
  // Versions are often in brackets ("(Dance Mix)", "(Lofi Flip)"): look at the names as written.
  const low = (x) => String(x || '').toLowerCase().replace(/[^\p{L}\p{N}]+/gu, ' ');
  const otherVersion = versionOf(low(yt.raw || yt.title)) !== versionOf(low(c.title));
  return sim + 1 + rank * 0.3 - (otherVersion ? 1.5 : 0);
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
  saveTimer = setTimeout(() => {
    try { localStorage.setItem('sangeet', JSON.stringify(S)); } catch {}
    if (typeof CloudSync !== 'undefined') CloudSync.changed();
  }, 300);
}
const seenSet = new Set(S.seen);
function markSeen(ids) {
  for (const id of ids) if (!seenSet.has(id)) { seenSet.add(id); S.seen.push(id); }
  if (S.seen.length > 20000) S.seen.splice(0, S.seen.length - 20000).forEach((x) => seenSet.delete(x));
  save();
}
function slim(t) { const { _k, _sk, _keys, ...rest } = t; return rest; }
const isLiked = (t) => S.liked.some((x) => x.id === t.id);
function toggleLike(t) {
  const i = S.liked.findIndex((x) => x.id === t.id);
  if (i >= 0) S.liked.splice(i, 1); else S.liked.unshift(slim(t));
  save();
  refreshLikes();
}
function recordPlay(t) {
  if (t.src === 'radio') return; // live radio stays out of history and suggestions
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
const PLACEHOLDER = 'data:image/svg+xml;utf8,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#40202f"/><stop offset="1" stop-color="#1c1c23"/></linearGradient></defs><rect width="100" height="100" fill="url(#g)"/><path d="M47 30v23a9 9 0 105 8V40h10V30z" fill="#ff5c6b" opacity=".85"/></svg>');
const art = (t, big) => (t && t.img ? (big ? t.img.replace(/\d+x\d+(?=\.\w+$)/, '500x500') : t.img) : PLACEHOLDER);
document.addEventListener('error', (e) => {
  const el = e.target;
  if (el.tagName === 'IMG' && el.src !== PLACEHOLDER) el.src = PLACEHOLDER;
}, true);
function streamUrl(t) {
  if (t.src === 'radio') return t.media; // a live station's own stream
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
    this.rank = new Map();
    for (const l of S.langs) {
      const list = this.data[l]?.tracks || [];
      list.forEach((t, i) => this.rank.set(t.id, 1 - i / list.length));
    }
    this.total = this.tracks.length;
    this.loaded = true;
  },
  search(q, limit = 80) {
    const words = norm(q).split(' ').filter(Boolean);
    if (!words.length) return [];
    const out = [];
    for (const t of this.tracks) {
      if (!words.every((w) => t._k.includes(w))) continue;
      const title = norm(t.title), want = words.join(' ');
      let s = title === want ? 4 : title.startsWith(words[0]) ? 2 : 0;
      const singers = splitArtists(t.artist).map(norm);
      if (singers.includes(want)) s += singers[0] === want ? 3 : 2.5;
      if (JUNK.test(t.title)) s -= 3;
      s += (this.trackPl.get(t.id) || []).length * 0.05;
      out.push([t, s]);
    }
    return out.sort((a, b) => b[1] - a[1]).slice(0, limit).map((x) => x[0]);
  },
  /** Same song from YouTube → the library copy (plays in the background). */
  match(t) {
    const keys = ytKeys(t);
    if (!keys.length) return null;
    // Quick filter: a catalog name must contain the longest word of one of the parts (fast over a lakh songs).
    const words = keys.map((k) => k.split(' ').sort((x, y) => y.length - x.length)[0]);
    let best = null, top = 0;
    for (const c of this.tracks) {
      const ck = c._sk || (c._sk = songKey(c.title));
      if (!words.some((w) => ck.includes(w))) continue;
      const sc = fitScore(t, c, this.rank.get(c.id) || 0);
      if (sc > top) { top = sc; best = c; }
    }
    return top >= 1.75 ? best : null;
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
      const title = norm(t.title);
      let s = title === want ? 3 : title.startsWith(want) ? 1.5 : 0;
      // A singer's name: their own songs first, not mashups that only mention them in the title.
      const singers = splitArtists(t.artist).map(norm);
      if (singers.includes(want)) s += singers[0] === want ? 3 : 2.5;
      if (JUNK.test(t.title)) s -= 3;
      out.push([t, s, n]);
    }
    return out.sort((a, b) => b[1] - a[1] || a[2] - b[2]).slice(0, limit).map((x) => x[0]);
  },
  /** Finds one song by title + singer (YouTube songs, imported playlists). */
  async findSong(title, artist, raw = '') {
    const yt = { title, artist, raw };
    const keys = ytKeys(yt);
    if (!keys.length) return null;
    const singer = splitArtists(artist)[0] || '';
    for (const q of [...new Set([`${keys[0]} ${singer}`.trim(), ...keys.slice(0, 2)])]) {
      const list = await this.search(q, 30);
      let best = null, top = 0;
      list.forEach((c, k) => { const sc = fitScore(yt, c, 1 - k / list.length); if (sc > top) { top = sc; best = c; } });
      if (top >= 1.75) return best;
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

const JUNK = /mash ?up|lo-?fi|remix|slowed|reverb|mixtape|karaoke|instrumental|jukebox|non ?stop|\b8d\b/i;

/** True when a and b differ by at most `max` letters (typos like "arjit" for "arijit"). */
function near(a, b, max) {
  if (Math.abs(a.length - b.length) > max) return false;
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    let best = i;
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
      best = Math.min(best, cur[j]);
    }
    if (best > max) return false;
    prev = cur;
  }
  return prev[b.length] <= max;
}

/* ------------------------------------------------------------------ singers (artist results + spelling fixes) */
const Artists = {
  list: null,
  async load() {
    if (this.list) return this.list;
    let rows = Deep.info ? await Deep.get(`data/artists.json${Deep.v()}`) : null;
    if (!Array.isArray(rows) || !rows.length) {
      // Older catalog without the singers file: use the songs already on the phone.
      const c = new Map();
      for (const t of Catalog.tracks) for (const a of splitArtists(t.artist)) c.set(a, (c.get(a) || 0) + 1);
      rows = [...c.entries()].filter((x) => x[1] >= 2).sort((a, b) => b[1] - a[1]);
    }
    return (this.list = rows.map(([name, n]) => ({ name, n, k: norm(name).replace(/ /g, '') })));
  },
  /** Singers for what was typed: the exact name, the start of a name, or a small spelling mistake. */
  async find(q, max = 3) {
    const k = norm(q).replace(/ /g, '');
    if (Array.from(k).length < 3) return [];
    const out = [];
    for (const a of await this.load()) {
      let s = 0, how = '';
      if (a.k === k) { s = 30; how = 'exact'; }
      else if (k.length >= 4 && a.k.startsWith(k)) { s = 15; how = 'start'; }
      else if (k.length >= 6 && a.n >= 5 && a.k[0] === k[0] && near(a.k, k, k.length >= 10 ? 2 : 1)) { s = 10; how = 'typo'; }
      // Song count weighs a lot: "arjit singh" (a mistagged name with 3 songs) means Arijit Singh.
      if (s) out.push([{ ...a, how }, s + Math.log10(a.n + 1) * 12]);
    }
    // Typed a real name correctly: no look-alike names.
    const list = out.some((x) => x[0].how === 'exact') ? out.filter((x) => x[0].how !== 'typo') : out;
    return list.sort((x, y) => y[1] - x[1]).slice(0, max).map((x) => x[0]);
  },
};

/* ------------------------------------------------------------------ recommendations: fresh songs, never repeats */
function suggestions(count = 25, exclude = new Set()) {
  const artists = new Map();
  const addA = (t, w) => splitArtists(t.artist).forEach((a) => artists.set(norm(a), (artists.get(norm(a)) || 0) + w));
  for (const r of Object.values(S.history)) addA(r.t, r.c * (0.5 + 1 / (1 + (Date.now() - r.last) / 864e5 / 7)));
  S.liked.forEach((t) => addA(t, 3));
  // Other Sangeet listeners: songs they play with yours, and what most of them play (Community).
  const comm = Community.scores([...S.liked.slice(0, 25), ...recent().slice(0, 40)].map((x) => x.id));
  // Songs that share playlists with what you play and like ("people who like this also like").
  const plScore = new Map();
  for (const s of [...S.liked.slice(0, 25), ...recent().slice(0, 40)]) {
    for (const pi of Catalog.trackPl.get(s.id) || []) plScore.set(pi, (plScore.get(pi) || 0) + 1);
  }
  // New songs (this year) by singers you like come up first.
  const now = new Date(), since = now.getMonth() < 3 ? now.getFullYear() - 1 : now.getFullYear();
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
    scored.push([t, Math.log1p(a) + Math.log1p(p) * 0.8 + chart + (a > 0 && t.year >= since ? 1.5 : 0) + Math.log1p(comm.get(t.id) || 0) * 1.5 + Math.random() * 2.5]);
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

/** Songs like this one (Spotify-style radio): same playlists, same singers, same language and era, popular ones first.
 * Used after a song picked in search, so "Khuda Jaane" is followed by similar songs, not by more songs named "Khuda…". */
function radio(seed, count = 25, exclude = new Set()) {
  const near = Community.scores([seed.id]);
  const singers = new Set(splitArtists(seed.artist).map(norm));
  const pls = new Set(Catalog.trackPl.get(seed.id) || []);
  const lang = seed.lang || Catalog.byId.get(seed.id)?.lang || '';
  const seedTitle = norm(seed.title);
  const skipTitles = new Set([...exclude].map((id) => Catalog.byId.get(id)).filter(Boolean).map((t) => norm(t.title)));
  const scored = [];
  for (const t of Catalog.tracks) {
    if (t.id === seed.id || exclude.has(t.id)) continue;
    const title = norm(t.title);
    if (title === seedTitle || skipTitles.has(title) || JUNK.test(t.title)) continue;
    let s = 0, chart = 0;
    for (const pi of Catalog.trackPl.get(t.id) || []) {
      if (pls.has(pi)) s += 1.5;
      if (Catalog.playlists[pi].chart) chart = 0.6;
    }
    s = Math.min(s, 6) + chart;
    if (splitArtists(t.artist).some((a) => singers.has(norm(a)))) s += 2.5;
    if (lang && t.lang === lang) s += 1.5;
    if (seed.year && t.year) s += Math.max(0, 1 - Math.abs(seed.year - t.year) / 8);
    s += (Catalog.rank.get(t.id) || 0) * 2;
    s += (near.get(t.id) || 0) * 1.5; // listeners who play the seed also play this
    if (seenSet.has(t.id)) s -= 1;
    scored.push([t, s + Math.random() * 1.5]);
  }
  scored.sort((a, b) => b[1] - a[1]);
  // Mix it up: at most 3 songs per singer, never the same singer twice in a row.
  const top = scored.slice(0, count * 4).map((x) => x[0]);
  const per = new Map(), out = [];
  while (top.length && out.length < count) {
    const last = out.length ? norm(splitArtists(out[out.length - 1].artist)[0] || '') : '';
    let i = top.findIndex((t) => { const a = norm(splitArtists(t.artist)[0] || ''); return a !== last && (per.get(a) || 0) < 3; });
    if (i < 0) i = 0;
    const t = top.splice(i, 1)[0];
    const a = norm(splitArtists(t.artist)[0] || '');
    per.set(a, (per.get(a) || 0) + 1);
    out.push(t);
  }
  return out;
}

/* ------------------------------------------------------------------ YouTube (search + songs that aren't in the library) */
const Tube = {
  cache: new Map(),
  async search(q) {
    if (!S.ytKey) return [];
    const key = norm(q);
    if (!this.cache.has(key)) this.cache.set(key, this.fetchSearch(q).then((r) => { if (!r.length) this.cache.delete(key); return r; }));
    return this.cache.get(key);
  },
  async fetchSearch(q) {
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
    return { id: 'yt:' + vid, src: 'yt', sid: vid, title, artist, raw: `${raw} ${ch}`, album: 'YouTube', dur: 0, img: (th.high || th.medium || th.default || {}).url || '', lang: '' };
  },
  /** A public YouTube / YouTube Music playlist: {title, tracks}. */
  async playlist(id) {
    if (!S.ytKey) return null;
    try {
      const meta = await (await fetch(`https://www.googleapis.com/youtube/v3/playlists?part=snippet&id=${encodeURIComponent(id)}&key=${S.ytKey}`)).json();
      const tracks = [];
      let token = '';
      for (let page = 0; page < 40; page++) { // up to 2000 songs, 1 quota unit per 50
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

/* ------------------------------------------------------------------ downloads: songs saved in the app (play without internet)
 * The song files come from JioSaavn's CDN, which allows the app to read them (CORS). They are kept in
 * IndexedDB; "Save to Files" also puts a copy in the iPhone's Files app (kept even if the app is removed). */
const Offline = {
  db: null, ids: new Set(), blobs: new Map(), urls: new Map(), sizes: new Map(),
  queue: [], active: new Map(), running: false, onchange: null,
  open() {
    return this.db || (this.db = new Promise((ok, fail) => {
      const r = indexedDB.open('sangeet-offline', 1);
      r.onupgradeneeded = () => r.result.createObjectStore('songs');
      r.onsuccess = () => ok(r.result);
      r.onerror = () => fail(r.error);
    }));
  },
  async store(mode, fn) {
    const db = await this.open();
    return new Promise((ok, fail) => {
      const tx = db.transaction('songs', mode);
      const req = fn(tx.objectStore('songs'));
      tx.oncomplete = () => ok(req && req.result);
      tx.onerror = () => fail(tx.error);
    });
  },
  /** Keeps a playable link to every saved song, so playing one needs no waiting (iPhone wants play() right on the tap). */
  keep(rec) {
    this.ids.add(rec.t.id);
    this.blobs.set(rec.t.id, rec.blob);
    this.sizes.set(rec.t.id, rec.size || rec.blob.size);
    this.meta.set(rec.t.id, rec.t);
    if (!this.urls.has(rec.t.id)) this.urls.set(rec.t.id, URL.createObjectURL(rec.blob));
  },
  async init() {
    try { (await this.store('readonly', (st) => st.getAll())).forEach((rec) => rec?.t && this.keep(rec)); } catch {}
    this.list = null;
  },
  async songs() {
    try { return (await this.store('readonly', (st) => st.getAll())).filter((r) => r?.t).sort((a, b) => b.at - a.at); } catch { return []; }
  },
  has(t) { return !!t && this.ids.has(t.id); },
  /** Same song already saved from elsewhere (same name and singer). */
  same(t) {
    const want = norm(t.title) + '|' + norm(splitArtists(t.artist)[0] || '');
    return [...this.ids].some((id) => { const x = this.meta.get(id); return x && norm(x.title) + '|' + norm(splitArtists(x.artist)[0] || '') === want; });
  },
  meta: new Map(),
  /** Queue songs for download (YouTube songs: the same song from the catalog, when it is there). */
  async add(list) {
    let added = 0, skipped = 0, youtube = 0;
    for (let t of list) {
      if (t.src === 'yt') {
        const m = Catalog.match(t) || (await Deep.findSong(t.title, t.artist, t.raw));
        if (!m) { youtube++; continue; }
        t = m;
      }
      if (t.src !== 'js' || this.has(t) || this.active.has(t.id) || this.queue.some((x) => x.id === t.id) || this.same(t)) { skipped++; continue; }
      this.queue.push(t);
      added++;
    }
    if (added) toast(added === 1 ? 'Downloading… see Library → Downloads' : `Downloading ${added} songs… see Library → Downloads`);
    else if (youtube) toast("This YouTube song isn't in the catalog, so it can't be saved");
    else if (skipped) toast('Already downloaded');
    this.changed();
    this.run();
    try { navigator.storage?.persist?.(); } catch {}
  },
  changed() {
    clearTimeout(this.t);
    this.t = setTimeout(() => { this.onchange?.(); }, 150);
  },
  async run() {
    if (this.running) return;
    this.running = true;
    while (this.queue.length) {
      const t = this.queue.shift();
      const job = { t, pct: 0 };
      this.active.set(t.id, job);
      this.changed();
      try {
        const res = await fetch(streamUrl(t));
        if (!res.ok) throw new Error('HTTP ' + res.status);
        const total = +res.headers.get('content-length') || 0;
        const reader = res.body.getReader();
        const parts = [];
        let got = 0;
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          parts.push(value);
          got += value.length;
          if (total) { job.pct = Math.min(99, Math.round((got * 100) / total)); this.changed(); }
        }
        const blob = new Blob(parts, { type: 'audio/mp4' });
        const rec = { t: slim(t), blob, size: blob.size, at: Date.now() };
        await this.store('readwrite', (st) => st.put(rec, t.id));
        this.keep(rec);
        this.meta.set(t.id, rec.t);
      } catch (e) {
        toast(`Couldn't download "${t.title}". Check the internet and try again.`, 3500);
      }
      this.active.delete(t.id);
      this.changed();
    }
    this.running = false;
  },
  async remove(id) {
    try { await this.store('readwrite', (st) => st.delete(id)); } catch {}
    const u = this.urls.get(id);
    if (u && Player.current?.id !== id) URL.revokeObjectURL(u);
    this.urls.delete(id); this.blobs.delete(id); this.ids.delete(id); this.sizes.delete(id); this.meta.delete(id);
    this.changed();
  },
  /** A copy in the iPhone's Files app (Share sheet → "Save to Files"). Must run right on the tap. */
  saveToFiles(t) {
    const blob = this.blobs.get(t.id);
    if (!blob) return toast('Download it first');
    const name = `${t.title} - ${t.artist}`.replace(/[\\/:*?"<>|]/g, '').slice(0, 120) + '.m4a';
    const file = new File([blob], name, { type: 'audio/mp4' });
    if (navigator.canShare?.({ files: [file] })) {
      navigator.share({ files: [file], title: t.title }).catch(() => {});
      return;
    }
    const a = h('a', { href: this.urls.get(t.id), download: name });
    document.body.append(a);
    a.click();
    a.remove();
  },
  bytes() { let n = 0; this.sizes.forEach((v) => (n += v)); return n; },
  /** No internet, or Offline mode turned on in Settings: only downloaded songs play. */
  only() { return !!S.offlineOnly || !navigator.onLine; },
};

/* ------------------------------------------------------------------ sleep timer */
const Sleep = {
  timer: null, until: 0, endOfSong: false,
  set(min) {
    this.clear();
    if (min === 'end') { this.endOfSong = true; toast('Music stops after this song'); return; }
    this.until = Date.now() + min * 60000;
    this.timer = setTimeout(() => this.fire(), min * 60000);
    toast(`Music stops in ${min} minutes`);
  },
  fire() { this.clear(); Player.pause(); toast('Sleep timer: paused'); },
  /** Also checked every half second (timers can be late while the phone is locked). */
  check() { if (this.until && Date.now() >= this.until) this.fire(); },
  clear() { clearTimeout(this.timer); this.timer = null; this.until = 0; this.endOfSong = false; },
  get on() { return !!this.until || this.endOfSong; },
  label() {
    if (this.endOfSong) return 'Stops after this song';
    if (this.until) return `Stops in ${Math.max(1, Math.round((this.until - Date.now()) / 60000))} min`;
    return '';
  },
};

/* ------------------------------------------------------------------ listening stats (Library → Your Stats) */
const dayKey = (d) => `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;
const Stats = {
  n: 0,
  add(ms) {
    const st = S.stats || (S.stats = { ms: 0, days: {}, hours: Array(24).fill(0) });
    const d = new Date();
    st.ms += ms;
    st.days[dayKey(d)] = (st.days[dayKey(d)] || 0) + ms;
    st.hours[d.getHours()] += ms;
    if (++this.n % 30 === 0) { // every 15 s
      const keys = Object.keys(st.days);
      if (keys.length > 400) keys.slice(0, keys.length - 400).forEach((k) => delete st.days[k]);
      save();
    }
  },
};
document.addEventListener('visibilitychange', () => { if (document.hidden) save(); });

/* ------------------------------------------------------------------ player */
const audio = $('#audio');
/**
 * "YouTube in background" (experimental): a YouTube song as plain audio from public Invidious / Piped servers,
 * so it keeps playing with the screen locked (YouTube's own player stops there). The servers come and go;
 * the list is checked again with every catalog build. When none works, YouTube's player is used as before.
 */
const YtAudio = {
  servers: null, good: null,
  async list() {
    if (!this.servers) this.servers = await fetch(`data/yt-servers.json${Deep.v()}`).then((r) => r.json()).catch(() => []);
    // The server that worked last time first.
    return this.good ? [this.good, ...this.servers.filter((s) => s !== this.good)] : this.servers;
  },
  async urlFrom(s, vid) {
    if (s.type === 'invidious') return `${s.url}/latest_version?id=${vid}&itag=140&local=true`;
    const r = await fetch(`${s.api}/streams/${vid}`, { signal: AbortSignal.timeout?.(6000) });
    const streams = ((await r.json()).audioStreams || []).filter((a) => /mp4/.test(a.mimeType || ''));
    return streams.sort((a, b) => (b.bitrate || 0) - (a.bitrate || 0))[0]?.url;
  },
  /** Plays [url]; true once it really plays (or can play but needs a tap first). */
  tryUrl(url) {
    return new Promise((resolve) => {
      const done = (ok) => {
        clearTimeout(timer);
        ['playing', 'canplay', 'error'].forEach((e) => audio.removeEventListener(e, on[e]));
        resolve(ok);
      };
      const on = { playing: () => done(true), canplay: () => {}, error: () => done(false) };
      const timer = setTimeout(() => done(false), 9000);
      Object.entries(on).forEach(([e, f]) => audio.addEventListener(e, f));
      audio.src = url;
      audio.play().catch((e) => {
        if (e.name !== 'NotAllowedError') return;
        if (audio.readyState >= 3) done(true);
        else { audio.removeEventListener('canplay', on.canplay); on.canplay = () => done(true); audio.addEventListener('canplay', on.canplay); }
      });
    });
  },
  /** True when [t] now plays as audio (or a newer song took over); false: use YouTube's player. */
  async play(t) {
    Player.trying = true;
    try {
      for (const s of (await this.list()).slice(0, 4)) {
        if (Player.current !== t) return true;
        const url = await this.urlFrom(s, t.sid).catch(() => null);
        if (Player.current !== t) return true;
        if (url && (await this.tryUrl(url))) {
          if (Player.current === t) this.good = s;
          return true;
        }
      }
      return false;
    } finally { Player.trying = false; }
  },
};

const Player = {
  queue: [], i: -1, playing: false, loading: false, mode: 'audio', error: '', fetching: false,
  get current() { return this.queue[this.i]; },
  play(list, i = 0, keep = false) {
    if (!list[i]) return;
    // Offline: nothing in this list is downloaded, so leave what is playing alone.
    if (Offline.only() && !list.some((t) => Offline.has(t))) {
      toast("You're offline: only downloaded songs play (Library → Downloads)", 3500);
      return;
    }
    this.radioMode = false;
    this.queue = keep ? list : list.slice();
    this.load(i);
  },
  /** This song, then songs like it (and more like them when those run out). */
  playRadio(t) {
    this.play([t, ...radio(t, 25, new Set([t.id]))]);
    this.radioMode = true;
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
    // Offline (no internet, or Offline mode on): only downloaded songs can play; skip to the next one.
    if (Offline.only() && this.queue[i] && !Offline.has(this.queue[i])) {
      const j = this.queue.findIndex((x, k) => k > i && Offline.has(x));
      if (j < 0) { toast("You're offline: only downloaded songs play (Library → Downloads)", 3500); this.pause(); this.loading = false; UI.update(); return; }
      i = j;
    }
    this.i = i;
    let t = this.current;
    this.error = '';
    this.loading = true;
    if (t.src === 'yt' || t.src === 'q') {
      // Same song in the catalog plays in the background, so prefer it.
      UI.trackChanged();
      const m = Catalog.match(t) || (await Deep.findSong(t.title, t.artist, t.raw));
      if (this.current !== t) return;
      if (m) t = this.queue[i] = m;
      else if (t.src === 'q') {
        const y = (await Tube.search(`${t.title} ${t.artist}`))[0];
        if (this.current !== t) return;
        if (!y) return this.failed();
        t = this.queue[i] = y;
      }
    }
    let viaAudio = false;
    if (t.src === 'yt' && S.ytAudio) {
      this.mode = 'audio';
      if (Tube.player && Tube.player.stopVideo) Tube.player.stopVideo();
      $('#ytbox').hidden = true;
      UI.trackChanged();
      viaAudio = await YtAudio.play(t);
      if (this.current !== t) return;
      if (viaAudio) audio.defaultPlaybackRate = audio.playbackRate = S.speed || 1;
      else if (!this.toldYt) { this.toldYt = true; toast('YouTube background servers are busy, playing with YouTube for now', 3500); }
    }
    if (viaAudio) {
      // Playing already.
    } else if (t.src === 'yt') {
      this.mode = 'yt';
      audio.pause();
      $('#ytbox').hidden = false;
      const p = await Tube.ensure();
      if (this.current !== t) return;
      p.loadVideoById(t.sid);
      if ((S.speed || 1) !== 1) p.setPlaybackRate?.(S.speed);
    } else {
      this.mode = 'audio';
      if (Tube.player && Tube.player.stopVideo) Tube.player.stopVideo();
      $('#ytbox').hidden = true;
      // Saved on the phone: play the file (works without internet).
      audio.src = Offline.urls.get(t.id) || streamUrl(t);
      audio.preservesPitch = true;
      audio.defaultPlaybackRate = audio.playbackRate = S.speed || 1;
      audio.play().catch((e) => {
        this.loading = false;
        if (e.name === 'NotAllowedError') this.playing = false; // iPhone needs a tap first
        UI.update();
      });
    }
    recordPlay(t);
    this.updateSession(t);
    UI.trackChanged();
    if (this.queue.length - this.i <= 3 && t.src !== 'radio') this.autoplay(false);
  },
  next() {
    if (this.i + 1 < this.queue.length) return this.load(this.i + 1);
    if (this.repeat === 'all' && this.queue.length) return this.load(0);
    this.autoplay(true); // never repeat on its own: fetch fresh songs
  },
  /** A song finished: sleep timer "end of song", repeat one, or the next song. */
  ended() {
    if (Sleep.endOfSong) { Sleep.clear(); this.playing = false; UI.update(); toast('Sleep timer: stopped after the song'); return; }
    if (this.repeat === 'one') { this.seek(0); this.resume(); return; }
    this.next();
  },
  repeat: 'off',
  cycleRepeat() {
    this.repeat = { off: 'all', all: 'one', one: 'off' }[this.repeat];
    toast({ off: 'Repeat off', all: 'Repeat all', one: 'Repeat this song' }[this.repeat]);
  },
  shuffleOn: false,
  /** Shuffle: mixes the songs still to come (the playing one stays). */
  toggleShuffle() {
    this.shuffleOn = !this.shuffleOn;
    if (this.shuffleOn) this.queue.push(...shuffle(this.queue.splice(this.i + 1)));
    toast(this.shuffleOn ? 'Shuffle on' : 'Shuffle off');
  },
  setSpeed(v) {
    S.speed = v; save();
    audio.defaultPlaybackRate = audio.playbackRate = v;
    Tube.player?.setPlaybackRate?.(v);
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
    if (this.mode === 'yt') { Tube.player?.seekTo(s, true); this.updateState(); } else audio.currentTime = s;
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
    const ids = new Set(this.queue.map((t) => t.id));
    const fresh = this.radioMode && this.current ? radio(this.current, 20, ids) : suggestions(20, ids);
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
    if (s === 0) return this.ended();
    const was = this.playing;
    this.playing = s === 1;
    this.loading = s === 3 || s === -1;
    if (s === 1 && !was) this.updateSession();
    if (was !== this.playing) this.updateState();
    UI.update();
  },
  /** Song name, singer and cover on the lock screen, Control Center and Dynamic Island. */
  updateSession(t = this.current) {
    if (!('mediaSession' in navigator) || !t) return;
    const big = art(t, true);
    navigator.mediaSession.metadata = new MediaMetadata({
      title: t.title, artist: t.artist, album: t.album || 'Sangeet',
      artwork: t.img
        ? ['150x150', '500x500'].map((sz) => ({ src: big.replace('500x500', sz), sizes: sz, type: 'image/jpeg' }))
        : [{ src: new URL('icon-512.png', location.href).href, sizes: '512x512', type: 'image/png' }],
    });
  },
  /** Playing/paused and the seek bar for the lock screen (only when they change: iPhone dislikes constant updates). */
  updateState() {
    if (!('mediaSession' in navigator)) return;
    const ms = navigator.mediaSession;
    ms.playbackState = this.playing ? 'playing' : 'paused';
    const d = this.duration();
    if (ms.setPositionState && d) {
      try { ms.setPositionState({ duration: d, position: Math.min(this.time(), d), playbackRate: 1 }); } catch {}
    }
  },
};
audio.addEventListener('playing', () => {
  Player.playing = true; Player.loading = false;
  // iPhone forgets the song details when a new song loads: set them again once it really plays.
  Player.updateSession(); Player.updateState();
  UI.update();
  // First song on an iPhone: there is no notification there, so say where the controls are.
  if (!S.tipControls && /iPhone|iPad/.test(navigator.userAgent)) {
    S.tipControls = true; save();
    setTimeout(() => toast('Controls: lock screen, Control Center or the Dynamic Island', 5000), 1500);
  }
});
audio.addEventListener('pause', () => { Player.playing = false; Player.updateState(); UI.update(); });
audio.addEventListener('seeked', () => Player.updateState());
audio.addEventListener('durationchange', () => Player.updateState());
audio.addEventListener('waiting', () => { Player.loading = true; UI.update(); });
audio.addEventListener('ended', () => Player.ended());
audio.addEventListener('error', () => { if (audio.getAttribute('src') && Player.mode === 'audio' && !Player.trying) Player.failed(); });
if ('mediaSession' in navigator) {
  const ms = navigator.mediaSession;
  ms.setActionHandler('play', () => Player.resume());
  ms.setActionHandler('pause', () => Player.pause());
  ms.setActionHandler('previoustrack', () => Player.prev());
  ms.setActionHandler('nexttrack', () => Player.next());
  try { ms.setActionHandler('seekto', (d) => { Player.seek(d.seekTime); Player.updateState(); }); } catch {}
  try { ms.setActionHandler('stop', () => Player.pause()); } catch {}
}

/* ------------------------------------------------------------------ shared UI pieces */
function trackRow(t, onClick) {
  return h('div', { class: 'row' + (Player.current?.id === t.id ? ' playing' : ''), 'data-id': t.id, onclick: onClick },
    h('img', { class: 'art', src: art(t), loading: 'lazy', alt: '' }),
    h('div', { class: 'meta' }, h('div', { class: 't' }, t.title),
      h('div', { class: 's' }, Offline.has(t) ? h('span', { class: 'saved', title: 'Downloaded' }, icon('download')) : null, t.src === 'yt' ? `${t.artist} · YouTube` : t.artist)),
    h('button', { class: 'icon-btn more', 'aria-label': 'More', onclick: (e) => { e.stopPropagation(); trackMenu(t); } }, icon('more')));
}
/** Long lists render 60 rows at a time as you scroll. `asRadio`: tapping a song plays it followed by songs like it
 * (search results), instead of the rest of the list (playlists, albums). */
function trackList(tracks, asRadio = false) {
  const box = h('div');
  let shown = 0;
  const more = () => {
    const end = Math.min(tracks.length, shown + 60);
    for (; shown < end; shown++) { const i = shown; box.append(trackRow(tracks[i], () => {
      if (!asRadio) return Player.play(tracks, i);
      Recent.onPick?.({ t: slim(tracks[i]) });
      Player.playRadio(tracks[i]);
    })); }
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
    ['Start radio', () => Player.playRadio(t)],
    ['Add to queue', () => Player.addToQueue(t)],
    [isLiked(t) ? 'Remove from Liked' : 'Like', () => toggleLike(t)],
    ['Add to playlist', () => playlistPicker(t)],
    ...(t.src === 'radio' ? [] : Offline.has(t)
      ? [['Save to Files (iPhone)', () => Offline.saveToFiles(t)], ['Remove download', () => Offline.remove(t.id).then(() => toast('Download removed'))]]
      : [['Download', () => Offline.add([t])]]),
    ...(t.artist && t.src === 'js' ? [['Go to artist', () => openArtist(splitArtists(t.artist)[0] || t.artist)]] : []),
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
/** Share a song as a Sangeet link (#play=…): it opens here on iPhone, and in the app on Android. */
async function share(t) {
  const o = Sync.encode(t);
  const url = o ? `${location.origin}${location.pathname}#play=${await Sync.pack(JSON.stringify(o))}`
    : `https://www.jiosaavn.com/search/song/${encodeURIComponent(t.title + ' ' + t.artist)}`;
  const text = `🎵 ${t.title} · ${t.artist} on Sangeet`;
  if (navigator.share) navigator.share({ title: t.title, text, url }).catch(() => {});
  else navigator.clipboard?.writeText(`${text}\n${url}`).then(() => toast('Link copied'));
}
/** Opened a shared song link: offer to play it (a tap is needed to start sound), or open it in the Android app. */
const SongLink = {
  async fromHash() {
    const m = location.hash.match(/play=([\w-]+)/);
    if (!m) return;
    history.replaceState(null, '', location.pathname);
    try {
      const t = Sync.decode(JSON.parse(await Sync.unpack(m[1])));
      if (!t) throw new Error('bad link');
      const known = Catalog.byId.get(t.id) || t;
      const items = [['▶ Play', () => Player.playRadio(known)], [isLiked(known) ? 'Liked ✓' : 'Like', () => { if (!isLiked(known)) toggleLike(known); }]];
      if (/Android/i.test(navigator.userAgent)) {
        items.push(['Open in the Sangeet app', () => {
          location.href = `intent://play?song=${m[1]}#Intent;scheme=sangeet;package=com.sangeet.player;end`;
        }]);
      }
      menu(`${known.title} · ${known.artist}`, items);
    } catch {
      toast("That song link didn't work");
    }
  },
};
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
      h('div', { class: 'cover-wrap' },
        h('img', { class: 'cover', src: art(t, true), alt: '', loading: i < 2 ? 'eager' : 'lazy', onclick: (e) => this.coverTap(e, t, i) }),
        h('div', { class: 'pop' }, icon('heartFill'))),
      h('div', { class: 'info' },
        h('div', { class: 'meta', onclick: () => trackMenu(t) }, h('div', { class: 'title' }, t.title), h('div', { class: 'artist' }, t.artist)),
        likeBtn(t)),
      seek, ctr);
    ctr.querySelector('.big').onclick = () => this.tap(i);
    p.seek = seek;
    p.ctr = ctr;
    return p;
  },
  coverTap(e, t, i) {
    const now = Date.now();
    const pop = e.currentTarget.nextSibling;
    if (now - (this.lastTap || 0) < 280) {
      clearTimeout(this.tapTimer);
      this.lastTap = 0;
      if (!isLiked(t)) toggleLike(t);
      pop.classList.remove('show');
      void pop.offsetWidth;
      pop.classList.add('show');
      return;
    }
    this.lastTap = now;
    this.tapTimer = setTimeout(() => this.tap(i), 280);
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

/** "Recent searches", like Spotify: songs and singers picked from results, and words searched. Newest first. */
const Recent = {
  onPick: null,
  key: (r) => (r.t ? 't:' + r.t.id : r.a ? 'a:' + norm(r.a.name) : 'q:' + norm(r.q)),
  list() {
    if (!S.recent) S.recent = (S.searches || []).map((q) => ({ q })); // older versions kept only the words
    return S.recent;
  },
  add(item, words) {
    const q = (words || '').trim();
    const add = [item, q.length >= 2 ? { q } : null].filter(Boolean);
    if (!add.length) return;
    const keys = new Set(add.map(this.key));
    S.recent = [...add, ...this.list().filter((r) => !keys.has(this.key(r)))].slice(0, 15);
    save();
  },
  remove(r) { S.recent = this.list().filter((x) => this.key(x) !== this.key(r)); save(); },
  clear() { S.recent = []; save(); },
  row(r, onWords, refresh) {
    const x = h('button', { class: 'icon-btn more', 'aria-label': 'Remove', onclick: (e) => { e.stopPropagation(); this.remove(r); refresh(); } }, '✕');
    if (r.t) {
      return h('div', { class: 'row', onclick: () => Player.playRadio(r.t) },
        h('img', { class: 'art', src: art(r.t), loading: 'lazy', alt: '' }),
        h('div', { class: 'meta' }, h('div', { class: 't' }, r.t.title), h('div', { class: 's' }, `Song · ${r.t.artist}`)), x);
    }
    if (r.a) {
      return h('div', { class: 'row', onclick: () => pushPage(() => artistPage(r.a.name)) },
        h('div', { class: 'art avatar' }, Array.from(r.a.name)[0] || '?'),
        h('div', { class: 'meta' }, h('div', { class: 't' }, r.a.name), h('div', { class: 's' }, 'Artist')), x);
    }
    return h('div', { class: 'row', onclick: onWords },
      h('div', { class: 'art avatar recent-q' }, icon('clock')),
      h('div', { class: 'meta' }, h('div', { class: 't' }, r.q), h('div', { class: 's' }, 'Search')), x);
  },
};

function artistRow(a) {
  return h('div', { class: 'row', onclick: () => { Recent.onPick?.({ a: { name: a.name, n: a.n } }); pushPage(() => artistPage(a.name)); } },
    h('div', { class: 'art avatar' }, Array.from(a.name)[0] || '?'),
    h('div', { class: 'meta' }, h('div', { class: 't' }, a.name), h('div', { class: 's' }, `Artist · ${a.n} songs`)));
}
function searchPage() {
  const results = h('div');
  const input = h('input', { type: 'search', placeholder: 'Songs, artists, movies', autocomplete: 'off', autocapitalize: 'off', spellcheck: false, enterkeyhint: 'search' });
  let ytTimer, ytAuto, seq = 0;
  const run = () => {
    const raw = input.value.trim();
    searchPage.q = raw;
    // Typed (or said) in Hindi / Punjabi script: the catalog is searched in its Latin spellings, YouTube as typed.
    const qs = Translit.has(raw) ? Translit.variants(raw) : [raw];
    const q = qs[0] || raw;
    const each = (list) => { const seen = new Set(); return list.flat().filter((t) => !seen.has(t.id) && seen.add(t.id)); };
    const my = ++seq;
    clearTimeout(ytTimer);
    clearTimeout(ytAuto);
    if (raw.length < 2) { results.replaceChildren(browse()); return; }
    const local = each(qs.map((x) => Catalog.search(x, 40))).slice(0, 40);
    const artistBox = h('div'), albumBox = h('div'), deepBox = h('div'), fixBox = h('div'), ytBox = h('div');
    // A few words (4+) may be a line from the middle of a song: YouTube finds songs by their lyrics, so its
    // results come first then (they play from the catalog when the same song is there).
    const line = raw.split(/\s+/).length >= 4;
    const localEl = local.length ? trackList(local, true) : h('div', { class: 'spinner' });
    results.replaceChildren(albumBox, artistBox, fixBox, line ? ytBox : '', localEl, deepBox, line ? '' : ytBox);
    const shown = new Set(local.map((t) => norm(t.title)));
    const youtube = async () => {
      ytBox.replaceChildren(h('div', { class: 'spinner' }));
      const yt = (await Tube.search(line ? `${raw} song` : raw))
        .map((t) => Catalog.match(t) || t) // in the catalog: show (and play) that one, it keeps playing in the background
        .filter((t) => !shown.has(norm(t.title)));
      if (my !== seq) return;
      fill(ytBox, yt.length ? [h('div', { class: 'section' }, line ? '🎤 Songs with these lyrics' : 'From YouTube'), trackList(yt, true)] : shown.size ? [] : h('div', { class: 'empty' }, 'No songs found'));
    };
    // Then the full catalog (lakhs of songs) and singers; YouTube only for what isn't there.
    ytTimer = setTimeout(async () => {
      const ids = new Set(local.map((t) => t.id));
      const [deeps, artists] = await Promise.all([Promise.all(qs.map((x) => Deep.search(x))), Artists.find(q)]);
      const deep = each(deeps);
      if (my !== seq) return;
      if (!local.length) localEl.remove();
      const more = deep.filter((t) => !ids.has(t.id));
      more.forEach((t) => shown.add(norm(t.title)));
      if (artists.length) fill(artistBox, h('div', { class: 'section' }, 'Artists'), artists.map(artistRow));
      // A movie's name: its album with all its songs, to play one after another.
      Albums.find(q, [...local, ...deep]).then((albums) => {
        if (my === seq && albums.length) fill(albumBox, h('div', { class: 'section' }, '💿 Movies & albums'), h('div', { class: 'grid' }, albums.map(albumCard)));
      });
      if (more.length) fill(deepBox, local.length ? h('div', { class: 'section' }, 'More songs') : null, trackList(more, true));
      // Misspelt singer ("arjit singh", "sidhu moosewala"): first the songs of the singer it most likely means.
      const fix = artists[0];
      if (fix && fix.how !== 'start' && norm(fix.name) !== norm(q)) {
        const songs = (await Deep.search(fix.name, 40)).filter((t) => !shown.has(norm(t.title)));
        if (my !== seq) return;
        songs.forEach((t) => shown.add(norm(t.title)));
        if (songs.length) fill(fixBox, h('div', { class: 'section' }, `Songs by ${fix.name}`), trackList(songs, true));
      }
      if (shown.size < 8 || line) ytAuto = setTimeout(youtube, 600); // only once typing has stopped (YouTube allows few searches a day)
      else ytBox.replaceChildren(h('button', { class: 'chip', style: 'margin:12px 16px', onclick: youtube }, 'Search YouTube too'));
    }, 350);
  };
  input.addEventListener('input', () => { clearTimeout(run.t); run.t = setTimeout(run, 150); });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { input.blur(); Recent.add(null, input.value); } });
  // A song or singer picked from the results goes into "Recent searches", with the words typed.
  Recent.onPick = (item) => Recent.add(item, input.value);
  const browse = () => {
    const recent = Recent.list();
    const charts = Catalog.playlists.filter((p) => p.chart).slice(0, 6);
    const moods = ['Happy', 'Romantic', 'Sad', 'Party', 'Chill', 'Sleep', 'Workout', '90s', 'Bhakti', 'Wedding', 'Road trip', 'Rain'];
    return h('div', null,
      recent.length ? [
        h('div', { class: 'section-row' }, h('div', { class: 'section' }, 'Recent searches'),
          h('button', { class: 'link', onclick: () => { Recent.clear(); results.replaceChildren(browse()); } }, 'Clear all')),
        recent.map((r) => Recent.row(r, () => { if (r.q != null) { input.value = r.q; run(); } }, () => results.replaceChildren(browse()))),
      ] : null,
      h('div', { class: 'section' }, '🎉 Festivals & seasons'),
      h('div', { class: 'chips scroll' }, festivalsNow().map(([name, emoji, q, , , now]) =>
        h('button', { class: 'chip' + (now ? ' on' : ''), onclick: () => pushPage(() => djPage(q)) }, `${emoji} ${name}`))),
      h('div', { class: 'section' }, 'Browse all'),
      h('div', { class: 'cat-row' },
        h('div', { class: 'cat', style: 'background:linear-gradient(135deg,#b45309,#3b1d02)', onclick: () => pushPage(() => moviesPage()) }, '🎬 Movies'),
        CATEGORIES.map((c) => h('div', { class: 'cat', style: `background:${c.color}`, onclick: () => pushPage(() => categoryPage(c)) }, `${c.emoji} ${c.name}`)),
        BROWSE.map(([name, q, color]) => h('div', { class: 'cat', style: `background:${color}`, onclick: () => pushPage(() => djPage(q)) }, name))),
      h('div', { class: 'section' }, 'Moods'),
      h('div', { class: 'chips' }, moods.map((m) => h('button', { class: 'chip', onclick: () => pushPage(() => djPage(`${m} ${S.langs[0] || 'hindi'} songs`)) }, m))),
      charts.length ? [h('div', { class: 'section' }, 'Top charts'), h('div', { class: 'grid' }, charts.map(playlistCard))] : null);
  };
  // Coming back from an artist or a song: keep what was searched.
  if (searchPage.q) { input.value = searchPage.q; run(); } else results.replaceChildren(browse());
  // Voice search: the browser's speech recognition (Safari on iPhone asks for the microphone once).
  const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
  let mic = null;
  if (Speech) {
    mic = h('button', { class: 'icon-btn mic', 'aria-label': 'Voice search', onclick: () => {
      if (mic.rec) { mic.rec.stop(); return; }
      const rec = new Speech();
      rec.lang = 'hi-IN'; // Hindi + English words
      rec.interimResults = true;
      rec.onresult = (e) => {
        const r = e.results[e.results.length - 1];
        input.value = r[0].transcript;
        if (r.isFinal) { run(); Recent.add(null, input.value); }
      };
      rec.onerror = (e) => { if (e.error === 'not-allowed') toast('Allow the microphone for voice search'); };
      rec.onend = () => { mic.rec = null; mic.classList.remove('on'); };
      mic.rec = rec;
      mic.classList.add('on');
      try { rec.start(); toast('Listening… say a song, singer or a line'); } catch { mic.rec = null; mic.classList.remove('on'); }
    } }, icon('mic'));
  }
  return h('div', null, header('Search'), h('div', { class: 'search-box' + (mic ? ' with-mic' : '') }, input, mic), results);
}

/* ------------------------------------------------------------------ Browse all + festivals (same as the Android app) */
const BROWSE = [
  ['🎬 Bollywood Hits', 'bollywood hits', '#e13300'], ['🎵 Hindi Romantic', 'hindi romantic songs', '#dc148c'],
  ['🥁 Punjabi Hits', 'punjabi hits', '#e8115b'], ['🔥 Party', 'bollywood party songs', '#7358ff'],
  ['🎧 Lofi Chill', 'hindi lofi', '#477d95'], ['🎤 Arijit Singh', 'arijit singh', '#8d67ab'],
  ['💔 Sad Songs', 'hindi sad songs', '#1e3264'], ['📻 Old is Gold', 'old hindi songs 90s', '#ba5d07'],
  ['💕 Punjabi Romantic', 'punjabi romantic songs', '#b06239'], ['🌾 Haryanvi', 'haryanvi songs', '#608108'],
  ['🎺 Bhojpuri', 'bhojpuri songs', '#27856a'], ['🙏 Devotional', 'bhakti songs hindi', '#f59b23'],
  ['💪 Workout', 'gym workout hindi songs', '#148a08'], ['🎸 Indie India', 'indian indie songs', '#503750'],
  ['🌍 English Pop', 'english pop hits', '#0d73ec'],
];
// [name, emoji, search, from [month, day], to [month, day]]
const FESTIVALS = [
  ['Lohri & Makar Sankranti', '🪁', 'lohri punjabi songs', [1, 8], [1, 16]], ['Republic Day', '🇮🇳', 'desh bhakti songs', [1, 20], [1, 27]],
  ['Valentine Week', '💝', 'romantic love songs hindi', [2, 6], [2, 15]], ['Holi', '🎨', 'holi songs', [2, 25], [3, 25]],
  ['Baisakhi', '🌾', 'baisakhi punjabi bhangra', [4, 5], [4, 16]], ['Monsoon', '🌧️', 'barish monsoon songs hindi', [6, 25], [9, 10]],
  ['Independence Day', '🇮🇳', 'desh bhakti songs', [8, 8], [8, 16]], ['Raksha Bandhan', '🎀', 'raksha bandhan songs', [8, 1], [8, 31]],
  ['Janmashtami', '🦚', 'krishna bhajan', [8, 10], [9, 10]], ['Ganesh Chaturthi', '🐘', 'ganpati songs', [8, 20], [9, 25]],
  ['Navratri & Garba', '🪔', 'navratri garba dandiya songs', [9, 15], [10, 25]], ['Durga Puja', '🔱', 'durga puja songs', [9, 20], [10, 20]],
  ['Diwali', '🪔', 'diwali songs', [10, 12], [11, 15]], ['Chhath Puja', '🌅', 'chhath puja geet', [10, 25], [11, 20]],
  ['Wedding Season', '💍', 'wedding songs bollywood', [11, 10], [2, 28]], ['Christmas', '🎄', 'christmas songs', [12, 15], [12, 26]],
  ['New Year Party', '🎉', 'new year party songs', [12, 26], [1, 3]],
];
/** Festivals going on today first. */
function festivalsNow() {
  const d = new Date(), today = (d.getMonth() + 1) * 100 + d.getDate();
  const on = (f) => { const a = f[3][0] * 100 + f[3][1], b = f[4][0] * 100 + f[4][1]; return a <= b ? today >= a && today <= b : today >= a || today <= b; };
  return [...FESTIVALS.filter(on).map((f) => [...f, true]), ...FESTIVALS.filter((f) => !on(f))];
}

/* ------------------------------------------------------------------ special categories (Haryanvi Badmashi) */
const BADMASHI = /badma?sh?|bdmash|\bgund[aeiy]|gundagardi|\bgoli|band[ou]+k|raf+al|rifle|pistol|\bkatt[ae]\b|\basla\b|licen[cs]e|\bjail\b|khoon|dushman|gangster|\bgang\b|ak ?47|\bbore\b|encounter|rangdari|dabdaba|hathyar|qatal|\bkatl\b|\bmaut\b|\bbadla\b|warrant|hawalat|\bdaku\b|bahubali|khatarnak|\bthar\b|bawal|rangbaaz|shooter|firing|\bfire\b|\bbullet\b|danger|\bdon\b/i;
/** Garhwali, Kumaoni, Jaunsari and Himachali songs: words and singers in their names (same list as Android). */
const PAHADI = /garhwali|garwali|gadwali|kumaoni|kumauni|kumaon|jaunsari|himachali|pahadi|pahari|uttarakhand|\bnati\b|गढ़वाली|गढवाली|कुमाऊँनी|कुमाउनी|जौनसारी|पहाड़ी|पहाडी|हिमाचली|narendra singh negi|gajendra rana|meena rana|pritam bhartwan|kishan mahipal|inder arya|saurav maithani|anisha ranghar|rohit chauhan|kuldeep sharma|thakur das rathi|vicky chauhan|basanti bisht/i;
const CATEGORIES = [
  // The language's popular songs whose titles fit the category, most popular first.
  { name: 'Haryanvi Badmashi', emoji: '😎', color: 'linear-gradient(135deg,#8b1e1e,#2b0b0b)', lang: 'haryanvi', match: BADMASHI },
  // Pahadi songs are mostly on YouTube: the catalog's few (listed as Hindi) plus YouTube searches.
  { name: 'Pahadi', emoji: '🏔️', color: 'linear-gradient(135deg,#2e6b5e,#0f2a24)', lang: 'hindi', match: PAHADI, field: 'any',
    youtube: ['new garhwali songs', 'kumaoni songs', 'himachali pahari nati'] },
];
function categoryPage(cat) {
  const body = h('div', null, h('div', { class: 'spinner' }));
  const cached = categoryPage.cache.get(cat.name);
  const show = (songs) => fill(body,
    songs.length ? h('div', { class: 'actions' },
      h('button', { class: 'pill primary', onclick: () => Player.play(songs) }, icon('play'), 'Play'),
      h('button', { class: 'pill', onclick: () => Player.play(shuffle(songs)) }, icon('shuffle'), 'Shuffle')) : null,
    songs.length ? trackList(songs) : h('div', { class: 'empty' }, "Couldn't load songs. Check your connection."));
  if (cached) show(cached);
  else (async () => {
    const seen = new Set(), songs = [];
    const add = (t, trusted = false) => {
      const k = norm(t.title);
      const fits = trusted || (t.lang === cat.lang && cat.match.test(cat.field === 'any' ? `${t.title} ${t.artist} ${t.album}` : t.title));
      if (fits && !JUNK.test(t.title) && !seen.has(k)) { seen.add(k); songs.push(t); }
    };
    (await Catalog.loadLang(cat.lang))?.tracks.forEach((t) => add(t));
    if (cat.youtube) {
      const lists = await Promise.all(cat.youtube.map((q) => Tube.search(q)));
      // Take turns between the searches; a YouTube song that is in the catalog plays from there.
      const most = Math.max(0, ...lists.map((l) => l.length));
      for (let i = 0; i < most; i++) for (const l of lists) if (l[i]) add(Catalog.match(l[i]) || l[i], true);
    }
    categoryPage.cache.set(cat.name, songs);
    show(songs);
  })();
  return h('div', null, header(`${cat.emoji} ${cat.name}`, true), body);
}
categoryPage.cache = new Map();

/** Movies / albums named like a search ("aashiqui 2", "brahmastra songs"), with all their songs from the catalog. */
const Albums = {
  words: (s) => norm(String(s || '').replace(/\(.*?\)|\[.*?\]/g, ' ')).split(' ').filter(Boolean),
  skip: new Set(['songs', 'song', 'movie', 'film', 'album', 'all', 'ke', 'gaane', 'gane', 'full', 'jukebox']),
  async find(q, found) {
    const want = this.words(q).filter((w) => !this.skip.has(w));
    if (!want.length) return [];
    const fits = (album) => {
      const name = this.words(album);
      // All typed words are in the album's name, or its whole name is in what was typed ("kesariya brahmastra").
      return name.length && (want.every((w) => name.includes(w)) || ` ${want.join(' ')} `.includes(` ${name.join(' ')} `));
    };
    // One album = same name, year and language (many singles share a song's name: "Tum Hi Ho" by forty singers;
    // a film's Tamil and Telugu versions share its name).
    const id = (t) => `${norm(t.album || '')}|${t.year || 0}|${t.lang || ''}`;
    const pool = [...found, ...(await Deep.search(want.join(' '), 100))];
    const groups = new Map();
    for (const t of pool) if (t.album && t.album !== 'YouTube' && fits(t.album) && !groups.has(id(t))) groups.set(id(t), t);
    const out = [], names = new Set();
    for (const [key, first] of [...groups].slice(0, 5)) {
      const songs = new Map();
      for (const t of [...pool, ...(await Deep.search(first.album, 150))]) if (id(t) === key && !songs.has(t.id)) songs.set(t.id, t);
      const tracks = [...songs.values()];
      // A movie has several different songs (not one song's versions); the same album once.
      if (new Set(tracks.map((t) => norm(t.title).split(' ').slice(0, 3).join(' '))).size < 3 || names.has(key)) continue;
      names.add(key);
      out.push({ title: first.album, tracks, img: first.img, year: first.year, exact: this.words(first.album).join(' ') === want.join(' ') });
    }
    // The exact name first ("aashiqui 2" before "Aashiqui"), then the bigger albums.
    return out.sort((a, b) => (b.exact - a.exact) || (b.tracks.length - a.tracks.length));
  },
};
/* ------------------------------------------------------------------ Movies: every Indian film with its details */
// data/movies.json (from Wikidata): [title, year, languages, music, producers, directors, cast], "|"-separated.
const Movies = {
  list: null,
  async load() {
    if (!this.list) {
      const rows = (await Deep.get(`data/movies.json${Deep.v()}`)) || [];
      this.list = rows.map(([title, year, langs, music, producers, directors, cast]) => {
        const m = { title, year, langs: split(langs), music: split(music), producers: split(producers), directors: split(directors), cast: split(cast) };
        m.key = norm([title, ...m.langs, ...m.music, ...m.producers, ...m.directors, ...m.cast].join(' '));
        return m;
      });
    }
    return this.list;
    function split(x) { return x ? x.split('|') : []; }
  },
  /** The film named like this album (same name, year within one), for its details on album pages. */
  async of(title, year) {
    const t = norm(title);
    return (await this.load()).find((m) => norm(m.title) === t && (!year || !m.year || Math.abs(m.year - year) <= 1)) || null;
  },
};
const DECADES = [['All', 0, 9999], ['2020s', 2020, 2029], ['2010s', 2010, 2019], ['2000s', 2000, 2009], ['90s', 1990, 1999], ['80s', 1980, 1989], ['70s', 1970, 1979], ['Older', 0, 1969]];
function moviesPage(initial = '') {
  const input = h('input', { type: 'search', placeholder: 'Movie, music director, producer, actor…', value: initial, autocomplete: 'off', autocapitalize: 'off', spellcheck: false });
  const langBox = h('div', { class: 'chips scroll' }), decBox = h('div', { class: 'chips scroll' });
  const list = h('div', null, h('div', { class: 'spinner' }));
  let lang = '', dec = DECADES[0], shown = 60, all = [];
  const chipRow = (box, items, isOn, pick) => fill(box, items.map((it) => h('button', { class: 'chip' + (isOn(it) ? ' on' : ''), onclick: () => { pick(it); draw(); } }, it.label)));
  const draw = () => {
    const words = norm(input.value).split(' ').filter(Boolean);
    const found = all.filter((m) => (!lang || m.langs.includes(lang)) && m.year >= dec[1] && m.year <= dec[2] && words.every((w) => m.key.includes(w)));
    chipRow(decBox, DECADES.map((d) => ({ label: d[0], d })), (it) => it.d === dec, (it) => { dec = it.d; shown = 60; });
    const langs = [...new Set(all.flatMap((m) => m.langs))];
    const count = (l) => all.filter((m) => m.langs.includes(l)).length;
    langs.sort((a, b) => count(b) - count(a));
    chipRow(langBox, [{ label: 'All languages', l: '' }, ...langs.slice(0, 10).map((l) => ({ label: l, l }))], (it) => it.l === lang, (it) => { lang = it.l; shown = 60; });
    fill(list,
      h('div', { class: 'note' }, found.length === 1 ? '1 movie' : `${found.length.toLocaleString()} movies`),
      found.slice(0, shown).map(movieRow),
      found.length > shown ? h('button', { class: 'chip', style: 'margin:12px 16px', onclick: () => { shown += 100; draw(); } }, 'Show more') : null);
  };
  input.addEventListener('input', () => { clearTimeout(draw.t); draw.t = setTimeout(() => { shown = 60; draw(); }, 200); });
  Movies.load().then((l) => { all = l; if (!l.length) fill(list, h('div', { class: 'empty' }, "Movies aren't ready yet. Try again later.")); else draw(); });
  return h('div', null, header('🎬 Movies', true), h('div', { class: 'search-box' }, input), langBox, decBox, list);
}
function movieRow(m) {
  const by = [m.music.length ? `Music: ${m.music.slice(0, 2).join(', ')}` : '', m.directors.length ? `Director: ${m.directors[0]}` : ''].filter(Boolean).join(' · ');
  return h('div', { class: 'movie-row', onclick: () => pushPage(() => moviePage(m)) },
    h('div', { class: 't' }, m.title, h('span', { class: 'y' }, ` ${m.year || ''}`)),
    h('div', { class: 'note', style: 'padding:0' }, [m.langs.join(', '), by].filter(Boolean).join(' · ')),
    m.cast.length ? h('div', { class: 'note', style: 'padding:0' }, m.cast.slice(0, 3).join(', ')) : null);
}
/** A film: its details (tap a name for that person's other films) and its songs to play one after another. */
function moviePage(m) {
  const songs = h('div', null, h('div', { class: 'spinner' }));
  const people = (label, names) => names.length ? h('div', { class: 'detail' }, h('b', null, label),
    names.map((n) => h('button', { class: 'chip', onclick: () => pushPage(() => moviesPage(n)) }, n))) : null;
  (async () => {
    const found = [...Catalog.search(m.title, 40), ...(await Deep.search(m.title))];
    const albums = (await Albums.find(m.title, found)).filter((a) => !m.year || !a.year || Math.abs(a.year - m.year) <= 1);
    let tracks = albums[0]?.tracks || [];
    if (!tracks.length) tracks = (await Tube.search(`${m.title} ${m.year || ''} movie songs`)).map((t) => Catalog.match(t) || t);
    fill(songs, tracks.length ? [
      h('div', { class: 'actions' },
        h('button', { class: 'pill primary', onclick: () => Player.play(tracks) }, icon('play'), 'Play'),
        h('button', { class: 'pill', onclick: () => Player.play(shuffle(tracks)) }, icon('shuffle'), 'Shuffle'),
        h('button', { class: 'pill', onclick: () => { S.playlists.unshift({ id: String(Date.now()), name: m.title, tracks: tracks.map(slim) }); save(); toast('Saved to your playlists'); } }, icon('plus'), 'Save')),
      trackList(tracks)] : h('div', { class: 'empty' }, "This movie's songs aren't here yet."));
  })();
  return h('div', null, header(m.title, true),
    h('div', { class: 'movie-details' },
      h('div', { class: 'note', style: 'padding:0' }, [m.year, m.langs.join(', ')].filter(Boolean).join(' · ')),
      people('Music', m.music), people('Producers', m.producers), people('Director', m.directors), people('Cast', m.cast)),
    songs);
}
function albumCard(a) {
  return h('div', { class: 'card', onclick: async () => {
    const m = await Movies.of(a.title, a.year);
    pushPage(() => (m ? moviePage(m) : songsPage(a.title, a.tracks, null, a)));
  } },
    h('img', { class: 'cover', src: (a.img || '').replace('150x150', '500x500'), loading: 'lazy', alt: '' }),
    h('div', { class: 't' }, a.title),
    h('div', { class: 'note', style: 'padding:0' }, `${a.tracks.length} songs${a.year ? ' · ' + a.year : ''}`));
}

/** All songs of one singer: popular ones first, YouTube when the catalog has only a few. */
function artistPage(name) {
  const body = h('div', null, h('div', { class: 'spinner' }));
  const key = norm(name);
  const cached = artistPage.cache.get(key);
  const show = (songs, yt) => {
    const all = [...songs, ...yt];
    fill(body,
      all.length ? h('div', { class: 'actions' },
        h('button', { class: 'pill primary', onclick: () => Player.play(all) }, icon('play'), 'Play'),
        h('button', { class: 'pill', onclick: () => Player.play(shuffle(all)) }, icon('shuffle'), 'Shuffle')) : null,
      songs.length ? trackList(songs) : null,
      yt.length ? [h('div', { class: 'section' }, 'From YouTube'), trackList(yt)] : null,
      all.length ? null : h('div', { class: 'empty' }, 'No songs found'));
  };
  if (cached) show(...cached);
  else (async () => {
    const seen = new Set(), songs = [];
    const add = (t) => {
      const k = norm(t.title);
      if (!seen.has(k) && splitArtists(t.artist).some((a) => norm(a) === key)) { seen.add(k); songs.push(t); }
    };
    Catalog.tracks.forEach(add);
    (await Deep.search(name, 80)).forEach(add);
    let yt = [];
    if (songs.length < 15) yt = (await Tube.search(`${name} songs`)).filter((t) => !seen.has(norm(t.title)));
    artistPage.cache.set(key, [songs, yt]);
    show(songs, yt);
  })();
  return h('div', null, header(name, true), body);
}
artistPage.cache = new Map();
function openArtist(name) {
  UI.closeNowPlaying();
  if (tab === 'feed') document.querySelector('#tabs button[data-tab="search"]').click();
  pushPage(() => artistPage(name));
}

/* Live FM radio (owner, Oct 10): India's stations from the free, keyless Radio Browser directory (CORS *; CI-probed:
 * ~300 working stations, most over https; Vividh Bharati / AIR are HLS, which the iPhone plays). Only https streams
 * (the site is https). Same source as Android's LiveRadio. */
const Radio = {
  HOSTS: ['all.api.radio-browser.info', 'de1.api.radio-browser.info', 'de2.api.radio-browser.info'],
  async stations() {
    if (this.list && Date.now() - this.at < 3600000) return this.list;
    for (const host of this.HOSTS) {
      try {
        const r = await fetch(`https://${host}/json/stations/search?countrycode=IN&hidebroken=true&order=clickcount&reverse=true&limit=300`);
        if (!r.ok) continue;
        const seen = new Set();
        this.list = (await r.json()).filter((x) => (x.url_resolved || '').startsWith('https://') && x.name && !seen.has(x.name.toLowerCase()) && seen.add(x.name.toLowerCase()))
          .map((x) => {
            const langs = (x.language || '').toLowerCase().split(/[,;]/).map((l) => l.trim()).filter(Boolean);
            return { id: `radio:${x.stationuuid}`, src: 'radio', sid: x.stationuuid, title: x.name.replace(/\s+/g, ' ').trim(),
              artist: `Live radio${langs[0] ? ' · ' + langs[0][0].toUpperCase() + langs[0].slice(1) : ''}`, album: 'Live radio',
              img: (x.favicon || '').startsWith('https://') ? x.favicon : '', media: x.url_resolved, dur: 0, lang: langs[0] || '', langs };
          });
        this.at = Date.now();
        return this.list;
      } catch {}
    }
    return [];
  },
};
function radioPage() {
  const list = h('div', null, h('div', { class: 'spinner' }));
  const chips = h('div', { class: 'chips' });
  Radio.stations().then((all) => {
    if (!all.length) return fill(list, h('div', { class: 'empty' }, "Couldn't load radio stations. Check your internet."));
    const count = {};
    all.forEach((x) => x.langs.forEach((l) => { count[l] = (count[l] || 0) + 1; }));
    const langs = [...new Set([...S.langs, ...Object.keys(count).sort((a, b) => count[b] - count[a])])].filter((l) => count[l]).slice(0, 10);
    const show = (l) => {
      chips.querySelectorAll('.chip').forEach((b) => b.classList.toggle('on', b.dataset.l === l));
      fill(list, trackList(l === 'all' ? all : all.filter((x) => x.langs.includes(l))));
    };
    fill(chips, ['all', ...langs].map((l) => h('button', { class: 'chip', 'data-l': l, onclick: () => show(l) }, l === 'all' ? 'All' : l[0].toUpperCase() + l.slice(1))));
    show('all');
  });
  return h('div', null, header('Live radio', true), chips, list);
}
/* Listen together (owner, Oct 10): phones in one room play the same song at the same moment. Same messages as
 * Android's Together.kt, through ntfy.sh (free, keyless, CORS *; topic "sangeet-tg-<code>"):
 * {v:1, k:'s', from, o: <song, as in #play links>, n: title, p: seconds, on: playing} from the host, on a change and
 * every 5 minutes (ntfy allows ~250 messages a day), and {v:1, k:'h', from} when a guest joins (the host answers). */
const Together = {
  NTFY: 'https://ntfy.sh',
  LETTERS: 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789', // no 0/O, 1/I
  room: null, // { code, host, note }
  me: Math.random().toString(36).slice(2, 10),
  topic(code) { return 'sangeet-tg-' + code.toLowerCase(); },
  link(code) { return location.origin + location.pathname + '#together=' + code; },
  /** "ABC234", "abc 234" or a link with #together=ABC234 → "ABC234"; null when it isn't a code. */
  clean(text) {
    const m = String(text || '').match(/together=([A-Za-z0-9]+)/);
    const c = (m ? m[1] : String(text || '').replace(/[^A-Za-z0-9]/g, '')).toUpperCase();
    return c.length === 6 ? c : null;
  },
  start() {
    const code = Array.from({ length: 6 }, () => this.LETTERS[Math.floor(Math.random() * this.LETTERS.length)]).join('');
    this.open({ code, host: true, note: 'Send the code or link to your friends' });
  },
  join(text) {
    const code = this.clean(text);
    if (!code) return false;
    if (this.room?.code === code) return true;
    this.open({ code, host: false, note: 'Waiting for the host…' });
    this.send({ k: 'h' });
    return true;
  },
  leave() {
    this.es?.close();
    this.es = null;
    clearInterval(this.timer);
    clearInterval(this.seekTimer);
    this.room = null;
    this.following = null;
    this.changed();
  },
  open(room) {
    this.leave();
    this.room = room;
    this.es = new EventSource(`${this.NTFY}/${this.topic(room.code)}/sse`);
    this.es.onmessage = (e) => {
      try {
        const ev = JSON.parse(e.data);
        if (ev.event && ev.event !== 'message') return;
        const m = JSON.parse(ev.message);
        if (m.from === this.me || this.room?.code !== room.code) return;
        if (m.k === 'h' && this.room.host) this.send(this.state());
        else if (m.k === 's' && !this.room.host) this.follow(m);
      } catch {}
    };
    if (room.host) {
      let last = {};
      this.timer = setInterval(() => {
        const t = Player.current;
        if (!t) return;
        const on = Player.playing || Player.loading;
        const pos = Player.time();
        const now = Date.now();
        const expected = last.on ? last.pos + (now - last.at) / 1000 : last.pos;
        if (t.id !== last.id || on !== last.on || Math.abs(pos - expected) > 3 || now - (this.sentAt || 0) > 300000) {
          last = { id: t.id, on, pos, at: now };
          this.send(this.state());
        }
      }, 1500);
    }
    this.changed();
  },
  state() {
    const t = Player.current;
    return { k: 's', o: t ? Sync.encode(t) : null, n: t?.title || '', p: Player.time(), on: Player.playing || Player.loading };
  },
  send(msg) {
    if (!this.room) return;
    this.sentAt = Date.now();
    fetch(`${this.NTFY}/${this.topic(this.room.code)}`, { method: 'POST', body: JSON.stringify({ v: 1, from: this.me, ...msg }) }).catch(() => {});
  },
  /** A guest: play the host's song from where the host is. */
  follow(m) {
    const t = m.o && Sync.decode(m.o);
    if (!t) return this.note(`The host is playing ${m.n || 'a song'}, which can't be shared (a phone file or radio)`);
    this.note('Playing with the host');
    const got = Date.now();
    const target = () => (m.p || 0) + (m.on ? 0.4 + (Date.now() - got) / 1000 : 0); // the message is a moment old
    const same = this.following === t.id && Player.current && (Player.current.id === t.id || Player.current.id === this.followingNow);
    if (!same) {
      this.following = t.id;
      this.followingNow = null;
      Player.play([Catalog.byId.get(t.id) || t], 0);
      // Once it has loaded (a YouTube song may become the catalog's copy): jump to the host's spot, pause if the host has.
      let tries = 0;
      clearInterval(this.seekTimer);
      this.seekTimer = setInterval(() => {
        this.followingNow = Player.current?.id;
        if (++tries > 40 || this.following !== t.id) return clearInterval(this.seekTimer);
        if (Player.loading || !(Player.duration() > 0)) return;
        clearInterval(this.seekTimer);
        if (Math.abs(Player.time() - target()) > 2) Player.seek(target());
        if (!m.on) Player.pause();
      }, 500);
      return;
    }
    if (Math.abs(Player.time() - target()) > 2.5) Player.seek(target());
    if (m.on && !Player.playing && !Player.loading) Player.resume();
    if (!m.on && Player.playing) Player.pause();
  },
  note(text) {
    if (!this.room) return;
    this.room.note = text;
    this.changed();
  },
  changed() {
    this.onchange?.();
    UI.update?.();
  },
  /** Opened a friend's room link (#together=ABC234). */
  fromHash() {
    const m = location.hash.match(/together=([A-Za-z0-9]+)/);
    if (!m) return;
    history.replaceState(null, '', location.pathname);
    this.join(m[1]);
    if (tab !== 'library') document.querySelector('#tabs button[data-tab="library"]')?.click();
    pushPage(togetherPage);
  },
};
function togetherPage() {
  const box = h('div');
  const draw = () => {
    const r = Together.room;
    if (!r) {
      const input = h('input', { type: 'text', placeholder: 'Room code or link', autocapitalize: 'characters', spellcheck: false, style: 'width:100%' });
      fill(box,
        h('div', { class: 'note' }, "Play the same song at the same moment on your friends' phones (iPhone or Android)."),
        h('button', { class: 'pill primary', style: 'margin:8px 16px', onclick: () => Together.start() }, 'Start a room'),
        h('div', { class: 'section' }, 'Or join a friend'),
        h('div', { class: 'setting', style: 'display:block' }, input),
        h('button', { class: 'pill', style: 'margin:0 16px 8px', onclick: () => { if (!Together.join(input.value)) toast('A room code has 6 letters and numbers'); } }, 'Join'));
      return;
    }
    const invite = () => {
      const url = Together.link(r.code);
      if (navigator.share) navigator.share({ title: 'Listen with me on Sangeet', url }).catch(() => {});
      else navigator.clipboard?.writeText(url).then(() => toast('Link copied'));
    };
    fill(box,
      h('div', { class: 'section' }, r.host ? 'Your room' : "In a friend's room"),
      h('div', { class: 'room-code' }, r.code),
      h('div', { class: 'note' }, r.note || ''),
      h('div', { class: 'actions' },
        r.host ? h('button', { class: 'pill primary', onclick: invite }, 'Invite friends') : null,
        h('button', { class: 'pill', onclick: () => Together.leave() }, 'Leave')));
  };
  Together.onchange = () => { if (box.isConnected) draw(); };
  draw();
  return h('div', null, header('Listen together', true), box);
}
/** "Song of the day": one song picked for you each day (from your suggestions, never the same one twice). */
function songOfTheDay() {
  const day = new Date().toDateString();
  if (!S.sotd || S.sotd.day !== day) {
    const shown = new Set(S.sotdShown || []);
    const pick = suggestions(30).find((t) => !shown.has(t.id) && !isLiked(t));
    if (!pick) return null;
    S.sotd = { day, t: slim(pick) };
    S.sotdShown = [...(S.sotdShown || []), pick.id].slice(-400);
    save();
  }
  const t = S.sotd.t;
  return h('div', { class: 'row sotd', onclick: () => Player.playRadio(t) },
    h('img', { class: 'art', src: art(t), loading: 'lazy', alt: '' }),
    h('div', { class: 'meta' }, h('div', { class: 's' }, 'Song of the day'), h('div', { class: 't' }, t.title), h('div', { class: 's' }, t.artist)),
    h('button', { class: 'icon-btn', 'aria-label': 'Play', onclick: (e) => { e.stopPropagation(); Player.playRadio(t); } }, icon('play')));
}
function libraryPage() {
  const link = (ic, label, count, fn) => h('div', { class: 'link-row', onclick: fn }, icon(ic), label, count != null ? h('span', { class: 'count' }, count) : null);
  const charts = Catalog.playlists.filter((p) => p.chart);
  return h('div', null,
    header('Library'),
    songOfTheDay(),
    link('sparkles', 'AI DJ', null, () => pushPage(() => djPage())),
    link('heartFill', 'Liked Songs', S.liked.length, () => pushPage(() => songsPage('Liked Songs', S.liked))),
    link('clock', 'Recently Played', null, () => pushPage(() => songsPage('Recently Played', recent().slice(0, 300)))),
    link('globe', 'Online Library', Catalog.playlists.length || null, () => pushPage(onlineLibraryPage)),
    link('film', 'Movies', null, () => pushPage(() => moviesPage())),
    link('radio', 'Live radio', null, () => pushPage(radioPage)),
    link('people', 'Listen together', Together.room ? Together.room.code : null, () => pushPage(togetherPage)),
    Offline.only() ? h('div', { class: 'offline-banner', onclick: () => pushPage(downloadsPage) },
      navigator.onLine ? 'Offline mode is on: only downloaded songs play.' : "You're offline. Your downloaded songs still play →") : null,
    link('download', 'Downloads', Offline.ids.size || null, () => pushPage(downloadsPage)),
    link('chart', 'Your Stats', null, () => pushPage(statsPage)),
    link('heartFill', 'Blend with a friend', null, () => pushPage(blendPage)),
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
    madeForYou(),
    charts.length ? h('div', { class: 'section' }, 'Top charts') : null,
    charts.length ? h('div', { class: 'grid' }, charts.slice(0, 12).map(playlistCard)) : null);
}

/* ------------------------------------------------------------------ "Made for you" mixes, rebuilt once a day */
let mixCache = null;
function madeForYou() {
  const day = new Date().toDateString();
  if (!mixCache || mixCache.day !== day || mixCache.total !== Catalog.total) {
    const mixes = [];
    const favs = new Map();
    const add = (t, w) => splitArtists(t.artist).forEach((a) => favs.set(a, (favs.get(a) || 0) + w));
    Object.values(S.history).forEach((r) => add(r.t, r.c));
    S.liked.forEach((t) => add(t, 3));
    const top = [...favs.entries()].sort((a, b) => b[1] - a[1]).map((x) => x[0]).slice(0, 3);
    const fresh = newFromYourSingers(30);
    if (fresh.length >= 5) mixes.push({ title: '🆕 New from your singers', tracks: fresh });
    const daily = suggestions(30);
    if (daily.length >= 10) mixes.push({ title: 'Daily Mix', tracks: daily });
    for (const a of top) {
      const n = norm(a);
      const songs = shuffle(Catalog.tracks.filter((t) => norm(t.artist).includes(n))).slice(0, 30);
      if (songs.length >= 8) mixes.push({ title: `${a} Mix`, tracks: songs });
    }
    for (const l of S.langs.slice(0, 3)) {
      const pool = Catalog.data[l]?.tracks || [];
      const songs = shuffle(pool.slice(0, 3000)).slice(0, 30);
      if (songs.length >= 10) mixes.push({ title: `${l[0].toUpperCase() + l.slice(1)} Mix`, tracks: songs });
    }
    // A different cover on every mix.
    const used = new Set();
    for (const m of mixes) {
      const i = m.tracks.findIndex((t) => t.img && !used.has(t.img));
      if (i > 0) m.tracks.unshift(m.tracks.splice(i, 1)[0]);
      if (m.tracks[0]?.img) used.add(m.tracks[0].img);
    }
    mixCache = { day, total: Catalog.total, mixes };
  }
  const mixes = mixCache.mixes;
  if (!mixes.length) return null;
  return [h('div', { class: 'section' }, 'Made for you'),
    h('div', { class: 'grid' }, mixes.map((m) => h('div', { class: 'card', onclick: () => pushPage(() => songsPage(m.title, m.tracks, null, m)) },
      h('img', { class: 'cover', src: art(m.tracks[0], true), loading: 'lazy', alt: '' }),
      h('div', { class: 't' }, m.title))))];
}

/* ------------------------------------------------------------------ move the library between phones (same link format as Android) */
const Sync = {
  async pack(text) {
    const stream = new Blob([text]).stream().pipeThrough(new CompressionStream('deflate-raw'));
    const bytes = new Uint8Array(await new Response(stream).arrayBuffer());
    let bin = '';
    for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  },
  async unpack(data) {
    const b64 = data.replace(/-/g, '+').replace(/_/g, '/');
    const bin = atob(b64 + '='.repeat((4 - (b64.length % 4)) % 4));
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0));
    const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
    return new Response(stream).text();
  },
  encode(t) {
    if (t.src === 'js') {
      const m = t.media && (t.media.startsWith('http') ? t.media : `${AAC}${t.media}_96.mp4`);
      return { s: 'js', i: t.sid, t: t.title, a: t.artist, al: t.album || '', d: t.dur || 0, img: art(t, true), m, h: t.hq ? 1 : 0 };
    }
    if (t.src === 'yt') return { s: 'yt', i: t.sid, t: t.title, a: t.artist, d: t.dur || 0, img: t.img };
    return null;
  },
  decode(o) {
    if (!o || !o.i || !o.t) return null;
    if (o.s === 'js') return { id: 'js:' + o.i, src: 'js', sid: o.i, title: o.t, artist: o.a || '', album: o.al || '', dur: o.d || 0, img: (o.img || '').replace('500x500', '150x150'), media: o.m || '', hq: o.h === 1, lang: '' };
    if (o.s === 'yt') return { id: 'yt:' + o.i, src: 'yt', sid: o.i, title: o.t, artist: o.a || '', album: 'YouTube', dur: o.d || 0, img: o.img || '', lang: '' };
    return null;
  },
  async link() {
    const data = {
      v: 1,
      l: S.liked.map((t) => this.encode(t)).filter(Boolean),
      p: S.playlists.map((p) => ({ n: p.name, t: p.tracks.map((t) => this.encode(t)).filter(Boolean) })),
    };
    return location.origin + location.pathname + '#sync=' + (await this.pack(JSON.stringify(data)));
  },
  async share() {
    const url = await this.link();
    if (navigator.share) navigator.share({ title: 'My Sangeet library', url }).catch(() => {});
    else navigator.clipboard?.writeText(url).then(() => toast('Link copied'));
  },
  /** Opened a library link from another phone: ask, then add its liked songs and playlists. */
  async importFromHash() {
    const m = location.hash.match(/sync=([\w-]+)/);
    if (!m) return;
    history.replaceState(null, '', location.pathname);
    try {
      const d = JSON.parse(await this.unpack(m[1]));
      const liked = (d.l || []).map((o) => this.decode(o)).filter(Boolean);
      const lists = (d.p || []).map((p) => ({ name: p.n || 'Imported playlist', tracks: (p.t || []).map((o) => this.decode(o)).filter(Boolean) })).filter((p) => p.tracks.length);
      if (!confirm(`Add ${liked.length} liked songs and ${lists.length} playlists from your other phone?`)) return;
      const have = new Set(S.liked.map((t) => t.id));
      liked.forEach((t) => { if (!have.has(t.id)) S.liked.push(t); });
      lists.forEach((p) => S.playlists.push({ id: String(Date.now() + Math.random()), name: p.name, tracks: p.tracks }));
      save();
      toast('Library added');
    } catch {
      toast("That library link didn't work");
    }
  },
};
function playlistCard(p) {
  return h('div', { class: 'card', onclick: () => pushPage(() => songsPage(p.title, p.tracks, null, p)) },
    h('img', { class: 'cover', src: (p.img || '').replace('150x150', '500x500'), loading: 'lazy', alt: '' }),
    h('div', { class: 't' }, p.title));
}
/** Library → Your Stats: how much you listened, streak, favourite time, top singers and songs. */
function statsPage() {
  const st = S.stats || { ms: 0, days: {}, hours: Array(24).fill(0) };
  const dur = (ms) => { const m = Math.round(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)} h ${m % 60} min` : `${m} min`; };
  const ago = (k) => dayKey(new Date(Date.now() - k * 864e5));
  let week = 0;
  for (let k = 0; k < 7; k++) week += st.days[ago(k)] || 0;
  let streak = 0;
  for (let k = (st.days[ago(0)] || 0) >= 60000 ? 0 : 1; (st.days[ago(k)] || 0) >= 60000; k++) streak++;
  const hour = st.hours.indexOf(Math.max(...st.hours));
  const hourName = (x) => `${((x + 11) % 12) + 1} ${x < 12 ? 'AM' : 'PM'}`;
  const plays = Object.values(S.history).sort((a, b) => b.c - a.c);
  const singers = new Map();
  for (const r of plays) { const a = splitArtists(r.t.artist)[0]; if (a) singers.set(a, (singers.get(a) || 0) + r.c); }
  const topSingers = [...singers.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8);
  const topSongs = plays.slice(0, 20).map((r) => r.t);
  return h('div', null, header('Your Stats', true),
    h('div', { class: 'actions' }, h('button', { class: 'pill primary', onclick: shareWrapped }, '🎁 Share my Wrapped')),
    h('div', { class: 'stats-hero' },
      h('div', { class: 'small' }, 'You listened for'),
      h('div', { class: 'big' }, dur(st.ms)),
      h('div', null, `This week: ${dur(week)}`),
      streak ? h('div', null, `🔥 ${streak}-day listening streak`) : null,
      st.ms > 60000 ? h('div', null, `Your favourite time: ${hourName(hour)}`) : null,
      topSingers[0] ? h('div', null, `Top singer: ${topSingers[0][0]}`) : null),
    topSingers.length ? [h('div', { class: 'section' }, 'Top singers'), topSingers.map(([name, n]) =>
      h('div', { class: 'row', onclick: () => pushPage(() => artistPage(name)) },
        h('div', { class: 'art avatar' }, Array.from(name)[0]),
        h('div', { class: 'meta' }, h('div', { class: 't' }, name), h('div', { class: 's' }, `${n} ${n === 1 ? 'play' : 'plays'}`))))] : null,
    topSongs.length ? [h('div', { class: 'section' }, 'Most played'), trackList(topSongs)] : h('div', { class: 'empty' }, 'Play some songs and your stats show up here.'));
}

/** Library → Downloads: what is downloading (with %), and every song saved in the app. */
function downloadsPage() {
  const body = h('div');
  const render = async () => {
    const saved = await Offline.songs();
    const tracks = saved.map((r) => Catalog.byId.get(r.t.id) || r.t);
    const mb = (n) => (n / 1048576).toFixed(n > 104857600 ? 0 : 1) + ' MB';
    const going = [...Offline.active.values(), ...Offline.queue.map((t) => ({ t, pct: -1 }))];
    fill(body,
      h('div', { class: 'note' }, `${tracks.length} songs · ${mb(Offline.bytes())} on this iPhone. They play without internet. Keep the app open while songs download.`),
      tracks.length ? h('div', { class: 'actions' },
        h('button', { class: 'pill primary', onclick: () => Player.play(tracks) }, icon('play'), 'Play'),
        h('button', { class: 'pill', onclick: () => Player.play(shuffle(tracks)) }, icon('shuffle'), 'Shuffle')) : null,
      going.length ? [h('div', { class: 'section' }, 'Downloading'), going.map(({ t, pct }) =>
        h('div', { class: 'row' },
          h('img', { class: 'art', src: art(t), alt: '' }),
          h('div', { class: 'meta' }, h('div', { class: 't' }, t.title),
            h('div', { class: 'progress' }, h('div', { style: `width:${Math.max(pct, 0)}%` })),
            h('div', { class: 's' }, pct < 0 ? 'Waiting…' : `${pct}%`))))] : null,
      tracks.length ? [h('div', { class: 'section' }, 'Saved in the app'), tracks.map((t, i) =>
        h('div', { class: 'row', onclick: () => Player.play(tracks, i) },
          h('img', { class: 'art', src: art(t), loading: 'lazy', alt: '' }),
          h('div', { class: 'meta' }, h('div', { class: 't' }, t.title), h('div', { class: 's' }, `${t.artist} · ${mb(Offline.sizes.get(t.id) || 0)}`)),
          h('button', { class: 'icon-btn', 'aria-label': 'Save to Files', onclick: (e) => { e.stopPropagation(); Offline.saveToFiles(t); } }, icon('files')),
          h('button', { class: 'icon-btn', 'aria-label': 'Remove download', onclick: (e) => { e.stopPropagation(); Offline.remove(t.id); } }, icon('close'))))]
        : going.length ? null : h('div', { class: 'empty' }, 'No downloads yet. Tap ⋯ on a song and choose Download, or Download on a playlist.'),
      tracks.length ? h('div', { class: 'note' }, '📁 Save to Files keeps a copy in the iPhone Files app (it stays even if Sangeet is removed).') : null);
  };
  Offline.onchange = () => { if (document.body.contains(body)) render(); };
  render();
  return h('div', null, header('Downloads', true), body);
}

function songsPage(title, tracks, mine, online) {
  const actions = tracks.length ? h('div', { class: 'actions' },
    h('button', { class: 'pill primary', onclick: () => Player.play(tracks) }, icon('play'), 'Play'),
    h('button', { class: 'pill', onclick: () => Player.play(shuffle(tracks)) }, icon('shuffle'), 'Shuffle'),
    h('button', { class: 'pill', onclick: () => Offline.add(tracks) }, icon('download'), 'Download'),
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
  [['sad', 'dukh', 'dukhi', 'udaas', 'udas', 'rona', 'tanhai', 'bewafa', 'breakup', 'heartbreak', 'dard', 'emotional'], ['sad', 'heartbreak', 'broken', 'dard', 'emotional', 'judaai']],
  // Third list: close moods, used when few playlists are named after the mood itself.
  [['happy', 'khush', 'khushi', 'mast', 'masti', 'cheerful', 'good mood', 'upbeat'], ['happy', 'feel good', 'good vibes', 'cheerful'], ['party', 'dance', 'celebration', 'wedding', 'bhangra']],
  [['party', 'dance', 'club', 'naach', 'dj', 'dhamaal', 'bhangra', 'birthday'], ['party', 'dance', 'club', 'dj', 'bhangra']],
  [['romantic', 'love', 'pyaar', 'pyar', 'ishq', 'mohabbat', 'crush', 'date'], ['romantic', 'love', 'romance', 'ishq', 'pyaar']],
  [['chill', 'lofi', 'relax', 'calm', 'sukoon', 'study', 'padhai', 'focus'], ['lofi', 'chill', 'relax', 'calm', 'acoustic', 'unplugged']],
  [['sleep', 'neend', 'night', 'raat', 'lori'], ['night', 'sleep', 'soft', 'calm'], ['lofi', 'chill', 'acoustic', 'unplugged']],
  [['gym', 'workout', 'running', 'exercise', 'josh', 'motivation'], ['workout', 'gym', 'power', 'motivation']],
  [['bhakti', 'bhajan', 'devotional', 'god', 'mandir', 'aarti', 'bhagwan', 'krishna', 'shiv', 'hanuman'], ['bhakti', 'devotional', 'bhajan', 'aarti', 'spiritual']],
  [['drive', 'road trip', 'travel', 'safar', 'long drive', 'journey'], ['drive', 'road', 'travel', 'trip']],
  [['rain', 'barish', 'baarish', 'monsoon'], ['rain', 'monsoon', 'barish', 'baarish'], ['romantic', 'love', 'chill']],
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
  const era = /\b(90s|nineties|purane|purani|old|older|retro|classic|sadabahar)\b/.test(q) ? 'old' : /\b(new|newer|naye|nayi|latest|trending|20\d\d)\b/.test(q) ? 'new' : '';
  // Singers named in the request (matched against the library's own artists).
  const artistSet = new Set();
  // A singer's full name, or just a first name that's long enough ("arijit", "diljit") - but never a word that
  // means a mood, a language or an era ("happy" is not Happy Raikoti, "punjabi" not Punjabi Outlawz).
  const reserved = new Set([...ALL_LANGS, 'bollywood', 'songs', 'music', 'remix', 'latest', 'purane', 'naye', 'retro', 'classic',
    ...MOODS.flatMap((m) => m.flat().flatMap((k) => k.split(' ')))]);
  for (const t of tracks) for (const a of splitArtists(t.artist)) {
    const n = norm(a), first = n.split(' ')[0];
    if ((n.length > 3 && !reserved.has(n) && q.includes(' ' + n + ' ')) ||
        (first.length >= 5 && n.includes(' ') && !reserved.has(first) && q.includes(' ' + first + ' '))) artistSet.add(n);
  }
  // A mood's songs come from playlists made for it ("Feel Good Hindi"), never from the word being in a song's
  // name or singer (Happy Raikoti, "Happy Birthday").
  const has = (text, k) => (' ' + norm(text) + ' ').includes(' ' + k + ' ');
  const moodTracks = new Set();
  if (mood) for (const p of pls) if (mood[1].some((k) => has(p.title, k))) p.tracks.forEach((t) => moodTracks.add(t.id));
  if (mood && mood[2] && moodTracks.size < 30) for (const p of pls) if (mood[2].some((k) => has(p.title, k))) p.tracks.forEach((t) => moodTracks.add(t.id));
  const moodWords = mood ? new Set([...mood[0], ...mood[1]].flatMap((k) => k.split(' '))) : new Set();
  const stop = new Set(['songs', 'song', 'for', 'a', 'the', 'and', 'with', 'of', 'me', 'my', 'music', 'gaane', 'gane', 'play', 'some', 'best', 'hits', ...langs]);
  const words = norm(text).split(' ').filter((w) => w.length > 2 && !stop.has(w) && !moodWords.has(w));
  // Other topic words ("bollywood", "punjabi party", "holi"): songs of playlists named so count most.
  const topicTracks = new Set();
  if (words.length) for (const p of pls) if (words.some((w) => has(p.title, w))) p.tracks.forEach((t) => topicTracks.add(t.id));
  const strictMood = mood && moodTracks.size >= 15;
  const scored = [];
  for (const t of tracks) {
    let s = 0;
    if (artistSet.size) { if (splitArtists(t.artist).some((a) => artistSet.has(norm(a)))) s += 5; else continue; }
    if (mood) {
      if (moodTracks.has(t.id)) s += 4;
      else if (strictMood || [...moodWords].some((k) => has(t.title + ' ' + t.artist, k))) continue;
    }
    if (topicTracks.has(t.id)) s += 2;
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
/** Understands a chat message for the DJ: a follow-up ("aur", "sirf Arijit", "no remix") changes the last mix. */
/* AI DJ helper (Oct 9, owner: "a free AI, not one that gives wrong data"): a free online AI (LLM7.io, no key)
 * names songs for the request; a song is kept only when the catalog has that very song by that singer, so nothing
 * made-up plays. Models picked by CI probes that checked every named song on JioSaavn (CLAUDE.md). Settings can
 * turn it off; any failure just leaves the built-in DJ's mix. */
const BUILT_IN_GEMINI = '__GEMINI_KEY__'; // filled in by web.yml from the GEMINI_API_KEY secret
const FreeAi = {
  URL: 'https://api.llm7.io/v1/chat/completions',
  GEMINI: 'https://generativelanguage.googleapis.com/v1beta',
  geminiKey() { return (S.geminiKey || '').trim() || (BUILT_IN_GEMINI.startsWith('__') ? '' : BUILT_IN_GEMINI); },
  /** The best Flash model the key may use (a plain Flash, newest first; else Flash-Lite). Asked once. */
  async geminiModel(key, signal) {
    if (this.model) return this.model;
    const r = await fetch(`${this.GEMINI}/models?pageSize=200&key=${key}`, { signal });
    if (!r.ok) return null;
    const names = ((await r.json()).models || []).filter((m) => (m.supportedGenerationMethods || []).includes('generateContent')).map((m) => m.name);
    const ver = (n) => parseFloat((n.match(/gemini-(\d+(?:\.\d+)?)/) || [])[1] || 0);
    const best = (list) => list.sort((a, b) => ver(b) - ver(a))[0];
    this.model = best(names.filter((n) => /^models\/gemini-\d+(\.\d+)?-flash(-latest)?$/.test(n)))
      || best(names.filter((n) => n.includes('flash-lite') && !n.includes('preview'))) || best(names.filter((n) => n.includes('flash'))) || null;
    return this.model;
  },
  async askGemini(key, user, signal) {
    const model = await this.geminiModel(key, signal);
    if (!model) return '';
    const r = await fetch(`${this.GEMINI}/${model}:generateContent?key=${key}`, { method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ systemInstruction: { parts: [{ text: this.SYSTEM }] }, contents: [{ role: 'user', parts: [{ text: user }] }],
        generationConfig: { temperature: 0.4, maxOutputTokens: 4000, responseMimeType: 'application/json' } }) });
    if (!r.ok) { if (r.status === 404) this.model = null; return ''; }
    return ((await r.json()).candidates?.[0]?.content?.parts || []).map((p) => p.text || '').join('');
  },
  // Keyless on LLM7.io (CI probe, Oct 9); a busy (503) or used-up (429) one is skipped. Same list as Android FreeAi.
  MODELS: ['glm-5.2', 'DeepSeek-V4-Flash-0731', 'minimax-m3', 'gemma4:31b'],
  SYSTEM: 'You are the DJ of Sangeet, an Indian music app (JioSaavn and YouTube catalogue). Turn the listener\'s request '
    + '(English, Hindi or Hinglish) into a playlist. Reply ONLY with JSON: {"title":str,"languages":[str],"songs":["Song - Singer"]}. '
    + 'songs: 12 real, released songs that fit, with their exact title and main singer. Never invent a song; if you know '
    + 'fewer, give fewer. Mix famous hits with less obvious ones and different singers.',
  async songs(text, history = []) {
    if (S.freeAi === false || !navigator.onLine) return [];
    const top = Object.values(S.history).sort((a, b) => b.c - a.c).slice(0, 40);
    const singers = [...new Set(top.map((r) => (r.t.artist || '').split(',')[0].trim()).filter(Boolean))].slice(0, 8);
    const user = `Listener's languages: ${S.langs.join(', ')}. `
      + (singers.length ? `The listener plays these singers most: ${singers.join(', ')}. ` : '')
      + (history.length ? `Earlier in this chat: ${history.slice(-4).map((x) => `"${x}"`).join(' | ')}. Treat the request as a change to that mix if it reads like one. ` : '')
      + `Request: ${text}`;
    const fromText = async (content, who) => {
      const plan = JSON.parse(content.slice(content.indexOf('{'), content.lastIndexOf('}') + 1));
      const names = (plan.songs || []).map((x) => (typeof x === 'string' ? x : x && x.title ? `${x.title} - ${x.artist || x.singer || ''}` : ''))
        .filter((x) => x.length > 1).slice(0, 18);
      const found = (await Promise.all(names.map((x) => this.find(x)))).filter(Boolean);
      console.log(`free AI ${who}: ${names.length} named, ${found.length} real`);
      return found;
    };
    const key = this.geminiKey();
    return (await this.race([
      async (signal) => (key ? fromText(await this.askGemini(key, user, signal), 'Gemini') : null),
      (signal) => this.keyless(this.SYSTEM, user, 3000, signal, (content, model) => fromText(content, model)),
    ], 22000)) || [];
  },
  /**
   * Runs [fns] at the same time and gives the first answer that isn't empty (null if none); the others are stopped.
   * Gemini's free tier takes ~19 s and is often busy (CI probe, Oct 10), so it no longer goes first and alone.
   */
  race(fns, ms) {
    const ctl = new AbortController();
    const timer = setTimeout(() => ctl.abort(), ms);
    return new Promise((done) => {
      let left = fns.length;
      const take = (v) => { if (v && v.length) done(v); else if (--left === 0) done(null); };
      fns.forEach((fn) => Promise.resolve().then(() => fn(ctl.signal)).then(take, () => take(null)));
    }).finally(() => { clearTimeout(timer); ctl.abort(); });
  },
  /** The keyless models in turn (a busy or used-up one is skipped) until [use] makes something of an answer. */
  async keyless(system, user, maxTokens, signal, use) {
    for (const model of this.MODELS) {
      if (signal.aborted) break;
      try {
        const r = await fetch(this.URL, { method: 'POST', signal, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer unused' },
          body: JSON.stringify({ model, temperature: 0.4, max_tokens: maxTokens, messages: [{ role: 'system', content: system }, { role: 'user', content: user }] }) });
        if (!r.ok) continue;
        const v = await use((await r.json()).choices?.[0]?.message?.content || '', model);
        if (v && v.length) return v;
      } catch {}
    }
    return null;
  },
  MEANING: 'You explain Indian song lyrics to listeners. Reply in simple English, in at most 3 short sentences, with no heading '
    + 'and no markdown. If the line is not in English (Hindi, Punjabi, Haryanvi or another language), first give its English '
    + 'translation in quotes, then what it means in the song.',
  /** What a lyrics line means (Gemini first, then the keyless models), or null. Same prompt as Android. */
  async meaning(line, t) {
    if (!navigator.onLine) return null;
    const user = `Song: "${t.title}" by ${(t.artist || '').split(',')[0]}.\nLine: "${line.slice(0, 300)}"`;
    const key = this.geminiKey();
    const clean = (text) => (text || '').replace(/<think>[\s\S]*?<\/think>/g, '').trim();
    return this.race([
      async (signal) => {
        if (!key) return null;
        const model = await this.geminiModel(key, signal);
        if (!model) return null;
        const r = await fetch(`${this.GEMINI}/${model}:generateContent?key=${key}`, { method: 'POST', signal, headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ systemInstruction: { parts: [{ text: this.MEANING }] }, contents: [{ role: 'user', parts: [{ text: user }] }], generationConfig: { temperature: 0.4, maxOutputTokens: 1500 } }) });
        return r.ok ? clean(((await r.json()).candidates?.[0]?.content?.parts || []).map((p) => p.text || '').join('')) : null;
      },
      (signal) => this.keyless(this.MEANING, user, 1500, signal, (content) => clean(content)),
    ], 25000);
  },
  /** The catalog's song for "Song - Singer", or null when there is no such song by that singer. */
  async find(line) {
    const [name, singer = ''] = line.split(/\s+[-–—]\s+|\s+by\s+/);
    const want = norm(name);
    if (want.length < 2) return null;
    const q = `${name} ${singer}`.trim();
    const pool = [...Catalog.search(q, 10), ...(await Deep.search(q, 10))];
    const who = norm(singer), first = who.split(' ')[0];
    return pool.find((t) => {
      const got = norm(t.title), by = norm(t.artist);
      const same = got === want || got.startsWith(`${want} `) || (want.length >= 6 && got.includes(want));
      return same && (!who || by.includes(who) || (first.length >= 4 && by.includes(first)));
    }) || null;
  },
};
const DjChat = {
  FOLLOW: /^(aur|or|more|isme|add|also|only|sirf|bas|bina|without|no |hata|remove|zyada|kam|less|purane|naye|new|old|same|aise|similar|thoda|ab |and |make|change)/i,
  without(text) {
    const t = ` ${norm(text)} `, out = [];
    for (const m of t.matchAll(/(?:bina|without|no|except) ([a-z0-9]+(?: [a-z0-9]+)?)/g)) out.push(m[1]);
    for (const m of t.matchAll(/([a-z0-9]+(?: [a-z0-9]+)?) (?:ke alawa|ke bina|hata|hatao|remove)/g)) out.push(m[1]);
    return out.map((w) => w.replace(/^wale /, '')).filter((w) => w && !['songs', 'gaane', 'gane'].includes(w));
  },
  /** {base, without, more}: the request to run now. */
  next(text, prev) {
    const without = this.without(text);
    if (!prev || !this.FOLLOW.test(text.trim())) return { base: text, without, more: false };
    const extra = norm(text).replace(/\b(aur|or|more|isme|add|also|only|sirf|bas|bina|without|no|hata|hatao|remove|zyada|kam|less|same|aise|similar|thoda|ab|and|make|change|it|this|like|songs|gaane|gane|wale|do|de)\b/g, ' ')
      .split(' ').filter((w) => w && !without.some((x) => x.split(' ').includes(w))).join(' ');
    return { base: `${prev.base} ${extra}`.trim(), without: [...new Set([...prev.without, ...without])], more: !extra && !without.length };
  },
};
async function djMix(req, shown, history = []) {
  const text = Translit.has(req.base) ? Translit.variants(req.base)[0] : req.base;
  const t = ` ${norm(text)} `;
  const fromAi = FreeAi.songs(req.base, history); // the AI reads Hindi script as it is
  let tracks = [];
  // "Kesariya jaise gaane", "songs like Kesariya": that song, then songs like it.
  const like = (t.match(/(?:songs like|like|similar to) (.+?) $/) || t.match(/^ (.+?) (?:jaise|jaisa|jaisi|type)\b/) || [])[1];
  if (like) {
    const seed = Catalog.search(like, 5)[0] || (await Deep.search(like, 5))[0];
    if (seed) tracks = [seed, ...radio(seed, 40, shown)];
  }
  // A film's name: its album, all its songs.
  if (!tracks.length) {
    const words = norm(text).replace(/\b(movie|film|album|songs|song|gaane|gane|ke|ki|ka)\b/g, ' ').trim();
    const albums = words && !MOODS.some(([keys]) => keys.some((k) => t.includes(` ${k} `)))
      ? (await Albums.find(words, [...Catalog.search(words, 40), ...(await Deep.search(words))])).filter((a) => a.exact) : [];
    if (albums.length) tracks = albums[0].tracks;
  }
  if (!tracks.length) tracks = (await aiDj(text)).tracks;
  // Nothing fits everything ("sad punjabi" + "only Arijit"): drop the language words, then the mood words.
  if (!tracks.length) {
    const lean = norm(text).split(' ').filter((w) => !ALL_LANGS.includes(w)).join(' ');
    if (lean !== norm(text)) tracks = (await aiDj(lean)).tracks;
    if (!tracks.length) {
      const moodless = lean.split(' ').filter((w) => !MOODS.some((m) => m.flat().includes(w))).join(' ');
      if (moodless && moodless !== lean) tracks = (await aiDj(moodless)).tracks;
    }
  }
  // The built-in DJ's songs now; the free AI's (real ones only, in the asked languages) when they come
  // (out.later), for the DJ page to mix in while the songs play (owner's report, Oct 10: the DJ waited for a slow AI).
  const langsAsked = ALL_LANGS.filter((l) => t.includes(` ${l} `));
  const bad = (x) => req.without.some((w) => norm(`${x.title} ${x.artist}`).includes(w));
  const seen = new Set();
  const out = tracks.filter((x) => !shown.has(x.id) && !bad(x) && !seen.has(x.id) && seen.add(x.id));
  out.later = fromAi.then((ai) => ai.filter((x) => (!langsAsked.length || !x.lang || langsAsked.includes(x.lang)) && !bad(x) && !shown.has(x.id) && !seen.has(x.id)))
    .catch(() => []);
  return out;
}
function djPage(initial) {
  const out = h('div'), chat = h('div');
  const input = h('input', { type: 'text', placeholder: 'What do you want to hear?', enterkeyhint: 'go' });
  let prev = null, turn = 0;
  const shown = new Set(), asked = [];
  const run = async (text) => {
    if (!text.trim()) return;
    input.value = '';
    input.blur();
    const req = DjChat.next(text, prev);
    const mine = ++turn;
    out.replaceChildren(h('div', { class: 'spinner' }));
    const past = asked.slice();
    const mix = await djMix(req, req.more ? shown : new Set(), past);
    if (mine !== turn) return;
    let tracks = mix.slice(0, 60);
    const title = req.base.trim().replace(/^./, (c) => c.toUpperCase()).slice(0, 40);
    const line = h('div', { class: 'note', style: 'padding:0 16px 8px;opacity:.8' }, tracks.length ? `DJ: ${req.more ? `${tracks.length} more` : `${title} · ${tracks.length} songs`}${req.without.length ? ` (no ${req.without.join(', ')})` : ''}` : "DJ: I couldn't find songs for that. Try other words.");
    chat.append(h('div', { class: 'note', style: 'padding:4px 16px' }, `You: ${text}`), line);
    asked.push(text);
    if (!tracks.length) {
      // Nothing built in: the online AI may still find some.
      const ai = (await mix.later).slice(0, 30);
      if (mine !== turn || !ai.length) return out.replaceChildren();
      tracks = ai;
      line.textContent = `DJ: ${title} · ${ai.length} songs · picked by the online AI`;
    }
    prev = req;
    tracks.forEach((x) => shown.add(x.id));
    Player.play(tracks);
    input.placeholder = 'Change it: "only Arijit", "no remix", "more"…';
    const list = h('div', null, trackList(tracks));
    out.replaceChildren(
      h('div', { class: 'chips' }, ['More like this', 'Newer songs', 'No remix', 'Make it sad', 'Also Punjabi'].map((f) => h('button', { class: 'chip', onclick: () => run(f) }, f)),
        h('button', { class: 'chip', onclick: () => { turn++; prev = null; shown.clear(); asked.length = 0; chat.replaceChildren(); out.replaceChildren(); input.placeholder = 'What do you want to hear?'; } }, 'New chat')),
      h('div', { class: 'section' }, title),
      h('div', { class: 'actions' },
        h('button', { class: 'pill primary', onclick: () => Player.play(tracks) }, icon('play'), 'Play'),
        h('button', { class: 'pill', onclick: () => { S.playlists.unshift({ id: String(Date.now()), name: title, tracks: tracks.map(slim) }); save(); toast('Saved to your playlists'); } }, icon('plus'), 'Save')),
      list);
    // The online AI's songs, when they come: between the DJ's songs still to come, if this mix still plays.
    const ai = await mix.later;
    const queued = new Set(Player.queue.map((x) => x.id));
    if (mine !== turn || !tracks.some((x) => queued.has(x.id))) return;
    const extra = ai.filter((x) => !queued.has(x.id)).slice(0, 20);
    if (!extra.length) return;
    let at = Player.i + 1;
    extra.forEach((x) => { Player.queue.splice(Math.min(at, Player.queue.length), 0, x); at += 2; shown.add(x.id); });
    const cur = Math.max(0, tracks.findIndex((x) => x.id === Player.current?.id));
    const rest = tracks.slice(cur + 1), merged = tracks.slice(0, cur + 1);
    for (let i = 0; i < Math.max(extra.length, rest.length); i++) { if (extra[i]) merged.push(extra[i]); if (rest[i]) merged.push(rest[i]); }
    tracks = merged;
    fill(list, trackList(tracks));
    line.textContent += ` · ${extra.length} picked by the online AI`;
    UI.update();
  };
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter') run(input.value); });
  const examples = ['Sad Punjabi songs for a night drive', '90s Bollywood romantic', 'Arijit Singh latest', 'Kesariya jaise gaane', 'Gym workout Hindi', 'Rainy day chill'];
  if (initial) setTimeout(() => run(initial), 0);
  return h('div', null, header('AI DJ', true), h('div', { class: 'search-box' }, input),
    h('div', { class: 'chips' }, examples.map((e) => h('button', { class: 'chip', onclick: () => run(e) }, e))), chat, out);
}

const ACCENTS = [['Coral', '#ff5c6b'], ['Green', '#1db954'], ['Violet', '#8b5cf6'], ['Blue', '#3b82f6'], ['Teal', '#14b8a6'], ['Orange', '#f97316'], ['Pink', '#ec4899'], ['Red', '#ef4444']];
function toggleRow(label, note, on, change) {
  const sw = h('button', { class: 'switch' + (on ? ' on' : ''), role: 'switch', 'aria-label': label, 'aria-checked': String(on), onclick: () => {
    on = !on; sw.classList.toggle('on', on); sw.setAttribute('aria-checked', String(on)); change(on);
  } }, h('span'));
  return h('div', { class: 'setting toggle' }, h('div', null, h('label', null, label), h('div', { class: 'note', style: 'padding:2px 0 0' }, note)), sw);
}
/** "Report a problem": the user writes what happened; it is shared (WhatsApp, email…) with app details. */
function reportProblem() {
  const what = prompt('What went wrong?');
  if (what == null) return;
  const text = `Sangeet (iPhone app) problem\n\n${what || '(not described)'}\n\nVersion ${window.SANGEET_BUILD} · ${navigator.userAgent}\nNow playing: ${Player.current ? `${Player.current.title} (${Player.current.id})` : '-'}\nOffline: ${!navigator.onLine}`;
  if (navigator.share) navigator.share({ title: 'Sangeet problem', text }).catch(() => {});
  else navigator.clipboard?.writeText(text).then(() => toast('Copied. Paste it in a message.'));
}

/** Settings → Sync with another phone. */
function syncBox() {
  const box = h('div');
  const say = (text) => { toast(text); draw(); };
  const draw = () => {
    const code = CloudSync.code;
    if (!code) {
      const input = h('input', { type: 'text', placeholder: 'Sync link or code from your other phone', autocapitalize: 'off', spellcheck: false, style: 'width:100%' });
      fill(box, h('div', { class: 'section' }, 'Sync with another phone'),
        h('div', { class: 'note' }, 'Liked songs and playlists stay the same on both phones (iPhone or Android). No account needed.'),
        h('button', { class: 'pill primary', style: 'margin:8px 16px', onclick: async () => {
          try { const c = await CloudSync.start(); const url = CloudSync.link(c); if (navigator.share) navigator.share({ title: 'Sangeet sync', url }).catch(() => {}); else navigator.clipboard?.writeText(url); say('Sync is on. Open the link on your other phone.'); } catch (e) { say(`Didn't work: ${e.message}`); }
        } }, 'Start sync'),
        h('div', { class: 'setting', style: 'display:block' }, input),
        h('button', { class: 'pill', style: 'margin:0 16px 8px', onclick: async () => {
          try { const [l, p] = await CloudSync.join(input.value); say(`Synced: ${l} liked songs, ${p} playlists`); } catch (e) { say(e.message || "Didn't work"); }
        } }, 'Join'));
    } else {
      fill(box, h('div', { class: 'section' }, 'Sync with another phone'),
        h('div', { class: 'note' }, `Sync is on (code ${code.slice(0, 8)}…). Changes go to your other phone by themselves.`),
        h('div', { class: 'actions' },
          h('button', { class: 'pill', onclick: () => { const url = CloudSync.link(code); if (navigator.share) navigator.share({ title: 'Sangeet sync', url }).catch(() => {}); else navigator.clipboard?.writeText(url).then(() => toast('Link copied')); } }, 'Send sync link'),
          h('button', { class: 'pill', onclick: async () => { try { const r = await CloudSync.sync(); say(r ? `Synced: ${r[0]} liked songs, ${r[1]} playlists` : "This sync code doesn't work any more. Stop and start again."); } catch (e) { say(e.message); } } }, 'Sync now'),
          h('button', { class: 'pill', onclick: () => { CloudSync.set(null); say('Sync stopped on this phone'); } }, 'Stop')));
    }
  };
  draw();
  return box;
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
  const look = h('select', { onchange: (e) => {
    S.look = e.target.value;
    save();
    if (applyLook() && standalone) toast('Close and reopen the app for the top bar (time, battery) colors', 4500);
  } }, LOOKS.map(([v, l]) => h('option', { value: v, selected: (S.look || 'classic') === v }, l)));
  return h('div', null,
    header('Settings'),
    h('div', { class: 'section' }, 'Look'),
    h('div', { class: 'setting' }, h('label', null, 'Theme'), look),
    h('div', { class: 'note' }, 'Liquid Glass: the iOS 26 look, with glass bars floating over the songs.'),
    h('div', { class: 'setting', style: 'display:block' }, h('label', null, 'Accent color'),
      h('div', { class: 'swatches' }, ACCENTS.map(([name, c]) => {
        const b = h('button', { class: 'swatch' + ((S.accent || '#ff5c6b') === c ? ' on' : ''), style: `background:${c}`, 'aria-label': name, onclick: () => {
          S.accent = c; save(); applyLook();
          b.parentNode.querySelectorAll('.swatch').forEach((x) => x.classList.toggle('on', x === b));
        } });
        return b;
      }))),
    h('div', { class: 'section' }, 'Languages'), langChips, count,
    h('div', { class: 'section' }, 'Audio'),
    h('div', { class: 'setting' }, h('label', null, 'Streaming quality'), quality),
    h('div', { class: 'setting', style: 'display:block' }, h('label', null, 'Gemini API key (optional)'), (() => {
      const k = h('input', { type: 'text', value: S.geminiKey || '', autocapitalize: 'off', spellcheck: false, placeholder: BUILT_IN_GEMINI.startsWith('__') ? 'Free at aistudio.google.com' : 'Built in (or paste your own)', style: 'font-family:ui-monospace,monospace;font-size:13px;width:100%' });
      k.addEventListener('change', () => { S.geminiKey = k.value.trim(); FreeAi.model = null; save(); toast(S.geminiKey ? 'Gemini key saved' : 'Gemini key removed'); });
      return k;
    })()),
    toggleRow('Free online AI for the DJ',
      'The AI DJ also asks a free online AI (Gemini with a key, else LLM7.io: GLM, DeepSeek) for songs. Only songs that really exist are played. It gets your request and the singers you play most.',
      S.freeAi !== false, (on) => { S.freeAi = on; save(); }),
    h('div', { class: 'section' }, 'YouTube Data API key'),
    h('div', { class: 'setting' }, key),
    h('div', { class: 'note' }, 'Used for search results. Leave empty to turn off.'),
    toggleRow('YouTube in background (experimental)',
      'YouTube songs keep playing with the screen locked, through free public servers. They are sometimes slow or down; then YouTube plays as before (stops when locked).',
      !!S.ytAudio, (v) => { S.ytAudio = v; save(); }),
    h('div', { class: 'section' }, 'Move library'),
    syncBox(),
    h('button', { class: 'danger', style: 'color:var(--text)', onclick: () => Sync.share() }, 'Send liked songs and playlists to another phone'),
    h('div', { class: 'note' }, 'Open the link on the other phone. On Android, paste it in Library → Import playlist.'),
    h('div', { class: 'section' }, 'Offline'),
    toggleRow('Offline mode', 'Play only downloaded songs (saves data). Turns on by itself without internet.', !!S.offlineOnly, (v) => { S.offlineOnly = v; save(); }),
    h('div', { class: 'section' }, 'Help'),
    h('button', { class: 'danger', style: 'color:var(--text)', onclick: reportProblem }, 'Report a problem'),
    h('div', { class: 'note' }, 'Write what went wrong and send it with WhatsApp, email or any app.'),
    h('div', { class: 'section' }, 'Suggestions'),
    h('button', { class: 'danger', onclick: () => { S.seen = []; seenSet.clear(); save(); Feed.reset(); toast('Done'); } }, 'Reset suggestions'),
    h('div', { class: 'note' }, "Songs you've heard are never suggested twice."),
    standalone ? null : h('div', null, h('div', { class: 'section' }, 'Install on iPhone'),
      h('div', { class: 'note' }, 'In Safari tap Share, then "Add to Home Screen". Sangeet opens full screen like an app.')),
    h('button', { class: 'danger', style: 'color:var(--text)', onclick: () => WhatsNew.show() }, "What's new"),
    h('div', { class: 'note' }, `Version ${window.SANGEET_BUILD}`));
}

/* ------------------------------------------------------------------ Community: what the app's listeners share */
// data/community.json (built by CI from the Android app's daily anonymous uploads; only what 2+ listeners share).
const Community = {
  data: null, ids: [], idx: new Map(),
  async load() {
    if (this.data) return;
    const d = await Deep.get(`data/community.json${Deep.v()}`);
    this.data = d && d.tracks ? d : { tracks: [], together: {}, top: [] };
    this.ids = this.data.tracks.map((t) => (t[0] === 'YOUTUBE' ? 'yt:' : 'js:') + t[1]);
    this.idx = new Map(this.ids.map((id, i) => [id, i]));
  },
  /** Song id -> how much listeners like you play it (songs played with [mine]), plus what most listeners play. */
  scores(mine) {
    const out = new Map();
    if (!this.data) return out;
    for (const m of mine) {
      (this.data.together[this.idx.get(m)] || []).forEach((j, r) => out.set(this.ids[j], (out.get(this.ids[j]) || 0) + 1 / (1 + r * 0.2)));
    }
    if (mine.length > 1) this.data.top.forEach((j, r) => out.set(this.ids[j], (out.get(this.ids[j]) || 0) + 0.5 / (1 + r * 0.05)));
    return out;
  },
};

/* ------------------------------------------------------------------ What's new (the update log, same file as Android) */
const WhatsNew = {
  KEY: 'sangeet-whatsnew',
  async list() {
    try {
      const r = await fetch(`whats-new.json?b=${window.SANGEET_BUILD}`);
      if (!r.ok) return [];
      return (await r.json()).map((e) => ({ ...e, items: e.items.filter((i) => i.on !== 'android') })).filter((e) => e.items.length);
    } catch { return []; }
  },
  /** After an update: the new entries, once. A first visit shows nothing; an older install sees the newest entry. */
  async check() {
    const list = await this.list();
    if (!list.length) return;
    let seen = null;
    try { seen = localStorage.getItem(this.KEY); localStorage.setItem(this.KEY, list[0].date); } catch {}
    const usedBefore = S.liked.length || Object.keys(S.history || {}).length;
    const fresh = seen ? list.filter((e) => e.date > seen) : usedBefore ? list.slice(0, 1) : [];
    if (fresh.length) this.show(fresh);
  },
  async show(entries) {
    entries = entries || (await this.list());
    if (!entries.length) return toast('Nothing yet');
    const close = () => box.remove();
    const box = h('div', { class: 'menu', onclick: close },
      h('div', { class: 'box whats-new', onclick: (e) => e.stopPropagation() },
        h('h2', null, "What's new"),
        entries.map((e) => h('div', null,
          h('div', { class: 'wn-title' }, [e.title, e.date].filter(Boolean).join(' · ')),
          [['new', 'New'], ['fix', 'Fixed']].map(([type, heading]) => {
            const items = e.items.filter((i) => (i.type || 'new') === type);
            return items.length ? h('div', null, h('div', { class: 'wn-kind' }, heading), h('ul', null, items.map((i) => h('li', null, i.text)))) : '';
          }))),
        h('button', { class: 'pill primary', style: 'margin:12px 20px', onclick: close }, 'OK')));
    document.body.append(box);
  },
};

/* ------------------------------------------------------------------ look: Classic or Liquid Glass */
const LOOKS = [['classic', 'Classic dark'], ['glass-dark', 'Liquid Glass · Dark'], ['glass-light', 'Liquid Glass · Light'], ['glass-auto', 'Liquid Glass · Same as iPhone']];
const lightQuery = matchMedia('(prefers-color-scheme: light)');
function applyLook() {
  const look = S.look || 'classic';
  const glass = look !== 'classic';
  const light = glass && (look === 'glass-light' || (look === 'glass-auto' && lightQuery.matches));
  document.documentElement.style.setProperty('--accent', S.accent || '#ff5c6b');
  document.body.classList.toggle('glass', glass);
  document.body.classList.toggle('light', light);
  // Liquid Glass tab bar: For You, Library and Settings in one glass capsule, Search on its own round button.
  const tabs = $('#tabs');
  const btn = (t) => tabs.querySelector(`button[data-tab="${t}"]`);
  let cap = tabs.querySelector('.cap');
  if (glass && !cap) {
    cap = h('div', { class: 'cap' });
    ['feed', 'library', 'settings'].forEach((t) => cap.append(btn(t)));
    tabs.prepend(cap);
  } else if (!glass && cap) {
    ['feed', 'search', 'library', 'settings'].forEach((t) => tabs.append(btn(t)));
    cap.remove();
  }
  document.querySelector('meta[name="theme-color"]').setAttribute('content', light ? '#f2f2f7' : glass ? '#000000' : '#0b0b0f');
  // Status bar text color is read when the app opens (index.html).
  const before = (() => { try { return localStorage.getItem('sangeet-look'); } catch { return null; } })();
  try { localStorage.setItem('sangeet-look', light ? 'light' : 'dark'); } catch {}
  return before != null && before !== (light ? 'light' : 'dark');
}
lightQuery.addEventListener?.('change', () => { if (S.look === 'glass-auto') applyLook(); });

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
    CarMode.render();
  },
  tick() {
    const d = Player.duration(), p = d ? (Player.time() / d) * 100 : 0;
    const bar = $('#mini .bar');
    if (bar) bar.style.width = p + '%';
    Feed.activePage()?.seek.tick();
    if (UI.np) { UI.np.seek.tick(); UI.np.lyricsTick?.(); }
  },
  openNowPlaying(refresh) {
    const t = Player.current;
    if (!t) return;
    SongVideo.stop();
    const np = $('#np');
    const view = refresh && this.np ? this.np.view : 'art';
    const body = h('div', { class: 'np-body' });
    const seek = seekBar(), ctr = controls(), err = h('div', { class: 'err' });
    const lyricsBtn = h('button', { class: 'icon-btn', 'aria-label': 'Lyrics' }, icon('lyrics'));
    const queueBtn = h('button', { class: 'icon-btn', 'aria-label': 'Up next' }, icon('queue'));
    const sleepNote = h('div', { class: 'sleep-note' }, Sleep.label());
    const shuffleBtn = h('button', { class: 'icon-btn' + (Player.shuffleOn ? ' on' : ''), 'aria-label': 'Shuffle', onclick: () => { Player.toggleShuffle(); shuffleBtn.classList.toggle('on', Player.shuffleOn); } }, icon('shuffle'));
    const repeatBtn = h('button', { class: 'icon-btn' + (Player.repeat !== 'off' ? ' on' : ''), 'aria-label': 'Repeat', onclick: () => {
      Player.cycleRepeat();
      repeatBtn.classList.toggle('on', Player.repeat !== 'off');
      repeatBtn.replaceChildren(icon(Player.repeat === 'one' ? 'repeatOne' : 'repeat'));
    } }, icon(Player.repeat === 'one' ? 'repeatOne' : 'repeat'));
    const speedBtn = h('button', { class: 'icon-btn' + ((S.speed || 1) !== 1 ? ' on' : ''), 'aria-label': 'Playback speed', onclick: () =>
      menu(`Playback speed (now ${S.speed || 1}x)`, [0.75, 1, 1.25, 1.5, 2].map((v) => [`${v}x${v === 1 ? ' (normal)' : ''}`, () => { Player.setSpeed(v); speedBtn.classList.toggle('on', v !== 1); toast(`Speed ${v}x`); }])) }, icon('speed'));
    const sleepBtn = h('button', { class: 'icon-btn' + (Sleep.on ? ' on' : ''), 'aria-label': 'Sleep timer', onclick: () =>
      menu('Sleep timer', [
        ...[15, 30, 45, 60, 90].map((m) => [`${m} minutes`, () => Sleep.set(m)]),
        ['End of this song', () => Sleep.set('end')],
        ...(Sleep.on ? [['Turn off', () => { Sleep.clear(); toast('Sleep timer off'); }]] : []),
      ].map(([l, f]) => [l, () => { f(); sleepBtn.classList.toggle('on', Sleep.on); sleepNote.textContent = Sleep.label(); }])) }, icon('moon'));
    // Song | Video, like YouTube Music (not for radio; a YouTube song already shows its video).
    const canVideo = t.src !== 'radio' && Player.mode !== 'yt' && (t.src === 'yt' || S.ytKey);
    const songChip = h('button', { class: 'chip' }, 'Song'), videoChip = h('button', { class: 'chip' }, 'Video');
    const mark = () => { songChip.classList.toggle('on', !S.video); videoChip.classList.toggle('on', !!S.video); };
    songChip.onclick = () => { if (!S.video) return; S.video = false; save(); mark(); show('art'); };
    videoChip.onclick = () => { if (S.video) return; S.video = true; save(); mark(); show('art'); };
    SongVideo.onNone = () => { S.video = false; mark(); if (this.np === state && state.view === 'art') show('art'); };
    mark();
    np.replaceChildren(
      h('div', { class: 'bg', style: t.img ? `background-image:url("${art(t, true)}")` : '' }),
      h('div', { class: 'np-top' },
        h('button', { class: 'icon-btn', 'aria-label': 'Close', onclick: () => this.closeNowPlaying() }, icon('down')),
        canVideo ? h('div', { class: 'np-switch' }, songChip, videoChip) : null,
        h('div', { style: 'display:flex' },
          h('button', { class: 'icon-btn', 'aria-label': 'Car mode', onclick: () => CarMode.open() }, icon('car')),
          h('button', { class: 'icon-btn', 'aria-label': 'More', onclick: () => trackMenu(t) }, icon('more')))),
      body,
      h('div', { class: 'np-bottom' },
        h('div', { class: 'info', style: 'display:flex;align-items:center;gap:12px' },
          h('div', { class: 'meta', style: 'flex:1;min-width:0' },
            h('div', { style: 'font-size:22px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis' }, t.title),
            h('div', { style: 'color:rgba(255,255,255,.7);white-space:nowrap;overflow:hidden;text-overflow:ellipsis' }, t.artist)),
          likeBtn(t)),
        seek, ctr, err,
        h('div', { class: 'row-icons' }, shuffleBtn, repeatBtn, speedBtn, sleepBtn, lyricsBtn, queueBtn),
        sleepNote));
    const state = { seek, ctr, err, view, lyricsTick: null };
    const show = (v) => {
      state.view = v;
      state.lyricsTick = null;
      SongVideo.stop();
      lyricsBtn.classList.toggle('on', v === 'lyrics');
      queueBtn.classList.toggle('on', v === 'queue');
      if (v === 'lyrics') return showLyrics(t, body, state);
      if (v === 'queue') {
        const up = Player.queue.slice(Player.i);
        return body.replaceChildren(h('div', { class: 'queue' }, up.map((x, k) => trackRow(x, () => Player.load(Player.i + k)))));
      }
      if (canVideo && S.video) return SongVideo.show(t, body);
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
  initSwipe() {
    const np = $('#np');
    let y0 = null;
    np.addEventListener('touchstart', (e) => {
      const y = e.touches[0].clientY;
      y0 = y < np.clientHeight * 0.45 && !e.target.closest('.lyrics, .queue, input') ? y : null;
    }, { passive: true });
    np.addEventListener('touchmove', (e) => {
      if (y0 == null) return;
      const dy = Math.max(0, e.touches[0].clientY - y0);
      np.style.transform = `translateY(${dy}px)`;
    }, { passive: true });
    np.addEventListener('touchend', (e) => {
      if (y0 == null) return;
      const dy = e.changedTouches[0].clientY - y0;
      y0 = null;
      np.style.transition = 'transform .2s ease-out';
      np.style.transform = dy > 110 ? 'translateY(100%)' : '';
      setTimeout(() => {
        np.style.transition = '';
        if (dy > 110) { np.style.transform = ''; this.closeNowPlaying(); }
      }, 200);
    });
  },
  closeNowPlaying() {
    SongVideo.stop();
    $('#np').hidden = true;
    document.body.classList.remove('np-open');
    this.np = null;
  },
};
setInterval(() => { UI.tick(); if (Player.playing) Stats.add(500); Sleep.check(); }, 500);

/* ------------------------------------------------------------------ synced lyrics (LRCLIB, free) */
const lyricsCache = new Map();
async function getLyrics(t) {
  if (t.src === 'radio') return []; // live radio has no lyrics
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
  let picking = false;
  const pick = h('button', { class: 'chip lyric-pick', onclick: () => {
    picking = !picking;
    pick.classList.toggle('on', picking);
    pick.textContent = picking ? 'Tap a line' : 'Pick a line';
  } }, 'Pick a line');
  const sing = h('button', { class: 'chip lyric-pick', onclick: () => singAlong(t, lines) }, 'Sing along');
  // A picked line: what it means (Gemini, else the free AI), or share it as a picture.
  const lineMenu = (line) => menu(line, [['Meaning', () => lineMeaning(t, line)], ['Share as picture', () => shareLyric(t, line)]]);
  const box = h('div', { class: 'lyrics' }, lines.map((l) => h('p', { onclick: () => {
    if (picking && l.text) { picking = false; pick.classList.remove('on'); pick.textContent = 'Pick a line'; return lineMenu(l.text); }
    Player.seek(l.time);
  } }, l.text || '♪')));
  body.replaceChildren(h('div', { class: 'lyrics-wrap' }, h('div', { class: 'chips' }, sing, pick), box));
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

/** Sing along: the line being sung, very big; the one before and the next one around it. */
function singAlong(t, lines) {
  const before = h('div', { class: 'sa-side' }), now = h('div', { class: 'sa-now' }), next = h('div', { class: 'sa-side' });
  const close = () => { clearInterval(timer); box.remove(); };
  const playBtn = h('button', { class: 'icon-btn', 'aria-label': 'Play or pause', onclick: () => Player.toggle() });
  let shown = null;
  const box = h('div', { class: 'sing-along' },
    h('div', { class: 'sa-top' }, h('span', null, t.title), playBtn, h('button', { class: 'icon-btn', 'aria-label': 'Close', onclick: close }, '✕')),
    h('div', { class: 'sa-lines' }, before, now, next));
  let last = -2;
  const tick = () => {
    if (Player.current?.id !== t.id) return close();
    if (shown !== Player.playing) { shown = Player.playing; fill(playBtn, [icon(shown ? 'pause' : 'play')]); }
    const at = Player.time() + 0.3;
    let i = -1;
    for (let k = 0; k < lines.length && lines[k].time <= at; k++) i = k;
    if (i === last) return;
    last = i;
    before.textContent = lines[i - 1]?.text || '';
    now.textContent = (i >= 0 && lines[i].text) || '♪';
    next.textContent = lines[i + 1]?.text || '';
  };
  const timer = setInterval(tick, 250);
  document.body.append(box);
  tick();
}
/** What a lyrics line means, from Gemini (with a key) or the free AI. */
async function lineMeaning(t, line) {
  const text = h('p', null, 'Finding the meaning…');
  const box = h('div', { class: 'menu', onclick: () => box.remove() },
    h('div', { class: 'box', onclick: (e) => e.stopPropagation() }, h('h3', { style: 'margin:12px 20px 4px' }, line),
      h('div', { style: 'padding:0 20px 8px' }, text),
      h('button', { class: 'pill primary', style: 'margin:8px 20px 16px', onclick: () => box.remove() }, 'OK')));
  document.body.append(box);
  const m = await FreeAi.meaning(line, t);
  text.textContent = m || "Couldn't get the meaning right now. Try again in a minute.";
}

/* Song video (owner, Oct 10: "song ka video bhi, jaise YT Music"): Now Playing's Song | Video switch shows the
 * song's YouTube music video, muted, in step with the song that keeps playing (catalog audio plays on when the
 * phone is locked; the video is only the picture). A YouTube song already shows its own video (Player.mode 'yt'). */
const SongVideo = {
  yt: null, timer: null, box: null,
  /** The video to show for [t]: its own for a YouTube song, else the first music video YouTube finds. */
  async idFor(t) {
    if (t.src === 'yt') return t.sid;
    const found = await Tube.search(`${t.title.replace(/\s*\(.*$/, '')} ${(t.artist || '').split(',')[0]}`);
    return found[0]?.sid || null;
  },
  async show(t, box) {
    this.stop();
    this.box = box;
    fill(box, h('img', { class: 'cover', src: art(t, true), alt: '' }), h('div', { class: 'spinner np-video-wait' }));
    const id = await this.idFor(t).catch(() => null);
    if (this.box !== box || Player.current?.id !== t.id) return;
    if (!id) { toast('No video for this song'); return this.onNone?.(); }
    await Tube.ensure(); // loads YouTube's player script
    if (this.box !== box) return;
    const el = h('div');
    fill(box, h('div', { class: 'np-video' }, el));
    this.yt = new window.YT.Player(el, {
      videoId: id,
      playerVars: { playsinline: 1, autoplay: 1, mute: 1, controls: 0, rel: 0, modestbranding: 1, disablekb: 1, start: Math.floor(Player.time()) },
      events: {
        onReady: (e) => { e.target.mute(); e.target.seekTo(Player.time(), true); if (Player.playing) e.target.playVideo(); },
        onError: () => { if (this.box === box) { toast('No video for this song'); this.onNone?.(); } },
      },
    });
    // Keep the picture with the song: same play/pause, and a jump when it drifts.
    let lastSeek = 0;
    this.timer = setInterval(() => {
      const y = this.yt;
      if (!y || !y.getCurrentTime || Player.current?.id !== t.id) return;
      const st = y.getPlayerState?.();
      if (Player.playing && st !== 1 && st !== 3) y.playVideo();
      if (!Player.playing && st === 1) y.pauseVideo();
      const drift = y.getCurrentTime() - Player.time();
      if (Math.abs(drift) > 1.2 && Date.now() - lastSeek > 3000 && Player.time() < (y.getDuration?.() || Infinity)) {
        lastSeek = Date.now();
        y.seekTo(Player.time() + 0.3, true);
      }
    }, 1000);
  },
  stop() {
    clearInterval(this.timer);
    this.timer = null;
    try { this.yt?.destroy?.(); } catch {}
    this.yt = null;
    this.box = null;
  },
};

/* ------------------------------------------------------------------ picture cards (Wrapped, lyrics) shared to Instagram / WhatsApp */
const Card = {
  img(src) {
    return new Promise((ok) => {
      if (!src) return ok(null);
      const i = new Image();
      i.crossOrigin = 'anonymous'; // so the card can still be saved as a picture
      i.onload = () => ok(i);
      i.onerror = () => ok(null);
      i.src = src;
    });
  },
  canvas() {
    const c = document.createElement('canvas');
    c.width = 1080; c.height = 1350;
    return c;
  },
  bg(ctx, accent) {
    const g = ctx.createLinearGradient(0, 0, 1080, 1350);
    g.addColorStop(0, accent);
    g.addColorStop(1, '#0b0b0f');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 1080, 1350);
  },
  /** Writes text over several lines; returns the y below it. */
  text(ctx, text, x, y, maxW, size, weight, color, maxLines = 3, align = 'left') {
    ctx.font = `${weight} ${size}px -apple-system, "SF Pro Display", Roboto, sans-serif`;
    ctx.fillStyle = color;
    ctx.textAlign = align;
    const words = String(text).split(/\s+/);
    let line = '', lines = [];
    for (const w of words) {
      const tryLine = line ? line + ' ' + w : w;
      if (ctx.measureText(tryLine).width > maxW && line) { lines.push(line); line = w; } else line = tryLine;
    }
    if (line) lines.push(line);
    if (lines.length > maxLines) { lines = lines.slice(0, maxLines); lines[maxLines - 1] += '…'; }
    for (const l of lines) { ctx.fillText(l, x, y); y += size * 1.25; }
    return y;
  },
  /** Shows the finished card with a Share button (the iPhone share sheet must open right on a tap). */
  async preview(draw, name, text) {
    const m = $('#menu');
    const box = h('div', { class: 'box card-preview', onclick: (e) => e.stopPropagation() }, h('div', { class: 'spinner' }));
    m.replaceChildren(box);
    m.onclick = () => (m.hidden = true);
    m.hidden = false;
    let blob = null;
    for (const withImages of [true, false]) {
      const c = Card.canvas();
      await draw(c.getContext('2d'), withImages);
      try { blob = await new Promise((ok, no) => { try { c.toBlob((b) => (b ? ok(b) : no()), 'image/png'); } catch (e) { no(e); } }); break; } catch {}
    }
    if (!blob) { m.hidden = true; return toast("Couldn't make the picture"); }
    const file = new File([blob], name, { type: 'image/png' });
    const url = URL.createObjectURL(blob);
    fill(box,
      h('img', { src: url, class: 'card-img', alt: '' }),
      h('div', { class: 'actions' },
        h('button', { class: 'pill primary', onclick: () => {
          if (navigator.canShare?.({ files: [file] })) navigator.share({ files: [file], text }).catch(() => {});
          else { const a = h('a', { href: url, download: name }); document.body.append(a); a.click(); a.remove(); }
        } }, 'Share'),
        h('button', { class: 'pill', onclick: () => (m.hidden = true) }, 'Close')));
  },
};

/** "My Sangeet Wrapped": minutes, top songs, top singers and streak on one picture. */
function shareWrapped() {
  const st = S.stats || { ms: 0, days: {}, hours: Array(24).fill(0) };
  const plays = Object.values(S.history).sort((a, b) => b.c - a.c);
  const singers = new Map();
  for (const r of plays) { const a = splitArtists(r.t.artist)[0]; if (a) singers.set(a, (singers.get(a) || 0) + r.c); }
  const top = [...singers.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map((x) => x[0]);
  const songs = plays.slice(0, 5).map((r) => r.t);
  const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#ff5c6b';
  Card.preview(async (ctx, withImages) => {
    Card.bg(ctx, accent);
    Card.text(ctx, 'MY SANGEET WRAPPED', 80, 130, 560, 40, 800, 'rgba(255,255,255,.85)', 1);
    let y = Card.text(ctx, String(new Date().getFullYear()), 80, 270, 560, 120, 900, '#fff', 1);
    y = Card.text(ctx, `${Math.round(st.ms / 60000).toLocaleString()} minutes of music`, 80, Math.max(y + 30, 470), 920, 58, 800, '#fff', 1);
    const cover = withImages && songs[0] ? await Card.img(art(songs[0], true)) : null;
    if (cover) { ctx.save(); ctx.beginPath(); ctx.roundRect(700, 90, 300, 300, 28); ctx.clip(); ctx.drawImage(cover, 700, 90, 300, 300); ctx.restore(); }
    y += 40;
    Card.text(ctx, 'Top songs', 80, y, 440, 40, 700, 'rgba(255,255,255,.7)', 1);
    Card.text(ctx, 'Top singers', 580, y, 440, 40, 700, 'rgba(255,255,255,.7)', 1);
    let ys = y + 64, ya = y + 64;
    songs.forEach((t, k) => { ys = Card.text(ctx, `${k + 1}. ${t.title}`, 80, ys, 460, 38, 600, '#fff', 2) + 10; });
    top.forEach((a, k) => { ya = Card.text(ctx, `${k + 1}. ${a}`, 580, ya, 440, 38, 600, '#fff', 2) + 10; });
    let streak = 0;
    for (let k = (st.days[dayKey(new Date())] || 0) >= 60000 ? 0 : 1; (st.days[dayKey(new Date(Date.now() - k * 864e5))] || 0) >= 60000; k++) streak++;
    if (streak) Card.text(ctx, `🔥 ${streak}-day listening streak`, 80, 1180, 920, 44, 700, '#fff', 1);
    Card.text(ctx, 'Sangeet 🎵', 1000, 1280, 600, 36, 700, 'rgba(255,255,255,.75)', 1, 'right');
  }, 'sangeet-wrapped.png', 'My Sangeet Wrapped 🎵');
}

/** A line of lyrics on the song's cover, as a picture. */
function shareLyric(t, line) {
  const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#ff5c6b';
  Card.preview(async (ctx, withImages) => {
    Card.bg(ctx, accent);
    const cover = withImages ? await Card.img(art(t, true)) : null;
    if (cover) { ctx.save(); ctx.beginPath(); ctx.roundRect(80, 90, 400, 400, 28); ctx.clip(); ctx.drawImage(cover, 80, 90, 400, 400); ctx.restore(); }
    let y = Card.text(ctx, `“${line}”`, 80, 640, 920, 76, 800, '#fff', 6);
    y = Card.text(ctx, t.title, 80, y + 50, 920, 46, 700, '#fff', 2);
    Card.text(ctx, t.artist, 80, y, 920, 38, 500, 'rgba(255,255,255,.75)', 2);
    Card.text(ctx, 'Sangeet 🎵', 1000, 1280, 600, 36, 700, 'rgba(255,255,255,.75)', 1, 'right');
  }, 'sangeet-lyrics.png', `${t.title} · ${t.artist} 🎵`);
}

/* ------------------------------------------------------------------ Blend: one playlist from your and a friend's taste */
const Blend = {
  /** Your taste: most played and liked songs, and favourite singers. Same song format as the library link. */
  taste() {
    const plays = Object.values(S.history).sort((a, b) => b.c - a.c).map((r) => r.t);
    const songs = [...plays.slice(0, 30), ...S.liked.slice(0, 20)];
    const seen = new Set();
    return songs.filter((t) => !seen.has(t.id) && seen.add(t.id)).slice(0, 40);
  },
  async link(name) {
    const data = { v: 1, n: name, s: this.taste().map((t) => Sync.encode(t)).filter(Boolean) };
    return location.origin + location.pathname + '#blend=' + (await Sync.pack(JSON.stringify(data)));
  },
  /** Opened a friend's Blend link: mix their songs with yours (and songs like both), save it as a playlist. */
  async fromHash() {
    const m = location.hash.match(/blend=([\w-]+)/);
    if (!m) return;
    history.replaceState(null, '', location.pathname);
    try {
      const d = JSON.parse(await Sync.unpack(m[1]));
      const friend = (d.n || 'Friend').slice(0, 30);
      const theirs = (d.s || []).map((o) => Sync.decode(o)).filter(Boolean);
      const mine = this.taste();
      if (!theirs.length) return toast("That Blend link didn't work");
      const out = [], ids = new Set();
      const add = (t) => { if (t && !ids.has(t.id)) { ids.add(t.id); out.push(t); } };
      for (let k = 0; k < Math.max(mine.length, theirs.length) && out.length < 40; k++) { add(theirs[k]); add(mine[k]); }
      // A few songs like the ones you both have.
      const seed = theirs.find((t) => t.src === 'js') || mine[0];
      if (seed) radio(seed, 10, ids).forEach(add);
      const name = `Blend: You + ${friend}`;
      S.playlists.unshift({ id: String(Date.now()), name, tracks: out.map(slim) });
      save();
      toast(`${name} saved in your playlists`);
      if (tab !== 'library') document.querySelector('#tabs button[data-tab="library"]').click();
      pushPage(() => songsPage(name, out, S.playlists[0]));
    } catch {
      toast("That Blend link didn't work");
    }
  },
};
function blendPage() {
  const nameIn = h('input', { type: 'text', placeholder: 'Your name', value: S.name || '' });
  const share = h('button', { class: 'pill primary' }, 'Send my Blend link');
  let url = null;
  const prepare = async () => { url = await Blend.link((S.name = nameIn.value.trim() || 'Friend')); save(); };
  nameIn.addEventListener('change', prepare);
  prepare();
  share.onclick = () => {
    if (!url) return;
    if (navigator.share) navigator.share({ title: 'Blend with me on Sangeet', text: 'Open this to make a playlist from both our tastes 🎵', url }).catch(() => {});
    else navigator.clipboard?.writeText(url).then(() => toast('Link copied'));
  };
  return h('div', null, header('Blend', true),
    h('div', { class: 'note' }, 'Make one playlist from your taste and a friend\'s: send them your link. When they open it, Sangeet mixes your songs with theirs (and songs like both) and saves it for them. Ask them for theirs too.'),
    h('div', { class: 'setting', style: 'display:block' }, nameIn),
    h('div', { class: 'actions' }, share),
    Blend.taste().length < 5 ? h('div', { class: 'note' }, 'Play or like a few more songs first, so your Blend has your taste in it.') : null);
}

/* ------------------------------------------------------------------ Car mode: big buttons, easy to hit while driving */
const CarMode = {
  el: null,
  open() {
    this.el = h('div', { id: 'car' });
    document.body.append(this.el);
    this.render();
  },
  close() { this.el?.remove(); this.el = null; },
  render() {
    if (!this.el) return;
    const t = Player.current;
    fill(this.el,
      h('button', { class: 'car-close', 'aria-label': 'Close car mode', onclick: () => this.close() }, icon('down')),
      h('div', { class: 'car-title' }, t ? t.title : 'Nothing playing'),
      h('div', { class: 'car-artist' }, t ? t.artist : ''),
      h('div', { class: 'car-controls' },
        h('button', { 'aria-label': 'Previous', onclick: () => Player.prev() }, icon('prev')),
        h('button', { class: 'big', 'aria-label': 'Play/Pause', onclick: () => Player.toggle() }, icon(Player.playing ? 'pause' : 'play')),
        h('button', { 'aria-label': 'Next', onclick: () => Player.next() }, icon('next'))),
      t ? h('button', { class: 'car-like' + (isLiked(t) ? ' on' : ''), onclick: () => { toggleLike(t); this.render(); } }, icon(isLiked(t) ? 'heartFill' : 'heart')) : null);
  },
};

/* ------------------------------------------------------------------ new songs from your singers */
/** Recent songs (this year; also last year early in the year) by the singers you play and like most. */
function newFromYourSingers(limit = 30) {
  const fav = new Map();
  for (const r of Object.values(S.history)) for (const a of splitArtists(r.t.artist)) fav.set(norm(a), (fav.get(norm(a)) || 0) + r.c);
  for (const t of S.liked) for (const a of splitArtists(t.artist)) fav.set(norm(a), (fav.get(norm(a)) || 0) + 3);
  const d = new Date(), since = d.getMonth() < 3 ? d.getFullYear() - 1 : d.getFullYear();
  return Catalog.tracks
    .filter((t) => t.year >= since && splitArtists(t.artist).some((a) => fav.has(norm(a))))
    .sort((a, b) => b.year - a.year || Math.max(...splitArtists(b.artist).map((x) => fav.get(norm(x)) || 0)) - Math.max(...splitArtists(a.artist).map((x) => fav.get(norm(x)) || 0)))
    .slice(0, limit);
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

applyLook();

/* Sync with another phone (owner, Oct 9): liked songs and playlists the same on iPhone and Android, no account. One
 * phone makes a sync code; the library is kept packed on restful-api.dev (free keyless JSON store, CI-probed) under
 * that long code. Every sync merges three ways against what both had last time (an un-liked song goes on both).
 * Same data as Android's CloudSync. */
const CloudSync = {
  API: 'https://api.restful-api.dev/objects',
  get code() { try { return localStorage.getItem('sangeet-sync-code'); } catch { return null; } },
  set(code, lib) {
    try {
      if (!code) { localStorage.removeItem('sangeet-sync-code'); localStorage.removeItem('sangeet-sync-base'); return; }
      localStorage.setItem('sangeet-sync-code', code);
      if (lib) localStorage.setItem('sangeet-sync-base', JSON.stringify(this.toJson(lib)));
    } catch {}
  },
  link(code) { return `${location.origin}${location.pathname}#joinsync=${code}`; },
  me() {
    try { let id = localStorage.getItem('sangeet-sync-me'); if (!id) { id = Math.random().toString(36).slice(2, 10); localStorage.setItem('sangeet-sync-me', id); } return id; } catch { return 'web'; }
  },
  local() {
    const tracks = new Map(), ok = (t) => Sync.encode(t) && tracks.set(t.id, t);
    const liked = S.liked.filter(ok).map((t) => t.id);
    const lists = {};
    S.playlists.filter((p) => !p.name.startsWith('✨')).forEach((p) => { lists[p.name] = p.tracks.filter(ok).map((t) => t.id); });
    return { liked, lists, tracks };
  },
  toJson(l) {
    const enc = (id) => (l.tracks.get(id) ? Sync.encode(l.tracks.get(id)) : null);
    return { l: l.liked.map(enc).filter(Boolean), p: Object.entries(l.lists).map(([n, ids]) => ({ n, t: ids.map(enc).filter(Boolean) })) };
  },
  fromJson(o) {
    const tracks = new Map();
    const list = (a) => (a || []).map((x) => Sync.decode(x)).filter(Boolean).map((t) => { tracks.set(t.id, t); return t.id; });
    const lists = {};
    (o.p || []).forEach((p) => { lists[p.n || 'Playlist'] = list(p.t); });
    return { liked: list(o.l), lists, tracks };
  },
  async call(method, url, body) {
    const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
    if (r.status === 404) return null;
    if (!r.ok) throw new Error(`sync HTTP ${r.status}`);
    return r.json();
  },
  async wrap(l) { return { name: 'sangeet-sync', data: { v: 1, u: Date.now(), by: this.me(), z: await Sync.pack(JSON.stringify(this.toJson(l))) } }; },
  async unwrap(o) { return o && o.data && o.data.z ? this.fromJson(JSON.parse(await Sync.unpack(o.data.z))) : null; },
  async start() {
    const mine = this.local();
    const made = await this.call('POST', this.API, await this.wrap(mine));
    if (!made || !made.id) throw new Error('no sync code');
    this.set(made.id, mine);
    return made.id;
  },
  async join(text) {
    const code = String(text).split('joinsync=').pop().trim().replace(/[^A-Za-z0-9]/g, '');
    if (code.length < 8) throw new Error('That code is too short');
    try { localStorage.setItem('sangeet-sync-code', code); localStorage.removeItem('sangeet-sync-base'); } catch {}
    const r = await this.sync();
    if (!r) { this.set(null); throw new Error("That sync code wasn't found"); }
    return r;
  },
  /** Merges with the other phone; [liked, playlists] after it, or null when the code is gone. */
  async sync() {
    const code = this.code;
    if (!code || this.busy) return null;
    this.busy = true;
    try {
      const remote = await this.unwrap(await this.call('GET', `${this.API}/${code}`));
      if (!remote) return null;
      let base = null;
      try { const b = localStorage.getItem('sangeet-sync-base'); if (b) base = this.fromJson(JSON.parse(b)); } catch {}
      const mine = this.local();
      const tracks = new Map([...mine.tracks, ...remote.tracks]);
      const merge = (b, a, r) => {
        const bs = new Set(b || []), as = new Set(a), rs = new Set(r);
        const gone = new Set([...bs].filter((x) => !as.has(x) || !rs.has(x)));
        return [...new Set([...a, ...r])].filter((x) => !gone.has(x));
      };
      const liked = merge(base && base.liked, mine.liked, remote.liked);
      const names = [...new Set([...Object.keys(mine.lists), ...Object.keys(remote.lists)])]
        .filter((n) => !(base && base.lists[n] && (!mine.lists[n] || !remote.lists[n])));
      const lists = {};
      names.forEach((n) => { lists[n] = merge(base && base.lists[n], mine.lists[n] || [], remote.lists[n] || []); });
      const merged = { liked, lists, tracks };
      // Into this phone's library (keeps the songs' own details when they are already here).
      const have = new Map([...S.liked, ...S.playlists.flatMap((p) => p.tracks)].map((t) => [t.id, t]));
      const pick = (id) => have.get(id) || tracks.get(id);
      S.liked = liked.map(pick).filter(Boolean);
      const auto = S.playlists.filter((p) => p.name.startsWith('✨'));
      S.playlists = [...names.map((n) => {
        const old = S.playlists.find((p) => p.name === n);
        return { ...(old || { id: String(Date.now() + Math.random()) }), name: n, tracks: lists[n].map(pick).filter(Boolean) };
      }), ...auto];
      this.sig = this.signature();
      save();
      refreshLikes?.();
      if (JSON.stringify(this.toJson(merged)) !== JSON.stringify(this.toJson(remote))) await this.call('PUT', `${this.API}/${code}`, await this.wrap(merged));
      this.set(code, merged);
      this.sig = this.signature();
      return [liked.length, names.length];
    } finally { this.busy = false; }
  },
  signature() { return `${S.liked.map((t) => t.id).join()}|${S.playlists.map((p) => `${p.name}:${p.tracks.length}`).join()}`; },
  /** After likes / playlists change: sync a little later. */
  changed() {
    if (!this.code) return;
    const sig = this.signature();
    if (sig === this.sig) return;
    this.sig = sig;
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.sync().catch(() => {}), 20000);
  },
  async fromHash() {
    const m = location.hash.match(/joinsync=([A-Za-z0-9]+)/);
    if (!m) return;
    history.replaceState(null, '', location.pathname);
    if (this.code === m[1]) return toast('Sync is already on');
    if (!confirm('Sync your liked songs and playlists with your other phone?')) return;
    try { const [l, p] = await this.join(m[1]); toast(`Synced: ${l} liked songs, ${p} playlists`); } catch (e) { toast(e.message || "Sync didn't work"); }
  },
};
/* How many iPhones / browsers use the site, for the owner dashboard (Android): a free public hit counter
 * (abacus.jasoncameron.dev, no key). Once per browser ever ("web-users") and once a day ("web-day-yyyymmdd"). Only a
 * number is sent. Same counter space as Android's Usage. */
const Visits = {
  URL: 'https://abacus.jasoncameron.dev/hit/sangeet-asdf968566/',
  count() {
    if (!navigator.onLine || /HeadlessChrome|Playwright/.test(navigator.userAgent)) return;
    const d = new Date(), day = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
    const hit = (name, flag, value) => {
      try { if (localStorage.getItem(flag) === value) return; } catch { return; }
      fetch(this.URL + name, { mode: 'cors' }).then((r) => { if (r.ok) try { localStorage.setItem(flag, value); } catch {} }).catch(() => {});
    };
    hit('web-users', 'sangeet-counted', '1');
    hit(`web-day-${day}`, 'sangeet-counted-day', day);
  },
};
(async () => {
  UI.initSwipe();
  Feed.init();
  await Promise.all([Catalog.load(), Offline.init()]);
  Feed.reset();
  UI.update();
  Sync.importFromHash();
  Blend.fromHash();
  SongLink.fromHash();
  CloudSync.fromHash();
  Together.fromHash();
  window.addEventListener('hashchange', () => { Sync.importFromHash(); Blend.fromHash(); SongLink.fromHash(); CloudSync.fromHash(); Together.fromHash(); });
  if (CloudSync.code) setTimeout(() => CloudSync.sync().catch(() => {}), 3000);
  setTimeout(() => WhatsNew.check(), 1500);
  Community.load().catch(() => {});
  setTimeout(() => Visits.count(), 4000);
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
})();
