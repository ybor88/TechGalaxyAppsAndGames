# VolcanoEscape

App Android (Kotlin + Jetpack Compose) che aiuta a evacuare rapidamente dall'area di un
vulcano italiano selezionato, mostrando il percorso stradale meno trafficato verso una zona
sicura e tenendo traccia degli ultimi eventi sismici/vulcanici registrati dall'INGV.

## Come funziona

1. **Lista vulcani** — elenco precompilato dei vulcani italiani monitorati dall'INGV (Etna,
   Vesuvio, Campi Flegrei, Stromboli, Vulcano, Ischia, Colli Albani, Pantelleria, Vulture).
2. **Monitoraggio** — per il vulcano scelto, l'app interroga il webservice pubblico e gratuito
   INGV (`webservices.ingv.it/fdsnws/event/1/query`, formato FDSN-event/ISIDe) per le scosse
   registrate negli ultimi 30 giorni entro 30 km dal cratere. Nessuna API key richiesta.
3. **Via di fuga** — presa la posizione GPS dell'utente, l'app calcola un punto sicuro
   proseguendo lungo la direzione "vulcano → utente" (allontanandosi quindi dal vulcano) e
   chiede a TomTom Routing API il percorso stradale con traffico reale, scegliendo tra le
   alternative quella con minor ritardo da traffico.

## Servizi esterni usati (e perché)

| Servizio | Uso | Costo |
|---|---|---|
| [INGV](https://www.ingv.it/) (`webservices.ingv.it`, `terremoti.ingv.it`) | Dati sismici/vulcanici | Gratuito, pubblico, nessuna chiave |
| [TomTom Routing/Maps API](https://developer.tomtom.com/) | Calcolo percorso con traffico reale | Gratuito fino a 2.500 richieste/giorno di routing, **nessuna carta di credito richiesta** |

Google Maps Platform è stato scartato di proposito: richiede una carta di credito registrata
fin dal primo utilizzo e fattura automaticamente oltre le soglie gratuite mensili.

## Setup

1. Procurati una chiave API TomTom gratuita — vedi [docs/TOMTOM_API_KEY.md](docs/TOMTOM_API_KEY.md)
   per i passaggi dettagliati (registrazione, dashboard, verifica).
2. Copia `local.properties.example` in `local.properties` e inserisci la chiave:
   ```
   TOMTOM_API_KEY=la-tua-chiave
   sdk.dir=<percorso del tuo Android SDK>
   ```
3. Apri la cartella `VolcanoEscape` in Android Studio (Hedgehog o successivo) e lascia che
   sincronizzi Gradle.
4. Esegui su un dispositivo/emulatore con Android 8.0 (API 26) o superiore.

In alternativa, da terminale: `start.bat` compila, avvia (o riusa) un emulatore, installa e
lancia l'app in un solo comando — stesso script già usato negli altri progetti Android di
questa cartella (FrigoZero, PlayerBase, TurboBrothers). Accetta il nome di un AVD come
argomento opzionale e `--dry-run` per vedere i comandi senza eseguirli.

Nota: il progetto è stato compilato e verificato sia con la JDK inclusa in Android Studio sia
con la JDK di sistema (`./gradlew assembleDebug` funziona da terminale senza configurazioni
aggiuntive). La chiave TomTom viene iniettata come risorsa stringa (`resValue`) invece che via
`BuildConfig`, proprio per evitare di dover compilare codice Java ed essere quindi indipendenti
dalla versione di JDK disponibile sulla macchina.

## Logo e icona

Il logo fornito (bussola con vulcano in eruzione, pin di fuga e strade) è già stato integrato
come icona dell'app: la sorgente ad alta risoluzione è in
`app/src/main/ic_launcher_source/logo.png`, da cui sono state generate le icone
`ic_launcher.png`/`ic_launcher_round.png` per tutte le densità (`mipmap-mdpi` → `mipmap-xxxhdpi`).
Per rigenerarle dopo una modifica al logo, usa Android Studio (tasto destro su `res` → New →
Image Asset) oppure ripeti lo script PowerShell di ridimensionamento.

## Struttura del progetto

```
app/src/main/java/com/volcanoescape/app/
├── data/
│   ├── model/        Volcano, SeismicEvent, EscapeRoute
│   ├── remote/        IngvApi, TomTomRoutingApi, NetworkModule
│   ├── repository/    SeismicRepository, RoutingRepository, GeoMath
│   └── location/       LocationProvider (FusedLocationProviderClient)
└── ui/
    ├── theme/          Colori/tipografia derivati dal logo
    ├── navigation/     NavHost e destinazioni
    └── screens/        volcanolist, monitoring, route
```

## Possibili sviluppi futuri

- Notifiche push quando l'INGV registra un evento sopra una soglia di magnitudine.
- Più punti di raccolta/rifugio configurabili per vulcano invece del punto sicuro calcolato
  geometricamente.
- Modalità offline con l'ultimo percorso calcolato salvato in cache.
