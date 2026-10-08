#pragma once
#include <array>
#include <atomic>
#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <thread>
#include <vector>
#include "audio_output.h"
#include "rewind_ring.h"
#include <mgba/core/interface.h>
#include <mgba/gba/interface.h>
struct mCore;
namespace gbalite {
class Core final {
    struct Rotation { mRotationSource d{}; Core* owner; } rotation_{{},this};
    struct Light { GBALuminanceSource d{}; Core* owner; } light_{{},this};
    struct Rumble { mRumble d{}; Core* owner; } rumble_{{},this};
    std::array<int32_t,4> pendingPeripheral_{0,0,0,233}, latchedPeripheral_{0,0,0,233};
    int detectedPeripheral_=0, manualPeripheral_=0;
    bool rumbleOn_=false;
    uint64_t peripheralStopEpoch_=0;
    void clearPeripherals();
    void mountPeripherals();
    mCore* core_ = nullptr;
    std::vector<uint8_t> romBytes_;
    std::mutex control_, frameMutex_;
    std::condition_variable wake_;
    std::thread worker_;
    bool loaded_ = false, running_ = false, quit_ = false, hasFrame_ = false;
    std::atomic<uint32_t> keys_{0};
    std::array<uint32_t, 240 * 160> pixels_{}, published_{};
    std::array<int16_t, 2048 * 2> pcm_{};
    AudioOutput audio_;
    RewindRing rewind_;
    int speed_ = 1;
    bool rewinding_ = false;
    uint64_t frameCount_ = 0, lateFrames_ = 0;
    size_t stateSize_ = 0;
    bool restore(const std::vector<uint8_t>& bytes);
#ifdef GBA_TEST_HOOKS
    std::atomic<uint64_t> nonzeroSamples_{0};
    std::atomic<uint64_t> framesForTest_{0};
#endif
    void loop();
    void audioRates();
public:
    Core();
    ~Core();
    Core(const Core&) = delete;
    Core& operator=(const Core&) = delete;
    int load(int fd, int64_t length);
    bool start();
    bool pause();
    bool reset();
    void setButton(uint32_t mask, bool down);
    bool copyFrame(void* target, size_t capacity);
    std::vector<uint8_t> exportBytes(bool state);
    bool importBytes(const std::vector<uint8_t>& bytes, bool state);
    bool control(int command, int value);
    std::vector<int64_t> metrics();
    void updatePeripherals(int32_t x,int32_t y,int32_t z,int light);
    void configurePeripherals(int mask);
    std::vector<int64_t> peripheralStatus();
#ifdef GBA_TEST_HOOKS
    uint64_t nonzeroSamples() const { return nonzeroSamples_.load(); }
    uint32_t probeReadForTest(int index);
    uint64_t framesForTest() const { return framesForTest_.load(); }
    uint64_t playedForTest() const { return audio_.playedForTest(); }
#endif
};
}
