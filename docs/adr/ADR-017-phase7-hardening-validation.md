# ADR-017 — Phase 7 validation and bounded cleanup

Date: 2026-10-07. Status: Accepted within Phase 7 scope.

Baseline is frozen in evidence/phase7/baseline.json before application changes.
No core, dependency, toolchain, storage format or permission baseline upgrade.
Rumble remains DEFERRED — V1 zero-permission policy. Phase 8 is not started.

Test workloads use androidTest only, with separate ROM identities and private test
databases. Never clear/uninstall the user's application or delete their save files.
Long tests measure elapsed wall time, memory, descriptors and threads; emulated
frames alone cannot prove a 60-minute run. Every incomplete run remains evidence.
Instrumentation and test hooks never enter Release. Missing API devices and remote
CI are explicit coverage gaps, not inferred passes.

Official mGBA suite source is pinned to e6942030d25ffe3ba76c72b73a86da073ec857cc,
archive SHA-256 4a82b13a84c5a5c904bdb73c1dab4d3a3c78b45e9fa2000e10553ac1ce19a585.
Its full build requires unavailable devkitARM/libgba/grit. A clearly labelled
selected shifter workload may reuse its MIT instruction functions and unchanged
expected cases with an original freestanding test driver and fixed NDK assembler.
This does not count as the full suite, timing suite or BIOS accuracy validation.
GNU .func/.endfunc metadata may be removed for Clang assembly compatibility;
instruction bytes and expected results must not be changed to suppress failures.

For importer failure after snapshot commit but before index commit, cleanup must
only remove a snapshot newly created by that import and still unreferenced by the
database; never remove or replace a previously indexed snapshot on failure.
Startup temp cleanup must be serialized with imports, match exact project UUID
temp names and only touch private temporary files. Unknown/corrupt generations,
valid snapshots and user-selected external files are preserved.

Image cache may evict on low-memory callbacks without recycling bitmaps still
owned by Compose. Rewind retains its existing bounded low-memory strategy.
No new user-facing features, modules, SDKs or Android permissions are authorized.

Phase 7A scope update (2026-10-07): sanitizer/fuzz work is suspended and
its existing findings/logs are retained for 7B. Ordinary full regression remains
in scope. Periodic checkpoints must rotate A/B/C autosaves after battery commit,
as required by PROJECT_SPEC section 12, using the existing format and transaction
ordering. Windows-only host test timezone helpers require portable equivalents
for the existing Linux CI configuration; this changes no emulated clock behavior.

Phase 7A slow-host pacing correction (2026-10-07): API 31 official ARM translation
stalled the normal four-mode fast-forward test after 4x while consuming one CPU
core. The worker can repeatedly reach an already-expired pacing deadline while
holding the control mutex. An expired timed wait need not release the mutex,
starving pause/speed/metrics callers. For late frames only, wait against a future
deadline initially tested with one millisecond. That removed the emulator stall
but failed the unchanged phone pacing threshold (about 155 fps at a fast-forward
step). The final candidate explicitly unlocks, sleeps for 50 microseconds and
relocks for late frames only; the explicit unlock avoids an expired-wait shortcut
without imposing a millisecond delay per late frame. Rerun the original performance
assertions; the intermediate failure remains evidence.
This retains the same worker ownership, emulated frame cycles and speed settings;
fast-forward remains best effort. Preserve the failed run and rerun the actual
slow-host regression and final phone long-run against the corrected binary.

Phase 7A release DEX isolation (2026-10-07): normal APK inspection found five
debug-only JNI probe declarations in Release DEX, although their native exports
are guarded by GBA_TEST_HOOKS. Put the same JniBridge class name and unchanged
production JNI declarations in debug/release source sets; only debug declares
the five probes. Native export names, Core API, adapters and application behavior
remain unchanged. The shared NativeBridge interface enforces production methods
in both variants. Verify compilation, ordinary JNI regression and absence of
the probe names in final Release DEX; this is APK boundary QA, not 7B ELF testing.

Phase7A lifecycle observation correction (2026-10-08): the API34 long-run failed
after1226261ms because frame count changed2482→2483 while the test considered
Home complete. Logcat shows a periodic checkpoint paused the Session, Activity
onStop occurred, checkpoint briefly resumed, and the queued background command
then paused it. Lifecycle.CREATED plus Session.PAUSED alone can observe that
intermediate checkpoint pause. Add a completion acknowledgement to the existing
ordered ViewModel lifecycle command queue; no change to Session/Core ownership,
foreground policy, native code, permissions or save formats. Tests wait for the
actual last lifecycle command to complete successfully before taking their
background baseline. Retain the strict five-second zero-frame assertion and
the failed log/metrics. Add an explicit opt-in normal checkpoint/Home contention
regression. This is observation/coordination QA, not proof of the historical
black-screen root cause or a claim that asynchronous onStop completes instantly.

Phase7A final full-batch follow-up (2026-10-08): preserve the 14/15 JNI failure
and unchanged single-method recheck PASS. The input probe sampled its baseline
after a fixed100ms and checked only the red channel, which also accepts white
HLE startup frames. Require the ROM's documented opaque pure-red no-key frame
before sampling the baseline; retain two-second press/release checks and record
RGBA/metrics. This removes a test precondition ambiguity, not a demonstrated
production input repair. Also align the standalone core-mgba instrumentation
APK targetSdk with the app's existing36: captured system logs show its inherited
old-target warning dialog overlapping the subsequent Activity focus wait. This
does not upgrade compileSdk/toolchains/minSdk or change app targetSdk/permissions.
Rebuild and rerun the affected complete normal suites; retain every prior failure.
The slow-provider recreate/Home test also used an unchecked accessibility global
Home action followed by a fixed750ms sleep. Use normal shell KEYCODE_HOME, consume
its completion/error output, and wait up to5s for actual lifecycle below STARTED
before retaining the same stopped/cleanup/data-integrity assertions. No Session
foreground override or injected onStop is used. Prior failure remains evidence.
