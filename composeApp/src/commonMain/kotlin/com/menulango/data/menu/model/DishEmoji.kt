package com.menulango.data.menu.model

/**
 * The emoji that best pictures a dish: the one the model chose, or else a guess read from its
 * name, then its ingredients, then its menu section. Local and offline, so saved menus get plates too. Keywords are matched at the start of
 * a word ("anchov" catches "anchovies", "tea" does not catch "steak") and the most specific come
 * first, so "fried cheese" is cheese, not something fried.
 */
internal fun Dish.emoji(): String =
    emoji ?: sequenceOf(readableName, ingredients.take(INGREDIENTS_CONSIDERED).joinToString(" "), section.orEmpty())
        .map(::emojiFor)
        .firstOrNull { it != null }
        ?: DEFAULT_EMOJI

internal fun emojiFor(text: String): String? {
    val words =
        " " +
            text
                .lowercase()
                .split(Regex("[^\\p{L}]+"))
                .filter { it.isNotEmpty() }
                .joinToString(" ")
    return DISH_EMOJI.firstOrNull { (keywords, _) -> keywords.any { words.contains(" $it") } }?.second
}

private const val DEFAULT_EMOJI = "🍽️"
private const val INGREDIENTS_CONSIDERED = 3

private val DISH_EMOJI: List<Pair<List<String>, String>> =
    listOf(
        listOf("ice cream", "gelato", "sorbet", "frozen yogh") to "🍨",
        listOf("hamburger", "cheeseburger", "burger") to "🍔",
        listOf("pizza", "calzone") to "🍕",
        listOf("sushi", "sashimi", "maki", "nigiri") to "🍣",
        listOf("octopus") to "🐙",
        listOf("squid", "calamar", "cuttlefish") to "🦑",
        listOf("shrimp", "prawn", "scampi", "langoustine") to "🍤",
        listOf("crab", "lobster", "crayfish") to "🦀",
        listOf("oyster", "mussel", "clam", "scallop", "seafood") to "🦪",
        listOf("aubergine", "eggplant", "moussaka", "melitzan") to "🍆",
        listOf("tomato", "stuffed pepper", "gemist") to "🍅",
        listOf("mushroom", "truffle") to "🍄",
        listOf("dip", "hummus", "tzatziki", "yoghurt", "yogurt") to "🥣",
        listOf("salad", "slaw") to "🥗",
        listOf("cheese", "feta", "halloumi", "saganaki", "mozzarella", "burrata", "fondue") to "🧀",
        listOf("fish", "cod", "salmon", "tuna", "sardine", "anchov", "bream", "bass", "trout", "mackerel", "hake") to
            "🐟",
        listOf("dumpling", "gyoza", "momo", "pierog", "wonton", "dim sum", "bao") to "🥟",
        listOf("noodle", "ramen", "pho", "udon", "soba", "pad thai") to "🍜",
        listOf(
            "pasta",
            "spaghetti",
            "lasagn",
            "ravioli",
            "gnocchi",
            "macaroni",
            "tagliatelle",
            "linguine",
            "penne",
            "pastitsio",
        ) to "🍝",
        listOf("taco", "burrito", "quesadilla", "nacho", "enchilada") to "🌮",
        listOf("curry", "dal", "masala", "korma", "vindaloo") to "🍛",
        listOf("paella", "tagine") to "🥘",
        listOf("risotto", "rice", "pilaf", "biryani") to "🍚",
        listOf("soup", "broth", "stew", "chowder", "bisque", "goulash") to "🍲",
        listOf("sandwich", "wrap", "gyro", "pita", "panini", "toastie") to "🥙",
        listOf("fries", "chips", "potato") to "🍟",
        listOf("egg", "omelet", "frittata", "shakshuka") to "🍳",
        listOf("steak", "beef", "veal", "entrecote", "ribeye", "sirloin") to "🥩",
        listOf("chicken", "poultry", "duck", "turkey", "wings") to "🍗",
        listOf("bacon", "ham", "sausage", "chorizo", "pork", "loukanik") to "🥓",
        listOf("vegetable", "veggie", "greens", "broccoli", "zucchini", "courgette") to "🥦",
        listOf("lamb", "kebab", "souvlaki", "meatball", "kofte", "keftedes", "offal", "liver", "grill", "meat") to
            "🍖",
        listOf("pancake", "crepe", "waffle") to "🥞",
        listOf("pie", "tart", "baklava", "pastry", "strudel", "galaktoboureko") to "🥧",
        listOf("cake", "cheesecake", "tiramisu", "brownie", "dessert", "sweet") to "🍰",
        listOf("chocolate", "cocoa") to "🍫",
        listOf("fruit", "strawberr", "berry", "berries") to "🍓",
        listOf("bread", "focaccia", "baguette", "flatbread") to "🍞",
        listOf("olive") to "🫒",
        listOf("coffee", "espresso", "cappuccino", "latte") to "☕",
        listOf("tea", "matcha", "chai") to "🍵",
        listOf("wine", "sangria", "prosecco") to "🍷",
        listOf("beer", "ale", "lager") to "🍺",
        listOf("cocktail", "spritz", "mojito", "margarita", "ouzo", "raki") to "🍸",
        listOf("juice", "lemonade", "smoothie", "soda") to "🧃",
    )
