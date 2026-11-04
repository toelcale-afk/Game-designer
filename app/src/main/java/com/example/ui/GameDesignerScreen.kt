package com.example.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aistudio.packflow.xkrv.installer.ErrorLog
import com.example.core.Campaign
import com.example.core.Faction
import com.example.core.FileKind
import com.example.core.GameDesign
import com.example.core.LoseCondition
import com.example.core.Mission
import com.example.core.ProjectLayout
import com.example.core.SoundAction
import com.example.core.UnitDef
import com.example.core.WinCondition
import com.example.data.ProjectRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DESIGN_TABS = listOf("Units", "Campaign", "Menus & HUD", "Sounds", "Saves")
private val FACTION_COLORS = listOf(0xFF2E5AAC, 0xFF3E8E2F, 0xFF7B3FA0, 0xFFB91C1C, 0xFFD4A017, 0xFF6B7280, 0xFF0E7490, 0xFFC2410C)

/** Project files the designer can pick from (project-relative paths). */
private data class DesignAssets(val images: List<String>, val levels: List<String>, val sounds: List<String>)

/** Game Designer: units, campaign, menus/HUD, sound hookups and save settings, saved to code/data/. */
@Composable
fun GameDesignerScreen(project: File?, onOpenProjects: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProjectRepository(context) }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var design by remember(project) { mutableStateOf<GameDesign?>(null) }
    var saved by remember(project) { mutableStateOf<GameDesign?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(project) {
        if (project == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            val dir = File(project, ProjectLayout.DATA)
            GameDesign.fromFiles { name -> File(dir, name).takeIf { it.exists() }?.readText() }
        }
        design = loaded
        saved = loaded
    }
    val assets by produceState(DesignAssets(emptyList(), emptyList(), emptyList()), project, saved) {
        value = withContext(Dispatchers.IO) {
            if (project == null) return@withContext DesignAssets(emptyList(), emptyList(), emptyList())
            val all = project.walkTopDown().filter { it.isFile }.map { it.relativeTo(project).invariantSeparatorsPath }.toList()
            DesignAssets(
                images = all.filter { (it.startsWith("sprites/") || it.startsWith("tiles/")) && ProjectLayout.kindOf(it) == FileKind.IMAGE }.sorted(),
                levels = all.filter { it.startsWith("levels/") && it.endsWith(".json") }.sorted(),
                sounds = all.filter { (it.startsWith("sounds/") || it.startsWith("voice/") || it.startsWith("music/")) && ProjectLayout.kindOf(it) == FileKind.AUDIO }.sorted()
            )
        }
    }

    fun save() {
        val d = design ?: return
        val p = project ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val levels = d.campaign.missions.mapNotNull { m -> m.level?.let { path -> File(p, path).takeIf { it.exists() }?.let { path to it.readText() } } }.toMap()
                    val dir = File(p, ProjectLayout.DATA)
                    d.files(levels).forEach { (name, text) -> repo.saveReplacing(dir, name, text.toByteArray()) }
                }
            }.onSuccess { saved = d; message = "Saved to code/data/" }
                .onFailure { ErrorLog.error("Design save failed", it); message = "Save failed: ${it.message}" }
        }
    }

    Column(Modifier.fillMaxSize().testTag("game_designer_screen")) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Home") }
            Column(Modifier.weight(1f)) {
                Text("Game Designer" + if (design != null && design != saved) " •" else "", style = MaterialTheme.typography.titleLarge)
                Text(project?.let { repo.readMeta(it).name } ?: "No project open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { save() }, enabled = design != null && design != saved, modifier = Modifier.padding(end = 8.dp).testTag("design_save")) { Text("Save") }
        }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
            DESIGN_TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        if (project == null) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Open or create a project first. The design is saved as JSON in its code/data/ folder.")
                Button(onClick = onOpenProjects) { Text("Go to Projects") }
            }
            return@Column
        }
        val d = design
        if (d == null) {
            Text("Loading…", modifier = Modifier.padding(24.dp))
            return@Column
        }
        message?.let { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        val issues = remember(d, assets) {
            d.validate(existingFiles = (assets.images + assets.levels + assets.sounds).toSet().ifEmpty { emptySet() })
        }
        if (issues.isNotEmpty()) IssuesCard(issues)
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> UnitsTab(project, d, assets) { design = it }
                1 -> CampaignTab(d, assets) { design = it }
                2 -> MenusTab(d, assets) { design = it }
                3 -> SoundsTab(project, d, assets) { design = it }
                else -> SavesTab(d) { design = it }
            }
        }
    }
}

