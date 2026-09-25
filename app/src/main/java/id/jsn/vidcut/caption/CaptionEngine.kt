package id.jsn.vidcut.caption

import id.jsn.vidcut.subtitle.SubtitleCue

/** Pluggable transcription contract. A Whisper/Vosk implementation can be supplied later. */
interface CaptionEngine {
    suspend fun transcribe(audioPath: String, language: String = "id"): List<SubtitleCue>
}

class CaptionPipeline {
    fun normalize(cues: List<SubtitleCue>, maxChars: Int = 42): List<SubtitleCue> =
        cues.map { cue ->
            val text = cue.text.replace(Regex("\\s+"), " ").trim()
            if (text.length <= maxChars) cue.copy(text = text)
            else cue.copy(text = text.chunked(maxChars).joinToString("\n"))
        }.filter { it.endMs > it.startMs && it.text.isNotBlank() }
}
