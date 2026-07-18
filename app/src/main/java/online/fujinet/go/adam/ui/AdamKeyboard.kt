package online.fujinet.go.adam.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.fujinet.go.adam.SessionController
import online.fujinet.go.adam.input.AdamKeys

/**
 * Sticky-modifier state for the ADAM keyboard. ADAM has a single modifier -- SHIFT --
 * which selects the upper character set per row and is a sticky toggle (held until
 * tapped again), not one-shot. Hoisted into a holder so the portrait keyboard and the
 * split landscape halves share one source of truth.
 */
private class AdamKeyboardModifiers {
    var shift by mutableStateOf(false)
}

@Composable
private fun rememberAdamKeyboardModifiers() = remember { AdamKeyboardModifiers() }

/**
 * On-screen ADAM keyboard: the SmartKeys (I-VI) and WP/UNDO row, the editing
 * block (MOVE/COPY, STORE/GET, CLEAR, INSERT, PRINT, DELETE, WILD CARD), the
 * QWERTY core, and the cursor cluster with HOME. Keys inject the EOS byte codes
 * the ADAM keyboard hardware produces (see AdamKeys).
 *
 * Full-width; used in portrait and as the fallback for landscapes too narrow for
 * [LandscapeSplitKeyboard].
 */
@Composable
fun AdamKeyboard(
    session: SessionController,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val mods = rememberAdamKeyboardModifiers()
    val shift = mods.shift
    val emit = rememberFujiHaptic(FujiHapticPattern.KeyPress)
    val onHaptic = { if (hapticsEnabled) emit() }
    fun send(code: Int) { onHaptic(); session.key(code) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        // SmartKeys + WP + UNDO + WILD CARD
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) {
            // The physical "ESCAPE/WP" key. EOS abort (CONFIG "[ESC] ABORT",
            // BASIC, etc.) reads ASCII ESC 0x1B; WP 0x80 is SmartWriter-only and
            // not what software here expects, so send ESCAPE.
            Key("ESC\nWP", 1.6f) { send(AdamKeys.ESCAPE) }
            Key("I", 1f) { send(AdamKeys.SMART_I) }
            Key("II", 1f) { send(AdamKeys.SMART_II) }
            Key("III", 1f) { send(AdamKeys.SMART_III) }
            Key("IV", 1f) { send(AdamKeys.SMART_IV) }
            Key("V", 1f) { send(AdamKeys.SMART_V) }
            Key("VI", 1f) { send(AdamKeys.SMART_VI) }
            Key("UNDO", 1.6f) { send(AdamKeys.UNDO) }
            Key("WILD", 1.6f) { send(AdamKeys.WILDCARD) }
        }
        // Editing block
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) {
            Key("MOVE\nCOPY", 1f) { send(AdamKeys.MOVE_COPY) }
            Key("STORE\nGET", 1f) { send(AdamKeys.STORE_GET) }
            Key("CLEAR", 1f) { send(AdamKeys.CLEAR) }
            Key("INSERT", 1f) { send(AdamKeys.INSERT) }
            Key("PRINT", 1f) { send(AdamKeys.PRINT) }
            Key("DELETE", 1f) { send(AdamKeys.DELETE) }
        }
        // Number row
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) {
            for (c in (if (shift) "!@#\$%^&*()" else "1234567890")) {
                Key(c.toString(), 1f) { send(AdamKeys.char(c)) }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) {
            for (c in (if (shift) "QWERTYUIOP" else "qwertyuiop")) {
                Key(c.toString(), 1f) { send(AdamKeys.char(c)) }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) {
            Key("TAB", 1.5f) { send(AdamKeys.TAB) }
            for (c in (if (shift) "ASDFGHJKL" else "asdfghjkl")) {
                Key(c.toString(), 1f) { send(AdamKeys.char(c)) }
            }
            Key("RTN", 1.5f) { send(AdamKeys.RETURN) }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) {
            Key(if (shift) "SHIFT*" else "shift", 1.6f, active = shift) { onHaptic(); mods.shift = !mods.shift }
            for (c in (if (shift) "ZXCVBNM<>" else "zxcvbnm,.")) {
                Key(c.toString(), 1f) { send(AdamKeys.char(c)) }
            }
            Key("BKSP", 1.6f) { send(AdamKeys.BACKSPACE) }
        }
        // Space bar + cursor cluster with HOME
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(3.dp)) {
            Key("HOME", 1.4f) { send(AdamKeys.HOME) }
            Key("space", 4f) { send(AdamKeys.SPACE) }
            Key("◀", 1f) { send(AdamKeys.LEFT) }
            Key("▲", 1f) { send(AdamKeys.UP) }
            Key("▼", 1f) { send(AdamKeys.DOWN) }
            Key("▶", 1f) { send(AdamKeys.RIGHT) }
        }
    }
}

