package com.menulango.feature.table

import com.menulango.data.order.OrderBook
import com.menulango.data.order.TableOrder
import com.menulango.testDish
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeTransport : NearbyTransport {
    override val events = MutableSharedFlow<NearbyEvent>(extraBufferCapacity = 32)
    val sent = mutableListOf<Pair<String, TableMessage>>()
    val answers = mutableListOf<Pair<String, Boolean>>()
    var hosting = false
    var looking = false

    override fun host(name: String) {
        hosting = true
    }

    override fun look(name: String) {
        looking = true
    }

    override fun join(peer: String) = Unit

    override fun answer(
        peer: String,
        accept: Boolean,
    ) {
        answers += peer to accept
    }

    override fun send(
        peer: String,
        bytes: ByteArray,
    ) {
        sent += peer to TableMessage.decode(bytes)!!
    }

    override fun stop() {
        hosting = false
        looking = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TableSessionTest {
    private val moussaka = testDish("m1").copy(originalName = "Μουσακάς", readableName = "Moussaka")

    private fun TestScope.session(
        transport: FakeTransport,
        orders: OrderBook,
    ) = TableSession(transport, orders, MapSettings(), backgroundScope).also { it.name = "Me" }

    @Test
    fun theHostAcceptsAGuestAndTheirPicksArriveAsTheirOwnPerson() =
        runTest {
            val transport = FakeTransport()
            val orders = OrderBook()
            val session = session(transport, orders)
            val arrivals = mutableListOf<TableActivity>()
            backgroundScope.launchCollect(session, arrivals)

            session.host("menu") { listOf(moussaka) }
            runCurrent()
            transport.events.emit(NearbyEvent.JoinAsked("p1", "Nia"))
            runCurrent()
            val request = (session.table.value as TableState.Hosting).requests.single()
            session.answer(request, accept = true)
            assertEquals(listOf("p1" to true), transport.answers)

            val picks =
                TableMessage.Picks(
                    "nia-key",
                    "Nia",
                    listOf(SyncedLine("Μουσακάς", "Moussaka", 2, emoji = "🍆")),
                )
            transport.events.emit(NearbyEvent.Received("p1", TableMessage.encode(picks)))
            runCurrent()

            val order = orders.current("menu")
            val nia = order.diners.single { it.remote == "nia-key" }
            assertEquals("Nia", nia.name)
            assertEquals(2, order.linesFor(nia.id).single().quantity)
            assertEquals(2, (session.table.value as TableState.Hosting).guests.single().dishes)
            assertIs<TableActivity.Arrived>(arrivals.single())
            assertIs<TableMessage.Received>(transport.sent.last().second)
        }

    @Test
    fun aGuestIntroducesThemselvesThenSendsTheirPicksAsTheyChange() =
        runTest {
            val transport = FakeTransport()
            val orders = OrderBook()
            val session = session(transport, orders)
            session.look("mine")
            runCurrent()
            transport.events.emit(NearbyEvent.TableFound("host", "Victoria"))
            runCurrent()
            session.join((session.table.value as TableState.Looking).tables.single())
            transport.events.emit(NearbyEvent.Connected("host"))
            runCurrent()
            assertIs<TableMessage.Hello>(transport.sent.first().second)

            orders.update("mine") { TableOrder().add(moussaka) }
            // Past the short settle before each send.
            advanceTimeBy(1_000)
            runCurrent()
            val picks =
                transport.sent
                    .map { it.second }
                    .filterIsInstance<TableMessage.Picks>()
                    .last()
            assertEquals(listOf("Moussaka" to 1), picks.lines.map { it.readable to it.quantity })
            assertEquals("Victoria", (session.table.value as TableState.Joined).hostName)
        }

    @Test
    fun losingTheHostGoesBackToLookingAndSaysSo() =
        runTest {
            val transport = FakeTransport()
            val session = session(transport, OrderBook())
            session.look("mine")
            runCurrent()
            transport.events.emit(NearbyEvent.TableFound("host", "Victoria"))
            runCurrent()
            transport.events.emit(NearbyEvent.Connected("host"))
            runCurrent()
            transport.events.emit(NearbyEvent.Disconnected("host"))
            runCurrent()
            val state = session.table.value
            assertIs<TableState.Looking>(state)
            assertTrue(state.lost)
            session.stop()
            assertIs<TableState.Idle>(session.table.value)
        }

    @Test
    fun onlyNewOrIncreasedDishesCountAsNew() {
        val before = listOf(SyncedLine("a", "A", 1), SyncedLine("b", "B", 2))
        val after = listOf(SyncedLine("a", "A", 2), SyncedLine("b", "B", 2), SyncedLine("c", "C", 1))
        assertEquals(listOf("A", "C"), newlyAdded(before, after).map { it.readable })
    }
}

private fun CoroutineScope.launchCollect(
    session: TableSession,
    into: MutableList<TableActivity>,
) = launch(UnconfinedTestDispatcher()) { session.activities.collect { into += it } }
