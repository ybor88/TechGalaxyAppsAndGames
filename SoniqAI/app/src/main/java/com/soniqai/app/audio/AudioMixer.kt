// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.audio

import kotlin.math.abs
import kotlin.math.max

/** Somma la base strumentale e la voce registrata in un unico PCM, con normalizzazione anti-clip. */
object AudioMixer {

    fun mix(
        instrumental: PcmAudio,
        vocal: PcmAudio,
        instrumentalGain: Double = 0.85,
        vocalGain: Double = 1.4,
    ): ShortArray {
        val length = max(instrumental.samples.size, vocal.samples.size)
        val mixed = DoubleArray(length)
        for (i in 0 until length) {
            val instrumentSample = if (i < instrumental.samples.size) instrumental.samples[i] / 32768.0 else 0.0
            val vocalSample = if (i < vocal.samples.size) vocal.samples[i] / 32768.0 else 0.0
            mixed[i] = instrumentSample * instrumentalGain + vocalSample * vocalGain
        }
        var peak = 0.0
        for (v in mixed) peak = max(peak, abs(v))
        val scale = if (peak > 0.95) 0.95 / peak else 1.0
        return ShortArray(length) { i ->
            val v = (mixed[i] * scale).coerceIn(-1.0, 1.0)
            (v * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
