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

    private val motorDescriptionRegex = Regex("""scuderia|squadra|team|automobil|motocicl|costruttore|formula|motomondiale""", RegexOption.IGNORE_CASE)

    /**
     * Logo di una scuderia (Formula 1 / MotoGP), per il rating di piloti e moto: la voce viene dal
     * template dello stemmino ("Template:Automobilismo Ferrari" -> voce "Scuderia Ferrari"),
     * altrimenti dalla voce omonima se la descrizione parla di una scuderia.
     */
    fun fetchMotorTeam(teamName: String, motorbike: Boolean): String? {
        if (teamName.isBlank()) return null
        val prefix = if (motorbike) "Motociclismo" else "Automobilismo"
        val voce = clubArticleFromTemplate(prefix, teamName) ?: teamName
        // 1. Campo "logo" dell'infobox della voce (es. "Scuderia Ferrari HP.png", anche JPEG come
        //    "Oracle Red Bull Racing 2026.jpg"). Campo presente ma vuoto (es. Brabham): nessun
        //    logo su Wikipedia, e l'immagine principale sarebbe una foto della vettura.
        when (val field = infoboxLogo(voce)) {
            is LogoField.Found -> return field.url
            LogoField.Empty -> return null
            LogoField.NoInfobox -> Unit
        }
        // 2. Voci senza infobox: immagine principale, ma solo se non è una foto JPEG.
        return fetchWith(voce, null)?.takeIf(::isLogoImage)
            ?: fetchWith(teamName, motorDescriptionRegex)?.takeIf(::isLogoImage)
    }

    /**
     * Logo di una squadra di football americano, rugby o hockey: dalla voce collegata nella carriera
     * del giocatore ([link]), dal template dello stemmino ("Template:Hockey su ghiaccio Edmonton" ->
     * "Edmonton Oilers", "Template:Rugby Newcastle" -> "Newcastle Red Bulls") o dalla voce omonima
     * ("New England Patriots"). Si legge solo il campo dell'infobox ("logo", oppure "nomestemma" +
     * "ext" delle squadre di football e rugby): l'immagine principale è spesso una foto dello stadio
     * o le divise (verificato su Newcastle Red Bulls e New England Patriots).
     */
    fun fetchTeamSport(teamName: String, link: String?, templatePrefix: String?): String? {
        val candidates = listOfNotNull(
            link,
            templatePrefix?.let { clubArticleFromTemplate(it, teamName) },
            teamName.ifBlank { null },
        ).distinct()
        for (voce in candidates) {
            (infoboxLogo(voce) as? LogoField.Found)?.let { return it.url }
        }
        return null
    }

    private sealed class LogoField {
        data class Found(val url: String) : LogoField()
        object Empty : LogoField()
        object NoInfobox : LogoField()
    }

    private val logoFieldRegex = Regex("""\n\s*\|\s*logo\s*=\s*([^\n|]*)""", RegexOption.IGNORE_CASE)
    private val crestFieldRegex = Regex("""\n\s*\|\s*nomestemma\s*=\s*([^\n|]*)""", RegexOption.IGNORE_CASE)
    private val extFieldRegex = Regex("""\n\s*\|\s*ext\s*=\s*([^\n|]*)""", RegexOption.IGNORE_CASE)
    private val hasExtension = Regex("""\.(svg|png|gif|jpe?g|webp)$""", RegexOption.IGNORE_CASE)
    private val redirectRegex = Regex("""^#(?:REDIRECT|RINVIA)\s*:?\s*\[\[([^\]|#]+)""", RegexOption.IGNORE_CASE)

    private fun infoboxLogo(title: String, redirectsLeft: Int = 2): LogoField = runCatching {
        val encoded = URLEncoder.encode(title.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://it.wikipedia.org/w/index.php?title=$encoded&action=raw")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use LogoField.NoInfobox
            val text = response.body?.string() ?: return@use LogoField.NoInfobox
            redirectRegex.find(text.trim())?.let { r ->
                return@use if (redirectsLeft > 0) infoboxLogo(r.groupValues[1], redirectsLeft - 1) else LogoField.NoInfobox
            }
            val head = text.take(15_000)
            // "nomestemma = Stade Français Rugby logo" + "ext = svg" (infobox di football e rugby;
            // senza estensione vale png).
            val crest = crestFieldRegex.find(head)?.groupValues?.get(1)?.let(::fileName)?.ifBlank { null }
            if (crest != null) {
                val ext = extFieldRegex.find(head)?.groupValues?.get(1)?.let(::fileName)?.ifBlank { null } ?: "png"
                val file = if (hasExtension.containsMatchIn(crest)) crest else "$crest.$ext"
                return@use LogoField.Found(
                    "https://it.wikipedia.org/wiki/Special:FilePath/" + URLEncoder.encode(file.replace(' ', '_'), "UTF-8") + "?width=200",
                )
            }
            val m = logoFieldRegex.find(head) ?: return@use LogoField.NoInfobox
            val file = fileName(m.groupValues[1])
            if (file.isBlank()) LogoField.Empty
            else LogoField.Found(
                "https://it.wikipedia.org/wiki/Special:FilePath/" + URLEncoder.encode(file.replace(' ', '_'), "UTF-8") + "?width=200",
            )
        }
    }.getOrDefault(LogoField.NoInfobox)

    private fun fileName(raw: String): String = raw.replace(Regex("""<!--.*?-->"""), "").trim()
        .removePrefix("[[").removePrefix("File:").removePrefix("file:").removePrefix("Immagine:")
        .substringBefore("|").substringBefore("]]").trim()

    /** true per un'immagine da logo (SVG/PNG/GIF/WebP, o un file SVG servito come PNG), false per le foto JPEG. */
    fun isLogoImage(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return false
        return true
    }

    private val templateVoceRegex =Regex("""\|\s*voce\s*=\s*([^|\n}]+)""")

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
