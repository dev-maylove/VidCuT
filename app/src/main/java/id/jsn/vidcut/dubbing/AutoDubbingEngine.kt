package id.jsn.vidcut.dubbing

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Full auto-dubbing pipeline (Phase 14B–15C).
 *
 * Supports cooperative cancellation via [cancelFlag] and reports ETA-style progress.
 */
class AutoDubbingEngine(private val context: Context) {

    suspend fun run(
        source: Uri,
        options: AutoDubbingOptions,
        modelLanguageTag: String = options.sourceLanguage,
        outputName: String = "vidcut-auto-dubbed.mp4",
        cancelFlag: AtomicBoolean = AtomicBoolean(false),
        onProgress: (String) -> Unit = {}
    ): AutoDubbingResult = withContext(Dispatchers.IO) {
        fun check() {
            if (cancelFlag.get()) throw InterruptedException("Auto Dubbing dibatalkan.")
            coroutineContext.ensureActive()
        }

        val work = File(context.cacheDir, "auto-dubbing").apply { mkdirs() }
        val decoder = AudioDecoder(context)
        val startedAt = System.currentTimeMillis()

        fun eta(step: Int, total: Int = 10): String {
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
            if (step <= 1 || elapsed < 1.0) return ""
            val perStep = elapsed / (step - 1)
            val remain = ((total - step + 1) * perStep).toInt().coerceAtLeast(0)
            return " · sisa ±${remain}s"
        }

        check()
        onProgress("1/10 Mengekstrak audio untuk STT…")
        val sourceAudio = decoder.extractTo16kMonoWav(source, File(work, "source-16k.wav"))

        check()
        onProgress("2/10 Mengekstrak audio 44.1 kHz…${eta(2)}")
        val originalMixAudio = decoder.extractToPcmWav(
            source, File(work, "original-44k.wav"), 44_100
        )

        check()
        onProgress("3/10 AI Dialogue / Music / SFX separation…${eta(3)}")
        val stems = run {
            val cached = StemCache.lookup(context, originalMixAudio)
            if (cached != null) {
                onProgress("3/10 TIGER-DnR cache hit — lewati separation")
                cached
            } else {
                val tiger = TigerAudioSeparator(context)
                val stemDir = File(work, "tiger-stems").apply { mkdirs() }
                val result = tiger.separate(
                    inputWav = originalMixAudio,
                    outputDir = stemDir,
                    cancelFlag = cancelFlag
                ) { p ->
                    check()
                    onProgress("3/10 TIGER-DnR $p${eta(3)}")
                }
                StemCache.store(context, originalMixAudio, result)
            }
        }

        check()
        onProgress("4/10 Speech-to-Text…${eta(4)}")
        val voskDir = VoskSttEngine.modelDir(context, modelLanguageTag)
        if (!voskDir.isDirectory) {
            throw IllegalStateException(
                "Model Vosk untuk '$modelLanguageTag' belum dipasang di ${voskDir.absolutePath}. " +
                    "Pasang model Vosk offline terlebih dahulu (lihat dokumentasi Phase 15)."
            )
        }
        val stt = VoskSttEngine(context)
        val detected = try {
            stt.transcribe(sourceAudio, modelLanguageTag)
        } catch (t: Throwable) {
            throw IllegalStateException(
                "STT gagal: ${t.message}. Pastikan model Vosk untuk '$modelLanguageTag' valid.",
                t
            )
        }
        require(detected.isNotEmpty()) {
            "STT tidak menemukan dialog. Coba bahasa sumber lain atau pastikan video memiliki percakapan."
        }

        check()
        onProgress("5/10 Menerjemahkan ${detected.size} segmen…${eta(5)}")
        val translator = MlKitTranslationEngine()
        val translated = detected.mapIndexed { idx, seg ->
            check()
            if (idx % 3 == 0) onProgress("5/10 Translate ${idx + 1}/${detected.size}${eta(5)}")
            seg.copy(
                translatedText = translator.translate(
                    seg.sourceText,
                    options.sourceLanguage,
                    options.targetLanguage
                )
            )
        }

        check()
        onProgress("6/10 Membuat suara multi-speaker (TTS + time-stretch)…${eta(6)}")
        val dubbingProject = DubbingProject(
            segments = translated.map { it.toDubbingSegment(options) },
            originalVolume = 0f,
            dubbingVolume = options.dubbingVolume
        )
        val dubbing = DubbingEngine(context).generate(dubbingProject, cancelFlag = cancelFlag)

        check()
        onProgress("7/10 Mixing Music + SFX + dubbing (duck + limit)…${eta(7)}")
        val mixedWav = AudioMixer().mixSeparated(
            musicWav = stems.music,
            effectsWav = stems.effects,
            dubbingWav = dubbing.output,
            outputWav = File(work, "mixed-audio.wav"),
            musicGain = options.musicVolume,
            effectsGain = options.effectsVolume,
            dubbingGain = options.dubbingVolume,
            enableDucking = true,
            enableNormalize = true
        )

        check()
        onProgress("8/10 Encoding mixed AAC…${eta(8)}")
        val aac = PcmAacEncoder().encodeWavToM4a(
            mixedWav,
            File(work, "mixed-audio.m4a")
        )

        check()
        onProgress("9/10 Render video final…${eta(9)}")
        val outDir = context.getExternalFilesDir("Movies") ?: context.filesDir
        val final = AutoDubbingRenderEngine(context).muxVideoWithDubbing(
            source,
            aac,
            File(outDir, outputName)
        )

        onProgress("10/10 Selesai — dialogue diganti, Music+SFX + ducking + limiter.${eta(10)}")
        AutoDubbingResult(
            sourceAudio = sourceAudio,
            segments = translated,
            dubbingWav = dubbing.output,
            mixedAac = aac,
            finalVideo = final,
            stemDialogue = stems.dialogue,
            stemMusic = stems.music,
            stemEffects = stems.effects
        )
    }
}
