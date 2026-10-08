# GBA Lite — Phase 6 Library / App Experience 实施任务书

**文档版本：** v0.1  
**执行范围：** Phase 6 — Library / App Experience  
**执行者：** Codex / 开发代理  
**最高约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`  
**前置验收：** Phase 0–4 = READY；Phase 5 经最终人工验收后视为 READY；真实手机 Rumble 按 V1 零权限策略 `DEFERRED`，不得阻塞 Phase 6。

---

## 1. 本轮目标

Phase 6 只解决：

> **把已经成熟的“游戏播放器”整合成一个真正完整、极简、离线的 GBA 模拟器 App。**

核心路径：

```text
首页
→ Continue Last Game
→ Recent Games
→ Game Library
→ 点一次进入游戏
```

同时正式整理：

```text
ROM 导入
ZIP ROM
游戏详情
游戏时长
本地封面/缩略图
统一 Settings
空状态
Library 管理
```

目标是：第一次打开就知道怎么添加游戏；之后绝大多数情况下 1–2 次点击即可继续玩。

---

## 2. 产品定位

本阶段不是大型 ROM Frontend，也不做在线数据库。

坚持：

```text
简洁
低摩擦
完全离线
无广告
无追踪
存档安全
```

不追求：

```text
在线封面
账号
云同步
推荐流
资讯
社区
ROM 商店
```

---

## 3. 必须保留的既有能力

不得破坏：

```text
SAF ROM
Game ID = ROM SHA-256
mGBA 0.10.5
CoreAdapter / Session / JNI
GLES / Oboe / InputRouter
Battery Save
4 Manual States
Quick Save / Load
Autosave A/B/C
Quick Resume
Fast Forward
Rewind
Touch Layout
HID Controller
Original / Sharp / GBA Color / LCD
Fit / Integer
背景黑 / 白
RTC
Tilt
Gyro
Solar
Phase 5 外设设置
```

继续保持：

```text
No INTERNET
No Ads
No Analytics
No online cover
No online ROM
No cloud
No unknown binary
```

---

## 4. V1 零权限策略

Phase 6 默认继续：

```text
Debug permissions = []
Release permissions = []
```

不得为了 Library、ZIP、封面、截图或游戏时长新增：

```text
READ_EXTERNAL_STORAGE
WRITE_EXTERNAL_STORAGE
MANAGE_EXTERNAL_STORAGE
INTERNET
BLUETOOTH*
LOCATION
```

ROM 访问继续依赖 `Storage Access Framework`。

---

## 5. 本轮必须完成

```text
Home
Continue Last Game
Recent Games
Game Library
.gba import
.zip single-GBA import
Game Details
Play Time
Last Played
Local Artwork Strategy
Library Remove
Broken URI Recovery
Unified Settings
Empty States
Local Search
Sort
Basic Library Metadata
```

---

## 6. 本轮禁止提前实现

```text
联网封面
在线 ROM
Cheats
IPS / UPS / BPS
Local Link
Wi-Fi Link
Bluetooth Link
SkyEmu
Cloud Sync
RetroAchievements
账号
Remote metadata
ROM scraper
```

---

## 7. 顶层导航

建议正式收敛为：

```text
Home
Library
Settings
```

推荐结构：

```text
Home
├─ Continue
├─ Recent
└─ Add Game

Library
├─ All Games
├─ Search
└─ Sort

Settings
├─ Display
├─ Controls
├─ Audio
├─ Gameplay
├─ Peripherals
├─ Saves
└─ About
```

不要为了 Phase 6 重写整个 App Shell；以最小架构变化完成整合。

---

## 8. Home

Home 最高优先级是：

> “我现在想继续玩。”

建议：

```text
GBA Lite

[ Continue Last Game ]

Recent Games
[ Game A ]
[ Game B ]
[ Game C ]

