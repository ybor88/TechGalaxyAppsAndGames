// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.drive

import android.content.Context
import android.net.Uri
import com.scouttable.app.data.ScoutTableDatabase
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup/ripristino di tutti i dati dell'app come un unico file, tramite il selettore di sistema
 * Android (Storage Access Framework): l'utente sceglie "Google Drive" come cartella di
 * destinazione/origine esattamente come farebbe salvando una foto o un PDF da qualsiasi app.
 * Niente Sign-In, niente credenziali OAuth da configurare in Google Cloud Console: quella strada
 * (rimossa) richiedeva un client ID registrato per pacchetto+SHA-1 ed era la causa dell'errore
 * "codice 10" (DEVELOPER_ERROR) segnalato dall'utente.
 *
 * Il file è uno zip con:
 * - il database (giocatori, statistiche, stelle, visionati, URL delle fonti...);
 * - le immagini scelte a mano dalla galleria (cartella "logos" della memoria interna): nel
 *   database c'è solo il loro percorso, quindi con il solo database (formato precedente) su un
 *   altro telefono sparivano;
 * - i dati della sezione "Detentori" e di "NBA vs mondo" (stessa cartella "competitions/" dello zip).
 * Gli stemmi e le foto presi da internet sono URL e si ricaricano da soli. I backup del formato
 * precedente (il solo file del database) restano importabili.
 */
object BackupManager {

    const val BACKUP_MIME_TYPE = "application/zip"
    const val BACKUP_FILE_NAME = "scouttable_backup.zip"

    private const val DB_ENTRY = "scouttable.db"
    private const val LOGOS_DIR = "logos"
    private const val LOGOS_PREFIX = "$LOGOS_DIR/"
    private const val COMPETITIONS_PREFIX = "competitions/"
    // Dati di "NBA vs mondo": salvati nella stessa cartella dello zip dei Detentori, così i backup
    // vecchi e nuovi si importano allo stesso modo.
    private const val NBA_INTL_FILE = "nba_international.json"
    // Percorso delle immagini salvate a mano, uguale su ogni dispositivo a parte la cartella
    // dell'app (es. "file:///data/user/0/com.scouttable.app/files/logos/<uuid>.jpg").
    private const val LOGOS_PATH_MARKER = "/files/logos/"

