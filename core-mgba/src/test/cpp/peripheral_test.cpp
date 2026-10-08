// Original probe + pinned core bus, Apache-2.0. No Android/synthetic-event claims.
#define NOMINMAX
#include <mgba/core/core.h>
#include <mgba/core/blip_buf.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/cart/gpio.h>
#include <mgba-util/vfs.h>
#include <cassert>
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>
#include <ctime>
#include <cstdlib>
#ifdef _WIN32
#include <crtdbg.h>
#endif
#include <cstdio>
static int32_t tx=0,ty=0,gz=0;
static uint8_t sunlight=233;
static bool motor=false;
static mRotationSource rotation{[](mRotationSource*){},[](mRotationSource*){return tx;},[](mRotationSource*){return ty;},[](mRotationSource*){return gz;}};
static GBALuminanceSource light{[](GBALuminanceSource*){},[](GBALuminanceSource*){return sunlight;}};
static mRumble rumble{[](mRumble*,int on){motor=on!=0;}};
static void setTestTimezone(const char* value) {
#ifdef _WIN32
    _putenv_s("TZ",value);_tzset();
#else
    setenv("TZ",value,1);tzset();
#endif
}
int main(int argc,char**argv) {
#ifdef _WIN32
    _CrtSetReportMode(_CRT_ASSERT,_CRTDBG_MODE_FILE);_CrtSetReportFile(_CRT_ASSERT,_CRTDBG_FILE_STDERR);
    _set_abort_behavior(0,_WRITE_ABORT_MSG|_CALL_REPORTFAULT);
#endif
    assert(argc==2);
    setTestTimezone("UTC0");
    for(const auto* name:{"rtc-probe","tilt-probe","rotation-probe","solar-probe","rumble-probe"}) {
        std::ifstream input(std::string(argv[1])+"/"+name+".gba",std::ios::binary);
        std::vector<char> bytes((std::istreambuf_iterator<char>(input)),{});assert(!bytes.empty());
        auto* c=mCoreCreate(mPLATFORM_GBA);assert(c && c->init(c));mCoreInitConfig(c,nullptr);
        mCoreConfigSetDefaultIntValue(&c->config,"skipBios",1);mCoreConfigSetDefaultIntValue(&c->config,"useBios",0);mCoreLoadForeignConfig(c,&c->config);
        std::vector<color_t> pixels(240*160);c->setVideoBuffer(c,pixels.data(),240);c->setAudioBufferSize(c,2048);
        c->setPeripheral(c,mPERIPH_ROTATION,&rotation);c->setPeripheral(c,mPERIPH_GBA_LUMINANCE,&light);c->setPeripheral(c,mPERIPH_RUMBLE,&rumble);
        assert(c->loadROM(c,VFileFromConstMemory(bytes.data(),bytes.size())));assert(c->loadSave(c,VFileMemChunk(nullptr,0)));c->reset(c);
        const auto read=[&](int index){return c->busRead32(c,0x02000000+index*4);};
        const auto frames=[&](){for(int i=0;i<60;i++) { c->runFrame(c);blip_clear(c->getAudioChannel(c,0));blip_clear(c->getAudioChannel(c,1)); } assert(read(0)==0x50423547);};
        const std::string id=name;
        if(id=="rtc-probe") {
            c->rtc.override=RTC_FIXED;c->rtc.value=1709251198000LL;frames();
            assert(read(2)==0x24 && read(3)==2 && read(4)==0x29 && read(6)==0x23 && read(7)==0x59 && read(8)==0x58);
            for(int i=0;i<480;i++) c->runFrame(c);assert(read(8)==0x58);
            std::vector<uint8_t> state(c->stateSize(c));assert(c->saveState(c,state.data()));
            c->rtc.value+=2000;assert(c->loadState(c,state.data()));frames();
            assert(read(3)==3 && read(4)==1 && read(6)==0 && read(7)==0 && read(8)==0);
            setTestTimezone("UTC-2");frames();assert(read(6)==2);
            setTestTimezone("UTC0");c->rtc.value-=2000;frames();assert(read(4)==0x29 && read(8)==0x58);
        } else if(id=="tilt-probe") {
            assert(static_cast<GBA*>(c->board)->memory.hw.devices&HW_TILT);
            tx=0;ty=0;frames();assert(read(2)==0x3a0 && read(3)==0x3a0);
            tx=1073741824;ty=-1073741824;frames();assert(read(2)==0x2a0 && read(3)==0x4a0);
        } else if(id=="rotation-probe") {
            assert(static_cast<GBA*>(c->board)->memory.hw.devices&HW_GYRO);
            gz=0;frames();assert(read(4)==0x700);
            gz=1073741824;frames();assert(read(4)==0x900);
            gz=-1073741824;frames();assert(read(4)==0x500);
        } else if(id=="solar-probe") {
            sunlight=233;frames();assert(read(2)==233);
            sunlight=50;frames();assert(read(2)==50);
        } else {
            c->setKeys(c,0);frames();assert(!motor && read(2)==0);
            c->setKeys(c,1);frames();assert(motor && read(2)==1);
            c->setKeys(c,0);frames();assert(!motor);
        }
        mCoreConfigDeinit(&c->config);c->deinit(c);std::cout<<"PASS real ROM bus: "<<name<<"\n";
    }
}
