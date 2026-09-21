// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.audio

import kotlin.math.ceil
import kotlin.math.max

enum class SectionType { STROFA, RITORNELLO }

/** Blocco della scaletta musicale (usato dal generatore del beat per decidere il pattern ritmico). */
data class SongSection(val type: SectionType, val startSec: Double, val endSec: Double)

/** Una riga di testo con il suo intervallo di tempo esatto: quello che la schermata karaoke mostra. */
data class LyricLine(val type: SectionType, val startSec: Double, val endSec: Double, val text: String)

/**
 * Scaletta musicale (per [BeatGenerator]): alterna blocchi di strofa/ritornello lungo tutta la
 * durata scelta dall'utente, chiudendo sempre su un ritornello (finale "in crescendo") se un
 * ritornello è stato scritto. Puramente ritmica: non sa nulla del testo.
 */
fun buildMusicArrangement(
    totalDurationSec: Double,
    hasChorus: Boolean,
    blockSeconds: Double = 15.0,
): List<SongSection> {
    val blockCount = max(2, Math.round(totalDurationSec / blockSeconds).toInt())
    val actualBlockSeconds = totalDurationSec / blockCount
    return (0 until blockCount).map { i ->
        val start = i * actualBlockSeconds
        val end = if (i == blockCount - 1) totalDurationSec else (i + 1) * actualBlockSeconds
        val isChorus = hasChorus && (i == blockCount - 1 || i % 2 == 1)
        SongSection(if (isChorus) SectionType.RITORNELLO else SectionType.STROFA, start, end)
    }
}

/**
 * Distribuisce automaticamente TUTTO il testo importato/scritto sotto il brano, sincronizzato al
 * tempo scelto per la durata: nessuna riga persa, nessuna sincronizzazione manuale da parte
 * dell'utente. Le righe di strofa riempiono in ordine le sezioni "strofa" di [musicSections]
 * (distribuite il più possibile in modo uniforme, così da consumare esattamente tutte le righe
 * entro l'ultima sezione di strofa); il ritornello scelto occupa per intero ogni sezione
 * "ritornello". All'interno di una sezione, il tempo di ciascuna riga è proporzionale alla sua
 * lunghezza (righe più lunghe restano visibili più a lungo).
 */
fun buildLyricTimeline(musicSections: List<SongSection>, verseLyrics: String, chorusLyrics: String): List<LyricLine> {
    val verseLines = verseLyrics.lines().map { it.trim() }.filter { it.isNotBlank() }
    val chorusText = chorusLyrics.trim()

    var lineCursor = 0
    var remainingVerseSections = musicSections.count { it.type == SectionType.STROFA }
    val result = mutableListOf<LyricLine>()

    for (section in musicSections) {
        if (section.type == SectionType.RITORNELLO) {
            result += LyricLine(SectionType.RITORNELLO, section.startSec, section.endSec, chorusText.ifBlank { "(ritornello)" })
            continue
        }

        val remainingLines = verseLines.size - lineCursor
        if (remainingLines <= 0) {
            result += LyricLine(SectionType.STROFA, section.startSec, section.endSec, verseLines.lastOrNull() ?: "(strofa)")
        } else {
            val linesHere = ceil(remainingLines.toDouble() / remainingVerseSections).toInt().coerceAtLeast(1)
            val chunk = verseLines.subList(lineCursor, minOf(lineCursor + linesHere, verseLines.size))
            lineCursor += chunk.size

            val weights = chunk.map { max(1, it.length) }
            val totalWeight = weights.sum().toDouble()
            val sectionDuration = section.endSec - section.startSec
            var cursor = section.startSec
            chunk.forEachIndexed { i, line ->
                val end = if (i == chunk.lastIndex) section.endSec else cursor + sectionDuration * (weights[i] / totalWeight)
                result += LyricLine(SectionType.STROFA, cursor, end, line)
                cursor = end
            }
        }
        remainingVerseSections--
    }
    return result
}
