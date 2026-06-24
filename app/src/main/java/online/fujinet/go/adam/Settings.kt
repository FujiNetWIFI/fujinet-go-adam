package online.fujinet.go.adam

import android.content.Context

/** Machine/display/controller options that map onto ADAMEm command-line args. */
data class SessionConfig(
    val palette: String = PALETTE_NAMES[0],
    val expansion: String = EXPANSION_NAMES[0],
    val joystick: String = JOYSTICK_NAMES[1],   // default: Both ports
    val swapButtons: String = "No",
    val reverseKeypad: String = "No",
    val cartPath: String? = null,
)

/** Names for ADAMEm's four built-in palettes (index = -palette value). */
val PALETTE_NAMES = listOf("Default (TMS9928)", "Palette 2", "Palette 3", "Palette 4")

/** Expansion module modes (index = -expansion value). */
val EXPANSION_NAMES = listOf(
    "None",
    "Roller controller (mouse)",
    "Roller controller (joystick)",
    "Driving module (joystick)",
    "Driving module (mouse)",
    "Super Action speed roller, both ports (mouse)",
    "Speed roller, port 1 (mouse)",
    "Speed roller, port 2 (mouse)",
)

/** Joystick modes (index = -joystick value). */
val JOYSTICK_NAMES = listOf(
    "No joystick",
    "Both ports",
    "Port 2 only",
    "Port 1 only",
)

val YES_NO = listOf("No", "Yes")

/** Synchronous, SharedPreferences-backed settings store. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fujiadam", Context.MODE_PRIVATE)

    var config: SessionConfig
        get() = SessionConfig(
            palette = prefs.getString(KEY_PALETTE, null)?.takeIf { it in PALETTE_NAMES } ?: PALETTE_NAMES[0],
            expansion = prefs.getString(KEY_EXPANSION, null)?.takeIf { it in EXPANSION_NAMES } ?: EXPANSION_NAMES[0],
            joystick = prefs.getString(KEY_JOYSTICK, null)?.takeIf { it in JOYSTICK_NAMES } ?: JOYSTICK_NAMES[1],
            swapButtons = prefs.getString(KEY_SWAP_BUTTONS, null)?.takeIf { it in YES_NO } ?: "No",
            reverseKeypad = prefs.getString(KEY_REVERSE_KEYPAD, null)?.takeIf { it in YES_NO } ?: "No",
            cartPath = prefs.getString(KEY_CART, null),
        )
        set(value) {
            prefs.edit()
                .putString(KEY_PALETTE, value.palette)
                .putString(KEY_EXPANSION, value.expansion)
                .putString(KEY_JOYSTICK, value.joystick)
                .putString(KEY_SWAP_BUTTONS, value.swapButtons)
                .putString(KEY_REVERSE_KEYPAD, value.reverseKeypad)
                .putString(KEY_CART, value.cartPath)
                .apply()
        }

    private companion object {
        const val KEY_PALETTE = "palette"
        const val KEY_EXPANSION = "expansion"
        const val KEY_JOYSTICK = "joystick"
        const val KEY_SWAP_BUTTONS = "swapButtons"
        const val KEY_REVERSE_KEYPAD = "reverseKeypad"
        const val KEY_CART = "cart"
    }
}
