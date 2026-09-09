package com.example.sentinelai.core

/**
 * Local offline signature database (known-malicious file hashes).
 * The offline half of Cloud & Offline Protection: SentinelAI never depends
 * on a network connection to recognize a known threat. See CloudLookup.kt
 * for the optional online lookup that complements it.
 */
object Signatures {
    const val VERSION = "2026.09.01"

    // EICAR standard antivirus test file — a safe, industry-standard string
    // used to verify detection without using real malware.
    private val HASHES = mapOf(
        "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f" to "EICAR-Test-File"
    )

    fun lookup(sha256: String): String? = HASHES[sha256.lowercase()]

    fun count(): Int = HASHES.size
}
