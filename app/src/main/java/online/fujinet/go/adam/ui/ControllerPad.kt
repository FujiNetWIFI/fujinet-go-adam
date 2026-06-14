package online.fujinet.go.adam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.SessionController

/**
 * A ColecoVision/ADAM controller: an 8-way d-pad on the left and two fire
 * buttons on the right. Both halves push the combined joystick state to [port].
 */
@Composable
fun ControllerPad(
    session: SessionController,
    port: Int,
    modifier: Modifier = Modifier,
) {
    var up by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var right by remember { mutableStateOf(false) }
    var fireL by remember { mutableStateOf(false) }
    var fireR by remember { mutableStateOf(false) }

    fun push() = session.joystick(port, up, down, left, right, fireL, fireR)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // D-pad
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HoldKey("▲", { up = it; push() })
            Row {
                HoldKey("◀", { left = it; push() })
                Spacer(Modifier.size(56.dp))
                HoldKey("▶", { right = it; push() })
            }
            HoldKey("▼", { down = it; push() })
        }
        // Fire buttons
        Row(verticalAlignment = Alignment.Bottom) {
            HoldKey("L", { fireL = it; push() }, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            HoldKey("R", { fireR = it; push() }, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun HoldKey(
    label: String,
    onHold: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(56.dp)
            .background(color, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onHold(true)
                    try {
                        awaitRelease()
                    } finally {
                        onHold(false)
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
    }
}
