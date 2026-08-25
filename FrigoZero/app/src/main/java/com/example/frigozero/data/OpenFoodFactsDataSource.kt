package com.example.frigozero.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lookup di prodotti tramite codice a barre su Open Food Facts (open-data, gratuito).
 * https://world.openfoodfacts.org/data
 */
object OpenFoodFactsDataSource {

    private const val connectTimeoutMillis = 4000
    private const val readTimeoutMillis = 6000

    data class ProductResult(
        val barcode: String,
        val displayName: String,
        val canonicalIngredient: String?
    )

    suspend fun lookupByBarcode(barcode: String): ProductResult? = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
            "?fields=product_name,product_name_it,generic_name,generic_name_it,categories_tags"

        try {
            val response = getJsonFromUrl(url) ?: return@withContext null

            if (response.optInt("status", 0) != 1) {
                return@withContext null
            }

            val product = response.optJSONObject("product") ?: return@withContext null
            val name = firstNonBlank(
                product.optString("product_name_it"),
                product.optString("product_name"),
                product.optString("generic_name_it"),
                product.optString("generic_name")
            ) ?: return@withContext null

            val canonical = product.optJSONArray("categories_tags")?.let { tags ->
                (0 until tags.length())
                    .mapNotNull { idx -> tags.optString(idx).takeIf { it.isNotBlank() } }
                    .firstNotNullOfOrNull { tag ->
                        val cleaned = tag.substringAfter(':').replace('-', ' ')
                        IngredientCatalog.toCanonicalIngredient(cleaned)
                    }
            }

            ProductResult(barcode = barcode, displayName = name.trim(), canonicalIngredient = canonical)
        } catch (e: Exception) {
            Log.w("FrigoZero", "Open Food Facts lookup failed", e)
            null
        }
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() }

    private fun getJsonFromUrl(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.setRequestProperty("User-Agent", "FrigoZero/1.0 (Android)")

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
