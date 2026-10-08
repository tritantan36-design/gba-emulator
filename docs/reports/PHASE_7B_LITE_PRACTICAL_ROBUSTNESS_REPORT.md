# Phase 7B Lite — Practical Robustness Report

日期：2026-10-08，Asia/Shanghai。项目：`D:\GPT\projects\gba-emulator`。
本报告依据用户提供的 Lite 范围独立执行；原始暂停与失败证据保留。

## A. Scope

GBA only、自用、完全离线、零权限。没有新增用户功能、联网服务、存档格式或
GameId 算法。使用固定本地 synthetic fixture、项目原创合法 ROM 和仓库既有测试入口。
高级 fuzz/ASan/HWASan 不运行。设备为项目自有 `GBA_Lite_API34`，显式 serial
`emulator-5572`，arm64 APK 经官方模拟器 ARM translation 执行；不冒充物理 ARM 硬件测试。

## B. Malformed ROM

PASS：0/1/191/192 字节、固定随机192字节、坏头、header-only/truncated、全0、全FF、
随机内容、超大流式输入拒绝；临时文件清理、已有正常 snapshot 保持。
新增 native fixture 经生产 JNI FD 入口测试已知/未知长度；超大 sparse 文件检查长度拒绝。
最小256字节幂次 fixture 的已知/未知长度 load/close 也实际通过。
正常332字节 bringup 与401字节 persistence ROM 在完整 JNI 中正常运行。

导入最小长度改为256，且与 native 一样检查 byte3=0xEA、byte0xB2=0x96。
native 小文件 owned backing 零扩展到512，VFile 保留原始逻辑长度。
这覆盖已识别的固定 detector 读取，不是对任意 malformed 内容或所有 native 代码的证明。

## C. ZIP Robustness

PASS：corrupt central directory、truncated ZIP、CRC mismatch、encrypted flag、nested ZIP、
duplicate ROM name、case/NFC collision、非法UTF8、未知附带文件以及多ROM拒绝。
正常单ROM ZIP、Unicode 名称、可允许 README/LICENSE 和直接GBA的内容身份一致。
每个既有异常 ZIP fixture 现在先保留正常 ROM，并确认拒绝后文件集合与内容不变。
encrypted fixture 验证政策拒绝，未运行密码破解或穷举。

## D. ZIP Path Safety

PASS：`../`、绝对路径、drive path、backslash、重复分隔符和嵌套逃逸拒绝。
新增受控临时父目录中的 sibling sentinel，确认导入目录及兄弟正常文件均不被修改。
输出名始终由内容 SHA256 生成，没有按 ZIP entry 名创建目标路径。

## E. ZIP Resource Boundaries

PASS：entry count<=64、路径UTF8字节<=240、深度<=8；输入<=40MiB、展开总量<=40MiB、
单ROM<=32MiB、压缩比<=200。超大声明、过量条目、高压缩比与超大 ZIP 输入流均实际拒绝。
读取使用64KiB缓冲；处理有取消与10秒 guard，SAF 慢源 deadline 由既有 Activity 测试覆盖。
不宣称能中断任意 Java 阻塞 read；不宣称测得所有可能文件的峰值内存。
nested archive/encrypted policy：拒绝，不递归解压。

## F. State / Save Protection

PASS：truncated/hash-corrupt State、missing metadata、wrong GameId/core/schema、非法manifest
路径、invalid autosave current、坏当前generation、missing backup、安全读错与写错保护。
新增坏 State 写入验证 snapshot 全部SHA不变，且另一游戏仍可读；坏 battery且无backup时
拒绝后续写入；有效current不因backup缺失而不可读。坏JSON/未知版本读取不重置generation。
既有 battery backup recovery 和 ENOSPC/fsync/实际rename failure 保留有效提交。
Quick/manual 损坏时报告错误；不会静默加载旧overwrite。Autosave通过其他有效A/B/C候选回退。
invalid current pointer 不扫描任意未知generation进行猜测修复；原始文件保留。

## G. ROM Switch Isolation

PASS（复用7A实际证据）：100次IO/ABC开关、另一个完整60分钟测试中的50次切换。
源码断言覆盖先退出旧session再开新ROM、预期GameId、checkpoint、State与截图的游戏目录，
资源关闭后FD/threads有界。`rom-switch-results.json` 固定引用文件的SHA256。
Playtime monotonic 与错误ROM不能污染旧save的 JVM 回归本轮重新通过。
没有声称重新运行长测，也没有把多个中断时段拼成60分钟。

