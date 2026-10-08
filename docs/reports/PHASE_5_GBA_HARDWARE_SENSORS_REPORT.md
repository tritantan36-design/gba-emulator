# Phase 5 — GBA Hardware / Sensors

日期：2026-10-07。项目：`D:/GPT/projects/gba-emulator`。

**Phase 5 — READY**。最终人工验收及阶段关闭日期：2026-10-07。
用户明确确认 Tilt / Gyro / Solar / RTC 人工体验均无问题；现有自动验证结果见 M 节。
用户同时批准真实手机 Rumble 为 **DEFERRED — V1 zero-permission policy**，
不再阻塞阶段关闭。此项为明确的产品范围与验收门槛调整，见 ADR-015 的后续决策；
不代表真实震动已实现或实机测试通过。
**Phase 6 — NOT STARTED；V1 Release — NOT READY。**

## A. 实际完成及版本

版本 0.5.0 / versionCode 5。沿用系统 RTC；接入重力/加速度倾斜、独立角速度、环境光及手动
日照、手动倾斜圆盘和独立角速度滑块、模式与校准持久化、按 ROM 能力注册传感器、后台与
native 停止 epoch 清理。菜单 → 设置 → 外设提供入口和可用性说明。
卡带震动接入原生开/关 mailbox、Session 和 DisabledHapticOutput，未实现真实输出。

## B. 固定 mGBA API 核查

mGBA 0.10.5 / `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`，upstream 未修改。
完整结构、源码位置、数值与 probe 限制见 [GBA_PERIPHERALS.md](../GBA_PERIPHERALS.md)。
core/interface.h：mRotationSource、mRumble、mRTCSource/mRTCGenericSource；
gba/interface.h：GBALuminanceSource、mPERIPH_GBA_LUMINANCE；gba/core.c：挂载；
gba/cart/gpio.c：卡带总线与数值转换；gba/overrides.c：实际硬件检测。
同 commit Libretro 仅作数值参考，没有加入 Libretro 层。

## C. 模块 / Core / Session / JNI

先记录 ADR-014、ADR-015。复用 core-api/input/data/emulator-session/core-mgba/app/feature-player，
不新增 Gradle 模块或依赖。Core API 使用不可变标量；SensorMapper 为纯 JVM；SensorAdapter
仅持应用 Context。Session Mutex → Adapter synchronized → JNI registry shared ownership →
native control mutex。每帧统一锁存 X/Y/Z/light；回调对象由每个 Core 持有到 deinit。
UI 不调用 JNI，SensorAdapter 不持 native handle，audio/GL 回调不调用 Android 传感器。
未修改正式 PROJECT_SPEC、ARCHITECTURE；未更改 renderer、audio、storage 或 upstream 实现。

## D. RTC：时间与保存恢复

保留 RTC_NO_OVERRIDE → time(0) epoch seconds；GPIO localtime_r 转为本地 BCD 日期时间。
单调时间仅用于 pacing，快进不加速 RTC。Host 用 RTC_FIXED 测试源验证 UTC 闰日、跨午夜、
时区和时钟跳变、State 读回；Android JNI 通过真实 ROM 读取当前日期、秒数及 1/2/4/8×。
原 raw State v7 序列化硬件交易/锁存字段；下一次 RTC 交易刷新真实时间。未加入自定义生产
RTC、扩展 State 格式或修改 battery trailer。RTC 人工体验由用户于 2026-10-07 最终确认无问题；具体自动时间验证仍以 M 节日志为依据。

## E. Tilt：映射、校准和回退

优先 TYPE_GRAVITY，缺少时 TYPE_ACCELEROMETER + 低通。m/s² 设备坐标校准后按 display
rotation 0/90/180/270 变换；死区 .15、低通、±9.81 范围、-2e8 X/+2e8 Y 定点比例。
NaN/Inf 转安全有界值；超过 500ms 的运动样本归零。校准保存设备坐标，避免旋屏改变零点。
手动圆盘只在用户打开时显示，释放/取消归中。缺失或注册失败使用手动方案；恢复重新检查。
JVM 与真实 ROM/JNI 数值验证通过；Tilt 人工体验由用户于 2026-10-07 最终确认无问题。

