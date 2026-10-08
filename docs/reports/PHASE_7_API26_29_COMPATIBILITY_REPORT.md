# Phase 7 API26 / API29 compatibility

2026-10-08. **PASS_WITH_NOTE for the executed TEST-ONLY emulator scope.**
The production arm64 APK is not claimed to run on x86 or to have been tested on
physical ARM API26/29 hardware. No GameId, save identity, product permission,
native core or frozen toolchain change was made.

Opt in with `-PapiCompatibility=true` and build `:app:assembleCompat
:app:assembleCompatAndroidTest`. The app label is `GBA Lite TEST-ONLY / NOT FOR
RELEASE`, and its manifest sets `android:testOnly=true`. Install using `adb install
-r -t` on an explicitly selected project emulator. The variant inherits Debug and
adds x86_64. AGP merges the default arm64 filter, so the resulting compatibility APK
contains **arm64-v8a and x86_64**. Ordinary Debug/Release remain arm64-v8a only.
Test probe declarations are confined to Debug/compat; Release source boundaries
and the zero-permission audit pass.

| Check | API26 | API29 |
|---|---|---|
|Official image / owned AVD|x86_64 rev16 / GBA_Lite_API26_Compat, emulator-5576|x86_64 rev13 / GbaLite_QA_API29, emulator-5578|
|Boot, adb, install, launch|PASS|PASS|
|Library/search/details/rename/remove/settings|Original batch FAIL in LazyColumn test scroll; corrected test targeted PASS 1/1, 6.635s|PASS in five-test batch|
|ZIP/duplicate/invalid input|PASS|PASS|
|Battery, four slots, Quick, background/recreate, exit/Continue|PASS in original batch|Original batch FAIL due asynchronous state-operation test synchronization; corrected targeted PASS 1/1, 13.673s|
|Real Home / foreground, own audio counter|PASS|PASS|
|Framebuffer, thumbnails, screenshots, orientation, visible frame|PASS|PASS|
|Real DocumentsUI Downloads import|PASS; original Apache-2.0 phase7-stress-a.gba|PASS; same original asset|
|Imported ROM play smoke|Visible red test frame|Initial black frame; red frame recovered after pause/continue and Home/foreground. MONITORED; no root-cause fix claimed|
|Cold relaunch|PASS|PASS, COLD 818ms|
|Final emulator shutdown|PASS|PASS|

Original batches are **4 PASS / 1 FAIL**, respectively 36.114s and 55.395s. The
targeted passes are separate runs; neither batch is relabeled 5/5. Library scrolling
now targets the LazyColumn container before clicking its composed item. Persistence
tests now wait for an enabled operation button, a newly committed state generation,
and completion rather than accepting an old current.json as proof of a new save.
No product behavior was changed to make these assertions pass.

API29's SAF black frame and successful recovery are both retained in screenshots
and app logs. This path has a known observed limitation and is not clean visual
acceptance. The later manual helper could not find a scrolled Exit menu item; it
then performed a successful force-stop/cold relaunch. Normal in-app exit/Continue
was exercised by the persistence test, not established by that helper invocation.

Local raw evidence: `docs/reports/evidence/phase7/remaining-gaps/api26-*` and
`api29-*`. It is intentionally not published. All tests use original internal
homebrew; no representative complete-gameplay or physical HID/audio/sensor/thermal
acceptance is implied. Existing API34/36 and long-run evidence is reused.

## 后续完整 Homebrew 定向验证

API29 同一TEST-ONLY compat包新增Blob Goes 3D v1.1完整游戏输入脚本：短烟测
1/1 PASS35.449s；10分钟脚本1/1 PASS616.727s。Quick/Slot、4x快进、倒带、
Home恢复、Continue及实际TextureView可见帧已检查。它使用严格哈希的测试provider，
不是新增系统DocumentsUI picker测试；不改变既有SAF黑帧MONITORED结论。
Hyperspace Roll短烟测1/1 PASS41.597s，截图确认实际骰子战斗，10分钟session1/1 PASS626.748s；覆盖仍PARTIAL，详细见
[gameplay执行报告](PHASE_7_GAMEPLAY_EXECUTION_REPORT.md)。这些不是普通arm64包在API29实机运行的证据。
