# Phase 6 — Library / App Experience

日期：2026-10-07。项目：`D:/GPT/projects/gba-emulator`。版本：0.6.0 / versionCode 6。

**Phase 6 — READY**
最终人工验收日期：2026-10-07。用户明确反馈：“人工确认通过”。
Phase 5 READY 保留；实体 Rumble 为 DEFERRED — V1 zero-permission policy。
Phase 7 NOT STARTED；V1 Release NOT READY。本报告为阶段关闭状态记录。

## A. 实际完成

已实现 Home / Continue / Recent / Library、GBA 与单 ROM ZIP 导入、详情、重命名、移出、
重新绑定、搜索及四种排序、单调现实时间计时、本地图片回退和统一设置入口。
导入先进入游戏库并提供“打开游戏”，重复内容保持单一身份；冷启动默认首页。
最终完整 Activity 套件 28/28、JNI 15/15 通过；发现的加载与导航问题已修复。
用户已完成并确认最终人工验收通过；人工反馈与自动测试证据分别记录。

## B. 架构变化

实施前记录 ADR-016。沿用 app / data / storage / emulator-session / core-api / feature-player，
没有新增 Gradle 模块或第三方依赖。app 协调 UI 和用例；data 管理 Room / SAF / 图片索引；
storage 的 RomImport 是纯 JVM 有界校验器；Session 串行计时及持久化；UI 不访问 JNI。
PROJECT_SPEC、ARCHITECTURE、mGBA/Oboe 及冻结工具链未修改。

## C. Home

首页提供 Continue Last Game、最近最多五个已实际运行的游戏和添加入口。
Continue 复用 Session 的 battery → 最新有效 autosave 恢复，不建立第二套存档恢复。
已验证的私有快照优先；撤销源 URI 不应阻止现存快照运行。
源的已知修改标记改变时重新校验；不同内容建立新身份并保留旧存档。

## D. Library

仅索引已导入内容，不扫描设备。显示名称、缩略图、上次游玩和现实游戏时长。
本地不区分大小写 contains 搜索；默认最近游玩，另有名称、添加时间、游戏时长排序。
Room 行以 GameId 唯一。移出先移除私有 ROM 快照，再以 inLibrary=false 保留历史时长；
不删除 battery、Manual/Quick/Autosave、截图或旧存档历史。重新导入可恢复条目。

## E. ROM Import

ACTION_OPEN_DOCUMENT，content URI，IO 线程读写。基本 GBA 校验沿用至少 192 字节、
最大 32 MiB、header[0xB2]==0x96；不要求商业 logo，不放宽原 Native 校验。
私有随机 temp → 有界验证 / SHA-256 → fsync → ATOMIC_MOVE → Room 事务索引。
GameId 始终为解压后 ROM bytes 的 lowercase SHA-256，名称/URI/封面不参与身份。
明确重新绑定必须相同 SHA-256，错误文件不会替换原记录或存档。
移除或缺少快照后可从有效 source 重建；两个来源均不可用时保留条目并标记 unavailable。

## F. ZIP Security

标准 java.util.zip 配合 central/local header 和 data descriptor 校验；只写固定私有 temp，
不使用 entry 名作为输出路径，不调用 extractAll。仅一个 .gba，可附 README.txt / LICENSE。

| 常量 | 上限 |
|---|---:|
| 压缩输入 | 40 MiB |
| 解压总量 | 40 MiB |
| 单 ROM | 32 MiB |
| entry 数 | 64 |
| 单项及总体压缩比 | 200:1 |
| UTF-8 路径长度 | 240 bytes |
| 路径深度 | 8 |
| 读取预算 | 10 秒 |

拒绝绝对/drive/backslash/traversal 路径、Unicode 规范化或大小写重复名、嵌套 ZIP、
加密、ZIP64、多磁盘、未知文件类型、CRC/大小不符、截断或不一致目录及重叠数据。
每项实际读取仍受声明大小与硬上限限制。失败/取消删除 temp，不产生部分 GameEntity。
Android 慢 provider 的超时/取消用例及最终完整套件均已实机通过。

## G. Game Details

