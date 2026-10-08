# Phase 3 — Player Experience 报告

日期：2026-10-06；用户反馈修订：2026-10-07。项目目录：`D:/GPT/projects/gba-emulator`。

结论：**READY**。根据既有自动回归、人工反馈及用户 2026-10-07 的最终验收确认，满足进入 Phase 4 的技术条件。此前待确认的持续快进速度与声音恢复、物理 HID 实机验收由用户最终确认完成并通过；证据归属见 M 节。历史横屏黑屏在新版自动与人工复验中未再出现，因根因未明确，保留为非阻断复发风险。本结论不代表 V1 已达到正式发布标准。

按 PROJECT_SPEC → ARCHITECTURE → Phase 0/1 报告 → Phase 2 报告 → Phase 3 实施文档执行。最高规格、正式架构及历史验收报告保留；只实施 Phase 3，无 Phase 4+ 功能。

## A. 实际完成

实现真实模拟快进、内存倒带、State 缩略图、私有 framebuffer 截图、独立横竖屏输入布局与编辑、来源隔离多点触控、系统已连接 HID 输入、暂停菜单及保留 Session 的旋转流程。应用版本 0.3.0 / code 3，arm64-v8a。源码在上述项目目录；Debug/Release 位于 `app/build/outputs/apk/`。

交付复制到 `D:/GPT/artifacts/gba-emulator/phase3/`。单个/整体缩放修订后普通 Debug SHA-256：`271a335dc5f3ea52c5f6572dc796a434e3ee730754ac6d765f626e4681aae475`；unsigned Release：`3e87fb7229534c16f76f5b4f8daa6bbac5d4509404be270ad59b508f24d969dd`。手机已安装普通 Debug 更新。签名 SHA-256 保持 `3660dfb189306e49d973a7f91c9cbc1cb35cc93921f0292e8db28b3fa10b4094`。

## B. 架构变化

UI → Session → Core API → mGBA adapter/JNI 路径保留。Core API 增加速度、倒带、缓存收缩及内部指标；Native worker 独占 pacing、倒带 State 和帧发布。Session 使用 input 模块的来源路由，继续持有持久化 Mutex。ViewModel 持有生命周期命令队列与私有图片、布局存储。Renderer 只重建 Surface，未增加滤镜或 shader。

重大决策均先记录 ADR：009（归属、缓存、输入与图片隔离）、010（ROM 使用有界只读 VFile，修复检测过程扩容崩溃）、011（Activity 单一生命周期来源，ViewModel 串行处理）。mGBA/Oboe 上游源码未改；Room schema 1/2、存档格式与迁移保留。

## C. Fast Forward

支持 1/2/4/8×，按住与切换。基于 mGBA frameCycles/frequency 和单调时钟推进实际模拟帧，GLES 显示最新帧。快进静音并排空 PCM；恢复 1× 重开单一音频流。暂停、后台与退出取消加速。实测见 N；测试 ROM 达到 8× 不代表所有商业游戏及设备都能达到。

按用户明确选择修订：点击菜单 2×/4×/8× 立即开始持续快进，游戏菜单显示当前倍率，从菜单“关闭快进（恢复1×）”停止。暂停菜单内 Core 仍暂停，“继续”恢复所选持续快进；切后台取消。按住快进/倒带保留，进入按住模式清除持续快进标记，避免松开后显示错误倍率。没有修改 native pacing。

## D. Rewind

每 12 模拟帧捕获一次（约 201 ms），原始 State 加 RGBA framebuffer；最多 150 个快照，payload 硬上限 64 MiB。实测单个 State 397,312 B、帧 153,600 B，保留 121 个、66,660,352 B，约 24.31 秒历史。捕获期间另有一个有界临时 State/帧及容器开销，payload 上限不能等同进程总内存。

按住连续弹出历史，名义间隔 50 ms；实测恢复 25 个快照约 1,695 ms，恢复成本使其慢于名义间隔。松开保留当前位置继续；被弹出未来历史不保留。每次载入 State 前后克隆/恢复当前 battery，测试比较字节一致。无倒带文件。倒带时周期 checkpoint 延后；后台/退出保存当前 battery，跳过该次倒带 autosave。手动载入 State 清空倒带历史。低内存清空并将当前 Core 缓存限额降至 16 MiB，不删除正式存档。

## E. Input

