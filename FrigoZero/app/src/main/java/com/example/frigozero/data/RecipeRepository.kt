package com.example.frigozero.data

import android.content.Context

object RecipeRepository {

    private const val minimumIngredientsForSuggestions = 1

    // Le ricette personali (inserite a mano dall'utente) usano un intervallo di id
    // separato dal catalogo integrato (1-16) e dalle ricette online (id negativi).
    private const val userRecipeIdBase = 100000

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
        ),
        // Ricette scritte a mano per ingredienti tipicamente italiani che TheMealDB
        // (database anglosassone) non copre: bresaola, gorgonzola, speck, taleggio,
        // radicchio, coppa, mortadella.
        Recipe(
            id = 11,
            name = "Involtini di Bresaola e Rucola",
            description = "Antipasto fresco e veloce con bresaola, rucola e scaglie di parmigiano.",
            ingredients = listOf("bresaola", "rucola", "parmigiano", "limone"),
            steps = listOf(
                "Disponi le fette di bresaola su un tagliere, leggermente sovrapposte.",
                "Lava e asciuga la rucola, poi distribuiscila al centro di ogni fetta.",
                "Con un pelapatate ricava scaglie sottili di parmigiano e aggiungile sopra la rucola.",
                "Arrotola ogni fetta di bresaola su se stessa formando un involtino.",
                "Disponi gli involtini su un piatto da portata e irrora con succo di limone e un filo d'olio.",
                "Servi freddo come antipasto."
            ),
            emoji = "🥩",
            cookTimeMinutes = 10,
            difficulty = "Facile"
        ),
        Recipe(
            id = 12,
            name = "Risotto al Gorgonzola e Noci",
            description = "Risotto cremoso con il sapore deciso del gorgonzola e la croccantezza delle noci.",
            ingredients = listOf("riso", "gorgonzola", "noce", "burro", "brodo", "cipolla"),
            steps = listOf(
                "Scalda il brodo in una pentola e tienilo caldo a fuoco basso.",
                "Soffriggi la cipolla tritata nel burro in una casseruola.",
                "Aggiungi il riso e tostalo per un minuto mescolando.",
                "Aggiungi il brodo caldo un mestolo alla volta, mescolando fino ad assorbimento.",
                "A metà cottura unisci il gorgonzola a pezzetti e mescola per farlo sciogliere.",
                "A fine cottura (circa 18 minuti) manteca con una noce di burro e le noci tritate grossolanamente.",
                "Servi caldo con una macinata di pepe."
            ),
            emoji = "🍚",
            cookTimeMinutes = 30,
            difficulty = "Media"
        ),
        Recipe(
            id = 13,
            name = "Speck e Melone",
            description = "Il classico antipasto estivo: dolcezza del melone e sapidità dello speck.",
            ingredients = listOf("speck", "melone"),
            steps = listOf(
                "Taglia il melone a metà, elimina i semi e ricava delle fette sottili.",
                "Elimina la buccia da ogni fetta di melone.",
                "Avvolgi ogni fetta di melone con una fetta di speck.",
                "Disponi gli involtini su un piatto da portata.",
                "Servi fresco come antipasto."
            ),
            emoji = "🍈",
            cookTimeMinutes = 10,
            difficulty = "Facile"
        ),
        Recipe(
            id = 14,
            name = "Insalata di Radicchio, Pere e Gorgonzola",
            description = "Insalata autunnale con il contrasto tra radicchio amarognolo, pera dolce e gorgonzola.",
            ingredients = listOf("radicchio", "pera", "gorgonzola", "noce"),
            steps = listOf(
                "Lava il radicchio, asciugalo e taglialo a listarelle.",
                "Lava la pera e tagliala a fettine sottili.",
                "Disponi il radicchio in una ciotola capiente e aggiungi le fette di pera.",
                "Sbriciola il gorgonzola sopra l'insalata.",
                "Completa con le noci tritate grossolanamente.",
                "Condisci con olio extravergine, sale e un filo di aceto balsamico."
            ),
            emoji = "🥗",
            cookTimeMinutes = 10,
            difficulty = "Facile"
        ),
        Recipe(
            id = 15,
            name = "Pasta al Taleggio e Speck",
            description = "Pasta cremosa con taleggio fuso e speck croccante.",
            ingredients = listOf("pasta", "taleggio", "speck", "panna"),
            steps = listOf(
                "Porta a ebollizione una pentola d'acqua salata e cuoci la pasta.",
                "Taglia lo speck a listarelle e rosolalo in padella senza aggiungere grassi finché è croccante.",
                "Taglia il taleggio a cubetti, eliminando la crosta.",
                "Aggiungi la panna in padella con lo speck e scalda a fuoco basso.",
                "Unisci il taleggio e mescola finché si scioglie creando una crema.",
                "Scola la pasta al dente e mantecala nella padella con il condimento.",
                "Servi subito con una macinata di pepe nero."
            ),
            emoji = "🧀",
            cookTimeMinutes = 20,
            difficulty = "Facile"
        ),
        Recipe(
            id = 16,
            name = "Tagliere di Salumi con Coppa e Mortadella",
            description = "Tagliere semplice e veloce, perfetto come antipasto o aperitivo.",
            ingredients = listOf("coppa", "mortadella", "pane", "formaggio"),
            steps = listOf(
                "Taglia la mortadella a fette non troppo sottili.",
                "Disponi la coppa a fette leggermente arrotolate su un tagliere.",
                "Aggiungi il pane tagliato a fette o a pezzi.",
                "Completa il tagliere con cubetti o fette di formaggio.",
                "Servi a temperatura ambiente come antipasto o aperitivo."
            ),
            emoji = "🥓",
            cookTimeMinutes = 5,
            difficulty = "Facile"
        )
    )

    private var cachedRemoteRecipes: List<Recipe> = emptyList()
    private var userRecipes: MutableList<Recipe> = mutableListOf()
    private var persistenceContext: Context? = null

    /** Da chiamare una volta all'avvio dell'app per caricare le ricette personali salvate. */
    fun init(context: Context) {
        persistenceContext = context.applicationContext
        userRecipes = UserRecipeStore.load(context).toMutableList()
    }

    /** Aggiunge una ricetta inserita manualmente dall'utente all'archivio locale e la persiste. */
    fun addUserRecipe(
        name: String,
        description: String,
        ingredients: List<String>,
        steps: List<String>,
        cookTimeMinutes: Int,
        difficulty: String,
        emoji: String
    ): Recipe {
        val nextId = userRecipeIdBase + userRecipes.size
        val recipe = Recipe(
            id = nextId,
            name = name.trim(),
            description = description.trim(),
            ingredients = ingredients.map { IngredientCatalog.toDisplayIngredient(it) }.filter { it.isNotBlank() }.distinct(),
            steps = steps.map { it.trim() }.filter { it.isNotBlank() },
            emoji = emoji.trim().ifBlank { "🍽️" },
            cookTimeMinutes = cookTimeMinutes,
            difficulty = difficulty
        )
        userRecipes.add(recipe)
        persistUserRecipes()
        return recipe
    }

    fun isUserRecipe(id: Int): Boolean = id >= userRecipeIdBase

    /** Tutte le ricette inserite manualmente dall'utente, per la schermata "Le mie ricette". */
    fun getUserRecipes(): List<Recipe> = userRecipes.toList()

    fun deleteUserRecipe(id: Int) {
        userRecipes.removeAll { it.id == id }
        persistUserRecipes()
    }

    private fun persistUserRecipes() {
        persistenceContext?.let { UserRecipeStore.save(it, userRecipes) }
    }

    /** Cerca solo nel catalogo di ricette dell'app (nessuna chiamata di rete). */
    fun getRecipesFromLocalArchive(scannedIngredients: List<String>): List<Pair<Recipe, Int>> {
        val normalized = normalize(scannedIngredients)
        cachedRemoteRecipes = emptyList()

        if (normalized.size < minimumIngredientsForSuggestions) {
            return emptyList()
        }

        return rankRecipes(allRecipes + userRecipes, normalized) { _, _, matchedRecipeIngredients, matchedUserIngredients ->
            matchedRecipeIngredients >= 1 && matchedUserIngredients >= 1
        }
    }

    /** Cerca solo online su TheMealDB (ricerca stretta, poi parziale). */
    suspend fun getRecipesFromWeb(scannedIngredients: List<String>): List<Pair<Recipe, Int>> {
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

        cachedRemoteRecipes = partialRemote
        return partialRemote.map { recipe ->
            val recipeIngredients = normalize(recipe.ingredients)
            recipe to recipeIngredients.count(normalized::contains)
        }
    }

    fun getDisplayIngredientName(ingredient: String): String {
        return IngredientCatalog.toItalianLabel(ingredient)
    }

    fun getRecipeSourceLabel(id: Int): String = when {
        id >= userRecipeIdBase -> "Ricetta personale"
        id > 0 -> "Catalogo locale"
        else -> "TheMealDB"
    }

    fun getAllRecipes(): List<Recipe> = allRecipes + userRecipes + cachedRemoteRecipes

    fun getRecipeById(id: Int): Recipe? = (allRecipes + userRecipes + cachedRemoteRecipes)
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
