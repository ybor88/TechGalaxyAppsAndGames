# Manuale Operativo – CondoFacile

Guida pratica per avviare l'app e testare tutte le funzionalità, sezione per sezione. Gli screenshot di riferimento sono nella cartella [screenshots/](screenshots/).

## 1. Avvio

Doppio click su `start-dev.bat` nella cartella `CondoFacile`. Si aprono due terminali (backend e frontend) e il browser su `http://localhost:3000/dashboard`.

- Frontend: http://localhost:3000
- Backend API: http://localhost:3001/api

In alternativa, manualmente:
```
cd backend && npm run start:dev
cd frontend && npm run dev
```

## 2. Credenziali

Vedi [CREDENZIALI.md](CREDENZIALI.md) — non sono più mostrate nella schermata di login.

| Ruolo | Username | Password |
|---|---|---|
| Amministratore | `admin` | `admin123` |
| Condòmino | `mario.rossi` | `condo123` |

Il condòmino demo è collegato all'unità A2 di "Parco Volta".

## 3. Panoramica ruoli

- **Amministratore**: accesso completo — anagrafica, quote, documenti, fornitori, comunicazioni, assemblee, segnalazioni, analytics.
- **Condòmino**: vista personale — le proprie quote, le proprie segnalazioni, bacheca comunicazioni, documenti visibili, assemblee.

Il menu laterale cambia automaticamente in base al ruolo con cui si accede.

## 4. Test per sezione (vista Amministratore)

### Dashboard
Login come `admin` → si apre su `/dashboard`.
- Verificare i 4 contatori in alto (condòmini paganti, segnalazioni aperte, spese mese, lavori in corso)
- Verificare il grafico "Spese Ultimi 6 Mesi" (una barra per mese, non duplicati)
- Verificare le liste "Segnalazioni Aperte" e "Scadenze Imminenti"
- Riferimento: `01-dashboard-admin.png`

### Anagrafica
Menu → Anagrafica.
- Selezionare un condominio dalla lista a sinistra → il pannello destro mostra i condòmini
- **Modifica Condominio**: bottone "Modifica" in alto → cambiare nome/indirizzo → Salva → verificare che il nome si aggiorni sia nell'header sia nella lista a sinistra
- **Aggiungi Condòmino**: bottone "Aggiungi Condòmino" → compilare il form (nome, cognome, unità obbligatori) → verificare che appaia nella lista
- **Modifica/Disattiva Condòmino**: icone matita/power su ogni riga
- Riferimento: `02-anagrafica.png`, `02b-anagrafica-modifica-modal.png`

### Fornitori
Menu → Fornitori.
- "Aggiungi" per creare un nuovo fornitore (nome, tipo, contatti)
- "Analytics" in alto per la panoramica interventi/costi per fornitore
- Riferimento: `03-fornitori.png`, `03b-fornitori-analytics.png`

### Documenti
Menu → Documenti.
- "Carica" per aggiungere un documento (categoria, visibilità pubblica/privata/selettiva)
- Verificare che i documenti siano raggruppati per categoria (Regolamento, Verbali, Fatture, Contratti, Certificazioni, Polizze, Planimetrie)
- Icona matita/download/cestino su ogni documento
- Riferimento: `04-documenti.png`

### Comunicazioni
Menu → Comunicazioni.
- Creare una nuova comunicazione (tipo: avviso/assemblea/manutenzione/emergenza/circolare)
- Verificare il badge colorato per tipo e il conteggio letture
- Riferimento: `05-comunicazioni.png`

### Assemblee
Menu → Assemblee.
- Creare una nuova assemblea (data, luogo, ordine del giorno)
- Aprire un'assemblea conclusa per vedere verbale, punti OdG e presenze
- Riferimento: `06-assemblee.png`

### Quote & Pagamenti
Menu → Quote & Pagamenti.
- Selezionare un condominio dal menu a tendina
- "Nuova Quota" per creare una rata mensile (collettiva o personale)
- Cliccare su una quota per vedere il dettaglio pagamenti per condòmino e cambiarne lo stato (pagato/in attesa/in mora)
- Riferimento: `07-pagamenti.png`

### Segnalazioni (Ticket)
Menu → Segnalazioni.
- Filtri per stato, priorità, categoria in alto
- Aprire una segnalazione per vedere dettagli, note, e cambiare stato/priorità/assegnatario
- Riferimento: `08-ticket-admin.png`

### Analytics
Menu → Analytics (panoramica fornitori/interventi).
- Riferimento: `09-analytics.png`

### Impostazioni
Menu → Impostazioni: profilo utente, foto profilo, ruolo.
- Riferimento: `10-impostazioni-admin.png`

## 5. Test vista Condòmino

Logout → login come `mario.rossi` / `condo123`.

- **Dashboard**: sintesi quota corrente, scadenze, storico comunicazioni, segnalazioni aperte — `11-dashboard-condomino.png`
- **Le mie Quote**: storico pagamenti con stato pagato/in attesa/in mora e download ricevuta per i pagati — `12-mie-quote-condomino.png`
- **Segnalazioni**: aprire una nuova segnalazione, verificare che compaia nella lista — `13-ticket-condomino.png`
- **Bacheca (Comunicazioni)**: sola lettura, marcare come letta — `14-comunicazioni-condomino.png`
- **Documenti**: solo i documenti visibili alla propria unità — `15-documenti-condomino.png`

## 6. Cosa verificare sempre dopo una modifica

1. Il dato aggiornato compare subito senza bisogno di refresh manuale della pagina
2. Nessun errore in console del browser (F12 → Console)
3. Il dato è coerente tra le diverse viste che lo mostrano (es. rinominare un condominio in Anagrafica deve riflettersi anche in Dashboard/Quote/Ticket)

## Note

- Il database di sviluppo (`backend/prisma/dev.db`) contiene dati demo realistici (10 condòmini, fornitori, documenti, quote, segnalazioni). Per resettarlo, vedi lo script di seed in `backend/prisma/seed.ts`.
