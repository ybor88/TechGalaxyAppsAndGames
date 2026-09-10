# ScoutTable AI (Android)

App nativa Android (Kotlin + Jetpack Compose + Material 3) per lo scouting automatizzato di
giocatori di **basket** e **calcio**: basta incollare una lista di nomi, l'app recupera da
internet le informazioni di ciascun giocatore e le mostra in tabella con filtri e ordinamento,
calcola una classifica "miglior club" e gestisce una revisione mensile dei giocatori attivi.
Stile grafico derivato dal logo (`logoScoutTable.jpeg`): sfondo blu notte, card molto
arrotondate, accenti in gradiente blu → verde.

## Stato del progetto

Scaffold completo e funzionante offline (Room + WorkManager), per entrambe le sezioni
Basket/Calcio:

- **Mostra lista**: tabella con logo, giocatore, anno, carriera migliore, stato, nazione;
  ordinamento di default per nome; filtri per stato/nazione/carriera migliore + ricerca
  testuale; icona "visiona" che apre la ricerca video di Google per quel giocatore; tocca una
  riga per aprire la **modifica manuale** (o eliminare il giocatore).
- **Genera nuova lista** e **Aggiorna nuova lista**: stesso meccanismo, incolli un elenco di nomi
  (uno per riga) e l'app li cerca su internet. In entrambi i casi i giocatori trovati si
  **aggiungono** alla lista esistente dello sport; se un giocatore è già presente (stesso
  `giocatore + nazione`) viene aggiornato con le informazioni nuove invece di duplicarsi. Non si
  cancella mai la lista precedente.
- **Miglior club**: raggruppa i giocatori per `carriera_migliore` (il club recuperato da
  internet) e mostra una classifica per numero di giocatori, con drill-down sui nominativi.
- **Revisione giocatori**: ogni 30 giorni (WorkManager) marca i giocatori con stato "Attivo"
  come da rivedere; pulsante per eseguire subito e uno per rilanciare automaticamente la
  ricerca su internet per ciascun giocatore segnalato, aggiornando i dati in-app.
- **Backup dati**: icona nella barra in alto → "Esporta backup" salva un file con il selettore
  di sistema Android (l'utente sceglie dove: una cartella su Google Drive se l'app Drive è
  installata, o qualunque altra posizione); "Importa backup" rilegge quel file (anche su un
  altro dispositivo) e sovrascrive i dati locali. Nessun accesso/account richiesto lato app.

## Recupero automatico dei dati da internet

Non serve preparare nessun file: "Genera nuova lista" e "Aggiorna nuova lista" mostrano solo
una casella di testo dove incollare i nomi (uno per riga, opzionalmente `Nome Cognome Anno` per
evitare omonimi — es. "Francesco Totti 1976"). `PlayerLookupService`
(`data/lookup/PlayerLookupService.kt`) combina più fonti:

- **[TheSportsDB](https://www.thesportsdb.com/)** (API gratuita, chiave di test `123`): anno di
  nascita, nazione, ruolo, club attuale e stemma — sia per il calcio (`strSport = "Soccer"`) sia
  per il basket (`strSport = "Basketball"`).
- **Wikipedia** (`action=raw`, nessuna chiave richiesta): presenze/gol di carriera per il calcio
  (sommando `capsN`/`goalsN` dall'infobox) e presenze stimate/punti/assist per il basket (da
  `statNvalue`, es. "32,292 (30.1 ppg)" → punti e partite stimate dividendo per la media). Serve
  anche come **fallback per i giocatori ritirati**: TheSportsDB smette di riportare un club reale
  per loro (usa un placeholder `"_Retired ..."`), quindi club/competizione/stemma per un ritirato
  vengono recuperati dal club con più presenze su Wikipedia, poi cercato di nuovo su TheSportsDB
  per lo stemma.
- **Proballers** (opzionale, solo basket): se nella riga incolli anche il link alla pagina
  giocatore (`Nome Cognome | https://www.proballers.com/...`), i suoi dati stagione-per-stagione
  (più precisi della stima Wikipedia) hanno la precedenza.

Limiti da tenere presenti:
- Sono fonti gratuite di terze parti: nomi poco noti o scritti in modo ambiguo possono non
  essere trovati (l'app segnala quali nomi non ha trovato a fine ricerca).
- Le presenze per il basket (senza URL Proballers) sono una **stima**, non un dato esatto.
- L'assist non è tracciato per il calcio da nessuna delle due fonti: il campo esiste nel database
  ma non viene mostrato in UI per quello sport.
- Se la ricerca prende dati sbagliati/incompleti, tocca il giocatore nella lista per aprire
  la **modifica manuale** (modifica tutti i campi, oppure elimina il giocatore).

## Classifica "Miglior club"

Raggruppa i giocatori per `carriera_migliore` (il club recuperato da internet, o inserito a
mano) e conta quanti giocatori della tua lista appartengono a ciascun club, in ordine
decrescente — calcolato separatamente per Basket e Calcio. Non essendoci più un punteggio
("valore") a disposizione, la classifica riflette quanto un club è rappresentato nella tua
lista di giocatori scoutati, non una valutazione qualitativa assoluta del club.

## Come aprire/buildare il progetto

1. Installa **Android Studio** (Koala o successivo) oppure usa `gradlew.bat` da riga di
   comando (richiede l'Android SDK — percorso in `local.properties`, non versionato).
2. Apri la cartella come progetto esistente, lascia sincronizzare Gradle.
3. Esegui `start.bat` (Windows) per buildare, installare su un device/emulatore connesso e
   lanciare l'app; `start.bat --dry-run` per vedere i comandi senza eseguirli.

## Backup dati (Google Drive incluso, senza configurazione)

`BackupManager` (`data/drive/BackupManager.kt`) esporta/importa il database come un file
qualunque, tramite il selettore di sistema Android (Storage Access Framework):

- **Esporta backup**: apre il dialog "Salva come" di sistema. Se l'app Google Drive è
  installata e l'utente è già loggato, "Google Drive" compare tra le posizioni disponibili
  esattamente come per salvare un PDF o una foto da qualsiasi altra app — nessun accesso
  richiesto dentro ScoutTable.
- **Importa backup**: apre il dialog "Apri" di sistema per scegliere il file esportato in
  precedenza (da Drive o da qualunque altra posizione/dispositivo) e sovrascrive i dati locali;
  richiede di riavviare l'app per vedere i dati importati.

Questo approccio (deliberatamente più semplice della prima versione basata su Google Sign-In +
Drive REST API) non richiede nessuna credenziale OAuth né un progetto Google Cloud: la versione
precedente falliva con l'errore "codice 10" (`DEVELOPER_ERROR`) finché non si registrava un
OAuth Client ID per pacchetto+SHA-1, un passaggio manuale scomodo per un uso personale.

## Limiti noti / prossimi passi

- Nessun test automatizzato; verificato solo tramite build (`gradlew assembleDebug`).
- Il ripristino da Drive sovrascrive il file del database mentre l'app è aperta: dopo un
  "Ripristina" è necessario **riavviare l'app**.
- iOS non è coperto da questo progetto (come per `MyCallBagEyeNeurologyIOS`, andrebbe creato
  come progetto separato se necessario).
