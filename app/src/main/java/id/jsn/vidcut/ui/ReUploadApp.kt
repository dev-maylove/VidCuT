package id.jsn.vidcut.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import id.jsn.vidcut.model.Clip
import id.jsn.vidcut.ui.vm.ProjectViewModel
import java.util.Locale

@Composable
fun ReUploadApp(vm: ProjectViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Studio", "Editor", "Clips", "Subtitle", "Voice", "Dubbing", "Auto Dub", "Render", "About").forEachIndexed { i, label ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            Icon(
                                when (i) {
                                    0 -> Icons.Default.VideoLibrary
                                    1 -> Icons.Default.ContentCut
                                    2 -> Icons.Default.List
                                    3 -> Icons.Default.ClosedCaption
                                    4 -> Icons.Default.RecordVoiceOver
                                    5 -> Icons.Default.RecordVoiceOver
                                    6 -> Icons.Default.AutoAwesome
                                    7 -> Icons.Default.Movie
                                    else -> Icons.Default.Info
                                },
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(12.dp)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> Studio(vm)
                1 -> Editor(vm)
                2 -> ClipsScreen(vm)
                3 -> SubtitleScreen(vm)
                4 -> VoiceScreen(vm)
                5 -> DubbingScreen()
                6 -> AutoDubbingScreen(vm.sourceUri)
                7 -> RenderScreen(vm)
                8 -> AboutScreen()
            }
        }
    }
}

@Composable
private fun Studio(vm: ProjectViewModel) {
    val activity = LocalContext.current as? id.jsn.vidcut.MainActivity
    Column {
        Image(
            painter = painterResource(id = id.jsn.vidcut.R.drawable.vidcut_logo),
            contentDescription = "VidCut logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            contentScale = ContentScale.Fit
        )
        Text("VidCut", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        VideoPreview(vm.sourceUri, null)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { activity?.pickVideo() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Pilih Video")
        }
        Text(vm.status)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::detect) { Text("Detect Scene") }
            Button(onClick = vm::generateClips) { Text("Generate Clips") }
        }
    }
}

@Composable
private fun Editor(vm: ProjectViewModel) {
    Column {
        Text("Timeline Trim", style = MaterialTheme.typography.titleLarge)
        Text("Start ${fmt(vm.startMs)} — End ${fmt(vm.endMs)}")
        if (vm.durationMs > 1000) {
            RangeSlider(
                value = vm.startMs.toFloat()..vm.endMs.toFloat(),
                onValueChange = { r ->
                    vm.setStart(r.start.toLong())
                    vm.setEnd(r.endInclusive.toLong())
                },
                valueRange = 0f..vm.durationMs.toFloat(),
                steps = 0
            )
        }
        Button(onClick = vm::reset) { Text("Reset") }
        Button(onClick = vm::detect) { Text("Detect Scene") }
    }
}

@Composable
private fun ClipsScreen(vm: ProjectViewModel) {
    val clips = vm.clips
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Clip List (${clips.size})", style = MaterialTheme.typography.titleLarge)
            Button(onClick = vm::generateClips) { Text("Generate") }
        }
        Spacer(Modifier.height(8.dp))
        if (clips.isEmpty()) {
            Text("Belum ada clip. Jalankan Generate Clips.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(clips, key = { it.id }) { c ->
                    ClipCard(c, vm)
                }
            }
        }
    }
}

@Composable
private fun ClipCard(c: Clip, vm: ProjectViewModel) {
    var editing by remember(c.id) { mutableStateOf(false) }
    var name by remember(c.id) { mutableStateOf(c.name) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier = Modifier.padding(12.dp)) {
            Text("#${c.index}  ${c.name}", style = MaterialTheme.typography.titleMedium)
            Text("${fmt(c.startMs)} → ${fmt(c.endMs)} • ${fmt(c.durationMs)}")
            Text("Status: ${c.status}")
            if (editing) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row {
                    Button(onClick = {
                        vm.renameClip(c.id, name)
                        editing = false
                    }) { Text("Simpan") }
                    TextButton(onClick = { editing = false }) { Text("Batal") }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { editing = true }) { Text("Rename") }
                    TextButton(onClick = { vm.deleteClip(c.id) }) { Text("Hapus") }
                }
            }
        }
    }
}

