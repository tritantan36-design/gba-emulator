# GBA Lite — Phase 0–7B Lite

Offline Android GBA bring-up using Kotlin/Compose, JNI, source-built mGBA, GLES2 and source-built Oboe.

Read PROJECT_SPEC.md → ARCHITECTURE.md → docs/reports/PHASE_0_1_REPORT.md →
docs/reports/PHASE_2_PERSISTENCE_REPORT.md → docs/reports/PHASE_3_PLAYER_EXPERIENCE_REPORT.md →
docs/reports/PHASE_4_RENDERER_VISUAL_EXPERIENCE_REPORT.md → GBA_TEST_ROM_CORPUS.md →
docs/reports/PHASE_5_GBA_HARDWARE_SENSORS_REPORT.md →
PHASE_6_LIBRARY_APP_EXPERIENCE_IMPLEMENTATION.md.
Phase 3 adds native 1/2/4/8 pacing, bounded in-memory rewind, framebuffer WebP images,
separate touch profiles/editor, source-aware multi-touch and Android HID input, plus a pause menu.
Phase 4 adds Original/Sharp/GBA Color/LCD, Fit/Integer, DataStore settings, single-pass
GLES2 shaders and nonblocking TextureView/EGL lifecycle with actual Surface regression.
See docs/RENDERER.md, ADR-012/013 and docs/reports/PHASE_4_RENDERER_VISUAL_EXPERIENCE_REPORT.md for acceptance;
implementation/build success alone does not mean READY.
Phase 5 implements RTC, Tilt, Gyro, Solar, manual controls and persisted peripheral settings.
The user chose zero permissions; physical rumble is DEFERRED — V1 zero-permission policy,
unimplemented and non-blocking by explicit product decision.
Phase 5 is READY after final user acceptance on 2026-10-07; see docs/reports/PHASE_5_GBA_HARDWARE_SENSORS_REPORT.md
and docs/GBA_PERIPHERALS.md. Phase 6 is READY after automated regression and final user
acceptance on 2026-10-07;
see docs/reports/PHASE_6_LIBRARY_APP_EXPERIENCE_REPORT.md.
Phase 7A final closeout is BLOCKED / NOT READY; see
docs/reports/PHASE_7A_COMPATIBILITY_STABILITY_RELEASE_QA_REPORT.md and
docs/reports/PHASE_7_COMPATIBILITY_MATRIX.md. Phase 7B Lite is PASS within its
practical fixed-fixture scope; see docs/reports/PHASE_7B_LITE_PRACTICAL_ROBUSTNESS_REPORT.md.
Advanced Hardening is DEFERRED — PRE_PUBLIC_RELEASE_HARDENING, with historical
findings retained. Whole Phase 7 remains NOT READY because 7A gates are incomplete;
Phase 7.5/8 are NOT STARTED; V1 Release is NOT READY.
Latest: [7A final closeout](docs/reports/PHASE_7A_FINAL_CLOSEOUT_REPORT.md),
[evidence index](docs/reports/evidence/phase7/INDEX.md),
[manual checklist](docs/PHASE_7_FINAL_MANUAL_ACCEPTANCE_CHECKLIST.md).
Remote repository is connected; Remote CI PASS (run 37740246698). API26/29
TEST-ONLY compatibility smoke completed with notes; API29 SAF black frame recovered
after pause/continue and Home, with risk MONITORED. Complete gameplay breadth and
Phase2 historical State remain incomplete. Manual Acceptance PENDING_USER. Current API36
candidate minimal5/5 PASS73.303s; no repeated long-run or product changes in closeout.
Phase 6 adds Home/Recent/Library, bounded single-GBA ZIP import, content-based duplicate/relink
handling, metadata-only rename/removal, real play time, local artwork and shared offline settings.
Room explicitly migrates 1→2→3; library removal preserves saves, states and screenshots.
Phase 2 adds content-addressed game identity, raw battery persistence with backups, four manual states,
independent Quick Save/Load, A/B/C autosaves and LastSession resume. Files remain app-private and offline.
Background/exit saves battery first; checkpoints every 45 seconds. State loading never restores older battery bytes.
Activity configuration recreation retains Session in a ViewModel. Sudden process death can lose work since
the last completed checkpoint/autosave; this is not a zero-loss guarantee. Removing app data deletes saves.

