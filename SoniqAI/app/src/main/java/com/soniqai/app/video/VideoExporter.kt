// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.video

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.soniqai.app.audio.PcmAudio
import com.soniqai.app.audio.WavFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max

/**
 * Compone un mp4 (video H.264 + audio AAC) da una foto statica e un WAV: nessuna libreria nativa
 * esterna (niente FFmpeg), solo le API Android MediaCodec/MediaMuxer. Il video è a bassa frame
 * rate (una foto ferma non ha bisogno di più fotogrammi) ma di durata identica all'audio, pronto
 * per essere condiviso ad es. sull'app YouTube tramite il menu di condivisione di sistema.
 */
object VideoExporter {

    private const val VIDEO_WIDTH = 720
    private const val VIDEO_HEIGHT = 720
    private const val VIDEO_FRAME_RATE = 2
    private const val VIDEO_BITRATE = 2_500_000
    private const val AUDIO_BITRATE = 128_000
    private const val TIMEOUT_US = 10_000L

    fun export(coverImagePath: String, audioWavPath: String, title: String, outputFile: File) {
        val audio = WavFile.read(File(audioWavPath))
        val bitmap = buildCoverFrame(coverImagePath, title)
        try {
            val videoTrack = encodeVideoTrack(bitmap, audio.durationSec)
            val audioTrack = encodeAudioTrack(audio)
            mux(videoTrack, audioTrack, outputFile)
        } finally {
            bitmap.recycle()
        }
    }

    // --- Fotogramma di copertina -------------------------------------------------------------

    private fun buildCoverFrame(imagePath: String, title: String): Bitmap {
        val source = BitmapFactory.decodeFile(imagePath)
            ?: error("Impossibile leggere la foto di copertina: $imagePath")
        val target = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)

        val scale = max(VIDEO_WIDTH.toFloat() / source.width, VIDEO_HEIGHT.toFloat() / source.height)
        val scaledW = source.width * scale
        val scaledH = source.height * scale
        val left = (VIDEO_WIDTH - scaledW) / 2f
        val top = (VIDEO_HEIGHT - scaledH) / 2f
        canvas.drawBitmap(source, null, RectF(left, top, left + scaledW, top + scaledH), Paint(Paint.ANTI_ALIAS_FLAG))
        source.recycle()

        val scrimHeight = VIDEO_HEIGHT * 0.3f
        val scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f, VIDEO_HEIGHT - scrimHeight, 0f, VIDEO_HEIGHT.toFloat(),
                0x00000000, 0xCC000000.toInt(), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, VIDEO_HEIGHT - scrimHeight, VIDEO_WIDTH.toFloat(), VIDEO_HEIGHT.toFloat(), scrimPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        canvas.drawText(title, 32f, VIDEO_HEIGHT - 52f, titlePaint)

