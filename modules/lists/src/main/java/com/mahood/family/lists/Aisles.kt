package com.mahood.family.lists

/** Very small keyword guesser so new items land in a sensible aisle. Users can still move them. */
object Aisles {
    val order = listOf("Fruit & veg", "Meat & fish", "Dairy & eggs", "Bakery", "Cupboard", "Frozen", "Drinks", "Household", "Other")

    private val keywords = mapOf(
        "Fruit & veg" to listOf("apple", "banana", "orange", "grape", "berry", "berries", "lemon", "lime", "pear", "melon", "tomato", "potato", "onion", "garlic", "carrot", "broccoli", "lettuce", "salad", "cucumber", "pepper", "mushroom", "spinach", "avocado", "courgette", "leek", "celery", "herb", "ginger", "kiwi", "mango", "peas", "beans"),
        "Meat & fish" to listOf("chicken", "beef", "pork", "lamb", "mince", "bacon", "sausage", "ham", "turkey", "fish", "salmon", "tuna", "cod", "prawn", "steak"),
        "Dairy & eggs" to listOf("milk", "cheese", "cheddar", "butter", "yogurt", "yoghurt", "cream", "egg", "eggs", "custard"),
        "Bakery" to listOf("bread", "loaf", "sourdough", "roll", "bagel", "croissant", "wrap", "pitta", "muffin", "cake", "crumpet"),
        "Cupboard" to listOf("pasta", "rice", "flour", "sugar", "oil", "cereal", "oats", "tin", "beans", "soup", "sauce", "ketchup", "jam", "honey", "coffee", "tea", "biscuit", "crisps", "spice", "salt", "stock", "noodle", "chocolate"),
        "Frozen" to listOf("frozen", "ice cream", "pizza", "chips", "ice"),
        "Drinks" to listOf("juice", "water", "squash", "lemonade", "cola", "wine", "beer", "gin"),
        "Household" to listOf("toilet", "kitchen roll", "bin bag", "washing", "detergent", "soap", "shampoo", "toothpaste", "bleach", "sponge", "foil", "cling", "battery", "batteries", "nappies", "tissues"),
    )

    fun guess(name: String): String {
        val n = name.lowercase()
        for (aisle in order) {
            val words = keywords[aisle] ?: continue
            if (words.any { n.contains(it) }) return aisle
        }
        return "Other"
    }
}
