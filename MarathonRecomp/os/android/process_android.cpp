#include <os/process.h>
#include <os/android/android_paths.h>

#include <unistd.h>

// There is no executable of our own on Android (the process is app_process),
// so report a virtual executable inside the root folder: the working directory,
// portable.txt check and GAME_INSTALL_DIRECTORY all resolve there.
std::filesystem::path os::process::GetExecutablePath()
{
    return std::filesystem::path(MARATHON_ANDROID_ROOT) / "MarathonRecomp";
}

std::filesystem::path os::process::GetExecutableRoot()
{
    return GetExecutablePath().remove_filename();
}

std::filesystem::path os::process::GetWorkingDirectory()
{
    char cwd[PATH_MAX] = {};
    char *res = getcwd(cwd, sizeof(cwd));
    if (res != nullptr)
        return std::filesystem::path(std::u8string_view((const char8_t*)(cwd)));

    return std::filesystem::path();
}

bool os::process::SetWorkingDirectory(const std::filesystem::path& path)
{
    return chdir(path.c_str()) == 0;
}

bool os::process::StartProcess(const std::filesystem::path& path, const std::vector<std::string>& args, std::filesystem::path work)
{
    // Not possible on Android.
    return false;
}

void os::process::CheckConsole()
{
    g_consoleVisible = false;
}

void os::process::ShowConsole()
{
}
