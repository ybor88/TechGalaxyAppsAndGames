# PayRecap

**Il tuo cedolino, in un colpo d'occhio.** App Android (APK).

© Roberto Di Flumeri

PayRecap trasforma la busta paga in un riepilogo semplice. Carichi il cedolino in **PDF** (anche protetto da password)
o in **foto** e l'app estrae lordo, netto, tasse, contratto, ferie, ROL e straordinari. Tutto avviene **sul telefono**:
l'app non chiede il permesso di accedere a Internet e il backup automatico di Android è disattivato.

## Avvio rapido (Windows)

Requisiti: Android Studio (SDK e JDK inclusi). Facoltativo: un emulatore (AVD) o un telefono collegato via USB.

```
start.bat                 unit test + build + emulatore + installa + avvia
start.bat --demo          come sopra, con due cedolini di esempio già caricati
start.bat --solo-apk      unit test + build, genera PayRecap.apk senza emulatore
start.bat --salta-test    salta gli unit test
start.bat NomeAVD         usa un emulatore specifico
```

L'APK pronto viene copiato in `PayRecap\PayRecap.apk`. Per il telefono: copialo sul dispositivo, aprilo e
consenti "installa app sconosciute".

## Funzioni

| Funzione | Dettaglio |
|---|---|
| Caricamento | PDF digitale (testo letto direttamente), PDF protetto da password, PDF scansionato o foto (OCR offline). Anche "Apri con" / "Condividi" da altre app |
| Revisione | Dopo la lettura mostra i campi riconosciuti (es. 11 su 11) e quelli mancanti; ogni valore si può correggere prima di salvare. Si può vedere il testo letto |
| Riepilogo economico | Lordo e netto effettivi, IRPEF, addizionali regionale e comunale, contributi, totale e incidenza sul lordo |
| Stima IRPEF | Scaglioni 2026 e detrazione lavoro dipendente su base annua. È sempre etichettata **STIMA**: dipende da contratto, imponibile progressivo, detrazioni e conguagli; fa fede il cedolino |
| Contratto | Tipo (indeterminato, determinato, apprendistato, part-time...), CCNL, livello, scadenza con giorni mancanti |
| Ferie e ROL | Maturati, goduti e residui, in giorni o ore |
| Straordinario | Non è un residuo: ore effettuate, pagate, accantonate e banca ore residua, quando presenti |
| Confronto | Ogni voce rispetto al mese precedente (le tredicesime si confrontano tra loro) |
| Avvisi | Variazioni di netto, lordo e tasse oltre soglia; netto maggiore del lordo; voci non riepilogate; ferie in negativo o accumulate; conti ferie/ROL che non tornano; straordinario non riconosciuto; contratto in scadenza o scaduto; cambi di livello, CCNL o contratto; IRPEF lontana dalla stima |
| Storico | Grafico lordo/netto degli ultimi 12 mesi, totali dell'anno, elenco cedolini con variazione del netto e numero di avvisi |
| Impostazioni | Soglia degli avvisi (3–30%), preavviso scadenza (15–180 giorni), cedolini di esempio, eliminazione dei dati |

## Come legge il cedolino

1. **PDF**: il testo viene estratto con PdfBox (anche con password). Se il PDF è una scansione senza testo, le pagine
   vengono disegnate e lette con l'OCR.
2. **Foto**: OCR con ML Kit (modello incluso nell'APK, funziona offline). I pezzi di testo vengono ricomposti in righe
   per tenere insieme etichetta e importo.
3. **Riconoscimento** (`dominio/ParserCedolino.kt`): cerca le etichette più diffuse (Zucchetti, TeamSystem, INAZ, paghe
   interne...) sia nella forma "etichetta valore" sia nelle tabelle con intestazione sopra e valori sotto. Gestisce
   importi all'italiana e all'inglese e ignora date, percentuali, rate ("10/11") e progressivi annui. Quello che non trova
   resta vuoto: non inventa valori.

## Test

- **64 unit test** (`app/src/test`): parser (numeri, periodo, tredicesima, etichette, tabelle ferie/ROL, straordinario,
  contratto, azienda), analisi (confronti, ogni tipo di avviso, soglie, stima IRPEF, scaglioni e detrazioni),
  ricomposizione delle righe OCR.
- **Prova sul dispositivo**: in `test_cedolini/` ci sono tre cedolini fittizi per provare la lettura reale:
  PDF digitale, PDF protetto (password `RSSMRA80A01H501U`) e foto JPG per l'OCR. `genera_cedolini.py` li rigenera
  (richiede `reportlab` e `pymupdf`). Per provarli: `adb push test_cedolini\<file> /sdcard/Download/`, poi
  "Carica il cedolino" nell'app.

## Struttura

```
app/src/main/java/com/payrecap/app/
├── MainActivity.kt          apertura app e file ricevuti da altre app
├── dominio/                 logica pura, testata con JUnit
│   ├── Cedolino.kt          modello (entità Room)
│   ├── ParserCedolino.kt    riconoscimento delle voci dal testo
│   ├── Analisi.kt           confronti, avvisi, stima IRPEF
│   ├── Righe.kt             ricomposizione delle righe OCR
│   └── Esempi.kt            cedolini di esempio (dati fittizi)
├── dati/                    Room, preferenze, estrazione testo (PdfBox + ML Kit)
└── ui/                      Jetpack Compose: Riepilogo, Storico, Impostazioni, Revisione
```

Tecnologie: Kotlin, Jetpack Compose, Material 3, Room, PdfBox-Android, ML Kit Text Recognition. minSdk 26.
