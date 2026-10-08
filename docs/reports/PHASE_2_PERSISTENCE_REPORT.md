# Phase 2 Persistence 实施报告

日期：2026-10-06（Asia/Shanghai）  
状态：**READY**。仅为进入 Phase 3 的技术门槛，不代表 V1 可发布；本轮未执行 Phase 3。

## A. 实际完成

- 按 PROJECT_SPEC → ARCHITECTURE → PHASE_0_1_REPORT（READY）→ PHASE_2_PERSISTENCE_IMPLEMENTATION 执行。
- 保留 Phase 0/1 的 SAF、Session/Core API、独立模拟线程、JNI、GLES、Oboe、按键及生命周期链路。
- ROM SHA-256 Game ID、私有内容寻址 ROM snapshot、持久 SAF URI、已知 provider size/modified token 的哈希复用；未知/变化 token 才重算。
- 正常 SRAM/FLASH/EEPROM 导入/导出、原子持久化、有效旧版本备份、读取失败 fallback、45 秒安全点。
- 4 个手动 Slot、独立 Quick Save/Load、State JSON 元数据、ROM/核心版本/大小/哈希校验。
- A→B→C→A rolling autosave、最新有效候选选择及损坏 fallback、LastSession、基础 Quick Resume。
- ViewModel 保持 Activity configuration recreation 下的 Session；后台/退出/换 ROM 串行保存。
- Room 元数据、明确 1→2 migration、原子失败/损坏/存档回归/迁移/实机测试、ADR-008 和格式文档。
- 没有增加 Android 权限、网络/Analytics/Ads SDK；没有修改 mGBA/Oboe 上游或升级冻结工具链。

## B. 新增/修改目录与架构

项目：`D:\GPT\projects\gba-emulator`。
新增 `storage/`（纯 JVM 原子文件实现）、`data/`（Android Room/SaveRepository）。
修改 `core-api/`、`core-mgba/`、`emulator-session/`、`app/`、`feature-player/`、`test-rom/`、CI/审计脚本。
新增 `docs/adr/ADR-008-persistence-transactions.md`、`docs/SAVE_FORMAT.md`、本报告及 evidence/phase2。
正式 PROJECT_SPEC/ARCHITECTURE 未改动。UI → Session/Repository → Core API/Storage；UI 无 JNI。
Core API 只交换模型与 bounded bytes，不泄漏 Android/mGBA/GL/Oboe 类型。存储路径不进入 Native Core。
app 仅在 composition root 构造 adapter。没有全局 Native singleton 或 Activity 引用泄漏。

## C. 数据模型

| 模型 | 实际内容 |
|---|---|
| Game ID | lowercase SHA-256(ROM bytes)，同内容改名同 ID，不同内容不同 ID |
| Save RAM | 原始 `.sav` bytes，非空且最大 128 KiB；无 Save 类型返回 NoSave |
| State | mCore 原始 state bytes，最大 2 MiB，含独立 schema-1 JSON |
| Autosave | auto-a/b/c，UTC createdAt + 单调 sequence |
| LastSession | gameId/romUri/lastPlayedAt/sessionClosedCleanly，不存 FD/pointer/Activity |
| Room | GameEntity、LastSessionEntity、SaveStateEntity；只存元数据，不存大型 Blob |
| Room migration | schema 1→2 加 sourceSize/sourceModified（默认 -1）；保留旧数据，无 destructive migration |
| State metadata | schemaVersion=1、gameId、core、coreVersion、coreCommit、stateVersion、createdAt、appVersion、slot、kind、stateSize、stateSha256、sequence |

Room 是索引；State 自包含元数据和 manifest 为读取权威，不依赖可能过期的 Room State 索引。
Autosave/Quick Resume 内部默认 ON；没有完整 Settings UI，也未加入此阶段不需要的 DataStore。

## D. 实际文件结构

