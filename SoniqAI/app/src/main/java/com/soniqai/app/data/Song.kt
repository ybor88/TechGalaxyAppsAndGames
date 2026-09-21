// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val id: String,
    val titolo: String,
    val genere: String,
    val tono: String,
    val durataSec: Int,
    val testoStrofe: String,
    val ritornello: String,
    /** Percorso del WAV strumentale generato da [com.soniqai.app.audio.BeatGenerator]. */
    val beatPath: String? = null,
    /** Percorso del WAV con la voce registrata dal microfono. */
    val vocalPath: String? = null,
    /** Percorso del WAV finale (strumentale + voce mixati). */
    val mixPath: String? = null,
    /** Percorso della foto di copertina scelta per il video. */
    val coverPath: String? = null,
    /** Percorso del video mp4 esportato (copertina + audio), pronto per la condivisione. */
    val videoPath: String? = null,
    val createdAt: Long,
)
