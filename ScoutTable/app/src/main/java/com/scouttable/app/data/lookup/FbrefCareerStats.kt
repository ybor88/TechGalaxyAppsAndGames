// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Totali di campionato di un calciatore letti da FBref (vedi [FbrefCareerStats]). */
data class FbrefTotals(
    val nome: String?,
    val annoNascita: Int?,
    /** Nazione in inglese, come quella di TheSportsDB (es. "Italy"). */
    val nazione: String?,
    /** Ruolo grezzo in inglese (es. "Forward"): va tradotto con [translateRole]. */
    val posizione: String?,
    val presenze: Int,
    val gol: Int,
    val assist: Int,
    val minuti: Int,
    /** Gol subiti (solo portieri, tabella "Goalkeeping"). */
    val golSubiti: Int,
    /** Club con più presenze di campionato e il suo campionato (es. "Roma", "Serie A"). */
    val clubPrincipale: String?,
    val competizione: String,
    /** Anno di fine dell'ultima stagione giocata (es. "2016-2017" -> 2017). */
    val ultimaStagione: Int?,
)

/**
 * Seconda fonte per il calcio, accanto a Wikipedia: la pagina giocatore di FBref (es.
 * https://fbref.com/en/players/ed75b6c7/Francesco-Totti). Struttura verificata sulla pagina di
 * Francesco Totti:
 * - "div#meta": nome nell'h1, poi paragrafi "<strong>Position:</strong> FW-MF (AM)",
 *   "<strong>Born:</strong> September 27, 1976 in Rome, Italy", "<strong>National Team:</strong> Italy";
 * - tabella "stats_standard_dom_lg" (campionato nazionale), una riga per stagione e club, celle
 *   riconosciute dall'attributo data-stat (year_id/team/comp_level/games/minutes/goals/assists),
 *   più stabile dell'ordine delle colonne; i totali in tfoot non vengono letti;
 * - "stats_keeper_dom_lg" per i portieri (gk_goals_against).
 * Su FBref alcune tabelle arrivano dentro commenti HTML (caricate poi via JavaScript): i commenti
 * vengono quindi "aperti" prima del parsing.
 *
 * Solo campionato: niente coppe né Nazionale, che restano da Wikipedia.
 */
object FbrefCareerStats {

    private const val CHALLENGE_MARKER = "Just a moment"
    private val fbrefUrlRegex = Regex("""fbref\.com/(?:[a-z]{2}/)?players/([0-9a-f]{8})(?:/([^/?#\s]+))?""", RegexOption.IGNORE_CASE)
    private val yearRegex = Regex("""(?:19|20)\d{2}""")

    // Sigle di FBref (la prima è il ruolo principale): "FW-MF (AM)" -> "Forward".
    private val positionAbbrev = mapOf("GK" to "Goalkeeper", "DF" to "Defender", "MF" to "Midfielder", "FW" to "Forward")

    /** Qualsiasi pagina del giocatore (anche le sottopagine per stagione) -> pagina principale inglese. */
    fun canonicalProfileUrl(rawUrl: String): String {
        val m = fbrefUrlRegex.find(rawUrl.trim()) ?: return rawUrl.trim()
        val slug = m.groupValues[2].takeIf { it.isNotBlank() && !it.contains("matchlogs", ignoreCase = true) }
        return "https://fbref.com/en/players/${m.groupValues[1]}" + if (slug != null) "/$slug" else ""
    }

    suspend fun fetchCareerTotals(rawProfileUrl: String): FbrefTotals? = runCatching {
        val profileUrl = canonicalProfileUrl(rawProfileUrl)
        val html = fetchHtml(profileUrl) ?: return@runCatching null
        val doc = Jsoup.parse(html.replace("<!--", "").replace("-->", ""), profileUrl)

        val meta = doc.selectFirst("div#meta")
        fun field(label: String): String? = meta?.select("p")
            ?.firstOrNull { p -> p.select("strong").any { it.text().trim().removeSuffix(":") == label } }
            ?.text()?.substringAfter("$label:")?.trim()?.ifBlank { null }

        val nome = meta?.selectFirst("h1")?.text()?.trim()?.ifBlank { null }
        val born = field("Born")
        val annoNascita = born?.let { yearRegex.find(it)?.value?.toIntOrNull() }
        // "National Team: Italy"; in mancanza, il paese di nascita ("... in Rome, Italy").
        val nazione = field("National Team")?.split(Regex("""\s{2,}| [a-z]{2,3}$"""))?.firstOrNull()?.trim()?.ifBlank { null }
            ?: born?.substringAfter(" in ", "")?.substringAfterLast(',')?.trim()
                ?.removeSuffix(meta?.selectFirst("p span.f-i")?.text().orEmpty())?.trim()?.ifBlank { null }
        val posizione = field("Position")?.substringBefore('▪')?.trim()
            ?.split('-', ' ', ',')?.firstNotNullOfOrNull { positionAbbrev[it.trim().uppercase()] }

        val rows = seasonRows(doc, "stats_standard_dom_lg")
        if (rows.isEmpty()) return@runCatching null
        var presenze = 0; var gol = 0; var assist = 0; var minuti = 0
        var ultimaStagione: Int? = null
        val clubGames = mutableMapOf<String, Int>()
        val clubComp = mutableMapOf<String, MutableMap<String, Int>>()
        for (row in rows) {
            val games = row.stat("games") ?: continue
            if (games <= 0) continue
            presenze += games
            gol += row.stat("goals") ?: 0
            assist += row.stat("assists") ?: 0
            minuti += row.stat("minutes") ?: 0
            row.statText("year_id")?.let(::seasonEndYear)?.let { ultimaStagione = maxOf(ultimaStagione ?: it, it) }
            val team = row.statText("team") ?: continue
            clubGames[team] = (clubGames[team] ?: 0) + games
            // "1. Serie A" -> "Serie A" (il numero è il livello nella piramide del paese).
            row.statText("comp_level")?.replace(Regex("""^\d+\.\s*"""), "")?.ifBlank { null }?.let { comp ->
                val byComp = clubComp.getOrPut(team) { mutableMapOf() }
                byComp[comp] = (byComp[comp] ?: 0) + games
            }
        }
        val golSubiti = seasonRows(doc, "stats_keeper_dom_lg").sumOf { it.stat("gk_goals_against") ?: 0 }
        val club = clubGames.maxByOrNull { it.value }?.key

        FbrefTotals(
            nome = nome,
            annoNascita = annoNascita,
            nazione = nazione,
            posizione = posizione,
            presenze = presenze,
            gol = gol,
            assist = assist,
            minuti = minuti,
            golSubiti = golSubiti,
            clubPrincipale = club,
            competizione = club?.let { clubComp[it] }?.maxByOrNull { it.value }?.key.orEmpty(),
            ultimaStagione = ultimaStagione,
        )
    }.getOrNull()

    /** Righe stagione della tabella [tableId] (tbody, senza intestazioni ripetute né totali). */
    private fun seasonRows(doc: Document, tableId: String): List<Element> =
        doc.select("table#$tableId tbody tr").filter { row ->
            !row.hasClass("thead") && !row.hasClass("spacer") && row.selectFirst("[data-stat=year_id]") != null
        }

    private fun Element.statText(stat: String): String? = selectFirst("[data-stat=$stat]")?.text()?.trim()?.ifBlank { null }

    private fun Element.stat(stat: String): Int? = statText(stat)?.replace(",", "")?.toDoubleOrNull()?.toInt()

    /** Come Proballers/RealGM: richiesta diretta, e se Cloudflare la blocca la WebView. */
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
}
