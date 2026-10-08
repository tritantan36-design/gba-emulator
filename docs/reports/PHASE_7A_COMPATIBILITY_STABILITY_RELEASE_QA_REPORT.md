# Phase 7A Compatibility / Stability / Release QA report

2026-10-08 最新剩余缺口执行（取代下方历史无remote/HEAD状态）：
Remote 已连接并推送到 [tritantan36-design/gba-emulator](https://github.com/tritantan36-design/gba-emulator)，
代码提交 `fbc3cb4134bd19f2d5371645049d6684aabea187`。Remote CI PASS (run 37740246698)。
API26/29 TEST-ONLY x86_64 最小覆盖 PASS_WITH_NOTE；API29 实际 SAF 黑帧经
暂停/继续及 Home 返回恢复，风险 MONITORED，未宣称根因修复。
完整合法游戏类型覆盖仍 MISSING；Phase2 无真实历史资产 NOT_TESTED；
Manual Acceptance PENDING_USER；Phase7A/Phase7 NOT READY；7.5/8 NOT STARTED。
当前普通构建/JVM/lint/权限及APK审计通过；Release仅Git元数据改变。
最新依据：[剩余缺口报告](PHASE_7_REMAINING_GAPS_CLOSEOUT_REPORT.md)、
[真实CI](PHASE_7_REMOTE_CI_EXECUTION_REPORT.md)、
[API26/29](PHASE_7_API26_29_COMPATIBILITY_REPORT.md)、
[许可审查](PHASE_7_GAMEPLAY_LICENSE_REVIEW.md)。

---
以下为历史关闭点/证据，旧无remote、旧候选SHA、未执行API26/29等描述不代表当前状态。

Updated: 2026-10-08 (Asia/Shanghai). **FINAL CLOSEOUT — BLOCKED / NOT READY**.

Latest execution: [PHASE_7A_FINAL_CLOSEOUT_REPORT.md](PHASE_7A_FINAL_CLOSEOUT_REPORT.md).
Automatic gates BLOCKED (actual Remote CI has no remote/HEAD); Manual Acceptance
PENDING_USER. API26/29 arm64 app coverage, representative legal gameplay and Phase2
historical State remain NOT_TESTED. Second physical OEM is a justified nonblocking
limitation under the original gate. Phase7B Lite PASS; Advanced Hardening DEFERRED.
Current API36 normal candidate minimal regression PASS5/5,73.303s; installed SHA
matches candidate and cold relaunch succeeds. Evidence/candidate metadata:
[final index](evidence/phase7/INDEX.md). No production change in this closeout.
Sections below retain historical execution details and old candidate hashes;
the final-closeout report and sections Z/AA define the current decision.

Phase 7 remains NOT READY; Phase 8 NOT STARTED; V1 Release NOT READY.
Historical pause: [PHASE_7_PAUSE_CHECKPOINT.md](PHASE_7_PAUSE_CHECKPOINT.md).
Subsequent independent Phase7B Lite is PASS; see [Lite report](PHASE_7B_LITE_PRACTICAL_ROBUSTNESS_REPORT.md).
Its importer/native boundary fixes create a new candidate. The APK hashes and
reproducibility evidence below remain historical7A candidates; the7A verdict/gates
are unchanged. Advanced Hardening is deferred, with full-dependency findings retained.
Latest resumed checkpoint: [PHASE_7A_RESUME_CHECKPOINT.md](PHASE_7A_RESUME_CHECKPOINT.md).
The user subsequently authorized Phase7A-0 and continuation from this checkpoint.
Phase7A-0 is PASS_WITH_NOTE / infrastructure READY; API34 is the primary automated
target. Its report and evidence are separate. Valid completed results are reused;
only missing/failed suitable automatic checks resume. No phone long-run restarts.

## A. Baseline

Phase 0–6 reports are READY. Frozen pre-change Phase 6 evidence is
`evidence/phase7/baseline.json`: version 0.6.0/code 6; Room 3; display settings 2;
peripheral settings 1; state metadata 1/raw state 7. Fixed mGBA 0.10.5 commit
26b7884bc25a5933960f3cdcd98bac1ae14d42e2, Oboe 1.9.3 commit
b15f5e39c01a7ada306d959e5129620b145fb8b4. AGP 8.13.0, Gradle 8.14.3,
Kotlin 2.2.20, Compose BOM 2025.09.01, NDK 27.2.12479018, CMake 3.22.1,
Temurin JDK 21.0.12.1+1 unchanged. Current application 0.7.0/code 7.
Historical APK/certificate hashes remain in the immutable baseline and Phase 6 report.
Git has no HEAD, tracked files or remote; the workspace is uncommitted/untracked.

## B. Changed Code

Periodic checkpoint now rotates existing A/B/C autosaves after battery commit,
as required by PROJECT_SPEC section 12; a failed battery write preserves the old
states and leaves execution paused. Existing format and layer boundaries retained.
Serialized importer cleanup touches only exact project UUID temporary files;
failed indexing conservatively cleans a newly created, unreferenced snapshot.
Low-memory callbacks evict the bounded artwork cache without recycling images
still owned by Compose. Version/About updated to 0.7.0. No permission, SDK, core,
toolchain or architecture upgrade. Late native frames now explicitly unlock/yield50us
before the next iteration, preventing control-lock starvation on slow ARM translation.
The intermediate1ms wait failed unchanged phone FPS assertions;50us passed the
original phone pacing test15.866s and emulator16-combination test56.206s. Both
intermediate failures and subsequent targeted passes remain evidence. ADR-017 records scope and ownership decisions.
Test-only additions: isolated stress ROMs, ordinary lifecycle/resource/large-library
tests, selected MIT shifter driver, portable host timezone helpers, provenance and
clean-build tooling. Earlier specialist harness/findings remain preserved;
subsequent7B Lite PASS, Advanced Hardening DEFERRED with historical findings retained.
2026-10-08 resume: the existing ordered ViewModel lifecycle queue now acknowledges
command completion. Session.PAUSED can be a periodic-checkpoint intermediate state;
long-run waits for actual Home/onStop plus successful lifecycle acknowledgement,
then retains the unchanged five-second zero-frame assertion. ADR017 records this
observation correction. No native/Session behavior, core, permissions or formats changed.

## C. Compatibility Matrix

See PHASE_7_COMPATIBILITY_MATRIX.md. Host results do not imply full gameplay
compatibility or new human acceptance.

## D. Test ROM Corpus

Internal bringup, persistence, color/LCD and peripheral assets retained with fixed
hashes. Selected mGBA shifter: 70/70 original expected cases PASS, pinned source
and MIT notice retained. The full upstream suite is not built: devkitARM/libgba/grit
are unavailable. No commercial ROM or BIOS packaged. Legal third-party gameplay
categories remain NOT_TESTED; licence inventory distinguishes candidates from tested ROMs.

## E. Android API Matrix

Final closeout: current7B candidate API36 targeted5/5 PASS73.303s on software GPU.
Navigation, ZIP, battery/Quick/Slot/Resume, actual Home audio counters and orientation
verified; real DocumentsUI picker evidence reused from7A-0, not falsely attributed
to test-provider interception. API34 current Lite evidence reused; API26/29 remain
NOT_TESTED. See final-closeout API matrix for the current candidate and limitations.
The following results are preserved historical runs.

API 36: OPPO PLG110 arm64 phone, prior ordinary regression retained.
The phone long-run was stopped by the user (section G), not still in progress.
New API34 primary and API36 official ARM-bridge emulators: actual install/launch,
SAF, navigation/persistence/recreate/Home audio smoke PASS3 tests on each target;
26.098s/29.295s respectively. Host NVIDIA GPU backend and actual orientation PNGs
recorded in PHASE_7A_0_ANDROID_EMULATOR_INFRASTRUCTURE_REPORT.md. This does not
replace physical hardware or final gameplay acceptance.
API 31: official Google APIs x86_64 image with arm64-v8a / libndk_translation.so;
15 JNI tests passed (47.849 s) before the pacing correction. Corrected four-mode
1/2/4/8x performance regression passed (56.206 s). Final API31 batch had JNI13/15 PASS and Activity32/43 PASS,3 FAIL/8 SKIP;
five failed methods passed unchanged on API34 targeted revalidation. These are
distinct historical/targeted results, not a new full API34 batch.
Emulator/ARM translation is not physical ARM performance, audio listening or OEM coverage.
API 26: official ARM64 revision 3 installed; emulator 37.2.12 rejects boot with
`QEMU2 emulator does not support arm64 CPU architecture`. NOT_TESTED.
7A-0 additionally probed official API26 x86_64 revision16: boot34445ms succeeds,
guest ABI x86_64,x86, original APK install NO_MATCHING_ABIS. API26 app smoke remains
NOT_TESTED. Empty new probe AVD removed; prior artifacts and AVD preserved.
API 29 revision 13: actual ABI x86_64,x86; APK install fails NO_MATCHING_ABIS.
API 33 revision 17: actual ABI x86_64; APK install fails NO_MATCHING_ABIS.
These are explicit coverage gaps; no ABI property/image/emulator-limit changes.
ADR-018 records official provenance and API31 metadata discrepancy (package revision14,
source.properties revision12). No driver/Windows feature changes were made.

## F. Multi-device Results

Second OEM: NOT_TESTED. User confirmed only the current phone is available.

## G. Long-run Results

Final short-yield candidate real 60-minute phone run started 2026-10-07
21:26:35 +08:00; user requested stop at21:41:03 +08:00. Phone app and JNI harness
force-stopped, stay-awake restored to0. Last recorded sample815002ms (13min35s),
14 completed cycle samples, no complete event: USER_STOPPED, not a60-minute PASS.
Actual file phase7-longrun-1791379595870.jsonl and final captured copy retained.
No long-run restart without new user authorization.
It measures elapsed time, FF, rewind, states, 50 orientation/Home/ROM-switch cycles
and 120 shader selections. Automated internal-ROM stress does not replace human gameplay.
The 20:29 run failed at 1588.74 s: frame-stop measurement mistook periodic-checkpoint
PAUSED for Activity Home/onStop. Logcat shows stop at20:55:57.770; the test now waits
for real Lifecycle.CREATED plus Session.PAUSED. Failed log remains preserved.
The next run was superseded after nine completed cycles by the production pacing
correction; not a 60-minute PASS. A subsequent start was blocked by the JNI test
package's DeprecatedTargetSdkVersionDialog; actual dialog read and dismissed.
No interrupted, failed or superseded durations are added to the final run.
API34 Emulator resumed run failed at1226261ms (20min26s);20 cycle samples, final
event incomplete. Instrumentation1228.019s FAIL: background frames2482→2483.
Logcat shows checkpoint pause, onStop, brief checkpoint resume, then queued
background pause. The test had observed checkpoint PAUSED before that command
completed. Original log/metrics preserved as7a-resume-api34-sixty-minute.log and
7a-resume-api34-longrun-failed-*. No60-minute PASS.
New explicit opt-in checkpoint/Home contention regression PASS54.829s,8 cycles,
strict five-second invariant after actual lifecycle acknowledgement. Log:
7a-lifecycle-ack-targeted.log. A new complete acknowledged API34 run started
2026-10-08 around00:31:51+08; FAILED3006.396s, final metrics3004375ms,
48 cycle samples and incomplete event. Home→onStop wait timed out while Session
remained RUNNING. App log shows PAUSED at17:20:14.643Z, STOPPED at17:21:55.667Z,
approximately99-second GPU completion/frame stalls and4670 skipped frames.
No matching Windows power/sleep events in the queried window; AC standby setting0.
Root cause remains unconfirmed; do not classify this as merely failed Home delivery,
an established production fix, or a completed60-minute PASS.
Official software/Google SwiftShader GPU isolation now runs on the same API34 AVD,
with no image/ABI/app/driver changes. Test Home uses normal shell KEYCODE_HOME;
lifecycle completion and five-second zero-frame assertion remain mandatory.
The checkpoint/Home contention case now tests50 cycles; a separate case validates
100 actual mode changes. Both PASS337.991s on official Google SwiftShader,
7a-home-key-fifty-and-shader-hundred.log.50 Home cycles each verify five seconds
with zero core-frame advancement after acknowledgement;100 actual changes cover
all four modes, retain context52, and check each actual bitmap is nonblack. This
is presence/context stress, not replacement for pixel-golden/color accuracy tests.
Completed software/Google SwiftShader API34 run: **PASS**, instrumentation
3614.31s; metrics3612378ms (60min12.378s),62 samples,60 cycle samples and final
complete event. Start approximately2026-10-08 01:38:38+08; complete02:38:51+08.
Evidence:7a-software-api34-sixty-minute.log and7a-software-api34-longrun-final-*.
This is one real elapsed mixed session including pauses, not combined attempts or
60 minutes of uninterrupted foreground CPU/physical gameplay.50 orientation/Home/
ROM-switch cycles (150 orientation transitions) completed.120 mode selections mean
90 effective changes; the separate100 actual-change test supplies that stress gate.
The loop's rewind branch is conditional and was not individually traced/counted;
do not claim60 executed rewind actions. Existing independent rewind checks remain
separate functional evidence. No new full15/43-test suite is claimed.
Captured app/PID/event/crash evidence contains no observed app FATAL/native crash/
ANR; this is the captured window, not all-driver certification. Actual final-game
PNG was saved and inspected: colored internal test frame and touch controls visible.
The old host/NVIDIA stall remains unresolved; software PASS does not erase it.

## H. Memory / Heap

Completed earlier API31 100 IO evidence: 22 samples over231064ms, last event closed;
RSS start/peak/end275744/280020/276812KiB. See7a-hundred-io-resource-summary.json.
Some translated native-heap readings are0/unavailable, not proof of zero allocation
or absence of leaks. Final phone long-run was user-stopped; no thermal certification.
USB charging, uncontrolled brightness/background services prevent thermal certification.
Failed API34 long-run22 samples: RSS261512/269280/259528KiB, native heap
27890928/32165328/28861792bytes, Java27899928/27899928/6681712bytes,
PSS163764/168229/160192KiB (start/sampled peak/end). These finite failed-run
observations do not replace a completed60-minute test.

Completed software long-run (start / sampled peak / end):

| Metric | Start | Sampled peak | End |
|---|---:|---:|---:|
|RSS KiB|246300|269620|269476|
|Native heap bytes|27697856|32336704|28567456|
|Java used bytes|7713168|16086592|7322568|
|PSS KiB|148622|168205|165707|
|FD|151|160|157|
|OS threads|40|53|46|
|JVM threads|32|49|43|

Source:7a-software-api34-longrun-final-summary.json. Sampling at cycle boundaries
can miss transient peaks, including between state/ROM resets. Native heap and
Java used return near their measured baselines; RSS/FD/threads finish above start.
Finite bounded observations are not proof of zero leaks or a complete allocation
profile. Per-core underrun sampled peak1146, final0 after stop is a counter reset,
not zero underruns or a human audio-quality PASS; counters reset on ROM changes.
Final Session STOPPED, rewindBytes0. Pre-runner-teardown renderer snapshot still
shows1 texture/1 program/2 buffers/1 Surface: no claim all GL objects had already
been released. Passive query after runner teardown found no application PID;
process termination is not proof of earlier per-object teardown. No physical
thermal/battery, comprehensive ASan or fuzz evidence is added.

## I. FD / Thread

Earlier completed extended100 IO run: FD start/peak/end143/144/140, OS threads
47/47/44, JVM threads43/43/41. Bounded-growth assertions passed for that run.
The final Activity batch result is recorded separately at the pause checkpoint.
Phone100 Quick +100 Slot and20 core cycles passed72.084s; distinct from100 IO.
Failed API34 run FD153/160/125, OS threads39/49/47, JVM31/45/44.
Final renderer texture/program0, buffers2/activeSurface1 reflect retained Activity
view lifetime; not proof that every EGL/renderer object was destroyed.

## J. Lifecycle Stress

API34 Emulator long-run failed at a transient checkpoint/background boundary;
the earlier8-cycle acknowledged contention regression passed. The subsequent
full-duration attempt failed at the recorded graphics/lifecycle stall (section G).
Software-backend50 Home/checkpoint cycles and full-duration3614.31s isolation PASS.
Full run also completed50 actual Home/foreground/ROM-switch cycles and150
orientation transitions. Prior failures remain separate evidence.
The user-stopped phone run is not restarted.
No manual Session foreground override is used to force a pass. OEM test provider
is pre-woken through the test shell and the real Activity must regain window focus.

## K. Renderer Stress

Original/Sharp/GBA Color/LCD, viewport and real Surface assertions retained.
The three failed final API31 Activity methods passed unchanged on API34 (86.557s),
including actual background margins/preferences and all-mode performance checks.
No new full43-test API34 batch is claimed; original API31 failures remain evidence.
No black-screen root cause claim. Long-run shader selection alone does not prove
every mode rendered.
The planned120 mode selections contain30 redundant Original selections, hence
only90 effective changes in a complete run. A dedicated100 actual-change/real
Surface check passed, all four modes/context52; mode samples confirm100 changes,
7a-software-hundred-shader-validation.json. Selection count is not relabelled as
change count. Previous host GPU stall remains a separate unresolved risk.

## L. Audio

Normal JNI regression passed 15 tests in 42.547 s. Host PCM queue/core tests pass.
These establish automated counters/PCM paths, not new human listening acceptance.
Bluetooth headset and wired/USB audio routes NOT_TESTED.

## M. Input

Existing synthetic multi-touch/HID regression retained. Phase 7 physical Bluetooth
and USB acceptance pending/NOT_TESTED respectively. Historical Bluetooth approval
does not establish new Phase 7 acceptance. No BLUETOOTH_CONNECT permission.

## N. Sensors / RTC

Short real-device RTC/FF/state and scalar Tilt/Gyro/Solar JNI checks pass.
API31 independent wall-clock RTC regression PASS639.010s (over10min), with
FF/Home/State/ROM reopen; this is automated probe evidence, not physical sensor or
manual-game acceptance. Metrics:7a-api31-rtc-wall-clock-metrics.jsonl. API34 listener
registration/orientation/Home/rewind/exit targeted regression passed unchanged;
emulated sensors do not establish physical motion/light acceptance.
Physical rumble DEFERRED — V1 zero-permission policy.

## O. Library

New 0/1/100/500/1000 synthetic metadata scroll/search/navigation test and 300-image
bounded-cache/low-memory test passed within the interrupted Activity run. This is
UI metadata stress, not 1000 imported games or 1000 real database records.
The first search assertion incorrectly matched the editable query and game title;
fixed to target the game identity card. Original failure retained.

## P. Persistence

Current Lite JVM75 executions/0 failures and normal Persistence Activity passes
are recorded separately in the Lite report; the61-execution run below is historical7A.

Final ordinary JVM scoped regression: 61 executions/19 suites, 52 distinct cases,
zero failures/errors/skips (Debug/Release variants account for duplicated executions).
Existing corruption fallback and injected write/commit failure tests retained;
new periodic autosave rotation/battery-failure test passed. Real-device 100 Quick
and 100 Slot save/load passed. Existing Phase7IoFailureTest XML records3/3 PASS,
0.721s (2026-10-07T14:40:28.555Z), already included in the61 executions:
injected directory-fsync failure, real rename failure after deleting the temporary
source, and injected ENOSPC error after write all preserve the committed manifest
and battery generation. This is not actual full-disk exhaustion or universal IO
failure coverage; no additional test was run to close this documentation gap.
Controlled process-death/new-PID recovery also passed (section R).

## Q. Room / DataStore Upgrade

Schemas 1/2/3 retained; explicit 1→2→3 migration, no destructive fallback.
Existing migration/settings tests remain in normal suite; actual archived Phase6
upgrade verification passed (section R). Display v1/v2 and peripheral v1 formats
unchanged. Actual Phase3–5 raw State fixtures were collected/read successfully;
Phase2 fixture unavailable. Editing a new state's version label would not establish
historic compatibility.

## R. Install / Upgrade

Actual archived Phase6 Debug -> current Phase7 Debug PASS on isolated API31 AVD.
Preparation installed Phase6 using -r -d on this test-only AVD (no user-phone downgrade
or clear), then actual code6 produced version0.6.0 auto/Quick/Slot states, battery,
thumbnail/screenshot, playtime and preferences for a unique legal test ROM.
Seed12.570s and verification18.534s passed. File sets/SHA, GameId, ROM URI/name,
addedAt/playtime, LastSession, library and input/display/peripheral files retained;
Continue, Quick and Slot load actually executed. See 7a-actual-six-to-seven-upgrade-results.json.
Controlled live-game force-stop -> new PID/cold launch PASS9.451s; last-safe file
hashes unchanged and Continue/Quick/Slot worked. Preparation runner interruption is
expected controlled termination evidence, never a PASS or spontaneous native crash.
Phase3–5 actual archived APK native State capture/read tests PASS; capture/read logs
use `7a-phase-{three,four,five}-settled-state-*`. The initial immediate-frame-read
failure is preserved; successful fixtures resume before inspecting the published frame.
No Phase2
archived APK located. Historical raw core states are distinct from Room/JSON upgrade.

## S. Dependency / License / Provenance

Normal build/unit/lint passed. APK/source/permission/license provenance audit
passed (PHASE_7A_APK_PROVENANCE_AUDIT.md); upstream mGBA/Oboe unchanged.
Historical7A61 normal JVM executions excluded ZIP/malformed specialist cases then reserved for
7B. Subsequent7B Lite completed those fixed-fixture checks; current JVM75 executions PASS. The earlier unfiltered 65-test run also executed those legacy fixtures; retained
as historical evidence, not a new 7B verdict. No further specialist runs in 7A.
Final lint:0 errors/4 warnings: arm64-only ChromeOS notice (explicit V1 scope), DataExtractionRules notice
(allowBackup=false; no cloud transfer claim), 2 optional String.toUri style notices.
Main-thread IO inspection: ROM hash/ZIP, state, thumbnail decode, screenshot encode,
DataStore and Room heavy operations dispatched to IO. Controlled StrictMode passed
with0 app-origin main-thread IO violations; this does not prove all possible paths.

## T. CI

Workflow updated for Phase 7A ordinary selection, exact JDK and pinned SDK tools;
host timezone helper made portable. Configuration is not a remote run.
Remote CI BLOCKED: no remote repository/CI provided. No source upload authorized
or performed. Mandatory actual remote CI gate is unmet.

## U. APK Audit

Current candidate is the subsequent7B Lite build: Debug SHA256
`a0cc12c18cab69a6294cdf95fafd516899cb957a1090f9897463b304aef24051`;
unsigned Release SHA256
`6e30e42213e043ee7408f4d2ccf520b2056189cb6629b9beab9a67d81c605905`.
Empty permissions and no ROM/BIOS/test artifacts in Release verified; current
build outputs match archived ordinary candidate. Exact timestamps and missing
Git HEAD recorded in final-closeout/candidate-apks.json. No current-candidate
two-clean-build reproducibility claim. All hashes/reproducibility results below
belong to earlier historical7A candidates.

Debug/Release permissions []; no production ROM/ZIP/BIOS/sanitizer runtime/wrap.
Native provenance audit passes. Debug/Release JNI declarations are now isolated;
Release DEX omits Debug test probes. Prior candidate two clean unsigned Release
builds are bit-identical, dependencies equal and all entries equal; evidence
7a-jni-isolated-release-reproducibility.json. Prior unsigned Release SHA256:
079ad749f9b0dbb227b77a0854372729fae485329856ea459fb151144f387696.
After lifecycle acknowledgement source changes, two new clean unsigned Release
builds are bit-identical, dependencies/entries equal:
7a-lifecycle-ack-release-reproducibility.json. New candidate SHA256:
77dbada49b1099b96ead34de3cc39eb92fc06f4e252ba1d33af685d3861d4fb7.
Debug/androidTest rebuild, lint0errors/4warnings and ordinary APK/provenance audit
PASS. Home own-audio/frame invariant PASS7.638s on new candidate. Evidence labels
7a-lifecycle-ack-final-build-lint, -apk-audit, -home-audio. The prior hash remains
historical, unchanged; no signing or7B ELF tests.
Earlier pre-fix and one-millisecond candidate reproducibility records retained and
not presented as final binaries. Historical Phase6 hashes unchanged. No production keystore.

## V. Privacy Audit

No INTERNET, Analytics, advertisements, account or remote crash telemetry.
Only app-local evidence/logcat is collected. Development dependency/source fetch
does not grant the application runtime network permission.

## W. Manual Acceptance

**PENDING_USER**. User scheduled joint final acceptance after7B; Lite has now
passed, but no final human acceptance has been supplied. Earlier agreement to play
a legal local game is not PASS; Phase0–6 feedback is historical. Prepared checklist:
[PHASE_7_FINAL_MANUAL_ACCEPTANCE_CHECKLIST.md](../PHASE_7_FINAL_MANUAL_ACCEPTANCE_CHECKLIST.md).
Automatic gates are BLOCKED, so this is preparation, not final signoff readiness.
Physical HID/audio/sensors/OEM/renderer feel/thermal and30–60min actual gameplay
require real user confirmation. No phone long-run restarted or ROM uploaded.

## X. Known Issues

Phase 3 whole-screen black-screen root cause unconfirmed; Phase 4 GLSurfaceView
ANR mitigation uses TextureView per ADR-013, not proof of Phase 3 root cause.
SAF screenshot/window intermittent dark/red result and lock/Doze interference
remain monitored. Interrupted/failing Activity logs are retained, not discarded.
Lite importer/native fixed-boundary findings were repaired and regressed.
Full-dependency sanitizer/fuzz findings remain open under Advanced Hardening DEFERRED;
they are not a current claim that7B Lite is unfinished. Renderer classification:
Phase3 NOT REPRODUCED/MONITORED; Phase4 lifecycle MITIGATED; host NVIDIA
stall MONITORED with root cause OPEN, software QA backend MITIGATED.
Later host/NVIDIA approximately99-second graphics stall remains unconfirmed;
software isolation passed without establishing a physical-GPU fix.

## Y. Risks

Single physical OEM; emulated API31/34/36 coverage does not close API26/29/33
ARM ABI gaps. No real remote CI, incomplete gameplay breadth and Phase2 historic
State fixture. Host/NVIDIA99-second graphics stall root cause unconfirmed despite
software-backend PASS. Final physical/manual feedback missing; no physical thermal
certification.8x remains best effort. Release unsigned.

## Z. Unfinished

Completed/reused:7A-0 PASS_WITH_NOTE; original failed assertions have separate
targeted passes;50 Home/100 actual mode changes and one full software60-minute
run PASS; current Lite JVM75, host1+7, JNI17, Activity targeted11 and restored8,
existing UBSan JNI17/Activity15, normal build/lint/APK audit PASS within their
recorded scope. Actual Phase3–5 State reads and Phase6 upgrade PASS reused.
Current candidate API36 minimal5/5 PASS73.303s added; no repeated long-run.

BLOCKED: actual Remote CI, no remote/first commit/HEAD. NOT_TESTED: API26/29
arm64 application coverage, representative complete legal gameplay categories,
Phase2 actual historical State. PENDING_USER: final manual/physical acceptance.
Second OEM NOT_TESTED is a justified limitation because no second phone is available.
Host graphics stall root cause remains OPEN/MONITORED; software QA is mitigated.
Advanced Hardening DEFERRED, not an obsolete “7B Lite missing” blocker.
No test is still running. Old failures and prior candidate evidence remain archived.

## AA. Phase 7A Decision

**Phase7A Automatic Gates — BLOCKED; Phase7A / Phase7 — NOT READY.**
Phase7B Lite PASS. Advanced Hardening DEFERRED — PRE_PUBLIC_RELEASE_HARDENING.
Manual Acceptance PENDING_USER. Phase7.5/8 NOT STARTED; V1 Release NOT READY;
Release unsigned. Remote CI configuration/local success cannot waive its real-run gate.
The historical final-automation checkpoint describes the pre-Lite candidate;
latest authoritative execution is PHASE_7A_FINAL_CLOSEOUT_REPORT.md.
It is not accurate to say only manual acceptance remains.
