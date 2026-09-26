package com.menulango.data.search

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextSearchTest {
    @Test
    fun accentsAndCaseAreIgnored() {
        assertTrue(TextSearch.matches("creme brulee", listOf("Crème Brûlée")))
    }

    @Test
    fun everyWordMustAppearInAnyOrder() {
        assertTrue(TextSearch.matches("octopus grilled", listOf("Grilled octopus")))
        assertFalse(TextSearch.matches("octopus lamb", listOf("Grilled octopus")))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(TextSearch.matches("  ", listOf("Anything")))
    }

    @Test
    fun onlyTheGivenFieldsAreSearched() {
        assertFalse(TextSearch.matches("garlic", listOf("Tzatziki", null)))
        assertTrue(TextSearch.matches("garlic", listOf("Tzatziki", "yoghurt, garlic, cucumber")))
    }
}
