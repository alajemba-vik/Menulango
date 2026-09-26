package com.menulango.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuColourTest {
    private fun argb(
        r: Int,
        g: Int,
        b: Int,
    ) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun blackPrintOnWhiteCardHasNoBrandColour() {
        val page = IntArray(1000) { if (it % 10 == 0) argb(20, 20, 20) else argb(250, 248, 244) }
        assertNull(dominantHue(page))
    }

    @Test
    fun aRedHeadingOnCreamIsFound() {
        val page = IntArray(1000) { if (it % 8 == 0) argb(180, 40, 50) else argb(245, 240, 230) }
        val colour = assertNotNull(dominantHue(page))
        assertTrue(colour.red > colour.green && colour.red > colour.blue, "red wins: $colour")
    }

    @Test
    fun theHeaderColourAlwaysCarriesWhiteText() {
        listOf(Color(0xFFFFE066), Color(0xFF7FDBFF), Color(0xFFE4572E), Color(0xFF2ECC40)).forEach {
            assertTrue(contrastWithWhite(headerSafe(it)) >= 4.5f, "white on ${headerSafe(it)}")
        }
    }
}
