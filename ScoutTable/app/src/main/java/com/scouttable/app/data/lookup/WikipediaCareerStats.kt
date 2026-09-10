package com.scouttable.app.data.lookup

import java.net.URLEncoder
import kotlinx.coroutines.delay
import okhttp3.Request

data class CareerTotals(val presenze: Int, val punteggio: Int, val club: String?)

/**
 * Somma presenze e gol di carriera (a livello di club) dall'infobox Wikipedia del giocatore
 * ("caps1"/"goals1", "caps2"/"goals2", ... per ogni squadra in cui ha giocato) e individua il
 * club con più presenze ("clubsN" abbinato al caps più alto). Niente assist: l'infobox standard
 * dei calciatori non li traccia. Verificato manualmente su Francesco Totti (caps1=619, goals1=250,
 * clubs1=[[AS Roma|Roma]]) prima di scrivere questo parser.
 *
 * Serve soprattutto per i giocatori ritirati: TheSportsDB non riporta più un club reale per loro
 * ("_Retired Soccer"), quindi il club/lega/stemma vengono recuperati da qui.
 */
object WikipediaCareerStats {

    // Esclude natcaps/natgoals (nazionale) e youthcaps ecc.: richiede che "caps"/"goals"/"clubs"
    // non sia preceduto da una lettera, cosi' non intercetta varianti diverse dalla carriera club.
    private val capsRegex = Regex("""(?<![A-Za-z])caps(\d+)\s*=\s*([0-9]+)""")
    private val goalsRegex = Regex("""(?<![A-Za-z])goals(\d+)\s*=\s*([0-9]+)""")
    private val clubsRegex = Regex("""(?<![A-Za-z])clubs(\d+)\s*=\s*(.+)""")

    // Le ricerche multiple ravvicinate possono far scattare rate-limit temporanei su Wikipedia:
    // un retry con una breve pausa assorbe questi blip senza appesantire troppo l'attesa utente.
    suspend fun fetchClubCareerTotals(playerName: String): CareerTotals? =
        fetchOnce(playerName) ?: run { delay(600); fetchOnce(playerName) }

    private suspend fun fetchOnce(playerName: String): CareerTotals? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/w/index.php?title=$title&action=raw")
            // Wikipedia chiede un User-Agent descrittivo per le richieste automatiche.
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()

        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val fullText = response.body?.string() ?: return@use null
            if (fullText.contains("#REDIRECT", ignoreCase = true) && fullText.length < 200) return@use null

            // Limitarsi a infobox + paragrafo iniziale (prima della prima sezione "==...==")
            // evita di raccogliere numerazioni "capsN/clubsN" di altre carriere/sezioni più
            // avanti nell'articolo che riuserebbero gli stessi indici.
            val wikitext = stripHtmlComments(fullText.substringBefore("\n=="))

            val capsByIndex = capsRegex.findAll(wikitext)
                .associate { it.groupValues[1] to (it.groupValues[2].toIntOrNull() ?: 0) }
            val clubsByIndex = clubsRegex.findAll(wikitext)
                .associate { it.groupValues[1] to it.groupValues[2] }

            val presenze = capsByIndex.values.sum()
            val punteggio = goalsRegex.findAll(wikitext).sumOf { it.groupValues[2].toIntOrNull() ?: 0 }
            if (presenze == 0 && punteggio == 0) return@use null

            val bestClubIndex = capsByIndex.maxByOrNull { it.value }?.key
            val bestClub = bestClubIndex?.let { clubsByIndex[it] }?.let(::cleanWikiText)?.ifBlank { null }

            CareerTotals(presenze, punteggio, bestClub)
        }
    }.getOrNull()
}
