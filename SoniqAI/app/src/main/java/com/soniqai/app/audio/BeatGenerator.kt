// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.audio

import com.soniqai.app.audio.theory.ChordDegree
import com.soniqai.app.audio.theory.Genre
import com.soniqai.app.audio.theory.Tonalita
import com.soniqai.app.audio.theory.WaveShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sintetizzatore procedurale della base strumentale: nessun servizio esterno, nessun file audio
 * campionato. Ogni battuta somma quattro strati (batteria, basso, pad/accordi, e nel ritornello
 * un lead extra) generati a partire da onde elementari (sin/dente di sega/quadra/triangolare),
 * seguendo tempo/accordi/pattern definiti da [Genre] e la tonalità scelta dall'utente.
 */
object BeatGenerator {

    fun generate(genre: Genre, tonalita: Tonalita, durationSec: Double, sections: List<SongSection>): ShortArray {
        val secondsPerBeat = 60.0 / genre.tempoBpm
        val beatsPerBar = 4
        val barDurationSec = secondsPerBeat * beatsPerBar
        val totalSamples = (durationSec * SAMPLE_RATE).toInt()
        val mix = DoubleArray(totalSamples)

        val totalBars = Math.ceil(durationSec / barDurationSec).toInt()
        for (barIndex in 0 until totalBars) {
            val barStartSec = barIndex * barDurationSec
            if (barStartSec >= durationSec) break
            val barEndSec = min(barStartSec + barDurationSec, durationSec)

            val section = sections.firstOrNull { barStartSec >= it.startSec && barStartSec < it.endSec }
                ?: sections.last()
            val isChorus = section.type == SectionType.RITORNELLO
            val pattern = if (isChorus) genre.chorusPattern else genre.basePattern
            val chordDegree = genre.chordDegrees[barIndex % genre.chordDegrees.size]
            val chord = ChordDegree(chordDegree).frequencies(tonalita)
            val bassFreq = tonalita.degreeFrequency(chordDegree, octaveOffset = -1)

            renderDrums(mix, pattern, barStartSec, barDurationSec)
            renderBass(mix, bassFreq, genre.bassWave, barStartSec, secondsPerBeat)
            renderPad(mix, chord, genre.padWave, barStartSec, barEndSec, volume = if (isChorus) 0.22 else 0.15)
            if (isChorus) renderLead(mix, chord, genre.padWave, barStartSec, secondsPerBeat)
        }
        return normalizeToShort(mix)
    }

    // --- Batteria -------------------------------------------------------------------------

    private fun renderDrums(mix: DoubleArray, pattern: com.soniqai.app.audio.theory.DrumPattern, barStartSec: Double, barDurationSec: Double) {
        val stepDuration = barDurationSec / 16
        for (step in 0 until 16) {
            val stepStart = barStartSec + step * stepDuration
            if (pattern.kick[step]) addKick(mix, stepStart)
            if (pattern.snare[step]) addSnare(mix, stepStart)
            if (pattern.hihat[step]) addHihat(mix, stepStart)
        }
    }

    private fun addKick(mix: DoubleArray, startSec: Double) {
        val durationSec = 0.12
        val n = (durationSec * SAMPLE_RATE).toInt()
        writeInto(mix, startSec, n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / durationSec
            val freq = 150.0 - 100.0 * progress // pitch sweep 150Hz -> 50Hz
            val envelope = Math.exp(-6.0 * progress)
            sin(2 * PI * freq * t) * envelope * 0.9
        }
    }

    private fun addSnare(mix: DoubleArray, startSec: Double) {
        val durationSec = 0.14
        val n = (durationSec * SAMPLE_RATE).toInt()
        writeInto(mix, startSec, n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / durationSec
            val envelope = Math.exp(-9.0 * progress)
            val tone = sin(2 * PI * 190.0 * t) * 0.4
            val noise = (Random.nextDouble(-1.0, 1.0)) * 0.6
            (tone + noise) * envelope * 0.7
        }
    }

    private fun addHihat(mix: DoubleArray, startSec: Double) {
        val durationSec = 0.05
        val n = (durationSec * SAMPLE_RATE).toInt()
        writeInto(mix, startSec, n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / durationSec
            val envelope = Math.exp(-18.0 * progress)
            Random.nextDouble(-1.0, 1.0) * envelope * 0.35
        }
    }

