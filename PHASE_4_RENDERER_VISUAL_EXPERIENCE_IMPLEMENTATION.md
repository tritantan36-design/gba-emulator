# GBA Lite — Phase 4 Renderer / Visual Experience 实施任务书

**文档版本：** v0.1  
**执行范围：** Phase 4 — Renderer / Visual Experience  
**执行者：** Codex / 开发代理  
**最高约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`  
**前置验收：**
- `docs/reports/PHASE_0_1_REPORT.md` = READY
- `docs/reports/PHASE_2_PERSISTENCE_REPORT.md` = READY
- `docs/reports/PHASE_3_PLAYER_EXPERIENCE_REPORT.md` 经最终人工验收后视为 READY

---

## 1. 本轮目标

Phase 4 只解决：

> **让 GBA Lite 的画面显示稳定、清晰、自然，并提供少量高质量、可理解的显示模式。**

目标路径：

```text
打开游戏
→ 默认画面清晰且比例正确
→ Original / Sharp / GBA Color / LCD
→ 横竖屏切换稳定
→ 无裁切、无拉伸、无黑屏
→ 滤镜切换即时生效
→ 快进/倒带/截图/State 缩略图均保持正常
```

本阶段不是滤镜商店，也不做 RetroArch 式复杂 Shader 系统。

---

## 2. 产品原则

V1 只保留四种模式：

```text
Original
Sharp
GBA Color
LCD
```

禁止本阶段加入：

```text
CRT / scanline pack / HQ2x / xBRZ / SABR / Anime4K
Bloom / LUT 下载 / Custom shader import
Shader plugin / Online shader repository
```

原则：

> 少而精，默认就好看。

---

## 3. 必须保留的基线

不得破坏：

```text
SAF ROM
Game ID
mGBA 0.10.5
CoreAdapter
Session
JNI
GLES
Oboe
InputRouter
Fast Forward
Rewind
State Thumbnail
Screenshot
Portrait/Landscape
Touch Layout
HID Controller
Battery Persistence
Manual State
Autosave
Quick Resume
```

继续保持：

```text
0 Android permissions
No INTERNET
No Ads
No Analytics
No unknown native binary
```

不得擅自升级 mGBA、Oboe、AGP、Gradle、Kotlin、Compose BOM、NDK、CMake。确有阻塞时先写 ADR。

---

## 4. Phase 3 历史黑屏风险

Phase 3 曾出现横屏整屏黑屏，后续自动 Surface 像素回归与人工正横屏、反横屏、回竖屏均通过，黑屏未再复现，但根因未明确。

因此 Phase 4 必须把：

```text
Surface recreate
Orientation
Shader switch
Background/Foreground
```

导致的黑屏作为专项回归风险。

不得宣称“Phase 3 黑屏根因已修复”，除非本阶段真的定位并有证据。

---

## 5. 架构原则

保持：

```text
mGBA
↓
FrameSource
↓
Renderer
↓
Texture
↓
Shader / Filter
↓
Surface
```

Renderer 必须继续独立于 mGBA、Session business logic、Persistence 和 Input。

禁止：

```text
mGBA Adapter 直接管理 Shader
UI 直接调用 JNI Renderer
Filter 逻辑进入 Core
Surface recreate → Core recreate
```

允许新增：

```kotlin
enum class DisplayMode {
    ORIGINAL,
    SHARP,
    GBA_COLOR,
    LCD
}

