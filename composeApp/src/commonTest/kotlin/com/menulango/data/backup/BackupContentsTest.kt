package com.menulango.data.backup

import com.menulango.data.marks.MenuMarks
import com.menulango.data.preferences.AppLanguage
import com.menulango.data.preferences.Appearance
import com.menulango.data.preferences.Preferences
import com.menulango.data.preferences.StartPage
import com.menulango.data.tips.Tip
import com.menulango.data.tips.Tips
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a backup carries arrives on the other phone: every field a diner can set. */
class BackupContentsTest {
    @Test
    fun preferencesTravelWhole() {
        val old = Preferences(MapSettings())
        old.setAppearance(Appearance.Dark)
        old.setLanguage(AppLanguage.French)
        old.setAvoid(setOf("coriander"))
        old.setCalmMotion(true)
        old.setStartPage(StartPage.Camera)

        val fresh = Preferences(MapSettings())
        fresh.merge(old.backup())

        assertEquals(Appearance.Dark, fresh.appearance.value)
        assertEquals(AppLanguage.French, fresh.language.value)
        assertEquals(setOf("coriander"), fresh.avoid.value)
        assertTrue(fresh.calmMotion.value)
        assertEquals(StartPage.Camera, fresh.startPage.value)
    }

    @Test
    fun menuMarksTravelWhole() {
        val old = MenuMarks(MapSettings())
        old.rename("menu-1", "Taverna Nikos")
        old.hide("menu-1", "tzatziki")
        old.recordPick("menu-1", "octopus")
        old.note("menu-1", "octopus", "Best thing we ate all week")

        val fresh = MenuMarks(MapSettings())
        fresh.merge(old.backup())

        val mark = fresh.current("menu-1")
        assertEquals("Taverna Nikos", mark.name)
        assertEquals(setOf("tzatziki"), mark.hidden)
        assertEquals(listOf("octopus"), mark.picked)
        assertEquals("Best thing we ate all week", mark.notes["octopus"])
    }

    @Test
    fun marksOnThisPhoneWinOverTheBackup() {
        val backup = MenuMarks(MapSettings()).apply { rename("menu-1", "From backup") }.backup()
        val phone = MenuMarks(MapSettings()).apply { rename("menu-1", "Mine") }
        phone.merge(backup)
        assertEquals("Mine", phone.current("menu-1").name)
    }

    @Test
    fun readTipsTravel() {
        val old = Tips(MapSettings())
        old.markSeen(Tip.Scan)
        val fresh = Tips(MapSettings())
        fresh.merge(old.backup())
        assertTrue(Tip.Scan in fresh.seen.value)
    }
}
