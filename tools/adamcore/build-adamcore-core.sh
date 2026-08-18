#!/usr/bin/env bash
# Stage the adamcore emulator sources (and, for dev builds only, the ADAM
# system ROMs) for the app build. adamcore is the clean-room GPLv3
# ADAM/ColecoVision core that replaced the non-commercial ADAMEm; see
# COMPLIANCE.md.
#
# Sources come from a git checkout of the adamcore repository
# (https://github.com/tschak909/adamcore), pinned by SOURCE_COMMIT
# (override the location with ADAMCORE_SRC=/path). Staged trees are
# git-ignored.
#
# The Coleco system ROMs (OS7/EOS/WP) are copyrighted and are NOT part of
# the repository or of release builds -- users import their own dumps at the
# app's first-run ROM gate. --with-roms (wired to -PadamRoms=true, dev debug
# builds only) stages them from ADAM_ROMS_SRC (default ~/Workspace/adam-roms)
# so a local build boots without an import step.
set -euo pipefail

SOURCE_URL="https://github.com/tschak909/adamcore"
SOURCE_BRANCH="main"
SOURCE_COMMIT="d37f05596931859d41d4e4d267531509a3d7f2f3"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="${ADAMCORE_SRC:-$HOME/Workspace/adamcore}"
GEN="$ROOT/app/src/main/cpp-generated/adamcore"
ASSETS="$ROOT/app/src/main/assets-generated/adamem"
ROMS="${ADAM_ROMS_SRC:-$HOME/Workspace/adam-roms}"

WITH_ROMS=0
for arg in "$@"; do
    case "$arg" in
        --with-roms) WITH_ROMS=1 ;;
        *) echo "error: unknown argument: $arg" >&2; exit 1 ;;
    esac
done

if [ ! -f "$SRC/src/machine.c" ]; then
    echo "error: adamcore sources not found at $SRC (set ADAMCORE_SRC=, or" \
         "clone $SOURCE_URL)" >&2
    exit 1
fi

if [ "$SOURCE_COMMIT" != "HEAD" ]; then
    have="$(git -C "$SRC" rev-parse HEAD)"
    if [ "$have" != "$SOURCE_COMMIT" ]; then
        echo "warning: adamcore checkout at $have, pinned $SOURCE_COMMIT" >&2
    fi
fi

echo "Staging adamcore from $SRC"
rm -rf "$GEN"
mkdir -p "$GEN"
cp -r "$SRC/include" "$SRC/src" "$GEN/"
git -C "$SRC" rev-parse HEAD > "$GEN/.source-info" 2>/dev/null || true

if [ "$WITH_ROMS" = "1" ]; then
    echo "Staging ADAM system ROMs (DEV BUILD -- do not distribute)"
    mkdir -p "$ASSETS/roms"
    for r in EOS.rom OS7.rom WP.rom; do
        if [ ! -f "$ROMS/$r" ]; then
            echo "error: missing $ROMS/$r (set ADAM_ROMS_SRC=)" >&2
            exit 1
        fi
        cp "$ROMS/$r" "$ASSETS/roms/$r"
    done
else
    # Distributable tree: remove any ROMs staged by an earlier dev build, or
    # they'd ride along into the release merged assets.
    rm -rf "$ASSETS/roms"
fi

# Drop stale ADAMEm staging from earlier builds, if present.
rm -rf "$ROOT/app/src/main/cpp-generated/adamem"
rm -f "$ASSETS/adamem.snd"

echo "adamcore staging complete"
