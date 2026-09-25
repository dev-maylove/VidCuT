package id.jsn.vidcut.engine

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class ScenePoint(val seconds: Double)

class SimpleSceneDetector(private val context: Context) {

    suspend fun detect(
        uri: Uri,
        threshold: Double = 0.35,
        sampleEveryMs: Long = 1000L
    ): List<ScenePoint> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        val out = mutableListOf<ScenePoint>()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: return@withContext emptyList()

            var prev: Bitmap? = null
            var t = 0L
            while (t < durationMs) {
                // getFrameAtTime expects microseconds
                val frame = retriever.getFrameAtTime(
                    t * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (frame != null) {
                    val previous = prev
                    if (previous != null && diff(previous, frame) >= threshold) {
                        out += ScenePoint(t / 1000.0)
                    }
                    previous?.recycle()
                    prev = frame.copy(Bitmap.Config.RGB_565, false)
                    frame.recycle()
                }
                t += sampleEveryMs
            }
            prev?.recycle()
            out
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun diff(a: Bitmap, b: Bitmap): Double {
        val w = 24
        val h = 24
        val x = Bitmap.createScaledBitmap(a, w, h, true)
        val y = Bitmap.createScaledBitmap(b, w, h, true)
        val p = IntArray(w * h)
        val q = IntArray(w * h)
        x.getPixels(p, 0, w, 0, 0, w, h)
        y.getPixels(q, 0, w, 0, 0, w, h)
        var s = 0L
        for (i in p.indices) {
            s += abs(((p[i] shr 16) and 255) - ((q[i] shr 16) and 255))
            s += abs(((p[i] shr 8) and 255) - ((q[i] shr 8) and 255))
            s += abs((p[i] and 255) - (q[i] and 255))
        }
        x.recycle()
        y.recycle()
        return s.toDouble() / (w * h * 3 * 255)
    }
}
