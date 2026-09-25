package id.jsn.vidcut.engine

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import id.jsn.vidcut.model.Clip
import java.io.File

class BatchRenderEngine(private val context: Context) {
    fun export(
        source: Uri,
        clips: List<Clip>,
        onClipProgress: (Int, Int) -> Unit,
        onComplete: (List<Clip>) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (clips.isEmpty()) { onComplete(emptyList()); return }
        exportAt(0, source, clips, mutableListOf(), onClipProgress, onComplete, onError)
    }

    private fun exportAt(
        pos: Int, source: Uri, clips: List<Clip>, done: MutableList<Clip>,
        onClipProgress: (Int, Int) -> Unit, onComplete: (List<Clip>) -> Unit, onError: (Throwable) -> Unit
    ) {
        if (pos >= clips.size) { onComplete(done); return }
        val clip = clips[pos]
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        dir.mkdirs()
        val out = File(dir, "reupload_${clip.index}_${System.currentTimeMillis()}.mp4")
        val item = MediaItem.Builder().setUri(source)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMs).setEndPositionMs(clip.endMs).build()).build()
        val edited = EditedMediaItem.Builder(item).build()
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, result: ExportResult) {
                    MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf("video/mp4"), null)
                    done += clip.copy(status = "EXPORTED", outputPath = out.absolutePath)
                    onClipProgress(pos + 1, clips.size)
                    exportAt(pos + 1, source, clips, done, onClipProgress, onComplete, onError)
                }
                override fun onError(composition: androidx.media3.transformer.Composition, result: ExportResult, exception: ExportException) {
                    onError(exception)
                }
            }).build()
        try { transformer.start(edited, out.absolutePath) } catch (t: Throwable) { onError(t) }
    }
}
