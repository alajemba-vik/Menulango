package com.menulango.feature.order

/**
 * Translates diner-authored requests after they open the waiter view.
 *
 * This boundary deliberately has no HTTP client: implementations run on the device. A failed or
 * unsupported language never replaces the diner's original note.
 */
internal interface NoteTranslator {
    /** Starts a Wi-Fi-only download for the restaurant language, without delaying the menu. */
    fun prefetchTargetLanguage(targetLanguageTag: String)

    suspend fun translate(
        notes: Map<String, String>,
        sourceLanguageTag: String,
        targetLanguageTag: String,
    ): NoteTranslationResult
}

/** A successful batch may omit a line only when that particular line could not be translated. */
internal sealed interface NoteTranslationResult {
    data class Ready(
        val translations: Map<String, String>,
    ) : NoteTranslationResult

    /** The screen keeps the original note visible and explains that restaurant copy is unavailable. */
    data object Unavailable : NoteTranslationResult
}

/** Platform translations are local. Android and iOS supply their own native implementation. */
internal expect fun onDeviceNoteTranslator(): NoteTranslator

/**
 * Small Objective-C-friendly boundary implemented by the iOS shell. Kotlin owns the order and
 * cache; Swift owns the Apple-side ML Kit SDK, so the shared framework never needs a CocoaPods
 * dependency of its own.
 */
public data class IosTranslationNote(
    public val key: String,
    public val text: String,
)

public interface IosNoteTranslationCallback {
    public fun onSuccess(translations: List<IosTranslationNote>)

    public fun onFailure()
}

public interface IosNoteTranslationBridge {
    /** Starts a Wi-Fi-only model download. Completion is intentionally not needed by the UI. */
    public fun prefetch(targetLanguageTag: String)

    public fun translate(
        notes: List<IosTranslationNote>,
        sourceLanguageTag: String,
        targetLanguageTag: String,
        callback: IosNoteTranslationCallback,
    )
}
