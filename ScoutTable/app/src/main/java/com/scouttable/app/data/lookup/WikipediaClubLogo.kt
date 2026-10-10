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

    fun fetch(clubName: String): String? = runCatching {
        val title = URLEncoder.encode(clubName.trim().replace(' ', '_'), "UTF-8").replace("+", "%20")
        val request = Request.Builder()
            .url("https://it.wikipedia.org/api/rest_v1/page/summary/$title")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string() ?: return@use null)
            if (json.optString("type") != "standard") return@use null
            if (!clubDescriptionRegex.containsMatchIn(json.optString("description"))) return@use null
            json.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
                ?: json.optJSONObject("originalimage")?.optString("source")?.ifBlank { null }
        }
    }.getOrNull()
}
