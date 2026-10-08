// Original test runner, Apache-2.0. MIT upstream test ROM is separate.
#define NOMINMAX
#include <mgba/core/core.h>
#include <mgba/core/blip_buf.h>
#include <mgba-util/vfs.h>
#include <fstream>
#include <iterator>
#include <vector>
#include <iostream>
int main(int argc,char**argv) {
    if(argc!=2) return 2;
    std::ifstream in(argv[1],std::ios::binary);
    std::vector<char> bytes((std::istreambuf_iterator<char>(in)),{});
    if(bytes.size()<192) return 3;
    auto*c=mCoreCreate(mPLATFORM_GBA);if(!c||!c->init(c)) return 4;
    mCoreInitConfig(c,nullptr);mCoreConfigSetDefaultIntValue(&c->config,"skipBios",1);
    mCoreConfigSetDefaultIntValue(&c->config,"useBios",0);mCoreLoadForeignConfig(c,&c->config);
    std::vector<color_t> pixels(240*160);c->setVideoBuffer(c,pixels.data(),240);c->setAudioBufferSize(c,2048);
    if(!c->loadROM(c,VFileFromConstMemory(bytes.data(),bytes.size()))) return 5;
    c->reset(c);
    for(int n=0;n<60;++n){c->runFrame(c);blip_clear(c->getAudioChannel(c,0));blip_clear(c->getAudioChannel(c,1));}
    auto read=[&](int i){return c->busRead32(c,0x02000000+i*4);};
    std::cout<<"magic="<<std::hex<<read(0)<<std::dec<<" passed="<<read(1)<<" total="<<read(2)<<" firstFailed="<<read(3)<<" rd="<<std::hex<<read(4)<<" cpsr="<<read(5)<<"\n";
    bool ok=read(0)==0x37534846 && read(2)>50 && read(1)==read(2);
    mCoreConfigDeinit(&c->config);c->deinit(c);return ok?0:1;
}
