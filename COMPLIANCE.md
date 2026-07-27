# Licensing & Compliance

FujiNet Go Adam is a **uniformly GPLv3** application: original glue code plus
two GPLv3 emulation/runtime components. Earlier releases embedded the
non-commercially-licensed ADAMEm emulator; that core has been replaced by
**adamcore**, a clean-room GPLv3 implementation, which removes the
non-commercial restriction from the combined work.

## Components and their licenses

### adamcore (the emulator core) — GPLv3
- Copyright © 2026 Thomas Cherryhomes; GNU GPL v3 or later.
- A from-scratch, clean-room ADAM/ColecoVision core written without reference
  to any existing emulator's source (the AdamEm/AdamEmSDL source was never
  consulted). The adamcore repository's `PROVENANCE.md` records the clean-room
  statement and the complete list of hardware documentation used.
- Staged into the build by `tools/adamcore/build-adamcore-core.sh` and
  compiled into `libadamcore.so` together with the app's native glue.

### FujiNet firmware / fujinet-pc (ADAM target) — GPLv3
- `libfujinet.so` is built from the FujiNet firmware (`fujinet-pc-adam`,
  `FujiNetWIFI/fujinet-firmware`), which is GPLv3.
- The Android build applies source transforms (SHARED library target, an
  in-process entry wrapper, a `reboot()`/`exit()` guard, mbedTLS-for-Android
  wiring). These modifications are GPLv3 and the corresponding modified source
  is reproducible from `tools/fujinet/build-fujinet.sh`.

### Bundled libraries (pulled in by the FujiNet build)
- **Mbed TLS** — Apache-2.0 (or GPL-2.0); cross-compiled from source.
- **libssh** — LGPL-2.1.
- **libsmb2** — LGPL-2.1.
- **libnfs** — LGPL-2.1.
- **expat** — MIT.
- **cJSON** — MIT.

### ADAM system ROMs (`EOS.rom`, `OS7.rom`, `WP.rom`)
These are the original Coleco firmware images, bundled as runtime assets from
`tools/adamcore/roms/`. They are **in the public domain** and so are freely
redistributable with the app. They are still not part of the GPL'd program
itself — they are data the emulator loads at runtime, carrying no license of
their own.

## Net effect

A combined, distributed binary is bound by the **GPLv3** (offer corresponding
source for the app, adamcore, and the FujiNet runtime) plus the LGPL/MIT/
Apache terms of the bundled libraries. There is no longer any non-commercial
restriction, and the bundled ROMs are public domain, so commercial
distribution (including app-store publication) is permitted under the GPLv3's
terms.

The FujiNet Go Adam glue code (build scripts, `adam_host.c`,
`session_runtime.cpp`, `adam_core.cpp`, the Kotlin app) is offered under the
terms in [LICENSE](./LICENSE) (GPLv3).

See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for attribution details.
