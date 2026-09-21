// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.data

import android.content.Context
import android.net.Uri
import com.soniqai.app.audio.AudioMixer
import com.soniqai.app.audio.BeatGenerator
import com.soniqai.app.audio.SAMPLE_RATE
import com.soniqai.app.audio.WavFile
import com.soniqai.app.audio.buildMusicArrangement
import com.soniqai.app.audio.theory.Genre
import com.soniqai.app.audio.theory.MusicKey
import com.soniqai.app.audio.theory.MusicMode
import com.soniqai.app.audio.theory.Tonalita
import com.soniqai.app.video.VideoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Coordina generazione del beat, registrazione voce, mixaggio ed esportazione video. Ogni brano
 * ha una propria cartella in [songDir]; nessun dato lascia il dispositivo (nessun servizio esterno).
 */
class SongRepository(
    private val context: Context,
    private val dao: SongDao,
) {
    fun observeAll(): Flow<List<Song>> = dao.observeAll()

    fun observeById(id: String): Flow<Song?> = dao.observeById(id)

    suspend fun findById(id: String): Song? = dao.findById(id)

    private fun songDir(songId: String): File =
        File(context.getExternalFilesDir(null), "songs/$songId").apply { mkdirs() }

    suspend fun createSong(
        titolo: String,
        genere: Genre,
        tono: Tonalita,
        durataSec: Int,
        testoStrofe: String,
        ritornello: String,
    ): Song = withContext(Dispatchers.IO) {
        val song = Song(
            id = UUID.randomUUID().toString(),
            titolo = titolo.ifBlank { "Brano senza titolo" },
            genere = genere.name,
            tono = "${tono.key.name}:${tono.mode.name}",
            durataSec = durataSec,
            testoStrofe = testoStrofe,
            ritornello = ritornello,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(song)
        song
    }

    /** Sintetizza la base strumentale sul dispositivo e la salva come WAV nella cartella del brano. */
    suspend fun generateBeat(song: Song): Song = withContext(Dispatchers.Default) {
        val genere = Genre.valueOf(song.genere)
        val tono = parseTonalita(song.tono)
        val sections = buildMusicArrangement(song.durataSec.toDouble(), hasChorus = song.ritornello.isNotBlank())
        val pcm = BeatGenerator.generate(genere, tono, song.durataSec.toDouble(), sections)
        val file = File(songDir(song.id), "beat.wav")
        WavFile.write(file, pcm, SAMPLE_RATE, 1)
        val updated = song.copy(beatPath = file.absolutePath)
        dao.upsert(updated)
        updated
    }

    suspend fun attachVocal(song: Song, vocalFile: File): Song = withContext(Dispatchers.IO) {
        val updated = song.copy(vocalPath = vocalFile.absolutePath)
        dao.upsert(updated)
        updated
    }

    fun vocalOutputFile(song: Song): File = File(songDir(song.id), "vocal_${System.currentTimeMillis()}.wav")

    /** Somma strumentale e voce registrata in un unico WAV finale. */
    suspend fun mixSong(song: Song): Song = withContext(Dispatchers.Default) {
        val beatPath = requireNotNull(song.beatPath) { "Genera prima la base strumentale" }
        val vocalPath = requireNotNull(song.vocalPath) { "Registra prima la voce" }
        val beat = WavFile.read(File(beatPath))
        val vocal = WavFile.read(File(vocalPath))
        val mixed = AudioMixer.mix(beat, vocal)
        val file = File(songDir(song.id), "mix.wav")
        WavFile.write(file, mixed, SAMPLE_RATE, 1)
        val updated = song.copy(mixPath = file.absolutePath)
        dao.upsert(updated)
        updated
    }

    suspend fun setCover(song: Song, sourceUri: Uri): Song = withContext(Dispatchers.IO) {
        val file = File(songDir(song.id), "cover.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val updated = song.copy(coverPath = file.absolutePath)
        dao.upsert(updated)
        updated
    }

    /** Genera l'mp4 (copertina statica + audio) pronto per essere condiviso, ad es. sull'app YouTube. */
    suspend fun exportVideo(song: Song): Song = withContext(Dispatchers.Default) {
        val coverPath = requireNotNull(song.coverPath) { "Scegli prima una foto di copertina" }
        val audioPath = song.mixPath
            ?: song.beatPath
            ?: error("Genera prima la base strumentale (ed eventualmente registra la voce)")
        val outputFile = File(songDir(song.id), "video.mp4")
        VideoExporter.export(
            coverImagePath = coverPath,
            audioWavPath = audioPath,
            title = song.titolo,
            outputFile = outputFile,
        )
        val updated = song.copy(videoPath = outputFile.absolutePath)
        dao.upsert(updated)
        updated
    }

    suspend fun deleteSong(song: Song) = withContext(Dispatchers.IO) {
        songDir(song.id).deleteRecursively()
        dao.deleteById(song.id)
    }

    companion object {
        fun parseTonalita(raw: String): Tonalita {
            val parts = raw.split(":")
            val key = MusicKey.valueOf(parts.getOrElse(0) { MusicKey.DO.name })
            val mode = MusicMode.valueOf(parts.getOrElse(1) { MusicMode.MAGGIORE.name })
            return Tonalita(key, mode)
        }
    }
}
