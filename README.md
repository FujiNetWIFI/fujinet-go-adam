# FujiNet Go Adam

Android Coleco ADAM emulation with integrated FujiNet, in the spirit of
[FujiNet Go 800](https://github.com/mozzwald/fujinet-go-800) (Atari 8-bit).

This repository fuses two desktop programs into one cohesive mobile app:

- **ADAMEm** — Marcel de Kogel's Coleco ADAM emulator (Geoff Oltmans' SDL port),
  compiled headless into a native library and driven frame-by-frame into an
  Android `Surface`.
- **fujinet-pc (ADAM target)** — the FujiNet firmware/PC port built as
  `libfujinet.so` and run in-process as a background runtime.

The two halves talk over **AdamNet "Bus over IP" (BoIP) on loopback TCP 65216**:
ADAMEm acts as the AdamNet master and listens; the FujiNet runtime connects in
as the BoIP client (`NetAdamNet`). This is the ADAM analogue of FujiNet Go 800's
NetSIO link. To the user it is transparent — boot the ADAM and the FujiNet drive
is just there.

## Architecture

| Concern | Component |
|---|---|
| Emulator core | ADAMEm (C), run on a worker thread via `adamem_main()` |
| App native lib | `libadamcore.so` (core + Android host + session + JNI) |
| Android host | `app/src/main/cpp/adam_host.c` (replaces ADAMEm's SDL driver) |
| FujiNet runtime | `libfujinet.so` (fujinet-pc ADAM target), dlopen'd in-process |
| Transport | AdamNet BoIP, TCP 65216 (emulator listens, FujiNet connects) |
| UI | Android Surface host (Compose UI: in progress) |

## Sources

The native components are built from local checkouts (not pinned GitHub
tarballs), so unpushed changes are used as-is:

- ADAMEm: `~/Workspace/adamem_sdl` (branch `fujinet-adamnet-bridge`)
- FujiNet: `~/Workspace/fujinet-pc-adam` (branch `adam-pc-bus-over-ip`)

Override with `ADAMEM_SRC=` / `FUJINET_SRC=` when running the build scripts.

## Build requirements

- JDK 21 (Gradle 8.11.1 daemon; JDK 26 is too new)
- Android SDK (compile/target SDK 36) + an installed NDK
- `bash`, `git`, `python3`, `cmake`, `rsync`
- The FujiNet build also clones and cross-compiles Mbed TLS.

`local.properties` records `sdk.dir` and `ndk.dir` (this project uses a
system NDK at `/opt/android-ndk`).

## Build

```bash
# Full (all four ABIs):
./gradlew assembleMantisDebug

# Fast single-ABI dev build:
./gradlew assembleMantisDebug -PadamAbi=arm64-v8a
```

The Gradle build invokes the staging/cross-compile scripts:

- `bash tools/adamem/build-adamem-core.sh` — stages the ADAMEm core sources +
  ROMs into the generated trees.
- `bash tools/fujinet/build-fujinet.sh --all-abis` — builds `libfujinet.so` and
  the runtime assets (forced to `[BOIP] enabled=1 host=127.0.0.1 port=65216`).

## Generated (uncommitted) directories

- `app/src/main/cpp-generated/` — staged ADAMEm sources
- `app/src/main/assets-generated/` — FujiNet runtime + ADAM ROM assets
- `app/src/main/jniLibs-generated/` — `libfujinet.so` per ABI
- `tools/adamem/work/`, `tools/fujinet/work/`

## Licensing

This is a mixed-license project — see [COMPLIANCE.md](./COMPLIANCE.md) and
[THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). Note in particular that
**ADAMEm is distributed under a non-commercial license**, which constrains
distribution of any combined binary.
