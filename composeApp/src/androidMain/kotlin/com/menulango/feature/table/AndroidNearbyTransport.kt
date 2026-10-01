package com.menulango.feature.table

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Google's Nearby Connections, over Bluetooth and Wi-Fi with no internet: the host advertises
 * the table, guests discover it and ask to join, and the host's phone is the hub (a star, the
 * shape of one table around one phone). Android to Android only, as the API is.
 */
internal class AndroidNearbyTransport(
    context: Context,
) : NearbyTransport {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val flow = MutableSharedFlow<NearbyEvent>(extraBufferCapacity = BUFFER)
    override val events: SharedFlow<NearbyEvent> = flow.asSharedFlow()

    private var isHost = false
    private var myName = ""

    private val payloads =
        object : PayloadCallback() {
            override fun onPayloadReceived(
                endpointId: String,
                payload: Payload,
            ) {
                payload.asBytes()?.let { flow.tryEmit(NearbyEvent.Received(endpointId, it)) }
            }

            override fun onPayloadTransferUpdate(
                endpointId: String,
                update: PayloadTransferUpdate,
            ) = Unit
        }

    private val lifecycle =
        object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(
                endpointId: String,
                info: ConnectionInfo,
            ) {
                // The host decides who sits at the table; a guest accepts the host it chose.
                if (isHost) {
                    flow.tryEmit(NearbyEvent.JoinAsked(endpointId, info.endpointName))
                } else {
                    client.acceptConnection(endpointId, payloads)
                }
            }

            override fun onConnectionResult(
                endpointId: String,
                resolution: ConnectionResolution,
            ) {
                flow.tryEmit(
                    if (resolution.status.isSuccess) {
                        NearbyEvent.Connected(
                            endpointId,
                        )
                    } else {
                        NearbyEvent.Failed(endpointId)
                    },
                )
            }

            override fun onDisconnected(endpointId: String) {
                flow.tryEmit(NearbyEvent.Disconnected(endpointId))
            }
        }

    private val discovery =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(
                endpointId: String,
                info: DiscoveredEndpointInfo,
            ) {
                if (info.serviceId == SERVICE_ID) flow.tryEmit(NearbyEvent.TableFound(endpointId, info.endpointName))
            }

            override fun onEndpointLost(endpointId: String) {
                flow.tryEmit(NearbyEvent.TableLost(endpointId))
            }
        }

    override fun host(name: String) {
        isHost = true
        myName = name
        client
            .startAdvertising(name, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { flow.tryEmit(NearbyEvent.Failed(null)) }
    }

    override fun look(name: String) {
        isHost = false
        myName = name
        client
            .startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { flow.tryEmit(NearbyEvent.Failed(null)) }
    }

    override fun join(peer: String) {
        client.stopDiscovery()
        client
            .requestConnection(myName, peer, lifecycle)
            .addOnFailureListener { flow.tryEmit(NearbyEvent.Failed(peer)) }
    }

    override fun answer(
        peer: String,
        accept: Boolean,
    ) {
        if (accept) client.acceptConnection(peer, payloads) else client.rejectConnection(peer)
    }

    override fun send(
        peer: String,
        bytes: ByteArray,
    ) {
        client.sendPayload(peer, Payload.fromBytes(bytes))
    }

    override fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private companion object {
        const val SERVICE_ID = "com.menulango.table"
        val STRATEGY: Strategy = Strategy.P2P_STAR
        const val BUFFER = 64
    }
}
