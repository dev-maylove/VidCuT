package id.jsn.vidcut.engine

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import id.jsn.vidcut.subtitle.SubtitleCue
import id.jsn.vidcut.subtitle.SubtitleOverlay
import java.io.File

@OptIn(UnstableApi::class)
class SubtitleRenderEngine(private val context: Context) {
    fun render(source: String, output: File, cues: List<SubtitleCue>, listener: Transformer.Listener) {
        val item = MediaItem.fromUri(source)
        val overlay = SubtitleOverlay(cues)
        val effects = Effects(
            /* audioProcessors = */ emptyList(),
            /* videoEffects = */ listOf(OverlayEffect(listOf(overlay)))
        )
        val edited = EditedMediaItem.Builder(item).setEffects(effects).build()
        Transformer.Builder(context).addListener(listener).build().start(edited, output.absolutePath)
    }
}