    /** Esporta database, immagini e dati "Detentori"/"NBA vs mondo" nel file scelto dall'utente (es. su Drive). */
    suspend fun exportTo(context: Context, destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath(ScoutTableDatabase.DB_NAME)
            require(dbFile.exists()) { "Nessun dato locale da esportare." }
            // Room usa write-ahead logging: le scritture più recenti (a volte anche lo schema
            // delle tabelle) possono trovarsi solo nel file -wal, non ancora nel file .db
            // principale. Un checkpoint forza a scrivere tutto nel file principale prima di
            // copiarlo, altrimenti il backup esportato può risultare incompleto o vuoto.
            // .query() su Android è lazy: il cursore prepara lo statement ma non lo esegue finché
            // non se ne legge il risultato. Senza moveToFirst() il checkpoint non scatta davvero
            // (scoperto testando l'export: il file copiato risultava di una sola pagina, vuoto).
            ScoutTableDatabase.get(context).openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val output = context.contentResolver.openOutputStream(destination) ?: error("Impossibile scrivere nel file scelto.")
            ZipOutputStream(output).use { zip ->
                zip.putFile(DB_ENTRY, dbFile)
                File(context.filesDir, LOGOS_DIR).listFiles()?.filter { it.isFile }?.forEach { zip.putFile(LOGOS_PREFIX + it.name, it) }
                context.filesDir.listFiles()?.filter { it.isFile && (it.name.startsWith("competitions_") || it.name == NBA_INTL_FILE) }
                    ?.forEach { zip.putFile(COMPETITIONS_PREFIX + it.name, it) }
            }
            Unit
        }
    }

    private fun ZipOutputStream.putFile(name: String, file: File) {
        putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }

    /**
     * Sostituisce i dati locali con quelli del backup scelto dall'utente (anche creato su un altro
     * telefono). Richiede di riavviare l'app per vedere i dati ripristinati.
     */
    suspend fun importFrom(context: Context, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(source) ?: error("Impossibile leggere il file scelto.")
            BufferedInputStream(input).use { stream ->
                stream.mark(4)
                val header = ByteArray(2).also { stream.read(it) }
                stream.reset()
                if (header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()) {
                    importZip(context, stream)
                } else {
                    // Backup del formato precedente: il solo file del database.
                    replaceDatabase(context, stream)
                }
            }
            Unit
        }
    }

    private fun importZip(context: Context, stream: InputStream) {
        // Prima si estrae tutto in una cartella temporanea: se il file non è un backup valido
        // (manca il database) i dati attuali non vengono toccati.
        val staging = File(context.cacheDir, "backup_import").apply { deleteRecursively(); mkdirs() }
        try {
            ZipInputStream(stream).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val name = entry.name
                    // Solo i nomi previsti, senza percorsi: un file con "../" non deve poter
                    // scrivere fuori dalla cartella dell'app.
                    val allowed = name == DB_ENTRY ||
                        (name.startsWith(LOGOS_PREFIX) || name.startsWith(COMPETITIONS_PREFIX)) &&
                        !name.contains("..") && name.count { it == '/' } == 1
                    if (!entry.isDirectory && allowed) {
                        val target = File(staging, name).apply { parentFile?.mkdirs() }
                        target.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            val db = File(staging, DB_ENTRY)
            require(db.exists()) { "Il file scelto non è un backup di ScoutTable." }

            db.inputStream().use { replaceDatabase(context, it) }
            File(staging, LOGOS_DIR).listFiles()?.forEach { file ->
                file.copyTo(File(File(context.filesDir, LOGOS_DIR).apply { mkdirs() }, file.name), overwrite = true)
            }
            File(staging, COMPETITIONS_PREFIX.trimEnd('/')).listFiles()?.forEach { file ->
                file.copyTo(File(context.filesDir, file.name), overwrite = true)
            }
            relinkLogoPaths(context)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun replaceDatabase(context: Context, source: InputStream) {
        // Chiude la connessione Room corrente prima di toccare il file: altrimenti la
        // connessione già aperta continuerebbe a usare pagine/cache del database precedente
        // anche dopo averlo sovrascritto (visibile senza nemmeno riavviare il processo).
        ScoutTableDatabase.closeAndReset()
        val dbFile = context.getDatabasePath(ScoutTableDatabase.DB_NAME)
        dbFile.parentFile?.mkdirs()
        dbFile.outputStream().use { output -> source.copyTo(output) }
        // File -wal/-shm residui della sessione precedente andrebbero letti insieme al file
        // principale, non a quello appena importato: rimuoverli forza Room a ripartire da un
        // DB pulito (il file importato, checkpointato in fase di export, è già autosufficiente).
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }

    /**
     * Le immagini scelte a mano sono salvate nel database con il percorso completo del file, che
     * contiene la cartella dell'app sul telefono di origine: su un altro dispositivo (o un altro
     * profilo utente) la cartella può essere diversa, quindi i percorsi vanno riscritti su quella
     * di questo dispositivo, dove le immagini sono appena state ripristinate.
     */
    private fun relinkLogoPaths(context: Context) {
        val newPrefix = Uri.fromFile(File(context.filesDir, LOGOS_DIR)).toString() + "/"
        val db = ScoutTableDatabase.get(context).openHelper.writableDatabase
        for (column in listOf("logoPath", "secondLogoPath")) {
            db.execSQL(
                "UPDATE players SET $column = ? || substr($column, instr($column, ?) + ?) WHERE instr($column, ?) > 0",
                arrayOf<Any>(newPrefix, LOGOS_PATH_MARKER, LOGOS_PATH_MARKER.length, LOGOS_PATH_MARKER),
            )
        }
        ScoutTableDatabase.closeAndReset()
    }
}
