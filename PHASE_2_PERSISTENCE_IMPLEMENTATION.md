# GBA Lite — Phase 2 Persistence 实施任务书

**文档版本：** v0.1  
**执行范围：** Phase 2 — Persistence  
**执行者：** Codex / 开发代理  
**最高约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`  
**前置验收：** `docs/reports/PHASE_0_1_REPORT.md` 状态必须为 `READY`

---

## 1. 本轮目标

本轮只解决一个核心问题：

> **让用户的 GBA 游戏进度可以被可靠保存、恢复，并在常见生命周期变化、退出、后台、Activity 重建与异常中尽可能不丢失。**

Phase 2 完成后，应用应从“只能临时运行游戏的开发版本”升级为：

> **具备基础持久化能力、可以安全保存正常游戏进度，并支持基础 Save State / Autosave / Quick Resume 的开发版本。**

本轮不追求完整产品体验，也不进入 Phase 3+ 的 UI、Shader、Rewind、手柄、传感器等工作。

---

## 2. 当前已确认基线

本轮必须以现有 Phase 0/1 成果为基础，不得破坏：

```text
Android + Compose
SAF ROM 选择
mGBA 源码构建
EmulatorCore / Session 边界
JNI
独立 Emulation Thread
GLES 原始画面
Oboe 音频
基础 GBA 按键
前后台 pause/resume
```

当前项目安全基线：

```text
0 Android runtime permissions
No INTERNET
No Analytics
No Ads
No Firebase
No OkHttp / Retrofit
No online WebView
No unknown emulator binary
```

当前核心基线：

```text
mGBA 0.10.5
固定 commit
未修改 upstream
arm64-v8a only
```

Phase 2 不得擅自升级：

- mGBA；
- Oboe；
- Kotlin；
- AGP；
- Gradle；
- NDK；
- CMake；
- Compose BOM；

除非 persistence 实现确实被现有版本阻塞，并且必须通过 ADR 明确记录。

---

## 3. Phase 2 的最高原则

本阶段优先级：

```text
1. SRAM / FLASH / EEPROM 安全
2. 写入原子性
3. 数据不损坏
4. 生命周期一致性
5. 向前兼容
6. Save State
7. Autosave
8. Quick Resume
9. UI 便利性
```

任何时候：

> **正常游戏存档（battery save / SRAM / FLASH / EEPROM）优先级高于 Save State。**

Save State 可以因为模拟核心版本变化而失效。

正常 `.sav` 不允许因为 App 更新而被破坏。

---

## 4. 本轮禁止提前实现

本轮禁止实现：

```text
Rewind
Fast Forward UI
Shader / Sharp / GBA Color / LCD
完整游戏库美化
封面下载
在线封面
Cheats
IPS / UPS / BPS patch
Controller profile editor
USB/Bluetooth gamepad improvements
RTC / Tilt / Gyro / Solar / Rumble
Local Link
Network Link
SkyEmu
Cloud Sync
RetroAchievements
账号系统
在线更新
录像
直播
```

如果某项只为 Phase 2 测试所需，必须明确标记：

```text
TEST ONLY
```

不得演变为正式 Phase 3+ 功能。

---

## 5. 本轮必须新增/完善的模块

建议在现有项目中新增或正式启用：

```text
storage/
data/
emulator-session/
```

必要时新增：

```text
save-domain/
```

但不要为了模块数量而过度拆分。

推荐职责：

```text
storage
├── SRAM 文件
├── State 文件
├── Autosave 文件
├── Screenshot 文件（接口/占位）
└── Atomic file operations

data
├── Game metadata
├── Save State metadata
└── Last Session metadata

emulator-session
├── 生命周期
├── flush SRAM
├── save/load state orchestration
├── autosave rotation
└── quick resume
```

---

## 6. `core-api` 扩展

Phase 2 允许扩展 `EmulatorCore`，但仍不得泄漏 mGBA 类型。

建议新增：

```kotlin
interface EmulatorCore : AutoCloseable {
    fun loadGame(source: GameSource): LoadResult

