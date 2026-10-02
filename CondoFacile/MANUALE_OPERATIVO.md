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

Il database parte vuoto: nessun condominio/condòmino demo precaricato. L'amministratore crea condominii e condòmini da Anagrafica, oppure i condòmini si registrano autonomamente (vedi sotto).

## 2bis. Registrazione nuovo condòmino

Dalla schermata di login, link "Registrati" → form con nome, cognome, unità richiesta, email, telefono, username e password. **L'email è obbligatoria e deve essere un indirizzo Gmail (@gmail.com)**: è l'indirizzo a cui arriverà l'eventuale recupero credenziali. La richiesta resta "in attesa di approvazione" e il login è bloccato finché l'amministratore non la approva.

Approvazione: menu "Richieste" (badge con il conteggio delle richieste in sospeso) → sezione "Richieste di registrazione" → pulsante **"Approva"** → scegli condominio, unità (precompilata con quella richiesta), millesimi e tipo → l'account diventa attivo e il condòmino accede con **lo username e la password scelti in fase di registrazione**. Se l'invio email è configurato riceve anche un'email di conferma. Il pulsante "Rifiuta" elimina la richiesta. (Resta possibile anche il collegamento da Anagrafica → "Aggiungi Condòmino" → tab "Associa utente esistente".)

## 2ter. Recupero credenziali

Dalla schermata di login, link "Password dimenticata?" → l'utente inserisce username o email e invia la richiesta. Se non esiste nessun account con quei dati, viene invitato a registrarsi.

L'amministratore vede le richieste nel menu "Richieste" (stesso badge di cui sopra) → sezione "Richieste di reset password" (condòmino, email, condominio/unità, data) → pulsante **"Approva e invia email"**: il condòmino riceve un'email con il suo username e un link per scegliere una nuova password (pagina `/reimposta-password`, link valido 1 ora e utilizzabile una sola volta). La password originale non può essere inviata perché è salvata solo in forma cifrata (hash).

In alternativa, "Reimposta manualmente" (anche da Anagrafica, icona 🔑) permette all'amministratore di impostare lui una password da comunicare al condòmino; eventuali link già inviati vengono annullati.

**Configurazione invio email (Gmail)** in `backend/.env`:
- `SMTP_USER` = indirizzo Gmail mittente
- `SMTP_PASS` = "password per le app" di 16 caratteri (Account Google → Sicurezza → Verifica in due passaggi attiva → Password per le app)
- `FRONTEND_URL` = indirizzo pubblico del frontend usato nei link (default `http://localhost:3000`)

Senza `SMTP_USER`/`SMTP_PASS` l'approvazione di un recupero credenziali viene bloccata con un messaggio di errore (la richiesta resta in attesa); l'approvazione delle registrazioni funziona comunque, ma senza email di conferma. Dopo aver modificato `.env` riavviare il backend.

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

Il database parte vuoto: per testare la vista condòmino, crea prima un condòmino con account da Anagrafica (o registrati da `/registrati` e approva la richiesta da "Richieste"), poi fai logout e accedi con quelle credenziali.

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
