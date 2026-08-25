package com.example.playerbase.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Recupera lo stemma/logo di una squadra da TheSportsDB (API pubblica gratuita)
 * in base al nome inserito dall'utente in "Max Team". Risultati tenuti in cache
 * in memoria per evitare richieste ripetute per la stessa squadra.
 */
object TeamLogoRepository {

    private const val baseUrl = "https://www.thesportsdb.com/api/v1/json/3"
    private const val connectTimeoutMillis = 4000
    private const val readTimeoutMillis = 6000

    private val cache = mutableMapOf<String, String?>()

    suspend fun findLogoUrl(teamName: String): String? {
        val key = teamName.trim().lowercase()
        if (key.isBlank()) return null
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null

        return withContext(Dispatchers.IO) {
            val result = try {
                val encoded = URLEncoder.encode(teamName.trim(), "UTF-8")
                val response = getJsonFromUrl("$baseUrl/searchteams.php?t=$encoded")
                val teams = response?.optJSONArray("teams")
                val firstTeam = teams?.optJSONObject(0)
                val badge = firstTeam?.optString("strTeamBadge")
                badge?.takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                Log.w("PlayerBase", "Team logo lookup failed for $teamName", e)
                null
            }
            cache[key] = result
            result
        }
    }

    private fun getJsonFromUrl(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                null
            } else {
                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(payload)
            }
        } finally {
            connection.disconnect()
        }
    }
}