enum class ScaleMode {
    FIT,
    INTEGER
}
```

Renderer 可暴露：

```kotlin
fun setDisplayMode(mode: DisplayMode)
fun setScaleMode(mode: ScaleMode)
```

不得泄漏 mGBA native 类型。

---

## 6. Original

定义：

> 尽可能忠实显示 mGBA 输出的 240×160 RGBA8888 framebuffer，不做色彩模拟或 LCD 效果。

建议：

```text
baseline texture path
NEAREST
```

用途：

```text
原始参考
最低开销
Shader fallback
调试 baseline
```

Original 必须永远可用。

---

## 7. Sharp

目标：

> 现代高 DPI 手机屏幕下保持像素边界清晰，同时避免粗糙或不必要的模糊。

方向：

```text
integer-aware scaling
+
pixel-preserving sampling
```

不得：

```text
过度平滑
过度锐化
ringing
halo
```

重点测试：

```text
文字
1px 线条
Sprite
平台游戏
菜单 UI
```

---

## 8. GBA Color

目标：

> 模拟 GBA 实机屏幕更自然的综合色彩观感，而不是简单降低饱和度。

设计考虑：

```text
gamma
contrast
saturation
black level
white point
GBA LCD gamut
```

V1 使用固定、可审计的 shader / matrix / transfer curve。

禁止仅做：

```text
saturation *= 0.7
```

然后宣称是 GBA Color。

如果采用公开色彩校正公式或 emulator community 方法，必须在 ADR 或 Renderer 文档中记录来源与简化假设。

不得复制 My Boy / Pizza Boy 的闭源 shader。

---

## 9. LCD

目标：

> 轻度模拟掌机 LCD 像素观感。

允许：

```text
subtle pixel grid
very mild softening
mild LCD structure
```

默认不做复杂 temporal persistence。

禁止：

```text
重 CRT scanline
Bloom
Vignette
强 ghosting
严重偏色
```

LCD 是 GBA 掌机感，不是 CRT 电视感。

---

## 10. Shader 安全边界

Shader 必须：

- 内置；
- 源码可审计；
- 不联网下载；
- 不加载用户 GLSL；
- 不动态执行外部 shader；
- 不建立插件系统。

建议目录：

```text
renderer/
└── shaders/
    ├── common.vert
    ├── original.frag
    ├── sharp.frag
    ├── gba_color.frag
    └── lcd.frag
```

Shader 编译/链接失败时必须：

```text
fallback → Original
```

并记录 Debug error，不能黑屏或 Crash。

---

## 11. Shader 切换

用户切换：

```text
Original → Sharp → GBA Color → LCD
```

必须：

- 不重启 Core；
- 不重载 ROM；
- 不清 Rewind；
- 不影响 Audio；
- 不影响 Persistence；
- 不重建 Session。

只允许 Renderer 状态改变。

切换应即时或接近即时。

---

## 12. 缩放模式

Phase 4 至少实现：

```text
Fit
Integer
```

默认保持 GBA 原生：

```text
240 × 160
3:2
```

不得拉伸到手机屏幕比例。

### Fit

```text
完整显示
保持 3:2
不裁切
居中
允许 letterbox / pillarbox
```

### Integer

选择当前 Surface 可容纳的最大整数倍：

```text
1× / 2× / 3× / 4×...
```

如果无法容纳 1×，fallback 到 Fit。

本阶段不强制做 Fill。若实现 Fill，必须保持 3:2 并明确存在裁切，禁止非等比拉伸。

---

## 13. Portrait / Landscape

竖屏：

```text
优先利用安全区可用宽度
保持 3:2
触控区域使用剩余空间
```

横屏：

```text
尽可能利用 safeDrawing 可用区域
保持 3:2
```

不得恢复 Phase 3 已移除的横屏 62% Surface width 限制，除非有新的明确证据。

Shader/Scale Mode 不得改变 Input Profile 坐标系。

---

## 14. Surface / Orientation

必须覆盖：

```text
Portrait
Landscape
Reverse Landscape
Portrait
```

至少连续切换 10 次。

每次检查：

```text
实际 Surface 非黑
viewport 正确
3:2 正确
Fit 不裁切
无 stretch
触控可用
Audio 可恢复
```

Surface recreate 只允许重建 Renderer transient resources。

Core / Session / Persistence 必须保持。

---

## 15. GLES Context Loss

必须考虑：

```text
Surface destroyed
Surface recreated
Context lost
Activity recreate
Background
```

需要时重建：

```text
Texture
Program
Buffer
```

禁止继续使用旧 GL handle。

Renderer 所有 GL 资源只允许在正确 GL thread/context 操作。

---

## 16. Texture 上传

当前 framebuffer：

```text
240×160 RGBA8888
```

继续使用可复用 buffer → texture upload。

禁止每帧：

```text
创建 Bitmap
创建 ByteBuffer
创建临时大对象
```

避免 GC 与 allocation 抖动。

---

## 17. Screenshot / Thumbnail 策略

默认继续：

> Screenshot 和 State Thumbnail 保存原始 framebuffer，而不是滤镜后的 Android Surface。

原因：

```text
跨模式一致
State thumbnail 风格稳定
不依赖 GPU shader
不包含 Android UI
```

如果决定改成“保存用户看到的滤镜后画面”，必须写 ADR。

Shader 切换不得改变 State 本体或 Rewind State 格式。

---

## 18. Rewind / Fast Forward 回归

### Rewind

每种 DisplayMode：

```text
rewind 5–10 秒
→ 松开
→ 继续
```

必须：

```text
DisplayMode 保持
Battery 不变
State 不损坏
无黑屏
```

### Fast Forward

Original / Sharp / GBA Color / LCD 下分别验证：

```text
2×
4×
8×
→ 关闭
→ 恢复 1×
```

检查：

```text
画面恢复
声音恢复
无 audio queue runaway
无黑屏
Shader 不显著拖垮模拟吞吐
```

Renderer 在快进时允许只显示最新帧并丢弃中间显示帧。

---

## 19. 性能约束

GBA 分辨率很低，本阶段 shader 应优先：

```text
single-pass
```

禁止：

```text
重型多级 FBO 链
复杂 convolution
大量 framebuffer copies
重型 temporal pipeline
```

目标：

```text
四种模式 1× 均稳定
2× 稳定
4× 尽量保持 Phase 3 性能
8× Best Effort
```

记录：

```text
GPU frame time
CPU render time
dropped frames
actual FF multiplier
audio underrun delta
```

---

## 20. Debug-only Renderer Metrics

允许：

```text
surfaceCreated / changed / destroyed
context id
viewport
texture id
program id
display mode
frame sequence
last uploaded frame
foreground state
GL compile/link error
```

只保存在本地 Debug 日志。

No Analytics。

Release 不得每帧高频 `glGetError()`。

---

## 21. 黑屏专项自动回归

必须建立真实 Surface 检查：

```text
launch
→ known non-black test frame
→ portrait assert pixels
→ landscape assert pixels
→ reverse landscape assert pixels
→ portrait assert pixels
```

再加入组合：

```text
Shader switch
+
Orientation
+
Background
+
Foreground
```

禁止仅用：

```text
Session frame counter
native frame count
```

证明“画面正常”。

必须使用至少一种：

```text
UiAutomation screenshot
PixelCopy
Surface pixel capture
```

确认真实显示。

---

## 22. Shader Fallback

任何：

```text
compile failure
link failure
runtime GL setup failure
invalid persisted display mode
```

都必须：

```text
fallback → Original
```

用户层可提示：

```text
显示模式加载失败，已恢复 Original
```

不能留下黑屏状态。

---

## 23. Display 设置 UI

保持极简：

```text
显示模式
  Original
  Sharp
  GBA Color
  LCD

