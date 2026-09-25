package id.jsn.vidcut.dubbing

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads and verifies the pinned TIGER-DnR LiteRT model revision. */
object TigerModelManager {
    const val REVISION = "c9cb0b1"
    private const val BASE_URL = "https://huggingface.co/litert-community/TIGER-DnR-LiteRT/resolve/$REVISION/"
    private const val EXPECTED_SIZE = 16_116_992L

    data class ModelSpec(
        val key: String,
        val fileName: String,
        val url: String,
        val expectedBytes: Long,
        val sha256: String
    )

    // SHA-256 values are the file-content hashes exposed by the pinned Hugging Face/Xet files.
    val specs = listOf(
        ModelSpec("dialogue", "tiger_dialog_fp16.tflite", BASE_URL + "tiger_dialog_fp16.tflite", EXPECTED_SIZE,
            "318e0cec4ded4e73e646ba876eab1244e667c3bc0fb52cfbd20edcce29214d3d"),
        ModelSpec("effects", "tiger_effect_fp16.tflite", BASE_URL + "tiger_effect_fp16.tflite", EXPECTED_SIZE,
            "46c608782339106f6e13c7c94d08f7dd54989210f880de361daae975507ba219"),
        ModelSpec("music", "tiger_music_fp16.tflite", BASE_URL + "tiger_music_fp16.tflite", EXPECTED_SIZE,
            "7a97fe583d5bea5fec57635fdcf8498aad97b8b97006c6e7492d305711d2aaa5")
    )

    fun root(context: Context): File = File(context.filesDir, "ai-models/tiger-dnr")
    fun modelFile(context: Context, key: String): File = File(root(context), specs.first { it.key == key }.fileName)

    fun verifyInstalled(context: Context): Boolean = specs.all { spec ->
        val f = File(root(context), spec.fileName)
        f.exists() && f.length() == spec.expectedBytes && sha256(f).equals(spec.sha256, ignoreCase = true)
    }

    fun isInstalled(context: Context): Boolean = verifyInstalled(context)

    fun installedBytes(context: Context): Long = specs.sumOf { spec ->
        File(root(context), spec.fileName).takeIf { it.exists() }?.length() ?: 0L
    }

    suspend fun downloadAll(
        context: Context,
        onProgress: (model: String, percent: Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        root(context).mkdirs()
        specs.forEach { spec ->
            val destination = File(root(context), spec.fileName)
            if (destination.exists() && destination.length() == spec.expectedBytes &&
                sha256(destination).equals(spec.sha256, ignoreCase = true)) {
                onProgress(spec.key, 100)
            } else {
                downloadOne(spec, destination, onProgress)
            }
        }
        check(verifyInstalled(context)) { "Verifikasi semua model TIGER-DnR gagal." }
    }

    private fun downloadOne(spec: ModelSpec, destination: File, onProgress: (String, Int) -> Unit) {
        val part = File(destination.parentFile, destination.name + ".part")
        var existing = if (part.exists()) part.length() else 0L
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
            }
            connection.connect()
            val code = connection.responseCode
            require(code in 200..299) { "HTTP $code saat mengunduh ${spec.fileName}" }
            val append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL
            if (!append) {
                existing = 0L
                part.delete()
            }

            val responseLength = connection.contentLengthLong
            val total = if (responseLength > 0L) responseLength + existing else spec.expectedBytes
            FileOutputStream(part, append).buffered(256 * 1024).use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var downloaded = existing
                    var last = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        if (percent != last) {
                            last = percent
                            onProgress(spec.key, percent)
                        }
                    }
                }
            }

            require(part.length() == spec.expectedBytes) {
                "Ukuran ${spec.fileName} tidak sesuai: ${part.length()} != ${spec.expectedBytes}"
            }
            val sha = sha256(part)
            require(sha.equals(spec.sha256, ignoreCase = true)) {
                "SHA-256 ${spec.fileName} tidak cocok: $sha"
            }

            val verified = File(destination.parentFile, destination.name + ".verified")
            verified.writeText("sha256=$sha\nrevision=$REVISION\nsize=${part.length()}\n")
            if (destination.exists()) destination.delete()
            check(part.renameTo(destination)) { "Gagal memasang ${spec.fileName}" }
            File(destination.parentFile, destination.name + ".sha256").writeText(sha + "\n")
            onProgress(spec.key, 100)
        } finally {
            connection?.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun deleteAll(context: Context) {
        root(context).deleteRecursively()
    }
}
