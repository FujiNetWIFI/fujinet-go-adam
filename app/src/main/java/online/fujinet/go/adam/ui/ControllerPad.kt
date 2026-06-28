package online.fujinet.go.adam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.input.AdamKeys
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

    /** Tap an ADAM keyboard key (SmartKeys / ESC): momentary, into the key buffer. */
    fun key(code: Int) = session.key(code)
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

/**
 * The 12-key ColecoVision keypad (1-9, *, 0, #), momentary per press. Each key is
 * touch-driven on a phone and focus-driven on a TV: the keys are [focusable] so the
 * remote's D-pad can move between them and OK/Enter presses the highlighted one --
 * games that select with the Coleco keypad (skill level, etc.) need this when the
 * joystick is driven by a gamepad and there's no touchscreen.
 */
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
                    KeypadKey(label, value, controller)
                }
            }
        }
    }
}

/**
 * One Coleco keypad button. The value is *held* while pressed (set on press/key-down,
 * cleared to -1 on release/key-up) rather than pulsed on a single tap: the emulator
 * polls the controller register per frame, so a value present for only an instant can
 * fall between frames and a polling game would never see it.
 */
@Composable
private fun KeypadKey(
    label: String,
    value: Int,
    controller: Controller,
    size: Dp = 40.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    val bg = if (focused) FocusAmber else MaterialTheme.colorScheme.surface
    val fg = if (focused) Color.Black else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(size)
            .background(bg, shape)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            // Touch (phone): hold to press the key, release to clear it.
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    controller.keypad(value)
                    try {
                        awaitRelease()
                    } finally {
                        controller.keypad(-1)
                    }
                })
            }
            // TV remote / gamepad: the D-pad navigates here and OK/Enter holds the key.
            .onKeyEvent { ev ->
                if (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter) {
                    when (ev.type) {
                        KeyEventType.KeyDown -> { controller.keypad(value); true }
                        KeyEventType.KeyUp -> { controller.keypad(-1); true }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable(interactionSource = interaction),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

/** Joystick + keypad and fire buttons together, for the stacked (portrait)
 *  layout: the keypad sits under the joystick halo so the row stays narrow
 *  enough that the fire buttons don't run off the right edge on a phone. */
@Composable
fun ControllerRow(controller: Controller, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            JoystickPad(controller, size = 132.dp)
            Keypad(controller)
        }
        FireButtons(controller)
    }
}

/**
 * ESC plus the six ADAM SmartKeys (I-VI) as momentary taps, for the joystick
 * page: software driven by the joystick (SmartBASIC, games, CONFIG) often still
 * needs the SmartKeys and ESC without opening the full keyboard.
 */
@Composable
fun SmartKeyBar(controller: Controller, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapKey("ESC", Modifier.weight(1.4f)) { controller.key(AdamKeys.ESCAPE) }
        TapKey("I", Modifier.weight(1f)) { controller.key(AdamKeys.SMART_I) }
        TapKey("II", Modifier.weight(1f)) { controller.key(AdamKeys.SMART_II) }
        TapKey("III", Modifier.weight(1f)) { controller.key(AdamKeys.SMART_III) }
        TapKey("IV", Modifier.weight(1f)) { controller.key(AdamKeys.SMART_IV) }
        TapKey("V", Modifier.weight(1f)) { controller.key(AdamKeys.SMART_V) }
        TapKey("VI", Modifier.weight(1f)) { controller.key(AdamKeys.SMART_VI) }
    }
}

/** A momentary (single-tap) key button, sized by the caller's weight. */
@Composable
private fun TapKey(label: String, modifier: Modifier = Modifier, onTap: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
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
