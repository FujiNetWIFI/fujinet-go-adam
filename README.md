# FujiNet Go Adam

Android Coleco ADAM emulation with integrated FujiNet, in the spirit of
[FujiNet Go 800](https://github.com/mozzwald/fujinet-go-800) (Atari 8-bit).

This repository fuses two codebases into one cohesive mobile app:

- **[adamcore](https://github.com/tschak909/adamcore)** — a clean-room GPLv3
  Coleco ADAM / ColecoVision emulator core (C99, no SDL), compiled into the
  app's native library and driven frame-by-frame into an Android `Surface`.
- **fujinet-pc (ADAM target)** — the FujiNet firmware/PC port built as
  `libfujinet.so` and run in-process as a background runtime.

The two halves talk over **AdamNet "Bus over IP" (BoIP) on loopback TCP 65216**:
adamcore acts as the AdamNet master and listens; the FujiNet runtime connects in
as the BoIP client (`NetAdamNet`). This is the ADAM analogue of FujiNet Go 800's
NetSIO link. To the user it is transparent — boot the ADAM and the FujiNet drive
is just there.

## Architecture

| Concern | Component |
|---|---|
| Emulator core | adamcore (C99), run on a worker thread via `adamhost_main()` |
| App native lib | `libadamcore.so` (core + Android host + session + JNI) |
| Android host | `app/src/main/cpp/adam_host.c` (vsync-paced frame loop, input, audio) |
| FujiNet runtime | `libfujinet.so` (fujinet-pc ADAM target), dlopen'd in-process |
| Transport | AdamNet BoIP, TCP 65216 (emulator listens, FujiNet connects) |
| UI | Android Surface host (Compose UI: in progress) |

## Sources

The native components are built from local checkouts (not pinned GitHub
tarballs), so unpushed changes are used as-is:

- adamcore: `~/Workspace/adamcore` (clean-room GPLv3 core, upstream
  [tschak909/adamcore](https://github.com/tschak909/adamcore)). The staging
  script records the expected revision in `SOURCE_COMMIT` and warns — but does
  not fail — when the checkout sits elsewhere.
- FujiNet: `~/Workspace/fujinet-pc-adam` (branch `adam-pc-bus-over-ip`)

Override with `ADAMCORE_SRC=` / `FUJINET_SRC=` when running the build scripts.

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
./gradlew assembleDebug

# Fast single-ABI dev build:
./gradlew assembleDebug -PadamAbi=arm64-v8a

# Unit tests:
./gradlew testDebugUnitTest
```

The application id / package is `online.fujinet.go.adam`.

The Gradle build invokes the staging/cross-compile scripts:

- `bash tools/adamcore/build-adamcore-core.sh` — stages the adamcore sources +
  ROMs into the generated trees.
- `bash tools/fujinet/build-fujinet.sh --all-abis` — builds `libfujinet.so` and
  the runtime assets (forced to `[BOIP] enabled=1 host=127.0.0.1 port=65216`).

## Generated (uncommitted) directories

- `app/src/main/cpp-generated/` — staged adamcore sources
- `app/src/main/assets-generated/` — FujiNet runtime + ADAM ROM assets
- `app/src/main/jniLibs-generated/` — `libfujinet.so` per ABI
- `tools/fujinet/work/`

## Licensing

This is a mixed-license project — see [COMPLIANCE.md](./COMPLIANCE.md) and
[THIRD_PARTY_NOTICES.md](./THIRD_PARTY_NOTICES.md). The app, adamcore and the
FujiNet runtime are all GPLv3. The Coleco system ROMs (EOS/OS7/SmartWriter)
are **copyrighted and are not bundled in release builds** — users import
their own dumps at the first-run ROM gate (dev builds may bundle them via
`-PadamRoms=true`, which release builds refuse). The FujiNet runtime pulls
in LGPL/MIT/Apache libraries.
