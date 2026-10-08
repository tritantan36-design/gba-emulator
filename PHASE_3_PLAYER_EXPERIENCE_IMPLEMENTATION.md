# GBA Lite — Phase 3 Player Experience 实施任务书

**文档版本：** v0.1  
**执行范围：** Phase 3 — Player Experience  
**执行者：** Codex / 开发代理  
**最高约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`  
**前置验收：** `docs/reports/PHASE_2_PERSISTENCE_REPORT.md` 必须为 `READY`

---

## 1. 本轮目标

本轮目标：

> 让已经具备稳定运行和可靠存档能力的 GBA Lite，真正具备“玩起来舒服”的播放器体验。

核心体验链路：

```text
启动游戏
→ 顺手操控
→ 需要时快进
→ 操作失误时倒带
→ 横竖屏都舒服
→ 触控与手柄都稳定
→ Save State 一眼可识别
→ 退出/恢复仍保持 Phase 2 的可靠性
```

---

## 2. 必须保留的既有基线

不得破坏：

```text
SAF ROM
Game ID
Session / Core API
mGBA
GLES
Oboe
InputRouter
SRAM/FLASH/EEPROM persistence
4 Save State
Quick Save/Load
A/B/C Autosave
Quick Resume
Room metadata
Atomic persistence
```

仍必须保持：

```text
0 Android runtime permissions
No INTERNET
No Analytics
No Ads
No Firebase
No unknown emulator binary
```

不得擅自升级：

- mGBA
- Oboe
- Kotlin
- AGP
- Gradle
- NDK
- CMake
- Compose BOM

如确有阻塞，先新增 ADR。

---

## 3. 竞品参考原则

允许参考 My Boy 与 Pizza Boy A Pro 的公开功能和可观察交互行为，但只作为体验标杆。

### My Boy 重点参考

```text
快速操作路径
布局 Profile
横竖屏独立布局
硬件手柄
简洁游戏内操作
```

### Pizza Boy 重点参考

```text
Rewind
Fast Forward
触控自定义
Save/Auto Save 入口
现代 Player UI
```

禁止：

- 复制反编译代码
- 复制 proprietary native 实现
- 复制 UI 资源
- 复制图标、皮肤、素材
- 引入闭源二进制
- 猜测闭源内部实现并当成事实

---

## 4. 本轮必须完成

```text
Fast Forward
Rewind
Save State thumbnail
Screenshot
Portrait layout
Landscape layout
Touch layout customization
Multi-touch correctness
USB gamepad
Bluetooth HID gamepad
Default controller mapping
Input profile
Player / Pause UI
```

---

## 5. 本轮禁止提前实现

```text
Sharp Shader
GBA Color
LCD Shader
RTC
Tilt
Gyro
Solar
Rumble
Cheats
Patch
Local Link
Network Link
SkyEmu
完整 Library 重构
封面下载
RetroAchievements
Cloud Sync
账号
联网功能
```

---

# 6. Fast Forward

支持：

```text
1×
2×
4×
8×
```

必须支持：

### Hold

```text
按住 → Fast Forward → 松开 → 恢复 1×
```

### Toggle

```text
点击 → 进入指定倍率 → 再次点击 → 恢复 1×
```

实现必须基于：

```text
Emulation pacing
→ 解除/调整 normal frame pacing
→ Core 更快运行
→ Renderer 可丢弃部分中间帧
```

禁止用简单修改 `sleep()` 伪造倍率。

### 音频

稳定优先。

优先允许：

```text
Fast Forward 时静音
```

不要为了变速音频引入复杂 DSP。

### 性能目标

```text
2× 必须稳定
4× 主流目标设备应稳定
8× Best Effort
```

不能出现：

```text
UI 卡死
audio queue runaway
持续内存增长
无法回到 1×
```

---

# 7. Rewind

产品交互：

```text
长按 Rewind
→ 游戏持续回退
→ 松开
→ 从当前位置继续
```

不要把 Rewind 做成“打开菜单选择旧 State”。

### 实现方向

```text
mGBA State
↓
内存 Ring Buffer
```

定期捕获：

```text
100–250 ms / snapshot
```

最终间隔通过测试决定。

目标时长：

```text
15–30 秒
```

必须有硬内存上限，建议初始：

```text
32–96 MiB
```

不得无限增长。

### 关键安全要求

Rewind：

```text
只存在内存
```

不得写入正式磁盘 State。

不得污染：

```text
Manual State
Quick Save
Autosave
Battery Save
```

必须保持：

> State ≠ Battery Save

Rewind 恢复旧状态时，不允许把旧 battery 数据覆盖 Phase 2 的正常 `.sav`。

### 音频

Rewind 时优先：

```text
mute
```

松开后：

```text
clear stale audio queue
→ resume current state
```

---

# 8. Save State Thumbnail

Phase 2 已预留 `StateScreenshotWriter`，Phase 3 必须实现。

至少 Manual Slot 1–4 显示：

```text
Screenshot
Date
Time
```

流程：

```text
state capture point
→ current FrameSource
→ encode WebP
→ slot thumbnail
```

截图失败：

> 不得导致 State 保存失败。

建议文件：

```text
slot-1.webp
```

不写 EXIF，不记录位置，不增加网络依赖。

---

# 9. 独立 Screenshot

必须提供截图功能。

截图来源：

```text
当前 emulated framebuffer
```

不得截图整个 Android UI。

优先存储：

```text
App private screenshots/
```

如使用 MediaStore：

- 不得增加旧式 storage permission
- 必须记录 ADR 或实现说明

---

# 10. Portrait Layout

必须有真正可用的默认 Portrait 布局。

示意：

```text
┌─────────────────────┐
│                     │
│      GBA Screen     │
│                     │
├─────────────────────┤
│ D-pad         A  B  │
│                     │
│ L   Start Select  R │
└─────────────────────┘
```

要求：

- 默认无需调整即可玩
- 不挡画面
- 适配安全区
- 不贴系统返回手势边缘过近

---

# 11. Landscape Layout

必须有独立 Landscape Profile。

不得简单旋转 Portrait 坐标。

示意：

```text
┌─────────────────────────────┐
│        GBA SCREEN           │
│                             │
│ D-pad                  A B  │
│ L     Select Start       R  │
└─────────────────────────────┘
```

---

# 12. Touch Layout Profile

建议：

```text
InputProfile
├── orientation
├── dpad
├── a
├── b
├── l
├── r
├── start
├── select
├── opacity
└── scale
```

至少：

```text
Portrait Profile
Landscape Profile
```

---

# 13. Touch 自定义

支持：

```text
移动
大小
透明度
```

流程可以保持极简：

```text
调整按键
→ 拖动 / 缩放
→ 保存
```

不要建立复杂编辑器。

最重要原则：

> 默认布局必须好用，自定义只是增强，不是补救。

---

# 14. D-pad

必须专项处理：

```text
UP
DOWN
LEFT
RIGHT
diagonal
slide
dead zone
```

例如：

```text
UP → RIGHT
```

手指滑动时应自然转换，不要求抬手再按。

Dead zone 与 D-pad 尺寸相关，不能写死 px。

---

# 15. Multi-touch

必须支持：

```text
方向 + A
方向 + B
方向 + A + B
L + A
R + B
```

每个 pointer 独立跟踪。

禁止单 active touch 简化实现。

以下情况必须释放全部逻辑按键：

```text
Activity pause
Surface destroy
Touch cancel
Pointer lost
Player exit
```

避免 stuck button。

---

# 16. Haptic

本阶段不要求触控震动。

默认：

```text
不新增 VIBRATE
```

继续优先保持：

```text
0 permissions
```

---

# 17. Gamepad

必须正式支持：

```text
USB HID
已由系统连接的 Bluetooth HID
```

通过：

```text
InputDevice
KeyEvent
MotionEvent
```

App 不负责扫描、配对、连接 Bluetooth。

因此：

> 不申请 `BLUETOOTH_CONNECT`。

---

# 18. 默认 Gamepad 映射

建议：

```text
D-pad / Left Stick → GBA D-pad
South → A
West → B
L1 → L
R1 → R
Start → Start
Select / Back → Select
```

实际按键必须通过逻辑 Mapping 层处理。

Analog Stick：

```text
Left Stick → Digital D-pad
```

需要：

```text
dead zone
axis threshold
```

初始 threshold 可约 0.5，最终通过测试确定。

---

# 19. Gamepad Reconnect

至少验证：

```text
游戏中断开
→ 触控仍可操作
→ 重新连接
→ 手柄恢复
```

不得：

```text
Crash
按键卡住
Session 锁死
```

---

# 20. InputRouter

所有输入继续走：

```text
Touch
Gamepad
Keyboard
↓
InputRouter
↓
EmulatorSession
↓
EmulatorCore
```

禁止：

```text
Player UI → JNI
Gamepad handler → JNI
```

---

# 21. Player UI

目标：

> 极简、低干扰。

正常游戏时默认只显示：

```text
游戏画面
+
触控按键
```

只允许一个克制的 Menu 入口。

不要加入多个长期悬浮按钮。

---

# 22. Pause Menu

建议：

```text
继续

