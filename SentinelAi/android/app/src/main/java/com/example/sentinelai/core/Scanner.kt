package com.example.sentinelai.core

import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.log2

/** A file to scan, abstracted over its real source (SAF document tree or app-private storage). */
data class ScannableFile(
    val displayPath: String,
    val name: String,
    val size: Long,
    val opener: () -> InputStream
)

data class DetectionResult(
    val path: String,
    val sha256: String = "",
    val riskScore: Int = 0,
    val reasons: List<String> = emptyList(),
    val threatName: String = "",
    val verdict: String = "clean", // clean | suspicious | malicious
    val error: String = ""
) {
    val isFlagged: Boolean get() = verdict == "suspicious" || verdict == "malicious"
}

/**
 * Detection engine: hash-based signature matching + heuristic risk scoring.
 * The heuristic scorer is "AI Advanced Detection" / "Predictive Defense": a
 * transparent, weighted rule model — not a black-box model — that flags
 * suspicious traits before a file is known-bad in any database.
 */
object Scanner {
    private val EXECUTABLE_EXTENSIONS = setOf(
        "apk", "exe", "dll", "scr", "bat", "cmd", "vbs", "js", "ps1", "msi", "com", "jar", "dex"
    )
    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "txt", "mp3", "mp4"
    )
    private val SUSPICIOUS_KEYWORDS = listOf("crack", "keygen", "loader", "activator", "patcher", "hack")
    private const val CHUNK_SIZE = 1 * 1024 * 1024
    private const val ENTROPY_SAMPLE_MAX = 4 * 1024 * 1024

    private fun extensionOf(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx >= 0) name.substring(idx + 1).lowercase() else ""
    }

    private fun hasDoubleExtension(name: String): Boolean {
        val parts = name.lowercase().split(".")
        if (parts.size < 3) return false
        val lastExt = parts.last()
        val secondExt = parts[parts.size - 2]
        return lastExt in EXECUTABLE_EXTENSIONS && secondExt in DOCUMENT_EXTENSIONS
    }

    private fun shannonEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        val freq = IntArray(256)
        for (b in data) freq[b.toInt() and 0xFF]++
        val length = data.size.toDouble()
        var entropy = 0.0
        for (count in freq) {
            if (count > 0) {
                val p = count / length
                entropy -= p * log2(p)
            }
        }
        return entropy
    }

    private fun verdictForScore(score: Int): String = when {
        score >= 70 -> "malicious"
        score >= 30 -> "suspicious"
        else -> "clean"
    }

    fun scanFile(file: ScannableFile): DetectionResult {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var sampleBuffer: ByteArray? = null
            var sampleFilled = 0

            file.opener().use { input ->
                val buffer = ByteArray(CHUNK_SIZE)
                var totalRead = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    if (totalRead < ENTROPY_SAMPLE_MAX) {
                        if (sampleBuffer == null) {
                            sampleBuffer = ByteArray(minOf(file.size.toInt().coerceAtLeast(0), ENTROPY_SAMPLE_MAX))
                        }
                        val toCopy = minOf(read, (sampleBuffer?.size ?: 0) - sampleFilled)
                        if (toCopy > 0) {
                            System.arraycopy(buffer, 0, sampleBuffer!!, sampleFilled, toCopy)
                            sampleFilled += toCopy
                        }
                    }
                    totalRead += read
                }
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val known = Signatures.lookup(sha256)
            if (known != null) {
                return DetectionResult(
                    path = file.displayPath,
                    sha256 = sha256,
                    riskScore = 100,
                    reasons = listOf("Hash corrispondente a firma nota nel database offline"),
                    threatName = known,
                    verdict = "malicious"
                )
            }

            var score = 0
            val reasons = mutableListOf<String>()
            val ext = extensionOf(file.name)
            val lowered = file.name.lowercase()

            if (lowered == "autorun.inf") {
                score += 20
                reasons.add("File autorun.inf rilevato")
            }
            if (hasDoubleExtension(file.name)) {
                score += 35
                reasons.add("Doppia estensione sospetta (es. .pdf.exe)")
            }
            if (SUSPICIOUS_KEYWORDS.any { lowered.contains(it) } && ext in EXECUTABLE_EXTENSIONS) {
                score += 15
                reasons.add("Nome file contiene termini associati a software pirata/malevolo")
            }
            if (ext in EXECUTABLE_EXTENSIONS && file.size > 0 && sampleFilled > 0) {
                val entropy = shannonEntropy(sampleBuffer!!.copyOf(sampleFilled))
                if (entropy >= 7.5) {
                    score += 25
                    reasons.add("Entropia elevata (%.2f/8.0): possibile file impacchettato/offuscato".format(entropy))
                }
            }
            if (ext in EXECUTABLE_EXTENSIONS && file.size == 0L) {
                score += 10
                reasons.add("Eseguibile di dimensione zero (anomalo)")
            }
            if (ext in EXECUTABLE_EXTENSIONS && (file.displayPath.contains("/temp/", true) ||
                    file.displayPath.contains("/tmp/", true) || file.displayPath.contains("/cache/", true))
            ) {
                score += 10
                reasons.add("Eseguibile in una cartella temporanea/cache")
            }

            score = minOf(score, 99)
            val verdict = verdictForScore(score)
            DetectionResult(
                path = file.displayPath,
                sha256 = sha256,
                riskScore = score,
                reasons = reasons,
                threatName = when (verdict) {
                    "malicious" -> "Heuristic.Suspicious.Generic"
                    "suspicious" -> "Heuristic.PotentiallyUnwanted"
                    else -> ""
                },
                verdict = verdict
            )
        } catch (exc: Exception) {
            DetectionResult(path = file.displayPath, error = exc.message ?: "Errore sconosciuto")
        }
    }
}
