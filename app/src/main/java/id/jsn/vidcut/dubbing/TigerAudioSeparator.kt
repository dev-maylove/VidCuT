package id.jsn.vidcut.dubbing

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * On-device TIGER-DnR separator.
 *
 * Input: mono 44.1 kHz PCM WAV.
 * Output: dialogue.wav, music.wav, effects.wav.
 *
 * The public model card specifies 531,968 samples of audio per chunk,
 * reflect padded by 1,024 samples on both sides, producing 534,016 input
 * samples. Each graph returns two buffers containing real/imaginary spectra.
 */
class TigerAudioSeparator(private val context: Context) {
    companion object {
        const val SAMPLE_RATE = 44_100
        const val WINDOW = 2_048
        const val HOP = 512
        const val CHUNK_SAMPLES = 531_968
        const val PAD = WINDOW / 2
        const val INPUT_SAMPLES = CHUNK_SAMPLES + WINDOW
        const val FRAMES = 1_040
        const val FREQ_BINS = 1_025
        const val DEFAULT_CHUNK_HOP = SAMPLE_RATE * 6 // 6 seconds; overlaps ~6 s (smoother Phase 15)
    }

    data class Result(val dialogue: File, val music: File, val effects: File)

    private data class GraphSpec(val key: String, val sourceIndex: Int, val label: String)

    private val graphs = listOf(
        GraphSpec("dialogue", 2, "Dialogue"),
        GraphSpec("music", 0, "Music"),
        GraphSpec("effects", 1, "SFX")
    )

    suspend fun separate(
        inputWav: File,
        outputDir: File,
        cancelFlag: AtomicBoolean? = null,
        onProgress: (String) -> Unit = {}
    ): Result {
        require(inputWav.exists()) { "Audio input tidak ditemukan." }
        require(TigerModelManager.isInstalled(context)) {
            "Model TIGER-DnR belum dipasang. Download model terlebih dahulu."
        }
        val reader = MonoStereoWav.PcmWavReader(inputWav)
        val samples = try {
            require(reader.sampleRate == SAMPLE_RATE && reader.bitsPerSample == 16) {
                "TIGER membutuhkan WAV PCM 16-bit 44.1 kHz."
            }
            require(reader.channels in 1..2) {
                "TIGER membutuhkan WAV mono/stereo 16-bit 44.1 kHz."
            }
            reader.readAllFloat()
        } finally {
            reader.close()
        }
        require(samples.isNotEmpty()) { "Audio input kosong." }
        outputDir.mkdirs()
        runtimeFallbackMessage = onProgress
        this.cancelFlag = cancelFlag

        val files = mutableMapOf<String, File>()
        graphs.forEachIndexed { index, graph ->
            checkCancelled()
            onProgress("${index + 1}/3 Memisahkan ${graph.label}…")
            val model = createModelWithFallback(graph)
            try {
                val output = FloatArray(samples.size)
                val weights = FloatArray(samples.size)
                processGraph(model, graph.sourceIndex, samples, output, weights, onProgress)
                val normalized = FloatArray(samples.size) { i ->
                    val w = weights[i]
                    val v = if (w > 1e-6f) output[i] / w else 0f
                    when {
                        v.isNaN() || v.isInfinite() -> 0f
                        else -> v
                    }
                }
                val file = File(outputDir, when (graph.key) {
                    "dialogue" -> "dialogue.wav"
                    "music" -> "music.wav"
                    else -> "effects.wav"
                })
                writeMonoWav(file, normalized, SAMPLE_RATE)
                files[graph.key] = file
            } finally {
                runCatching { model.close() }
            }
        }
        runtimeFallbackMessage = null
        onProgress("3/3 Separation selesai.")
        return Result(files.getValue("dialogue"), files.getValue("music"), files.getValue("effects"))
    }

