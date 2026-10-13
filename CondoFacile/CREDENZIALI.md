# Credenziali di accesso (ambiente demo/sviluppo)

Queste credenziali sono create dallo script di seed (`backend/prisma/seed.ts`) e servono solo per test locali. Non sono mostrate nella schermata di login dell'app.

## Amministratore
- Username: `admin`
- Password: `admin123`

## Condòmini
Nessun condòmino demo viene creato all'avvio (`start.bat` esegue `backend/prisma/seed.js`, che garantisce solo l'account admin).
Per provare l'accesso da condòmino: registrati da "Registrati" con un indirizzo Gmail, poi approva la richiesta come admin da menu "Richieste".

## Dati demo massivi (test di carico)
Lo script `backend/prisma/seed-massivo.js` (`node prisma/seed-massivo.js` dalla cartella `backend`) aggiunge 20 condomìni "Residenza Demo …" con ~850 condòmini, ~600 account, 24 mesi di quote e pagamenti, ~1.400 ticket, assemblee, comunicazioni e documenti. Non cancella dati esistenti e non si ripete se i dati demo sono già presenti (usa `--force` per aggiungere un altro lotto).

- Tutti gli account condòmino demo hanno password: `password123`
- Username nel formato `nome.cognome.<id>` (es. `sara.romano.14`): l'elenco completo è visibile da admin in Anagrafica di ogni condominio.
- Nella Dashboard l'amministratore sceglie il condominio da visualizzare dal menu "Condominio".

Verifica rapida di tutti gli endpoint (backend avviato): `node test/smoke-endpoints.js http://localhost:3001/api`
