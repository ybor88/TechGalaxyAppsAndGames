/* Ghost Me — service worker (© Roberto Di Flumeri)
 * Rende l'app installabile e disponibile offline per la parte statica.
 * Le API (/v1) non vengono MAI salvate in cache: contengono dati personali. */
const VERSIONE = "ghostme-v1";
const SHELL = ["/", "/offline.html", "/logo.png", "/manifest.webmanifest", "/icone/icona-192.png"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSIONE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((chiavi) => Promise.all(chiavi.filter((k) => k !== VERSIONE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET" || url.origin !== self.location.origin || url.pathname.startsWith("/v1/")) return;

  // risorse statiche con hash nel nome: cache-first
  if (url.pathname.startsWith("/_next/static/") || url.pathname.startsWith("/icone/")) {
    e.respondWith(
      caches.match(e.request).then((r) => r || fetch(e.request).then((res) => {
        const copia = res.clone();
        caches.open(VERSIONE).then((c) => c.put(e.request, copia));
        return res;
      })),
    );
    return;
  }
  // pagine: rete prima, pagina offline se non c'è connessione
  if (e.request.mode === "navigate") {
    e.respondWith(fetch(e.request).catch(() => caches.match("/offline.html")));
  }
});
