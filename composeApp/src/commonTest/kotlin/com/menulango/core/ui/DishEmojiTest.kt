package com.menulango.core.ui

import com.menulango.core.design.FoodGroup
import com.menulango.data.menu.model.emoji
import com.menulango.data.menu.model.emojiFor
import com.menulango.testDish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DishEmojiTest {
    @Test
    fun theMostSpecificWordWins() {
        assertEquals("🧀", emojiFor("Fried cheese"))
        assertEquals("🐙", emojiFor("Grilled octopus"))
        assertEquals("🍆", emojiFor("Smoky aubergine dip"))
        assertEquals("🍅", emojiFor("Stuffed tomatoes and peppers"))
    }

    @Test
    fun keywordsMatchAtTheStartOfAWordOnly() {
        assertEquals("🐟", emojiFor("Fried anchovies"))
        // "tea" must not be found inside "steak".
        assertEquals("🥩", emojiFor("Steak frites"))
        assertNull(emojiFor("Kokoretsi"))
    }

    @Test
    fun theModelsOwnEmojiWinsOverTheGuess() {
        assertEquals("🦑", testDish("Grilled octopus").copy(emoji = "🦑").emoji())
    }

    @Test
    fun fallsBackToIngredientsThenSectionThenAPlate() {
        val kokoretsi = testDish("Kokoretsi").copy(ingredients = listOf("lamb offal", "lemon"))
        assertEquals("🍖", kokoretsi.emoji())
        assertEquals("🍰", testDish("Loukoumades").copy(section = "Desserts").emoji())
        assertEquals("🍽️", testDish("Specialità della casa").emoji())
    }

    @Test
    fun theColourFollowsTheKindOfFood() {
        assertEquals(FoodGroup.Sea, foodGroupOf("🐙"))
        assertEquals(FoodGroup.Garden, foodGroupOf("🥗"))
        assertEquals(FoodGroup.Grill, testDish("Lamb souvlaki").foodGroup())
        assertEquals(FoodGroup.Sweet, foodGroupOf("🍰"))
        assertEquals(FoodGroup.Drink, foodGroupOf("☕️"))
        // Anything unlisted, including the default plate, sits with the kitchen's staples.
        assertEquals(FoodGroup.Hearth, foodGroupOf("🧀"))
        assertEquals(FoodGroup.Hearth, foodGroupOf("🍽️"))
    }
}
