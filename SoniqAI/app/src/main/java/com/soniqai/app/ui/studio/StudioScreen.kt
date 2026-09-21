// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.ui.studio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.soniqai.app.audio.SectionType
import com.soniqai.app.audio.buildLyricTimeline
import com.soniqai.app.audio.buildMusicArrangement
import com.soniqai.app.audio.VoiceRecorder
import com.soniqai.app.audio.theory.Genre
import com.soniqai.app.data.Song
import com.soniqai.app.data.SongRepository
import com.soniqai.app.data.rememberSongRepository
import com.soniqai.app.ui.theme.SoniqGradient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(songId: String, onBack: () -> Unit) {
    val repository = rememberSongRepository()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val song by repository.observeById(songId).collectAsState(initial = null)
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // --- Riproduzione (base, voce o mix) --------------------------------------------------
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingTag by remember { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableStateOf(0) }

    fun stopPlayback() {
        mediaPlayer?.runCatching { stop(); release() }
        mediaPlayer = null
        playingTag = null
    }

    fun playFile(path: String, tag: String) {
        stopPlayback()
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(path)
            player.setOnCompletionListener { stopPlayback() }
            player.prepare()
            player.start()
            mediaPlayer = player
            playingTag = tag
        }.onFailure { errorMessage = "Impossibile riprodurre il file: ${it.message}" }
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer?.runCatching { stop(); release() } } }

    LaunchedEffect(playingTag) {
        while (playingTag != null) {
            positionMs = mediaPlayer?.currentPosition ?: 0
            delay(150)
        }
    }

    // --- Registrazione voce ------------------------------------------------------------------
    val voiceRecorder = remember { VoiceRecorder() }
    val amplitude by voiceRecorder.amplitude.collectAsState()
    var isRecording by remember { mutableStateOf(false) }

    fun stopRecording(currentSong: Song) {
        if (!isRecording) return
        isRecording = false
        stopPlayback()
        scope.launch {
            busyLabel = "Salvataggio voce..."
            val file = repository.vocalOutputFile(currentSong)
            voiceRecorder.stop(file)
            repository.attachVocal(currentSong, file)
            busyLabel = null
        }
    }

    fun startRecording(currentSong: Song) {
        val beatPath = currentSong.beatPath ?: return
        stopPlayback()
        playFile(beatPath, "beat_recording")
        voiceRecorder.start()
        isRecording = true
    }

    LaunchedEffect(isRecording, song?.durataSec) {
        if (isRecording) {
            delay((song?.durataSec ?: 60) * 1000L)
            song?.let { stopRecording(it) }
        }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) song?.let { startRecording(it) } else errorMessage = "Permesso microfono negato"
    }

    fun requestRecording(currentSong: Song) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startRecording(currentSong) else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // --- Copertina -----------------------------------------------------------------------------
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val currentSong = song
        if (uri != null && currentSong != null) {
            scope.launch {
                busyLabel = "Salvataggio copertina..."
                repository.setCover(currentSong, uri)
                busyLabel = null
            }
        }
    }

    fun shareVideo(currentSong: Song) {
        val path = currentSong.videoPath ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, currentSong.titolo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Condividi video"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(song?.titolo ?: "Brano") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Indietro") } },
            )
        },
    ) { padding ->
        val currentSong = song
        if (currentSong == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SongHeader(currentSong)

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            busyLabel?.let {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(it, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            BaseMusicaleCard(
                song = currentSong,
                isPlaying = playingTag == "beat",
                onGenerate = {
                    scope.launch {
                        busyLabel = "Generazione base musicale..."
                        runCatching { repository.generateBeat(currentSong) }
                            .onFailure { errorMessage = it.message }
                        busyLabel = null
                    }
                },
                onPlayToggle = {
                    if (playingTag == "beat") stopPlayback() else currentSong.beatPath?.let { playFile(it, "beat") }
                },
            )

            currentSong.beatPath?.let { beatPath ->
                LyricsCard(
                    song = currentSong,
                    positionMs = if (playingTag == "beat" || playingTag == "beat_recording" || playingTag == "mix") positionMs else 0,
                )
            }

            RecordVoiceCard(
                song = currentSong,
                isRecording = isRecording,
                amplitude = amplitude,
                onStart = { requestRecording(currentSong) },
                onStop = { stopRecording(currentSong) },
                onPreview = {
                    if (playingTag == "vocal") stopPlayback() else currentSong.vocalPath?.let { playFile(it, "vocal") }
                },
                isPreviewing = playingTag == "vocal",
            )

            MixCard(
                song = currentSong,
                isPlaying = playingTag == "mix",
                onMix = {
                    scope.launch {
                        busyLabel = "Mixaggio in corso..."
                        runCatching { repository.mixSong(currentSong) }
                            .onFailure { errorMessage = it.message }
                        busyLabel = null
                    }
                },
                onPlayToggle = {
                    if (playingTag == "mix") stopPlayback() else currentSong.mixPath?.let { playFile(it, "mix") }
                },
            )

            CoverAndVideoCard(
                song = currentSong,
                onPickCover = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onExportVideo = {
                    scope.launch {
                        busyLabel = "Creazione video in corso..."
                        runCatching { repository.exportVideo(currentSong) }
                            .onFailure { errorMessage = it.message }
                        busyLabel = null
                    }
                },
                onShare = { shareVideo(currentSong) },
            )
        }
    }
}

@Composable
private fun SongHeader(song: Song) {
    val genere = runCatching { Genre.valueOf(song.genere) }.getOrNull()
    val tono = runCatching { SongRepository.parseTonalita(song.tono) }.getOrNull()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoniqGradient)
            .padding(16.dp),
    ) {
        Column {
            Text(song.titolo, style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
            Text(
                "${genere?.label ?: song.genere} · ${tono?.label ?: song.tono} · ${song.durataSec}s",
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun BaseMusicaleCard(song: Song, isPlaying: Boolean, onGenerate: () -> Unit, onPlayToggle: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Base musicale", style = MaterialTheme.typography.titleMedium)
            Text(
                "Generata sul dispositivo, senza servizi esterni: batteria, basso e accordi sintetizzati in base a genere e tono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (song.beatPath == null) {
                Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) { Text("Genera base musicale") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlayToggle) {
                        Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Text(if (isPlaying) " Ferma" else " Riproduci", modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(onClick = onGenerate) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(" Rigenera", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsCard(song: Song, positionMs: Int) {
    val lyricLines = remember(song.id, song.durataSec, song.testoStrofe, song.ritornello) {
        val musicSections = buildMusicArrangement(song.durataSec.toDouble(), hasChorus = song.ritornello.isNotBlank())
        buildLyricTimeline(musicSections, song.testoStrofe, song.ritornello)
    }
    val currentSec = positionMs / 1000.0
    val currentIndex = lyricLines.indexOfFirst { currentSec >= it.startSec && currentSec < it.endSec }.let { if (it >= 0) it else 0 }
    val currentLine = lyricLines.getOrNull(currentIndex)
    val nextLine = lyricLines.getOrNull(currentIndex + 1)

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Testo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sincronizzato in automatico con la durata del brano: nessuna riga persa, nessuna sincronizzazione manuale.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (currentLine?.type == SectionType.RITORNELLO) "Ritornello" else "Strofa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                currentLine?.text.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
            )
            nextLine?.let {
                Text(
                    it.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RecordVoiceCard(
    song: Song,
    isRecording: Boolean,
    amplitude: Float,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPreview: () -> Unit,
    isPreviewing: Boolean,
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Registra voce", style = MaterialTheme.typography.titleMedium)
            Text(
                "Canta sopra la base: parte in automatico mentre registri, così puoi seguire il ritmo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (song.beatPath == null) {
                Text("Genera prima la base musicale.", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            if (isRecording) {
                LinearProgressIndicator(
                    progress = { amplitude.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Text(" Ferma registrazione", modifier = Modifier.padding(start = 4.dp))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStart) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                        Text(" Registra", modifier = Modifier.padding(start = 4.dp))
                    }
                    if (song.vocalPath != null) {
                        OutlinedButton(onClick = onPreview) {
                            Icon(if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                            Text(if (isPreviewing) " Ferma" else " Ascolta voce", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MixCard(song: Song, isPlaying: Boolean, onMix: () -> Unit, onPlayToggle: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mix finale", style = MaterialTheme.typography.titleMedium)
            Text(
                "Unisce base strumentale e voce registrata in un unico file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (song.beatPath == null || song.vocalPath == null) {
                Text("Servono sia la base musicale sia la voce registrata.", style = MaterialTheme.typography.bodySmall)
            } else if (song.mixPath == null) {
                Button(onClick = onMix, modifier = Modifier.fillMaxWidth()) { Text("Crea mix finale") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPlayToggle) {
                        Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                        Text(if (isPlaying) " Ferma" else " Riproduci mix", modifier = Modifier.padding(start = 4.dp))
                    }
                    OutlinedButton(onClick = onMix) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(" Rimixa", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverAndVideoCard(song: Song, onPickCover: () -> Unit, onExportVideo: () -> Unit, onShare: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Copertina e video", style = MaterialTheme.typography.titleMedium)
            Text(
                "Scegli una foto: verrà usata come copertina statica del video da condividere (ad es. su YouTube, tramite il menu di condivisione).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            song.coverPath?.let { path ->
                AsyncImage(
                    model = path,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).padding(bottom = 12.dp),
                )
            }

            OutlinedButton(onClick = onPickCover, modifier = Modifier.fillMaxWidth()) {
                Text(if (song.coverPath == null) "Scegli foto di copertina" else "Cambia foto")
            }

            val audioReady = song.mixPath != null || song.beatPath != null
            if (audioReady && song.coverPath != null) {
                Button(onClick = onExportVideo, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null)
                    Text(" Esporta video", modifier = Modifier.padding(start = 4.dp))
                }
            }

            song.videoPath?.let {
                Button(onClick = onShare, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text(" Condividi video", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