独立 portrait/landscape JSON 布局，使用 AtomicFile，支持拖动、尺寸、透明度、默认恢复与保存。按键目标至少 48 dp，安全边距。用户反馈后放大竖屏默认按键（A/B 从 .17 到 .20、肩键从 .14 到 .17，D-pad .31 到 .32、Start/Select .14 到 .16），保留已有自定义布局，需“恢复默认”采用新默认。拖动模式折叠编辑工具栏，允许接触肩键。D-pad 相对半径死区与对角判定；按 pointer、touch/HID/device 来源合并，取消、丢 pointer、Surface 销毁、后台及退出释放输入。

系统 HID 默认 South→A、West→B、L1/R1、Start、Select/Back；D-pad 与摇杆阈值/死区。InputManager 监听断开/变化释放设备来源。软件键盘保留。模拟 KeyEvent/MotionEvent、触控与 HID 合并、断开重连测试通过；**不等同物理 USB/蓝牙验收**。

按用户后续要求将尺寸滑块与持久化校验上限从 .32 扩至 .60，最小值 .07 保留，编辑界面显示当前百分比。最大半径仍受可用触控区域边界限制，以保证按键在屏内；不保证小窗口能达到名义尺寸。原已保存的较小尺寸无需迁移，新增最大尺寸通过同一 AtomicFile 存取路径。

新增“单个／整体”模式。单个修改当前选中按键；整体以进入该模式时的尺寸为基准，统一比例调整所有按键，保留位置、透明度和原有相对大小。整体比例范围取所有按键的共同合法区间，任一到边界后不继续破坏比例；拖动位置不会被后续整体缩放覆盖。保存仍采用原布局 schema，无新格式或迁移。恢复默认同时重设整体缩放基准。

## F. Player UI

普通游戏画面只有屏幕、触控和一个菜单入口。暂停菜单含继续、Quick Save/Load、四槽日期缩略图、快进、倒带、截图、布局编辑、退出。按住操作临时显示操作条，松开恢复；无 Core 调试参数。保存/读取与恢复均等待 Session 操作，未绕过层级。

反馈修订：移除横屏 Surface 的 62% 宽度限制，填满可用容器，仍使用原 renderer 的 3:2 viewport，不裁切、不拉伸。额外边距从横 16/竖 8 dp 减至横 8/竖 4 dp，仍保留 safeDrawing；游戏中隐藏系统栏，可边缘滑动临时呼出，退出恢复。竖屏画面使用安全区可用宽度，受 3:2 与物理屏幕宽度限制。菜单增加按键位置、Xbox/PlayStation 常用标签映射说明，不改变既有 HID 映射。属于 Phase 3 UI 布局修订，无 Phase 4 renderer 功能。

## G. Orientation

旋转复用 ViewModel/Session/Core，重建 Surface、切换独立输入布局并释放按键。用户暂停与前后台状态分开。Activity 唯一提交 foreground；ViewModel 单消费者队列防止旧后台 checkpoint 晚于新前台命令。运行、暂停、后台返回与 recreate 已在 Activity 回归验证。编辑中的未保存草稿未跨旋转保留，旋转后返回暂停菜单。

## H. Screenshot / Thumbnail

直接复制 240×160 RGBA framebuffer 后编码 WebP，无菜单/按键覆盖和 EXIF。显式截图保存 `files/screenshots`；State 缩略图保存 `files/thumbnails/<gameId>`，时间戳必须匹配 State createdAt 才显示。State 原子提交先完成，图片错误独立，不影响存档成功。编码/实际界面解码在 IO；非逐帧编码。图片尺寸、像素与缩略图匹配有自动测试。应用私有图片不自动进入相册。

## I. Persistence Regression

固定 `test-save-v1.sav` 32,768 B，SHA-256 `91fe8bc63da1c542130e95137a4e28eeb1ec7df6239ca92fbe08e050b3979c9f` 保持不变。Quick、四槽、battery、后台 autosave、退出/继续、ROM 重命名、失效 URI 及 Room 1→2 回归通过。新增倒带 battery 隔离、checkpoint 延后、快进 checkpoint 恢复速度及缩略图失败隔离测试。

仪器测试 ROM 添加专用标记形成独立 GameId，不覆盖用户原 ROM 存档；测试会更新 LastSession，人工测试应重新选择 Download 的原始 ROM。未清除或卸载主应用数据。Phase 2 的人工确认是历史证据，不替代本轮验收。

## J. Permissions

