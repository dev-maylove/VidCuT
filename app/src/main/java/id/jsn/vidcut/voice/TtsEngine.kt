package id.jsn.vidcut.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TtsEngine(private val context: Context) {
    fun synthesize(text: String, locale: Locale = Locale("id", "ID"), timeoutSec: Long = 60): File {
        require(text.isNotBlank()) { "Teks voice kosong." }
        val output = File(context.cacheDir, "tts-${UUID.randomUUID()}.wav")
        val ready = CountDownLatch(1)
        var initError: Int? = null
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            initError = status
            if (status == TextToSpeech.SUCCESS) tts.language = locale
            ready.countDown()
        }
        check(ready.await(10, TimeUnit.SECONDS)) { "TTS initialization timeout" }
        check(initError == TextToSpeech.SUCCESS) { "TTS tidak tersedia di perangkat." }
        val done = CountDownLatch(1)
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { done.countDown() }
            override fun onError(utteranceId: String?) { done.countDown() }
        })
        val params = android.os.Bundle()
        val result = tts.synthesizeToFile(text, params, output, utteranceId)
        check(result == TextToSpeech.SUCCESS) { "Gagal memulai TTS." }
        check(done.await(timeoutSec, TimeUnit.SECONDS)) { "TTS timeout" }
        tts.stop(); tts.shutdown()
        check(output.exists() && output.length() > 44) { "File audio TTS tidak terbentuk." }
        return output
    }
}