缩放
  Fit
  Integer
```

建议说明：

```text
Original
原始像素显示

Sharp
适合现代高分辨率屏幕的清晰显示

GBA Color
模拟 GBA 屏幕综合色彩

LCD
轻度模拟掌机 LCD 像素观感
```

不要加入十几个参数滑块。

建议默认：

```text
Sharp
```

但若 Sharp 存在兼容性或显示异常：

```text
Default = Original
```

安全优先。

---

## 24. 设置持久化

保存：

```text
displayMode
scaleMode
```

可使用已有轻量设置体系；如果项目已有 DataStore 则使用 DataStore。

本阶段默认全局设置，不要求 per-game display settings。

未知/废弃 enum 值必须 fallback Original/Fit，不得 Crash。

---

## 25. 自研视觉测试 ROM

建议新增合法原创：

```text
color-pattern.gba
lcd-pattern.gba
```

### color-pattern.gba

显示：

```text
RGB ramps
grayscale
black/white
primary colors
low/high brightness patches
```

用于 GBA Color 验证。

### lcd-pattern.gba

显示：

```text
1 px checkerboard
horizontal lines
vertical lines
diagonal lines
fine text
sprites
high contrast edges
```

用于：

```text
aliasing
blur
ringing
pixel grid
LCD strength
```

许可证使用项目现有 Apache-2.0 测试资产策略。

---

## 26. GBA Test ROM Corpus

如仓库已纳入 `GBA_TEST_ROM_CORPUS.md`，Phase 4 按其法律边界使用。

优先：

```text
Internal bringup
Internal persistence
Internal visual patterns
mGBA suite
160p Test Suite（合法集成后）
BeatBeast（assets audit 后）
Varooom 3D（credits/license audit 后）
```

禁止：

```text
商业 ROM
Nintendo BIOS
随机 ROM 网站
批量抓 Homebrew ROM
```

---

## 27. Frame / Visual 测试

对 deterministic 自研 ROM：

```text
frame N
→ raw RGBA
→ SHA-256
```

可用于确认 Core framebuffer 没被 Renderer 路径破坏。

Shader 输出不建议跨 GPU 做 bit-exact hash。

Instrumentation 应更偏向：

```text
非黑
viewport 正确
关键区域符合预期
四模式确实产生合理差异
```

Visual Golden 只能来自合法测试 ROM。

---

## 28. 色彩验收

GBA Color 必须检查：

```text
灰阶
黑/白
红/绿/蓝
低亮度
高亮度
文字
自然色块
```

禁止仅凭“看起来更复古”验收。

报告中必须说明：

```text
公式
参数
来源
简化假设
```

---

## 29. LCD 验收

检查：

```text
细文字仍可读
pixel grid 不过强
边缘无明显 halo
无强 ghosting
无严重暗化
无明显掉帧
```

如果 LCD 效果过重：

> 宁可减弱，也不要追求戏剧化。

---

## 30. 生命周期组合测试

每种 DisplayMode 至少一次：

```text
选择模式
→ background
→ foreground
→ actual Surface 非黑
→ mode 仍保持
```

并覆盖：

```text
Activity recreate
orientation change
pause menu
Quick Save
Quick Load
Rewind
Fast Forward
```

---

## 31. Renderer 内存与资源

至少记录：

```text
texture count
program count
buffer count
```

连续 orientation 10–20 次后：

> 不应持续增长。

检查：

```text
stale GL handles
leaked Surface
重复 Program
重复 Texture
```

---

## 32. 长时间测试

建议 30–60 分钟：

```text
Original
Sharp
GBA Color
LCD
```

过程中穿插：

```text
FF
Rewind
State Save/Load
Orientation
Background/Foreground
```

观察：

```text
FPS
memory
audio
GL error
black frame
主观温升
```

不需要实验室级热测试，但要记录异常。

---

## 33. 必须新增测试

### JVM / Unit

```text
DisplayMode serialization
ScaleMode serialization
invalid setting fallback
viewport calculation
3:2 aspect ratio
integer scale calculation
```

### Instrumentation

```text
Original visible
Sharp visible
GBA Color visible
LCD visible
mode switching
portrait
landscape
reverse landscape
10× orientation loop
background/foreground
Activity recreate
FF
Rewind
Screenshot
State thumbnail
```

### Existing Regression

继续全部执行：

```text
Phase 2 Persistence
Phase 3 Input
Phase 3 HID
Phase 3 Touch
Phase 3 FF
Phase 3 Rewind
```

---

## 34. UBSan / ASan

Phase 4 无大量新增 native 逻辑，但仍必须跑现有 UBSan regression：

```text
normal
FF
rewind
orientation
close
```

若 ASan 工具链仍不可用：

```text
记录 blocker
```

不得记 PASS。

---

## 35. Manifest / Dependency Audit

目标：

```text
Debug permissions = []
Release permissions = []
```

Shader 不需要权限。

Phase 4 应尽量：

```text
0 新第三方依赖
```

禁止：

```text
closed-source shader SDK
online effects SDK
GPU telemetry SDK
Ads
Analytics
network SDK
```

---

## 36. Build / Lint

必须：

```text
JVM PASS
Native PASS
Instrumentation PASS
UBSan regression PASS
lint 0 errors
Debug build PASS
unsigned Release build PASS
```

现有非阻断 warnings 可以保留，但报告要列明。

---

## 37. 人工实机验收

至少检查：

```text
Original
Sharp
GBA Color
LCD

