# GBA Lite Android 模拟器正式项目规格

**文档版本：** v0.1  
**项目代号：** GBA Lite（暂定名）  
**目标平台：** Android  
**项目状态：** Architecture Locked / V1 Planning  
**项目性质：** 开源、完全离线、GBA 专用模拟器

---

## 1. 项目目标

GBA Lite 是一个面向 Android 的轻量级 Game Boy Advance 模拟器。

目标不是做 RetroArch 式的功能堆叠，也不是复制 My Boy / Pizza Boy，而是：

> 安全、干净、快速、稳定、低延迟、打开即可玩。

核心原则：

- GBA Only
- 极简 UI
- V1 完全离线
- 无广告、无 Analytics、无账号
- 无动态插件下载、无 ROM 下载
- 模拟核心开源且可审计
- Android 前端开源
- UI 与模拟核心解耦
- 存档安全优先于新增功能

---

## 2. 已锁定决策

| 项目 | 决策 |
|---|---|
| 模拟平台 | GBA Only |
| 主核心 | mGBA |
| 第二核心 | V1 不集成；架构预留 SkyEmu |
| UI | Kotlin + Jetpack Compose |
| 产品方向 | 极简 |
| 网络 | V1 完全离线 |
| INTERNET 权限 | 禁止 |
| 广告 / Analytics | 禁止 |
| 用户账号 | 不提供 |
| ROM 下载 | 不提供 |
| 在线插件 | 不提供 |
| 项目 | 开源 |
| App 自有代码许可证 | Apache-2.0 |
| mGBA | MPL-2.0 |
| ROM 访问 | Android Storage Access Framework |
| 视频 | OpenGL ES |
| 音频 | Oboe / AAudio |
| Native Bridge | JNI |
| Native Build | NDK + CMake |
| 设置 | DataStore |
| 数据库 | Room |
| 异步 | Coroutines |

---

## 3. 明确不做

本项目不是：

- My Boy 修改版
- My Boy 反编译版本
- RetroArch 换皮
- mGBA 桌面 UI 移植
- ROM 下载器
- 在线游戏平台

禁止复制任何闭源模拟器的源码、图标、专有素材和反编译代码。

允许借鉴公开产品交互模式，例如：

- Quick Save
- Rewind
- Fast Forward
- Save State 缩略图
- 游戏库
- 自动恢复
- Shader 模式

但必须独立实现。

---

## 4. 总体架构

```text
Jetpack Compose UI
        │
        ▼
Application Layer
Session / Save / Lifecycle / Library
        │
        ▼
EmulatorCore API
        │
        ▼
MgbaCoreAdapter
        │ JNI
        ▼
mGBA Core
```

外围系统：

```text
mGBA framebuffer → OpenGL Renderer → Shader → Screen
mGBA PCM         → Ring Buffer     → Oboe  → AAudio
```

---

## 5. 核心架构原则

Android 上层只认识 `EmulatorCore`，不能直接依赖 mGBA API。

建议接口：

```kotlin
interface EmulatorCore : AutoCloseable {
    fun loadGame(source: GameSource): LoadResult
    fun start()
    fun pause()
    fun resume()
    fun reset()
    fun stop()

    fun setButton(button: GbaButton, pressed: Boolean)

    fun saveState(slot: Int): StateResult
    fun loadState(slot: Int): StateResult

    fun setFastForward(multiplier: Float)
    fun beginRewind()
    fun endRewind()

    fun flushSaveRam()

    fun setTilt(x: Float, y: Float)
    fun setGyro(value: Float)
    fun setSolarLevel(level: Float)

    override fun close()
}
```

未来可以实现：

```text
MgbaCoreAdapter
SkyEmuCoreAdapter
```

UI 无需改写。

---

## 6. 模拟核心

Production Core：**mGBA**

规则：

- 使用官方稳定 release
- 固定具体 commit SHA
- 不跟随 master
- 不自动更新
- 每次升级必须做兼容性测试
- 修改 MPL 文件时保留对应源码与许可证
- 不使用来源不明的预编译 `.so`

建议：

```text
third_party/
└── mgba/
```

优先通过 Adapter 解决适配问题，不大改上游。

重大改动写 ADR。

---

## 7. SkyEmu 策略

