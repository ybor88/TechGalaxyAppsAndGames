package com.example.frigozero.data

object IngredientCatalog {

    private val genericLabels = setOf(
        "food", "fruit", "vegetable", "meat", "fish", "seafood", "dairy",
        "ingredient", "cuisine", "dish", "meal", "snack", "beverage", "drink",
        "produce", "plant", "plant food", "natural foods", "superfood", "recipe",
        "tableware", "serveware", "kitchen utensil", "kitchen appliance", "packaging",
        "whole food", "local food", "comfort food", "fast food", "junk food",
        "health food", "staple food", "processed food", "fresh food", "raw food",
        "red", "green", "yellow", "orange", "blue", "purple", "brown", "white", "black",
        "round", "small", "large", "fresh", "cooked", "raw", "sliced", "chopped",
        "cibo", "frutta", "verdura", "carne", "pesce", "latticini", "ingrediente",
        "cucina", "piatto", "pasto", "spuntino", "bevanda", "prodotto", "pianta",
        "ricetta", "imballaggio", "contenitore", "stoviglia", "stoviglie",
        "utensile", "utensili", "elettrodomestico", "confezione", "bottiglia", "barattolo", "lattina"
    )

    private val scannerBlockedKeywords = setOf(
        "tableware", "serveware", "utensil", "kitchen utensil", "kitchen appliance",
        "packaging", "container", "bottle", "jar", "can", "cutlery", "dishware",
        "plate", "bowl", "cup", "glass", "fork", "knife", "spoon", "pot", "pan",
        "furniture", "wood", "table", "surface", "floor", "wall", "room", "indoor",
        "person", "people", "hand", "arm", "face", "human", "body",
        "stoviglia", "stoviglie", "utensile", "utensili", "elettrodomestico",
        "confezione", "contenitore", "bottiglia", "barattolo", "lattina",
        "piatto", "ciotola", "bicchiere", "forchetta", "coltello", "cucchiaio",
        "tavolo", "superficie", "persona", "mano", "mobile"
    )

