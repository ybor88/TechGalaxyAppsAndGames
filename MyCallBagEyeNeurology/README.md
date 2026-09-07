# Optools (Android)

App nativa Android (Kotlin + Jetpack Compose) ispirata a "My Call Bag: Eye & Neurology", pensata per un uso professionale in negozio di ottica. Ideata da Antonio Di Somma.

## Stato del progetto

Scaffold completo con navigazione a 5 reparti (Optometria, Oftalmologia, Neurologia, Medicina d'urgenza, Calcolatori clinici).
Strumenti **già funzionanti**:

- Tabella acuità visiva (Snellen/Tumbling E, scalata in base alla distanza di test)
- Misurazione PD (metodo "carta di calibrazione" con fotocamera frontale)
- Amsler Grid, Duochrome Test, Contrast Sensitivity Chart, Worth 4 Dot, Anisometropia
- Lensometro (metodo di neutralizzazione manuale, **beta**, non una vera misura automatica da immagine)
- Astigmatism Dial, Test colori pseudoisocromatico (tavole generate, non le tavole Ishihara originali coperte da copyright)
- Clock Drawing Test, NIH Stroke Scale
- Calibro pupillare
- Calcolatori: BMI, CHA₂DS₂-VASc, Glasgow Coma Scale, QTc (Bazett), Cockcroft-Gault

Tutti gli altri strumenti elencati nel catalogo (`ToolsCatalog.kt`) sono presenti in navigazione ma mostrano una schermata "in sviluppo" (`ComingSoonScreen`) finché non vengono implementati.

## Come aprire il progetto

1. Installa **Android Studio** (Koala o successivo).
2. Apri questa cartella come progetto esistente (File > Open).
3. Al primo avvio, Android Studio proporrà di creare il Gradle Wrapper mancante: accetta (serve una connessione internet per scaricare Gradle 8.x).
4. Lascia sincronizzare il progetto (Gradle Sync).
5. Esegui su un dispositivo/emulatore con Android 8.0 (API 26) o superiore.

## Note tecniche importanti

- **Fotocamera/AI**: PD measurement e Lensometro usano CameraX. Il Lensometro *non* è una misura ottica automatica reale: per una lensometria accurata servirebbe un banco ottico o un collimatore dedicato; qui è implementato il metodo classico di "neutralizzazione" con inserimento manuale della distanza al punto neutro.
- **Test colori**: le tavole Ishihara originali sono protette da copyright e non sono incluse. Ho generato tavole pseudoisocromatiche procedurali equivalenti nello scopo ma non validate clinicamente come le originali.
- **Precisione strumenti "a schermo" (Amsler, calibro pupillare, acuità visiva)**: dipende dalla densità di pixel reale del dispositivo; la conversione usata è approssimata (160dpi baseline Android). Per un uso clinico affidabile è consigliata una calibrazione per singolo modello di dispositivo.

## ⚠️ Avvertenza regolatoria

Un'app che fornisce misure cliniche (acuità visiva, PD, potere lenti, screening neurologico) può rientrare nella definizione di **dispositivo medico software** secondo il Regolamento UE 2017/745 (MDR) o normative equivalenti (FDA negli USA), a seconda dell'uso previsto dichiarato. Prima di un uso commerciale o clinico:

- valuta con un consulente regolatorio se l'app richiede marcatura CE come dispositivo medico o rientra in un'eccezione;
- mantieni sempre un disclaimer chiaro ("strumento educativo/di supporto, non sostituisce apparecchiature certificate né una diagnosi professionale");
- non raccogliere dati sanitari dei pazienti senza una gestione conforme al GDPR (minimizzazione dati, conservazione locale se possibile, informativa privacy).

## Prossimi passi suggeriti

1. Implementare gli strumenti rimanenti in `ToolsCatalog.kt` uno alla volta (partendo da quelli senza fotocamera).
2. Aggiungere test di calibrazione per-dispositivo (fisico, es. inquadrando un righello) invece della stima a 160dpi.
3. Aggiungere persistenza locale (Room) per salvare le misurazioni per paziente/cliente.
4. Valutare un vero modulo di lensometria automatica solo se si dispone di hardware di supporto (adattatore ottico per lo smartphone).
