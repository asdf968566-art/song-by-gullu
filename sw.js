// Offline shell + cached song catalog. A new build gets a new cache name.
const CACHE = 'sangeet-87';
const SHELL = ['./', 'index.html', 'app.js?v=87', 'style.css?v=87', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'apple-touch-icon.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('sangeet-') && k !== CACHE && k !== 'sangeet-data').map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;

  // Which catalog build is current: always ask the network first.
  if (url.pathname.endsWith('/data/index.json')) {
    e.respondWith(fetch(e.request).then((r) => {
      const copy = r.clone();
      caches.open('sangeet-data').then((c) => c.put(e.request, copy));
      return r;
    }).catch(() => caches.match(e.request)));
    return;
  }

  // Song catalog files (their URLs carry the build): cache first.
  if (url.pathname.includes('/data/')) {
    e.respondWith(caches.open('sangeet-data').then(async (c) => {
      const cached = await c.match(e.request);
      if (cached) return cached;
      const r = await fetch(e.request);
      if (r.ok) c.put(e.request, r.clone());
      return r;
    }));
    return;
  }

  // App files: cache first, network as fallback.
  e.respondWith(caches.match(e.request).then((r) => r || fetch(e.request)));
});
