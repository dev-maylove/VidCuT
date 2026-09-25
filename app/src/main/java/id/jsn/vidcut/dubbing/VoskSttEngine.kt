package id.jsn.vidcut.dubbing

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Offline STT adapter. A Vosk model must be installed in filesDir/vosk/<language>/.
 * The model itself is deliberately not bundled into the APK because mobile models
 * can be tens of megabytes and have independent licenses.
 */
class VoskSttEngine(private val context: Context) : SpeechToTextEngine {

    override fun transcribe(pcm16Mono16k: File, languageTag: String): List<AutoDubbingSegment> {
        val modelDir = File(context.filesDir, "vosk/${languageTag.lowercase()}")
        require(modelDir.isDirectory) {
            "Model Vosk untuk '$languageTag' belum dipasang: ${modelDir.absolutePath}"
        }

        val segments = mutableListOf<AutoDubbingSegment>()
        Model(modelDir.absolutePath).use { model ->
            Recognizer(model, 16_000f).use { recognizer ->
                recognizer.setWords(true)
                FileInputStream(pcm16Mono16k).use { input ->
                    skipWavHeader(input)
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        if (recognizer.acceptWaveForm(buffer, n)) {
                            parseResult(recognizer.result, segments)
                        }
                    }
                    parseResult(recognizer.finalResult, segments)
                }
            }
        }
        return normalizeSegments(segments)
    }

    private fun parseResult(json: String, out: MutableList<AutoDubbingSegment>) {
        val root = JSONObject(json)
        val words = root.optJSONArray("result")
        val text = root.optString("text").trim()
        if (words == null || words.length() == 0 || text.isBlank()) return
        val start = (words.optJSONObject(0)?.optDouble("start", 0.0) ?: 0.0) * 1000.0
        val end = (words.optJSONObject(words.length() - 1)?.optDouble("end", start / 1000.0) ?: start / 1000.0) * 1000.0
        out += AutoDubbingSegment(
            startMs = start.toLong().coerceAtLeast(0),
            endMs = end.toLong().coerceAtLeast(start.toLong() + 100),
            sourceText = text,
            translatedText = text
        )
    }

    private fun normalizeSegments(input: List<AutoDubbingSegment>): List<AutoDubbingSegment> {
        if (input.isEmpty()) return emptyList()
        return input.sortedBy { it.startMs }.mapIndexed { index, s ->
            s.copy(speakerIndex = index % 2)
        }
    }

    private fun skipWavHeader(input: FileInputStream) {
        val h = ByteArray(12)
        if (input.read(h) != 12) error("WAV header rusak")
        var consumed = 12
        while (true) {
            val chunk = ByteArray(8)
            if (input.read(chunk) != 8) break
            val size = ((chunk[4].toInt() and 255)) or
                ((chunk[5].toInt() and 255) shl 8) or
                ((chunk[6].toInt() and 255) shl 16) or
                ((chunk[7].toInt() and 255) shl 24)
            val id = String(chunk, 0, 4)
            if (id == "data") return
            val skip = size + (size and 1)
            var left = skip
            while (left > 0) {
                val n = input.skip(left.toLong()).toInt()
                if (n <= 0) break
                left -= n
            }
            consumed += 8 + skip
        }
    }

    companion object {
        fun modelDir(context: Context, languageTag: String): File =
            File(context.filesDir, "vosk/${languageTag.lowercase()}")

        fun downloadAndInstall(context: Context, languageTag: String, url: String): File {
            val tmp = File(context.cacheDir, "vosk-model.zip")
            val target = modelDir(context, languageTag)
            target.parentFile?.mkdirs()
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.requestMethod = "GET"
            connection.connect()
            require(connection.responseCode in 200..299) { "Download model HTTP ${connection.responseCode}" }
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            ZipInputStream(FileInputStream(tmp)).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    val name = e.name.replace('\\', '/')
                    if (name.contains("..")) continue
                    val relative = name.substringAfter('/', name)
                    if (relative.isBlank()) continue
                    val out = File(target, relative)
                    out.parentFile?.mkdirs()
                    if (!e.isDirectory) FileOutputStream(out).use { zis.copyTo(it, 64 * 1024) }
                }
            }
            tmp.delete()
            require(File(target, "am").exists() || File(target, "conf").exists()) {
                "ZIP model tidak terlihat seperti model Vosk."
            }
            return target
        }
    }
}