Debug/Release merged manifest 权限均为空：没有 INTERNET、蓝牙、旧 Storage 或其他新增权限。系统完成蓝牙配对，应用仅处理 InputDevice。未添加 Analytics、广告或联网 SDK。

## K. Dependencies

无新增外部库。固定 mGBA 0.10.5 commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`、Oboe 1.9.3 commit `b15f5e39c01a7ada306d959e5129620b145fb8b4`，官方源内容审计通过。本机源编译 native bridge；另一 APK native 库仅已有官方 AndroidX graphics-path。工具链、依赖锁和许可证保留；依赖清单见 PHASE_3_DEPENDENCIES.txt。

## L. 自动测试

| 检查 | 实际结果 |
|---|---|
| Debug/Release、test、lint | BUILD SUCCESSFUL；无 Lint error，保留非阻断 warning |
| JVM | 最新 39 次执行通过（新增整体缩放比例/位置/边界测试；adapter 的 7 项在两个 variant 各执行一次） |
| Host native | PCM 1 项、mGBA 3 项通过 |
| 普通设备 JNI | 最终 11 项通过，36.677 s |
| 普通设备 Activity | 12 项通过，27.665 s |
| 反馈修订 Activity | 最终 12 项通过，32.703 s：包含最大尺寸存取、用户自定义位置触控、直接点选 2/4/8×、暂停继续保持持续快进、关闭恢复 1×，及既有持久化/旋转回归 |
| 单个/整体与画面回归 | 最新 12 项通过，44.925 s：含整体布局存取及正/反横屏、连续七次方向切换、各方向后台返回后的实际 Surface 画面像素检查 |
| UBSan Activity | 12 项通过，28.19 s |
| UBSan JNI | 11 项通过，40.203 s；实际画面按下/松开均有界验证 |
| 源码/权限/依赖审计 | 44 项通过，PHASE_3_AUDIT_RESULTS.md |

设备为实际 OPPO arm64 Android 16 / API 36。UBSan 对自有 bridge 及源码依赖使用 undefined trap；构建标志与执行日志归档。ASan 受现有 MSVC 环境 runtime 缺失限制，本轮未获得 ASan PASS。CI 配置已更新但未声称远程 CI 运行。

反馈修订未改变 native/audio/storage/data 实现；原普通 JNI/UBSan 指该 native 实现的 Phase 3 检查，UI 修订最终以新增 feedback Activity 日志为准。最终 Debug/Release、JVM、Lint 再次通过，44 项审计仍通过。

最终日志、实际性能与失败历史在 `docs/reports/evidence/phase3/`。Release ELF 已验证 GNU_RELRO、BIND_NOW、不可执行 GNU_STACK、stack_chk；无 ForTest 测试导出。报告不是完整恶意 ROM 安全审计或发布签核。

## M. 人工实机验收

用户早期反馈当时仅有蓝牙手柄，已完成配对及 USB 调试授权。2026-10-07 用户最终确认此前待确认的持续快进速度与声音恢复、物理 HID 实机验收均已完成并通过。以下人工结果来自用户确认；物理 HID 的具体接入方式、型号及独立执行日志未随本次确认提供，不将其写为 Codex/ADB 自动执行的测试结果。

| 项目 | 用户实测反馈 |
|---|---|
| 蓝牙按键、断开重连 | 没问题；不清楚按键映射，已补菜单说明 |
| 组合触控、布局保存 | 没问题 |
| 倒带 | 没问题 |
| 存档、缩略图、截图 | 没问题 |
| 旋转、后台、退出继续 | 没问题 |
| 横竖屏与单个/整体缩放 | 用户曾报告横屏整个屏幕黑屏；新版重新选择 GbaLite_Phase2_Persistence_20261006.gba，手动横屏/反向横屏/回竖屏，并检查两种缩放后反馈“没问题”。本次验收通过，黑屏未再出现，原因未定位 |
| 快进 | 用户要求选择倍数立即持续快进再点关闭，已实施；确认快进静音、关闭后恢复连续测试音，并最终确认持续快进速度与声音恢复通过；2/4/8× 吞吐及恢复 1× 另有自动测试证据 |
| 物理 HID 最终验收 | 此前记录为待验收；用户 2026-10-07 最终确认已完成并通过。证据为用户人工确认，接入方式、型号及独立测试日志未提供 |

## N. 性能

普通 JNI 实测，合法自制 ROM，约两秒采样；FPS 指模拟吞吐，不是显示刷新率。

| 请求 | 模拟 FPS | 实际倍率 | cache payload B | 累计 audio underrun |
|---|---:|---:|---:|---:|
| 1× | 59.320 | 0.993 | 6,060,032 | 17 |
| 2× | 119.463 | 2.000 | 17,078,272 | 17 |
| 1× | 59.982 | 1.004 | 22,587,392 | 19 |
| 4× | 239.080 | 4.003 | 44,623,872 | 19 |
| 1× | 59.959 | 1.004 | 50,132,992 | 21 |
| 8× | 477.673 | 7.998 | 66,660,352 | 21 |
| 1× | 59.922 | 1.003 | 66,660,352 | 23 |

正常启动/恢复音频存在 underrun，未声称零欠载。快进静音期间计数不增长。累计 lateFrames 从 16 增至 908，含 deadline 迟到统计；并非卡死证据。尚无 30–60 分钟温升、峰值 RSS、商业游戏或其他设备吞吐测量。

## O. 已知问题

发现并修复 ROM 可扩容 VFile 导致 CRC32 崩溃（ADR-010）和重复生命周期来源导致前台仍暂停（ADR-011）；失败日志保留。测试补齐异步 UI 显示等待，Compose 仪器测试明确使用主线程 effectContext；UBSan 下旧 100 ms 按键采样改为有界等待实际变化及松开恢复。修复后结果以最终日志为准，未抹除失败历史。

反馈修订测试读取当前自定义 A 键位置，替代写死默认位置，并等待实际 GLES 画面变化。扩大上限后的回归曾遇到 9 项 No compose hierarchies；ADB 状态证实 mWakefulness=Dozing、mDreamingLockscreen=true。用户解锁后复验，进一步捕获 Dialog 在 IO 线程创建异常；将播放器 coroutine scope、slots 收集、布局/图片状态发布明确限制 Main.immediate，文件和编码工作继续 IO。最终完整回归通过；两次失败记录均归档，不计为 PASS。该变更未绕过 Session 或改变持久化线程归属。

新版方向切换和单个/整体缩放已获用户“没问题”确认；快进静音与关闭后的连续测试音恢复也获用户确认。布局未保存草稿旋转后丢弃；图片使用 app-private 路径，没有本轮范围外分享/相册功能。

旧测试仅验证 Session 和 native 帧计数，不能证明实际显示；已补 UiAutomation 实际 Surface 中心像素检查，并覆盖反向横屏、连续旋转与后台返回。在本机原版和新增缩放版均未复现。用户随后手动复验反馈“没问题”，本轮黑屏未再出现；关闭当前复验待办，但不否定早先报告或宣称根因已修复。首次补测因播放器未在前台超时，待用户准备后复验通过，记录保留。

## P. 风险

单机自制 ROM 测试不能覆盖所有 ROM、低端设备、不同 Android/OEM 或手柄型号。内存 payload 与进程 RSS 不同；状态恢复成本影响倒带速度。存在非零音频欠载，待长时间实际听感验证。ASan、完整 fuzz、长期温升未验证。Release unsigned 产物仅供构建审计，不是发布版；主应用使用现有 Debug 签名保留数据。

此前整屏黑屏现象未找到明确根因，虽自动和用户复验均正常，仍保留复发风险；若再发生需记录当时 foreground/锁屏状态、声音及返回行为。

## Q. 未完成项

本阶段开发与回归、最终人工验收已完成。建议后续补长时间运行、多 ROM、更多设备与物理 HID 型号/接入方式的详细记录；ASan、完整 fuzz 及正式签名发布工作仍未完成，不作为本次 Phase 4 技术进入条件的阻断项。若黑屏复发继续排查，不将复验正常等同根因修复。本报告未包含 Phase 4 实现。

## R. 是否允许进入 Phase 4

**READY**

理由：Phase 3 规定的 Fast Forward、Rewind、Save State 缩略图、
Screenshot、横竖屏布局、触控编辑、多点触控、HID 手柄、生命周期与
Persistence 回归均已通过自动测试与人工实机验收。

此前待确认的持续快进速度与声音恢复、物理 HID 实机验收均已完成并通过。

此前出现过的横屏黑屏在新版中未复现，自动实际 Surface 回归与人工正/反横屏、
回竖屏复验均通过；因历史根因未明确，仍保留为非阻断复发风险。

结论仅表示满足进入 Phase 4 的技术条件，不代表 V1 已达到正式发布标准。
