package com.scouttable.app.data.lookup

import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request

data class CareerTotals(val presenze: Int, val punteggio: Int)

/**
 * Somma presenze e gol di carriera (a livello di club) dall'infobox Wikipedia del giocatore
 * ("caps1"/"goals1", "caps2"/"goals2", ... per ogni squadra in cui ha giocato). Niente assist:
 * l'infobox standard dei calciatori non li traccia. Verificato manualmente su Francesco Totti
 * (caps1=619, goals1=250) prima di scrivere questo parser.
 */
object WikipediaCareerStats {

    private val client = OkHttpClient()

    // Esclude natcaps/natgoals (nazionale) e youthcaps ecc.: richiede che "caps"/"goals" non sia
    // preceduto da una lettera, cosi' non intercetta varianti diverse dalla carriera di club.
    private val capsRegex = Regex("""(?<![A-Za-z])caps(\d+)\s*=\s*([0-9]+)""")
    private val goalsRegex = Regex("""(?<![A-Za-z])goals(\d+)\s*=\s*([0-9]+)""")

    suspend fun fetchClubCareerTotals(playerName: String): CareerTotals? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/w/index.php?title=$title&action=raw")
            // Wikipedia chiede un User-Agent descrittivo per le richieste automatiche.
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val wikitext = response.body?.string() ?: return@use null
            if (wikitext.contains("#REDIRECT", ignoreCase = true) && wikitext.length < 200) return@use null

            val presenze = capsRegex.findAll(wikitext).sumOf { it.groupValues[2].toIntOrNull() ?: 0 }
            val punteggio = goalsRegex.findAll(wikitext).sumOf { it.groupValues[2].toIntOrNull() ?: 0 }
            if (presenze == 0 && punteggio == 0) null else CareerTotals(presenze, punteggio)
        }
    }.getOrNull()
}
