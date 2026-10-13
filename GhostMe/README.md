# Ghost Me

**Una copia digitale di te, sotto il tuo controllo.**

Ghost Me costruisce un alter ego digitale a partire da risposte, preferenze e contenuti forniti
volontariamente dall'utente. L'alter ego dialoga, riassume il profilo dichiarato e simula possibili
scelte, sempre con l'etichetta "contenuto generato": non è una previsione certa, una diagnosi o la
volontà reale dell'utente.

© Roberto Di Flumeri

## Avvio rapido (Windows)

Requisiti: Python 3.11+ e Node.js 20+. Facoltativo: [Ollama](https://ollama.com) per risposte con un modello linguistico locale.

```
start.bat
```

Doppio clic su `start.bat`: chiude eventuali avvii precedenti (solo porte 8100/3100), installa le
dipendenze, crea l'utente dimostrativo se manca, compila il frontend, avvia backend e frontend in due
finestre e apre http://localhost:3100. Per fermare l'app chiudi le due finestre "Ghost Me".

- App (PWA): http://localhost:3100
- Documentazione API (OpenAPI 3): http://localhost:8100/docs
- Utenti dimostrativi (creati da start.bat):
  - `demo@ghostme.it` / `DemoGhost2026`: profilo base
  - `sofia@ghostme.it` / `SofiaGhost2026`: avatar completo con un anno di storia (49 dichiarazioni, 8 conoscenze,
    7 conversazioni, 6 decisioni, 4 versioni, 23 voci di diario, link condivisi, compatibilità con Luca)
  - `luca@ghostme.it` / `LucaGhost2026`: secondo utente per la compatibilità
  - Per ricreare Sofia da zero: `backend\.venv\Scripts\python backend\scripts\popola.py --reset`
- Moderatore: `backend\.venv\Scripts\python backend\scripts\moderatore.py email@esempio.it`

## Architettura

| Livello | Tecnologia (tutto gratuito / open source) |
|---|---|
| Client | PWA Next.js 16 + React 19 + Tailwind CSS, installabile su Android e desktop |
| API | FastAPI (REST/JSON, OpenAPI 3), monolite modulare |
| Persistenza | SQLite in locale; PostgreSQL in produzione (`GHOSTME_DATABASE_URL=postgresql+asyncpg://…`) |
| Sicurezza | bcrypt, JWT con access token breve (15 min) e refresh token ruotato in cookie httpOnly, cifratura Fernet a riposo |
| AI | Motore integrato istantaneo (ricerca BM25 sulle fonti autorizzate, citazioni) oppure Ollama locale, con fallback automatico |

```
GhostMe/
├─ backend/   API FastAPI, modelli, servizi, 124 test automatici (pytest)
├─ frontend/  PWA Next.js (20 pagine), service worker, manifest, icone
└─ start.bat  avvio completo
```

## Funzionalità (riferimento alla specifica)

| ID | Funzionalità | Dove |
|---|---|---|
| F01 | Profilo dichiarato: questionario + dichiarazioni con categoria, importanza, validità, visibilità, uso AI | Il mio profilo |
| F02 | Chat con l'alter ego, solo su dati autorizzati, con fonti e confidenza | Chat |
| F03 | Simulatore di decisioni con criteri pesati, pro/contro e stime dichiarate | Simulatore decisioni |
| F04 | Versioni passate immutabili (impronta SHA-256) e simulazioni future etichettate; chat con una versione | Versioni |
| F05 | Compatibilità consensuale con codice invito e criteri visibili | Compatibilità |
| F06 | Diario di evoluzione e storico delle modifiche | Diario, Timeline |
| F07 | Esito delle decisioni e apprendimento che aggiorna il profilo | Simulatore decisioni |
| F08 | Link condiviso con scope, limite domande, scadenza, revoca, watermark, segnalazioni | Condivisione |
| F09 | Sfide di auto-riflessione non cliniche | Sfide |
| F10 | Timeline con eventi, fonti e variazioni per categoria (nessun punteggio psicologico) | Timeline |

Privacy e sicurezza (§8): consensi per finalità con registro storico, cifratura dei contenuti a riposo,
email salvata solo come hash, controllo di proprietà su ogni risorsa, rate limiting, esportazione
JSON/Markdown, cancellazione self-service con ri-autenticazione e ricevuta, conservazione delle chat
configurabile, moderazione con audit senza accesso ai contenuti, log senza dati personali.

Regole etiche applicate dal motore: rifiuto di imitare terze persone, nessuna deduzione di attributi
sensibili non dichiarati, avviso quando si toccano temi medici/legali/finanziari, indicazioni di aiuto
(Telefono Amico, 112) in caso di frasi di crisi, neutralizzazione di istruzioni nascoste nelle fonti
(prompt injection).

## Test

```
cd backend
.venv\Scripts\python -m pytest
```

## Configurazione

Variabili d'ambiente (prefisso `GHOSTME_`, oppure file `backend/.env`): `DATABASE_URL`, `FRONTEND_ORIGIN`,
`OLLAMA_URL`, `OLLAMA_MODEL`, `OLLAMA_TIMEOUT_S`, `COOKIE_SECURE` (true dietro HTTPS).
I segreti (chiave JWT, chiave di cifratura, pepper email) se non indicati vengono generati al primo
avvio in `backend/.secrets`: **conserva una copia di questo file**, senza la chiave di cifratura i
dati salvati non sono più leggibili.

Per installare la PWA su Android da un altro dispositivo serve HTTPS (requisito dei browser per i
service worker): pubblica l'app dietro un reverse proxy con certificato (es. Caddy o nginx + Let's Encrypt).
