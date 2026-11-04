package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.FileKind
import com.example.core.Json
import com.example.core.Level
import com.example.core.LevelTile
import com.example.core.ProjectLayout
import com.example.core.objList
import com.example.core.str
import com.example.data.ProjectRepository
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CELL_DP = 32.dp

private enum class LevelTool(val label: String) { PAINT("Paint"), FILL("Fill"), UNIT("Unit"), OBJECT("Object"), SPAWN("Spawn"), ERASE("Erase") }

val TEAM_COLORS = listOf(Color(0xFF3B82F6), Color(0xFFEF4444), Color(0xFFA3A3A3), Color(0xFF22C55E))
val TEAM_NAMES = listOf("Player", "Enemy", "Neutral", "Ally")

/** Unit IDs from the Game Designer (code/data/units.json), or a few defaults. */
fun projectUnitIds(project: File): List<String> = runCatching {
    Json.parseObject(File(project, "${ProjectLayout.DATA}/units.json").readText()).objList("units").map { it.str("id") }.filter { it.isNotBlank() }
}.getOrNull()?.ifEmpty { null } ?: listOf("marine", "ork_boy", "termagant")

/** Levels tab: paint tiles, place units/objects, set spawn points; saved as levels/<name>.json. */
@Composable
fun LevelEditorScreen(project: File) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember(project) { mutableStateOf<Pair<File, Level>?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val levelsDir = File(project, "levels")

    val current = editing
    if (current != null) {
        LevelEditor(
            project = project,
            file = current.first,
            initial = current.second,
            onSave = { level ->
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { repo.saveReplacing(levelsDir, current.first.name, level.toJson().toByteArray()) }
                    }.onSuccess { message = "Saved levels/${current.first.name}" }
                        .onFailure { ErrorLog.error("Level save failed", it); message = "Save failed: ${it.message}" }
                }
            },
            onClose = { editing = null; refresh++ },
            message = message
        )
        return
    }

    val files by produceState(emptyList<File>(), refresh) {
        value = withContext(Dispatchers.IO) { levelsDir.listFiles()?.filter { it.extension == "json" }?.sortedBy { it.name } ?: emptyList() }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Levels", style = MaterialTheme.typography.titleLarge)
            Text(
                "Tile-grid maps for your game: paint terrain, place units and objects, set spawn points. Saved as levels/<name>.json, which the PackFlow engine loads.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Button(onClick = { showNew = true }, modifier = Modifier.testTag("new_level_button")) { Text("New level") }
        }
        items(files, key = { it.path }) { f ->
            Card(Modifier.fillMaxWidth().clickable {
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { Level.fromJson(f.readText()) } }
                        .onSuccess { editing = f to it }
                        .onFailure { message = "Can't open ${f.name}: ${it.message}" }
                }
            }) {
                Text(f.nameWithoutExtension, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        message?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.error) } }
    }

    if (showNew) {
        var name by remember { mutableStateOf("Level 1") }
        var w by remember { mutableStateOf("16") }
        var h by remember { mutableStateOf("12") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New level") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = w, onValueChange = { w = it.filter(Char::isDigit).take(3) }, label = { Text("Width") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = h, onValueChange = { h = it.filter(Char::isDigit).take(3) }, label = { Text("Height") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showNew = false
                    val level = Level.blank(name.trim().ifBlank { "Level" }, (w.toIntOrNull() ?: 16).coerceIn(1, Level.MAX_SIDE), (h.toIntOrNull() ?: 12).coerceIn(1, Level.MAX_SIDE))
                    val file = ProjectRepository.uniqueChild(levelsDir.apply { mkdirs() }, ProjectLayout.slug(level.name) + ".json")
                    editing = file to level
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LevelEditor(project: File, file: File, initial: Level, onSave: (Level) -> Unit, onClose: () -> Unit, message: String?) {
    var level by remember(file) { mutableStateOf(initial) }
    var saved by remember(file) { mutableStateOf(initial) }
    var tool by remember { mutableStateOf(LevelTool.PAINT) }
    var tileIndex by remember { mutableIntStateOf(0) }
    var team by remember { mutableIntStateOf(0) }
    val unitIds = remember(project) { projectUnitIds(project) }
    var unitId by remember { mutableStateOf(unitIds.first()) }
    var objectType by remember { mutableStateOf("crate") }
    var moveMode by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var showTileImages by remember { mutableStateOf(false) }
    var showUnitMenu by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    // Tile images used by this level, loaded once.
    val images by produceState(emptyMap<String, ImageBitmap>(), level.tiles) {
        value = withContext(Dispatchers.IO) {
            level.tiles.mapNotNull { t -> t.image?.let { p -> BitmapFactory.decodeFile(File(project, p).path)?.let { p to it.asImageBitmap() } } }.toMap()
        }
    }
    val projectTileImages by produceState(emptyList<String>(), project) {
        value = withContext(Dispatchers.IO) {
            File(project, "tiles").walkTopDown().filter { it.isFile && ProjectLayout.kindOf(it.name) == FileKind.IMAGE }
                .map { it.relativeTo(project).invariantSeparatorsPath }.take(200).toList()
        }
    }

    fun apply(x: Int, y: Int, dragging: Boolean) {
        level = when (tool) {
            LevelTool.PAINT -> level.paint(x, y, tileIndex)
            LevelTool.FILL -> if (dragging) level else level.fill(x, y, tileIndex)
            LevelTool.UNIT -> if (dragging) level else level.placeUnit(unitId, team, x, y)
            LevelTool.OBJECT -> if (dragging) level else level.placeObject(objectType, x, y)
            LevelTool.SPAWN -> if (dragging) level else level.toggleSpawn(team, x, y)
            LevelTool.ERASE -> level.clearCell(x, y)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(level.name + if (level != saved) " •" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
            Button(onClick = { onSave(level); saved = level }, enabled = level != saved) { Text("Save") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp)) }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelTool.entries.forEach { t -> FilterChip(selected = tool == t && !moveMode, onClick = { tool = t; moveMode = false }, label = { Text(t.label) }) }
            FilterChip(selected = moveMode, onClick = { moveMode = !moveMode }, label = { Text("Move/zoom") })
        }
        when (tool) {
            LevelTool.PAINT, LevelTool.FILL -> Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                level.tiles.forEachIndexed { i, t ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { tileIndex = i }) {
                        Box(
                            Modifier.size(34.dp).background(Color(t.color.toInt()), RoundedCornerShape(6.dp))
                                .border(if (i == tileIndex) 3.dp else 1.dp, if (i == tileIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        )
                        Text(t.name + if (!t.walkable) " ⛔" else "", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box {
                    OutlinedButton(onClick = { showTileImages = true }, enabled = projectTileImages.isNotEmpty()) { Text("+ Tile from tiles/") }
                    DropdownMenu(expanded = showTileImages, onDismissRequest = { showTileImages = false }) {
                        projectTileImages.forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = {
                                showTileImages = false
                                val id = File(p).nameWithoutExtension
                                level = level.withTile(LevelTile(id = id, name = id, color = 0xFF808080, image = p))
                                tileIndex = level.tiles.lastIndex
                            })
                        }
                    }
                }
            }
            LevelTool.UNIT, LevelTool.SPAWN -> Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                TEAM_NAMES.forEachIndexed { i, n -> FilterChip(selected = team == i, onClick = { team = i }, label = { Text(n, color = TEAM_COLORS[i]) }) }
                if (tool == LevelTool.UNIT) Box {
                    FilledTonalButton(onClick = { showUnitMenu = true }) { Text("Unit: $unitId") }
                    DropdownMenu(expanded = showUnitMenu, onDismissRequest = { showUnitMenu = false }) {
                        unitIds.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { unitId = u; showUnitMenu = false }) }
                    }
                }
            }
            LevelTool.OBJECT -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("crate", "barricade", "objective", "rubble", "tree", "pickup").forEach { o ->
                    FilterChip(selected = objectType == o, onClick = { objectType = o }, label = { Text(o) })
                }
            }
            LevelTool.ERASE -> Text("Tap or drag to remove units, objects and spawns.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(8.dp))
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color(0xFF05070C))
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(moveMode) {
                        if (moveMode) {
                            detectTransformGestures { _, p, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.25f, 6f)
                                pan += p
                            }
                        }
                    }
                    .pointerInput(moveMode, tool, tileIndex, team, unitId, objectType, level.width, level.height) {
                        if (!moveMode) {
                            val cellAt = { o: Offset ->
                                val cell = CELL_DP.toPx() * scale
                                val x = ((o.x - pan.x) / cell).toInt()
                                val y = ((o.y - pan.y) / cell).toInt()
                                if (o.x - pan.x >= 0 && o.y - pan.y >= 0 && level.inBounds(x, y)) x to y else null
                            }
                            detectTapGestures { o -> cellAt(o)?.let { (x, y) -> apply(x, y, dragging = false) } }
                        }
                    }
                    .pointerInput(moveMode, tool, tileIndex, team, level.width, level.height) {
                        if (!moveMode && (tool == LevelTool.PAINT || tool == LevelTool.ERASE)) {
                            detectDragGestures { change, _ ->
                                val cell = CELL_DP.toPx() * scale
                                val o = change.position
                                val x = ((o.x - pan.x) / cell).toInt()
                                val y = ((o.y - pan.y) / cell).toInt()
                                if (o.x - pan.x >= 0 && o.y - pan.y >= 0 && level.inBounds(x, y)) apply(x, y, dragging = true)
                            }
                        }
                    }
            ) {
                val cell = CELL_DP.toPx() * scale
                for (y in 0 until level.height) for (x in 0 until level.width) {
                    val t = level.tileAt(x, y)
                    val tl = Offset(pan.x + x * cell, pan.y + y * cell)
                    val img = t.image?.let { images[it] }
                    if (img != null) {
                        drawImage(
                            img, dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()),
                            dstSize = IntSize(cell.roundToInt() + 1, cell.roundToInt() + 1), filterQuality = FilterQuality.None
                        )
                    } else {
                        drawRect(Color(t.color.toInt()), tl, Size(cell, cell))
                    }
                    drawRect(Color.Black.copy(alpha = 0.25f), tl, Size(cell, cell), style = Stroke(1f))
                }
                level.objects.forEach { o ->
                    val tl = Offset(pan.x + o.x * cell + cell * 0.2f, pan.y + o.y * cell + cell * 0.2f)
                    drawRect(Color(0xFFD4A017), tl, Size(cell * 0.6f, cell * 0.6f), style = Stroke(3f))
                }
                level.spawns.forEach { s ->
                    val c = Offset(pan.x + (s.x + 0.5f) * cell, pan.y + (s.y + 0.5f) * cell)
                    drawCircle(TEAM_COLORS[s.team % TEAM_COLORS.size], cell * 0.42f, c, style = Stroke(4f))
                }
                level.units.forEach { u ->
                    val c = Offset(pan.x + (u.x + 0.5f) * cell, pan.y + (u.y + 0.5f) * cell)
                    drawCircle(TEAM_COLORS[u.team % TEAM_COLORS.size], cell * 0.32f, c)
                    if (cell >= 20f) {
                        drawText(
                            measurer, u.unitId.take(1).uppercase(), topLeft = Offset(c.x - cell * 0.12f, c.y - cell * 0.22f),
                            style = TextStyle(color = Color.White, fontSize = (cell * 0.3f / density).sp)
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            var w by remember(level.width) { mutableStateOf(level.width.toString()) }
            var h by remember(level.height) { mutableStateOf(level.height.toString()) }
            OutlinedTextField(value = w, onValueChange = { w = it.filter(Char::isDigit).take(3) }, label = { Text("W") }, singleLine = true, modifier = Modifier.width(80.dp))
            OutlinedTextField(value = h, onValueChange = { h = it.filter(Char::isDigit).take(3) }, label = { Text("H") }, singleLine = true, modifier = Modifier.width(80.dp))
            OutlinedButton(onClick = { level = level.resize(w.toIntOrNull() ?: level.width, h.toIntOrNull() ?: level.height) }) { Text("Resize") }
            Text("${level.units.size} units · ${level.spawns.size} spawns", style = MaterialTheme.typography.labelSmall)
        }
    }
}
