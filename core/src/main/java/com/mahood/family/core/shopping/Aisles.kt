package com.mahood.family.core.shopping

/**
 * Guesses which aisle a shopping item belongs in.
 *
 * Matching is done on whole words (with simple plural handling), never on
 * substrings, so "shampoo" no longer matches "ham" and "pineapple" is not an
 * apple. When several keywords match, the longest phrase wins, then the one
 * at the end of the name ("chicken soup" is soup, "apple juice" is juice).
 * Corrections people make in the app are remembered and win over the guess.
 */
object Aisles {
    const val OTHER = "Other"

    val order = listOf(
        "Fruit & veg", "Meat & fish", "Dairy & eggs", "Bakery", "Cupboard", "Frozen", "Drinks",
        "Baby", "Toiletries & health", "Household", OTHER,
    )

    val emoji = mapOf(
        "Fruit & veg" to "🥕", "Meat & fish" to "🥩", "Dairy & eggs" to "🥛", "Bakery" to "🍞",
        "Cupboard" to "🥫", "Frozen" to "🧊", "Drinks" to "🧃", "Baby" to "🍼",
        "Toiletries & health" to "🪥", "Household" to "🧽", OTHER to "🛒",
    )

    private val keywords: Map<String, List<String>> = mapOf(
        "Fruit & veg" to listOf(
            "apple", "banana", "orange", "satsuma", "clementine", "tangerine", "grape", "berry", "strawberry", "raspberry",
            "blueberry", "blackberry", "lemon", "lime", "pear", "melon", "watermelon", "pineapple", "peach", "nectarine",
            "plum", "cherry", "kiwi", "mango", "papaya", "pomegranate", "fig", "date", "rhubarb", "tomato", "cherry tomato",
            "potato", "sweet potato", "onion", "red onion", "spring onion", "shallot", "garlic", "carrot", "parsnip", "swede",
            "turnip", "beetroot", "broccoli", "cauliflower", "cabbage", "kale", "lettuce", "salad", "rocket", "spinach",
            "cucumber", "pepper", "chilli", "mushroom", "avocado", "courgette", "aubergine", "squash", "butternut squash",
            "pumpkin", "leek", "celery", "asparagus", "sweetcorn", "corn on the cob", "pea", "mangetout", "green bean",
            "runner bean", "sprout", "brussels sprout", "herb", "basil", "coriander", "parsley", "mint", "rosemary",
            "thyme", "ginger", "veg", "vegetable", "fruit",
        ),
        "Meat & fish" to listOf(
            "chicken", "chicken breast", "chicken thigh", "beef", "pork", "lamb", "mince", "steak", "bacon", "sausage",
            "ham", "turkey", "duck", "gammon", "chorizo", "salami", "pepperoni", "meatball", "burger", "chop", "joint",
            "fish", "salmon", "tuna steak", "cod", "haddock", "pollock", "mackerel", "trout", "prawn", "shrimp", "mussel",
            "seabass", "sea bass", "kipper", "meat",
        ),
        "Dairy & eggs" to listOf(
            "milk", "oat milk", "soya milk", "cheese", "cheddar", "mozzarella", "parmesan", "feta", "halloumi", "brie",
            "cream cheese", "butter", "spread", "yogurt", "yoghurt", "fromage frais", "cream", "double cream", "single cream",
            "creme fraiche", "sour cream", "egg", "custard", "dairylea", "babybel",
        ),
        "Bakery" to listOf(
            "bread", "loaf", "sourdough", "roll", "bread roll", "bun", "bagel", "croissant", "wrap", "tortilla", "pitta",
            "naan", "muffin", "crumpet", "pancake", "scone", "teacake", "brioche", "baguette", "cake", "doughnut", "donut",
        ),
        "Cupboard" to listOf(
            "pasta", "spaghetti", "penne", "fusilli", "lasagne", "noodle", "rice", "couscous", "quinoa", "flour", "sugar",
            "oil", "olive oil", "vinegar", "cereal", "porridge", "oat", "weetabix", "cornflakes", "granola", "tin", "tinned",
            "baked bean", "bean", "chickpea", "lentil", "chopped tomato", "tomato puree", "passata", "soup", "sauce",
            "pasta sauce", "soy sauce", "ketchup", "mayo", "mayonnaise", "mustard", "jam", "honey", "marmalade",
            "peanut butter", "nutella", "spread", "coffee", "tea", "tea bag", "hot chocolate", "biscuit", "cracker",
            "crisp", "popcorn", "chocolate", "sweet", "raisin", "nut", "spice", "salt", "stock", "stock cube", "gravy",
            "herbs and spices", "curry paste", "tomato paste", "coconut milk", "tuna", "sweetcorn tin", "cracker",
            "rice cake", "oatcake", "breadstick", "snack", "baking powder", "yeast", "vanilla",
        ),
        "Frozen" to listOf(
            "frozen", "ice cream", "ice lolly", "lolly", "ice", "frozen pea", "fish finger", "chip", "oven chip",
            "potato waffle", "waffle", "pizza", "frozen veg", "garlic bread", "yorkshire pudding",
        ),
        "Drinks" to listOf(
            "juice", "orange juice", "apple juice", "water", "sparkling water", "squash", "lemonade", "cola", "coke",
            "fizzy", "pop", "wine", "beer", "lager", "cider", "gin", "vodka", "whisky", "prosecco", "irn bru", "smoothie",
        ),
        "Baby" to listOf(
            "nappy", "nappies", "baby wipe", "wipe", "formula", "baby formula", "baby milk", "follow on milk", "baby food",
            "pouch", "baby pouch", "puffs", "baby rice", "rusk", "teether", "dummy", "bottle", "nappy bag", "sudocrem",
            "metanium", "bepanthen", "baby bath", "baby shampoo", "swim nappy", "pull up", "training pant",
        ),
        "Toiletries & health" to listOf(
            "toothpaste", "toothbrush", "floss", "mouthwash", "shampoo", "conditioner", "shower gel", "body wash",
            "bubble bath", "soap", "hand wash", "deodorant", "razor", "shaving", "moisturiser", "lotion", "sun cream",
            "suncream", "plaster", "calpol", "paracetamol", "ibuprofen", "nurofen", "piriton", "medicine", "vitamin",
            "tampon", "sanitary", "pad", "cotton wool", "cotton bud", "tissue", "lip balm", "hairbrush", "hair",
            "contact lens", "thermometer",
        ),
        "Household" to listOf(
            "toilet roll", "toilet paper", "loo roll", "kitchen roll", "bin bag", "bin liner", "washing up liquid",
            "fairy liquid", "dishwasher tablet", "dishwasher salt", "rinse aid", "washing powder", "washing liquid",
            "laundry", "detergent", "fabric softener", "fabric conditioner", "antibacterial wipe", "anti bacterial wipe", "cleaning wipe", "kitchen wipe", "floor wipe", "bleach", "cleaner", "spray",
            "anti bacterial", "antibacterial", "disinfectant", "sponge", "cloth", "scourer", "foil", "tin foil",
            "cling film", "clingfilm", "baking paper", "freezer bag", "sandwich bag", "battery", "batteries", "light bulb",
            "bulb", "candle", "match", "lighter", "air freshener", "polish", "glove", "rubber glove", "dog food",
            "cat food", "cat litter", "pet food",
        ),
    )

