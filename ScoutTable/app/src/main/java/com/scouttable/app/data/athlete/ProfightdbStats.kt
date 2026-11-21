// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import com.scouttable.app.data.lookup.fetchHtmlWithFallback
import org.jsoup.Jsoup

/**
 * Seconda fonte per il record dei wrestler: profightdb.com (Internet Wrestling Database), pagina
 * "Win/Loss Record" (`/winlossrecord/<nome>-<id>.html`), riga TOTAL della tabella per federazione:
 * "370 (66.43%) | 40 (7.18%) | 147 (26.39%)" = vittorie, pareggi, sconfitte.
 *
 * Usata quando cagematch.net non è leggibile o il wrestler non ha un link cagematch. Attenzione:
 * profightdb registra soprattutto gli incontri televisivi e in PPV, quindi i totali sono più bassi
 * di quelli di cagematch (es. Hulk Hogan: 557 incontri contro 2132).
 */
object ProfightdbStats {

    private val slugRegex = Regex("""profightdb\.com/(?:wrestlers|winlossrecord|facts-and-stats|pwi|relations)/([a-z0-9-]+-\d+)\.html""", RegexOption.IGNORE_CASE)

    /** "hulk-hogan-19" da un link profightdb (anche dentro un wikitext); null se assente. */
    fun slug(text: String?): String? = text?.let { slugRegex.find(it)?.groupValues?.get(1)?.lowercase() }

    fun isProfightdbUrl(url: String?): Boolean = url?.contains("profightdb.com", ignoreCase = true) == true

    fun winLossUrl(slug: String) = "http://www.profightdb.com/winlossrecord/$slug.html"

    suspend fun fetch(slug: String): CagematchRecord? {
        val html = fetchHtmlWithFallback(winLossUrl(slug), allowWebView = false) ?: return null
        return parse(html)
    }

    /** Riga TOTAL della tabella "Promotion | Win % | Draw % | Loss %"; null se assente. */
    fun parse(html: String): CagematchRecord? {
        val doc = Jsoup.parse(html)
        val table = doc.select("table").firstOrNull { t ->
            t.select("tr").firstOrNull()?.text()?.contains("Promotion", ignoreCase = true) == true
        } ?: return null
        val total = table.select("tr").firstOrNull { it.select("td").firstOrNull()?.text()?.trim().equals("TOTAL", ignoreCase = true) }
            ?: return null
        val cells = total.select("td").map { it.text() }
        fun count(i: Int) = cells.getOrNull(i)?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() } ?: 0
        val vinti = count(1)
        val pareggi = count(2)
        val persi = count(3)
        return CagematchRecord(
            nr = 0,
            nome = doc.select("h1").firstOrNull()?.ownText()?.trim().orEmpty()
                .ifBlank { doc.title().substringBefore(':').trim() },
            incontri = vinti + pareggi + persi,
            vinti = vinti,
            persi = persi,
            pareggi = pareggi,
        )
    }
}
