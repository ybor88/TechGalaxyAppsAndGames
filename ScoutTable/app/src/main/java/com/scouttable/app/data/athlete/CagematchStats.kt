// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.athlete

import com.scouttable.app.data.lookup.fetchHtmlWithFallback
import org.jsoup.Jsoup

/** Record di un wrestler dalla pagina "Match Statistics" di cagematch.net. */
data class CagematchRecord(val nr: Int, val nome: String, val incontri: Int, val vinti: Int, val persi: Int, val pareggi: Int)

/**
 * Statistiche degli incontri di wrestling da cagematch.net, pagina "Match Statistics" del
 * wrestler (`?id=2&nr=<numero>&page=22`), box "Statistics overview":
 * "Total number of matches / wins / losses / draws" (es. Hulk Hogan: 2132, 1601, 395, 136).
 *
 * Il sito è protetto da una verifica Sucuri: la pagina si apre con [fetchHtmlWithFallback], che
 * ricorre alla WebView dell'app (la stessa usata per Proballers) quando la richiesta diretta
 * riceve la pagina di verifica.
 */
object CagematchStats {

    private val nrRegex = Regex("""cagematch\.net/[^\s|\]\}<"']*?[?&]nr=(\d+)""", RegexOption.IGNORE_CASE)

    /** Numero del wrestler da un link cagematch (anche dentro un wikitext); null se assente. */
    fun wrestlerNr(text: String?): Int? =
        text?.let { t -> nrRegex.findAll(t).firstOrNull { Regex("""[?&]id=2\b""").containsMatchIn(it.value) }?.groupValues?.get(1)?.toIntOrNull() }

    fun isCagematchUrl(url: String?): Boolean = url?.contains("cagematch.net", ignoreCase = true) == true

    fun statsUrl(nr: Int) = "https://www.cagematch.net/?id=2&nr=$nr&page=22"

    suspend fun fetch(nr: Int): CagematchRecord? {
        val html = fetchHtmlWithFallback(statsUrl(nr)) ?: return null
        return parse(html, nr)
    }

    /** Legge il box "Statistics overview"; null se la pagina non lo contiene. */
    fun parse(html: String, nr: Int): CagematchRecord? {
        val doc = Jsoup.parse(html)
        val values = doc.select("div.InformationBoxRow").associate { row ->
            row.select(".InformationBoxTitle").text().trim().lowercase() to row.select(".InformationBoxContents").text().trim()
        }
        fun count(label: String): Int? = values.entries.firstOrNull { it.key.startsWith(label) }?.value
            ?.let { Regex("""\d[\d.,]*""").find(it)?.value?.filter(Char::isDigit)?.toIntOrNull() }
        val incontri = count("total number of matches") ?: return null
        return CagematchRecord(
            nr = nr,
            nome = doc.select("h1.TextHeader").text().trim(),
            incontri = incontri,
            vinti = count("total number of wins") ?: 0,
            persi = count("total number of losses") ?: 0,
            pareggi = count("total number of draws") ?: 0,
        )
    }
}