Portrait
Landscape
Reverse Landscape

Fit
Integer

2×
4×
8×

Rewind
Screenshot
State thumbnail
Background/Foreground
```

反馈维度：

```text
是否黑屏
是否拉伸
是否裁切
是否明显模糊
是否锐化过度
颜色是否过饱和/过灰
LCD 是否过重
是否闪烁
是否掉帧
声音是否正常恢复
```

---

## 38. Phase 4 验收标准

### Display

- [ ] Original
- [ ] Sharp
- [ ] GBA Color
- [ ] LCD
- [ ] 即时切换
- [ ] Shader 失败 fallback Original

### Scaling

- [ ] 3:2
- [ ] Fit
- [ ] Integer
- [ ] 不拉伸
- [ ] Fit 不裁切

### Surface

- [ ] Portrait
- [ ] Landscape
- [ ] Reverse Landscape
- [ ] 连续旋转
- [ ] Actual Surface pixel 验证
- [ ] 无黑屏

### Lifecycle

- [ ] Background/Foreground
- [ ] Activity recreate
- [ ] DisplayMode 恢复
- [ ] Core/Session 不重建

### Regression

- [ ] Touch
- [ ] HID
- [ ] FF
- [ ] Rewind
- [ ] Save State
- [ ] Thumbnail
- [ ] Screenshot
- [ ] Persistence

### Security

- [ ] 0 permissions
- [ ] No INTERNET
- [ ] No Ads/Analytics
- [ ] No external shader
- [ ] No unknown binary

### Build/Test

- [ ] JVM PASS
- [ ] Native PASS
- [ ] Instrumentation PASS
- [ ] UBSan PASS
- [ ] lint 0 errors
- [ ] Debug PASS
- [ ] unsigned Release PASS

---

## 39. Phase 4 不要求完成

```text
RTC
Tilt
Gyro
Solar
Rumble
Cheats
Patch
Link
SkyEmu
Cloud
Achievements
Final signing
Complete Library redesign
```

这些属于 Phase 5+。

---

## 40. 完成后必须生成报告

输出：

```text
docs/reports/PHASE_4_RENDERER_VISUAL_EXPERIENCE_REPORT.md
```

必须包括：

### A. 实际完成
### B. 架构变化
### C. Original / Sharp / GBA Color / LCD
### D. GBA Color 公式、来源、简化
### E. LCD 实现与性能
### F. Fit / Integer / Viewport
### G. Orientation / Surface
### H. Phase 3 历史黑屏专项回归
### I. Shader Failure / Fallback
### J. Screenshot / Thumbnail
### K. FF / Rewind Regression
### L. Persistence / Input Regression
### M. Permissions
### N. Dependencies
### O. 自动测试
### P. Actual Surface Tests
### Q. 性能
### R. 人工实机验收
### S. 已知问题
### T. 风险
### U. 未完成项
### V. 是否允许进入 Phase 5

最终只能：

```text
READY
```

或：

```text
NOT READY
```

---

## 41. Phase 5 硬门槛

任一出现：

```text
Shader 导致黑屏
旋转后黑屏
Surface recreate 后黑屏
Renderer resource 持续增长
Fit 拉伸/裁切错误
GBA Color 明显错误
LCD 明显不可用
FF 被 Shader 显著拖垮
Rewind 后 Renderer 失效
Phase 2 Persistence regression
Phase 3 Input regression
新增 INTERNET
新增危险权限
动态外部 Shader
UI 直接 JNI
```

必须：

```text
NOT READY
```

---

## 42. Git 提交建议

```text
feat: add display mode abstraction
feat: add sharp display mode
feat: add gba color correction
feat: add lcd display mode
feat: add integer scaling
feat: persist renderer preferences
test: add renderer viewport coverage
test: add shader switching instrumentation
test: add orientation surface regression
test: add phase 3 black-screen regression
test: add fast-forward renderer regression
docs: add phase 4 renderer report
```

不要把整个 Phase 4 压成一个 commit。

---

## 43. Codex 阅读顺序

```text
PROJECT_SPEC.md
→ ARCHITECTURE.md
→ PHASE_0_1_REPORT.md
→ PHASE_2_PERSISTENCE_REPORT.md
→ PHASE_3_PLAYER_EXPERIENCE_REPORT.md
→ GBA_TEST_ROM_CORPUS.md（如已纳入项目）
→ PHASE_4_RENDERER_VISUAL_EXPERIENCE_IMPLEMENTATION.md
```

---

## 44. Codex 强约束

```text
只实施 Phase 4
不提前实施 Phase 5+
不升级 mGBA
不升级冻结工具链
不添加 INTERNET
不添加 Ads / Analytics
不引入动态外部 Shader
不复制 My Boy / Pizza Boy shader、代码、资源
不破坏 Phase 2 Persistence
不破坏 Phase 3 Player/Input
不通过重建 Core 解决 Renderer 问题
```

重大技术选择必须：

> 先写 ADR，再实施。

---

## 45. 本轮成功定义

Phase 4 成功不是：

> “滤镜更多了”。

而是：

> **GBA Lite 在现代手机屏幕上，无论横屏、竖屏、旋转、后台恢复、快进还是倒带，都能稳定显示正确比例的画面，并提供 Original / Sharp / GBA Color / LCD 四种少而精的视觉模式。**

目标路径：

```text
继续游戏
→ Sharp 清晰显示
→ 切 GBA Color
→ 颜色自然
→ 横屏
→ 无黑屏
→ 反向横屏
→ 无黑屏
→ 4× 快进
→ 恢复 1×
→ 声音正常
→ Rewind
→ 继续
→ 切 LCD
→ 显示正常
→ 退出
→ 下次继续
```

全部通过后：

```text
Phase 4 = READY
```

之后才允许进入：

```text
Phase 5 — GBA Hardware / Sensors
```