    private fun createModelWithFallback(graph: GraphSpec): CompiledModel {
        val path = TigerModelManager.modelFile(context, graph.key).absolutePath
        return try {
            CompiledModel.create(path, CompiledModel.Options(Accelerator.GPU), null)
        } catch (gpuError: Throwable) {
            onRuntimeFallback("GPU ${graph.label} tidak tersedia; mencoba CPU (${gpuError.message ?: "compile error"})")
            CompiledModel.create(path, CompiledModel.Options(Accelerator.CPU), null)
        }
    }

    private var runtimeFallbackMessage: ((String) -> Unit)? = null
    private var cancelFlag: AtomicBoolean? = null

    private fun onRuntimeFallback(message: String) {
        runtimeFallbackMessage?.invoke(message)
    }

    private fun checkCancelled() {
        if (cancelFlag?.get() == true) throw InterruptedException("TIGER separation dibatalkan.")
    }

    private suspend fun processGraph(
        model: CompiledModel,
        sourceIndex: Int,
        samples: FloatArray,
        output: FloatArray,
        weights: FloatArray,
        onProgress: (String) -> Unit
    ) {
        val input = model.createInputBuffers()
        val outputs = model.createOutputBuffers()
        val padded = FloatArray(INPUT_SAMPLES)
        val spectrumSize = FREQ_BINS * FRAMES
        val window = hann(WINDOW)
        val frameRe = FloatArray(WINDOW)
        val frameIm = FloatArray(WINDOW)
        val hop = DEFAULT_CHUNK_HOP
        var start = 0
        var chunkIndex = 0
        val totalChunks = max(1, (samples.size + hop - 1) / hop)

        while (start < samples.size || (samples.isEmpty() && start == 0)) {
            checkCancelled()
            TigerRuntimePolicy.beforeInference(context)
            java.util.Arrays.fill(padded, 0f)
            val valid = min(CHUNK_SAMPLES, samples.size - start).coerceAtLeast(0)
            if (valid > 0) System.arraycopy(samples, start, padded, 0, valid)
            reflectPad(padded, valid)

            input[0].writeFloat(padded)
            model.run(input, outputs)
            val real = outputs[0].readFloat()
            val imag = outputs[1].readFloat()
            require(real.size >= spectrumSize * 3 && imag.size >= spectrumSize * 3) {
                "Output TIGER tidak sesuai kontrak model."
            }

            val specOffset = sourceIndex * spectrumSize
            val local = FloatArray(CHUNK_SAMPLES)
            inverseStft(
                real, imag, specOffset, local, window, frameRe, frameIm
            )

            val writeCount = valid
            for (i in 0 until writeCount) {
                val global = start + i
                if (global >= output.size) break
                val fade = overlapWeight(i, valid, hop)
                output[global] += local[i] * fade
                weights[global] += fade
            }

            chunkIndex++
            onProgress("${((chunkIndex * 100f) / totalChunks).roundToInt().coerceAtMost(100)}%")
            if (valid == 0 || start + valid >= samples.size) break
            start += hop
        }
    }

    /**
     * Reflect-pad [0, valid) samples into the center of [PAD, PAD+valid).
     * Layout required by the model card: [left-reflect][audio][right-reflect]
     * totaling INPUT_SAMPLES = CHUNK_SAMPLES + WINDOW.
     */
    private fun reflectPad(buffer: FloatArray, valid: Int) {
        require(buffer.size >= INPUT_SAMPLES) { "Pad buffer too small." }
        val source = if (valid > 0) buffer.copyOf(valid) else FloatArray(0)
        java.util.Arrays.fill(buffer, 0f)
        if (valid <= 0) return
        // Left reflection
        for (i in 0 until PAD) {
            val src = min(valid - 1, i + 1)
            buffer[PAD - 1 - i] = source[src]
        }
        // Center audio
        System.arraycopy(source, 0, buffer, PAD, valid)
        // Right reflection
        for (i in 0 until PAD) {
            val src = max(0, valid - 2 - i)
            val pos = PAD + valid + i
            if (pos < buffer.size) buffer[pos] = source[src]
        }
    }

