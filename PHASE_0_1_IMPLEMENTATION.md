# GBA Lite — Phase 0/1 实施任务书

**文档版本：** v0.1  
**执行范围：** Phase 0 Foundation + Phase 1 Core Bring-up  
**执行者：** Codex / 开发代理  
**最高约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`

---

## 1. 本轮目标

本轮只完成：

> 建立一个可维护、可测试、完全离线的 Android GBA 模拟器基础工程，并让合法 Homebrew GBA ROM 通过 mGBA 成功运行，具备画面、声音和基础按键控制。

本轮不是做完整产品。

---

## 2. 本轮禁止提前实现

禁止实现：

```text
Save State
Autosave
Quick Save / Load
Rewind
Fast Forward UI
Shader 系统
GBA Color
LCD Filter
游戏库完整功能
封面系统
Cheats
Patch
RetroAchievements
Cloud
Network Link
Local Link
SkyEmu
复杂设置页
控制器布局编辑器
```

如果为了测试 Core 必须建立临时接口，必须标记为 `TEST ONLY`，不得演变为正式产品实现。

---

## 3. 执行前要求

Codex 开始前必须完整阅读：

```text
PROJECT_SPEC.md
ARCHITECTURE.md
PHASE_0_1_IMPLEMENTATION.md
```

优先级：

```text
PROJECT_SPEC.md
    ↓
ARCHITECTURE.md
    ↓
PHASE_0_1_IMPLEMENTATION.md
```

冲突时以上级文档为准。

---

## 4. Phase 0 — Foundation

建立：

```text
gba-lite/
├── app/
├── core-api/
├── core-mgba/
├── renderer/
├── audio/
├── input/
├── feature-player/
├── third_party/
│   └── mgba/
├── docs/
│   ├── adr/
│   └── reports/
├── gradle/
├── LICENSE
├── NOTICE
├── PROJECT_SPEC.md
└── ARCHITECTURE.md
```

当前阶段某模块没有代码时，可以保持最小骨架。

---

## 5. Gradle / Android 基础

要求：

- Kotlin
- Jetpack Compose
- Android Gradle Plugin 固定版本
- Gradle Wrapper 固定版本
- Version Catalog
- Dependency Locking
- `compileSdk` / `targetSdk` 固定
- 最低 Android 版本由实现阶段决定并记录 ADR

禁止：

```text
+
latest
SNAPSHOT
```

---

## 6. Manifest

Phase 0 验收必须确认 Manifest 不含：

```text
INTERNET
ACCESS_NETWORK_STATE
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
BLUETOOTH_CONNECT
LOCATION
CAMERA
MICROPHONE
CONTACTS
SMS
PHONE
```

如果当前阶段不需要震动，则连 `VIBRATE` 也不加。

---

## 7. License

项目自有代码：

```text
Apache-2.0
```

必须包含：

```text
LICENSE
NOTICE
```

mGBA 保留：

```text
MPL-2.0
upstream license
copyright
```

`NOTICE` 中说明：

```text
This project uses mGBA as its Game Boy Advance emulation core.
```

不得暗示 mGBA 官方支持本项目。

---

## 8. mGBA 获取

必须从官方 upstream 引入。

要求：

- 使用稳定 release
- 固定 commit SHA
- 记录 upstream URL
- 记录 release tag
- 记录 commit SHA
- 不使用第三方预编译 `.so`
- Native library 必须由本项目源码构建

建议放置：

```text
third_party/mgba/
```

优先使用 Git Submodule。

---

## 9. 初始 ADR

创建：

```text
docs/adr/ADR-001-mgba-as-production-core.md
docs/adr/ADR-002-offline-v1.md
docs/adr/ADR-003-core-adapter-boundary.md
docs/adr/ADR-004-opengl-renderer.md
```

其中 ADR-001 至少记录：

```text
Context
Decision
Why mGBA
Why not Libretro
Why SkyEmu is deferred
License implications
Upgrade policy
```

ADR-002 记录：

- V1 无 INTERNET
- 无 Analytics
- 无 Ads
- 无账号
- 无动态下载
- 后续若联网必须重新评审安全模型

---

## 10. `core-api`

最低定义：

```text
EmulatorCore
GameSource
LoadResult
GbaButton
EmulatorCapabilities
```

Phase 0/1 只定义当前真正需要的方法，不提前堆大量空 API。

最低接口：

```kotlin
interface EmulatorCore : AutoCloseable {
    fun loadGame(source: GameSource): LoadResult

    fun start()
    fun pause()
    fun resume()
    fun reset()
    fun stop()

    fun setButton(button: GbaButton, pressed: Boolean)

