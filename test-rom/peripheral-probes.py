"""Build only project-authored Apache-2.0 peripheral probes with the pinned NDK."""
import pathlib, subprocess, hashlib, json
root=pathlib.Path(__file__).resolve().parent
tools=pathlib.Path('D:/GPT/tools/android-sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin')
out=root/'build';out.mkdir(exist_ok=True)
manifest={'license':'Apache-2.0','source':'test-rom/peripheral-probe.c','mapperNote':'Header IDs select pinned mGBA mapper only; all executable code/assets are original. rotation has gyro detected and manually enabled tilt, tilt-probe detects tilt.','roms':{}}
for name,mode,code in [('rtc-probe',0,'U3IE'),('rotation-probe',1,'RZWE'),('tilt-probe',1,'KHPJ'),('solar-probe',2,'U3IE'),('rumble-probe',3,'V49E')]:
    asm=f'''.syntax unified
.cpu arm7tdmi
.arm
.section .text.start,"ax"
.global _start
_start:
    b init
    .space 0xA0-4,0
    .ascii "GBALITEPROBE"
    .ascii "{code}"
    .ascii "00"
    .byte 0x96
    .space 0xD0-0xB3,0
init:
    ldr sp, =0x03007F00
    bl main
1:  b 1b
.ltorg
'''
    (out/f'{name}.s').write_text(asm)
    linker='SECTIONS { . = 0x08000000; .text : { *(.text.start) *(.text*) *(.rodata*) } .data : { *(.data*) } .bss : { *(.bss*) } /DISCARD/ : { *(.ARM.exidx*) *(.comment*) } }'
    (out/'probe.ld').write_text(linker)
    commands=[['clang.exe','--target=arm-none-eabi','-mcpu=arm7tdmi','-c',str(out/f'{name}.s'),'-o',str(out/f'{name}-start.o')],
        ['clang.exe','--target=arm-none-eabi','-mcpu=arm7tdmi','-marm','-O1','-ffreestanding','-fno-builtin',f'-DPROBE={mode}','-c',str(root/'peripheral-probe.c'),'-o',str(out/f'{name}.o')],
        ['ld.lld.exe','-T',str(out/'probe.ld'),'-e','_start',str(out/f'{name}-start.o'),str(out/f'{name}.o'),'-o',str(out/f'{name}.elf')],
        ['llvm-objcopy.exe','-O','binary',str(out/f'{name}.elf'),str(out/f'{name}.gba')]]
    for command in commands: subprocess.run([str(tools/command[0]),*command[1:]],check=True)
    data=(out/f'{name}.gba').read_bytes()
    for module in ['app','core-mgba']:
        dest=root.parent/module/'src/androidTest/assets';dest.mkdir(exist_ok=True,parents=True);(dest/f'{name}.gba').write_bytes(data)
    manifest['roms'][name]={'sha256':hashlib.sha256(data).hexdigest(),'headerMapper':code,'probeMode':mode,'size':len(data)}
manifest['sourceSha256']=hashlib.sha256((root/'peripheral-probe.c').read_bytes()).hexdigest()
(root/'manifests/peripheral-tests.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps(manifest,indent=2))
