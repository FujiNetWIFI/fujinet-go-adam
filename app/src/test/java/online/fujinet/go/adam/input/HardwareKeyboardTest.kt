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
    fun cursorKeysAreReservedForNavigationNotTyping() {
        // The cursor cluster drives on-screen-keyboard focus (TV remote), so it is
        // not in the typing table; isDpadNavigation() filters it ahead of this lookup.
        assertNull(specialAdamCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(specialAdamCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertNull(specialAdamCode(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(specialAdamCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertNull(specialAdamCode(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
