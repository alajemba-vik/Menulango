package com.menulango.data

/**
 * Proof for the proxy that a scan comes from this app on a real device: a Firebase App Check token
 * backed by Apple App Attest or Google Play Integrity. Without it anyone could call the proxy with
 * made-up device ids and spend the Gemini budget.
 *
 * Supplied by each platform at launch; returns null when App Check is not configured or the
 * platform could not produce a token, and the scan is sent without one.
 */
public fun interface AppAttestation {
    public suspend fun token(): String?

    public companion object {
        public val None: AppAttestation = AppAttestation { null }
    }
}