        val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 20f
        }
        canvas.drawText("SoniqAI · © Roberto Di Flumeri", 32f, VIDEO_HEIGHT - 20f, creditPaint)

        return target
    }

    // --- Traccia video ------------------------------------------------------------------------

    private data class EncodedTrack(val format: MediaFormat, val samples: List<Pair<ByteArray, MediaCodec.BufferInfo>>)

    private fun encodeVideoTrack(bitmap: Bitmap, durationSec: Double): EncodedTrack {
        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val encoder = MediaCodec.createEncoderByType(mime)
        val colorFormat = chooseColorFormat(encoder, mime)
        val format = MediaFormat.createVideoFormat(mime, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val yuvFrame = bitmapToYuv(bitmap, colorFormat)
        val totalFrames = ceil(durationSec * VIDEO_FRAME_RATE).toInt().coerceAtLeast(1)
        val frameDurationUs = 1_000_000L / VIDEO_FRAME_RATE
        val bufferInfo = MediaCodec.BufferInfo()
        val samples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        val formatHolder = arrayOfNulls<MediaFormat>(1)

        var frameIndex = 0
        var inputDone = false
        while (!inputDone) {
            val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                inputBuffer.clear()
                if (frameIndex < totalFrames) {
                    inputBuffer.put(yuvFrame)
                    encoder.queueInputBuffer(inputIndex, 0, yuvFrame.size, frameIndex * frameDurationUs, 0)
                    frameIndex++
                } else {
                    encoder.queueInputBuffer(inputIndex, 0, 0, frameIndex * frameDurationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                }
            }
            drainEncoder(encoder, bufferInfo, samples, formatHolder, waitForEos = false)
        }
        drainEncoder(encoder, bufferInfo, samples, formatHolder, waitForEos = true)
        encoder.stop()
        encoder.release()
        return EncodedTrack(formatHolder[0] ?: format, samples)
    }

    private fun chooseColorFormat(encoder: MediaCodec, mime: String): Int {
        val supported = encoder.codecInfo.getCapabilitiesForType(mime).colorFormats.toSet()
        return when {
            supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            else -> MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        }
    }

    /** Converte il fotogramma RGB in YUV420 (planare I420 o semi-planare NV12, secondo cosa richiede l'encoder). */
    private fun bitmapToYuv(bitmap: Bitmap, colorFormat: Int): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val frameSize = width * height
        val yuv = ByteArray(frameSize + frameSize / 2)
        val isSemiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        val uPlane = IntArray(frameSize / 4)
        val vPlane = IntArray(frameSize / 4)

        var yIndex = 0
        var uvIndex = frameSize
        var chromaIndex = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    if (isSemiPlanar) {
                        yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    } else {
                        uPlane[chromaIndex] = u.coerceIn(0, 255)
                        vPlane[chromaIndex] = v.coerceIn(0, 255)
                        chromaIndex++
                    }
                }
            }
        }
        if (!isSemiPlanar) {
            for (u in uPlane) yuv[uvIndex++] = u.toByte()
            for (v in vPlane) yuv[uvIndex++] = v.toByte()
        }
        return yuv
    }

    // --- Traccia audio ------------------------------------------------------------------------

    private fun encodeAudioTrack(pcm: PcmAudio): EncodedTrack {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val encoder = MediaCodec.createEncoderByType(mime)
        val format = MediaFormat.createAudioFormat(mime, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val pcmBytes = ByteBuffer.allocate(pcm.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (s in pcm.samples) putShort(s)
        }.array()
        val bytesPerSecond = pcm.sampleRate * pcm.channels * 2

        val bufferInfo = MediaCodec.BufferInfo()
        val samples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        val formatHolder = arrayOfNulls<MediaFormat>(1)

        var offset = 0
        var inputDone = false
        while (!inputDone) {
            val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                inputBuffer.clear()
                val remaining = pcmBytes.size - offset
                if (remaining > 0) {
                    val toCopy = minOf(inputBuffer.capacity(), remaining)
                    val pts = offset.toLong() * 1_000_000L / bytesPerSecond
                    inputBuffer.put(pcmBytes, offset, toCopy)
                    encoder.queueInputBuffer(inputIndex, 0, toCopy, pts, 0)
                    offset += toCopy
                } else {
                    val pts = offset.toLong() * 1_000_000L / bytesPerSecond
                    encoder.queueInputBuffer(inputIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                }
            }
            drainEncoder(encoder, bufferInfo, samples, formatHolder, waitForEos = false)
        }
        drainEncoder(encoder, bufferInfo, samples, formatHolder, waitForEos = true)
        encoder.stop()
        encoder.release()
        return EncodedTrack(formatHolder[0] ?: format, samples)
    }

    // --- Utility comuni -----------------------------------------------------------------------

    /** Preleva l'output disponibile dall'encoder. Ritorna true quando è stato raggiunto l'end-of-stream. */
    private fun drainEncoder(
        encoder: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        samples: MutableList<Pair<ByteArray, MediaCodec.BufferInfo>>,
        formatHolder: Array<MediaFormat?>,
        waitForEos: Boolean,
    ): Boolean {
        while (true) {
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEos) return false
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    formatHolder[0] = encoder.outputFormat
                }
                outputIndex >= 0 -> {
                    val outBuffer = encoder.getOutputBuffer(outputIndex)!!
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (!isConfig && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val data = ByteArray(bufferInfo.size)
                        outBuffer.get(data)
                        val infoCopy = MediaCodec.BufferInfo().apply {
                            set(0, data.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        }
                        samples.add(data to infoCopy)
                    }
                    val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (isEos) return true
                }
            }
        }
    }

    private fun mux(video: EncodedTrack, audio: EncodedTrack, outputFile: File) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoTrackIndex = muxer.addTrack(video.format)
        val audioTrackIndex = muxer.addTrack(audio.format)
        muxer.start()
        for ((data, info) in video.samples) {
            muxer.writeSampleData(videoTrackIndex, ByteBuffer.wrap(data), info)
        }
        for ((data, info) in audio.samples) {
            muxer.writeSampleData(audioTrackIndex, ByteBuffer.wrap(data), info)
        }
        muxer.stop()
        muxer.release()
    }
}
