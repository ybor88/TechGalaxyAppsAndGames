package com.scouttable.app.data.lookup

import java.net.URLEncoder
import okhttp3.Request
import org.json.JSONObject

/**
 * Recupera la foto reale del giocatore (non lo stemma del club) dalla pagina Wikipedia, tramite
 * la REST API ufficiale "page/summary" (nessuno scraping: endpoint pubblico e documentato). Usata
 * come immagine principale in lista; se assente si ricade sullo stemma del club e, in ultima
 * istanza, su un avatar generato (vedi [com.scouttable.app.ui.common.PlayerAvatar]) cosi' la riga
 * non è mai senza immagine.
 */
object WikipediaPlayerPhoto {

    suspend fun fetch(playerName: String): String? = runCatching {
        val title = URLEncoder.encode(playerName.trim().replace(' ', '_'), "UTF-8")
        val request = Request.Builder()
            .url("https://en.wikipedia.org/api/rest_v1/page/summary/$title")
            .header("User-Agent", "ScoutTableApp/1.0 (Android scouting app; contact: n/a)")
            .build()

        lookupHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string() ?: return@use null
            val json = JSONObject(body)
            json.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
                ?: json.optJSONObject("originalimage")?.optString("source")?.ifBlank { null }
        }
    }.getOrNull()
}
