# GBA Lite — Phase 7A-0 Android Emulator Test Infrastructure Setup

**文档版本：** v1.0  
**阶段定位：** Phase 7A-0 — Android Emulator 测试基础设施搭建  
**执行者：** Codex / 开发代理  
**执行时机：** Phase 7A 正式兼容性/稳定性测试之前，Phase 7.5 UI/UX 优化之前  
**目标：** 建立 Emulator-first 的 Android 自动测试环境，减少后续对真实手机的频繁依赖

---

## 0. 核心要求

本任务只搭建和验证 Android 测试基础设施。

不要：

```text
实现新用户功能
修改模拟器核心逻辑
进入 Phase 7B 防御性安全测试
进入 Phase 7.5 UI/UX 优化
进入 Phase 8 Signing / Release Candidate
创建生产 keystore
升级 mGBA
升级冻结工具链
改变 Game ID / Save identity
```

允许：

```text
检查本机 Android SDK / Emulator / adb 环境
安装必要的 Android Emulator 组件和 system image
创建 AVD
配置项目级测试脚本
配置 Gradle Managed Devices（如合适）
运行 Debug 构建和 instrumentation
验证 adb / install / launch / screenshot / logcat
补充仅与测试基础设施相关的文档和 CI 配置
```

---

# 1. 为什么现在执行

本项目即将继续：

```text
Phase 7A
→ Phase 7B
→ Phase 7 总结
→ Phase 7.5 UI / UX 优化
→ Phase 8 RC / Signing / V1.0
```

Android Emulator 应在 Phase 7.5 之前建立，因为后续大量工作适合自动运行：

```text
Android API compatibility
install / launch / relaunch
SAF import
Activity recreate
background / foreground
orientation stress
ROM switch stress
save / state regression
Room / DataStore migration
Library stress
long-run automation
instrumentation
logcat collection
screenshot capture
UI regression
```

目标不是完全替代真实设备。

最终仍保留真实设备用于：

```text
Bluetooth HID
USB HID
真实传感器
OEM-specific lifecycle behavior
真实 GPU driver
真实音频延迟 / 杂音
thermal / battery
最终人工体验验收
```

---

# 2. Emulator-first 测试策略

建立以下测试层级：

```text
Tier 1 — Primary Emulator
API 34
日常开发 / 自动安装 / instrumentation / UI / 生命周期测试

Tier 2 — Compatibility Emulators
API 26
API 29
API 36

Tier 3 — Physical Device
只用于模拟器不能可靠覆盖的最终验收
```

不要一次安装所有 Android API。

第一阶段先完成：

```text
API 34
```

确认整条自动化链路稳定后，再补：

```text
API 26
API 29
API 36
```

---

# 3. 环境发现：先检查，后安装

开始前只读检查当前环境。

记录：

```text
OS
CPU architecture
virtualization availability
Android Studio version
Android SDK root
cmdline-tools version
sdkmanager availability
avdmanager availability
adb version
emulator version
installed platforms
installed build-tools
installed emulator system images
existing AVDs
Java / JDK version
Gradle version
AGP version
project minSdk
project targetSdk
free disk space
```

优先使用项目现有工具链。

禁止为了创建 Emulator 擅自升级：

```text
AGP
Gradle
Kotlin
NDK
CMake
Compose BOM
mGBA
Oboe
```

---

# 4. Windows 虚拟化检查

如果当前开发机是 Windows：

检查：

```text
CPU virtualization support
Windows Hypervisor Platform / Hyper-V compatibility
Android Emulator hypervisor capability
emulator -accel-check
```

只做检测。

如果发现虚拟化未启用，或需要修改：

```text
BIOS / UEFI
Hyper-V
Windows Hypervisor Platform
Virtual Machine Platform
系统安全策略
```

不要自行修改。

必须：

```text
记录原因
输出最小人工操作说明
暂停相关安装步骤
等待用户完成后继续
```

不要为了获得加速能力关闭系统安全功能。

---

# 5. 磁盘空间原则

在安装 system image 前检查磁盘空间。

优先：

```text
x86_64 system image
```

如果主机架构或 Android Emulator 当前推荐路径要求其他架构，则以可执行性为准。

不要：

```text
下载重复 system image
保留无用 AVD
一次安装大量 API
```

如果空间不足：

```text
先列出预计新增空间
不要擅自删除用户文件
不要清理未知 SDK / AVD
等待确认
```

---

# 6. Primary AVD — API 34

先创建一个稳定的主要测试设备。

建议：

