// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.audio.theory

enum class WaveShape { SINE, SAW, SQUARE, TRIANGLE }

/**
 * Pattern ritmico su 16 semicrome (una battuta in 4/4): true = colpo suonato in quello step.
 * Stessa lunghezza per kick/snare/hihat, indipendentemente dal genere.
 */
data class DrumPattern(
    val kick: BooleanArray,
    val snare: BooleanArray,
    val hihat: BooleanArray,
)

/** Definisce come [com.soniqai.app.audio.BeatGenerator] sintetizza la base strumentale di un genere. */
enum class Genre(
    val label: String,
    val tempoBpm: Int,
    val chordDegrees: List<Int>,
    val bassWave: WaveShape,
    val padWave: WaveShape,
    val basePattern: DrumPattern,
    val chorusPattern: DrumPattern,
) {
    POP(
        label = "Pop",
        tempoBpm = 112,
        chordDegrees = listOf(1, 5, 6, 4),
        bassWave = WaveShape.TRIANGLE,
        padWave = WaveShape.SAW,
        basePattern = DrumPattern(
            kick = boolSteps("1000100010001000"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("1010101010101010"),
        ),
        chorusPattern = DrumPattern(
            kick = boolSteps("1000100010001000"),
            snare = boolSteps("0000100000001010"),
            hihat = boolSteps("1111111111111111"),
        ),
    ),
    ROCK(
        label = "Rock",
        tempoBpm = 128,
        chordDegrees = listOf(6, 4, 1, 5),
        bassWave = WaveShape.SQUARE,
        padWave = WaveShape.SAW,
        basePattern = DrumPattern(
            kick = boolSteps("1010001010100010"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("1010101010101010"),
        ),
        chorusPattern = DrumPattern(
            kick = boolSteps("1010101010101010"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("1111111111111111"),
        ),
    ),
    HIP_HOP(
        label = "Hip Hop",
        tempoBpm = 92,
        chordDegrees = listOf(1, 6, 3, 7),
        bassWave = WaveShape.SINE,
        padWave = WaveShape.SINE,
        basePattern = DrumPattern(
            kick = boolSteps("1000000110000000"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("1010101110101011"),
        ),
        chorusPattern = DrumPattern(
            kick = boolSteps("1000000110001000"),
            snare = boolSteps("0000100000001010"),
            hihat = boolSteps("1111101111111011"),
        ),
    ),
    EDM(
        label = "EDM",
        tempoBpm = 128,
        chordDegrees = listOf(6, 4, 1, 5),
        bassWave = WaveShape.SAW,
        padWave = WaveShape.SQUARE,
        basePattern = DrumPattern(
            kick = boolSteps("1000100010001000"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("0010101000101010"),
        ),
        chorusPattern = DrumPattern(
            kick = boolSteps("1010101010101010"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("1111111111111111"),
        ),
    ),
    REGGAETON(
        label = "Reggaeton",
        tempoBpm = 96,
        chordDegrees = listOf(1, 6, 3, 7),
        bassWave = WaveShape.SINE,
        padWave = WaveShape.SAW,
        basePattern = DrumPattern(
            kick = boolSteps("1001001010010010"),
            snare = boolSteps("0000101000001010"),
            hihat = boolSteps("1010101010101010"),
        ),
        chorusPattern = DrumPattern(
            kick = boolSteps("1001001010010010"),
            snare = boolSteps("0000101000001010"),
            hihat = boolSteps("1111111111111111"),
        ),
    ),
    LOFI(
        label = "Lo-fi",
        tempoBpm = 76,
        chordDegrees = listOf(2, 5, 1, 6),
        bassWave = WaveShape.SINE,
        padWave = WaveShape.SINE,
        basePattern = DrumPattern(
            kick = boolSteps("1000000010000010"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("0010001000100010"),
        ),
        chorusPattern = DrumPattern(
            kick = boolSteps("1000000010000010"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("1010101010101010"),
        ),
    ),
    ACUSTICO(
        label = "Acustico",
        tempoBpm = 100,
        chordDegrees = listOf(1, 6, 4, 5),
        bassWave = WaveShape.SINE,
        padWave = WaveShape.TRIANGLE,
        basePattern = DrumPattern(
            kick = boolSteps("1000000010000000"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("0010001000100010"),
        ),
        chorusPattern = DrumPattern(
            kick = boolSteps("1000100010001000"),
            snare = boolSteps("0000100000001000"),
            hihat = boolSteps("1010101010101010"),
        ),
    ),
    ;
}

/** Converte una stringa di 16 caratteri '0'/'1' in un pattern booleano leggibile a colpo d'occhio. */
private fun boolSteps(pattern: String): BooleanArray {
    require(pattern.length == 16) { "Il pattern ritmico deve avere 16 step, trovati ${pattern.length}" }
    return BooleanArray(16) { pattern[it] == '1' }
}
