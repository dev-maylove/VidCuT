package id.jsn.vidcut.dubbing

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.BufferedOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device dubbing engine (Phase 15).
 *
 * Pipeline: timed dialogue → Android TTS → time-stretch to slot → edge fade →
 * silence gap → PCM timeline mix.
 */
class DubbingEngine(private val context: Context) {
    data class Result(val output: File, val durationMs: Long, val segmentCount: Int)

    private data class PcmTrack(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    /** Optional cancellation flag for long jobs. */
    private var cancelled: AtomicBoolean? = null

    fun generate(
        project: DubbingProject,
        timeoutSec: Long = 180,
        cancelFlag: AtomicBoolean? = null
    ): Result {
        cancelled = cancelFlag
        require(project.segments.isNotEmpty()) { "Belum ada segmen dubbing." }
        require(project.dubbingVolume in 0f..1.5f)
        checkCancelled()

        val sorted = project.segments.sortedBy { it.startMs }
        val files = sorted.map { segment ->
            checkCancelled()
            val wav = File(context.cacheDir, "dub-${UUID.randomUUID()}.wav")
            synthesizeSegment(segment, wav, timeoutSec)
            segment to wav
        }

        val durationMs = sorted.maxOf { it.endMs }
        val output = File(context.cacheDir, "vidcut-dubbing-${UUID.randomUUID()}.wav")
        mixPcm(files, output, durationMs, project.dubbingVolume)
        files.forEach { it.second.delete() }
        return Result(output, durationMs, sorted.size)
    }

    private fun checkCancelled() {
        if (cancelled?.get() == true) throw InterruptedException("Dubbing dibatalkan.")
    }

    private fun synthesizeSegment(segment: DubbingSegment, output: File, timeoutSec: Long) {
        val ready = CountDownLatch(1)
        val done = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            initStatus = status
            if (status == TextToSpeech.SUCCESS) {
                val languageResult = tts.setLanguage(segment.speaker.locale)
                check(
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                        languageResult != TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    "Bahasa TTS tidak tersedia: ${segment.speaker.locale}"
                }
                // Slightly slower rate for low-confidence segments feels more natural
                val rate = segment.speaker.rate
                tts.setPitch(segment.speaker.pitch)
                tts.setSpeechRate(rate)
            }
            ready.countDown()
        }
        check(ready.await(10, TimeUnit.SECONDS)) { "TTS initialization timeout" }
        check(initStatus == TextToSpeech.SUCCESS) {
            "TTS tidak tersedia untuk ${segment.speaker.locale}."
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                done.countDown()
            }
            override fun onError(utteranceId: String?) {
                done.countDown()
            }
        })
        val id = UUID.randomUUID().toString()
        val result = tts.synthesizeToFile(segment.dubbedText, Bundle(), output, id)
        check(result == TextToSpeech.SUCCESS) { "Gagal memulai TTS dubbing." }
        check(done.await(timeoutSec, TimeUnit.SECONDS)) { "TTS dubbing timeout" }
        tts.stop()
        tts.shutdown()
        check(output.exists() && output.length() > 44) { "Audio dubbing tidak terbentuk." }
    }

    private fun mixPcm(
        files: List<Pair<DubbingSegment, File>>,
        output: File,
        durationMs: Long,
        gain: Float
    ) {
        val targetRate = 44100
        val gapMs = 100L // Phase 15C: silence between segments feel
        val totalFrames = (durationMs * targetRate / 1000L).toInt().coerceAtLeast(1)
        val mixed = IntArray(totalFrames)

        files.forEach { (segment, file) ->
            checkCancelled()
            val track = readPcm16(file)
            val mono = toMono(track)
            val resampled = resample(mono, track.sampleRate, targetRate)

            // Time-stretch so TTS fills the dialogue slot (minus small gap)
            val slotMs = (segment.endMs - segment.startMs - gapMs / 2).coerceAtLeast(80)
            val targetFrames = (slotMs * targetRate / 1000L).toInt().coerceAtLeast(1)
            // Only stretch if ratio is within sane bounds (0.65x … 1.55x)
            val ratio = targetFrames.toDouble() / resampled.size.coerceAtLeast(1)
            val stretched = when {
                ratio in 0.65..1.55 -> AudioProcessing.timeStretchToFrames(resampled, targetFrames)
                ratio > 1.55 -> {
                    // Too short: stretch to max then pad silence
                    val maxFrames = (resampled.size * 1.55).roundToInt()
                    val s = AudioProcessing.timeStretchToFrames(resampled, maxFrames)
                    s + ShortArray((targetFrames - maxFrames).coerceAtLeast(0))
                }
                else -> {
                    // Too long: compress to min then truncate
                    val minFrames = (resampled.size * 0.65).roundToInt().coerceAtLeast(1)
                    AudioProcessing.timeStretchToFrames(resampled, minOf(minFrames, targetFrames))
                }
            }

            AudioProcessing.applyEdgeFade(stretched, fadeSamples = (targetRate * 0.012).toInt())

            val offset = (segment.startMs * targetRate / 1000L).toInt()
            val frames = min(stretched.size, totalFrames - offset)
            if (offset >= 0 && frames > 0) {
                for (i in 0 until frames) {
                    val added = (stretched[i] * gain).toInt()
                    mixed[offset + i] = (mixed[offset + i] + added).coerceIn(-32768, 32767)
                }
            }
        }

        BufferedOutputStream(output.outputStream()).use { out ->
            writeWavHeader(out, totalFrames * 2, targetRate, 1, 16)
            val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            mixed.forEach { sample ->
                if (buffer.remaining() < 2) {
                    out.write(buffer.array(), 0, buffer.position())
                    buffer.clear()
                }
                buffer.putShort(sample.toShort())
            }
            if (buffer.position() > 0) out.write(buffer.array(), 0, buffer.position())
        }
    }

    private fun readPcm16(wav: File): PcmTrack {
        val bytes = wav.readBytes()
        require(bytes.size > 44) { "WAV dubbing tidak valid." }
        val fmtOffset = findChunk(bytes, "fmt ")
        val dataOffset = findChunk(bytes, "data")
        val channels = readShortLE(bytes, fmtOffset + 10)
        val sampleRate = readIntLE(bytes, fmtOffset + 12)
        val bits = readShortLE(bytes, fmtOffset + 22)
        require(bits == 16) { "Hanya WAV PCM 16-bit yang didukung." }
        require(channels in 1..2) { "Jumlah channel WAV tidak didukung: $channels" }

        val dataSize = readIntLE(bytes, dataOffset + 4).coerceAtMost(bytes.size - dataOffset - 8)
        val samples = ShortArray(dataSize / 2)
        var p = dataOffset + 8
        for (i in samples.indices) {
            samples[i] = readShortLE(bytes, p).toShort()
            p += 2
        }
        return PcmTrack(samples, sampleRate, channels)
    }

    private fun toMono(track: PcmTrack): ShortArray {
        if (track.channels == 1) return track.samples
        val frames = track.samples.size / track.channels
        return ShortArray(frames) { i ->
            ((track.samples[i * 2].toInt() + track.samples[i * 2 + 1].toInt()) / 2).toShort()
        }
    }

    private fun resample(input: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
        if (input.isEmpty() || sourceRate == targetRate) return input
        val outSize = (input.size.toLong() * targetRate / sourceRate).toInt().coerceAtLeast(1)
        return ShortArray(outSize) { i ->
            val src = i.toDouble() * sourceRate / targetRate
            val left = src.toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val frac = src - left
            (input[left] * (1.0 - frac) + input[right] * frac).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun findChunk(bytes: ByteArray, target: String): Int {
        var p = 12
        while (p + 8 <= bytes.size) {
            val id = String(bytes, p, 4, Charsets.US_ASCII)
            val size = readIntLE(bytes, p + 4)
            if (id == target) return p
            if (size < 0) break
            p += 8 + size + (size and 1)
        }
        error("Chunk WAV $target tidak ditemukan.")
    }

    private fun readShortLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun writeWavHeader(
        out: BufferedOutputStream,
        dataLength: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        fun writeAscii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeInt(v: Int) =
            out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
        fun writeShort(v: Int) =
            out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
        writeAscii("RIFF"); writeInt(36 + dataLength); writeAscii("WAVE")
        writeAscii("fmt "); writeInt(16); writeShort(1); writeShort(channels)
        writeInt(sampleRate); writeInt(byteRate); writeShort(blockAlign); writeShort(bitsPerSample)
        writeAscii("data"); writeInt(dataLength)
    }
}
