package com.scouttable.app.data.lookup

import kotlin.math.roundToInt
import okhttp3.Request
import org.jsoup.Jsoup

data class ProballersTotals(
    val presenze: Int,
    val punteggio: Int,
    val assist: Int,
    val competizione: String,
    /** Squadra in cui ha giocato più partite: usata anche per i ritirati, che su TheSportsDB
     * non hanno più un club reale associato. */
    val squadraPrincipale: String?,
    val nazione: String? = null,
    val annoNascita: Int? = null,
    /** Ruolo grezzo in inglese (es. "Point Guard"): va tradotto con [translateRole]. */
    val posizione: String? = null,
    /** Presenze/punti/assist con la Nazionale (sezione "International competitions" della stessa
     * pagina: Olimpiadi/EuroBasket/Mondiali, es. "USA"/"Olympics" per Michael Jordan 1984/1992). */
    val presenzeNazionale: Int = 0,
    val punteggioNazionale: Int = 0,
    val assistNazionale: Int = 0,
    /** Rimbalzi totali di club, dalla colonna "Reb" (media a partita), stesso calcolo di punti/assist. */
    val rimbalzi: Int = 0,
    /** Rimbalzi totali in Nazionale, stessa sezione "International competitions" di [presenzeNazionale]. */
    val rimbalziNazionale: Int = 0,
)

// Sigle usate da Proballers nel campo "Position" (es. "sg, sf" per un giocatore che copre due
// ruoli: si prende sempre e solo il primo, quello principale).
private val proballersPositionAbbrev: Map<String, String> = mapOf(
    "pg" to "Point Guard", "sg" to "Shooting Guard", "sf" to "Small Forward",
    "pf" to "Power Forward", "c" to "Center", "g" to "Guard", "f" to "Forward",
)

/**
 * Legge la tabella "Regular Season Stats" di una pagina giocatore Proballers (es.
 * https://www.proballers.com/basketball/player/2765/michael-jordan, verificata manualmente) e
 * calcola i totali di carriera. La tabella riporta MEDIE a partita (Pts/Reb/Ast) + GP (partite
 * giocate) per stagione: i totali si ottengono moltiplicando media * GP e sommando tutte le
 * stagioni. "Competizione massima" = la lega in cui ha giocato più stagioni; "squadra principale"
 * = la squadra in cui ha totalizzato più partite.
 */
object ProballersCareerStats {

    suspend fun fetchCareerTotals(profileUrl: String): ProballersTotals? = runCatching {
        val request = Request.Builder()
            .url(profileUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            .build()

        val html = lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        } ?: return@runCatching null

        val doc = Jsoup.parse(html, profileUrl)
        val rows = doc.select("section#anchor-regular-season table.table tbody tr")
        if (rows.isEmpty()) return@runCatching null

        val (totalGames, totalPoints, totalAssists, topLeague, topTeam, totalRebounds) = sumSeasonRows(rows)
        if (totalGames == 0) return@runCatching null

        // Sezione separata sulla stessa pagina ("International competitions stats"): Olimpiadi/
        // EuroBasket/Mondiali con la Nazionale, stessa struttura di tabella (Season/Team/League/
        // Pts/Reb/Ast/GP/...) della carriera di club, verificata su Michael Jordan (USA, Olympics
        // 1984 e 1992). Se assente (giocatore mai convocato) i totali restano a zero.
        val nationalRows = doc.select("section#anchor-international table.table tbody tr")
        val (nationalGames, nationalPoints, nationalAssists, _, _, nationalRebounds) = sumSeasonRows(nationalRows)

        // Blocco anagrafico ("Date of birth"/"Nationality"/"Position"), a fianco della tabella
        // statistiche sulla stessa pagina: coppie <span class="title">/<span class="info">
        // dentro "div.identity__stats__profil" (verificato su Michael Jordan).
        val profileFields = doc.select("div.identity__stats__profil > div").associate { row ->
            row.selectFirst("span.title")?.text()?.trim().orEmpty() to
                row.selectFirst("span.info")?.text()?.trim().orEmpty()
        }
        val nazione = profileFields["Nationality"]?.let(::countryFromDemonym)
        val annoNascita = profileFields["Date of birth"]
            ?.let { Regex("""(19|20)\d{2}""").find(it)?.value?.toIntOrNull() }
        // "sg, sf" -> solo il primo (ruolo principale).
        val posizione = profileFields["Position"]?.split(',')?.firstOrNull()?.trim()?.lowercase()
            ?.let { proballersPositionAbbrev[it] }

        ProballersTotals(
            presenze = totalGames,
            punteggio = totalPoints,
            assist = totalAssists,
            competizione = topLeague,
            squadraPrincipale = topTeam,
            nazione = nazione,
            annoNascita = annoNascita,
            posizione = posizione,
            presenzeNazionale = nationalGames,
            punteggioNazionale = nationalPoints,
            assistNazionale = nationalAssists,
            rimbalzi = totalRebounds,
            rimbalziNazionale = nationalRebounds,
        )
    }.getOrNull()

    private data class SeasonRowsTotals(
        val games: Int,
        val points: Int,
        val assists: Int,
        val topLeague: String,
        val topTeam: String?,
        val rebounds: Int,
    )

    /** Somma le righe di una tabella stagione-per-stagione Proballers (formato condiviso da
     * "Regular Season Stats" e "International competitions stats"): MEDIE a partita (Pts/Reb/Ast) +
     * GP, moltiplicate e sommate su tutte le stagioni. */
    private fun sumSeasonRows(rows: org.jsoup.select.Elements): SeasonRowsTotals {
        var totalGames = 0
        var totalPoints = 0
        var totalAssists = 0
        var totalRebounds = 0
        val leagueCounts = mutableMapOf<String, Int>()
        val teamGames = mutableMapOf<String, Int>()

        for (row in rows) {
            val cells = row.select("> td")
            if (cells.size < 7) continue

            val teamName = cells[1].selectFirst("a")?.text()?.trim()?.ifBlank { null }
            val leagueName = cells[2].selectFirst("a")?.attr("title")?.ifBlank { null }
                ?: cells[2].text().trim()
            val ptsAvg = cells[3].text().trim().toDoubleOrNull()
            val rebAvg = cells[4].text().trim().toDoubleOrNull()
            val astAvg = cells[5].text().trim().toDoubleOrNull()
            val gp = cells[6].text().trim().toIntOrNull()

            if (gp != null && gp > 0) {
                totalGames += gp
                if (ptsAvg != null) totalPoints += (ptsAvg * gp).roundToInt()
                if (rebAvg != null) totalRebounds += (rebAvg * gp).roundToInt()
                if (astAvg != null) totalAssists += (astAvg * gp).roundToInt()
                if (leagueName.isNotBlank()) leagueCounts[leagueName] = (leagueCounts[leagueName] ?: 0) + 1
                if (teamName != null) teamGames[teamName] = (teamGames[teamName] ?: 0) + gp
            }
        }

        return SeasonRowsTotals(
            games = totalGames,
            points = totalPoints,
            assists = totalAssists,
            topLeague = leagueCounts.maxByOrNull { it.value }?.key ?: "",
            topTeam = teamGames.maxByOrNull { it.value }?.key,
            rebounds = totalRebounds,
        )
    }
}
