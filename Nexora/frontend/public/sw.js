/* Nexora — service worker (© Roberto Di Flumeri)
 * Rende l'app installabile e mostra una pagina dedicata quando manca la rete.
 * Le API (/api) non passano mai dalla cache: contengono dati aziendali riservati. */
const VERSIONE = "nexora-v1";
const SHELL = ["/offline.html", "/logo.png", "/manifest.webmanifest", "/icone/icona-192.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSIONE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((k) => Promise.all(k.filter((x) => x !== VERSIONE).map((x) => caches.delete(x)))).then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/api/")) return;
  if (url.pathname.startsWith("/_next/static/") || url.pathname.startsWith("/icone/")) {
    e.respondWith(caches.match(e.request).then((r) => r || fetch(e.request).then((res) => {
      const copia = res.clone();
      caches.open(VERSIONE).then((c) => c.put(e.request, copia));
      return res;
    })));
    return;
  }
  if (e.request.mode === "navigate") e.respondWith(fetch(e.request).catch(() => caches.match("/offline.html")));
});
