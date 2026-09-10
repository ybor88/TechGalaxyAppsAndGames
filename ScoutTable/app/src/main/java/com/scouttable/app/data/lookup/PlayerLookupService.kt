package com.scouttable.app.data.lookup

import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Recupera automaticamente le informazioni di un giocatore da TheSportsDB (API pubblica gratuita,
 * chiave di test "123") dato solo il nome: sostituisce l'import manuale da file.
 * Funziona sia per il calcio (strSport = "Soccer") sia per il basket (strSport = "Basketball").
 * Best-effort: alcuni giocatori poco noti potrebbero non essere trovati.
 */
object PlayerLookupService {

    private const val API_KEY = "123"
    private const val BASE = "https://www.thesportsdb.com/api/v1/json/$API_KEY"
    private val client = OkHttpClient()

    sealed class LookupResult {
        data class Found(val row: PlayerImportRow) : LookupResult()
        data class NotFound(val query: String) : LookupResult()
        data class Error(val query: String, val message: String) : LookupResult()
    }

    suspend fun lookup(name: String, sport: Sport, existingId: String? = null): LookupResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedName = URLEncoder.encode(name.trim(), "UTF-8")
                val searchJson = getJson("$BASE/searchplayers.php?p=$encodedName")
                val candidates = searchJson?.optJSONArray("player")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext LookupResult.NotFound(name)
                }

                val wantedSport = if (sport == Sport.BASKET) "Basketball" else "Soccer"
                var chosen: JSONObject? = null
                for (i in 0 until candidates.length()) {
                    val candidate = candidates.getJSONObject(i)
                    if (candidate.optString("strSport").equals(wantedSport, ignoreCase = true)) {
                        chosen = candidate
                        break
                    }
                }
                val player = chosen ?: candidates.getJSONObject(0)

                val anno = player.optString("dateBorn").take(4).toIntOrNull() ?: 0
                val nazione = player.optString("strNationality")
                val rawTeam = player.optString("strTeam")
                // TheSportsDB usa una squadra placeholder ("_Retired Soccer"/"_Retired Basketball")
                // al posto di un vero club per i giocatori ritirati: non è un nome di club reale.
                val isRetiredPlaceholder = rawTeam.startsWith("_Retired", ignoreCase = true)
                val club = if (isRetiredPlaceholder) "" else rawTeam
                val stato = if (isRetiredPlaceholder) PlayerStatus.RITIRATO else mapStatus(player.optString("strStatus"))
                val logo = if (club.isNotBlank()) fetchTeamBadge(club) else null

                LookupResult.Found(
                    PlayerImportRow(
                        id = existingId,
                        nome = player.optString("strPlayer").ifBlank { name },
                        anno = anno,
                        carrieraMigliore = club,
                        stato = stato,
                        nazione = nazione,
                        logoPath = logo,
                    )
                )
            }.getOrElse { LookupResult.Error(name, it.message ?: "errore sconosciuto") }
        }

    private fun fetchTeamBadge(teamName: String): String? = runCatching {
        val encoded = URLEncoder.encode(teamName, "UTF-8")
        val json = getJson("$BASE/searchteams.php?t=$encoded")
        val team = json?.optJSONArray("teams")?.let { if (it.length() > 0) it.getJSONObject(0) else null }
            ?: return@runCatching null
        team.optString("strBadge").ifBlank { null } ?: team.optString("strLogo").ifBlank { null }
    }.getOrNull()

    private fun getJson(url: String): JSONObject? {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()
            if (body.isNullOrBlank() || body == "null") return null
            return JSONObject(body)
        }
    }

    private fun mapStatus(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("retire") -> "Ritirato"
            lower.contains("injur") -> "Infortunato"
            lower.contains("free") -> "Svincolato"
            else -> "Attivo"
        }
    }
}
