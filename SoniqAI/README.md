# SoniqAI (Android)

App nativa Android (Kotlin + Jetpack Compose + Material 3) per creare un brano: scegli genere,
tono, durata e testo, l'app genera **sul dispositivo** una base musicale, ci registri sopra la
voce dal microfono, mixi il tutto e infine esporti un video (foto di copertina + audio) pronto
da condividere, ad esempio sull'app YouTube. Stile grafico derivato dal logo (`SoniqAI.jpeg`):
sfondo blu notte, gradiente ciano → magenta → arancio per header e card in evidenza.

## Scelte di scope (decise con l'utente)

- **Nessun servizio AI a pagamento**: niente Suno/Udio/API cloud. La base musicale è sintetizzata
  interamente offline con un motore procedurale (`audio/BeatGenerator.kt`): batteria, basso e
  accordi generati da forme d'onda elementari secondo genere/tono scelti, non un brano "cantato"
  dall'AI. Il canto è quello vero dell'utente, registrato con il microfono sopra la base.
- **Nessuna integrazione YouTube via OAuth/API** in questa fase: l'app esporta un file mp4 locale
  (copertina + audio) e lo condivide con il menu di condivisione standard di Android, da cui
  l'utente può scegliere l'app YouTube (o qualunque altra) per pubblicarlo. Un'integrazione con
  YouTube Data API v3 (login diretto, upload automatico) è rimandabile a una fase successiva.

## Funzionalità

- **Crea brano** (`ui/create/CreateSongScreen.kt`): titolo, genere (Pop/Rock/Hip Hop/EDM/
  Reggaeton/Lo-fi/Acustico), tono (12 note × maggiore/minore), durata in minuti:secondi (da 0:15 a
  6:00), testo delle strofe (scritto a mano o importato da un file `.txt`) e ritornello dedicato.
- **Sincronizzazione automatica del testo** (`audio/SongSection.kt`): una volta scelta la durata,
  l'app distribuisce da sola TUTTO il testo importato lungo il brano — nessuna riga persa, nessuna
  sincronizzazione manuale. `buildMusicArrangement` decide la scaletta ritmica (blocchi
  strofa/ritornello, chiusura sempre su un ritornello se presente); `buildLyricTimeline` riempie in
  ordine le sezioni di strofa con le righe del testo (distribuite in modo uniforme così da
  consumarle tutte entro l'ultima sezione, con tempo per riga proporzionale alla sua lunghezza) e
  ripete il ritornello scelto in ogni sezione "ritornello", con una base più energica (batteria più
  densa + lead melodico).
- **Studio del brano** (`ui/studio/StudioScreen.kt`):
  - genera/rigenera la base musicale e la riproduce;
  - mostra il testo in stile karaoke (riga corrente + anteprima della prossima), sincronizzato in
    automatico alla riproduzione;
  - registra la voce dal microfono mentre la base suona in sottofondo (`audio/VoiceRecorder.kt`),
    con indicatore di livello e stop automatico a fine durata;
  - crea il **mix finale** sommando base e voce (`audio/AudioMixer.kt`);
  - sceglie una foto di copertina ed esporta il **video mp4** (`video/VideoExporter.kt`, via
    MediaCodec/MediaMuxer — nessuna libreria nativa esterna tipo FFmpeg);
  - condivide il video con il menu di sistema (`Intent.ACTION_SEND`, tramite `FileProvider`).
- **Home** (`ui/home/HomeScreen.kt`): elenco dei brani creati con stato (base/voce/video pronti).

## Architettura

Stesso stack/stile delle altre app del workspace (es. ScoutTable): Kotlin + Compose + Material 3,
Room per la persistenza (`data/Song.kt`, `SongDao.kt`, `SoniqAiDatabase.kt`), nessun framework DI
(provider manuale in `data/AppContainer.kt`), tutta la logica audio/video isolata in `audio/` e
`video/` così da poterla testare/estendere senza toccare la UI.

- `audio/theory/`: teoria musicale minima necessaria alla generazione (note, scale, accordi,
  profili di genere con tempo/pattern ritmico/accordi).
- `audio/BeatGenerator.kt`: sintetizzatore procedurale (nessun campione audio, solo forme d'onda).
- `audio/WavFile.kt`: lettura/scrittura WAV PCM 16-bit.
- `audio/VoiceRecorder.kt`, `audio/AudioMixer.kt`: registrazione microfono e mixaggio.
- `video/VideoExporter.kt`: composizione mp4 (immagine statica + audio) via MediaCodec/MediaMuxer.
- `data/SongRepository.kt`: orchestratore di tutto il flusso, ogni brano ha una propria cartella
  in `getExternalFilesDir()/songs/<id>/` (beat.wav, vocal.wav, mix.wav, cover.jpg, video.mp4).

## Limiti noti / cose da testare su device reale

- Il motore musicale è procedurale, non un modello AI: produce basi semplici ma riconoscibili per
  genere, non una produzione professionale.
- `VideoExporter` usa le API MediaCodec a basso livello (necessarie per restare senza dipendenze
  esterne): la compilazione è stata verificata (`./gradlew :app:assembleDebug` completa con
  successo), ma le API media Android sono note per comportarsi in modo leggermente diverso tra
  produttori — vale la pena provare l'esportazione video su un paio di dispositivi reali diversi
  prima di considerarla definitiva.
- Nessun monitoraggio audio a bassa latenza in cuffia durante la registrazione: la base viene
  riprodotta dall'altoparlante mentre si registra, quindi un po' di "bleed" nel microfono è
  normale (consigliate le cuffie per un risultato più pulito).

## Build

Stesso toolchain di ScoutTable/VolcanoEscape: Gradle 9.3.1, AGP 8.5.2, Kotlin 1.9.24, compileSdk
34, minSdk 26.

```
./gradlew :app:assembleDebug
```

L'APK di debug viene generato in `app/build/outputs/apk/debug/`.

---
© Roberto Di Flumeri