## F. Gyro：角速度

TYPE_GYROSCOPE 的屏幕法线 Z（rad/s）；平面方向旋转不改变该法线轴。独立偏置/死区 .025、
低通、±3rad/s 钳制、-5.5e8 比例。手动滑块表示角速度，释放停止，未将倾斜当成陀螺仪。
真实 GPIO serial probe 与 JNI 中立/正负输入、rewind 清理通过；Gyro 人工体验由用户于 2026-10-07 最终确认无问题。

## G. Solar：自动与手动

TYPE_LIGHT lux → log(1+lux)/log(10001) → 0..100% → 0..10 级；采用 pinned GBA_LUX_LEVELS
和 255-(22+level) 的反向阈值。手动 0..100%，关闭为暗值；缺传感器或注册失败自动回退手动。
不读取/更改系统屏幕亮度。真实 ROM 计数已验证 233 暗 → 50 强光；设置写独立 DataStore。
Solar 人工体验由用户于 2026-10-07 最终确认无问题。

## H. Rumble：权限与输出

用户于 2026-10-07 明确选择零权限，见 ADR-015。Debug/Release merged permissions 均为 []。
产品状态为 **DEFERRED — V1 zero-permission policy**，不阻塞阶段关闭；不申请 VIBRATE，不使用触控 haptic 冒充。
现有 DisabledHapticOutput 的运行时状态字符串仍为 BLOCKED_BY_PERMISSION_APPROVAL；本次仅改文档，不修改代码或 APK。
真实 GPIO pin 3 → mRumble boolean → 受控最新 mailbox / stopEpoch → JNI/Session 通路已验证。
JVM 记录式 HapticOutput 为合成测试；生产 DisabledHapticOutput 不驱动手机或手柄。
真实输出的限频/幅度/持续时间与 actuator 降级属于未授权、未实现部分，不声称已实机通过。
如果以后改变权限选择，必须另行获明确批准并按 ADR 落实；本轮不再请求该批准。

## I. 生命周期、线程与节能

按每个 ROM 的实际 hw.devices 决定 AUTO 注册；手动模式可以通过 pinned initializer 挂载
未检测到的硬件，不改 save type。目标 SENSOR_DELAY_GAME，Session latest ticker 50Hz。
Home/onStop 即注销；Session pause/State/rewind 清理原生值并增加 stopEpoch，application
ticker 检测 epoch/游戏变化，丢弃陈旧输入后重新采样。沿用唯一 Activity foreground 队列。
无传感器历史库/运动日志、静态 Activity 引用或新的后台服务。实机 Activity 已验证按 ROM 注册、三种方向、Home/返回、倒带和退出时监听器变化；Tilt / Gyro / Solar / RTC 人工体验已由用户最终确认。

## J. 设置、schema 与兼容性

独立单例 DataStore `settings/peripherals-v1.settings`，有界 256 字节、编码版本 1，存模式、
校准、手动日照和震动开关；损坏/未知版本安全默认。原 display v1/v2 文件未改。
GameId、Room、battery/generation、State JSON schema 与 pinned core identity 均未改。
StateMetadata.appVersion 更新为 0.5.0，仅标记创建应用版本。旧 v1 sav SHA-256 保持
`91fe8bc63da1c542130e95137a4e28eeb1ec7df6239ca92fbe08e050b3979c9f`。

## K. 合法 ROM 与哈希

项目原创 Apache-2.0 `peripheral-probe.c` + `peripheral-probes.py`，固定 NDK 源码编译；
五份 ROM 均仅在 androidTest/assets，主 APK 无 ROM。源码和 ROM 哈希见
`test-rom/manifests/peripheral-tests.json`。Header ID 仅选择核心内置 mapper，不含商业游戏代码
或素材、Nintendo logo/BIOS。原始值来自真正的 GPIO/tilt bus 读取，并输出 EWRAM 与色条。
ROM 的标识/适用列/反向日照 meter 限制在 GBA_PERIPHERALS 中明确说明。

## L. Phase 0–4 回归

