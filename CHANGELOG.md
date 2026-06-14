# Changelog

## 0.1.0 (in progress) — bring-up

Initial vertical slice fusing ADAMEm and fujinet-pc (ADAM) into one Android app.

- Build pipeline:
  - `tools/adamem/build-adamem-core.sh` stages the ADAMEm core from the local
    checkout (renames `main()` → `adamem_main()`; excludes the SDL host).
  - `tools/fujinet/build-fujinet.sh` cross-compiles the FujiNet ADAM runtime to
    `libfujinet.so` from the local `fujinet-pc-adam` checkout, with Android
    source transforms (SHARED target, in-process entry wrapper, reboot guard,
    mbedTLS-for-Android, libssh→mbedTLS, CMake 4 policy shim) and forces the
    runtime config to `[BOIP] enabled=1 host=127.0.0.1 port=65216`.
- Native:
  - `libadamcore.so` = ADAMEm core + `adam_host.c` (SDL-free Android host) +
    `session_runtime.cpp` (drives the emulator thread + in-process FujiNet) +
    `adam_core.cpp` (JNI) + the dlopen'd FujiNet wrapper.
  - ADAMEm renders RGB565 into an `ANativeWindow` surface; AdamNet BoIP joins
    the emulator (master/listener) and FujiNet (client) on loopback TCP 65216.
- App:
  - Minimal `SurfaceView` host (`MainActivity`) + `EmulatorNative` JNI +
    `RuntimeInstaller` (stages runtime assets and ADAM ROMs).
- Known gaps: audio (PSG) not yet wired; full Compose UI (touch keyboard,
  joystick/spinner, media picker, settings, FujiNet WebUI) is the next phase.
