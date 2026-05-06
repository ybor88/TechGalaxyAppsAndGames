package com.example.frigozero.data

object RecipeRepository {

    private const val minimumIngredientsForSuggestions = 2
    private const val generatedRecipeStartId = 10_000

    private data class RankedRecipe(
        val recipe: Recipe,
        val matchCount: Int,
        val score: Int
    )

    private val allRecipes = listOf(
        Recipe(
            id = 1,
            name = "Frittata di Verdure",
            description = "Una frittata soffice e gustosa con le verdure che hai in frigo.",
            ingredients = listOf("egg", "eggs", "tomato", "pepper", "onion", "cheese"),
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
            difficulty = "Easy"
        ),
        Recipe(
            id = 2,
            name = "Pasta al Pomodoro",
            description = "Il classico intramontabile: pasta fresca con salsa di pomodoro.",
            ingredients = listOf("pasta", "tomato", "tomatoes", "garlic", "onion", "basil"),
            steps = listOf(
                "Porta a ebollizione una pentola d'acqua salata.",
                "Soffriggi aglio e cipolla in olio d'oliva.",
                "Aggiungi i pomodori e cuoci 15 minuti a fuoco basso.",
                "Cuoci la pasta al dente.",
                "Scola e condisci con la salsa. Aggiungi basilico fresco."
            ),
            emoji = "🍝",
            cookTimeMinutes = 25,
            difficulty = "Easy"
        ),
        Recipe(
            id = 3,
            name = "Insalata di Pollo",
            description = "Insalata leggera e proteica con pollo e verdure fresche.",
            ingredients = listOf("chicken", "lettuce", "tomato", "cucumber", "lemon"),
            steps = listOf(
                "Griglia il petto di pollo con sale, pepe e succo di limone.",
                "Taglia il pollo a strisce sottili.",
                "Mescola lattuga, pomodori e cetriolo in una ciotola.",
                "Aggiungi il pollo sopra.",
                "Condisci con olio, limone, sale e pepe."
            ),
            emoji = "🥗",
            cookTimeMinutes = 20,
            difficulty = "Easy"
        ),
        Recipe(
            id = 4,
            name = "Zuppa di Verdure",
            description = "Una zuppa calda e nutriente con le verdure del frigo.",
            ingredients = listOf("carrot", "potato", "onion", "celery", "tomato", "broth"),
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
            difficulty = "Easy"
        ),
        Recipe(
            id = 5,
            name = "Risotto al Formaggio",
            description = "Risotto cremoso e saporito con formaggio fuso.",
            ingredients = listOf("rice", "cheese", "butter", "onion", "broth"),
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
            difficulty = "Medium"
        ),
        Recipe(
            id = 6,
            name = "Uova Strapazzate con Funghi",
            description = "Colazione o cena veloce con uova e funghi saltati.",
            ingredients = listOf("egg", "eggs", "mushroom", "mushrooms", "butter", "garlic"),
            steps = listOf(
                "Pulisci e taglia i funghi a fette.",
                "Saltali in padella con burro e aglio.",
                "Sbatti le uova con un pizzico di sale.",
                "Aggiungi le uova in padella e strapazza a fuoco basso.",
                "Servi caldo con pane tostato."
            ),
            emoji = "🍄",
            cookTimeMinutes = 10,
            difficulty = "Easy"
        ),
        Recipe(
            id = 7,
            name = "Toast con Avocado",
            description = "Colazione healthy con avocado cremoso su pane tostato.",
            ingredients = listOf("bread", "avocado", "lemon", "egg", "tomato"),
            steps = listOf(
                "Tosta il pane.",
                "Schiaccia l'avocado con succo di limone, sale e pepe.",
                "Spalma l'avocado sul pane.",
                "Aggiungi fette di pomodoro.",
                "Opzionale: aggiungi un uovo in camicia sopra."
            ),
            emoji = "🥑",
            cookTimeMinutes = 10,
            difficulty = "Easy"
        ),
        Recipe(
            id = 8,
            name = "Pollo al Forno con Patate",
            description = "Classico piatto domenicale: pollo croccante con patate al forno.",
            ingredients = listOf("chicken", "potato", "garlic", "rosemary", "olive oil"),
            steps = listOf(
                "Preriscalda il forno a 200°C.",
                "Taglia le patate a spicchi e condiscile con olio, sale e rosmarino.",
                "Disponi il pollo in una teglia con aglio e olio.",
                "Aggiungi le patate attorno al pollo.",
                "Cuoci 45-50 minuti girando a metà cottura."
            ),
            emoji = "🍗",
            cookTimeMinutes = 60,
            difficulty = "Medium"
        ),
        Recipe(
            id = 9,
            name = "Smoothie Verde",
            description = "Smoothie energizzante con spinaci, banana e latte.",
            ingredients = listOf("spinach", "banana", "milk", "apple", "yogurt"),
            steps = listOf(
                "Metti tutti gli ingredienti nel frullatore.",
                "Frulla fino ad ottenere una consistenza omogenea.",
                "Aggiungi ghiaccio se desideri.",
                "Servi subito."
            ),
            emoji = "🥤",
            cookTimeMinutes = 5,
            difficulty = "Easy"
        ),
        Recipe(
            id = 10,
            name = "Insalata Caprese",
            description = "L'insalata italiana più semplice e buona: mozzarella, pomodoro e basilico.",
            ingredients = listOf("tomato", "mozzarella", "cheese", "basil"),
            steps = listOf(
                "Taglia pomodori e mozzarella a fette spesse.",
                "Disponi alternando fette di pomodoro e mozzarella.",
                "Aggiungi foglie di basilico fresco.",
                "Condisci con olio extra vergine, sale e pepe."
            ),
            emoji = "🧀",
            cookTimeMinutes = 5,
            difficulty = "Easy"
        )
    )

