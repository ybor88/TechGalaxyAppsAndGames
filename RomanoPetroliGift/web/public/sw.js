// Service worker minimale RP Fidelity — abilita l'installazione come PWA
// (richiesta da Chrome/Android per "Aggiungi a schermata Home"; iOS/Safari non lo richiede
// ma non fa danno averlo). Cache leggera dei soli asset statici, mai delle pagine dinamiche
// (login/dashboard/dati cliente vanno sempre presi in rete).

// v2: strategia network-first (prima la rete, cache solo come fallback offline). Con v1 la
// cache-first serviva per sempre la versione salvata al primo avvio, mostrando ad es. il logo
// alla dimensione originale se lo stile veniva aggiornato dopo l'installazione della PWA.
const CACHE_NAME = 'rpfidelity-static-v2';
const STATIC_ASSETS = [
    '/assets/css/style.css',
    '/assets/img/logo.jpeg',
    '/assets/img/icons/icon-192.png',
    '/assets/img/icons/icon-512.png',
];

self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => cache.addAll(STATIC_ASSETS))
    );
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((keys) =>
            Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
        )
    );
    self.clients.claim();
});

self.addEventListener('fetch', (event) => {
    const url = new URL(event.request.url);

    // Solo asset statici passano dalla cache; tutto il resto (pagine, API) va sempre in rete.
    if (event.request.method === 'GET' && STATIC_ASSETS.includes(url.pathname)) {
        event.respondWith(
            fetch(event.request)
                .then((response) => {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
                    return response;
                })
                .catch(() => caches.match(event.request))
        );
    }
});
