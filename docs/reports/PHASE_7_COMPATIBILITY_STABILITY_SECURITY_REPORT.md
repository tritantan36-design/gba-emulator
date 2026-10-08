# Phase 7 Compatibility / Stability / Security Report

> 2026-10-08 Phase7.5 后续更新：用户要求先完成7.5再合并验收；Round A+B UI已实现，
> Phase7/7.5仍NOT_READY，Manual PENDING_USER，Phase8 NOT_STARTED。
> 当前0.7.5候选、定向结果与合并清单见 [7.5报告](PHASE_7_5_UI_UX_POLISH_REPORT.md)。
> 下文7.5未启动、0.7.0候选及旧CI属于本报告历史收尾时点，不是当前交接包。

2026-10-08 最新剩余缺口执行（以下历史章节不作为当前阻塞清单）：
代码提交 `280782965cc2ceb4a3354ba8e26cdde1072aaae7` 已推送到用户指定仓库；
[Remote CI run37751328064](https://github.com/tritantan36-design/gba-emulator/actions/runs/37751328064)
真实SUCCESS，build397秒，artifact11537839324；JVM/lint/普通构建/host/审计通过。
API26/29 TEST-ONLY x86_64最小覆盖PASS_WITH_NOTE；API29 SAF黑帧恢复风险MONITORED。
Blob Goes 3D完整homebrew脚本10分钟PASS616.727s；Hyperspace Roll10分钟session PASS626.748s，升级脚本限制保留，覆盖PARTIAL。
游戏广度仍有限：action/audio-heavy/save-heavy PARTIAL，RTC/sensor完整游戏MISSING，物理体验待用户。
Phase2真实历史资产不可用，justified NOT_TESTED；原有renderer根因未强行关闭。
用户要求最后交付人工测试；普通Debug候选/校验值/清单/空白回填已准备，PENDING_USER。
**Phase7A / Phase7 NOT READY；7B Lite PASS；Advanced Hardening DEFERRED；7.5/8 NOT STARTED。**

当前决策与执行范围以[剩余缺口报告](PHASE_7_REMAINING_GAPS_CLOSEOUT_REPORT.md)、
[游戏执行](PHASE_7_GAMEPLAY_EXECUTION_REPORT.md)、
[CI记录](PHASE_7_REMOTE_CI_EXECUTION_REPORT.md)及
[最终人工清单](../PHASE_7_FINAL_MANUAL_ACCEPTANCE_CHECKLIST.md)为准。

---
以下为历史关闭点/证据，旧无remote、旧候选SHA、未执行API26/29等描述不代表当前状态。

Updated: 2026-10-08, Asia/Shanghai. **FINAL CLOSEOUT — NOT READY**.

## Latest final-gate summary

| Gate | Latest status |
|---|---|
|Phase7A-0|PASS_WITH_NOTE / infrastructure READY|
|Phase7A automatic gates|BLOCKED; actual Remote CI unavailable without remote/first commit|
|Phase7A|NOT READY|
|Phase7B Lite|PASS within practical fixed-fixture scope|
|Advanced Hardening|DEFERRED — PRE_PUBLIC_RELEASE_HARDENING|
|Remote CI|BLOCKED; no run ID/commit/result/artifacts|
|Manual Acceptance|PENDING_USER; checklist prepared, automatic prerequisites incomplete|
|Remaining coverage|API26/29 app, representative legal gameplay, Phase2 State NOT_TESTED|
|Remaining renderer risk|Host NVIDIA stall MONITORED/root cause OPEN; software QA MITIGATED|
|Second physical OEM|NOT_TESTED, justified nonblocking limitation under original gate|
|Final Decision|Phase7 NOT READY;7.5/8 NOT STARTED; Release unsigned|

Current candidate API36 minimal regression **5/5 PASS73.303s**, cold relaunch PASS;
installed SHA matches normal candidate. Other valid checks reused without new long-run.
See [final closeout](PHASE_7A_FINAL_CLOSEOUT_REPORT.md),
[evidence index](evidence/phase7/INDEX.md), and
[manual checklist](../PHASE_7_FINAL_MANUAL_ACCEPTANCE_CHECKLIST.md).
Earlier report sections below retain historical runs; latest candidate metadata is
evidence/phase7/7a-final-closeout/candidate-apks.json. Original failures are retained.


Subsequent independently authorized Phase7B Lite: **PASS within practical scope**;
see [Lite report](PHASE_7B_LITE_PRACTICAL_ROBUSTNESS_REPORT.md) and ADR-019.
Advanced Hardening is **DEFERRED — PRE_PUBLIC_RELEASE_HARDENING**. 7A/manual/CI
gates are not waived; Phase7 remains NOT READY,7.5/8 NOT STARTED. Earlier7A APK
hashes and specialist execution statements below describe their historical candidates;
current7B APKs/evidence are in the Lite report. The pre-Lite version of this report is
preserved at evidence/phase7/7b-lite/prior-phase7-report.md.

This report consolidates the Phase7A ordinary QA report, Phase7A-0 infrastructure
report and preserved earlier specialist findings. It does not authorize Phase7B,
Phase7.5 or Phase8. Only actual executed checks are described as passing.

## A. Baseline

Phase0–6 approved reports retained. Baseline version0.6.0/code6; current0.7.0/code7.
Frozen core/toolchain/format details: `PHASE_7A_COMPATIBILITY_STABILITY_RELEASE_QA_REPORT.md`
sectionA and `evidence/phase7/baseline.json`. PROJECT_SPEC/ARCHITECTURE unchanged.

## B. Changed Code

Phase7 fixes periodic A/B/C checkpoint rotation, narrow importer cleanup, artwork
cache eviction/low-memory handling and native control-lock yielding; ADR017.
JNI Debug test declarations isolated from Release. Phase7A-0 changes only scripts,
documents and development SDK/AVDs, no product/core/permission/ABI change; ADR018.
All prior modifications retained. No source rollback, upload or Git commit made.
Resumed7A adds acknowledgement of the existing ordered ViewModel lifecycle
commands; tests distinguish their completion from a periodic-checkpoint PAUSED
state. Strict background zero-frame assertion retained; no native/Session policy
change. This post-infrastructure observation correction is recorded in ADR017.

## C. Compatibility Matrix

See `PHASE_7_COMPATIBILITY_MATRIX.md`; individual passing probes do not establish
all-game compatibility. Internal legal assets only. New targeted retests are not
mislabelled as a full suite on the new emulator target.

## D. Test ROM Corpus

Self-authored ROMs and pinned selected MIT shifter tests;70/70 expected shifter
cases passed. Full upstream suite/toolchain and broader legitimate gameplay
categories incomplete. No commercial ROM/BIOS collected or bundled.

## E. Android API Matrix

Current normal candidate API36 targeted5/5 PASS73.303s; software GPU, official ARM
translation. The following7A-0 three-test timings belong to older candidates.

7A-0 primary API34 official Google image with actual ARM bridge is operational.
API34/API36 minimal instrumentation3/3 each passed; real SAF, orientation, restart
and evidence collection verified. API31 earlier targeted, RTC, upgrade and resource
results retained. API26 x86 boots but original arm64 APK cannot install; API26 ARM
cannot boot on x64 host. API29 has no matching ARM ABI. These gaps are NOT_TESTED,
not product passes. Details:7A-0 report sectionsE–N.

## F. Multi-device Results

Only one physical OEM/API36 phone available. Emulator API coverage does not
constitute additional physical OEM coverage. Physical second-device coverage NOT_TESTED.

## G. Long-run Results

Phone long-run USER_STOPPED, last valid sample815002ms/14 cycles, not60min PASS.
No phone restart. API34 Emulator run started2026-10-08 around00:02+08 and failed
at1226261ms (20min26s),20 cycle samples, final incomplete event. Instrumentation
FAIL1228.019s: background frames2482→2483 at a periodic-checkpoint/lifecycle boundary.
Original log/metrics preserved. Acknowledged checkpoint/Home regression passed
54.829s/8 cycles with the same strict five-second invariant. Full retry started
2026-10-08 around00:31:51+08, FAILED3006.396s; metrics3004375ms/48 cycle samples,
incomplete event. Home/onStop wait timeout with Session RUNNING; logs show PAUSED
then onStop delayed approximately101s, GPU/frame completion stalls approximately99s.
Root cause not established; no matching host sleep events returned, AC standby0.
Same official API34 image now uses SDK software/Google SwiftShader for isolation;
no driver/Windows setting/app changes.50-cycle explicit Home and100 actual shader
changes targeted tests PASS337.991s.50 Home cycles verify actual lifecycle
acknowledgement then five seconds of zero frames;100 mode changes cover all four
modes/context52 and actual nonblack bitmaps.

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

Completed API31 100 IO evidence: RSS start/peak/end275744/280020/276812KiB.
Failed API34 run RSS261512/269280/259528KiB; native heap27890928/32165328/28861792
bytes; Java27899928/27899928/6681712bytes; PSS163764/168229/160192KiB
(start/sampled peak/end). The completed independent software run is below.
Translated native-heap0 readings mean unavailable;
not zero-allocation or proof of no leak. No physical thermal certification.

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

Completed100 IO bounded-growth assertions passed: FD143/144/140, OS threads47/47/44,
JVM43/43/41. Latest API31 repeat also finished100 IO in579893ms. Completed software
long-run FD151/160/157, OS40/53/46, JVM32/49/43. Failed first API34 FD153/160/125,
OS threads39/49/47, JVM31/45/44. No general absence-of-leaks claim.

## J. Lifecycle Stress

Existing recreate/pause/foreground/exit, controlled process death and selected
Home audio/frame checks passed. API34 original sensor/orientation/Home/exit failure
retested unchanged and passed. Checkpoint/Home contention passed8 cycles after
adding actual lifecycle acknowledgement. Later50 Home/checkpoint cycles PASS
337.991s alongside100 actual mode changes; complete software run also verifies
50 Home/foreground/ROM-switch cycles and150 orientation transitions. Host-GPU
failed run/stall retained; not resolved by software PASS.
No forced Session foreground override used to create a pass.

## K. Renderer Stress

Original/Sharp/GBA Color/LCD and viewport/fallback coverage retained. Two previous
API31 settings/performance failures retested unchanged on API34 and passed as part
of3/3 targeted86.557s run. Fullscreen tutorial was observed/dismissed in infrastructure
setup. Historical black-screen original root cause remains unconfirmed. Host GPU
warnings recorded in7A-0; visible screenshots/smoke pass does not certify all drivers.
Separate100 actual mode changes PASS, all four modes/context52/nonblack bitmap
assertions;100 real changes are not confused with120 selections/90 changes in
the full long-run. Final pre-teardown GL counts remain1/1/2/1; no all-zero claim.

## L. Audio

Host PCM checks and API34/36 Home stop/foreground resume counters pass. Native
1x return and pacing assertions passed targeted API34. Real audible quality, latency
and Bluetooth/USB audio routing require physical acceptance; emulator is insufficient.

## M. Input

Prior synthetic touch/HID tests retained. Phase0–6 physical Bluetooth acceptance
is historical. Current Phase7 physical HID/manual feedback pending; USB NOT_TESTED.
No new Bluetooth permission; system handles pairing.

## N. Sensors / RTC

Existing scalar/RTC checks retained; independent API31 wall-clock check639.010s
passed. API34 sensor register/unregister/rewind lifecycle targeted check passed,
not proof of actual motion/light behavior. Rumble DEFERRED—V1 zero-permission policy.

## O. Library / ZIP

Existing ordinary ZIP/import/cancel/deadline tests and UI/Room1000-record/cache
stress passed. Real API34 SAF picker, GBA/ZIP import and cancellation/reopen verified.
API36 real SAF GBA import verified. These do not establish malformed/fuzz coverage.

## P. Persistence

Battery/state separation, Quick/Slot, A/B/C autosave and100 IO regression evidence
retained; normal injected write/commit failures passed. GameId/source/save formats
unchanged. Existing Phase7IoFailureTest XML:3/3 PASS in0.721s, already in the61
normal executions. Injected directory-fsync failure, actual missing-source rename
failure and injected ENOSPC preserve the previous committed manifest/battery.
No actual full-drive exhaustion or universal IO failure coverage is claimed.
Last completed checkpoint is the sudden-process-death guarantee, not retention
of every frame. No new execution was needed for this evidence correction.

## Q. Room / DataStore Upgrade

Explicit1→2→3 schemas, no destructive fallback. Actual Phase6 APK data upgraded
on isolated AVD, verification18.534s passed after seed12.570s. Controlled process
death/new PID recovery9.451s passed. Actual Phase3–5 native State reads passed;
Phase2 actual archived fixture unavailable. No user-phone downgrade/clear.

## R. Malformed ROM / ZIP

Historical advanced campaign is deferred. Subsequent7B Lite fixed ROM/ZIP
fixtures PASS; production import/native small-ROM boundaries fixed with regressions.
See Lite sectionsB–E. No new fuzz campaign or all-parser audit is claimed.

## S. Fuzz

NOT COMPLETE. Earlier raw native parser harness found a small-header bounds
failure at mGBA vfame.c:63; that historical raw-harness result did not establish
production adapter reachability. Lite subsequently added bounded owned backing for
the identified small-ROM detector reads and fixed-fixture JNI passes; this does not
close arbitrary-input parser audit or establish full fuzz safety.
Earlier JVM bounded mutation is not full fuzz PASS. No new fuzz run here.

## T. UBSan

Full-dependency UBSan NOT COMPLETE. Earlier fully instrumented one-shot halted on
signed-left-shift at mGBA util/hash.c:32; this finding remains open/deferred. Subsequent
existing Android bridge/audio/Oboe UBSan:JNI17/17 andActivity15/15 PASS. The pinned
mGBA target is not instrumented by that switch; see Lite sectionJ/build flags.

## U. ASan

NOT COMPLETE. Earlier Windows attempt lacked required runtime; earlier native
test crash evidence retained. No comprehensive ASan PASS, no new execution here.

## V. Native Hardening

Existing build flags/source remain. No new Release ELF specialist validation in
7A-0/resumed7A; advanced ELF validation is DEFERRED with its own future proof required.
Lite is PASS within its distinct practical scope. No security-policy bypass.

## W. Dependency / License / Provenance

Prior ordinary audit passes; pinned official sources/licenses and test manifests
retained. No unknown application.so, runtime networking/ads/analytics SDK added.
New emulator images are official SDK development components, not application payloads.

## X. CI

Pinned ordinary workflow configuration exists. Mandatory real remote CI BLOCKED:
no Git remote and no first commit/HEAD; run ID/result/duration/artifact unavailable. Local builds do not count as remote
CI. No source upload or remote repository creation authorized/performed.

## Y. APK Audit

Current post-Lite ordinary candidates: Debug `a0cc12c18cab69a6294cdf95fafd516899cb957a1090f9897463b304aef24051`;
unsigned Release `6e30e42213e043ee7408f4d2ccf520b2056189cb6629b9beab9a67d81c605905`.
Closeout SHA/permission dumps match archived and build-output APKs; no production
change/rebuild in this closeout. Build-output timestamps/UNAVAILABLE_NO_HEAD in
candidate-apks.json. No two-clean-build claim for this new SHA. The reproducibility
paragraphs below describe older7A candidates only.

Permissions empty; no ROM/ZIP/BIOS/test providers/Debug JNI probes in Release.
Prior two clean unsigned Release builds/hashes retained; prior SHA256
`079ad749f9b0dbb227b77a0854372729fae485329856ea459fb151144f387696`.
7A-0 did not change Release. Subsequent7A lifecycle acknowledgement requires a
new candidate: two clean builds bit-identical with unchanged dependencies;
`7a-lifecycle-ack-release-reproducibility.json`, SHA256
`77dbada49b1099b96ead34de3cc39eb92fc06f4e252ba1d33af685d3861d4fb7`.
New candidate ordinary APK/provenance audit PASS; lint0errors/4warnings;
Home audio/frame invariant PASS7.638s. Old source-set audit evidence retained.
Release unsigned; no production keystore or release authorization.

## Z. Manual Acceptance

**PENDING_USER**. Checklist prepared at docs/PHASE_7_FINAL_MANUAL_ACCEPTANCE_CHECKLIST.md;
automatic gates remain BLOCKED, not final-signoff ready. User agreed to inspect their legitimate local RPG;
agreement is not PASS. No game name/ROM/screenshots requested or collected.
Physical HID/sensors/OEM/audio/GPU/thermal/final gameplay cannot be substituted
by emulator results. Feedback requested without starting a new manual session.

## AA. Known Issues

Historical renderer root cause unconfirmed; official translated emulator performance
varies. Original API31 failures preserved despite all five targeted API34 retests
passing. SDK download fallback/host graphics/script UI limitations recorded in7A-0.
Historical upstream/full-dependency findings remain open; Lite production boundary
fixes and fixed-fixture retests are recorded separately. Host/NVIDIA99-second graphics
stall remains unconfirmed despite completed software isolation; no physical fix claim.

## AB. Risks

API26/29 arm64 coverage, broader legal gameplay and physical routing/HID coverage
incomplete; single OEM. Native8x best effort. No long-term physical thermal/battery
certification, no real remote CI, no comprehensive sanitizer result, unsigned Release.

## AC. Unfinished

| Gate | Status |
|---|---|
|7A-0 Emulator infrastructure|PASS_WITH_NOTE / READY|
|Paused five ordinary failed assertions|Targeted API34 PASS; original failures preserved|
|60-minute mixed stability/resource analysis|Software API34 PASS3614.31s; two host failures retained; phone stopped|
|Home/checkpoint and real shader changes|50 cycles /100 changes PASS337.991s|
|API26/29 application coverage|NOT_TESTED—official available image/ABI limits|
|Remote CI|BLOCKED—no remote/first commit/HEAD; configuration is not a run|
|Final physical/manual acceptance|PENDING_USER; prepared checklist, untested hardware|
|Phase2 historical raw State|Fixture unavailable|
|7B Lite practical robustness|PASS; full scope/evidence in Lite report|
|Advanced Hardening / full-dependency findings|DEFERRED — PRE_PUBLIC_RELEASE_HARDENING; historical findings retained|
|V1 signing/release|NOT READY; unsigned|

## AD. 是否允许进入 Phase 8

**NOT READY — 不允许进入 Phase 8。**

7A-0基础设施就绪只支持继续Phase7A，不等于Phase7完成。缺失的CI、兼容性、
人工证据不能改写为PASS。7B Lite通过不补齐7A门槛。Phase7.5/8均未启动，V1 Release仍未就绪。
