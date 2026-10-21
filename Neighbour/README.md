# Neighbour

**Persone vere. Più vicine.**

Neighbour è un'app iperlocale, per Android (PWA installabile) e web, che collega persone dello stesso quartiere per scambiare oggetti, chiedere piccoli aiuti, scoprire eventi, trovare servizi e condividere segnalazioni utili. È una rete circoscritta e basata sulla fiducia, non un social network generalista.

© Roberto Di Flumeri

## Avvio rapido (Windows)

Requisiti: Python 3.11+ e Node.js 20+.

```
start.bat
```

`start.bat` fa tutto da solo:

1. chiude eventuali avvii precedenti (solo le porte 8200 e 3200);
2. installa le dipendenze;
3. crea il quartiere dimostrativo se manca;
4. compila il frontend;
5. avvia backend e frontend e apre http://localhost:3200.

- **App (PWA):** http://localhost:3200
- **Documentazione API (OpenAPI 3):** http://localhost:8200/docs
- **Quartiere dimostrativo (Isola, Milano):** tutti con password `VicinoDemo2026`

  | Utente | Email | Ruolo |
  |---|---|---|
  | Giulia | `giulia@neighbour.it` | residente: regali, segnalazioni, gruppo "Cani dell'Isola" |
  | Marco | `marco@neighbour.it` | contributore: organizza il calcetto del giovedì |
  | Elena | `elena@neighbour.it` | residente anziana: richieste di aiuto |
  | Panificio Isola | `forno@neighbour.it` | profilo professionale con scheda servizio |
  | Dott.ssa Bianchi | `veterinaria@neighbour.it` | profilo professionale (ambulatorio veterinario) |
  | Luca, Sara, Chiara, Fatima, Dario | `luca@`, `sara@`, `chiara@`, `fatima@`, `dario@neighbour.it` | studenti, famiglie, nuovi arrivati |
  | Nonno Giorgio | `giorgio@neighbour.it` | pensionato, ripara di tutto |
  | Moderazione | `moderatore@` / `moderatrice@neighbour.it` | due moderatori: i ricorsi li decide l'altro |

- **Ricreare il quartiere da zero:** `backend\.venv\Scripts\python backend\scripts\demo.py --reset`
- **Nominare un moderatore:** `backend\.venv\Scripts\python backend\scripts\moderatore.py email@esempio.it`

Il quartiere contiene 14 vicini, 42 annunci, aiuti e segnalazioni (25 foto), 9 eventi, 6 gruppi, 9 servizi, 12 conversazioni, feedback e segnalazioni alla moderazione con un ricorso aperto. I profili lo dichiarano nella loro presentazione e le foto sono illustrazioni di esempio: contenuti "seed" trasparenti, come previsto dal §11 della specifica.

Gli screenshot di tutta l'app (desktop, tema scuro e mobile) sono nella cartella `screens/`.

## Architettura

| Livello | Tecnologia (tutto gratuito e open source) |
|---|---|
| Client | PWA Next.js 16 + React 19 + Tailwind CSS, installabile su Android e desktop; mappa Leaflet + OpenStreetMap |
| API | FastAPI (REST/JSON, OpenAPI 3), monolite modulare separabile per router |
| Persistenza | SQLite in locale; PostgreSQL in produzione (`NEIGHBOUR_DATABASE_URL=postgresql+asyncpg://…`) |
| File | Foto ricodificate con Pillow e servite con URL firmati HMAC a scadenza (stesso schema degli URL firmati S3) |
| Identity | bcrypt, access token JWT da 15 minuti e refresh token ruotato in cookie httpOnly, con revoca della famiglia se riusato |
| Osservabilità | Log JSON senza contenuti personali, latenza p95 e tasso di errori per rotta |

```
Neighbour/
├─ backend/   API FastAPI, modelli, servizi, 89 test automatici (pytest)
├─ frontend/  PWA Next.js (24 pagine), service worker, manifest, icone dal logo
└─ start.bat  avvio completo
```

## Funzionalità (riferimento alla specifica)

