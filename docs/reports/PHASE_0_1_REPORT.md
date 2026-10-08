# Phase 0/1 实施报告

日期：2026-10-06（Asia/Shanghai）  
状态：**READY** — 仅允许进入 Phase 2 的技术验收结论；不等于 V1 可发布。

## A. 实际完成内容

- 完整阅读并原样纳入用户提供的三份文档，执行优先级为 PROJECT_SPEC > ARCHITECTURE > PHASE_0_1_IMPLEMENTATION。
- 建立八模块 Kotlin/Compose Android 工程，Gradle Wrapper、Version Catalog、Dependency Locking 与 SHA-256 Dependency Verification。
- 固定官方 mGBA/Oboe 源码并从源码构建；未下载第三方模拟器 .so，未修改 upstream。
- 实现 EmulatorCore、GameSource、LoadResult、GbaButton、EmulatorCapabilities 与 FrameSource；没有预建存档、回溯、传感器或快进 API。
- 实现每个 Session 独立 Native core、受控 FD 加载、独立模拟线程、线程安全按键、可重复安全 close、无效/stale 句柄检查和 JNI 异常转换。
- 实现 SAF ACTION_OPEN_DOCUMENT，只接受 .gba；ROM 大小限制 192 bytes–32 MiB，Native 读取最多十秒，支持受控 FD。
- 实现 240×160 RGBA8888 → 可复用 direct buffer → GLES2 texture，保持 3:2 比例，Surface/Activity 重建可安全释放和重新建立。
- 实现 mGBA PCM → bounded native SPSC queue → source-built Oboe，启动音频后才恢复模拟，暂停/退出关闭音频。
- 实现 D-pad、A/B、L/R、Start/Select 与 InputRouter；退出、后台、恢复与 Activity 销毁均经 Session 协调。
- 最小 Player，无游戏库、设置页、滤镜、快进或其他 Phase 2+ 功能。
- 建立 CI workflow、单元/native/JNI/Activity 测试、权限/依赖/许可证/源码审计、七份 ADR 和本报告。

## B. 项目目录

绝对路径：`D:\GPT\projects\gba-emulator`

```text
gba-emulator/
├── PROJECT_SPEC.md / ARCHITECTURE.md / PHASE_0_1_IMPLEMENTATION.md
├── README.md / LICENSE / NOTICE
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradlew / gradlew.bat / settings-gradle.lockfile
├── gradle/
│   ├── libs.versions.toml / verification-metadata.xml
│   └── wrapper/gradle-wrapper.jar / gradle-wrapper.properties
├── app/                     # composition root, SAF, Activity, Compose host
├── core-api/                # platform-independent API, models, FrameSource
├── core-mgba/               # adapter, JNI, native worker, native tests
├── emulator-session/        # lifecycle/state/error coordination
├── renderer/                # Original GLES2 texture pipeline
├── audio/                   # native Oboe and bounded PCM ring
├── input/                   # InputRouter
├── feature-player/          # minimal Player and touch pads
├── third_party/
│   ├── mgba/ / oboe/        # unchanged official source and upstream licenses
│   ├── SOURCE_LOCK.json
│   └── mgba-files.sha256.json / oboe-files.sha256.json
├── test-rom/                # Apache-2.0 source and build script, TEST ONLY
├── scripts/                 # audit, source inventory, report, native test runner
├── .github/workflows/android.yml
└── docs/
    ├── SECURITY.md / TESTING.md
    ├── adr/ADR-001..007
    └── reports/
        ├── PHASE_0_1_REPORT.md
        ├── AUDIT_RESULTS.md / DEPENDENCIES.txt
        └── evidence/        # actual build/native/device/signing logs

Each Gradle module has its own build.gradle.kts and gradle.lockfile.
Android modules include src/main/AndroidManifest.xml; test ROMs/providers are androidTest-only.
Build outputs under each module's build/ are ignored by Git.
```