本轮 JVM、host 与普通 JNI 已回归既有 battery/State/重开/旧 sav、音频/输入、FF/倒带和
色彩图案原始帧。Activity 原有 18 项加新 2 项已执行，覆盖四 Shader、
黑白背景、Fit/Integer、实际 TextureView、Home 音频及 Surface 专项；最终结果见 M 节。
Phase 4 历史 READY 和日志保留，不用历史 PASS 代替本轮执行。

## M. 自动测试、失败与证据

| 检查 | 本轮结果 | 证据（evidence/phase5/） |
|---|---|---|
| JVM | 49 次执行，0 failures / 0 errors | jvm-summary.json，test-results/ |
| Host native | PCM 1 + mGBA 6 = 7 项通过 | phase5-host-final.log |
| 普通 JNI | 15 项完整通过，42.191 秒 | phase5-normal-foreground-jni.log |
| Debug / unsigned Release / 测试 APK | 构建成功 | phase5-delivery-verified-build.log |
| lint | 0 errors / 19 warnings | lint-results-debug.txt |
| 安全 / provenance 审计 | 66 PASS / 0 FAIL | PHASE_5_AUDIT_RESULTS.md，phase5-delivery-verified-audit.log |
| 普通 Activity | 20 项完整通过，179.228 秒 | phase5-normal-activity-final.log |
| UBSan JNI | 15 项完整通过，45.066 秒 | phase5-ubsan-foreground-jni.log |
| UBSan Activity | 20 项完整通过，232.033 秒 | phase5-ubsan-activity-final.log |

JNI 普通及 UBSan 整轮通过使用同一 native bridge 和测试 ROM。最终普通构建、单元测试、lint 及审计已重跑。

失败历史：host 首轮 Windows 断言对话框未退出；改为 stderr 后发现探针绘制需要更多帧，
保持同样数值断言等待完整采样。随后发现 Solar probe 在第一脉冲计数偏一，修复原创 ROM
计数并优化显示循环，真实阈值测试通过。普通 JNI 首次相同 Solar 用例失败，修正 ROM 后
完整 15 项通过。所有早期日志保留，没有改写失败记录或放宽数值断言来掩盖生产缺陷。

UBSan 日志末尾的 Process crashed 来自主动 force-stop 独立测试进程，不是整轮完成。
观察到 do_freezer_trap；新增仅 androidTest 的 KEEP_SCREEN_ON 前台测试 Activity 后，
相同普通和 UBSan JNI 均完整通过。该执行阻断关闭，不声称永久排除所有冻结问题。详见 DEVICE_BLOCK_NOTE。

Activity 首轮 19/20：新增 Home 用例误启动新 Activity，改为复用原 Activity 后通过。
后续普通及 UBSan 各有一轮 19/20，旧 SAF 红色屏幕截图断言失败；UBSan 诊断像素 #990000，
Original 偏好已写入。基线显式选择 Original 并恢复旧偏好，数值断言未放宽；单独三项基线
重跑通过。另一次单项独立执行未导航至播放器，保留其超时日志。根因未完全确认，保留
间歇性窗口截图/测试环境风险，后续普通 20/20 与 UBSan 20/20 整轮均通过，不以重跑抹去失败。

## N. 人工验收：严格分开

**已完成阶段最终人工验收。** 用户于 2026-10-07 明确确认：
“Tilt / Gyro / Solar / RTC 的人工体验都没问题”。上述四类人工体验记录为通过，
证据来源为用户最终反馈，不是新增自动测试日志或新的逐步骤设备记录。

自动验证独立归档于 M 节：普通 JNI 输入为合成标量，host 使用固定测试源；
Activity 已验证真实传感器注册/注销，设备报告 Tilt/Gyro/Light 均可用。
不将这些自动测试冒充人工姿态/光照验收，也不从用户概括确认推导未报告的量化数据。
原验收步骤保留于 [PHASE_5_MANUAL_ACCEPTANCE.md](PHASE_5_MANUAL_ACCEPTANCE.md)。
真实手机 Rumble 经用户明确产品决策延期，不记为实现完成或实体震动 PASS。

## O. 性能与电量

