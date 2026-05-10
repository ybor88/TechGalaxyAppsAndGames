package com.example.frigozero.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

object RecipeWebDataSource {

    private const val baseUrl = "https://www.themealdb.com/api/json/v1/1"
    private const val connectTimeoutMillis = 4000
    private const val readTimeoutMillis = 6000
    private val englishStopwords = setOf(
        "the", "and", "with", "for", "from", "into", "your", "then", "until", "serve", "heat",
        "cook", "boil", "bake", "fry", "mix", "stir", "add", "remove", "minutes", "minute"
    )
    private val tokenTranslation = mapOf(
        "chicken" to "pollo",
        "beef" to "manzo",
        "pork" to "maiale",
        "fish" to "pesce",
        "salmon" to "salmone",
        "tuna" to "tonno",
        "shrimp" to "gamberi",
        "prawn" to "gamberi",
        "onion" to "cipolla",
        "garlic" to "aglio",
        "tomato" to "pomodoro",
        "potato" to "patata",
        "carrot" to "carota",
        "pepper" to "peperone",
        "egg" to "uovo",
        "eggs" to "uova",
        "cheese" to "formaggio",
        "milk" to "latte",
        "cream" to "panna",
        "butter" to "burro",
        "rice" to "riso",
        "pasta" to "pasta",
        "mushroom" to "fungo",
        "mushrooms" to "funghi",
        "spinach" to "spinaci",
        "lemon" to "limone",
        "orange" to "arancia",
        "apple" to "mela",
        "banana" to "banana",
        "bread" to "pane",
        "oil" to "olio",
        "olive" to "oliva",
        "salt" to "sale",
        "peppercorn" to "pepe",
        "peppercorns" to "pepe",
        "parsley" to "prezzemolo",
        "basil" to "basilico",
        "rosemary" to "rosmarino"
    )

    private data class MealSummary(
        val id: Int,
        val name: String
    )

    suspend fun searchRecipes(availableIngredients: List<String>): List<Recipe> = withContext(Dispatchers.IO) {
        if (availableIngredients.isEmpty()) {
            return@withContext emptyList()
        }

        val normalizedIngredients = availableIngredients
            .mapNotNull { IngredientCatalog.toApiIngredient(it) }
            .distinct()

        if (normalizedIngredients.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            val candidates = collectCandidates(normalizedIngredients)
            candidates.mapNotNull { summary ->
                fetchRecipeDetails(summary.id, normalizedIngredients)
            }
        } catch (e: Exception) {
            Log.w("FrigoZero", "Online recipe search failed", e)
            emptyList()
        }
    }

    /**
     * Fallback: cerca ricette che contengono ALMENO 1 degli ingredienti selezionati.
     * Le ricette con più ingredienti in comune vengono mostrate prima.
     */
    suspend fun searchRecipesPartial(availableIngredients: List<String>): List<Recipe> = withContext(Dispatchers.IO) {
        if (availableIngredients.isEmpty()) return@withContext emptyList()

        val normalizedIngredients = availableIngredients
            .mapNotNull { IngredientCatalog.toApiIngredient(it) }
            .distinct()

        if (normalizedIngredients.isEmpty()) return@withContext emptyList()

        try {
            val candidates = collectCandidatesAtLeastOne(normalizedIngredients)
            candidates.mapNotNull { summary ->
                fetchRecipeDetailsPartial(summary.id, normalizedIngredients)
            }
        } catch (e: Exception) {
            Log.w("FrigoZero", "Partial recipe search failed", e)
            emptyList()
        }
    }