```text
files/
├── roms/<game-id>.gba
└── persistence/<game-id>/
    ├── battery/
    │   ├── current.json                  # current/previous UUID
    │   ├── <current-uuid>/battery.sav    # raw 正常存档
    │   ├── <current-uuid>/metadata.json
    │   └── <previous-uuid>/...           # .sav 备份版本
    ├── slot-1/ … slot-4/
    ├── quick/
    └── auto-a/ … auto-c/

每个 State key：current.json + <uuid>/game.state + <uuid>/metadata.json
databases/save-metadata.db                # Room 元数据
```

本轮采用 generation + 单一原子 manifest，未采用两个独立 `.state/.json` 的直接覆盖。
截图接口 `StateScreenshotWriter` 已预留、失败与 State commit 隔离；本轮未生成截图或缩略图。

## E. 原子写入策略

1. 在同一 App 私有目录创建新 UUID generation；旧正式存档不动。
2. 写 binary/JSON，flush/fsync，验证非空/大小/长度/SHA-256，fsync generation。
3. 写 `current.json.tmp.<uuid>` 并 fsync；manifest 同时指定当前和旧有效备份。
4. 同目录 ATOMIC_MOVE + REPLACE_EXISTING 切换 manifest，随后 fsync 父目录。
5. 不支持 atomic replace 时返回失败，绝不退化成直接覆盖正式 `.sav`。
6. 失败保留旧有效版本；正常存档当前失败尝试 previous；两者不可用则停止加载，禁止以新空档覆盖。
7. 成功后只清理不再引用且验证有效/兼容的旧 generation；损坏/不兼容文件保留。
8. 启动只清理本项目 private `current.json.tmp.<uuid>`；不会扫描/删除用户外部文件。

Session Mutex 串行 Native 操作；进程内 commit lock 也协调多个 repository 实例。
硬件/文件系统断电持久性仍取决于平台，不宣称任意断电完全无损。

## F. mGBA Save/State API

