# GBA Lite — Phase 5 GBA Hardware / Sensors 实施任务书

**版本：** v1.0  
**适用工程：** `D:\GPT\projects\gba-emulator`  
**执行者：** Codex  
**阶段：** Phase 5 — GBA Hardware / Sensors  
**阶段目标：** RTC / Tilt / Gyro / Solar / Rumble  
**产出报告：** `docs/reports/PHASE_5_GBA_HARDWARE_SENSORS_REPORT.md`

> 本文档是可以直接交给 Codex 的实施任务，不是建议清单。先核查现有实现，避免重复造轮子；按受控的小批次实现、构建、验证和记录。**仅允许实施 Phase 5，禁止启动 Phase 6 或宣称 V1 可发布。**

---

## 0. 前置条件和阅读顺序

严格阅读：

1. `PROJECT_SPEC.md`（最高规格，保持原样）
2. `ARCHITECTURE.md`（正式技术边界，保持原样）
3. `docs/reports/PHASE_0_1_REPORT.md`
4. `docs/reports/PHASE_2_PERSISTENCE_REPORT.md`
5. `docs/reports/PHASE_3_PLAYER_EXPERIENCE_REPORT.md`
6. `docs/reports/PHASE_4_RENDERER_VISUAL_EXPERIENCE_REPORT.md`
7. `docs/adr/ADR-008*` 至 `ADR-013*`、`docs/SAVE_FORMAT.md`、`docs/RENDERER.md`
8. `GBA_TEST_ROM_CORPUS.md`（若已纳入仓库）
9. 本文档

**前置验收：** Phase 0/1、2、3 均通过；Phase 4 的最终人工验收已由用户明确确认通过，应该先将 Phase 4 实施报告同步为 `READY`。如果本地报告仍写 `NOT READY`，只能依据原始证据与用户最终验收结论做文档关闭，不允许擅自改写失败日志或自行宣布未完成的测试通过。Phase 4 关闭后才能开始本轮生产代码。

**基线：** mGBA `0.10.5`，固定 commit `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`；Oboe `1.9.3`；`arm64-v8a`；Kotlin/Compose + JNI + `EmulatorSession` + 独立核心线程 + GLES/EGL + Oboe + SAF + Room/DataStore。保留现有锁文件、上游源码哈希与 Release/Debug 构建链。**不升级核心和工具链，不修改 mGBA/Oboe upstream。**

---

## 1. 目标及硬约束

Phase 5 完成后，用户应当可以：

- 在使用实时时钟的 GBA 游戏中获得正确的日期/时间行为；
- 通过手机姿态控制倾斜（Tilt），通过角速度控制陀螺仪（Gyro）；
- 对有太阳能感应的 GBA 游戏使用手机环境光或手动日照滑块；
- 对有震动事件的游戏获得**真实受游戏事件驱动**的 Rumble，或在权限/硬件不具备时获得透明降级；
- 清楚地看到传感器可用性、启用状态和替代方案；
- 在旋转屏幕、暂停、后台、快进、倒带、载入 State、切换 ROM、关闭应用之后，没有传感器泄漏、残留震动、坏存档或生命周期异常。

保持：

```text
UI → ViewModel / Application → EmulatorSession
   → Core API → MgbaCoreAdapter → JNI → pinned mGBA

Android SensorManager → SensorAdapter → Session/Core API → mGBA peripheral
mGBA rumble event → JNI event bridge → Session → HapticOutput
```

**禁止** UI 直接 JNI、SensorManager 直接握住 native 指针、在音频实时回调中调用 Android API、把外设状态写入 `.sav`、通过重新创建 Core 解决传感器更新、加载外部驱动 `.so`。

---

## 2. 先进行 mGBA 0.10.5 源码能力核查

实施前，阅读 **本地固定版本**，不以 GitHub `master` 代替当前源码：

