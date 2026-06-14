package online.fujinet.go.adam.input

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the ColecoVision/ADAM controller bit-packing (active-low, idle
 * 0x7F7F) and the ADAM key codes that the Compose UI sends to the native core.
 */
class AdamControllerTest {

    @Test fun idleIsAllReleased() {
        assertEquals(0x7F7F, AdamController.encode())
        assertEquals(AdamController.IDLE, AdamController.encode())
    }

    @Test fun directionsClearTheExpectedBits() {
        assertEquals(0x7E7F, AdamController.encode(up = true))     // ~0x0100
        assertEquals(0x7D7F, AdamController.encode(right = true))  // ~0x0200
        assertEquals(0x7B7F, AdamController.encode(down = true))   // ~0x0400
        assertEquals(0x777F, AdamController.encode(left = true))   // ~0x0800
    }

    @Test fun fireButtonsClearTheExpectedBits() {
        assertEquals(0x3F7F, AdamController.encode(fireLeft = true))   // ~0x4000
        assertEquals(0x7F3F, AdamController.encode(fireRight = true))  // ~0x0040
    }

    @Test fun directionsAndFireCombine() {
        assertEquals(0x3E7F, AdamController.encode(up = true, fireLeft = true))
    }

    @Test fun keypadSetsLowNibble() {
        assertEquals(0x7F70, AdamController.encode(keypad = 0))
        assertEquals(0x7F75, AdamController.encode(keypad = 5))
        assertEquals(0x7F7D, AdamController.encode(keypad = 13))
    }

    @Test fun keypadOutOfRangeIsNoKey() {
        assertEquals(0x7F7F, AdamController.encode(keypad = -1))
        assertEquals(0x7F7F, AdamController.encode(keypad = 99))
    }

    @Test fun adamKeyCodes() {
        assertEquals(65, AdamKeys.char('A'))
        assertEquals(97, AdamKeys.char('a'))
        assertEquals(0x0D, AdamKeys.RETURN)
        assertEquals(0x20, AdamKeys.SPACE)
        assertEquals(0x08, AdamKeys.BACKSPACE)
    }
}
