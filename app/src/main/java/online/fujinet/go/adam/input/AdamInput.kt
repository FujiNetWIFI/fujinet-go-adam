package online.fujinet.go.adam.input

/**
 * Encoding helpers for ADAM/ColecoVision input, kept on the Kotlin side so the
 * native `adamhost_set_joystick(port, rawState)` stays a thin setter.
 *
 * The ColecoVision controller is read as a 16-bit value, active-low, idle =
 * 0x7F7F. Direction and fire bits live as documented in ADAMEm's AdamemSDL.c;
 * the low nibble carries the keypad value (0x0F = no key).
 */
object AdamController {
    const val IDLE = 0x7F7F

    // Direction bits (cleared when pressed).
    private const val UP = 0x0100
    private const val RIGHT = 0x0200
    private const val DOWN = 0x0400
    private const val LEFT = 0x0800

    // Fire buttons (cleared when pressed).
    private const val FIRE_LEFT = 0x4000   // "fire"/left action button
    private const val FIRE_RIGHT = 0x0040  // "arm"/right action button

    private const val KEYPAD_NONE = 0x0F

    /**
     * @param keypad 0..9, 10 (*), 11 (#), or negative for none.
     */
    fun encode(
        up: Boolean = false,
        down: Boolean = false,
        left: Boolean = false,
        right: Boolean = false,
        fireLeft: Boolean = false,
        fireRight: Boolean = false,
        keypad: Int = -1,
    ): Int {
        var s = IDLE
        if (up) s = s and UP.inv()
        if (down) s = s and DOWN.inv()
        if (left) s = s and LEFT.inv()
        if (right) s = s and RIGHT.inv()
        if (fireLeft) s = s and FIRE_LEFT.inv()
        if (fireRight) s = s and FIRE_RIGHT.inv()
        val key = if (keypad in 0..13) keypad else KEYPAD_NONE
        s = (s and 0xFFF0) or key
        return s
    }
}

/** ADAM keyboard byte codes (ASCII-compatible; the EOS keyboard driver reads these). */
object AdamKeys {
    const val RETURN = 0x0D
    const val BACKSPACE = 0x08
    const val ESCAPE = 0x1B
    const val TAB = 0x09
    const val SPACE = 0x20

    fun char(c: Char): Int = c.code and 0xFF
}
