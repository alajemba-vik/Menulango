package com.menulango.data.menu

import com.menulango.core.result.AppError
import com.menulango.core.result.AppResult
import com.menulango.data.menu.model.Menu
import com.menulango.data.menu.remote.MenuAssembler
import com.menulango.data.menu.remote.MenuContractViolation
import com.menulango.data.menu.remote.MenuResponseParser
import com.menulango.data.menu.remote.toDocument
import com.menulango.dishJson
import com.menulango.menuDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuResponseParserTest {
    private val release = MenuResponseParser(failLoudly = false)
    private val debug = MenuResponseParser(failLoudly = true)

    private fun AppResult<Menu>.menu(): Menu = assertIs<AppResult.Ok<Menu>>(this).value

    @Test
    fun wellFormedResponseBecomesDomainObjects() {
        val menu = release.parseDocument(menuDocument(dishJson("kokoretsi"))).menu()

        assertEquals("el", menu.meta.language)
        val dish = menu.dishes.single()
        assertEquals("Dish kokoretsi", dish.readableName)
        assertEquals("Κοκορέτσι {kokoretsi}", dish.originalName)
        assertEquals(listOf("lamb", "lemon"), dish.ingredients, "blank ingredients are dropped")
        assertEquals(12.5, dish.price?.amount)
        assertTrue(dish.flags.offal)
        assertEquals(listOf("gluten"), dish.allergens.likelyContains)
    }

    @Test
    fun aLoneEmojiIsKeptAndAnythingElseIsDroppedWithoutCostingTheDish() {
        val menu =
            debug
                .parseDocument(
                    menuDocument(
                        dishJson("octopus", extra = ", \"emoji\": \"🐙\""),
                        dishJson("chef", extra = ", \"emoji\": \"👨‍🍳\""),
                        dishJson("word", extra = ", \"emoji\": \"octopus\""),
                        dishJson("none"),
                    ),
                ).menu()

        assertEquals(listOf("🐙", "👨‍🍳", null, null), menu.dishes.map { it.emoji })
    }

    @Test
    fun theEmojiSurvivesTheCache() {
        val menu = release.parseDocument(menuDocument(dishJson("octopus", extra = ", \"emoji\": \"🐙\""))).menu()
        val reread = release.parseDocument(menu.toDocument()).menu()
        assertEquals("🐙", reread.dishes.single().emoji)
    }

    @Test
    fun malformedNotJsonAtAll() {
        val fenced = "```json\n" + menuDocument(dishJson("a")) + "\n```"
        assertEquals(AppResult.Err(AppError.Malformed), release.parseDocument(fenced))
        assertFailsWith<MenuContractViolation> { debug.parseDocument(fenced) }
    }

    @Test
    fun malformedDishesAreDroppedAndTheRestSurvive() {
        val missingName = dishJson("no-name").replace("\"readableName\": \"Dish no-name\",", "")
        val document =
            menuDocument(
                dishJson("good"),
                missingName,
                dishJson("overconfident", confidence = "1.7"),
                dishJson("negative-price", amount = "-4"),
                dishJson("off-scale", adventure = "9"),
            )

        val menu = release.parseDocument(document).menu()

        assertEquals(listOf("good"), menu.dishes.map { it.id })
        assertFailsWith<MenuContractViolation> { debug.parseDocument(document) }
    }

    @Test
    fun malformedWrongTypeInOneDishDoesNotSinkTheMenu() {
        val wrongType = dishJson("typed").replace("\"effortLevel\": 5", "\"effortLevel\": \"very high\"")
        val menu = release.parseDocument(menuDocument(wrongType, dishJson("fine"))).menu()

        assertEquals(listOf("fine"), menu.dishes.map { it.id })
    }

    @Test
    fun menuWhoseEveryDishIsBrokenIsMalformedNotEmpty() {
        val result = release.parseDocument(menuDocument(dishJson("x", confidence = "-1")))
        assertEquals(AppResult.Err(AppError.Malformed), result)
    }

    @Test
    fun menuWithNoDishesIsEmptyNotAnError() {
        assertTrue(
            release
                .parseDocument(menuDocument())
                .menu()
                .dishes
                .isEmpty(),
        )
    }

    @Test
    fun proxyErrorsMapToAppErrors() {
        assertEquals(AppResult.Err(AppError.RateLimited), release.parseDocument("""{"error":"RATE_LIMITED"}"""))
        assertEquals(AppResult.Err(AppError.Unreadable), release.parseDocument("""{"error":"UNREADABLE"}"""))
        assertEquals(AppResult.Err(AppError.Upstream), release.parseDocument("""{"error":"UPSTREAM"}"""))
    }

    @Test
    fun missingPriceAmountMeansNoPriceNotABrokenDish() {
        val dish = release.parseDish(dishJson("p").replace("\"amount\": 12.5,", ""))
        assertNull(dish?.price)
        assertEquals("p", dish?.id)
    }

    @Test
    fun duplicateIdsAreMadeUniqueAndTheMenuIsCapped() {
        val menu = release.parseDocument(menuDocument(*Array(70) { dishJson("same") })).menu()

        assertEquals(MenuAssembler.MAX_DISHES, menu.dishes.size)
        assertEquals(listOf("same", "same-2", "same-3"), menu.dishes.take(3).map { it.id })
    }
}
