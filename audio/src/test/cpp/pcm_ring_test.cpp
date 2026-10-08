#include "pcm_ring.h"
#include <cassert>
#include <array>
#include <thread>
int main() {
    gbalite::PcmRing<8> ring;
    std::array<int16_t,20> input{},out{};
    for(size_t i=0;i<input.size();++i) input[i]=static_cast<int16_t>(i+1);
    assert(ring.push(input.data(),10)==8);
    assert(ring.pop(out.data(),5)==5);
    for(size_t i=0;i<10;++i) assert(out[i]==input[i]);
    assert(ring.push(input.data(),5)==5); // wrap around
    assert(ring.pop(out.data(),10)==8);
    assert(out[16]==0 && out[17]==0 && out[18]==0 && out[19]==0);
    ring.clear(); assert(ring.pop(out.data(),1)==0); assert(out[0]==0);
    gbalite::PcmRing<1024> concurrent;
    std::thread producer([&] {
        for(int i=1;i<20000;++i) { int16_t pair[2]={static_cast<int16_t>(i),static_cast<int16_t>(-i)};
            while(!concurrent.push(pair,1)) std::this_thread::yield(); }
    });
    for(int i=1;i<20000;++i) {
        int16_t pair[2]; while(!concurrent.pop(pair,1)) std::this_thread::yield();
        assert(pair[0]==i && pair[1]==-i);
    }
    producer.join();
}
