// TEST ONLY: source-built mGBA frame/PCM/input smoke test without Android/Oboe.
#define NOMINMAX
#include <mgba/core/core.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>
#include <array>
#include <algorithm>
#include <cassert>
#include <fstream>
#include <iterator>
#include <iostream>
#include <vector>

int main(int argc, char** argv) {
    assert(argc == 2);
    std::ifstream stream(argv[1], std::ios::binary);
    std::vector<char> rom((std::istreambuf_iterator<char>(stream)), std::istreambuf_iterator<char>());
    assert(rom.size() >= 192);
    mCore* core = mCoreCreate(mPLATFORM_GBA);
    assert(core && core->init(core));
    mCoreInitConfig(core, nullptr);
    mCoreConfigSetDefaultIntValue(&core->config, "skipBios", 1);
    mCoreConfigSetDefaultIntValue(&core->config, "useBios", 0);
    mCoreConfigSetDefaultIntValue(&core->config, "volume", 256);
    mCoreLoadForeignConfig(core, &core->config);
    std::array<color_t, 240 * 160> pixels{};
    core->setVideoBuffer(core, pixels.data(), 240);
    core->setAudioBufferSize(core, 2048);
    std::array<char,256> invalid{};
    auto* bad = VFileMemChunk(invalid.data(), invalid.size());
    assert(bad && !core->isROM(bad)); bad->close(bad);
    auto* vf = VFileMemChunk(rom.data(), rom.size());
    assert(vf && core->isROM(vf)); vf->seek(vf,0,SEEK_SET);
    assert(core->loadROM(core,vf)); core->reset(core);
    auto* left = core->getAudioChannel(core,0);
    auto* right = core->getAudioChannel(core,1);
    blip_set_rates(left,core->frequency(core),32768);
    blip_set_rates(right,core->frequency(core),32768);
    std::array<int16_t,4096> pcm{};
    size_t nonzero = 0;
    auto frames = [&](int count) {
        for (int f=0; f<count; ++f) {
            core->runFrame(core);
            int n=std::min({blip_samples_avail(left),blip_samples_avail(right),2048});
            if (n>0) {
                blip_read_samples(left,pcm.data(),n,1); blip_read_samples(right,pcm.data()+1,n,1);
                for(int i=0;i<n*2;++i) if(pcm[i]) ++nonzero;
            }
        }
    };
    frames(10);
    auto initial = pixels[0] & 0x00ffffff;
    assert(initial == 0x000000ff); // red RGB888
    assert(std::all_of(pixels.begin(),pixels.end(),[&](color_t p) { return (p & 0x00ffffff) == initial; }));
    for (int bit=0;bit<10;++bit) {
        core->setKeys(core,1u << bit); frames(10);
        assert((pixels[0] & 0x00ffffff) != initial);
        core->setKeys(core,0); frames(10);
        assert((pixels[0] & 0x00ffffff) == initial);
    }
    frames(300); // ~5 seconds of emulated time, no host real-time requirement
    assert(nonzero > 1000);
    std::cout << "PASS: 510 frames, 10 GBA keys, invalid ROM, non-silent stereo PCM=" << nonzero << "\n";
    mCoreConfigDeinit(&core->config); core->deinit(core);
}
