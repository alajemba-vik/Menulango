package com.menulango.feature.table

import com.menulango.data.menu.model.Dish
import com.menulango.data.menu.model.emoji
import com.menulango.data.order.OrderBook
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * The phone-to-phone link one platform offers (Nearby Connections on Android, MultipeerConnectivity
 * on iPhone): one phone hosts, the others find it and ask to join, and bytes go both ways.
 */
internal interface NearbyTransport {
    val events: Flow<NearbyEvent>

    fun host(name: String)

    fun look(name: String)

    fun join(peer: String)

    fun answer(
        peer: String,
        accept: Boolean,
    )

    fun send(
        peer: String,
        bytes: ByteArray,
    )

    fun stop()
}

internal sealed interface NearbyEvent {
    data class TableFound(
        val peer: String,
        val name: String,
    ) : NearbyEvent

    data class TableLost(
        val peer: String,
    ) : NearbyEvent

    /** On the host: someone asks to join. Nothing is connected until the host accepts. */
    data class JoinAsked(
        val peer: String,
        val name: String,
    ) : NearbyEvent

    data class Connected(
        val peer: String,
    ) : NearbyEvent

    data class Disconnected(
        val peer: String,
    ) : NearbyEvent

    data class Received(
        val peer: String,
        val bytes: ByteArray,
    ) : NearbyEvent

    /** The radio couldn't start or a connection failed; [peer] is null when it's the whole link. */
    data class Failed(
        val peer: String?,
    ) : NearbyEvent
}

internal data class NearbyTable(
    val peer: String,
    val name: String,
)

internal data class JoinRequest(
    val peer: String,
    val name: String,
)

internal data class TableGuest(
    val peer: String,
    val name: String,
    val dishes: Int = 0,
    val unmatched: List<String> = emptyList(),
)

internal sealed interface TableState {
    data object Idle : TableState

    data class Hosting(
        val orderKey: String,
        val guests: List<TableGuest> = emptyList(),
        val requests: List<JoinRequest> = emptyList(),
    ) : TableState

    data class Looking(
        val orderKey: String,
        val tables: List<NearbyTable> = emptyList(),
        val joining: String? = null,
        /** Set after a join failed or the table went away. */
        val lost: Boolean = false,
    ) : TableState

    data class Joined(
        val orderKey: String,
        val peer: String,
        val hostName: String,
        /** From the host's reply: how many of this guest's dishes it found on its own scan. */
        val matched: Int? = null,
        val unmatched: List<String> = emptyList(),
    ) : TableState
}

/** Something to show as it happens: a dish leaving this phone, or arriving on it. */
internal sealed interface TableActivity {
    val emoji: String

    data class Sent(
        override val emoji: String,
        val dish: String,
        val hostName: String,
    ) : TableActivity

    data class Arrived(
        override val emoji: String,
        val dish: String,
        val guestName: String,
    ) : TableActivity
}

/**
 * Off for the first release: Nearby Connections and MultipeerConnectivity each reach only their
 * own platform, so an iPhone and an Android phone at one table could not find each other. Comes
 * back with a join-by-code table that works across both.
 */
internal const val PICK_TOGETHER_ENABLED = false

/**
 * One table, shared across phones. The host's picks list gathers everyone's picks, each person as
 * themselves; a guest just picks on their own phone and their choices appear on the host's, live.
 * Every guest scans the menu for themselves: only picks travel, by name, as full snapshots.
 */
