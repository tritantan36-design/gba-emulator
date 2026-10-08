/* Project-authored homebrew. Apache-2.0. TEST ONLY, no Nintendo logo/BIOS. */
.syntax unified
.cpu arm7tdmi
.arm
.section .text
.global _start
_start:
    b main
    .space 0xA0 - 4, 0
    .ascii "GBALITESAVE "
    .ascii "GLTE"
    .ascii "00"
    .byte 0x96
    .space 0xC0 - 0xB3, 0
main:
    mov r9, #0                   /* independent State-only green counter */
    ldr r0, =0x04000000
    ldr r1, =0x0403
    strh r1, [r0]                  /* mode 3, BG2 */
    mov r1, #0x80
    strh r1, [r0, #0x84]          /* PSG master enable */
    ldr r1, =0x1177
    strh r1, [r0, #0x80]          /* channel 1, stereo */
    ldr r1, =0xF080
    strh r1, [r0, #0x62]          /* square wave */
    ldr r1, =0x8400
    strh r1, [r0, #0x64]          /* trigger, ~128 Hz */
    ldr r7, =0x0E000000
    ldrb r8, [r7]
    cmp r8, #255
    moveq r8, #0
    strb r8, [r7]
wait_active:
    ldrh r1, [r0, #6]
    cmp r1, #160
    bge wait_active
wait_blank:
    ldrh r1, [r0, #6]
    cmp r1, #160
    blt wait_blank
    ldr r6, =0x04000130
    ldrh r4, [r6]
    mvn r4, r4
    ldr r5, =0x3ff
    and r4, r4, r5
    tst r4, #8                   /* Start increments State-only counter */
    addne r9, r9, #1
    and r9, r9, #31
        tst r4, #1
    ldrb r8, [r7]
    addne r8, r8, #1
    strb r8, [r7]
    mov r4, r4, lsl #5
    orr r4, r4, #0x1f             /* red with no buttons */
    orr r4, r4, r9, lsl #5
        orr r4, r4, r8, lsl #10
    ldr r2, =0x06000000
    ldr r3, =38400
fill:
    strh r4, [r2], #2
    subs r3, r3, #1
    bne fill
    b wait_active
.ltorg

.ascii "SRAM_V113"