    fun start()
    fun pause()
    fun resume()
    fun reset()
    fun stop()

    fun setButton(button: GbaButton, pressed: Boolean)

    fun flushSaveRam(target: SaveRamTarget): SaveRamResult
    fun loadSaveRam(source: SaveRamSource): SaveRamResult

    fun saveState(target: StateTarget): StateResult
    fun loadState(source: StateSource): StateResult

    override fun close()
}
```

如果 mGBA 更适合采用：

```text
core owns save memory
storage owns file path
adapter imports/exports bytes
```

也允许。

但要求：

> **文件系统路径与 Android Storage 决策不得进入 mGBA core。**

---

## 7. Game ID

Phase 2 必须正式建立稳定的 Game ID。

推荐：

```text
Game ID = SHA-256(ROM bytes)
```

要求：

- 同一 ROM 改文件名后仍是同一个 Game ID；
- 同名但内容不同的 ROM 必须区分；
- ROM hash 不依赖文件修改时间；
- 不使用 Android URI 作为 Game ID；
- 不使用 display name 作为 Game ID。

Game ID 建议：

```text
hex lowercase SHA-256
```

数据库可另外保存：

```text
displayName
romUri
romHash
lastPlayedAt
```

---

## 8. ROM Hash 计算

Hash 计算必须：

- 使用受控 SAF 输入；
- 在后台线程；
- 不阻塞 Compose Main Thread；
- 有明确大小限制；
- 错误时不创建不完整 Game Record；
- 计算完成后复用，不每次启动重新全量计算。

如果 URI 内容发生变化：

应重新计算并视为新 ROM。

---

## 9. 正常游戏存档模型

正常游戏存档必须与 Save State 分开。

建议文件：

```text
files/
└── saves/
    └── <game-id>.sav
```

建议同时保留：

```text
<game-id>.sav
<game-id>.sav.bak
```

禁止：

```text
把 State 文件当成正常存档
把正常存档嵌入 Room Blob
只保留内存副本
```

---

## 10. SRAM / FLASH / EEPROM

Phase 2 必须确认并支持 mGBA 的正常 battery save 行为。

至少覆盖：

```text
SRAM
FLASH
EEPROM
```

本项目上层统一称为：

```text
Save RAM
```

不要求 UI 区分底层类型。

---

## 11. 正常存档导入时机

加载 ROM 时：

```text
resolve Game ID
↓
open/create save file
↓
load existing .sav if present
↓
attach/import into core
↓
start emulation
```

如果 `.sav` 不存在：

```text
正常启动新游戏
```

不得把“不存在存档”视为错误。

---

## 12. SRAM 写入时机

至少触发：

### A. App 进入后台

```text
pause
↓
flush save RAM
↓
persist atomically
```

### B. 返回游戏库 / 正常退出

```text
pause
↓
flush save RAM
↓
persist atomically
↓
autosave
↓
close core
```

### C. 定期安全点

建议：

```text
每 30–60 秒
```

但不能：

- 每帧写磁盘；
- 高频 fsync；
- 明显影响游戏流畅性。

具体策略由实现选择，并记录测试结果。

---

## 13. SRAM 原子写入

禁止：

```text
直接覆盖 game.sav
```

建议流程：

```text
1. 导出当前 Save RAM
2. 写入 <game-id>.sav.tmp
3. flush
4. fsync（在 Android/文件系统允许范围内）
5. 基本校验
6. 旧 game.sav → game.sav.bak
7. atomic rename/replace tmp → game.sav
8. 成功后更新 metadata
```

如果平台文件系统无法保证某种原子语义：

必须在 ADR / 报告中说明实际保证范围。

---

## 14. 写入失败策略

任何存档写入失败：

不得：

- 删除当前旧 `.sav`；
- 删除 `.bak`；
- 把空文件覆盖正式存档；
- 静默忽略。

必须：

```text
保留最后已知有效版本
+
返回结构化错误
+
用户可看到“保存失败”提示
```

---

## 15. `.sav` 基本校验

不得假设所有游戏存档固定大小。

可做：

- 非空检查；
- 上限限制；
- 导出结果与 core 返回值检查；
- 写入后文件长度一致性；
- 必要的 checksum 仅用于本项目写入校验。

禁止自行“修复”用户 `.sav` 内容。

---

## 16. Save State 目录

建议：

```text
files/
└── states/
    └── <game-id>/
        ├── slot-1.state
        ├── slot-1.json
        ├── slot-1.webp
        ├── slot-2.state
        ├── slot-2.json
        └── ...
