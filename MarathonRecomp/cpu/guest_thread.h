#pragma once

#include <kernel/xdm.h>

// Use pthreads directly on macOS to be able to increase default stack size.
// Android port: same on Android, where bionic threads get ~1 MiB stacks.
#if defined(__APPLE__) || defined(__ANDROID__)
#define USE_PTHREAD 1
#endif

#ifdef USE_PTHREAD
#include <pthread.h>
#endif

#define CURRENT_THREAD_HANDLE uint32_t(-2)

struct GuestThreadContext
{
    PPCContext ppcContext{};
    uint8_t* thread = nullptr;

    GuestThreadContext(uint32_t cpuNumber);
    ~GuestThreadContext();
};

struct GuestThreadParams
{
    uint32_t function;
    uint32_t value;
    uint32_t flags;
};

struct GuestThreadHandle : KernelObject
{
    GuestThreadParams params;
    std::atomic<bool> suspended;
    #ifdef USE_PTHREAD
    pthread_t thread{};
    // A pthread may be joined only once (bionic aborts on a second join, macOS
    // returns ESRCH). Wait() and the destructor can both join, so serialise.
    std::mutex joinMutex;
    bool joined = false;
    void JoinOnce();
    #else
    std::thread thread;
    #endif
    // HACK(1)
    std::atomic<bool> isFinished = false;

    GuestThreadHandle(const GuestThreadParams& params);
    ~GuestThreadHandle() override;

    uint32_t GetThreadId() const;

    uint32_t Wait(uint32_t timeout) override;
};

struct GuestThread
{
    static uint32_t Start(const GuestThreadParams& params);
    static GuestThreadHandle* Start(const GuestThreadParams& params, uint32_t* threadId);

    static uint32_t GetCurrentThreadId();
    static void SetLastError(uint32_t error);

#ifdef _WIN32
    static void SetThreadName(uint32_t threadId, const char* name);
#endif
};
