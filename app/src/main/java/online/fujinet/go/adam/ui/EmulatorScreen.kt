package online.fujinet.go.adam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.R
import online.fujinet.go.adam.SessionController
import online.fujinet.go.adam.fujinet.FujiNetWebViewActivity

private enum class Overlay { NONE, KEYBOARD, CONTROLLER }

@Composable
fun EmulatorScreen(session: SessionController, onShutdown: () -> Unit = {}) {
    var overlay by remember { mutableStateOf(Overlay.KEYBOARD) }
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val controller = rememberController(session, port = 0)

    var showSettings by remember { mutableStateOf(false) }
    var keyboardHaptics by remember { mutableStateOf(session.keyboardHapticsEnabled) }
    var joystickHaptics by remember { mutableStateOf(session.joystickHapticsEnabled) }

    if (showSettings) {
        SettingsDialog(
            config = session.config,
            keyboardHaptics = keyboardHaptics,
            joystickHaptics = joystickHaptics,
            onApply = { session.applyConfig(it) },
            onKeyboardHapticsChange = { keyboardHaptics = it; session.keyboardHapticsEnabled = it },
            onJoystickHapticsChange = { joystickHaptics = it; session.joystickHapticsEnabled = it },
            onResetColeco = { session.resetColeco(); showSettings = false },
            onDismiss = { showSettings = false },
        )
    }

    // MainActivity enables edge-to-edge on every API level, so keep the controls
    // clear of the status bar, the gesture/navigation bar and any display cutout
    // (without this the top menu bar hid under the status bar on older devices,
    // and the bottom keyboard/joystick row behind the nav bar).
    Column(modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        FunctionBar(
            overlay = overlay,
            onToggleKeyboard = { overlay = if (overlay == Overlay.KEYBOARD) Overlay.NONE else Overlay.KEYBOARD },
            onToggleController = { overlay = if (overlay == Overlay.CONTROLLER) Overlay.NONE else Overlay.CONTROLLER },
            onSettings = { showSettings = true },
            onOpenFujiNet = {
                context.startActivity(Intent(context, FujiNetWebViewActivity::class.java))
            },
            onShutdown = onShutdown,
        )

        if (landscape && overlay == Overlay.CONTROLLER) {
            // Flank the screen so it can render as large as possible: d-pad +
            // keypad on the left, fire buttons on the right. A phone's landscape
            // height is short, so the joystick + 4-row keypad column is compacted;
            // at full size its bottom keypad row (* 0 #) ran off the screen.
            val compact = compactControls()
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    JoystickPad(controller, size = if (compact) 132.dp else 176.dp, hapticsEnabled = joystickHaptics)
                    Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                    Keypad(controller, keySize = if (compact) 30.dp else 40.dp, hapticsEnabled = joystickHaptics)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    SmartKeyBar(controller, Modifier.fillMaxWidth(), hapticsEnabled = joystickHaptics)
                    EmulatorSurface(session = session, modifier = Modifier.fillMaxWidth().weight(1f))
                }
                FireButtons(controller, Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp), hapticsEnabled = joystickHaptics)
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EmulatorSurface(session = session, modifier = Modifier.fillMaxSize())
            }
            when (overlay) {
                Overlay.KEYBOARD -> AdamKeyboard(session = session, hapticsEnabled = keyboardHaptics)
                Overlay.CONTROLLER -> Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                    SmartKeyBar(controller, Modifier.fillMaxWidth(), hapticsEnabled = joystickHaptics)
                    ControllerRow(
                        controller,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        hapticsEnabled = joystickHaptics,
                    )
                }
                Overlay.NONE -> Unit
            }
        }
    }
}

@Composable
private fun FunctionBar(
    overlay: Overlay,
    onToggleKeyboard: () -> Unit,
    onToggleController: () -> Unit,
    onSettings: () -> Unit,
    onOpenFujiNet: () -> Unit,
    onShutdown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        BarButton(Icons.Filled.Keyboard, "Keyboard", Modifier.weight(1f), overlay == Overlay.KEYBOARD, onToggleKeyboard)
        BarButton(Icons.Filled.Gamepad, "Joystick", Modifier.weight(1f), overlay == Overlay.CONTROLLER, onToggleController)
        FujiNetBarButton(Modifier.weight(1f), onClick = onOpenFujiNet)
        BarButton(Icons.Filled.Settings, "Settings", Modifier.weight(1f), onClick = onSettings)
        BarButton(Icons.Filled.PowerSettingsNew, "Power off", Modifier.weight(1f), onClick = onShutdown)
    }
}

/**
 * The FujiNet web-UI button: the FujiNet "dot" logo, its white tile tinted to the
 * UI accent (Modulate keeps the black centre dot black and the corners
 * transparent, recolouring only the white). Matches the other Go-family apps.
 */
@Composable
private fun FujiNetBarButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.fujinet_toolbar),
            contentDescription = "FujiNet web UI",
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.Modulate),
        )
    }
}

// Active/selected toolbar tint. The periwinkle accent is light, so its onPrimary
// is black, which would vanish on the dark bar; the selected tab reads white.
private val AdamActiveTint = Color.White

@Composable
private fun BarButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) AdamActiveTint else MaterialTheme.colorScheme.primary,
        )
    }
}
