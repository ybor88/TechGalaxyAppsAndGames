// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SAMPLE_RATE = 44100

data class PcmAudio(val samples: ShortArray, val sampleRate: Int, val channels: Int) {
    val durationSec: Double get() = samples.size.toDouble() / channels / sampleRate
}

/** Scrittura/lettura di file WAV PCM 16-bit non compresso (formato 1), mono o stereo. */
object WavFile {

    fun write(file: File, samples: ShortArray, sampleRate: Int = SAMPLE_RATE, channels: Int = 1) {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val dataSize = samples.size * 2

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // sub-chunk size (PCM)
            header.putShort(1) // audio format = PCM
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(16) // bits per sample
            header.put("data".toByteArray())
            header.putInt(dataSize)
            raf.write(header.array())

            val body = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) body.putShort(s)
            raf.write(body.array())
        }
    }

    fun read(file: File): PcmAudio {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(44)
            raf.readFully(header)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            bb.position(22)
            val channels = bb.short.toInt()
            val sampleRate = bb.int
            bb.position(40)
            val dataSize = bb.int
            val data = ByteArray(dataSize)
            raf.readFully(data)
            val samples = ShortArray(dataSize / 2)
            val dataBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            for (i in samples.indices) samples[i] = dataBuffer.short
            return PcmAudio(samples, sampleRate, channels)
        }
    }
}
