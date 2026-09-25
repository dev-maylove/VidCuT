package id.jsn.vidcut.dubbing

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslationEngine : TranslationEngine {
    override suspend fun translate(text: String, sourceLanguage: String, targetLanguage: String): String {
        if (text.isBlank() || sourceLanguage.equals(targetLanguage, true)) return text

        val source = mapLanguage(sourceLanguage)
        val target = mapLanguage(targetLanguage)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return translator.translate(text).await()
        } finally {
            translator.close()
        }
    }

    private fun mapLanguage(tag: String): String = when (tag.lowercase().substringBefore('-')) {
        "id", "in" -> TranslateLanguage.INDONESIAN
        "en" -> TranslateLanguage.ENGLISH
        "es" -> TranslateLanguage.SPANISH
        "fr" -> TranslateLanguage.FRENCH
        "de" -> TranslateLanguage.GERMAN
        "it" -> TranslateLanguage.ITALIAN
        "pt" -> TranslateLanguage.PORTUGUESE
        "ja" -> TranslateLanguage.JAPANESE
        "ko" -> TranslateLanguage.KOREAN
        "zh" -> TranslateLanguage.CHINESE
        "ru" -> TranslateLanguage.RUSSIAN
        else -> error("Bahasa belum dipetakan: $tag")
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
}