## H. Quick / Slot / Autosave

PASS：复用真实100 Quick+100 Slot证据和100 IO evidence；本轮完整普通JNI、Persistence
Activity、storage/session JVM重新覆盖保存读取、battery不回滚、metadata/hash/identity/sequence。
已完成50 Home/checkpoint cycles与60分钟background/foreground证据复用。
坏缩略图与missing thumbnail安全回退、300-image cache有界的Activity用例通过；thumbnail失败
不影响有效state的session回归通过。没有重复无意义高次数压力测试。

## I. JNI Regression

普通完整JNI：17/17 PASS，43.153s。修复前现有版本15/15 PASS，43.172s。
host：audio1/1 + mGBA7/7 PASS；正常MSVC环境实际执行既有`scripts/native-tests.cmd`。
首次沙箱MSVC PDB错误保留，不计PASS；正常环境同一脚本通过。
普通Activity原批：27 PASS /2 FAIL，233.766s。最终修正测试APK定向11/11 PASS，134.958s，
包含两个失败方法、整个Library测试类、metadata fallback。原29项单批仍记录FAIL；
不声称重跑了一个完整29/29批次。最终覆盖的每个普通回归方法均有通过结果。
恢复普通APK后的最终启动/重建/Persistence烟测8/8 PASS，69.069s。

## J. UBSan

既有 `-PgbaUbsan=true` 路径实际构建；完整JNI17/17 PASS，44.209s。
UBSan Activity/native：15/15 PASS，229s；包含Activity/Persistence/Player全类、旋转24次、
Home音频及Metadata固定回归，无trap或native crash。未称完整29项Activity批次。
编译证据 `ubsan-build.ninja`：自有bridge带`-fsanitize=undefined -fsanitize-trap=undefined`；
上游mGBA目标自己的COMPILE_OPTIONS覆盖继承选项，其编译行没有UBSan标志。
继承该标志的audio/Oboe目标也在此既有构建范围内；逐object记录见`ubsan-instrumentation.json`。
不能称全mGBA UBSan通过。
历史全依赖插桩 `util/hash.c:32` signed-left-shift finding 未修复；调查保留到Advanced Hardening。
没有为Lite新增复杂sanitizer环境，也没有用未插桩依赖的通过结果关闭历史发现。

## K. Native Lifecycle

普通JNI stale/invalid handle、repeated create/destroy、load/close、rewind、Save/State全部通过。
Activity覆盖正常启动、重建、旋转、快进/倒带、Home、后台帧/自有audio停止与恢复。
复用长期开关/资源证据，不将有限回归表述为全路径无泄漏证明。
本轮没有观察到可重复native crash、ANR或OOM。旧暂停、主动停止和历史失败日志未删除。

## L. Metadata / Settings Fallback

PASS：storage JSON/版本/指针失败保留有效数据；Display/Peripheral未知版本与坏内容安全回退。
新增Android input profile测试：missing/invalid JSON/future schema/incomplete controls返回默认值，
坏文件不被读操作覆盖，独立landscape有效设置与文件SHA不变。旧设置和thumbnail回归保留。

## M. Findings

1. 原导入门槛接受header-only及与native启动签名不一致的文件，可能留下不可启动Library row。
2. pinned VFame小幂次ROM detector访问超过逻辑长度；生产owned backing必须提供有效边界。
3. 旋转固定休眠不足以证明EGL资源就绪，原批纹理数断言得到0。
4. rename click后立即输入时对话框尚无SetText node，原批失败。
5. 既有UBSan开关不等于mGBA全插桩；历史signed-shift finding不能关闭。

## N. Fixed Findings

1/2：统一256字节门槛、导入匹配native ARM签名、owned512-byte backing且保留logical length。
新增固定JNI边界及短文件拒绝；GameId/正常数据格式不变；上游源码未改。
3/4：测试增加有界真实就绪等待，保留原资源数、颜色、viewport、rename最终值等断言。
最终定向11项通过。一次定向运行误装旧test APK，SHA不一致已识别，原失败日志保留；
最新已安装test APK SHA核对为`5be91da2d4aa6fb27cfbb1b9be24047f38b526053f3d383af604ba90931a0d72`。
初始512字节门槛误拒合法tiny homebrew，产生8 JNI失败；已修正并完整17项复验通过。
详见ADR-019。历史upstream parser/UBSan发现未伪称全部修复。