V1 不集成 SkyEmu。

但架构预留：

```text
EmulatorCore
├── mGBA     ← V1
└── SkyEmu   ← Future
```

未来定位：

- Compatibility fallback
- Reference core
- Regression comparison

普通用户 V1 不显示核心选择。

---

## 8. 推荐项目结构

```text
gba-lite/
├── app/
├── core-api/
├── core-mgba/
├── emulator-session/
├── renderer/
├── audio/
├── input/
├── storage/
├── data/
├── feature-library/
├── feature-player/
├── feature-settings/
├── common-ui/
├── third_party/
│   └── mgba/
├── shaders/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── SECURITY.md
│   ├── SAVE_FORMAT.md
│   ├── TESTING.md
│   ├── adr/
│   └── reports/
├── LICENSE
├── NOTICE
└── PROJECT_SPEC.md
```

不为架构漂亮而过度拆分。

---

## 9. Emulator 生命周期

```text
EMPTY → LOADED → RUNNING → PAUSED → SUSPENDED → STOPPED
```

后台：

```text
pause → flush SRAM → autosave → session metadata
```

恢复：

```text
restore session → resume
```

正常退出：

```text
pause → flush SRAM → autosave → close core
```

异常情况下至少保留最近 SRAM 与 rolling autosave。

---

## 10. Game ID

禁止只依赖文件名。

使用 ROM 内容哈希，推荐：

```text
SHA-256(ROM)
```

相同 ROM 即使文件名不同，也应映射到同一个游戏身份。

---

## 11. SRAM 与 Save State 分离

SRAM / FLASH / EEPROM 是游戏自己的正常存档，应尽量保持跨模拟器可迁移。

Save State 属于模拟器内部状态，必须保存：

```text
core
coreVersion
stateVersion
gameHash
createdAt
screenshot
```

State 不兼容时禁止静默加载，也不能删除旧 State。

---

## 12. Autosave

至少维护：

```text
Autosave A
Autosave B
Autosave C
```

循环覆盖。

触发：

- App 后台
- 返回游戏库
- 正常退出
- 周期性安全点

目标：尽量避免杀后台、闪退、误退出导致大量进度丢失。

---

## 13. V1 ROM 与游戏库

支持：

- `.gba`
- `.zip` 内单个 `.gba`

V1 不支持：

- 7z
- rar
- 在线 ROM
- SMB / WebDAV
- 网络目录

ROM 使用 SAF：

```text
ACTION_OPEN_DOCUMENT
ACTION_OPEN_DOCUMENT_TREE
```

禁止申请全盘存储权限。

---

## 14. V1 Player

支持：

- 横屏 / 竖屏
- 全屏
- 安全区适配
- D-pad
- A/B/L/R
- Start/Select

暂停菜单只保留高频项：

```text
继续
即时存档
读取存档
倒带
快进
截图
设置
退出游戏
```

---

## 15. Save State

V1：

```text
4 个手动 Slot + Autosave
```

每个 Slot 显示：

- 截图
- 日期时间
- 游戏时间

---

## 16. Rewind

V1 必须支持。

目标：

```text
至少 10–30 秒
```

优先使用内存滚动状态缓冲，不频繁写磁盘。

---

## 17. Fast Forward

提供：

```text
1× / 2× / 4× / 8×
```

2× 必须稳定；4× 应在主流设备稳定；8× 为 Best Effort。

---

## 18. 输入

Touch：

- D-pad
- A/B
- L/R
- Start/Select
- Turbo

Controller：

- USB HID
- Bluetooth HID

使用 Android 标准 `InputDevice / KeyEvent / MotionEvent`。

App 不负责蓝牙配对，因此不应为了手柄申请 Bluetooth 权限。

---

## 19. 触控布局

支持：

- 拖动
- 大小
- 透明度
- 横屏布局
- 竖屏布局

默认布局必须足够好，不能要求用户先配置才能玩。

---

## 20. GBA 硬件

V1 计划支持：

```text
RTC
Rumble
Tilt
Gyroscope
Solar Sensor
```

传感器不可用时允许软件模拟值。

---

## 21. 视频系统

GBA 原始：

```text
240 × 160
```

流程：

```text
Framebuffer → OpenGL Texture → Shader → Screen
```

