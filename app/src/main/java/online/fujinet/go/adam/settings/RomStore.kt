package online.fujinet.go.adam.settings

import android.content.Context
import java.io.File

/**
 * System-ROM directory layout and presence checks for the "System ROMs
 * required" gate (ui/RomGate.kt). Mirrors the layout RuntimeInstaller
 * produces (<filesDir>/adamem/roms -- the paths handed to adamcore as
 * -os7/-eos/-wp), computed independently here so the gate can check status
 * before a session exists.
 *
 * The Coleco system ROMs are copyrighted and are NOT shipped in release
 * builds (see COMPLIANCE.md): users import their own dumps. Dev builds
 * (-PadamRoms=true) bundle them as assets, which stageEmbeddedRoms() copies
 * out fill-missing -- a user import always wins over a bundled copy.
 */
object RomStore {

    /**
     * OS7 and EOS are both 8192 bytes, so imports classify by CRC32 first
     * (each image has one canonical Coleco revision); a size + file-name
     * match ("os7"/"eos"/"wp" in the picked document's name) is accepted
     * with a warning as a fallback for re-dumped originals.
     */
    enum class AdamRom(
        val fileName: String,
        val label: String,
        val expectedSize: Int,
        val expectedCrc: Long,
        val nameHint: String,
    ) {
        OS7("OS7.rom", "ColecoVision OS7 BIOS", 8192, 0x535D211DL, "os7"),
        EOS("EOS.rom", "ADAM EOS (Elementary OS)", 8192, 0x05A37A34L, "eos"),
        WP("WP.rom", "SmartWriter word processor", 32768, 0x58D86A2AL, "wp"),
    }

    fun romsDir(context: Context): File =
        File(context.filesDir, "adamem/roms").apply { mkdirs() }
            .also { stageEmbeddedRoms(context, it) }

    // Dev-only: -PadamRoms=true builds ship the ROMs under assets/adamem/roms;
    // release builds ship none. Fill-missing so an import is never clobbered.
    private fun stageEmbeddedRoms(context: Context, dir: File) {
        for (rom in AdamRom.entries) {
            val dest = File(dir, rom.fileName)
            if (dest.exists()) continue
            try {
                context.assets.open("adamem/roms/${rom.fileName}").use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {
                // Absent asset (release build, or no -PadamRoms) -- expected.
            }
        }
    }

    data class RomStatus(val rom: AdamRom, val present: Boolean)

    fun status(context: Context): List<RomStatus> {
        val dir = romsDir(context)
        return AdamRom.entries.map { rom ->
            val f = File(dir, rom.fileName)
            RomStatus(rom, f.isFile && f.length() == rom.expectedSize.toLong())
        }
    }

    /** All three are required: the ADAM can't reach EOS/SmartWriter without them. */
    fun hasSystemRoms(context: Context): Boolean = status(context).all { it.present }
}
