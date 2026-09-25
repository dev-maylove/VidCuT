package id.jsn.vidcut.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import id.jsn.vidcut.model.Clip
import id.jsn.vidcut.ui.vm.ProjectViewModel
import java.util.Locale

/** App-level destinations (matches welcome mockup bottom nav). */
private enum class AppTab { Studio, Proyek, Templat, Profil }

/** Studio workspace tool categories (Level 1) — progressive disclosure. */
private enum class StudioTool {
    None, Edit, Audio, Teks, Overlay, Efek, Filter, Format
}

@Composable
fun ReUploadApp(vm: ProjectViewModel) {
    var appTab by remember { mutableStateOf(AppTab.Studio) }
    // null = home/welcome inside Studio; non-null = project workspace open
    var inProject by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (!inProject) {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = appTab == tab,
                            onClick = { appTab = tab },
                            icon = {
                                Icon(
                                    when (tab) {
                                        AppTab.Studio -> Icons.Default.Home
                                        AppTab.Proyek -> Icons.Default.Folder
                                        AppTab.Templat -> Icons.Default.AutoAwesome
                                        AppTab.Profil -> Icons.Default.Person
                                    },
                                    contentDescription = tab.name
                                )
                            },
                            label = { Text(tab.name) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                inProject -> StudioWorkspace(
                    vm = vm,
                    onBack = { inProject = false }
                )
                appTab == AppTab.Studio -> StudioHome(
                    vm = vm,
                    onOpenProject = { inProject = true }
                )
                appTab == AppTab.Proyek -> ProjectsScreen(
                    vm = vm,
                    onOpen = { inProject = true }
                )
                appTab == AppTab.Templat -> TemplatesScreen()
                appTab == AppTab.Profil -> AboutScreen()
            }
        }
    }
}

/* ───────────────────── Studio Home (welcome) ───────────────────── */

@Composable
private fun StudioHome(vm: ProjectViewModel, onOpenProject: () -> Unit) {
    val activity = LocalContext.current as? id.jsn.vidcut.MainActivity
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Image(
            painter = painterResource(id = id.jsn.vidcut.R.drawable.vidcut_logo),
            contentDescription = "VidCut",
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .height(72.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "VidCut",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(28.dp))
        Text(
            "Selamat Datang di VidCut",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Icon(
            Icons.Default.MovieCreation,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Mari buat video pertamamu.\nKetuk tombol di bawah untuk memulai.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                activity?.pickVideo()
                onOpenProject()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF9B5CFF), Color(0xFF3DCBFF))
                        ),
                        RoundedCornerShape(28.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("+  Proyek Baru", fontWeight = FontWeight.SemiBold)
            }
        }
        if (vm.sourceUri != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenProject,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Lanjutkan proyek terakhir")
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(vm.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
    }
}

/* ───────────────────── Studio Workspace (project open) ───────────────────── */

@Composable
private fun StudioWorkspace(vm: ProjectViewModel, onBack: () -> Unit) {
    var tool by remember { mutableStateOf(StudioTool.None) }
    var selectedClip by remember { mutableStateOf(false) }
    var showProjectSettings by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf("1080p") }
    var fps by remember { mutableStateOf("30fps") }

    Column(Modifier.fillMaxSize()) {
        // 1) TOP BAR
        StudioTopBar(
            resolution = resolution,
            fps = fps,
            onBack = onBack,
            onUndo = { /* hook later */ },
            onRedo = { /* hook later */ },
            onSettings = { showProjectSettings = true },
            onExport = { tool = StudioTool.Format },
            rendering = vm.rendering
        )

        // 2) PREVIEW
        VideoPreview(vm.sourceUri, vm.clips.firstOrNull { it.id == vm.selectedClipId })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "${fmt(vm.startMs)} / ${fmt(vm.durationMs.coerceAtLeast(1))}",
                style = MaterialTheme.typography.labelMedium
            )
            Text(vm.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // 3) TIMELINE (simplified multi-track strip)
        TimelineStrip(
            vm = vm,
            onClipSelected = { selectedClip = true },
            onAddMedia = { (LocalContext.current as? id.jsn.vidcut.MainActivity)?.pickVideo() }
        )

        HorizontalDivider()

        // 4) BOTTOM TOOLBAR — Level 1 categories OR contextual OR sub-menu
        when {
            selectedClip -> ContextualClipMenu(
                onClose = { selectedClip = false },
                onDelete = {
                    vm.selectedClipId?.let { vm.deleteClip(it) }
                    selectedClip = false
                }
            )
            tool != StudioTool.None -> ToolSubMenu(
                tool = tool,
                vm = vm,
                onClose = { tool = StudioTool.None }
            )
            else -> MainToolbar(onSelect = { tool = it }, onExport = {
                // jump to render actions inside Format/Edit via dedicated panel
                tool = StudioTool.Edit
            })
        }
    }

    if (showProjectSettings) {
        ProjectSettingsSheet(
            resolution = resolution,
            fps = fps,
            onResolution = { resolution = it },
            onFps = { fps = it },
            onDismiss = { showProjectSettings = false }
        )
    }
}

