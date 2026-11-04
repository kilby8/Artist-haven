// Offline support. Pages are fetched from the network first so updates arrive straight away;
// the cached copy is used when there is no connection.
const CACHE = 'artist-haven-v1';

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(['./', 'index.html', 'style.css', 'app.js', 'worker.js', 'db.js', 'manifest.webmanifest',
    'icons/icon-192.png', 'engine/Artist-haven-web-web.mjs', 'engine/Artist-haven-web-engine.mjs',
    'engine/kotlin-kotlin-stdlib.mjs', 'engine/kotlinx-serialization-kotlinx-serialization-core.mjs',
    'engine/kotlinx-serialization-kotlinx-serialization-json.mjs'])).catch(() => {}));
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (k !== CACHE) await caches.delete(k);
    await self.clients.claim();
  })());
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    try {
      const res = await fetch(req, { cache: 'no-cache' });
      if (res.ok) cache.put(req, res.clone());
      return res;
    } catch (err) {
      const hit = await cache.match(req, { ignoreSearch: true });
      if (hit) return hit;
      throw err;
    }
  })());
});
