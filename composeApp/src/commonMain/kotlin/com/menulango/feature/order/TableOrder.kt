package com.menulango.feature.order

import com.menulango.data.menu.model.Dish
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Someone at the table. The first is always the phone's owner; the rest are named by them. */
@Serializable
internal data class Diner(
    val id: Int,
    /** Null for the phone's owner, shown as "You". */
    val name: String?,
    /**
     * Set for someone picking on their own phone at a hosted table: that phone's key, so each
     * update from it replaces their picks rather than adding to them.
     */
    val remote: String? = null,
)

/** One person's estimated totals, and how many of their plates the estimate covers. */
internal data class NutritionSummary(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val platesCounted: Int,
    val plates: Int,
) {
    val isPartial: Boolean get() = platesCounted < plates
}

/** @param note how this person wants it — "no cucumber, dressing on the side" — to tell the waiter. */
@Serializable
internal data class OrderLine(
    val dish: Dish,
    val dinerId: Int,
    val quantity: Int,
    val note: String? = null,
)

/**
 * What one table is ordering from one menu, person by person. Pure and immutable, so every change
 * is a new value that can be tested and rendered without ceremony.
 *
 * @param activeDinerId whose order a new dish joins: the person the phone is being passed to.
 */
@Serializable
internal data class TableOrder(
    val diners: List<Diner> = listOf(Diner(OWNER, null)),
    val lines: List<OrderLine> = emptyList(),
    val activeDinerId: Int = OWNER,
    /** Cached restaurant-language notes, keyed by diner and dish, so toggling never calls the API again. */
    val waiterTranslations: Map<String, String> = emptyMap(),
) {
    val dishCount: Int get() = lines.sumOf { it.quantity }

    val isEmpty: Boolean get() = lines.isEmpty()

    /** Null when any ordered dish has no printed price: an estimate that skips dishes misleads. */
    val total: Double?
        get() = if (lines.any { it.dish.price == null }) null else lines.sumOf { it.dish.price!!.amount * it.quantity }

    fun quantityOf(dishId: String): Int = lines.filter { it.dish.id == dishId }.sumOf { it.quantity }

    fun linesFor(dinerId: Int): List<OrderLine> = lines.filter { it.dinerId == dinerId }

    /**
     * Roughly what one person's plates add up to, from the dishes that carry an estimate; null
     * when none of theirs does. Drinks and dishes the model couldn't picture have none, so the
     * summary says how many plates it counted rather than passing off a partial sum as the whole.
     */
    fun nutritionFor(dinerId: Int): NutritionSummary? {
        val theirs = linesFor(dinerId)
        val estimated = theirs.filter { it.dish.nutrition != null }
        if (estimated.isEmpty()) return null

        fun sum(part: (com.menulango.data.menu.model.Nutrition) -> Int) =
            estimated.sumOf { part(it.dish.nutrition!!) * it.quantity }
        return NutritionSummary(
            kcal = sum { it.kcal },
            proteinG = sum { it.proteinG },
            carbsG = sum { it.carbsG },
            fatG = sum { it.fatG },
            platesCounted = estimated.sumOf { it.quantity },
            plates = theirs.sumOf { it.quantity },
        )
    }

    fun add(dish: Dish): TableOrder = change(dish, activeDinerId, +1)

    fun change(
        dish: Dish,
        dinerId: Int,
        delta: Int,
    ): TableOrder {
        val existing = lines.firstOrNull { it.dish.id == dish.id && it.dinerId == dinerId }
        val quantity = (existing?.quantity ?: 0) + delta
        val others = lines.filterNot { it === existing }
        return copy(
            lines =
                when {
                    quantity <= 0 -> others
                    existing == null -> lines + OrderLine(dish, dinerId, quantity)
                    else -> lines.map { if (it === existing) it.copy(quantity = quantity) else it }
                },
        )
    }

    /** Adds a person and makes them the one being ordered for. */
    fun addDiner(name: String?): TableOrder {
        val id = (diners.maxOf { it.id }) + 1
        return copy(diners = diners + Diner(id, name?.trim()?.takeIf { it.isNotEmpty() }), activeDinerId = id)
    }

    fun rename(
        dinerId: Int,
        name: String,
    ): TableOrder =
        copy(
            diners =
                diners.map {
                    if (it.id ==
                        dinerId
                    ) {
                        it.copy(name = name.trim().ifEmpty { null })
                    } else {
                        it
                    }
                },
        )

    /** Removes a guest and their dishes. The phone's owner always stays. */
    fun removeDiner(dinerId: Int): TableOrder {
        if (dinerId == OWNER) return this
        return copy(
            diners = diners.filterNot { it.id == dinerId },
            lines = lines.filterNot { it.dinerId == dinerId },
            activeDinerId = if (activeDinerId == dinerId) OWNER else activeDinerId,
        )
    }

    fun lineFor(
        dishId: String,
        dinerId: Int,
    ): OrderLine? = lines.firstOrNull { it.dish.id == dishId && it.dinerId == dinerId }

    /** Sets or clears how one person wants one dish. A blank note is no note. */
    fun setNote(
        dishId: String,
        dinerId: Int,
        note: String,
    ): TableOrder =
        copy(
            lines =
                lines.map {
                    if (it.dish.id == dishId &&
                        it.dinerId == dinerId
                    ) {
                        it.copy(note = note.trim().ifEmpty { null })
                    } else {
                        it
                    }
                },
            waiterTranslations = waiterTranslations - noteKey(dishId, dinerId),
        )

    /** Keeps translations already prepared for other picks; a new note only adds its own copy. */
    fun saveWaiterTranslations(translations: Map<String, String>): TableOrder =
        copy(waiterTranslations = waiterTranslations + translations)

    fun waiterNote(
        dishId: String,
        dinerId: Int,
    ): String? = waiterTranslations[noteKey(dishId, dinerId)]

    /** Every pick gone; the people at the table stay, ready for the next round. */
    fun cleared(): TableOrder = copy(lines = emptyList())

    fun activate(dinerId: Int): TableOrder =
        if (diners.any { it.id == dinerId }) copy(activeDinerId = dinerId) else this

    companion object {
        const val OWNER: Int = 1

        fun noteKey(
            dishId: String,
            dinerId: Int,
        ): String = "$dinerId:$dishId"
    }
}

