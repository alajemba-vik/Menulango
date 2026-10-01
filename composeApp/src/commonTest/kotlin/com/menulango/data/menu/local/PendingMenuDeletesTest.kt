package com.menulango.data.menu.local

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingMenuDeletesTest {
    @Test
    fun aSwipedMenuIsStillListedAfterTheAppRestarts() {
        val settings = MapSettings()
        PendingMenuDeletes(settings).add("~:abc")
        // A new instance on the same storage is what the next launch sees.
        assertEquals(setOf("~:abc"), PendingMenuDeletes(settings).all())
    }

    @Test
    fun undoingOrFinishingADeleteRemovesItFromTheList() {
        val pending = PendingMenuDeletes(MapSettings())
        pending.add("~:abc")
        pending.add("u09tvw:def")
        pending.remove("~:abc")
        assertEquals(setOf("u09tvw:def"), pending.all())
        pending.remove("u09tvw:def")
        assertTrue(pending.all().isEmpty())
    }
}
