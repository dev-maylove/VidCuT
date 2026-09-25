package id.jsn.vidcut.dubbing

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Phase 15C: cache TIGER stems by SHA-256 of source WAV so identical audio
 * does not re-run expensive separation.
 */
object StemCache {
    private fun root(context: Context): File = File(context.cacheDir, "tiger-stem-cache").also { it.mkdirs() }

    fun keyFor(wav: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(wav).use { input ->
            val buf = ByteArray(1024 * 256)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        // Include size to avoid rare collisions on truncated files
        digest.update(wav.length().toString().toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }.take(32)
    }

    fun lookup(context: Context, sourceWav: File): TigerAudioSeparator.Result? {
        val key = keyFor(sourceWav)
        val dir = File(root(context), key)
        val dialogue = File(dir, "dialogue.wav")
        val music = File(dir, "music.wav")
        val effects = File(dir, "effects.wav")
        if (dialogue.exists() && music.exists() && effects.exists() &&
            dialogue.length() > 44 && music.length() > 44 && effects.length() > 44
        ) {
            return TigerAudioSeparator.Result(dialogue, music, effects)
        }
        return null
    }

    fun store(context: Context, sourceWav: File, result: TigerAudioSeparator.Result): TigerAudioSeparator.Result {
        val key = keyFor(sourceWav)
        val dir = File(root(context), key).also { it.mkdirs() }
        val dialogue = File(dir, "dialogue.wav")
        val music = File(dir, "music.wav")
        val effects = File(dir, "effects.wav")
        result.dialogue.copyTo(dialogue, overwrite = true)
        result.music.copyTo(music, overwrite = true)
        result.effects.copyTo(effects, overwrite = true)
        return TigerAudioSeparator.Result(dialogue, music, effects)
    }

    fun clear(context: Context) {
        root(context).deleteRecursively()
    }
}
