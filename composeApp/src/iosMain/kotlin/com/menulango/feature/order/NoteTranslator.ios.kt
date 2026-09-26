package com.menulango.feature.order

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private var bridge: IosNoteTranslationBridge? = null

/** Installed by the Swift app shell before Koin starts. */
public fun installIosNoteTranslationBridge(value: IosNoteTranslationBridge) {
    bridge = value
}

internal actual fun onDeviceNoteTranslator(): NoteTranslator = IosBridgeNoteTranslator

private object IosBridgeNoteTranslator : NoteTranslator {
    override fun prefetchTargetLanguage(targetLanguageTag: String) {
        bridge?.prefetch(targetLanguageTag)
    }

    override suspend fun translate(
        notes: Map<String, String>,
        sourceLanguageTag: String,
        targetLanguageTag: String,
    ): NoteTranslationResult =
        suspendCancellableCoroutine { continuation ->
            val translator = bridge
            if (translator == null) {
                continuation.resume(NoteTranslationResult.Unavailable)
                return@suspendCancellableCoroutine
            }
            translator.translate(
                notes = notes.map { (key, text) -> IosTranslationNote(key, text) },
                sourceLanguageTag = sourceLanguageTag,
                targetLanguageTag = targetLanguageTag,
                callback =
                    object : IosNoteTranslationCallback {
                        override fun onSuccess(translations: List<IosTranslationNote>) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    NoteTranslationResult.Ready(
                                        translations.associate { it.key to it.text.trim() }.filterValues {
                                            it
                                                .isNotEmpty()
                                        },
                                    ),
                                )
                            }
                        }

                        override fun onFailure() {
                            if (continuation.isActive) continuation.resume(NoteTranslationResult.Unavailable)
                        }
                    },
            )
        }
}
