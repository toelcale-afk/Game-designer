package com.example.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.FileKind
import com.example.core.ProjectLayout
import com.example.core.ProjectTemplates
import com.example.data.PackagerEngine
import com.example.data.ProjectInfo
import com.example.data.ProjectRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Built-in file browser for game projects. Each project has code/, sprites/, tiles/, levels/,
 * sounds/, music/, voice/, icon/ and builds/.
 */
@Composable
fun ProjectsScreen(
    currentProject: File?,
    onCurrentProjectChange: (File?) -> Unit,
    onSendToPackFlow: (File) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    // Folder being browsed (absolute path); null = the project list.
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }
    val browseDir = browsing?.let { File(it) }?.takeIf { it.isDirectory }
    val projectDir = browseDir?.let { d -> generateSequence(d) { it.parentFile }.firstOrNull { it.parentFile == repo.root } }

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                refresh++
            } catch (t: Throwable) {
                ErrorLog.error("$label failed", t)
                message = "$label failed: ${t.message}"
            }
        }
    }

    var exportTarget by remember { mutableStateOf<File?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val project = exportTarget
        if (uri != null && project != null) run("Export") {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { repo.exportZip(project, it) }
            }
            message = "Exported ${project.name}.zip"
        }
        exportTarget = null
    }
    val importZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run("Import project") {
            val dir = withContext(Dispatchers.IO) { repo.importZip(uri) }
            message = "Imported ${dir.name}"
        }
    }
    val importFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val dest = browseDir
        if (uris.isNotEmpty() && dest != null) run("Import files") {
            withContext(Dispatchers.IO) { uris.forEach { repo.importUri(it, dest) } }
            message = "Imported ${uris.size} file(s) into ${dest.name}/"
        }
    }

    BackHandler {
        when {
            browseDir == null -> onBack()
            browseDir == projectDir -> browsing = null
            else -> browsing = browseDir.parentFile?.absolutePath
        }
    }

    var previewFile by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var moveTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var showNewProject by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().testTag("projects_screen")) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                when {
                    browseDir == null -> onBack()
                    browseDir == projectDir -> browsing = null
                    else -> browsing = browseDir.parentFile?.absolutePath
                }
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                text = if (browseDir == null || projectDir == null) "Projects"
                else repo.readMeta(projectDir).name + browseDir.relativeTo(projectDir).invariantSeparatorsPath.let { if (it.isEmpty()) "" else " / $it" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }
        message?.let {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().clickable { message = null }) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        if (browseDir == null || projectDir == null) {
            ProjectList(
                repo = repo,
                refresh = refresh,
                current = currentProject,
                onNew = { showNewProject = true },
                onImportZip = { importZipLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                onOpen = { dir ->
                    onCurrentProjectChange(dir)
                    browsing = dir.absolutePath
                },
                onExport = { dir -> exportTarget = dir; exportLauncher.launch("${dir.name}.zip") },
                onSend = { dir -> onCurrentProjectChange(dir); onSendToPackFlow(dir) },
                onDelete = { deleteTarget = it },
                onRename = { renameTarget = it }
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { onSendToPackFlow(projectDir) }, modifier = Modifier.testTag("send_to_packflow_button")) {
                    Text("Send to PackFlow")
                }
                FilledTonalButton(onClick = { importFilesLauncher.launch(arrayOf("*/*")) }) { Text("Import here") }
                OutlinedButton(onClick = { showNewFolder = true }) { Text("New folder") }
                OutlinedButton(onClick = { exportTarget = projectDir; exportLauncher.launch("${projectDir.name}.zip") }) { Text("Export zip") }
            }
            val files by produceState(emptyList<File>(), browseDir, refresh) {
                value = withContext(Dispatchers.IO) { repo.children(browseDir) }
            }
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (files.isEmpty()) {
                    item { Text("Empty folder. Use Import here to add files from your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(files, key = { it.absolutePath }) { f ->
                    FileRow(
                        file = f,
                        onClick = { if (f.isDirectory) browsing = f.absolutePath else previewFile = f },
                        onRename = { renameTarget = f },
                        onMove = { moveTarget = f },
                        onDelete = { deleteTarget = f }
                    )
                }
            }
        }
    }

    previewFile?.let { f ->
        FilePreviewDialog(
            file = f,
            onDismiss = { previewFile = null },
            onSaveText = { text ->
                run("Save") {
                    withContext(Dispatchers.IO) { repo.writeText(f, text) }
                    message = "Saved ${f.name}"
                }
            }
        )
    }

    if (showNewProject) {
        NewProjectDialog(
            onDismiss = { showNewProject = false },
            onCreate = { name, templateId ->
                showNewProject = false
                run("Create project") {
                    val dir = withContext(Dispatchers.IO) {
                        val template = ProjectTemplates.byId(templateId)
                        repo.create(name, template.id, template.files(name))
                    }
                    onCurrentProjectChange(dir)
                    browsing = dir.absolutePath
                    message = "Created ${dir.name}"
                }
            }
        )
    }

    if (showNewFolder && browseDir != null) {
        TextPromptDialog(
            title = "New folder",
            initial = "",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                showNewFolder = false
                run("New folder") { withContext(Dispatchers.IO) { repo.newFolder(browseDir, name) } }
            }
        )
    }

    renameTarget?.let { target ->
        val isProject = target.parentFile == repo.root
        TextPromptDialog(
            title = if (isProject) "Rename project" else "Rename ${target.name}",
            initial = if (isProject) repo.readMeta(target).name else target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                run("Rename") {
                    withContext(Dispatchers.IO) {
                        if (isProject) repo.writeMeta(target, repo.readMeta(target).copy(name = name.trim(), appName = name.trim()))
                        else repo.rename(target, name)
                    }
                }
            }
        )
    }

    moveTarget?.let { target ->
        val root = projectDir
        if (root != null) {
            MoveDialog(
                projectDir = root,
                file = target,
                onDismiss = { moveTarget = null },
                onMove = { dest ->
                    moveTarget = null
                    run("Move") {
                        withContext(Dispatchers.IO) { repo.move(target, dest) }
                        message = "Moved ${target.name} to ${dest.relativeTo(root).invariantSeparatorsPath.ifEmpty { "project root" }}"
                    }
                }
            )
        }
    }

    deleteTarget?.let { target ->
        val isProject = target.parentFile == repo.root
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(if (isProject) "Delete project?" else "Delete ${target.name}?") },
            text = { Text(if (isProject || target.isDirectory) "Everything inside is deleted. This can't be undone." else "This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    run("Delete") {
                        withContext(Dispatchers.IO) { repo.delete(target) }
                        if (currentProject == target) onCurrentProjectChange(null)
                        if (isProject && browsing?.startsWith(target.absolutePath) == true) browsing = null
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProjectList(
    repo: ProjectRepository,
    refresh: Int,
    current: File?,
    onNew: () -> Unit,
    onImportZip: () -> Unit,
    onOpen: (File) -> Unit,
    onExport: (File) -> Unit,
    onSend: (File) -> Unit,
    onDelete: (File) -> Unit,
    onRename: (File) -> Unit
) {
    val projects by produceState(emptyList<ProjectInfo>(), refresh) {
        value = withContext(Dispatchers.IO) { repo.list() }
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNew, modifier = Modifier.testTag("new_project_button")) { Text("New project") }
                OutlinedButton(onClick = onImportZip) { Text("Import project zip") }
            }
        }
        if (projects.isEmpty()) {
            item { Text("No projects yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(projects, key = { it.dir.absolutePath }) { p ->
            var menu by remember { mutableStateOf(false) }
            val isCurrent = p.dir == current
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(p.dir) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.meta.name + if (isCurrent) "  (current)" else "", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${p.fileCount} files · ${PackagerEngine.formatBytes(p.sizeBytes)} · ${p.meta.packageName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Send to PackFlow") }, onClick = { menu = false; onSend(p.dir) })
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename(p.dir) })
                            DropdownMenuItem(text = { Text("Export zip") }, onClick = { menu = false; onExport(p.dir) })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete(p.dir) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: File, onClick: () -> Unit, onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val kind = ProjectLayout.kindOf(file.name)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                file.isDirectory -> Icons.Default.Folder
                kind == FileKind.IMAGE -> Icons.Default.Image
                kind == FileKind.AUDIO -> Icons.Default.AudioFile
                kind == FileKind.TEXT -> Icons.Default.Code
                else -> Icons.Default.Description
            },
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyLarge)
            if (file.isFile) {
                Text(PackagerEngine.formatBytes(file.length()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${file.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Move") }, onClick = { menu = false; onMove() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun FilePreviewDialog(file: File, onDismiss: () -> Unit, onSaveText: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(file.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                when (ProjectLayout.kindOf(file.name)) {
                    FileKind.IMAGE -> ImagePreview(file)
                    FileKind.AUDIO -> AudioPreview(file)
                    FileKind.TEXT -> TextPreview(file, onSaveText)
                    FileKind.OTHER -> Text("${PackagerEngine.formatBytes(file.length())}. No preview for this file type.")
                }
            }
        }
    }
}

