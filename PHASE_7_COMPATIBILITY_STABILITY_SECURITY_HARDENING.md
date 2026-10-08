# GBA Lite — Phase 7 Compatibility / Stability / Security Hardening 实施任务书

**文档版本：** v0.1  
**执行范围：** Phase 7 — Compatibility / Stability / Security Hardening  
**执行者：** Codex / 开发代理  
**最高约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`

**前置状态：**
- Phase 0/1 = READY
- Phase 2 = READY
- Phase 3 = READY
- Phase 4 = READY
- Phase 5 = READY
- Phase 6 = READY
- Phase 7 = NOT STARTED
- V1 Release = NOT READY

---

## 1. 本轮目标

Phase 7 不再增加面向用户的新功能。

本阶段只解决：

> **兼容性、稳定性、安全性、长期运行、异常输入、资源泄漏和发布前风险。**

目标是把项目从：

```text
功能完整
```

提升为：

```text
具备进入 Release Candidate / Signing 的工程条件
```

---

## 2. 核心原则

```text
No New Features
Fix / Harden / Measure / Verify
```

禁止因为测试失败而：

```text
降低断言
关闭测试
放宽安全限制
绕过异常
删除失败证据
```

正确流程：

```text
复现
→ 定位
→ 修复
→ 增加针对性回归
→ 保留历史失败
```

---

## 3. 必须保持的 V1 功能基线

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
V1 zero-permission policy
```

不得为了 Phase 7 添加 `VIBRATE`。

---

## 4. 权限基线

目标继续：

```text
Debug permissions = []
Release permissions = []
```

禁止新增：

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

## 5. Phase 7 禁止事项

不实现：

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

## 6. 成功定义

成功不是“测试数量更多”。

成功是：

> **明确哪些环境验证过、哪些没有；所有高风险路径都有证据、边界和回归；不存在明显会阻止 V1 Release Candidate 的稳定性或安全问题。**

---

## 7. 执行顺序

严格按：

```text
1. Baseline Freeze
2. Test Corpus
3. Multi-ROM Compatibility
4. Android API Compatibility
5. Long Run
6. Memory / FD / Thread Leak
7. Lifecycle Stress
8. Audio Hardening
9. Input Hardening
10. Renderer Hardening
11. Persistence Hardening
12. ROM / ZIP Security
13. Sanitizers / Fuzz
14. Install / Upgrade
15. Dependency / License / Provenance
16. CI
17. Final Audit
18. Manual Acceptance
```

---

## 8. Baseline Freeze

Phase 7 开始前记录：

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

建议：

```text
0.7.0 / versionCode 7
```

不得擅自升级冻结工具链。

---

## 9. ADR 规则

默认不修改：

```text
PROJECT_SPEC.md
ARCHITECTURE.md
```

如果发现必须改变架构或产品安全边界：

> 先写 ADR，再实施。

---

## 10. GBA_TEST_ROM_CORPUS

Phase 7 必须正式执行 `GBA_TEST_ROM_CORPUS.md`。

Tier：

```text
A — repo / CI safe
B — pinned fetch/build
C — local manual
D — user-owned commercial ROM
```

自动流程仅允许：

```text
Tier A
已完成许可审计的 Tier B
```

---

## 11. 自动 ROM 最低集合

至少：

```text
Internal bringup
Internal persistence
Internal color-pattern
Internal lcd-pattern
Internal peripherals
Selected mGBA test suite
```

如许可已完成，再加入：

```text
NanoBoyAdvance hw-test
FuzzARM
160p Test Suite
```

---

## 12. Homebrew Manual Corpus

至少选择若干合法 Homebrew，覆盖：

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

## 13. Commercial ROM 边界

允许用户本地私有验证：

```text
user-owned ROM
```

禁止：

```text
上传
提交
打包
复制进 test assets
建立公开截图 corpus
```

报告仅写：

```text
local user-owned commercial ROM manual compatibility
```

---

## 14. Compatibility Matrix

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

## 15. Android API Matrix

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

可以使用：

```text
Emulator + real device
```

组合。

每个代表 API 至少：

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

---

## 16. Android 版本差异重点

专项检查：

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

## 17. 第二设备

如能获得第二台 Android 实机：

优先不同 OEM。

例如：

```text
Samsung
Pixel
Xiaomi
vivo
OnePlus
```

若没有：

```text
NOT TESTED
```

不得伪造多设备覆盖。

---

## 18. 长时间稳定性

必须至少：

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

推荐再增加：

```text
2–4 hours
```

但不强制。

---

## 19. 长测指标

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

## 20. Core / Session Leak Stress

执行：

```text
open game
exit
open next game
```

至少 20–50 次。

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

---

## 21. Orientation Stress

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

## 22. Background Stress

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

## 23. ROM Switch Stress

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

