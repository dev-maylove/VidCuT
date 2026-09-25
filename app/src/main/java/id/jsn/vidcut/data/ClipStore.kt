package id.jsn.vidcut.data

import android.content.Context
import id.jsn.vidcut.model.Clip
import org.json.JSONArray
import org.json.JSONObject

class ClipStore(context: Context) {
    private val prefs = context.getSharedPreferences("clip_store", Context.MODE_PRIVATE)

    fun load(): List<Clip> = runCatching {
        val raw = prefs.getString("clips", "[]") ?: "[]"
        val a = JSONArray(raw)
        buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val outputPathRaw = o.optString("outputPath", "")
                add(
                    Clip(
                        id = o.getLong("id"),
                        index = o.getInt("index"),
                        name = o.getString("name"),
                        startMs = o.getLong("startMs"),
                        endMs = o.getLong("endMs"),
                        status = o.optString("status", "READY"),
                        outputPath = outputPathRaw.takeIf { it.isNotBlank() },
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(clips: List<Clip>) {
        val a = JSONArray()
        clips.forEach { c ->
            a.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("index", c.index)
                    put("name", c.name)
                    put("startMs", c.startMs)
                    put("endMs", c.endMs)
                    put("status", c.status)
                    put("outputPath", c.outputPath ?: "")
                    put("createdAt", c.createdAt)
                }
            )
        }
        prefs.edit().putString("clips", a.toString()).apply()
    }
}