## O. Deferred Advanced Hardening

**DEFERRED — PRE_PUBLIC_RELEASE_HARDENING**。
详见 `PHASE_7B_DEFERRED_ADVANCED_HARDENING.md`：libFuzzer、ZIP mutation、ASan/HWASan、
Linux/WSL campaign、extended corpus、advanced ELF、long-duration fuzz及full-dependency UBSan。
没有新执行这些高级任务。

## P. Remaining Risks

这是有限固定fixture与已有实际回归，不是完整安全审计、全fuzz覆盖或全依赖sanitizer通过。
不足256字节的非常规ROM现在拒绝；正常项目tiny homebrew兼容已验证。
7A人工验收、代表性旧ARMAPI、远程CI、广泛合法游戏与Phase2历史State等门槛
仍未满足。第二OEM NOT_TESTED，原7A条件为设备可用时执行，属于justified limitation。
原host图形停顿风险保留。模拟器通过不替代物理HID/audio/sensor/thermal验收。
Release仍unsigned；未运行正式签名或发布。突发进程死亡仅保证最后成功checkpoint。

## Q. Evidence

位置：`docs/reports/evidence/phase7/7b-lite/`。
`malformed-rom-results.json`、`zip-regression-results.json`、`path-safety-results.json`、
`save-state-results.json`、`rom-switch-results.json`、`metadata-fallback-results.json`、
`jvm-results.json`/`jvm-xml/`、`device-results.json`、`jni-final.log`、`host-native-2.log`、
`activity-normal.log`、`activity-latest-targeted.log`、`ubsan-jni.log`、`ubsan-activity.log`、
`ubsan-build.ninja`、`deferred-hardening-list.md`、`artifact-hashes.json`、`source-hashes.json`。
最终JVM75次执行、0 failures/errors；重复Android Debug/Release测试variant计为不同执行，
不冒充75个不同测试方法。收集脚本：`scripts/phase7b_lite_evidence.py`。
修复前/中间失败及旧测试APK复验全部保留，原7A证据仅引用且记录SHA。
Debug/Release APK权限列表均无uses-permission，特别是无INTERNET。
最终普通构建/lint/离线APK与上游源码审计PASS；lint0 errors/4 warnings。
交付在`normal-restored-apks/`，与`delivery-apks/`对应文件SHA一致；模拟器安装普通版SHA已核对。
Debug SHA256：`a0cc12c18cab69a6294cdf95fafd516899cb957a1090f9897463b304aef24051`。
unsigned Release SHA256：`6e30e42213e043ee7408f4d2ccf520b2056189cb6629b9beab9a67d81c605905`。
这是7B后续候选，7A历史双构建可重现性证据仍属于旧候选；没有宣称新候选做了两次clean重建。

## R. Phase 7B Lite Decision

**Phase 7B Lite — PASS（本报告限定范围）。**

- Phase 7A — NOT READY：原未满足门槛保留，包括联合人工验收与实际remote CI等。
- Phase 7B Lite — PASS：固定异常输入、数据保护、普通JNI/native生命周期回归及失败修正复验完成。
- Existing Android UBSan — PASS within bridge/audio/Oboe scope；full mGBA instrumentation不包含在此PASS中。
- Advanced Hardening — DEFERRED — PRE_PUBLIC_RELEASE_HARDENING。
- Phase 7 — NOT READY：7A尚未PASS，不能标为READY_FOR_7_5。
- Phase 7.5 — NOT STARTED。
- Phase 8 — NOT STARTED；V1 Release — NOT READY，Release unsigned。

当前没有继续运行的回归/压力任务；未重新启动手机长测。后续Phase7.5改动仍须保留Lite回归。

后续7A最终收尾：API36当前候选最小5/5 PASS73.303s；Lite结论不变。
Remote CI BLOCKED（无remote/首次commit），Manual PENDING_USER；见
[final closeout](PHASE_7A_FINAL_CLOSEOUT_REPORT.md)及[evidence index](evidence/phase7/INDEX.md)。
