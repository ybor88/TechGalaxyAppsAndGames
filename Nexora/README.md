# Nexora — ERP cloud per PMI ed e-commerce

Gestionale web e mobile (PWA installabile) per vendere qualsiasi tipo di prodotto: vendite,
clienti, magazzino multi-sede, acquisti, fatturazione elettronica, analytics, motore AI e
integrazioni e-commerce. Specifica funzionale: `Nexora_Specifica_Tecnica_Funzionale.txt`.

© Roberto Di Flumeri

## Moduli

| Modulo | Cosa fa |
| --- | --- |
| Dashboard | Fatturato (mese, confronto con lo stesso periodo del mese precedente, anno), ordini aperti, prodotti più venduti, valore di magazzino, scadenze, previsione a 30 giorni, alert |
| Catalogo | SKU, barcode EAN (anche con lettore USB o fotocamera), categorie, prezzi, varianti (taglia, colore…), immagini, lotti e seriali, esportazione CSV |
| CRM clienti | Anagrafica completa (aziende e privati), storico ordini e fatture, credito aperto, profilo RFM |
| Fornitori | Anagrafica, tempi di consegna, condizioni di pagamento, storico acquisti |
| Ordini di vendita | Bozza → confermato (merce impegnata) → in preparazione → spedito (scarico + DDT) → consegnato; spedizioni GLS/DHL con tracking ed etichetta PDF |
| Magazzino multi-sede | Carico, scarico, trasferimenti, rettifiche, inventari con differenze, lotti con scadenza (FEFO), numeri di serie |
| Acquisti | Ordini ai fornitori, invio, ricevimento anche parziale con lotti/seriali e costo medio ponderato |
| Fatturazione | Preventivi (accettazione → ordine), DDT, fatture, note di credito con reso; PDF, XML FatturaPA 1.2 e stato SdI, scadenze rateali, incassi e link di pagamento |
| Analytics | Ricavi, margini, categorie, canali, clienti e prodotti per periodo |
| Motore AI | Previsione vendite (Holt-Winters con stagionalità settimanale), riordino automatico (punto di riordino con scorta di sicurezza), analisi clienti RFM con rischio di abbandono, alert intelligenti |
| Integrazioni | Shopify, WooCommerce, Magento, Stripe, PayPal, GLS, DHL (webhook firmati, modalità dimostrativa senza credenziali) |
| Sicurezza | JWT con refresh token ruotato, MFA TOTP con codici di recupero, SSO OpenID Connect (PKCE), OAuth2 client credentials per le API, ruoli, isolamento tra aziende, rate limiting, audit log |

## Architettura

- **backend/** — API REST Node.js (Fastify, TypeScript), Prisma, SQLite in locale (lo schema è
  portabile su PostgreSQL), PDF con PDFKit, barcode e QR. Porta 8300.
- **frontend/** — Next.js (React, TypeScript, Tailwind), PWA con funzionamento offline di base.
  Porta 3300; le chiamate `/api` passano dal proxy verso il backend.

## Avvio in locale

Requisiti: Node.js 20 o successivo.

```bash
# backend
cd backend
npm install
npm run db:push        # crea il database SQLite (prisma/nexora.db)
npm run generate
npm run demo           # azienda dimostrativa: 1 anno di attività simulata
npm run dev            # http://127.0.0.1:8300

# frontend (in un altro terminale)
cd frontend
npm install
npm run dev            # http://localhost:3300
```

In produzione: `npm run build && npm start` in entrambe le cartelle. I segreti (JWT e chiave di
cifratura dei dati) vengono generati al primo avvio in `backend/.secrets.json`, oppure letti da
`NEXORA_JWT_SECRET` e `NEXORA_DATA_KEY`.

### Dati dimostrativi

| Comando (in `backend/`) | Contenuto |
| --- | --- |
| `npm run demo` | 1 anno, 23 prodotti (con varianti, lotti e seriali), 88 clienti, 3 magazzini, ~2.000 ordini |
| `npm run demo -- --reset` | ricrea l'azienda dimostrativa da zero |
| `npm run demo -- --reset --grande` | base dati grande: 2 anni, oltre 120 articoli, ~1.100 clienti, 4 magazzini, ~11.000 ordini e fatture, ~30.000 movimenti (circa 25 minuti) |

La simulazione usa gli stessi servizi dell'app: ordini con stagionalità, spedizioni e DDT, fatture
e incassi (con qualche ritardo), riordini ai fornitori basati sulle vendite reali, preventivi,
note di credito, lotti in scadenza e integrazioni.

Accessi (password per tutti `NexoraDemo2026`):

| Email | Ruolo |
| --- | --- |
| admin@nexora.demo | amministratore |
| vendite@nexora.demo | vendite |
| magazzino@nexora.demo | magazzino |
| contabilita@nexora.demo | contabilità |
| direzione@nexora.demo | sola lettura |

## Test

```bash
cd backend
npm test               # 63 test: sicurezza, vendite e documenti, catalogo e magazzino, AI, integrazioni
npm run typecheck
cd ../frontend
npm run typecheck
```

## Schermate

In `screenshots/` (1920×1080, base dati grande): accesso, dashboard, alert, ordini, dettaglio
ordine, clienti, scheda cliente, documenti, fattura, scadenze e incassi, prodotti, scheda
prodotto con varianti, giacenze, lotti e scadenze, acquisti, analytics, motore AI, integrazioni,
impostazioni.
