package online.fujinet.go.adam

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * Stages the bundled FujiNet runtime tree and the ADAM ROMs from APK assets into
 * a writable runtime directory the native layer can chdir into and mutate.
 *
 * Assets are produced by tools/fujinet/build-fujinet.sh and
 * tools/adamem/build-adamem-core.sh:
 *   assets/fujinet/{fnconfig.ini, data/, SD/}
 *   assets/adamem/roms/{EOS.rom, OS7.rom, WP.rom}
 */
class RuntimeInstaller(private val context: Context) {

    data class Paths(
        val runtimeRoot: String,
        val configPath: String,
        val sdPath: String,
        val dataPath: String,
        val os7Rom: String,
        val eosRom: String,
        val wpRom: String,
    )

    fun install(force: Boolean = false): Paths {
        val root = File(context.filesDir, "fujinet")
        val romDir = File(context.filesDir, "adamem/roms")

        if (force || !File(root, "fnconfig.ini").exists()) {
            copyAssetDir("fujinet", root)
        }
        if (force || !File(romDir, "EOS.rom").exists()) {
            copyAssetDir("adamem/roms", romDir)
        }

        return Paths(
            runtimeRoot = root.absolutePath,
            configPath = File(root, "fnconfig.ini").absolutePath,
            sdPath = File(root, "SD").absolutePath,
            dataPath = File(root, "data").absolutePath,
            os7Rom = File(romDir, "OS7.rom").absolutePath,
            eosRom = File(romDir, "EOS.rom").absolutePath,
            wpRom = File(romDir, "WP.rom").absolutePath,
        )
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val assets: AssetManager = context.assets
        val entries = assets.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            // It's a file, not a directory.
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (entry in entries) {
            copyAssetDir("$assetPath/$entry", File(dest, entry))
        }
    }
}