[ Add Game ]
```

禁止 Banner、轮播、资讯、在线推荐。

---

## 9. Continue Last Game

必须复用 Phase 2 Quick Resume，不得创建第二套恢复逻辑。

流程：

```text
LastSession
→ validate ROM access
→ resolve Game ID
→ battery
→ latest valid autosave
→ resume
```

ROM URI 失效时提示重新选择原 ROM。

重新绑定只有：

```text
SHA-256 == 原 Game ID
```

才允许。

Hash 不一致时明确提示：

```text
选择的文件不是原来的游戏。
```

---

## 10. Recent Games

按：

```text
lastPlayedAt DESC
```

显示最近 3–5 个。

每个条目建议包含：

```text
Thumbnail
Display Name
Last Played
Play Time
```

点击直接进入游戏，不强制经过详情页。

---

## 11. Game Library

Library 是所有已导入 ROM 的本地索引。

建议 Grid 或紧凑 List。

每项：

```text
Local Image
Display Name
Last Played
Play Time
```

不要默认显示 SHA、URI、Core Version 等调试信息。

---

## 12. Library 数据模型

继续使用 Room，扩展现有 `GameEntity`。

建议字段：

```text
gameId
displayName
romUri
romHash
addedAt
lastPlayedAt
playTimeMs
localArtworkPath
```

若已有则复用，不重复。

Room 只存 metadata，不存 ROM Blob。

---

## 13. Game ID 规则

永久保持：

```text
Game ID = lowercase SHA-256(ROM bytes)
```

以下字段不得参与身份：

```text
Display Name
File Name
Artwork
Last Played
URI
```

Game ID 继续作为 Save / State / ROM snapshot 的唯一逻辑身份。

---

## 14. ROM Import

支持：

```text
.gba
.zip
```

入口仍为：

```text
ACTION_OPEN_DOCUMENT
```

禁止扫描整个设备。

流程：

```text
SAF select
→ validate
→ bounded read
→ ROM identity
→ App-private snapshot
→ Game record
→ Library
```

---

## 15. .gba Import

沿用 Phase 0/1 既有 ROM 安全限制：

```text
minimum plausible size
maximum 32 MiB
read timeout
basic header validation
```

不得为了 Library 新增宽松旁路。

---

## 16. ZIP Import

Phase 6 必须正式支持：

> **ZIP 内只允许一个有效 `.gba`。**

允许额外存在：

```text
README.txt
LICENSE
```

但 `.gba` 必须 exactly one。

多个 `.gba`：直接拒绝，不做复杂二级选择器。

---

## 17. ZIP 安全

必须防：

```text
../
absolute path
drive prefix
nested traversal
zip bomb
huge decompressed size
extreme compression ratio
duplicate entry
nested zip recursion
encrypted entry
corrupt central directory
```

禁止嵌套 ZIP。

---

## 18. ZIP Limits

必须设置常量限制：

```text
compressed input max
decompressed total max
entry count max
single ROM max = 32 MiB
compression ratio max
filename/path length max
```

实际数值必须记录到实现报告。

---

## 19. ZIP 解析原则

优先：

```text
streaming
bounded
fail closed
```

禁止 `extractAll()` 到公共目录。

如需 temp：

```text
App-private
random temp name
cleanup on failure
atomic final commit
```

---

## 20. ZIP Game ID

必须：

```text
SHA-256(extracted GBA bytes)
```

不是 ZIP 文件本身的 hash。

同一个 ROM：

```text
直接 .gba
ZIP 中 .gba
```

必须得到同一个 Game ID。

---

## 21. Duplicate Import

同一 Game ID 再导入：

```text
不创建第二条 Library 项
保留 Save / State / Play Time
必要时更新 source URI/snapshot
```

UI 提示：

```text
已在游戏库中
```

并提供“打开游戏”。

---

## 22. App-private ROM Snapshot

继续使用既有：

```text
files/roms/<game-id>.gba
```

运行时优先使用经过验证的私有 snapshot。

SAF URI 作为 source / relink 信息。

不要每次启动都重新从外部 URI 读取 ROM。

---

## 23. Source Change

如果 provider modification token 变化，或用户重新选择 ROM：

```text
重新 SHA-256
```

不同内容必须新建 Game ID。

禁止“同文件名即同游戏”。

---

## 24. Display Name

可做轻量文件名清理：

```text
remove .gba
_ / - → space
trim duplicate spaces
```

不要建立大型 ROM Name Database。

允许用户重命名显示名，但仅改变 metadata，不改变 Game ID / Save path。

---

## 25. Game Details

建议保持极简：

```text
[ Local Artwork ]

