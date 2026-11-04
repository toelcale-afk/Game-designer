package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.IconShape
import com.example.core.IconSpec
import com.example.core.IconTransform
import com.example.core.PxRect
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val BACKGROUND_PRESETS = listOf(
    0xFF0B1120, 0xFFFFFFFF, 0xFF000000, 0xFF1E3A8A, 0xFF065F46, 0xFF7F1D1D, 0xFFF59E0B, 0xFF6D28D9
)

fun IconShape.toComposeShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> RoundedCornerShape(32)
    IconShape.ROUNDED -> RoundedCornerShape(18)
    IconShape.SQUARE -> RectangleShape
    IconShape.TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 10, bottomStartPercent = 50)
}

/** Pipeline step 2: icon maker. */
@Composable
fun IconStepScreen(
    viewModel: PackFlowViewModel,
    onNext: () -> Unit,
    projectImages: List<File> = emptyList()
) {
    val context = LocalContext.current
    val ui by viewModel.iconMaker.state.collectAsStateWithLifecycle()
    val controller = viewModel.iconMaker

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) controller.pickSource(context, uri)
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) controller.pickBackgroundImage(context, uri)
    }

    val sourceImage: ImageBitmap? = remember(ui.source) { ui.source?.asImageBitmap() }
    val bgImage: ImageBitmap? = remember(ui.backgroundImage) { ui.backgroundImage?.asImageBitmap() }

    // Gestures update this local copy instantly; it's saved to the design shortly after.
    var transform by remember(ui.design.sourcePath) { mutableStateOf(ui.design.transform) }
    LaunchedEffect(transform) {
        if (transform != ui.design.transform) {
            delay(350)
            controller.setTransform(transform)
        }
    }
    val bgColor = Color(ui.design.backgroundColor.toInt())
    var hex by remember(ui.design.backgroundColor) {
        mutableStateOf(String.format("#%06X", ui.design.backgroundColor and 0xFFFFFF))
    }
    var showProjectMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("icon_step_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("2 Launcher icon", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pick an image, drag to move it and pinch (or use the slider) to zoom. PackFlow makes every Android icon size plus an adaptive icon.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                pickSource.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Image from phone") }
            Box {
                OutlinedButton(
                    onClick = { showProjectMenu = true },
                    enabled = projectImages.isNotEmpty()
                ) { Text(if (projectImages.isEmpty()) "No project images" else "From project") }
                DropdownMenu(expanded = showProjectMenu, onDismissRequest = { showProjectMenu = false }) {
                    projectImages.forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.parentFile?.name?.let { "$it/${f.name}" } ?: f.name) },
                            onClick = {
                                showProjectMenu = false
                                controller.useProjectImage(f)
                            }
                        )
                    }
                }
            }
        }

        // Big interactive preview in the selected shape.
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val shape = ui.design.previewShape.toComposeShape()
            Canvas(
                modifier = Modifier
                    .size(220.dp)
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .pointerInput(ui.design.sourcePath) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            transform = IconTransform(
                                scale = transform.scale * zoom,
                                offsetX = transform.offsetX + pan.x / size.width,
                                offsetY = transform.offsetY + pan.y / size.height
                            ).clamped()
                        }
                    }
                    .testTag("icon_preview_canvas")
            ) {
                drawIcon(bgColor, bgImage, sourceImage, transform)
            }
        }

        // Small previews of every launcher shape.
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconShape.entries.forEach { s ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { controller.setPreviewShape(s) }
                ) {
                    val selected = s == ui.design.previewShape
                    Canvas(
                        Modifier
                            .size(52.dp)
                            .clip(s.toComposeShape())
                            .border(
                                if (selected) 2.dp else 0.dp,
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                s.toComposeShape()
                            )
                    ) { drawIcon(bgColor, bgImage, sourceImage, transform) }
                    Text(s.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Text("Zoom", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = transform.scale,
            onValueChange = { transform = transform.copy(scale = it).clamped() },
            valueRange = IconTransform.MIN_SCALE..IconTransform.MAX_SCALE
        )
        TextButton(onClick = { transform = IconTransform() }) { Text("Reset position") }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Background", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BACKGROUND_PRESETS.forEach { argb ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(argb.toInt()))
                                .border(
                                    2.dp,
                                    if (argb == ui.design.backgroundColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                                .clickable { controller.setBackgroundColor(argb) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { v ->
                            hex = v
                            IconSpec.parseColor(v)?.let { controller.setBackgroundColor(it) }
                        },
                        label = { Text("Hex color") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Background image") }
                    if (ui.backgroundImage != null) {
                        TextButton(onClick = { controller.clearBackgroundImage() }) { Text("Remove") }
                    }
                }
            }
        }

        if (ui.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(ui.message ?: "Working…")
            }
        } else {
            ui.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        Button(
            onClick = { controller.setTransform(transform); controller.generate() },
            enabled = ui.source != null && !ui.busy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("generate_icons_button")
        ) { Text(if (ui.hasIcon) "Icons generated ✓ (regenerate)" else "Generate all icon sizes") }

        Button(
            onClick = {
                if (ui.source != null && !ui.hasIcon) {
                    controller.setTransform(transform)
                    controller.generate(onDone = onNext)
                } else {
                    onNext()
                }
            },
            enabled = !ui.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("icon_next_button")
        ) {
            Text(
                when {
                    ui.source == null -> "Skip (default icon): 3 Compile & Check"
                    ui.hasIcon -> "Next: 3 Compile & Check"
                    else -> "Generate & next: 3 Compile & Check"
                }
            )
        }
    }
}

/** Same layout as IconRenderer.visible(): background, then the image in the visible square. */
private fun DrawScope.drawIcon(bg: Color, bgImage: ImageBitmap?, source: ImageBitmap?, t: IconTransform) {
    drawRect(bg)
    val side = size.minDimension
    bgImage?.let { img ->
        val r = IconSpec.coverRect(side, img.width, img.height)
        drawImage(
            image = img,
            dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
            dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt())
        )
    }
    source?.let { img ->
        val r = IconSpec.imageRect(PxRect(0f, 0f, side, side), img.width, img.height, t)
        drawImage(
            image = img,
            dstOffset = IntOffset(r.left.roundToInt(), r.top.roundToInt()),
            dstSize = IntSize(r.width.roundToInt(), r.height.roundToInt())
        )
    }
}
