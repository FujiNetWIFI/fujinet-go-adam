package com.mantismoonlabs.fujinetgoadam.core

import android.view.Surface

/**
 * JNI bridge to libadamcore.so (the ADAMEm core + Android host + the in-process
 * FujiNet runtime). The native side runs the emulator on its own thread and the
 * FujiNet runtime in-process; the two meet over AdamNet "Bus over IP" on
 * loopback TCP 65216.
 */
object EmulatorNative {
    init {
        // libfujinet.so is dlopen'd by the native layer on demand; we only need
        // to load our own core, which links the JNI + host + ADAMEm core.
        System.loadLibrary("adamcore")
    }

    external fun nativeStartSession(
        runtimeRoot: String,
        configPath: String,
        sdPath: String,
        dataPath: String,
        adamArgs: Array<String>,
    )

    external fun nativeStopSession()
    external fun nativeIsRunning(): Boolean
    external fun nativeAttachSurface(surface: Surface)
    external fun nativeDetachSurface()
    external fun nativeInjectKey(adamChar: Int)
    external fun nativeSetJoystick(port: Int, adamnetState: Int)
    external fun nativeRequestReset(mode: Int)

    /** Fills [out] with mono signed-16 PSG samples (44100 Hz). Returns count. */
    external fun nativeRenderAudio(out: ShortArray): Int
}

