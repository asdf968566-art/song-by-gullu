// Offline shell + cached song catalog. A new build gets a new cache name.
const CACHE = 'sangeet-__BUILD__';
const SHELL = ['./', 'index.html', 'app.js?v=__BUILD__', 'style.css?v=__BUILD__', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png', 'apple-touch-icon.png'];

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

  // Song catalog: answer from cache at once, refresh it in the background.
  if (url.pathname.includes('/data/')) {
    e.respondWith(caches.open('sangeet-data').then(async (c) => {
      const cached = await c.match(e.request);
      const fresh = fetch(e.request).then((r) => { if (r.ok) c.put(e.request, r.clone()); return r; });
      if (cached) { e.waitUntil(fresh.catch(() => {})); return cached; }
      return fresh;
    }));
    return;
  }

  // App files: cache first, network as fallback.
  e.respondWith(caches.match(e.request).then((r) => r || fetch(e.request)));
});
