package com.menulango.data.history

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.menulango.data.db.MenuLangoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The dishes a diner has already eaten, so "Something new" is genuinely new to them.
 *
 * Keys are produced by the caller (see `dishHistoryKey`), so this store knows nothing about how
 * dishes are identified.
 */
internal class EatenHistory(
    private val database: MenuLangoDatabase,
    private val io: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) {
    val keys: Flow<Set<String>> =
        database.eatenDishQueries
            .selectAllKeys()
            .asFlow()
            .mapToList(io)
            .map { it.toSet() }

    suspend fun setEaten(
        dishKey: String,
        eaten: Boolean,
    ): Unit =
        withContext(io) {
            if (eaten) {
                database.eatenDishQueries.insert(dishKey, nowMillis())
            } else {
                database.eatenDishQueries.delete(dishKey)
            }
        }
}