/** Minimum flank width worth splitting into; below this we keep the stacked keyboard. */
private val MIN_HALF_WIDTH = 150.dp
/** Cap so keys don't grow absurdly tall on very tall landscape panels. */
private val MAX_SPLIT_KEY_HEIGHT = 64.dp
/** The ADAM keyboard has seven rows, stacked to fill each flank. */
private const val SPLIT_ROWS = 7

/**
 * Landscape ADAM keyboard split into two halves placed in the pillar-box margins
 * beside the ~square (256x212) picture, so the display keeps full height between them.
 * Both halves share one [AdamKeyboardModifiers] so SHIFT toggled on one half applies to
 * a key tapped on the other. Falls back to the stacked [AdamKeyboard] when too narrow.
 */
@Composable
fun LandscapeSplitKeyboard(
    session: SessionController,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val mods = rememberAdamKeyboardModifiers()
    val emit = rememberFujiHaptic(FujiHapticPattern.KeyPress)
    val onHaptic = { if (hapticsEnabled) emit() }
    val send = { code: Int -> onHaptic(); session.key(code) }

    BoxWithConstraints(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        val sideWidth = (maxWidth - maxHeight * FRAME_RATIO) / 2
        if (sideWidth < MIN_HALF_WIDTH) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    EmulatorSurface(session = session, modifier = Modifier.fillMaxSize())
                }
                AdamKeyboard(session = session, hapticsEnabled = hapticsEnabled)
            }
        } else {
            val gap = 3.dp
            val keyH = ((maxHeight - gap * (SPLIT_ROWS - 1)) / SPLIT_ROWS).coerceAtMost(MAX_SPLIT_KEY_HEIGHT)
            val font: TextUnit = 13.sp
            Row(Modifier.fillMaxSize()) {
                AdamKeyboardHalf(left = true, mods, send, onHaptic, keyH, font, Modifier.width(sideWidth))
                EmulatorSurface(session = session, modifier = Modifier.weight(1f).fillMaxHeight())
                AdamKeyboardHalf(left = false, mods, send, onHaptic, keyH, font, Modifier.width(sideWidth))
            }
        }
    }
}

/**
 * One half of the split ADAM keyboard: the seven rows stacked to fill the flank. The
 * alphanumeric rows are split down the middle; the SmartKey, editing, TAB/RTN,
 * shift/BKSP and space/arrow rows are hand-split across the two flanks.
 */