- `include/mgba/core/interface.h`：`mRotationSource`、`mRTCSource`、`mRTCGenericSource`、`mRumble`、`mPeripheral`；
- GBA 平台头文件：`GBALuminanceSource` 与 `mPERIPH_GBA_LUMINANCE` 的定义、回调语义；
- `src/gba/` 中 GPIO / cartridge / RTC / solar / rotation / rumble 的连接点与检测行为；
- 已有 upstream adapter 作为**公开实现参考**：同一固定 commit 的 `src/platform/libretro/libretro.c`，只研究 API、数值转换与生命周期，不添加 Libretro 层；
- 当前 `core-mgba`、`core-api`、`emulator-session` 的 JNI 与 worker ownership。

输出一份 `docs/GBA_PERIPHERALS.md`，至少记录：

| 能力 | 现有 pinned mGBA API | 默认行为 | 本轮 Android adapter 责任 | 需要 ROM 验证的点 |
|---|---|---|---|---|
| RTC | 以实际源码为准 | 记录 | 记录 | 记录 |
| Tilt | `mRotationSource` 类接口 | 记录 | 记录 | 记录 |
| Gyro | `mRotationSource` 类接口 | 记录 | 记录 | 记录 |
| Solar | `GBALuminanceSource` 类接口 | 记录 | 记录 | 记录 |
| Rumble | `mRumble` 类接口 | 记录 | 记录 | 记录 |

**这些名字是上游核查锚点，不保证每项都可以直接用同一个 `setPeripheral` 枚举挂载。以固定版本的真实定义为准。** 如果 mGBA 本身已经提供可用 RTC，优先做端到端验证，勿额外覆写时间源。

---

## 3. 建议模块与抽象

尽量复用现有 `input`、`core-api`、`emulator-session`、`data`，如有必要新增轻量 `peripherals` / `sensors` 模块。不要仅为“模块多”而拆分。

推荐的**概念模型**（不是必须逐字生成的接口）：

```kotlin
enum class PeripheralKind { RTC, TILT, GYRO, SOLAR, RUMBLE }
enum class InputSourceMode { AUTO, PHYSICAL, MANUAL, DISABLED }

data class PeripheralCapabilities(
    val rtc: Boolean,
    val tilt: Boolean,
    val gyro: Boolean,
    val solar: Boolean,
    val rumble: Boolean,
)

data class PeripheralSettings(
    val tiltMode: InputSourceMode,
    val gyroMode: InputSourceMode,
    val solarMode: InputSourceMode,
    val rumbleEnabled: Boolean,
)
```

再按当前代码风格设计：

- `SensorProvider`：查询设备能力、注册/注销 Android 传感器；
- `SensorMapper`：坐标变换、滤波、定标、范围夹取，纯 Kotlin/JVM 可测试；
- `PeripheralController`：接收用户设置、提供稳定的**最新值快照**；
- `EmulatorSession`：控制生命周期及 Core 外设装卸；
- `MgbaCoreAdapter`：实现 Core API 和串行命令；
- `HapticOutput`：仅负责已批准的震动目标；
- DataStore：仅保存用户选择的模式、校准与开关。

Core API 使用纯标量或不可变结构；不要向 `core-api` 泄漏 `SensorEvent`、`SensorManager`、`Activity`、`Vibrator` 或 mGBA 类型。Native 使用受控指针、atomic latest-sample、ring/锁或已有 worker command queue，**不得在 JNI 或 sensor callback 内直接从另一线程读写运行中的 mGBA 非线程安全对象**。

---

## 4. RTC — 实时时钟

### 4.1 用户体验

- 默认使用手机系统时间，无需网络及定位；
- 游戏内昼夜、日期逻辑不应随 2×/4×/8× 快进同步加速；
- 退后台再回来，RTC 应符合真实经过时间；
- 载入 Save State / Quick Resume 后 RTC 仍有可解释的时间语义；
- UI 不需要复杂的 RTC 设置。仅在诊断需要时加入**本地 Debug 专用固定时间源**。

### 4.2 实现

