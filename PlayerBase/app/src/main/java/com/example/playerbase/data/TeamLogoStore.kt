package com.example.playerbase.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Salva in modo permanente (filesDir/team_logos/<squadra>/) i loghi squadra
 * caricati dall'utente, indicizzati per nome squadra normalizzato: un
 * giocatore con lo stesso Max Team di un altro riusa automaticamente l'ultimo
 * logo caricato, senza doverlo ricaricare ogni volta. A differenza di un
 * singolo file, ogni upload si accumula nella cartella della squadra invece
 * di sovrascrivere il precedente: se giocatori diversi caricano stemmi
 * diversi per lo stesso nome squadra, lo storico resta disponibile (vedi
 * [getLogoHistory]) invece di andare perso.
 */
class TeamLogoStore(context: Context) {

    private val dir = File(context.filesDir, "team_logos").apply { mkdirs() }
    private val resolver = context.contentResolver

    /** Ultimo logo caricato per questa squadra (quello mostrato ovunque tranne nello storico). */
    fun getLogoFile(teamName: String): File? = getLogoHistory(teamName).firstOrNull()

    /** Tutti i loghi caricati nel tempo per questa squadra, dal più recente al più vecchio. */
    fun getLogoHistory(teamName: String): List<File> {
        val folder = folderFor(teamName) ?: return emptyList()
        migrateLegacyFile(teamName, folder)
        return folder.listFiles { f -> f.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun saveLogo(teamName: String, sourceUri: Uri): Boolean {
        val folder = folderFor(teamName) ?: return false
        folder.mkdirs()
        val file = File(folder, "${System.currentTimeMillis()}.logo")
        return try {
            resolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
    }

    /** Cancella l'intero storico dei loghi di questa squadra (non solo l'ultimo). */
    fun removeLogo(teamName: String) {
        val folder = folderFor(teamName) ?: return
        folder.listFiles()?.forEach { it.delete() }
    }

    private fun folderFor(teamName: String): File? {
        val key = teamName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        if (key.isBlank()) return null
        return File(dir, key)
    }

    /** Importa il logo salvato dalla vecchia versione (un file per squadra) nel nuovo storico. */
    private fun migrateLegacyFile(teamName: String, folder: File) {
        val key = teamName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        if (key.isBlank()) return
        val legacyFile = File(dir, "$key.logo")
        if (!legacyFile.exists()) return
        folder.mkdirs()
        legacyFile.copyTo(File(folder, "${legacyFile.lastModified()}.logo"), overwrite = true)
        legacyFile.delete()
    }
}
