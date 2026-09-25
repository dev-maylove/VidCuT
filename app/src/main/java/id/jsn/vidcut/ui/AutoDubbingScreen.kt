package id.jsn.vidcut.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import id.jsn.vidcut.dubbing.AutoDubbingEngine
import id.jsn.vidcut.dubbing.AutoDubbingOptions
import id.jsn.vidcut.dubbing.DubbingSpeaker
import id.jsn.vidcut.dubbing.TigerModelDownloadWorker
import id.jsn.vidcut.dubbing.TigerModelManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun AutoDubbingScreen(sourceUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceLanguage by remember { mutableStateOf("en") }
    var targetLanguage by remember { mutableStateOf("id") }
    var status by remember { mutableStateOf("Auto Dubbing siap (Phase 15).") }
    var running by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(TigerModelManager.isInstalled(context)) }
    var modelProgress by remember { mutableFloatStateOf(0f) }
    var modelWorkId by remember { mutableStateOf<java.util.UUID?>(null) }
    var lastResultPath by remember { mutableStateOf<String?>(null) }
    var stemInfo by remember { mutableStateOf<String?>(null) }
    var speakerA by remember { mutableStateOf(DubbingSpeaker.FEMALE_NATURAL) }
    var speakerB by remember { mutableStateOf(DubbingSpeaker.MALE_NATURAL) }
    var musicVol by remember { mutableFloatStateOf(0.75f) }
    var effectsVol by remember { mutableFloatStateOf(0.90f) }
    var dubVol by remember { mutableFloatStateOf(1.0f) }

    val cancelFlag = remember { AtomicBoolean(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(modelWorkId) {
        val id = modelWorkId ?: return@LaunchedEffect
        WorkManager.getInstance(context).getWorkInfoByIdFlow(id).collect { info ->
            if (info == null) return@collect
            val percent = info.progress.getInt("percent", 0)
            modelProgress = percent / 100f
            status = when (info.state) {
                WorkInfo.State.RUNNING ->
                    "Download ${info.progress.getString("model") ?: "model"}: $percent%"
                WorkInfo.State.SUCCEEDED -> {
                    modelReady = TigerModelManager.isInstalled(context)
                    "TIGER-DnR siap. ±48 MB terpasang."
                }
                WorkInfo.State.FAILED ->
                    "Download model gagal: ${info.outputData.getString("error") ?: "unknown error"}"
                WorkInfo.State.CANCELLED -> "Download model dibatalkan."
                else -> "Menunggu download model…"
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Auto Dubbing", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Video → TIGER-DnR → stems → STT → translate → TTS (time-stretch) → mix (duck+limit) → AAC → video.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = sourceLanguage,
            onValueChange = { sourceLanguage = it },
            label = { Text("Bahasa sumber (STT / Vosk)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = targetLanguage,
            onValueChange = { targetLanguage = it },
            label = { Text("Bahasa target (TTS / translate)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("Speaker A (genap)", style = MaterialTheme.typography.labelMedium)
        SpeakerChips(speakerA) { speakerA = it }
        Text("Speaker B (ganjil)", style = MaterialTheme.typography.labelMedium)
        SpeakerChips(speakerB) { speakerB = it }

        Text("Music volume: ${"%.0f".format(musicVol * 100)}%")
        Slider(value = musicVol, onValueChange = { musicVol = it }, valueRange = 0f..1.2f)
        Text("SFX volume: ${"%.0f".format(effectsVol * 100)}%")
        Slider(value = effectsVol, onValueChange = { effectsVol = it }, valueRange = 0f..1.2f)
        Text("Dubbing volume: ${"%.0f".format(dubVol * 100)}%")
        Slider(value = dubVol, onValueChange = { dubVol = it }, valueRange = 0f..1.5f)

        // Model download
        if (!modelReady) {
            Button(
                onClick = {
                    val req = OneTimeWorkRequestBuilder<TigerModelDownloadWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "tiger-dnr-download",
                        ExistingWorkPolicy.KEEP,
                        req
                    )
                    modelWorkId = req.id
                    status = "Memulai download model TIGER-DnR…"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download model TIGER-DnR (±48 MB)")
            }
            if (modelProgress > 0f && modelProgress < 1f) {
                LinearProgressIndicator(progress = { modelProgress }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Text(
                "✓ Model TIGER-DnR terpasang",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (sourceUri == null) {
                        status = "Pilih video terlebih dahulu di Studio."
                        return@Button
                    }
                    if (!modelReady) {
                        status = "Install model TIGER-DnR terlebih dahulu."
                        return@Button
                    }
                    cancelFlag.set(false)
                    running = true
                    status = "Memulai Auto Dubbing…"
                    lastResultPath = null
                    stemInfo = null
                    job = scope.launch {
                        runCatching {
                            AutoDubbingEngine(context.applicationContext).run(
                                source = sourceUri,
                                options = AutoDubbingOptions(
                                    sourceLanguage = sourceLanguage.trim(),
                                    targetLanguage = targetLanguage.trim(),
                                    musicVolume = musicVol,
                                    effectsVolume = effectsVol,
                                    dubbingVolume = dubVol,
                                    speakerA = speakerA,
                                    speakerB = speakerB
                                ),
                                modelLanguageTag = sourceLanguage.trim(),
                                cancelFlag = cancelFlag
                            ) { message -> status = message }
                        }.onSuccess { result ->
                            lastResultPath = result.finalVideo?.absolutePath
                            stemInfo = listOfNotNull(
                                result.stemDialogue?.let { "dialogue: ${it.name}" },
                                result.stemMusic?.let { "music: ${it.name}" },
                                result.stemEffects?.let { "effects: ${it.name}" }
                            ).joinToString(" · ")
                            status = "Selesai: ${result.finalVideo?.absolutePath}"
                        }.onFailure {
                            status = if (it is InterruptedException || it.message?.contains("dibatalkan") == true) {
                                "Dibatalkan."
                            } else {
                                "Auto Dubbing gagal: ${it.message}"
                            }
                        }
                        running = false
                        job = null
                    }
                },
                enabled = sourceUri != null && !running && modelReady,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (running) "Memproses…" else "Generate")
            }

            OutlinedButton(
                onClick = {
                    cancelFlag.set(true)
                    job?.cancel()
                    status = "Membatalkan…"
                },
                enabled = running,
                modifier = Modifier.weight(0.6f)
            ) {
                Text("Batal")
            }
        }

        if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
        stemInfo?.let {
            Text("Stems: $it", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lastResultPath?.let {
            Text("Output: $it", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }

        Text(
            "Phase 15: time-stretch TTS, ducking Music/SFX di bawah voice, peak limiter −1 dBFS, " +
                "normalize RMS stems, stem cache, cancel job, ETA progress, speaker preference.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SpeakerChips(selected: DubbingSpeaker, onSelect: (DubbingSpeaker) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DubbingSpeaker.entries.forEach { sp ->
            FilterChip(
                selected = selected == sp,
                onClick = { onSelect(sp) },
                label = { Text(sp.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
