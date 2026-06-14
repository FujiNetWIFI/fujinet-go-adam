package online.fujinet.go.adam

import android.content.Context

/** Machine/display options that map onto ADAMEm command-line args. */
data class SessionConfig(
    val palette: Int = 0,        // -palette N (0..3; NR_PALETTES = 4)
    val cartPath: String? = null // -cart <path> for ColecoVision cartridges
)

/** Names for ADAMEm's four built-in palettes (index = -palette value). */
val PALETTE_NAMES = listOf("Default (TMS9928)", "Palette 2", "Palette 3", "Palette 4")

/** Synchronous, SharedPreferences-backed settings store. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("fujiadam", Context.MODE_PRIVATE)

    var config: SessionConfig
        get() = SessionConfig(
            palette = prefs.getInt(KEY_PALETTE, 0).coerceIn(0, PALETTE_NAMES.size - 1),
            cartPath = prefs.getString(KEY_CART, null),
        )
        set(value) {
            prefs.edit()
                .putInt(KEY_PALETTE, value.palette)
                .putString(KEY_CART, value.cartPath)
                .apply()
        }

    private companion object {
        const val KEY_PALETTE = "palette"
        const val KEY_CART = "cart"
    }
}
