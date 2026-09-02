# Changelog

## 1.1.0

### Changed
- **FujiNet is far quicker to talk to.** Listing a directory in CONFIG used to
  stop between every entry, and an EOS cold boot paused for several seconds
  right after the bus reset. Neither happens on a real ADAM, and neither was a
  fault in FujiNet -- the same firmware is fast under ADAMEm talking to
  fujinet-pc. Both were in how the emulator drove the AdamNet link.
- **Directory entries arrive about five times faster** (351 ms -> 67 ms each).
  Every FujiNet command was preceded by a probe asking the device whether it
  had a leftover response to discard. The device answers that probe in a way
  the probe could not interpret, so it always ran to its full timeout, adding a
  quarter of a second to every command. The probe is gone -- real hardware does
  not send one -- and replies are now collected as soon as they arrive rather
  than on the next emulated frame.
- **Cold boot no longer stalls** (4222 ms -> 383 ms for the bus roll-call). The
  ADAM asks every bus address in turn who is there, and the addresses FujiNet
  does not answer for were each given the long grace period meant for a device
  that is present but still starting up. Once FujiNet has shown it is
  responding, silence from an address is taken to mean nothing is attached.

## 1.0.5

### Changed
- **The bundled FujiNet runtime is rebuilt from upstream `master`
  (`13465cdd`).** The previous build came from `force-mbedtls-3`, a branch that
  was never merged, so the runtime had been standing still while upstream moved
  on. The Mbed TLS 3.x requirement it was branched for has landed, and this
  picks up the device-chain rework and the Drivewire cassette media format
  alongside it.
- **Still missing the AdamNet device-spec padding fix.** That fix has never
  landed on the firmware's mainline, so moving to `master` does not restore it:
  `N:` opens on AdamNet can still see the padding that follows the device spec,
  and a `GCAL` day view in particular may come back empty. It returns once the
  AdamNet work is merged upstream.

## 1.0.4

### Changed
- **The bundled FujiNet runtime no longer builds against Mbed TLS 4.x.** That
  release removed the low-level crypto headers the runtime's TLS backend needs
  and split the rest into a separate library, so a build machine whose system
  Mbed TLS had moved to 4.x could produce a runtime with broken TLS instead of
  failing outright. The build now requires a 3.x install, or stops and says so.

### Added
- Gmail message composition, and calendar events that can be created and
  edited rather than only read.

### Known issue
- **AdamNet N: opens can again see the padding that follows the device
  spec.** The fix released in 1.0.2 has not yet landed on the firmware's
  mainline, and this build tracks mainline. A protocol that reads the device
  spec byte for byte -- GCAL in particular -- may fetch an empty result. The
  fix returns once the AdamNet work is merged upstream.

## 1.0.3

### Added
- **Every on-screen control now answers with a tactile blip.** Until now only
  the emulated machine's own controls -- the ADAM keyboard, the ColecoVision
  keypad, the SmartKeys bar, the stick and the fire buttons -- pulsed under a
  fingertip, while the app's own controls said nothing back. The toolbar
  buttons, everything in Settings (both haptics switches, the palette,
  expansion, joystick-mode, button-swap and keypad-reverse pickers, the
  ColecoVision reset button, Apply and Cancel) and the first-run ROM-import
  gate now give the same short confirmation.
- **A third haptics switch, "Interface haptics", in Settings.** It governs the
  app's own controls only, so the toolbar and the dialogs can be silenced
  without giving up the pulse under the keyboard and the controller, or the
  other way round. It is on by default, and its pulse is deliberately lighter
  than a keypress: tapping Settings is incidental, pressing a key is the point.

## 1.0.2

FujiNet runtime fix: the bundled firmware moves from `a4b35d18` to
`a7a4fd3a` (fujinet-firmware).

### Fixed
- An AdamNet `N:` open no longer carries the padding that follows the device
  spec. The client library copies the URL into a fixed 256-byte field and
  sends the whole block, and the runtime read that payload by length, so the
  padding rode along inside the string — invisible in the logs, but not to a
  protocol that reads the device spec byte for byte. `N:GCAL:///DAY/<date>`
  was parsed as a calendar named after its own date instead of as a DAY view,
  so day, week and month listings came back empty. The runtime now stops at
  the end of the string, as the other buses already do.

## 1.0.1

FujiNet runtime refresh: the bundled firmware moves from `3e888b1c` to
`a4b35d18` (fujinet-firmware).

### Changed
- The web UI gains an optional global password and an SD card file manager,
  N: gains the CALENDAR/GCAL/ICAL protocols, the CONFIG slot-assignment
  screen shows the right filenames, opening an AdamNet network connection no
  longer crashes on a null device, and a zero-length app key write clears the
  key instead of failing.
- `tools/fujinet/build-fujinet.sh` follows the firmware's mbedTLS discovery
  rewrite (it now probes for `mbedtls/sha256.h`, absent in Mbed TLS 4.x) and
  links the new `gumbo_fn` target.

## 1.0.0

Google Play readiness release.

### Changed
- **Coleco system ROMs (OS7/EOS/WP) are no longer bundled or tracked in the
  repository.** On first run a ROM gate imports the user's own dumps
  (classified by CRC32 with a file-name fallback); the session refuses to
  start without them instead of the old silent black screen. Dev builds may
  stage local ROMs via `-PadamRoms=true`, which release builds refuse; a
  `verifyNoEmbeddedRoms` byte-probe additionally guards
  `assembleRelease`/`bundleRelease`. COMPLIANCE/NOTICES no longer describe
  the images as public domain.
- `RuntimeInstaller` stages ROM assets per-file (fill-missing) and no longer
  crashes the launch when the asset dir is absent (ROM-free release builds).
- Cleartext HTTP scoped to the loopback FujiNet web UI via a network
  security config; release builds carry full native debug symbols.

### Added
- Release signing via `keystore.properties`, `tools/release-play.sh`
  (signed AAB), privacy policy under `docs/`, and a Play submission
  checklist.

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
- ADAM system ROMs are bundled (public domain — see COMPLIANCE.md).
- Spinner/second-controller, SmartKeys, and an about/diagnostics screen are not
  yet wired.
