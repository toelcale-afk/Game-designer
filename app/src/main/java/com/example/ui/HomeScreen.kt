package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Top-level sections of the studio. */
enum class StudioSection(val title: String, val subtitle: String) {
    HOME("Home", ""),
    ASSETS("Asset Generator", "Sprites, tiles, levels, sound effects, music and voice"),
    DESIGNER("Game Designer", "Units, campaigns, menus, sounds and saves"),
    PACKFLOW("PackFlow", "Preview, icon, build and install the APK"),
    PROJECTS("Projects", "Your games: files, import, export")
}

@Composable
fun HomeScreen(
    currentProjectName: String?,
    onOpen: (StudioSection) -> Unit
) {
    val entries = listOf(
        Triple(StudioSection.ASSETS, Icons.Default.Brush, Color(0xFF6D28D9)),
        Triple(StudioSection.DESIGNER, Icons.Default.SportsEsports, Color(0xFFB45309)),
        Triple(StudioSection.PACKFLOW, Icons.Default.InstallMobile, Color(0xFF047857)),
        Triple(StudioSection.PROJECTS, Icons.Default.Folder, Color(0xFF1D4ED8))
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("home_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("PackFlow Studio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (currentProjectName != null) "Current project: $currentProjectName"
            else "No project open. Start in Projects to create one.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (currentProjectName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val twoColumns = maxWidth >= 560.dp
            if (twoColumns) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            row.forEach { (section, icon, color) ->
                                Box(Modifier.weight(1f)) { HomeEntry(section, icon, color) { onOpen(section) } }
                            }
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    entries.forEach { (section, icon, color) -> HomeEntry(section, icon, color) { onOpen(section) } }
                }
            }
        }
    }
}

@Composable
private fun HomeEntry(section: StudioSection, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick)
            .testTag("home_${section.name.lowercase()}"),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.22f))
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(56.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color.copy(alpha = 1f), modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(section.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