@Composable
private fun ImagePreview(file: File) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }
    }
    val bmp = bitmap
    if (bmp == null) {
        Text("Loading…")
        return
    }
    Text("${bmp.width} × ${bmp.height} px", style = MaterialTheme.typography.labelSmall)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp)
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Nearest-neighbour scaling keeps pixel art crisp.
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
        )
    }
}

@Composable
fun AudioPreview(file: File) {
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    DisposableEffect(file) {
        onDispose {
            player?.release()
            player = null
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            if (playing) {
                player?.stop(); player?.release(); player = null; playing = false
            } else {
                runCatching {
                    MediaPlayer().apply {
                        setDataSource(file.path)
                        setOnCompletionListener { playing = false }
                        prepare()
                        start()
                    }
                }.onSuccess { player = it; playing = true }
                    .onFailure { ErrorLog.error("Can't play ${file.name}", it) }
            }
        }) { Text(if (playing) "Stop" else "Play") }
        Text("${file.name} · ${PackagerEngine.formatBytes(file.length())}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TextPreview(file: File, onSave: (String) -> Unit) {
    val tooBig = file.length() > 1_000_000
    var text by remember(file) { mutableStateOf<String?>(null) }
    LaunchedEffect(file) { text = withContext(Dispatchers.IO) { if (tooBig) null else file.readText() } }
    if (tooBig) {
        Text("This file is over 1 MB; too large to edit here.")
        return
    }
    val current = text ?: run { Text("Loading…"); return }
    var edited by remember(current) { mutableStateOf(current) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onSave(edited); text = edited }, enabled = edited != current) { Text("Save") }
        if (edited != current) TextButton(onClick = { edited = current }) { Text("Undo changes") }
    }
    Surface(color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxSize()) {
        BasicTextField(
            value = edited,
            onValueChange = { edited = it },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        )
    }
}

@Composable
private fun MoveDialog(projectDir: File, file: File, onDismiss: () -> Unit, onMove: (File) -> Unit) {
    val folders = remember(projectDir, file) {
        listOf(projectDir) + projectDir.walkTopDown().filter {
            it.isDirectory && it != projectDir && it != file && !it.path.startsWith(file.path + File.separator)
        }.sortedBy { it.path }.toList()
    }
    var choice by remember { mutableStateOf<File?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${file.name} to…") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(folders) { f ->
                    Row(Modifier.fillMaxWidth().clickable { choice = f }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = choice == f, onClick = { choice = f })
                        Text(f.relativeTo(projectDir).invariantSeparatorsPath.ifEmpty { "(project root)" })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = choice != null && choice != file.parentFile, onClick = { choice?.let(onMove) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(ProjectTemplates.all.first().id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Game name") }, singleLine = true)
                ProjectTemplates.all.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { template = t.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = template == t.id, onClick = { template = t.id })
                        Column {
                            Text(t.title)
                            Text(t.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, template) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TextPromptDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
