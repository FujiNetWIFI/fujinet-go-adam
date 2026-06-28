#!/usr/bin/env bash
#
# Stage the ADAMEm emulator core from a local checkout into the Android native
# source tree (app/src/main/cpp-generated/adamem) and copy its system ROMs into
# the generated asset tree (app/src/main/assets-generated/adamem).
#
# Unlike fujinet-go-800's atari800 staging (which downloads a pinned GitHub
# tarball), ADAMEm is sourced from the user's local working copy so unpushed
# changes -- including the AdamNet "Bus over IP" bridge -- are used as-is.
#
# The only source transform is renaming ADAMEm.c's `main()` to `adamem_main()`
# so the Android session runtime can drive the emulator on a worker thread.
# The SDL host (AdamemSDL.c / AdamSDLSound_2.c) is NOT staged; the Android host
# layer in app/src/main/cpp/adamem_android replaces it.

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
PROJECT_ROOT=$(cd -- "${SCRIPT_DIR}/../.." &>/dev/null && pwd)

# Source location (override with ADAMEM_SRC=/path bash build-adamem-core.sh)
SOURCE_DIR="${ADAMEM_SRC:-${HOME}/Workspace/adamem_sdl}"
SOURCE_BRANCH="fujinet-adamnet-bridge"
SOURCE_COMMIT="d129799"

GENERATED_SOURCE_ROOT="${PROJECT_ROOT}/app/src/main/cpp-generated/adamem"
GENERATED_ASSET_ROOT="${PROJECT_ROOT}/app/src/main/assets-generated/adamem"
STAMP_PATH="${GENERATED_SOURCE_ROOT}/.source-info"

# Core C sources to compile into libadamcore.so (SDL host excluded on purpose).
CORE_C_SOURCES=(
    "ADAMEm.c"
    "Coleco.c"
    "Z80.c"
    "Sound.c"
    "AdamSDLSound_2.c"
    "Bitmap.c"
    "AdamNet.c"
    "sms_ntsc.c"
    "IDE/HarddiskIDE.c"
)

# Headers the core needs (AdamemSDL.h / AdamSDLSound_2.h deliberately excluded;
# the Android host provides replacements on the include path).
CORE_HEADERS=(
    "Coleco.h"
    "Z80.h"
    "Z80IO.h"
    "Z80CDx86.h"
    "Z80Codes.h"
    "Z80DAA.h"
    "Common.h"
    "Help.h"
    "AdamNet.h"
    "Bitmap.h"
    "Sound.h"
    "AdamSDLSound_2.h"
    "sms_ntsc.h"
    "sms_ntsc_config.h"
    "sms_ntsc_impl.h"
    "Asm.h"
    "IDE/HarddiskIDE.h"
    "IDE/MsxTypes.h"
)

# System ROMs + generated sound table, bundled as runtime assets.
ASSET_FILES=(
    "EOS.rom"
    "OS7.rom"
    "WP.rom"
)

fail() {
    echo "build-adamem-core.sh: $*" >&2
    exit 1
}

[[ -d "${SOURCE_DIR}" ]] || fail "ADAMEm source not found at ${SOURCE_DIR} (set ADAMEM_SRC)"
[[ -f "${SOURCE_DIR}/ADAMEm.c" ]] || fail "ADAMEm.c missing under ${SOURCE_DIR}"

# Record the resolved commit when the checkout is a git repo (best effort).
if command -v git >/dev/null 2>&1 && git -C "${SOURCE_DIR}" rev-parse --git-dir >/dev/null 2>&1; then
    SOURCE_COMMIT=$(git -C "${SOURCE_DIR}" rev-parse --short HEAD 2>/dev/null || echo "${SOURCE_COMMIT}")
    SOURCE_BRANCH=$(git -C "${SOURCE_DIR}" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "${SOURCE_BRANCH}")
fi

source_fingerprint() {
    local f
    {
        for f in "${CORE_C_SOURCES[@]}" "${CORE_HEADERS[@]}"; do
            [[ -f "${SOURCE_DIR}/${f}" ]] && sha256sum "${SOURCE_DIR}/${f}"
        done
    } | sha256sum | awk '{ print $1 }'
}

