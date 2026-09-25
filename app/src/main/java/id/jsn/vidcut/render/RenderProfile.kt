package id.jsn.vidcut.render

data class RenderProfile(
    val width: Int = 1080,
    val height: Int = 1920,
    val fps: Int = 30,
    val crf: Int = 21,
    val subtitleEnabled: Boolean = true,
    val watermarkText: String = "",
    val sourceVolume: Float = 1f,
    val voiceVolume: Float = 1f
)

object RenderProfiles {
    val vertical = RenderProfile()
    val square = RenderProfile(1080, 1080)
    val landscape = RenderProfile(1920, 1080)
}
