package com.menulango.data

import com.russhwolf.settings.Settings
import kotlin.uuid.Uuid

/**
 * A random, install-scoped identifier sent to the proxy for rate limiting.
 *
 * Exists so the proxy can stop one device from burning the Gemini budget without knowing who
 * anyone is. It is not tied to an account, an advertising id or the hardware, and reinstalling
 * the app replaces it.
 */
internal class DeviceIdentity(
    private val settings: Settings,
) {
    val id: String by lazy {
        settings.getStringOrNull(KEY) ?: Uuid.random().toString().also { settings.putString(KEY, it) }
    }

    private companion object {
        const val KEY = "device.id"
    }
}
