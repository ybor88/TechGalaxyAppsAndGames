package com.example.frigozero.data

object RecipeRepository {

    private const val minimumIngredientsForSuggestions = 1

    private data class RankedRecipe(
        val recipe: Recipe,
        val matchCount: Int,
        val score: Int
    )

    // Le ricette locali sono mantenute solo come ultima risorsa (es. nessuna connessione).
    private val allRecipes = listOf(
        Recipe(
            id = 1,
            name = "Frittata di Verdure",
            description = "Una frittata soffice e gustosa con le verdure che hai in frigo.",
            ingredients = listOf("uovo", "pomodoro", "peperone", "cipolla", "formaggio"),
            steps = listOf(
                "Sbatti le uova in una ciotola con sale e pepe.",
                "Taglia le verdure a pezzetti e saltale in padella con olio.",
                "Aggiungi le uova sbattute sopra le verdure.",
                "Cuoci a fuoco medio finché i bordi si solidificano.",
                "Gira la frittata e cuoci altri 2 minuti.",
                "Servi calda con una spolverata di formaggio."
            ),
            emoji = "🍳",
            cookTimeMinutes = 15,
            difficulty = "Facile"
        ),
        Recipe(
            id = 2,
            name = "Pasta al Pomodoro",
            description = "Il classico intramontabile: pasta fresca con salsa di pomodoro.",
            ingredients = listOf("pasta", "pomodoro", "aglio", "cipolla", "basilico"),
            steps = listOf(
                "Porta a ebollizione una pentola d'acqua salata.",
                "Soffriggi aglio e cipolla in olio d'oliva.",
                "Aggiungi i pomodori e cuoci 15 minuti a fuoco basso.",
                "Cuoci la pasta al dente.",
                "Scola e condisci con la salsa. Aggiungi basilico fresco."
            ),
            emoji = "🍝",
            cookTimeMinutes = 25,
            difficulty = "Facile"
        ),
        Recipe(
            id = 3,
            name = "Insalata di Pollo",
            description = "Insalata leggera e proteica con pollo e verdure fresche.",
            ingredients = listOf("pollo", "lattuga", "pomodoro", "cetriolo", "limone"),
            steps = listOf(
                "Griglia il petto di pollo con sale, pepe e succo di limone.",
                "Taglia il pollo a strisce sottili.",
                "Mescola lattuga, pomodori e cetriolo in una ciotola.",
                "Aggiungi il pollo sopra.",
                "Condisci con olio, limone, sale e pepe."
            ),
            emoji = "🥗",
            cookTimeMinutes = 20,
            difficulty = "Facile"
        ),
        Recipe(
            id = 4,
            name = "Zuppa di Verdure",
            description = "Una zuppa calda e nutriente con le verdure del frigo.",
            ingredients = listOf("carota", "patata", "cipolla", "sedano", "pomodoro", "brodo"),
            steps = listOf(
                "Taglia tutte le verdure a cubetti.",
                "Soffriggi cipolla e sedano in olio.",
                "Aggiungi carote, patate e pomodori.",
                "Copri con brodo vegetale e porta a ebollizione.",
                "Cuoci 20 minuti a fuoco medio.",
                "Aggiusta di sale e servi caldo."
            ),
            emoji = "🍲",
            cookTimeMinutes = 35,
            difficulty = "Facile"
        ),
        Recipe(
            id = 5,
            name = "Risotto al Formaggio",
            description = "Risotto cremoso e saporito con formaggio fuso.",
            ingredients = listOf("riso", "formaggio", "burro", "cipolla", "brodo"),
            steps = listOf(
                "Scalda il brodo in una pentola separata.",
                "Soffriggi la cipolla tritata nel burro.",
                "Aggiungi il riso e tosta 1 minuto.",
                "Aggiungi il brodo caldo un mestolo alla volta mescolando.",
                "Continua finché il riso è cotto (circa 18 minuti).",
                "Manteca con burro e formaggio grattugiato."
            ),
            emoji = "🍚",
            cookTimeMinutes = 30,
            difficulty = "Media"
        ),
        Recipe(
            id = 6,
            name = "Uova Strapazzate con Funghi",
            description = "Colazione o cena veloce con uova e funghi saltati.",
            ingredients = listOf("uovo", "fungo", "burro", "aglio"),
            steps = listOf(
                "Pulisci e taglia i funghi a fette.",
                "Saltali in padella con burro e aglio.",
                "Sbatti le uova con un pizzico di sale.",
                "Aggiungi le uova in padella e strapazza a fuoco basso.",
                "Servi caldo con pane tostato."
            ),
            emoji = "🍄",
            cookTimeMinutes = 10,
            difficulty = "Facile"
        ),
        Recipe(
            id = 7,
            name = "Toast con Avocado",
            description = "Colazione healthy con avocado cremoso su pane tostato.",
            ingredients = listOf("pane", "avocado", "limone", "uovo", "pomodoro"),
            steps = listOf(
                "Tosta il pane.",
                "Schiaccia l'avocado con succo di limone, sale e pepe.",
                "Spalma l'avocado sul pane.",
                "Aggiungi fette di pomodoro.",
                "Opzionale: aggiungi un uovo in camicia sopra."
            ),
            emoji = "🥑",
            cookTimeMinutes = 10,
            difficulty = "Facile"
        ),
        Recipe(
            id = 8,
            name = "Pollo al Forno con Patate",
            description = "Classico piatto domenicale: pollo croccante con patate al forno.",
            ingredients = listOf("pollo", "patata", "aglio", "rosmarino", "olio d oliva"),
            steps = listOf(
                "Preriscalda il forno a 200°C.",
                "Taglia le patate a spicchi e condiscile con olio, sale e rosmarino.",
                "Disponi il pollo in una teglia con aglio e olio.",
                "Aggiungi le patate attorno al pollo.",
                "Cuoci 45-50 minuti girando a metà cottura."
            ),
            emoji = "🍗",
            cookTimeMinutes = 60,
            difficulty = "Media"
        ),
        Recipe(
            id = 9,
            name = "Smoothie Verde",
            description = "Smoothie energizzante con spinaci, banana e latte.",
            ingredients = listOf("spinaci", "banana", "latte", "mela", "yogurt"),
            steps = listOf(
                "Metti tutti gli ingredienti nel frullatore.",
                "Frulla fino ad ottenere una consistenza omogenea.",
                "Aggiungi ghiaccio se desideri.",
                "Servi subito."
            ),
            emoji = "🥤",
            cookTimeMinutes = 5,
            difficulty = "Facile"
        ),
        Recipe(
            id = 10,
            name = "Insalata Caprese",
            description = "L'insalata italiana più semplice e buona: mozzarella, pomodoro e basilico.",
            ingredients = listOf("pomodoro", "mozzarella", "basilico"),
            steps = listOf(
                "Taglia pomodori e mozzarella a fette spesse.",
                "Disponi alternando fette di pomodoro e mozzarella.",
                "Aggiungi foglie di basilico fresco.",
                "Condisci con olio extra vergine, sale e pepe."
            ),
            emoji = "🧀",
            cookTimeMinutes = 5,
            difficulty = "Facile"
        )
    )

