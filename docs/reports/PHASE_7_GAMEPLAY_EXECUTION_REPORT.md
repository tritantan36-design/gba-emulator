# Phase 7 legal gameplay execution

2026-10-08, Asia/Shanghai. Additional full homebrew games were tested on the
project-owned API29 x86_64 software-GPU emulator with the TEST-ONLY compat APK.
This is new gameplay breadth; existing API34/36, 50 Home, 100 display changes,
60-minute internal stress, JNI17, host native and UBSan results were reused.
No production code, product ABI, permissions, GameId, Save identity, mGBA or frozen
toolchain changed. No third-party game bytes were added to either APK or Git/CI.

| Game / exact binary | Short smoke | Representative script | Scope |
|---|---|---|---|
|Blob Goes 3D v1.1, SHA `313cbb23444ef35497b4de23a459311f732c885d392f5e465ebe1861a26746f4`|PASS 1/1, 35.449s|PASS 1/1, 616.727s; requested gameplay600s|Actual 3D level, directions/jump, Quick and slot1 save/load, 4x→1x, rewind, checkpoint, Home pause/frame stop, foreground and Continue with visible TextureView|
|Hyperspace Roll / agb v0.25.0, SHA `de5a61698452d5f409970f1f5e042d0d4ace21ccc0ea7d2a770b8c311b8f3c57`|PASS 1/1, 41.597s; actual battle screenshot inspected|PASS 1/1, 626.748s; requested session600s (coverage PARTIAL)|Title/customise/dice battle; music loops and SFX; same state/speed/rewind/lifecycle checks; upgrade choices can stall the simple input script|

Hyperspace result: totalElapsedMs625608,1265 harness input actions,17 distinct sampled
frame CRC32 values. Gameplay-end frames36926, nonzero PCM samples40492069.
The whole elapsed session includes configuration; it is not600s continuous combat.

Blob long-run device result: totalElapsedMs615707, 764 input actions, 15 distinct
sampled framebuffer CRC32 values. At gameplay-end:36381 frames,4941802 nonzero
PCM samples, renderer fallbacks0; lateFrames736/audioUnderruns153 are retained,
not converted to a human smoothness or listening-quality PASS. After Continue a
new core resets counters, so the final counter is not the full-session total.
Input and changing frames do not establish level completion or exact game-progress
semantics. State API success/fresh-frame checks are narrower than human acceptance.

## Reproduction and isolation

[Inventory](../../test-rom/manifests/phase7-gameplay-local.json) pins the official
release/source revisions, hashes, component licenses and distribution decisions.
[License review](PHASE_7_GAMEPLAY_LICENSE_REVIEW.md) is separate from execution.
Stage the exact reviewed ROM under `files/phase7-gameplay/` of both the test and
target packages. The test-only provider has a two-file/hash allowlist; no arbitrary
path, automatic download or embedded game asset is accepted. Build with
`-PapiCompatibility=true :app:assembleCompat :app:assembleCompatAndroidTest` for
an owned x86_64 emulator. Explicit arguments are required:

```text
adb -s <owned-emulator> shell am instrument -w -r
  -e class dev.gbalite.app.Phase7GameplayTest
  -e phase7Gameplay blob-v1.1
  -e phase7GameplaySeconds 600
  dev.gbalite.app.test/androidx.test.runner.AndroidJUnitRunner
```

Use `hyperspace-v0.25.0` for the second game. These are local opt-in tests; ordinary
CI compiles them but does not download/stage/execute full games. Test provider URI
import is not a fresh system DocumentsUI/SAF picker run. Existing real SAF evidence
and API29 black-frame MONITORED status are unchanged. Production candidates remain
arm64 only; no physical ARM API29 performance or GPU conclusion follows.

## Original failures retained

New harness setup iterations are all preserved locally: file URI rejected by the
content-only importer, sparse-title grid sampling false black, intentional frame
invalidation after State import, foreground Activity task identity/CLEAR_TASK, and
normal45s checkpoint transient PAUSED. Fixes use a content provider, whole actual
TextureView scan, fresh-frame readiness, preserved MAIN/LAUNCHER identity with
explicit flags, and bounded RUNNING wait plus persistence-error assertion. No
production assertion or failed CI job was removed. Separate short/long passes do
not rewrite those failed executions. No new product renderer root-cause fix is claimed.

## Coverage and handoff

Platformer and high-load/3D have bounded scripted game coverage. Action breadth is
PARTIAL (3D platformer and dice battle, not a broad real-time action corpus).
Audio-heavy game evidence remains PARTIAL: generated/mixed PCM is present, but the simple
script can remain on the upgrade screen when a new face equals the selected old face.
The screenshot and upstream condition were inspected; this is an input-script coverage
limit, not an emulator deadlock. Long elapsed time alone does not establish continuous battle
or representative full soundtrack/level coverage, and listening quality is untested.
Save-heavy remains PARTIAL: Quick/Slot/checkpoints and Flash/SRAM paths do not
establish a save-intensive RPG/city-game progression test. RTC/sensor full-game
coverage remains MISSING; prior internal probes are PARTIAL and cannot replace it.
No 30-minute full-game or physical user acceptance is claimed.

Raw instrumentation logs, result.json/events.jsonl, source/notice SHA reviews and
actual screenshots are local-only under `evidence/phase7/gameplay-closeout/`.
The user requested manual testing at the end. Installable ordinary Debug/checksums,
checklist and blank result form are prepared under `evidence/phase7/manual-handoff/`.
Manual PENDING_USER; Phase7 NOT READY until gameplay scope/limitations and actual
physical feedback are resolved. Phase7.5/8 NOT_STARTED.