禁止 CPU 先放大。

V1 显示模式：

1. Original
2. Sharp
3. GBA Color
4. LCD

Shader 属于 Renderer，不属于 Core。

V1 不允许第三方 Shader 导入。

---

## 22. 音频

```text
mGBA PCM → Native Ring Buffer → Oboe → AAudio
```

旧设备允许 Oboe fallback。

目标：

- 无明显爆音
- 无周期卡顿
- 音画同步
- 尽量低延迟

Compose 不参与实时音频处理。

---

## 23. 安全模型

ROM、ZIP、Save State 均视为不可信输入。

必须：

- 防路径穿越
- 限制 ZIP 解压大小与压缩比
- 不执行 ROM 中任何主机代码
- 不动态加载 APK / DEX / 用户 native library
- 不下载动态代码
- 不调用 shell
- 不接受远程 URL ROM

---

## 24. 权限策略

V1 目标 Manifest：

```text
INTERNET                ❌
ACCESS_NETWORK_STATE    ❌
READ_EXTERNAL_STORAGE   ❌
WRITE_EXTERNAL_STORAGE  ❌
MANAGE_EXTERNAL_STORAGE ❌
BLUETOOTH_CONNECT       ❌
LOCATION                ❌
CAMERA                  ❌
MICROPHONE              ❌
CONTACTS                ❌
SMS                     ❌
PHONE                   ❌
ACCOUNTS                ❌
```

可能保留：

```text
VIBRATE
```

只用于震动。

任何新增权限都必须先更新本规格。

---

## 25. 网络原则

V1：

```text
Network = 0
```

以下依赖默认视为违规：

```text
OkHttp
Retrofit
Firebase
Crashlytics
Ads SDK
Remote Config
在线 WebView
```

---

## 26. 隐私

App 不收集：

- 游戏列表
- ROM 名称
- 使用时间
- IP
- Crash log
- 用户 ID
- 设备 ID

Release 默认不上传任何数据。

---

## 27. Native 安全

Release/测试应考虑：

```text
FORTIFY
stack protector
RELRO
PIE
NX
ASan
UBSan
corrupted ROM smoke tests
```

Native crash 不得导致存档损坏。

---

## 28. 依赖与供应链

所有依赖固定版本。

禁止：

```text
+
latest
SNAPSHOT
```

mGBA 固定：

```text
release + commit SHA
```

Gradle 使用 Version Catalog 与 Dependency Locking。

不使用来源不明的预编译 native binary。

---

## 29. Release 安全

正式 APK 只能使用正式 keystore。

禁止：

- debug key
- test key
- 公共 key

每次 Release 记录：

```text
APK SHA-256
AAB SHA-256
Signing Certificate SHA-256
Git Commit
mGBA Commit
Build Version
```

---

## 30. V1 必须完成

| 类别 | 功能 |
|---|---|
| ROM | `.gba` |
| ROM | `.zip` 单 ROM |
| Library | 游戏库、最近游戏 |
| Core | mGBA |
| Save | SRAM、Autosave、4 State、Quick Save/Load |
| Playback | Pause、Reset、Fast Forward、Rewind |
| Input | Touch、USB/Bluetooth Gamepad、自定义布局 |
| Video | Original、Sharp、GBA Color、LCD |
| Orientation | 横屏、竖屏 |
| Audio | 低延迟音频 |
| GBA | RTC、Rumble、Tilt、Gyro、Solar |
| Misc | Screenshot |
| Security | No Internet / No Analytics / No Ads |

---

## 31. V1 明确不包含

```text
GB / GBC
Wi-Fi Link
Bluetooth Link
Cloud Sync
RetroAchievements
Cheat database
在线 ROM
在线封面
Shader 下载
插件系统
SkyEmu
用户账号
录像
直播
```

Cheat 与 Patch 可进入 V1.x。

---

## 32. UI 页面

V1 主要 Surface：

```text
Library
Player
Settings
```

辅助：

```text
Save State Sheet
Pause Sheet
Control Editor
```

不建立大量页面。

---

## 33. 默认体验

```text
首次启动
↓
添加游戏
↓
系统文件选择器
↓
进入游戏库
↓
点击
↓
立即开始
```

重新打开：

