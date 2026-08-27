package com.example.playerbase.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ricerca sul web tramite la Instant Answer API di DuckDuckGo: gratuita e
 * senza chiave API. Usata come fallback quando l'assistente locale non trova
 * nulla nei dati dei giocatori già salvati.
 */
object WebSearchService {

    suspend fun search(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            extractAnswer(JSONObject(body))
        } catch (e: Exception) {
            null
        }
    }

    private fun extractAnswer(json: JSONObject): String? {
        json.optString("Answer").takeIf { it.isNotBlank() }?.let { return it }

        json.optString("AbstractText").takeIf { it.isNotBlank() }?.let { text ->
            val source = json.optString("AbstractURL").takeIf { it.isNotBlank() }
            return if (source != null) "$text\n\nFonte: $source" else text
        }

        json.optString("Definition").takeIf { it.isNotBlank() }?.let { text ->
            val source = json.optString("DefinitionURL").takeIf { it.isNotBlank() }
            return if (source != null) "$text\n\nFonte: $source" else text
        }

        val related = json.optJSONArray("RelatedTopics")
        if (related != null) {
            for (i in 0 until related.length()) {
                val text = related.optJSONObject(i)?.optString("Text")
                if (!text.isNullOrBlank()) return text
            }
        }

        return null
    }
}
