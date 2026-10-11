cmake_policy(SET CMP0077 NEW)

if (ANDROID)
    # Android port: no vcpkg. FreeType is prebuilt for arm64 (see android/README).
    set(MSDF_ATLAS_USE_VCPKG OFF CACHE BOOL "" FORCE)
endif()

set(MSDF_ATLAS_BUILD_STANDALONE OFF)
set(MSDF_ATLAS_USE_SKIA OFF)
set(MSDF_ATLAS_NO_ARTERY_FONT ON)
set(MSDFGEN_DISABLE_PNG ON)

set(SDL2MIXER_DEPS_SHARED OFF)
set(SDL2MIXER_VENDORED ON)
set(SDL2MIXER_FLAC OFF)
set(SDL2MIXER_MOD OFF)
set(SDL2MIXER_MP3 OFF)
set(SDL2MIXER_MIDI OFF)
set(SDL2MIXER_OPUS OFF)
set(SDL2MIXER_VORBIS "VORBISFILE")
set(SDL2MIXER_WAVPACK OFF)

if (CMAKE_SYSTEM_NAME MATCHES "Linux")
    set(PLUME_SDL_VULKAN_ENABLED ON CACHE BOOL "")
endif()

if (WIN32)
    set(PLUME_D3D12_AGILITY_SDK_ENABLED ON CACHE BOOL "")
endif()

if (APPLE AND CMAKE_OSX_ARCHITECTURES)
    set(BASE_ARCHITECTURE "${CMAKE_OSX_ARCHITECTURES}")
elseif (CMAKE_SYSTEM_PROCESSOR)
    set(BASE_ARCHITECTURE "${CMAKE_SYSTEM_PROCESSOR}")
else()
    set(BASE_ARCHITECTURE "${CMAKE_HOST_SYSTEM_PROCESSOR}")
endif()

# Next, match common architecture strings down to a known common value.
if (BASE_ARCHITECTURE MATCHES "(x86)|(X86)|(amd64)|(AMD64)")
    set(ARCHITECTURE "x86_64")
elseif (BASE_ARCHITECTURE MATCHES "(aarch64)|(AARCH64)|(arm64)|(ARM64)")
    set(ARCHITECTURE "arm64")
else()
    message(FATAL_ERROR "Unsupported CPU architecture: ${BASE_ARCHITECTURE}")
endif()

add_subdirectory("${MARATHON_RECOMP_THIRDPARTY_ROOT}/msdf-atlas-gen")
if (ANDROID)
    # Android port: the installer's file picker lists <root>/import instead.
    add_library(nfd STATIC "${CMAKE_SOURCE_DIR}/MarathonRecomp/os/android/nfd_android.cpp")
    target_include_directories(nfd PUBLIC "${MARATHON_RECOMP_THIRDPARTY_ROOT}/nativefiledialog-extended/src/include")
    target_include_directories(nfd PRIVATE "${CMAKE_SOURCE_DIR}/MarathonRecomp")
    add_library(nfd::nfd ALIAS nfd)
else()
    add_subdirectory("${MARATHON_RECOMP_THIRDPARTY_ROOT}/nativefiledialog-extended")
endif()
add_subdirectory("${MARATHON_RECOMP_THIRDPARTY_ROOT}/o1heap")
add_subdirectory("${MARATHON_RECOMP_THIRDPARTY_ROOT}/SDL")
add_subdirectory("${MARATHON_RECOMP_THIRDPARTY_ROOT}/SDL_mixer")
add_subdirectory("${MARATHON_RECOMP_THIRDPARTY_ROOT}/plume")
if (ANDROID)
    # Android port: FFmpeg 7.1.1 + the xmaframes patch, built with the NDK
    # (--disable-everything --enable-decoder=xmaframes), see android/README.
    add_library(ffmpeg INTERFACE)
    target_include_directories(ffmpeg INTERFACE "${MARATHON_ANDROID_FFMPEG}/include")
    target_link_libraries(ffmpeg INTERFACE
        "${MARATHON_ANDROID_FFMPEG}/lib/libavformat.a"
        "${MARATHON_ANDROID_FFMPEG}/lib/libavcodec.a"
        "${MARATHON_ANDROID_FFMPEG}/lib/libswscale.a"
        "${MARATHON_ANDROID_FFMPEG}/lib/libavfilter.a"
        "${MARATHON_ANDROID_FFMPEG}/lib/libswresample.a"
        "${MARATHON_ANDROID_FFMPEG}/lib/libavutil.a")
else()
    add_subdirectory("${MARATHON_RECOMP_THIRDPARTY_ROOT}/ffmpeg-core")
endif()