/**
 * One table order per menu, for as long as the app is open: an order is for tonight, not forever.
 * Shared by the menu and the choose screen, so a pick made in either lands in the same order.
 */
internal class OrderBook(
    private val settings: Settings? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val orders = MutableStateFlow(readOrders())

    /** A live view of one menu's order. */
    fun order(menuKey: String): Flow<TableOrder> = orders.map { it[menuKey] ?: TableOrder() }.distinctUntilChanged()

    fun update(
        menuKey: String,
        change: (TableOrder) -> TableOrder,
    ) {
        orders.value = orders.value + (menuKey to change(orders.value[menuKey] ?: TableOrder()))
        persist()
    }

    fun current(menuKey: String): TableOrder = orders.value[menuKey] ?: TableOrder()

    fun backup(): Map<String, TableOrder> = orders.value

    /** A live order wins if both devices have an order for the same menu. */
    fun merge(incoming: Map<String, TableOrder>) {
        orders.value = incoming + orders.value
        persist()
    }

    private fun readOrders(): Map<String, TableOrder> =
        settings
            ?.getStringOrNull(KEY)
            ?.let { stored -> runCatching { json.decodeFromString<Map<String, TableOrder>>(stored) }.getOrNull() }
            .orEmpty()

    private fun persist() {
        settings?.putString(KEY, json.encodeToString(orders.value))
    }

    private companion object {
        const val KEY = "orders.by-menu.v1"
    }
}
