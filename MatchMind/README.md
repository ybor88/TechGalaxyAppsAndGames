# MatchMind

**Pronostici di calcio e basket sulle partite reali, con credito virtuale.** App Android (APK).

© Roberto Di Flumeri

MatchMind genera schedine con i soli pronostici che giudica sicuri: **il numero di partite non è fisso**, lo decide
il metodo. Nel calcio sceglie per ogni partita la giocata migliore tra vincente (1/2), pareggio (X), doppia chance
(1X/X2/12), Goal/NoGoal e Under/Over 2,5; nel basket solo il vincente. Gioca le schedine con un credito virtuale e,
dopo le partite, le **valida sui risultati reali**: verde se tutti i pronostici sono corretti, rossa altrimenti. Ogni risultato insegna qualcosa al modello. Il grafico mensile
mostra quanto si è incassato e quanto perso, per capire se nel tempo il metodo crea **credito o debito**.

Nessuna scommessa reale, nessun pagamento: solo credito virtuale.

## Avvio rapido (Windows)

Requisiti: Android Studio (SDK e JDK inclusi). Facoltativo: un emulatore (AVD) o un telefono collegato via USB.

```
start.bat                 unit test + build + emulatore + installa + avvia
start.bat --solo-apk      unit test + build, genera MatchMind.apk senza emulatore
start.bat --salta-test    salta gli unit test
start.bat NomeAVD         usa un emulatore specifico
```

L'APK pronto viene copiato in `MatchMind\MatchMind.apk`. Per il telefono: copialo sul dispositivo, aprilo e
consenti "installa app sconosciute".

## Funzioni

| Funzione | Dove |
|---|---|
| Credito virtuale iniziale, **ricarica** e **azzeramento** del saldo | Primo avvio, Impostazioni |
| Schedina **Calcio** e schedina **Basket** separate | Home |
| Numero di partite deciso dal metodo in base alla **sicurezza** scelta (probabilità minima di vincere la schedina, predefinita 30%) | Impostazioni |
| Tipi di pronostico nel calcio: vincente, pareggio, doppia chance, Goal/NoGoal, Under/Over 2,5 (attivabili singolarmente) | Impostazioni |
| Anteprima con quota totale, probabilità di vincerla, valore atteso, puntata a scelta | Dopo "Genera" |
| **Validazione** sui risultati reali: verde/rossa, risultato e esito di ogni partita | Verifica (anche automatica all'apertura) |
| Grafico mensile **incassato vs perso** con netto del mese, andamento del saldo | Statistiche |
| Credito o debito generato dal metodo (indipendente da ricariche e azzeramenti) | Home, Statistiche |
| Esperienza appresa: affidabilità per tipo di pronostico, calibrazione, peso delle quote, leghe più prevedibili | Statistiche |
| Strategia **Sicura** (massima probabilità) o **Valore** (quota superiore al rischio) | Impostazioni |

## Da dove arrivano i dati

Feed pubblico ESPN (gratuito, senza chiave): calendario, **risultati reali**, classifiche e **quote reali
DraftKings** (1X2 e Under/Over gol). Le richieste partono dal telefono; nessun server intermedio.

- Calcio: Serie A, Serie B, Premier League, Championship, LaLiga, Bundesliga, Ligue 1, Eredivisie, Primeira Liga,
  Scottish Premiership, Süper Lig, Champions, Europa e Conference League, MLS, Liga MX, Brasileirão, Liga Argentina.
- Basket: NBA, WNBA, NCAA.

## Il metodo (perché questo e non un altro)

Ogni partita riceve una probabilità da tre fonti:

1. **Mercato**: le quote del bookmaker convertite in probabilità togliendo il margine. È il predittore singolo
   più accurato perché incorpora informazioni che le statistiche non vedono (infortuni, turnover, motivazioni).
2. **Statistica**: calcio con modello di **Poisson** su gol fatti/subiti per partita (attacco e difesa rispetto
   alla media di lega, vantaggio del campo); basket con la **differenza punti** per partita e vantaggio casa.
   A inizio stagione i dati sono "ammorbiditi" verso la media; nel basket si aggiunge la stagione regolare
   precedente, che pesa sempre meno man mano che si gioca. Le partite di preseason sono escluse.
3. **Gol**: dai gol attesi di casa e ospite, resi coerenti con le quote 1X2 e Under/Over del mercato, si ricavano
   Goal/NoGoal e Under/Over; la doppia chance è la somma di due esiti, con quota ricavata dalle quote reali.
4. **Esperienza**: ogni partita analizzata (non solo le 10 in schedina, circa 100+ al giorno) viene salvata con le
   probabilità previste e confrontata con il risultato reale. Da questo storico l'app impara, separatamente per
   calcio e basket:
   - quanto pesare mercato e statistica (il peso che avrebbe previsto meglio i risultati già visti);
   - la **calibrazione per tipo di pronostico**: se i pronostici dati all'80% si avverano solo il 70% delle volte,
     le probabilità di quel tipo (vincente, pareggio, doppia chance, Goal, Under/Over) vengono corrette;
   - l'**affidabilità delle leghe**: leghe più imprevedibili del previsto vengono penalizzate nella scelta.

   Le correzioni sono prudenti (stimatori bayesiani): pesano poco finché le partite verificate sono poche.

**Quante partite**: si aggiunge un pronostico alla volta, dal più sicuro (probabilità minima 70%, quota minima
1,15), finché la probabilità di vincere l'intera schedina resta sopra la soglia di sicurezza (massimo 10 partite).
Pronostici molto sicuri → più partite; pronostici meno sicuri → meno partite. Se nessun pronostico è abbastanza
sicuro la schedina non viene proposta.

**Onestà matematica**: una multipla vince solo se *tutti* i pronostici sono giusti e il margine del bookmaker si
moltiplica su ogni partita: per questo il valore atteso mostrato prima di giocare è di solito negativo. Con la
strategia Sicura e tutti i tipi attivi prevale la doppia chance (la giocata più probabile, con quote basse). La strategia **Valore** cerca le partite in cui la probabilità
stimata supera quella implicita nella quota. Il grafico mensile dice, con i risultati reali, se il metodo crea
credito o debito.

Regole di validazione come nei bookmaker: nel calcio conta il risultato dei 90 minuti (partite decise ai
supplementari: pronostico annullato; vale per tutti i tipi di pronostico, Goal e Under/Over compresi); partita rinviata o annullata oltre 48 ore: pronostico nullo (quota 1,00);
un solo pronostico sbagliato rende la schedina persa.

## Struttura

```
MatchMind/
├─ app/src/main/java/com/matchmind/app/
│  ├─ dominio/   modelli, probabilità (Poisson, basket), esperienza, generatore, verifica, contabilità
│  ├─ dati/      feed ESPN (parser + client), database Room, preferenze, repository
│  └─ ui/        Compose: Home, Schedine (+ dettaglio/validazione), Statistiche, Impostazioni, grafici
├─ app/src/test/ 49 unit test, con risposte ESPN reali salvate in resources/espn
├─ start.bat     test, build, emulatore, installazione e avvio
└─ MatchMind.apk ultimo APK generato
```

Tecnologie (tutte gratuite): Kotlin, Jetpack Compose (Material 3), Room, DataStore, OkHttp, JUnit.

## Test

```
gradlew.bat testDebugUnitTest
```

## Gioco responsabile

MatchMind è una simulazione: nessun metodo può garantire vincite. Il gioco d'azzardo può causare dipendenza;
Telefono Verde Nazionale 800 558822 (gratuito e anonimo). Vietato ai minori di 18 anni.
