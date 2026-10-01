package com.menulango.feature.table

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.io.encoding.Base64

/**
 * Apple's MultipeerConnectivity, implemented in Swift (the app shell) and driven from here.
 * Bytes cross the bridge as Base64 text, which both sides read without any NSData plumbing.
 */
public interface IosNearbyBridge {
    public fun setListener(listener: IosNearbyListener)

    public fun host(name: String)

    public fun look(name: String)

    public fun join(peer: String)

    public fun answer(
        peer: String,
        accept: Boolean,
    )

    public fun send(
        peer: String,
        base64: String,
    )

    public fun stop()
}

/** What the Swift side reports back, one call per event. */
public interface IosNearbyListener {
    public fun tableFound(
        peer: String,
        name: String,
    )

    public fun tableLost(peer: String)

    public fun joinAsked(
        peer: String,
        name: String,
    )

    public fun connected(peer: String)

    public fun disconnected(peer: String)

    public fun received(
        peer: String,
        base64: String,
    )

    /** [peer] is null when the whole link failed to start. */
    public fun failed(peer: String?)
}

internal class IosNearbyTransport(
    private val bridge: IosNearbyBridge,
) : NearbyTransport {
    private val flow = MutableSharedFlow<NearbyEvent>(extraBufferCapacity = BUFFER)
    override val events: SharedFlow<NearbyEvent> = flow.asSharedFlow()

    init {
        bridge.setListener(
            object : IosNearbyListener {
                override fun tableFound(
                    peer: String,
                    name: String,
                ) {
                    flow.tryEmit(NearbyEvent.TableFound(peer, name))
                }

                override fun tableLost(peer: String) {
                    flow.tryEmit(NearbyEvent.TableLost(peer))
                }

                override fun joinAsked(
                    peer: String,
                    name: String,
                ) {
                    flow.tryEmit(NearbyEvent.JoinAsked(peer, name))
                }

                override fun connected(peer: String) {
                    flow.tryEmit(NearbyEvent.Connected(peer))
                }

                override fun disconnected(peer: String) {
                    flow.tryEmit(NearbyEvent.Disconnected(peer))
                }

                override fun received(
                    peer: String,
                    base64: String,
                ) {
                    runCatching { Base64.decode(base64) }.getOrNull()?.let {
                        flow.tryEmit(NearbyEvent.Received(peer, it))
                    }
                }

                override fun failed(peer: String?) {
                    flow.tryEmit(NearbyEvent.Failed(peer))
                }
            },
        )
    }

    override fun host(name: String) = bridge.host(name)

    override fun look(name: String) = bridge.look(name)

    override fun join(peer: String) = bridge.join(peer)

    override fun answer(
        peer: String,
        accept: Boolean,
    ) = bridge.answer(peer, accept)

    override fun send(
        peer: String,
        bytes: ByteArray,
    ) = bridge.send(peer, Base64.encode(bytes))

    override fun stop() = bridge.stop()

    private companion object {
        const val BUFFER = 64
    }
}
