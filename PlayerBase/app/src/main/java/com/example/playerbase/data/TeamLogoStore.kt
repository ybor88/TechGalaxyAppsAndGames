package com.example.playerbase.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Salva in modo permanente (filesDir/team_logos) il logo squadra caricato
 * dall'utente, indicizzato per nome squadra normalizzato: un giocatore con lo
 * stesso Max Team di un altro riusa automaticamente lo stesso logo caricato,
 * senza doverlo ricaricare ogni volta.
 */
class TeamLogoStore(context: Context) {

    private val dir = File(context.filesDir, "team_logos").apply { mkdirs() }
    private val resolver = context.contentResolver

    fun getLogoFile(teamName: String): File? =
        fileFor(teamName)?.takeIf { it.exists() }

    fun saveLogo(teamName: String, sourceUri: Uri): Boolean {
        val file = fileFor(teamName) ?: return false
        return try {
            resolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
    }

    fun removeLogo(teamName: String) {
        fileFor(teamName)?.takeIf { it.exists() }?.delete()
    }

    private fun fileFor(teamName: String): File? {
        val key = teamName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        if (key.isBlank()) return null
        return File(dir, "$key.logo")
    }
}
