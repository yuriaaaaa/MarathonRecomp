#pragma once

// Android port: NDK r27 ships libc++ 18, which has no std::atomic_ref (added
// in libc++ 19). The game uses it on plain integers in guest memory for
// compare-exchange, store and C++20 wait/notify, so provide just that, built
// on the compiler's __atomic builtins and the futex syscall.

#include <atomic>
#include <memory>
#include <type_traits>

#if defined(__ANDROID__) && defined(_LIBCPP_VERSION) && _LIBCPP_VERSION < 190000

#include <linux/futex.h>
#include <sched.h>
#include <sys/syscall.h>
#include <unistd.h>

namespace std
{
    template <class T>
    struct atomic_ref
    {
        static_assert(std::is_trivially_copyable_v<T>);
        static_assert(sizeof(T) == 1 || sizeof(T) == 2 || sizeof(T) == 4 || sizeof(T) == 8);

        using value_type = T;
        static constexpr size_t required_alignment = alignof(T);
        static constexpr bool is_always_lock_free = __atomic_always_lock_free(sizeof(T), 0);

        explicit atomic_ref(T& object) noexcept : m_ptr(std::addressof(object)) {}
        atomic_ref(const atomic_ref&) noexcept = default;
        atomic_ref& operator=(const atomic_ref&) = delete;

        T load(memory_order order = memory_order_seq_cst) const noexcept
        {
            T value;
            __atomic_load(m_ptr, &value, int(order));
            return value;
        }

        void store(T desired, memory_order order = memory_order_seq_cst) const noexcept
        {
            __atomic_store(m_ptr, &desired, int(order));
        }

        T operator=(T desired) const noexcept
        {
            store(desired);
            return desired;
        }

        operator T() const noexcept { return load(); }

        T exchange(T desired, memory_order order = memory_order_seq_cst) const noexcept
        {
            T previous;
            __atomic_exchange(m_ptr, &desired, &previous, int(order));
            return previous;
        }

        bool compare_exchange_weak(T& expected, T desired, memory_order success, memory_order failure) const noexcept
        {
            return __atomic_compare_exchange(m_ptr, &expected, &desired, true, int(success), int(failure));
        }

        bool compare_exchange_weak(T& expected, T desired, memory_order order = memory_order_seq_cst) const noexcept
        {
            return compare_exchange_weak(expected, desired, order, FailureOrder(order));
        }

        bool compare_exchange_strong(T& expected, T desired, memory_order success, memory_order failure) const noexcept
        {
            return __atomic_compare_exchange(m_ptr, &expected, &desired, false, int(success), int(failure));
        }

        bool compare_exchange_strong(T& expected, T desired, memory_order order = memory_order_seq_cst) const noexcept
        {
            return compare_exchange_strong(expected, desired, order, FailureOrder(order));
        }

        template <class U = T, class = std::enable_if_t<std::is_integral_v<U>>>
        T fetch_add(T value, memory_order order = memory_order_seq_cst) const noexcept
        {
            return __atomic_fetch_add(m_ptr, value, int(order));
        }

        template <class U = T, class = std::enable_if_t<std::is_integral_v<U>>>
        T fetch_sub(T value, memory_order order = memory_order_seq_cst) const noexcept
        {
            return __atomic_fetch_sub(m_ptr, value, int(order));
        }

        void wait(T old, memory_order order = memory_order_seq_cst) const noexcept
        {
            for (uint32_t spin = 0; ; spin++)
            {
                T current = load(order);
                if (!BitEqual(current, old))
                    return;

                if constexpr (sizeof(T) == 4)
                {
                    if (spin >= 16)
                    {
                        uint32_t expected;
                        __builtin_memcpy(&expected, &old, 4);
                        syscall(SYS_futex, reinterpret_cast<uint32_t*>(m_ptr), FUTEX_WAIT_PRIVATE, expected, nullptr, nullptr, 0);
                        continue;
                    }
                }
                else if (spin >= 16)
                {
                    usleep(50);
                    continue;
                }

                sched_yield();
            }
        }

        void notify_one() const noexcept
        {
            if constexpr (sizeof(T) == 4)
                syscall(SYS_futex, reinterpret_cast<uint32_t*>(m_ptr), FUTEX_WAKE_PRIVATE, 1, nullptr, nullptr, 0);
        }

        void notify_all() const noexcept
        {
            if constexpr (sizeof(T) == 4)
                syscall(SYS_futex, reinterpret_cast<uint32_t*>(m_ptr), FUTEX_WAKE_PRIVATE, INT32_MAX, nullptr, nullptr, 0);
        }

    private:
        static constexpr memory_order FailureOrder(memory_order order) noexcept
        {
            return order == memory_order_acq_rel ? memory_order_acquire
                : order == memory_order_release ? memory_order_relaxed
                : order;
        }

        static bool BitEqual(const T& a, const T& b) noexcept
        {
            return __builtin_memcmp(&a, &b, sizeof(T)) == 0;
        }

        T* m_ptr;
    };
}

#endif
