# GBA Lite — ARCHITECTURE.md

**文档版本：** v0.1  
**适用阶段：** Phase 0 起  
**优先级：** 次于 `PROJECT_SPEC.md`，高于阶段实施文档

---

## 1. 文档目的

本文件定义 GBA Lite 的正式软件架构。

所有实现必须遵守：

1. UI 不直接依赖 mGBA。
2. 模拟核心可替换。
3. 视频、音频、输入、存档、生命周期彼此解耦。
4. V1 完全离线。
5. 不为了快速实现而跨越模块边界。
6. 存档安全优先。
7. Native 层尽可能薄。
8. 高风险系统能力必须显式隔离。

如果本文件与 `PROJECT_SPEC.md` 冲突，以 `PROJECT_SPEC.md` 为最高优先级。

---

## 2. 总体分层

```text
┌───────────────────────────────────────────┐
│                Presentation               │
│ Jetpack Compose                           │
│ Library / Player / Settings / Sheets      │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│              Application Layer            │
│ Session / Library / Save / Settings       │
│ Lifecycle / Input routing                 │
└─────────────────────┬─────────────────────┘
                      │
                      ▼
┌───────────────────────────────────────────┐
│                Domain API                 │
│ EmulatorCore / Game / Save / Profiles     │
└─────────────────────┬─────────────────────┘
                      │
             ┌────────┼─────────┐
             │        │         │
             ▼        ▼         ▼
        Core mGBA  Renderer   Audio
             │
             ▼
            JNI
             │
             ▼
        mGBA Native
```

---

## 3. 依赖方向

允许：

```text
feature-player → emulator-session
emulator-session → core-api
core-mgba → core-api
renderer → core-api/common model
audio → core-api/common model
```

禁止：

```text
feature-player → core-mgba
feature-library → JNI
app → mGBA C API
renderer → Compose
audio → Compose
core-mgba → UI
```

---

## 4. Gradle 模块

推荐：

```text
:app
:core-api
:core-mgba
:emulator-session
:renderer
:audio
:input
:storage
:data
:feature-library
:feature-player
:feature-settings
:common-ui
```

Phase 0/1 可以暂时不创建所有 Feature 模块，但最终依赖方向必须保持一致。

Phase 0 最低需要：

```text
:app
:core-api
:core-mgba
:feature-player
```

---

## 5. `core-api`

这是最关键的稳定边界。

不得包含：

- mGBA Header
- `mCore`
- JNI 类型
- Android View
- OpenGL Surface
- Oboe 类型

建议核心接口：

```kotlin
interface EmulatorCore : AutoCloseable {
    val capabilities: EmulatorCapabilities

    fun loadGame(source: GameSource): LoadResult

    fun start()
    fun pause()
    fun resume()
    fun reset()
    fun stop()

    fun setButton(button: GbaButton, pressed: Boolean)

    fun setFastForward(multiplier: Float)

    fun flushSaveRam(): SaveRamResult

    fun saveState(target: StateTarget): StateResult
    fun loadState(source: StateSource): StateResult

    fun beginRewind()
    fun endRewind()

    fun setTilt(x: Float, y: Float)
    fun setGyro(value: Float)
    fun setSolarLevel(level: Float)

    override fun close()
}
```

能力模型：

```kotlin
data class EmulatorCapabilities(
    val rewind: Boolean,
    val fastForward: Boolean,
    val rtc: Boolean,
    val rumble: Boolean,
    val tilt: Boolean,
    val gyro: Boolean,
    val solar: Boolean,
)
```

未来 SkyEmu 应实现同一接口。

---

## 6. GameSource

ROM 来源不应被假设成任意文件路径。

建议：

```kotlin
sealed interface GameSource {
    data class UriSource(val uri: Uri) : GameSource
    data class FileDescriptorSource(
        val fd: Int,
        val length: Long?,
        val displayName: String?
    ) : GameSource
}
```

Android 层负责：

```text
SAF URI
↓
ParcelFileDescriptor
↓
受控 FD
↓
Native
```

Native 不自行遍历用户文件系统。

---

## 7. JNI 边界

JNI 只负责：

```text
create/destroy core
load ROM
run/pause/reset
input
frame access
audio access
save/state
sensor values
```

JNI 不负责：

- UI
- Room
- DataStore
- 文件选择器
- 页面生命周期
- 导航
- 权限弹窗
- 游戏库

建议 Native Handle：

