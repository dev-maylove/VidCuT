package id.jsn.vidcut.dubbing

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Shared DSP helpers for Phase 15: loudness, time-stretch, ducking, limiter.
 */
object AudioProcessing {

    /** RMS of mono float samples in [-1, 1]. */
    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            val v = if (s.isNaN() || s.isInfinite()) 0f else s
            sum += v * v
        }
        return sqrt(sum / samples.size).toFloat()
    }

    fun rms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / samples.size).toFloat()
    }

    /** Peak absolute value. */
    fun peak(samples: FloatArray): Float {
        var p = 0f
        for (s in samples) {
            val a = abs(if (s.isNaN() || s.isInfinite()) 0f else s)
            if (a > p) p = a
        }
        return p
    }

    /**
     * Scale samples so RMS matches [targetRms]. Clamps peak to avoid clip.
     */
    fun normalizeRms(samples: FloatArray, targetRms: Float = 0.12f, maxPeak: Float = 0.95f): FloatArray {
        val current = rms(samples)
        if (current < 1e-8f) return samples.copyOf()
        var gain = targetRms / current
        val p = peak(samples)
        if (p * gain > maxPeak && p > 1e-8f) {
            gain = maxPeak / p
        }
        return FloatArray(samples.size) { i ->
            val v = samples[i]
            when {
                v.isNaN() || v.isInfinite() -> 0f
                else -> (v * gain).coerceIn(-1f, 1f)
            }
        }
    }

    fun normalizeRms(samples: ShortArray, targetRms: Float = 0.12f, maxPeak: Float = 0.95f): ShortArray {
        val floats = FloatArray(samples.size) { samples[it] / 32768f }
        val norm = normalizeRms(floats, targetRms, maxPeak)
        return ShortArray(norm.size) { (norm[it] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort() }
    }

    /**
     * Simple soft peak limiter targeting [ceiling] (e.g. 0.89 ≈ −1 dBFS).
     */
    fun limitPeak(samples: ShortArray, ceiling: Float = 0.89f): ShortArray {
        val limit = (ceiling * 32767f).roundToInt().coerceIn(1, 32767)
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            out[i] = when {
                s > limit -> limit.toShort()
                s < -limit -> (-limit).toShort()
                else -> samples[i]
            }
        }
        return out
    }

    /**
     * Linear time-stretch (resample ratio) so [input] fills exactly [targetFrames].
     * ratio > 1 = stretch longer, < 1 = compress shorter.
     */
    fun timeStretchToFrames(input: ShortArray, targetFrames: Int): ShortArray {
        if (input.isEmpty() || targetFrames <= 0) return ShortArray(targetFrames.coerceAtLeast(0))
        if (input.size == targetFrames) return input.copyOf()
        val out = ShortArray(targetFrames)
        val last = (input.size - 1).coerceAtLeast(0)
        for (i in 0 until targetFrames) {
            val src = if (targetFrames == 1) 0.0 else i.toDouble() * last / (targetFrames - 1)
            val left = src.toInt().coerceIn(0, last)
            val right = (left + 1).coerceAtMost(last)
            val frac = src - left
            out[i] = (input[left] * (1.0 - frac) + input[right] * frac)
                .roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /**
     * Apply simple sidechain ducking: when |voice| > threshold, scale music by duckGain.
     * Attack/release in samples for smooth transitions.
     */
    fun duck(
        music: ShortArray,
        voice: ShortArray,
        duckGain: Float = 0.45f,
        threshold: Float = 0.04f,
        attackSamples: Int = 1024,
        releaseSamples: Int = 4410
    ): ShortArray {
        val n = max(music.size, voice.size)
        val out = ShortArray(n)
        var env = 0f
        val atk = 1f / attackSamples.coerceAtLeast(1)
        val rel = 1f / releaseSamples.coerceAtLeast(1)
        for (i in 0 until n) {
            val v = if (i < voice.size) abs(voice[i] / 32768f) else 0f
            if (v > env) env += (v - env) * atk else env += (v - env) * rel
            val active = env > threshold
            val g = if (active) duckGain else 1f
            val m = if (i < music.size) music[i].toInt() else 0
            out[i] = (m * g).roundToInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Insert [gapSamples] of silence between consecutive non-empty regions is handled by mixer. */
    fun appendSilence(samples: ShortArray, gapSamples: Int): ShortArray {
        if (gapSamples <= 0) return samples
        return samples + ShortArray(gapSamples)
    }

    /** Fade in/out edges of a mono buffer (samples). */
    fun applyEdgeFade(samples: ShortArray, fadeSamples: Int) {
        val f = fadeSamples.coerceAtMost(samples.size / 2).coerceAtLeast(0)
        if (f == 0) return
        for (i in 0 until f) {
            val w = i.toFloat() / f
            samples[i] = (samples[i] * w).roundToInt().toShort()
            val j = samples.size - 1 - i
            samples[j] = (samples[j] * w).roundToInt().toShort()
        }
    }
}
