# Phase 4 — Renderer / Visual Experience

日期：2026-10-07。项目：`D:/GPT/projects/gba-emulator`。

**Phase 4 — READY**。最终人工验收日期：**2026-10-07**。

按 PROJECT_SPEC → ARCHITECTURE → Phase 0/1、2、3 报告 → GBA_TEST_ROM_CORPUS → Phase 4 实施文档执行。实现、既有自动回归、安全审计及用户最终人工验收均已通过，满足进入 Phase 5 的技术条件。本次仅更新验收文档与阶段状态，未启动 Phase 5；V1 Release 仍为 NOT READY。

## A. 实际完成

四种内置显示模式、Fit / Integer、全局 DataStore 显示偏好、显示设置入口、shader 故障回退、实际画面测试、自制色彩与像素图案 ROM，以及 Renderer 生命周期修订。版本 0.4.0 / versionCode 4。没有实现 Phase 5+。

用户反馈追加“菜单 → 设置 → 背景颜色”，可选黑色 / 白色并即时保存。底色覆盖游戏周围、横屏比例留边和竖屏按键区域；游戏像素、截图与 State 缩略图不变。显示偏好编码 v2 新增背景字段，兼容读取 v1（默认黑色），没有新增依赖、权限或游戏存档迁移。实施前已补 ADR-012 amendment。

## B. 架构变化

core-api 增加不可变 DisplaySettings / DisplayMode / ScaleMode；data 持有单例 DataStore；ViewModel 发布状态并按序保存；feature-player 仅传递设置与回退通知；renderer 消费 FrameSource 原始帧。没有 UI → mGBA/JNI 调用，没有改变 Core、Session、Room 或 Save State 格式。

[ADR-012](../adr/ADR-012-renderer-visual-experience.md) 先记录显示模式、色彩和偏好决策。[ADR-013](../adr/ADR-013-nonblocking-egl-lifecycle.md) 在实机捕获阻塞后，先记录再替换 GLSurfaceView 生命周期：保留 OriginalSurface 接口，使用 TextureView、独立 EGL14/GLES2 HandlerThread 与该线程的 Choreographer。UI 回调仅排队，不等待 swap / GL 线程。

## C. Original / Sharp / GBA Color / LCD

Original：内嵌最小 GLES2 程序、NEAREST，亦为回退基线。

Sharp：LINEAR 结合 texel 边缘重建，整数输出像素中心保持源像素；非整数边缘使用正权重插值，没有锐化振铃。

GBA Color：固定 gamma 与矩阵校色，旨在近似未背光 GBA LCD。

LCD：轻微水平邻居混合和低强度像素网格，单 pass，无历史纹理、重影或 FBO 链。缺失偏好默认 Sharp / Fit；未知或损坏偏好回退 Original / Fit。

## D. GBA Color 公式、来源、简化

`linear = pow(rgb, 3.2) * .94`；`out = pow(clamp(M * linear, 0, 1), 1 / 2.2)`。

```text
M = [ .820  .240 -.060
      .125  .665  .210
      .195  .075  .730 ]
```