@Composable
private fun RenderScreen(vm: ProjectViewModel) {
    Column {
        Text("Batch Export", style = MaterialTheme.typography.titleLarge)
        Text("${vm.clips.size} clip siap diproses")
        if (vm.rendering) {
            LinearProgressIndicator(
                progress = { vm.renderProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = vm::exportAll,
            enabled = !vm.rendering && vm.clips.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.rendering) "Exporting…" else "Export Semua Clip")
        }
        Spacer(Modifier.height(8.dp))
        Text(vm.status)
        Text(
            "Output: app-specific Movies/VidCut. Video dapat dipindahkan/share dari tahap berikutnya.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun VoiceScreen(vm: ProjectViewModel) {
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("TTS siap.") }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice / TTS", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Narasi") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        )
        Button(
            onClick = {
                state = "Membuat WAV…"
                val appCtx = context.applicationContext
                Thread {
                    val result = runCatching {
                        id.jsn.vidcut.voice.TtsEngine(appCtx).synthesize(text)
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        result
                            .onSuccess { state = "WAV siap: ${it.name}" }
                            .onFailure { state = "TTS gagal: ${it.message}" }
                    }
                }.start()
            },
            enabled = text.isNotBlank()
        ) {
            Text("Generate Voice WAV")
        }
        Text(state)
        Text(
            "Output TTS akan dihubungkan ke audio mixing pada render pipeline.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}


@Composable
private fun DubbingScreen() {
    val context = LocalContext.current
    var sourceText by remember { mutableStateOf("") }
    var dubbedText by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("00:00:00") }
    var end by remember { mutableStateOf("00:00:05") }
    var speaker by remember { mutableStateOf(id.jsn.vidcut.dubbing.DubbingSpeaker.MALE_NATURAL) }
    var state by remember { mutableStateOf("Mesin dubbing siap.") }
    var output by remember { mutableStateOf<java.io.File?>(null) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Dubbing Studio", style = MaterialTheme.typography.titleLarge)
        Text(
            "Buat voice track dubbing bertiming dari dialog. Audio hasil dapat diteruskan ke render pipeline.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = sourceText,
            onValueChange = { sourceText = it },
            label = { Text("Dialog asli") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = dubbedText,
            onValueChange = { dubbedText = it },
            label = { Text("Dialog dubbing") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = start, onValueChange = { start = it },
                label = { Text("Mulai") }, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = end, onValueChange = { end = it },
                label = { Text("Selesai") }, modifier = Modifier.weight(1f)
            )
        }
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Suara: ${speaker.label}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                id.jsn.vidcut.dubbing.DubbingSpeaker.values().forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = { speaker = item; expanded = false }
                    )
                }
            }
        }
        Button(
            onClick = {
                val s = parseDubbingTime(start)
                val e = parseDubbingTime(end)
                if (s == null || e == null || e <= s) {
                    state = "Format waktu tidak valid."
                    return@Button
                }
                val text = dubbedText.ifBlank { sourceText }
                if (text.isBlank()) {
                    state = "Dialog dubbing masih kosong."
                    return@Button
                }
                state = "Generate dubbing…"
                Thread {
                    val result = runCatching {
                        id.jsn.vidcut.dubbing.DubbingEngine(context.applicationContext).generate(
                            id.jsn.vidcut.dubbing.DubbingProject(
                                segments = listOf(
                                    id.jsn.vidcut.dubbing.DubbingSegment(
                                        startMs = s, endMs = e, sourceText = sourceText.ifBlank { text },
                                        dubbedText = text, speaker = speaker
                                    )
                                )
                            )
                        )
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        result.onSuccess {
                            output = it.output
                            state = "Dubbing siap: ${it.output.name}"
                        }.onFailure { state = "Dubbing gagal: ${it.message}" }
                    }
                }.start()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate Dubbing")
        }
        Text(state)
        output?.let {
            Text("Output WAV: ${it.absolutePath}", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Mesin ini membuat voice track WAV on-device dengan Android TTS dan timing dialog. Untuk dubbing otomatis dari audio video, STT/translation adapter dapat ditambahkan tanpa mengubah struktur project.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun parseDubbingTime(value: String): Long? {
    val parts = value.trim().split(":")
    return try {
        when (parts.size) {
            2 -> parts[0].toLong() * 60_000 + parts[1].toLong() * 1_000
            3 -> parts[0].toLong() * 3_600_000 + parts[1].toLong() * 60_000 + parts[2].toLong() * 1_000
            else -> null
        }
    } catch (_: NumberFormatException) { null }
}

@Composable
private fun VideoPreview(uri: Uri?, clip: Clip?) {
    if (uri == null) {
        Text("Preview video akan muncul setelah import.")
        return
    }
    val context = LocalContext.current
    val player = remember(uri, clip) {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.Builder()
                .setUri(uri)
                .apply {
                    clip?.let {
                        setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(it.startMs)
                                .setEndPositionMs(it.endMs)
                                .build()
                        )
                    }
                }
                .build()
            setMediaItem(item)
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    )
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

@Composable
private fun SubtitleScreen(vm: ProjectViewModel) {
    var text by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("00:00:00,000") }
    var end by remember { mutableStateOf("00:00:03,000") }
    Column(
        Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Subtitle Engine", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Manual caption + import SRT. Caption dapat dipakai sebagai overlay saat export.",
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(onClick = { (LocalContext.current as? id.jsn.vidcut.MainActivity)?.pickSrt() }) {
            Text("Import SRT")
        }
        OutlinedTextField(
            value = start,
            onValueChange = { start = it },
            label = { Text("Start (HH:MM:SS,mmm)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = end,
            onValueChange = { end = it },
            label = { Text("End (HH:MM:SS,mmm)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Caption") },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )
        Button(
            onClick = { vm.addSubtitleCue(start, end, text) },
            enabled = text.isNotBlank(),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Text("Tambah Caption")
        }
        vm.subtitleCues.forEachIndexed { i, cue ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(Modifier = Modifier.padding(12.dp)) {
                    Text("${i + 1}. ${cue.text}")
                    Text("${cue.startMs} ms → ${cue.endMs} ms")
                }
            }
        }
        OutlinedButton(
            onClick = { vm.clearSubtitles() },
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Text("Hapus Semua")
        }
    }
}


@Composable
private fun AboutScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(id = id.jsn.vidcut.R.drawable.vidcut_logo),
            contentDescription = "VidCut logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentScale = ContentScale.Fit
        )
        Text("VidCut", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Smart vertical video editor for creators.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(Modifier = Modifier.padding(vertical = 4.dp))

        Text("Developer", style = MaterialTheme.typography.titleMedium)
        Text("DeV_MayLoVe", style = MaterialTheme.typography.titleLarge)
        Text(
            "Independent Android engineer focused on media tooling, " +
                "clean UX, and production-ready pipelines.",
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider(Modifier = Modifier.padding(vertical = 4.dp))

        Text("Version", style = MaterialTheme.typography.titleMedium)
        Text("1.9.0-bugfix  ·  build 12", style = MaterialTheme.typography.bodyLarge)

        Text("Capabilities", style = MaterialTheme.typography.titleMedium)
        Text(
            "• Scene detection & auto clip generation\n" +
                "• Timeline trim & batch export\n" +
                "• Manual / SRT captions\n" +
                "• On-device TTS (Indonesian default)\n" +
                "• Timed dubbing voice-track engine\n" +
                "• Vertical / square / landscape profiles\n" +
                "• System Photo Picker integration",
            style = MaterialTheme.typography.bodyMedium
        )

        HorizontalDivider(Modifier = Modifier.padding(vertical = 4.dp))

        Text("License", style = MaterialTheme.typography.titleMedium)
        Text(
            "Copyright © 2026 DeV_MayLoVe\n" +
                "Licensed under the Apache License, Version 2.0.\n" +
                "See the LICENSE file distributed with this project.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Process only video and audio that you own or are authorized to use. " +
                "Editing does not remove copyright obligations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Made with care for creators.",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
