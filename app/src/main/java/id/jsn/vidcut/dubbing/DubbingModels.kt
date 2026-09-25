package id.jsn.vidcut.dubbing

import java.util.Locale
import java.util.UUID

data class DubbingSegment(
    val id: String = UUID.randomUUID().toString(),
    val startMs: Long,
    val endMs: Long,
    val sourceText: String,
    val dubbedText: String = sourceText,
    val speaker: DubbingSpeaker = DubbingSpeaker.MALE_NATURAL
) {
    init {
        require(startMs >= 0) { "Start dubbing tidak boleh negatif." }
        require(endMs > startMs) { "End dubbing harus lebih besar dari start." }
        require(sourceText.isNotBlank()) { "Teks dubbing kosong." }
    }
}

enum class DubbingSpeaker(
    val label: String,
    val locale: Locale,
    val pitch: Float,
    val rate: Float
) {
    MALE_NATURAL("Male · Natural", Locale("id", "ID"), 0.95f, 1.0f),
    FEMALE_NATURAL("Female · Natural", Locale("id", "ID"), 1.12f, 1.0f),
    MALE_ENGLISH("Male · English", Locale.US, 0.95f, 1.0f),
    FEMALE_ENGLISH("Female · English", Locale.US, 1.10f, 1.0f)
}

data class DubbingProject(
    val segments: List<DubbingSegment> = emptyList(),
    val originalVolume: Float = 0.20f,
    val dubbingVolume: Float = 1.0f
)
