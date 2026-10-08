"""Apache-2.0, original pixel/font assets. No downloaded assets or BIOS/logo."""
from pathlib import Path
import hashlib, json, struct

root = Path(__file__).resolve().parent
out = root / 'build'
out.mkdir(exist_ok=True)
def rgb(r,g,b): return r | (g<<5) | (b<<10)
def color(x,y):
    ramp=x*31//239
    if y<24: return rgb(ramp,ramp,ramp)
    if y<48: return rgb(ramp,0,0)
    if y<72: return rgb(0,ramp,0)
    if y<96: return rgb(0,0,ramp)
    patches=[(0,0,0),(31,31,31),(31,0,0),(0,31,0),(0,0,31),(8,8,8),(16,16,16),(24,24,24)]
    if y<128: return rgb(*patches[x//30])
    natural=[(28,20,14),(6,16,8),(8,14,24),(28,25,16),(4,4,8),(24,12,8),(20,22,28),(31,28,22)]
    return rgb(*natural[x//30])
# Original 3x5 block letter designs, generated without external fonts.
glyphs={'T':['111','010','010','010','010'], 'I':['111','010','010','010','111'],
        'N':['101','111','111','111','101'], 'Y':['101','101','010','010','010']}
def lcd(x,y):
    if y<40: on=(x+y)%2
    elif y<80: on=x%2 if x<120 else y%2
    elif y<120: on=(x-y)%8<2
    else:
        on=False
        if 130<=y<135 and 12<=x<28:
            letter='TINY'[(x-12)//4]; col=(x-12)%4
            on=col<3 and glyphs[letter][y-130][col]=='1'
        # Original star/sprite pixel silhouette and high-contrast edges.
        if 40<=x<56 and 130<=y<146:
            dx=abs(x-48);dy=abs(y-138);on=dx+dy<7 or dx<2 or dy<2
        if x>=120: on=(x//16+y//16)%2
    return rgb(31,31,31) if on else rgb(0,0,0)
for name,pattern in [('color-pattern',color),('lcd-pattern',lcd)]:
    data=b''.join(struct.pack('<H',pattern(x,y)) for y in range(160) for x in range(240))
    (out/(name+'.rgb555')).write_bytes(data)
    rgba=b''.join(bytes((((p&31)<<3)|((p&31)>>2), (((p>>5)&31)<<3)|(((p>>5)&31)>>2),
                            (((p>>10)&31)<<3)|(((p>>10)&31)>>2),255))
                  for (p,) in struct.iter_unpack('<H',data))
    (out/(name+'.rgba')).write_bytes(rgba)
    asm='''/* Apache-2.0 original Phase 4 test-only homebrew; no Nintendo assets. */
.syntax unified
.cpu arm7tdmi
.arm
.section .text
.global _start
_start:
    b main
    .space 0xA0 - 4, 0
    .ascii "GBAVISUAL   "
    .ascii "GLV4"
    .ascii "00"
    .byte 0x96
    .space 0xC0 - 0xB3, 0
main:
    ldr r0, =0x04000000
    ldr r1, =0x0080
    strh r1, [r0]
    ldr r1, =pattern
    ldr r2, =0x06000000
    ldr r3, =38400
copy:
    ldrh r4, [r1], #2
    strh r4, [r2], #2
    subs r3, r3, #1
    bne copy
    ldr r1, =0x0403
    strh r1, [r0]
    mov r1, #0x80
    strh r1, [r0, #0x84]
    ldr r1, =0x1177
    strh r1, [r0, #0x80]
    ldr r1, =0xF080
    strh r1, [r0, #0x62]
    ldr r1, =0x8400
    strh r1, [r0, #0x64]
idle:
    b idle
.ltorg
.balign 4
pattern:
'''+f'    .incbin "{name}.rgb555"\n'
    (out/(name+'.s')).write_text(asm,encoding='utf-8')
    print(name,'raw SHA256',hashlib.sha256(rgba).hexdigest())