```text
Name:
GBA_Lite_API34

API:
34

ABI:
x86_64（若当前主机适用）

Device profile:
Pixel 类标准手机配置

RAM:
使用合理默认值，不做激进超配

Storage:
足够存放 Debug APK、测试 ROM fixture 和测试数据

Graphics:
优先自动 / hardware accelerated
如出现兼容问题再记录并评估 software 模式
```

不要依赖 Play Store 镜像，除非项目确实需要。

本项目：

```text
无账号
无 Play Services 依赖
无在线功能
```

因此优先使用适合自动测试的标准 Google APIs / AOSP image。

---

# 7. API 34 基础启动验证

启动：

```text
GBA_Lite_API34
```

验证：

```text
emulator boots successfully
adb device visible
device becomes boot_completed
no permanent offline state
screen responds
package install works
package uninstall works
```

记录：

```text
AVD name
API
ABI
system image revision
emulator version
boot time
graphics backend
adb serial
```

---

# 8. 项目自动化链路验证

在 API 34 上建立并验证：

```text
Gradle build
→ Debug APK
→ adb install
→ app launch
→ instrumentation
→ logcat capture
→ screenshot capture
→ app force-stop
→ relaunch
→ uninstall / reinstall
```

必须实际执行，不要只写脚本。

---

# 9. Debug APK 安装与启动

执行：

```text
clean / required build task
Debug APK build
adb install / install-multiple as applicable
launch main Activity
```

确认：

```text
应用能启动
无安装权限回归
无 INTERNET 权限
无启动 crash
无 immediate ANR
```

不要用模拟器测试结果冒充真实设备结果。

---

# 10. Instrumentation Baseline

运行现有：

```text
instrumentation tests
Android UI tests
Activity tests
```

如果当前项目没有完整 instrumentation：

```text
不要为了本阶段大规模重写测试架构
```

可以增加：

```text
最小 smoke instrumentation
```

只用于验证：

```text
launch
basic navigation
activity lifecycle
```

如果已有测试，优先复用现有测试。

---

# 11. adb 操作封装

建立可重复执行的项目脚本或文档命令，用于：

```text
detect emulator
wait for boot
install Debug APK
launch app
force-stop app
clear app data
relaunch app
capture logcat
capture screenshot
rotate device
send Home
return foreground
kill process
uninstall app
```

优先跨环境可维护方案。

如果必须使用 Windows PowerShell：

```text
保持命令可读
避免写死个人机器路径
优先读取 ANDROID_HOME / ANDROID_SDK_ROOT
```

---

# 12. Emulator 专用测试数据

仅允许：

```text
internal test ROM
合法可再分发 test ROM
synthetic metadata
synthetic save/state fixtures
```

商业 ROM：

```text
不得复制到 repo
不得加入自动化
不得加入 CI
不得写入测试资产
```

如用户后续在本地手动拖入自有 ROM，只属于私有人工验证。

---

# 13. SAF 测试

在 Emulator 中验证：

```text
SAF picker opens
.gba import
.zip single-ROM import
cancel
reopen
Activity recreate during picker flow where feasible
```

只能使用合法测试资产。

记录：

```text
API 34 result
import result
cancel result
relaunch result
```

---

# 14. 生命周期自动化 Smoke Test

在 API 34 至少执行：

```text
launch
Home
foreground
orientation portrait
orientation landscape
reverse landscape if available
force-stop
relaunch
```

验证：

```text
no crash
no ANR
no duplicate core
no duplicate renderer
no persistent audio after background
no obvious state loss
```

这只是基础设施 smoke test。

正式 50–100 次压力测试属于 Phase 7A。

---

# 15. Screenshot 与 UI 预备能力

建立 Emulator 截图能力，为 Phase 7.5 做准备。

验证：

```text
adb screenshot
stable output file
portrait screenshot
landscape screenshot
```

如果可能，建立统一输出目录：

```text
docs/reports/evidence/emulator/
```

不要在本阶段开始 UI 重设计。

---

# 16. Logcat / Crash Evidence

建立：

```text
filtered logcat capture
full logcat capture
crash / ANR evidence location
```

如果 native crash：

```text
保存 tombstone / stack evidence if available
```

本阶段只验证证据链是否可用。

---

# 17. API 26 / 29 / 36 Compatibility AVDs

只有 API 34 链路通过后再创建：

```text
GBA_Lite_API26
GBA_Lite_API29
GBA_Lite_API36
```

用途：

```text
API 26 = minSdk / legacy boundary
API 29 = Android 10 representative
API 34 = primary daily target
API 36 = latest compatibility target
```

如果某个 system image 当前不可用：

