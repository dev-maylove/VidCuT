package id.jsn.vidcut.dubbing

import java.io.File
import java.util.UUID

data class AutoDubbingOptions(
    val sourceLanguage: String = "en",
    val targetLanguage: String = "id",
    val preserveOriginalAudio: Float = 0.20f,
    val dubbingVolume: Float = 1.0f,
    val musicVolume: Float = 0.75f,
    val effectsVolume: Float = 0.90f,
    val speakerA: DubbingSpeaker = DubbingSpeaker.FEMALE_NATURAL,
    val speakerB: DubbingSpeaker = DubbingSpeaker.MALE_NATURAL
)

data class AutoDubbingSegment(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val sourceText: String,
    val translatedText: String,
    val speakerIndex: Int = 0,
    val confidence: Float = 0f
) {
    init {
        require(startMs >= 0)
        require(endMs > startMs)
        require(sourceText.isNotBlank())
        require(translatedText.isNotBlank())
    }

    fun toDubbingSegment(options: AutoDubbingOptions): DubbingSegment {
        val speaker = if (speakerIndex % 2 == 0) options.speakerA else options.speakerB
        return DubbingSegment(
            startMs = startMs,
            endMs = endMs,
            sourceText = sourceText,
            dubbedText = translatedText,
            speaker = speaker
        )
    }
}

data class AutoDubbingResult(
    val sourceAudio: File,
    val segments: List<AutoDubbingSegment>,
    val dubbingWav: File,
    val mixedAac: File,
    val finalVideo: File?,
    /** Phase 15C: stem paths for optional preview / debug. */
    val stemDialogue: File? = null,
    val stemMusic: File? = null,
    val stemEffects: File? = null
)

interface SpeechToTextEngine {
    fun transcribe(
        pcm16Mono16k: File,
        languageTag: String
    ): List<AutoDubbingSegment>
}

interface TranslationEngine {
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String
}
