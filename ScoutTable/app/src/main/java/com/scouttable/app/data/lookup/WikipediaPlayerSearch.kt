package com.scouttable.app.data.lookup

import java.net.URLEncoder
import okhttp3.Request
import org.json.JSONArray

/**
 * Ultima risorsa quando TheSportsDB non ha alcun risultato per il nome cercato (giocatori poco
 * noti al di fuori dei campionati/leghe principali che copre, es. cestisti di Serie A italiana
 * come Pietro Aradori): cerca direttamente su Wikipedia il titolo della pagina più simile, da
 * passare poi a [WikipediaCareerStats]/[WikipediaBasketballStats]/[WikipediaPlayerPhoto] al posto
 * del nome originale (utile anche se l'articolo ha un titolo leggermente diverso, es. con
 * disambiguazione tra parentesi).
 */
object WikipediaPlayerSearch {
    suspend fun findTitle(name: String): String? = runCatching {
        val encoded = URLEncoder.encode(name, "UTF-8")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/w/api.php?action=opensearch&format=json&limit=1&namespace=0&search=$encoded")
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
