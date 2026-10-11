# Host (x86_64 Linux) build of Marathon Recompiled's code-generation tools only:
# XenonRecomp, XenosRecomp, u8extract and file_to_c. android/build.sh runs them before the
# NDK build, because the Android build of MarathonRecompLib uses their pre-generated output.
cmake_minimum_required(VERSION 3.20)
project(MarathonHostTools C CXX)

get_filename_component(R "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)
set(MARATHON_RECOMP_THIRDPARTY_ROOT "${R}/thirdparty")
set(MARATHON_RECOMP_TOOLS_ROOT "${R}/tools")
set(CMAKE_CXX_STANDARD 20)
set(BUILD_SHARED_LIBS OFF)
add_subdirectory("${R}/tools" tools)

set(LIB "${R}/MarathonRecompLib")
target_compile_definitions(XenosRecomp PRIVATE
    XENOS_RECOMP_INPUT=\"${LIB}/private/shader\"
    XENOS_RECOMP_OUTPUT=\"${LIB}/shader/shader_cache.cpp\"
    XENOS_RECOMP_INCLUDE_INPUT=\"${R}/tools/XenosRecomp/XenosRecomp/shader_common.h\"
    MARATHON_RECOMP)

# Optional reverse-engineering helper (not needed for the build): prints default.xex's sections
# and dumps the mapped image, which android/tools/classify_patches.py reads.
add_executable(xexinfo EXCLUDE_FROM_ALL xexinfo/xexinfo.cpp)
target_link_libraries(xexinfo PRIVATE XenonUtils)
