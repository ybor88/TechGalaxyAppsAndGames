package com.scouttable.app.data.lookup

import kotlin.math.roundToInt
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

data class ProballersTotals(val presenze: Int, val punteggio: Int, val assist: Int, val competizione: String)

/**
 * Legge la tabella "Regular Season Stats" di una pagina giocatore Proballers (es.
 * https://www.proballers.com/basketball/player/2765/michael-jordan, verificata manualmente) e
 * calcola i totali di carriera. La tabella riporta MEDIE a partita (Pts/Reb/Ast) + GP (partite
 * giocate) per stagione: i totali si ottengono moltiplicando media * GP e sommando tutte le
 * stagioni. "Competizione massima" = la lega in cui ha giocato più stagioni.
 */
object ProballersCareerStats {

    private val client = OkHttpClient()

    suspend fun fetchCareerTotals(profileUrl: String): ProballersTotals? = runCatching {
        val request = Request.Builder()
            .url(profileUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        } ?: return@runCatching null

        val doc = Jsoup.parse(html, profileUrl)
        val rows = doc.select("section#anchor-regular-season table.table tbody tr")
        if (rows.isEmpty()) return@runCatching null

        var totalGames = 0
        var totalPoints = 0
        var totalAssists = 0
        val leagueCounts = mutableMapOf<String, Int>()

        for (row in rows) {
            val cells = row.select("> td")
            if (cells.size < 7) continue

            val leagueName = cells[2].selectFirst("a")?.attr("title")?.ifBlank { null }
                ?: cells[2].text().trim()
            val ptsAvg = cells[3].text().trim().toDoubleOrNull()
            val astAvg = cells[5].text().trim().toDoubleOrNull()
            val gp = cells[6].text().trim().toIntOrNull()

            if (gp != null && gp > 0) {
                totalGames += gp
                if (ptsAvg != null) totalPoints += (ptsAvg * gp).roundToInt()
                if (astAvg != null) totalAssists += (astAvg * gp).roundToInt()
                if (leagueName.isNotBlank()) leagueCounts[leagueName] = (leagueCounts[leagueName] ?: 0) + 1
            }
        }

        if (totalGames == 0) return@runCatching null
        val topLeague = leagueCounts.maxByOrNull { it.value }?.key ?: ""
        ProballersTotals(totalGames, totalPoints, totalAssists, topLeague)
    }.getOrNull()
}
