package id.jsn.vidcut.ui.vm

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.jsn.vidcut.data.ClipStore
import id.jsn.vidcut.engine.ClipGenerator
import id.jsn.vidcut.engine.ScenePoint
import id.jsn.vidcut.model.Clip
import id.jsn.vidcut.subtitle.SubtitleCue
import id.jsn.vidcut.subtitle.SrtParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectViewModel(private val context: Context) : ViewModel() {
    private val store = ClipStore(context)
    var sourceUri by mutableStateOf<Uri?>(null); private set
    var durationMs by mutableLongStateOf(0L); private set
    var startMs by mutableLongStateOf(0L); private set
    var endMs by mutableLongStateOf(0L); private set
    var scenes by mutableStateOf<List<ScenePoint>>(emptyList()); private set
    var clips by mutableStateOf(store.load()); private set
    var selectedClipId by mutableStateOf<Long?>(null); private set
    var status by mutableStateOf("Belum ada video."); private set
    var renderProgress by mutableStateOf(0f); private set
    var rendering by mutableStateOf(false); private set
    var subtitleCues by mutableStateOf<List<SubtitleCue>>(emptyList()); private set

    fun setSource(u: Uri) {
        sourceUri = u
        durationMs = 0
        startMs = 0
        endMs = 0
        clips = emptyList()
        store.save(emptyList())
        status = "Video berhasil di-import."
    }

    fun setDuration(d: Long) {
        durationMs = d
        if (endMs == 0L) endMs = d
    }

    fun setStart(v: Long) {
        startMs = v.coerceIn(0L, (endMs - 1000).coerceAtLeast(0))
    }

    fun setEnd(v: Long) {
        endMs = v.coerceIn(startMs + 1000, durationMs.coerceAtLeast(startMs + 1000))
    }

    fun reset() {
        startMs = 0
        endMs = durationMs
    }

    fun detect() {
        val u = sourceUri ?: return
        status = "Menganalisis scene..."
        viewModelScope.launch(Dispatchers.Default) {
            val found = id.jsn.vidcut.engine.SimpleSceneDetector(context).detect(u)
            withContext(Dispatchers.Main) {
                scenes = found
                status = "Scene terdeteksi: ${found.size}"
            }
        }
    }

    fun generateClips() {
        val d = durationMs
        if (d <= 0L) {
            status = "Durasi video belum tersedia."
            return
        }
        clips = ClipGenerator.generate(d, scenes)
        store.save(clips)
        selectedClipId = clips.firstOrNull()?.id
        status = "${clips.size} clip dibuat otomatis."
    }

    fun deleteClip(id: Long) {
        clips = clips.filterNot { it.id == id }
            .mapIndexed { idx, c -> c.copy(index = idx + 1, name = "Clip ${idx + 1}") }
        store.save(clips)
        if (selectedClipId == id) selectedClipId = clips.firstOrNull()?.id
    }

    fun renameClip(id: Long, name: String) {
        clips = clips.map {
            if (it.id == id) it.copy(name = name.ifBlank { "Clip ${it.index}" }) else it
        }
        store.save(clips)
    }

    fun selectClip(id: Long) {
        selectedClipId = id
    }

    fun selectedClip(): Clip? = clips.firstOrNull { it.id == selectedClipId }

    fun addSubtitleCue(start: String, end: String, text: String) {
        fun parse(s: String): Long? {
            val p = s.replace(',', ':').split(':')
            if (p.size != 4) return null
            return runCatching {
                p[0].toLong() * 3_600_000 +
                    p[1].toLong() * 60_000 +
                    p[2].toLong() * 1_000 +
                    p[3].toLong()
            }.getOrNull()
        }
        val a = parse(start)
        val b = parse(end)
        if (a == null || b == null || b <= a) {
            status = "Format waktu subtitle tidak valid."
            return
        }
        subtitleCues = (subtitleCues + SubtitleCue(a, b, text.trim())).sortedBy { it.startMs }
        status = "Caption ditambahkan."
    }

    fun loadSrt(content: String) {
        subtitleCues = SrtParser.parse(content)
        status = "SRT dimuat: ${subtitleCues.size} caption."
    }

    fun clearSubtitles() {
        subtitleCues = emptyList()
        status = "Subtitle dihapus."
    }

    fun exportAll() {
        val source = sourceUri ?: return
        if (clips.isEmpty()) {
            status = "Buat clip terlebih dahulu."
            return
        }
        rendering = true
        renderProgress = 0f
        status = "Export batch dimulai..."
        // Transformer callbacks may arrive on a background thread — marshal UI updates to Main.
        id.jsn.vidcut.engine.BatchRenderEngine(context).export(
            source = source,
            clips = clips,
            onClipProgress = { done, total ->
                viewModelScope.launch(Dispatchers.Main) {
                    renderProgress = done.toFloat() / total
                    status = "Export $done/$total"
                }
            },
            onComplete = { updated ->
                viewModelScope.launch(Dispatchers.Main) {
                    clips = updated
                    store.save(updated)
                    rendering = false
                    renderProgress = 1f
                    status = "Semua clip berhasil diexport."
                }
            },
            onError = { e ->
                viewModelScope.launch(Dispatchers.Main) {
                    rendering = false
                    status = "Export gagal: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        )
    }
}
