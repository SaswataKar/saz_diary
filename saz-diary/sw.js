// Keeps the diary working offline. Your entries live in IndexedDB, never in this cache.
// Pages load from the network first (so updates arrive), and fall back to the cache when offline.
const CACHE = "saz-diary-v3";
const CORE = ["./", "index.html", "manifest.webmanifest", "icon.svg", "icon-192.png", "icon-512.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE.map(u => new Request(u, {cache: "reload"})))));
  self.skipWaiting();
});
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});

self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // Never cache Google sign-in or Drive calls
  if (/(^|\.)googleapis\.com$|accounts\.google\.com$/.test(url.hostname)) return;

  const isPage = req.mode === "navigate" || (url.origin === location.origin && /\.(html|js|webmanifest)$|\/$/.test(url.pathname));
  if (isPage) {
    e.respondWith(fetch(req, {cache: "no-cache"}).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req).then(hit => hit || caches.match("index.html"))));
    return;
  }
  e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(res => {
    if (res && (res.ok || res.type === "opaque")) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
    return res;
  })));
});