@Composable
private fun StudioTopBar(
    resolution: String,
    fps: String,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSettings: () -> Unit,
    onExport: () -> Unit,
    rendering: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
        }
        IconButton(onClick = onUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
        }
        IconButton(onClick = onRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onSettings) {
            Text("$resolution · $fps", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = onExport,
            enabled = !rendering,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues()
        ) {
            Box(
                Modifier
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF9B5CFF), Color(0xFF3DCBFF))),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(if (rendering) "…" else "Export", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TimelineStrip(
    vm: ProjectViewModel,
    onClipSelected: () -> Unit,
    onAddMedia: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 8.dp)
    ) {
        Text("Timeline", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // V1 track
            AssistChip(
                onClick = { },
                label = { Text("V1") },
                leadingIcon = { Icon(Icons.Default.Videocam, null, Modifier.size(16.dp)) }
            )
            if (vm.clips.isEmpty()) {
                FilterChip(
                    selected = false,
                    onClick = onAddMedia,
                    label = { Text("+ Media") }
                )
            } else {
                vm.clips.forEach { c ->
                    FilterChip(
                        selected = vm.selectedClipId == c.id,
                        onClick = {
                            // select clip → contextual menu
                            onClipSelected()
                        },
                        label = { Text(c.name.take(12)) }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = onAddMedia,
                    label = { Text("+") }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // A1 / T1 placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = {}, label = { Text("A1 · Audio") })
            AssistChip(onClick = {}, label = { Text("T1 · Teks") })
        }
    }
}

@Composable
private fun MainToolbar(onSelect: (StudioTool) -> Unit, onExport: () -> Unit) {
    val items = listOf(
        StudioTool.Edit to (Icons.Default.ContentCut to "Edit"),
        StudioTool.Audio to (Icons.Default.MusicNote to "Audio"),
        StudioTool.Teks to (Icons.Default.Title to "Teks"),
        StudioTool.Overlay to (Icons.Default.Layers to "Overlay"),
        StudioTool.Efek to (Icons.Default.AutoAwesome to "Efek"),
        StudioTool.Filter to (Icons.Default.Tune to "Filter"),
        StudioTool.Format to (Icons.Default.AspectRatio to "Format")
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (tool, pair) ->
            val (icon, label) = pair
            NavigationBarItem(
                selected = false,
                onClick = { onSelect(tool) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.width(72.dp)
            )
        }
    }
}

@Composable
private fun ToolSubMenu(tool: StudioTool, vm: ProjectViewModel, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (tool) {
                    StudioTool.Edit -> "Edit"
                    StudioTool.Audio -> "Audio"
                    StudioTool.Teks -> "Teks"
                    StudioTool.Overlay -> "Overlay"
                    StudioTool.Efek -> "Efek"
                    StudioTool.Filter -> "Filter & Adjust"
                    StudioTool.Format -> "Format & Export"
                    StudioTool.None -> ""
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClose) { Text("Tutup") }
        }
        Spacer(Modifier.height(8.dp))
        when (tool) {
            StudioTool.Edit -> EditSubMenu(vm)
            StudioTool.Audio -> AudioSubMenu(vm)
            StudioTool.Teks -> TeksSubMenu(vm)
            StudioTool.Overlay -> Text("Tambah overlay / PiP — segera hadir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            StudioTool.Efek -> Text("Efek visual & body effects — segera hadir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            StudioTool.Filter -> Text("Filter & color grade — segera hadir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            StudioTool.Format -> FormatSubMenu(vm)
            StudioTool.None -> {}
        }
    }
}

@Composable
private fun EditSubMenu(vm: ProjectViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::detect, modifier = Modifier.weight(1f)) {
                Text("AI Detect Scene")
            }
            Button(onClick = vm::generateClips, modifier = Modifier.weight(1f)) {
                Text("AI Generate Clips")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::reset, modifier = Modifier.weight(1f)) { Text("Reset Trim") }
            OutlinedButton(onClick = { /* split later */ }, modifier = Modifier.weight(1f)) { Text("Split") }
        }
        if (vm.scenes.isNotEmpty()) {
            Text("Scene: ${vm.scenes.size}", style = MaterialTheme.typography.labelMedium)
        }
        if (vm.clips.isNotEmpty()) {
            Text("Klip: ${vm.clips.size}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AudioSubMenu(vm: ProjectViewModel) {
    var panel by remember { mutableStateOf(0) } // 0 menu, 1 voice, 2 dubbing, 3 autodub
    when (panel) {
        0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                1 to "Voice (Voiceover)",
                2 to "Dubbing",
                3 to "Auto Dub (AI)"
            ).forEach { (id, label) ->
                OutlinedButton(
                    onClick = { panel = id },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
            }
            Text("Music · SFX · Extract — segera hadir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        1 -> {
            TextButton(onClick = { panel = 0 }) { Text("← Audio") }
            VoiceScreen(vm)
        }
        2 -> {
            TextButton(onClick = { panel = 0 }) { Text("← Audio") }
            DubbingScreen()
        }
        3 -> {
            TextButton(onClick = { panel = 0 }) { Text("← Audio") }
            AutoDubbingScreen(vm.sourceUri)
        }
    }
}

@Composable
private fun TeksSubMenu(vm: ProjectViewModel) {
    SubtitleScreen(vm)
}

@Composable
private fun FormatSubMenu(vm: ProjectViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Rasio kanvas", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("9:16", "1:1", "16:9").forEach { r ->
                FilterChip(selected = false, onClick = { }, label = { Text(r) })
            }
        }
        HorizontalDivider()
        RenderScreen(vm)
    }
}