```

V1 规划：

```text
4 个手动 Slot
```

Phase 2 必须完成这 4 个 Slot 的底层能力。

---

## 17. Save State 元数据

每个 State 必须记录：

```json
{
  "schemaVersion": 1,
  "gameId": "<sha256>",
  "core": "mgba",
  "coreVersion": "0.10.5",
  "coreCommit": "<pinned-sha>",
  "stateVersion": "<if available>",
  "createdAt": "...",
  "appVersion": "0.1.x",
  "slot": 1,
  "kind": "manual"
}
```

允许增加：

```text
playTime
romDisplayName
stateSize
stateSha256
```

不得记录：

- 用户账号；
- 网络标识；
- 设备唯一 ID。

---

## 18. Save State 截图

本阶段目标：

> Save State 必须预留截图接口；如果现有 FrameSource 足够稳定，建议直接实现截图。

推荐：

```text
slot-1.webp
```

若 Phase 2 实现截图：

要求：

- 来源为当前 framebuffer；
- 不阻塞模拟线程过久；
- 不通过网络；
- 不写 EXIF 定位；
- 不影响 State 本体写入成功。

截图失败：

> 不得导致 State 保存失败。

---

## 19. Save State 原子写入

State 同样禁止直接覆盖。

建议：

```text
slot-1.state.tmp
↓
write
↓
flush
↓
optional checksum
↓
atomic replace
```

元数据和 State 要保持一致。

建议写入顺序：

```text
state temp
metadata temp
screenshot temp
↓
state commit
metadata commit
screenshot commit
```

如果最后部分失败：

必须能够识别并清理孤儿 temp 文件。

---

## 20. State 兼容性

加载前必须验证：

```text
gameId
core
coreVersion
stateVersion（如有）
```

如果明显不兼容：

不得静默加载。

返回：

```text
INCOMPATIBLE_STATE
```

UI 提示：

> 该即时存档由不同的模拟核心或版本创建，当前版本无法安全读取。

旧 State 不得自动删除。

---

## 21. State 与 ROM 不匹配

如果：

```text
State gameId != 当前 ROM gameId
```

必须拒绝加载。

不得仅凭文件名加载。

---

## 22. Quick Save / Quick Load

Phase 2 必须实现基础能力。

建议：

```text
Quick Save = 独立特殊 State
Quick Load = 加载该特殊 State
```

例如：

```text
quick.state
quick.json
quick.webp
```

不得偷偷覆盖：

```text
slot-1.state
```

---

## 23. Autosave

Phase 2 必须实现：

```text
Autosave A
Autosave B
Autosave C
```

循环覆盖。

目录：

```text
autosaves/
└── <game-id>/
    ├── auto-a.state
    ├── auto-a.json
    ├── auto-b.state
    ├── auto-b.json
    ├── auto-c.state
    └── auto-c.json