    private fun overlapWeight(i: Int, valid: Int, hop: Int): Float {
        if (valid <= hop) return 1f
        val overlap = CHUNK_SAMPLES - hop
        if (overlap <= 0) return 1f
        val edge = min(overlap / 2, valid / 2)
        if (edge <= 0) return 1f
        return when {
            i < edge -> 0.5f - 0.5f * cos(PI * i / edge).toFloat()
            i >= valid - edge -> 0.5f - 0.5f * cos(PI * (valid - i) / edge).toFloat()
            else -> 1f
        }
    }

    /**
     * Inverse STFT of one source spectrum.
     * Reconstructs the full reflect-padded length (INPUT_SAMPLES), then trims
     * PAD samples from both ends so [0, CHUNK_SAMPLES) matches the unpadded chunk.
     */
    private fun inverseStft(
        real: FloatArray,
        imag: FloatArray,
        offset: Int,
        output: FloatArray,
        window: FloatArray,
        re: FloatArray,
        im: FloatArray
    ) {
        // Full reconstruction buffer (must hold (FRAMES-1)*HOP + WINDOW == INPUT_SAMPLES)
        val full = FloatArray(INPUT_SAMPLES)
        val norm = FloatArray(INPUT_SAMPLES)
        for (frame in 0 until FRAMES) {
            java.util.Arrays.fill(re, 0f)
            java.util.Arrays.fill(im, 0f)
            for (k in 0 until FREQ_BINS) {
                val idx = offset + k * FRAMES + frame
                if (idx >= real.size || idx >= imag.size) continue
                re[k] = real[idx]
                im[k] = imag[idx]
            }
            // Hermitian symmetry for real signal
            for (k in 1 until WINDOW / 2) {
                re[WINDOW - k] = re[k]
                im[WINDOW - k] = -im[k]
            }
            fft(re, im, inverse = true)
            val base = frame * HOP
            for (n in 0 until WINDOW) {
                val pos = base + n
                if (pos >= full.size) break
                val w = window[n]
                val sample = re[n]
                if (sample.isNaN() || sample.isInfinite()) continue
                full[pos] += sample * w
                norm[pos] += w * w
            }
        }
        for (i in full.indices) {
            if (norm[i] > 1e-8f) full[i] /= norm[i]
            else full[i] = 0f
            if (full[i].isNaN() || full[i].isInfinite()) full[i] = 0f
        }
        // Trim left PAD (and right is already outside CHUNK_SAMPLES)
        java.util.Arrays.fill(output, 0f)
        val copyLen = min(CHUNK_SAMPLES, full.size - PAD).coerceAtLeast(0)
        if (copyLen > 0) System.arraycopy(full, PAD, output, 0, copyLen)
    }

    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = (2.0 * PI / len) * if (inverse) 1.0 else -1.0
            val wLenRe = cos(angle).toFloat()
            val wLenIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextRe = wRe * wLenRe - wIm * wLenIm
                    wIm = wRe * wLenIm + wIm * wLenRe
                    wRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) {
            for (i in re.indices) { re[i] /= n; im[i] /= n }
        }
    }

    private fun hann(n: Int): FloatArray = FloatArray(n) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / n)).toFloat()
    }

    private fun writeMonoWav(file: File, samples: FloatArray, rate: Int) {
        val pcm = ShortArray(samples.size) { i ->
            val s = samples[i]
            val clean = when {
                s.isNaN() || s.isInfinite() -> 0f
                else -> s.coerceIn(-1f, 1f)
            }
            (clean * 32767f).roundToInt().toShort()
        }
        MonoStereoWav.WavWriter(file, rate, 1).use { writer ->
            pcm.forEach(writer::writeMono)
        }
    }
}
