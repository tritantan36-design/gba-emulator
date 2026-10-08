# Phase 0–3 testing

The legal test ROM is project-authored `test-rom/bringup.s`, licensed Apache-2.0.
ROM SHA-256: `219485756a043e5e2e51fe0ae9890e0462859dad9479ce4208a2c98284d15ced`.
It contains no Nintendo logo/BIOS or commercial game code. This single ROM is not a compatibility suite.

## Automated checks

- JVM: hardware key masks, input cancellation and simultaneous buttons, session lifecycle/error cleanup, adapter invalid states and double close.
- Host native: bounded SPSC ring wrap/overflow/underrun and concurrent producer/consumer.
- Host native mGBA: 510 frames, uniform 240x160 pixels, all ten buttons, invalid ROM rejection, non-silent PCM.
- Android JNI: create/destroy/stale handles, invalid ROM, frame copying, all ten inputs, PCM consumed by Oboe, pause/resume, repeated shutdown.
- Android Activity: launch, ACTION_OPEN_DOCUMENT selection, test-provider Player navigation, Activity recreation.
- Build: debug/release Kotlin and arm64 native compilation, Debug APK, unsigned Release APK, instrumentation APKs, lint.
- Audit: both merged manifests, resolved release runtime dependencies, upstream source file hashes, module boundaries, APK ABI/native library provenance, absence of test ROM from production APK.

Windows native checks: `scripts/native-tests.cmd` uses the installed Microsoft toolchain.
Linux/CI native checks: configure the CMake projects in `audio/src/test/cpp` and `core-mgba/src/test/cpp`, build, then run CTest.
These are host test programs, not extra Android ABIs; APK supports arm64-v8a only.

Device commands:

```sh
./gradlew :core-mgba:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

The SAF monitor/test provider tests validate the application result path. They do not replace real provider testing.

## Required manual device acceptance

1. Install Debug APK on Android 8+ arm64 phone. Select the legal test `.gba` through the real system picker.
2. Confirm red image fills a 3:2 surface without stretching and stereo tone is audible without sustained crackles.
3. Hold/release each of D-pad, A/B, L/R, Select/Start; confirm immediate color change and release restoration. Check simultaneous A+direction.
4. Run for at least ten minutes. UI/exit must remain responsive; collect local native crash/leak evidence if any.
5. Send to background: audio and emulation stop. Return: surface/audio resume, then emulation continues.
6. Lock/unlock, recreate Activity and surface, and repeat enter/exit at least twenty times. No stale handles/crashes; memory settles.
7. Try invalid/non-GBA/truncated files, canceled SAF selection and a pipe-backed document provider. Errors must remain friendly.

Phase 2 retains Session in an Activity ViewModel and checkpoints on background; a new process resolves
LastSession, validates URI/Game ID, imports battery before a compatible valid autosave.
Tests never inject a commercial ROM or grant storage/network permissions.

## Sanitizers

`-DGBA_SANITIZERS=ON` enables ASan+UBSan only for Debug native builds. It is rejected for non-Debug builds.
Run sanitized tests with the NDK sanitizer runtime and Android wrap configuration on a supported debuggable device.
No sanitizer execution is claimed unless recorded in the report. Release safety flags remain enabled.
Phase 2 command `./gradlew :core-mgba:connectedDebugAndroidTest -PgbaUbsan=true` enables NDK UBSan
trap mode in Debug only. Normal builds default OFF. On this phone Gradle's split install timed out;
manual installation of the same source-built APK followed by `adb shell am instrument -w -r
dev.gbalite.coremgba.test/androidx.test.runner.AndroidJUnitRunner` completed all seven tests successfully.
Host ASan attempted via scripts/native-asan.cmd but the installed MSVC lacks its runtime library.

Phase 2 regression checks include hash identity, generation commit faults/backup recovery, four slots,
independent quick state, mismatched ROM/core/schema rejection, bounded nested JSON, autosave rotation/
corrupt-candidate fallback, six native battery type/size variants and immutable test-save-v1.sav.
Device tests add actual SRAM restart/state battery protection, URI persistence, background save,
recreation, four slots, quick state, fresh Activity resume and Room schema migration retention.
Before release, additionally run 30–60 min real-time persistence/audio/memory checks and hostile-state fuzzing.

## Acceptance gate

Phase 3 adds JVM source/pointer ownership, radial D-pad/axis mapping, per-orientation profiles,
pause-state retention, screenshot failure isolation, FF checkpoint/rewind disk isolation;
host native capture/restore/wrap/64 MiB memory cap;
PlayerJniTest measures actual 1/2/4/8 throughput, rewind ~5 seconds with current battery retained,
release/resume, repeated capture/free and close during rewind.
PlayerExperienceTest covers framebuffer WebP/State thumbnails, retained Session across rotations,
actual Android multi-pointer events/cancel/slide, profile persistence, synthetic HID lifecycle,
Hold/Toggle FF and hold rewind. Synthetic input tests are not physical USB/Bluetooth acceptance.

For Phase 3 manual acceptance: test the defaults in both orientations, thumb reach, D-pad slide/diagonal,
direction+A+B and shoulders; move/resize/opacity then restart to verify the independent profile.
Menu → FF Hold/Toggle at 2/4/8, return to normal; Menu → Rewind hold/release; create four thumbnails,
Quick Save/Load, screenshot; rotate while paused/running, background during FF/rewind, exit/resume.
Connect USB and system-paired Bluetooth HID, test all mappings and left-stick threshold,
disconnect while held, use touch, reconnect. App does not scan/pair devices and has no permissions.
Run UBSan on both JNI and Activity tests for thumbnail/frame-copy coverage.
Long-play 30–60 minutes remains separately measured; never infer it from accelerated emulated frames.
Phase 3 requires a READY report before Phase 4; any unverified or failed acceptance item stays visible.

Phase 2 may begin only after all Phase 0/1 checklist items, including actual Android playback/lifecycle checks,
pass. A successful host smoke test or compiled instrumentation APK alone does not satisfy this gate.

Phase 5 adds SensorMapper/settings JVM tests, Session synthetic haptic lifecycle tests,
host real-bus peripheral probes and four JNI tests (15 JNI tests total).
The test-only ForegroundJniRunner keeps the JNI harness visible to avoid background freezing:
`adb shell am instrument -w -r dev.gbalite.coremgba.test/dev.gbalite.mgba.ForegroundJniRunner`.
Activity instrumentation remains AndroidJUnitRunner (20 tests). Use `-PgbaUbsan=true`
for trap-on-undefined-behavior builds, archive those separately and restore the ordinary build.
Physical motion, calibration and light acceptance is separate from synthetic scalar injection;
follow docs/reports/PHASE_5_MANUAL_ACCEPTANCE.md. No physical rumble test is claimed:
the user declined VIBRATE and the production HapticOutput is disabled.

Phase 6 adds deterministic ZIP validation and monotonic play-time JVM tests. Original fixture
hashes are pinned in test-rom/manifests/library-tests.json; regenerate with
`python test-rom/library-fixtures.py`, never with downloaded ROMs. LibraryExperienceTest adds
8 Activity methods for explicit schema 2→3 migration, import identity/relink/removal safety,
real SAF UI/navigation, renamed ZIP playback/time persistence, synthetic empty/500-row UI,
slow-provider cancellation and deadline behavior. The full Activity suite contains 28 methods;
JNI remains 15. A compiled APK is not a device PASS. Preserve failed logs alongside successful
reruns and record timings as observed, not as universal performance guarantees.
Run `python scripts/audit.py --phase6` for the separate Phase 6 audit report, without overwriting
historical audits. Manual steps: docs/reports/PHASE_6_MANUAL_ACCEPTANCE.md. Do not clear user
data to manufacture an empty Home; instrumentation supplies isolated synthetic metadata.