要求：

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

## 24. Save State Stress

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

## 25. Autosave Stress

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

## 26. Sudden Process Death

测试：

```text
gameplay
→ force-stop / kill process
→ relaunch
```

只验证既有保证：

```text
last safe point
```

不得声称：

```text
last-frame perfect
```

---

## 27. Storage / IO Failure

通过测试替身或受控环境验证：

```text
write fail
fsync fail
rename fail
disk full
permission revoked
read truncation
```

必须：

```text
fail closed
preserve prior valid generation
```

---

## 28. FD Leak

循环：

```text
import
play
save
screenshot
state
close
```

至少 100 次。

记录：

```text
baseline
peak
final
```

不得线性增长。

---

## 29. Thread Leak

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

反复游戏启动/关闭后：

```text
thread count returns near baseline
```

---

## 30. JNI Ownership

审计：

```text
native core
callbacks
registry ownership
VFile
state buffers
audio buffers
frame buffers
sensor callback objects
```

目标：

```text
no UAF
no double-free
no stale handle
```

---

## 31. Renderer Hardening

继续回归历史风险：

```text
Phase 3 black screen
Phase 4 GLSurfaceView ANR
TextureView lifecycle
eglSwapBuffers
context recreation
```

新增：

```text
orientation stress
background stress
shader switch stress
```

---

## 32. Shader Stress

循环：

```text
Original
Sharp
GBA Color
LCD
```

至少 100 次。

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

## 33. Audio Hardening

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

无法验证的物理路径标记：

```text
NOT TESTED
```

---

## 34. Audio Underrun

目标不是绝对 0。

关注：

```text
persistent audible failure
runaway underrun
permanent silence
duplicate stream
```

记录：

```text
1× long run
FF→1×
Home→foreground
rewind
state load
```

---

## 35. Input Hardening

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

## 36. HID Matrix

如有真实设备：

```text
Bluetooth controller
USB controller
```

没有 USB 实物：

```text
NOT TESTED
```

合成事件不能冒充物理 USB 验收。

---

## 37. Sensor Hardening

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

---

## 38. RTC Long-run

至少：

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

## 39. Library Hardening

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

## 40. Thumbnail Stress

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

## 41. ZIP Security Regression

Phase 6 全部 fixture 必须继续通过：

```text
traversal
absolute path
drive path
duplicate name
case collision
unicode normalization
zip bomb
huge ratio
too many entries
encrypted
ZIP64
nested zip
corrupt central directory
truncated
CRC mismatch
```

---

## 42. Malformed GBA Corpus

新增：

```text
0 byte
1 byte
191 byte
random 192 byte
oversized >32 MiB
bad header
truncated
all 0x00
all 0xFF
random content
```

不得出现：

```text
native crash
OOM
ANR
```

---

## 43. Native ROM Fuzz

至少建立一个：

```text
bounded ROM parser fuzz harness
```

目标路径：

```text
ROM open
hardware detection
VFile
```

优先：

```text
libFuzzer / clang fuzz target
```

若 Windows 工具链阻塞：

```text
Linux CI / WSL
```

或明确记录 BLOCKED。

---

## 44. ZIP Fuzz

对纯 JVM ZIP parser 做 mutation corpus。

覆盖：

```text
central directory
sizes
flags
names
CRC
descriptor
```

记录：

```text
duration
iterations
corpus
seed
crashes
timeouts
OOM
```

如果只运行 deterministic malformed fixtures：

> 不得写 “fuzz passed”。

---

## 45. UBSan

必须重跑：

```text
JNI full
Activity full
ROM corpus subset
save/load
rewind
orientation
```

---

## 46. ASan

再次解决此前 ASan blocker。

优先路径：

```text
Android ASan / HWASan
Linux host ASan
WSL host ASan
```

至少尝试一种真正可执行路径。

只有实际执行成功才允许：

```text
ASan PASS
```

否则：

```text
ASan BLOCKED
```

或：

```text
ASan NOT RUN
```

---

## 47. Native Hardening

重新检查 Release ELF：

```text
RELRO
BIND_NOW
NX GNU_STACK
stack protector
FORTIFY if applicable
PIE
```

---

## 48. Integer / Size Audit

专项审计：

```text
size_t
uint32_t
int
JNI jsize
file length
state size
ZIP size
frame size
```

防止：

```text
overflow
truncation
negative size
signed/unsigned mismatch
```

---

## 49. Path Safety

所有 App-private 路径：

```text
ROM
Save
State
Screenshot
Thumbnail
Temp
```

必须：

```text
derived from controlled identifiers
not arbitrary user path
```

---

## 50. Metadata Robustness

损坏：

```text
Room row
JSON metadata
DataStore
current.json
thumbnail
```

必须：

```text
fail safely
fallback when possible
preserve valid data
```

