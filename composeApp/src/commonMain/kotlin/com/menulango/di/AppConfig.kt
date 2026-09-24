package com.menulango.di

/**
 * Everything that differs between builds, supplied by each platform's entry point.
 *
 * Exists so commonMain never reads a build system: Android passes BuildConfig values, iOS passes
 * Info.plist values, and the shared code sees one immutable object. A blank [proxyUrl] or
 * [revenueCatApiKey] is valid — the app then runs on the sample menu and without purchases, which
 * is exactly what a stranger cloning the repository needs.
 */
public data class AppConfig(
    val proxyUrl: String,
    val revenueCatApiKey: String,
    val isDebug: Boolean,
) {
    val hasProxy: Boolean get() = proxyUrl.isNotBlank()
    val hasBilling: Boolean get() = revenueCatApiKey.isNotBlank()

    /** The proxy also serves the privacy policy, so the app and both store listings share one URL. */
    val privacyPolicyUrl: String? get() = if (hasProxy) "${proxyUrl.trimEnd('/')}/privacy" else null
}