## C. mGBA 与 Oboe 信息

| 组件 | Release tag | 固定 commit | 来源 | License | 上游修改 |
|---|---|---|---|---|---|
| mGBA | 0.10.5 | `26b7884bc25a5933960f3cdcd98bac1ae14d42e2` | https://github.com/mgba-emu/mgba | MPL-2.0，含上游 bundled notices | 无 |
| Oboe | 1.9.3 | `b15f5e39c01a7ada306d959e5129620b145fb8b4` | https://github.com/google/oboe | Apache-2.0 | 无 |

Git transport 两次连接失败，改用官方固定 commit 源码归档 vendoring（记录于 ADR-001），未建立 submodule。
源码归档 SHA-256：mGBA `45dda6406a2525ceff0096b67db1b0c74e74555fab722dfdb33001f65020dc6e`；Oboe `8c9ef2dee084e1c7dac939a664851fb4279cd25ebd0a5496a83e4fb162777b66`。
来源、版本及逐文件哈希分别见 SOURCE_LOCK.json 与 *-files.sha256.json。审计验证保留文件集和全部哈希均与官方归档对应内容相同。
按 ADR-007 排除未使用的 cinema/ 回归 ROM、video logs 与商业游戏画面基线；所有源码、headers、build files 与许可证未改动。
只构建 GBA core，未集成 Qt/SDL/Libretro/GB/GBC/SkyEmu；上游源文件中未启用的能力不作为 App 功能开放。

## D. 架构变化

没有修改正式 ARCHITECTURE.md，也未改变依赖方向。
按正式架构建立 emulator-session，防止 Player 直接访问具体 adapter/JNI。
app 仅在 composition root 构造 MgbaCoreAdapter；业务代码使用 EmulatorCore。
core-api 不包含 Android、JNI、mGBA、GL 或 Oboe 类型；Renderer 无具体核心依赖；Audio 无 Compose 依赖。
本轮方法按阶段文档保持最小集合，不实现正式架构中仅作未来建议的 Save/Rewind/Sensor 接口。
重大初始选择已记录 ADR-001..007：主核心、离线、安全边界、GLES、Android/toolchain、native 音频和生命周期、上游非源码测试资产排除。
FrameSource 和 Debug-only 测试计数器是 Bring-up 所需边界/测试设施；测试计数器不出现在 Release native exports 中。

## E. Android 配置

| 配置 | 值 |
|---|---|
| applicationId | dev.gbalite.app |
| minSdk | 26 |
| targetSdk / compileSdk | 36 / 36 |
| AGP | 8.13.0 |
| Gradle | 8.14.3 |
| Kotlin / Compose BOM | 2.2.20 / 2025.09.01 |
| JDK | 21（本机 21.0.12） |
| NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| Android ABI | arm64-v8a only |
| Version | 0.1.0 / versionCode 1 |

Gradle wrapper jar 校验值 `7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172` 与官方 8.14.3 wrapper checksum 一致。
Wrapper distribution 使用官方 SHA-256；CI actions 固定官方 release commit。

## F. 最终 merged manifest 与依赖审计

Debug 申请权限：`[]`。Release 申请权限：`[]`。
无 INTERNET、网络状态、旧式存储、全盘存储、蓝牙、震动、账户、位置等任何申请权限。
AndroidX optional receiver helper 引入的自定义 permission/uses-permission 已从合并清单移除；本项目未使用该 helper。
完整运行时依赖清单见 DEPENDENCIES.txt；所有版本有 Gradle lock，下载 artifacts/metadata 有校验值。
无 Firebase/Analytics/Ads/OkHttp/Retrofit/在线 WebView/联网 SDK。
APK native libraries 仅 `libgba_bridge.so`（本项目从源码构建，静态链接 mGBA/Oboe/NDK runtime）和
`libandroidx.graphics.path.so`（Google Maven 官方 androidx.graphics:graphics-path:1.0.1，锁版本/校验值）。
后者为已知来源 AndroidX Compose 传递运行时依赖，非未知模拟核心二进制。
完整审计结果见 AUDIT_RESULTS.md，全部 PASS；测试 ROM 不在正式 App APK 内。