| ID | Funzionalità | Dove |
|---|---|---|
| F01 | Mappa di quartiere con zone approssimate (~150 m) e filtri per tipo e distanza | Mappa |
| F02 | Annunci e mercatino: offerte, richieste, regali, scambi; stati bozza → pubblicato → prenotato → chiuso | Pubblica, scheda annuncio |
| F03 | Eventi con luogo approssimativo, capienza, adesione libera o su approvazione, lista d'attesa con promozione automatica, aggiornamenti ai partecipanti | Eventi |
| F04 | Richieste di aiuto non emergenziali con categoria, disponibilità e scadenza | Pubblica → Chiedo aiuto |
| F05 | Chat privata contestuale con blocco e segnalazione immediati, limite di 3 messaggi senza risposta, quota di nuove chat | Messaggi |
| F06 | Segnalazioni di quartiere con foto, validità e stato "risolto"; avviso automatico del 112 sulle emergenze | Pubblica → Segnalazione |
| F07 | Servizi locali, con "Suggerito da un vicino" distinto da "Scheda del professionista" e consigli dei vicini | Servizi locali |
| F08 | Gruppi di interesse con bacheca ed eventi riservati ai membri | Gruppi |
| F09 | Reputazione contestuale solo dopo uno scambio o un evento concluso, senza punteggio globale | Profili |
| F10 | Notifiche per area, tipo e categoria, con ore di silenzio, soglia oraria e riepilogo giornaliero | Notifiche |

## Privacy, sicurezza e moderazione (§7, §8, §11)

- **Posizione:**
  - l'area dell'utente si salva come centro di una cella geohash di circa 1,2 km;
  - i contenuti come centro di una cella di circa 150 m;
  - nessuna coordinata precisa e nessun indirizzo escono dal server;
  - il feed filtra lato server con i prefissi geohash e la distanza tra i centri delle celle.
- **Visibilità:** ogni contenuto rispetta sia il raggio di chi pubblica sia quello di chi legge (da 300 m a 5 km).
- **Dati personali nei testi pubblici:** telefono, email e indirizzo con civico vengono bloccati; si condividono solo in chat.
- **Cifratura:** email salvata solo come hash con pepper; messaggi ed evidenze delle segnalazioni cifrati a riposo (Fernet).
- **Foto:** ricodificate, quindi senza EXIF né posizione GPS; i file non immagine vengono rifiutati.
- **Consensi:** separati e versionati per trattamento, geolocalizzazione, notifiche, profilazione (feed "Per te") e statistiche, con registro storico. Usare la posizione GPS richiede il consenso, verificato lato server.
- **Blocchi:** valgono in entrambe le direzioni, sia su feed, mappa, chat e notifiche sia nell'apertura dei profili.
- **Anti-spam:**
  - 10 pubblicazioni ogni 24 ore;
  - stop ai contenuti ripetuti entro 7 giorni;
  - limiti su chat, segnalazioni, gruppi e servizi;
  - profili professionali ben riconoscibili.
- **Moderazione:**
  - motivi di segnalazione codificati, con copia del contenuto come evidenza;
  - decisioni motivate: archiviazione, rimozione, avviso, sospensione di 7 giorni in sola lettura, blocco;
  - ricorso deciso da un moderatore diverso;
  - elenco degli utenti recidivi, audit di ogni azione e dashboard qualità.
- **Account:** esportazione JSON e Markdown, cancellazione self-service con ri-autenticazione e ricevuta.
- **Offline:** l'interfaccia resta disponibile, le bozze si salvano sul dispositivo e si possono consultare gli ultimi contenuti visti; le API non passano mai dalla cache del service worker.

## Test

```
cd backend
.venv\Scripts\python -m pytest
```

Ci sono 89 test su autenticazione, autorizzazione, validazione, privacy della posizione, feed e paginazione, eventi (anche con richieste concorrenti), chat, reputazione, moderazione, notifiche, foto e cancellazione. In più, durante lo sviluppo l'app è stata provata dall'interfaccia con un test end-to-end di 32 flussi su desktop e mobile.

## Fuori perimetro MVP (come da specifica)

Pagamenti integrati, logistica, tracciamento della posizione in tempo reale e marketplace professionale con fatturazione.