```text
不要擅自换成大量其他 API
记录不可用原因
选择最接近的可用代表 API
在报告中说明
```

---

# 18. 每个 Compatibility AVD 的最小验证

每个 AVD 至少：

```text
boot
adb visible
install
launch
basic navigation
SAF open
play internal/legal test ROM if available
save/load smoke
background/foreground
orientation
exit
relaunch
uninstall
```

不要在本阶段执行完整长测。

完整压力验证留给 Phase 7A。

---

# 19. Gradle Managed Devices 评估

API 34 普通 AVD 稳定后，评估是否适合加入：

```text
Gradle Managed Devices
```

目的：

```text
Gradle 自动启动 Emulator
→ 安装 APK
→ 跑 instrumentation
→ 收集结果
→ 关闭设备
```

如果项目当前 AGP / Gradle 版本支持且改动低风险：

```text
可以配置一个 API 34 managed device
```

但：

```text
不得为了 GMD 升级冻结工具链
```

如果当前工具链不适合：

```text
记录 DEFERRED
继续使用普通 AVD + adb
```

---

# 20. 不要求模拟器覆盖的项目

明确标记为 Physical Device Required：

```text
真实 Bluetooth controller
真实 USB controller
OEM-specific behavior
真实 gyro / tilt 行为
真实音频设备切换
真实音频 latency / underrun perception
GPU vendor-specific rendering
Doze / lock-screen OEM behavior
thermal
battery consumption
最终触控体验
最终 30–60 min real gameplay
```

不得把 Emulator PASS 写成这些项目的最终 PASS。

---

# 21. Renderer 注意事项

本项目历史上存在：

```text
black screen
GLSurfaceView ANR
TextureView lifecycle issues
```

Emulator 可以用于：

```text
基础回归
生命周期压力
横竖屏
context recreation
shader switching
```

但最终仍要求真实设备回归。

在 Emulator 上出现 renderer 问题时记录：

```text
graphics backend
host GPU
emulator version
API
AVD image
```

避免把虚拟 GPU 问题直接认定为应用回归。

---

# 22. Audio 注意事项

Emulator 只用于：

```text
audio starts
audio stops
pause/resume
no duplicate stream
no permanent silence
basic underrun observation
```

以下仍留给实机：

```text
真实 latency
杂音
Bluetooth route
USB audio
speaker switching quality
```

---

# 23. Sensor 注意事项

Emulator 可用于：

```text
逻辑路径
sensor injection where supported
lifecycle register/unregister
RTC
```

但：

```text
Tilt
Gyro
Solar
```

最终功能体验仍需要真实设备或明确的受控 sensor injection。

不得冒充物理验收。

---

# 24. Emulator-first 项目规范

在项目文档中补充一份测试策略，例如：

```text
docs/ANDROID_TEST_DEVICE_STRATEGY.md
```

至少包含：

```text
Primary automated target = API 34 Emulator

Compatibility targets:
API 26
API 29
API 36

Physical device required for:
HID
sensors
OEM lifecycle
real audio
thermal/battery
final renderer validation
manual acceptance
```

并说明：

```text
Emulator results never replace explicitly required physical-device acceptance.
```

---

# 25. 与 Phase 7A 的衔接

Phase 7A 后续默认：

```text
普通自动测试优先 Emulator
真实设备只在必要项目和最终人工验收连接
```

Phase 7A 可直接复用本阶段：

```text
AVD
adb scripts
screenshots
log capture
GMD
test data
```

不要重新搭建重复环境。

---

# 26. 与 Phase 7.5 的衔接

Phase 7.5 UI/UX 优化时默认：

```text
修改 UI
→ build
→ API 34 Emulator install
→ launch
→ portrait screenshot
→ landscape screenshot
→ interaction smoke test
→ regression
```

必要时再在：

```text
API 26
API 36
```

验证系统栏、edge-to-edge、导航等差异。

最终 UI 视觉和触控体验仍需真实手机确认。

---

# 27. CI 边界

本任务可以准备 Emulator CI，但不要强行扩大 CI。

优先：

```text
现有 remote CI 保持稳定
```

如果 instrumentation emulator CI 容易加入：

```text
可以新增
```

如果：

```text
启动不稳定
成本过高
当前 runner 不支持
```

则：

```text
DEFERRED
```

不要因此阻塞本地 Emulator-first 流程。

---

# 28. 安全与权限边界

本任务属于正常 Android 开发测试基础设施。

不要：

```text
网络扫描
第三方系统测试
漏洞利用
绕过系统安全策略
关闭主机安全功能
```

