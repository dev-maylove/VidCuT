package id.jsn.vidcut.dubbing

import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Phase 15 mixer: Music + SFX + dubbing with loudness normalize, ducking, and peak limiter.
 */
class AudioMixer {

    fun mix(
        originalWav: File,
        dubbingWav: File,
        outputWav: File,
        originalGain: Float,
        dubbingGain: Float
    ): File {
        val original = WavPcm(originalWav)
        val dubbing = WavPcm(dubbingWav)
        require(original.sampleRate == dubbing.sampleRate) {
            "Sample rate audio harus sama untuk mixing."
        }
        val count = max(original.samples.size, dubbing.samples.size)
        val mixed = ShortArray(count)
        for (i in 0 until count) {
            val a = if (i < original.samples.size) original.samples[i] * originalGain else 0f
            val b = if (i < dubbing.samples.size) dubbing.samples[i] * dubbingGain else 0f
            mixed[i] = (a + b).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        writeWav(outputWav, AudioProcessing.limitPeak(mixed), original.sampleRate)
        return outputWav
    }

    /**
     * Mix separated stems + dubbing.
     * - Normalize each stem RMS toward target levels
     * - Duck music (and lightly SFX) under active voice
     * - Peak-limit final mix to ~−1 dBFS
     */
    fun mixSeparated(
        musicWav: File,
        effectsWav: File,
        dubbingWav: File,
        outputWav: File,
        musicGain: Float,
        effectsGain: Float,
        dubbingGain: Float,
        enableDucking: Boolean = true,
        enableNormalize: Boolean = true
    ): File {
        var music = WavPcm(musicWav)
        var effects = WavPcm(effectsWav)
        var dubbing = WavPcm(dubbingWav)
        require(music.sampleRate == effects.sampleRate && effects.sampleRate == dubbing.sampleRate) {
            "Sample rate audio harus sama untuk mixing."
        }

        if (enableNormalize) {
            music = music.copy(samples = AudioProcessing.normalizeRms(music.samples, targetRms = 0.10f))
            effects = effects.copy(samples = AudioProcessing.normalizeRms(effects.samples, targetRms = 0.09f))
            dubbing = dubbing.copy(samples = AudioProcessing.normalizeRms(dubbing.samples, targetRms = 0.14f))
        }

        // Apply user gains
        val musicScaled = scale(music.samples, musicGain)
        val effectsScaled = scale(effects.samples, effectsGain)
        val dubbingScaled = scale(dubbing.samples, dubbingGain)

        val duckedMusic = if (enableDucking) {
            AudioProcessing.duck(
                music = musicScaled,
                voice = dubbingScaled,
                duckGain = 0.42f,
                threshold = 0.035f
            )
        } else musicScaled

        val duckedEffects = if (enableDucking) {
            AudioProcessing.duck(
                music = effectsScaled,
                voice = dubbingScaled,
                duckGain = 0.55f,
                threshold = 0.04f
            )
        } else effectsScaled

        val count = maxOf(duckedMusic.size, duckedEffects.size, dubbingScaled.size)
        val mixed = ShortArray(count)
        for (i in 0 until count) {
            val m = if (i < duckedMusic.size) duckedMusic[i].toInt() else 0
            val e = if (i < duckedEffects.size) duckedEffects[i].toInt() else 0
            val d = if (i < dubbingScaled.size) dubbingScaled[i].toInt() else 0
            mixed[i] = (m + e + d).coerceIn(-32768, 32767).toShort()
        }

        writeWav(outputWav, AudioProcessing.limitPeak(mixed, ceiling = 0.89f), music.sampleRate)
        return outputWav
    }

    private fun scale(samples: ShortArray, gain: Float): ShortArray {
        if (gain == 1f) return samples
        return ShortArray(samples.size) {
            (samples[it] * gain).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private data class WavPcm(val sampleRate: Int, val samples: ShortArray) {
        companion object {
            operator fun invoke(file: File): WavPcm {
                val reader = MonoStereoWav.PcmWavReader(file)
                try {
                    require(reader.channels in 1..2) { "Channel WAV tidak didukung." }
                    val floats = reader.readAllFloat()
                    val samples = ShortArray(floats.size) { i ->
                        val v = floats[i]
                        val clean = if (v.isNaN() || v.isInfinite()) 0f else v.coerceIn(-1f, 1f)
                        (clean * 32767f).roundToInt().toShort()
                    }
                    return WavPcm(reader.sampleRate, samples)
                } finally {
                    reader.close()
                }
            }
        }
    }

    private fun writeWav(file: File, samples: ShortArray, rate: Int) {
        file.parentFile?.mkdirs()
        java.io.BufferedOutputStream(file.outputStream()).use { out ->
            fun leInt(v: Int) = out.write(
                byteArrayOf(
                    (v and 255).toByte(), ((v shr 8) and 255).toByte(),
                    ((v shr 16) and 255).toByte(), ((v shr 24) and 255).toByte()
                )
            )
            fun leShort(v: Int) = out.write(
                byteArrayOf((v and 255).toByte(), ((v shr 8) and 255).toByte())
            )
            val bytes = samples.size * 2
            out.write("RIFF".toByteArray()); leInt(36 + bytes)
            out.write("WAVEfmt ".toByteArray()); leInt(16); leShort(1); leShort(1)
            leInt(rate); leInt(rate * 2); leShort(2); leShort(16)
            out.write("data".toByteArray()); leInt(bytes)
            val buf = ByteArray(2)
            for (s in samples) {
                buf[0] = (s.toInt() and 255).toByte()
                buf[1] = ((s.toInt() shr 8) and 255).toByte()
                out.write(buf)
            }
        }
    }
}
