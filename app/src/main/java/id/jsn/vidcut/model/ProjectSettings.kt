package id.jsn.vidcut.model

data class ProjectSettings(
    val projectName: String = "Untitled Project",
    val targetWidth: Int = 1080,
    val targetHeight: Int = 1920,
    val fps: Int = 30,
    val maxClips: Int = 5,
    val minClipSeconds: Int = 8,
    val maxClipSeconds: Int = 45,
    val watermark: String = ""
)