@OptIn(FlowPreview::class)
internal class TableSession(
    private val transport: NearbyTransport,
    private val orders: OrderBook,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow<TableState>(TableState.Idle)
    val table: StateFlow<TableState> = state.asStateFlow()

    private val activity = MutableSharedFlow<TableActivity>(extraBufferCapacity = ACTIVITY_BUFFER)
    val activities: SharedFlow<TableActivity> = activity.asSharedFlow()

    /** The name others see: asked for once, then remembered. */
    var name: String
        get() = settings.getString(KEY_NAME, "")
        set(value) = settings.putString(KEY_NAME, value.trim().take(MAX_NAME))

    private val guestKey: String by lazy {
        settings.getStringOrNull(KEY_GUEST) ?: Uuid.random().toString().also { settings.putString(KEY_GUEST, it) }
    }

    private var job: Job? = null
    private var hostDishes: () -> List<Dish> = { emptyList() }

    /** What each connected guest last sent, to spot what's new for the arrival animation. */
    private val lastFrom = mutableMapOf<String, List<SyncedLine>>()
    private val keyOf = mutableMapOf<String, String>()
    private var lastSent: List<SyncedLine> = emptyList()

    fun host(
        orderKey: String,
        dishes: () -> List<Dish>,
    ) {
        stop()
        hostDishes = dishes
        state.value = TableState.Hosting(orderKey)
        transport.host(name)
        job = scope.launch { transport.events.collect(::onHostEvent) }
    }

    fun look(orderKey: String) {
        stop()
        state.value = TableState.Looking(orderKey)
        transport.look(name)
        job = scope.launch { transport.events.collect(::onGuestEvent) }
    }

    fun join(table: NearbyTable) {
        val looking = state.value as? TableState.Looking ?: return
        state.value = looking.copy(joining = table.peer, lost = false)
        transport.join(table.peer)
    }

    fun answer(
        request: JoinRequest,
        accept: Boolean,
    ) {
        val hosting = state.value as? TableState.Hosting ?: return
        state.value = hosting.copy(requests = hosting.requests - request)
        transport.answer(request.peer, accept)
        if (accept) {
            state.update<TableState.Hosting> { it.copy(guests = it.guests + TableGuest(request.peer, request.name)) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        transport.stop()
        lastFrom.clear()
        keyOf.clear()
        lastSent = emptyList()
        state.value = TableState.Idle
    }

    private fun onHostEvent(event: NearbyEvent) {
        when (event) {
            is NearbyEvent.JoinAsked -> {
                state.update<TableState.Hosting> {
                    val others = it.requests.filterNot { r -> r.peer == event.peer }
                    it.copy(requests = others + JoinRequest(event.peer, event.name))
                }
            }

            is NearbyEvent.Disconnected -> {
                // Their picks stay on the host's list: they were ordered, the phone just left.
                state.update<TableState.Hosting> { it.copy(guests = it.guests.filterNot { g -> g.peer == event.peer }) }
                lastFrom.remove(event.peer)
            }

            is NearbyEvent.Received -> {
                onHostMessage(event.peer, TableMessage.decode(event.bytes) ?: return)
            }

            else -> {
                Unit
            }
        }
    }

    private fun onHostMessage(
        peer: String,
        message: TableMessage,
    ) {
        val hosting = state.value as? TableState.Hosting ?: return
        when (message) {
            is TableMessage.Hello -> {
                keyOf[peer] = message.guestKey
                state.update<TableState.Hosting> {
                    it.copy(guests = it.guests.map { g -> if (g.peer == peer) g.copy(name = message.name) else g })
                }
            }

            is TableMessage.Picks -> {
                val matched = match(hostDishes(), message.lines)
                orders.update(hosting.orderKey) { it.withRemoteGuest(message.guestKey, message.name, matched) }
                val before = lastFrom[peer].orEmpty()
                newlyAdded(before, message.lines).forEach {
                    activity.tryEmit(TableActivity.Arrived(it.emoji ?: DEFAULT_PLATE, it.readable, message.name))
                }
                lastFrom[peer] = message.lines
                state.update<TableState.Hosting> {
                    it.copy(
                        guests =
                            it.guests.map { g ->
                                if (g.peer != peer) {
                                    g
                                } else {
                                    g.copy(
                                        name = message.name,
                                        dishes = matched.lines.sumOf { l -> l.second },
                                        unmatched = matched.unmatched,
                                    )
                                }
                            },
                    )
                }
                transport.send(peer, TableMessage.encode(TableMessage.Received(matched.lines.size, matched.unmatched)))
            }

            is TableMessage.Received -> {
                Unit
            }
        }
    }

    private fun onGuestEvent(event: NearbyEvent) {
        when (event) {
            is NearbyEvent.TableFound -> {
                state.update<TableState.Looking> {
                    val others = it.tables.filterNot { t -> t.peer == event.peer }
                    it.copy(tables = others + NearbyTable(event.peer, event.name))
                }
            }

            is NearbyEvent.TableLost -> {
                state.update<TableState.Looking> { it.copy(tables = it.tables.filterNot { t -> t.peer == event.peer }) }
            }

            is NearbyEvent.Connected -> {
                val looking = state.value as? TableState.Looking ?: return
                val host = looking.tables.firstOrNull { it.peer == event.peer } ?: NearbyTable(event.peer, "")
                state.value = TableState.Joined(looking.orderKey, host.peer, host.name)
                transport.send(host.peer, TableMessage.encode(TableMessage.Hello(name, guestKey)))
                startSendingPicks(looking.orderKey, host)
            }

            is NearbyEvent.Disconnected, is NearbyEvent.Failed -> {
                val orderKey =
                    when (val now = state.value) {
                        is TableState.Joined -> now.orderKey
                        is TableState.Looking -> now.orderKey
                        else -> return
                    }
                // Back to looking, saying so, rather than silently pretending to be connected.
                job?.cancel()
                state.value = TableState.Looking(orderKey, lost = true)
                transport.look(name)
                job = scope.launch { transport.events.collect(::onGuestEvent) }
            }

            is NearbyEvent.Received -> {
                val reply = TableMessage.decode(event.bytes) as? TableMessage.Received ?: return
                state.update<TableState.Joined> { it.copy(matched = reply.matched, unmatched = reply.unmatched) }
            }

            is NearbyEvent.JoinAsked -> {
                Unit
            }
        }
    }

    /** Every change to this phone's picks goes to the host, settled for a moment first. */
    private fun startSendingPicks(
        orderKey: String,
        host: NearbyTable,
    ) {
        scope
            .launch {
                orders
                    .order(orderKey)
                    .map { it.toSyncedLines { dish -> dish.emoji() } }
                    .distinctUntilChanged()
                    .debounce(SEND_SETTLE_MS)
                    .collect { lines ->
                        val joined = state.value as? TableState.Joined ?: return@collect
                        if (joined.peer != host.peer) return@collect
                        transport.send(host.peer, TableMessage.encode(TableMessage.Picks(guestKey, name, lines)))
                        newlyAdded(lastSent, lines).forEach {
                            val sent = TableActivity.Sent(it.emoji ?: DEFAULT_PLATE, it.readable, joined.hostName)
                            activity.tryEmit(sent)
                        }
                        lastSent = lines
                    }
            }.also { sending -> job?.invokeOnCompletion { sending.cancel() } }
    }

    private inline fun <reified T : TableState> MutableStateFlow<TableState>.update(change: (T) -> T) {
        val now = value as? T ?: return
        value = change(now)
    }

    private companion object {
        const val KEY_NAME = "table.name"
        const val KEY_GUEST = "table.guestKey"
        const val MAX_NAME = 24
        const val SEND_SETTLE_MS = 350L
        const val ACTIVITY_BUFFER = 16
        const val DEFAULT_PLATE = "🍽️"
    }
}

/** Dishes that are new, or ordered more of, since [before]: what gets a flying plate. */
internal fun newlyAdded(
    before: List<SyncedLine>,
    after: List<SyncedLine>,
): List<SyncedLine> {
    val had = before.associate { it.original to it.quantity }
    return after.filter { it.quantity > (had[it.original] ?: 0) }
}
