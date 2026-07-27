#!/usr/bin/env bash
# Stage the adamcore emulator sources and the ADAM system ROMs for the app
# build. adamcore is the clean-room GPLv3 ADAM/ColecoVision core that
# replaced the non-commercial ADAMEm; see COMPLIANCE.md.
#
# Sources come from a git checkout of the adamcore repository
# (https://github.com/tschak909/adamcore), pinned by SOURCE_COMMIT
# (override the location with ADAMCORE_SRC=/path). Staged trees are
# git-ignored; ROMs are committed under tools/adamcore/roms and staged
# into the APK assets from there.
set -euo pipefail

SOURCE_URL="https://github.com/tschak909/adamcore"
SOURCE_BRANCH="main"
SOURCE_COMMIT="d37f05596931859d41d4e4d267531509a3d7f2f3"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="${ADAMCORE_SRC:-$HOME/Workspace/adamcore}"
GEN="$ROOT/app/src/main/cpp-generated/adamcore"
ASSETS="$ROOT/app/src/main/assets-generated/adamem"
ROMS="$ROOT/tools/adamcore/roms"

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

echo "Staging ADAM system ROMs"
mkdir -p "$ASSETS/roms"
for r in EOS.rom OS7.rom WP.rom; do
    if [ ! -f "$ROMS/$r" ]; then
        echo "error: missing $ROMS/$r" >&2
        exit 1
    fi
    cp "$ROMS/$r" "$ASSETS/roms/$r"
done

# Drop stale ADAMEm staging from earlier builds, if present.
rm -rf "$ROOT/app/src/main/cpp-generated/adamem"
rm -f "$ASSETS/adamem.snd"

echo "adamcore staging complete"
