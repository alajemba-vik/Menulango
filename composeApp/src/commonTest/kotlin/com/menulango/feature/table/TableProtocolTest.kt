package com.menulango.feature.table

import com.menulango.data.order.TableOrder
import com.menulango.testDish
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TableProtocolTest {
    // Two scans of one menu: same names, different ids.
    private val hostMenu =
        listOf(
            testDish("host-1").copy(originalName = "Μουσακάς", readableName = "Moussaka"),
            testDish("host-2").copy(originalName = "Χωριάτικη", readableName = "Greek salad"),
        )

    @Test
    fun messagesSurviveTheTripAndStrangersAreIgnored() {
        val sent = TableMessage.Picks("key", "Nia", listOf(SyncedLine("Μουσακάς", "Moussaka", 2, "no onion", "🍆")))
        assertEquals(sent, TableMessage.decode(TableMessage.encode(sent)))
        assertNull(TableMessage.decode("not json".encodeToByteArray()))
        assertNull(TableMessage.decode("""{"type":"future-thing"}""".encodeToByteArray()))
    }

    @Test
    fun dishesAreFoundByTheirPrintedNameEvenWithDifferentIdsAndAccents() {
        val result =
            match(
                hostMenu,
                listOf(
                    SyncedLine("ΜΟΥΣΑΚΆΣ", "Moussaka", 1),
                    SyncedLine("unknown print", "greek  salad", 2),
                    SyncedLine("Κάτι άλλο", "House special", 1),
                ),
            )
        assertEquals(listOf("host-1" to 1, "host-2" to 2), result.lines.map { it.first.id to it.second })
        assertEquals(listOf("House special"), result.unmatched)
    }

    @Test
    fun aGuestsNewSnapshotReplacesTheirPicksAndNeverTouchesOthers() {
        val mine = TableOrder().add(hostMenu[1])
        val first = mine.withRemoteGuest("nia", "Nia", match(hostMenu, listOf(SyncedLine("Μουσακάς", "Moussaka", 1))))
        val second = first.withRemoteGuest("nia", "Nia", match(hostMenu, listOf(SyncedLine("Μουσακάς", "Moussaka", 3))))
        assertEquals(2, second.diners.size)
        val nia = second.diners.single { it.remote == "nia" }
        assertEquals(3, second.linesFor(nia.id).single().quantity)
        assertEquals(1, second.linesFor(TableOrder.OWNER).single().quantity)
        val cleared = second.withRemoteGuest("nia", "Nia", match(hostMenu, emptyList()))
        assertEquals(emptyList(), cleared.linesFor(nia.id))
    }

    @Test
    fun aGuestsOwnTableFlattensToOneLinePerDish() {
        val guest = TableOrder().add(hostMenu[0]).addDiner("Sam").add(hostMenu[0])
        val lines = guest.toSyncedLines { "🍽️" }
        assertEquals(1, lines.size)
        assertEquals(2, lines.single().quantity)
        assertIs<TableMessage.Hello>(TableMessage.decode(TableMessage.encode(TableMessage.Hello("Nia", "k"))))
    }
}
