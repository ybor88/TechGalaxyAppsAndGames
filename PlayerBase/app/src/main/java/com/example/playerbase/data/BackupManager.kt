package com.example.playerbase.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup completo in un unico file .zip: elenco giocatori (CSV) più le
 * immagini caricate (foto profilo e loghi squadra). Usato per spostare tutto
 * il database — dati e immagini — su un altro dispositivo o come copia di
 * sicurezza, dato che l'export/import solo CSV non porterebbe con sé le foto.
 */
class BackupManager(private val context: Context) {

    private val playerRepo = PlayerCsvRepository(context)
    private val photosDir = File(context.filesDir, "player_photos").apply { mkdirs() }
    private val logosDir = File(context.filesDir, "team_logos").apply { mkdirs() }
    private val logoAssignDir = File(context.filesDir, "team_logo_assignments").apply { mkdirs() }

    fun exportTo(targetUri: Uri, players: List<Player>): Boolean = try {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("players.csv"))
                zip.write(playerRepo.toCsv(players).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                writeDir(zip, photosDir, "player_photos/")
                // team_logos/ ora contiene una sottocartella per squadra (storico stemmi):
                // serve ricorsione, non solo i file diretti, altrimenti l'export non
                // porta con sé nessuno stemma caricato dopo l'introduzione dello storico.
                writeDir(zip, logosDir, "team_logos/")
                writeDir(zip, logoAssignDir, "team_logo_assignments/")
            }
        } != null
    } catch (e: Exception) {
        false
    }

    private fun writeDir(zip: ZipOutputStream, dir: File, prefix: String) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                writeDir(zip, file, "$prefix${file.name}/")
            } else if (file.isFile) {
                zip.putNextEntry(ZipEntry(prefix + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Ritorna i giocatori importati (dati + immagini ripristinate), o null se il file non è un backup valido. */
    fun importFrom(sourceUri: Uri): List<Player>? = try {
        var importedPlayers: List<Player>? = null
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "players.csv" -> {
                            importedPlayers = playerRepo.fromCsv(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        !entry.isDirectory && name.startsWith("player_photos/") -> {
                            File(photosDir, name.removePrefix("player_photos/")).outputStream().use { zip.copyTo(it) }
                        }
                        !entry.isDirectory && name.startsWith("team_logos/") -> {
                            // Lo storico stemmi è annidato in team_logos/<squadra>/<file>:
                            // va ricreata la sottocartella prima di scrivere il file.
                            val target = File(logosDir, name.removePrefix("team_logos/"))
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        !entry.isDirectory && name.startsWith("team_logo_assignments/") -> {
                            File(logoAssignDir, name.removePrefix("team_logo_assignments/")).outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        importedPlayers
    } catch (e: Exception) {
        null
    }
}