@Composable
private fun ContextualClipMenu(onClose: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Klip", style = MaterialTheme.typography.labelLarge)
        AssistChip(onClick = { }, label = { Text("Volume") })
        AssistChip(onClick = { }, label = { Text("Crop") })
        AssistChip(onClick = { }, label = { Text("Rotate") })
        AssistChip(onClick = { }, label = { Text("Extract Audio") })
        AssistChip(onClick = onDelete, label = { Text("Hapus") })
        TextButton(onClick = onClose) { Text("Selesai") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectSettingsSheet(
    resolution: String,
    fps: String,
    onResolution: (String) -> Unit,
    onFps: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pengaturan Proyek", style = MaterialTheme.typography.titleLarge)
            Text("Resolusi")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("720p", "1080p", "4K").forEach { r ->
                    FilterChip(selected = resolution == r, onClick = { onResolution(r) }, label = { Text(r) })
                }
            }
            Text("Frame rate")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("30fps", "60fps").forEach { f ->
                    FilterChip(selected = fps == f, onClick = { onFps(f) }, label = { Text(f) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ───────────────────── Proyek / Templat ───────────────────── */

@Composable
private fun ProjectsScreen(vm: ProjectViewModel, onOpen: () -> Unit) {
    Column(Modifier.padding(16.dp)) {
        Text("Proyek", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (vm.sourceUri == null && vm.clips.isEmpty()) {
            Text("Belum ada proyek. Buat dari tab Studio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Proyek terakhir", style = MaterialTheme.typography.titleMedium)
                    Text(vm.status, style = MaterialTheme.typography.bodySmall)
                    Text("${vm.clips.size} klip · ${fmt(vm.durationMs)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TemplatesScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Templat", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Template teks & proyek siap pakai akan muncul di sini.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ───────────────────── Existing feature screens (embedded) ───────────────────── */

@Composable
private fun ClipCard(c: Clip, vm: ProjectViewModel) {
    var editing by remember(c.id) { mutableStateOf(false) }
    var name by remember(c.id) { mutableStateOf(c.name) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Export / Render", style = MaterialTheme.typography.titleMedium)
        Text("${vm.clips.size} klip siap diproses", style = MaterialTheme.typography.bodySmall)
        if (vm.rendering) {
            LinearProgressIndicator(progress = { vm.renderProgress }, modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = vm::exportAll,
            enabled = !vm.rendering && vm.clips.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (vm.rendering) "Merender…" else "Export semua klip")
        }
        Text(vm.status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VoiceScreen(vm: ProjectViewModel) {
    var text by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("TTS siap.") }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voiceover (TTS)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Teks untuk dibacakan") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
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
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generate Voice WAV") }
        Text(state, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dubbing", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = sourceText, onValueChange = { sourceText = it }, label = { Text("Teks sumber") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = dubbedText, onValueChange = { dubbedText = it }, label = { Text("Teks dubbing") }, modifier = Modifier.fillMaxWidth())
        Row {
            OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Mulai") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Selesai") }, modifier = Modifier.weight(1f))
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = speaker.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Speaker") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                id.jsn.vidcut.dubbing.DubbingSpeaker.entries.forEach {
                    DropdownMenuItem(text = { Text(it.name) }, onClick = { speaker = it; expanded = false })
                }
            }
        }
        Button(
            onClick = {
                state = "Dubbing…"
                // keep lightweight — engine call remains in VM/engine layer when wired
                state = "Siapkan segmen di Auto Dub untuk pipeline penuh."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Proses dubbing") }
        Text(state, style = MaterialTheme.typography.bodySmall)
        output?.let { Text("Output: ${it.name}", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun VideoPreview(uri: Uri?, clip: Clip?) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(uri, clip) {
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
            clip?.let {
                player.seekTo(it.startMs)
            }
        }
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
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
    val activity = LocalContext.current as? id.jsn.vidcut.MainActivity
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Subtitle / Caption", style = MaterialTheme.typography.titleMedium)
        Text(
            "Manual caption + import SRT. Dipakai sebagai overlay saat export.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { activity?.pickSrt() }, modifier = Modifier.fillMaxWidth()) {
            Text("Import SRT")
        }
        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Caption") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )
        Button(
            onClick = { vm.addSubtitleCue(start, end, text) },
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Tambah Caption") }
        vm.subtitleCues.forEachIndexed { i, cue ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${i + 1}. ${cue.text}")
                    Text("${cue.startMs} ms → ${cue.endMs} ms")
                }
            }
        }
        if (vm.subtitleCues.isNotEmpty()) {
            OutlinedButton(onClick = { vm.clearSubtitles() }, modifier = Modifier.fillMaxWidth()) {
                Text("Hapus Semua")
            }
        }
    }
}

@Composable
private fun AboutScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(id = id.jsn.vidcut.R.drawable.vidcut_logo),
            contentDescription = "VidCut logo",
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentScale = ContentScale.Fit
        )
        Text("VidCut", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Smart vertical video editor for creators.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(
            Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text("Developer", style = MaterialTheme.typography.titleMedium)
        Text("DeV_MayLoVe", style = MaterialTheme.typography.titleLarge)
        Text(
            "Independent Android engineer focused on media tooling, clean UX, and production-ready pipelines.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(
            Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text("Version", style = MaterialTheme.typography.titleMedium)
        Text("1.12.0 · Studio IA", style = MaterialTheme.typography.bodyLarge)
        Text(
            "• Progressive disclosure workspace\n" +
                "• Scene detection & auto clips\n" +
                "• Captions / SRT\n" +
                "• TTS · Dubbing · Auto Dub\n" +
                "• Multi-ratio export profiles",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(
            Modifier
                .padding(vertical = 4.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text("License", style = MaterialTheme.typography.titleMedium)
        Text(
            "Copyright © 2026 DeV_MayLoVe\nLicensed under the Apache License, Version 2.0.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