## G. 测试、构建与验收结果

| 测试集合 | 执行数 | 失败/错误 | 结果 |
|---|---:|---:|---|
| core-api/test | 1 | 0 | PASS |
| input/test | 2 | 0 | PASS |
| emulator-session/test | 4 | 0 | PASS |
| core-mgba/testDebugUnitTest | 7 | 0 | PASS |
| core-mgba/testReleaseUnitTest | 7 | 0 | PASS |
| core-mgba | 5 | 0 | PASS |
| app | 3 | 0 | PASS |
| native PCM queue | 1 | 0 | PASS |
| native mGBA Homebrew smoke | 1 | 0 | PASS |

JVM 单元执行 21 项（14 个独立测试，其中 adapter 在 Debug/Release 各运行一次）；host native 2 项；实机 8 项，共 31 次执行，最终全部通过。
Host smoke 验证 510 帧、240×160 全帧颜色、全部十个按键、无效 ROM 和 558658 个非零 PCM 样本。
PCM ring 验证 wrap、overflow、underrun、清空与 19999 个并发 stereo pair 顺序。
Android 测试设备为实际 arm64 手机，Android 16/API 36。JNI 验证非静音 PCM 已被 Oboe callback 消费、pause 后帧数停止、resume 后继续、30 次 create/destroy 后没有残留注册句柄。
Activity 测试验证 ACTION_OPEN_DOCUMENT、测试文档 provider → Player、真正 GLES 红色显示、触控 A 改变实际显示、后台/恢复和 Activity recreate。
用户通过真实系统文件选择器选择自制 ROM，明确确认：画面、声音、各按键和后台恢复都正常。
Surface/Activity recreation 在本阶段关闭旧 core；不自动恢复游戏进度。

构建：Debug APK、unsigned Release APK、JNI/Activity 测试 APK 全部成功；固定 lock/checksum 的最终构建成功。
lint：0 errors，14 warnings。剩余警告为已固定依赖的更新提醒、项目不支持 ChromeOS x86_64，以及 Android backup rules 建议；未禁用 lint 或创建 baseline。
Native ELF 检查：stack protector/FORTIFY imports、GNU_RELRO、BIND_NOW、GNU_STACK RW（无 X）、PIC/DYN 与 16 KiB alignment；Release 不含 Debug-only test exports。
PIE 针对 Android 平台进程；本项目构建的是位置无关 shared library。
ASan/UBSan 配置已预留，未执行 sanitizer runtime 测试；恶意 ROM fuzzing 尚未完成。
CI 配置已建立，等价任务在本机通过；尚未向远程仓库发布或实际触发 GitHub Actions。

首次设备测试曾因独立 test APK provider 依赖 target APK 的 Kotlin runtime 而失败；provider 改为 Java，重新编译/lint/实机测试全部通过。
另一次 test APK 安装受手机系统安装确认影响；用户确认安装后 JNI tests 正常完成。未绕过手机安全策略。
关键最终日志存放 evidence/；失败尝试的原始日志仍在本机，未掩盖验证过程。

## H. 合法测试 ROM

来源：本项目原创 `test-rom/bringup.s`，Apache-2.0。
由官方 NDK clang/ld.lld/llvm-objcopy 从源码构建，332 bytes。
SHA-256：`219485756a043e5e2e51fe0ae9890e0462859dad9479ce4208a2c98284d15ced`。
无商业 ROM、Nintendo logo 或 Nintendo BIOS；使用 mGBA 内置 HLE/skip BIOS。
生成文件只作为 androidTest asset，真实 SAF 验收在用户手机 Download 放置同一测试文件；不随 App 分发。
单个测试 ROM 不构成商业游戏兼容性或硬件测试套件结论。

