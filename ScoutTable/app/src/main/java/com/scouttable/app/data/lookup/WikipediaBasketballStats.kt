package com.scouttable.app.data.lookup

import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import okhttp3.Request

data class BasketballCareerTotals(
    val presenze: Int,
    val punteggio: Int,
    val assist: Int,
    val squadra: String?,
    val competizione: String?,
)

/**
 * Recupera i totali di carriera di un cestista dall'infobox Wikipedia
 * ({{Infobox basketball biography}}): "stat1label"/"stat1value" ecc. (etichette libere, es.
 * "Points"/"32,292 (30.1 ppg)") e "team1" (prima squadra elencata) + "stats_league".
 * Le presenze non sono un campo diretto: si stimano dividendo il totale punti per la media a
 * partita quando disponibile (es. 32,292 / 30.1 ≈ 1073, verificato su Michael Jordan: reale 1072).
 * A differenza del calcio, funziona automaticamente senza bisogno di incollare un URL Proballers
 * (quello resta disponibile come opzione più precisa, se fornito ha la precedenza).
 */
object WikipediaBasketballStats {

    private val statLabelRegex = Regex("""(?<![A-Za-z])stat(\d+)label\s*=\s*(.+)""")
    private val statValueRegex = Regex("""(?<![A-Za-z])stat(\d+)value\s*=\s*(.+)""")
    private val team1Regex = Regex("""(?<![A-Za-z])team1\s*=\s*(.+)""")
    private val leagueRegex = Regex("""stats_league\s*=\s*(.+)""")
    // Es. "32,292 (30.1 ppg)": [^)]* dopo il numero decimale consuma "ppg"/"rpg"/"apg" prima
    // della parentesi di chiusura (senza, il gruppo opzionale non trovava mai ")" e le presenze
    // stimate restavano sempre a 0, nascondendo l'intera riga statistiche in UI).
    private val valuePattern = Regex("""([0-9,]+)\s*(?:\(([0-9.]+)[^)]*\))?""")

    suspend fun fetch(playerName: String): BasketballCareerTotals? =
        fetchOnce(playerName) ?: run { delay(600); fetchOnce(playerName) }

    private suspend fun fetchOnce(playerName: String): BasketballCareerTotals? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/w/index.php?title=$title&action=raw")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()

        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val fullText = response.body?.string() ?: return@use null
            if (fullText.contains("#REDIRECT", ignoreCase = true) && fullText.length < 200) return@use null

            // Alcuni giocatori (es. Michael Jordan col baseball) hanno una seconda carriera con
            // un proprio blocco statNlabel/statNvalue più avanti nell'articolo, che riusa gli
            // stessi indici numerici: limitandoci a infobox + paragrafo iniziale (prima della
            // prima sezione "==...=="), evitiamo di mescolare i due blocchi.
            val wikitext = stripHtmlComments(fullText.substringBefore("\n=="))

            val labels = statLabelRegex.findAll(wikitext).associate { it.groupValues[1] to it.groupValues[2] }
            val values = statValueRegex.findAll(wikitext).associate { it.groupValues[1] to it.groupValues[2] }

            var punteggio = 0
            var assist = 0
            var estimatedGames = 0

            for ((index, rawLabel) in labels) {
                val rawValue = values[index] ?: continue
                val match = valuePattern.find(rawValue) ?: continue
                val total = match.groupValues[1].replace(",", "").toIntOrNull() ?: continue
                val perGame = match.groupValues[2].toDoubleOrNull()
                val label = cleanWikiText(rawLabel)

                when {
                    label.contains("point", ignoreCase = true) -> {
                        punteggio = total
                        if (perGame != null && perGame > 0) estimatedGames = (total / perGame).roundToInt()
                    }
                    label.contains("assist", ignoreCase = true) -> assist = total
                }
            }

            if (punteggio == 0 && assist == 0) return@use null

            val squadra = team1Regex.find(wikitext)?.groupValues?.get(1)?.let(::cleanWikiText)?.ifBlank { null }
            val competizione = leagueRegex.find(wikitext)?.groupValues?.get(1)?.let(::cleanWikiText)?.ifBlank { null }

            BasketballCareerTotals(estimatedGames, punteggio, assist, squadra, competizione)
        }
    }.getOrNull()
}