---

## 51. State Compatibility

验证历史阶段生成的 State：

```text
Phase 2
Phase 3
Phase 4
Phase 5
Phase 6
```

Phase 7 均可读取。

推荐固定合法 State fixtures。

---

## 52. Save Compatibility

固定：

```text
test-save-v1.sav
```

SHA-256 必须保持不变。

继续验证：

```text
battery import/export
backup fallback
ROM rename
reimport
Library remove/readd
```

---

## 53. Room Migration

必须验证：

```text
schema 1 → current
schema 2 → current
schema 3 → current
```

禁止 destructive migration。

---

## 54. DataStore Compatibility

验证：

```text
display v1
display v2
peripherals v1
```

读取当前版本。

未知版本：

```text
safe fallback
```

---

## 55. Install / Upgrade Matrix

必须验证：

```text
fresh install Phase 7 Debug
Phase 6 Debug → Phase 7 Debug
```

如历史 APK 可用，推荐再加：

```text
Phase 2 → Phase 7
Phase 4 → Phase 7
```

---

## 56. Upgrade 数据验证

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

## 57. Production Signing

Phase 7 仍保持：

```text
Release unsigned
```

不得创建正式生产 keystore。

正式 Signing 属于：

```text
Phase 8
```

---

## 58. Dependency Audit

重新生成：

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

---

## 59. Native Provenance

APK 中所有 `.so` 必须可解释。

允许：

```text
own libgba_bridge.so
pinned official AndroidX native
```

任何新增 `.so`：

```text
FAIL until provenance verified
```

---

## 60. Upstream Integrity

继续验证：

```text
mGBA upstream unchanged
Oboe upstream unchanged
```

默认不 vendor patch。

---

## 61. License Audit

确认：

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

## 62. Lint Warnings

当前历史约 20 warnings。

Phase 7 不要求为 0 warning 强行升级。

必须分类：

```text
toolchain version notice
safe-to-ignore
actionable
potential bug
```

后两类必须：

```text
fix
or documented justification
```

---

## 63. Main-thread IO Audit

专项确认以下不在 UI thread：

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

## 64. StrictMode

Debug 可启用受控 StrictMode，用于发现：

```text
disk IO on main
network
resource misuse
```

不得让 Release 因 StrictMode 崩溃。

---

## 65. ANR Stress

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

## 66. Slow Provider

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

## 67. Crash Recovery / Temp Cleanup

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

## 68. Low Memory

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

## 69. Process Recreate

分别覆盖：

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

## 70. Battery / Thermal

可以记录：

```text
60 min battery delta
thermal status
```

但必须注明系统屏幕亮度、后台服务等干扰。

不得称为：

```text
thermal certification
```

---

## 71. Crash Collection

继续保持无联网 Telemetry。

允许 Debug：

```text
logcat
native tombstone
local evidence
```

禁止新增：

```text
Crashlytics
Sentry
remote telemetry
```

---

## 72. Remote CI

Phase 7 必须真正运行至少一次：

```text
remote CI
```

不能只说：

```text
CI config exists
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

---

## 73. CI Reproducibility

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

## 74. Runtime 网络与 CI 网络

必须区分：

```text
App Runtime = No INTERNET
```

与：

```text
Developer / CI may fetch pinned dependencies/test sources
```

两者不能混淆。

---

## 75. Build Reproducibility

至少两次 clean Release 比较：

```text
dependency set
manifest
native .so hashes
resources
```

如果 APK 本身不 bit-identical：

说明原因。

---

## 76. APK Audit

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

## 77. Privacy Audit

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

## 78. Security Fail-Closed

以下解析遇到不确定：

```text
ROM
ZIP
State
Battery
Metadata
Settings
```

必须：

```text
reject / safe fallback
```

禁止带风险继续写入。

---

## 79. Manual Phase 7 Acceptance

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

目标是确认 Hardening 没有破坏核心体验。

---

## 80. Manual Long-run

用户至少：

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

## 81. 历史风险必须保留

包括：

```text
Phase 3 historical black screen
Phase 4 GLSurfaceView ANR
SAF screenshot/window intermittent dark/red issue
device lock/doze interference
ASan historical blocker
single-device coverage
```

只有：

```text
明确根因
+ 修复
+ targeted regression
```

才允许写：

```text
ROOT CAUSE FIXED
```

否则写：

```text
not reproduced
mitigated
monitored
```

---

## 82. 自动验证最低门槛

至少：

```text
JVM full suite
Host native full suite
JNI full suite
Activity full suite
UBSan JNI
UBSan Activity
ASan or explicit BLOCKED
ZIP security fixtures
Malformed ROM fixtures
long-run
resource leak stress
upgrade tests
remote CI
```

---

## 83. 性能回归

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

## 84. Phase 7 报告

最终必须生成：

```text
docs/reports/PHASE_7_COMPATIBILITY_STABILITY_SECURITY_REPORT.md
```

---

## 85. 报告章节

必须包含：

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
O. Library / ZIP
P. Persistence
Q. Room / DataStore Upgrade
R. Malformed ROM / ZIP
S. Fuzz
T. UBSan
U. ASan
V. Native Hardening
W. Dependency / License / Provenance
X. CI
Y. APK Audit
Z. Manual Acceptance
AA. Known Issues
AB. Risks
AC. Unfinished
AD. 是否允许进入 Phase 8
```

