# OpticProSuite iOS — Manuale di compilazione e test su Mac

Questa cartella contiene il porting Swift/SwiftUI completo dell'app Android (tutti i 37 strumenti,
incluse le schermate con fotocamera). Il progetto Android in `MyCallBagEyeNeurology/` NON è stato
toccato: questo è un progetto separato, pensato per essere aperto e compilato con **Xcode su Mac**.

## 1. Cosa serve

- Un Mac con **Xcode** installato (gratis dal Mac App Store, versione 15 o superiore).
- Un **Apple ID** qualsiasi (anche gratuito, es. lo stesso di iCloud) — serve solo per firmare l'app.
- Per testare la fotocamera: un **iPhone** con cavo USB (o stessa rete Wi-Fi). Nel Simulatore la
  fotocamera non è disponibile (mostra solo schermo nero), quindi per gli strumenti con fotocamera
  (lensometro, funduscopia, topografia, misurazioni PD, ecc.) serve un iPhone vero.

## 2. Copiare i file sul Mac

Copia l'intera cartella `MyCallBagEyeNeurologyIOS` sul Mac (via AirDrop, chiavetta, cloud, ecc.).
Dentro trovi la sottocartella `OpticProSuite/` con tutto il codice Swift e le immagini.

## 3. Creare il progetto Xcode

1. Apri **Xcode** → **Create New Project**.
2. Scegli **iOS** → **App** → Next.
3. Compila:
   - **Product Name**: `OpticProSuite`
   - **Interface**: `SwiftUI`
   - **Language**: `Swift`
   - **Organization Identifier**: qualcosa a tua scelta, es. `com.tuonome` (il Bundle ID finale sarà
     `com.tuonome.OpticProSuite`)
4. Salva il progetto in una cartella qualsiasi (diversa da quella ricevuta).

## 4. Importare il codice sorgente

1. Nel **Project Navigator** di Xcode (pannello a sinistra), elimina il file `ContentView.swift`
   generato di default (tasto destro → Delete → "Move to Trash").
2. Apri il Finder sulla cartella `MyCallBagEyeNeurologyIOS/OpticProSuite` ricevuta.
3. Trascina **tutte** le sottocartelle (`Calculators`, `Common`, `Emergency`, `Navigation`,
   `Neurology`, `Ophthalmology`, `Optometry`, `Theme`) e il file `OpticProSuiteApp.swift` dentro il
   Project Navigator di Xcode, rilasciandoli sul gruppo con il nome del progetto.
4. Nella finestra che appare:
   - Spunta **"Copy items if needed"**
   - Scegli **"Create groups"**
   - Verifica che sotto "Add to targets" sia spuntato il target `OpticProSuite`
5. Se Xcode ha generato un suo file `OpticProSuiteApp.swift` di default, eliminalo (tasto destro →
   Delete) prima di importare il nostro, altrimenti avrai due `@main` e la compilazione fallirà.

## 5. Importare le immagini (Assets)

1. Nel progetto Xcode, apri `Assets.xcassets`.
2. Dal Finder, apri `MyCallBagEyeNeurologyIOS/OpticProSuite/Resources/Assets.xcassets`.
3. Trascina dentro il pannello Assets di Xcode tutti gli imageset che vedi nel Finder
   (`app_logo`, `home_banner`, `header_optometria`, `header_oftalmologia`, `header_neurologia`,
   `header_urgenza`, `header_calcolatori`).

## 6. Permessi fotocamera e libreria foto (obbligatorio)

Senza questo passaggio l'app va in crash quando si apre uno strumento con fotocamera.

1. Seleziona il progetto (icona blu in cima al Project Navigator) → target **OpticProSuite** →
   scheda **Info**.
2. Passa il mouse su una riga qualsiasi → clicca **+** per aggiungere due nuove righe:
   - Chiave: `Privacy - Camera Usage Description` → Valore: `OpticProSuite usa la fotocamera per gli strumenti clinici`
   - Chiave: `Privacy - Photo Library Additions Usage Description` → Valore: `OpticProSuite salva le foto scattate durante gli esami`

(In alternativa, il file `Resources/Info.plist` ricevuto contiene già queste chiavi come riferimento.)

## 7. Versione minima di iOS

1. Seleziona il progetto → target **OpticProSuite** → scheda **General**.
2. In **Minimum Deployments**, imposta **iOS 16.0** o superiore (il codice usa `NavigationStack`,
   disponibile da iOS 16).

