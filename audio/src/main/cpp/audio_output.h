#pragma once
#include "pcm_ring.h"
#include <oboe/Oboe.h>
#include <memory>

namespace gbalite {
class AudioOutput final : public oboe::AudioStreamDataCallback {
    PcmRing<> ring_;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<uint64_t> underruns_{0};
#ifdef GBA_TEST_HOOKS
    std::atomic<uint64_t> played_{0};
#endif
public:
    ~AudioOutput() { stop(); }
    bool start();
    void stop();
    size_t push(const int16_t* data, size_t frames) { return ring_.push(data, frames); }
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void* data, int32_t frames) override {
        if(ring_.pop(static_cast<int16_t*>(data), static_cast<size_t>(frames)) < static_cast<size_t>(frames))
            underruns_.fetch_add(1,std::memory_order_relaxed);
#ifdef GBA_TEST_HOOKS
        auto* pcm = static_cast<int16_t*>(data);
        uint64_t count = 0;
        for (int32_t i = 0; i < frames * 2; ++i) if (pcm[i] != 0) ++count;
        played_.fetch_add(count, std::memory_order_relaxed);
#endif
        return oboe::DataCallbackResult::Continue;
    }
    uint64_t underruns() const { return underruns_.load(); }
#ifdef GBA_TEST_HOOKS
    uint64_t playedForTest() const { return played_.load(); }
#endif
};
}