详情提供继续/开始、时长和 Last Played、重命名、游戏设置、重新选择原 ROM、移出确认。
“存档管理 · 4 个存档位”进入现有播放器暂停菜单，继续复用原存档操作和缩略图。
默认界面不显示 SHA、URI、GPIO 或 Core 调试字段。

## H. Play Time

Session 使用注入的单调毫秒时钟，首个有效模拟帧后才开始计时并更新 Last Played。
RUNNING/前台累计；暂停菜单、设置、后台、加载不累计；FF 和 Rewind 只算现实时间。
在状态转换累积，在 45 秒安全点、后台和结束落盘；数据库写入失败保留待重试增量。
没有每帧写数据库。纯计时及实际 Session fake-clock 生命周期测试已通过。
本轮 Android 重开持久化、暂停设置不计时及 Continue 实机断言已通过。

## I. Artwork Strategy

依次选择最近 Manual State thumbnail → 同 GameId screenshot → autosave thumbnail →
原创 Compose Canvas 卡带占位图。旧无身份截图不猜测归属，不联网获取图片。
新截图放在 screenshots/<game-id>/；旧截图保留。缩略图时间戳与 State metadata 匹配。
IO decode，文件大小/像素尺寸校验，下采样，2 MiB LruCache；列表不读 ROM 或重新 hash。

## J. Settings Consolidation

首页与播放器共用显示、控制、音频、游戏运行、外设、存档、关于七类设置。
显示/背景与外设复用原单例 DataStore；布局读写原 portrait/landscape AtomicFile。
提供布局编辑/默认恢复和真实 HID 映射说明。无独立音频功能时只说明现有音频策略。
Gameplay / Saves 仅说明底层已存在的功能，不添加无效开关。
实体 Rumble 显示“真实震动当前未启用”，移除误导性的可切换控件。
About 包含版本、mGBA/Oboe 归属及本地开源许可正文；无在线 WebView。

## K. Room / Data Migration

明确 1→2→3 migration；旧 schema 1/2 均保留，schema 3 已导出。
新增 addedAt、playTimeMs、localArtworkPath、inLibrary、unavailable，旧行安全默认。
迁移不改 GameId、ROM hash、Room State/LastSession identity 或 Save / State / Settings 格式。
StateMetadata.appVersion 标记为 0.6.0，schemaVersion 1 / raw State v7 / core identity 不变。
原 v1 sav 的 SHA-256 继续由审计检查。禁止 destructive migration。
现有 1→2→3 与新增 2→3 Android migration tests 已通过；2→3 额外断言旧 State
的 gameId/key/coreVersion/slot/sequence 原样保留。

## L. Error UX

分别提示读取失败、文件过大、非法 GBA、ZIP 无 ROM、多个 ROM、不安全/损坏 ZIP、
文件失效与重新绑定 hash 不匹配。进度只有“正在添加游戏…”或“正在打开游戏…”，无假百分比。
取消入口只在导入时显示。失败保留旧存档；不可用条目可从详情重新绑定或移出。

## M. Permissions

Debug / unsigned Release merged permissions 均为 []。没有 INTERNET、Storage、Bluetooth、
位置、VIBRATE 或其他新增权限。测试 Provider / 慢速管道只位于 androidTest。

## N. Dependencies

无新增外部依赖，继续固定版本和验证 metadata；ZIP 使用 JDK / Android 标准库。
没有 Ads / Analytics / 网络 SDK、未知 native binary、外部 Shader 或 ROM 下载。
正式规格和 upstream 内容由本轮审计复核；历史审计结果保持原样。

## O. Automated Tests

已完成的普通本地构建和结果归档于 evidence/phase6/：

| 类别 | 实际结果 |
|---|---|
| JVM | 62 次执行，0 failures / errors；17 XML suites |
| Host native | PCM 1 + mGBA 6，共 7 项通过 |
| Debug / unsigned Release / 两测试 APK | 构建成功 |
| lint | 0 errors，20 warnings；未禁用检查 |
| 安全审计 | 83 项 PASS，见 PHASE_6_AUDIT_RESULTS.md |
| Android Activity | 最终完整 28/28 PASS，214.282 秒，phase6-activity-window-focus-final.log |
| Android JNI | 本轮 15/15 PASS，42.818 秒，phase6-jni-final.log |

