// Android port: SDLActivity runs SDL_main from libmain.so on a Java thread
// with a ~1 MiB stack. The game's main() (compiled as MarathonMain, see
// CMakeLists.txt) runs the first guest thread on the calling thread, so give it
// the 8+ MiB stack it gets on desktop Linux.

#include <SDL.h>

#include <jni.h>
#include <pthread.h>

#include <thread>

int MarathonMain(int argc, char* argv[]);

// The thread the game runs on (app.cpp and achievement_overlay.cpp compare against it; on
// desktop they capture it in a static initialiser, which here would be the Java UI thread).
std::thread::id g_androidMainThreadId;

namespace
{
    struct MainArgs
    {
        int argc;
        char** argv;
        int result;
    };

    void* MainThread(void* data)
    {
        auto* args = static_cast<MainArgs*>(data);
        g_androidMainThreadId = std::this_thread::get_id();
        args->result = MarathonMain(args->argc, args->argv);
        return nullptr;
    }
}

extern "C" __attribute__((visibility("default"))) int SDL_main(int argc, char* argv[])
{
    SDL_SetMainReady();

    // The game is designed for a landscape TV; never let SDL rotate to portrait.
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");
    // Touches act as mouse clicks so the installer and menus can be tapped.
    SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS, "1");
    // Pause the event loop while the app is in the background.
    SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE, "1");
    // The back gesture must not close the game mid-play.
    SDL_SetHint(SDL_HINT_ANDROID_TRAP_BACK_BUTTON, "1");

    MainArgs args{ argc, argv, 0 };
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, 64 * 1024 * 1024);
    pthread_t thread;
    if (pthread_create(&thread, &attr, MainThread, &args) != 0)
    {
        g_androidMainThreadId = std::this_thread::get_id();
        return MarathonMain(argc, argv);
    }

    pthread_attr_destroy(&attr);
    pthread_join(thread, nullptr);
    return args.result;
}

// The launcher's exit confirmation (back button): quit the way the PC version does when its
// window is closed. Marathon's window event watch handles SDL_QUIT by saving its settings and
// ending the process (App::Exit), unless the game is saving, in which case Java asks again.
extern "C" JNIEXPORT void JNICALL Java_com_marathonrecomp_android_GameActivity_nativeRequestExit(JNIEnv*, jclass)
{
    SDL_Event event{};
    event.type = SDL_QUIT;
    SDL_PushEvent(&event);
}