Build requirements: JDK21, Android SDK36, NDK27.2.12479018, CMake3.22.1.
Configure sdk.dir in local.properties or ANDROID_HOME. Targets arm64-v8a only, Android8/API26+.

```sh
./gradlew :app:assembleDebug :app:assembleRelease test lint :app:assembleDebugAndroidTest :core-mgba:assembleDebugAndroidTest :app:exportRuntimeDependencies -Pphase7a=true
python scripts/audit.py --phase7a
./gradlew :core-mgba:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

On Windows use gradlew.bat. Connected tests require an authorized arm64 phone or
an unmodified official emulator image that actually advertises arm64 support.
Phase7A-0 establishes API34 as the primary automatic target; use explicit serials
with `scripts/android_emulator.py`. See `docs/ANDROID_TEST_DEVICE_STRATEGY.md` and
`docs/reports/PHASE_7A_0_ANDROID_EMULATOR_INFRASTRUCTURE_REPORT.md` for actual
readiness, commands and physical-device-only acceptance boundaries. Preserve the
Phase7 pause archive and reuse valid completed results instead of rerunning them.
API31 Google APIs ARM translation is recorded in ADR-018; it does not replace
physical audio/HID/sensor acceptance. Pure x86 images cannot install this arm64 APK.
The Phase7A selector excludes ZIP/malformed cases assigned to Lite. Long-run tests require
explicit instrumentation arguments and must not restart after the user's stop request.
Default `gradlew.bat --offline test` includes the retained Lite import/data-protection
regressions. Lite execution, existing UBSan scope, exact APK hashes and all original
failures/retests are recorded under docs/reports/evidence/phase7/7b-lite/.
Release APK is **unsigned**; no debug key is applied to release, and it is not ready for distribution.
Production signing and release provenance gates remain mandatory before any release.

Legal test ROMs: test-rom/bringup.s, persistence.s and visual-patterns.py (Apache-2.0),
generated only in androidTest assets. Visual manifests pin ROM and raw frame 300 hashes.
test-rom/test-save-v1.sav is a permanent, project-authored regression asset; see test-rom/README.md.
Use test-rom/build.ps1 to regenerate it on Windows with official NDK tools.
TEST ONLY PCM counter exports are compiled into debug native builds only.

Native source provenance: third_party/SOURCE_LOCK.json and *-files.sha256.json.
mGBA/Oboe source files are unchanged official commit snapshots; mGBA's unused `cinema/` regression assets
are excluded explicitly in SOURCE_LOCK.json and ADR-007. See their LICENSE files and root NOTICE.
Official source: https://github.com/mgba-emu/mgba and https://github.com/google/oboe.

Architecture and implementation choices: docs/adr/. Evidence and remaining gates: docs/reports/.
CI device tests use an explicitly configured self-hosted `android-arm64` runner. A green build-only CI
does not establish device acceptance or permit the next phase automatically.

Opt-in emulator compatibility only:

```sh
./gradlew :app:assembleCompat :app:assembleCompatAndroidTest -PapiCompatibility=true
```

This creates a TEST-ONLY / NOT FOR RELEASE APK with arm64-v8a and x86_64.
Default product APKs remain arm64-v8a. See
[API26/29 report](docs/reports/PHASE_7_API26_29_COMPATIBILITY_REPORT.md) and
[remaining gaps](docs/reports/PHASE_7_REMAINING_GAPS_CLOSEOUT_REPORT.md).
