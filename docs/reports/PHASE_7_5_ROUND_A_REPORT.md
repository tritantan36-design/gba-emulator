# Phase7.5 Round A checkpoint

2026-10-08. Implementation and emulator targeted regression PASS; user visual/physical
acceptance PENDING_USER. User explicitly requested completing7.5 then combined
acceptance, overriding the document's READY-first and intermediate stop schedule;
see ADR-020. Phase7 remains NOT_READY, Phase8 NOT_STARTED.

Shared light/graphite tokens, original consistent icons, native GBA touch styling,
six-action dark Pause root with save/load/speed/display subpages, compact Settings
root summaries, uniform secondary top back and lighter bottom navigation implemented.
Saved profiles, input routing/dead zone/slide/pointer ownership and backend unchanged.
No fake X/Y/L2/R2, new dependency, permissions or network.

Frozen toolchain compat build PASS29s; first Kotlin syntax error fixed and original
log retained. API29 TEST-ONLY UI test1/1 PASS24.478s: actual TextureView red pixels
and3:2 viewport, portrait/landscape/reverse landscape, pause frame stop, Quick + all4
slots save/load,4x→1x, display navigation, background resume and exit.
The runner does not certify physical HID, motion, sound, comfort or OEM performance.

11 actual screenshots under local ignored evidence/phase7_5/round-a/:
player-portrait,player-landscape,player-reverse-landscape,pause-root,pause-save,
pause-load,pause-fast-forward,settings-root,settings-display,settings-controls,
bottom-navigation. Portrait/player,landscape/pause/settings captures visually
inspected. Solid red is the original lawful persistence test framebuffer, not a crash
or a screenshot substitute. Final regression and Round B will refine this checkpoint.
Existing source/renderer historical risks remain monitored, not root-cause-fixed.
