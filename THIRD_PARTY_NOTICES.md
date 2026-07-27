# Third-Party Notices

FujiNet Go Adam incorporates the following third-party software. See
[COMPLIANCE.md](./COMPLIANCE.md) for how these licenses interact.

## adamcore — clean-room Coleco ADAM / ColecoVision emulator
- Copyright © 2026 Thomas Cherryhomes.
- License: GNU GPL v3 (or later).
- A from-scratch, clean-room implementation written without reference to any
  existing emulator's source code (see the adamcore repository's
  PROVENANCE.md).
- Source: https://github.com/tschak909/adamcore
- Used as the emulator core inside `libadamcore.so`.

## FujiNet firmware (fujinet-pc, ADAM target)
- Copyright © The FujiNet project / contributors.
- License: GNU GPL v3.
- Source: https://github.com/FujiNetWIFI/fujinet-firmware
- Used as the in-process FujiNet runtime (`libfujinet.so`).

## Mbed TLS
- Copyright © The Mbed TLS Contributors.
- License: Apache-2.0.
- Source: https://github.com/Mbed-TLS/mbedtls

## libssh
- Copyright © The libssh contributors.
- License: LGPL-2.1.

## libsmb2
- Copyright © Ronnie Sahlberg and contributors.
- License: LGPL-2.1.

## libnfs
- Copyright © Ronnie Sahlberg and contributors.
- License: LGPL-2.1.

## Expat (libexpat)
- Copyright © The Expat maintainers.
- License: MIT.

## cJSON
- Copyright © Dave Gamble and cJSON contributors.
- License: MIT.

## Coleco ADAM system ROMs
- `EOS.rom`, `OS7.rom`, `WP.rom` are the original Coleco firmware images, now
  in the public domain. They are bundled from `tools/adamcore/roms/` for
  emulation and carry no license of their own. See the ROM note in
  [COMPLIANCE.md](./COMPLIANCE.md).
