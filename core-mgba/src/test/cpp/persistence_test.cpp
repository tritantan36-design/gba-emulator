// TEST ONLY: original homebrew plus mGBA memory APIs, no commercial ROM/BIOS.
#define NOMINMAX
#include <mgba/core/core.h>
#include <mgba/internal/gba/gba.h>
#include <mgba/internal/gba/savedata.h>
#include <mgba-util/vfs.h>
#include <cassert>
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>
#include <cstring>

static mCore* create(const std::vector<char>& rom) {
    auto* c=mCoreCreate(mPLATFORM_GBA); assert(c && c->init(c));
    mCoreInitConfig(c,nullptr);
    mCoreConfigSetDefaultIntValue(&c->config,"skipBios",1);
    mCoreConfigSetDefaultIntValue(&c->config,"useBios",0);
    mCoreLoadForeignConfig(c,&c->config);
    auto* vf=VFileMemChunk(rom.data(),rom.size()); assert(c->loadROM(c,vf));
    assert(c->loadSave(c,VFileMemChunk(nullptr,0))); c->reset(c); return c;
}
static void destroy(mCore* c) { mCoreConfigDeinit(&c->config); c->deinit(c); }
int main(int argc,char** argv) {
    assert(argc==3);
    std::ifstream input(argv[1],std::ios::binary);
    std::vector<char> rom((std::istreambuf_iterator<char>(input)),{});
    for(auto type: {SAVEDATA_SRAM,SAVEDATA_SRAM512,SAVEDATA_FLASH512,SAVEDATA_FLASH1M,SAVEDATA_EEPROM512,SAVEDATA_EEPROM}) {
        auto* c=create(rom); auto* gba=static_cast<GBA*>(c->board);
        GBASavedataForceType(&gba->memory.savedata,type);
        auto size=GBASavedataSize(&gba->memory.savedata); assert(size>0 && size<=131072);
        std::vector<unsigned char> expected(size);
        for(size_t i=0;i<size;++i) expected[i]=static_cast<unsigned char>((i*13+7)&255);
        assert(c->savedataRestore(c,expected.data(),size,true));
        void* bytes=nullptr; assert(c->savedataClone(c,&bytes)==size);
        assert(!std::memcmp(bytes,expected.data(),size)); free(bytes);
        std::vector<unsigned char> state(c->stateSize(c),0); assert(c->saveState(c,state.data()));
        auto changed=expected; changed[0]^=255; assert(c->savedataRestore(c,changed.data(),size,true));
        assert(c->loadState(c,state.data()));
        assert(c->savedataClone(c,&bytes)==size); assert(!std::memcmp(bytes,changed.data(),size)); free(bytes);
        auto corrupt=state; corrupt[0]=255; corrupt[3]=255; assert(!c->loadState(c,corrupt.data()));
        destroy(c);
        auto* reopened=create(rom); auto* next=static_cast<GBA*>(reopened->board);
        GBASavedataForceType(&next->memory.savedata,type);
        assert(reopened->savedataRestore(reopened,expected.data(),size,true));
        assert(reopened->savedataClone(reopened,&bytes)==size); assert(!std::memcmp(bytes,expected.data(),size)); free(bytes);
        if(type==SAVEDATA_SRAM) {
            std::ifstream regression(argv[2],std::ios::binary); std::vector<char> old((std::istreambuf_iterator<char>(regression)),{});
            assert(old.size()==size); assert(reopened->savedataRestore(reopened,old.data(),old.size(),true));
            assert(reopened->savedataClone(reopened,&bytes)==size); assert(!std::memcmp(bytes,old.data(),size)); free(bytes);
        }
        destroy(reopened);
    }
    std::cout<<"PASS: six SRAM/FLASH/EEPROM variants, state preserves battery, restart, bad magic, fixed v1 .sav regression\n";
}