```kotlin
@JvmInline
value class NativeCoreHandle(val value: Long)
```

JNI 禁止暴露裸指针给 UI。

---

## 8. Native 实例生命周期

每个 Emulator Session 对应一个 Native Core 实例。

```text
Session created
↓
nativeCreate()
↓
load ROM
↓
run
↓
pause/resume
↓
flush SRAM
↓
nativeDestroy()
```

禁止：

- 全局单例 mGBA Core
- 多页面共享裸 Native 指针
- 未关闭 Core 即加载新 ROM

---

## 9. 模拟线程模型

模拟循环不得运行在 Main Thread。

```text
UI Thread
│
├─ input events
│
└─ state observation
     │
     ▼
Emulation Thread
│
├─ mGBA frame execution
├─ audio sample production
└─ framebuffer production
```

原则：

- Compose 不等待每帧 JNI 调用完成
- UI 与模拟循环之间使用线程安全状态
- Native 不回调复杂 UI 逻辑
- 避免每个像素/音频样本跨 JNI

---

## 10. Frame Pipeline

GBA 原生分辨率：

```text
240 × 160
```

推荐：

```text
mGBA framebuffer
↓
Native buffer
↓
OpenGL texture upload / shared buffer
↓
Shader
↓
Surface
```

禁止每帧：

```text
framebuffer → Bitmap → Compose Image
```

Phase 1 先实现简单 OpenGL 纹理上传。

---

## 11. Renderer

`renderer` 独立于具体核心。

输入：

```text
FrameBuffer
width
height
pixelFormat
timestamp/frame index
```

输出：

```text
Surface
```

V1 最终支持：

```text
Original
Sharp
GBA Color
LCD
```

Phase 1 只实现 `Original`。

---

## 12. Shader

Shader 属于 App 资源：

```text
shaders/
├── original/
├── sharp/
├── gba_color/
└── lcd/
```

V1 禁止：

- 网络下载 Shader
- 用户任意导入 Shader
- Shader 插件系统

每个 Shader 建议记录：

```text
id
version
displayName
requiredGlesVersion
```

---

## 13. Audio Pipeline

推荐：

```text
mGBA PCM
↓
native ring buffer
↓
Oboe callback
↓
AAudio
```

原则：

- Audio callback 不做磁盘 IO
- 不做 JNI 高频对象分配
- 不写日志
- 不访问 Room/DataStore
- buffer underrun 仅记录本地 debug 信息

Phase 1 只需建立可用 PCM → Oboe 路径。

---

## 14. Input Architecture

统一按钮：

```kotlin
enum class GbaButton {
    A, B, L, R, START, SELECT,
    UP, DOWN, LEFT, RIGHT
}
```

输入来源：

```text
Touch
USB gamepad
Bluetooth gamepad
Keyboard
```

全部转为：

```text
InputEvent
↓
InputRouter
↓
EmulatorCore.setButton()
```

不要让 mGBA 直接理解 Android KeyCode。

---

## 15. Touch

触控层只负责：

```text
hit test
visual feedback
button state
```

不得管理 ROM、Save State 或直接调用 JNI。

---

## 16. EmulatorSession

`emulator-session` 是核心业务模块。

职责：

```text
创建 core
加载 ROM
RUNNING/PAUSED 状态
前后台生命周期
autosave 协调
SRAM flush
退出顺序
异常恢复
```

建议状态：

```kotlin
sealed interface EmulatorSessionState {
    data object Empty : EmulatorSessionState
    data object Loading : EmulatorSessionState
    data object Ready : EmulatorSessionState
    data object Running : EmulatorSessionState
    data object Paused : EmulatorSessionState
    data object Suspended : EmulatorSessionState
    data class Error(val reason: SessionError) : EmulatorSessionState
    data object Closed : EmulatorSessionState
}
```

---

## 17. 生命周期规则

进入后台：

```text
pause
↓
flush SRAM
↓
autosave（Phase 2）
↓
mark session suspended
```

返回前台：

```text
resume surface/audio
↓
resume emulator
```

退出游戏：

```text
pause
↓
flush SRAM
↓
autosave
↓
stop audio
↓
release renderer
↓
close core
```

---

## 18. Storage

持久化由 `storage` 负责。

Native Core 不决定 Android 文件路径。

建议：

```kotlin
interface SaveStorage {
    suspend fun openSaveRam(gameId: GameId): SaveHandle
    suspend fun writeState(...)
    suspend fun readState(...)
    suspend fun writeScreenshot(...)
}
```

