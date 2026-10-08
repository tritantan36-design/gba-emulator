/* Original GBA Lite probe, Apache-2.0. No BIOS, commercial code or external assets.
 * Results are real emulated cartridge-bus reads, also published in EWRAM for tests. */
typedef unsigned short u16;
typedef unsigned int u32;
typedef unsigned char u8;
#define R16(a) (*(volatile u16*)(a))
#define R8(a) (*(volatile u8*)(a))
#define DATA R16(0x080000C4)
#define DIR R16(0x080000C6)
#define CONTROL R16(0x080000C8)
static void rtc(u32* out) {
    CONTROL=1;DIR=7;DATA=1;DATA=5;
    unsigned cmd=0xA6; /* DATETIME read, LSB first. */
    for(unsigned i=0;i<8;i++) { unsigned p=4|(((cmd>>i)&1)<<1);DATA=p;DATA=p|1; }
    DIR=5;
    for(unsigned byte=0;byte<7;byte++) {
        unsigned v=0;
        for(unsigned bit=0;bit<8;bit++) { DATA=4;DATA=5;v|=((DATA>>1)&1)<<bit; }
        out[byte]=v;
    }
    DATA=1;
}
static unsigned solar(void) {
    CONTROL=1;DIR=7;DATA=2;DATA=0;
    unsigned n=0;
    while(n<255) {DATA=1;n++;if(DATA&8) break;DATA=0;}
    return n;
}
static unsigned gyro(void) {
    CONTROL=1;DIR=3;DATA=1;
    unsigned v=(DATA>>2)&1;
    for(unsigned i=1;i<16;i++) {DATA=2;DATA=0;v=(v<<1)|((DATA>>2)&1);}
    return v;
}
static void tilt(u32* out) {
    R8(0x0E008000)=0x55;R8(0x0E008100)=0xAA;
    out[0]=R8(0x0E008200)|((R8(0x0E008300)&15)<<8);
    out[1]=R8(0x0E008400)|((R8(0x0E008500)&15)<<8);
}
void main(void) {
    volatile u32* results=(volatile u32*)0x02000000;
    u32 values[7];unsigned frames=0;
    R16(0x04000000)=0x403;
    for(;;) {
        while(R16(0x04000006)>=160) {}
        while(R16(0x04000006)<160) {}
        for(unsigned i=0;i<7;i++) values[i]=0;
#if PROBE == 0
        rtc(values);
#elif PROBE == 1
        tilt(values); values[2]=gyro();
#elif PROBE == 2
        values[0]=solar();
#elif PROBE == 3
        CONTROL=1;DIR=8;values[0]=(R16(0x04000130)&1)?0:1;DATA=values[0]?8:0;
#endif
        results[0]=0x50423547;results[1]=++frames;
        for(unsigned i=0;i<7;i++) results[i+2]=values[i];
        /* Seven vertical meter columns: different raw ranges intentionally documented. */
        for(unsigned channel=0;channel<7;channel++) {
            unsigned v=values[channel];
#if PROBE == 1
            if(channel<2) v=(v*255)/1900; else if(channel==2) v=(v*255)/2600;
#endif
            unsigned h=(v*140)/255;
            for(unsigned y=0;y<160;y++) {
                unsigned color=(y>=160-h)?(channel%3==0?0x1f:channel%3==1?0x3e0:0x7c00):0x1084;
                unsigned start=channel*34;unsigned end=channel==6?240:start+34;
                for(unsigned x=start;x<end;x++) R16(0x06000000+2*(y*240+x))=color;
            }
        }
    }
}
