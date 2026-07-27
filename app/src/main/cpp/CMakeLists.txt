cmake_minimum_required(VERSION 3.22.1)

project("adamemu")

set(ADAMCORE_GENERATED_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../cpp-generated/adamcore")

if(NOT EXISTS "${ADAMCORE_GENERATED_DIR}/src/machine.c")
    message(FATAL_ERROR "Generated adamcore source tree is missing. Run tools/adamcore/build-adamcore-core.sh before configuring CMake.")
endif()

# adamcore emulator library sources staged from the adamcore repository
# (clean-room GPLv3 core; see COMPLIANCE.md). Mirrors the source list in
# adamcore's own CMakeLists; src/net_win32.c is the Windows counterpart of
# net_posix.c and is never built here.
set(ADAMCORE_SOURCES
        src/z80.c
        src/tms9928a.c
        src/sn76489.c
        src/machine.c
        src/cart.c
        src/adamnet.c
        src/boip.c
        src/net_posix.c
        src/palette.c
        src/debug.c
)
list(TRANSFORM ADAMCORE_SOURCES PREPEND "${ADAMCORE_GENERATED_DIR}/")

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
        ${ADAMCORE_SOURCES}
)

# The Z80 interpreter and per-scanline VDP renderer are hot; at the NDK debug
# default (-O0) they are several times slower, which can drop the emulated
# machine below real time on weaker devices. target_compile_options is emitted
# after CMAKE_C_FLAGS_<CONFIG>, so the trailing -O2 wins over a debug -O0;
# -g (debugSymbolLevel=FULL) still emits the symbols/CFI a tombstone needs.
target_compile_options(adamcore PRIVATE -O2 -Wall -Wextra)

target_include_directories(adamcore PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${ADAMCORE_GENERATED_DIR}/include
        ${ADAMCORE_GENERATED_DIR}/src
)

find_library(log-lib log)
find_library(android-lib android)
find_library(dl-lib dl)

target_link_libraries(
        adamcore
        ${log-lib}
        ${android-lib}
        ${dl-lib}
)
