package com.menulango.feature.choose

import com.menulango.data.menu.model.DishFlags
import com.menulango.testDish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DishChooserTest {
    private val salad = testDish("salad", adventure = 1, effort = 1)
    private val moussaka = testDish("moussaka", adventure = 2, effort = 5)
    private val kokoretsi =
        testDish(
            "kokoretsi",
            adventure = 5,
            effort = 4,
            flags = DishFlags.None.copy(offal = true, localSpecialty = true),
        )
    private val octopus =
        testDish("octopus", adventure = 4, effort = 3, flags = DishFlags.None.copy(localSpecialty = true))
    private val tartare = testDish("tartare", adventure = 1, effort = 2, flags = DishFlags.None.copy(raw = true))
    private val menu = listOf(salad, moussaka, kokoretsi, octopus, tartare)

    private fun top(
        mode: ChoiceMode,
        dishes: List<com.menulango.data.menu.model.Dish> = menu,
        eaten: Set<String> = emptySet(),
    ) = DishChooser.rank(mode, dishes, eaten).firstOrNull()?.id

    @Test
    fun somethingNewPicksTheMostAdventurousDishNotYetEaten() {
        assertEquals("kokoretsi", top(ChoiceMode.SomethingNew))
        assertEquals("octopus", top(ChoiceMode.SomethingNew, eaten = setOf("kokoretsi")))
    }

    @Test
    fun somethingSpecialPicksTheMostWorkToMake() {
        assertEquals("moussaka", top(ChoiceMode.SomethingSpecial))
    }

    @Test
    fun onlyHerePicksTheMostAdventurousLocalSpecialty() {
        assertEquals("kokoretsi", top(ChoiceMode.OnlyHere))
        assertEquals(listOf("kokoretsi", "octopus"), DishChooser.rank(ChoiceMode.OnlyHere, menu).map { it.id })
    }

    @Test
    fun keepItSimpleNeverOffersRawOffalOrSpicy() {
        val hot = testDish("hot", adventure = 1, flags = DishFlags.None.copy(spicy = 3))
        val ranked = DishChooser.rank(ChoiceMode.KeepItSimple, menu + hot).map { it.id }

        assertEquals(listOf("salad", "moussaka"), ranked)
    }

    @Test
    fun dishesWithoutAReasonOrWithAGuessedExplanationAreNeverChosen() {
        val bare = testDish("bare", adventure = 5, effort = 5, pitch = null)
        val guess = testDish("guess", adventure = 5, effort = 5, confidence = 0.3)

        assertEquals("kokoretsi", top(ChoiceMode.SomethingNew, menu + bare + guess))
        assertEquals("moussaka", top(ChoiceMode.SomethingSpecial, menu + bare + guess))
    }

    @Test
    fun modeWithNoCandidateReturnsEmpty() {
        assertTrue(DishChooser.rank(ChoiceMode.OnlyHere, listOf(salad, moussaka)).isEmpty())
    }

    @Test
    fun tiesBreakOnConfidence() {
        val sure = testDish("sure", adventure = 5, effort = 4, confidence = 0.95)
        assertEquals("sure", top(ChoiceMode.SomethingNew, listOf(kokoretsi, sure)))
    }
}
