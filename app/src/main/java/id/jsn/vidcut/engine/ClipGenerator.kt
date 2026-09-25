package id.jsn.vidcut.engine

import id.jsn.vidcut.model.Clip

object ClipGenerator {
    fun generate(
        durationMs: Long,
        scenePoints: List<ScenePoint>,
        minMs: Long = 8_000L,
        maxMs: Long = 45_000L,
        maxClips: Int = 5
    ): List<Clip> {
        if (durationMs < minMs) return emptyList()
        // ScenePoint.seconds is in seconds; convert to milliseconds.
        val points = (listOf(0L) + scenePoints.map { (it.seconds * 1000.0).toLong() })
            .filter { it in 0 until durationMs }
            .distinct()
            .sorted()
        val starts = points.filter { it + minMs <= durationMs }
        val result = mutableListOf<Clip>()
        for ((i, start) in starts.withIndex()) {
            if (result.size >= maxClips) break
            val next = points.drop(i + 1).firstOrNull { it - start >= minMs }
            val naturalEnd = next ?: durationMs
            val end = minOf(naturalEnd, start + maxMs, durationMs)
            if (end - start >= minMs) {
                result += Clip(
                    id = System.nanoTime() + result.size,
                    index = result.size + 1,
                    name = "Clip ${result.size + 1}",
                    startMs = start,
                    endMs = end
                )
            }
        }
        if (result.isEmpty()) {
            result += Clip(1L, 1, "Clip 1", 0L, minOf(durationMs, maxMs))
        }
        return result
    }
}
