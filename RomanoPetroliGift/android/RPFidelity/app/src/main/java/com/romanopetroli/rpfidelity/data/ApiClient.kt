package com.romanopetroli.rpfidelity.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ApiResult(
    val success: Boolean,
    val statusCode: Int,
    val body: JSONObject,
    val errorMessage: String?
)

/**
 * Client HTTP minimale per l'API RP Fidelity (stesso stile di OpenFoodFactsDataSource in FrigoZero:
 * HttpURLConnection + org.json, nessuna dipendenza Retrofit/OkHttp).
 */
object ApiClient {
    private const val connectTimeoutMillis = 6000
    private const val readTimeoutMillis = 8000

    /** Token dell'utente loggato, impostato dopo login/registrazione o al ripristino della sessione. */
    var token: String? = null

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): ApiResult =
        withContext(Dispatchers.IO) { request("GET", path, query, null) }

    suspend fun post(path: String, body: Map<String, Any?> = emptyMap()): ApiResult =
        withContext(Dispatchers.IO) { request("POST", path, emptyMap(), toJson(body)) }

    private fun toJson(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            when (value) {
                is List<*> -> json.put(key, JSONArray(value))
                else -> json.put(key, value)
            }
        }
        return json
    }

    private fun request(
        method: String,
        path: String,
        query: Map<String, String>,
        jsonBody: JSONObject?
    ): ApiResult {
        val queryString = if (query.isEmpty()) {
            ""
        } else {
            "?" + query.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
        }

        val connection = URL(NetworkConfig.BASE_URL + path + queryString).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }

            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(jsonBody.toString()) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                JSONObject()
            }

            ApiResult(
                success = status in 200..299,
                statusCode = status,
                body = json,
                errorMessage = if (status !in 200..299) json.optString("error", "Errore (HTTP $status)") else null
            )
        } catch (e: Exception) {
            Log.e("RPFidelity", "Chiamata API fallita: $method $path", e)
            ApiResult(
                success = false,
                statusCode = -1,
                body = JSONObject(),
                errorMessage = "Connessione al server non riuscita. Controlla la rete e riprova."
            )
        } finally {
            connection.disconnect()
        }
    }
}
