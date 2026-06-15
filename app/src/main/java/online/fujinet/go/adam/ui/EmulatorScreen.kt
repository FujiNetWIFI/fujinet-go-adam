package online.fujinet.go.adam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.MediaImport
import online.fujinet.go.adam.SessionController
import online.fujinet.go.adam.fujinet.FujiNetWebViewActivity
import kotlin.concurrent.thread

private enum class Overlay { NONE, KEYBOARD, CONTROLLER }

@Composable
fun EmulatorScreen(session: SessionController, onShutdown: () -> Unit = {}) {
    var overlay by remember { mutableStateOf(Overlay.CONTROLLER) }
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val controller = rememberController(session, port = 0)

    var showSettings by remember { mutableStateOf(false) }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val sd = session.sdPath
        if (uri != null && sd != null) {
            thread(name = "adam-import") { MediaImport.importToSd(context, uri, sd) }
        }
    }

    val cartPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            thread(name = "adam-cart") {
                MediaImport.importCartridge(context, uri)?.let { session.loadCartridge(it) }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            config = session.config,
            onApply = { session.applyConfig(it) },
            onEjectCartridge = { session.ejectCartridge(); showSettings = false },
            onResetColeco = { session.resetColeco(); showSettings = false },
            onDismiss = { showSettings = false },
        )
    }

    // Edge-to-edge is enforced on recent Android, so keep the controls clear of
    // the status bar, the gesture/navigation bar and any display cutout (the
    // bottom keyboard/joystick row was being hidden behind the nav bar).
    Column(modifier = Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        FunctionBar(
            overlay = overlay,
            onToggleKeyboard = { overlay = if (overlay == Overlay.KEYBOARD) Overlay.NONE else Overlay.KEYBOARD },
            onToggleController = { overlay = if (overlay == Overlay.CONTROLLER) Overlay.NONE else Overlay.CONTROLLER },
            onReset = { session.resetAdam() },
            onImport = { importPicker.launch(arrayOf("*/*")) },
            onCartridge = { cartPicker.launch(arrayOf("*/*")) },
            onSettings = { showSettings = true },
            onOpenFujiNet = {
                context.startActivity(Intent(context, FujiNetWebViewActivity::class.java))
            },
            onShutdown = onShutdown,
        )

        if (landscape && overlay == Overlay.CONTROLLER) {
            // Flank the screen so it can render as large as possible: d-pad +
            // keypad on the left, fire buttons on the right.
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.align(Alignment.CenterVertically).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    JoystickPad(controller)
                    Spacer(Modifier.height(16.dp))
                    Keypad(controller)
                }
                EmulatorSurface(session = session, modifier = Modifier.weight(1f).fillMaxHeight())
                FireButtons(controller, Modifier.align(Alignment.CenterVertically).padding(horizontal = 8.dp))
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                EmulatorSurface(session = session, modifier = Modifier.fillMaxSize())
            }
            when (overlay) {
                Overlay.KEYBOARD -> AdamKeyboard(session = session)
                Overlay.CONTROLLER -> ControllerRow(
                    controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
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
    onReset: () -> Unit,
    onImport: () -> Unit,
    onCartridge: () -> Unit,
    onSettings: () -> Unit,
    onOpenFujiNet: () -> Unit,
    onShutdown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BarButton("FujiNet", active = false, onClick = onOpenFujiNet)
        BarButton("Import", active = false, onClick = onImport)
        BarButton("Cart", active = false, onClick = onCartridge)
        BarButton("Joy", active = overlay == Overlay.CONTROLLER, onClick = onToggleController)
        BarButton("Keys", active = overlay == Overlay.KEYBOARD, onClick = onToggleKeyboard)
        BarButton("Reset", active = false, onClick = onReset)
        BarButton("⚙", active = false, onClick = onSettings)
        BarButton("⏻ Stop", active = false, onClick = onShutdown)
    }
}

@Composable
private fun BarButton(label: String, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Text(
        label,
        color = tint,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
