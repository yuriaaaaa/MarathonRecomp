#include <os/version.h>

#include <sys/system_properties.h>

os::version::OSVersion os::version::GetOSVersion()
{
    os::version::OSVersion version{};
    char value[PROP_VALUE_MAX] = {};
    if (__system_property_get("ro.build.version.sdk", value) > 0)
        version.Major = uint32_t(atoi(value));

    return version;
}
