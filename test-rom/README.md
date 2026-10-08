# TEST ONLY — project-authored homebrew

Source: test-rom/bringup.s, written in this repository. License: Apache-2.0 (root LICENSE).
No commercial code, Nintendo logo or BIOS is included. mGBA HLE/skip BIOS is required.
Build with build.ps1 using pinned official NDK clang/ld.lld/llvm-objcopy.
The generated ROM is a test asset only, never bundled in the production app.
Expected: solid red 240x160 framebuffer, continuous stereo PSG tone (~128 Hz).
Each of ten key bits alters color independently; release returns red.
This validates bring-up; it is not a game compatibility suite.
# Phase 2 assets (TEST ONLY)

`persistence.s` is an original derivative of our bring-up source, Apache-2.0.
It reads/writes SRAM at 0x0E000000; holding A increments byte 0 and changes the framebuffer.
Holding Start changes an independent green counter in CPU state; Quick Load restores that counter,
while the blue SRAM counter stays current. This makes the two persistence mechanisms distinguishable.
The base is red: adding blue generally looks pink/magenta, not pure blue; adding green tends toward yellow.
It contains the conventional SRAM_V113 identification string, no Nintendo BIOS/logo or commercial code.

`test-save-v1.sav` is a 32768-byte fixed battery fixture owned by this project. Each byte is
`(offset * 13 + 7) & 255`, imported/exported by native tests with our homebrew loaded.
SHA-256: `91fe8bc63da1c542130e95137a4e28eeb1ec7df6239ca92fbe08e050b3979c9f`.
Never regenerate this fixture during an ordinary test: future versions must read and rewrite it unchanged.
Native tests force all six save types only in the test harness to validate mGBA import/export behavior;
this is not evidence for every game's FLASH/EEPROM command protocol compatibility.

Phase 6 `library-fixtures.py` wraps our original bring-up ROM with distinct test-only markers
and creates deterministic ZIPs in app/src/androidTest/assets. Their sizes and SHA-256 values
are pinned in test-rom/manifests/library-tests.json. Direct and single-ROM ZIP fixtures contain
identical ROM bytes; multi-ROM archives are rejection cases. The CRUD fixture has a separate
identity so synthetic battery bytes cannot contaminate the playback fixture or user saves.
Slow/timeout variants use an androidTest-only pipe provider to verify cancellation/deadlines.
These assets remain Apache-2.0 and are absent from the production APK.