    /** Each keyword split into words, with the aisle it belongs to. */
    private val phrases: List<Pair<List<String>, String>> = keywords.flatMap { (aisle, words) ->
        words.map { normalise(it).split(' ') to aisle }
    }

    /** Lower case, letters and digits only, single spaces. Also the key for remembered corrections. */
    fun normalise(text: String): String =
        text.lowercase().replace('é', 'e').replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The word itself plus simple singular forms: berries -> berry, tomatoes -> tomato, eggs -> egg. */
    private fun forms(word: String): Set<String> {
        val out = mutableSetOf(word)
        if (word.length > 3) {
            if (word.endsWith("ies")) out += word.dropLast(3) + "y"
            if (word.endsWith("es")) out += word.dropLast(2)
            if (word.endsWith("s") && !word.endsWith("ss")) out += word.dropLast(1)
        }
        return out
    }

    /**
     * The best aisle for [name]. [learned] maps a normalised item name to the aisle
     * someone in the family moved it to; that always wins.
     */
    fun guess(name: String, learned: Map<String, String> = emptyMap()): String {
        val norm = normalise(name)
        if (norm.isEmpty()) return OTHER
        learned[norm]?.let { if (it in order) return it }
        val tokens = norm.split(' ').map(::forms)
        var best: String? = null
        var bestScore = -1
        for ((phrase, aisle) in phrases) {
            if (phrase.size > tokens.size) continue
            for (start in 0..tokens.size - phrase.size) {
                val matches = phrase.indices.all { phrase[it] in tokens[start + it] }
                if (!matches) continue
                val endsName = start + phrase.size == tokens.size
                val score = phrase.size * 10 + (if (endsName) 5 else 0)
                if (score > bestScore) {
                    bestScore = score
                    best = aisle
                }
            }
        }
        return best ?: OTHER
    }
}

/** Splits a typed item into quantity and name. */
object Quantity {
    /** "2 milk", "2x milk", "milk x2", "500g mince", "2 packs of nappies" -> (qty, name). */
    fun parse(text: String): Pair<String?, String> {
        val t = text.trim()
        Regex("^(\\d+(?:\\.\\d+)?\\s*(?:g|kg|ml|l|ltr|litres?|pints?|packs?|tins?|bags?|boxes|box|bottles?|cartons?|jars?)?)\\s*(?:\\s+of)?\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(t)?.let { return it.groupValues[1].trim() to it.groupValues[2].trim() }
        Regex("^(\\d+)\\s*[xX]\\s*(.+)$").find(t)?.let { return it.groupValues[1] to it.groupValues[2].trim() }
        Regex("^(.+?)\\s+[xX]\\s*(\\d+)$").find(t)?.let { return it.groupValues[2] to it.groupValues[1].trim() }
        return null to t
    }

}
