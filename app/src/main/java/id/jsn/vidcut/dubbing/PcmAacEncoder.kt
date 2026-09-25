package id.jsn.vidcut.dubbing

import android.media.*
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmAacEncoder {
    fun encodeWavToM4a(wav: File, output: File, sampleRate: Int = 44_100, bitrate: Int = 128_000): File {
        val extractor = WavReader(wav)
        val codecFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(codecFormat)
            ?: error("AAC encoder tidak tersedia di perangkat.")
        val codec = MediaCodec.createByCodecName(codecName)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        codec.configure(codecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        var track = -1
        var started = false
        val info = MediaCodec.BufferInfo()
        var eos = false
        try {
            while (!eos) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val input = codec.getInputBuffer(inIndex)!!
                    input.clear()
                    val chunk = extractor.readPcm(input)
                    if (chunk <= 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, extractor.ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, chunk, extractor.ptsUs, 0)
                    }
                }
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(info, 0)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                            started = true
                        }
                        outIndex >= 0 -> {
                            val out = codec.getOutputBuffer(outIndex)!!
                            if (info.size > 0 && started && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                out.position(info.offset)
                                out.limit(info.offset + info.size)
                                muxer.writeSampleData(track, out, info)
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                eos = true
                                break
                            }
                        }
                        else -> break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (started) runCatching { muxer.stop() }
            muxer.release()
            extractor.close()
        }
        return output
    }

    private class WavReader(private val file: File) {
        private val input = FileInputStream(file)
        var ptsUs = 0L
            private set
        private var dataRemaining = 0L
        private var sampleRate = 44_100
        init {
            val header = ByteArray(44)
            require(input.read(header) == 44) { "WAV terlalu pendek." }
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE")
            sampleRate = bb.getInt(24)
            dataRemaining = bb.getInt(40).toLong() and 0xffffffffL
        }
        fun readPcm(dst: ByteBuffer): Int {
            if (dataRemaining <= 0) return -1
            val max = minOf(dst.remaining(), dataRemaining.toInt())
            val buf = ByteArray(max)
            val n = input.read(buf)
            if (n <= 0) return -1
            dst.put(buf, 0, n)
            dataRemaining -= n
            ptsUs += n / 2L * 1_000_000L / sampleRate
            return n
        }
        fun close() = input.close()
    }
}