@Composable
private fun IssuesCard(issues: List<String>) {
    var open by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { open = !open },
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("${issues.size} thing(s) to check" + if (open) "" else " (tap to see)", style = MaterialTheme.typography.labelLarge)
            if (open) issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------- shared widgets

@Composable
fun <T> Picker(label: String, selected: T, options: List<Pair<T, String>>, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(options.firstOrNull { it.first == selected }?.second ?: "Choose…", maxLines = 1)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false }) }
            }
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Text("−") }
        Text(value.toString(), modifier = Modifier.width(44.dp).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SpriteThumb(project: File, path: String?, size: Int = 40) {
    val bmp by produceState<android.graphics.Bitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(project, it).path) } }
    }
    Box(Modifier.size(size.dp).background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = null, filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
            ?: Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- Units

@Composable
private fun UnitsTab(project: File, d: GameDesign, assets: DesignAssets, onChange: (GameDesign) -> Unit) {
    var editing by remember { mutableStateOf<UnitDef?>(null) }
    var newFaction by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Factions", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                d.factions.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(f.color.toInt())))
                        Text(" ${f.name}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = { newFaction = true }) { Text("+ Faction") }
            }
            Spacer(Modifier.size(6.dp))
            Button(onClick = {
                val faction = d.factions.firstOrNull()?.id ?: "neutral"
                editing = UnitDef(GameDesign.uniqueId("New unit", d.units.map { it.id }), "New unit", faction)
            }, modifier = Modifier.testTag("add_unit")) { Text("+ Unit") }
        }
        d.factions.forEach { f ->
            val units = d.units.filter { it.faction == f.id }
            if (units.isNotEmpty()) item { Text(f.name, style = MaterialTheme.typography.titleSmall, color = Color(f.color.toInt())) }
            items(units, key = { "u_" + it.id }) { u -> UnitCard(project, u) { editing = u } }
        }
        val orphans = d.units.filter { u -> d.factions.none { it.id == u.faction } }
        if (orphans.isNotEmpty()) {
            item { Text("No faction", style = MaterialTheme.typography.titleSmall) }
            items(orphans, key = { "o_" + it.id }) { u -> UnitCard(project, u) { editing = u } }
        }
    }

    editing?.let { u ->
        UnitDialog(
            project = project, unit = u, factions = d.factions, images = assets.images,
            isNew = d.units.none { it.id == u.id },
            onDismiss = { editing = null },
            onSave = { edited ->
                editing = null
                val exists = d.units.any { it.id == u.id }
                onChange(d.copy(units = if (exists) d.units.map { if (it.id == u.id) edited else it } else d.units + edited))
            },
            onDelete = { editing = null; onChange(d.copy(units = d.units.filterNot { it.id == u.id })) }
        )
    }
    if (newFaction) {
        var name by remember { mutableStateOf("") }
        var color by remember { mutableStateOf(FACTION_COLORS[d.factions.size % FACTION_COLORS.size]) }
        AlertDialog(
            onDismissRequest = { newFaction = false },
            title = { Text("New faction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FACTION_COLORS.forEach { c ->
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c.toInt())).clickable { color = c }) {
                                if (c == color) Text("✓", color = Color.White, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    newFaction = false
                    onChange(d.copy(factions = d.factions + Faction(GameDesign.uniqueId(name, d.factions.map { it.id }), name.trim(), color)))
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { newFaction = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UnitCard(project: File, u: UnitDef, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SpriteThumb(project, u.sprite)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(u.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "HP ${u.health} · Move ${u.movement} · Atk ${u.attack} · Range ${u.range} · Def ${u.defense} · Cost ${u.cost}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UnitDialog(
    project: File, unit: UnitDef, factions: List<Faction>, images: List<String>, isNew: Boolean,
    onDismiss: () -> Unit, onSave: (UnitDef) -> Unit, onDelete: () -> Unit
) {
    var u by remember(unit) { mutableStateOf(unit) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isNew) "New unit" else "Edit ${unit.name}", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = u.name, onValueChange = { u = u.copy(name = it.take(40)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("ID: ${u.id} (used in levels)", style = MaterialTheme.typography.labelSmall)
                Picker("Faction", u.faction, factions.map { it.id to it.name }, { u = u.copy(faction = it) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteThumb(project, u.sprite, 56)
                    Spacer(Modifier.width(10.dp))
                    Picker("Sprite", u.sprite, listOf<Pair<String?, String>>(null to "None") + images.map { it to it.removePrefix("sprites/") }, { u = u.copy(sprite = it) })
                }
                Stepper("Health", u.health, 1..999) { u = u.copy(health = it) }
                Stepper("Movement", u.movement, 0..20) { u = u.copy(movement = it) }
                Stepper("Attack", u.attack, 0..99) { u = u.copy(attack = it) }
                Stepper("Range", u.range, 1..20) { u = u.copy(range = it) }
                Stepper("Defense", u.defense, 0..99) { u = u.copy(defense = it) }
                Stepper("Cost", u.cost, 0..9999) { u = u.copy(cost = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(u) }, enabled = u.name.isNotBlank()) { Text("Done") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Campaign

@Composable
private fun CampaignTab(d: GameDesign, assets: DesignAssets, onChange: (GameDesign) -> Unit) {
    var editing by remember { mutableStateOf<Mission?>(null) }
    val ordered = d.campaign.ordered()
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                value = d.campaign.name, onValueChange = { onChange(d.copy(campaign = d.campaign.copy(name = it.take(60)))) },
                label = { Text("Campaign name") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(6.dp))
            Button(onClick = {
                val id = GameDesign.uniqueId("mission ${d.campaign.missions.size + 1}", d.campaign.missions.map { it.id })
                editing = Mission(id, "Mission ${d.campaign.missions.size + 1}", playerFaction = d.factions.firstOrNull()?.id.orEmpty(), enemyFaction = d.factions.getOrNull(1)?.id.orEmpty(), level = assets.levels.firstOrNull())
            }, modifier = Modifier.testTag("add_mission")) { Text("+ Mission") }
            Text(
                "Missions play in order: each one's \"Next\" starts after a victory. New missions are added to the end of the chain.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(ordered, key = { it.id }) { m ->
            val isStart = (d.campaign.start ?: ordered.firstOrNull()?.id) == m.id
            Card(Modifier.fillMaxWidth().clickable { editing = m }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Text((if (isStart) "▶ " else "") + m.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${m.level ?: "no level"} · Win: ${m.win.label}${if (m.winValue.isNotBlank()) " (${m.winValue})" else ""} · Next: ${m.next?.let { id -> d.campaign.missions.firstOrNull { it.id == id }?.name } ?: "end of campaign"}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (m.briefing.isNotBlank()) Text(m.briefing.take(120), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    editing?.let { m ->
        MissionDialog(
            mission = m, d = d, assets = assets,
            onDismiss = { editing = null },
            onSave = { edited, makeStart ->
                editing = null
                val exists = d.campaign.missions.any { it.id == m.id }
                var missions = if (exists) d.campaign.missions.map { if (it.id == m.id) edited else it } else {
                    // Link the current last mission of the chain to the new one.
                    val last = d.campaign.ordered().lastOrNull { it.next == null }
                    d.campaign.missions.map { if (last != null && it.id == last.id) it.copy(next = edited.id) else it } + edited
                }
                if (edited.next == edited.id) missions = missions.map { if (it.id == edited.id) it.copy(next = null) else it }
                onChange(d.copy(campaign = d.campaign.copy(missions = missions, start = if (makeStart) edited.id else d.campaign.start ?: missions.firstOrNull()?.id)))
            },
            onDelete = {
                editing = null
                val missions = d.campaign.missions.filterNot { it.id == m.id }.map { if (it.next == m.id) it.copy(next = m.next) else it }
                onChange(d.copy(campaign = Campaign(d.campaign.name, if (d.campaign.start == m.id) m.next ?: missions.firstOrNull()?.id else d.campaign.start, missions)))
            }
        )
    }
}

@Composable
private fun MissionDialog(
    mission: Mission, d: GameDesign, assets: DesignAssets,
    onDismiss: () -> Unit, onSave: (Mission, Boolean) -> Unit, onDelete: () -> Unit
) {
    var m by remember(mission) { mutableStateOf(mission) }
    var makeStart by remember { mutableStateOf(d.campaign.start == mission.id) }
    val isNew = d.campaign.missions.none { it.id == mission.id }
    val factionOptions = listOf("" to "None") + d.factions.map { it.id to it.name }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isNew) "New mission" else "Edit mission", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(value = m.name, onValueChange = { m = m.copy(name = it.take(60)) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = m.briefing, onValueChange = { m = m.copy(briefing = it.take(2000)) }, label = { Text("Briefing text") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Picker("Level", m.level, listOf<Pair<String?, String>>(null to "None") + assets.levels.map { it to it.removePrefix("levels/") }, { m = m.copy(level = it) })
                if (assets.levels.isEmpty()) Text("Make a level in Asset Generator → Levels first.", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Picker("Player faction", m.playerFaction, factionOptions, { m = m.copy(playerFaction = it) })
                    Picker("Enemy faction", m.enemyFaction, factionOptions, { m = m.copy(enemyFaction = it) })
                }
                Picker("Win condition", m.win, WinCondition.entries.map { it to it.label }, { m = m.copy(win = it) })
                if (m.win.needsValue) OutlinedTextField(
                    value = m.winValue, onValueChange = { m = m.copy(winValue = it.take(40)) }, singleLine = true,
                    label = { Text(when (m.win) { WinCondition.SURVIVE_TURNS -> "Turns"; WinCondition.REACH_TILE -> "Tile x,y"; else -> "Unit ID" }) }
                )
                Picker("Lose condition", m.lose, LoseCondition.entries.map { it to it.label }, { m = m.copy(lose = it) })
                if (m.lose.needsValue) OutlinedTextField(
                    value = m.loseValue, onValueChange = { m = m.copy(loseValue = it.take(40)) }, singleLine = true,
                    label = { Text(if (m.lose == LoseCondition.TURN_LIMIT) "Turn limit" else "Unit ID") }
                )
                Picker("Next mission (after victory)", m.next, listOf<Pair<String?, String>>(null to "End of campaign") + d.campaign.missions.filter { it.id != m.id }.map { it.id to it.name }, { m = m.copy(next = it) })
                SwitchRow("Campaign starts here", makeStart) { makeStart = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(m, makeStart) }, enabled = m.name.isNotBlank()) { Text("Done") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    if (!isNew) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Menus & HUD

@Composable
private fun MenusTab(d: GameDesign, assets: DesignAssets, onChange: (GameDesign) -> Unit) {
    val menu = d.menu
    val hud = d.hud
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Main menu", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = menu.title, onValueChange = { onChange(d.copy(menu = menu.copy(title = it.take(60)))) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = menu.subtitle, onValueChange = { onChange(d.copy(menu = menu.copy(subtitle = it.take(120)))) }, label = { Text("Subtitle") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Picker("Background image", menu.background, listOf<Pair<String?, String>>(null to "None") + assets.images.map { it to it }, { onChange(d.copy(menu = menu.copy(background = it))) })
        Text("Accent color", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf(0xFFF3C623) + FACTION_COLORS).forEach { c ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c.toInt())).clickable { onChange(d.copy(menu = menu.copy(accentColor = c))) }) {
                    if (c == menu.accentColor) Text("✓", color = Color.Black, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
        SwitchRow("Continue button (needs saves)", menu.showContinue) { onChange(d.copy(menu = menu.copy(showContinue = it))) }
        SwitchRow("Settings button", menu.showSettings) { onChange(d.copy(menu = menu.copy(showSettings = it))) }
        SwitchRow("Credits button", menu.showCredits) { onChange(d.copy(menu = menu.copy(showCredits = it))) }
        if (menu.showCredits) OutlinedTextField(value = menu.credits, onValueChange = { onChange(d.copy(menu = menu.copy(credits = it.take(2000)))) }, label = { Text("Credits text") }, minLines = 3, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.size(8.dp))
        Text("Battle HUD", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Turn counter", hud.showTurnCounter) { onChange(d.copy(hud = hud.copy(showTurnCounter = it))) }
        SwitchRow("Selected unit panel", hud.showUnitPanel) { onChange(d.copy(hud = hud.copy(showUnitPanel = it))) }
        SwitchRow("End turn button", hud.showEndTurnButton) { onChange(d.copy(hud = hud.copy(showEndTurnButton = it))) }
        SwitchRow("Mission objective", hud.showObjective) { onChange(d.copy(hud = hud.copy(showObjective = it))) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = hud.position == "top", onClick = { onChange(d.copy(hud = hud.copy(position = "top"))) }, label = { Text("HUD at top") })
            FilterChip(selected = hud.position == "bottom", onClick = { onChange(d.copy(hud = hud.copy(position = "bottom"))) }, label = { Text("HUD at bottom") })
        }
        Text("HUD size ${(hud.scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        Slider(value = hud.scale, onValueChange = { onChange(d.copy(hud = hud.copy(scale = (it * 20).toInt() / 20f))) }, valueRange = 0.75f..1.75f)
    }
}

// ---------------------------------------------------------------- Sounds

@Composable
private fun SoundsTab(project: File, d: GameDesign, assets: DesignAssets, onChange: (GameDesign) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Text("Which clip plays for each game action. Make clips in Asset Generator → Sound, Music or Voice.", style = MaterialTheme.typography.bodySmall)
        }
        items(SoundAction.entries, key = { it.id }) { action ->
            val current = d.sounds[action.id]
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(10.dp)) {
                    Picker(
                        action.label, current,
                        listOf<Pair<String?, String>>(null to "No sound") + assets.sounds.map { it to it },
                        { v -> onChange(d.copy(sounds = if (v == null) d.sounds - action.id else d.sounds + (action.id to v))) }
                    )
                    current?.let { path -> File(project, path).takeIf { it.exists() }?.let { AudioPreview(it) } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Saves

@Composable
private fun SavesTab(d: GameDesign, onChange: (GameDesign) -> Unit) {
    val s = d.save
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Save games", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Players can save", s.enabled) { onChange(d.copy(save = s.copy(enabled = it))) }
        if (s.enabled) {
            Stepper("Save slots", s.slots, 1..10) { onChange(d.copy(save = s.copy(slots = it))) }
            SwitchRow("Autosave at the start of each turn", s.autosaveEachTurn) { onChange(d.copy(save = s.copy(autosaveEachTurn = it))) }
            OutlinedTextField(
                value = s.storageKey, onValueChange = { v -> onChange(d.copy(save = s.copy(storageKey = v.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)))) },
                label = { Text("Storage key") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Saves are stored on the player's phone (WebView localStorage) under this key. Change it if two of your games could share a package name.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(onClick = { onChange(d.copy(save = com.example.core.SaveSettings())) }) { Text("Reset to defaults") }
    }
}
