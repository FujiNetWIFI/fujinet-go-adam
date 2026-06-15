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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.SessionController

/**
 * Held state for one ColecoVision/ADAM controller. The d-pad, fire buttons and
 * keypad are separate composables ([DPad] / [FireButtons] / [Keypad]) that share
 * this state, so they can sit together (portrait) or flank the screen (landscape).
 */
class Controller(private val session: SessionController, private val port: Int) {
    private var up = false
    private var down = false
    private var left = false
    private var right = false
    private var fireL = false
    private var fireR = false
    private var keypad = -1

    private fun push() = session.joystick(port, up, down, left, right, fireL, fireR, keypad)

    fun up(v: Boolean) { up = v; push() }
    fun down(v: Boolean) { down = v; push() }
    fun left(v: Boolean) { left = v; push() }
    fun right(v: Boolean) { right = v; push() }

    /** Set all four directions at once (analog [JoystickPad] / hardware gamepad). */
    fun move(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        this.up = up; this.down = down; this.left = left; this.right = right; push()
    }
    fun fireLeft(v: Boolean) { fireL = v; push() }
    fun fireRight(v: Boolean) { fireR = v; push() }
    fun keypad(v: Int) { keypad = v; push() }
}

@Composable
fun rememberController(session: SessionController, port: Int): Controller =
    remember(session, port) { Controller(session, port) }

@Composable
fun DPad(controller: Controller, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HoldKey("▲", controller::up)
        Row {
            HoldKey("◀", controller::left)
            Spacer(Modifier.size(56.dp))
            HoldKey("▶", controller::right)
        }
        HoldKey("▼", controller::down)
    }
}

@Composable
fun FireButtons(controller: Controller, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        HoldKey("L", controller::fireLeft, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        HoldKey("R", controller::fireRight, color = MaterialTheme.colorScheme.primary)
    }
}

/** The 12-key ColecoVision keypad (1-9, *, 0, #), momentary per press. */
@Composable
fun Keypad(controller: Controller, modifier: Modifier = Modifier) {
    val rows = listOf(
        listOf("1" to 1, "2" to 2, "3" to 3),
        listOf("4" to 4, "5" to 5, "6" to 6),
        listOf("7" to 7, "8" to 8, "9" to 9),
        listOf("*" to 10, "0" to 0, "#" to 11),
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        for (row in rows) {
            Row {
                for ((label, value) in row) {
                    HoldKey(label, { down -> controller.keypad(if (down) value else -1) }, size = 40.dp)
                }
            }
        }
    }
}

/** D-pad, keypad and fire buttons together, for the stacked (portrait) layout. */
@Composable
fun ControllerRow(controller: Controller, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JoystickPad(controller, size = 132.dp)
        Keypad(controller)
        FireButtons(controller)
    }
}

@Composable
private fun HoldKey(
    label: String,
    onHold: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(size)
            .background(color, if (size < 48.dp) RoundedCornerShape(6.dp) else CircleShape)
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
