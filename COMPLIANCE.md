# Licensing & Compliance

FujiNet Go Adam is a **mixed-license** project. The shipped app is built from
original glue code plus two third-party emulation/runtime components with
different — and partly restrictive — license terms. Read this before
distributing any build.

## Components and their licenses

### ADAMEm (the emulator core) — NON-COMMERCIAL
- Original ADAMEm © Marcel de Kogel (1996–1999).
- SDL port and later additions © Geoff Oltmans (2006–present).
- Source headers state: *"You are not allowed to distribute this software
  commercially. Please notify me if you make any changes to this file."*

This is **not** an OSI-approved open-source license and is **not** GPL-compatible.
It permits non-commercial use and redistribution of (modified) sources with
notification, but **prohibits commercial distribution**. Because ADAMEm code is
linked into `libadamcore.so`, this restriction propagates to any combined
binary: **do not sell or commercially distribute builds of this app.**

Local modifications staged into the build (the AdamNet Bus-over-IP bridge in
`AdamNet.c`, and the `adam_host.c` Android driver that replaces `AdamemSDL.c`)
are changes to ADAMEm and should be shared back with the upstream authors per
the "notify me if you make changes" request.

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
These are Coleco copyrighted firmware images bundled as runtime assets from the
ADAMEm checkout. They are **not** freely licensed. There is no open-source ADAM
OS replacement equivalent to the Altirra ROMs that FujiNet Go 800 uses for
Atari. Bundling/redistributing them may infringe Coleco's copyright; for public
distribution they should likely be removed and supplied by the end user.

## Net effect

A combined, distributed binary is simultaneously bound by:
- ADAMEm's **non-commercial** restriction (no commercial distribution), and
- FujiNet's **GPLv3** copyleft obligations (offer corresponding source), and
- the ADAM ROM copyright question above.

The original FujiNet Go Adam glue code (build scripts, `adam_host.c`,
`session_runtime.cpp`, `adam_core.cpp`, the Kotlin app) is offered under the
terms in [LICENSE](./LICENSE), **except** where a stricter component license
(ADAMEm) governs the combined work. When the licenses conflict, the most
restrictive applicable terms win.

See [THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md) for attribution details.
