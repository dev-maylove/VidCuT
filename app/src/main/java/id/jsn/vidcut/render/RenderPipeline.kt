package id.jsn.vidcut.render

import id.jsn.vidcut.subtitle.SubtitleCue

/** Phase 8 render configuration. Actual Media3 effects are applied by the export layer. */
data class RenderJob(
    val sourcePath: String,
    val outputPath: String,
    val profile: RenderProfile = RenderProfiles.vertical,
    val subtitles: List<SubtitleCue> = emptyList(),
    val narrationPath: String? = null,
    /** Optional generated VidCut dubbing track; preferred over narration when supplied. */
    val dubbingTrackPath: String? = null
)

class RenderPipeline {
    fun validate(job: RenderJob) {
        require(job.profile.width > 0 && job.profile.height > 0)
        require(job.profile.fps in 1..120)
        require(job.sourcePath.isNotBlank() && job.outputPath.isNotBlank())
        job.narrationPath?.let { require(it.isNotBlank()) }
        job.dubbingTrackPath?.let { require(it.isNotBlank()) }
    }
}
