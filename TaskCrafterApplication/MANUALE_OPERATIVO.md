# Manuale Operativo – TaskCrafter

Guida pratica per avviare l'app desktop e testare tutte le funzionalità. Gli screenshot di riferimento sono nella cartella [screenshots/](screenshots/). Per l'installazione da zero su un altro PC vedi [MANUALE_INSTALLAZIONE.md](MANUALE_INSTALLAZIONE.md).

## 1. Avvio

```
mvn clean package
java -jar target/TaskCrafterApplication-1.0-SNAPSHOT-jar-with-dependencies.jar
```

oppure doppio click su `start.bat`. I task sono salvati in `tasks.json` nella cartella del progetto — cancellalo (o rinominalo) per ripartire con lista vuota.

Alla partenza appare uno splash screen (3 secondi) — `00_splash_avvio.png` — poi la finestra principale a schermo intero.

## 2. Struttura della finestra

Barra laterale sinistra sempre visibile:
- **Aggiungi Task** — apre il form di inserimento
- **Mostra Task** — torna alla vista corrente lasciando il form
- **Cancella tutti** — svuota l'intera lista (chiede conferma, operazione distruttiva: usarla solo se si vuole davvero ripartire da zero)
- **Viste**: Lista, Kanban, Calendario, Statistiche

## 3. Test per funzionalità

### Viste multiple
Cliccare in sequenza Lista → Kanban → Calendario → Statistiche nella barra laterale e verificare che i task compaiano coerentemente in tutte:
- **Lista**: righe con priorità/stato/scadenza, icone matita (modifica) e cestino (elimina) sulla destra di ogni riga — `02_lista_task.png`
- **Kanban**: tre colonne Da Fare / In Corso / Completato, ogni card ha bottoni "Modifica" ed "Elimina" reali — `03_bacheca_kanban.png`
- **Calendario**: mese corrente con pallino sui giorni con scadenze, click su un giorno per il dettaglio — `04_calendario_scadenze.png`
- **Statistiche**: contatori totale/principali/sottotask/completati/in corso/da fare + barre per stato — `05_statistiche_report.png`

### Progetti e sottotask
Un task può contenere sottotask annidati (vedi ad es. "Lancio sito e-commerce ClientX" nei dati demo). In Lista/Kanban i sottotask compaiono indentati sotto il task padre con l'etichetta "Sublast di: ...".

### Aggiungi Task
Bottone "Aggiungi Task" in alto a sinistra → compilare titolo, descrizione, priorità, scadenza, etichette, stato, ed eventualmente "Sottotask di" per agganciarlo a un task esistente → "Conferma Task" → verificare che compaia in Lista — `07_form_aggiungi_task.png`

### Modifica Task
Da Kanban (o Calendario), bottone "Modifica" su una card → il form si pre-compila con i dati esistenti, il bottone diventa "Conferma Modifica" — `08_form_modifica_task.png`

> Nota: in vista **Lista** la riga usa un componente a lista con zone cliccabili (non bottoni veri): doppio click su una riga, oppure click nella parte destra (icona matita), per modificarla; click sull'icona cestino per eliminarla.

### Elimina Task
Bottone "Elimina" su una card (Kanban/Calendario) o icona cestino in Lista → appare un dialog di conferma arancione "Conferma eliminazione" con bottoni "No" / "Sì, elimina" (stesso stile del dialog mostrato in `01_notifiche_e_automazione.png`). **L'operazione è immediata e non reversibile una volta confermata** — testarla su un task creato apposta per la prova, non su dati reali.

### Ricerca intelligente
Nella vista Lista, campo di ricerca in alto con comandi rapidi via tooltip: `p:alta`, `s:in_corso`, `tag:lavoro`, `overdue`, `oggi`, `open`. Si possono combinare con filtri stato/priorità e checkbox "Solo aperti"/"In ritardo" — `06_ricerca_intelligente.png`

### Promemoria e notifiche desktop
Automatiche, non richiedono azione:
- All'avvio, se ci sono più di 3 task ad ALTA priorità non completati → toast "Allerta priorità" in basso a destra
- Task scaduti o in scadenza entro 60 minuti → toast "Task in ritardo" / "Scadenze imminenti"
- Dopo le 7:00, una volta al giorno → toast "Riepilogo giornaliero" con task completati/in ritardo
- Riferimento: `01_notifiche_e_automazione.png` (toast in basso a destra)

### Automazioni semplici
Se un task **non** ad alta priorità è scaduto, all'avvio (o al successivo controllo periodico) appare un dialog "Automazione Priorità" che chiede conferma prima di alzarne la priorità di un livello. Con più di 3 task scaduti candidati, la richiesta diventa cumulativa ("aumenta per tutti?"). Nessuna modifica avviene senza conferma esplicita — `01_notifiche_e_automazione.png`

### Download task in Excel
Bottone verde "Scarica Excel" nella vista Lista: esporta i task **attualmente filtrati** (non tutta la lista, se sono attivi filtri/ricerca) in un file `.xlsx` con colonne Titolo, Descrizione, Priorità, Stato, Scadenza, Etichette, Tipo Record, Task Principale, Livello. Si apre una finestra di salvataggio file standard di Windows.

## 4. Checklist di collaudo rapido

1. Avvio → splash → finestra principale senza errori in console
2. Le 4 viste mostrano gli stessi task in modo coerente
3. Aggiungi → Modifica → verificare che i dati coincidano
4. Elimina un task di prova → conferma → verificare che sparisca da tutte le viste
5. Ricerca con un comando rapido (es. `p:alta`) → verificare che filtri correttamente
6. Esporta Excel con un filtro attivo → verificare che il file contenga solo i task filtrati

## Note sui dati demo

`tasks.json` contiene un set di dati demo realistico (progetti con sottotask, priorità/stati misti, scadenze passate/future) utile per vedere tutte le viste popolate. Il task originale "TAGLIANDO AUTO" è stato mantenuto tra i dati demo.