优先沿用 mGBA 0.10.5 默认 RTC 方案。若确需自定义来源，先确认 `mRTCSource` / `mRTCGenericSource` 的采样、Unix 时间、序列化/反序列化契约，再写 ADR。区分：

```text
Unix epoch / absolute instant
vs
Android 时区 / 用户展示的当地时间
vs
monotonic elapsedRealtime
```

不能把 `elapsedRealtime` 当作墙上时间，也不能把模拟帧数当作 RTC；验证本机时区变化、跨午夜、闰日、设备手动更改系统时间、快进/暂停/倒带的边界。不得联网校时或申请位置。

### 4.3 数据安全

- 保持固定 State schema / battery 兼容性；
- RTC 游戏保存文件不能与传感器设置互相污染；
- 不改变已经固定的 State 内容格式，除非先明确兼容性策略和 ADR；
- 如果游戏在 Save State 中内含 RTC 状态，先验证官方 loadState 行为，避免复位/倒退造成异常。

---

## 5. Tilt — 倾斜传感

### 5.1 物理输入

优先根据实际设备传感器支持使用 `TYPE_GRAVITY`，必要时使用 `TYPE_ACCELEROMETER` 低通提取重力分量。不可将陀螺仪角速度等同于倾斜角度。至少包含：

- 物理设备存在性检测；
- portrait / landscape / reverse landscape 坐标变换；
- 一键**校准当前姿态为中立**；
- 有界低通滤波和合理死区；
- mGBA 所需的轴方向、零点、符号、固定点范围转换；
- NaN/Inf/异常 spike/输入暂时缺失的安全处理。

### 5.2 手动回退

手机没有可用物理传感器或用户不便转动手机时，提供极简**虚拟 Tilt**：例如一个小型双轴触控板（仅需要时显示）。松手自动归中；不强迫用户进入复杂设置。`AUTO` 模式以物理优先、缺失时可选择手动。

### 5.3 生命周期

只有运行相关 ROM 且模式启用时才注册相应传感器；pause、background、stop、ROM switch、close 立即注销；恢复后重新校准或安全复位，不能突然产生最大倾斜。

---

## 6. Gyro — 陀螺仪

物理模式通过 Android `TYPE_GYROSCOPE` 获得**角速度（rad/s）**，不能把角度或加速度原值直接送给 GBA Gyro。实现：

- Android 传感器坐标 → 当前显示方向 → GBA 所需旋转轴；
- 有界强度/偏置/死区、符号反向与比例校准；
- 单位换算及数值饱和；
- 防抖，静止时应接近零；
- 设备缺失时明确 `UNAVAILABLE`，允许独立的手动角速度控制（若提供），与 Tilt 手动模拟分开；
- 不根据设备名称硬编码 OPPO 或其他品牌特殊值。

旋转/暂停/恢复时清理陈旧角速度，避免“放下手机后游戏仍持续旋转”。快进与倒带过程中保持明确定义：输入由当前实时采样驱动，不能把真实传感器事件重放当成历史 Save State；倒带时可暂停更新，松开后重新采样。

---

## 7. Solar — 光照/太阳能

GBA Solar 是**卡带外设的光照级别**，不是手机系统亮度值。先核实 mGBA 0.10.5 `GBALuminanceSource` 的数值方向及采样语义，再映射。

### 7.1 两种模式

1. **自动：** 设备存在且可用 `TYPE_LIGHT` 时，读取 lux 并映射到有界游戏级别；
2. **手动：** 使用 0–100%“日照强度”滑块（主界面不常驻，游戏内设置可快速到达）。

建议对 lux 使用**分段/对数或经验证的等级映射**，不得直接 `lux.toInt()` 塞入单字节。明暗方向由 pinned mGBA 实现和合法测试 ROM 确认；应当符合直觉：提高日照模拟值会让游戏获得更强日照。不要把手机屏幕亮度误认为环境光。

### 7.2 状态及兼容性