---

## 19. Game ID

推荐：

```text
SHA-256(ROM)
```

数据库保存：

```text
gameId
displayName
romHash
romUri
lastPlayedAt
playTime
```

文件名不能作为唯一标识。

---

## 20. Room 与 DataStore

Room 存元数据，不存大型二进制 State。

建议 Entity：

```text
GameEntity
SaveStateEntity
RecentSessionEntity
InputProfileEntity
```

DataStore 存：

```text
display mode
audio enabled
fast-forward default
touch opacity
touch scale
orientation preference
```

ROM、State、SRAM 不放 DataStore。

---

## 21. ZIP

V1 支持：

```text
.zip 内单个 .gba
```

规则：

- 不向任意外部目录解压
- 禁止 `../`
- 限制最大解压大小
- 限制 Entry 数
- 多个 GBA 时要求选择或拒绝
- 非 GBA 内容忽略或拒绝

---

## 22. 网络隔离

V1 Manifest 不允许：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Gradle 默认禁止：

```text
OkHttp
Retrofit
Firebase
Ads SDK
Analytics SDK
Crashlytics
```

如果 transitive dependency 带入网络库，必须评估并尽量移除。

---

## 23. Logging

Release：

- 不上传日志
- 不包含 ROM 路径
- 不记录 Save 内容
- 不记录可识别用户的信息

Debug 本地日志可记录：

```text
core init
ROM load result
frame/audio status
native errors
```

---

## 24. Build Variants

建议：

```text
debug
release
asan
```

禁止 release 使用 debug keystore。

---

## 25. mGBA 集成

目标：

```text
third_party/mgba
↓
CMake
↓
只链接所需核心
↓
MgbaCoreAdapter
```

不引入：

- Qt frontend
- SDL frontend
- Libretro frontend
- 不需要的平台代码

只使用 mGBA Core，不移植桌面 UI。

---

## 26. mGBA 修改策略

优先级：

1. 不改 upstream
2. Adapter 解决
3. Build patch
4. 必须修改时做最小 patch
5. 每个 patch 记录 ADR 或 `patches/README.md`

禁止为了短期方便大改 mGBA。

---

## 27. Error Handling

核心错误转为结构化结果，例如：

```kotlin
sealed interface LoadResult {
    data object Success : LoadResult
    data class Unsupported(val reason: String) : LoadResult
    data class Corrupted(val reason: String) : LoadResult
    data class IoError(val cause: Throwable?) : LoadResult
    data class NativeError(val code: Int) : LoadResult
}
```

Native crash 不能成为正常错误处理机制。

---

## 28. Testing Architecture

Kotlin：

```text
session state machine
input mapping
storage
database
settings
```

JNI：

```text
create/destroy
invalid handle
load test ROM
frame production
button injection
```

Native：

```text
wrapper
buffer bounds
ROM errors
state transitions
```

Instrumentation：

```text
SAF
Activity lifecycle
surface recreation
orientation changes
process restore
```

---

## 29. Phase 0/1 架构冻结项

Phase 0/1 完成前必须确认：

- `EmulatorCore` 稳定
- JNI 不泄漏 mGBA API
- Renderer 不依赖 mGBA
- Audio 不依赖 Compose
- Player 不直接 JNI
- App 无 INTERNET
- mGBA commit 固定
- Homebrew ROM 能稳定运行

---

## 30. 初始 ADR

应创建：

```text
docs/adr/
├── ADR-001-mgba-as-production-core.md
├── ADR-002-offline-v1.md
├── ADR-003-core-adapter-boundary.md
└── ADR-004-opengl-renderer.md
```

---

## 31. 架构违规示例

违规：

```kotlin
nativeMgbaPressA()
```

直接在 Compose 中调用。

正确：

```text
Compose
↓
InputRouter
↓
EmulatorSession
↓
EmulatorCore
↓
MgbaCoreAdapter
↓
JNI
```

另一个违规：

```text
UI 直接读取 save.sav
```

正确：

```text
UI
↓
SaveRepository
↓
SaveStorage
```

---

## 32. 核心原则总结

长期可维护性依赖：

```text
UI ≠ Emulator Core
Renderer ≠ Emulator Core
Storage ≠ Emulator Core
Android ≠ mGBA
```

任何为了少写几行代码而打破这些边界的实现，都应视为技术债。
