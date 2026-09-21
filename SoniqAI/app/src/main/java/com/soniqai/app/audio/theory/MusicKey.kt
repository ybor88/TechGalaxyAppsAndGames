// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.audio.theory

/** Le 12 note della scala cromatica, con la frequenza (Hz) della relativa ottava di riferimento (4). */
enum class MusicKey(val label: String, val rootFrequencyC4Octave: Double) {
    DO("Do", 261.63),
    DO_DIESIS("Do#", 277.18),
    RE("Re", 293.66),
    RE_DIESIS("Re#", 311.13),
    MI("Mi", 329.63),
    FA("Fa", 349.23),
    FA_DIESIS("Fa#", 369.99),
    SOL("Sol", 392.00),
    SOL_DIESIS("Sol#", 415.30),
    LA("La", 440.00),
    LA_DIESIS("La#", 466.16),
    SI("Si", 493.88),
}

enum class MusicMode(val label: String, val scaleIntervals: IntArray) {
    MAGGIORE("Maggiore", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    MINORE("Minore", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
}

/** Il "tono" scelto dall'utente: nota fondamentale + modo (maggiore/minore). */
data class Tonalita(val key: MusicKey, val mode: MusicMode) {
    val label: String get() = "${key.label} ${mode.label.lowercase()}"

    /** Frequenza (Hz) della nota fondamentale, in una data ottava relativa a quella di riferimento (0 = ottava 4). */
    fun rootFrequency(octaveOffset: Int = 0): Double =
        key.rootFrequencyC4Octave * Math.pow(2.0, octaveOffset.toDouble())

    /**
     * Frequenza (Hz) del grado [degree] della scala (1 = tonica, 2 = seconda, ...), eventualmente
     * oltre la settima (es. 8 = tonica ottava sopra, 9 = seconda ottava sopra...), con [octaveOffset]
     * addizionale rispetto all'ottava di riferimento.
     */
    fun degreeFrequency(degree: Int, octaveOffset: Int = 0): Double {
        val zeroBased = degree - 1
        val octaveJump = Math.floorDiv(zeroBased, mode.scaleIntervals.size)
        val degreeInScale = Math.floorMod(zeroBased, mode.scaleIntervals.size)
        val semitones = mode.scaleIntervals[degreeInScale]
        val totalOctave = octaveOffset + octaveJump
        return rootFrequency(totalOctave) * Math.pow(2.0, semitones / 12.0)
    }
}

/** Un accordo espresso come grado della scala (1-7); la triade si ottiene con [degree], [degree]+2, [degree]+4. */
data class ChordDegree(val degree: Int) {
    fun frequencies(tonalita: Tonalita, octaveOffset: Int = 0): Triple<Double, Double, Double> = Triple(
        tonalita.degreeFrequency(degree, octaveOffset),
        tonalita.degreeFrequency(degree + 2, octaveOffset),
        tonalita.degreeFrequency(degree + 4, octaveOffset),
    )
}