    // --- Basso ------------------------------------------------------------------------------

    private fun renderBass(mix: DoubleArray, freq: Double, wave: WaveShape, barStartSec: Double, secondsPerBeat: Double) {
        // Nota di basso su ogni battito (1,2,3,4): pattern semplice ma solido per qualunque genere.
        for (beat in 0 until 4) {
            val startSec = barStartSec + beat * secondsPerBeat
            val durationSec = secondsPerBeat * 0.9
            val n = (durationSec * SAMPLE_RATE).toInt()
            writeInto(mix, startSec, n) { i ->
                val t = i.toDouble() / SAMPLE_RATE
                val progress = t / durationSec
                val envelope = min(1.0, progress * 40) * Math.exp(-2.0 * progress)
                waveValue(wave, freq, t) * envelope * 0.5
            }
        }
    }

    // --- Pad / accordi ------------------------------------------------------------------------

    private fun renderPad(mix: DoubleArray, chord: Triple<Double, Double, Double>, wave: WaveShape, startSec: Double, endSec: Double, volume: Double) {
        val durationSec = endSec - startSec
        val n = (durationSec * SAMPLE_RATE).toInt()
        val attackSamples = (0.03 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val releaseSamples = (0.05 * SAMPLE_RATE).toInt().coerceAtLeast(1)
        writeInto(mix, startSec, n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i > n - releaseSamples -> max(0.0, (n - i).toDouble() / releaseSamples)
                else -> 1.0
            }
            val chordValue = waveValue(wave, chord.first, t) + waveValue(wave, chord.second, t) + waveValue(wave, chord.third, t)
            (chordValue / 3.0) * envelope * volume
        }
    }

    // --- Lead (solo nel ritornello, per dare più energia) --------------------------------------

    private fun renderLead(mix: DoubleArray, chord: Triple<Double, Double, Double>, wave: WaveShape, barStartSec: Double, secondsPerBeat: Double) {
        val notes = listOf(chord.first, chord.second, chord.third, chord.second)
        for (beat in 0 until 4) {
            val startSec = barStartSec + beat * secondsPerBeat
            val durationSec = secondsPerBeat * 0.45
            val n = (durationSec * SAMPLE_RATE).toInt()
            val freq = notes[beat] * 2.0 // un'ottava sopra il pad, per farlo emergere
            writeInto(mix, startSec, n) { i ->
                val t = i.toDouble() / SAMPLE_RATE
                val progress = t / durationSec
                val envelope = min(1.0, progress * 60) * Math.exp(-4.0 * progress)
                waveValue(wave, freq, t) * envelope * 0.18
            }
        }
    }

    // --- Utility --------------------------------------------------------------------------

    private inline fun writeInto(mix: DoubleArray, startSec: Double, sampleCount: Int, sample: (Int) -> Double) {
        val startIndex = (startSec * SAMPLE_RATE).toInt()
        if (startIndex >= mix.size) return
        val end = min(startIndex + sampleCount, mix.size)
        for (i in startIndex until end) {
            mix[i] += sample(i - startIndex)
        }
    }

    private fun waveValue(shape: WaveShape, freq: Double, t: Double): Double = when (shape) {
        WaveShape.SINE -> sin(2 * PI * freq * t)
        WaveShape.SAW -> {
            val phase = (freq * t) % 1.0
            2.0 * (phase - Math.floor(phase + 0.5))
        }
        WaveShape.SQUARE -> if (sin(2 * PI * freq * t) >= 0) 1.0 else -1.0
        WaveShape.TRIANGLE -> {
            val phase = (freq * t) % 1.0
            2.0 * abs(2.0 * (phase - Math.floor(phase + 0.5))) - 1.0
        }
    }

    private fun normalizeToShort(mix: DoubleArray): ShortArray {
        var peak = 0.0
        for (v in mix) peak = max(peak, abs(v))
        val scale = if (peak > 0.9) 0.9 / peak else 1.0
        return ShortArray(mix.size) { i ->
            val v = (mix[i] * scale).coerceIn(-1.0, 1.0)
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