- 没有光传感器时默认手动，不显示空白/失效选项；
- 环境光读取可能受手机遮挡，允许手动覆盖；
- 用户切换模式、退出、背景恢复不应留下过期采样；
- 如某 ROM 不需要 Solar，则不必长期注册 `TYPE_LIGHT`；
- 不能静默修改游戏的 battery `.sav`。

---

## 8. Rumble — 震动及权限决策门（重要）

### 8.1 必须区别三件事

```text
A. GBA cartridge rumble events （游戏触发）
B. Android phone vibrator / gamepad vibrator（输出设备）
C. Touch button haptic feedback（用户触摸反馈）
```

**C 不是 A 的等价实现。** 不要通过 `View.performHapticFeedback()` 随手模拟点击声称完成游戏卡带 Rumble。

### 8.2 零权限策略

此前产品高优先级基线为 **Debug/Release merged permissions = []**。Android 的 `VIBRATE` 是 **normal permission**，不是危险权限，但仍会使“零权限”变成“一个权限”。因此：

1. 先调研当前 API 26–36 上**是否存在可合法、可靠、无需新增 `VIBRATE` 声明**的游戏事件驱动输出路径，且能用目标手机或已连接手柄真实验证；
2. 如没有，不允许假装已经实现 Rumble，也不允许静默添加 `VIBRATE`；
3. 在 `docs/adr/ADR-0XX-rumble-permission.md` 记录零权限与真实 Rumble 的权衡，列出“保持 0 权限 / 授权唯一 normal `VIBRATE`”两套方案、对 Manifest/用户体验/测试的影响，**请求用户明确批准**；
4. 未获批准时，继续完成其他四项能力和 `mGBA → HapticOutput` 接口与合成测试，真实震动标为 `BLOCKED_BY_PERMISSION_APPROVAL`，整个 Phase 5 不得虚报 `READY`；
5. 若用户明确批准仅 `VIBRATE`，才在文档化更改后添加这唯一的 normal permission；Debug/Release 精确 manifest allowlist 应变为 `['android.permission.VIBRATE']`，且禁止其它权限。报告必须明确产品安全基线从“0 permissions”变更为“仅 1 个 normal permission”，不应再宣称 0 权限。

**不要为了维持 0 权限而使用隐藏 API、反射绕过、静默特权请求或 root。**

### 8.3 技术要求

- `mRumble` 回调 → 受控事件队列 → `HapticOutput`；绝不在 core/audio callback 直接调用 `Vibrator`；
- 支持设备无 actuator、系统关闭震动、手柄断连、API/振幅不支持等降级；
- 如选择手机震动或手柄震动，目标要明确，不误发两路；
- 有节流、幅度钳制、连续震动安全上限、事件合并和停止事件；
- pause、background、rewind、loadState、close、ROM switch 时立即停止；
- 先验证 pinned mGBA 实际 `mRumble` 语义（enable/on-off 等），不要凭空假设它返回连续振幅；
- 默认提供“震动 开/关”设置；未授权/无设备时清楚显示不可用原因；
- 严禁把纯合成回调测试等同于实体震动验收。

---

## 9. 自动检测与每游戏能力

优先由 pinned mGBA 的**真实 cartridge/peripheral capability** 或经过验证的 ROM 行为检测决定启用哪些外设；不得仅凭 ROM 文件名推断。若 mGBA 不提供稳定判断接口，允许：

```text
AUTO（默认；可检测则自动）
MANUAL（用户自行开启指定外设）
DISABLED
```

游戏内设置宜简洁：

```text
外设
  实时时钟：自动
  倾斜：自动 / 手动 / 关闭       [校准]
  陀螺仪：自动 / 手动 / 关闭   [校准]
  太阳能：自动 / 手动 / 关闭   [日照滑块]
  震动：开 / 关               [设备状态]
```

