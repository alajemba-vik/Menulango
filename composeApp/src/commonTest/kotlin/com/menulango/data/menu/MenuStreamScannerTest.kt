package com.menulango.data.menu

import com.menulango.data.menu.remote.MenuResponseParser
import com.menulango.data.menu.remote.MenuStreamScanner
import com.menulango.data.menu.remote.StreamFragment
import com.menulango.dishJson
import com.menulango.menuDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MenuStreamScannerTest {
    private val parser = MenuResponseParser(failLoudly = true)

    @Test
    fun emitsEachDishAsSoonAsItClosesEvenFedOneByteAtATime() {
        // Braces and escaped quotes inside strings, and Greek split across byte boundaries.
        val document = menuDocument(dishJson("one"), dishJson("two"), dishJson("three"))
        val scanner = MenuStreamScanner()

        val fragments = document.encodeToByteArray().flatMap { byte -> scanner.feed(byteArrayOf(byte)) }

        assertIs<StreamFragment.MetaBlock>(fragments.first())
        val dishes = fragments.filterIsInstance<StreamFragment.DishObject>().map { parser.parseDish(it.json) }
        assertEquals(listOf("one", "two", "three"), dishes.map { it?.id })
        assertEquals("Κοκορέτσι {one}", dishes.first()?.originalName)
        assertTrue(scanner.isComplete)
    }

    @Test
    fun truncatedStreamKeepsTheDishesThatFinished() {
        val document = menuDocument(dishJson("one"), dishJson("two"))
        val cut = document.encodeToByteArray().let { it.copyOf(it.size - 40) }
        val scanner = MenuStreamScanner()

        val dishes = scanner.feed(cut).filterIsInstance<StreamFragment.DishObject>()

        assertEquals(1, dishes.size)
        assertFalse(scanner.isComplete)
    }

    @Test
    fun menuBlockAfterDishesIsStillFound() {
        val document = """{"dishes":[${dishJson("a")}],"menu":{"language":"ja","confidence":0.5}}"""
        val fragments = MenuStreamScanner().feed(document.encodeToByteArray())

        assertEquals(2, fragments.size)
        assertEquals("ja", parser.parseMeta(assertIs<StreamFragment.MetaBlock>(fragments[1]).json).language)
    }
}