```

---

## 24. Autosave 触发

至少：

### A. App 进入后台

### B. 正常退出 Player

### C. 返回 Library

可选：

### D. 周期性 Autosave

如果加入周期性 Autosave：

必须避免影响性能。

建议频率：

```text
数分钟级
```

而不是秒级。

---

## 25. Autosave 轮换

不能：

```text
永远只覆盖 auto-a
```

推荐：

```text
A → B → C → A
```

metadata 中记录：

```text
createdAt
sequence
```

Quick Resume 时选择：

> 最新且校验有效的 Autosave。

如果最新损坏：

尝试前一个。

---

## 26. Quick Resume

Phase 2 必须实现基础 Quick Resume。

重新打开 App 后：

```text
读取 LastSession
↓
确认 ROM URI 仍可访问
↓
确认 Game ID
↓
寻找最新有效 Autosave
↓
加载 ROM
↓
加载正常 .sav
↓
加载 Autosave
↓
恢复游戏
```

如果 Autosave 不可用：

可退化为：

```text
只加载 ROM + 正常 .sav
```

绝不能因为 Autosave 问题阻止用户访问正常存档。

---

## 27. Last Session

建议保存：

```text
gameId
romUri
lastPlayedAt
autosaveId
sessionClosedCleanly
```

不要存：

- Native pointer；
- File descriptor；
- Activity reference。

---

## 28. URI 权限持久化

如果当前 SAF 流程允许：

应使用：

```text
takePersistableUriPermission
```

仅针对用户明确选择的 ROM。

要求：

- 不申请全盘文件权限；
- URI 失效时有清晰错误；
- 不自动扫描用户存储。

---

## 29. Activity 重建

Phase 0/1 当前 Activity recreate 会关闭旧 core。

Phase 2 必须明确处理：

```text
orientation / configuration change
Activity recreation
```

至少保证：

```text
正常 Save RAM 已落盘
+
可恢复游戏
```

可选择：

### 方案 A

保存临时 Autosave 后重建 Session。

### 方案 B

Session 提升到更稳定生命周期容器。

若采用 B：

必须记录 ADR。

不得为了避免重建而引入：

- 全局静态 Native Core；
- Activity 泄漏；
- 不受控单例。

---

## 30. Process Death

Phase 2 不要求“进程被系统杀死后零损失恢复到最后一帧”。

但必须保证：

```text
最近一次正常 .sav
+
最近一次成功 Autosave
```

可用于恢复。

报告中不得宣称：

> “Process Death 完全无损”

除非有实测证据。

---

## 31. Room 数据模型

Phase 2 建议正式加入 Room。

最低：

```text
GameEntity
LastSessionEntity
SaveStateEntity
```

示例：

```text
GameEntity
- gameId
- displayName
- romUri
- romHash
- lastPlayedAt