固定 mGBA 0.10.5 commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`，Oboe 1.9.3 及工具链不变。
Native 用 memory-only VFile 作 battery backing；使用官方 savedataClone/savedataRestore，路径不进入 mGBA。
使用官方 stateSize/saveState/loadState 原始 State API，stateVersion=7，不把历史 battery bytes 嵌入 State。
导入 State 前校验元数据与 binary，Native 再检查精确长度及 pinned version magic；保留当前 battery bytes。
Native control mutex、暂停模拟线程、受控 JNI byte arrays 与异常转换；没有改 upstream。
Debug UBSan 配置仅影响测试；最终 Debug/Release 关闭 sanitizer，Release 不含 TEST ONLY counter exports。

## G. 生命周期流程

| 场景 | 实际流程 |
|---|---|
| background | pause/audio stop → flush battery → atomic write → autosave → metadata |
| foreground | Surface resume → audio/core resume |
| Activity recreate | ViewModel 保持 Session；onStop 安全点，重建 Surface 后 resume |
| exit | pause → battery → autosave → mark clean → close core；battery 失败时拒绝退出、保留 live core 可重试 |
| ROM switch | 串行先保存/关闭旧 core，再创建/加载新 core；旧 battery 失败时拒绝换 ROM |
| periodic | 每 45 秒 pause/capture battery → atomic persist → resume；相同 bytes 跳过写入 |
| process relaunch | LastSession → URI access/identity → ROM → current/backup battery → newest valid compatible autosave → start |

Autosave 不可用时退化为 ROM + 正常存档；URI 失效提示重选，旧存档不删除。
正常存档写入失败不继续做 Autosave/close。最终 ViewModel clearing 保存/关闭使用非取消的串行清理。
突然 process death 可丢失最后一次成功安全点以后的工作；没有宣称最后一帧完全无损。

## H. Manifest

最终 Debug permissions = `[]`；Release permissions = `[]`。
没有 INTERNET、旧式存储、全盘存储或其他新增权限。只持久化用户明确选择的 SAF URI 读取授权。
androidTest provider/runner 只在测试 APK；其测试权限不进入 App APK。

## I. Dependencies

新增 Room 2.7.2 runtime/compiler（Java annotation processor），Gson 2.13.2（Apache-2.0）。
Version Catalog、各模块 Gradle lock、SHA-256 verification metadata 均已更新。
最终构建未使用 --write-locks/--write-verification-metadata，现有锁与校验生效。
运行时完整列表：PHASE_2_DEPENDENCIES.txt。无 Firebase/Analytics/Ads/OkHttp/Retrofit/online SDK。
APK native 仍仅 source-built libgba_bridge.so + 官方 Google Maven AndroidX graphics-path .so。
来源/许可证/源码/ABI/权限/依赖审计：39 项全部 PASS。

## J. 测试与构建

| JVM suite | 次数 | 结果 |
|---|---:|---|
| dev.gbalite.core.ButtonTest | 1 | PASS |
| dev.gbalite.mgba.AdapterTest | 7 | PASS |
| dev.gbalite.mgba.AdapterTest | 7 | PASS |
| dev.gbalite.session.PersistenceSessionTest | 4 | PASS |
| dev.gbalite.session.SessionTest | 4 | PASS |
| dev.gbalite.input.InputRouterTest | 2 | PASS |
| dev.gbalite.storage.StorageTest | 7 | PASS |

- JVM：32 次，0 failures/errors（25 个独立测试；adapter Debug/Release 重复执行）。
- Host native：3 CTests PASS：PCM ring、mGBA bring-up、persistence。六种 SRAM/FLASH/EEPROM 容量变体、State 不回退 battery、重开、bad magic、固定 v1 `.sav` 回归。
- 最终普通 arm64 实机：JNI 7 项 + Activity 8 项 = 15 项 PASS。
- JNI：真正 SRAM 按键写入、重新创建 core 后同 bytes、State load 保持 battery、损坏/超限 State、20 次 save/load/close 后无 registry handles。
- Activity：真实 GLES/input 基线、持久 URI、已有 battery、后台保存/recreate、4 Slots/Quick、退出恢复、同内容改名同 ID、新 Activity 从持久 Room/State 恢复、Room 1→2 migration 保留旧 Game/LastSession。
- Storage failure：写后中断/替换前 IO error、旧主/备份保持、损坏 current fallback、incomplete temp 清理、missing metadata、wrong ROM/core/schema、path traversal、深层 JSON、A/B/C 轮换、损坏后下一次 State 保存可继续且旧损坏文件保留。
- Session：battery→State→close 顺序、write failure 拒绝 close/switch、并发 command 串行、ROM save 隔离、fresh core autosave resume。
- NDK UBSan trap：额外 7 JNI tests PASS，覆盖 ROM/Save RAM/State 保存加载/关闭；没有触发 trap。Gradle split install 超时，手动安装同一 APK 后直接 instrumentation 通过，保留失败及成功日志。
- Host ASan：尝试但 MSVC 安装缺少 clang_rt.asan_dynamic_runtime_thunk-x86_64.lib，未运行；不记为 PASS。
- lint：0 errors，18 warnings。保留升级/ABI/backup 等建议，未创建 baseline 或禁用检查。
- Debug APK、unsigned Release APK、两类 instrumentation APK 全部成功；Release ELF RELRO/BIND_NOW/NX/stack protector 检查通过，无 TEST ONLY exports。
- CI 已更新为 Phase 0–2；本机等价任务通过，未推送或运行远程 GitHub Actions。

普通必需测试 50 次执行全部 PASS；UBSan 的 7 次额外执行另计。证据：evidence/phase2/。
历史第一次设备结果为 13 项 PASS；最终新增改名/新 Activity 与独立 Room migration 后为 15 项 PASS。

## K. 实机验收（自动与人工分开）

自动：Android 16/API 36 arm64 手机，最终 15 项 + UBSan 7 项，全部 PASS。
人工：用户经真实系统 SAF 选择 Download 中的原创测试 ROM，确认退出后粉色保留、Quick Load 恢复绿色、Slot 1 和后台恢复正常。
用户最初反馈 A 后粉色而非蓝色；这是红底叠加蓝色分量的正常结果，指引已更正，未当作代码缺陷或隐藏失败。
真实 SAF provider 的持续读取授权仍受 provider 行为约束；测试 fixture 的 grant 仅用于自动测试。

## L. 已知问题

- 截图接口已预留，但 State screenshots/thumbnails 未实现（阶段文档允许预留）。
- App-private 保存暂无外部导出/导入 UI；卸载/清除 App 数据会移除这些文件。
- 没有做 30–60 分钟真实时间连续游戏/音频/内存长测，也没有完整恶意 State fuzzing。
- 只测试 Android 16 arm64；旧 API 26/OpenSL fallback/其他设备尚无专项验证。
- Host ASan 环境缺少运行库；已有 NDK UBSan 实机测试，不等同完整 ASan/leak audit。
- Release unsigned；没有正式 keystore/AAB/release provenance，不能作为 V1 发布包。

## M. 风险

- 文件系统/硬件断电保证具有平台差异；保留当前和备份以及 fsync/atomic commit，仍不承诺普适零损失。
- native State 格式绑定固定 core，升级必须显式做兼容性策略；当前严格拒绝明显不兼容状态且不删除旧文件。
- SAF URI 可被撤销/provider 可异常；可信非零 modification token 才复用哈希，未知 token 重算；provider 虚报 token 仍是外部限制。
- process death 恢复最近成功安全点，未落盘部分可能丢失；Android 最终清理不能保证系统在杀进程前完成。
- registry/worker 所有权与循环回归通过，但长期 native heap/FD/音频路由稳定性没有全面 profiling。
- 损坏/不兼容/orphan generation 保守保留，反复失败可能占用更多存储；当前优先数据安全。

## N. 未完成项

本轮 Phase 2 必需实现、构建、自动/人工验收完成。
Phase 3+：Rewind/Fast Forward UI、Shader、手柄/布局编辑、GBA sensors、完整 Library/Settings/封面、在线/第二核心等，均未提前实现。
正式签名/发布、远程 CI、更多设备/长测/fuzz/ASan 仍需后续验证。
源代码未提交或推送；不覆盖 Phase 0/1 历史报告，用户可审查当前工程和证据。

## O. 是否允许进入 Phase 3

**READY**

理由：Game ID、正常存档原子写入/备份、State 错误 ROM/版本/损坏拒绝、Quick、rolling autosave、LastSession/URI、生命周期、架构、安全、测试/构建门槛全部通过；人工真实 SAF 也已确认。
上述设备覆盖/长测/发布限制保留。本结论不自动启动 Phase 3，也不是 V1 release approval。

## 构建与固定测试资产

| 产物 | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `ed88d5e8603635cd48fc5483b836d40b618d596fab4c76c565171637182f532f` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | `84b43a1dd4a04e4faa233faf1be74b9dc510c9df38a5f68380a5f867376e97da` |
| `test-rom/test-save-v1.sav` | `91fe8bc63da1c542130e95137a4e28eeb1ec7df6239ca92fbe08e050b3979c9f` |
| `test-rom/build/persistence.gba`（TEST ONLY） | `87addcdfb37cd92f807e885e16e449b1d3898973e13686ad6d3200f2b14a94f7` |

Debug cert SHA-256：`3660dfb189306e49d973a7f91c9cbc1cb35cc93921f0292e8db28b3fa10b4094`，仅开发。
Release 无签名；没有下载未知模拟核心 binary、商业 ROM 或 Nintendo BIOS。
