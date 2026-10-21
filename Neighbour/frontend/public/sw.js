/* Neighbour — service worker (© Roberto Di Flumeri)
 * Rende l'app installabile (Android e desktop) e disponibile offline per la parte statica.
 * Le API (/v1) non passano mai dalla cache del service worker: contengono dati personali.
 * Gli ultimi contenuti consultati restano nel dispositivo (localStorage) per la consultazione offline. */
const VERSIONE = "neighbour-v1";
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
  if (e.request.mode === "navigate") {
    e.respondWith(fetch(e.request).catch(() => caches.match(e.request).then((r) => r || caches.match("/offline.html"))));
  }
});

// notifiche del dispositivo: un clic apre il contenuto collegato
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  const link = (e.notification.data && e.notification.data.link) || "/app/notifiche";
  e.waitUntil(self.clients.matchAll({ type: "window" }).then((finestre) => {
    for (const f of finestre) if ("focus" in f) { f.navigate(link); return f.focus(); }
    return self.clients.openWindow(link);
  }));
});
