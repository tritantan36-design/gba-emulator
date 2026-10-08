#pragma once
#include <array>
#include <atomic>
#include <cstdint>
#include <algorithm>
#include <cstddef>

namespace gbalite {
// One producer (emulation), one consumer (Oboe). Indices count stereo frames.
template<size_t Capacity = 4096> class PcmRing {
    static_assert(Capacity > 0 && (Capacity & (Capacity - 1)) == 0, "Capacity must be a power of two");
    std::array<int16_t, Capacity * 2> samples_{};
    std::atomic<uint64_t> write_{0}, read_{0};
public:
    size_t push(const int16_t* data, size_t frames) {
        auto w = write_.load(std::memory_order_relaxed);
        auto r = read_.load(std::memory_order_acquire);
        auto count = std::min(frames, Capacity - static_cast<size_t>(w - r));
        for (size_t i = 0; i < count; ++i) {
            samples_[((w + i) % Capacity) * 2] = data[i * 2];
            samples_[((w + i) % Capacity) * 2 + 1] = data[i * 2 + 1];
        }
        write_.store(w + count, std::memory_order_release);
        return count;
    }
    size_t pop(int16_t* data, size_t frames) {
        auto r = read_.load(std::memory_order_relaxed);
        auto w = write_.load(std::memory_order_acquire);
        auto count = std::min(frames, static_cast<size_t>(w - r));
        for (size_t i = 0; i < count; ++i) {
            data[i * 2] = samples_[((r + i) % Capacity) * 2];
            data[i * 2 + 1] = samples_[((r + i) % Capacity) * 2 + 1];
        }
        read_.store(r + count, std::memory_order_release);
        std::fill(data + count * 2, data + frames * 2, int16_t{0});
        return count;
    }
    // Only while BOTH producer and consumer are stopped.
    void clear() { read_.store(0); write_.store(0); }
};
}
