package com.menulango.feature.order

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/** Google ML Kit runs the text translation on-device once its language models have downloaded. */
internal actual fun onDeviceNoteTranslator(): NoteTranslator = AndroidOnDeviceNoteTranslator()

private class AndroidOnDeviceNoteTranslator : NoteTranslator {
    override fun prefetchTargetLanguage(targetLanguageTag: String) {
        val language = TranslateLanguage.fromLanguageTag(targetLanguageTag) ?: return
        val model = TranslateRemoteModel.Builder(language).build()
        RemoteModelManager.getInstance().download(
            model,
            DownloadConditions.Builder().requireWifi().build(),
        )
    }

    override suspend fun translate(
        notes: Map<String, String>,
        sourceLanguageTag: String,
        targetLanguageTag: String,
    ): NoteTranslationResult {
        val source = TranslateLanguage.fromLanguageTag(sourceLanguageTag) ?: return NoteTranslationResult.Unavailable
        val target = TranslateLanguage.fromLanguageTag(targetLanguageTag) ?: return NoteTranslationResult.Unavailable
        if (source == target) return NoteTranslationResult.Ready(notes)

        val translator =
            Translation.getClient(
                TranslatorOptions
                    .Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build(),
            )
        return try {
            // This can download a language model the first time. The caller remains on the waiter
            // screen and shows its own-language copy while it happens.
            translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).await()
            NoteTranslationResult.Ready(
                notes
                    .mapValues { (_, note) ->
                        translator.translate(note).await().trim()
                    }.filterValues { it.isNotEmpty() },
            )
        } catch (_: Exception) {
            NoteTranslationResult.Unavailable
        } finally {
            translator.close()
        }
    }
}