初次编译存在 Compose Material3 DisplayMode 名称遮蔽，添加明确导入后构建通过；
早期失败日志保留。现有 UI 测试按新导入与统一设置导航调整，保持实际颜色/存档断言。
没有声称新增 UBSan、ASan、远程 CI 或长测通过。
新增导航测试最初未滚动到屏幕外条目，改为实际滚动后通过。计时测试发现
清理过的显示名没有 `.gba` 后缀，核心因此拒绝加载；已修正为传入校验后私有快照文件名，
并扩展测试为 ZIP 导入 → 重命名 → 撤销 source URI → 私有快照运行 / Continue。
撤权场景额外使用测试 Provider 强制抛出 SecurityException 的 URI，避免 exported 测试
Provider 在撤销 grant 后仍可读而造成假通过；该行为不进入生产 Provider/权限配置。
第二轮全量 23/28：导入后打开入口提前出现导致点击被 loading guard 忽略，已改为库刷新
完成后发布结果；显示设置的横屏白色选项需实际滚动，保留原 Surface 颜色断言。
第三轮发现搜索键盘占用设置页空间，导航时明确清焦点并关闭键盘，测试按真实滚动路径操作。
第三轮全量 27/28；其后的组合轮 25/28，有 2 项 No compose hierarchies 和 1 项测试
Provider shell 查询失败。新增 8 项单独复验全部通过。测试查询现改用 ContentResolver API
而非 UiAutomation shell 子进程；不据此宣称已完全确认间歇失败根因。
随后组合轮 27/28，再现 Phase 5 历史 SAF 截图像素 #990000（Original，红色断言 >200）。
保留截图 phase6-red-failure.png 与失败日志。合成 SAF 后显式将目标窗口带回前台并等待焦点，
保持原颜色断言，最后完整 28/28 通过。此为测试前置条件修正，不宣称消除了历史故障的全部根因。
首次全量运行遇手机重新锁屏，主动停止并保留原日志；日志末尾的 Process crashed
来自本轮 am force-stop 终止，不作为通过记录或自行归因于生产崩溃。

## P. Security Fixtures

RomImportTest 覆盖单 ROM、README、多/无 ROM、绝对/drive/traversal、嵌套 ZIP、
声明尺寸/压缩比、entry 数、长名、UTF-8、重复/规范化名称、损坏目录、CRC、截断、加密、
取消、hash mismatch 和超大读取。部分单个 JUnit 用例内部包含多种输入。
Android 使用原创 Apache-2.0 固定哈希资产，manifest 在 test-rom/manifests/library-tests.json。
独立 CRUD ROM 身份隔离合成 battery 测试，避免污染播放器和用户存档。
慢速/超时 provider 是独立测试管道，不进入生产 APK。无商业 ROM / BIOS。

## Q. Regression Phase 2–5

本轮 JVM/Host 已回归 persistence/session/input/renderer 原始帧和外设 bus 基线。
原有 20 项设备 Activity 回归与 15 项 JNI 已通过，包括 battery、4 Slots/Quick/Autosave/Resume、
迁移、实际 Surface/旋转、输入、FF/Rewind、图片与传感器注册生命周期。
新增库用例与这些基线的最终完整组合轮 28/28 通过。Phase 5 人工 PASS 作为历史证据保留。
不以 Phase 2–5 旧报告 READY 代替本轮 Android 回归。

## R. Performance

500 条合成 metadata 的实际 LazyColumn 滚动/搜索用例已通过；
第二轮 1053 ms，最终轮 993 ms，见 evidence/phase6/final-library-ui-metrics.txt。该值包含测试驱动开销，
不是帧率、启动耗时或所有设备上的性能保证。
不会每次重组读取 ROM/hash/同步 decode；此为实现方式，不是实测性能结论。
没有多设备、内存/温升或 30–60 分钟电量长测结果。
本轮 Home 后的 1200 ms 检查：played=65476、frames=65 均保持不变，返回后 played=65902；
见 final-home-audio-metrics.txt。最终四模式 1/2/4/8→1 的计时与显示数据在
final-renderer-metrics.txt；这些短时结果不等于长期稳定性或全部 ROM 的速度保证。

## S. Manual Acceptance

