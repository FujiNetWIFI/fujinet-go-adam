cmake_minimum_required(VERSION 3.22.1)

project("adamemu")

set(ADAMEM_GENERATED_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../cpp-generated/adamem")
set(ADAMEM_ANDROID_DIR "${CMAKE_CURRENT_SOURCE_DIR}/adamem_android")

if(NOT EXISTS "${ADAMEM_GENERATED_DIR}/Coleco.c")
    message(FATAL_ERROR "Generated ADAMEm source tree is missing. Run tools/adamem/build-adamem-core.sh before configuring CMake.")
endif()

# ADAMEm core sources staged from the local checkout (SDL host excluded; the
# adam_host.c Android layer replaces it).
set(ADAMEM_CORE_SOURCES
        ADAMEm.c
        Coleco.c
        Z80.c
        Sound.c
        AdamSDLSound_2.c
        Bitmap.c
        AdamNet.c
        sms_ntsc.c
        IDE/HarddiskIDE.c
)
list(TRANSFORM ADAMEM_CORE_SOURCES PREPEND "${ADAMEM_GENERATED_DIR}/")

# ARM tuning (no-ops on x86/x86_64).
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_ANDROID_ARM_MODE ON)
    set(CMAKE_ANDROID_ARM_NEON ON)
endif()

# 16K page-size support for newer NDKs on ARM (lld expects -z,max-page-size).
if((ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
        AND (NOT DEFINED CMAKE_ANDROID_NDK_VERSION OR CMAKE_ANDROID_NDK_VERSION VERSION_GREATER_EQUAL 26))
    add_link_options(-Wl,-z,max-page-size=16384)
endif()

add_library(
        adamcore
        SHARED
        adam_host.c
        session_runtime.cpp
        adam_core.cpp
        fujinet_android.cpp
        ${ADAMEM_CORE_SOURCES}
)

# ADAMEm core compile defines: little-endian host, SDL option-globals path
# (no real SDL -- adamem_android provides shim headers), IDE hard disk support,
# and the SN76489/AY PSG sound emulation (driven into an AudioTrack, not SDL).
set_source_files_properties(
        ${ADAMEM_CORE_SOURCES}
        "${CMAKE_CURRENT_SOURCE_DIR}/adam_host.c"
        PROPERTIES COMPILE_DEFINITIONS "SDL;LSB_FIRST;IDEHD;SOUND;SOUND_PSG"
)

# The ADAMEm sources are pre-C11 K&R-ish C with many benign warnings; keep them
# from failing the build while still surfacing errors.
set_source_files_properties(
        ${ADAMEM_CORE_SOURCES}
        PROPERTIES COMPILE_FLAGS "-w -fcommon"
)

# Optimise the whole emulator library regardless of build type. The Z80
# interpreter AND the per-frame RefreshScreen pixel loops in adam_host.c are hot;
# at the NDK debug default (-O0) they are several times slower, which drops the
# emulated machine below real time on weaker devices. target_compile_options is
# emitted after CMAKE_C_FLAGS_<CONFIG>, so the trailing -O2 wins over a debug -O0;
# -g (debugSymbolLevel=FULL) still emits the symbols/CFI a tombstone needs, so
# crash symbolication is unaffected.
target_compile_options(adamcore PRIVATE -O2)

target_include_directories(adamcore PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${ADAMEM_ANDROID_DIR}
        ${ADAMEM_GENERATED_DIR}
        ${ADAMEM_GENERATED_DIR}/IDE
)

find_library(log-lib log)
find_library(android-lib android)
find_library(z-lib z)
find_library(dl-lib dl)

target_link_libraries(
        adamcore
        ${log-lib}
        ${android-lib}
        ${z-lib}
        ${dl-lib}
)
