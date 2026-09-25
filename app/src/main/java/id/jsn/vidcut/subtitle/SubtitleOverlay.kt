package id.jsn.vidcut.subtitle
import androidx.media3.common.util.UnstableApi

import android.text.SpannableString
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.StaticOverlaySettings

@OptIn(UnstableApi::class)
class SubtitleOverlay(private val cues: List<SubtitleCue>) : TextOverlay() {
    override fun getText(presentationTimeUs: Long): SpannableString {
        val ms = presentationTimeUs / 1000L
        val cue = cues.lastOrNull { ms >= it.startMs && ms < it.endMs }
        return SpannableString(cue?.text ?: "")
    }

    override fun getOverlaySettings(presentationTimeUs: Long) =
        StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(0f, -0.72f)
            .setBackgroundFrameAnchor(0f, -0.72f)
            .build()
}
