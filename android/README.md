# Marathon Recompiled for Android: building

This folder holds the Android (arm64-v8a) port of Marathon Recompiled: the launcher app, the build
script and the patches it applies to third-party code. The port's changes to the game runtime itself
are in `MarathonRecomp/` (see `MarathonRecomp/os/android/`). What the port does and what was
reverse-engineered is described in [docs/ANDROID_PORT.md](../docs/ANDROID_PORT.md).

## You need your own copy of the game

**This repository contains no game files and no code recompiled from the game, and no prebuilt APK
is published.** To build and play, you need your own legally obtained copy of Sonic the Hedgehog
(2006) for Xbox 360, as a disc image (ISO) you made yourself or as an extracted game folder.

- The build reads `default.xex`, `xenon/archives/shader.arc` and `xenon/archives/shader_lt.arc`
  from your copy and recompiles them on your computer. The result stays on your machine.
- On the phone, the app installs the game data from your own copy (see "On the phone" below).
- Do not share the APK you build: it contains code recompiled from your copy of the game.

See [docs/DUMPING-en.md](../docs/DUMPING-en.md) for how to dump the game from your own console and
disc.

## Requirements

- Linux x86_64 (the shader compiler bundled with XenosRecomp is a Linux x64 binary)
- Android SDK with platform 35 and build-tools 35, and **NDK 27.2.12479018**
- JDK 17 or newer, CMake 3.20+, Ninja, Make, Clang, Git, Python 3
- A few GB of free disk space. The native game library is the slow part: it took about 15 minutes
  on a 2-core machine.

Third-party sources are fetched at pinned versions during the build: FFmpeg n7.1.1 (with the
xmaframes decoder patch from `thirdparty/ffmpeg-core`), FreeType 2.13.3, xz 5.6.4, libarchive 3.8.1,
libadrenotools 8fae8ce, the Sonic '06 Mod Manager patch set (Knuxfan24, commit 52ac810) and unlub
(from Sonic '06 Mod Manager, commit 2ee1203). Gradle 8.9 comes through the wrapper.

## Build

```sh
git clone --recurse-submodules https://github.com/yuriaaaaa/MarathonRecomp
cd MarathonRecomp
export ANDROID_HOME=$HOME/Android/Sdk          # your SDK
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018

# Point --game at your extracted game folder (the one with default.xex and xenon/).
android/build.sh --game /path/to/your/extracted/game
```

The APK is written to `android/build/out/MarathonRecomp-android-arm64.apk`.

If your game is an ISO, extract it first with an Xbox 360 ISO extraction tool, or copy the three
files into `MarathonRecompLib/private/` yourself and run `android/build.sh` without `--game`.

`android/build.sh --skip-game` builds everything that doesn't need the game (host tools, FFmpeg,
FreeType, libarchive, libadrenotools and the launcher). Its APK has no game library and can't start
the game; it is only a check that the sources build.

### Signing

The release APK is signed with the Android debug key unless you provide your own:

```sh
export MARATHON_KEYSTORE=/path/to/your.keystore
export MARATHON_STORE_PASSWORD=... MARATHON_KEY_ALIAS=... MARATHON_KEY_PASSWORD=...
```

An APK signed with a different key can't be installed over an earlier build; uninstall the old one
first (your game data, saves and mods in `Internal storage/MarathonRecomp` stay).

## What the build does

1. Initialises the submodules and applies `patches/plume-android.patch` to `thirdparty/plume`
   (Android surface diagnostics and the custom Vulkan driver loader).
2. Builds the host tools (`host/CMakeLists.txt`): XenonRecomp, XenosRecomp, u8extract, file_to_c.
3. Recompiles `default.xex` into `MarathonRecompLib/ppc/` and the shader archives into
   `MarathonRecompLib/shader/shader_cache.cpp` (SPIR-V). Both folders are git-ignored.
4. Builds FFmpeg (XMA audio only), FreeType, libarchive + xz (for `modarchive/`, the mod archive
   extractor) and libadrenotools (custom Turnip drivers) with the NDK.
5. Builds `libmain.so` from the repository's CMake project with the NDK (API 29) and strips it.
6. Copies the Mod Manager patch set into the app's assets and unlub into `android-app/app/libs`,
   then builds the APK with Gradle.

## On the phone

1. Install the APK and open **Marathon Recompiled**. Allow "All files access" when asked.
2. Put your extracted game folder (or the files from your own disc) in
   `Internal storage/MarathonRecomp/import/`, then press Start and use the installer's
   Add Folder / Add Files. Alternatively copy an already extracted game to
   `Internal storage/MarathonRecomp/game/` (the game counts as installed when `game/default.xex` exists).
3. DLC from your own copy can be installed with the launcher's Install DLC button.

Logs are written to `Internal storage/MarathonRecomp/logs/`.

## Layout

| Path | What it is |
| --- | --- |
| `build.sh` | The whole build |
| `android-app/` | Launcher and game activity (Java, no AndroidX), SDL2's Java layer, Gradle project |
| `host/` | Host CMake project for the code-generation tools; `host/xexinfo` is an optional XEX inspection tool |
| `modarchive/` | `libmodarchive.so`, the libarchive-based extractor for downloaded mods |
| `patches/` | `plume-android.patch` and `ffmpeg-xmaframes.patch` |
| `tools/classify_patches.py` | Reverse-engineering helper that sorts Mod Manager patches into code and data writes |
