package id.jsn.vidcut

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import id.jsn.vidcut.ui.ReUploadApp
import id.jsn.vidcut.ui.theme.ReUploadTheme
import id.jsn.vidcut.ui.vm.ProjectViewModel

class MainActivity : ComponentActivity() {
    private lateinit var vm: ProjectViewModel

    /** System Photo Picker (VideoOnly) with automatic document-picker fallback. */
    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Photo Picker URIs often do not support persistable grants.
        }
        vm.setSource(uri)
        // Reliable duration probe (independent of ExoPlayer).
        probeDuration(uri)
    }

    private val subtitlePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                vm.loadSrt(it.readText())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ProjectViewModel(applicationContext)
        setContent {
            ReUploadTheme {
                ReUploadApp(vm)
            }
        }
    }

    fun pickVideo() {
        videoPicker.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.VideoOnly
            )
        )
    }

    fun pickSrt() {
        subtitlePicker.launch(
            arrayOf(
                "text/*",
                "application/x-subrip",
                "application/octet-stream"
            )
        )
    }

    private fun probeDuration(uri: Uri) {
        Thread {
            var duration = 0L
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                // ignore; duration stays 0
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            if (duration > 0L) {
                runOnUiThread { vm.setDuration(duration) }
            }
        }.start()
    }
}