有限采样频率、最新快照不积压、仅相关 ROM 前台采样、后台注销及零真实震动。
没有做本轮 CPU/温升/内存长测或 30–60 分钟电量分析，不能据代码设计宣称已节能验收。
短时渲染与 Home 音频证据分别记录普通/UBSan 数据。GPU completion wait 仅为等待上界；Window GPU_DURATION 是窗口合成范围，不是孤立 GLES 执行时长。UBSan 开销数据不作为普通版性能结论：例如 LCD 8× 的短时实际值约 5.42×；该回归通过既有至少 4× 的吞吐下界，不能声称 UBSan 达到 8×。
仅一台 OPPO PLG110 / Android 16 / arm64；旧 API 与多设备实测仍未覆盖。

## P. 依赖、许可、权限与 native 来源

mGBA、Oboe、工具链及依赖锁不升级，无新增 SDK。Manifest 权限仍为 []，无 INTERNET、
Analytics、广告、位置、蓝牙、存储或传感器高采样权限。原官方固定 AndroidX native allowlist
与精确 DataStore SHA 审计通过；项目 native 从固定源码构建。没有未知预编译 binary。
PROJECT_SPEC、ARCHITECTURE、Phase 0–4 报告/审计与历史 APK 保持原样。

## Q. 未完成项与风险

- 真实手机 Rumble：DEFERRED — V1 zero-permission policy；未实现、未实机验收，按用户决策不阻塞阶段关闭。
- Tilt / Gyro / Solar / RTC 最终人工体验验收已完成，依据用户 2026-10-07 明确确认。
- 旧 SAF 窗口红色截图曾间歇失败，精确根因未确认；保留失败与重跑证据。
- 历史横屏黑屏原始根因未完全确认，必须保留 Surface/ANR 回归，不宣称永久修复。
- 多设备、更多合法 ROM、ASan/fuzz、远程 CI、长时间运行/温升/内存/电量仍未完成。
- Release 未正式签名；未建正式 keystore、AAB 或发布验收。

## R. 构建、签名、Git

最终普通产物：`D:/GPT/artifacts/gba-emulator/phase5/delivery/`。
更早普通与 UBSan 产物目录全部保留，不覆盖 Phase 4 产物。实际 UBSan Activity 主 APK 位于 phase5/ubsan-run，SHA-256 `e927efa33542a5be0d2494eeb7052d8e8972444e3fd9678cb0ae890908e98ff9`；设备 base.apk 哈希已核对一致。

| 最终普通 APK | SHA-256 |
|---|---|
| app-debug.apk | c6fba7fed85c318564958736802eccb7ad4136c00e3388d9caf629f08bd94139 |
| app-release-unsigned.apk | 83535c2987cdce633f58bdce50b8421e1d8ad9af5e8f4a99138bfbe227a3105b |

Debug 开发证书 SHA-256 保持 `3660dfb189306e49d973a7f91c9cbc1cb35cc93921f0292e8db28b3fa10b4094`。
最终普通版及测试包已覆盖安装，未 uninstall / clear 用户数据。已停止冻结的独立 JNI 测试。
Git 延续原有整个项目未跟踪状态；本轮未暂存、提交、reset 或清理已有文件。

## S. 是否允许进入 Phase 6

**READY**

Tilt / Gyro / Solar / RTC 的实现、既有阶段回归、自动测试与安全审计已通过；
用户于 2026-10-07 最终确认四类人工体验均无问题。
真实手机 Rumble 根据同日用户明确决策调整为
**DEFERRED — V1 zero-permission policy**，不再作为本阶段关闭阻断项。
此决策取代此前任务书/ADR 中将未实现实体震动列为 READY 阻断项的门槛；
原任务书、PROJECT_SPEC 与 ARCHITECTURE 保持原文，决策依据记录于 ADR-015。

保留历史横屏黑屏与间歇性 SAF 窗口截图风险，不宣称原始根因已完全确认。
多设备、ASan/fuzz、远程 CI、长测、正式签名与发布验收仍未完成。
**Phase 5 READY，允许规划与实施 Phase 6，但本轮未开始 Phase 6；V1 Release NOT READY。**
