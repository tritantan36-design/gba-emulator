// TEST ONLY Apache-2.0; bounded parser, no CPU execution of untrusted instructions.
#define NOMINMAX
#include <mgba/core/core.h>
#include <mgba-util/vfs.h>
#include <cstdint>
#include <cstddef>
extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data,size_t size) {
    if(size<192 || size>4*1024*1024 || data[0xB2]!=0x96) return 0;
    auto* c=mCoreCreate(mPLATFORM_GBA);
    if(!c) return 0;
    if(!c->init(c)) {c->deinit(c);return 0;}
    mCoreInitConfig(c,nullptr);
    mCoreConfigSetDefaultIntValue(&c->config,"skipBios",1);
    mCoreConfigSetDefaultIntValue(&c->config,"useBios",0);
    mCoreLoadForeignConfig(c,&c->config);
    auto* vf=VFileFromConstMemory(data,size);
    if(vf) {
        if(c->isROM(vf)) {
            vf->seek(vf,0,SEEK_SET);
            if(!c->loadROM(c,vf)) vf->close(vf);
            // On success the core owns VFile until deinit, as in production.
        } else vf->close(vf);
    }
    mCoreConfigDeinit(&c->config);c->deinit(c);
    return 0;
}
