package com.menulango.core.result

/**
 * The outcome of anything that can fail for reasons outside the app's control.
 *
 * Exists so failures travel as values the UI must handle, rather than exceptions it might forget
 * to catch. Programming errors still throw — this type is for the world misbehaving, not us.
 */
public sealed interface AppResult<out T> {
    public data class Ok<out T>(
        val value: T,
    ) : AppResult<T>

    public data class Err(
        val error: AppError,
    ) : AppResult<Nothing>
}

/**
 * Every way the world can let the user down, in terms the UI can explain in plain language.
 *
 * Deliberately small: each case maps to one sentence and one recovery action on screen.
 */
public sealed interface AppError {
    /** No connection, or the connection dropped. Retrying later will help. */
    public data object Offline : AppError

    /** The proxy asked us to slow down. */
    public data object RateLimited : AppError

    /** The photo could not be read as a menu: blurred, dark, or not a menu at all. */
    public data object Unreadable : AppError

    /** The model or proxy failed on its side. */
    public data object Upstream : AppError

    /** The response arrived but broke the contract badly enough that nothing could be kept. */
    public data object Malformed : AppError

    /** This build has no proxy URL, so it can only show the sample menu. */
    public data object NotConfigured : AppError

    /** The camera or photo library could not provide an image. */
    public data object CaptureFailed : AppError
}
