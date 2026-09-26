package com.menulango.feature.order

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** Google ML Kit runs the text translation on-device once its language models have downloaded. */
internal actual fun onDeviceNoteTranslator(): NoteTranslator = AndroidOnDeviceNoteTranslator()

private class AndroidOnDeviceNoteTranslator : NoteTranslator {
    override fun prefetchTargetLanguage(targetLanguageTag: String) {
        val languages =
            listOfNotNull(
                TranslateLanguage.fromLanguageTag(targetLanguageTag),
                TranslateLanguage.fromLanguageTag(Locale.getDefault().toLanguageTag()),
            ).distinct()
        languages.forEach { language ->
            RemoteModelManager.getInstance().download(
                TranslateRemoteModel.Builder(language).build(),
                DownloadConditions.Builder().requireWifi().build(),
            )
        }
    }

    override suspend fun translate(
        notes: Map<String, String>,
        sourceLanguageTag: String,
        targetLanguageTag: String,
    ): NoteTranslationResult {
        val source = TranslateLanguage.fromLanguageTag(sourceLanguageTag) ?: return NoteTranslationResult.Unavailable
        val target = TranslateLanguage.fromLanguageTag(targetLanguageTag) ?: return NoteTranslationResult.Unavailable
        if (source == target) return NoteTranslationResult.Ready(notes)
        val modelManager = RemoteModelManager.getInstance()
        val sourceModel = TranslateRemoteModel.Builder(source).build()
        val targetModel = TranslateRemoteModel.Builder(target).build()
        // Opening the waiter view is never held up by a download. Models are requested quietly
        // when the menu opens; if either one is still missing, keep the diner note and fall back.
        val modelsReady =
            runCatching {
                modelManager.isModelDownloaded(sourceModel).await() &&
                    modelManager.isModelDownloaded(targetModel).await()
            }.getOrDefault(false)
        if (!modelsReady) {
            return NoteTranslationResult.Unavailable
        }

        val translator =
            Translation.getClient(
                TranslatorOptions
                    .Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build(),
            )
        return try {
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