来源为 Pokefan531 / hunterk 的 public-domain [gba-color.glsl](https://github.com/libretro/glsl-shaders/blob/77697c58448380156a87b251e6461d5ab00071f3/handheld/shaders/color/gba-color.glsl)，固定 revision。对比度和饱和度取 identity、offset 为零；先 clamp 再分数幂防 NaN。黑为零，白约 .972。不是具体手机面板或每台 GBA 的测量校准；没有复制商业模拟器资源。完整说明见 [RENDERER.md](../RENDERER.md)。

## E. LCD 实现与性能

中心 96% 加左右邻居均值 4%；网格最多衰减 6%，小于 2× 时淡出。一次绘制、三次纹理采样、一张 240×160 纹理。没有每帧 Bitmap、历史帧、ghosting、bloom 或多 pass。实际细字、棋盘格和高对比图案由自制 ROM 验证；性能结果见 Q。

## F. Fit / Integer / Viewport

Fit 使用屏幕内最大的整数像素 3:2 矩形并居中，边缘至多额外空两像素。Integer 使用最大的完整 240×160 倍数，不足 1× 回退 Fit。原有触控布局坐标和存储格式保留，不通过拉伸或裁切占满屏幕。

人工反馈修订：用户报告横屏四周多余白边，明确选择“保留完整画面，尽量放大”。去除横屏 root 的 safeDrawing / 8dp、4dp 额外边距，背景统一黑色，Surface 延伸到完整窗口；安全区仅保留给横屏触控。竖屏显示状态栏并在内容应用 safeDrawing；横屏隐藏系统栏并允许画面延伸至 cutout 区域。Fit 可最大化完整画面，两侧比例黑边仍可能存在；Integer 保留其整数缩放语义，没有加入 Fill 或裁切。

用户随后确认横竖屏修订“没问题”，并要求背景可选黑白；因此上述默认黑色现可在设置中切换。可选白色留边是用户选择的底色，不是恢复之前的多余布局边距。

## G. Orientation / Surface

测试四模式 × 两缩放 × 正横屏 / 反横屏 / 竖屏共 24 个组合；验证真实 TextureView 内容、viewport、同一 Session 与 ViewModel、资源数量、后台销毁和前台新 context、Activity recreate。Surface 保持亮屏，不申请 WAKE_LOCK。销毁的 SurfaceTexture 在 EGL 清理后释放；共享 EGL display 引用计数防止邻接 Surface 相互终止。白边修订额外断言横屏 Surface 与窗口同尺寸且屏幕位置为 (0,0)，竖屏状态栏可见且 Surface 位于其下方；全部通过。

## H. Phase 3 历史黑屏专项回归

本轮旧 GLSurfaceView 在方向 / 后台组合测试中出现可复现阻塞：main 等待 GLThread.onWindowResize，GL 线程停在 eglSwapBuffers，证据保存为 phase4-watchdog.txt。依 ADR-013 改为非阻塞 EGL 生命周期后，实际画面方向 / recreate 回归已通过。

这解释本轮捕获的 ANR，不等同于证明 Phase 3 用户历史整屏黑屏的根因。用户于 2026-10-07 最终确认新版竖屏、正反横屏及后台恢复正常；当前版本未复现历史黑屏，相关 Surface 回归通过，仍保留非阻断复发风险，不宣称已完全确认原始故障根因。

## I. Shader Failure / Fallback

通过真实非法 GLSL、varying 不匹配、非法 GL 参数分别触发 compile / link / setup 故障。失败 program 在 GL 线程释放，切 Original / NEAREST，显示通知并持久化 Original；模拟 Core 不重建。资源上限一张纹理、四个 lazy program、两个复用 CPU buffer。完全失效的 GLES2/EGL 驱动不能靠 Original shader 修复。

## J. Screenshot / Thumbnail

仍从原始 240×160 framebuffer 生成，不包含 shader、触控 UI 或黑边。细图案测试比较截图源像素；Slot 缩略图尺寸与回读验证继续通过。不新增分享或相册功能。

## K. FF / Rewind Regression

四模式依次测试 1× → 2× → 4× → 8× → 1×，记录实际模拟速率并检查真实显示；保持倒带五秒验证快照消耗、释放继续、模式保持以及 Battery 数据不被倒带覆盖。快进期间沿用静音策略，回到 1× 恢复音频。普通 / UBSan 回归结果及版本范围见 O 节；用户于 2026-10-07 最终确认 2× / 4× / 8× 快进、恢复 1× 后声音及倒带正常。

## L. Persistence / Input Regression

继续运行 Phase 2/3 的 Battery、后台落盘、Quick / Slot、恢复、错误与迁移、触控布局、组合输入、HID 事件和生命周期测试。保留既有测试 ROM 后缀以隔离测试 GameId，不清空用户数据。用户于 2026-10-07 最终确认本轮触控、蓝牙手柄、存档与生命周期恢复均正常；人工结果与既有自动结果分别记录，不将人工蓝牙验收写为新增自动测试。

## M. Permissions

Debug / Release 合并 manifest 权限列表均为 `[]`。没有 INTERNET、危险权限、Analytics、广告或联网 SDK。为诊断暂加的 debug provider queries 已移除；测试 ROM Provider 仅在 androidTest APK。

## N. Dependencies

冻结工具链、mGBA 0.10.5、Oboe 1.9.3、现有 AndroidX / Kotlin 均未升级。仅新增最高规格要求的官方 AndroidX DataStore core 1.1.7，版本、依赖锁和 SHA256 verification metadata 均固定。mGBA / Oboe 从已固定源码构建，没有未知预编译 native binary。APK native 内容仅 arm64-v8a：项目源码构建的 libgba_bridge.so，以及固定官方 AndroidX AAR 中的 libandroidx.graphics.path.so、libdatastore_shared_counter.so。完整坐标见 PHASE_4_DEPENDENCIES.txt，APK 内容与 SHA256 在 evidence/phase4。NOTICE 更新 DataStore 与色彩公式归属。

## O. 自动测试

- JVM：45 次测试执行，含 Debug / Release 同一测试的分别执行，零失败。
- Host native：PCM 测试 1 项、mGBA 5 项，共 6 项通过；两图案在第 300 帧逐像素对比原始 RGBA。
- 普通 JNI：11 项通过；UBSan JNI：11 项通过。
- 独立 Home 音频检查：1 项通过，实际 Home 后音频播放与模拟帧计数停止，返回恢复。
- 白边反馈修订前，完整普通 Activity：17 项全部通过，157.428 秒；完整 UBSan Activity：17 项全部通过，163.031 秒。UBSan 使用 `-fsanitize=undefined -fsanitize-trap=undefined`，本轮没有 trap 或进程崩溃。白边修订只改变 Compose / Window 边距和系统栏策略，native 与 shader 未改变；修订后的普通 17 项回归全部通过，157.170 秒。
- 最新背景设置版普通 Activity：18 项全部通过，162.695 秒，日志 phase4-background-verified-activity.log。新增测试经菜单真实操作，验证黑白留边颜色、偏好落盘、Activity 重建后保持、游戏颜色正确及 Session 不重建。45 次 JVM 执行亦通过，显示设置覆盖 16 组模式 / 缩放 / 背景 round trip 和 v1 兼容。UBSan 记录仍为前述修订前 17 项，本次 native 源码未变，未将其写成新版 18 项 UBSan 通过。
- 审计：修订后 55 项通过；普通 Debug / unsigned Release、单元测试、测试 APK、lint 构建通过，lint 0 errors / 19 warnings（包括冻结工具链的版本提示）。早先报告曾将 53/54 项通过误报为全部通过，复核发现唯一失败为 native 白名单漏列官方 DataStore 库。已核对锁定 AAR 和 APK 对应库字节完全一致，并增加 Debug / Release 精确 SHA256 审计；原失败日志保留，未改写为通过。

此前 GLSurfaceView 阻塞、旧 buffer 捕获时序、首次测试 Provider 不可访问的失败记录保留。因用户反馈后台滴声而主动 force-stop 的 UBSan Activity 运行完成前 15 项后中断，不计整轮通过。Provider 首次访问问题通过测试 shell 预先启动独立测试 Provider 处理，没有降低生产 SAF 校验。

## P. Actual Surface Tests

实际 TextureView bitmap 与 UiAutomation 屏幕像素验证颜色、单调灰阶、黑白、RGB patch、细图案、24 个方向 / 缩放组合、后台前台 / context recreate、shader 故障恢复、快进与倒带。不是用 Core 出帧或 View 存在替代画面验收；跨 GPU 只比较局部颜色容差，未使用整屏 GPU hash。

两原创 Apache-2.0 测试 ROM、预期 RGBA 和 hashes 在 test-rom/manifests/video-tests.json。没有商业、来源不清或未授权 ROM。

## Q. 性能

白边反馈修订前，普通版 Choreographer 测试数据见 [normal-renderer-metrics.txt](evidence/phase4/normal-renderer-metrics.txt)：

| 模式 | 2× 实际 | 4× 实际 | 8× 实际 | 回到 1× 实际 | 绘制 FPS 范围 | CPU 提交均值范围 ms |
|---|---:|---:|---:|---:|---:|---:|
| Original | 2.000 | 3.959 | 8.001 | .996 | 59.51–60.60 | 1.00–1.39 |
| Sharp | 1.992 | 4.004 | 8.013 | 1.003 | 60.27–60.82 | 1.06–1.51 |
| GBA Color | 2.005 | 4.021 | 7.999 | .952 | 59.23–60.61 | 1.14–2.00 |
| LCD | 1.996 | 4.007 | 7.997 | .990 | 59.67–60.45 | 1.02–1.49 |

每速度窗口 1.5 秒，8× 为 4 秒；短窗比率包含切速时序，GBA Color 返回 1× 的 .952 不能描述为精确 1×。各模式仍维持约 60 次/秒绘制，未显示 shader 将 2×/4×/8× 显著拖垮。Window GPU_DURATION 平均 2.698 ms / 2748 samples；稀疏 GL 完成等待 1.33–4.77 ms，上界而非精确 shader 时长。普通版 8× 的显示跳过下界为 1670–1677 帧，符合生产帧远多于约 60 Hz 显示。非零欠载出现在部分 1× 短窗（最多 5）；本轮人工听感已通过，短窗计数及长期稳定性仍需后续复核。

UBSan 记录见 [ubsan-renderer-metrics.txt](evidence/phase4/ubsan-renderer-metrics.txt)：8× best effort 实际 5.36–6.98×，绘制仍约 60 Hz；GBA Color 返回 1× 短窗约 .930、欠载增量 18。sanitizer 开销下的结果没有冒充普通版性能，也不把非零欠载隐藏为完全无风险。

白边修订后最新记录见 [fullscreen-renderer-metrics.txt](evidence/phase4/fullscreen-renderer-metrics.txt)：四模式 8× 实际 7.976–7.996×，绘制约 60.45–60.58 FPS，Window GPU_DURATION 平均 2.630 ms / 2624 samples；完整窗口布局没有显著拖慢本机测试 ROM。

背景设置版最新普通记录见 [background-renderer-metrics.txt](evidence/phase4/background-renderer-metrics.txt)：四模式 8× 实际 7.994–8.023×，绘制 60.26–60.52 FPS；Window GPU_DURATION 平均 2.095 ms / 2898 samples。后台静音计数记录归档在 background-home-audio-metrics.txt，完整 18 项回归通过。

CPU 计时含 driver stall；稀疏 glFinish 完成等待为 GPU / driver backlog 上界，不能冒充 shader GPU execution 时间。另记录 API 31+ Window GPU_DURATION，范围是 Window compositor，不能当作独立 GLES Surface 时间。产生帧数减绘制数仅为显示跳过的下界。存在开始播放时短暂非零音频欠载；快进故意跳过显示并静音，不能把所有跳帧当作慢速缺陷。

单台 OPPO PLG110 / Android 16 / arm64，短时自制 ROM，尚无低端机、多 ROM 或长期温升结论。

## R. 人工实机验收

### 最终人工验收：用户确认

**PASS — 2026-10-07**。依据用户本次明确反馈，Phase 4 全部最终人工验收已完成并通过，此前遗留的人工验收阻断项全部关闭。

| 验收项目 | 用户最终确认 |
|---|---|
| Original / Sharp / GBA Color / LCD | 四种显示模式正常 |
| Fit / Integer | 缩放正常 |
| 黑色 / 白色背景 | 切换及设置持久化正常 |
| 竖屏 / 正横屏 / 反横屏 | 无异常，当前版本未复现历史黑屏 |
| 2× / 4× / 8× 快进 | 快进正常，恢复 1× 后声音正常 |
| Rewind / Save State / 缩略图 / 截图 | 均正常 |
| 触控 / 蓝牙手柄 | 均正常 |
| 后台恢复 / 生命周期恢复 | 均正常 |

证据归属：上表为**用户最终人工反馈确认**，不是 Codex 新执行的自动测试。自动测试结果由 O、P 节及既有 `evidence/phase4/` 日志支持，安全审计由 `PHASE_4_AUDIT_RESULTS.md` 支持。本次仅更新 Markdown，没有重新运行测试、生成设备记录或修改历史日志。

### 既有自动检查与历史修订记录

用户已确认测试暂停后滴声停止。独立自动 Home 检查通过：后台开始和 1200 ms 后播放计数均为 62916、帧计数均为 66；返回前台计数恢复增长。见 [home-audio-metrics.txt](evidence/phase4/home-audio-metrics.txt)。这不能替代四显示模式的人工视觉与听感验收。

普通版已恢复且自动操作结束。已将 GbaLite_Phase4_Color_Pattern.gba 和 GbaLite_Phase4_LCD_Pattern.gba 放入手机 Download，并发出人工验收步骤：四模式、Fit / Integer、竖屏 / 正反横屏、细字与棋盘格，以及既有 Persistence ROM 上的 2×/4×/8×、倒带、截图 / 缩略图、Home 静音恢复、触控 / 蓝牙和偏好保存。当时待确认的项目现已由用户于 2026-10-07 最终确认通过，见上表。

用户随后报告横屏多余白边并选择保留完整画面最大化显示；已完成修订，安装新版，全部 17 项回归通过后停止自动操作。新版 Home 静音检查在后台 1200 ms 内播放计数保持 66316、帧保持 68，返回恢复增长，证据见 fullscreen-home-audio-metrics.txt。用户当时回复“没问题”，确认该项人工复验通过；其余项目由本次最终人工反馈另行确认。

背景黑白设置已构建、安装。增加第 18 项实机测试，检查菜单入口、真实 GLES 留边颜色、磁盘偏好与 Activity 重建、游戏颜色及 Session 保持。首次整轮在工具中断及 ADB 断开前只记录前 10 项通过、第 11 项开始，不能视为 18 项完成；用户随后确认重连，已补测。

重连后的第一轮完整测试记录 16/18 通过，两项失败为白色留边采样和最终 Activity recreate 后的画面采样。旧测试在 Compose / Surface 重绘完成前取样，并选择了屏幕圆角处的 (0,0)。修改测试为等待 Compose 空闲及后续实际绘制、从留边中部取样；通用捕获也等待新 Surface 至少两次绘制。背景设置单项重新通过（5.889 秒），随后完整 18 项通过（162.695 秒），没有为通过测试改动游戏画面或生产颜色实现。失败日志归档为 phase4-background-first-complete-failure.log，不能当作通过。背景设置功能已交付，用户于 2026-10-07 最终确认黑白切换及偏好保持正常。

## S. 已知问题

历史整屏黑屏根因未完全确定；本轮捕获的 GLSurfaceView 等待已替换架构并复验，当前版本自动与人工验收均未复现。最终人工确认声音恢复正常；短时非零音频欠载计数仍需长期听感观察。自动测试反复恢复播放器会重新播放测试 ROM 的持续测试音；正常 Home 静音已独立验证。

## T. 风险

色彩模型为社区近似，OLED / LCD 面板视觉可能不同。EGL 完全失效无法通过 shader fallback 修复。只覆盖一台手机与自制 ROM。ASan 沿用现有 Windows host runtime 限制，未通过；不伪造 sanitizer、长期性能、商业 ROM 或多设备验收。

## U. 未完成项

**Phase 4 最终人工验收已完成（2026-10-07）**，此前人工验收阻断项全部关闭。背景设置版构建、45 次 JVM 执行、18 项实机回归和 55 项审计结果沿用既有记录；本次没有新增测试执行。

以下为仍未完成的非阻断项目，不因 Phase 4 READY 自动标记完成：

- 多设备兼容性测试。
- 长时间运行、温升与内存分析。
- 完整 ASan / Fuzz 验证。
- 更广泛的合法 ROM 兼容性测试。
- Release 正式签名及发布验收。

普通 APK 归档仍在 `D:/GPT/artifacts/gba-emulator/phase4/normal`，既有 APK SHA-256 记录未修改；Debug 签名 SHA256 沿用 `3660dfb189306e49d973a7f91c9cbc1cb35cc93921f0292e8db28b3fa10b4094`，Release 仍未正式签名。历史黑屏复发、色彩近似模型及不同驱动 / 面板的非阻断风险继续保留。未声称完成 ASan、远程 CI 或长期稳定性测试。

## V. 是否允许进入 Phase 5

**READY**

理由：Phase 4 规定的 Original / Sharp / GBA Color / LCD 四种显示模式、Fit / Integer 缩放、背景设置、Shader 故障回退、Surface 生命周期、横竖屏、快进倒带以及存档和输入回归已完成。既有自动测试、安全审计及用户于 2026-10-07 确认的最终人工实机验收均已通过，满足进入 Phase 5 的技术条件。

历史横屏黑屏在当前版本未复现，相关 Surface 回归与最终人工验收通过；不宣称已完全确认原始故障根因，仍保留为非阻断复发风险。

Phase 4 READY 不等于 V1 正式发布许可；Release 仍未正式签名，V1 Release 仍为 NOT READY。允许规划与实施 Phase 5，但本轮只更新文档，**未启动 Phase 5**。

项目未另设独立的正式阶段状态文件，在本报告汇总阶段状态：

```text
Phase 0 — PASS
Phase 1 — PASS
Phase 2 — PASS
Phase 3 — PASS
Phase 4 — PASS / READY
Phase 5 — NOT STARTED

V1 Release — NOT READY
```
