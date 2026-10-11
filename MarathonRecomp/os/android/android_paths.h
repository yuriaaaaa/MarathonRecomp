#pragma once

// Android port: everything the game reads or writes lives in one folder on
// shared storage (the app asks for "All files access" before starting), so the
// player can drop in the game and pull out logs and saves with a file manager.
//
//   <root>/import  - put the game ISO / extracted folder (and DLC) here for the installer
//   <root>/game    - installed game files (written by the installer)
//   <root>/user    - config, saves, achievements
//   <root>/logs    - one log per session
#define MARATHON_ANDROID_ROOT "/storage/emulated/0/MarathonRecomp"

namespace os::android
{
    inline const char* RootPath() { return MARATHON_ANDROID_ROOT; }
}
