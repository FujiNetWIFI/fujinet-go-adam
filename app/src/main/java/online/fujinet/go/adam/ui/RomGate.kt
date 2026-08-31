package online.fujinet.go.adam.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import online.fujinet.go.adam.MediaImport
import online.fujinet.go.adam.R
import online.fujinet.go.adam.settings.RomStore

private val GateAccent = Color(0xFF8D84E5)
private val GateBackground = Color(0xFF0D0B1A)
private val GateBody = Color(0xFFE9E7F5)

/**
 * Shown in place of the emulator surface until OS7.rom, EOS.rom and WP.rom
 * are present -- release builds ship no Coleco system ROMs (see
 * COMPLIANCE.md), so this is every user's first-run screen unless they built
 * with -PadamRoms=true. Imports are classified by CRC32 (with a file-name
 * fallback), so one picker handles all three. [onImported] is called after
 * each import attempt so the caller can re-check [RomStore.hasSystemRoms].
 */
@Composable
fun RomGate(onImported: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refreshToken by remember { mutableIntStateOf(0) }
    val status = remember(refreshToken) { RomStore.status(context) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val result = MediaImport.importSystemRom(context, uri)) {
            is MediaImport.RomImportResult.Success -> {
                val note = if (result.crcMatches) "" else
                    " (matched by name/size, not the reference dump)"
                Toast.makeText(context, "Imported ${result.fileName}$note", Toast.LENGTH_LONG).show()
            }
            is MediaImport.RomImportResult.NotRecognized -> {
                Toast.makeText(
                    context,
                    "Not a recognized ADAM system ROM (${result.actualSize} bytes; " +
                        "expected OS7/EOS at 8192 or WP at 32768)",
                    Toast.LENGTH_LONG,
                ).show()
            }
            MediaImport.RomImportResult.ReadFailed -> {
                Toast.makeText(context, "Could not read the selected file", Toast.LENGTH_SHORT).show()
            }
        }
        refreshToken++
        onImported()
    }

    Column(
        modifier = modifier.fillMaxSize().background(GateBackground)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.fujinet_toolbar),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
        )
        Text(
            "ADAM system ROMs required",
            style = MaterialTheme.typography.titleLarge,
            color = GateAccent,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        Text(
            "Import OS7.rom, EOS.rom and WP.rom from your own Coleco ADAM " +
                "to boot. All three are needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = GateBody,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            status.forEach { rom ->
                Text(
                    (if (rom.present) "✓ " else "✗ ") +
                        "${rom.rom.label} (${rom.rom.fileName}, ${rom.rom.expectedSize} B)",
                    color = if (rom.present) GateAccent else GateBody,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
        val blip = LocalUiHaptic.current
        Button(onClick = { blip(); picker.launch(arrayOf("*/*")) }) {
            Text("Import ROMs…")
        }
    }
}
