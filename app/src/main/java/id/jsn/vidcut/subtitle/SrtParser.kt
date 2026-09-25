package id.jsn.vidcut.subtitle

object SrtParser {
    fun parse(input: String): List<SubtitleCue> = input.replace("\r", "").split("\n\n")
        .mapNotNull { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.size < 3) return@mapNotNull null
            val timing = lines[1].split(" --> ")
            if (timing.size != 2) return@mapNotNull null
            val start = parseTime(timing[0].trim()) ?: return@mapNotNull null
            val end = parseTime(timing[1].trim()) ?: return@mapNotNull null
            SubtitleCue(start, end, lines.drop(2).joinToString("\n"))
        }
        .sortedBy { it.startMs }

    private fun parseTime(s: String): Long? {
        val p = s.replace(',', ':').split(':')
        if (p.size != 4) return null
        return runCatching {
            val h = p[0].toLong(); val m = p[1].toLong(); val sec = p[2].toLong(); val ms = p[3].toLong()
            h * 3_600_000 + m * 60_000 + sec * 1_000 + ms
        }.getOrNull()
    }
}
