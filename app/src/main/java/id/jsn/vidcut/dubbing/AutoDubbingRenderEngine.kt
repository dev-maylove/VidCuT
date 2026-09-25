package id.jsn.vidcut.dubbing

import android.content.Context
import android.media.*
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

class AutoDubbingRenderEngine(private val context: Context) {

    /**
     * Produces a final MP4 by copying the original video track and replacing/adding
     * its audio with the generated AAC dubbing track. This avoids a second video encode.
     */
    fun muxVideoWithDubbing(source: Uri, dubbingM4a: File, output: File): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, source, null)
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(dubbingM4a.absolutePath)

        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val trackMap = IntArray(extractor.trackCount) { -1 }
        var videoTrack = -1
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && videoTrack < 0) {
                extractor.selectTrack(i)
                videoTrack = muxer.addTrack(f)
                trackMap[i] = videoTrack
            }
        }

        require(videoTrack >= 0) { "Video track tidak ditemukan." }

        var audioIndex = -1
        for (i in 0 until audioExtractor.trackCount) {
            val f = audioExtractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioIndex = i
                break
            }
        }
        require(audioIndex >= 0) { "Dubbing AAC track tidak ditemukan." }
        audioExtractor.selectTrack(audioIndex)
        val audioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioIndex))
        muxer.start()

        copyTrack(extractor, muxer, videoTrack, 8 * 1024 * 1024)
        copyTrack(audioExtractor, muxer, audioTrack, 2 * 1024 * 1024)

        muxer.stop()
        muxer.release()
        extractor.release()
        audioExtractor.release()
        return output
    }

    private fun copyTrack(extractor: MediaExtractor, muxer: MediaMuxer, track: Int, maxBuffer: Int) {
        val buffer = ByteBuffer.allocate(maxBuffer)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.flags = extractor.sampleFlags
            info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0)
            muxer.writeSampleData(track, buffer, info)
            extractor.advance()
            buffer.clear()
        }
    }
}