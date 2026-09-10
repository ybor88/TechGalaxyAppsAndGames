package com.scouttable.app.data.lookup

import com.scouttable.app.data.PlayerStatus
import com.scouttable.app.data.Sport
import com.scouttable.app.data.importexport.PlayerImportRow
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * - Basket: stimate automaticamente dall'infobox Wikipedia ([WikipediaBasketballStats]); se
 *   l'utente incolla anche l'URL della pagina Proballers del giocatore, quei dati (più precisi,
 *   stagione per stagione) hanno la precedenza ([ProballersCareerStats]).
 */
object PlayerLookupService {

    private const val API_KEY = "123"
    private const val BASE = "https://www.thesportsdb.com/api/v1/json/$API_KEY"

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
                val rawPosition = player.optString("strPosition")
                // TheSportsDB usa una squadra placeholder ("_Retired Soccer"/"_Retired Basketball")
                // al posto di un vero club per i giocatori ritirati: non è un nome di club reale.
                // Per chi ha smesso di giocare ed è diventato allenatore/manager (es. Hernán Crespo),
                // TheSportsDB tiene un solo profilo e lo aggiorna al ruolo attuale (strPosition =
                // "Manager"/"Coach", strTeam = la squadra che allena oggi): va trattato come i
                // ritirati, ignorando squadra/stato attuali, per ottenere il profilo da GIOCATORE
                // (carriera e club recuperati da Wikipedia) e non quello da allenatore.
                val isCoachProfile = rawPosition.contains("manager", ignoreCase = true) ||
                    rawPosition.contains("coach", ignoreCase = true)
                val isRetiredPlaceholder = rawTeam.startsWith("_Retired", ignoreCase = true) || isCoachProfile
                val stato = if (isRetiredPlaceholder) PlayerStatus.RITIRATO else mapStatus(player.optString("strStatus"))
                val resolvedName = player.optString("strPlayer").ifBlank { cleanName }
                val ruolo = if (isCoachProfile) "" else rawPosition

                var club = if (isRetiredPlaceholder) "" else rawTeam
                var teamInfo = if (club.isNotBlank()) fetchTeamInfo(club) else null
                var presenze = 0
                var punteggio = 0
                var assist = 0

                if (sport == Sport.CALCIO) {
                    WikipediaCareerStats.fetchClubCareerTotals(resolvedName)?.let {
                        // Somma di TUTTA la carriera (tutti i club, non solo il migliore): vedi
                        // WikipediaCareerStats, che somma caps1+caps2+... su ogni club elencato.
                        presenze = it.presenze
                        punteggio = it.punteggio
                        // "Carriera migliore" = il club con più presenze in assoluto, sempre: non
                        // solo per i ritirati. Per un giocatore attivo (es. Cristiano Ronaldo ad
                        // Al-Nassr) il club ATTUALE di TheSportsDB non è necessariamente quello
                        // dove ha reso di più in carriera (es. Real Madrid); Wikipedia lo sostituisce
                        // ogni volta che lo trova, indipendentemente dallo stato del giocatore.
                        if (!it.club.isNullOrBlank()) {
                            club = it.club
                            teamInfo = fetchTeamInfo(club)
                        }
                    }
                } else {
                    // Automatico da Wikipedia (nessun link da incollare), come per il calcio.
                    WikipediaBasketballStats.fetch(resolvedName)?.let {
                        presenze = it.presenze
                        punteggio = it.punteggio
                        assist = it.assist
                        if (!it.squadra.isNullOrBlank()) {
                            club = it.squadra
                            teamInfo = fetchTeamInfo(club)
                        }
                        if (!it.competizione.isNullOrBlank()) {
                            teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                        }
                    }
                    // Se l'utente ha incollato un URL Proballers, i suoi dati (più precisi,
                    // stagione per stagione) hanno la precedenza su quelli stimati da Wikipedia.
                    if (!extraUrl.isNullOrBlank()) {
                        ProballersCareerStats.fetchCareerTotals(extraUrl)?.let {
                            presenze = it.presenze
                            punteggio = it.punteggio
                            assist = it.assist
                            if (!it.squadraPrincipale.isNullOrBlank()) {
                                club = it.squadraPrincipale
                                teamInfo = fetchTeamInfo(club)
                            }
                            if (it.competizione.isNotBlank()) {
                                teamInfo = TeamInfo(teamInfo?.badge, it.competizione)
                            }
                        }
                    }
                }

                // Stemma del club al primo posto (è quello associato alla "carriera migliore"
                // mostrata); se manca, foto reale del giocatore da Wikipedia; se manca pure quella,
                // la UI mostra un avatar generato (mai una riga senza immagine, come richiesto).
                val fotoGiocatore = if (teamInfo?.badge.isNullOrBlank()) WikipediaPlayerPhoto.fetch(resolvedName) else null

                LookupResult.Found(
                    PlayerImportRow(
                        id = existingId,
                        nome = resolvedName,
                        anno = anno,
                        carrieraMigliore = club,
                        stato = stato,
                        nazione = nazione,
                        logoPath = teamInfo?.badge ?: fotoGiocatore,
                        ruolo = ruolo,
                        presenze = presenze,
                        punteggio = punteggio,
                        assist = assist,
                        competizione = teamInfo?.league.orEmpty(),
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
        lookupHttpClient.newCall(request).execute().use { response ->
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
