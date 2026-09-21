// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Registra la voce dal microfono in PCM 16-bit mono a [SAMPLE_RATE] Hz mentre in UI viene
 * riprodotta la base strumentale (mixaggio a posteriori via [AudioMixer], non in tempo reale:
 * niente monitoraggio audio a bassa latenza, ma nessuna dipendenza da librerie native esterne).
 */
class VoiceRecorder {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    @Volatile private var recording = false
    private var recordedBytes: ByteArray = ByteArray(0)

    private val _amplitude = MutableStateFlow(0f)
    /** Livello RMS (0..1) dell'ultimo blocco registrato, per un semplice indicatore visivo. */
    val amplitude: StateFlow<Float> = _amplitude

    fun start() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(2048)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        audioRecord = record
        record.startRecording()
        recording = true

        recordingThread = Thread {
            val byteStream = ByteArrayOutputStream()
            val shortBuffer = ShortArray(2048)
            while (recording) {
                val read = record.read(shortBuffer, 0, shortBuffer.size)
                if (read > 0) {
                    var sumSquares = 0.0
                    val chunk = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until read) {
                        chunk.putShort(shortBuffer[i])
                        val normalized = shortBuffer[i].toDouble() / Short.MAX_VALUE
                        sumSquares += normalized * normalized
                    }
                    byteStream.write(chunk.array())
                    _amplitude.value = sqrt(sumSquares / read).toFloat()
                }
            }
            recordedBytes = byteStream.toByteArray()
        }.also { it.start() }
    }

    /** Ferma la registrazione e salva il file WAV. Bloccante per pochi millisecondi: chiamare da un thread IO. */
    fun stop(outputFile: File): File {
        recording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val samples = ShortArray(recordedBytes.size / 2)
        val bb = ByteBuffer.wrap(recordedBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in samples.indices) samples[i] = bb.short
        WavFile.write(outputFile, samples, SAMPLE_RATE, 1)
        return outputFile
    }
}