    private var cachedRemoteRecipes: List<Recipe> = emptyList()

    suspend fun getRecipesForIngredients(scannedIngredients: List<String>): List<Pair<Recipe, Int>> {
        val normalized = normalize(scannedIngredients)

        if (normalized.size < minimumIngredientsForSuggestions) {
            cachedRemoteRecipes = emptyList()
            return emptyList()
        }

        // 1. Ricerca web STRETTA (TheMealDB): ricette che contengono TUTTI gli ingredienti
        val strictRemote = try {
            RecipeWebDataSource.searchRecipes(normalized)
        } catch (_: Exception) {
            emptyList()
        }

        if (strictRemote.isNotEmpty()) {
            cachedRemoteRecipes = strictRemote
            return strictRemote.map { recipe ->
                val recipeIngredients = normalize(recipe.ingredients)
                recipe to recipeIngredients.count(normalized::contains)
            }
        }

        // 2. Ricerca web PARZIALE (TheMealDB — 3 endpoint: filter, search, categoria):
        //    ricette che contengono almeno 1 ingrediente selezionato
        val partialRemote = try {
            RecipeWebDataSource.searchRecipesPartial(normalized)
        } catch (_: Exception) {
            emptyList()
        }

        if (partialRemote.isNotEmpty()) {
            cachedRemoteRecipes = partialRemote
            return partialRemote.map { recipe ->
                val recipeIngredients = normalize(recipe.ingredients)
                recipe to recipeIngredients.count(normalized::contains)
            }
        }

        // 3. Nessuna ricetta trovata online — segnala risultato vuoto.
        cachedRemoteRecipes = emptyList()
        return emptyList()
    }

    fun getDisplayIngredientName(ingredient: String): String {
        return IngredientCatalog.toItalianLabel(ingredient)
    }

    fun getRecipeSourceLabel(id: Int): String = when {
        id > 0 -> "Catalogo locale"
        else -> "TheMealDB"
    }

    fun getAllRecipes(): List<Recipe> = allRecipes + cachedRemoteRecipes

    fun getRecipeById(id: Int): Recipe? = (allRecipes + cachedRemoteRecipes)
        .find { it.id == id }

    private fun normalize(values: List<String>): List<String> {
        return values
            .map { IngredientCatalog.toDisplayIngredient(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun rankRecipes(
        recipes: List<Recipe>,
        availableIngredients: List<String>,
        predicate: (recipeIngredients: List<String>, availableIngredients: List<String>, matchedRecipeIngredients: Int, matchedUserIngredients: Int) -> Boolean
    ): List<Pair<Recipe, Int>> {
        return recipes
            .mapNotNull { recipe ->
                val recipeIngredients = normalize(recipe.ingredients)
                val matchedRecipeIngredients = recipeIngredients.count(availableIngredients::contains)
                val matchedUserIngredients = availableIngredients.count(recipeIngredients::contains)

                if (!predicate(recipeIngredients, availableIngredients, matchedRecipeIngredients, matchedUserIngredients)) {
                    null
                } else {
                    Triple(recipe, matchedRecipeIngredients,
                        matchedUserIngredients * 100 + matchedRecipeIngredients * 10 - recipeIngredients.size)
                }
            }
            .sortedByDescending { it.third }
            .map { it.first to it.second }
    }
}
