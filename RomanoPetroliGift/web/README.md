# RP Fidelity — Portale Web

Portale di fidelizzazione Romano Petroli (Fase 1 del progetto RP Fidelity). PHP + MySQL "vanilla", senza dipendenze da Composer: deployabile su qualsiasi hosting condiviso via semplice upload FTP.

## Struttura

- `public/` — document root (punta qui il tuo hosting/vhost)
- `src/Core` — router, connessione DB, autenticazione, viste
- `src/Controllers`, `src/Models`, `src/Views` — logica applicativa
- `database/schema.sql` — struttura tabelle
- `database/seed.sql` — dati di prova (admin + cliente demo)
- `api/` — endpoint REST per le future app mobile (Fase 2/3)

## Avvio in locale

1. Copia `config/config.example.php` in `config/config.php` e inserisci le credenziali del tuo MySQL locale.
2. Importa lo schema: `mysql -u root rpfidelity < database/schema.sql`
3. (Opzionale) importa i dati di prova: `mysql -u root rpfidelity < database/seed.sql`
4. Avvia il server di sviluppo PHP:
   ```
   php -S localhost:8000 -t public public/index.php
   ```
5. Apri `http://localhost:8000`

**Utenti demo** (da `seed.sql`):
- Admin: `admin@rpfidelity.it` / `admin123`
- Cliente: `cliente@rpfidelity.it` / `cliente123`

## Deploy su hosting condiviso

1. Carica via FTP l'intero contenuto della cartella `web/` sul tuo hosting.
2. Imposta il **document root** del dominio sulla cartella `public/` (oppure, se non puoi cambiarlo, sposta il contenuto di `public/` nella root pubblica e adatta i percorsi `require` in `index.php`).
3. Crea il database MySQL da pannello hosting e importa `database/schema.sql`.
4. Copia `config/config.example.php` in `config/config.php` con le credenziali fornite dal tuo hosting.
5. Assicurati che il modulo `mod_rewrite` di Apache sia attivo (il file `public/.htaccess` è già incluso).


3387310@aruba.it username
1994Ottobre19! Password