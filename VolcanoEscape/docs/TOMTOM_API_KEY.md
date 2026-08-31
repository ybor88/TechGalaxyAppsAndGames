# Come procurarsi la chiave API TomTom

La funzionalità "Trova la via di fuga meno trafficata" chiama la TomTom Routing API. Senza una
chiave valida in `local.properties`, l'app mostra l'errore
"TOMTOM_API_KEY mancante: impostala in local.properties" e non calcola alcun percorso.

## Costo

Gratuita, **nessuna carta di credito richiesta** per registrarsi. Il piano free copre già tutte
le API TomTom (routing incluso) con un'unica chiave, senza doverle abilitare una per una come su
Google Cloud. Le soglie esatte (richieste/giorno o al mese) sono indicate nella dashboard dopo la
registrazione — TomTom sta rivedendo i piani a partire da luglio 2026, quindi conviene controllarle
lì piuttosto che fidarsi di un numero fisso.

## Passaggi

1. Vai su [developer.tomtom.com](https://developer.tomtom.com/) (oppure direttamente
   [my.tomtom.com](https://my.tomtom.com/)) e registrati con la tua email.
2. Conferma l'indirizzo email se richiesto.
3. Nella dashboard apri **"API & SDK Keys"**. Trovi già una chiave pronta chiamata
   *"My first API key"*, oppure creane una nuova col pulsante **"Add new key"**.
4. Copia la stringa della chiave.

## Collegarla al progetto

Apri `local.properties` nella root del progetto (copialo da `local.properties.example` se non
esiste ancora) e imposta:

```
TOMTOM_API_KEY=la-tua-chiave-qui
sdk.dir=<percorso del tuo Android SDK>
```

Il file è nel `.gitignore`: la chiave resta locale e non finisce mai nel repository. Non serve
nessun altro passaggio: `app/build.gradle.kts` legge `local.properties` a ogni build e la
inietta come risorsa stringa (`resValue`), letta poi da `RoutingRepository` in fase di chiamata.

## Verificare che la chiave funzioni

Puoi testarla da terminale prima ancora di aprire l'app, con una richiesta di prova:

```
curl "https://api.tomtom.com/routing/1/calculateRoute/40.8210,14.4260:40.8500,14.5000/json?key=LA_TUA_CHIAVE"
```

Una risposta HTTP 200 con un oggetto `routes` conferma che la chiave è attiva e abilitata per il
routing.
