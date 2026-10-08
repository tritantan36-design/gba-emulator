# GBA Lite — Phase 7A Compatibility / Stability / Release QA

**文档版本：** v1.0  
**阶段定位：** Phase 7A — 非安全类兼容性、稳定性与发布前质量保障  
**执行者：** Codex / 开发代理  
**上位约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`  
**来源：** 从原 `PHASE_7_COMPATIBILITY_STABILITY_SECURITY_HARDENING.md` 拆分而来

---

## 0. 本文档的作用

本文件只执行 Phase 7 中**普通软件工程 QA** 部分。

本阶段明确不执行：

```text
fuzz
ASan / HWASan
UBSan 专项安全验证
ZIP traversal 攻击性 fixture
ZIP bomb 攻击性 fixture
UAF / double-free 专项
native parser fuzz harness
malformed-input mutation fuzz
Release ELF security hardening
路径逃逸 / 整数溢出专项安全审计
```

这些内容全部移交：

```text
PHASE_7B_DEFENSIVE_ROBUSTNESS_VALIDATION.md
```

Phase 7A 完成后不得直接宣布整个 Phase 7 READY。

正确状态为：

```text
Phase 7A = PASS / READY_FOR_7B
Phase 7B = NOT STARTED / BLOCKED / PASS
Phase 7  = 仅当 7A + 7B 均满足原始硬门槛后才能 READY
```

---

# 1. 本轮目标

Phase 7A 不增加任何面向用户的新功能。

只解决：

```text
兼容性
稳定性
长时间运行
生命周期
资源泄漏
音频
输入
渲染
存档
数据库兼容
升级
依赖与许可
CI
APK 发布前审计
人工验收
```

原则：

```text
No New Features
Fix / Measure / Verify
```

---

# 2. 前置状态

开始前确认：

```text
Phase 0 = READY
Phase 1 = READY
Phase 2 = READY
Phase 3 = READY
Phase 4 = READY
Phase 5 = READY
Phase 6 = READY
```

如果 Phase 6 报告仍写 `NOT READY`，但用户已经完成最终人工验收：

```text
只更新 Phase 6 报告状态
不要修改生产代码
不要重新解释历史测试
```

---

# 3. V1 功能基线

不得破坏：

```text
.gba import
.zip single-ROM import
Home / Continue / Recent / Library
Search / Sort / Game Details / Play Time
Battery Save
Manual State
Quick Save / Load
Autosave
Quick Resume
Fast Forward
Rewind
Touch Controls
HID input
Original / Sharp / GBA Color / LCD
Fit / Integer
Black / White background
RTC
Tilt
Gyro
Solar
Screenshot
State Thumbnail
Settings
```

Rumble：

```text
physical output = DEFERRED
```

不得添加：

```text
VIBRATE
```

---

# 4. 权限基线

必须保持：

```text
Debug permissions = []
Release permissions = []
```

不得新增：

```text
INTERNET
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
BLUETOOTH_CONNECT
LOCATION
VIBRATE
QUERY_ALL_PACKAGES
```

---

# 5. 禁止新增功能

本阶段不实现：

```text
Cheats
IPS / UPS / BPS
Local Link
Wi-Fi Link
Bluetooth Link
SkyEmu
Achievements
Cloud
Online Covers
ROM Scraper
Accounts
Sync
Shader Downloads
New Core
Netplay
```

---

# 6. Baseline Freeze

Phase 7A 开始前记录：

```text
app version
versionCode
Git state
Debug APK SHA-256
unsigned Release APK SHA-256
Debug certificate SHA-256
mGBA commit
Oboe commit
AGP
Gradle
Kotlin
Compose BOM
NDK
CMake
Room schema
DataStore schema
```

不要擅自升级冻结工具链。

---

# 7. ADR 规则

默认不修改：

```text
PROJECT_SPEC.md
ARCHITECTURE.md
```

如果必须改变架构或产品边界：

```text
先写 ADR
再实施
```

---

# 8. Test ROM Corpus

读取并执行：

```text
GBA_TEST_ROM_CORPUS.md
```

Tier：

```text
A — repo / CI safe
B — pinned fetch/build
C — local manual
D — user-owned commercial ROM
```

Phase 7A 自动流程只允许：

```text
Tier A
已许可审计的 Tier B
```

最低自动集合：

```text
Internal bringup
Internal persistence
Internal color-pattern
Internal lcd-pattern
Internal peripherals
Selected mGBA test suite
```

如许可完成，可加入：

```text
NanoBoyAdvance hw-test
160p Test Suite
```

`FuzzARM` 如其执行路径涉及 fuzzing，则放到 7B。

---

# 9. Homebrew Manual Corpus

至少覆盖：

```text
platformer
action
audio-heavy
high-load / 3D
save-heavy
RTC / sensor
```

不能只用自研测试 ROM。

---

# 10. Commercial ROM 边界

只允许：

```text
user-owned ROM
local private manual validation
```

禁止：

```text
上传
提交
打包
复制进 test assets
公开截图 corpus
```

报告仅写：

```text
local user-owned commercial ROM manual compatibility
```

未实际测试时：

```text
NOT_TESTED
```

---

# 11. Compatibility Matrix

创建：

```text
docs/reports/PHASE_7_COMPATIBILITY_MATRIX.md
```

字段：

```text
ROM/Test
Source
License Tier
Core Boot
Video
Audio
Input
Save
State
FF
Rewind
RTC/Sensor
30min+
Result
Notes
```

结果只允许：

```text
PASS
PASS_WITH_NOTE
FAIL
NOT_TESTED
NOT_APPLICABLE
```

---

# 12. Android API Compatibility

minSdk：

```text
26
```

至少覆盖代表 API：

```text
26
28/29
31/32
33/34
35/36
```

可使用：

```text
Emulator + real device
```

每个代表 API 至少验证：

```text
install
launch
SAF import
play
audio
touch
save/load state
background/foreground
orientation
library
settings
exit
relaunch
```

重点关注：

```text
SAF
TextureView / EGL
Audio
InputDevice
DataStore
Room
edge-to-edge
cutout
system bars
back navigation
lifecycle
```

---

# 13. Multi-device

如有第二台 Android 实机：

```text
优先不同 OEM
```

例如：

```text
Samsung
Pixel
Xiaomi
vivo
OnePlus
```

没有第二设备：

```text
NOT_TESTED
```

不得伪造覆盖。

---

# 14. Long-run Stability

必须至少执行：

```text
60 minutes continuous gameplay
```

期间穿插：

```text
pause
2× / 4× / 8×
rewind
state save/load
orientation
background/foreground
```

推荐：

```text
2–4 hours
```

但不是硬要求。

记录：

```text
start RSS
peak RSS
end RSS
native heap
Java heap
FD count
thread count
audio underruns
GL errors
ANR
crash
battery checkpoint
autosave
```

---

# 15. Core / Session Resource Stress

执行：

```text
open game
exit
open next game
```

至少：

```text
20–50 次
```

检查：

```text
Session
Core
Renderer
Audio
Input
SensorAdapter
ViewModel
TextureView
SurfaceTexture
```

目标：

```text
资源释放后回到接近基线
无重复 session/core
无明显线性增长
```

---

# 16. Orientation Stress

循环：

```text
portrait
landscape
reverse landscape
portrait
```

至少：

```text
50–100 cycles
```

记录：

```text
RSS
threads
FD
GL resources
EGL contexts
Surface lifecycle
```

---

# 17. Background Stress

循环：

```text
foreground 10s
Home 5s
foreground 10s
```

至少：

```text
50 cycles
```

验证：

```text
no audio leak
no sensor leak
no duplicate core
no duplicate renderer
no duplicate input listener
no stale save
```

---

# 18. ROM Switch Stress

循环：

```text
ROM A
ROM B
ROM C
```

至少：

```text
50 switches
```

必须：

```text
save old
close old
open new
```

不得出现：

```text
cross-game save
cross-game thumbnail
cross-game playtime
stale input
```

---

# 19. Save State Stress

自动执行：

```text
Quick Save/Load × 100
Slot Save/Load × 100
```

检查：

```text
atomicity
metadata
hash
battery isolation
thumbnail isolation
```

---

# 20. Autosave Stress

反复：

```text
background
foreground
exit
resume
```

检查：

```text
A/B/C sequence
latest valid
corrupt-current fallback previous
```

---

# 21. Sudden Process Death

测试：

```text
gameplay
→ force-stop / kill process
→ relaunch
```

只验证：

```text
last safe point
```

不得声称：

```text
last-frame perfect
```

---

# 22. Storage / IO Failure

通过测试替身或受控环境验证：

```text
write fail
fsync fail
rename fail
disk full
permission revoked
read truncation
```

要求：

```text
fail safely
preserve prior valid generation
```

此处只验证数据完整性，不进行攻击性 fuzz。

---

# 23. FD Leak

循环：

```text
import
play
save
screenshot
state
close
```

至少：

```text
100 次
```

记录：

```text
baseline
peak
final
```

不得线性增长。

---

# 24. Thread Leak

重点检查：

```text
Emulator worker
Audio
Renderer HandlerThread
Choreographer
Sensor ticker
DataStore
Room
```

反复启动/关闭游戏后：

```text
thread count returns near baseline
```

---

# 25. Renderer Hardening

继续回归：

```text
Phase 3 historical black screen
Phase 4 GLSurfaceView ANR
TextureView lifecycle
eglSwapBuffers
context recreation
orientation stress
background stress
shader switch stress
```

---

# 26. Shader Stress

循环：

```text
Original
Sharp
GBA Color
LCD
```

至少：

```text
100 次
```

期间加入：

```text
orientation
FF
rewind
```

检查：

```text
GL program leak
texture leak
black frame
wrong viewport
```

---

# 27. Audio Hardening

验证：

```text
start/stop
pause/resume
Home
FF→1×
rewind release
state load
```

如设备可用，再验证：

```text
speaker → Bluetooth headset
Bluetooth → speaker
wired / USB audio
```

无法验证：

```text
NOT_TESTED
```

Audio underrun 的目标不是绝对 0。

重点看：

```text
persistent audible failure
runaway underrun
permanent silence
duplicate stream
```

---

# 28. Input Hardening

测试：

```text
multi-touch
D-pad slide
A+B
L+R
touch + HID
disconnect
reconnect
rapid key
orientation while pressed
background while pressed
```

必须确保：

```text
all logical buttons released
```

---

# 29. HID Matrix

如有真实设备：

```text
Bluetooth controller
USB controller
```

没有 USB 实物：

```text
NOT_TESTED
```

合成事件不能冒充物理 USB 验收。

---

# 30. Sensor / RTC Hardening

回归：

```text
RTC
Tilt
Gyro
Solar
```

重点：

```text
register only relevant ROM
unregister on Home
rotation
pause
rewind
ROM switch
```

Rumble：

```text
DEFERRED
```

RTC 至少：

```text
10+ minutes
background
relaunch
FF
state load
```

确认：

```text
RTC follows wall clock
FF does not accelerate RTC
```

---

# 31. Library Hardening

测试：

```text
0 games
1 game
100 games
500 games
1000 synthetic metadata rows
```

覆盖：

```text
Home
Recent
Library
Search
Sort
Details
```

---

# 32. Thumbnail Stress

模拟：

```text
hundreds of thumbnails
missing files
corrupt WebP
oversized image
wrong dimensions
```

必须：

```text
fallback placeholder
no crash
bounded memory
```

---

# 33. State Compatibility

验证历史阶段 State：

```text
Phase 2
Phase 3
Phase 4
Phase 5
Phase 6
```

Phase 7A 均应可读取。

推荐固定合法 State fixtures。

---

# 34. Save Compatibility

固定：

```text
test-save-v1.sav
```

SHA-256 保持不变。

继续验证：

```text
battery import/export
backup fallback
ROM rename
reimport
Library remove/readd
```

---

# 35. Room Migration

必须验证：

```text
schema 1 → current
schema 2 → current
schema 3 → current
```

禁止：

```text
destructive migration
```

---

# 36. DataStore Compatibility

验证：

```text
display v1
display v2
peripherals v1
```

未知版本：

```text
safe fallback
```

---

# 37. Install / Upgrade Matrix

必须：

```text
fresh install Phase 7 Debug
Phase 6 Debug → Phase 7 Debug
```

如历史 APK 可用，推荐：

```text
Phase 2 → Phase 7
Phase 4 → Phase 7
```

升级后确认：

```text
Library remains
GameId remains
Battery remains
States remain
Quick remains
Autosave remains
PlayTime remains
Display settings remain
Touch layout remains
Peripheral settings remain
```

---

# 38. Production Signing Boundary

Phase 7A 继续保持：

```text
Release unsigned
```

不得创建生产 keystore。

正式签名属于：

```text
Phase 8
```

---

# 39. Dependency / Provenance / License

重新生成并验证：

```text
Debug dependencies
Release dependencies
lockfiles
verification metadata
```

确认：

```text
no dynamic versions
no snapshots
no unexpected repositories
```

APK 中 `.so` 必须可解释。

允许：

```text
own libgba_bridge.so
pinned official AndroidX native
```

新增未知 `.so`：

```text
FAIL until provenance verified
```

继续验证：

```text
mGBA upstream unchanged
Oboe upstream unchanged
```

License audit：

```text
own Apache-2.0
mGBA MPL-2.0
Oboe
AndroidX
shader attribution
test ROM licenses
```

更新：

```text
THIRD_PARTY_ROM_LICENSES.md
```

记录：

```text
source
revision
license
redistribution
CI eligibility
```

---

# 40. Lint Warnings

历史约 20 warnings。

分类：

```text
toolchain version notice
safe-to-ignore
actionable
potential bug
```

后两类：

```text
fix
or documented justification
```

不要求为了 0 warning 强行升级工具链。

---

# 41. Main-thread IO Audit

确认以下不在 UI thread：

```text
ROM hash
ZIP parsing
thumbnail decode
DataStore
Room heavy operations
screenshot encoding
State IO
```

---

# 42. StrictMode

Debug 可启用受控 StrictMode 检查：

```text
disk IO on main
network
resource misuse
```

不得让 Release 因 StrictMode 崩溃。

---

# 43. ANR Stress

组合：

```text
slow SAF provider
orientation
background
large valid ZIP
1000 library rows
```

检查主线程响应。

---

# 44. Slow Provider

继续 Phase 6 slow-provider 路径，并增加：

```text
cancel
Activity recreate
background
```

取消后：

```text
temp cleanup
no partial Room row
no orphan snapshot
```

---

# 45. Crash Recovery / Temp Cleanup

受控中断：

```text
ROM temp
state temp
screenshot temp
thumbnail temp
```

下一次启动不得误认 temp 为正式数据。

只能清：

```text
own unreferenced temp
```

不得误删合法旧 generation。

---

# 46. Low Memory

模拟：

```text
onTrimMemory
```

验证：

```text
rewind cache shrink/clear
thumbnail cache trim
core remains valid
save remains intact
```

---

# 47. Process Recreate

覆盖：

```text
Activity recreate
process death
cold restart
```

验证：

```text
Library
LastSession
Quick Resume
settings
```

---

# 48. Battery / Thermal

可记录：

```text
60 min battery delta
thermal status
```

必须注明：

```text
screen brightness
background services
environmental interference
```

不得称为：

```text
thermal certification
```

---

# 49. Crash Collection

继续：

```text
no network telemetry
```

允许 Debug：

```text
logcat
native tombstone
local evidence
```

禁止：

```text
Crashlytics
Sentry
remote telemetry
```

---

# 50. Remote CI

Phase 7A 必须真正运行至少一次：

```text
remote CI
```

最低：

```text
JVM
host native
lint
Debug build
unsigned Release build
dependency verification
license/provenance audit
ROM manifest validation
```

Instrumentation 如环境稳定则加入。

固定：

```text
JDK
Gradle
NDK
CMake
dependencies
test ROM revisions
```

禁止：

```text
latest
master without pin
```

---

# 51. Runtime 网络与 CI 网络

必须区分：

```text
App Runtime = No INTERNET
```

与：

```text
Developer / CI may fetch pinned dependencies/test sources
```

---

# 52. Build Reproducibility

至少两次 clean Release 比较：

```text
dependency set
manifest
native .so hashes
resources
```

如果 APK 不 bit-identical：

```text
说明原因
```

---

# 53. APK Audit

最终检查：

```text
permissions
activities
providers
services
receivers
native libs
assets
ROM absence
BIOS absence
network SDK absence
test class absence
debug-only code absence
```

Production APK 默认：

```text
no .gba
no BIOS
```

---

# 54. Privacy Audit

确认：

```text
no network
no analytics
no ad id
no account
no telemetry
no external logging
```

---

# 55. Manual Phase 7A Acceptance

用户最终至少验证：

```text
normal game launch
Continue
Battery
Quick/Slot
FF
Rewind
Touch
Bluetooth controller
4 display modes
orientation
background
Library
ZIP import
RTC
Tilt
Gyro
Solar
```

Manual long-run：

```text
30–60 min real gameplay
```

确认：

```text
无明显性能恶化
无持续杂音
无黑屏
无粘键
无存档丢失
```

---

# 56. 历史风险

保留：

```text
Phase 3 historical black screen
Phase 4 GLSurfaceView ANR
SAF screenshot/window intermittent dark/red issue
device lock/doze interference
single-device coverage
```

只有：

```text
明确根因
+ 修复
+ targeted regression
```

才能写：

```text
ROOT CAUSE FIXED
```

否则：

```text
not reproduced
mitigated
monitored
```

ASan historical blocker 交给 7B。

---

# 57. 性能回归

不追求比 Phase 6 更高 FPS。

目标：

```text
no meaningful regression
```

至少记录：

```text
1×
2×
4×
8×
```

并覆盖四种显示模式。

---

# 58. Evidence

保存到：

```text
docs/reports/evidence/phase7/
```

Phase 7A 至少包括：

```text
baseline
build logs
test summaries
compatibility matrix
long-run metrics
memory metrics
FD metrics
thread metrics
upgrade results
dependency list
license audit
CI result
APK audit
SHA-256
manual acceptance
```

不得伪造：

```text
multi-device
remote CI
2h long run
commercial ROM
USB HID
```

除非真正执行。

---

# 59. Phase 7A 自动验证最低门槛

至少：

```text
JVM full suite
Host native full suite
JNI normal regression suite
Activity full suite
long-run
resource leak stress
orientation/background stress
renderer stress
audio/input regression
save/state/autosave stress
upgrade tests
remote CI
dependency/license/provenance audit
APK audit
```

注意：

```text
UBSan
ASan
fuzz
malformed attack fixtures
```

不属于 7A，全部交给 7B。

---

# 60. Phase 7A Hard Blockers

以下任一出现：

```text
save corruption
cross-game save
repeatable black screen
repeatable ANR
native crash during normal valid usage
destructive migration
permission regression
INTERNET
unknown .so
test ROM in production APK
upgrade data loss
persistent audio leak
persistent input stuck
linear FD leak
linear thread leak
linear GL resource leak
```

必须：

```text
Phase 7A = NOT READY
```

7B 专属 blocker 不在这里重复判断。

---

# 61. Phase 7A 报告

生成：

```text
docs/reports/PHASE_7A_COMPATIBILITY_STABILITY_RELEASE_QA_REPORT.md
```

至少包含：

```text
A. Baseline
B. Changed Code
C. Compatibility Matrix
D. Test ROM Corpus
E. Android API Matrix
F. Multi-device Results
G. Long-run Results
H. Memory / Heap
I. FD / Thread
J. Lifecycle Stress
K. Renderer Stress
L. Audio
M. Input
N. Sensors / RTC
O. Library
P. Persistence
Q. Room / DataStore Upgrade
R. Install / Upgrade
S. Dependency / License / Provenance
T. CI
U. APK Audit
V. Privacy Audit
W. Manual Acceptance
X. Known Issues
Y. Risks
Z. Unfinished
AA. Phase 7A Decision
```

---

# 62. Phase 7A 最终状态

全部通过后只能写：

```text
Phase 7A — PASS / READY_FOR_7B
```

并明确：

```text
Phase 7 — NOT READY
原因：Phase 7B Defensive Robustness Validation 尚未完成
```

不得提前进入 Phase 8。

---

# 63. Codex 执行指令

现在开始执行。

要求：

```text
不要只输出计划
不要只审阅文档
不要执行 7B 的 fuzz / ASan / 攻击性 malformed-input 专项
不要因为 7B 尚未完成而停止 7A
```

在当前项目范围内：

```text
读取代码
运行正常构建与测试
修复普通兼容性/稳定性问题
增加正常回归
保存证据
更新报告
```

持续执行直到：

```text
A. Phase 7A = PASS / READY_FOR_7B
```

或者：

```text
B. 出现 Phase 7A hard blocker
   且其余可安全执行工作已经完成并记录
```

---

# 64. 最终提醒

Phase 7A 的核心是：

> **先把与网络安全分类无关的兼容性、稳定性、资源、生命周期、数据兼容、CI 和发布前 QA 全部做完。**

不要在 Phase 7A 中执行 Phase 7B 专属测试。
