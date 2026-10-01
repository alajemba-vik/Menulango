package com.menulango.data.order

import com.menulango.data.menu.model.Nutrition
import com.menulango.data.menu.model.Price
import com.menulango.testDish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableOrderTest {
    private val salad = testDish("salad").copy(price = Price(8.5, "EUR", "8,50€"))
    private val octopus = testDish("octopus").copy(price = Price(16.0, "EUR", "16,00€"))

    @Test
    fun addingTheSameDishTwiceRaisesItsQuantity() {
        val order = TableOrder().add(salad).add(salad)
        assertEquals(1, order.lines.size)
        assertEquals(2, order.quantityOf("salad"))
    }

    @Test
    fun newDishesJoinWhoeverIsBeingOrderedFor() {
        val order = TableOrder().add(salad).addDiner("Ana").add(octopus)
        val ana = order.diners.last().id
        assertEquals(listOf("salad"), order.linesFor(TableOrder.OWNER).map { it.dish.id })
        assertEquals(listOf("octopus"), order.linesFor(ana).map { it.dish.id })
        assertEquals(2, order.dishCount)
    }

    @Test
    fun takingTheLastOneAwayRemovesTheLine() {
        val order = TableOrder().add(salad).change(salad, TableOrder.OWNER, -1)
        assertTrue(order.isEmpty)
    }

    @Test
    fun nutritionAddsUpEachPersonsEstimatedPlatesAndCountsTheRest() {
        val soup = testDish("soup").copy(nutrition = Nutrition(kcal = 300, proteinG = 10, carbsG = 30, fatG = 12))
        val wine = testDish("wine")
        val order =
            TableOrder()
                .add(soup)
                .add(soup)
                .add(wine)
                .addDiner("Ana")
        val mine = order.nutritionFor(TableOrder.OWNER)!!
        assertEquals(600, mine.kcal)
        assertEquals(20, mine.proteinG)
        assertEquals(2, mine.platesCounted)
        assertEquals(3, mine.plates)
        assertTrue(mine.isPartial)
        assertNull(order.nutritionFor(order.diners.last().id))
    }

    @Test
    fun theTotalIsOnlyGivenWhenEveryDishHasAPrice() {
        assertEquals(
            33.0,
            TableOrder()
                .add(salad)
                .add(salad)
                .add(octopus)
                .total,
        )
        assertNull(TableOrder().add(salad).add(testDish("unpriced")).total)
    }

    @Test
    fun removingAGuestTakesTheirDishesAndHandsTheOrderBack() {
        val withAna = TableOrder().addDiner("Ana").add(octopus)
        val order = withAna.removeDiner(withAna.activeDinerId)
        assertTrue(order.isEmpty)
        assertEquals(TableOrder.OWNER, order.activeDinerId)
        assertEquals(order, order.removeDiner(TableOrder.OWNER), "the phone's owner always stays")
    }

    @Test
    fun theOrderBookKeepsOneOrderPerMenu() {
        val book = OrderBook()
        book.update("taverna") { it.add(salad) }
        book.update("bistro") { it.add(octopus).add(octopus) }
        assertEquals(1, book.current("taverna").dishCount)
        assertEquals(2, book.current("bistro").dishCount)
        assertTrue(book.current("elsewhere").isEmpty)
    }

    @Test
    fun aNoteBelongsToOnePersonsDishAndBlankClearsIt() {
        val order = TableOrder().add(salad).addDiner("Ana").add(salad)
        val ana = order.activeDinerId
        val noted = order.setNote("salad", ana, "  no onions ")
        assertEquals("no onions", noted.lineFor("salad", ana)?.note)
        assertNull(noted.lineFor("salad", TableOrder.OWNER)?.note)
        assertNull(noted.setNote("salad", ana, " ").lineFor("salad", ana)?.note)
    }
}