source_is_current() {
    local fp="$1"
    [[ -f "${STAMP_PATH}" ]] &&
    grep -q "^source_commit=${SOURCE_COMMIT}$" "${STAMP_PATH}" &&
    grep -q "^source_fingerprint=${fp}$" "${STAMP_PATH}" &&
    [[ -f "${GENERATED_SOURCE_ROOT}/Coleco.c" ]]
}

FP=$(source_fingerprint)
if source_is_current "${FP}"; then
    exit 0
fi

echo "Staging ADAMEm core from ${SOURCE_DIR} (${SOURCE_BRANCH} ${SOURCE_COMMIT})"

rm -rf "${GENERATED_SOURCE_ROOT}"
mkdir -p "${GENERATED_SOURCE_ROOT}/IDE"

# Copy headers verbatim.
for h in "${CORE_HEADERS[@]}"; do
    [[ -f "${SOURCE_DIR}/${h}" ]] || fail "missing header ${h}"
    cp "${SOURCE_DIR}/${h}" "${GENERATED_SOURCE_ROOT}/${h}"
done

# Copy core C sources verbatim, except ADAMEm.c which gets the main() rename.
for c in "${CORE_C_SOURCES[@]}"; do
    [[ -f "${SOURCE_DIR}/${c}" ]] || fail "missing source ${c}"
    if [[ "${c}" == "ADAMEm.c" ]]; then
        python3 - "${SOURCE_DIR}/${c}" "${GENERATED_SOURCE_ROOT}/${c}" <<'PY'
import sys
src, dst = sys.argv[1], sys.argv[2]
text = open(src, encoding="latin-1").read()
needle = (
    "#if defined OSX || defined WINDOWS_HOST\n"
    "int SDL_main (int argc, char *argv[])\n"
    "#else\n"
    "int main (int argc,char *argv[])\n"
    "#endif\n"
)
replacement = "int adamem_main (int argc, char *argv[])\n"
if needle not in text:
    sys.exit("build-adamem-core.sh: could not locate main() entry point to rename in ADAMEm.c")
text = text.replace(needle, replacement, 1)
open(dst, "w", encoding="latin-1").write(text)
PY
    elif [[ "${c}" == "AdamSDLSound_2.c" ]]; then
        # soundData() reads its PSG state from the SDL callback's userdata pointer
        # (Init() sets it to &soundState, which is file-static). Expose an accessor
        # so the Android audio bridge can hand soundData the right pointer.
        cp "${SOURCE_DIR}/${c}" "${GENERATED_SOURCE_ROOT}/${c}"
        printf '\n/* [fujinet-go-adam] expose the file-static PSG state to the audio bridge. */\nvoid *adamsound_get_state(void) { return &soundState; }\n' \
            >> "${GENERATED_SOURCE_ROOT}/${c}"
    else
        cp "${SOURCE_DIR}/${c}" "${GENERATED_SOURCE_ROOT}/${c}"
    fi
done

# Stage system ROMs (+ optional generated sound table) as runtime assets.
rm -rf "${GENERATED_ASSET_ROOT}"
mkdir -p "${GENERATED_ASSET_ROOT}/roms"
for r in "${ASSET_FILES[@]}"; do
    [[ -f "${SOURCE_DIR}/${r}" ]] || fail "missing ROM asset ${r}"
    cp "${SOURCE_DIR}/${r}" "${GENERATED_ASSET_ROOT}/roms/${r}"
done
# ADAMEm's PSG sound table, if the checkout has it (generated by makedata).
[[ -f "${SOURCE_DIR}/adamem.snd" ]] && cp "${SOURCE_DIR}/adamem.snd" "${GENERATED_ASSET_ROOT}/adamem.snd"

cat > "${STAMP_PATH}" <<EOF
source_dir=${SOURCE_DIR}
source_branch=${SOURCE_BRANCH}
source_commit=${SOURCE_COMMIT}
source_fingerprint=${FP}
EOF

echo "ADAMEm core staged:"
echo "  sources: ${GENERATED_SOURCE_ROOT}"
echo "  assets:  ${GENERATED_ASSET_ROOT}"