SaveStateEntity
- id
- gameId
- slot
- kind
- createdAt
- coreVersion
- statePath
- screenshotPath
```

Room 只保存元数据。

禁止把 `.state` 或 `.sav` 作为大 Blob 存入数据库。

---

## 32. DataStore

可以保存：

```text
quick resume enabled
autosave enabled
```

但 V1 默认：

```text
Autosave = ON
Quick Resume = ON
```

Phase 2 不需要做完整 Settings UI。

可暂时内部固定默认值。

---

## 33. 用户 UI 范围

本阶段只允许最小 UI：

### Pause / Player

新增：

```text
即时存档
读取存档
Quick Save
Quick Load
```

### 启动页 / 最小首页

允许：

```text
继续上次游戏
选择 ROM
```

不要在 Phase 2 做完整游戏库视觉重构。

---

## 34. Save State UI

最低可做：

```text
Slot 1
Slot 2
Slot 3
Slot 4
```

如果截图已实现：

显示缩略图。

同时显示：

```text
日期 / 时间
```

不要求 Phase 2 完成最终视觉设计。

---

## 35. 错误处理

必须结构化区分：

```text
SAVE_RAM_WRITE_FAILED
SAVE_RAM_READ_FAILED
STATE_WRITE_FAILED
STATE_READ_FAILED
STATE_INCOMPATIBLE
STATE_ROM_MISMATCH
STATE_CORRUPTED
AUTOSAVE_FAILED
ROM_URI_REVOKED
STORAGE_IO_ERROR
```

不能所有情况都显示：

```text
Unknown error
```

---

## 36. 数据损坏策略

如果正式 `.sav` 损坏或不可读取：

流程建议：

```text
尝试 game.sav
↓
失败
↓
尝试 game.sav.bak
↓
若 backup 可用 → 恢复/提示
```

不得自动覆盖两个版本。

---

## 37. Temp 文件清理

App 启动时可清理：

```text
*.tmp
```

但必须：

- 只清理本项目内部目录；
- 不删除正式 `.sav` / `.state`；
- 对可能未完成的原子替换保持谨慎。

建议 temp 命名：

```text
<target>.tmp.<uuid>
```

---

## 38. 并发控制

禁止同时发生：

```text
flush SRAM
+
close core
```

而无序竞争。

也禁止：

```text
Autosave
+
Manual Save State
+
ROM switch
```

同时操作同一个 Core 导致未定义状态。

应由 `EmulatorSession` 串行协调持久化操作。

建议：

```text
Mutex / serialized command queue
```

---

## 39. Emulator Pause 策略

创建 Save State 时：

建议：

```text
pause core
↓
capture state
↓
resume core
```

或者使用 mGBA 官方安全 State API。

不得在不确认线程安全的情况下从 UI 线程直接读取 Core 内部状态。

---

## 40. 时间戳

所有 metadata 建议使用：

```text
UTC timestamp
```

UI 再按系统时区展示。

不要使用本地时间作为唯一排序依据。

---

## 41. 文件 schema 版本

所有自有 metadata：

```text
schemaVersion = 1
```

后续升级必须：

- 显式 migration；
- 不静默忽略未知版本；
- 保证老版本正常 `.sav` 不受 metadata schema 变化影响。

---

## 42. Phase 2 测试 ROM

继续使用：

- 当前自制 Homebrew bring-up ROM；
- 可新增合法测试 ROM；
- 不加入商业 ROM；
- 不加入 Nintendo BIOS。

---

## 43. 必须新增的测试

### A. Game ID

至少：

```text
same ROM + different filename → same Game ID
different ROM → different Game ID
```

### B. Save RAM

测试：

```text
new game → create save
existing save → load
write → restart → load same bytes
failed write → old save remains
backup fallback
```

### C. Atomic Write

模拟：

```text
write interrupted
tmp incomplete
replace failure
disk IO error（可模拟）
```

要求旧正式存档仍存在。

### D. Save State

```text
save slot
load slot
overwrite slot
wrong ROM
corrupt state
missing metadata
incompatible core metadata
```

### E. Autosave Rotation

验证：

```text
A → B → C → A
```

并检查时间顺序。

### F. Quick Resume

验证：

```text
play
↓
background / exit
↓
relaunch
↓
resume latest valid autosave
```

### G. Lifecycle

至少：

```text
background
foreground
Activity recreate
normal exit
ROM switch
```

### H. Native Resource

反复：

```text
load
save
close
reopen
```

不得残留 core handle / worker thread / open FD。

---

## 44. `.sav` 回归测试

从 Phase 2 开始建立长期固定回归资产。

至少保存：

```text
test-save-v1.sav
```

来源必须合法测试 ROM。

后续每个版本都验证：

```text
旧版 .sav
↓
当前 App
↓
可读取
↓
再次写入
↓
仍可读取
```

这是永久发布门。

---

## 45. State 兼容测试

State 需要记录 core version。

本阶段至少模拟：

```text
same version → load
fake old/different version → reject safely
```

不得删除不兼容 State。

---

## 46. Instrumentation 测试

至少新增：

```text
SAF URI persisted
game launch with existing .sav
background triggers persistence
Activity recreate preserves recoverability
relaunch shows Resume path
manual State save/load
```

---

## 47. Manifest 审计

Phase 2 完成后必须再次检查：

```text
Debug permissions = []
Release permissions = []
```

或若出现本项目明确允许的权限：

必须有文档依据。

默认目标仍是：

```text
0 permissions
```

不得因为持久化需求添加旧式存储权限。

---

## 48. Dependency Audit

Phase 2 新增 Room / DataStore 时：

必须固定版本并加入：

```text
Version Catalog
Dependency Lock
Verification metadata
```

继续保证：

```text
No Firebase
No Analytics
No Ads
No OkHttp
No Retrofit
No online SDK
```

---

## 49. 安全要求

Phase 2 新增了不可信 State 文件解析面。

因此：

- State 路径必须在 App 私有目录；
- 不接受任意外部 State 自动加载；
- State 大小设合理上限；
- metadata JSON 做字段校验；
- 不通过 State metadata 构造任意文件路径；
- 禁止 `../`；
- 所有外部 URI 都来自用户明确 SAF 选择。

---

## 50. Native Sanitizer

Phase 0/1 已预留 ASan / UBSan。

Phase 2 如果环境允许，至少执行一次：

```text
ASan or UBSan
```

覆盖：

```text
load ROM
save RAM
save State
load State
close
```

如果本轮仍无法执行：

必须在报告中保留为风险，不能写已完成。

---

## 51. 长时间基础稳定性

Phase 2 建议增加：

```text
30–60 min Homebrew/test ROM run
```

期间验证：

```text
periodic SRAM flush
background/foreground
manual State
Autosave
memory growth
audio/frame continuity
```

不需要商业 ROM。

---

## 52. Phase 2 验收标准

必须全部满足：

### Game identity

- [ ] Game ID 使用 ROM 内容 SHA-256
- [ ] 文件名变化不影响同 ROM Game ID

### Save RAM

- [ ] SRAM/FLASH/EEPROM 可持久化
- [ ] App 重启后正常存档可恢复
- [ ] 原子写入
- [ ] `.bak` fallback
- [ ] 写入失败不破坏旧 Save

### Save State

- [ ] 4 个手动 Slot
- [ ] State metadata
- [ ] ROM mismatch 拒绝
- [ ] incompatible State 安全拒绝
- [ ] corrupt State 不导致 App/Native 崩溃

### Quick Save

- [ ] Quick Save
- [ ] Quick Load

### Autosave

- [ ] A/B/C rolling autosave
- [ ] 最新有效 autosave 可识别
- [ ] 最新损坏时可 fallback

### Quick Resume

- [ ] 上次游戏可恢复
- [ ] ROM URI 失效有清晰错误
- [ ] Autosave 不可用时仍可用正常 `.sav`

### Lifecycle

- [ ] 后台保存
- [ ] 正常退出保存
- [ ] Activity recreate 后可恢复
- [ ] ROM switch 不污染上一游戏 Save

### Architecture

- [ ] UI 不直接 JNI
- [ ] Storage 不进入 mGBA core
- [ ] core-api 无 mGBA/Android 实现泄漏
- [ ] Session 串行协调存档操作

### Security

- [ ] 无 INTERNET
- [ ] 无 Ads/Analytics
- [ ] 无旧式存储权限
- [ ] 无未知 binary
- [ ] 依赖固定/锁定

### Test / Build

- [ ] JVM tests PASS
- [ ] Native tests PASS
- [ ] Instrumentation PASS
- [ ] lint 0 errors
- [ ] Debug APK 构建成功
- [ ] unsigned Release APK 构建成功

---

## 53. Phase 2 不要求完成

Phase 2 READY 不代表：

```text
V1 可发布
```

本轮不要求：

- Production signing；
- Store packaging；
- Shader；
- Rewind；
- 手柄完整支持；
- 传感器；
- 全量游戏兼容性；
- 所有 API 26–36 真机覆盖；
- 网络功能。

---

## 54. 完成后必须生成报告

文件：

```text
docs/reports/PHASE_2_PERSISTENCE_REPORT.md
```

必须包括：

### A. 实际完成

### B. 新增/修改目录

### C. 数据模型

列出：

```text
Game ID
Save RAM
State
Autosave
Last Session
Room tables
metadata schema
```

### D. 存档文件结构

输出实际 tree。

### E. 原子写入策略

说明：

```text
tmp
flush/fsync
backup
replace
error path
```

### F. mGBA Save/State API 使用方式

说明是否修改 upstream。

### G. 生命周期流程

列出：

```text
background
foreground
Activity recreate
exit
ROM switch
process relaunch
```

### H. Manifest

最终 merged permission 列表。

### I. Dependencies

新增依赖与锁定情况。

### J. 测试

完整列出：

```text
unit
native
JNI
instrumentation
lifecycle
save regression
atomic failure
state corruption
lint
build
```

### K. 实机验收

必须区分：

```text
自动测试
人工真机验收
```

### L. 已知问题

不得为了 READY 而隐藏问题。

### M. 风险

重点：

```text
save corruption
state compatibility
URI persistence
process death
native memory
old Android
```

### N. 未完成项

明确 Phase 3+。

### O. 是否允许进入 Phase 3

只能：

```text
READY
```

或：

```text
NOT READY
```

并说明理由。

---

## 55. 进入 Phase 3 的硬门槛

以下任何一项失败：

> **Phase 2 必须 NOT READY**

```text
正常 .sav 有损坏风险
写入失败会覆盖旧 Save
Game ID 不稳定
State 可以跨错误 ROM 加载
Autosave 会破坏正常 Save
Activity/退出存在明显数据丢失
新增 INTERNET
新增旧式存储权限
出现未知 native binary
绕过 Session 直接 UI → JNI
核心版本被静默升级
```

---

## 56. Git 提交建议

建议拆分：

```text
feat: add stable ROM game identity
feat: add save RAM persistence
feat: add atomic save storage
feat: add save state storage
feat: add quick save and quick load
feat: add rolling autosaves
feat: add session resume metadata
feat: add quick resume flow
test: add save persistence regression
test: add state compatibility checks
test: add persistence lifecycle tests
docs: add phase 2 persistence report
```

不要把整个 Phase 2 压成一个巨大 commit。

---

## 57. Codex 本轮执行提示

执行时必须遵守：

```text
先完整阅读 PROJECT_SPEC.md
再阅读 ARCHITECTURE.md
再阅读 PHASE_0_1_REPORT.md
最后执行 PHASE_2_PERSISTENCE_IMPLEMENTATION.md
```

规则：

```text
只执行 Phase 2
不要提前进入 Phase 3+
不要升级 mGBA
不要添加 INTERNET
不要添加 Ads / Analytics
不要添加旧式存储权限
不要使用未知预编译二进制
不要为了省事让 UI 直接 JNI
不要牺牲 Save 安全换开发速度
```

若遇到重大技术决策：

> 新增 ADR 后再实施。

若某项功能无法在当前架构下安全实现：

> 优先保持数据安全和架构边界，不得通过临时危险方案绕过。

---

## 58. 本轮成功定义

Phase 2 成功不是：

> “已经有很多新功能”。

而是：

> **用户正常 GBA 存档可以可靠落盘；手动 State / Autosave / Quick Resume 可以稳定工作；生命周期变化不会轻易丢失进度；任何写入失败都优先保护最后一个有效存档。**

目标体验：

```text
玩游戏
↓
游戏内正常保存
↓
退出 App
↓
重新打开
↓
继续
```

以及：

```text
玩游戏
↓
App 后台 / 被系统回收
↓
重新进入
↓
从最近安全状态恢复
```

在满足以上目标并通过所有 Phase 2 发布门槛后，才允许进入 Phase 3。
