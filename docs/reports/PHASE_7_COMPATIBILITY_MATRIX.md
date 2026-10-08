# Phase 7 compatibility matrix

Updated: 2026-10-08. Phase7A automatic gates BLOCKED / NOT READY. Only executed checks are marked PASS.
Host video/PCM and native mailbox checks do not establish human visual/audio or
physical controller acceptance. Historical Phase 0–6 acceptance is not new Phase 7
evidence. All ROMs stay out of production APKs.

| ROM/Test | Source | License Tier | Core Boot | Video | Audio | Input | Save | State | FF | Rewind | RTC/Sensor | 30min+ | Result | Notes |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Internal bringup | test-rom/bringup.s | A / Apache-2.0 | PASS | PASS | PASS_WITH_NOTE | PASS | NOT_APPLICABLE | PASS | PASS_WITH_NOTE | PASS_WITH_NOTE | NOT_APPLICABLE | NOT_TESTED | PASS_WITH_NOTE | Host/earlier JNI passes retained. Two final API31 pacing/ring failures remain recorded; both unchanged methods PASS on API34, 21.911s. 8x best effort; no new human listening conclusion. |
| Internal persistence | test-rom/persistence.s | A / Apache-2.0 | PASS | PASS | NOT_TESTED | PASS | PASS | PASS | PASS | PASS | NOT_APPLICABLE | NOT_TESTED | PASS_WITH_NOTE | Native battery/state isolation and 100 Quick + 100 Slot transactions passed. |
| Internal color-pattern | video-tests.json | A / Apache-2.0 | PASS | PASS | PASS_WITH_NOTE | NOT_APPLICABLE | NOT_APPLICABLE | PASS_WITH_NOTE | PASS_WITH_NOTE | PASS_WITH_NOTE | NOT_APPLICABLE | NOT_TESTED | PASS_WITH_NOTE | Final API31 Original/2x performance failure preserved. Unchanged all-mode FF/rewind/state/image/performance and background settings methods PASS in API34 targeted3/3 run, 86.557s. API34/36 Home audio smoke passed; not audible quality or a new full suite. |
| Internal lcd-pattern | video-tests.json | A / Apache-2.0 | PASS | PASS | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_APPLICABLE | NOT_TESTED | PASS_WITH_NOTE | Fine-pattern raw framebuffer test; no new human LCD assessment. |
| Internal RTC probe | peripheral-tests.json | A / Apache-2.0 | PASS | NOT_TESTED | NOT_APPLICABLE | NOT_APPLICABLE | NOT_TESTED | PASS | PASS | NOT_TESTED | PASS_WITH_NOTE | NOT_TESTED | PASS_WITH_NOTE | Short real-device probe and independent API31 639.010s wall-clock/FF/Home/state/reopen check passed. No repeat required; not final physical sensor/gameplay acceptance. |
| Internal Tilt/Gyro/Solar probes | peripheral-tests.json | A / Apache-2.0 | PASS | NOT_TESTED | NOT_APPLICABLE | NOT_APPLICABLE | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | PASS_WITH_NOTE | NOT_TESTED | PASS_WITH_NOTE | Host GPIO and normal JNI scalar input tests. Final API31 listener failure preserved; unchanged sensor/orientation/Home/rewind/exit method PASS in API34 targeted3/3 run. Emulated sensor registration is not physical motion/light acceptance; historic Phase5 approval only. |
| Internal rumble mailbox | peripheral-tests.json | A / Apache-2.0 | PASS | NOT_APPLICABLE | NOT_APPLICABLE | PASS | NOT_APPLICABLE | NOT_TESTED | NOT_TESTED | NOT_TESTED | PASS_WITH_NOTE | NOT_TESTED | PASS_WITH_NOTE | Mailbox only; physical output DEFERRED — V1 zero-permission policy. |
| Selected mGBA shifter suite | mgba-emu/suite e6942030d25ffe3ba76c72b73a86da073ec857cc | A / MIT, retained selected source | PASS | PASS_WITH_NOTE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_APPLICABLE | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_APPLICABLE | NOT_TESTED | PASS_WITH_NOTE | 70/70 original expected cases passed. Selected driver, not full upstream/timing/BIOS suite. |
| Internal A/B/C stress variants | phase7-stress.json | A / Apache-2.0 | PASS | PASS | NOT_TESTED | PASS | PASS | PASS | PASS_WITH_NOTE | NOT_TESTED | NOT_APPLICABLE | PASS_WITH_NOTE | PASS_WITH_NOTE | Software API34 real elapsed3612378ms/60 cycles complete, instrumentation PASS3614.31s.50 Home/ROM-switch cycles and150 orientation transitions; requested FF/state/checkpoints pass. Rewind branch conditional, no per-action count: independent checks retained. Dedicated100 actual mode changes/50 Home cycles PASS337.991s. Automated internal corpus, not human gameplay/audio acceptance. Two host failures and phone USER_STOPPED retained. |
| Platformer homebrew | Local manual corpus | C / not acquired | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | No third-party ROM vendored or tested. |
| Action / audio-heavy homebrew | Local manual corpus | B after asset audit, otherwise C | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | BeatBeast candidate; source MIT alone does not complete asset audit. |
| High-load / 3D homebrew | Local manual corpus | B after asset audit, otherwise C | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | Varooom 3D candidate, not acquired. |
| Save-heavy / RTC-sensor gameplay | Local manual corpus | C / not acquired | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | Internal probes cannot substitute for real-game breadth. |
| local user-owned commercial ROM manual compatibility | Private local only | D | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | NOT_TESTED | No commercial ROM collected, copied, named or uploaded. |

Evidence: evidence/phase7/7a-host-portable-final.log,
7a-selected-suite-details.log, 7a-normal-jni.log,
7a-transactions-idle-device.log, 7a-resume-api34-targeted-jni.log,
7a-resume-api34-targeted-activity.log. Original failures remain in the pause checkpoint.
See the Phase 7A report for current open gates.

## Final closeout API/device coverage

| Target | Current coverage | Status |
|---|---|---|
|API26|TEST-ONLY x86_64 boot/install, five areas across original4/5 + targeted1/1, real SAF and visible red frame, relaunch|PASS_WITH_NOTE; physical ARM not tested|
|API29|TEST-ONLY x86_64 original4/5 + targeted1/1; real SAF import; black frame recovered after pause/continue and Home; relaunch|PASS_WITH_NOTE / display risk MONITORED|
|API34|7A-0/main flow, current Lite regression, prior completed software long-run reused|PASS_WITH_NOTE|
|API36|Current candidate targeted5/5 73.303s, installed SHA and cold relaunch verified; real SAF evidence reused from7A-0|PASS_WITH_NOTE|
|Second physical OEM|Not available; original requirement conditional on availability|NOT_TESTED / justified limitation|

Current API36 uses software GPU/official ARM translation, not physical performance or
audio/HID/sensor acceptance. Representative complete-gameplay categories in the ROM
table remain NOT_TESTED; internal probes/elapsed stress are not those games.
Actual Phase3–5 State reads and Phase6 upgrade reused PASS; Phase2 fixture NOT_TESTED.
Renderer root cause status and remaining blockers:
[final closeout](PHASE_7A_FINAL_CLOSEOUT_REPORT.md).

Latest detailed coverage: [API26/29 execution](PHASE_7_API26_29_COMPATIBILITY_REPORT.md).
Fresh release-specific [asset license review](PHASE_7_GAMEPLAY_LICENSE_REVIEW.md)
identified mixed/restricted terms; no new game acquired or played. Full-game categories
remain MISSING. Remote CI PASS (run 37740246698). Phase7 remains NOT READY.
