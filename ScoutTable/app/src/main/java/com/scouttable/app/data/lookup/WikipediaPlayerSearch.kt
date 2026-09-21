package com.scouttable.app.data.lookup

import com.scouttable.app.data.Sport
import java.net.URLEncoder
import okhttp3.Request
import org.json.JSONArray

/**
 * Ultima risorsa quando TheSportsDB non ha alcun risultato per il nome cercato (giocatori poco
 * noti al di fuori dei campionati/leghe principali che copre, es. cestisti di Serie A italiana
 * come Pietro Aradori, o calciatori di tanti anni fa): cerca direttamente su Wikipedia il titolo
 * della pagina più simile, da passare poi a
 * [WikipediaItCareerStats]/[WikipediaCareerStats]/[WikipediaBasketballStats]/[WikipediaPlayerPhoto]
 * al posto del nome originale (utile anche se l'articolo ha un titolo leggermente diverso, es. con
 * disambiguazione tra parentesi).
 *
 * Per il calcio si cerca prima su it.wikipedia.org (fonte primaria, vedi il commento in
 * [PlayerLookupService.lookup]): un vecchio calciatore italiano può non avere affatto una pagina
 * su en.wikipedia.org (o averla con un titolo diverso), e in quel caso interrogare solo l'inglese
 * restituiva NotFound pur esistendo la pagina italiana — da qui il "la ricerca ricomincia da
 * zero" anche quando l'utente trova la pagina a mano. L'inglese resta un ripiego per i casi in cui
 * anche l'italiana non trova nulla (es. giocatore straniero). Per il basket resta solo l'inglese,
 * fonte primaria consolidata (NBA/college).
 */
object WikipediaPlayerSearch {
    suspend fun findTitle(name: String, sport: Sport): String? =
        if (sport == Sport.CALCIO) {
            findTitleOn(name, "it.wikipedia.org") ?: findTitleOn(name, "en.wikipedia.org")
        } else {
            findTitleOn(name, "en.wikipedia.org")
        }

    private suspend fun findTitleOn(name: String, host: String): String? = runCatching {
        val encoded = URLEncoder.encode(name, "UTF-8")
        val request = Request.Builder()
            .url("https://$host/w/api.php?action=opensearch&format=json&limit=1&namespace=0&search=$encoded")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()
        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val titles = JSONArray(body).optJSONArray(1) ?: return@use null
            if (titles.length() == 0) null else titles.getString(0)
        }
    }.getOrNull()
}