    private val aliasesByCanonical = mapOf(
        "uovo" to setOf("egg", "eggs", "uovo", "uova", "chicken egg", "egg yolk", "egg white"),
        "pomodoro" to setOf("tomato", "tomatoes", "pomodoro", "pomodori", "cherry tomato", "cherry tomatoes", "tomato plant", "roma tomato"),
        "peperone" to setOf("pepper", "bell pepper", "peperone", "peperoni", "capsicum", "red pepper", "yellow pepper", "green pepper"),
        "cipolla" to setOf("onion", "onions", "cipolla", "cipolle", "red onion", "spring onion", "shallot"),
        "formaggio" to setOf("cheese", "formaggio", "formaggi", "cheddar", "brie", "gouda", "parmesan cheese", "cream cheese"),
        "pasta" to setOf("pasta", "spaghetti", "penne", "rigatoni", "fusilli", "linguine", "tagliatelle", "fettuccine"),
        "aglio" to setOf("garlic", "aglio", "garlic clove", "garlic bulb", "garlic head"),
        "basilico" to setOf("basil", "basilico", "fresh basil", "basil leaf"),
        "pollo" to setOf("chicken", "pollo", "chicken breast", "chicken leg", "chicken wing", "chicken thigh", "roast chicken"),
        "lattuga" to setOf("lettuce", "lattuga", "insalata"),
        "cetriolo" to setOf("cucumber", "cetriolo", "cetrioli"),
        "limone" to setOf("lemon", "limone", "limoni"),
        "carota" to setOf("carrot", "carota", "carote", "baby carrot", "carrot stick"),
        "patata" to setOf("potato", "potatoes", "patata", "patate", "sweet potato", "russet potato"),
        "sedano" to setOf("celery", "sedano"),
        "brodo" to setOf("broth", "stock", "brodo"),
        "riso" to setOf("rice", "riso"),
        "burro" to setOf("butter", "burro"),
        "fungo" to setOf("mushroom", "mushrooms", "fungo", "funghi", "champignon", "portobello", "shiitake"),
        "pane" to setOf("bread", "pane"),
        "avocado" to setOf("avocado"),
        "rosmarino" to setOf("rosemary", "rosmarino"),
        "olio d oliva" to setOf("olive oil", "oil", "olio", "olio d oliva", "olio di oliva"),
        "spinaci" to setOf("spinach", "spinaci", "spinacio"),
        "banana" to setOf("banana", "banane", "banana family"),
        "latte" to setOf("milk", "latte", "cow milk", "whole milk", "skim milk"),
        "mela" to setOf("apple", "apples", "mela", "mele", "granny smith", "red delicious", "golden delicious", "fuji apple", "apple fruit"),
        "yogurt" to setOf("yogurt", "yoghurt"),
        "mozzarella" to setOf("mozzarella"),
        "arancia" to setOf("orange", "oranges", "arancia", "arance", "navel orange", "blood orange", "mandarin", "clementine"),
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
        "tonno" to setOf("tuna", "tonno"),
        "capperi" to setOf("caper", "capers", "cappero", "capperi"),
        "origano" to setOf("oregano", "origano"),
        "timo" to setOf("thyme", "timo"),
        "menta" to setOf("mint", "menta"),
        "acciuga" to setOf("anchovy", "anchovies", "acciuga", "acciughe"),
        "senape" to setOf("mustard", "senape"),
        "aceto" to setOf("vinegar", "aceto"),
        "cannella" to setOf("cinnamon", "cannella"),
        "peperoncino" to setOf("chilli", "chili", "chilli sauce", "red chilli", "peperoncino"),
        "noce moscata" to setOf("nutmeg", "noce moscata"),
        "zafferano" to setOf("saffron", "zafferano"),
        "curcuma" to setOf("turmeric", "curcuma"),
        "paprika" to setOf("paprika"),
        "coriandolo" to setOf("coriander", "cilantro", "coriandolo"),
        "aneto" to setOf("dill", "aneto"),
        "salvia" to setOf("sage", "salvia"),
        "lampone" to setOf("raspberry", "raspberries", "lampone", "lamponi"),
        "fragola" to setOf("strawberry", "strawberries", "fragola", "fragole"),
        "pera" to setOf("pear", "pears", "pera", "pere"),
        "pesca" to setOf("peach", "peaches", "pesca", "pesche"),
        "ciliegia" to setOf("cherry", "cherries", "ciliegia", "ciliegie"),
        "uva" to setOf("grape", "grapes", "uva"),
        "anguria" to setOf("watermelon", "anguria"),
        "melone" to setOf("melon", "melone"),
        "ananas" to setOf("pineapple", "ananas"),
        "mango" to setOf("mango"),
        "kiwi" to setOf("kiwi"),
        "tofu" to setOf("tofu"),
        "ceci" to setOf("chickpea", "chickpeas", "ceci", "cece"),
        "mandorla" to setOf("almond", "almonds", "mandorla", "mandorle"),
        "noce" to setOf("walnut", "walnuts", "noce", "noci"),
        "nocciola" to setOf("hazelnut", "hazelnuts", "nocciola", "nocciole"),
        "pinolo" to setOf("pine nut", "pine nuts", "pinolo", "pinoli"),
        "uvetta" to setOf("raisin", "raisins", "uvetta"),
        "miele" to setOf("honey", "miele"),
        "cioccolato" to setOf("chocolate", "dark chocolate", "cioccolato"),
        "vaniglia" to setOf("vanilla", "vaniglia"),
        "lievito" to setOf("yeast", "baking powder", "lievito"),
        "panna acida" to setOf("sour cream", "panna acida"),
        "maionese" to setOf("mayonnaise", "mayo", "maionese"),
        "ketchup" to setOf("ketchup", "tomato sauce"),
        "salsa di soia" to setOf("soy sauce", "soya sauce", "salsa di soia"),
        "aceto balsamico" to setOf("balsamic vinegar", "aceto balsamico"),
        "olio di sesamo" to setOf("sesame oil", "olio di sesamo"),
        "tahini" to setOf("tahini"),
        "pesto" to setOf("pesto"),
        "polpo" to setOf("octopus", "polpo"),
        "calamaro" to setOf("squid", "calamaro", "calamari"),
        "vongola" to setOf("clam", "clams", "vongola", "vongole"),
        "cozza" to setOf("mussel", "mussels", "cozza", "cozze"),
        "merluzzo" to setOf("cod", "merluzzo"),
        "branzino" to setOf("sea bass", "branzino"),
        "orata" to setOf("sea bream", "bream", "orata"),
        "agnello" to setOf("lamb", "agnello"),
        "vitello" to setOf("veal", "vitello"),
        "tacchino" to setOf("turkey", "tacchino"),
        "anatra" to setOf("duck", "anatra"),
        "coniglio" to setOf("rabbit", "coniglio"),
        "speck" to setOf("speck"),
        "mortadella" to setOf("mortadella"),
        "coppa" to setOf("coppa"),
        "taleggio" to setOf("taleggio"),
        "gorgonzola" to setOf("gorgonzola"),
        "pecorino" to setOf("pecorino"),
        "ricotta" to setOf("ricotta"),
        "mascarpone" to setOf("mascarpone"),
        "parmigiano" to setOf("parmesan", "parmigiano", "parmigiano reggiano"),
        "rucola" to setOf("rocket", "arugula", "rucola"),
        "radicchio" to setOf("radicchio"),
        "cavolo" to setOf("cabbage", "cavolo"),
        "cavolo nero" to setOf("kale", "cavolo nero"),
        "bietola" to setOf("chard", "swiss chard", "bietola"),
        "asparago" to setOf("asparagus", "asparago", "asparagi"),
        "carciofo" to setOf("artichoke", "carciofo", "carciofi"),
        "finocchio" to setOf("fennel", "finocchio"),
        "porro" to setOf("leek", "porro", "porri"),
        "rape" to setOf("turnip", "rapa", "rape"),
        "castagna" to setOf("chestnut", "chestnuts", "castagna", "castagne"),
        "mais dolce" to setOf("sweet corn", "sweetcorn", "mais dolce"),
        "radice di zenzero" to setOf("ginger", "fresh ginger", "zenzero"),
        "zenzero" to setOf("ginger", "zenzero")
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
        "tonno" to "tuna",
        "capperi" to "capers",
        "origano" to "oregano",
        "timo" to "thyme",
        "menta" to "mint",
        "acciuga" to "anchovies",
        "senape" to "mustard",
        "aceto" to "white wine vinegar",
        "cannella" to "cinnamon",
        "peperoncino" to "red chilli",
        "noce moscata" to "nutmeg",
        "zafferano" to "saffron",
        "curcuma" to "turmeric",
        "paprika" to "paprika",
        "coriandolo" to "coriander",
        "aneto" to "dill",
        "salvia" to "sage",
        "lampone" to "raspberries",
        "fragola" to "strawberries",
        "pera" to "pears",
        "pesca" to "peach",
        "ciliegia" to "cherries",
        "uva" to "grapes",
        "anguria" to "watermelon",
        "melone" to "melon",
        "ananas" to "pineapple",
        "mango" to "mango",
        "kiwi" to "kiwi fruit",
        "tofu" to "tofu",
        "ceci" to "chickpeas",
        "mandorla" to "almonds",
        "noce" to "walnut",
        "nocciola" to "hazelnuts",
        "pinolo" to "pine nuts",
        "uvetta" to "raisins",
        "miele" to "honey",
        "cioccolato" to "chocolate",
        "vaniglia" to "vanilla",
        "lievito" to "baking powder",
        "panna acida" to "sour cream",
        "maionese" to "mayonnaise",
        "salsa di soia" to "soy sauce",
        "aceto balsamico" to "balsamic vinegar",
        "olio di sesamo" to "sesame oil",
        "tahini" to "tahini",
        "pesto" to "pesto",
        "polpo" to "octopus",
        "calamaro" to "squid",
        "vongola" to "clams",
        "cozza" to "mussels",
        "merluzzo" to "cod",
        "branzino" to "sea bass",
        "orata" to "bream",
        "agnello" to "lamb",
        "vitello" to "veal",
        "tacchino" to "turkey",
        "anatra" to "duck",
        "coniglio" to "rabbit",
        "ricotta" to "ricotta",
        "mascarpone" to "mascarpone",
        "parmigiano" to "parmesan",
        "rucola" to "rocket",
        "asparago" to "asparagus",
        "carciofo" to "artichoke",
        "finocchio" to "fennel",
        "porro" to "leek",
        "zenzero" to "ginger",
        "castagna" to "chestnuts",
        "radice di zenzero" to "ginger"
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

    fun isLikelyNonIngredientLabel(raw: String): Boolean {
        val normalized = normalize(raw)
        if (normalized.isBlank()) {
            return true
        }

        if (normalized in genericLabels || normalized in scannerBlockedKeywords) {
            return true
        }

        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        return tokens.any { token ->
            token in scannerBlockedKeywords || token in setOf("utensil", "appliance", "container", "packaging")
        }
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