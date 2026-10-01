package com.menulango.platform

import com.menulango.data.AppAttestation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Implemented in Swift, where the Firebase App Check SDK lives. */
public interface IosAppCheckBridge {
    public fun token(callback: IosAppCheckCallback)
}

public interface IosAppCheckCallback {
    /** Null when App Check is not configured or no token could be made. */
    public fun onToken(token: String?)
}

internal class IosAppCheckAttestation(
    private val bridge: IosAppCheckBridge,
) : AppAttestation {
    override suspend fun token(): String? =
        suspendCancellableCoroutine { continuation ->
            bridge.token(
                object : IosAppCheckCallback {
                    override fun onToken(token: String?) {
                        if (continuation.isActive) continuation.resume(token)
                    }
                },
            )
        }
}
