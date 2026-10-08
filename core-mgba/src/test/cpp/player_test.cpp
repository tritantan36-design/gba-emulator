// Original TEST ONLY homebrew and wrapper-owned rewind ring, no external ROM.
#define NOMINMAX
#include "../../main/cpp/rewind_ring.h"
#include <mgba/core/core.h>
#include <mgba-util/vfs.h>
#include <cassert>
#include <fstream>
#include <iostream>
#include <iterator>
#include <array>
int main(int argc,char** argv) {
    assert(argc==2); std::ifstream file(argv[1],std::ios::binary);
    std::vector<char> rom((std::istreambuf_iterator<char>(file)),{});
    auto* c=mCoreCreate(mPLATFORM_GBA); assert(c && c->init(c)); mCoreInitConfig(c,nullptr);
    mCoreConfigSetDefaultIntValue(&c->config,"skipBios",1); mCoreLoadForeignConfig(c,&c->config);
    // Detection must never expand a ROM when trailing bytes resemble an ARM literal load.
    const char suffix[]="PHASE3_TEST_ONLY"; rom.insert(rom.end(),suffix,suffix+sizeof(suffix)-1);
    auto* vf=VFileFromConstMemory(rom.data(),rom.size());assert(vf);
    assert(c->isROM(vf)); assert(vf->size(vf)==static_cast<ssize_t>(rom.size()));
    assert(c->loadROM(c,vf));
    assert(c->loadSave(c,VFileMemChunk(nullptr,0)));
    std::array<uint32_t,240*160> pixels{}; c->setVideoBuffer(c,pixels.data(),240); c->reset(c);
    gbalite::RewindRing ring;
    for(int frame=0;frame<2400;++frame) {
        c->runFrame(c);
        if(frame%12==0) {
            gbalite::RewindSnapshot s; s.state.resize(c->stateSize(c)); assert(c->saveState(c,s.state.data()));
            s.frame.assign(pixels.begin(),pixels.end()); ring.push(std::move(s));
            assert(ring.bytes()<=gbalite::RewindRing::cap); assert(ring.size()<=150);
        }
    }
    std::cout<<"state="<<c->stateSize(c)<<" ring="<<ring.size()<<" bytes="<<ring.bytes()<<" seconds="<<ring.size()*12.0* c->frameCycles(c)/c->frequency(c)<<"\n";
    assert(ring.size()>=75); // at least ~15 sec at 201 ms
    gbalite::RewindSnapshot out; int restored=0;
    while(ring.pop(out)) { assert(c->loadState(c,out.state.data())); ++restored; }
    assert(restored>=75 && ring.bytes()==0); ring.clear(); assert(!ring.pop(out));
    ring.trim();
    for(int i=0;i<200;++i) {
        gbalite::RewindSnapshot snapshot; snapshot.state.resize(1024*1024);
        ring.push(std::move(snapshot)); assert(ring.bytes()<=gbalite::RewindRing::cap/4);
    }
    assert(ring.size()==16); ring.clear(); assert(ring.bytes()==0);
    mCoreConfigDeinit(&c->config); c->deinit(c);
    std::cout<<"PASS capture/restore/wrap/cap/free\n";
}
