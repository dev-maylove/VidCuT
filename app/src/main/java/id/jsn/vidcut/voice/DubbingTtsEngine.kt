package id.jsn.vidcut.voice

import android.content.Context
import id.jsn.vidcut.dubbing.DubbingSegment
import java.io.File

/** Thin adapter kept in the existing voice package for backward compatibility. */
class DubbingTtsEngine(private val context: Context) {
    fun synthesize(segment: DubbingSegment): File =
        TtsEngine(context).synthesize(segment.dubbedText, segment.speaker.locale)
}
