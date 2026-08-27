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
 *
 * Ogni giocatore che carica personalmente un logo resta agganciato a QUEL
 * file specifico (filesDir/team_logo_assignments/<id>.assignment), non al
 * "più recente per la squadra": così se un compagno di squadra carica in
 * seguito uno stemma diverso, i giocatori che avevano già scelto il proprio
 * logo continuano a mostrarlo, invece di cambiare tutti insieme.
 */
class TeamLogoStore(context: Context) {

    private val dir = File(context.filesDir, "team_logos").apply { mkdirs() }
    private val assignDir = File(context.filesDir, "team_logo_assignments").apply { mkdirs() }
    private val resolver = context.contentResolver

    /** Ultimo logo caricato per questa squadra (usato per lo storico/statistiche). */
    fun getLogoFile(teamName: String): File? = getLogoHistory(teamName).firstOrNull()

    /**
     * Logo da mostrare per QUESTO giocatore: quello caricato personalmente da
     * lui, se ancora presente; altrimenti (nessuna scelta propria) l'ultimo
     * caricato per la squadra, come default condiviso.
     */
    fun getLogoForPlayer(playerId: String, teamName: String): File? {
        val history = getLogoHistory(teamName)
        if (history.isEmpty()) return null
        val assigned = assignedFileName(playerId)
        if (assigned != null) {
            history.firstOrNull { it.name == assigned }?.let { return it }
        }
        return history.first()
    }

    /** Se questo giocatore ha scelto personalmente un logo (non solo ereditato dalla squadra). */
    fun hasPlayerAssignment(playerId: String): Boolean = assignedFileName(playerId) != null

    /** Tutti i loghi caricati nel tempo per questa squadra, dal più recente al più vecchio. */
    fun getLogoHistory(teamName: String): List<File> {
        val folder = folderFor(teamName) ?: return emptyList()
        migrateLegacyFile(teamName, folder)
        return folder.listFiles { f -> f.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /** Carica un nuovo logo per la squadra e lo assegna a questo giocatore. */
    fun saveLogo(teamName: String, playerId: String, sourceUri: Uri): Boolean {
        val folder = folderFor(teamName) ?: return false
        folder.mkdirs()
        val file = File(folder, "${System.currentTimeMillis()}.logo")
        val saved = try {
            resolver.openInputStream(sourceUri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } != null
        } catch (e: Exception) {
            false
        }
        if (saved) setAssignedFileName(playerId, file.name)
        return saved
    }

    /**
     * Rimuove la scelta personale di logo di questo giocatore (torna a
     * ereditare il default di squadra). Non tocca lo storico della squadra:
     * gli altri giocatori e le statistiche non vengono influenzati.
     */
    fun removeLogo(playerId: String) {
        assignmentFile(playerId).delete()
    }

    private fun folderFor(teamName: String): File? {
        val key = teamName.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        if (key.isBlank()) return null
        return File(dir, key)
    }

    private fun assignmentFile(playerId: String): File = File(assignDir, "$playerId.assignment")

    private fun assignedFileName(playerId: String): String? =
        assignmentFile(playerId).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotBlank() }

    private fun setAssignedFileName(playerId: String, fileName: String) {
        assignmentFile(playerId).writeText(fileName)
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
