package com.example.frigozero.data

object IngredientCatalog {

    private val genericLabels = setOf(
        "food", "fruit", "vegetable", "meat", "fish", "seafood", "dairy",
        "ingredient", "cuisine", "dish", "meal", "snack", "beverage", "drink",
        "produce", "plant", "plant food", "natural foods", "superfood", "recipe",
        "tableware", "serveware", "kitchen utensil", "kitchen appliance", "packaging",
        "cibo", "frutta", "verdura", "carne", "pesce", "latticini", "ingrediente",
        "cucina", "piatto", "pasto", "spuntino", "bevanda", "prodotto", "pianta",
        "ricetta", "imballaggio", "contenitore"
    )

    private val aliasesByCanonical = mapOf(
        "uovo" to setOf("egg", "eggs", "uovo", "uova"),
        "pomodoro" to setOf("tomato", "tomatoes", "pomodoro", "pomodori"),
        "peperone" to setOf("pepper", "bell pepper", "peperone", "peperoni"),
        "cipolla" to setOf("onion", "onions", "cipolla", "cipolle"),
        "formaggio" to setOf("cheese", "formaggio", "formaggi"),
        "pasta" to setOf("pasta"),
        "aglio" to setOf("garlic", "aglio"),
        "basilico" to setOf("basil", "basilico"),
        "pollo" to setOf("chicken", "pollo"),
        "lattuga" to setOf("lettuce", "lattuga", "insalata"),
        "cetriolo" to setOf("cucumber", "cetriolo", "cetrioli"),
        "limone" to setOf("lemon", "limone", "limoni"),
        "carota" to setOf("carrot", "carota", "carote"),
        "patata" to setOf("potato", "potatoes", "patata", "patate"),
        "sedano" to setOf("celery", "sedano"),
        "brodo" to setOf("broth", "stock", "brodo"),
        "riso" to setOf("rice", "riso"),
        "burro" to setOf("butter", "burro"),
        "fungo" to setOf("mushroom", "mushrooms", "fungo", "funghi"),
        "pane" to setOf("bread", "pane"),
        "avocado" to setOf("avocado"),
        "rosmarino" to setOf("rosemary", "rosmarino"),
        "olio d oliva" to setOf("olive oil", "oil", "olio", "olio d oliva", "olio di oliva"),
        "spinaci" to setOf("spinach", "spinaci", "spinacio"),
        "banana" to setOf("banana", "banane"),
        "latte" to setOf("milk", "latte"),
        "mela" to setOf("apple", "apples", "mela", "mele"),
        "yogurt" to setOf("yogurt", "yoghurt"),
        "mozzarella" to setOf("mozzarella"),
        "arancia" to setOf("orange", "oranges", "arancia", "arance"),
        "panna" to setOf("cream", "panna"),
        "farina" to setOf("flour", "farina"),
        "zucchero" to setOf("sugar", "zucchero"),
        "prezzemolo" to setOf("parsley", "prezzemolo"),
        "zucchina" to setOf("zucchini", "zucchina", "zucchine"),
        "melanzana" to setOf("eggplant", "aubergine", "melanzana", "melanzane"),
        "broccoli" to setOf("broccoli", "broccolo"),
        "cavolfiore" to setOf("cauliflower", "cavolfiore"),
        "fagiolo" to setOf("bean", "beans", "fagiolo", "fagioli"),
        "lenticchia" to setOf("lentil", "lentils", "lenticchia", "lenticchie"),
        "mais" to setOf("corn", "mais"),
        "pisello" to setOf("pea", "peas", "pisello", "piselli"),
        "gambero" to setOf("shrimp", "prawn", "gambero", "gamberi"),
        "salsiccia" to setOf("sausage", "salsiccia", "salsicce"),
        "prosciutto" to setOf("ham", "prosciutto"),
        "pancetta" to setOf("bacon", "pancetta"),
        "manzo" to setOf("beef", "manzo"),
        "maiale" to setOf("pork", "maiale"),
        "salmone" to setOf("salmon", "salmone"),
        "tonno" to setOf("tuna", "tonno")
    )

    private val aliasToCanonical = buildMap {
        aliasesByCanonical.forEach { (canonical, aliases) ->
            aliases.forEach { alias -> put(normalize(alias), canonical) }
        }
    }