    private fun collectCandidatesAtLeastOne(ingredients: List<String>): List<MealSummary> {
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
                matchCounter[id] = ((previous?.first ?: 0) + 1) to name
            }
        }

        // Almeno 1 corrispondenza, ordinate per numero di ingredienti in comune (decrescente)
        return matchCounter.entries
            .filter { it.value.first >= 1 }
            .sortedByDescending { it.value.first }
            .take(12)
            .map { MealSummary(id = it.key, name = it.value.second) }
    }

    /** Recupera dettagli senza il filtro "tutti gli ingredienti devono esserci" */
    private fun fetchRecipeDetailsPartial(id: Int, requestedApiIngredients: List<String>): Recipe? {
        val response = getJsonFromUrl("$baseUrl/lookup.php?i=$id") ?: return null
        val meals = response.optJSONArray("meals") ?: return null
        val meal = meals.optJSONObject(0) ?: return null

        val ingredients = (1..20)
            .mapNotNull { idx ->
                val raw = meal.optString("strIngredient$idx").trim()
                if (raw.isBlank()) return@mapNotNull null
                localizeIngredient(raw)
            }
            .filter { it.isNotBlank() }
            .distinct()

        val steps = localizeSteps(meal.optString("strInstructions"), ingredients)
        val recipeName = localizeRecipeName(ingredients)

        return Recipe(
            id = -id,
            name = recipeName,
            description = "Ricetta compatibile con alcuni degli ingredienti che hai selezionato.",
            ingredients = ingredients,
            steps = steps,
            emoji = "🌐",
            cookTimeMinutes = 30,
            difficulty = "Media"
        )
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

        // Solo ricette che compaiono nei risultati di TUTTI gli ingredienti (intersezione completa)
        val requiredScore = ingredients.size
        return matchCounter.entries
            .filter { it.value.first >= requiredScore }
            .sortedWith(
                compareByDescending<Map.Entry<Int, Pair<Int, String>>> { it.value.first }
                    .thenBy { it.value.second }
            )
            .take(12)
            .map { MealSummary(id = it.key, name = it.value.second) }
    }

    private fun fetchRecipeDetails(id: Int, requestedApiIngredients: List<String>): Recipe? {
        val response = getJsonFromUrl("$baseUrl/lookup.php?i=$id") ?: return null
        val meals = response.optJSONArray("meals") ?: return null
        val meal = meals.optJSONObject(0) ?: return null

        val ingredients = (1..20)
            .mapNotNull { idx ->
                val raw = meal.optString("strIngredient$idx").trim()
                if (raw.isBlank()) return@mapNotNull null
                localizeIngredient(raw)
            }
            .filter { it.isNotBlank() }
            .distinct()

        val requestedCanonical = requestedApiIngredients
            .mapNotNull { IngredientCatalog.toCanonicalIngredient(it) }
            .distinct()

        // Richiede che TUTTI gli ingredienti selezionati siano presenti nella ricetta
        if (!requestedCanonical.all(ingredients::contains)) {
            return null
        }

        val steps = localizeSteps(meal.optString("strInstructions"), ingredients)
        val recipeName = localizeRecipeName(ingredients)

        return Recipe(
            id = -id,
            name = recipeName,
            description = "Ricetta trovata online e ordinata in base agli ingredienti che hai inserito.",
            ingredients = ingredients,
            steps = steps,
            emoji = "🌐",
            cookTimeMinutes = 30,
            difficulty = "Media"
        )
    }

    private fun localizeIngredient(raw: String): String? {
        IngredientCatalog.toCanonicalIngredient(raw)?.let { return it }

        val translated = translateCookingText(raw)
        IngredientCatalog.toCanonicalIngredient(translated)?.let { return it }

        val italianLabel = IngredientCatalog.toItalianLabel(translated)
        if (italianLabel.isBlank() || looksEnglish(italianLabel)) {
            return null
        }
        return italianLabel
    }

    private fun localizeRecipeName(ingredients: List<String>): String {
        val top = ingredients.take(2)
        return when (top.size) {
            0 -> "Ricetta internazionale"
            1 -> "Ricetta con ${top[0]}"
            else -> "Ricetta con ${top[0]} e ${top[1]}"
        }
    }

    private fun localizeSteps(instructionsRaw: String, ingredients: List<String>): List<String> {
        val translated = instructionsRaw
            .split("\n", ". ")
            .map { it.trim() }
            .filter { it.length > 8 }
            .map { translateCookingText(it) }
            .map { it.replaceFirstChar { ch -> ch.uppercase() } }
            .filterNot(::looksEnglish)
            .distinct()
            .take(8)

        if (translated.isNotEmpty()) {
            return translated
        }

        val ingredientText = ingredients.take(3).joinToString(", ")
        return listOf(
            "Prepara e taglia gli ingredienti: $ingredientText.",
            "Cuoci seguendo il metodo della ricetta originale e mescola con regolarita.",
            "Regola di sale e servi caldo."
        )
    }

    private fun translateCookingText(raw: String): String {
        val normalized = raw.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return ""

        return normalized
            .split(" ")
            .mapNotNull { token ->
                when {
                    token.isBlank() -> null
                    else -> tokenTranslation[token] ?: IngredientCatalog.toItalianLabel(token).ifBlank { token }
                }
            }
            .joinToString(" ")
            .trim()
    }

    private fun looksEnglish(text: String): Boolean {
        val tokens = text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(" ")
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return false
        val hits = tokens.count { it in englishStopwords }
        return hits >= 2
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