仅在游戏确有需要时显示简短帮助；不要让正常玩家理解 GBA GPIO、RTC 寄存器等技术细节。避免未经证据支持的“某游戏一定具备某外设”硬编码。全局默认足够好用，是否做每游戏覆盖可基于现有 Game ID 建模，但不要升级为完整游戏库重构。

---

## 10. 传感器权限、频率、功耗、隐私

- `SensorManager` 常规加速度/重力/陀螺仪/光照路径无需为本功能新增位置、相机、麦克风、联网或文件权限；
- 采样目标建议约 30–60 Hz 或 Android `SENSOR_DELAY_GAME`，依据性能实测调整；**不要申请 `HIGH_SAMPLING_RATE_SENSORS`**；
- 如设备不提供某 Sensor，功能应明确降级；
- 后台及无对应游戏时全部停止注册，防止耗电；
- 采样只留短期内存数据，不做传感器历史库、日志追踪、联网发送或遥测；
- Debug 日志不得持续记录个人行为级细粒度运动轨迹；
- Android API 26–36 行为差异需用 mock/instrumentation 覆盖，旧机真实支持作为非阻断已知风险保留，除非本阶段所需设备能力测试失败。

---

## 11. 与既有生命周期的协调

按现有 `ViewModel → EmulatorSession → Core` 单一命令队列与 Session Mutex 串行化；不能重新引入 Phase 3 的多生命周期源竞争或 Phase 4 的 EGL 同步阻塞。

| 场景 | 期望行为 |
|---|---|
| ROM load | 检测能力、挂载外设、设置初始值，再开始模拟 |
| Normal play | 注册必要传感器，安全读取最新样本，按需输出震动 |
| Pause menu | 暂停采样或稳定为 neutral；震动立即停止 |
| Background/Home | 注销传感器、停止震动，现有 battery→autosave 链路保持 |
| Foreground | 检查设备可用性、重新注册、丢弃过期样本、恢复 core/audio |
| Portrait/Landscape/reverse | 坐标变换正确、不重建 Core、无黑屏 |
| Fast Forward | RTC 不随倍率加速，传感器有限速，队列不堆积 |
| Rewind | 不回写旧 battery，震动停止，松开后接收新样本 |
| Quick/State Load | 不泄漏传感器、不损坏存档，RTC 兼容策略明确 |
| ROM switch/Exit | 所有 callback/监听/震动解除，无 stale handle |
| Process death/relaunch | 从保存偏好及最后有效存档恢复；物理输入重新初始化 |

禁止通过 `Activity` 静态引用保持 SensorManager listener、通过过期 native handle 派发回调、GL Surface 销毁时把传感器线程无限挂起。

---

## 12. 状态格式及兼容性

**存档安全仍是最高优先级。**

- 保留 Phase 2 正常 `.sav`、`.bak` / generation 原子事务；
- 不改变旧 `test-save-v1.sav`，固定 SHA-256 必须一致；
- 不改旧 Game ID 算法；
- 不破坏既有 Save State / Quick / A-B-C Autosave / Quick Resume；
- 如 RTC 或其他外设状态由 pinned mGBA 的 State API 内部维护，先确认兼容性与生命周期，再决定是否需元数据扩展；
- 校准值、手动日照、偏好存 DataStore；不要塞入 battery save；
- 新偏好 schema 显式版本化，旧版本缺省值安全，迁移不得破坏 Phase 4 display settings v1/v2；
- 不 destructive Room migration、不因未知配置删除用户存档。

特别验证：在 RTC 相关游戏内“保存 → App 重启 → RTC 正常推进”、在 Tilt/Gyro/Solar 游戏内“存 State → Load State → 输入恢复正常”。

---

## 13. 合法测试资产与分层验证

遵守 `GBA_TEST_ROM_CORPUS.md`：只使用自研 Apache-2.0 ROM、经独立许可证审计的开源测试 ROM 或用户私人本地 ROM；商业 ROM、Nintendo BIOS、来源不明 ROM 不能进仓库、CI 或 APK。

