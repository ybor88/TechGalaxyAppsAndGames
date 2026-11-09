// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.lookup

import java.net.URLEncoder
import okhttp3.Request
import org.json.JSONObject

/**
 * Stemma di un club di calcio dalla voce it.wikipedia del club (REST "page/summary": l'immagine
 * principale della voce di una società è lo stemma). Ripiego per i club che TheSportsDB non trova
 * col nome scritto da it.wikipedia nel blocco "Squadre" (es. "Bayern Monaco", "Dinamo Zagabria"):
 * la voce omonima su it.wikipedia è quasi sempre un redirect verso la società giusta, seguito in
 * automatico dall'endpoint.
 *
 * Accettata solo se la descrizione della voce parla di una società/squadra di calcio: con un nome
 * di città (es. "Aarhus", "Wolfsburg", "Fortaleza") la voce è quella della città e l'immagine è
 * uno stemma civico o una bandiera, non lo stemma del club.
 */
object WikipediaClubLogo {

    private val clubDescriptionRegex = Regex("""calcistic|calcio|football|soccer|squadra|club""", RegexOption.IGNORE_CASE)
    private val basketDescriptionRegex = Regex("""pallacanestro|cestistic|basket""", RegexOption.IGNORE_CASE)

    fun fetch(clubName: String): String? =
        fetchWith(clubName, clubDescriptionRegex)
            ?: clubArticleFromTemplate("Calcio", clubName)?.let { fetchWith(it, null) }

    /**
     * Stesso ripiego per il basket. I nomi dei club nelle voci di cestisti sono spesso solo la
     * città (es. "Varese" per Aldo Ossola), che su it.wikipedia è la voce del comune: si prova
     * allora "Pallacanestro <nome>", convenzione dei titoli delle società italiane (verificato:
     * "Pallacanestro Varese", "Olimpia Milano" -> "Pallacanestro Olimpia Milano").
     */
    fun fetchBasket(clubName: String): String? =
        fetchWith(clubName, basketDescriptionRegex)
            ?: clubName.takeUnless { it.contains("pallacanestro", ignoreCase = true) }
                ?.let { fetchWith("Pallacanestro $it", basketDescriptionRegex) }
            ?: clubArticleFromTemplate("Basket", clubName)?.let { fetchWith(it, null) }

    private val templateVoceRegex = Regex("""\|\s*voce\s*=\s*([^|\n}]+)""")

    /**
     * Voce del club a cui porta il nome scritto nella carriera di un giocatore: è il collegamento
     * che si vede cliccando il club sulla pagina. Nel blocco "Squadre" it.wikipedia scrive solo il
     * nome breve (es. "Leeds", che come voce è la città), e il collegamento arriva dal template
     * dello stemmino "Template:Calcio Leeds" (redirect a "Calcio Leeds United", campo
     * "voce = Leeds United Football Club"). Verificato su Wilfried Gnonto; vale anche per il basket
     * ("Template:Basket Varese" -> "Pallacanestro Varese").
     */
    private fun clubArticleFromTemplate(prefix: String, clubName: String): String? = runCatching {
        val title = URLEncoder.encode("Template:$prefix ${clubName.trim()}", "UTF-8")
        val request = Request.Builder()
            .url(
                "https://it.wikipedia.org/w/api.php?action=query&prop=revisions&rvprop=content&rvslots=main" +
                    "&redirects=1&format=json&formatversion=2&titles=$title",
            )
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val page = JSONObject(response.body?.string() ?: return@use null)
                .optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0) ?: return@use null
            val content = page.optJSONArray("revisions")?.optJSONObject(0)
                ?.optJSONObject("slots")?.optJSONObject("main")?.optString("content").orEmpty()
            templateVoceRegex.find(content)?.groupValues?.get(1)?.trim()?.ifBlank { null }
        }
    }.getOrNull()

    // descriptionRegex null: voce già certa (presa dal template dello stemmino), nessun controllo:
    // la descrizione può non dire "calcio" (es. FC Barcelona: "società polisportiva spagnola").
    private fun fetchWith(clubName: String, descriptionRegex: Regex?): String? = runCatching {
        val title = URLEncoder.encode(clubName.trim().replace(' ', '_'), "UTF-8").replace("+", "%20")
        val request = Request.Builder()
            .url("https://it.wikipedia.org/api/rest_v1/page/summary/$title")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string() ?: return@use null)
            if (json.optString("type") != "standard") return@use null
            if (descriptionRegex != null && !descriptionRegex.containsMatchIn(json.optString("description"))) return@use null
            json.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
                ?: json.optJSONObject("originalimage")?.optString("source")?.ifBlank { null }
        }
    }.getOrNull()
}
