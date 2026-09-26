package com.menulango.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.menulango.core.design.Elevation
import com.menulango.core.design.FoodGroup
import com.menulango.core.design.Paper
import com.menulango.data.menu.model.Dish
import kotlin.random.Random

/**
 * A matte stoneware dish with the dish's emoji on it — the app's stand-in for food photography.
 * Grey clay rather than white china, and a soft square rather than a circle, like the hand-thrown
 * dishes modern kitchens plate on: a slightly darker well, a pressed inner lip and iron speckles.
 * Decorative: screen readers skip it, the dish name says it all.
 */
@Composable
internal fun DishPlate(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val colors = Paper.colors
    val fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() }
    val clay = if (colors.isDark) STONE_DARK else STONE_LIGHT
    val speck = if (colors.isDark) Color.White else Color(0xFF6E5D4E)
    val seed = emoji.hashCode()
    val shape = RoundedCornerShape(percent = DISH_CORNER_PERCENT)
    Box(
        modifier
            .size(size)
            .shadow(Elevation.resting, shape, clip = false)
            .clip(shape)
            .drawWithCache {
                val w = this.size.width
                val lip = w * LIP
                val corner = CornerRadius(w * DISH_CORNER_PERCENT / 100f * 0.8f)
                val random = Random(seed)
                val specks =
                    List(SPECKS) {
                        Triple(
                            Offset(random.nextFloat() * w, random.nextFloat() * w),
                            (0.35f + random.nextFloat() * 0.55f) * density,
                            0.14f + random.nextFloat() * 0.24f,
                        )
                    }
                onDrawBehind {
                    drawRect(clay)
                    // The well sits a shade deeper than the lip.
                    drawRoundRect(
                        Color.Black.copy(alpha = if (colors.isDark) 0.16f else 0.05f),
                        topLeft = Offset(lip, lip),
                        size = Size(w - lip * 2, w - lip * 2),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.09f),
                        topLeft = Offset(lip, lip),
                        size = Size(w - lip * 2, w - lip * 2),
                        cornerRadius = corner,
                        style = Stroke(width = 1f * density),
                    )
                    specks.forEach { (at, r, alpha) -> drawCircle(speck.copy(alpha = alpha), r, at) }
                }
            }.clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = fontSize)
    }
}

/** Grey clay: warm enough to sit on cream felt and every food colour, never white. */
private val STONE_LIGHT = Color(0xFFDCD6CE)
private val STONE_DARK = Color(0xFF3A3431)
private const val DISH_CORNER_PERCENT = 32
private const val LIP = 0.12f
private const val SPECKS = 26

@Composable
internal fun DishPlate(
    dish: Dish,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) = DishPlate(dish.emoji(), modifier, size)

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

/** The kind of food a dish is, read from its emoji, so the colour always agrees with the plate. */
internal fun Dish.foodGroup(): FoodGroup = foodGroupOf(emoji())

internal fun foodGroupOf(emoji: String): FoodGroup =
    FOOD_GROUPS.entries.firstOrNull { (_, emojis) -> emoji.replace(VARIATION_SELECTOR, "") in emojis }?.key
        ?: FoodGroup.Hearth

private const val VARIATION_SELECTOR = "\uFE0F"

private val FOOD_GROUPS: Map<FoodGroup, Set<String>> =
    mapOf(
        FoodGroup.Sea to setOf("🐙", "🦑", "🍤", "🦀", "🦞", "🦪", "🐟", "🐠", "🐡", "🍣", "🍥", "🦐"),
        FoodGroup.Garden to
            setOf("🥗", "🥦", "🍆", "🍅", "🍄", "🫒", "🥣", "🥑", "🥕", "🌽", "🥒", "🫑", "🥬", "🌶", "🧆", "🫛"),
        FoodGroup.Grill to setOf("🍖", "🥩", "🍗", "🥓", "🍔", "🌮", "🥙", "🌭", "🍢", "🌯"),
        FoodGroup.Sweet to
            setOf(
                "🍰",
                "🥧",
                "🍨",
                "🍫",
                "🍓",
                "🥞",
                "🍩",
                "🍪",
                "🧁",
                "🍮",
                "🍦",
                "🍯",
                "🍑",
                "🍒",
                "🍎",
                "🍉",
                "🍋",
                "🍧",
                "🎂",
            ),
        FoodGroup.Drink to
            setOf("☕", "🍵", "🍷", "🍺", "🍸", "🧃", "🥤", "🍹", "🥂", "🍶", "🧉", "🥛", "🍾", "🍻", "🧋"),
    )

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