@Composable
private fun AdamKeyboardHalf(
    left: Boolean,
    mods: AdamKeyboardModifiers,
    send: (Int) -> Unit,
    onHaptic: () -> Unit,
    keyH: Dp,
    font: TextUnit,
    modifier: Modifier,
) {
    val shift = mods.shift
    // Left flank takes the first half of each string row, right flank the remainder.
    fun half(s: String) = if (left) s.substring(0, s.length / 2) else s.substring(s.length / 2)
    val gap = 3.dp
    Column(
        modifier = modifier.padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically),
    ) {
        // SmartKeys row.
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
            if (left) {
                Key("ESC\nWP", 1.6f, keyH = keyH, font = font) { send(AdamKeys.ESCAPE) }
                Key("I", 1f, keyH = keyH, font = font) { send(AdamKeys.SMART_I) }
                Key("II", 1f, keyH = keyH, font = font) { send(AdamKeys.SMART_II) }
                Key("III", 1f, keyH = keyH, font = font) { send(AdamKeys.SMART_III) }
            } else {
                Key("IV", 1f, keyH = keyH, font = font) { send(AdamKeys.SMART_IV) }
                Key("V", 1f, keyH = keyH, font = font) { send(AdamKeys.SMART_V) }
                Key("VI", 1f, keyH = keyH, font = font) { send(AdamKeys.SMART_VI) }
                Key("UNDO", 1.4f, keyH = keyH, font = font) { send(AdamKeys.UNDO) }
                Key("WILD", 1.4f, keyH = keyH, font = font) { send(AdamKeys.WILDCARD) }
            }
        }
        // Editing row.
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
            if (left) {
                Key("MOVE\nCOPY", 1f, keyH = keyH, font = font) { send(AdamKeys.MOVE_COPY) }
                Key("STORE\nGET", 1f, keyH = keyH, font = font) { send(AdamKeys.STORE_GET) }
                Key("CLEAR", 1f, keyH = keyH, font = font) { send(AdamKeys.CLEAR) }
            } else {
                Key("INSERT", 1f, keyH = keyH, font = font) { send(AdamKeys.INSERT) }
                Key("PRINT", 1f, keyH = keyH, font = font) { send(AdamKeys.PRINT) }
                Key("DELETE", 1f, keyH = keyH, font = font) { send(AdamKeys.DELETE) }
            }
        }
        // Number row.
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
            for (c in half(if (shift) "!@#\$%^&*()" else "1234567890")) {
                Key(c.toString(), 1f, keyH = keyH, font = font) { send(AdamKeys.char(c)) }
            }
        }
        // QWERTY row.
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
            for (c in half(if (shift) "QWERTYUIOP" else "qwertyuiop")) {
                Key(c.toString(), 1f, keyH = keyH, font = font) { send(AdamKeys.char(c)) }
            }
        }
        // Home row + TAB (left) / RTN (right).
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
            if (left) {
                Key("TAB", 1.5f, keyH = keyH, font = font) { send(AdamKeys.TAB) }
                for (c in half(if (shift) "ASDFGHJKL" else "asdfghjkl")) {
                    Key(c.toString(), 1f, keyH = keyH, font = font) { send(AdamKeys.char(c)) }
                }
            } else {
                for (c in half(if (shift) "ASDFGHJKL" else "asdfghjkl")) {
                    Key(c.toString(), 1f, keyH = keyH, font = font) { send(AdamKeys.char(c)) }
                }
                Key("RTN", 1.5f, keyH = keyH, font = font) { send(AdamKeys.RETURN) }
            }
        }
        // Bottom row + SHIFT (left) / BKSP (right).
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
            if (left) {
                Key(if (shift) "SHIFT*" else "shift", 1.6f, active = shift, keyH = keyH, font = font) {
                    onHaptic(); mods.shift = !mods.shift
                }
                for (c in half(if (shift) "ZXCVBNM<>" else "zxcvbnm,.")) {
                    Key(c.toString(), 1f, keyH = keyH, font = font) { send(AdamKeys.char(c)) }
                }
            } else {
                for (c in half(if (shift) "ZXCVBNM<>" else "zxcvbnm,.")) {
                    Key(c.toString(), 1f, keyH = keyH, font = font) { send(AdamKeys.char(c)) }
                }
                Key("BKSP", 1.6f, keyH = keyH, font = font) { send(AdamKeys.BACKSPACE) }
            }
        }
        // HOME + space (left) / cursor cluster (right).
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
            if (left) {
                Key("HOME", 1.4f, keyH = keyH, font = font) { send(AdamKeys.HOME) }
                Key("space", 2.6f, keyH = keyH, font = font) { send(AdamKeys.SPACE) }
            } else {
                Key("◀", 1f, keyH = keyH, font = font) { send(AdamKeys.LEFT) }
                Key("▲", 1f, keyH = keyH, font = font) { send(AdamKeys.UP) }
                Key("▼", 1f, keyH = keyH, font = font) { send(AdamKeys.DOWN) }
                Key("▶", 1f, keyH = keyH, font = font) { send(AdamKeys.RIGHT) }
            }
        }
    }
}

// Bright highlight for the D-pad / TV-remote focused key. Shared with the
// on-screen Coleco keypad ([Keypad]), which is focus-driven the same way.
internal val FocusAmber = Color(0xFFFFC107)

/**
 * A keyboard key. Height/font default to the compact/normal sizes; the landscape split
 * passes explicit [keyH]/[font] so keys grow to fill the tall flank column.
 */
@Composable
private fun RowScope.Key(
    label: String,
    weight: Float,
    active: Boolean = false,
    keyH: Dp? = null,
    font: TextUnit? = null,
    onTap: () -> Unit,
) {
    val compact = compactControls()
    val h = keyH ?: if (compact) 28.dp else 44.dp
    val f = font ?: if (compact) 9.sp else 11.sp
    val lh = if (font != null) (f.value * 1.15f).sp else if (compact) 10.sp else 12.sp
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    // On a TV the keys are driven by the remote's D-pad, so the focused key must read
    // clearly from across the room: a bright amber fill with a thick white outline.
    val bg = when {
        focused -> FocusAmber
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    // The focused (amber) and active (periwinkle primary) fills are both light,
    // so their label inverts to dark; resting keys keep the light onSurface label.
    val fg = when {
        focused -> Color.Black
        active -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .weight(weight)
            .height(h)
            .background(bg, shape)
            .then(if (focused) Modifier.border(3.dp, Color.White, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = ripple()) { onTap() }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = fg,
            fontSize = f,
            lineHeight = lh,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * On-screen controls shrink to compact sizes on TV and other short screens --
 * landscape, and small foldable cover displays like the razr's -- so they don't
 * fill the display or run off the bottom. Shared by the keyboard ([Key]) and the
 * joystick page's keypad / fire buttons / SmartKeys (see ControllerPad).
 */
@Composable
internal fun compactControls(): Boolean {
    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    return isTv || config.screenHeightDp < 480
}