Game Name

Continue / Play

Last Played
Play Time

Save States
4 Slots

Game Settings
Remove from Library
```

不在默认界面展示 SHA/Core/GPIO debug。

---

## 26. Local Artwork Strategy

V1 不联网抓封面。

优先级：

```text
1. 最近 Manual State thumbnail
2. 最近 Screenshot
3. 最近 Autosave thumbnail（若已有）
4. 项目原创 cartridge placeholder
```

任何图片缺失都必须安全 fallback。

---

## 27. Placeholder

使用项目原创的简洁 GBA cartridge / pixel placeholder。

不得使用：

```text
Nintendo 商标素材
商业封面
My Boy / Pizza Boy 资源
```

---

## 28. Play Time

Phase 6 必须实现。

定义：

> 只统计 Core 实际在前台运行且未暂停的现实时间。

不统计：

```text
Pause Menu
Home / Background
Settings
ROM loading
Activity stopped
```

使用 monotonic clock。

---

## 29. Play Time 与 FF / Rewind

Fast Forward：统计现实时间，不统计模拟时间。

例如现实玩 10 分钟，其中 5 分钟是 4×：

```text
Play Time += 10 minutes
```

Rewind 不倒扣 Play Time。

---

## 30. Play Time 持久化

不要每帧写 DB。

建议：

```text
background
session end
periodic 30–60 sec safe point
```

与现有 Session checkpoint 协调。

---

## 31. Last Played

只在真正成功启动游戏后更新，建议：

```text
first valid emulated frame
```

不要打开详情页就更新。

---

## 32. Sort / Search

Library 至少支持：

```text
Recently Played
Name
Recently Added
Play Time
```

默认：Recently Played。

本地 Search：

```text
case-insensitive contains(displayName)
```

不增加复杂搜索依赖。

---

## 33. Empty States

首次启动：

```text
GBA Lite

还没有游戏

选择一个 GBA 游戏文件开始

