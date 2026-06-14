package online.fujinet.go.adam

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/**
 * Imports a user-picked media file (e.g. a `.dsk`/`.ddp` ADAM image) into the
 * FujiNet SD directory, so it shows up in the FujiNet web UI's SD host and can
 * be mounted from there — disks are served to the ADAM by FujiNet over BoIP, not
 * by the local emulator.
 */
object MediaImport {

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
     * returns its absolute path (carts are loaded by ADAMEm itself, not FujiNet).
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
