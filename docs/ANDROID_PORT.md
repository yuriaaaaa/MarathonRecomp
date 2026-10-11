# Marathon Recompiled (Sonic '06) for Android: port notes

An Android (arm64-v8a) port of [Marathon Recompiled](https://github.com/sonicnext-dev/MarathonRecomp). Latest build: **0.6.0** (package `com.marathonrecomp.android`). Confirmed on the device: title screen, stages (Wave Ocean completed), cutscenes, music, touch controls, mods, patches, GameBanana installs, Turnip and the FPS counter.

## What was implemented

- **Android platform layer:** SDL2 Vulkan window (the default EGL surface caused the 0.1.0 crash), RGBA8 back buffer, game thread with SDL event pumping fixed (`g_mainThreadId` was captured on the Java UI thread), game in its own process, back-button exit dialog.
- **Mod Manager features in the launcher, modelled on [Sonic '06 Mod Manager](https://github.com/hyperbx/SonicNextModManager).** The Windows manager itself is .NET/WPF and can't run on Android, so its behaviour was re-implemented in the launcher:
  - **Mods:** `mod.ini` / `mod.json` mods, merged `+` archives, read-only and custom files, load order.
  - **Patches, tweaks and codes:** the 150 official `.mlua` patches from Knuxfan24/Sonic-06-Mod-Manager-Patches are bundled, with a Lua 5.0 decompile step (unlub) as the manager does; tweaks such as Tokyo Game Show camera and field of view; "Check for updates" fetches the latest patch set from GitHub. Code patches that change game code (68 of them) are not supported yet, since the game code is compiled ahead of time.
  - **GameBanana browser with 1-click install:** browse and search the Sonic '06 page, download straight into the mods folder, extract zip / 7z / RAR (libarchive), and handle `sonic06mm:` 1-click links and shared GameBanana links.
  - **DLC:** an Install DLC button in the launcher runs the game's installer in DLC mode (`--install-dlc`). Not yet confirmed on the device.
- **Touch controls** in an Xbox layout (SDL virtual joystick) with opacity, scale, haptics and rumble settings.
- **GPU drivers and FPS counter:** custom driver import through libadrenotools (confirmed with Turnip Gen8 V37 on the Adreno 830) and an on-screen FPS counter.
- **Known open issues:** resuming after switching apps (swapchain recreation), the 68 code patches, and Legacy of Solaris / Khronos (needs a native Xbox 360 DLL, `Core.xex`).

## Status of the source code

The port's source is in this repository. It was recovered from the build session that produced the 0.6.0 APK and checked against that APK (the launcher's classes and strings, and every string the port adds to the native library).

- **Runtime changes:** `MarathonRecomp/os/android/` plus the Android parts of `MarathonRecomp/`, `MarathonRecompLib/CMakeLists.txt` and `thirdparty/CMakeLists.txt`.
- **Launcher app:** `android/android-app/` (mods, Mod Manager patches, tweaks and codes, GameBanana browser with 1-click install, DLC install, touch controls, GPU drivers, FPS counter).
- **Build:** `android/build.sh` does every step listed in the build log below; [android/README.md](../android/README.md) explains how to run it.
- **Third-party changes:** `android/patches/plume-android.patch` (applied to the plume submodule) and `android/patches/ffmpeg-xmaframes.patch`.
- **Reverse-engineering helpers:** `android/host/xexinfo` and `android/tools/classify_patches.py`.

No game files, no code recompiled from the game and no prebuilt APK are included. To build, you need your own legally obtained copy of the game, as an ISO or an extracted folder.

Reference test device: Samsung Galaxy S25 (Snapdragon 8 Elite, Adreno 830). Results on other devices have not been tested.

---

## Build log: Marathon Recompiled (Sonic '06) Android port: build notes (8 Oct 2026)

First Android build of [Marathon Recompiled](https://github.com/sonicnext-dev/MarathonRecomp) (the PC static recompilation of Sonic the Hedgehog 2006), built on the same build machine as the WET port (2 vCPU, 8 GB RAM).

## Current build: 0.6.0 (code 13)
- `Marathon-arm64-release.apk`, package `com.marathonrecomp.android`, arm64-v8a only, minSdk 29, targetSdk 35
- APK SHA-256 `37ec8d50a9dea289097e04684853edb51965399723ccf9ca633f20976514b445`; zip SHA-256 `401df21ff382d843d0ba4c5e9461e0eb88abf9f6e6db247ac4afe20d029bea61`
- Adds the FPS counter and GPU driver import (see "0.6.0: FPS counter and GPU drivers"). New `libmain.so` plus libadrenotools and its four hooks.
- **0.6.0 confirmed on the device (17:10, 8 Oct):** "turnip works so does the fps counter". The user imported **Turnip Gen8 V37 by StevenMXZ** (Vulkan 1.4.359, "Turnip for a8xx", `libvulkan_freedreno.so`). Settings showed "Last start: Turnip Gen8 V37 (Adreno (TM) 830v1)" in green, and the title screen showed "60 FPS · 16.7 ms". Log 17:06:41 has "GPU driver: using …/files/drivers/1791475592026-turnip-gen8-v37-7/libvulkan_freedreno.so through AdrenoTools" and "GPU: Adreno (TM) 830v1 (driver 6802063)": the Mesa version 26.2.99 encoded, against the Qualcomm driver's 80320001. It ran 7,590 lines and ended with "Saving configuration...".
- **0.5.1 confirmed (16:52, 8 Oct):** Check for updates said "up to date (21 Nov 2025)". Patches work in game: Disable HUD and TGS 2005 Sonic are visible in the screenshot. Log 16:48:52 lists 4 data-write patches ("5 data writes applied") and 7 merged files from the patches, with no missing files.
- **0.5.0 confirmed on the device (16:38, 8 Oct):** "Everything works perfectly". Mods download, go into the right folder, the zips are extracted and deleted. Screenshots show Newest with thumbnails, the E3 HUD page with its Khronos warning and 3 files, and the mods folder holding P-06 Title Screen Logo next to Khronos and LoS. Log 16:35:35 shows 2 active mods and a merged `sprite.arc`, and loads `sprite/title/title_logo.dds` with "CSD modifier(s) redirected: sprite/title/title".
- **0.4.2 confirmed on the device (16:06, 8 Oct):** "Everything this perfect". The camera and controls are responsive, and the user finished Wave Ocean, including the high-speed section, with no glitches. Logs 15:56:48 and 15:56:57 (36,769 lines) show "Detected controller: Touch Controls" and end with "Saving configuration...", so Exit leaves through `App::Exit`. No crashes or swapchain errors.
- **Still open:** resuming after switching apps (the swapchain can't be recreated), the 68 code patches (the per-function variant idea), and Legacy of Solaris / Khronos (Core.xex).
- Earlier builds:
  - 0.5.1 (code 12, APK `2fa954ce…`): patch updates, confirmed
  - 0.5.0 (code 11, APK `02773913…`): GameBanana mod browser, confirmed working
  - 0.4.2 (code 10, APK `2a3241bf…`): back-button exit dialog; game in its own process
  - 0.4.1 (code 9, APK `fa793c75…`): **touch controls work** (user, 15:51). Log 15:46 shows "Detected controller: Touch Controls".
  - 0.4.0 (code 8, APK `f239dbe2…`): touch controls appeared and vibrated, but the game got no input from them (user, 15:36)
  - 0.3.0 (code 7, APK `62ab2327…`): Patches screen. The user said "all works well now" at 15:21 (8 Oct).
  - 0.2.2 (code 6, APK `1ecb5e07…`): names missing files instead of crashing (see "0.2.2" below)
  - 0.2.1 (code 5, APK `1e9a5566…`): mod fixes for Legacy of Solaris (see "Mods in 0.2.1"). **Mods work on the device:** LoS's title screen and title movie show; merged, read-only and custom files are served (log of 8 Oct 13:54).
  - 0.2.0 (code 4, APK `9663360e…`): added mods and codes (see "Mods in 0.2.0")
  - 0.1.2 (code 3, APK `9e286955…`): **works on the device.** Title screen, the first stage (rank A), cutscenes with subtitles, music and gameplay are all correct (user report, 8 Oct 13:11).
  - 0.1.0 (code 1, APK `0c83c9e7…`): crashed on Start
  - 0.1.1 (code 2, APK `c1e03120…`): the installer ran (rendered on the device with Vulkan) and installed the game, then crashed before the SEGA logo
- 0.1.2 plays correctly on the S25. 0.2.0 added mods; mods haven't been tested on the device yet. The user is about to test with Legacy of Solaris.

## Fixes in 0.1.1
- **0.1.0 crash on Start** (log `marathon_20261008_123809`): `vkCreateAndroidSurfaceKHR` returned `0xC46535FF` (`VK_ERROR_NATIVE_WINDOW_IN_USE_KHR`); plume then dereferenced the null surface inside `libvulkan.so` (SIGSEGV at 0x8, from `Video::CreateHostDevice`).
  - Cause: SDL2 on Android adds `SDL_WINDOW_OPENGL` by default (`SDL_DefaultGraphicsBackends`) and attaches an EGL surface to the ANativeWindow.
  - Fix: `GameWindow::GetWindowFlags` adds `SDL_WINDOW_VULKAN` on Android.
- **Back buffer format:** Android Vulkan swapchains offer RGBA8 but not BGRA8, so `BACKBUFFER_FORMAT` in `gpu/video.cpp` is `R8G8B8A8_UNORM` on Android. This applies to the swapchain, the intermediary back buffer and the guest back buffer.
- **Diagnostics:**
  - The log gets a `GPU:` line with the device name, driver and capabilities.
  - plume lists the offered surface formats if none match (`plume-android.patch`).
- On the phone, the user put the extracted game at `MarathonRecomp/import/game`. Add Folder picks it up, or the folder can be moved to `MarathonRecomp/game`.

## Inputs
- MarathonRecomp `f74495e` (main, 6 Oct 2026; the user's uploaded zip matches this tree), submodules shallow-cloned.
- The user's `default.xex`, `shader.arc`, `shader_lt.arc` (from their backup). Their XXH3-64 hashes match `install/hashes/game.cpp`: `default.xex` is accepted variant 3 of 3, both archives variant 1 of 1. No title update is needed.
- Game files stay in the ignored `MarathonRecompLib/private/`; generated code is in the ignored `MarathonRecompLib/ppc/`. Nothing game-derived is in the port patch.

## Layout on the build box
- `<build-root>/sonic06/MarathonRecomp`: clone, branch `android` (uncommitted changes; full diff in `marathon-android.patch`)
- `<build-root>/sonic06/host`: host CMake project that builds only the tools (XenonRecomp, XenosRecomp, u8extract, file_to_c)
- `<build-root>/sonic06/deps`: FFmpeg 7.1.1 and FreeType 2.13.3 sources and Android builds
- `<build-root>/sonic06/build-android`: NDK build tree
- `<build-root>/sonic06/android-app`: Gradle app (Java only, no AndroidX)
- `<build-root>/sonic06/symbols/libmain.so.unstripped`: for resolving crash backtraces

## Build steps
1. **Host tools:** `cmake -S host -B host/build -G Ninja -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ -DMARATHON_RECOMP_OPTIMIZE_TOOLS=OFF`, then `ninja XenonRecomp XenosRecomp u8extract file_to_c`. LTO must stay off: clang's LTO archives break the system `ld`.
2. **CPU code:** in `MarathonRecompLib`, `XenonRecomp config/Marathon.toml ../tools/XenonRecomp/XenonUtils/ppc_context.h`. Takes 7 s and writes 150 files (169 MB) to `ppc/`.
3. **Shaders:** `u8extract` both archives into `private/shader`, then run XenosRecomp with `LD_LIBRARY_PATH=tools/XenosRecomp/thirdparty/dxc-bin/lib/x64`. Takes 20 s; output is `shader/shader_cache.cpp` (SPIR-V only).
4. **FFmpeg:** n7.1.1, plus the `0008-xmaframes.patch` extracted from `thirdparty/ffmpeg-core/ffmpeg.patch` (applies cleanly; the patched `codec_id.h` and `avcodec.h` are identical to ffmpeg-core's headers).
   - Configure: `--target-os=android --arch=aarch64 --enable-cross-compile` with the NDK clang for API 26, `--enable-pic --disable-programs --enable-static --disable-shared --disable-autodetect --disable-everything --enable-decoder=xmaframes`.
   - The game uses FFmpeg only for XMA audio (`AV_CODEC_ID_XMAFRAMES`).
5. **FreeType** (for msdf-atlas-gen): CMake with the NDK toolchain, static, with zlib, bzip2, png, harfbuzz and brotli all off.
6. **Android native build:**
   ```
   VCPKG_ROOT=<repo>/thirdparty/vcpkg cmake -S MarathonRecomp -B build-android -G Ninja \
     -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a \
     -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release \
     -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DMARATHON_ANDROID_FFMPEG=<deps>/ffmpeg-android \
     -DMARATHON_HOST_FILE_TO_C=<host>/build/tools/file_to_c/file_to_c \
     -DFREETYPE_LIBRARY=... -DFREETYPE_INCLUDE_DIR_ft2build=... -DFREETYPE_INCLUDE_DIR_freetype2=... \
     -DMARATHON_RECOMP_OPTIMIZE_TOOLS=OFF -DSDL_TEST=OFF
   ninja -j2 MarathonRecomp
   ```
   - `VCPKG_ROOT` must also be set for ninja's automatic re-configure.
   - API 29 gives native ELF TLS; API < 29 would emulate the game's `thread_local` context.
   - The full build takes about 15 min; each of the 146 generated files compiles in about 3–4 s at -O3.
7. **Library:** strip `libmain.so` (254 MB → 62.6 MB) into `android-app/app/src/main/jniLibs/arm64-v8a/`, next to the NDK's `libc++_shared.so`.
8. **APK:** in `android-app`, `MARATHON_VERSION_CODE=1 MARATHON_VERSION_NAME=0.1.0 /opt/gradle/bin/gradle --no-parallel --offline :app:assembleRelease`.
   - Lint vital is off: its artifact isn't in the offline cache.
   - `useLegacyPackaging = true`, so the APK is 28.8 MB.

## Fixes in 0.1.2
- **Crash before the SEGA logo** (logs `marathon_20261008_124634` and `_124827`): `invalid pthread_t ... passed to pthread_join` → abort.
  - Stack: `NtClose` → `DestroyKernelObject` → `~GuestThreadHandle` → `pthread_join`.
  - With `USE_PTHREAD` (macOS, and now Android), `Wait()` and the destructor both call `pthread_join`. A second join is ESRCH on macOS but fatal in bionic.
- Fix (`cpu/guest_thread.h/.cpp`):
  - `JoinOnce()` with a mutex and a `joined` flag.
  - A timeout of 0 returns `STATUS_TIMEOUT` while the thread hasn't been joined; timed waits poll `isFinished`. Both now behave like the `std::thread` path.
  - A failed `pthread_create` marks the handle as joined.
- Device info from the 0.1.1 log: `Adreno (TM) 830`, driver `80320001`. Descriptor indexing, buffer device address, scalar block layout and triangle fans are supported; present wait is not; max texture size is 16384. The window is 2340×1080, and an Xbox Series X controller was detected.

## Mods in 0.2.0
- **The Windows Sonic '06 Mod Manager can't run on Android.** It is .NET 8 WPF, and it installs mods by editing the game folder: replacements, merging `+` archives, Lua scripts through the Marathon library, and XEX code patches. The `next` branch the user sent uses `mod.json`; their `Release.zip` is a single-file build from Feb 2023.
- **Native mod loader:** Marathon already has a runtime mod loader inherited from Unleashed: `mod/mod_loader.cpp` reads `cpkredir.ini` → `ModsDB.ini`, and `FileSystem::ResolvePath` serves mod files in place of game files. `ModLoader::Init()` is commented out upstream; on Android it is called.
  - On Android, `ResolvePath` uses a lower-case index of each mod's files, built at `Init`, because mods made on Windows don't always match the game's casing and Android storage is case-sensitive.
  - The redirect log line now reads `Mod Loader: <path> -> <file>`. Upstream's `LOGF_UTILITY("Mod Loader", ...)` passed its arguments in the wrong order and only printed "Mod Loader"; the game's own `[ResolvePath] Game` lines have the same bug.
  - The native loader can't merge Sonic '06 `.arc` archives. Its merge check looks for `.ar`/`.arl` endings from Unleashed, so it would serve partial merge archives whole.
- **Launcher (`ModsActivity`, `ModManager`, `U8Archive`):**
  - Mods are folders in `MarathonRecomp/mods` containing `mod.ini` (classic `[Details]`/`[Filesystem]`: Title, Author, Version, Description, Merge, Read-only, Platform, Save) or `mod.json` (Title, Author, Version, Description, Platform, Patches). A folder may be nested one level, as zips often are.
  - The order and enabled state live in `mods/mods.txt` (`1 <folder>`, `0 <folder>`, `code <Name>`); the top of the list wins.
  - On Start, `ModManager.apply()` decides per game path what each mod does, lowest priority first:
    - REPLACE: an archive in a non-merging mod, or one on its read-only list
    - MERGE: an archive in a classic mod with Merge=True, or a `+name.arc` in a `mod.json` mod
    - COPY: a `#name` file in a `mod.json` mod
  - Paths whose final result involves a merge or a rename are built into `mods/.marathon/merged` (the game file plus merges, or the last replacement plus merges). Rebuilds are skipped when `merged.manifest` (path, size and mtime of every input) is unchanged; stale outputs are deleted.
  - It writes:
    - `mods/.marathon/ModsDB.ini`: `merged` first, then one HMM-style `modN.ini` per enabled mod (`[Main] IncludeDir0="<absolute mod folder>"`), plus `[Codes]`
    - `user/cpkredir.ini`: `Enabled`, `LogType="console"`, `ModsDbIni`
  - With nothing enabled, `cpkredir.ini` says `Enabled="0"` and the merged folder is deleted. If preparing mods fails, the user can start without them.
  - **U8Archive:** reads and writes the 32-byte header (constant words `E4F91200 00000402`), 16-byte nodes (a directory's offset is its parent index and its length is the end index; a file's offset is the data offset, followed by stored length and uncompressed size) and the name table, with data 32-byte aligned and no trailing padding.
    - Merging is case-insensitive; it keeps the game's name spelling and appends new entries.
    - Stored (zlib) bytes are copied without recompressing.
    - Tested on the host: rewriting `shader.arc` and `shader_lt.arc` gives byte-identical files. A merge with a replaced, differently cased file and a new folder extracts correctly with `u8extract`.
  - The ModsDB, mod and cpkredir files the launcher generates were checked with the game's own `IniFile` parser on the host (quoted paths with spaces work).
- **Codes:** the launcher lists Marathon's hidden `Codes` options (23 of them; the Windows-only `DisableDWMRoundedCorners`, `UseOfficialTitleOnTitleBar`, and the `HUDToggleKey` and `EnableDebugMode` options are left out) and writes them to ModsDB `[Codes]`. They apply at runtime only and aren't saved to `config.toml`.
- **Not supported:** `.mlua` scripts, code (XEX) patches, archive.pkg additions for custom archives, and save redirection. The Mods screen notes these per mod.

## Mods in 0.2.1 (aimed at Legacy of Solaris)
- **Legacy of Solaris** (lost-legacy-team/LoS-Mod_Files_X; the user downloaded the GameBanana zip):
  - It is a classic `mod.ini` mod: `Merge="True"`, `CustomFilesystem="True"`, `Custom` (22 new files: .xma sounds, `title_loop_GBn.wmv`, `stage_csc_d.arc`, `player_mephiles.arc`) and `Read-only="player.arc,sprite.arc,text.arc,particle.arc"`. There are no RequiredPatches.
  - It needs Complete Physics Overhaul (a 260 MB .rar that bundles Physics Companion and Khronos) plus Khronos Resources, which its readme says to extract to the game root. LoS must be loaded last, so it goes at the top of our list.
  - Its readme says that on Xenia `protect_zero` must be `false`.
- **Bug fixed:** the Mod Manager's converter (`Interop/ModConverter.cs`) matches `Read-only` and `Custom` entries by **file name** (`Path.GetFileName`). 0.2.0 compared them against relative paths, so LoS's read-only archives would have been merged instead of replaced. Now:
  - a list entry matches either the relative path or the file name;
  - `Custom` and `Read-only` archives are REPLACE (served as they are, not copied into `merged`);
  - keys are read from whichever ini section has them, as the Mod Manager's `DeserialiseKey` does;
  - `RequiredPatches` gets a note on the Mods screen.
- **Finding mods:** `findMods` searches up to two folders down and returns every mod it finds, so a wrapper holding several mods (the CPO download) lists them all. A folder with no ini but with a `xenon` or `win32` child is a plain replace-only mod (for Khronos Resources); a `default.xex` in a mod gets a note.
- **Native changes:**
  - After `ModLoader::Init`, if any mod is active, guest page 0 (host `base`..`base+4096`, which `Memory()` sets to PROT_NONE) becomes RW. The log line is "Mods active: guest page 0 is readable (like Xenia's protect_zero=false)". Xenia protects 64 KiB; Marathon only protects 4 KiB and the rest below the heap (0x20000) is already RW.
  - The crash handler also prints "Fault is in guest memory: guest address 0x…" when `si_addr` lands inside guest memory (`g_androidGuestBase`, set in main).
- Host-tested with a LoS-like layout (LoS read-only `player.arc` over a CPO merge, a custom archive, a plain Khronos-style folder, and CPO with two more mods inside). Only real merges were built; the read-only and custom archives were served from the mod folder.

## 0.2.2: first mod test (8 Oct, 13:54)
- The user enabled only Legacy of Solaris, plus the codes ControllableSpinkick and ControllableTeleportDash, without CPO, Khronos or Khronos Resources.
- Mods loaded correctly: the redirect lines show the game opens `game:\xenon\archives\*.arc`, `game:\win32\archives\*.arc` and `game:\xenon\sound\*.wmv`. Merged archives were served from `.marathon/merged`, and LoS's read-only `player.arc`, `text.arc` and `particle.arc` from the mod folder. The title movie `title_loop_GBn.wmv` played.
- **Crash on starting a game (twice):** after `player/select_sonic.lua` (LoS's) loads, the game opened a disk file that didn't exist (upstream's `[ResolvePath] Game` line didn't print the path). That triggered `XamShowDirtyDiscErrorUI`, then the `XamLoaderLaunchTitle` stub, and then SIGSEGV in `sub_821EAF38` (fault 0xd4b58000, not guest memory). The vanilla game loads `select_sonic.pkg` straight from the archive at that point, so LoS's Lua reads a file that must come from the missing physics mods or Khronos Resources.
- A third run ended with `vkCreateSwapchainKHR` 0xC46535FF (VK_ERROR_NATIVE_WINDOW_IN_USE) when the user left the app. This is the known no-resume limitation.
- **Changes in 0.2.2:**
  - `XCreateFileA` logs "Can't open <guest path> (looked for <host path>)" for failed read opens and remembers the last one.
  - `XamShowDirtyDiscErrorUI` (Android) logs the file and shows an SDL message box ("Missing game file", with the path), then `_Exit(1)`.
  - `FileSystem::ResolvePath` logs the path itself as `[ResolvePath] <path>`.
  - Launcher: a top-level download with no mod.ini, mod.json, `xenon` or `win32` anywhere (but with files) becomes a plain mod with game-root layout, for Khronos Resources. The native loader looks up any relative path, so other top-level folders work too.

## Second mod test (8 Oct, 14:08): Legacy of Solaris can't run (Khronos = native DLL)
- The user was still on 0.2.1 with Legacy of Solaris and the Khronos mod (3 files) enabled, and `khronos_config.lua` in the game root. CPO, Physics Companion and Khronos Resources weren't installed.
- **Crash:** after `player/select_sonic.lua`, two threads hit SIGSEGV at 0x7ead8000. In 0.2.1's libmain, the pc `+0x1f35fa0` (in `sub_825C7178`) is the indirect-call lookup `ldr x9, [x9, x10]` with x10 = -0x81528000 (`PPC_LOOKUP_FUNC`). 0x7ead8000 = base 0x100000000 + 2×target − 0x81528000, so **target = 0: a guest call through a null function pointer.** The first test's crash (0xd4b58000, after the dirty-disc stub) was the same kind of fault, with target 0x2B040000.
- **Khronos** (GameBanana 616041, by GordinRamsay) is a set of Lua libraries "built on the DLL by Rei-san". Khronos Resources (5.6 MB) adds `common/`, `media/` and **`Core.xex`, a native Xbox 360 DLL**. Physics (616044) and Physics Companion (616042) require it, and LoS 2025 (Physics Compatible) requires those. The DLL is built on ReimousTH/Sonic-The-Hedgehog-2006-Marathon-Library, reverse-engineered game classes that call game functions at fixed addresses such as `0x82163620`.
- **Conclusion:** a static recompilation can't load extra PPC modules or runtime code hooks; PC Marathon Recompiled can't either. LoS 2025 (and anything built on Khronos) is unsupported. The LoS GameBanana page also says it uses "a big hybrid patch that combines multiple patches into one" (XEX code patches).
- **Possible future project (not started):**
  1. Recompile `Core.xex` with XenonRecomp.
  2. Emulate however it is loaded, which is still unknown: probably a Mod Manager patch that makes default.xex call XexLoadImage.
  3. Emulate the hooks it installs into game code. Recompiled code calls game functions directly (weak symbols), so each hook needs a build-time override.
  The first step would be to inspect `khronos.rar` and `khronosresources.rar`.
- The download page now says this and tells the user to switch LoS and Khronos off. No new APK.

## Patches in 0.3.0 (Mod Manager tweaks and patches)
- **Request:** the user asked for "the patches that allow tgs camera and other enhancements" from the Mod Manager as launcher options. The TGS camera is the classic Mod Manager's **Tweaks → Camera Type = Tokyo Game Show**.
- **Where the patches come from:** the classic manager (hyperbx/SonicNextModManager `main` branch, `Sonic-06-Mod-Manager/src/UnifyPatcher.cs`) downloads them from **Knuxfan24/Sonic-06-Mod-Manager-Patches**: 150 `.mlua` files, commit 52ac810 (21 Nov 2025). All are bundled in the APK as `assets/patches`; user `.mlua` files in `MarathonRecomp/patches` are added too, and a file with the same name replaces the bundled one.
- **Patch language:**
  - Header lines: `Title`, `Author`, `Platform`, `Blurb`, `Description`.
  - Section labels `All Systems:`, `Xbox 360:` and `PlayStation 3:`. With Platform "All Systems", commands only run after an `All Systems` or `Xbox 360` label.
  - Commands: `BeginBlock("core\archives\x.arc")` … `EndBlock`, where `core` means `xenon`. File commands inside a block: StringReplace, WriteBase64, Copy, Rename, RenameByExtension, Delete, Write* on archive files, ParameterAdd/Edit/Erase/Rename (on decompiled Lua lines), PackageAdd/Edit/Erase (BINA `.pkg`), and Ignore/Search filters for folder targets.
  - Executable writes: `WriteVirtualBytes/WriteVirtualNop(VA)` and `Write*(Executable|fileoffset)`, where VA = 0x82000000 + offset − 0x3000.
- **Classification** (`PatchEngine.parse`, cross-checked with `tools-patches/classify.py`):
  - The code ranges of default.xex come from the host tool `host/xexinfo`, which uses XenonUtils `Image::ParseImage`: `.text` 82160000–82AEAA6C, plus `.embsec_*` up to 82B0ABB8. `.rdata` is 82000600–8211B310 and `.data` 82B10000–82D5E89C.
  - **65 supported:** about 35 only edit archives, about 30 only write data (string or function-pointer tables in .rdata/.data), and some do both.
  - **85 unsupported:** 84 write into code, and DisableMusic renames files in the game folder.
  - 16 of the code patches already exist as Marathon codes with the same names: AntigravityRetainsMomentum, ControllableBoundAttack, ControllableSpinkick, ControllableTeleportDash, DisableKingdomValleyMist, DisablePushState, DisableTitleInputDelay, MidairControlForMachSpeed/Snowboards, RestoreChainJumpFlips, RestoreChaosBoostJump, RestoreChaosSpearFlips, RestoreContextualHUDColours, RestoreDemoCameraMode, RestoreSonicActionGauge and UnlimitedAntigravity.
- **Lua:** game `.lub` files are Lua 5.0 bytecode (`\x1BLuaP`). As the manager does, they are decompiled with **unlub.jar**, ShadowLAG's unluac 1.2.2 taken from the manager's `res/Applications`, now bundled as `app/libs/unlub.jar`. The text goes back into the archive, which the game accepts: mods already ship text `.lub` files. A file is only rewritten when an edit actually changes it. Line edits follow the manager exactly; for example, ParameterEdit splits the first matching line on spaces and sets word 2.
- **Tweaks** (`TweakEngine`, field of view 90):
  - **Camera type** edits `game.arc/xenon/cameraparam.lub`, and for TGS also `player.arc/xenon/player/common.lub`. The values set:

    | Line | TGS | E3 | Retail |
    |---|---|---|---|
    | distance | 4.5 | 5.5 | 6.5 |
    | springK | 0.225 | 0.98 | 0.98 |
    | altitude | −15 | 15 | 15 |
    | az_driveK | 50000 | 690 | 3250 |
    | az_dampingK | 2500 | 100 | 250 |

  - **Distance:** sets `distance` to value/100 (default 650).
  - **Height:** sets the `c_camera` y in `common.lub` (default 70).
  - **Amy's hammer range:** sets `c_hammer_head` in `amy.lub` (default 50).
  - **Starting rings:** appends `c_default_ring = N` to every player `.lub`.
  - **Not done:** FOV. The manager writes float degrees at file offset 0x4F4C, which is VA 0x82001F4C and holds π/4, probably a shared constant. MSAA, renderer and reflections are left out because Marathon has its own settings for them.
- **Pipeline (`ModManager.apply`):** order is mods, then patches in title order, then tweaks.
  - Every archive a patch or tweak touches gets built into `mods/.marathon/merged` from its final mod state: the game file, a mod's replacement, or a merge.
  - The manifest signature includes each patch's source hash and the tweak values. Problems are cached with the signature (`#problems`) and shown in a dialog before launch.
  - Data writes go to `mods/.marathon/xex_patches.txt`. `ApplyAndroidXexPatches()` in main.cpp writes them into guest memory right after `LdrLoadModule`, refuses code ranges, and logs "Patches: N data writes applied".
  - With nothing enabled, the merged folder and the file are deleted. "Start without them" also deletes the file.
- **U8Archive** can now find, create, remove, read (zlib inflate when length and uncompressed size are both non-zero) and replace files (zlib, best compression). Game archives aren't sorted (checked in shader.arc), so new entries are appended. **AssetPackage** reads and writes BINA `.pkg` files the way Marathon's AssetPackage does, and keeps the original 0x20 header bytes apart from the size fields.
- **UI:** the new `PatchesActivity` (a "Patches" button on the launcher) has:
  - camera type buttons;
  - steppers for distance, height, hammer and rings;
  - the built-in codes, moved here from Mods;
  - the 65 supported patches;
  - a toggle listing the unavailable ones, marking those available as built-in codes.
  State lives in `mods.txt` as `patch <id>` and `tweak <Name> <value>` lines.
- **Host test** (jtest/src5, PatchTest/Dbg3) used synthetic game.arc and player.arc, rebuilt from shader_lt.arc's tree with text lubs, one real Lua 5.0 bytecode file (render_cheap.lub) and a synthetic pkg:
  - TGS camera, height, hammer and rings were correct.
  - AllCharactersHaveSonicSpeed respected Ignore.
  - TGS2005Sonic's ParameterRename and PackageEdit worked, and the pkg round-trips byte-identically.
  - DoNotEnterMachSpeed's WriteBase64 worked. It first failed because the command parser rejected digits in names; fixed.
  - DisableFog and DisableIntroLogos produced xex_patches.txt.
  - Patches were applied on top of a mod's replacement player.arc.
  - The second apply was cached; disabling everything cleaned up.
  - Untested: real game lubs, which are big-endian, and real pkg files.
- **Next possible step for code patches:** for each code patch, run XenonRecomp on a patched copy of the XEX, take only the affected functions, rename them, and add a strong `sub_XXXX` that picks the patched or original `__imp__` version from the setting. Generated calls go through the weak `sub_` symbols, so this covers direct and indirect calls. Patches that add code caves would need those caves declared as functions.

## Touch controls in 0.4.0
- **Request:** touch controls in an Xbox layout "like our wet build", with settings to turn them on/off, an opacity slider, and vibration/haptics.
- **Java `TouchControlsView`:** a port of WET's `VirtualGamepadView.kt`, keeping the same layout fractions, look and multi-touch logic (an 8-way D-pad, triggers drawn as pills). Differences from WET:
  - No automatic L3 when the left stick is pushed.
  - Back/Start are drawn as ◀/▶.
  - Haptics go through `Vibrator` `EFFECT_TICK`.
  - `fade()` hides it while a physical controller is used. GameActivity watches `dispatchKeyEvent` and `dispatchGenericMotionEvent` from gamepad/joystick sources (axis > 0.5); the next touch shows it again.
- **Native `os/android/touch_controls_android.cpp`:** an SDL2 virtual joystick created with `SDL_JoystickAttachVirtualEx`.
  - Type GAMECONTROLLER, 15 buttons in `SDL_CONTROLLER_BUTTON_*` order and 6 axes. SDL builds the mapping in `VIRTUAL_JoystickGetGamepadMapping`.
  - VID/PID 045E:028E, so `SDL_GameControllerGetType` reports XBOX360 and Marathon shows Xbox prompts. The name is "Touch Controls".
  - It is attached in `hid::Init` (sdl_hid.cpp, Android only) right after `SDL_InitSubSystem(GAMECONTROLLER)`, so Marathon's event watch opens it like any other pad.
  - JNI `nativeSetState(buttons, lx, ly, rx, ry, lt, rt)` pushes only the values that changed; triggers are sent as `lt*2-32768`.
  - The `Rumble` callback calls the Java static `TouchControlsView.onRumble(low, high)` through `SDL_AndroidGetJNIEnv`, which runs `VibrationEffect.createOneShot(5000, amplitude = max>>8)` or cancels. SDL only calls it when the values change, and handles the 5 s expiry.
- **GameActivity:**
  - Calls `nativeConfigure(attach = !installer, rumble)` before SDL_main starts. The installer is shown when `game/default.xex` is missing or when started with `--install-dlc`.
  - Adds the view to `mLayout`; it is hidden when the setting is off, but the virtual pad still exists so it can be switched on live.
  - A four-finger tap within 400 ms cancels the gesture and opens `ControlsPanel` as a dialog. SDL2 doesn't pause on focus loss on API 24+, so only Marathon's mute-on-focus-lost applies.
  - onPause releases everything and stops the rumble.
- **Settings:** stored in SharedPreferences `touch_controls`: enabled (true), opacity (0.6), scale (1.0), buttonHaptics (true), rumble (true), hideWithController (true). The launcher's new **Controls** button opens `ControlsActivity`, which uses the same `ControlsPanel`.

## 0.4.1: main-thread fix (touch input)
- **Symptom (0.4.0):** the overlay drew, haptics worked and the log showed "Touch controls: on", but there was no "Detected controller" line and Start did nothing.
- **Host test:** SDL2 built for the host from `thirdparty/SDL`, with HIDAPI on (the virtual joystick option depends on it), running `scratchpad/sdlhost/vtest.cpp`. The same attach, event watch and pumping gave JOYDEVICEADDED, then CONTROLLERDEVICEADDED with type 1 (XBOX360), then CONTROLLERBUTTONDOWN. So the virtual pad works, provided `SDL_PumpEvents` (and with it `SDL_JoystickUpdate`) runs.
- **Root cause:** `app.cpp` only calls `SDL_PumpEvents()`, `SDL_FlushEvents()` and `GameWindow::Update()` when `std::this_thread::get_id() == g_mainThreadId`. That id is a file-static captured in a **static initialiser**, which on Android runs on the Java UI thread during `System.loadLibrary`, while the game runs on our 64 MiB pthread. So on Android the game never pumped SDL events.
  - Physical pads still worked because SDL's Android backend pushes their events straight from the Java callbacks, and Marathon's event watch sees them.
  - A virtual joystick needs `SDL_JoystickUpdate`.
  - The same bug stopped `AchievementOverlay` from ever dequeuing (achievement pop-ups), and left the SDL queue unflushed.
  - The installer loop pumps without the check, which is why it behaved.
- **Fix:** `os/android/android_main.cpp` defines `std::thread::id g_androidMainThreadId`, set at the start of the game thread. `app.cpp` and `ui/achievement_overlay.cpp` `#define g_mainThreadId g_androidMainThreadId` on Android. `gpu/video.cpp` only uses its copy in a commented-out assert.
- **Side effect:** `SDL_PumpEvents` now runs per frame, and with `SDL_HINT_ANDROID_BLOCK_ON_PAUSE=1` the game thread blocks while the activity is paused (and audio pauses). Before, the game kept running in the background. The swapchain still can't be rebuilt after resume (a known issue).

## 0.4.2: exit dialog
- **Request:** the system back button should open the same "Exit to the home screen?" pop-up as WET. WET's `ExitConfirmDialog` lives in GameDialogs.kt.
- **`ExitDialog.java`:** a port of that dialog with the same Mel & Carvão look: charcoal card, amber outline, "Keep playing" outlined and "Exit" filled amber. The window keeps immersive flags, dim 0.55, and 80% width up to 480dp.
- **GameActivity:**
  - `dispatchKeyEvent` intercepts KEYCODE_BACK when it comes from the system: no gamepad, joystick or D-pad device bits and not a mouse (WET's `isSystemBackSource`). Controller back still goes to SDL.
  - `onBackPressed` is overridden too.
  - Back closes the panel or the dialog first if one is open.
  - The four-finger panel gained an "Exit game" button.
- **Exiting:**
  1. `exitToLauncher()` releases the pad, stops the rumble, starts LauncherActivity and calls `finish()`.
  2. It then calls JNI `GameActivity.nativeRequestExit()` (android_main.cpp), which does `SDL_PushEvent(SDL_QUIT)`.
  3. Marathon's `Window_OnSDLEvent` watch runs synchronously and calls `App::Exit()` (`Config::Save` + `_Exit`), unless `App::s_isSaving` is set.
  4. If the call returns, it retries every 250 ms, 20 times, and then calls `Process.killProcess`.
- **GameActivity now runs in `android:process=":game"`**, so ending it leaves the launcher (main process) running, and every Start is a fresh process. Because SharedPreferences cache per process, `ControlsSettings` moved to `files/touch_controls.properties`, migrating from the old prefs once.

## 0.5.0: GameBanana mod browser
- **Request:** "a way in the mod manager it can search https://gamebanana.com/games/6836 and fetch the latest mods and be able to import them to the launcher without it going to download folder but it goes to the mods folder".
- **Screens:**
  - Mods gains a **Get mods** button and now reloads in `onResume`, so installs and "Turn on" show up straight away.
  - `ModBrowserActivity`: search box plus Newest / Updated; rows with a thumbnail, author, category, likes, views and date; an "Installed" or "update available" badge (`_tsDateUpdated` later than the install time); Load more.
  - `ModDetailsActivity`: large screenshot, stats, warnings (the page mentions Khronos/Core.xex, PS3, or default.xex), files with size, version, date and downloads, Install / Reinstall / Remove, the description (HTML without img/iframe, collapsed to 14 lines), and Open on GameBanana. "A newer file was uploaded" appears when a file's `_tsDateAdded` is later than the installed one.
  - Install shows a progress dialog (download bytes, then unpack %) with Cancel. Afterwards it lists the mods found under the new folder with their ModManager notes and offers **Turn on** (ModManager load, enable, save).
  - If another file of the same mod is installed: **Replace** (update) or **Keep both** (add-on).
- **`ModInstaller`:**
  - Downloads `https://gamebanana.com/dl/{fileId}` (302 to `filecacheNN.gamebanana.com/mods/<file>`, checked via WebFetch) into the app cache, checks `_sMd5Checksum`, then unpacks into `mods/.marathon/incoming/<fileId>`.
  - A download with mod.ini, mod.json or xenon/win32 within 3 levels goes to `mods/<Mod name>`. If that name is taken, it uses `<name> - <file>`, then `<name> (<fileId>)`. A replace swaps the old folder through a backup, so a failed move restores it.
  - `.gamebanana.json` in the folder records modId, name, fileId, file, version, updated, fileAdded, installed and source.
  - Patches-only downloads (`.mlua`, no mod files) are copied flat into `MarathonRecomp/patches`, with `patches/.gamebanana-<fileId>.json` listing them.
  - The download, the incoming folder and the backup are always cleaned up.
- **`libmodarchive.so`** (`<build-root>/sonic06/modarchive`): libarchive 3.8.1 + xz 5.6.4, static, via the NDK; JNI `ArchiveExtractor.nativeExtract(archive, dest, listener)`, progress every 1 MB, cancel when the listener returns false. Entry names are cleaned: absolute paths and `..` are refused, drive letters are stripped and backslashes become slashes. Only directories and regular files are written. Host-tested with zip, 7z (LZMA2, BCJ2), RAR4 and RAR5 (`scratchpad/extract-test`). 490 KB stripped.
- **`GameBanana` (apiv11):**
  - Lists use `Game/6836/Subfeed?_sSort=new|updated&_nPage=N&_csvModelInclusions=Mod`. Only `_sModelName == "Mod"` records are kept, because the plain feed also carries Questions and Requests. Up to 4 feed pages are read per batch until 8 mods turn up, and `_aMetadata._bIsComplete` ends the list. If the sort is refused, it falls back to `default`.
  - Search uses `Util/Search/Results?_sModelName=Mod&_sOrder=best_match&_idGameRow=6836&_sSearchString=…&_csvFields=name,description,article,attribs,studio,owner,credits&_nPage=N`. If that fails, it scans the feed (up to 10 pages per batch) and matches every word against name, author and category.
  - Mod pages use `Mod/{id}/ProfilePage`. It has `_aCategory`, not `_aRootCategory`, and no `_tsDateUpdated`.
  - Field names were checked against the live JSON for Subfeed and ProfilePage (mod 700557 "Restored 15th Anniversary Intro", file 1774218).
  - Query parameters could **not** be verified: WebFetch drops them, and curl to gamebanana.com is blocked by the sandbox allowlist.
- **1-click:** `OneClickActivity` (exported, translucent, noHistory). It handles `sonic06mm:` VIEW links, parsing `mmdl/(\d+),Mod,(\d+)` as fileId and modId, and text shares containing `gamebanana.com/mods/<id>` ("Install with Marathon" in the share sheet). It opens `ModDetailsActivity` with `FLAG_ACTIVITY_NEW_TASK`, which then asks "Install from GameBanana?" for that file.
- **Manifest:** adds `INTERNET` (only the browser uses it).

## 0.5.1: patch updates
- **Request:** "Do we have a refresh/fetch updated patches for the codes/tweaks".
- **What can update:**
  - Only the official Mod Manager patches. The classic manager's "Fetch the latest patches" (`RushInterface.UpdatePatches`) downloads `https://github.com/Knuxfan24/Sonic-06-Mod-Manager-Patches/archive/master.zip` (`Properties/Resources.resx` PatchURI_GitHub) and extracts every `.mlua` file.
  - Upstream master is still `52ac810` (21 Nov 2025), the bundled set, checked with `git fetch` on 8 Oct. MarathonRecomp main is still `f74495e`, so the built-in codes are current too.
  - Codes are compiled into libmain.so and tweaks are fixed engine settings; neither can be fetched.
- **`PatchUpdater`:**
  1. `GET api.github.com/repos/<repo>/commits/master` gives sha and committer date. If the sha matches the set in use, it reports up to date.
  2. Otherwise it downloads `github.com/<repo>/archive/<sha or master>.zip`, unpacks it with libmodarchive in the cache, and collects `.mlua` files flat.
  3. It compares them byte for byte with the set in use: added, changed, removed. If nothing changed, it reports up to date (and records the new sha if a downloaded set is active).
  4. It stages into `files/patches-online.new` and renames that to `files/patches-online`. `files/patches-online.properties` holds sha, date and the last check time.
  - A downloaded set replaces the bundled assets set entirely, so removals take effect. It is ignored if its date is older than `BUNDLED_DATE`, which handles a future APK with a newer bundle. User `.mlua` files in `MarathonRecomp/patches` still override by name.
  - If the commit API is rate-limited, it falls back to comparing the master zip's files and records the sha as `unknown-<time>`.
- **PatchesActivity:**
  - A "Check for updates" button shows its progress on the button itself.
  - A version line reads "Mod Manager patches: the set from <date> (<sha7>), bundled with the app / downloaded from GitHub · last checked …".
  - The result dialog lists New, Changed and Removed (by title, up to 12 each), "Now available here" (patches that newly work), and a warning for enabled patches that stopped being usable. "Use the app's set" (`PatchUpdater.reset`) goes back to the bundled set.
  - `officialSet(context)` picks the downloaded or the bundled source. The launcher's Start uses the same `loadLibrary`, and the manifest signature includes each patch's hash, so changed patches rebuild automatically.
- **Host test** (`scratchpad/pu-test`): PatchUpdater against a local fake of api.github.com, github.com (302) and codeload, with java.util.zip standing in for libmodarchive. Seven cases passed: bundled commit, new commit (+1, ~1, −1), same commit again, API 403 with the same files, a README-only commit, API failure with different files, and a fresh install with the API failing. The sandbox proxy blocks api.github.com and codeload from curl, so the real endpoints were not called.

## 0.6.0: FPS counter and GPU drivers
- **Request:** "add a fps counter option when we do the three finger tap for controls options" (the panel opens with four fingers), and "a way to import turnip drivers into options in the launcher with the fps counter".
- **Model:** WET's port (`<build-root>/wet-android`): `FpsCounterView.kt` (polls a native present counter) and `GpuDriverManager` plus `vulkan_instance.cpp` (loads the driver through libadrenotools). Re-done in Java and C++ for Marathon.
- **FPS counter:**
  - video.cpp `Video::Present` increments `std::atomic<uint64_t> g_androidPresentCount` after each successful swapchain present.
  - JNI `GameActivity.nativeGetPresentCount()` (gpu_android.cpp).
  - `FpsCounterView` (TextView, monospace amber on charcoal, top centre, not clickable) polls every 500 ms and shows "N FPS · x.x ms".
  - The setting is `showFps` in ControlsSettings (default off), with a toggle under a "Display" heading in ControlsPanel, so it appears both in game (four-finger panel, now titled "Settings") and on the launcher screen.
- **GPU driver:**
  - **libadrenotools** (bylaws, BSD-2, commit 8fae8ce, copied from WET's submodule into `<build-root>/ext/libadrenotools`): built with NDK 27.2 `-DBUILD_SHARED_LIBS=ON -Wl,-z,max-page-size=16384` into `ext/adrenotools-build`. The stripped `libadrenotools.so` and the hooks `libhook_impl`, `libmain_hook`, `libfile_redirect_hook` and `libgsl_alloc_hook` are in jniLibs. `useLegacyPackaging` keeps them as real files in nativeLibraryDir, which AdrenoTools needs.
  - **plume patch** (plume-android.patch):
    1. On Android, `VulkanInterface()` calls `MarathonAndroid_CustomVkGetInstanceProcAddr()` and uses `volkInitializeCustom()` when it returns a loader, else `volkInitialize()`.
    2. If `vkCreateInstance` fails with the custom loader, it calls `MarathonAndroid_CustomDriverFailed()`, re-runs `volkInitialize()` and retries with the system driver.
  - **`os/android/gpu_android.cpp`:**
    - Reads the env vars `MARATHON_CUSTOM_DRIVER`, `MARATHON_NATIVE_LIB_DIR` and `MARATHON_DRIVER_STATUS`.
    - dlopens `<libdir>/libadrenotools.so` and calls `adrenotools_open_libvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, nullptr, libDir, driverDir + "/", driverName, nullptr, nullptr)`, then takes `vkGetInstanceProcAddr` from the returned handle.
    - Writes `status=system|custom_ok|custom_failed`, `driver` and `error` to the status file. video.cpp then appends `device=` and `version=` (`AndroidGpu_ReportDevice`).
  - **GameActivity.passDriverChoice()** sets those env vars with `Os.setenv` before `super.onCreate`, so the game process, a fresh `:game` process each Start, has them before libmain loads.
  - **`DriverManager.java`:**
    - Imports through SAF + ZipInputStream: meta.json (`libraryName` or `libName`, name, author, packageVersion, driverVersion, minApi).
    - Picks the main .so by libraryName, then by a vulkan/freedreno/turnip name, then the first one. The main library must be ELF64 aarch64 ET_DYN. Every .so is extracted flat with its original name into `files/drivers/<time>-<slug>/`, with a `driver.json`.
    - Saves the selection as `files/drivers/active.txt` (the id), written atomically.
    - The last start is read from `files/drivers/last_start.txt`.
  - **Settings screen** (`ControlsActivity`, launcher button renamed from Controls to Settings):
    - the touch controls and Display panel;
    - a "GPU driver" section: help text, a Last start line (green when the custom driver ran, amber when it failed and the game fell back, grey for the system driver, with the device name), radio rows for Phone's driver and each imported driver (with Remove), and an "Import driver (.zip)" button that offers "Use it" after an import.
  - The launcher status line adds " · GPU driver: name", plus "(didn't start last time)" when that driver failed.
- **Drivers for the S25 (Adreno 830):** Turnip's a8xx support is experimental. **Banners-Turnip** (github.com/The412Banner/Banners-Turnip) builds nightly from Mesa main and ships an "A8xx (Experimental)" AdrenoTools zip listing A810/A825/A829/A830; its A6xx/A7xx zips won't work here. Android Authority reported in early 2026 on Rob Clark's (Qualcomm) Mesa MR 38450 for Adreno 8xx.
- **Host test** (`scratchpad/dm-test`): DriverManager with fake packages. Passed: a K11MCH1-style root `vulkan.ad07XX.so` with `libraryName` and minApi 28; a nested `lib/arm64-v8a/libvulkan_freedreno.so` with `libName` plus a companion libgsl.so; and errors for no meta.json, a file that isn't a zip, an x86-64 library, minApi 99 and a missing named library. Also checked: active, list, remove and the last-start parsing.
- **Not testable here:** the actual AdrenoTools load on the S25, Turnip A8xx with this game, and the FPS overlay on the device.

## Port changes (`marathon-android.patch`, plus `plume-android.patch` for the plume submodule)
- **CMake:**
  - `MarathonRecomp` becomes a shared library with output name `main` (loaded by SDLActivity).
  - `main.cpp` is compiled with `main=MarathonMain` and without the PCH.
  - `GAME_INSTALL_DIRECTORY` is `/storage/emulated/0/MarathonRecomp`.
  - Links `android` and `log`, with `--no-undefined`.
  - SDL2-static is linked with `--whole-archive`, so every JNI entry point (SDLActivity, HIDDeviceManager) is in `libmain.so`.
  - FFmpeg's static libraries go through `--exclude-libs`: its NEON assembly addresses tables PC-relative, which fails against preemptible symbols.
  - BIN2C uses the host `file_to_c`; built-in shaders use the x64 `dxc-linux`.
  - On Android, `MarathonRecompLib` skips the XenonRecomp, u8extract and XenosRecomp steps because their outputs are pre-generated.
  - No libcurl; vcpkg is off for msdf.
- **`os/android/`:**
  - `logger_android.cpp`: one log per session in `MarathonRecomp/logs` (newest 10 kept), stdout and stderr redirected there and to logcat, and a SIGSEGV/SIGBUS/SIGILL/SIGFPE/SIGABRT handler that writes pc, lr and an unwound backtrace with module offsets.
  - `process_android.cpp`: a virtual executable path inside the root folder; `StartProcess` is unsupported.
  - Stubs for media, user and version.
  - `nfd_android.cpp`: the installer's "pickers" return what is in `MarathonRecomp/import` (files for Add Files; subfolders, or the folder itself if it holds `default.xex`, for Add Folder).
  - `android_main.cpp`: exports `SDL_main`, sets hints (landscape, touch as mouse, block on pause, trap back button) and runs `MarathonMain` on a 64 MiB-stack pthread, because the Java SDL thread has about 1 MiB.
  - `atomic_ref_compat.h`: NDK r27's libc++ 18 has no `std::atomic_ref`. This polyfill provides `compare_exchange`, `store`, `load`, `exchange` and `fetch_add`/`fetch_sub`, plus `wait`/`notify` on futex. It is included from `stdafx.h` on Android.
- **`cpu/guest_thread.h`:** `USE_PTHREAD` on Android as on macOS, giving 8 MiB guest-thread stacks instead of bionic's ~1 MiB.
- **`user/paths.cpp`:** the user path is `MarathonRecomp/user`.
- **`ui/game_window.cpp`:** passes `info.info.android.window` (ANativeWindow) to plume, whose Vulkan backend already supports Android surfaces.
- **`install/update_checker_android.cpp`:** no update checks.

## App (`android-app`)
- **`LauncherActivity`** (plain views): asks for All files access, creates `MarathonRecomp/import` and `logs`, explains both ways of adding the game, and has Start and Install DLC (`--install-dlc`) buttons.
- **`GameActivity extends SDLActivity`:** loads `c++_shared` and `main`, passes arguments, keeps the screen on, `sensorLandscape`.
- **SDL Java:** the files are copied from `thirdparty/SDL/android-project` (SDL 2.32.11, the same tree as the native SDL).

## On the phone
- Root folder: `Internal storage/MarathonRecomp`
  - `game`: installed game. The game counts as installed when `game/default.xex` exists. Copying an extracted game there skips the installer's hash check.
  - `import`: source for the installer
  - `user`: config and saves
  - `logs`
- The game applies its own low-end defaults on integrated GPUs: MSAA 2×, 1024 shadows, quarter-resolution reflections, transparency AA off.

## Known risks for the first device test
- Switching apps destroys the ANativeWindow; plume keeps the old VkSurface, so expect a broken screen or a crash after resuming.
- Vulkan on Adreno through plume and DXC-generated SPIR-V has not been tested on a device (desktop Linux and macOS use the same SPIR-V path).
- The swapchain uses `preTransform = IDENTITY`, so the compositor rotates the image (a small cost).
- `std::atomic_ref` polyfill: the game's critical sections spin briefly, then wait on a futex.
