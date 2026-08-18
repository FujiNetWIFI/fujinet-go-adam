package online.fujinet.go.adam

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import online.fujinet.go.adam.settings.RomStore

/**
 * Imports a user-picked media file (e.g. a `.dsk`/`.ddp` ADAM image) into the
 * FujiNet SD directory, so it shows up in the FujiNet web UI's SD host and can
 * be mounted from there — disks are served to the ADAM by FujiNet over BoIP, not
 * by the local emulator.
 */
object MediaImport {

    sealed class RomImportResult {
        data class Success(val fileName: String, val crcMatches: Boolean) : RomImportResult()
        data class NotRecognized(val actualSize: Long) : RomImportResult()
        object ReadFailed : RomImportResult()
    }

    /**
     * Pure classification for system-ROM imports, split out so it is
     * unit-testable. CRC32 first (OS7 and EOS are both 8 KB, and each image
     * has a single canonical revision); size + display-name hint accepted
     * with a warning as a fallback for personal re-dumps.
     */
    internal fun classifySystemRom(size: Long, crc: Long, displayName: String?): RomImportResult {
        RomStore.AdamRom.entries.firstOrNull {
            it.expectedSize.toLong() == size && it.expectedCrc == crc
        }?.let { return RomImportResult.Success(it.fileName, crcMatches = true) }

        val name = displayName?.lowercase() ?: ""
        RomStore.AdamRom.entries.firstOrNull {
            it.expectedSize.toLong() == size && name.contains(it.nameHint)
        }?.let { return RomImportResult.Success(it.fileName, crcMatches = false) }

        return RomImportResult.NotRecognized(size)
    }

    /** SAF import for the Coleco system ROMs the first-run gate collects. */
    fun importSystemRom(context: Context, uri: Uri): RomImportResult {
        val romsDir = RomStore.romsDir(context)
        val temp = File(romsDir, ".import-tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return RomImportResult.ReadFailed
        } catch (t: Throwable) {
            Log.e(TAG, "System ROM import failed", t)
            temp.delete()
            return RomImportResult.ReadFailed
        }

        val verdict = classifySystemRom(temp.length(), crc32Of(temp), displayName(context, uri))
        if (verdict !is RomImportResult.Success) {
            temp.delete()
            return verdict
        }
        val dest = File(romsDir, verdict.fileName)
        dest.delete()
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
        Log.i(TAG, "Imported system ROM ${verdict.fileName}")
        return verdict
    }

    private fun crc32Of(file: File): Long {
        val crc = java.util.zip.CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    /** Returns the imported file name on success, or null. */
    fun importToSd(context: Context, uri: Uri, sdPath: String): String? {
        return try {
            val name = displayName(context, uri) ?: "import.dsk"
            val dest = File(sdPath, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Log.i(TAG, "Imported $name into $sdPath")
            name
        } catch (t: Throwable) {
            Log.e(TAG, "Import failed", t)
            null
        }
    }

    /**
     * Copies a ColecoVision/ADAM cartridge image into app-private storage and
     * returns its absolute path (carts are loaded by adamcore itself, not FujiNet).
     */
    fun importCartridge(context: Context, uri: Uri): String? {
        return try {
            val name = displayName(context, uri) ?: "cart.rom"
            val dir = File(context.filesDir, "carts").apply { mkdirs() }
            val dest = File(dir, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Log.i(TAG, "Imported cartridge $name")
            dest.absolutePath
        } catch (t: Throwable) {
            Log.e(TAG, "Cartridge import failed", t)
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private const val TAG = "FujiAdamMedia"
}
