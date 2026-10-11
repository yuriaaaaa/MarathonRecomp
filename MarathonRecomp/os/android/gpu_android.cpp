// Android: custom Vulkan drivers (Turnip and other AdrenoTools packages) and the frame counter
// behind the launcher's FPS overlay.
//
// The launcher passes three environment variables to the game process (GameActivity sets them
// with Os.setenv before SDL starts):
//   MARATHON_CUSTOM_DRIVER   full path of the selected driver's .so (unset = system driver)
//   MARATHON_NATIVE_LIB_DIR  the app's nativeLibraryDir, where libadrenotools.so and its hooks live
//   MARATHON_DRIVER_STATUS   a file to write the outcome to, which the launcher shows

#include <os/logger.h>

#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <jni.h>
#include <string>

#include <volk.h>

std::atomic<uint64_t> g_androidPresentCount{ 0 };

static std::string g_driverStatus;

static void WriteDriverStatus(const char* status, const char* driver, const std::string& error)
{
    const char* path = std::getenv("MARATHON_DRIVER_STATUS");
    g_driverStatus = std::string("status=") + status + "\ndriver=" + (driver ? driver : "-") + "\nerror=" + error + "\n";
    if (path == nullptr || path[0] == '\0')
        return;
    if (FILE* f = std::fopen(path, "w"))
    {
        std::fputs(g_driverStatus.c_str(), f);
        std::fclose(f);
    }
}

// Called by video.cpp once the device exists, so the launcher can show which GPU driver ran.
void AndroidGpu_ReportDevice(const std::string& name, uint64_t driverVersion)
{
    const char* path = std::getenv("MARATHON_DRIVER_STATUS");
    if (path == nullptr || path[0] == '\0')
        return;
    if (FILE* f = std::fopen(path, "w"))
    {
        std::fprintf(f, "%sdevice=%s\nversion=%llX\n", g_driverStatus.c_str(), name.c_str(), (unsigned long long)driverVersion);
        std::fclose(f);
    }
}

extern "C" PFN_vkGetInstanceProcAddr MarathonAndroid_CustomVkGetInstanceProcAddr()
{
    const char* driver = std::getenv("MARATHON_CUSTOM_DRIVER");
    if (driver == nullptr || driver[0] == '\0')
    {
        WriteDriverStatus("system", nullptr, "-");
        return nullptr;
    }

    const char* libDir = std::getenv("MARATHON_NATIVE_LIB_DIR");
    if (libDir == nullptr || libDir[0] == '\0')
    {
        LOGN_ERROR("GPU driver: the launcher didn't pass the app's library folder; using the system driver");
        WriteDriverStatus("custom_failed", driver, "no native library folder");
        return nullptr;
    }

    std::string adrenotoolsPath = std::string(libDir) + "/libadrenotools.so";
    void* adrenotools = dlopen(adrenotoolsPath.c_str(), RTLD_NOW);
    if (adrenotools == nullptr)
    {
        const char* error = dlerror();
        LOGFN_ERROR("GPU driver: can't load libadrenotools: {}; using the system driver", error ? error : "?");
        WriteDriverStatus("custom_failed", driver, error ? error : "libadrenotools failed to load");
        return nullptr;
    }

    using OpenLibvulkan = void* (*)(int, int, const char*, const char*, const char*, const char*, const char*, void**);
    auto openLibvulkan = reinterpret_cast<OpenLibvulkan>(dlsym(adrenotools, "adrenotools_open_libvulkan"));
    if (openLibvulkan == nullptr)
    {
        LOGN_ERROR("GPU driver: adrenotools_open_libvulkan is missing; using the system driver");
        WriteDriverStatus("custom_failed", driver, "adrenotools_open_libvulkan missing");
        return nullptr;
    }

    // AdrenoTools joins the folder and the file name without a separator.
    std::string path(driver);
    size_t slash = path.find_last_of('/');
    std::string driverDir = slash == std::string::npos ? std::string("./") : path.substr(0, slash + 1);
    std::string driverName = slash == std::string::npos ? path : path.substr(slash + 1);

    constexpr int ADRENOTOOLS_DRIVER_CUSTOM = 1 << 0;
    void* vulkan = openLibvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, nullptr, libDir,
        driverDir.c_str(), driverName.c_str(), nullptr, nullptr);
    if (vulkan == nullptr)
    {
        LOGFN_ERROR("GPU driver: AdrenoTools couldn't open {} (see logcat tag hook_impl); using the system driver", path);
        WriteDriverStatus("custom_failed", driver, "AdrenoTools couldn't open the driver");
        return nullptr;
    }

    auto getInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(vulkan, "vkGetInstanceProcAddr"));
    if (getInstanceProcAddr == nullptr)
    {
        LOGN_ERROR("GPU driver: the Vulkan loader has no vkGetInstanceProcAddr; using the system driver");
        WriteDriverStatus("custom_failed", driver, "vkGetInstanceProcAddr missing");
        return nullptr;
    }

    LOGFN("GPU driver: using {} through AdrenoTools", path);
    WriteDriverStatus("custom_ok", driver, "-");
    return getInstanceProcAddr;
}

extern "C" JNIEXPORT jlong JNICALL Java_com_marathonrecomp_android_GameActivity_nativeGetPresentCount(JNIEnv*, jclass)
{
    return (jlong)g_androidPresentCount.load(std::memory_order_relaxed);
}

// plume calls this when the custom driver loaded but vkCreateInstance failed; it then retries with
// the system driver.
extern "C" void MarathonAndroid_CustomDriverFailed(int result)
{
    char error[64];
    std::snprintf(error, sizeof(error), "vkCreateInstance failed (%d); used the system driver", result);
    LOGFN_ERROR("GPU driver: {}", error);
    WriteDriverStatus("custom_failed", std::getenv("MARATHON_CUSTOM_DRIVER"), error);
}
