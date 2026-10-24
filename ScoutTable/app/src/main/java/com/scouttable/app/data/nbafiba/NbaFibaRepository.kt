// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.nbafiba

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Una partita simulata registrata in "NBA FIBA Champions". */
data class NbaFibaMatch(
    val id: String,
    val playedAt: Long,
    val homeName: String,
    val homeLogo: String?,
    val homeScore: Int,
    val awayName: String,
    val awayLogo: String?,
    val awayScore: Int,
    /** true se la partita è finita ai supplementari. */
    val overtime: Boolean,
)

/**
 * Partite di "Simula partita" (solo basket) registrate scegliendo "NBA FIBA Champions": un file
 * JSON nella memoria dell'app, dalla più recente. Il nome inizia con "competitions_", così è già
 * incluso nel backup su Drive (vedi BackupManager) senza altre modifiche.
 */
class NbaFibaRepository(context: Context) {

    private val file = File(context.applicationContext.filesDir, "competitions_nbafiba_matches.json")

    suspend fun load(): List<NbaFibaMatch> = withContext(Dispatchers.IO) {
        val array = runCatching { JSONArray(file.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                NbaFibaMatch(
                    id = o.getString("id"),
                    playedAt = o.optLong("playedAt"),
                    homeName = o.getString("homeName"),
                    homeLogo = o.optString("homeLogo").ifBlank { null },
                    homeScore = o.getInt("homeScore"),
                    awayName = o.getString("awayName"),
                    awayLogo = o.optString("awayLogo").ifBlank { null },
                    awayScore = o.getInt("awayScore"),
                    overtime = o.optBoolean("overtime"),
                )
            }.getOrNull()
        }.sortedByDescending { it.playedAt }
    }

    /** Registra una partita e restituisce l'elenco aggiornato. */
    suspend fun add(
        homeName: String, homeLogo: String?, homeScore: Int,
        awayName: String, awayLogo: String?, awayScore: Int,
        overtime: Boolean,
    ): List<NbaFibaMatch> {
        val match = NbaFibaMatch(
            UUID.randomUUID().toString(), System.currentTimeMillis(),
            homeName, homeLogo, homeScore, awayName, awayLogo, awayScore, overtime,
        )
        val updated = listOf(match) + load()
        save(updated)
        return updated
    }

    /** Elimina una partita e restituisce l'elenco aggiornato. */
    suspend fun delete(id: String): List<NbaFibaMatch> {
        val updated = load().filterNot { it.id == id }
        save(updated)
        return updated
    }

    private suspend fun save(matches: List<NbaFibaMatch>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        matches.forEach { m ->
            array.put(
                JSONObject()
                    .put("id", m.id)
                    .put("playedAt", m.playedAt)
                    .put("homeName", m.homeName)
                    .put("homeLogo", m.homeLogo ?: "")
                    .put("homeScore", m.homeScore)
                    .put("awayName", m.awayName)
                    .put("awayLogo", m.awayLogo ?: "")
                    .put("awayScore", m.awayScore)
                    .put("overtime", m.overtime)
            )
        }
        file.writeText(array.toString())
    }
}
