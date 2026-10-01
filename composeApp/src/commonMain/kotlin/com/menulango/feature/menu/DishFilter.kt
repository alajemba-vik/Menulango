package com.menulango.feature.menu

import com.menulango.data.menu.model.Dish
import kotlin.math.ceil

/**
 * The diner's own narrowing of a long menu, as opposed to the choosing modes, which pick one dish
 * for them. Each filter reads the flags the model already returned, so filtering needs no second
 * call and works offline on a saved menu.
 */
internal enum class DishFilter {
    Vegetarian,
    Vegan,
    NoPork,

    /** No pork and no alcohol, by ingredients. Never a claim that the kitchen is halal. */
    HalalFriendly,

    /** No pork, shellfish, or meat served with dairy, by ingredients. Never a claim of kashrut. */
    KosherFriendly,
    NotSpicy,
    NothingRaw,
    NoOffal,
    Local,
    ToShare,

    /** At or under the menu's median price, so "cheap" means cheap for this restaurant. */
    Budget,
}

/**
 * Filtering rules for one menu. Pure, so the rules can be tested without Compose.
 *
 * @param dishes every dish read so far; the budget line is drawn from their prices.
 */
internal class DishFilters(
    private val dishes: List<Dish>,
) {
    /** The budget line, or null when too few dishes have a price for "cheap" to mean anything. */
    val budgetLimit: Double? =
        dishes
            .mapNotNull { it.price?.amount }
            .filter { it > 0 }
            .sorted()
            .takeIf { it.size >= MIN_PRICES_FOR_BUDGET }
            ?.let { prices -> ceil(prices[(prices.size - 1) / 2]) }

    fun matches(
        filter: DishFilter,
        dish: Dish,
    ): Boolean {
        val flags = dish.flags
        return when (filter) {
            DishFilter.Vegetarian -> flags.vegetarian || flags.vegan
            DishFilter.Vegan -> flags.vegan
            DishFilter.NoPork -> !flags.pork
            DishFilter.HalalFriendly -> !flags.pork && flags.alcohol == false
            DishFilter.KosherFriendly -> !flags.pork && flags.shellfish == false && flags.meatWithDairy == false
            DishFilter.NotSpicy -> flags.spicy <= 1
            DishFilter.NothingRaw -> !flags.raw
            DishFilter.NoOffal -> !flags.offal
            DishFilter.Local -> flags.localSpecialty
            DishFilter.ToShare -> flags.shareable
            DishFilter.Budget -> budgetLimit != null && dish.price?.amount?.let { it <= budgetLimit } == true
        }
    }

    /**
     * The dishes that pass every selected filter and mention none of the [avoid] words, in menu
     * order.
     */
    fun apply(
        selected: Set<DishFilter>,
        avoid: Set<String> = emptySet(),
    ): List<Dish> = dishes.filter { dish -> selected.all { matches(it, dish) } && avoid.none { mentions(dish, it) } }

    /**
     * The filters worth offering: those that would actually narrow this menu, keeping at least one
     * dish and removing at least one. A "Vegan" pill on a menu with no vegan dish would only ever
     * lead to an empty list. Selected filters always stay, so they can be turned off again.
     */
    fun options(selected: Set<DishFilter>): List<DishFilter> =
        DishFilter.entries.filter { filter ->
            filter in selected || dishes.count { matches(filter, it) } in 1 until dishes.size
        }

    private companion object {
        const val MIN_PRICES_FOR_BUDGET = 4
    }
}

/** The filters a standing profile may hold: what a diner can or won't eat, not a mood or a price. */
internal val DietaryFilters: List<DishFilter> =
    listOf(
        DishFilter.Vegetarian,
        DishFilter.Vegan,
        DishFilter.NoPork,
        DishFilter.HalalFriendly,
        DishFilter.KosherFriendly,
        DishFilter.NotSpicy,
        DishFilter.NothingRaw,
        DishFilter.NoOffal,
    )

internal fun Set<String>.toFilters(): Set<DishFilter> = DietaryFilters.filter { it.name in this }.toSet()

/**
 * Whether a dish mentions [word] — in its name, as printed, its ingredients or what it is — at the
 * start of a word, so "mushroom" finds "wild mushrooms" but "ham" does not find "champagne". Only
 * as good as what the menu and the model say: never an allergy guarantee.
 */
internal fun mentions(
    dish: Dish,
    word: String,
): Boolean {
    val needle = word.trim().lowercase()
    if (needle.isEmpty()) return false
    val haystack =
        (listOf(dish.readableName, dish.originalName, dish.whatItIs.orEmpty()) + dish.ingredients)
            .joinToString(" ")
            .lowercase()
    return Regex("(^|[^\\p{L}])" + Regex.escape(needle)).containsMatchIn(haystack)
}
