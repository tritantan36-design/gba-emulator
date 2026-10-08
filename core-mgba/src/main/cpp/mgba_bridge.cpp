#include "mgba_bridge.h"
#include <mgba/core/core.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/cart/gpio.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>
#include <algorithm>
#include <chrono>
#include <cstring>
#include <cstdlib>
#include <memory>
#include <stdexcept>
#include <vector>
#include <unistd.h>
#include <poll.h>
#include <cerrno>

namespace gbalite {
Core::Core() {
    rotation_.d.sample=[](mRotationSource*) {};
    rotation_.d.readTiltX=[](mRotationSource* s) { return reinterpret_cast<Rotation*>(s)->owner->latchedPeripheral_[0]; };
    rotation_.d.readTiltY=[](mRotationSource* s) { return reinterpret_cast<Rotation*>(s)->owner->latchedPeripheral_[1]; };
    rotation_.d.readGyroZ=[](mRotationSource* s) { return reinterpret_cast<Rotation*>(s)->owner->latchedPeripheral_[2]; };
    light_.d.sample=[](GBALuminanceSource*) {};
    light_.d.readLuminance=[](GBALuminanceSource* s) { return static_cast<uint8_t>(reinterpret_cast<Light*>(s)->owner->latchedPeripheral_[3]); };
    rumble_.d.setRumble=[](mRumble* s,int enable) { reinterpret_cast<Rumble*>(s)->owner->rumbleOn_=enable!=0; };
    core_ = mCoreCreate(mPLATFORM_GBA);
    if (!core_) throw std::runtime_error("create");
    if (!core_->init(core_)) { free(core_); core_ = nullptr; throw std::runtime_error("init"); }
    mCoreInitConfig(core_, nullptr);
    mCoreConfigSetDefaultIntValue(&core_->config, "skipBios", 1);
    mCoreConfigSetDefaultIntValue(&core_->config, "useBios", 0);
    mCoreConfigSetDefaultIntValue(&core_->config, "volume", 256);
    mCoreConfigSetDefaultIntValue(&core_->config, "sampleRate", 32768);
    mCoreLoadForeignConfig(core_, &core_->config); // does not load any host config file
    core_->setVideoBuffer(core_, pixels_.data(), 240);
    core_->setAudioBufferSize(core_, 2048);
    core_->setPeripheral(core_,mPERIPH_ROTATION,&rotation_.d);
    core_->setPeripheral(core_,mPERIPH_GBA_LUMINANCE,&light_.d);
    core_->setPeripheral(core_,mPERIPH_RUMBLE,&rumble_.d);
    try { worker_ = std::thread(&Core::loop, this); }
    catch (...) { mCoreConfigDeinit(&core_->config); core_->deinit(core_); core_ = nullptr; throw; }
}
Core::~Core() {
    { std::lock_guard<std::mutex> lock(control_); running_ = false; quit_ = true; wake_.notify_all(); }
    if (worker_.joinable()) worker_.join();
    audio_.stop();
    if (core_) { mCoreConfigDeinit(&core_->config); core_->deinit(core_); }
}
void Core::audioRates() {
    blip_set_rates(core_->getAudioChannel(core_, 0), core_->frequency(core_), 32768);
    blip_set_rates(core_->getAudioChannel(core_, 1), core_->frequency(core_), 32768);
}
int Core::load(int fd, int64_t length) {
    std::lock_guard<std::mutex> lock(control_);
    constexpr size_t maximum = 32 * 1024 * 1024;
    constexpr size_t minimum = 256;
    if (loaded_ || fd < 0) return 1;
    if (length >= 0 && (length < static_cast<int64_t>(minimum) || length > static_cast<int64_t>(maximum))) return 2;
    int owned = dup(fd);
    if (owned < 0) return 1;
    struct OwnedFd { int fd; ~OwnedFd() { ::close(fd); } } descriptor{owned};
    (void) lseek(owned, 0, SEEK_SET); // pipes are allowed
    std::vector<uint8_t> bytes;
    std::array<uint8_t, 65536> chunk{};
    auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(10);
    while (bytes.size() <= maximum) {
        if (std::chrono::steady_clock::now() > deadline) return 1;
        pollfd ready{owned, POLLIN, 0};
        int status = poll(&ready, 1, 250);
        if (status == 0) continue;
        if (status < 0) { if (errno == EINTR) continue; return 1; }
        if (ready.revents & (POLLERR | POLLNVAL)) return 1;
        auto count = read(owned, chunk.data(), std::min(chunk.size(), maximum + 1 - bytes.size()));
        if (count < 0) { if (errno == EINTR) continue; return 1; }
        if (!count) break;
        bytes.insert(bytes.end(), chunk.begin(), chunk.begin() + count);
    }
    if (bytes.size() < minimum || bytes.size() > maximum || bytes[0xB2] != 0x96) return 2;
    if (length >= 0 && bytes.size() != static_cast<size_t>(length)) return 1;
    romBytes_ = std::move(bytes);
    // Pinned VFame detection reads through 0x16B, even for a 256-byte image.
    // Preserve logical file length/CRC/GameId and tiny homebrew compatibility;
    // only the owned backing allocation gets zero padding for those probes.
    const size_t logicalSize = romBytes_.size();
    if (logicalSize < 512) romBytes_.resize(512, 0);
    auto* file = VFileFromConstMemory(romBytes_.data(), logicalSize);
    if (!file) return 3;
    if (!core_->isROM(file)) { file->close(file); return 2; }
    file->seek(file, 0, SEEK_SET);
    if (!core_->loadROM(core_, file)) { file->close(file); return 2; }
    // Memory-only battery file; mGBA never receives a filesystem path.
    auto* battery = VFileMemChunk(nullptr, 0);
    if (!battery) return 3;
    if (!core_->loadSave(core_, battery)) { battery->close(battery); return 3; }
    core_->reset(core_); audioRates(); loaded_ = true;
    detectedPeripheral_=static_cast<GBA*>(core_->board)->memory.hw.devices & 31;
    mountPeripherals(); clearPeripherals();
    return 0;
}
bool Core::start() {
    std::lock_guard<std::mutex> lock(control_);
    if (!loaded_ || quit_) return false;
    if (running_) return true;
    if (speed_ == 1 && !rewinding_ && !audio_.start()) return false;
    running_ = true; wake_.notify_all(); return true;
}
bool Core::pause() {
    std::lock_guard<std::mutex> lock(control_);
    if (quit_) return false;
    running_ = false; speed_ = 1; rewinding_ = false;
    clearPeripherals();
    if(loaded_) { blip_clear(core_->getAudioChannel(core_,0)); blip_clear(core_->getAudioChannel(core_,1)); }
    keys_.store(0); audio_.stop(); wake_.notify_all(); return true;
}
bool Core::reset() {
    std::lock_guard<std::mutex> lock(control_);
    if (!loaded_ || quit_) return false;
    bool wasRunning = running_;
    running_ = false; audio_.stop();
    keys_.store(0); rewind_.clear(); core_->reset(core_); audioRates();
    mountPeripherals(); clearPeripherals();
    { std::lock_guard<std::mutex> frame(frameMutex_); hasFrame_ = false; }
    if (wasRunning && !audio_.start()) return false;
    running_ = wasRunning; wake_.notify_all(); return true;
}
void Core::setButton(uint32_t mask, bool down) {
    mask &= 0x3ff;
    if (down) keys_.fetch_or(mask); else keys_.fetch_and(~mask);
}
bool Core::copyFrame(void* target, size_t capacity) {
    if (!target || capacity < published_.size() * sizeof(uint32_t)) return false;
    std::lock_guard<std::mutex> lock(frameMutex_);
    if (!hasFrame_) return false;
    std::memcpy(target, published_.data(), published_.size() * sizeof(uint32_t));
    return true;
}
std::vector<uint8_t> Core::exportBytes(bool state) {
    std::lock_guard<std::mutex> lock(control_);
    if (!loaded_ || running_ || quit_) throw std::runtime_error("Pause before export");
    if (state) {
        auto size = core_->stateSize(core_);
        if (!size || size > 2 * 1024 * 1024) throw std::runtime_error("State size");
        std::vector<uint8_t> out(size, 0);
        if (!core_->saveState(core_, out.data())) throw std::runtime_error("State export");
        return out;
    }
    void* data = nullptr;
    auto size = core_->savedataClone(core_, &data);
    std::unique_ptr<void, decltype(&free)> owned(data, &free);
    if (!size) return {};
    if (!data || size > 128 * 1024) throw std::runtime_error("Battery export");
    auto* begin = static_cast<uint8_t*>(data);
    return {begin, begin + size};
}
bool Core::importBytes(const std::vector<uint8_t>& bytes, bool state) {
    std::lock_guard<std::mutex> lock(control_);
    if (!loaded_ || running_ || quit_ || bytes.empty()) return false;
    if (!state) {
        if (bytes.size() > 128 * 1024) return false;
        // Before first execution loadSave detects type/size from the memory VFile.
        // After execution savedataRestore updates the currently owned save memory.
        void* existing = nullptr;
        auto size = core_->savedataClone(core_, &existing); free(existing);
        if (size && size != bytes.size()) return false;
        return core_->savedataRestore(core_, bytes.data(), bytes.size(), true);
    }
    if (bytes.size() != core_->stateSize(core_) || bytes.size() > 2 * 1024 * 1024) return false;
    // Enforce the pinned version before upstream touches any state fields.
    if (bytes.size() < 4 || bytes[0] != 7 || bytes[1] != 0 || bytes[2] != 0 || bytes[3] != 1) return false;
    if (!restore(bytes)) return false;
    rewind_.clear();
    keys_.store(0); audioRates();
    { std::lock_guard<std::mutex> frame(frameMutex_); hasFrame_ = false; }
    return true;
}
bool Core::restore(const std::vector<uint8_t>& bytes) {
    void* saved = nullptr;
    auto size = core_->savedataClone(core_, &saved);
    std::unique_ptr<void, decltype(&free)> owned(saved, &free);
    if(size && (!saved || size>128*1024)) return false;
    if (!core_->loadState(core_, bytes.data())) return false;
    mountPeripherals(); clearPeripherals();
    if (size && !core_->savedataRestore(core_, saved, size, true)) return false;
    return true;
}
bool Core::control(int command, int value) {
    std::lock_guard<std::mutex> lock(control_);
    if (!loaded_ || quit_) return false;
    if(command == 2) { rewind_.trim(); return true; }
    if(command == 0) {
        if(value != 1 && value != 2 && value != 4 && value != 8) return false;
        speed_ = value;
    } else if(command == 1) {
        clearPeripherals();
        rewinding_ = value != 0; speed_ = 1; keys_.store(0);
    } else return false;
    audio_.stop();
    if(!rewinding_ && speed_==1) {
        blip_clear(core_->getAudioChannel(core_,0)); blip_clear(core_->getAudioChannel(core_,1));
        audioRates();
    }
    if(running_ && speed_ == 1 && !rewinding_ && !audio_.start()) { running_=false; return false; }
    wake_.notify_all(); return true;
}
std::vector<int64_t> Core::metrics() {
    std::lock_guard<std::mutex> lock(control_);
    return {static_cast<int64_t>(frameCount_),static_cast<int64_t>(lateFrames_),
        static_cast<int64_t>(rewind_.size()),static_cast<int64_t>(rewind_.bytes()),
        static_cast<int64_t>(stateSize_),static_cast<int64_t>(audio_.underruns()),speed_,rewinding_ ? 1 : 0};
}
void Core::clearPeripherals() {
    pendingPeripheral_={0,0,0,233}; latchedPeripheral_=pendingPeripheral_;
    rumbleOn_=false; ++peripheralStopEpoch_;
}
void Core::mountPeripherals() {
    auto* hw=&static_cast<GBA*>(core_->board)->memory.hw;
    const auto add=(detectedPeripheral_|manualPeripheral_)&~hw->devices;
    if(add&HW_RTC) GBAHardwareInitRTC(hw);
    if(add&HW_TILT) GBAHardwareInitTilt(hw);
    if(add&HW_GYRO) GBAHardwareInitGyro(hw);
    if(add&HW_LIGHT_SENSOR) GBAHardwareInitLight(hw);
    if(add&HW_RUMBLE) GBAHardwareInitRumble(hw);
}
void Core::updatePeripherals(int32_t x,int32_t y,int32_t z,int light) {
    std::lock_guard<std::mutex> lock(control_);
    if(!loaded_ || !running_ || rewinding_ || quit_) return;
    pendingPeripheral_={std::clamp(x,-1962000000,1962000000),std::clamp(y,-1962000000,1962000000),
        std::clamp(z,-1650000000,1650000000),std::clamp(light,50,233)};
}
void Core::configurePeripherals(int mask) {
    std::lock_guard<std::mutex> lock(control_);
    if(!loaded_ || quit_) return;
    manualPeripheral_=mask&31; mountPeripherals(); clearPeripherals();
}
std::vector<int64_t> Core::peripheralStatus() {
    std::lock_guard<std::mutex> lock(control_);
    return {detectedPeripheral_,rumbleOn_?1:0,static_cast<int64_t>(peripheralStopEpoch_)};
}
#ifdef GBA_TEST_HOOKS
uint32_t Core::probeReadForTest(int index) {
    std::lock_guard<std::mutex> lock(control_);
    return loaded_ && index>=0 && index<9 ? core_->busRead32(core_,0x02000000+static_cast<uint32_t>(index)*4) : 0;
}
#endif
void Core::loop() {
    using Clock = std::chrono::steady_clock;
    auto nextFrame=Clock::now();
    int previousSpeed=0;
    std::unique_lock<std::mutex> lock(control_);
    while (!quit_) {
        wake_.wait(lock, [this] { return quit_ || running_; });
        if (quit_) break;
        auto begin = Clock::now();
        if(previousSpeed!=speed_ || begin>nextFrame+std::chrono::milliseconds(40)) { nextFrame=begin; previousSpeed=speed_; }
        if(rewinding_) {
            RewindSnapshot snapshot;
            if(rewind_.pop(snapshot) && restore(snapshot.state)) {
                std::lock_guard<std::mutex> frame(frameMutex_);
                std::copy(snapshot.frame.begin(),snapshot.frame.end(),published_.begin()); hasFrame_=true;
            }
            wake_.wait_until(lock,begin+std::chrono::milliseconds(50));
            previousSpeed=0;
            continue;
        }
        latchedPeripheral_=pendingPeripheral_;
        core_->setKeys(core_, keys_.load());
        core_->runFrame(core_);
        ++frameCount_;
#ifdef GBA_TEST_HOOKS
        framesForTest_.fetch_add(1);
#endif
        {
            std::lock_guard<std::mutex> frame(frameMutex_);
            for (size_t i = 0; i < pixels_.size(); ++i) published_[i] = pixels_[i] | 0xff000000u;
            hasFrame_ = true;
        }
        if(frameCount_%12 == 0) {
            try {
                stateSize_=core_->stateSize(core_);
                if(stateSize_ && stateSize_<=2*1024*1024) {
                    RewindSnapshot snapshot;
                    snapshot.state.resize(stateSize_);
                    if(core_->saveState(core_,snapshot.state.data())) {
                        snapshot.frame.assign(published_.begin(),published_.end());
                        rewind_.push(std::move(snapshot));
                    }
                }
            } catch(const std::bad_alloc&) { rewind_.clear(); }
        }
        auto* left = core_->getAudioChannel(core_, 0);
        auto* right = core_->getAudioChannel(core_, 1);
        int count = std::min({blip_samples_avail(left), blip_samples_avail(right), 2048});
        if (count > 0) {
            blip_read_samples(left, pcm_.data(), count, 1);
            blip_read_samples(right, pcm_.data() + 1, count, 1);
#ifdef GBA_TEST_HOOKS
            for (int i = 0; i < count * 2; ++i) if (pcm_[i] != 0) nonzeroSamples_.fetch_add(1);
#endif
            if(speed_ == 1) audio_.push(pcm_.data(), static_cast<size_t>(count));
        }
        auto frameTime = std::chrono::duration<double>(static_cast<double>(core_->frameCycles(core_)) / core_->frequency(core_));
        nextFrame += std::chrono::duration_cast<Clock::duration>(frameTime/speed_);
        auto deadline=nextFrame;
        if(Clock::now()>deadline) ++lateFrames_;
        // An already-expired timed wait may not release control_. Slow devices
        // must still admit pause/speed/metrics callers between emulated frames.
        const auto waitStart=Clock::now();
        if(deadline<=waitStart) {
            lock.unlock();
            std::this_thread::sleep_for(std::chrono::microseconds(50));
            lock.lock();
            continue;
        }
        const auto requestedSpeed=speed_;
        wake_.wait_until(lock, deadline, [this,requestedSpeed] { return quit_ || !running_ || rewinding_ || speed_!=requestedSpeed; });
    }
}
}