---

## 86. Evidence

所有 Phase 7 证据：

```text
docs/reports/evidence/phase7/
```

至少保存：

```text
build logs
test summaries
sanitizer logs
compatibility matrix
long-run metrics
memory metrics
FD metrics
thread metrics
APK audit
dependency list
license audit
CI result
SHA-256
```

---

## 87. 不得伪造

不得声称：

```text
multi-device
ASan
fuzz
remote CI
2h long run
commercial ROM
USB HID
```

除非真正执行。

---

## 88. Phase 8 硬阻断条件

任一出现：

```text
save corruption
cross-game save
repeatable black screen
repeatable ANR
native crash
UAF
double free
OOM on bounded malicious input
ZIP traversal
ZIP bomb bypass
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
Phase 7 = NOT READY
```

---

## 89. 可接受非阻断项

Phase 7 可保留：

```text
Release unsigned
no production keystore
no AAB
no Play Store upload
Rumble physical output deferred
some justified lint warnings
not every OEM tested
not every GBA ROM tested
```

这些不阻止进入 Phase 8。

---

## 90. Git 提交建议

```text
test: expand gba compatibility corpus
test: add malformed rom fixtures
test: expand zip security regression
test: add lifecycle stress coverage
test: add renderer stress coverage
test: add persistence stress coverage
test: add upgrade migration coverage
test: add resource leak metrics
test: add long-run validation
test: add sanitizer workflows
ci: run pinned phase 7 verification
chore: tighten dependency provenance audit
fix: resolve phase 7 hardening findings
docs: add compatibility matrix
docs: add phase 7 hardening report
```

不要把整个 Phase 7 压成一个 commit。

---

## 91. Codex 阅读顺序

```text
PROJECT_SPEC.md
→ ARCHITECTURE.md
→ PHASE_0_1_REPORT.md
→ PHASE_2_PERSISTENCE_REPORT.md
→ PHASE_3_PLAYER_EXPERIENCE_REPORT.md
→ PHASE_4_RENDERER_VISUAL_EXPERIENCE_REPORT.md
→ PHASE_5_GBA_HARDWARE_SENSORS_REPORT.md
→ PHASE_6_LIBRARY_APP_EXPERIENCE_REPORT.md
→ GBA_TEST_ROM_CORPUS.md
→ PHASE_7_COMPATIBILITY_STABILITY_SECURITY_HARDENING.md
```

---

## 92. Phase 6 状态收口

如果当前 Phase 6 报告仍写：

```text
NOT READY
```

但用户已经明确确认最终人工验收全部通过，则 Phase 7 开始前：

> 只更新 Phase 6 报告为 READY。

不要修改生产代码，也不要重新解释历史自动测试。

---

## 93. Codex 强约束

```text
只实施 Phase 7
不提前 Phase 8
不新增用户功能
不升级 mGBA
不升级冻结工具链
不增加权限
不加 INTERNET
不加 Ads / Analytics / Crashlytics
不增加新 Core
不修改 Game ID
不修改 Save identity
不 destructive migration
不复制商业 ROM
不打包 BIOS
```

---

## 94. 最终状态

全部硬门槛通过后：

```text
Phase 0 — PASS
Phase 1 — PASS
Phase 2 — PASS
Phase 3 — PASS
Phase 4 — PASS
Phase 5 — PASS
Phase 6 — PASS
Phase 7 — PASS / READY

Phase 8 — NOT STARTED
V1 Release — NOT READY
```

之后才允许进入：

```text
Phase 8 — Release Candidate / Signing / V1.0
```

---

## 95. Phase 7 最终成功路径

```text
冻结基线
↓
扩展合法 ROM corpus
↓
Android API / device compatibility
↓
60min+ long run
↓
memory / FD / thread stress
↓
renderer / audio / input / sensor stress
↓
persistence / migration / upgrade
↓
malformed ROM / ZIP
↓
UBSan / ASan / fuzz
↓
remote CI
↓
APK / license / provenance audit
↓
人工稳定性验收
↓
READY
```

Phase 7 的核心不是“功能更多”，而是：

> **让 Phase 8 可以安全地做正式签名、Release Candidate 和 V1.0。**
