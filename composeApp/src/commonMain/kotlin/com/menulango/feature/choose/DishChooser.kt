package com.menulango.feature.choose

import com.menulango.data.menu.local.normalizeDishName
import com.menulango.data.menu.model.Dish

/**
 * The four ways MenuLango helps someone choose.
 *
 * "Keep it simple" sits beside the brave options on purpose: an app that only ever pushes you to
 * be adventurous is exhausting by the third night of a holiday.
 */
internal enum class ChoiceMode {
    SomethingNew,
    SomethingSpecial,
    OnlyHere,
    KeepItSimple,
}

/**
 * Ranks the dishes already on the menu for a [ChoiceMode] — locally, with no second model call.
 *
 * Only dishes with a reason ([Dish.pitch]) and a confident explanation are eligible: the app never
 * recommends a bare name, and never recommends a dish it is guessing about.
 */
internal object DishChooser {
    /**
     * @param eatenDishKeys dishes the diner has marked as eaten, keyed by [dishHistoryKey].
     * @return candidates best-first; empty when nothing on this menu fits the mode.
     */
    fun rank(
        mode: ChoiceMode,
        dishes: List<Dish>,
        eatenDishKeys: Set<String> = emptySet(),
    ): List<Dish> {
        val eligible = dishes.filter { !it.pitch.isNullOrBlank() && !it.isBestGuess }
        return when (mode) {
            ChoiceMode.SomethingNew -> {
                eligible
                    .filter { dishHistoryKey(it) !in eatenDishKeys }
                    .sortedWith(byDescending { it.adventureLevel }.thenDescending { it.effortLevel })
            }

            ChoiceMode.SomethingSpecial -> {
                eligible.sortedWith(byDescending { it.effortLevel }.thenDescending { it.adventureLevel })
            }

            ChoiceMode.OnlyHere -> {
                eligible
                    .filter { it.flags.localSpecialty }
                    .sortedWith(byDescending { it.adventureLevel }.thenDescending { it.effortLevel })
            }

            ChoiceMode.KeepItSimple -> {
                eligible
                    .filter {
                        !it.flags.raw && !it.flags.offal && it.flags.spicy <= MILD &&
                            it.adventureLevel <= SIMPLE_CEILING
                    }.sortedWith(compareBy<Dish> { it.adventureLevel }.thenByDescending { it.confidence })
            }
        }
    }

    private const val MILD = 1
    private const val SIMPLE_CEILING = 2

    // Ties fall back to model confidence, then to menu order (sortedWith is stable).
    private fun byDescending(selector: (Dish) -> Int): Comparator<Dish> = compareByDescending(selector)

    private fun Comparator<Dish>.thenDescending(selector: (Dish) -> Int): Comparator<Dish> =
        then(compareByDescending(selector)).then(compareByDescending { it.confidence })
}

/**
 * The identity of a dish in the diner's eating history.
 *
 * Uses the readable name rather than the id: "moussaka" eaten in Athens is the same experience as
 * "moussaka" on a menu in Thessaloniki, even though the two menus assign different ids.
 */
internal fun dishHistoryKey(dish: Dish): String = normalizeDishName(dish.readableName)
