package com.menulango.data.menu.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.menulango.core.result.AppResult
import com.menulango.data.db.MenuLangoDatabase
import com.menulango.data.menu.model.Menu
import com.menulango.data.menu.remote.MenuResponseParser
import com.menulango.data.menu.remote.toDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A menu reopened from the device, with the photo it was read from when we kept one. */
internal data class CachedMenu(
    val cacheKey: String,
    val menu: Menu,
    val photo: ByteArray?,
)

/** Enough to offer "reopen last menu" on the capture screen without loading the whole document. */
internal data class CachedMenuSummary(
    val cacheKey: String,
    val dishCount: Int,
    val venueType: String?,
    val language: String?,
)

/**
 * The on-device menu cache.
 *
 * Exists because restaurant menus barely change: a returning diner sees the menu instantly and
 * offline, and rescanning a menu already read never costs a free scan. The key design
 * ([MenuCacheKey]) is what later lets the same cache move to the proxy and be shared.
 */
internal class MenuCache(
    private val database: MenuLangoDatabase,
    private val parser: MenuResponseParser,
    private val io: CoroutineDispatcher,
    private val nowMillis: () -> Long,
) {
    private val queries get() = database.savedMenuQueries

    suspend fun containsMenu(fingerprint: String): Boolean =
        withContext(io) { queries.countByFingerprint(fingerprint).executeAsOne() > 0 }

    suspend fun save(
        key: MenuCacheKey,
        menu: Menu,
        photo: ByteArray?,
    ): Unit =
        withContext(io) {
            val now = nowMillis()
            database.transaction {
                queries.upsert(
                    cacheKey = key.value,
                    fingerprint = key.fingerprint,
                    savedAtMillis = now,
                    lastOpenedAtMillis = now,
                    dishCount = menu.dishes.size.toLong(),
                    venueType = menu.meta.venueType,
                    language = menu.meta.language,
                    document = menu.toDocument(),
                    photo = photo,
                )
                queries.pruneOldest(MAX_SAVED_MENUS)
            }
        }

    /** Returns null if the menu is gone or no longer passes validation (e.g. after a schema change). */
    suspend fun open(cacheKey: String): CachedMenu? =
        withContext(io) {
            val row = queries.selectByKey(cacheKey).executeAsOneOrNull() ?: return@withContext null
            val menu = (parser.parseDocument(row.document) as? AppResult.Ok)?.value ?: return@withContext null
            queries.touch(nowMillis(), cacheKey)
            CachedMenu(cacheKey, menu, row.photo)
        }

    fun latest(): Flow<CachedMenuSummary?> =
        queries
            .selectLatestSummary()
            .asFlow()
            .mapToOneOrNull(io)
            .map { row ->
                row?.let { CachedMenuSummary(it.cacheKey, it.dishCount.toInt(), it.venueType, it.language) }
            }

    private companion object {
        /** Photos are ~300 KB each; twenty menus is a long trip and a few megabytes. */
        const val MAX_SAVED_MENUS = 20L
    }
}
