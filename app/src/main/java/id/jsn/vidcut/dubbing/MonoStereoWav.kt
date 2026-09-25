package id.jsn.vidcut.dubbing

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MonoStereoWav {
    fun duplicateMonoToStereo(input: File, output: File): File {
        val reader = PcmWavReader(input)
        require(reader.channels == 1) { "Input harus mono PCM WAV." }
        WavWriter(output, reader.sampleRate, 2).use { writer ->
            val buf = ShortArray(8192)
            while (true) {
                val n = reader.read(buf)
                if (n <= 0) break
                for (i in 0 until n) writer.writeStereo(buf[i], buf[i])
            }
        }
        reader.close()
        return output
    }

    class PcmWavReader(private val file: File) {
        private val input = BufferedInputStream(file.inputStream(), 64 * 1024)
        val sampleRate: Int
        val channels: Int
        val bitsPerSample: Int
        private var remainingSamples: Long

        init {
            val riff = ByteArray(12)
            require(input.read(riff) == 12) { "WAV tidak valid." }
            require(String(riff, 0, 4) == "RIFF" && String(riff, 8, 4) == "WAVE") {
                "Header RIFF/WAVE tidak ditemukan."
            }

            var rate = 0
            var ch = 0
            var bits = 16
            var dataBytes = -1L
            // Walk chunks until "data"
            val hdr = ByteArray(8)
            while (true) {
                val n = input.read(hdr)
                if (n < 8) break
                val id = String(hdr, 0, 4)
                val size = leInt(hdr, 4).toLong() and 0xffffffffL
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size.toInt().coerceAtLeast(16))
                        require(input.read(fmt) == fmt.size) { "Chunk fmt rusak." }
                        // audio format at 0, channels at 2, rate at 4, bits at 14
                        ch = leShort(fmt, 2)
                        rate = leInt(fmt, 4)
                        bits = leShort(fmt, 14)
                        if (size % 2 != 0L) input.read() // pad byte
                    }
                    "data" -> {
                        dataBytes = size
                        break
                    }
                    else -> {
                        // skip unknown chunk (+ pad)
                        var left = size + (size and 1)
                        while (left > 0) {
                            val skipped = input.skip(left)
                            if (skipped <= 0) break
                            left -= skipped
                        }
                    }
                }
            }
            require(rate > 0 && ch > 0 && dataBytes >= 0) {
                "Chunk fmt/data tidak lengkap di ${file.name}."
            }
            require(bits == 16) { "Hanya PCM 16-bit yang didukung (got $bits)." }
            sampleRate = rate
            channels = ch
            bitsPerSample = bits
            remainingSamples = dataBytes / (2L * ch.coerceAtLeast(1))
        }

        fun read(dst: ShortArray): Int {
            val count = minOf(dst.size.toLong(), remainingSamples).toInt()
            var readSamples = 0
            val bytes = ByteArray(2)
            while (readSamples < count) {
                if (input.read(bytes) != 2) break
                dst[readSamples++] = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).short
            }
            remainingSamples -= readSamples
            return readSamples
        }

        fun readAllFloat(): FloatArray {
            // For mono: one float per sample. For multi-channel we average to mono.
            val out = ArrayList<Float>((remainingSamples.coerceAtMost(Int.MAX_VALUE.toLong())).toInt())
            val buf = ShortArray(8192 * channels.coerceAtLeast(1))
            while (true) {
                val n = read(buf)
                if (n <= 0) break
                if (channels == 1) {
                    for (i in 0 until n) out += buf[i] / 32768f
                } else {
                    var i = 0
                    while (i + channels <= n) {
                        var sum = 0
                        for (c in 0 until channels) sum += buf[i + c].toInt()
                        out += (sum / channels) / 32768f
                        i += channels
                    }
                }
            }
            return out.toFloatArray()
        }

        fun close() = input.close()

        private fun leShort(b: ByteArray, p: Int) =
            (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)

        private fun leInt(b: ByteArray, p: Int) =
            (b[p].toInt() and 255) or
                ((b[p + 1].toInt() and 255) shl 8) or
                ((b[p + 2].toInt() and 255) shl 16) or
                ((b[p + 3].toInt() and 255) shl 24)
    }

    class WavWriter(
        private val file: File,
        private val sampleRate: Int,
        private val channels: Int
    ) : AutoCloseable {
        private val out = BufferedOutputStream(file.outputStream(), 64 * 1024)
        private var dataBytes = 0L

        init {
            require(channels in 1..2)
            writeHeaderPlaceholder()
        }

        fun writeMono(sample: Short) {
            val b = byteArrayOf((sample.toInt() and 255).toByte(), ((sample.toInt() shr 8) and 255).toByte())
            out.write(b); dataBytes += 2
        }

        fun writeStereo(left: Short, right: Short) {
            writeMono(left); writeMono(right)
        }

        private fun writeHeaderPlaceholder() {
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(0); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(channels.toShort())
            h.putInt(sampleRate); h.putInt(sampleRate * channels * 2)
            h.putShort((channels * 2).toShort()); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(0)
            out.write(h.array())
        }

        override fun close() {
            out.flush()
            out.close()
            java.io.RandomAccessFile(file, "rw").use { raf ->
                raf.seek(4L); raf.writeIntLE((36L + dataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                raf.seek(40L); raf.writeIntLE(dataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
        }

        private fun java.io.RandomAccessFile.writeIntLE(v: Int) {
            write(byteArrayOf(
                (v and 255).toByte(), ((v shr 8) and 255).toByte(),
                ((v shr 16) and 255).toByte(), ((v shr 24) and 255).toByte()
            ))
        }
    }
}