## 8. Firma dell'app (Signing)

1. Scheda **Signing & Capabilities**.
2. In **Team**, scegli il tuo Apple ID. Se non compare, vai su **Xcode → Settings → Accounts → +**
   e aggiungilo (è gratuito, basta l'Apple ID normale).
3. Se il **Bundle Identifier** risulta "già in uso", cambialo leggermente (es. aggiungi il tuo nome).

## 9. Provare nel Simulatore (il modo più veloce, senza iPhone)

1. In alto al centro, scegli come dispositivo un simulatore, es. **iPhone 15**.
2. Premi ▶️ (Play) o `Cmd+R`.
3. L'app si apre nel Simulatore. Puoi navigare tutte le schermate; gli strumenti con fotocamera
   mostreranno un riquadro nero al posto dell'anteprima live (il Simulatore non ha una fotocamera
   reale), ma calcolatori, test grafici, disegno, ecc. funzionano perfettamente.

## 10. Provare su iPhone reale (necessario per testare la fotocamera)

1. Collega l'iPhone al Mac con un cavo USB (o attiva il debug via rete Wi-Fi in Xcode).
2. Sull'iPhone, la prima volta comparirà "Vuoi fidarti di questo computer?" → tocca **Autorizza**.
3. In Xcode, scegli il tuo iPhone dal menu dei dispositivi (in alto, al posto del Simulatore).
4. Premi ▶️ (Play). Xcode installa e avvia l'app sul telefono.
5. **Se l'iPhone rifiuta l'app** ("Sviluppatore non attendibile"): sull'iPhone vai su
   **Impostazioni → Generali → VPN e gestione dispositivo**, tocca il tuo Apple ID/profilo sotto
   "App per sviluppatori" e conferma **Attendi**.
6. Riapri l'app dalla schermata Home dell'iPhone.

**Nota importante**: con un Apple ID gratuito (senza abbonamento Developer Program), l'app installata
sull'iPhone scade dopo **7 giorni** e va reinstallata ricollegando il Mac e rilanciando da Xcode
(bastano pochi secondi). Per un test occasionale con un amico va benissimo così.

## 11. Alternative per non dover ricollegare il Mac ogni 7 giorni

Su iOS non esiste un file installabile "con un tap" come l'APK di Android — serve sempre una firma
legata a un account Apple. Se serve un test più comodo e prolungato:

- **TestFlight** (richiede l'iscrizione a pagamento all'Apple Developer Program, 99$/anno): in Xcode,
  **Product → Archive → Distribute App → TestFlight**. Il tuo amico riceve un invito via email/link,
  installa l'app **TestFlight** dall'App Store e da lì installa la tua app, senza bisogno del cavo.
  Le build durano 90 giorni.
- **Export Ad Hoc + Diawi/simili** (richiede comunque l'account Developer a pagamento): genera un
  file `.ipa` da **Product → Archive → Distribute App → Ad Hoc**, poi lo si installa trascinandolo
  nella finestra "Devices and Simulators" di Xcode, oppure caricandolo su un servizio come Diawi per
  ottenere un link di installazione diretta.

Per un semplice test tra amici, il metodo del punto 10 (cavo + rilancio da Xcode ogni 7 giorni) è
gratuito e sufficiente.

## Struttura del progetto

```
OpticProSuite/
  OpticProSuiteApp.swift        punto di ingresso (equivalente a MainActivity)
  Theme/                        colori e tema scuro monocromatico
  Navigation/                   catalogo strumenti + routing (equivalente ad AppNavGraph)
  Common/                       Home, lista strumenti, fotocamera, "in sviluppo", misura calibrata
  Optometry/                    9 strumenti (vision chart, PD, Amsler, ecc.)
  Ophthalmology/                10 strumenti (lensometro, Placido, funduscopia, ecc.)
  Neurology/                    6 strumenti (clock drawing, NIHSS, VOMS, ecc.)
  Emergency/                    4 strumenti (calibro pupillare, vein finder, goniometro, ecc.)
  Calculators/                  8 calcolatori clinici (BMI, GCS, QTc, CHA2DS2-VASc, ecc.)
  Resources/                    immagini (Assets.xcassets) e Info.plist di riferimento
```

Tutte le formule cliniche e i testi sono identici alla versione Android; cambia solo il framework
grafico (SwiftUI invece di Jetpack Compose) e il motore fotocamera (AVFoundation invece di CameraX).
