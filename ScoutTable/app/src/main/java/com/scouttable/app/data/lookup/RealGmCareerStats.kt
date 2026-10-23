// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import kotlin.math.roundToInt
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Terza fonte per il basket, dopo Proballers e Wikipedia: la pagina giocatore di RealGM (es.
 * https://basketball.realgm.com/player/Andrea-Meneghin/Summary/3051). Restituisce gli stessi
 * [ProballersTotals] di [ProballersCareerStats], così in [PlayerLookupService] valgono le stesse
 * regole (precedenza sulle altre fonti, stato dall'ultima stagione, stemma di ripiego).
 *
 * Struttura verificata sulla pagina di Andrea Meneghin:
 * - "div.profile-box": nome e ruolo nell'h2 ("Andrea Meneghin SG #11"), poi coppie
 *   "Born:"/"Nationality:" in chiaro;
 * - tabelle stagione per stagione in contenitori "tabs_<sezione>_reg-N": N = 3 sono i TOTALI
 *   (Season/Age/Team/League/GP/GS/MIN/PTS/FGM/FGA/FG%/3PM/3PA/3P%/FTM/FTA/FT%/OFF/DEF/TRB/AST/STL/...),
 *   N = 1 le medie. Una stagione con più squadre ha una riga "All Teams" riassuntiva (scartata)
 *   seguita dalle righe delle singole squadre;
 * - giocatori di college: sezione "tabs_ncaa_reg-3" con la colonna "School" al posto di "Team"
 *   e la divisione ("NCAA DIII") nella riga "CAREER" del piede tabella;
 * - "Olympic Events Stats"/"FIBA Senior Team Events Stats": Nazionale, con una riga "TOTAL".
 *
 * Copertura: RealGM non ha tutte le stagioni dei giocatori più datati (per Meneghin solo dal
 * 2000-01), per questo resta un'opzione dopo Proballers e Wikipedia.
 */
object RealGmCareerStats {

    private const val CHALLENGE_MARKER = "Just a moment"
    private val realGmUrlRegex = Regex("""realgm\.com/player/([^/?#\s]+)/[^/?#\s]+/(\d+)""", RegexOption.IGNORE_CASE)
    private val totalsContainerRegex = Regex("""^tabs_(\w+?)_reg-3$""")
    private val yearRegex = Regex("""(?:19|20)\d{2}""")

    /** Qualsiasi scheda del giocatore (Summary/Stats/International...) -> pagina "Summary". */
    fun canonicalProfileUrl(rawUrl: String): String {
        val m = realGmUrlRegex.find(rawUrl.trim()) ?: return rawUrl.trim()
        return "https://basketball.realgm.com/player/${m.groupValues[1]}/Summary/${m.groupValues[2]}"
    }

    suspend fun fetchCareerTotals(rawProfileUrl: String): ProballersTotals? = runCatching {
        val profileUrl = canonicalProfileUrl(rawProfileUrl)
        val html = fetchHtml(profileUrl) ?: return@runCatching null
        parse(html, profileUrl)
    }.getOrNull()

    /** Separata dalla rete per i test. */
    internal fun parse(html: String, profileUrl: String): ProballersTotals? {
        val doc = Jsoup.parse(html, profileUrl)

        val club = sumTotalsTables(doc)
        val national = sumNationalTables(doc)
        if (club.games == 0 && national.games == 0) return null

        val profile = doc.selectFirst("div.profile-box")
        // Coppie "<p><strong>Born:</strong> <a>Feb 20, 1974</a> ...</p>".
        fun field(label: String): String? = profile?.select("p")
            ?.firstOrNull { it.selectFirst("strong")?.text()?.trim()?.removeSuffix(":") == label }
            ?.let { p -> p.text().substringAfter(":").trim() }?.ifBlank { null }
        val annoNascita = field("Born")?.let { yearRegex.find(it)?.value?.toIntOrNull() }
        val nazione = field("Nationality")?.let { countryFromDemonym(it) ?: it }
        // Ruolo nell'h2 dopo il nome, come sigla in "span.feature" ("SG", "PG", "C", "G-F"...).
        val posizione = profile?.select("h2 span.feature")?.map { it.text().trim().lowercase() }
            ?.firstNotNullOfOrNull { proballersPositionAbbrev[it.split('-', '/').first()] }
        // Stemma della squadra attuale/ultima a fianco del profilo: usato solo se è davvero la
        // squadra principale (il nome del file è la città/il club, es. ".../varese.png" per
        // "OpenJobMetis Varese"), altrimenti si lascia il ripiego TheSportsDB/Wikipedia.
        val logoUrl = profile?.select("img[src*=team_logos]")?.firstOrNull()?.attr("abs:src")?.ifBlank { null }
        val logoKey = logoUrl?.substringAfterLast('/')?.substringBefore('.')?.let(::normalizeForMatch).orEmpty()
        val topTeamLogo = logoUrl?.takeIf {
            logoKey.length >= 4 && club.topTeam != null && normalizeForMatch(club.topTeam).split(' ').any { it == logoKey || logoKey.contains(it) && it.length >= 4 }
        }

        return ProballersTotals(
            presenze = club.games,
            punteggio = club.points,
            assist = club.assists,
            competizione = club.topLeague,
            squadraPrincipale = club.topTeam,
            squadraPrincipaleLogoUrl = topTeamLogo,
            nazione = nazione,
            annoNascita = annoNascita,
            posizione = posizione,
            presenzeNazionale = national.games,
            punteggioNazionale = national.points,
            assistNazionale = national.assists,
            rimbalzi = club.rebounds,
            rimbalziNazionale = national.rebounds,
            minutiCarriera = club.minutes,
            palleRecuperate = club.steals,
            percentualeTiriDaDue = club.twoPct,
            percentualeTiriDaTre = club.threePct,
            ultimaStagione = club.lastSeason,
        )
    }

    /** Come Proballers: richiesta diretta, e se Cloudflare la blocca la WebView. */
    private suspend fun fetchHtml(url: String): String? {
        val direct = runCatching {
            val request = Request.Builder().url(url).withBrowserHeaders().build()
            lookupHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
        if (!direct.isNullOrBlank() && !direct.contains(CHALLENGE_MARKER)) return direct
        val context = AppContext.contextOrNull ?: return null
        return ProballersWebViewFetcher.fetchHtml(context, url)
    }

    private data class ClubTotals(
        val games: Int = 0,
        val points: Int = 0,
        val assists: Int = 0,
        val rebounds: Int = 0,
        val steals: Int = 0,
        val minutes: Int = 0,
        val twoPct: Int = 0,
        val threePct: Int = 0,
        val topTeam: String? = null,
        val topLeague: String = "",
        val lastSeason: Int? = null,
    )

    /** Indici delle colonne dall'intestazione: la tabella NBA può non avere la colonna League. */
    private fun columns(table: Element): Map<String, Int> =
        table.select("thead th").mapIndexed { i, th -> th.text().trim().uppercase() to i }.toMap()

    private fun Element.cellNumber(cols: Map<String, Int>, name: String): Double? {
        val i = cols[name] ?: return null
        return select("> td").getOrNull(i)?.text()?.trim()?.replace(",", "")?.toDoubleOrNull()
    }

    private fun Element.cellText(cols: Map<String, Int>, name: String): String? {
        val i = cols[name] ?: return null
        return select("> td").getOrNull(i)?.text()?.trim()?.ifBlank { null }
    }

    /** Somma le tabelle TOTALI di tutte le sezioni (NBA, internazionale, G League...). */
    private fun sumTotalsTables(doc: Document): ClubTotals {
        var games = 0; var points = 0; var assists = 0; var rebounds = 0; var steals = 0; var minutes = 0.0
        var fgm = 0.0; var fga = 0.0; var tpm = 0.0; var tpa = 0.0
        var lastSeason: Int? = null
        val teamGames = mutableMapOf<String, Int>()
        val teamLeagueGames = mutableMapOf<String, MutableMap<String, Int>>()

        val containers = doc.select("[id]").toList().filter { totalsContainerRegex.matches(it.id()) }
        for (container in containers) {
            val section = totalsContainerRegex.find(container.id())!!.groupValues[1]
            // Nel DOM renderizzato dalla WebView, bootstrap-table ("data-sticky-header") può
            // aggiungere una copia della sola intestazione: si prende la tabella con le righe.
            val table = container.select("table").firstOrNull { it.select("tbody tr td").isNotEmpty() } ?: continue
            val cols = columns(table)
            if ("GP" !in cols || "PTS" !in cols) continue
            // Sezione NCAA: la lega è la divisione nella riga "CAREER" del piede ("NCAA DIII").
            val sectionLeague = table.selectFirst("tfoot tr")?.select("th")?.getOrNull(2)?.text()?.trim()
                ?.takeIf { it.isNotBlank() && it != " " }
            for (row in table.select("tbody tr")) {
                // Le tabelle NCAA hanno "School" al posto di "Team" (verificato su Stephen Behil,
                // https://basketball.realgm.com/player/Stephen-Behil/Summary/209470: senza questo
                // ripiego nessuna riga era letta e il giocatore risultava "non trovato").
                val team = row.cellText(cols, "TEAM") ?: row.cellText(cols, "SCHOOL") ?: continue
                // Riga riassuntiva di una stagione con più squadre: le singole squadre seguono.
                if (team.equals("All Teams", ignoreCase = true)) continue
                val season = row.cellText(cols, "SEASON") ?: continue
                val gp = row.cellNumber(cols, "GP")?.roundToInt() ?: continue
                if (gp <= 0) continue
                games += gp
                points += row.cellNumber(cols, "PTS")?.roundToInt() ?: 0
                assists += row.cellNumber(cols, "AST")?.roundToInt() ?: 0
                rebounds += row.cellNumber(cols, "TRB")?.roundToInt() ?: 0
                steals += row.cellNumber(cols, "STL")?.roundToInt() ?: 0
                minutes += row.cellNumber(cols, "MIN") ?: 0.0
                fgm += row.cellNumber(cols, "FGM") ?: 0.0
                fga += row.cellNumber(cols, "FGA") ?: 0.0
                tpm += row.cellNumber(cols, "3PM") ?: 0.0
                tpa += row.cellNumber(cols, "3PA") ?: 0.0
                seasonEndYear(season)?.let { end -> lastSeason = maxOf(lastSeason ?: end, end) }
                val league = row.cellText(cols, "LEAGUE") ?: when {
                    section.equals("nba", ignoreCase = true) -> "NBA"
                    section.equals("ncaa", ignoreCase = true) -> sectionLeague ?: "NCAA"
                    else -> sectionLeague.orEmpty()
                }
                teamGames[team] = (teamGames[team] ?: 0) + gp
                if (league.isNotBlank()) {
                    val byLeague = teamLeagueGames.getOrPut(team) { mutableMapOf() }
                    byLeague[league] = (byLeague[league] ?: 0) + gp
                }
            }
        }
        val topTeam = teamGames.maxByOrNull { it.value }?.key
        val topLeague = topTeam?.let { teamLeagueGames[it] }?.maxByOrNull { it.value }?.key.orEmpty()
        val twoA = fga - tpa
        return ClubTotals(
            games = games,
            points = points,
            assists = assists,
            rebounds = rebounds,
            steals = steals,
            minutes = minutes.roundToInt(),
            twoPct = if (twoA > 0) ((fgm - tpm) / twoA * 100).roundToInt() else 0,
            threePct = if (tpa > 0) (tpm / tpa * 100).roundToInt() else 0,
            topTeam = topTeam,
            topLeague = topLeague,
            lastSeason = lastSeason,
        )
    }

    private data class NationalTotals(val games: Int = 0, val points: Int = 0, val assists: Int = 0, val rebounds: Int = 0)

    /** Righe "TOTAL" delle tabelle Olimpiadi/FIBA (Nazionale maggiore). */
    private fun sumNationalTables(doc: Document): NationalTotals {
        var games = 0; var points = 0; var assists = 0; var rebounds = 0
        for (table in doc.select("table")) {
            val cols = columns(table)
            if ("EVENT" !in cols || "GP" !in cols) continue
            val total = table.select("tr").firstOrNull { it.selectFirst("td")?.text()?.trim().equals("TOTAL", ignoreCase = true) }
                ?: continue
            games += total.cellNumber(cols, "GP")?.roundToInt() ?: 0
            points += total.cellNumber(cols, "PTS")?.roundToInt() ?: 0
            assists += total.cellNumber(cols, "AST")?.roundToInt() ?: 0
            rebounds += total.cellNumber(cols, "TRB")?.roundToInt() ?: 0
        }
        return NationalTotals(games, points, assists, rebounds)
    }
}
