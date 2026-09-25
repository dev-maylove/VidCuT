package id.jsn.vidcut.model

data class Clip(
    val id: Long,
    val index: Int,
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val status: String = "READY",
    val outputPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}
