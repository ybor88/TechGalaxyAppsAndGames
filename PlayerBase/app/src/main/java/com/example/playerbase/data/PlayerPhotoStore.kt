package com.example.playerbase.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Salva in modo permanente (filesDir/player_photos) la foto profilo caricata
 * dall'utente per un giocatore, indicizzata per id giocatore. Quando presente,
 * sostituisce l'avatar disegnato ovunque venga mostrata l'immagine del giocatore.
 */
class PlayerPhotoStore(context: Context) {

    private val dir = File(context.filesDir, "player_photos").apply { mkdirs() }
    private val resolver = context.contentResolver

    fun getPhotoFile(playerId: String): File? =
        fileFor(playerId).takeIf { it.exists() }

    fun savePhoto(playerId: String, sourceUri: Uri): Boolean {
        val file = fileFor(playerId)
        return try {
            resolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
    }

    fun removePhoto(playerId: String) {
        fileFor(playerId).takeIf { it.exists() }?.delete()
    }

    private fun fileFor(playerId: String): File = File(dir, "$playerId.photo")
}
