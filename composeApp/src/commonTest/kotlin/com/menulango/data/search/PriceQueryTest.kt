package com.menulango.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceQueryTest {
    @Test
    fun plainTextHasNoPrice() {
        val q = PriceQuery.parse("grilled octopus")
        assertFalse(q.hasPrice)
        assertEquals("grilled octopus", q.text)
    }

    @Test
    fun underWordsAndSymbols() {
        assertEquals(15.0, PriceQuery.parse("under 15").max)
        assertEquals(15.0, PriceQuery.parse("< 15").max)
        assertEquals(12.5, PriceQuery.parse("<12,50").max)
        assertEquals(12.0, PriceQuery.parse("moins de 12 €").max)
        assertEquals(800.0, PriceQuery.parse("800以下").max)
    }

    @Test
    fun overAndRanges() {
        assertEquals(20.0, PriceQuery.parse("over 20").min)
        val range = PriceQuery.parse("€10-25")
        assertEquals(10.0, range.min)
        assertEquals(25.0, range.max)
    }

    @Test
    fun textAndPriceTogether() {
        val q = PriceQuery.parse("pepper under 12")
        assertEquals("pepper", q.text)
        assertEquals(12.0, q.max)
        assertNull(q.min)
    }

    @Test
    fun allowsOnlyPricesInRange() {
        val q = PriceQuery.parse("under 10")
        assertTrue(q.allows(9.5))
        assertFalse(q.allows(11.0))
        assertFalse(q.allows(null))
    }
}
