package com.scouttable.app.data.drive

import android.content.Context
import android.net.Uri
import com.scouttable.app.data.ScoutTableDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup/ripristino del database locale come semplice file, tramite il selettore di sistema
 * Android (Storage Access Framework): l'utente sceglie "Google Drive" come cartella di
 * destinazione/origine esattamente come farebbe salvando una foto o un PDF da qualsiasi app.
 * Niente Sign-In, niente credenziali OAuth da configurare in Google Cloud Console: quella strada
 * (rimossa) richiedeva un client ID registrato per pacchetto+SHA-1 ed era la causa dell'errore
 * "codice 10" (DEVELOPER_ERROR) segnalato dall'utente.
 */
object BackupManager {

    const val BACKUP_MIME_TYPE = "application/octet-stream"
    const val BACKUP_FILE_NAME = "scouttable_backup.db"

    /** Copia il database locale nella posizione scelta dall'utente (es. una cartella su Drive). */
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
            context.contentResolver.openOutputStream(destination)?.use { output ->
                dbFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Impossibile scrivere nel file scelto.")
            Unit
        }
    }

    /**
     * Sovrascrive il database locale con il file scelto dall'utente (es. un backup precedente
     * salvato su Drive). Richiede di riavviare l'app per vedere i dati ripristinate.
     */
    suspend fun importFrom(context: Context, source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Chiude la connessione Room corrente prima di toccare il file: altrimenti la
            // connessione già aperta continuerebbe a usare pagine/cache del database precedente
            // anche dopo averlo sovrascritto (visibile senza nemmeno riavviare il processo).
            ScoutTableDatabase.closeAndReset()
            val dbFile = context.getDatabasePath(ScoutTableDatabase.DB_NAME)
            dbFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(source)?.use { input ->
                dbFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Impossibile leggere il file scelto.")
            // File -wal/-shm residui della sessione precedente andrebbero letti insieme al file
            // principale, non a quello appena importato: rimuoverli forza Room a ripartire da un
            // DB pulito (il file importato, checkpointato in fase di export, è già autosufficiente).
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            Unit
        }
    }
}