**优先编写自研测试 ROM**：

```text
rtc-probe.gba       显示/输出读取到的日期时间或 GPIO 探针值
rotation-probe.gba  将 Tilt X/Y 和 Gyro Z 对应的采样映射到画面/校验值
solar-probe.gba     显示/输出日照等级
rumble-probe.gba    可触发受控震动事件（只用自研合法 ROM）
```

实际是否能通过现有 GBA cartridge GPIO 访问由 pinned mGBA 能力决定，**不得造假的测试 ROM 声称覆盖真正外设链路**。如果 probe 必须模拟 cartridge mapper/外设识别，应在 `GBA_PERIPHERALS.md` 记录原理、ROM 源码及限制；退而使用 host native/JNI contract 测试时，明确区分“Adapter 验证”和“ROM 端到端验证”。不向 App APK 打包任何测试 ROM。

测试层次：

1. **JVM**：轴旋转、滤波、死区、校准、手动输入、lux 映射、设置迁移；
2. **Host Native**：Pinned core peripheral 生命周期、数值边界、重复初始化销毁、State 回归；
3. **JNI Android**：模拟 SensorEvent / callback 与真实 Core 外设读写、进程及线程安全；
4. **Instrumentation**：实际 App Settings、方向切换、后台/恢复、场景切换、0 崩溃；
5. **人工实机**：手机真实姿态/环境光/震动或对应降级，用户明确确认；
6. **长测（建议）**：30–60 分钟混合运行，收集监听器数、CPU/内存与 battery 安全点。

---

## 14. 验收用例（必须有证据）

### A. RTC

- [ ] 系统日期/时钟反映到游戏路径（真实 ROM 链路或注明不足）；
- [ ] 不随 2×/4×/8× 快进按倍率推进；
- [ ] 后台跨分钟恢复合理；
- [ ] RTC State/Autosave 兼容；
- [ ] 无系统时钟/时区权限或网络请求。

### B. Tilt

- [ ] 手机静置为中立；
- [ ] 倾斜上下/左右方向和幅值正确；
- [ ] 竖屏/正横屏/反横屏映射正确；
- [ ] 校准后中立正确；
- [ ] 无传感器时手动回退可用；
- [ ] 无过期/无限/NaN 输入。

### C. Gyro

- [ ] 角速度方向、单位与缩放通过固定版源码/ROM 验证；
- [ ] 静止接近零、运动响应可控；
- [ ] 旋转/后台恢复后无 stuck gyro；
- [ ] 缺传感器时状态明确且手动方案独立于 Tilt。

### D. Solar

- [ ] 自动光照读值映射合理；
- [ ] 手动滑块能改变真实游戏输入；
- [ ] 自动/手动/关闭切换可靠；
- [ ] 无光传感器时可正常使用手动；
- [ ] 不依赖屏幕亮度、定位、相机或联网。

### E. Rumble

- [ ] pinned mGBA callback → Session → HapticOutput 路径真实打通；
- [ ] 真设备执行成功，或因无授权/无硬件诚实记录阻断；
- [ ] pause/background/loadState/rewind/exit 全部停止；
- [ ] 不会无限震动、异常高频振动或 crash；
- [ ] Manifest 权限选择与用户批准一致；
- [ ] **0 权限触控反馈不得冒充真实 cartridge rumble**。

### F. Phase 0–4 完整回归

- [ ] 固定 v1 `.sav` bytes/hash 不变；
- [ ] battery/backup/generation/State/Quick/Autosave/Resume 全 PASS；
- [ ] FF/Rewind/HID/Touch/Multi-touch 全 PASS；
- [ ] 四 Shader、黑/白背景、Fit/Integer、旋转/后台真实 Surface 像素全 PASS；
- [ ] 旧横屏黑屏与 EGL wait ANR 专项回归 PASS；
- [ ] 音频 Home 停止/前台恢复正确；
- [ ] 无新危险权限、INTERNET、广告、Analytics 或未知 native binary。

