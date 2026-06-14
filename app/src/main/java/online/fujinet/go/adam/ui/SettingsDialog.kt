package online.fujinet.go.adam.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.PALETTE_NAMES
import online.fujinet.go.adam.SessionConfig

/**
 * Machine/display settings. Changing the palette (or ejecting a cartridge)
 * restarts the session so the new ADAMEm command line takes effect.
 */
@Composable
fun SettingsDialog(
    config: SessionConfig,
    onApply: (SessionConfig) -> Unit,
    onEjectCartridge: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Settings") },
        text = {
            Column {
                Text("Palette", style = MaterialTheme.typography.titleSmall)
                PALETTE_NAMES.forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = config.palette == index,
                                onClick = { onApply(config.copy(palette = index)) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = config.palette == index, onClick = null)
                        Text(name, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (config.cartPath != null) {
                    TextButton(onClick = onEjectCartridge, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Eject cartridge")
                    }
                }
            }
        },
    )
}
