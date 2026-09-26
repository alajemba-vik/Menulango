package com.menulango.data.menu

import com.menulango.data.menu.model.MenuMeta
import com.menulango.testDish
import kotlin.test.Test
import kotlin.test.assertEquals

class PageMergerTest {
    private fun dish(
        id: String,
        printed: String = id.uppercase(),
    ) = testDish(id).copy(originalName = printed)

    @Test
    fun pagesJoinInPageOrder() {
        val merged = PageMerger.dishes(listOf(listOf(dish("salad"), dish("dip")), listOf(dish("octopus"))))
        assertEquals(listOf("salad", "dip", "octopus"), merged.map { it.id })
    }

    @Test
    fun aDishPrintedOnTwoPagesAppearsOnceWhereItWasFirstSeen() {
        val merged =
            PageMerger.dishes(
                listOf(
                    listOf(dish("creme", "Crème brûlée")),
                    listOf(dish("octopus"), dish("creme-again", "CREME BRULEE")),
                ),
            )
        assertEquals(listOf("creme", "octopus"), merged.map { it.id })
    }

    @Test
    fun eachDishRemembersThePageItWasFirstPrintedOn() {
        val pages = listOf(listOf(dish("salad")), listOf(dish("octopus"), dish("salad-again", "SALAD")))
        assertEquals(mapOf("salad" to 1, "octopus" to 2), PageMerger.pageOfDish(pages))
    }

    @Test
    fun idsChosenOnDifferentPagesAreMadeUnique() {
        val merged = PageMerger.dishes(listOf(listOf(dish("soup", "Fakes")), listOf(dish("soup", "Fasolada"))))
        assertEquals(listOf("soup", "soup-2"), merged.map { it.id })
    }

    @Test
    fun theFirstPageThatCouldTellNamesTheMenu() {
        val meta =
            PageMerger.meta(
                listOf(
                    MenuMeta.Unknown.copy(confidence = 0.4),
                    MenuMeta(
                        language = "el",
                        currency = "EUR",
                        venueType = "taverna",
                        truncated = true,
                        confidence = 0.9,
                    ),
                ),
            )
        assertEquals("el", meta.language)
        assertEquals("taverna", meta.venueType)
        assertEquals(true, meta.truncated)
        assertEquals(0.9, meta.confidence)
    }
}
