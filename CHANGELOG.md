# Changelog

## 0.1.0 (in progress)

First working release fusing the ADAMEm Coleco ADAM emulator and the FujiNet
firmware (ADAM PC target) into one Android app, in the spirit of FujiNet Go 800.
Verified end-to-end on an x86_64 emulator: the FujiNet CONFIG menu loads and
runs on the emulated ADAM, served by the in-process FujiNet over AdamNet BoIP.

### Build pipeline
- `tools/adamem/build-adamem-core.sh` stages the ADAMEm core from the local
  checkout (renames `main()` → `adamem_main()`, exposes the PSG state accessor,
  excludes the SDL host).
- `tools/fujinet/build-fujinet.sh` cross-compiles the FujiNet ADAM runtime to
  `libfujinet.so` from the local `fujinet-pc-adam` checkout, with Android source
  transforms (SHARED target, in-process entry wrapper, `reboot()`/`exit()`
  guard, mbedTLS-for-Android, libssh→mbedTLS, CMake 4 policy shim, BoIP
  response-deadline fix) and forces `[BOIP] host=127.0.0.1 port=65216`. The
  transforms are idempotent, so they also work against a checkout that already
  carries an upstreamed fix.

### Native (`libadamcore.so`)
- `adam_host.c` — SDL-free Android host replacing AdamemSDL.c: RGB565 → an
  `ANativeWindow` surface, ColecoVision controller + ADAM keyboard input, PSG
  audio via AdamSDLSound_2 (with SDL shim headers, no real SDL).
- `session_runtime.cpp` drives the ADAMEm thread + the dlopen'd in-process
  FujiNet runtime, joined over AdamNet "Bus over IP" on loopback TCP 65216
  (ADAMEm is the master/listener, FujiNet the client).
- `adam_core.cpp` JNI bridge.

### App (Jetpack Compose)
- Emulator surface, on-screen ADAM keyboard + ColecoVision controller, PSG audio
  via AudioTrack.
- FujiNet WebUI (WebView → loopback), disk import into the FujiNet SD tree,
  ColecoVision cartridge loading, and a settings dialog (palette) with session
  restart.
- Adaptive launcher icon; package `online.fujinet.go.adam`.

### Fixes found during on-device verification
- PSG audio callback SIGSEGV (the generator's state pointer was null).
- AdamNet BoIP DDP stall: device responses were dropped by the 300µs hardware
  timing window over BoIP, stalling the CONFIG load with a black screen. The PC
  build now always sends responses (block reads ~800ms → ~22ms). Also ported
  upstream into `fujinet-pc-adam`.

### Known gaps
- ADAM system ROMs are bundled (Coleco copyright — see COMPLIANCE.md).
- Spinner/second-controller, SmartKeys, and an about/diagnostics screen are not
  yet wired.
