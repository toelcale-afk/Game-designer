package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Pipeline step 2: launcher icon. (Icon maker arrives in Phase 3.) */
@Composable
fun IconStepScreen(viewModel: PackFlowViewModel, onNext: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Launcher icon", style = MaterialTheme.typography.titleLarge)
        Text(
            "The default PackFlow icon is used for now.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next: 3 Compile & Check") }
    }
}