即时存档
读取存档

Quick Save
Quick Load

快进
倒带

截图

控制
设置

退出游戏
```

不要暴露：

```text
Core version
Frame skip internals
Audio buffer size
Renderer debug
```

---

# 23. Orientation

必须支持：

```text
Portrait
Landscape
```

要求：

- 切换方向不重载 ROM
- 不丢 Session
- 不丢输入状态
- Renderer Surface 正确重建
- Audio 不重复创建
- Persistence 不受破坏

继续使用既有 ViewModel / Session 生命周期。

禁止用：

```text
global static native core
```

---

# 24. Insets / 安全区

必须处理：

```text
notch
hole punch
gesture navigation
navigation bar
status bar
```

默认按钮不能落入高风险系统手势区。

---

# 25. Frame Pacing Debug Metrics

允许 Debug-only：

```text
FPS
frame time
dropped frames
late frames
actual FF multiplier
rewind buffer seconds
rewind memory MB
audio underrun
controller connected
```

Release 不显示，不联网。

---

# 26. Low Memory

收到 `onTrimMemory` 时可以：

```text
缩短 Rewind Buffer
```

不得：

- 清正常 Save
- 删除 Manual State
- 破坏当前 Session

---

# 27. Persistence 回归要求

Phase 3 必须继续完整跑 Phase 2：

```text
Game ID
Battery Save
Atomic Write
Backup fallback
Manual State
Quick Save/Load
Autosave A/B/C
Quick Resume
Room migration
```

任何 Phase 3 功能不得改变 Phase 2 存档安全模型。

---

# 28. 必须新增测试

## Fast Forward

```text
1× → 2× → 1×
1× → 4× → 1×
8× request
hold
toggle
background during FF
exit during FF
```

## Rewind

```text
capture ring
rewind 5 sec
release and resume
buffer wrap
memory cap
background
close
```

必须验证无 battery corruption。

## Thumbnail

```text
Save Slot 1
→ thumbnail exists
→ matches save time
```

截图失败时 State 必须仍成功。

## Screenshot

```text
file exists
resolution correct
no Android control overlay
```

## Multi-touch

```text
RIGHT + A
LEFT + B
UP + A + B
L + A
R + B
```

## D-pad

```text
UP → RIGHT slide
LEFT → DOWN slide
center dead zone
diagonal
pointer cancel
```

## Gamepad

```text
USB
Bluetooth HID InputDevice
D-pad
Analog
A/B
L/R
Start/Select
disconnect
reconnect
```

## Orientation

```text
Portrait → Landscape
Landscape → Portrait
normal play
paused
after Quick Save
after Autosave
```

---

# 29. Native Tests

至少增加：

```text
fast-forward pacing controls
rewind capture/restore loop
repeated capture/free
close during rewind
```

即使使用 mGBA 官方 State API，也必须测试 wrapper 所有权。

---

# 30. UBSan / ASan

Phase 3 至少再次运行 UBSan 覆盖：

```text
normal
FF
rewind
state thumbnail
close
```

如果 ASan 环境仍不可用：

保留风险，不能记 PASS。

---

# 31. 长时间体验测试

建议加入：

```text
30–60 min
```

过程中至少：

```text
正常 1×
周期 Save
多次 FF
多次 Rewind
Orientation change
State Save/Load
Background/Foreground
```

观察：

```text
memory
audio
frame pacing
native handles
```

---

# 32. Manifest 审计

目标继续是：

```text
Debug permissions = []
Release permissions = []
```

Gamepad 不得引入 Bluetooth permission。

Screenshot 不得引入旧式 Storage permission。

---

# 33. Dependency Audit

默认不需要大量新依赖。

继续禁止：

```text
Firebase
Ads
Analytics
OkHttp
Retrofit
online SDK
```

---

# 34. 人工 UI 验收

人工验收必须关注：

```text
拇指能否自然够到
D-pad 是否误触
A/B 是否过近
L/R 是否难按
横屏是否自然
Menu 是否碍眼
FF/Rewind 是否顺手
```

不是只验证“按钮能点”。

---

# 35. Phase 3 验收标准

## Fast Forward

- [ ] 1× / 2× / 4× / 8×
- [ ] Hold
- [ ] Toggle
- [ ] 可稳定返回 1×
- [ ] 无 audio queue runaway

## Rewind

- [ ] 内存 Ring Buffer
- [ ] 长按 Rewind
- [ ] 松开继续
- [ ] 默认至少约 15 秒
- [ ] 硬内存上限
- [ ] 不写正式 Rewind 文件
- [ ] 不破坏 battery

## Save UX

- [ ] Manual State thumbnail
- [ ] Thumbnail 失败不影响 State
- [ ] Quick Save/Load 入口可用

## Screenshot

- [ ] framebuffer Screenshot
- [ ] 不包含 Android 控件

## Touch

- [ ] Portrait
- [ ] Landscape
- [ ] 移动
- [ ] 大小
- [ ] 透明度
- [ ] Multi-touch
- [ ] D-pad slide
- [ ] Touch cancel 安全释放

## Gamepad

- [ ] USB HID
- [ ] Bluetooth HID
- [ ] Default mapping
- [ ] Analog → D-pad
- [ ] Disconnect 不 crash
- [ ] Reconnect 恢复

## Lifecycle

- [ ] 旋转不丢 Session
- [ ] 后台/前台正常
- [ ] FF/Rewind 下退出安全
- [ ] Phase 2 persistence regression 全 PASS

## Security

- [ ] 0 permissions
- [ ] No INTERNET
- [ ] No Ads/Analytics
- [ ] No unknown binary
- [ ] No proprietary copied code

## Build/Test

- [ ] JVM PASS
- [ ] Native PASS
- [ ] Instrumentation PASS
- [ ] Persistence regression PASS
- [ ] UBSan PASS 或明确记录 blocker
- [ ] lint 0 errors
- [ ] Debug APK PASS
- [ ] unsigned Release APK PASS

---

# 36. Phase 3 不要求完成

Phase 3 READY 不代表 V1 可发布。

本轮不要求：

```text
Final shaders
GBA Color
LCD
Sensors
Cheats
Patch
Link
Cloud
Achievements
Production signing
All Android devices
```

---

# 37. 完成后必须生成报告

输出：

```text
docs/reports/PHASE_3_PLAYER_EXPERIENCE_REPORT.md
```

必须包括：

## A. 实际完成
## B. 架构变化
## C. Fast Forward
## D. Rewind
## E. Input
## F. Player UI
## G. Orientation
## H. Screenshot / Thumbnail
## I. Persistence Regression
## J. Permissions
## K. Dependencies
## L. 自动测试
## M. 人工实机验收
## N. 性能
## O. 已知问题
## P. 风险
## Q. 未完成项
## R. 是否允许进入 Phase 4

Fast Forward 至少记录：

```text
pacing strategy
audio strategy
actual multiplier
```

Rewind 至少记录：

```text
snapshot interval
state size
buffer size
seconds
memory cap
restore strategy
battery isolation
```

性能至少记录：

```text
1× FPS
2× actual
4× actual
8× actual
rewind memory
audio underrun
```

最终只能输出：

```text
READY
```

或：

```text
NOT READY
```

---

# 38. 进入 Phase 4 的硬门槛

任一出现：

```text
Rewind 损坏正常 Save
Fast Forward 卡死或失控
触控 stuck button
Orientation 丢 Session
Gamepad 导致持续按键
Phase 2 persistence regression
新增 INTERNET
新增危险权限
UI 直接 JNI
出现未知 binary
```

必须：

```text
NOT READY
```

---

# 39. Git 提交建议

```text
feat: add fast-forward pacing
feat: add rewind ring buffer
feat: add state thumbnails
feat: add framebuffer screenshots
feat: add portrait input profile
feat: add landscape input profile
feat: add touch layout editor
fix: harden multi-touch input routing
feat: add Android gamepad mapping
feat: refine player pause menu
test: add player experience regression
test: add rewind and fast-forward coverage
docs: add phase 3 player experience report
```

不要做单个巨大 commit。

---

# 40. Codex 本轮执行提示

执行顺序：

```text
PROJECT_SPEC.md
→ ARCHITECTURE.md
→ PHASE_0_1_REPORT.md
→ PHASE_2_PERSISTENCE_REPORT.md
→ PHASE_3_PLAYER_EXPERIENCE_IMPLEMENTATION.md
```

严格要求：

```text
只执行 Phase 3
不提前实现 Phase 4+
不升级 mGBA
不升级冻结工具链
不添加 INTERNET
不添加 Ads / Analytics
不添加 Bluetooth permission
不添加旧式 Storage permission
不复制 My Boy / Pizza Boy 代码或资源
不让 UI 直接 JNI
不破坏 Phase 2 persistence
```

重大技术选择：

> 先新增 ADR，再实施。

---

# 41. 本轮成功定义

Phase 3 成功不是：

> “按钮更多了”。

而是：

> **用户真正拿手机玩 GBA 时，可以舒适触控、稳定使用快进和倒带、横竖屏自然切换、Save State 一眼能识别，并且所有这些体验提升都没有破坏 Phase 2 已建立的存档可靠性。**

目标路径：

```text
打开
→ 继续游戏
→ 正常玩
→ RPG 长动画按住快进
→ 操作失误长按倒带
→ 松开继续
→ 随时 Save State
→ 缩略图可识别
→ 横竖屏切换
→ 退出
→ 下次继续
```

全部通过后才允许：

```text
Phase 3 = READY
```

之后才进入：

```text
Phase 4 — Renderer / Visual Experience
```