## I. 已知问题

- 开发版本无持久化，退出/换 ROM/Activity recreation/进程结束会丢失进度，UI 已明确提示。
- 只实机验证 Android 16 arm64；API 26 设备、旧机 OpenSL fallback 尚未验证。
- 当前为基本 PCM queue 和 frame timing；未做高级延迟调优、设备断连/音频路由变更专项验收。
- 短期 smoke/lifecycle 测试通过，长时间游戏兼容性和严格 native heap profiling 尚未完成。
- lint 的上述 warning 仍保留；未扩展 ABI 或追逐新版本以消除版本提醒。
- Release 目前 unsigned，不能当作正式发布包；未创建/使用 production keystore。

## J. 尚未完成项（按阶段划分）

本轮 Phase 0/1 必需的实现、构建与设备验收已完成。
远程 CI 执行、更多设备/ROM 长测和 sanitizer 实测仍需后续验证，不宣称已完成。

**Persistence not implemented until Phase 2.**
SRAM、Save State、Autosave、Quick Save/Load、Quick Resume：未实现。
Rewind、Fast Forward UI、Shaders/GBA Color/LCD、完整库/封面、设置、控制编辑、手柄/GBA sensors：未提前实现。
V1 正式签名、发布校验和、release provenance 和安全门槛必须按后续阶段完成。

## K. 已知风险

- 音频设备路由变化与旧设备 fallback、低延迟稳定性仍有设备差异。
- 极端 Surface/Activity/进程生命周期及长时间 native leak 需要扩大验收样本；当前已有有界所有权、worker join、thread-safe frame copy 和重复关闭测试。
- 不可信且通过基本 header 的 ROM 仍进入 mGBA；sanitizer/fuzz/corrupted execution 回归尚未覆盖所有恶意输入。
- 主核心固定未修改，升级必须显式更新 release+SHA，重新验证兼容性；Phase 2 起存档回归优先。
- 目前无存档保护，用户不得用开发版本保存重要进度。
- 仅 arm64，不支持其他 Android ABI；本机 x64 native tests 不增加 APK ABI。
- 无 release signing credentials，严禁将 Debug APK 作为正式发布包。

## L. 是否满足进入 Phase 2 的条件

**READY**

理由：Phase 0 工程/源码/版本/许可证/API/NDK/CI 配置与 Debug 构建完成；Phase 1 合法 ROM 经 SAF → Session/Core API → mGBA → GLES/Oboe/Input 的完整链路已在实机验证，所有规定类型测试及 lint 通过，无申请权限、无联网/Analytics/Ads、无未知 emulator binary、未越过架构或提前实现 Phase 2+。
此结论仅是下一阶段技术进入条件。上述设备覆盖、长测、安全与正式签名限制仍保留；不会自动开始 Phase 2。

## 构建产物校验

| 产物 | 本机相对路径 | SHA-256 |
|---|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | `be4fc87763c7d2fd7144098eaf1e676b21bd7b37b3999b27fd64168a66b0240b` |
| Unsigned Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | `564953cbe41e9465949f521378abf70fb8048e19f322f049f33fcb6608448a04` |
| JNI test APK | `core-mgba/build/outputs/apk/androidTest/debug/core-mgba-debug-androidTest.apk` | `91b1e2a878bc96462711c0c113e7847517f4502607e553396bebf77faea4c6a4` |
| Activity test APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `5d354ed2dd3230c016512e55e33a78eef8d20ff9a23699b05efb9967dae2cae2` |

Debug signer certificate SHA-256：`3660dfb189306e49d973a7f91c9cbc1cb35cc93921f0292e8db28b3fa10b4094`，仅开发测试。
Release 无签名；未生成正式 AAB，没有正式 release commit/version/provenance 声明。
项目已独立 git init；本轮代码尚未提交或推送，用户可直接审查当前源代码和测试证据。
