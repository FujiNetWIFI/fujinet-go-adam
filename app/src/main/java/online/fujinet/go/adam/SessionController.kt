package online.fujinet.go.adam

import android.content.Context
import android.util.Log
import android.view.Surface
import online.fujinet.go.adam.core.EmulatorNative
import online.fujinet.go.adam.input.AdamController
import java.io.File
import kotlin.concurrent.thread

/**
 * Owns the lifetime of one ADAM session: stages runtime assets, builds the
 * ADAMEm command line from a [SessionConfig], starts the native emulator + the
 * in-process FujiNet runtime (joined over AdamNet BoIP on TCP 65216), and
 * forwards input. Supports restarting to apply settings or load a cartridge.
 */
class SessionController private constructor(private val context: Context) {

    private val settings = SettingsStore(context)

    @Volatile private var started = false
    @Volatile private var paths: RuntimeInstaller.Paths? = null
    private val audio = AudioOutput()
    private val lock = Any()

    /** The FujiNet SD directory (where imported media lands), once staged. */
    val sdPath: String? get() = paths?.sdPath

    val config: SessionConfig get() = settings.config

    fun startIfNeeded() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        thread(name = "adam-bootstrap") { launch(settings.config) }
    }

    /** Persist [config] and restart the session so it takes effect. */
    fun applyConfig(config: SessionConfig) {
        settings.config = config
        restart()
    }

    /** Load a ColecoVision/ADAM cartridge image and restart into it. */
    fun loadCartridge(path: String) {
        settings.config = settings.config.copy(cartPath = path)
        restart()
    }

    /** Clear any loaded cartridge and restart (back to the plain ADAM). */
    fun ejectCartridge() {
        settings.config = settings.config.copy(cartPath = null)
        restart()
    }

    private fun restart() {
        thread(name = "adam-restart") {
            stopInternal()
            synchronized(lock) { started = true }
            launch(settings.config)
        }
    }

    private fun launch(config: SessionConfig) {
        try {
            val p = paths ?: RuntimeInstaller(context.applicationContext).install().also { paths = it }
            EmulatorNative.nativeStartSession(
                p.runtimeRoot, p.configPath, p.sdPath, p.dataPath, buildArgs(p, config),
            )
            audio.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start session", t)
            synchronized(lock) { started = false }
        }
    }

    private fun buildArgs(p: RuntimeInstaller.Paths, config: SessionConfig): Array<String> {
        val args = mutableListOf(
            "-adam",
            "-os7", p.os7Rom,
            "-eos", p.eosRom,
            "-wp", p.wpRom,
            "-fujinet", BOIP_PORT.toString(),
            "-palette", config.palette.toString(),
        )
        config.cartPath?.let { cart ->
            if (File(cart).exists()) {
                args += listOf("-cart", cart)
            }
        }
        return args.toTypedArray()
    }

    fun stop() = stopInternal()

    private fun stopInternal() {
        synchronized(lock) { if (!started) return }
        audio.stop()
        EmulatorNative.nativeStopSession()
        synchronized(lock) { started = false }
    }

    fun attachSurface(surface: Surface) = EmulatorNative.nativeAttachSurface(surface)
    fun detachSurface() = EmulatorNative.nativeDetachSurface()

    fun key(code: Int) = EmulatorNative.nativeInjectKey(code)

    /** ADAM "computer reset" switch (EOS/SmartWriter; ResetColeco(0)). */
    fun resetAdam() = EmulatorNative.nativeRequestReset(0)

    /** ColecoVision reset switch (cartridge side; ResetColeco(1)). */
    fun resetColeco() = EmulatorNative.nativeRequestReset(1)

    fun joystick(
        port: Int,
        up: Boolean = false, down: Boolean = false, left: Boolean = false, right: Boolean = false,
        fireLeft: Boolean = false, fireRight: Boolean = false, keypad: Int = -1,
    ) {
        EmulatorNative.nativeSetJoystick(
            port,
            AdamController.encode(up, down, left, right, fireLeft, fireRight, keypad),
        )
    }

    companion object {
        @Volatile private var instance: SessionController? = null

        /**
         * Process-wide singleton so the activity and the foreground service share
         * one running ADAM session (and a new activity instance after a config
         * change or relaunch reuses it instead of starting a second emulator).
         */
        fun get(context: Context): SessionController =
            instance ?: synchronized(this) {
                instance ?: SessionController(context.applicationContext).also { instance = it }
            }

        private const val TAG = "FujiAdam"
        private const val BOIP_PORT = 65216
    }
}
