# Ghost Me — Manuale operativo

© Roberto Di Flumeri

## 1. Primo accesso
1. Apri l'app e scegli **Crea il tuo alter ego**.
2. Inserisci nome, email e password (almeno 10 caratteri con lettere e numeri).
3. Scegli i consensi. Solo il *trattamento del profilo* è obbligatorio; *elaborazione AI* serve per chat
   e simulatore, *condivisione* per link e compatibilità, *statistiche* è facoltativo.
4. Accetta i termini d'uso e conferma: vieni portato al questionario.

## 2. Il mio profilo
- **Questionario**: 15 domande su valori, abitudini, gusti, obiettivi e stile comunicativo. Rispondi solo
  a quelle che vuoi; per ciascuna indichi l'importanza (1-5) e se è condivisibile nei link.
- **Tutte le dichiarazioni**: aggiungi, modifica o elimina. Per ogni dichiarazione puoi:
  - attivare/disattivare **AI** (se spenta, l'alter ego non la usa);
  - renderla **condivisibile** o **privata** (l'occhio);
  - impostare una data di fine validità.
  Ogni modifica resta visibile nella Timeline.

## 3. Conoscenze
Note, ricordi o testi più lunghi. Se un testo contiene frasi che sembrano comandi per l'AI
(es. "ignora le istruzioni") viene segnalato e trattato come semplice dato.

## 4. Chat con l'alter ego
- Ogni risposta mostra **Contenuto generato**, il livello di **confidenza** e le **fonti personali** usate.
- Puoi iniziare una conversazione con una **versione passata o simulata** del profilo (menu "Parla con").
- L'alter ego non imita altre persone, non fa diagnosi e non deduce caratteristiche delicate non dichiarate.
- Motore: in *Privacy e dati* scegli **Integrato** (istantaneo), **Automatico** o **Ollama** (più
  naturale, richiede Ollama avviato; senza GPU può impiegare decine di secondi).

## 5. Simulatore di decisioni
1. Scrivi la domanda e almeno due opzioni (la descrizione aiuta le stime).
2. Aggiungi i criteri con la loro importanza, oppure **Usa i miei valori e obiettivi**.
3. Valuta ogni opzione per ogni criterio (1-5). Le celle vuote vengono stimate e segnalate.
4. **Analizza**: vedi la coerenza percentuale, pro e contro. Puoi correggere le valutazioni e ricalcolare.
5. Dopo aver deciso registra **scelta, esito e apprendimento**; l'apprendimento può entrare nel profilo.

## 6. Versioni e Timeline
- **Salva versione**: fotografa il profilo (immutabile, con verifica di integrità).
- **Confronta** due versioni, o una versione con il profilo attuale.
- **Simula il me futuro**: aggiungi, cambia o togli dichiarazioni per creare una versione *ipotetica*, sempre
  etichettata come simulazione.
- La **Timeline** mostra modifiche, versioni, diario e decisioni, e un grafico con il numero di
  dichiarazioni per categoria (non è un punteggio psicologico).

## 7. Diario e Sfide
- **Diario**: check-in o note libere, con energia facoltativa ed etichette. L'alter ego non le legge.
- **Sfide**: 12 esercizi di riflessione; la risposta può diventare una dichiarazione del profilo.

## 8. Condivisione
1. Scegli nome, categorie visibili, numero massimo di domande e scadenza, poi **Crea link**.
2. Copia subito il link: per sicurezza non verrà più mostrato.
3. Chi apre il link vede solo le dichiarazioni *condivisibili* delle categorie scelte, con il watermark
   "Risposta generata" e la possibilità di segnalare abusi.
4. **Revoca** in qualsiasi momento; **Accessi** mostra quando il link è stato usato.

## 9. Compatibilità
1. Scegli le categorie e genera un **codice invito** (valido 7 giorni).
2. L'altra persona, con il proprio account, lo inserisce in **Ho ricevuto un codice**.
3. Entrambi vedete i punti in comune per categoria, con i criteri usati. Ognuno può eliminare il confronto.

## 10. Privacy e dati
- Attiva/revoca i consensi e consulta il **registro** con data e versione.
- Imposta la **conservazione** delle conversazioni (cancellazione automatica).
- **Esporta** tutti i dati in JSON (portabile) o Markdown (leggibile).
- **Cambia password** (chiude le altre sessioni).
- **Cancella account**: password + scrivi ELIMINA. Ricevi un numero di ricevuta.

## 11. Moderazione (solo moderatori)
Segnalazioni ricevute dai link condivisi: archiviare, sospendere il link o bloccare l'utente. Sono visibili
metriche aggregate (utenti, errori, latenza p95 delle API) e il registro di audit. I contenuti personali
degli utenti non sono accessibili.

## 12. Installare l'app
- **Desktop (Chrome/Edge)**: icona "Installa" nella barra degli indirizzi.
- **Android (Chrome)**: menu ⋮ → *Installa app* / *Aggiungi a schermata Home* (richiede HTTPS se l'app non è
  aperta da `localhost`).

## 13. Backup
Copia la cartella `backend` con `ghostme.db` **e** il file `.secrets`: senza `.secrets` i dati cifrati non
sono recuperabili. Per ripristinare, rimetti entrambi i file al loro posto e riavvia.
