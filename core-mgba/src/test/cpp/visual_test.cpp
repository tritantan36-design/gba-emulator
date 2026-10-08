// Apache-2.0 TEST ONLY: deterministic frame 300 of project-authored patterns.
#define NOMINMAX
#include <mgba/core/core.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>
#include <array>
#include <algorithm>
#include <cassert>
#include <fstream>
#include <iterator>
#include <vector>
#include <iostream>
int main(int argc,char** argv) {
    assert(argc==3);
    std::ifstream in(argv[1],std::ios::binary), golden(argv[2],std::ios::binary);
    std::vector<char> rom((std::istreambuf_iterator<char>(in)),{});
    std::vector<unsigned char> expected((std::istreambuf_iterator<char>(golden)),{});
    assert(expected.size()==240*160*4);
    mCore* core=mCoreCreate(mPLATFORM_GBA);assert(core && core->init(core));
    mCoreInitConfig(core,nullptr);
    mCoreConfigSetDefaultIntValue(&core->config,"skipBios",1);
    mCoreConfigSetDefaultIntValue(&core->config,"useBios",0);mCoreLoadForeignConfig(core,&core->config);
    std::array<color_t,240*160> pixels{};core->setVideoBuffer(core,pixels.data(),240);core->setAudioBufferSize(core,2048);
    auto* vf=VFileFromConstMemory(rom.data(),rom.size());assert(vf && core->isROM(vf));vf->seek(vf,0,SEEK_SET);
    assert(core->loadROM(core,vf));core->reset(core);
    auto* left=core->getAudioChannel(core,0);auto* right=core->getAudioChannel(core,1);
    blip_set_rates(left,core->frequency(core),32768);blip_set_rates(right,core->frequency(core),32768);
    std::array<int16_t,4096> pcm{};
    for(int frame=0;frame<300;++frame) {
        core->runFrame(core);
        int n=std::min({blip_samples_avail(left),blip_samples_avail(right),2048});
        if(n>0) { blip_read_samples(left,pcm.data(),n,1);blip_read_samples(right,pcm.data()+1,n,1); }
    }
    for(size_t i=0;i<pixels.size();++i) {
        auto p=pixels[i]; assert((p&255)==expected[i*4]);assert(((p>>8)&255)==expected[i*4+1]);assert(((p>>16)&255)==expected[i*4+2]);
    }
    std::cout<<"PASS: frame 300 raw RGB matches all 38400 original pattern pixels: "<<argv[1]<<"\n";
    mCoreConfigDeinit(&core->config);core->deinit(core);
}
