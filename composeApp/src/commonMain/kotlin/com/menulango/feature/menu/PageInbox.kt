package com.menulango.feature.menu

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries pages photographed on the add-page camera back to the menu they belong to.
 *
 * The menu's ViewModel stays alive underneath the camera on the back stack and listens here for
 * its own [Delivery.sessionId], so no screen has to pass results back through navigation.
 */
internal class PageInbox {
    data class Delivery(
        val sessionId: Long,
        val pages: List<ByteArray>,
    )

    private val deliveries = MutableSharedFlow<Delivery>(extraBufferCapacity = BUFFER)

    val incoming: SharedFlow<Delivery> = deliveries.asSharedFlow()

    fun deliver(
        sessionId: Long,
        pages: List<ByteArray>,
    ) {
        if (pages.isNotEmpty()) deliveries.tryEmit(Delivery(sessionId, pages))
    }

    private companion object {
        const val BUFFER = 8
    }
}
