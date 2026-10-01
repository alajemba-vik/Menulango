package com.menulango.feature.table

import com.menulango.data.menu.model.Dish
import com.menulango.feature.menu.foldedName
import com.menulango.feature.order.Diner
import com.menulango.feature.order.OrderLine
import com.menulango.feature.order.TableOrder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What phones at one table say to each other. Each guest has scanned the menu for themselves, so
 * dishes travel by name, never by id: ids differ between two scans of the same menu, names don't.
 * Every picks message is a full snapshot, so a lost or repeated message can never double a dish.
 */
@Serializable
internal sealed interface TableMessage {
    /** A guest introducing themselves once connected. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        val name: String,
        /** Stable per install, so the host recognises the same person reconnecting. */
        val guestKey: String,
        val protocol: Int = PROTOCOL,
    ) : TableMessage

    /** Everything this guest has picked, as it stands now. */
    @Serializable
    @SerialName("picks")
    data class Picks(
        val guestKey: String,
        val name: String,
        val lines: List<SyncedLine>,
    ) : TableMessage

    /** The host's thanks: how many of the guest's dishes it found on its own copy of the menu. */
    @Serializable
    @SerialName("received")
    data class Received(
        val matched: Int,
        val unmatched: List<String>,
    ) : TableMessage

    companion object {
        const val PROTOCOL = 1

        private val json =
            Json {
                ignoreUnknownKeys = true
                classDiscriminator = "type"
            }

        fun encode(message: TableMessage): ByteArray = json.encodeToString(serializer(), message).encodeToByteArray()

        /** Null for anything that isn't a message this version understands. */
        fun decode(bytes: ByteArray): TableMessage? =
            runCatching { json.decodeFromString(serializer(), bytes.decodeToString()) }.getOrNull()
    }
}

/** One dish as a guest picked it, named both ways so the host can find it on its own scan. */
@Serializable
internal data class SyncedLine(
    val original: String,
    val readable: String,
    val quantity: Int,
    val note: String? = null,
    /** For the host's arrival animation: the plate the guest saw. */
    val emoji: String? = null,
)

/** A guest's order flattened to what travels: one line per dish, notes joined. */
internal fun TableOrder.toSyncedLines(emojiOf: (Dish) -> String): List<SyncedLine> =
    lines
        .groupBy { it.dish.id }
        .values
        .map { same ->
            val dish = same.first().dish
            SyncedLine(
                original = dish.originalName,
                readable = dish.readableName,
                quantity = same.sumOf { it.quantity },
                note =
                    same
                        .mapNotNull { it.note }
                        .distinct()
                        .joinToString("; ")
                        .ifEmpty { null },
                emoji = emojiOf(dish),
            )
        }.sortedBy { it.readable }

/** The guest's lines, found on the host's own copy of the menu, and the ones that weren't. */
internal data class Matched(
    val lines: List<Triple<Dish, Int, String?>>,
    val unmatched: List<String>,
)

/**
 * Finds each synced dish on the host's menu: by the name as printed first (the most reliable, it's
 * what both photos show), then by the readable name. Accents, case and spacing never matter.
 */
internal fun match(
    hostDishes: List<Dish>,
    synced: List<SyncedLine>,
): Matched {
    val byOriginal = hostDishes.groupBy { it.originalName.foldedName() }
    val byReadable = hostDishes.groupBy { it.readableName.foldedName() }
    val found = mutableListOf<Triple<Dish, Int, String?>>()
    val missing = mutableListOf<String>()
    for (line in synced) {
        if (line.quantity <= 0) continue
        val dish =
            byOriginal[line.original.foldedName()]?.firstOrNull()
                ?: byReadable[line.readable.foldedName()]?.firstOrNull()
        if (dish == null) missing += line.readable else found += Triple(dish, line.quantity, line.note)
    }
    return Matched(found, missing)
}

/**
 * The host's table with one remote guest's picks set to [matched]: their earlier picks are
 * replaced, never added to. A guest seen for the first time joins the table under their name.
 */
internal fun TableOrder.withRemoteGuest(
    guestKey: String,
    name: String,
    matched: Matched,
): TableOrder {
    val existing = diners.firstOrNull { it.remote == guestKey }
    val diner = existing?.copy(name = name) ?: Diner((diners.maxOf { it.id }) + 1, name, remote = guestKey)
    val others = lines.filterNot { it.dinerId == diner.id }
    val theirs = matched.lines.map { (dish, quantity, note) -> OrderLine(dish, diner.id, quantity, note) }
    return copy(
        diners = if (existing == null) diners + diner else diners.map { if (it.id == diner.id) diner else it },
        lines = others + theirs,
    )
}
