# Optools iOS — Manuale di compilazione e test su Mac

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
Dentro trovi la sottocartella `Optools/` con tutto il codice Swift e le immagini.

## 3. Generare il progetto Xcode (un solo comando, con XcodeGen)

Non serve più creare il progetto a mano e trascinare le cartelle: è già pronto un file
`project.yml` che descrive il progetto. Basta generare il `.xcodeproj` da terminale, sul Mac:

1. Installa XcodeGen una tantum (richiede [Homebrew](https://brew.sh)):
   ```
   brew install xcodegen
   ```
2. Apri il Terminale nella cartella `MyCallBagEyeNeurologyIOS/Optools` ricevuta ed esegui:
   ```
   xcodegen generate
   open Optools.xcodeproj
   ```
3. Xcode si apre con il progetto già completo: tutte le cartelle (`Calculators`, `Common`,
   `Emergency`, `Navigation`, `Neurology`, `Ophthalmology`, `Optometry`, `Theme`), il file
   `OptoolsApp.swift`, l'icona e tutte le immagini in `Assets.xcassets` sono già collegati
   al target. Le voci "Permessi fotocamera" (punto 4 sotto) sono già incluse in automatico.

Se preferisci non installare nulla da terminale, resta comunque possibile il metodo manuale
"Create New Project" + drag&drop descritto nella cronologia di questo file, ma non è più necessario.

Ogni volta che il codice Swift viene aggiornato (nuovi file o cartelle), rilancia semplicemente
`xcodegen generate` per rigenerare il progetto: non perdi le impostazioni di firma già scelte.

## 4. Permessi fotocamera e libreria foto (obbligatorio)

Senza questo passaggio l'app va in crash quando si apre uno strumento con fotocamera.

1. Seleziona il progetto (icona blu in cima al Project Navigator) → target **Optools** →
   scheda **Info**.
2. Passa il mouse su una riga qualsiasi → clicca **+** per aggiungere due nuove righe:
   - Chiave: `Privacy - Camera Usage Description` → Valore: `Optools usa la fotocamera per gli strumenti clinici`
   - Chiave: `Privacy - Photo Library Additions Usage Description` → Valore: `Optools salva le foto scattate durante gli esami`

(In alternativa, il file `Resources/Info.plist` ricevuto contiene già queste chiavi come riferimento.)

## 5. Versione minima di iOS

1. Seleziona il progetto → target **Optools** → scheda **General**.
2. In **Minimum Deployments**, imposta **iOS 16.0** o superiore (il codice usa `NavigationStack`,
   disponibile da iOS 16).

## 6. Firma dell'app (Signing)

1. Scheda **Signing & Capabilities**.
2. In **Team**, scegli il tuo Apple ID. Se non compare, vai su **Xcode → Settings → Accounts → +**
   e aggiungilo (è gratuito, basta l'Apple ID normale).
3. Se il **Bundle Identifier** risulta "già in uso", cambialo leggermente (es. aggiungi il tuo nome).

## 7. Provare nel Simulatore (il modo più veloce, senza iPhone)

1. In alto al centro, scegli come dispositivo un simulatore, es. **iPhone 15**.
2. Premi ▶️ (Play) o `Cmd+R`.
3. L'app si apre nel Simulatore. Puoi navigare tutte le schermate; gli strumenti con fotocamera
   mostreranno un riquadro nero al posto dell'anteprima live (il Simulatore non ha una fotocamera
   reale), ma calcolatori, test grafici, disegno, ecc. funzionano perfettamente.

## 8. Provare su iPhone reale (necessario per testare la fotocamera)

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

**Con un Apple ID gratuito NON si esporta un file `.ipa` a parte**: l'installazione avviene
direttamente premendo ▶️ in Xcode col telefono collegato (punto 8 sopra), non trascinando un IPA.
Un `.ipa` esportabile e installabile "da solo" (senza Xcode) richiede sempre un account Apple
Developer Program a pagamento — è una limitazione della piattaforma iOS, non di questo progetto.

## 9. Perché non si può ottenere un .ipa "pronto" senza Mac (Codemagic/CI)

È presente anche un file `codemagic.yaml` (nella cartella `MyCallBagEyeNeurologyIOS`, non dentro
`Optools`) che, se collegato a un account gratuito su [codemagic.io](https://codemagic.io),
compila automaticamente il progetto ad ogni push **per il Simulatore**, senza firma: serve solo a
verificare che il codice compili, in automatico, senza bisogno di un Mac. Non produce un `.ipa`
installabile su iPhone: quel passaggio, con un Apple ID gratuito, richiede sempre di passare da
Xcode su un Mac (punto 8). Con un account Developer Program a pagamento, invece, si potrebbe
estendere `codemagic.yaml` per firmare e generare un `.ipa` distribuibile via TestFlight o Ad Hoc.

## Struttura del progetto

```
Optools/
  OptoolsApp.swift        punto di ingresso (equivalente a MainActivity)
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