[ 选择游戏 ]
```

搜索无结果：

```text
没有找到游戏
[ 清除搜索 ]
```

不要教程轮播或大段说明。

---

## 34. Broken Game Recovery

Private snapshot 丢失但 SAF URI 有效：

```text
rebuild snapshot
```

两者都失效：

```text
Library entry 保留
status = unavailable
```

用户可：

```text
Re-link ROM
Remove from Library
```

禁止自动删除 Saves。

---

## 35. Remove from Library

默认只移除：

```text
Library entry
ROM private snapshot
optional cached artwork
```

不得默认删除：

```text
Battery Save
Manual State
Quick State
Autosave
Screenshots
历史 Play Time 数据（可按实现选择保留）
```

如果实现“删除全部游戏数据”，必须是独立操作并二次确认。

---

## 36. Settings Consolidation

Phase 6 必须整理此前逐阶段加入的 Settings。

一级建议：

```text
Display
Controls
Audio
Gameplay
Peripherals
Saves
About
```

不要继续把所有设置塞进 Pause Menu。

---

## 37. Display Settings

复用 Phase 4：

```text
Original / Sharp / GBA Color / LCD
Fit / Integer
Background Black / White
```

不能维护第二份独立显示配置。

---

## 38. Controls

包含：

```text
Portrait layout
Landscape layout
Reset layout
Gamepad mapping info
```

Phase 6 不要求完整 Controller Remapping。

---

## 39. Audio

只暴露真实存在的能力。

如果没有成熟可切换选项：

> 不要为了 Settings 完整感造假开关。

---

## 40. Gameplay

可整合：

```text
Fast Forward default multiplier
Rewind enabled
Quick Resume enabled
Autosave enabled
```

仅在底层已经存在时暴露。

---

## 41. Peripherals

整合：

```text
RTC info
Tilt mode / calibration
Gyro mode
Solar auto/manual
```

Rumble 按 V1 产品决策显示为：

```text
真实震动当前未启用
```

不得显示为一个“坏掉的可切换开关”。

---

## 42. Saves

可包含：

```text
Quick Resume
Autosave
Save location info
```

Phase 6 不做 Cloud / Drive。

---

## 43. About

至少：

```text
GBA Lite version
Open Source Licenses
mGBA attribution
Privacy: Offline
```

可以说明：

```text
No ads
No analytics
No network
```

避免夸张营销文案。

---

## 44. Back Navigation

必须统一：

```text
Player → Pause Menu → Game
Game Details → Library
Settings child → Settings
Settings → previous
Home → system back exits
```

避免深层页 Back 直接退出 App。

---

## 45. Player → Library

退出 Player：

```text
pause
→ battery persist
→ autosave
→ playtime persist
→ close core
→ Library/Home
```

继续使用 Session 串行化。

---

## 46. App Cold Start

默认建议：

```text
Cold Start → Home
```

Home 提供 Continue。

若 `PROJECT_SPEC.md` 明确要求自动 Quick Resume，则以最高规格为准，不擅自覆盖。

---

## 47. Library Performance

至少用 100–500 条 fake metadata 做 UI 性能验证。

不得：

```text
每次 recomposition 读 ROM
每次列表刷新重新 hash
主线程同步 decode WebP
一次加载全部原图
```

---

## 48. Thumbnail Loading

必须后台 IO、有界内存。

如需要缓存：

```text
small bounded cache
```

不要无必要引入大型图片 SDK。

---

## 49. Room Migration

如 Room schema 升级：

```text
explicit migration
```

禁止：

```text
fallbackToDestructiveMigration
```

必须覆盖从现有 Phase 2/3/4/5 schema 升级。

旧 Game ID、Save、State、Settings 都必须保留。

---

## 50. ZIP Security Tests

必须覆盖：

```text
single valid .gba
gba + README
two .gba
no .gba
../evil.gba
/absolute.gba
nested/../../evil.gba
huge declared size
huge compression ratio
too many entries
corrupt central directory
truncated zip
encrypted entry
nested zip
very long filename
UTF-8 edge cases
duplicate entry
```

要求：

```text
No traversal
No OOM
No ANR
No crash
No partial GameEntity
```

---

## 51. ROM Import Tests

至少：

```text
same ROM direct + ZIP → same Game ID
same ROM renamed → same Game ID
different ROM same filename → different Game ID
duplicate import → one Library item
URI revoked → private snapshot remains playable
```

---

## 52. Library Tests

至少：

```text
add
remove
rename display name
recent order
name order
added order
playtime order
search
broken URI
re-link
```

---

## 53. Play Time Tests

必须：

```text
foreground running increments
pause does not increment
background does not increment
settings does not increment
FF counts real time
rewind counts real time
relaunch persists
```

JVM 使用 fake monotonic clock 做 deterministic test。

---

## 54. UI Instrumentation

至少：

```text
empty Home
add GBA
add ZIP
Home Recent
Continue
Library
Search
Sort
Game Details
Rename
Settings categories
Back navigation
Remove Library
Re-link ROM
```

---

## 55. Phase 2–5 全量回归

必须继续运行：

### Persistence

```text
Battery
Atomic generation
Backup fallback
4 Slots
Quick
Autosave
Quick Resume
Room migration
```

### Player

```text
Touch
HID
FF
Rewind
Orientation
Screenshot
Thumbnail
```

### Renderer

```text
Original
Sharp
GBA Color
LCD
Fit
Integer
Black/White
Surface rotation
```

### Peripherals

```text
RTC
Tilt manual path
Gyro manual path
Solar manual path
sensor registration lifecycle
```

Phase 5 物理传感器人工 PASS 作为历史证据保留，不需要 CI 模拟真实姿态。

---

## 56. Manifest / Dependency Audit

目标：

```text
Debug permissions = []
Release permissions = []
```

Phase 6 默认尽量 0 新第三方依赖。

ZIP 优先使用 Java/Android 标准库。

禁止：

```text
network SDK
cover scraper
analytics
ads
closed ROM DB SDK
```

---

## 57. GBA_TEST_ROM_CORPUS

如已存在 `GBA_TEST_ROM_CORPUS.md`，必须遵守其法律与 SHA 固定规则。

可新增原创：

```text
zip-import fixtures
library test ROM variants
```

不得使用商业 ROM 作为仓库测试资产。

---

## 58. Error UX

必须明确区分：

```text
无法读取游戏文件
ZIP 中没有 GBA 游戏
ZIP 中包含多个 GBA 游戏
文件过大
压缩包不安全或已损坏
文件已失效，请重新选择
选择的文件与原游戏不匹配
```

不要全部显示 `Unknown error`。

---

## 59. Loading / Cancellation

Import 过程中显示：

```text
正在添加游戏…
```

不要显示虚假百分比。

用户取消时：

```text
删除 temp
不创建 GameEntity
不更新 LastSession
不影响旧数据
```

---

## 60. Phase 6 人工验收

用户至少验证：

```text
首次空状态
添加 .gba
添加 .zip
重复导入
Home Continue
Recent
Library
Search
Sort
Game Details
Play Time
Rename
Remove from Library
Re-link ROM
Settings
返回导航
重新打开 App
```

并确认：

```text
旧存档仍在
Shader 设置仍在
Touch layout 仍在
Sensor settings 仍在
```

---

## 61. Phase 6 验收标准

### Home

- [ ] Continue
- [ ] Recent Games
- [ ] Add Game
- [ ] Empty State

### Library

- [ ] All Games
- [ ] Search
- [ ] Sort
- [ ] No duplicate identity
- [ ] Broken game recovery

### Import

- [ ] `.gba`
- [ ] `.zip` single GBA
- [ ] ZIP traversal blocked
- [ ] ZIP bomb limits
- [ ] direct/ZIP same Game ID

### Game Details

- [ ] Play / Continue
- [ ] Last Played
- [ ] Play Time
- [ ] Save State access
- [ ] Rename
- [ ] Remove from Library

### Settings

- [ ] Display consolidated
- [ ] Controls consolidated
- [ ] Gameplay consolidated
- [ ] Peripherals consolidated
- [ ] Saves
- [ ] About

### Data

- [ ] Explicit Room migration
- [ ] Old GameId preserved
- [ ] Old battery preserved
- [ ] Old states preserved
- [ ] Old settings preserved

### Regression

- [ ] Phase 2 Persistence
- [ ] Phase 3 Player
- [ ] Phase 4 Renderer
- [ ] Phase 5 Peripherals

### Security

- [ ] 0 permissions
- [ ] No INTERNET
- [ ] No Ads / Analytics
- [ ] ZIP fail closed
- [ ] No unknown binary

### Build

- [ ] JVM PASS
- [ ] Native PASS
- [ ] Instrumentation PASS
- [ ] lint 0 errors
- [ ] Debug PASS
- [ ] unsigned Release PASS

---

## 62. Phase 6 不要求完成

```text
Cheats
IPS / UPS / BPS
Link
Cloud
Achievements
SkyEmu
Online metadata
Online covers
Production signing
Final release
```

---

## 63. 完成后必须生成报告

输出：

```text
docs/reports/PHASE_6_LIBRARY_APP_EXPERIENCE_REPORT.md
```

必须包括：

```text
A. 实际完成
B. 架构变化
C. Home
D. Library
E. ROM Import
F. ZIP Security
G. Game Details
H. Play Time
I. Artwork Strategy
J. Settings Consolidation
K. Room / Data Migration
L. Error UX
M. Permissions
N. Dependencies
O. Automated Tests
P. Security Fixtures
Q. Regression Phase 2–5
R. Performance
S. Manual Acceptance
T. Known Issues
U. Risks
V. Unfinished
W. 是否允许进入 Phase 7
```

最终只能：

```text
READY
```

或：

```text
NOT READY
```

---

## 64. 进入 Phase 7 的硬门槛

任一出现：

```text
ZIP path traversal
ZIP bomb 可导致 OOM/ANR
Duplicate ROM 产生重复 Save identity
Library 重构导致旧 Save 丢失
Remove Library 默认删除正常 Save
Play Time 明显重复计时
Quick Resume regression
Renderer regression
Input regression
Sensor settings regression
Room destructive migration
新增 INTERNET
新增 Storage permission
未知 native binary
```

必须：

```text
NOT READY
```

---

## 65. Git 提交建议

```text
feat: add home experience
feat: add local game library
feat: add gba library import
feat: add bounded zip gba import
feat: add game detail screen
feat: add local artwork fallback
feat: add play time tracking
feat: add local library search and sort
feat: consolidate app settings
feat: add rom relink flow
test: add zip security fixtures
test: add library migration coverage
test: add play time regression
test: add phase 2-5 regression
docs: add phase 6 library report
```

不要把整个 Phase 6 压成一个巨大 commit。

---

## 66. Codex 阅读顺序

```text
PROJECT_SPEC.md
→ ARCHITECTURE.md
→ PHASE_0_1_REPORT.md
→ PHASE_2_PERSISTENCE_REPORT.md
→ PHASE_3_PLAYER_EXPERIENCE_REPORT.md
→ PHASE_4_RENDERER_VISUAL_EXPERIENCE_REPORT.md
→ PHASE_5_GBA_HARDWARE_SENSORS_REPORT.md
→ GBA_TEST_ROM_CORPUS.md
→ PHASE_6_LIBRARY_APP_EXPERIENCE_IMPLEMENTATION.md
```

---

## 67. Phase 5 状态收口

如果当前 Phase 5 报告仍写 `NOT READY`，但用户已完成最终人工验收，并明确：

```text
Physical Rumble = DEFERRED
V1 zero-permission policy
```

则 Phase 6 开始前：

> 只更新 Phase 5 报告为 READY，不修改生产代码。

不得为了 Phase 5 READY 添加 `VIBRATE`。

---

## 68. Codex 强约束

```text
只实施 Phase 6
不提前 Phase 7+
不升级 mGBA
不升级冻结工具链
不添加 INTERNET
不添加 Storage permission
不做联网封面
不做 ROM scraper
不做 online DB
不做 Cheats / Patch / Link
不破坏 Game ID
不破坏 Save
不让 UI 直接 JNI
```

重大技术选择：

> 先写 ADR，再实施。

---

## 69. 本轮成功定义

Phase 6 成功不是：

> “多了一个游戏列表”。

而是：

> **GBA Lite 已经从一个功能完整的播放器，变成一个可以长期日常使用的极简 GBA 模拟器 App。**

目标路径：

```text
首次打开
→ 选择 .gba / .zip
→ 自动加入 Library
→ 开始游戏
→ 正常退出

第二次打开
→ Continue
→ 继续上次进度

之后
→ Library
→ 搜索 / 排序
→ 打开另一个游戏
→ Save / Rewind / Shader / Sensor 全部保持正常
```

始终保持：

```text
完全离线
零危险权限
无广告
无追踪
可审计
存档安全
```

全部通过后：

```text
Phase 6 = READY
```

之后才允许进入：

```text
Phase 7 — Compatibility / Stability / Security Hardening
```
