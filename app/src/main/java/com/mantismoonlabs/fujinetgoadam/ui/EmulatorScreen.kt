package com.mantismoonlabs.fujinetgoadam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
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
import com.mantismoonlabs.fujinetgoadam.MediaImport
import com.mantismoonlabs.fujinetgoadam.SessionController
import com.mantismoonlabs.fujinetgoadam.fujinet.FujiNetWebViewActivity
import kotlin.concurrent.thread

private enum class Overlay { NONE, KEYBOARD, CONTROLLER }

@Composable
fun EmulatorScreen(session: SessionController) {
    var overlay by remember { mutableStateOf(Overlay.CONTROLLER) }
    val context = LocalContext.current

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
            onDismiss = { showSettings = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        FunctionBar(
            overlay = overlay,
            onToggleKeyboard = { overlay = if (overlay == Overlay.KEYBOARD) Overlay.NONE else Overlay.KEYBOARD },
            onToggleController = { overlay = if (overlay == Overlay.CONTROLLER) Overlay.NONE else Overlay.CONTROLLER },
            onReset = { session.reset(coldStart = false) },
            onImport = { importPicker.launch(arrayOf("*/*")) },
            onCartridge = { cartPicker.launch(arrayOf("*/*")) },
            onSettings = { showSettings = true },
            onOpenFujiNet = {
                context.startActivity(Intent(context, FujiNetWebViewActivity::class.java))
            },
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            EmulatorSurface(session = session, modifier = Modifier.fillMaxSize())
        }

        when (overlay) {
            Overlay.KEYBOARD -> AdamKeyboard(session = session)
            Overlay.CONTROLLER -> ControllerPad(
                session = session,
                port = 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Overlay.NONE -> Unit
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "FujiNet Go Adam",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        BarButton("FujiNet", active = false, onClick = onOpenFujiNet)
        BarButton("Import", active = false, onClick = onImport)
        BarButton("Cart", active = false, onClick = onCartridge)
        BarButton("Joystick", active = overlay == Overlay.CONTROLLER, onClick = onToggleController)
        BarButton("Keyboard", active = overlay == Overlay.KEYBOARD, onClick = onToggleKeyboard)
        BarButton("Reset", active = false, onClick = onReset)
        BarButton("⚙", active = false, onClick = onSettings)
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
