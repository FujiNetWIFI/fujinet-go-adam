#!/usr/bin/env python3
"""Assert that a release build really carries no Coleco system ROMs.

Ported from fujinet-go-intv's tools/jzintv/verify-no-roms.py: take a
distinctive slice out of each copyrighted ROM image and grep every file in
the release output for it. Anything found is a ROM that got shipped despite
the policy, which would make the artifact non-redistributable.

usage: verify-no-roms.py [--require] <dir>...

With --require, the inability to run the check (no scan dirs, no local ROM
images to probe with) is itself a failure -- release builds must not pass on
a machine where the check silently could not run. Without it, those cases
skip with a warning. ADAM_ROMS_SRC overrides where the reference ROM images
are found (same variable the staging script uses).
"""

import os
import sys
from pathlib import Path

PROBE = 64

FORBIDDEN_ROMS = ["OS7.rom", "EOS.rom", "WP.rom"]

ROM_SEARCH_DIRS = [
    *([Path(os.environ["ADAM_ROMS_SRC"])] if os.environ.get("ADAM_ROMS_SRC") else []),
    Path.home() / "Workspace" / "adam-roms",
]


def probe_bytes(data):
    """A slice distinctive enough that finding it means something.

    Reject any chunk dominated by a single byte value and require real
    variety, so a run of 0x00 or 0xFF padding -- which occurs in almost any
    binary of a reasonable size -- can never be mistaken for a match.
    """
    max_run = PROBE // 2
    min_distinct = 12

    for start in range(0, max(1, len(data) - PROBE), PROBE):
        chunk = data[start:start + PROBE]
        if len(chunk) != PROBE:
            continue
        if max(chunk.count(b) for b in set(chunk)) > max_run:
            continue
        if len(set(chunk)) < min_distinct:
            continue
        return chunk
    return None


def find_rom_dir():
    for d in ROM_SEARCH_DIRS:
        if all((d / name).is_file() for name in FORBIDDEN_ROMS):
            return d
    return None


def main():
    args = sys.argv[1:]
    require = "--require" in args
    args = [a for a in args if a != "--require"]
    if not args:
        sys.exit(__doc__)

    scan_dirs = [Path(p) for p in args if Path(p).is_dir()]
    if not scan_dirs:
        if require:
            print("verify-no-roms: FAIL: none of the scan directories exist "
                  f"({args}); with --require the check must actually run")
            return 1
        print("verify-no-roms: no scan directories found; skipping "
              "(release build type's -PadamRoms guard is the primary check)")
        return 0

    romdir = find_rom_dir()
    if romdir is None:
        msg = ("verify-no-roms: no local ROM images found to probe with "
               f"(checked {[str(d) for d in ROM_SEARCH_DIRS]}; "
               "set ADAM_ROMS_SRC to override)")
        if require:
            print(f"{msg} -- FAIL under --require")
            return 1
        print(f"{msg}; skipping -- "
              "this only means we couldn't test, not that the build is clean")
        return 0

    files = []
    for scan_dir in scan_dirs:
        files.extend(f for f in scan_dir.rglob("*") if f.is_file())

    checked = 0
    leaked = []

    for rom_name in FORBIDDEN_ROMS:
        data = (romdir / rom_name).read_bytes()
        chunk = probe_bytes(data)
        if chunk is None:
            continue
        checked += 1
        for f in files:
            try:
                if chunk in f.read_bytes():
                    leaked.append((rom_name, str(f)))
            except OSError:
                continue

    if checked == 0:
        if require:
            print("verify-no-roms: FAIL: no ROM images had a distinctive "
                  "slice to probe with; with --require the check must "
                  "actually run")
            return 1
        print("verify-no-roms: no ROM images had a distinctive slice to "
              "probe with; skipping")
        return 0

    for rom_name, path in leaked:
        print(f"FAIL: {rom_name} bytes found embedded in {path}")

    if leaked:
        return 1

    print(f"verify-no-roms: {checked} ROM images checked across "
          f"{len(files)} files; none embedded, as intended")
    return 0


if __name__ == "__main__":
    sys.exit(main())
