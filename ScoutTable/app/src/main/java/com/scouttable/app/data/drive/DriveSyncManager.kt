package com.scouttable.app.data.drive

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.scouttable.app.data.ScoutTableDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Backup/ripristino del database locale su Google Drive (cartella nascosta appDataFolder,
 * visibile solo a questa app). Richiede che l'utente configuri un OAuth Client ID nella
 * Google Cloud Console del proprio account (vedi README): questa classe è pronta all'uso
 * ma senza quelle credenziali il Sign-In fallirà con ApiException.
 */
object DriveSyncManager {

    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val BACKUP_FILE_NAME = "scouttable_backup.db"
    private val client = OkHttpClient()

    fun signInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun lastSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    private suspend fun accessToken(context: Context, account: GoogleSignInAccount): String =
        withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(context, account.account!!, "oauth2:$DRIVE_SCOPE")
        }

    /** Carica il file locale del database su Drive (appDataFolder), sovrascrivendo il backup precedente. */
    suspend fun backup(context: Context, account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = accessToken(context, account)
            val dbFile = context.getDatabasePath(ScoutTableDatabase.DB_NAME)
            require(dbFile.exists()) { "Nessun dato locale da salvare." }

            val existingId = findBackupFileId(token)
            if (existingId == null) {
                uploadNewFile(token, dbFile)
            } else {
                updateExistingFile(token, existingId, dbFile)
            }
        }
    }

    /**
     * Sincronizzazione automatica all'apertura: se l'utente ha già effettuato l'accesso e il
     * database locale è ancora vuoto (primo avvio su un nuovo dispositivo), ripristina in modo
     * silenzioso l'ultimo backup. Non sovrascrive mai dati locali già presenti.
     */
    suspend fun syncOnLaunchIfNeeded(context: Context): String? {
        val account = lastSignedInAccount(context) ?: return null
        val dbFile = context.getDatabasePath(ScoutTableDatabase.DB_NAME)
        if (dbFile.exists() && dbFile.length() > 0) return null

        val result = restore(context, account)
        return if (result.isSuccess) "Dati ripristinati automaticamente da Google Drive." else null
    }

    /** Scarica l'ultimo backup da Drive e sovrascrive il database locale (riavviare l'app dopo). */
    suspend fun restore(context: Context, account: GoogleSignInAccount): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = accessToken(context, account)
            val fileId = findBackupFileId(token) ?: error("Nessun backup trovato su Drive.")

            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download fallito: ${response.code}")
                val dbFile = context.getDatabasePath(ScoutTableDatabase.DB_NAME)
                dbFile.parentFile?.mkdirs()
                response.body!!.byteStream().use { input ->
                    dbFile.outputStream().use { output -> input.copyTo(output) }
                }
                Unit
            }
        }
    }

    private fun findBackupFileId(token: String): String? {
        val request = Request.Builder()
            .url(
                "https://www.googleapis.com/drive/v3/files" +
                    "?spaces=appDataFolder&q=name='$BACKUP_FILE_NAME'&fields=files(id,name)"
            )
            .header("Authorization", "Bearer $token")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body!!.string())
            val files = json.optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).getString("id")
        }
    }

    private fun uploadNewFile(token: String, dbFile: File) {
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("parents", listOf("appDataFolder"))
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addPart(MultipartBody.Part.create(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType())))
            .addPart(MultipartBody.Part.create(dbFile.asRequestBody("application/octet-stream".toMediaType())))
            .build()

        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Upload fallito: ${response.code}")
        }
    }

    private fun updateExistingFile(token: String, fileId: String, dbFile: File) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(dbFile.asRequestBody("application/octet-stream".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Aggiornamento backup fallito: ${response.code}")
        }
    }
}
