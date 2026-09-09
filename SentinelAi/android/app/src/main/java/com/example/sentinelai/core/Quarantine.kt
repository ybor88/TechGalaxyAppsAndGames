package com.example.sentinelai.core

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Quarantine: neutralizes a flagged file by copying its bytes (XOR-
 * obfuscated so it can't be opened/executed by accident) into the app's
 * private storage, then removing the original. Restorable via restoreFile().
 */
class Quarantine(private val context: Context, private val db: Database) {

    private val xorKey = 0xA5.toByte()

    private fun xorTransform(data: ByteArray): ByteArray =
        ByteArray(data.size) { i -> (data[i].toInt() xor xorKey.toInt()).toByte() }

    private fun quarantineDir(): File {
        val dir = File(context.filesDir, "quarantine")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun quarantineFile(
        originalPath: String,
        threatName: String,
        riskScore: Int,
        opener: () -> InputStream,
        deleteOriginal: () -> Boolean
    ): Long {
        val token = UUID.randomUUID().toString()
        val dest = File(quarantineDir(), "$token.quar")

        val rawBytes = opener().use { it.readBytes() }
        dest.writeBytes(xorTransform(rawBytes))
        deleteOriginal()

        val id = db.addQuarantine(originalPath, dest.absolutePath, threatName, riskScore)
        db.logEvent("warning", "Messo in quarantena: $originalPath ($threatName)")
        return id
    }

    fun restoreFile(id: Long): String {
        val item = db.getQuarantineItem(id) ?: throw IllegalArgumentException("Elemento in quarantena non trovato")
        val quarantineFile = File(item.quarantinePath)
        val restoredDir = File(context.getExternalFilesDir(null), "Ripristinati")
        if (!restoredDir.exists()) restoredDir.mkdirs()

        val originalName = item.originalPath.substringAfterLast('/')
        var target = File(restoredDir, originalName)
        if (target.exists()) {
            target = File(restoredDir, "${System.currentTimeMillis()}_$originalName")
        }

        val obfuscated = quarantineFile.readBytes()
        target.writeBytes(xorTransform(obfuscated))
        quarantineFile.delete()

        db.markQuarantineRestored(id)
        db.logEvent("info", "Ripristinato dalla quarantena: ${target.name}")
        return target.absolutePath
    }

    fun deletePermanently(id: Long) {
        val item = db.getQuarantineItem(id) ?: throw IllegalArgumentException("Elemento in quarantena non trovato")
        File(item.quarantinePath).delete()
        db.deleteQuarantineRecord(id)
        db.logEvent("info", "Eliminato definitivamente: ${item.originalPath.substringAfterLast('/')}")
    }
}
