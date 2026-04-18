package com.example.frigozero.data

object RecipeRepository {

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

    /**
     * Returns recipes that can be made with the given ingredients.
     * A recipe is included if at least one of its ingredients matches.
     * Recipes are sorted by how many ingredients match (best match first).
     */
    fun getRecipesForIngredients(scannedIngredients: List<String>): List<Pair<Recipe, Int>> {
        val normalized = scannedIngredients.map { it.lowercase().trim() }
        return allRecipes
            .map { recipe ->
                val matchCount = recipe.ingredients.count { ing ->
                    normalized.any { scanned -> scanned.contains(ing) || ing.contains(scanned) }
                }
                Pair(recipe, matchCount)
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }

    fun getAllRecipes(): List<Recipe> = allRecipes

    fun getRecipeById(id: Int): Recipe? = allRecipes.find { it.id == id }
}

