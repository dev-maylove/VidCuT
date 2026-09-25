package id.jsn.vidcut.dubbing

import android.content.Context
import android.media.*
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Decodes the first audio track of a video to signed 16-bit mono PCM WAV.
 * The target sample rate is configurable: 16 kHz for STT, 44.1 kHz for mixing.
 */
class AudioDecoder(private val context: Context) {

    fun extractTo16kMonoWav(source: Uri, output: File): File =
        extractToPcmWav(source, output, 16_000)

    fun extractToPcmWav(source: Uri, output: File, targetRate: Int): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, source, null)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                track = i
                break
            }
        }
        require(track >= 0) { "Video tidak memiliki audio track." }

        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Audio MIME kosong")
        val sourceRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        extractor.selectTrack(track)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        output.parentFile?.mkdirs()
        val pcm = ArrayList<Short>(sourceRate * 30)
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuf = decoder.getInputBuffer(inIndex)!!
                        inBuf.clear()
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0), 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                when {
                    outIndex >= 0 -> {
                        val outBuf = decoder.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val bytes = ByteArray(bufferInfo.size)
                            outBuf.get(bytes)
                            appendResampled(bytes, sourceRate, channels, targetRate, pcm)
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                }
            }
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
            extractor.release()
        }

        require(pcm.isNotEmpty()) { "Audio tidak berhasil didekode." }
        writeWav(output, pcm.toShortArray(), targetRate)
        return output
    }

    private fun appendResampled(
        bytes: ByteArray,
        sourceRate: Int,
        channels: Int,
        targetRate: Int,
        out: MutableList<Short>
    ) {
        val input = ShortArray(bytes.size / 2)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in input.indices) input[i] = bb.short
        val frames = input.size / channels
        if (frames <= 0) return

        val mono = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += input[f * channels + c].toInt()
            mono[f] = (sum / channels).coerceIn(-32768, 32767).toShort()
        }

        val targetFrames = (frames.toDouble() * targetRate / sourceRate)
            .roundToInt().coerceAtLeast(1)
        for (i in 0 until targetFrames) {
            val pos = i.toDouble() * (frames - 1).coerceAtLeast(1) /
                (targetFrames - 1).coerceAtLeast(1)
            val a = pos.toInt().coerceIn(0, frames - 1)
            val b = (a + 1).coerceIn(0, frames - 1)
            val frac = pos - a
            out += (mono[a] * (1.0 - frac) + mono[b] * frac)
                .roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun writeWav(file: File, samples: ShortArray, rate: Int) {
        val dataBytes = samples.size * 2
        BufferedOutputStream(file.outputStream()).use { out ->
            fun leInt(v: Int) = out.write(byteArrayOf(
                (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(),
                ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()
            ))
            fun leShort(v: Int) = out.write(byteArrayOf(
                (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte()
            ))
            out.write("RIFF".toByteArray()); leInt(36 + dataBytes)
            out.write("WAVEfmt ".toByteArray()); leInt(16); leShort(1); leShort(1)
            leInt(rate); leInt(rate * 2); leShort(2); leShort(16)
            out.write("data".toByteArray()); leInt(dataBytes)
            val bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { bb.clear(); bb.putShort(it); out.write(bb.array()) }
        }
    }
}