**PASS — 用户于 2026-10-07 明确反馈：“人工确认通过”。**
该确认关闭此前请求的 .gba/.zip 导入与重复提示、Continue/Recent、搜索排序、详情、
时长、重命名、移出/重新绑定、统一设置、返回导航及重启后旧存档与偏好保持的人工验收项。
详见 PHASE_6_MANUAL_ACCEPTANCE.md。既有非空手机的空状态仍依据合成 UI 自动测试，
不声称清空用户数据或新增首次安装实测。人工反馈不作为新增自动测试日志。
两个原创同内容测试文件已复制到手机 Download，设备 SHA-256 记录在 manual-rom-device-sha256.txt。
测试进程已停止、应用回到首页，USB 常亮临时设置由原 0→2 恢复为 0；应用数据未清空。

## T. Known Issues

最终完整自动验证与用户人工验收均已通过。首轮全量锁屏中止及所有后续失败日志均保留。
No compose / 测试 Provider shell 查询与历史 SAF 截图变暗的间歇失败已在最终轮未复现，
但原始原因未全部证实，继续保留测试环境及窗口生命周期复发风险。
Phase 5 历史 SAF 窗口截图间歇失败和横屏黑屏原始根因未完全确认，继续保留复发风险。

## U. Risks

仅一台 OPPO PLG110 / Android 16 / arm64 的历史覆盖；旧设备/provider/文件系统存在差异。
恶意或故障 provider 若不响应 CancellationSignal/关闭，框架调用中止时机仍有平台限制，
不能以代码预算宣称所有系统调用绝对在 10 秒内返回。读写与解析均不在 UI 线程。
突然杀进程可能丢失最近成功安全点后的时长/进度；不会宣称任意断电零损失。
完整 ASan/fuzz、更多合法 ROM、多设备、远程 CI、长期稳定性及正式签名仍未完成。

## V. Unfinished

- 最终人工验收已完成，旧 Save / Shader / Touch / Sensor settings 保持的人工验收项已关闭。
- 多设备、长时间运行/温升/内存分析、完整 ASan/fuzz、更广泛合法 ROM 兼容性、远程 CI、正式签名及发布验收仍未完成；不因 Phase 6 READY 自动完成。
- Git 延续整个项目未跟踪状态，未暂存/提交；没有将现有项目强行整体提交。

普通版 APK 已安装；最终产物目录：`D:/GPT/artifacts/gba-emulator/phase6/delivery-final/`。
旧候选目录和哈希保留。最终 SHA-256（完整 JSON：evidence/phase6/apk-sha256-final.json）：

| APK | SHA-256 |
|---|---|
| Debug | `74b0a2dd5c2e9fb69ccd5ac21b0f8750e0c3ff6dbcb319a06def099d3f7bbf4f` |
| unsigned Release | `008e59e5fc13eea18faaf6799b37a6f8e41a02c3c551f890f2e73085eab0bb27` |
| Activity test | `81ec41af2264a0f490a1ea2abcef6723e214581fa7b67906c0996c370b11678e` |
| JNI test | `e1fe7c279a401bb281d7730f1eb9a763956cab620c033f5318ad08b0725563ac` |

Debug 证书 SHA-256 仍为 `3660dfb189306e49d973a7f91c9cbc1cb35cc93921f0292e8db28b3fa10b4094`。
Release 验签返回 1 / Missing META-INF/MANIFEST.MF，确认仍为 unsigned；不是发布许可。

## W. 是否允许进入 Phase 7

**READY**

Phase 6 的 Home / Continue / Recent / Library、GBA/ZIP 导入、身份去重与重新绑定、
详情管理、现实游戏时长、本地图片、统一设置、明确 Room 迁移及 Phase 2–5 回归已完成。
原有自动测试、安全审计、短时性能记录与用户最终人工验收均通过，满足进入 Phase 7 的技术条件。

历史窗口截图变暗、No compose / Provider 查询和横屏黑屏的原始根因未全部证实，
保留为非阻断复发风险，不宣称已彻底根除。本次关闭仅更新文档，没有重新构建 APK、
修改生产实现、历史测试数据、审计结论或已有产物 SHA-256。

**Phase 6 READY，允许规划与实施 Phase 7，但本轮未开始 Phase 7。**
**Phase 7 NOT STARTED；V1 Release NOT READY；Release 仍未正式签名。**
