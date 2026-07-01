package online.fujinet.go.adam.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.EXPANSION_NAMES
import online.fujinet.go.adam.JOYSTICK_NAMES
import online.fujinet.go.adam.PALETTE_NAMES
import online.fujinet.go.adam.SessionConfig
import online.fujinet.go.adam.YES_NO

/**
 * Machine/display/controller settings. Clicking Apply restarts the session so
 * the new ADAMEm command line takes effect.
 */
@Composable
fun SettingsDialog(
    config: SessionConfig,
    keyboardHaptics: Boolean,
    joystickHaptics: Boolean,
    onApply: (SessionConfig) -> Unit,
    onKeyboardHapticsChange: (Boolean) -> Unit,
    onJoystickHapticsChange: (Boolean) -> Unit,
    onResetColeco: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(config) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (draft != config) onApply(draft)
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OptionRow("Palette", PALETTE_NAMES, draft.palette) { draft = draft.copy(palette = it) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Controllers", style = MaterialTheme.typography.titleSmall)

                OptionRow("Expansion module", EXPANSION_NAMES, draft.expansion) { draft = draft.copy(expansion = it) }
                OptionRow("Joystick mode", JOYSTICK_NAMES, draft.joystick) { draft = draft.copy(joystick = it) }
                OptionRow("Swap joystick buttons", YES_NO, draft.swapButtons) { draft = draft.copy(swapButtons = it) }
                OptionRow("Reverse keypad", YES_NO, draft.reverseKeypad) { draft = draft.copy(reverseKeypad = it) }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Haptics", style = MaterialTheme.typography.titleSmall)
                // Haptics apply live (no session restart), so they call back immediately
                // rather than going through the draft/Apply path the machine options use.
                ToggleRow("Keyboard haptics", keyboardHaptics, onKeyboardHapticsChange)
                ToggleRow("Joystick haptics", joystickHaptics, onJoystickHapticsChange)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { onResetColeco(); onDismiss() }) {
                    Text("ColecoVision reset")
                }
            }
        },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected, textAlign = TextAlign.End)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { onSelect(opt); expanded = false },
                    )
                }
            }
        }
    }
}
