package online.fujinet.go.adam.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.fujinet.go.adam.SessionController
import online.fujinet.go.adam.input.AdamKeys

/**
 * On-screen ADAM keyboard: the SmartKeys (I-VI) and WP/UNDO row, the editing
 * block (MOVE/COPY, STORE/GET, CLEAR, INSERT, PRINT, DELETE, WILD CARD), the
 * QWERTY core, and the cursor cluster with HOME. Keys inject the EOS byte codes
 * the ADAM keyboard hardware produces (see AdamKeys).
 */
@Composable
fun AdamKeyboard(
    session: SessionController,
    modifier: Modifier = Modifier,
) {
    var shift by remember { mutableStateOf(false) }
    fun send(code: Int) = session.key(code)

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
            Key(if (shift) "SHIFT*" else "shift", 1.6f, active = shift) { shift = !shift }
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

@Composable
private fun RowScope.Key(
    label: String,
    weight: Float,
    active: Boolean = false,
    onTap: () -> Unit,
) {
    val compact = compactKeyboard()
    Box(
        modifier = Modifier
            .weight(weight)
            .height(if (compact) 28.dp else 44.dp)
            .background(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(6.dp),
            )
            .clickable { onTap() }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (compact) 9.sp else 11.sp,
            lineHeight = if (compact) 10.sp else 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The on-screen keyboard's keys shrink to a compact height on TV and other short
 * screens (e.g. landscape) so it doesn't fill most of the display.
 */
@Composable
private fun compactKeyboard(): Boolean {
    val config = LocalConfiguration.current
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    return isTv || config.screenHeightDp < 480
}