    private var cachedGeneratedRecipes: List<Recipe> = emptyList()
    private var cachedRemoteRecipes: List<Recipe> = emptyList()

    suspend fun getRecipesForIngredients(scannedIngredients: List<String>): List<Pair<Recipe, Int>> {
        val normalized = normalize(scannedIngredients)

        if (normalized.size < minimumIngredientsForSuggestions) {
            cachedGeneratedRecipes = emptyList()
            cachedRemoteRecipes = emptyList()
            return emptyList()
        }

        val exactCatalogRecipes = rankRecipes(
            recipes = allRecipes,
            availableIngredients = normalized
        ) { recipeIngredients, availableIngredients, matchedRecipeIngredients, matchedUserIngredients ->
            recipeIngredients.isNotEmpty() &&
                recipeIngredients.all(availableIngredients::contains) &&
                matchedRecipeIngredients == recipeIngredients.size
        }

        val remoteRecipes = try {
            val remoteRecipes = RecipeWebDataSource.searchRecipes(normalized)
            cachedRemoteRecipes = remoteRecipes
            remoteRecipes
        } catch (_: Exception) {
            cachedRemoteRecipes = emptyList()
            emptyList()
        }

        val strongRemoteRecipes = rankRecipes(
            recipes = remoteRecipes,
            availableIngredients = normalized
        ) { recipeIngredients, availableIngredients, _, matchedUserIngredients ->
            recipeIngredients.isNotEmpty() &&
                matchedUserIngredients >= minimumStrongMatchCount(availableIngredients.size)
        }

        val similarRemoteRecipes = rankRecipes(
            recipes = remoteRecipes,
            availableIngredients = normalized
        ) { recipeIngredients, availableIngredients, _, matchedUserIngredients ->
            recipeIngredients.isNotEmpty() &&
                matchedUserIngredients >= minimumSimilarMatchCount(availableIngredients.size)
        }

        val prioritizedRemoteRecipes = if (strongRemoteRecipes.isNotEmpty()) {
            strongRemoteRecipes
        } else {
            similarRemoteRecipes
        }

        val generated = if (prioritizedRemoteRecipes.isEmpty() && remoteRecipes.isEmpty()) {
            generateRecipesFromAvailableIngredients(normalized)
        } else {
            emptyList()
        }
        cachedGeneratedRecipes = generated

        val generatedPairs = generated.map { recipe ->
            val recipeIngredients = normalize(recipe.ingredients)
            recipe to recipeIngredients.count(normalized::contains)
        }

        val similarCatalogRecipes = if (exactCatalogRecipes.isEmpty()) {
            rankRecipes(
                recipes = allRecipes,
                availableIngredients = normalized
            ) { recipeIngredients, availableIngredients, matchedRecipeIngredients, _ ->
                recipeIngredients.isNotEmpty() &&
                    matchedRecipeIngredients >= minimumSimilarMatchCount(availableIngredients.size)
            }
        } else {
            emptyList()
        }

        val localFallbackRecipes = (exactCatalogRecipes + similarCatalogRecipes)
            .distinctBy { it.first.id }

        return if (prioritizedRemoteRecipes.isNotEmpty()) {
            (prioritizedRemoteRecipes + localFallbackRecipes)
                .distinctBy { it.first.id }
        } else {
            (localFallbackRecipes + generatedPairs)
                .distinctBy { it.first.id }
        }
    }

