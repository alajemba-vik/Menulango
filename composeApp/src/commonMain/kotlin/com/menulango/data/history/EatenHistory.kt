package com.menulango.data.history

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.menulango.data.db.MenuLangoDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
internal data class EatenDishRecord(
    val dishKey: String,
    val eatenAtMillis: Long,
)

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

    suspend fun backup(): List<EatenDishRecord> =
        withContext(io) {
            database.eatenDishQueries.selectAllForBackup().executeAsList().map {
                EatenDishRecord(
                    it.dishKey,
                    it.eatenAtMillis,
                )
            }
        }

    suspend fun merge(backup: List<EatenDishRecord>): Unit =
        withContext(io) {
            database.transaction {
                backup.forEach { database.eatenDishQueries.insert(it.dishKey, it.eatenAtMillis) }
            }
        }
}