如果 Android SDK / Emulator 下载需要联网：

```text
只访问官方 Android / Google SDK 来源
只安装明确需要的组件
```

如执行环境禁止自动下载：

```text
列出精确缺失组件
标记 MANUAL_INSTALL_REQUIRED
继续可执行部分
```

---

# 29. 失败处理

任何步骤失败时：

```text
先复现
记录完整错误
判断是：
- SDK
- system image
- virtualization
- adb
- emulator graphics
- Gradle
- app
- instrumentation
```

不要：

```text
盲目重装全部 Android Studio
删除整个 SDK
删除所有 AVD
升级项目工具链
关闭 Windows 安全功能
```

优先最小修复。

---

# 30. 输出报告

生成：

```text
docs/reports/PHASE_7A_0_ANDROID_EMULATOR_INFRASTRUCTURE_REPORT.md
```

至少包含：

```text
A. Host Environment
B. Existing Android Tooling
C. Virtualization Status
D. Installed Components
E. Primary API 34 AVD
F. API 34 Boot / adb Result
G. Build / Install / Launch Result
H. Instrumentation Result
I. SAF Smoke Result
J. Lifecycle Smoke Result
K. Screenshot / Logcat Evidence
L. API 26 Result
M. API 29 Result
N. API 36 Result
O. Gradle Managed Devices
P. Physical-device-only Matrix
Q. Changed Files
R. Known Issues
S. Deferred Items
T. Final Decision
```

---

# 31. Evidence

保存到：

```text
docs/reports/evidence/emulator/
```

建议包括：

```text
environment.txt
sdk-components.txt
avd-list.txt
accel-check.txt
api34-boot.txt
adb-devices.txt
build-log.txt
install-log.txt
launch-log.txt
instrumentation-result.txt
logcat.txt
portrait.png
landscape.png
api26-smoke.txt
api29-smoke.txt
api36-smoke.txt
```

不要保存用户隐私数据。

---

# 32. 成功定义

只有满足以下条件，Phase 7A-0 才算 PASS：

```text
1. API 34 AVD 可以稳定启动
2. adb 可以稳定识别
3. GBA Lite Debug APK 可以自动安装
4. App 可以自动启动
5. 基础 instrumentation / smoke test 可运行
6. logcat 可收集
7. screenshot 可自动保存
8. foreground/background/orientation smoke 可运行
9. 至少明确 API 26 / 29 / 36 的可用状态
10. 已形成 Emulator-first 测试策略文档
11. 已明确哪些项目仍必须真实设备验证
```

Compatibility AVD 如果因官方 image 或环境原因个别不可用：

```text
允许 PASS_WITH_NOTE
```

但 API 34 主测试链路必须可用。

---

# 33. 最终状态

成功后写：

```text
Phase 7A-0 — PASS
Android Emulator Infrastructure — READY
Primary Automated Android Target — API 34 Emulator
Physical Device — Final / hardware-specific acceptance only
```

然后：

```text
继续 Phase 7A
```

不要直接进入 Phase 7.5。

推荐顺序：

```text
Phase 7A-0
↓
Phase 7A
↓
Phase 7B
↓
Phase 7 Final Decision
↓
Phase 7.5
↓
Phase 8
```

---

# 34. Codex 执行指令

现在开始。

首先：

```text
只读检查当前 Android SDK / Emulator / adb / virtualization / AVD 环境。
```

然后在不升级冻结项目工具链的前提下：

```text
补齐必要 Android Emulator 组件
创建并验证 API 34 主 AVD
打通 build → install → launch → test → logcat → screenshot 链路
再创建 API 26 / 29 / 36 compatibility AVD
评估 Gradle Managed Devices
补充项目测试策略与执行报告
```

如果某一步需要用户手动修改 BIOS、Windows 功能或管理员级系统设置：

```text
不要自行修改。
停在该步骤。
明确告诉用户需要做什么。
其他不依赖该步骤的工作继续执行。
```

不要只输出计划。

实际执行能够安全自动完成的部分，并保存证据。

---

# 35. 最终原则

本阶段的目标不是“安装一个模拟器”这么简单。

目标是建立：

```text
Codex
↓
Gradle
↓
Android Emulator
↓
adb
↓
APK install
↓
App launch
↓
Instrumentation
↓
Lifecycle automation
↓
Logcat / Screenshot Evidence
```

这一条可重复、可审计的开发测试链路。

完成后，后续 GBA Lite 的绝大多数日常 Android 测试都优先在 Emulator 中自动完成，真实手机主要保留给硬件相关和最终人工验收。
