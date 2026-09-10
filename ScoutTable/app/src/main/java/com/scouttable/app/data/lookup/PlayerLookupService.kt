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
 *
 * Statistiche di carriera (presenze/punteggio/assist/competizione):
 * - Calcio: sommate dall'infobox Wikipedia ([WikipediaCareerStats]) — Transfermarkt non è
 *   utilizzabile, le sue statistiche sono caricate via JavaScript e non raggiungibili con una
 *   richiesta HTTP semplice.
 * - Basket: lette dalla pagina Proballers ([ProballersCareerStats]) il cui URL va incollato
 *   dall'utente (nessuna ricerca pubblica per nome disponibile su Proballers).
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

    /**
     * @param extraUrl per il basket, l'URL della pagina Proballers del giocatore (opzionale ma
     *   necessario per ottenere presenze/punteggio/assist/competizione).
     * @param expectedYear anno di nascita per disambiguare omonimi (es. "Francesco Totti" 1976).
     *   Se assente viene estratto automaticamente da un eventuale numero a 4 cifre in coda a [name].
     */
    suspend fun lookup(
        name: String,
        sport: Sport,
        existingId: String? = null,
        extraUrl: String? = null,
        expectedYear: Int? = null,
    ): LookupResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val (cleanName, parsedYear) = splitTrailingYear(name)
                val yearHint = expectedYear ?: parsedYear

                val encodedName = URLEncoder.encode(cleanName, "UTF-8")
                val searchJson = getJson("$BASE/searchplayers.php?p=$encodedName")
                val candidates = searchJson?.optJSONArray("player")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext LookupResult.NotFound(cleanName)
                }

                val wantedSport = if (sport == Sport.BASKET) "Basketball" else "Soccer"
                val sportMatches = (0 until candidates.length())
                    .map { candidates.getJSONObject(it) }
                    .filter { it.optString("strSport").equals(wantedSport, ignoreCase = true) }

                // Se ci sono più omonimi dello stesso sport, l'anno di nascita sceglie quello giusto.
                val chosen = if (yearHint != null) {
                    sportMatches.firstOrNull { it.optString("dateBorn").take(4).toIntOrNull() == yearHint }
                } else null

                val player = chosen ?: sportMatches.firstOrNull() ?: candidates.getJSONObject(0)

                val anno = player.optString("dateBorn").take(4).toIntOrNull() ?: 0
                val nazione = player.optString("strNationality")
                val rawTeam = player.optString("strTeam")
                // TheSportsDB usa una squadra placeholder ("_Retired Soccer"/"_Retired Basketball")
                // al posto di un vero club per i giocatori ritirati: non è un nome di club reale.
                val isRetiredPlaceholder = rawTeam.startsWith("_Retired", ignoreCase = true)
                val club = if (isRetiredPlaceholder) "" else rawTeam
                val stato = if (isRetiredPlaceholder) PlayerStatus.RITIRATO else mapStatus(player.optString("strStatus"))
                val teamInfo = if (club.isNotBlank()) fetchTeamInfo(club) else null
                val resolvedName = player.optString("strPlayer").ifBlank { cleanName }
                val ruolo = player.optString("strPosition")

                var presenze = 0
                var punteggio = 0
                var assist = 0
                var competizione = teamInfo?.league.orEmpty()

                if (sport == Sport.CALCIO) {
                    WikipediaCareerStats.fetchClubCareerTotals(resolvedName)?.let {
                        presenze = it.presenze
                        punteggio = it.punteggio
                    }
                } else if (!extraUrl.isNullOrBlank()) {
                    ProballersCareerStats.fetchCareerTotals(extraUrl)?.let {
                        presenze = it.presenze
                        punteggio = it.punteggio
                        assist = it.assist
                        if (it.competizione.isNotBlank()) competizione = it.competizione
                    }
                }

                LookupResult.Found(
                    PlayerImportRow(
                        id = existingId,
                        nome = resolvedName,
                        anno = anno,
                        carrieraMigliore = club,
                        stato = stato,
                        nazione = nazione,
                        logoPath = teamInfo?.badge,
                        ruolo = ruolo,
                        presenze = presenze,
                        punteggio = punteggio,
                        assist = assist,
                        competizione = competizione,
                        proballersUrl = if (sport == Sport.BASKET) extraUrl else null,
                    )
                )
            }.getOrElse { LookupResult.Error(name, it.message ?: "errore sconosciuto") }
        }

    /** "Francesco Totti 1976" -> ("Francesco Totti", 1976). Nessun numero finale -> anno null. */
    private fun splitTrailingYear(raw: String): Pair<String, Int?> {
        val trimmed = raw.trim()
        val match = Regex("""^(.*\S)\s+((?:18|19|20)\d{2})$""").find(trimmed) ?: return trimmed to null
        return match.groupValues[1].trim() to match.groupValues[2].toInt()
    }

    private data class TeamInfo(val badge: String?, val league: String)

    // Il logo del club è il campo su cui l'utente ha insistito di più: un retry silenzioso
    // assorbe i blip temporanei della chiave di test gratuita di TheSportsDB (rate limit basso).
    private fun fetchTeamInfo(teamName: String): TeamInfo? =
        fetchTeamInfoOnce(teamName) ?: fetchTeamInfoOnce(teamName)

    private fun fetchTeamInfoOnce(teamName: String): TeamInfo? = runCatching {
        val encoded = URLEncoder.encode(teamName, "UTF-8")
        val json = getJson("$BASE/searchteams.php?t=$encoded")
        val team = json?.optJSONArray("teams")?.let { if (it.length() > 0) it.getJSONObject(0) else null }
            ?: return@runCatching null
        val badge = team.optString("strBadge").ifBlank { null } ?: team.optString("strLogo").ifBlank { null }
        TeamInfo(badge, team.optString("strLeague"))
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