    private val apiIngredientByCanonical = mapOf(
        "uovo" to "egg",
        "pomodoro" to "tomato",
        "peperone" to "bell pepper",
        "cipolla" to "onion",
        "formaggio" to "cheese",
        "pasta" to "pasta",
        "aglio" to "garlic",
        "basilico" to "basil",
        "pollo" to "chicken",
        "lattuga" to "lettuce",
        "cetriolo" to "cucumber",
        "limone" to "lemon",
        "carota" to "carrot",
        "patata" to "potato",
        "sedano" to "celery",
        "brodo" to "broth",
        "riso" to "rice",
        "burro" to "butter",
        "fungo" to "mushroom",
        "pane" to "bread",
        "avocado" to "avocado",
        "rosmarino" to "rosemary",
        "olio d oliva" to "olive oil",
        "spinaci" to "spinach",
        "banana" to "banana",
        "latte" to "milk",
        "mela" to "apple",
        "yogurt" to "yogurt",
        "mozzarella" to "mozzarella",
        "arancia" to "orange",
        "panna" to "cream",
        "farina" to "flour",
        "zucchero" to "sugar",
        "prezzemolo" to "parsley",
        "zucchina" to "zucchini",
        "melanzana" to "eggplant",
        "broccoli" to "broccoli",
        "cavolfiore" to "cauliflower",
        "fagiolo" to "bean",
        "lenticchia" to "lentil",
        "mais" to "corn",
        "pisello" to "pea",
        "gambero" to "shrimp",
        "salsiccia" to "sausage",
        "prosciutto" to "ham",
        "pancetta" to "bacon",
        "manzo" to "beef",
        "maiale" to "pork",
        "salmone" to "salmon",
        "tonno" to "tuna"
    )

    private val italianTranslations = mapOf(
        "food" to "cibo",
        "fruit" to "frutta",
        "vegetable" to "verdura",
        "meat" to "carne",
        "fish" to "pesce",
        "seafood" to "frutti di mare",
        "dairy" to "latticini",
        "ingredient" to "ingrediente",
        "dish" to "piatto",
        "meal" to "pasto",
        "snack" to "spuntino",
        "beverage" to "bevanda",
        "drink" to "bevanda",
        "produce" to "prodotto fresco",
        "plant" to "pianta",
        "plant food" to "alimento vegetale",
        "natural foods" to "alimenti naturali",
        "superfood" to "superfood",
        "recipe" to "ricetta",
        "tableware" to "stoviglie",
        "serveware" to "piatto da portata",
        "kitchen utensil" to "utensile da cucina",
        "kitchen appliance" to "elettrodomestico",
        "packaging" to "confezione",
        "container" to "contenitore",
        "bottle" to "bottiglia",
        "jar" to "barattolo",
        "can" to "lattina"
    )

    private val searchableAliases = aliasToCanonical.keys.sortedByDescending { it.length }

    fun toCanonicalIngredient(raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) {
            return null
        }
        if (normalized in genericLabels) {
            return null
        }

        aliasToCanonical[normalized]?.let { return it }

        val padded = " $normalized "
        val matchedAlias = searchableAliases.firstOrNull { alias ->
            padded.contains(" $alias ")
        } ?: return null

        return aliasToCanonical[matchedAlias]
    }

    fun toDisplayIngredient(raw: String): String {
        return toCanonicalIngredient(raw) ?: toItalianLabel(raw)
    }

    fun toApiIngredient(raw: String): String? {
        val canonical = toCanonicalIngredient(raw) ?: return null
        return apiIngredientByCanonical[canonical]
    }

    fun toItalianLabel(raw: String): String {
        toCanonicalIngredient(raw)?.let { return it }

        val normalized = normalize(raw)
        if (normalized.isBlank()) {
            return ""
        }

        italianTranslations[normalized]?.let { return it }

        return normalized
            .split(" ")
            .mapNotNull { token ->
                when {
                    token.isBlank() -> null
                    token in genericLabels -> italianTranslations[token] ?: token
                    else -> italianTranslations[token] ?: token
                }
            }
            .joinToString(" ")
            .trim()
    }

    fun extractSpecificIngredients(labels: List<String>): List<String> {
        return labels
            .mapNotNull(::toCanonicalIngredient)
            .distinct()
    }

    fun extractBestEffortIngredients(labels: List<String>): List<String> {
        val canonical = extractSpecificIngredients(labels)
        if (canonical.isNotEmpty()) {
            return canonical
        }

        return labels
            .map(::toItalianLabel)
            .filter { it.isNotBlank() }
            .filterNot { translated ->
                val normalized = normalize(translated)
                normalized in genericLabels ||
                    normalized.split(" ").all { token -> token in genericLabels }
            }
            .distinct()
            .take(3)
    }

    private fun normalize(value: String): String {
        return value
            .lowercase()
            .replace("'", " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}