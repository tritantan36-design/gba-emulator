"""MIT upstream shifter cases + original freestanding driver. Not the full suite."""
from pathlib import Path
import hashlib, json, re, subprocess, sys
root=Path(__file__).resolve().parents[1]
rev='e6942030d25ffe3ba76c72b73a86da073ec857cc'
src=Path(sys.argv[1])
expected={
    'LICENSE':'2ee28a3f7b56a9b743bd5b7ca9ac0a337da4e1cce95ab917ecfae8944135f6fb',
    'src/shifter.c':'58992ff80c2a694273d718b94e353e857bba56fbd808c5f908ace04a188a69e1',
    'src/shifter-impl.s':'ffe4a5b9bb0e6bfdb6322af7f4be12a8c9b1575f0bd845a02263e8ded42aace0',
    'include/macros.s':'9144919b855e6a8a005c54d92ba7f7bbfe022f6278cc3568276b6ad201a26749',
}
for name,digest in expected.items():
    if hashlib.sha256((src/name).read_bytes()).hexdigest()!=digest:
        raise SystemExit('Pinned suite source mismatch: '+name)
build=root/'test-rom/build/suite-selected';build.mkdir(parents=True,exist_ok=True)
tool=Path('D:/GPT/tools/android-sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin')
original=(src/'src/shifter.c').read_text()
table=original[original.index('struct TestOutput'):original.index('static const u32 nShifterTests')]
driver='typedef unsigned int u32;\n'+table+'''
extern void invoke(const struct ShifterTest*, struct TestOutput*);
void main(void) {
    volatile u32* result=(volatile u32*)0x02000000;
    result[0]=0;result[1]=0;result[2]=sizeof(shifterTests)/sizeof(*shifterTests);result[3]=0xffffffff;
    for(u32 i=0;i<result[2];++i) {
        struct TestOutput out; invoke(&shifterTests[i],&out);
        if(out.rd==shifterTests[i].expected.rd && out.cpsr==shifterTests[i].expected.cpsr) ++result[1];
        else if(result[3]==0xffffffff) {result[3]=i;result[4]=out.rd;result[5]=out.cpsr;}
    }
    *(volatile unsigned short*)0x04000000=0x403;
    volatile unsigned short* pixels=(volatile unsigned short*)0x06000000;
    for(u32 i=0;i<38400;++i) pixels[i]=result[1]==result[2]?0x3e0:0x1f;
    result[0]=0x37534846;
    for(;;) {}
}
'''
(build/'driver.c').write_text(driver)
asm=(src/'src/shifter-impl.s').read_text()
asm=re.sub(r'\s*\.func NAME, NAME ;\\', '', asm).replace('\t.endfunc;','')
(build/'shifter.S').write_text(asm)
(build/'entry.s').write_text('''
.syntax unified
.cpu arm7tdmi
.arm
.section .text.entry
.global _start
_start:
 b boot
 .space 0xA0-4,0
 .ascii "GBASUITESEL "
 .ascii "GLS7"
 .ascii "00"
 .byte 0x96
 .space 0xC0-0xB3,0
boot:
 mov r0,#0x1f
 msr cpsr_c,r0
 ldr sp,=0x03007f00
 bl main
 b .
.global invoke
invoke:
 push {r4-r6,lr}
 mov r4,r0
 mov r5,r1
 ldr r2,[r4,#8]
 msr cpsr_f,r2
 ldr r0,[r4,#12]
 ldr r1,[r4,#16]
 ldr r3,[r4,#4]
 mov lr,pc
 bx r3
 mrs r2,cpsr
 str r0,[r5]
 str r2,[r5,#4]
 pop {r4-r6,lr}
 bx lr
 .ltorg
''')
(build/'link.ld').write_text('SECTIONS { . = 0x08000000; .text : { *(.text.entry) *(.text*) } .rodata : { *(.rodata*) } /DISCARD/ : { *(.ARM.exidx*) *(.comment*) } }')
for name in ('entry.s','driver.c','shifter.S'):
    subprocess.run([str(tool/'clang.exe'),'--target=arm-none-eabi','-mcpu=arm7tdmi','-marm','-ffreestanding','-fno-builtin','-O2','-I'+str(src/'include'),'-c',str(build/name),'-o',str(build/(name+'.o'))],check=True)
subprocess.run([str(tool/'ld.lld.exe'),'-T',str(build/'link.ld'),'-e','_start',*[str(build/(n+'.o')) for n in ('entry.s','driver.c','shifter.S')],'-o',str(build/'selected.elf')],check=True)
rom=build/'mgba-suite-shifter.gba'
subprocess.run([str(tool/'llvm-objcopy.exe'),'-O','binary',str(build/'selected.elf'),str(rom)],check=True)
license_dir=root/'test-rom/licenses/mgba-suite';license_dir.mkdir(parents=True,exist_ok=True)
for item in ('LICENSE','src/shifter.c','src/shifter-impl.s','include/macros.s'):
    dest=license_dir/Path(item).name
    dest.write_bytes((src/item).read_bytes())
for module in ('app','core-mgba'):
    (root/module/'src/androidTest/assets/mgba-suite-shifter.gba').write_bytes(rom.read_bytes())
manifest={'id':'mgba-suite-shifter','source':'https://github.com/mgba-emu/suite','revision':rev,'tier':'A','license':'MIT','archiveSha256':'4a82b13a84c5a5c904bdb73c1dab4d3a3c78b45e9fa2000e10553ac1ce19a585','romSha256':hashlib.sha256(rom.read_bytes()).hexdigest(),'sourceSha256':{item:hashlib.sha256((src/item).read_bytes()).hexdigest() for item in ('LICENSE','src/shifter.c','src/shifter-impl.s','include/macros.s')},'scope':'Shifter instruction functions and original expected cases only. No libgba, timing suite or full suite. Original wrapper. GNU function metadata removed for Clang; instructions unchanged.'}
(root/'test-rom/manifests/suite-selected.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest,indent=2))