    override fun close()
}
```

Save、Rewind、Sensors、Fast Forward 等以后再扩展。

---

## 11. `core-mgba`

建立：

```text
core-mgba/
├── src/main/java/...
├── src/main/cpp/
│   ├── mgba_bridge.cpp
│   ├── mgba_bridge.h
│   └── CMakeLists.txt
└── build.gradle.kts
```

职责：

```text
MgbaCoreAdapter
JNI bridge
mGBA lifecycle
frame/audio output
input mapping
```

---

## 12. JNI 设计

必须：

- Native handle 封装
- 空 handle 检查
- double close 安全
- JNI 异常转换
- 不暴露 mGBA 指针给 UI
- 不使用全局单例 core
- Native 资源可确定释放

最低 JNI：

```text
nativeCreate
nativeDestroy
nativeLoadRom
nativeStart
nativePause
nativeReset
nativeSetButton
```

Frame / audio 使用适合 mGBA 的最小实现。

---

## 13. Native Build

使用：

```text
Android NDK
CMake
```

初始目标 ABI：

```text
arm64-v8a
```

本轮不要擅自扩展到 32-bit ARM、x86，除非另有明确文档要求。

---

## 14. Native 安全

Debug/Test 为 sanitizer 留出配置。

Release/常规构建至少确认：

```text
stack protector
FORTIFY where available
PIE
RELRO
NX
```

不得关闭安全项来绕过构建问题。

---

## 15. ROM 选择

Phase 1 使用：

```text
Storage Access Framework
ACTION_OPEN_DOCUMENT
```

只接受：

```text
.gba
```

Phase 1 暂不做 ZIP。

流程：

```text
Uri
↓
ParcelFileDescriptor
↓
FD / controlled input
↓
core
```

禁止申请旧式存储权限。

---

## 16. 测试 ROM

仓库不得包含商业 ROM。

使用：

- Public domain ROM
- Homebrew ROM
- GBA test ROM

需要测试资源时，必须记录来源与许可证。

---

## 17. Player 最小 UI

Phase 1 只需要：

```text
选择 ROM
↓
进入 Player
```

Player：

```text
┌───────────────────────────┐
│                           │
│         Game Frame        │
│                           │
│   ↑                  A    │
│ ←   →              B      │
│   ↓                       │
│                           │
│       SELECT START        │
└───────────────────────────┘
```

允许简单按钮，无复杂动画、皮肤、布局编辑。

---

## 18. 输入

Phase 1 必须支持：

```text
D-pad
A
B
L
R
Start
Select
```

正确链路：

```text
Touch
↓
InputRouter
↓
EmulatorCore
↓
MgbaCoreAdapter
↓
JNI
```

禁止：

```text
Compose → JNI
```

直接调用。

---

## 19. Renderer

Phase 1 只实现：

```text
Original
```

目标：

- 正确显示 240×160
- 保持宽高比
- 避免每帧 Bitmap 分配
- 使用 OpenGL ES 或等效低开销路径
- Surface 重建后可恢复

不做：

```text
Sharp
GBA Color
LCD
```

---

## 20. Orientation

Phase 1 最低支持：

```text
Portrait
```

如果横屏实现成本很低，可以一起完成。

不得为了横屏推迟核心 Bring-up。

---

## 21. Audio

Phase 1：

```text
mGBA PCM
↓
native buffer
↓
Oboe
↓
AAudio / OpenSL fallback
```

最低目标：

- 有正确声音
- 不持续爆音
- Pause 时停止/静音
- Resume 后恢复

本轮不做高级 latency tuning。

---

## 22. Emulation Thread

必须使用独立线程。

禁止：

```text
Main Thread 跑 mGBA frame loop
```

必须验证：

- UI 不冻结
- ROM 运行时 Compose 可响应
- Pause 可及时生效
- Activity 销毁后线程停止

---

## 23. Lifecycle

Phase 1 最低实现：

后台：

```text
pause emulator
pause audio
```

前台：

```text
resume audio
resume emulator
```

Phase 1 尚未实现保存，因此报告必须明确：

```text
Persistence not implemented until Phase 2.
```

---

## 24. Crash Safety

Phase 1 要做到：

- ROM 读取失败不崩溃
- 无效文件给出错误
- Native create 失败给出错误
- Surface 销毁不会 use-after-free
- close 可重复安全调用
- Activity 重建不会泄漏 Native core

---

## 25. 错误 UI

最小即可：

```text
无法加载该 GBA 文件。
```

Debug log 可包含具体 native error。

用户界面不要暴露内部 pointer / JNI code。

---

## 26. CI

Phase 0 必须建立基础 CI。

最低：

```text
Gradle build
Kotlin compile
Unit tests
Android lint
CMake arm64-v8a build
Debug APK build
```

若环境允许，再加 native tests。

---

## 27. 测试

### Unit

```text
GbaButton mapping
EmulatorCore lifecycle wrapper
invalid state handling
```

### JNI

```text
nativeCreate/nativeDestroy
invalid handle
load invalid ROM
```

### Instrumentation

至少：

```text
App launch
SAF launch path
Player navigation
Activity recreate
```

---

## 28. Dependency Audit

本轮结束输出依赖清单。

确认：

```text
No Firebase
No Analytics
No Ads
No OkHttp unless explicitly justified
No Retrofit
No WebView networking dependency
```

如果 transitive dependency 带入网络库，必须解释并尽量删除。

---

## 29. Manifest Audit

本轮完成后输出最终 merged manifest 权限列表。

目标：

```text
无 INTERNET
无存储危险权限
无账户权限
无位置权限
```

---

## 30. Phase 0 验收标准

必须全部满足：

- [ ] Gradle 工程建立
- [ ] Compose App 可启动
- [ ] NDK/CMake 成功
- [ ] mGBA 源码纳入
- [ ] mGBA commit 固定
- [ ] License/NOTICE 完整
- [ ] CI 可运行
- [ ] 无 INTERNET
- [ ] 无 Analytics/Ads
- [ ] `core-api` 存在
- [ ] `core-mgba` 存在
- [ ] Native library 可加载

---

## 31. Phase 1 验收标准

必须全部满足：

- [ ] 可通过 SAF 选择 `.gba`
- [ ] Homebrew ROM 成功加载
- [ ] 游戏画面正确显示
- [ ] 游戏声音可播放
- [ ] D-pad 工作
- [ ] A/B 工作
- [ ] L/R 工作
- [ ] Start/Select 工作
- [ ] UI 不被模拟线程卡住
- [ ] App 后台后模拟暂停
- [ ] 回前台可继续
- [ ] 退出 Player 后 Native core 被释放
- [ ] 无明显 Native leak
- [ ] 无 INTERNET
- [ ] 未提前实现 Phase 2+
- [ ] 测试通过
- [ ] lint 通过
- [ ] Debug APK 成功构建

---

## 32. 完成后必须生成报告

文件：

```text
docs/reports/PHASE_0_1_REPORT.md
```

报告必须包含：

### A. 完成内容

实际完成了什么。

### B. 项目目录

输出最新 tree。

### C. mGBA 信息

```text
release/tag
commit SHA
来源
license
是否修改 upstream
```

### D. 架构变化

如果与 `ARCHITECTURE.md` 不同，必须说明。

### E. Android 配置

```text
minSdk
targetSdk
compileSdk
AGP
Gradle
Kotlin
NDK
CMake
```

### F. Manifest 权限

列出最终 merged manifest 权限。

### G. 测试

列出 unit / instrumentation / native / lint / build 结果。

### H. 测试 ROM

只记录合法 Homebrew/Test ROM。

### I. 已知问题

不得随意写 `none`。

### J. 未完成项

明确说明 Phase 2 才处理：

```text
SRAM
Save State
Autosave
Quick Resume
```

### K. 风险

例如：

```text
audio latency
surface lifecycle
mGBA patch
ABI
native leak
```

### L. 是否允许进入 Phase 2

只能输出：

```text
READY
```

或：

```text
NOT READY
```

并说明理由。

---

## 33. Git 提交建议

建议拆分：

```text
chore: initialize Android project
chore: add mGBA core
feat: add emulator core API
feat: add mGBA JNI bridge
feat: render GBA framebuffer
feat: add basic GBA input
feat: add audio output
test: add phase 0/1 validation
docs: add phase 0/1 report
```

不要做一个巨大 commit。

---

## 34. 不允许的捷径

禁止：

- 直接使用 RetroArch APK/Core 包
- 使用第三方预编译 mGBA `.so`
- 复制 My Boy 代码
- 为 ROM 访问申请全盘权限
- 为“以后联网”提前申请 INTERNET
- UI 直接 JNI
- 模拟循环放 Main Thread
- 关闭 lint/test 只为过构建
- Release 使用 debug key

---

## 35. Codex 启动提示

执行本任务时：

```text
先读 PROJECT_SPEC.md
再读 ARCHITECTURE.md
再执行 PHASE_0_1_IMPLEMENTATION.md

不要实现 Phase 2+
不要添加 INTERNET
不要添加广告/Analytics
不要使用未知 native binary
不要直接让 UI 调用 mGBA
不要静默修改架构
```

遇到重大架构问题：

> 新增 ADR，不要擅自扩大范围。

---

## 36. 本轮成功定义

成功不是：

> “已经像一个完整模拟器”。

而是：

> “Android + JNI + mGBA + Renderer + Audio + Input 这条核心技术链路可以稳定运行，而且没有破坏架构与安全边界。”

只要实现：

```text
合法 GBA ROM
↓
mGBA
↓
有画面
有声音
可控制
```

并满足本任务书的安全、架构和测试要求，Phase 0/1 即可视为成功。