    fun getDisplayIngredientName(ingredient: String): String {
        return IngredientCatalog.toItalianLabel(ingredient)
    }

    fun getRecipeSourceLabel(id: Int): String = when {
        id < 0 -> "Online"
        id >= generatedRecipeStartId -> "Generata"
        else -> "Catalogo"
    }

    fun getAllRecipes(): List<Recipe> = allRecipes + cachedGeneratedRecipes + cachedRemoteRecipes

    fun getRecipeById(id: Int): Recipe? = (allRecipes + cachedGeneratedRecipes + cachedRemoteRecipes)
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
                    RankedRecipe(
                        recipe = recipe,
                        matchCount = matchedRecipeIngredients,
                        score = matchedUserIngredients * 100 + matchedRecipeIngredients * 10 - recipeIngredients.size
                    )
                }
            }
            .sortedByDescending { it.score }
            .map { it.recipe to it.matchCount }
    }

    private fun minimumStrongMatchCount(availableSize: Int): Int = when {
        availableSize <= 2 -> availableSize
        availableSize == 3 -> 2
        else -> 3
    }

    private fun minimumSimilarMatchCount(availableSize: Int): Int = when {
        availableSize <= 2 -> 1
        else -> 2
    }

    private fun generateRecipesFromAvailableIngredients(ingredients: List<String>): List<Recipe> {
        if (ingredients.size < minimumIngredientsForSuggestions) {
            return emptyList()
        }

        val generated = mutableListOf<Recipe>()
        var nextId = generatedRecipeStartId

        fun addGenerated(
            name: String,
            description: String,
            ingredientList: List<String>,
            steps: List<String>,
            emoji: String,
            time: Int
        ) {
            val cleanIngredients = normalize(ingredientList)
            if (cleanIngredients.size < minimumIngredientsForSuggestions) return
            if (!cleanIngredients.all(ingredients::contains)) return
            if (generated.any { normalize(it.ingredients) == cleanIngredients }) return

            generated += Recipe(
                id = nextId++,
                name = name,
                description = description,
                ingredients = cleanIngredients,
                steps = steps,
                emoji = emoji,
                cookTimeMinutes = time,
                difficulty = "Easy"
            )
        }

        val tomato = "pomodoro" in ingredients
        val lemon = "limone" in ingredients
        if (tomato && lemon) {
            addGenerated(
                name = "Pomodori marinati al limone",
                description = "Preparazione fresca costruita davvero con pomodoro e limone.",
                ingredientList = listOf("pomodoro", "limone"),
                steps = listOf(
                    "Taglia il pomodoro a fette o cubetti.",
                    "Versa sopra succo di limone e lascia insaporire 2 minuti.",
                    "Mescola delicatamente e servi subito come insalata fresca."
                ),
                emoji = "🥗",
                time = 5
            )
        }

        if ("pane" in ingredients && tomato) {
            addGenerated(
                name = "Bruschetta pomodoro e pane",
                description = "Idea veloce costruita con gli ingredienti che hai inserito.",
                ingredientList = listOf("pane", "pomodoro"),
                steps = listOf(
                    "Tosta il pane in padella o nel tostapane.",
                    "Taglia il pomodoro a cubetti piccoli.",
                    "Disponi il pomodoro sul pane e servi subito."
                ),
                emoji = "🍅",
                time = 8
            )
        }

        if ("pollo" in ingredients && lemon) {
            addGenerated(
                name = "Pollo al limone veloce",
                description = "Secondo semplice basato sugli ingredienti disponibili.",
                ingredientList = listOf("pollo", "limone"),
                steps = listOf(
                    "Taglia il pollo a bocconcini.",
                    "Cuocilo in padella finché è dorato.",
                    "Aggiungi succo di limone a fine cottura e servi caldo."
                ),
                emoji = "🍋",
                time = 15
            )
        }

        if ("banana" in ingredients && "latte" in ingredients) {
            addGenerated(
                name = "Frullato banana e latte",
                description = "Bevanda semplice fatta solo con i tuoi ingredienti.",
                ingredientList = listOf("banana", "latte"),
                steps = listOf(
                    "Taglia la banana a rondelle.",
                    "Versala nel frullatore con il latte.",
                    "Frulla fino a ottenere una crema liscia e servi subito."
                ),
                emoji = "🥤",
                time = 4
            )
        }

        if ("uovo" in ingredients && tomato) {
            addGenerated(
                name = "Uova al pomodoro",
                description = "Ricetta essenziale ricavata da uovo e pomodoro.",
                ingredientList = listOf("uovo", "pomodoro"),
                steps = listOf(
                    "Scalda il pomodoro in padella per pochi minuti.",
                    "Rompi l'uovo direttamente in padella.",
                    "Copri e cuoci finché l'uovo raggiunge la consistenza desiderata."
                ),
                emoji = "🍳",
                time = 10
            )
        }

        if ("pasta" in ingredients && tomato) {
            addGenerated(
                name = "Pasta rapida al pomodoro",
                description = "Versione veloce basata strettamente sugli ingredienti disponibili.",
                ingredientList = listOf("pasta", "pomodoro") + ingredients.filter {
                    it in setOf("aglio", "basilico", "formaggio")
                }.take(2),
                steps = listOf(
                    "Cuoci la pasta in acqua salata.",
                    "Prepara in parallelo il condimento con pomodoro e gli eventuali extra disponibili.",
                    "Unisci tutto e servi caldo."
                ),
                emoji = "🍝",
                time = 15
            )
        }

        if (tomato && "formaggio" in ingredients) {
            addGenerated(
                name = "Pomodoro con formaggio fresco",
                description = "Abbinamento immediato costruito con gli ingredienti presenti.",
                ingredientList = listOf("pomodoro", "formaggio"),
                steps = listOf(
                    "Taglia il pomodoro a fette.",
                    "Aggiungi il formaggio a pezzetti o fettine.",
                    "Servi come piatto freddo veloce."
                ),
                emoji = "🧀",
                time = 5
            )
        }

        if ("uovo" in ingredients) {
            addGenerated(
                name = "Uova al salto",
                description = "Uova veloci con quello che hai in frigo.",
                ingredientList = listOf("uovo") + ingredients.filter {
                    it in setOf("pomodoro", "cipolla", "formaggio", "fungo", "spinaci")
                }.take(2),
                steps = listOf(
                    "Sbatti le uova.",
                    "Cuoci in padella con gli ingredienti scelti.",
                    "Servi caldo."
                ),
                emoji = "🍳",
                time = 10
            )
        }

        if ("pasta" in ingredients) {
            addGenerated(
                name = "Pasta del momento",
                description = "Pasta creata automaticamente solo con ingredienti disponibili.",
                ingredientList = listOf("pasta") + ingredients.filter {
                    it in setOf("pomodoro", "aglio", "cipolla", "basilico", "formaggio", "tonno")
                }.take(3),
                steps = listOf(
                    "Cuoci la pasta in acqua salata.",
                    "Prepara un condimento con gli ingredienti disponibili.",
                    "Unisci tutto e servi."
                ),
                emoji = "🍝",
                time = 15
            )
        }

        if (generated.isEmpty()) {
            val base = ingredients.take(3)
            val pairLabel = base.take(2).joinToString(" e ")
            addGenerated(
                name = "Piatto rapido di $pairLabel",
                description = "Preparazione semplice costruita davvero a partire da ${base.joinToString(", ")}.",
                ingredientList = base,
                steps = listOf(
                    "Prepara ${base.joinToString(", ")} tagliandoli in pezzi adatti alla cottura o al servizio.",
                    "Abbina gli ingredienti in un piatto freddo oppure scaldali brevemente in padella.",
                    "Assaggia, regola il condimento e servi subito."
                ),
                emoji = "👨‍🍳",
                time = 12
            )
        }

        return generated
    }
}

