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
        "cook", "boil", "bake", "fry", "mix", "stir", "add", "remove", "minutes", "minute",
        "place", "put", "take", "make", "use", "get", "let", "set", "keep", "turn",
        "over", "under", "about", "after", "before", "when", "while", "once", "every",
        "each", "both", "just", "now", "well", "away", "back", "side", "top", "bottom"
    )
    private val tokenTranslation = mapOf(
        // Carni
        "chicken" to "pollo", "beef" to "manzo", "pork" to "maiale", "fish" to "pesce",
        "salmon" to "salmone", "tuna" to "tonno", "shrimp" to "gamberi", "prawn" to "gamberi",
        "lamb" to "agnello", "veal" to "vitello", "turkey" to "tacchino", "duck" to "anatra",
        "bacon" to "pancetta", "ham" to "prosciutto", "sausage" to "salsiccia",
        // Verdure
        "onion" to "cipolla", "garlic" to "aglio", "tomato" to "pomodoro", "tomatoes" to "pomodori",
        "potato" to "patata", "potatoes" to "patate", "carrot" to "carota", "carrots" to "carote",
        "pepper" to "peperone", "peppers" to "peperoni", "spinach" to "spinaci",
        "mushroom" to "fungo", "mushrooms" to "funghi", "celery" to "sedano",
        "zucchini" to "zucchina", "eggplant" to "melanzana", "broccoli" to "broccoli",
        "cauliflower" to "cavolfiore", "leek" to "porro", "fennel" to "finocchio",
        "asparagus" to "asparago", "artichoke" to "carciofo", "cabbage" to "cavolo",
        "lettuce" to "lattuga", "cucumber" to "cetriolo", "corn" to "mais",
        "peas" to "piselli", "beans" to "fagioli", "lentils" to "lenticchie",
        // Frutta
        "lemon" to "limone", "orange" to "arancia", "apple" to "mela", "banana" to "banana",
        "tomato" to "pomodoro", "avocado" to "avocado", "pineapple" to "ananas",
        "mango" to "mango", "strawberry" to "fragola", "raspberry" to "lampone",
        // Latticini e uova
        "egg" to "uovo", "eggs" to "uova", "cheese" to "formaggio", "milk" to "latte",
        "cream" to "panna", "butter" to "burro", "yogurt" to "yogurt",
        "mozzarella" to "mozzarella", "parmesan" to "parmigiano", "ricotta" to "ricotta",
        // Basi
        "rice" to "riso", "pasta" to "pasta", "bread" to "pane", "flour" to "farina",
        "sugar" to "zucchero", "salt" to "sale", "oil" to "olio", "olive" to "oliva",
        "honey" to "miele", "chocolate" to "cioccolato", "vanilla" to "vaniglia",
        // Erbe e spezie
        "parsley" to "prezzemolo", "basil" to "basilico", "rosemary" to "rosmarino",
        "thyme" to "timo", "oregano" to "origano", "mint" to "menta", "sage" to "salvia",
        "cinnamon" to "cannella", "paprika" to "paprika", "saffron" to "zafferano",
        "nutmeg" to "noce moscata", "ginger" to "zenzero", "chilli" to "peperoncino",
        // Verbi di cottura (da usare nelle istruzioni)
        "heat" to "scalda", "add" to "aggiungi", "mix" to "mescola", "stir" to "mescola",
        "cook" to "cuoci", "boil" to "porta a ebollizione", "bake" to "cuoci in forno",
        "fry" to "friggi", "serve" to "servi", "cut" to "taglia", "chop" to "trita",
        "slice" to "affetta", "dice" to "taglia a cubetti", "season" to "condisci",
        "drain" to "scola", "rinse" to "sciacqua", "peel" to "pela", "grate" to "grattugia",
        "melt" to "sciogli", "simmer" to "cuoci a fuoco basso", "roast" to "arrostisci",
        "grill" to "griglia", "steam" to "cuoci al vapore", "marinate" to "marina",
        "blend" to "frulla", "whisk" to "sbatti", "knead" to "impasta",
        "preheat" to "preriscalda", "sprinkle" to "cospargi", "pour" to "versa",
        "remove" to "rimuovi", "place" to "disponi", "cover" to "copri",
        "combine" to "unisci", "transfer" to "trasferisci", "reduce" to "riduci",
        "bring" to "porta", "allow" to "lascia", "ensure" to "assicurati",
        "repeat" to "ripeti", "continue" to "continua", "finish" to "termina",
        "prepare" to "prepara", "wash" to "lava", "dry" to "asciuga",
        // Termini generici
        "minutes" to "minuti", "minute" to "minuto", "hours" to "ore", "hour" to "ora",
        "medium" to "fuoco medio", "high" to "fuoco alto", "low" to "fuoco basso",
        "oven" to "forno", "pan" to "padella", "pot" to "pentola", "bowl" to "ciotola",
        "plate" to "piatto", "water" to "acqua", "stock" to "brodo", "broth" to "brodo",
        "sauce" to "salsa", "juice" to "succo", "seeds" to "semi", "seed" to "seme",
        "fresh" to "fresco", "dried" to "secco", "ground" to "macinato",
        "large" to "grande", "small" to "piccolo", "hot" to "caldo", "cold" to "freddo",
        "fine" to "fine", "thick" to "spesso", "thin" to "sottile",
        "tablespoon" to "cucchiaio", "teaspoon" to "cucchiaino", "cup" to "tazza",
        "pinch" to "pizzico", "handful" to "manciata"
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
            val seenIds = mutableSetOf<Int>()
            val results = mutableListOf<Recipe>()

            // A) Filtro per ingrediente: ricette che contengono almeno 1 ingrediente (filter.php?i=)
            val filterCandidates = collectCandidatesAtLeastOne(normalizedIngredients)
            filterCandidates.forEach { summary ->
                if (seenIds.add(summary.id)) {
                    fetchRecipeDetailsPartial(summary.id, normalizedIngredients)?.let { results.add(it) }
                }
            }

            // B) Ricerca per nome pasto (search.php?s=): cerca ricette col nome dell'ingrediente nel titolo
            //    Es: "apple" → "Apple & Blackberry Crumble", "Tarte Tatin", ecc.
            val nameResults = searchByMealNameInternal(normalizedIngredients)
            nameResults.forEach { recipe ->
                val rawId = -recipe.id
                if (seenIds.add(rawId)) {
                    results.add(recipe)
                }
            }

            // C) Ricerca per categoria TheMealDB (filter.php?c=): espande i risultati
            //    mappando gli ingredienti alla categoria corrispondente (es. pollo → Chicken).
            //    Completamente gratuito, senza API key aggiuntiva.
            val categoryResults = searchByCategoryInternal(normalizedIngredients)
            categoryResults.forEach { recipe ->
                val rawId = -recipe.id
                if (seenIds.add(rawId)) {
                    results.add(recipe)
                }
            }

            results.take(20)
        } catch (e: Exception) {
            Log.w("FrigoZero", "Partial recipe search failed", e)
            emptyList()
        }
    }

    /**
     * Ricerca per nome del pasto: usa search.php?s= che cerca il nome dell'ingrediente
     * nel TITOLO della ricetta. Es: "chicken" → "Chicken Tikka Masala", "Chicken Alfredo", ecc.
     * Restituisce molte più ricette rispetto al solo filtro per ingrediente.
     */
    private fun searchByMealNameInternal(apiIngredients: List<String>): List<Recipe> {
        val seenIds = mutableSetOf<Int>()
        val results = mutableListOf<Recipe>()

        apiIngredients.take(3).forEach { apiIngredient ->
            val encoded = URLEncoder.encode(apiIngredient, "UTF-8")
            val response = getJsonFromUrl("$baseUrl/search.php?s=$encoded") ?: return@forEach
            val meals = response.optJSONArray("meals") ?: return@forEach

            for (i in 0 until meals.length()) {
                val meal = meals.optJSONObject(i) ?: continue
                val id = meal.optString("idMeal").toIntOrNull() ?: continue
                if (!seenIds.add(id)) continue

                // Parsea il pasto direttamente (search.php restituisce oggetto completo)
                val ingredients = (1..20)
                    .mapNotNull { idx ->
                        val raw = meal.optString("strIngredient$idx").trim()
                        if (raw.isBlank()) return@mapNotNull null
                        localizeIngredient(raw)
                    }
                    .filter { it.isNotBlank() }
                    .distinct()

                if (ingredients.isEmpty()) continue

                val steps = generateItalianSteps(ingredients)
                val recipeName = localizeRecipeName(ingredients)

                results.add(Recipe(
                    id = -id,
                    name = recipeName,
                    description = "Ricetta compatibile con alcuni degli ingredienti che hai selezionato.",
                    ingredients = ingredients,
                    steps = steps,
                    emoji = "🌐",
                    cookTimeMinutes = 30,
                    difficulty = "Media"
                ))
            }
        }
        return results
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

    // ──────────────────────────────────────────────────────────────────────────
    // Sorgente C: TheMealDB per categoria (filter.php?c=)
    // ──────────────────────────────────────────────────────────────────────────

    /** Mappa un ingrediente canonico italiano alla categoria TheMealDB corrispondente. */
    private fun ingredientToMealDbCategory(canonical: String): String? = when (canonical) {
        "pollo", "tacchino", "anatra", "fagiano" -> "Chicken"
        "manzo", "vitello" -> "Beef"
        "maiale", "pancetta", "salsiccia" -> "Pork"
        "agnello", "capretto" -> "Lamb"
        "capra" -> "Goat"
        "salmone", "tonno", "merluzzo", "branzino", "orata",
        "gamberi", "vongola", "cozza", "polpo", "calamaro" -> "Seafood"
        "pasta", "spaghetti", "tagliatelle", "penne", "rigatoni", "lasagna" -> "Pasta"
        else -> null
    }

    /**
     * Cerca per categoria TheMealDB: mappa gli ingredienti alla categoria
     * (es. pollo → Chicken) e recupera ricette aggiuntive di quella categoria.
     * Completamente gratuito, senza API key.
     */
    private fun searchByCategoryInternal(apiIngredients: List<String>): List<Recipe> {
        val seenCategoryIds = mutableSetOf<Int>()
        val results = mutableListOf<Recipe>()

        val categories = apiIngredients
            .mapNotNull { api ->
                val canonical = IngredientCatalog.toCanonicalIngredient(api) ?: api
                ingredientToMealDbCategory(canonical)
            }
            .distinct()
            .take(2)

        for (category in categories) {
            val encoded = URLEncoder.encode(category, "UTF-8")
            val response = getJsonFromUrl("$baseUrl/filter.php?c=$encoded") ?: continue
            val meals = response.optJSONArray("meals") ?: continue

            val mealIds = (0 until meals.length())
                .mapNotNull { meals.optJSONObject(it)?.optString("idMeal")?.toIntOrNull() }
                .shuffled()
                .take(8)

            for (id in mealIds) {
                if (!seenCategoryIds.add(id)) continue
                val recipe = fetchRecipeDetailsPartial(id, apiIngredients) ?: continue
                results.add(recipe)
                if (results.size >= 5) break
            }
            if (results.size >= 5) break
        }
        return results
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers (internal so che SpoonacularDataSource può usarli)
    // ──────────────────────────────────────────────────────────────────────────

    internal fun localizeIngredient(raw: String): String? {
        IngredientCatalog.toCanonicalIngredient(raw)?.let { return it }

        val translated = translateCookingText(raw)
        IngredientCatalog.toCanonicalIngredient(translated)?.let { return it }

        val italianLabel = IngredientCatalog.toItalianLabel(translated)
        if (italianLabel.isBlank() || looksEnglish(italianLabel)) {
            return null
        }
        return italianLabel
    }

    internal fun localizeRecipeName(ingredients: List<String>): String {
        val top = ingredients.take(2)
        return when (top.size) {
            0 -> "Ricetta internazionale"
            1 -> "Ricetta con ${top[0]}"
            else -> "Ricetta con ${top[0]} e ${top[1]}"
        }
    }

    private fun localizeSteps(instructionsRaw: String, ingredients: List<String>): List<String> {
        // La traduzione parola-per-parola non è affidabile per frasi complete in inglese.
        // Generiamo sempre passi italiani strutturati basati sugli ingredienti.
        return generateItalianSteps(ingredients)
    }

    internal fun generateItalianSteps(ingredients: List<String>): List<String> {
        val principle = ingredients.firstOrNull() ?: "ingrediente principale"
        val seconds = ingredients.drop(1).take(2)
        val all = ingredients.take(5).joinToString(", ")
        val secondsText = seconds.joinToString(", ")

        val steps = mutableListOf<String>()

        steps += "Raccogli tutti gli ingredienti necessari: $all."

        if (seconds.isNotEmpty()) {
            steps += "Lava e prepara $principle e $secondsText tagliando nella dimensione desiderata."
        } else {
            steps += "Lava e prepara $principle eliminando eventuali parti da scartare."
        }

        steps += when {
            ingredients.any { it in setOf("pasta", "riso") } ->
                "Porta a ebollizione una pentola d'acqua salata e cuoci la base (pasta o riso) secondo le istruzioni sulla confezione."
            ingredients.any { it in setOf("pollo", "manzo", "maiale", "agnello", "vitello", "tacchino", "anatra") } ->
                "Scalda una padella con un filo d'olio a fuoco medio-alto e rosola la carne su tutti i lati finché è ben dorata."
            ingredients.any { it in setOf("salmone", "tonno", "merluzzo", "branzino", "orata", "gambero", "vongola", "cozza") } ->
                "Scalda una padella antiaderente con olio e cuoci il pesce o i frutti di mare a fuoco medio per 3-5 minuti per lato."
            ingredients.any { it in setOf("uovo", "uova") } ->
                "Sbatti le uova in una ciotola con sale e pepe, poi versale in padella calda con un po' di burro."
            else ->
                "In una padella capiente, scalda un filo d'olio extravergine a fuoco medio."
        }

        if (secondsText.isNotEmpty()) {
            steps += "Aggiungi $secondsText e mescola bene per amalgamare tutti i sapori."
        }

        steps += "Aggiusta di sale, pepe e aromi a piacere. Mescola con regolarità durante la cottura."

        steps += "Impiatta la preparazione e servi caldo. Buon appetito! 🍽️"

        return steps
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
        return hits >= 1
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

