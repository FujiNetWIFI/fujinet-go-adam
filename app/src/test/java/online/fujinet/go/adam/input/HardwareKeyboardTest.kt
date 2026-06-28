package online.fujinet.go.adam.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the Android-keycode -> ADAM-byte mapping for the non-printable keys a
 * hardware keyboard sends. The KEYCODE_* values are compile-time constants
 * (inlined), so this runs under plain JUnit without an Android device. Printable
 * keys are resolved from the event's Unicode char at runtime and aren't covered here.
 */
class HardwareKeyboardTest {

    @Test
    fun specialKeysMapToAdamCodes() {
        assertEquals(AdamKeys.RETURN, specialAdamCode(KeyEvent.KEYCODE_ENTER))
        assertEquals(AdamKeys.RETURN, specialAdamCode(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertEquals(AdamKeys.ESCAPE, specialAdamCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(AdamKeys.TAB, specialAdamCode(KeyEvent.KEYCODE_TAB))
        assertEquals(AdamKeys.SPACE, specialAdamCode(KeyEvent.KEYCODE_SPACE))
        assertEquals(AdamKeys.BACKSPACE, specialAdamCode(KeyEvent.KEYCODE_DEL))
        assertEquals(AdamKeys.DELETE, specialAdamCode(KeyEvent.KEYCODE_FORWARD_DEL))
        assertEquals(AdamKeys.HOME, specialAdamCode(KeyEvent.KEYCODE_MOVE_HOME))
    }

    @Test
    fun printableAndUnknownKeysAreNotSpecialCased() {
        // Letters/digits go through the printable (Unicode) path, not the table.
        assertNull(specialAdamCode(KeyEvent.KEYCODE_A))
        assertNull(specialAdamCode(KeyEvent.KEYCODE_1))
        // Bare modifiers and volume keys are never forwarded.
        assertNull(specialAdamCode(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertNull(specialAdamCode(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun cursorKeysMapToAdamCursorCodesForTyping() {
        // Cursor keys typed on a keyboard reach the ADAM (e.g. the FujiNet CONFIG
        // selection bar). isDpadNavigation() routes a TV remote's D-pad (SOURCE_DPAD)
        // to focus navigation before this keycode lookup is consulted.
        assertEquals(AdamKeys.LEFT, specialAdamCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(AdamKeys.RIGHT, specialAdamCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(AdamKeys.UP, specialAdamCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(AdamKeys.DOWN, specialAdamCode(KeyEvent.KEYCODE_DPAD_DOWN))
        // DPAD_CENTER has no ADAM equivalent and is never forwarded.
        assertNull(specialAdamCode(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