### G. 工程门

- [ ] JVM、Host native、JNI 与 Activity instrumentation PASS；
- [ ] UBSan 原有关键路径及新增外设 bridge PASS；
- [ ] lint 0 errors，warning 列明；
- [ ] Debug APK 与 unsigned Release APK 构建成功；
- [ ] 固定版本/Source hashes/Dependency Lock/Manifest/许可证审计 PASS；
- [ ] 若 ASan/远程 CI/多设备/长测仍未执行，如实保留为风险，不能记 PASS。

---

## 15. Android Manifest 审计门

### 未授权改变权限前

```text
Debug permissions  = []
Release permissions = []
```

### 如果且仅如果用户批准真实 Rumble 所需的 `VIBRATE`

```text
Debug permissions  = [android.permission.VIBRATE]
Release permissions = [android.permission.VIBRATE]
```

`VIBRATE` 属于 normal permission；审批记录必须引用到 ADR / 实施报告。禁止隐式增加 `BODY_SENSORS`、`ACTIVITY_RECOGNITION`、`HIGH_SAMPLING_RATE_SENSORS`、`ACCESS_FINE_LOCATION`、`BLUETOOTH_CONNECT`、`INTERNET`、`WAKE_LOCK`、存储或其他权限。若操作系统/硬件需要其它权限才能完成某子项，停止该子项并记录，不允许扩散权限范围。

---

## 16. UI 与产品体验要求

保持 Phase 3/4 的白底、克制层级、单一入口与最小 Player：

- 外设设置优先置于游戏内暂停菜单的“控制/外设”下；
- 没有外设的游戏不显示大面积空白控制板；
- 校准、手动 Solar、虚拟 Tilt/Gyro 提供简单说明和默认值；
- 传感器可用性文案明确：“设备不支持 / 当前未启用 / 已使用手动控制”；
- 不用 Toast 高频弹出 sensor 值；
- 方向变更不覆盖已有触控 Profile；
- 手机品牌、不同光照、不同姿态不允许作为唯一硬编码校准依据。

**不要**重构完整 Library、Shader 页面，不做封面、主题商店、联机、作弊、补丁、云同步或 SkyEmu。

---

## 17. 执行与提交建议

分批实现，每批跑相关测试：

```text
A. audit existing pinned peripheral capabilities + docs / ADR
B. add pure SensorMapper and PeripheralSettings + migration tests
C. wire RTC end-to-end and regress State
D. wire Tilt/Gyro with orientation and calibration
E. wire Solar automatic/manual with real ROM probe
F. wire mGBA Rumble callback and permission decision gate
G. minimal settings UI and real-device acceptance
H. run complete Phase 0–4 regression and security audit
I. generate report, stop before Phase 6
```

建议小型 commits：

```text
docs: map pinned mGBA peripheral APIs
feat: add sensor mapper and calibration
feat: wire RTC regression and host clock
feat: add tilt and gyro adapters
feat: add automatic and manual solar input
feat: add bounded rumble callback bridge
feat: expose peripheral settings and fallback UI
test: add legal peripheral probe ROMs and native contracts
test: add lifecycle and phase 2-4 regressions
docs: close phase 5 with evidence and risks
```

严格检查 Git 工作区和既有未提交产物；不得覆盖/删除用户已有存档、测试结果或旧报告。**严禁只为通过验收修改断言来掩盖生产 Bug。**

---

## 18. 必须交付的报告

生成：`docs/reports/PHASE_5_GBA_HARDWARE_SENSORS_REPORT.md`

必须有以下章节：

