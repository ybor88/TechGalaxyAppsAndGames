package com.example.frigozero.data

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object RecipeWebDataSource {

    private const val baseUrl = "https://www.themealdb.com/api/json/v1/1"
    private const val connectTimeoutMillis = 4000
    private const val readTimeoutMillis = 6000

    private data class MealSummary(
        val id: Int,
        val name: String
    )

    fun searchRecipes(availableIngredients: List<String>): List<Recipe> {
        if (availableIngredients.size < 2) {
            return emptyList()
        }

        val normalizedIngredients = availableIngredients
            .map { IngredientCatalog.toDisplayIngredient(it) }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalizedIngredients.size < 2) {
            return emptyList()
        }

        return try {
            val candidates = collectCandidates(normalizedIngredients)
            candidates.mapNotNull { summary ->
                fetchRecipeDetails(summary.id, normalizedIngredients)
            }
        } catch (e: Exception) {
            Log.w("FrigoZero", "Online recipe search failed", e)
            emptyList()
        }
    }

    private fun collectCandidates(ingredients: List<String>): List<MealSummary> {
        val matchCounter = linkedMapOf<Int, Pair<Int, String>>()

        ingredients.take(4).forEach { ingredient ->
            val encoded = URLEncoder.encode(ingredient, "UTF-8")
            val response = getJsonFromUrl("$baseUrl/filter.php?i=$encoded") ?: return@forEach
            val meals = response.optJSONArray("meals") ?: return@forEach

            for (index in 0 until meals.length()) {
                val meal = meals.optJSONObject(index) ?: continue
                val id = meal.optString("idMeal").toIntOrNull() ?: continue
                val name = meal.optString("strMeal").ifBlank { "Ricetta online" }
                val previous = matchCounter[id]
                val nextScore = (previous?.first ?: 0) + 1
                matchCounter[id] = nextScore to name
            }
        }

        return matchCounter.entries
            .sortedWith(
                compareByDescending<Map.Entry<Int, Pair<Int, String>>> { it.value.first }
                    .thenBy { it.value.second }
            )
            .take(6)
            .map { MealSummary(id = it.key, name = it.value.second) }
    }

    private fun fetchRecipeDetails(id: Int, availableIngredients: List<String>): Recipe? {
        val response = getJsonFromUrl("$baseUrl/lookup.php?i=$id") ?: return null
        val meals = response.optJSONArray("meals") ?: return null
        val meal = meals.optJSONObject(0) ?: return null

        val ingredients = (1..20)
            .mapNotNull { idx ->
                val raw = meal.optString("strIngredient$idx").trim()
                if (raw.isBlank()) return@mapNotNull null
                IngredientCatalog.toCanonicalIngredient(raw)
                    ?: IngredientCatalog.toDisplayIngredient(raw)
            }
            .filter { it.isNotBlank() }
            .distinct()

        val matchCount = ingredients.count(availableIngredients::contains)
        if (matchCount == 0) {
            return null
        }

        val instructionsRaw = meal.optString("strInstructions")
        val steps = instructionsRaw
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(8)
            .ifEmpty {
                listOf(
                    "Segui i passaggi della ricetta online.",
                    "Adatta dosi e tempi agli ingredienti che hai disponibili."
                )
            }

        val recipeName = meal.optString("strMeal").ifBlank { "Ricetta online" }

        return Recipe(
            id = -id,
            name = recipeName,
            description = "Ricetta trovata online e ordinata in base agli ingredienti che hai inserito.",
            ingredients = ingredients,
            steps = steps,
            emoji = "🌐",
            cookTimeMinutes = 30,
            difficulty = "Medium"
        )
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