```text
继续游戏
↓
自动加载 ROM
↓
恢复 Autosave
↓
继续
```

---

## 34. 性能原则

主要目标：

```text
arm64-v8a
现代主流 Android 手机
```

V1 不为 x86、32-bit ARM、Android TV、ChromeOS 做专项优化。

---

## 35. 测试 ROM

仓库不得包含商业 ROM。

使用：

- Homebrew
- Public domain ROM
- 自制测试 ROM
- GBA hardware test suites

---

## 36. 测试层级

```text
Unit Tests
Android Instrumentation
JNI Tests
Native Tests
Save/Load Regression
Lifecycle Tests
Corrupted Input Tests
Controller Tests
Renderer Tests
```

核心升级必须运行 Compatibility Regression Suite。

---

## 37. Save Regression 是最高优先级

必须长期保证：

```text
旧版 .sav → 新版 App → 正常读取
```

State 可以版本不兼容，但必须提示且保留旧文件。

---

## 38. CI

每次 PR 至少：

```text
Kotlin compile
Unit tests
Android lint
CMake build
Native tests
JNI tests
Dependency audit
License check
Debug APK build
```

---

## 39. 开发阶段

### Phase 0 — Foundation

```text
Repo / Gradle / Compose / NDK / CMake / mGBA / CI / License
```

### Phase 1 — Core Bring-up

```text
加载 GBA / 运行 mGBA / Framebuffer / 基础音频 / 基础按键
```

### Phase 2 — Persistence

```text
SRAM / Save State / Autosave / Quick Save / Quick Load / Resume
```

### Phase 3 — Player Experience

```text
Landscape / Portrait / Touch / Gamepad / Fast Forward / Rewind / Screenshot
```

### Phase 4 — Renderer

```text
Original / Sharp / GBA Color / LCD / Frame pacing
```

### Phase 5 — GBA Hardware

```text
RTC / Rumble / Tilt / Gyro / Solar
```

### Phase 6 — UX Polish

```text
Library / Recent / Save thumbnails / Control Editor / Settings / Quick Resume
```

### Phase 7 — Security Hardening

```text
Manifest audit / Dependency audit / Sanitizer / Malformed ROM / ZIP protection / Signing / SBOM
```

### Phase 8 — V1 RC

冻结功能，只修：

```text
Bug / Crash / Data loss / Security / Compatibility / UX blocker
```

---

## 40. V1 发布门槛

以下任一失败，不得发布：

- ROM 无法稳定运行
- SRAM 可能丢失
- Autosave 不可靠
- 持续爆音或明显输入卡顿
- Release 使用 Debug 签名
- Manifest 出现 INTERNET
- 出现 Ads / Analytics
- 存在未知二进制依赖
- 严重 Native crash
- 升级导致旧 Save 无法读取

---

## 41. Definition of Done

功能只有满足：

```text
实现
+ 测试
+ 错误处理
+ 生命周期处理
+ 文档
+ 无新增危险权限
+ Release Build 可用
```

才算完成。

---

## 42. Codex 开发约束

Codex 每次开发必须：

1. 先读 `PROJECT_SPEC.md`
2. 不擅自扩大范围
3. 不擅自增加权限
4. 不加入网络依赖
5. 不加入广告 / Analytics
6. 不绕过测试
7. 不使用未知预编译二进制
8. 不大改 mGBA upstream
9. 重大架构变化写 ADR
10. 存档格式变化必须 migration + 兼容测试
11. Release 不得使用 Debug 签名
12. 每阶段输出实施报告

---

## 43. 产品成功标准

优先级：

```text
1. 游戏稳定运行
2. 存档不丢
3. 打开即可继续
4. 控制舒服
5. 声音稳定
6. 画面舒服
7. 设置简单
8. 安全透明
9. 再谈扩展
```

如果一个功能显著增加复杂度、安全风险和用户理解成本，却不能明显提升体验，则默认不加入。

---

## 44. 最终定位

GBA Lite 不追求成为“功能最多的 Android 模拟器”，而是：

> 一个开源、可审计、完全离线、无广告、无账号、无多余权限，同时具备现代存档、回溯、滤镜和手柄体验的 GBA 模拟器。

用户目标路径：

```text
添加游戏 → 点一下 → 玩
```
