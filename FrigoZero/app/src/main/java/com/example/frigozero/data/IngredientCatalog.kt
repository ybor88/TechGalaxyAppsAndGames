package com.example.frigozero.data

object IngredientCatalog {

    private val genericLabels = setOf(
        "food", "fruit", "vegetable", "meat", "fish", "seafood", "dairy",
        "ingredient", "cuisine", "dish", "meal", "snack", "beverage", "drink"
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
        return toCanonicalIngredient(raw) ?: normalize(raw)
    }

    fun extractSpecificIngredients(labels: List<String>): List<String> {
        return labels
            .mapNotNull(::toCanonicalIngredient)
            .distinct()
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