A. 实际完成及版本号  
B. 固定 mGBA 0.10.5 外设 API 核查结论（含文件、结构、调用边界）  
C. 模块 / Core / Session / JNI 架构变化  
D. RTC：时间语义、保存与恢复  
E. Tilt：轴映射、校准、缺设备回退  
F. Gyro：角速度映射和边界  
G. Solar：lux 映射、手动滑块、测试  
H. Rumble：回调、真实输出、**权限批准与 Manifest 审计**  
I. Lifecycle、后台节能与线程安全  
J. 设置偏好、schema、迁移  
K. 合法测试 ROM 与授权/哈希  
L. Phase 0–4 Persistence/Player/Renderer 回归  
M. 自动测试、失败历史及修复证据  
N. 人工设备验收，严格区分**真实传感器与合成事件**  
O. 性能、传感器注册状态、电量影响与已知局限  
P. 依赖、许可证、权限、native provenance  
Q. 未完成项与安全/兼容性风险  
R. 构建产物、哈希、签名状态与 Git 工作区  
S. 是否允许进入 Phase 6：只能 `READY` / `NOT READY`

报告必须保留 Phase 4 历史黑屏风险、仅单台 OPPO Android 16 验证的设备覆盖限制、ASan/fuzz/远程 CI/长测等实际未完成项。Debug 签名仅用于开发覆盖安装；unsigned Release 不能宣称正式发布。

### READY 硬门槛

**只有所有承诺功能有真实证据，且经用户授权的权限方案已经落实，才能标记 `READY`。** 任一关键项目存在以下情况必须 `NOT READY`：

```text
核心传感器读数接错 / 游戏读不到输入
Tilt/Gyro 坐标映射错误导致不可控制
Solar 手动降级失效
RTC 被模拟帧倍率错误推进
真实 Rumble 未实现却声称通过
未经批准添加 VIBRATE 或其他权限
后台传感器或震动继续运行
Native callback 发生 use-after-free、泄漏或竞态
Phase 2 battery/State/Autosave 回归失败
Phase 3 FF/Rewind/Input 回归失败
Phase 4 方向/实际 Surface 画面回归失败
新增 INTERNET / 未知 native binary / 外部动态插件
测试证据或人工验收被伪造
```

若外部硬件/授权不可得，允许安全地完成其它实现并给出 `NOT READY` + 清楚的阻断原因，不得越过安全约束。

---

## 19. 禁止进入下一阶段

此轮禁止：

```text
完整 Library / 封面 / 游戏商店
GB/GBC 支持
Cheats / IPS / UPS / BPS
本地联机 / Wi-Fi / Bluetooth Link
SkyEmu 第二核心
云同步 / 账号 / RetroAchievements
第三方插件 / 下载 Shader
正式 Release keystore / AAB 发布
```

Phase 5 成功定义：**真实 GBA 卡带外设能力通过可信、受限、可回退的 Android 适配运行，用户能理解并控制传感器/光照/震动，而此前的存档安全、运行体验与画面稳定性完全不退化。**

最终状态只在证据齐备后写：

```text
Phase 5 — READY
Phase 6 — NOT STARTED
V1 Release — NOT READY
```

---

## 20. 公开技术来源（核查锚点，不替代本地固定源码）

- Pinned mGBA `interface.h`：`https://github.com/mgba-emu/mgba/blob/26b7884bc25a5933960f3cdcd98bac1ae14d42e2/include/mgba/core/interface.h`
- Pinned mGBA Libretro adapter（**仅公开 API 参考，不引入 Libretro**）：`https://github.com/mgba-emu/mgba/blob/26b7884bc25a5933960f3cdcd98bac1ae14d42e2/src/platform/libretro/libretro.c`
- Android SensorManager：`https://developer.android.com/reference/android/hardware/SensorManager`
- Android Sensors Overview：`https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview`
- Android haptic feedback / `performHapticFeedback` 限制：`https://developer.android.com/develop/ui/views/haptics/haptic-feedback`
- Android Manifest permission `VIBRATE`（normal）：`https://developer.android.com/reference/android/Manifest.permission#VIBRATE`

**注意：公开上游最新文档不保证与固定 mGBA 0.10.5 完全一致，任何具体实现判断必须以仓库中固定 commit 的编译源码和实验验证为准。**
