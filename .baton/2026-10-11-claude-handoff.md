# Baton: Marathon Recompiled (Sonic '06) Android port, state at 0.6.0
From: claude · Date: 2026-10-11 · Repo commit: 86f1984

**TL;DR:** Works on the S25: stages, cutscenes, touch controls, mods, patches, GameBanana installs, Turnip and the FPS counter, all confirmed on the device. Open issues are resume after switching apps, the 68 code patches, and Legacy of Solaris. Next for ChatGPT: research how other SDL2/Vulkan Android ports recreate the swapchain after resume.

## Goal
A full-featured arm64 Android build of Marathon Recompiled from this fork, built by players from their own game copy. Source-only release `android-0.6.0` (David tags releases).

## State of play (verified)
- Android source on `main` under `android/` (`android-app`, patches, host tools). Build: `android/build.sh --game /path/to/extracted/game`; `--skip-game` passed on 2026-10-11. Clone with `--recurse-submodules`.
- 0.6.0 confirmed on the device on 2026-10-08 (Turnip Gen8 V37, 60 FPS at the title screen). Wave Ocean completed on 0.4.2. Mods, patches and GameBanana confirmed on 0.5.x.
- Full history: `docs/ANDROID_PORT.md`.

## Assumed / unverified
- Install DLC button: never tested on a device.
- Devices other than the S25 (Adreno 830): untested.

## Findings and dead ends
- Leaving the app destroys the ANativeWindow; plume keeps the old VkSurface, and the next `vkCreateSwapchainKHR` fails with `VK_ERROR_NATIVE_WINDOW_IN_USE`. The game thread blocks while paused (`SDL_HINT_ANDROID_BLOCK_ON_PAUSE=1`).
- The 68 Sonic '06 patches that change game code can't apply, because the code is compiled ahead of time. Idea on file: compile per-function variants and switch at run time.
- Legacy of Solaris needs Khronos (`Core.xex`, a native Xbox 360 DLL). Can't run as is.

## Next task for ChatGPT
Research only, no code:
1. How do other SDL2 + Vulkan Android ports (for example UnleashedRecomp Android forks, other recomps, PPSSPP, Dolphin) handle `SDL_APP_WILLENTERBACKGROUND` / surface loss: destroy and recreate the VkSurface and swapchain on resume? Give source links to the relevant functions.
2. Check sonicnext-dev/MarathonRecomp and its plume submodule for any upstream surface-recreation support since this fork's base.
3. Drop a baton `.baton/YYYY-MM-DD-chatgpt-resume-research.md`.

## Open questions for David
- Is resume after switching apps the next thing you want fixed, or the code patches?
