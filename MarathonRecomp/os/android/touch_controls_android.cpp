// Android port: on-screen touch controls.
//
// The launcher's TouchControlsView (Java) draws an Xbox 360 style pad over the game and sends
// its state here. It feeds an SDL virtual game controller, so the game sees an ordinary
// controller: Marathon's input code switches to it on use and back to a real pad when that
// one is used. Rumble the game sends to it is passed back to Java to vibrate the phone.
//
// Button i of the virtual controller is SDL_CONTROLLER_BUTTON_* i (A, B, X, Y, Back, Guide,
// Start, LS, RS, LB, RB, DUp, DDown, DLeft, DRight); the triggers are axes 4 and 5.

#include <SDL.h>
#include <SDL_system.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <mutex>

#include <os/logger.h>

namespace
{
    constexpr int kButtons = 15;
    constexpr int kAxes = 6;

    std::atomic<bool> g_enabled{ false };
    std::atomic<bool> g_rumble{ true };

    std::mutex g_mutex;
    SDL_Joystick* g_joystick = nullptr;
    int g_lastButtons = 0;
    Sint16 g_lastAxes[kAxes] = { 0, 0, 0, 0, -32768, -32768 };

    jclass g_viewClass = nullptr;
    jmethodID g_onRumble = nullptr;
    Uint16 g_lastLow = 0, g_lastHigh = 0;

    int SDLCALL Rumble(void*, Uint16 low, Uint16 high)
    {
        if (!g_rumble.load() || !g_viewClass || !g_onRumble)
            return 0;
        if (low == g_lastLow && high == g_lastHigh)
            return 0;
        g_lastLow = low;
        g_lastHigh = high;

        auto* env = static_cast<JNIEnv*>(SDL_AndroidGetJNIEnv());
        if (!env)
            return 0;
        env->CallStaticVoidMethod(g_viewClass, g_onRumble, jint(low), jint(high));
        if (env->ExceptionCheck())
            env->ExceptionClear();
        return 0;
    }

    void PushState(int buttons, const Sint16 axes[kAxes])
    {
        if (!g_joystick)
            return;
        for (int i = 0; i < kButtons; i++)
        {
            bool now = (buttons >> i) & 1;
            if (now != (((g_lastButtons >> i) & 1) != 0))
                SDL_JoystickSetVirtualButton(g_joystick, i, now ? SDL_PRESSED : SDL_RELEASED);
        }
        for (int i = 0; i < kAxes; i++)
        {
            if (axes[i] != g_lastAxes[i])
                SDL_JoystickSetVirtualAxis(g_joystick, i, axes[i]);
        }
        g_lastButtons = buttons;
        std::copy(axes, axes + kAxes, g_lastAxes);
    }
}

// Called by hid::Init once the game controller subsystem is up (game thread).
void AndroidTouchControls_Attach()
{
    if (!g_enabled.load())
        return;

    std::lock_guard lock(g_mutex);
    if (g_joystick)
        return;

    SDL_VirtualJoystickDesc desc{};
    desc.version = SDL_VIRTUAL_JOYSTICK_DESC_VERSION;
    desc.type = SDL_JOYSTICK_TYPE_GAMECONTROLLER;
    desc.naxes = kAxes;
    desc.nbuttons = kButtons;
    // Report as an Xbox 360 controller so the game shows Xbox button prompts.
    desc.vendor_id = 0x045E;
    desc.product_id = 0x028E;
    desc.name = "Touch Controls";
    desc.Rumble = Rumble;

    int index = SDL_JoystickAttachVirtualEx(&desc);
    if (index < 0)
    {
        LOGFN_ERROR("Touch controls: virtual controller failed: {}", SDL_GetError());
        return;
    }
    g_joystick = SDL_JoystickOpen(index);
    if (!g_joystick)
    {
        LOGFN_ERROR("Touch controls: opening the virtual controller failed: {}", SDL_GetError());
        return;
    }
    // Triggers rest at the bottom of their range.
    SDL_JoystickSetVirtualAxis(g_joystick, SDL_CONTROLLER_AXIS_TRIGGERLEFT, -32768);
    SDL_JoystickSetVirtualAxis(g_joystick, SDL_CONTROLLER_AXIS_TRIGGERRIGHT, -32768);
    LOGN("Touch controls: on");
}

extern "C" JNIEXPORT void JNICALL
Java_com_marathonrecomp_android_TouchControlsView_nativeConfigure(JNIEnv* env, jclass cls, jboolean enabled, jboolean rumble)
{
    g_enabled = enabled;
    g_rumble = rumble;
    if (!g_viewClass)
    {
        g_viewClass = static_cast<jclass>(env->NewGlobalRef(cls));
        g_onRumble = env->GetStaticMethodID(cls, "onRumble", "(II)V");
        if (env->ExceptionCheck())
        {
            env->ExceptionClear();
            g_onRumble = nullptr;
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_marathonrecomp_android_TouchControlsView_nativeSetRumble(JNIEnv*, jclass, jboolean rumble)
{
    g_rumble = rumble;
}

extern "C" JNIEXPORT void JNICALL
Java_com_marathonrecomp_android_TouchControlsView_nativeSetState(JNIEnv*, jclass, jint buttons,
    jint lx, jint ly, jint rx, jint ry, jint lt, jint rt)
{
    std::lock_guard lock(g_mutex);
    Sint16 axes[kAxes] = {
        Sint16(std::clamp<int>(lx, -32768, 32767)),
        Sint16(std::clamp<int>(ly, -32768, 32767)),
        Sint16(std::clamp<int>(rx, -32768, 32767)),
        Sint16(std::clamp<int>(ry, -32768, 32767)),
        // Joystick triggers use the full range; the controller mapping turns it into 0..32767.
        Sint16(std::clamp<int>(lt, 0, 32767) * 2 - 32768),
        Sint16(std::clamp<int>(rt, 0, 32767) * 2 - 32768),
    };
    PushState(buttons, axes);
}
