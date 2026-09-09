package com.example.sentinelai.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CloudResult(
    val known: Boolean,
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val harmless: Int = 0,
    val name: String = ""
)

class CloudLookupException(message: String) : Exception(message)

/**
 * Cloud & Offline Protection. Offline detection (Signatures + Scanner
 * heuristics) always runs and never needs network access. Cloud lookup is
 * an optional, explicit enhancement: only when the user supplies their own
 * VirusTotal API key does SentinelAI check a hash against VirusTotal's
 * public database. Without a key, the app stays fully offline.
 */
object CloudLookup {
    suspend fun lookupHash(sha256: String, apiKey: String): CloudResult = withContext(Dispatchers.IO) {
        val url = URL("https://www.virustotal.com/api/v3/files/$sha256")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("x-apikey", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val code = connection.responseCode
            if (code == 404) {
                return@withContext CloudResult(known = false)
            }
            if (code !in 200..299) {
                throw CloudLookupException("Errore HTTP $code da VirusTotal")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val attrs = json.getJSONObject("data").getJSONObject("attributes")
            val stats = attrs.getJSONObject("last_analysis_stats")
            CloudResult(
                known = true,
                malicious = stats.optInt("malicious", 0),
                suspicious = stats.optInt("suspicious", 0),
                harmless = stats.optInt("harmless", 0),
                name = attrs.optString("meaningful_name", "")
            )
        } catch (exc: CloudLookupException) {
            throw exc
        } catch (exc: Exception) {
            throw CloudLookupException("Connessione al cloud non riuscita: ${exc.message}")
        } finally {
            connection.disconnect()
        }
    }
}
