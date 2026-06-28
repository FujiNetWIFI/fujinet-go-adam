package online.fujinet.go.adam.input

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * Routes an attached hardware keyboard (USB / Bluetooth) to the ADAM keyboard,
 * mirroring what the on-screen [online.fujinet.go.adam.ui.AdamKeyboard] sends:
 * a single ADAM byte code per keypress (ASCII for printable keys, EOS codes for
 * the special keys -- see [AdamKeys]). The ADAM keyboard is a "type this byte"
 * model, so only key-down matters; [onKey] feeds the code and returns true when it
 * consumed the event.
 *
 * Only events from a real *alphabetic* keyboard device are consumed, and the D-pad
 * cluster (arrows + OK) is always left to Compose focus navigation so a TV remote
 * keeps driving the on-screen keyboard rather than typing.
 */
class HardwareKeyboard(private val onKey: (code: Int) -> Unit) {

    fun onKey(event: KeyEvent): Boolean {
        if (!event.isFromPhysicalKeyboard()) return false
        if (isDpadNavigation(event)) return false
        val code = mapAdamKey(event) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN) onKey(code)
        return true // consume the matching up too so it never reaches Compose
    }

    private fun KeyEvent.isFromPhysicalKeyboard(): Boolean {
        val d = device ?: return false
        return !d.isVirtual &&
            d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
            source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
    }
}

/**
 * Translate an Android key event to an ADAM key byte, or null for keys we don't
 * forward (bare modifiers, volume, function keys, etc.).
 */
internal fun mapAdamKey(event: KeyEvent): Int? {
    specialAdamCode(event.keyCode)?.let { return it }

    // Ctrl + letter -> ASCII control code (Ctrl-C = 0x03, etc.).
    val base = event.getUnicodeChar(0) and KeyCharacterMap.COMBINING_ACCENT_MASK
    if (event.isCtrlPressed && base in 'a'.code..'z'.code) return base - 'a'.code + 1

    // Printable key: the ADAM code is just the typed (possibly shifted) ASCII.
    val ch = event.unicodeChar and KeyCharacterMap.COMBINING_ACCENT_MASK
    if (ch == 0 || ch > 0x7e) return null
    return ch and 0xFF
}

/**
 * Map the Android keycodes for non-printable / special keys to their ADAM byte.
 * Pure (no [KeyEvent] instance) so it can be unit-tested. The cursor keys are
 * intentionally absent -- they navigate the on-screen keyboard (see
 * isDpadNavigation()); use the on-screen cursor cluster to move the ADAM cursor.
 */
internal fun specialAdamCode(androidKeyCode: Int): Int? = when (androidKeyCode) {
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> AdamKeys.RETURN
    KeyEvent.KEYCODE_ESCAPE -> AdamKeys.ESCAPE
    KeyEvent.KEYCODE_TAB -> AdamKeys.TAB
    KeyEvent.KEYCODE_SPACE -> AdamKeys.SPACE
    KeyEvent.KEYCODE_DEL -> AdamKeys.BACKSPACE          // Backspace
    KeyEvent.KEYCODE_FORWARD_DEL -> AdamKeys.DELETE
    KeyEvent.KEYCODE_MOVE_HOME -> AdamKeys.HOME
    else -> null
}

/**
 * True for the keys that must navigate/activate the on-screen keyboard rather than
 * type into the emulator. The arrows and DPAD_CENTER are always reserved. A remote's
 * "OK" can arrive as ENTER; it carries a D-pad source, whereas a typing keyboard's
 * Enter does not -- so keyboard Enter still reaches the ADAM as RETURN.
 */
private fun isDpadNavigation(event: KeyEvent): Boolean = when (event.keyCode) {
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER -> true
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER ->
        event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
    else -> false
}
