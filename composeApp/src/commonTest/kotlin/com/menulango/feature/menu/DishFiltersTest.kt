package com.menulango.feature.menu

import com.menulango.data.menu.model.DishFlags
import com.menulango.data.menu.model.Price
import com.menulango.testDish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DishFiltersTest {
    private fun priced(amount: Double) = Price(amount, "EUR", "$amount€")

    private val salad = testDish("salad", flags = DishFlags.None.copy(vegetarian = true)).copy(price = priced(8.5))
    private val dip = testDish("dip", flags = DishFlags.None.copy(vegan = true)).copy(price = priced(5.5))
    private val souvlaki = testDish("souvlaki", flags = DishFlags.None.copy(pork = true)).copy(price = priced(11.0))
    private val kokoretsi =
        testDish("kokoretsi", flags = DishFlags.None.copy(offal = true, localSpecialty = true))
            .copy(price = priced(12.5))
    private val menu = listOf(salad, dip, souvlaki, kokoretsi)

    private fun ids(
        vararg filters: DishFilter,
        dishes: List<com.menulango.data.menu.model.Dish> = menu,
    ) = DishFilters(dishes).apply(filters.toSet()).map { it.id }

    @Test
    fun noFiltersKeepsTheWholeMenuInOrder() {
        assertEquals(listOf("salad", "dip", "souvlaki", "kokoretsi"), ids())
    }

    @Test
    fun veganDishesCountAsVegetarian() {
        assertEquals(listOf("salad", "dip"), ids(DishFilter.Vegetarian))
        assertEquals(listOf("dip"), ids(DishFilter.Vegan))
    }

    @Test
    fun filtersCombineAsAnd() {
        assertEquals(listOf("salad", "dip"), ids(DishFilter.NoPork, DishFilter.NoOffal))
        assertEquals(emptyList(), ids(DishFilter.Vegan, DishFilter.Local))
    }

    @Test
    fun budgetIsTheMenusOwnMedianRoundedUp() {
        val filters = DishFilters(menu)
        assertEquals(9.0, filters.budgetLimit)
        assertEquals(listOf("salad", "dip"), ids(DishFilter.Budget))
    }

    @Test
    fun budgetNeedsEnoughPricesToMeanAnything() {
        val filters = DishFilters(menu.take(3))
        assertNull(filters.budgetLimit)
        assertFalse(DishFilter.Budget in filters.options(emptySet()))
    }

    @Test
    fun onlyFiltersThatNarrowTheMenuAreOffered() {
        val options = DishFilters(menu).options(emptySet())
        assertTrue(DishFilter.Vegetarian in options)
        // Nothing on this menu is spicy, so "Not spicy" would keep every dish.
        assertFalse(DishFilter.NotSpicy in options)
        // Nothing is shareable, so "To share" would empty the list.
        assertFalse(DishFilter.ToShare in options)
    }

    @Test
    fun aSelectedFilterStaysOfferedSoItCanBeTurnedOff() {
        assertTrue(DishFilter.ToShare in DishFilters(menu).options(setOf(DishFilter.ToShare)))
    }
}

class AvoidWordsTest {
    private val soup = testDish("Wild mushroom soup").copy(ingredients = listOf("porcini", "cream"))
    private val salad = testDish("Salad").copy(ingredients = listOf("coriander", "lime"))
    private val bubbly = testDish("Champagne sorbet")

    @Test
    fun aWordMatchesAtTheStartOfAWordInTheNameOrIngredients() {
        assertTrue(mentions(soup, "mushroom"))
        assertTrue(mentions(salad, "Coriander"))
        assertFalse(mentions(bubbly, "ham"), "ham is not in champagne")
    }

    @Test
    fun avoidedDishesLeaveTheMenu() {
        val kept = DishFilters(listOf(soup, salad, bubbly)).apply(emptySet(), setOf("mushroom", "coriander"))
        assertEquals(listOf(bubbly), kept)
    }
}
