# Phase 7 最终人工验收清单

日期：2026-10-08。**准备稿；Manual Acceptance = PENDING_USER**。
自动门槛仍 BLOCKED，当前不能签署 Phase7 PASS。此清单用于提前准备；
待 Remote CI/API/兼容性缺口处理后，再确认候选包和最终验收结论。
旧阶段人工通过、模拟器或 instrumentation 通过均不代替本次用户确认。

候选：0.7.0/code7，Debug `app/build/outputs/apk/debug/app-debug.apk`；
unsigned Release `app/build/outputs/apk/release/app-release-unsigned.apk`。
完整路径、SHA、构建输出时间及空权限列表见
[final-candidate.json](reports/evidence/phase7/remaining-gaps/final-candidate.json)。
没有 production 签名。当前代码提交 fbc3cb4134bd19f2d5371645049d6684aabea187；
最新候选及审计见 reports/evidence/phase7/remaining-gaps/final-candidate.json。
Debug沿用相同字节；Release重建增加Git提交元数据，其他ZIP条目内容未变。
Debug SHA256: a0cc12c18cab69a6294cdf95fafd516899cb957a1090f9897463b304aef24051
Release SHA256: a4e8a3594e7fba749b6eb9ba5fe676d0c0c302123da0b5536ec3779838ef4314
历史7A双 clean 构建属于旧候选，不适用于这个新 SHA。

使用自己的合法本地游戏；仅反馈类型、时长、现象与 PASS/FAIL/NOT_TESTED，
无需提供 ROM、BIOS 或商业游戏截图。每一行初始状态均为 PENDING_USER。
执行后填 PASS / FAIL；没有相应硬件或游戏时填 NOT_AVAILABLE，并说明原因。
尚未执行仍为 PENDING_USER，不能留空后算作通过。

| 项目 | 操作与通过条件 | 必须实机 |
|---|---|---|
| 正常启动 | SAF 打开合法 GBA，画面、输入、声音正常 | 最终游戏体验 PHYSICAL_DEVICE_REQUIRED |
| Continue | 退出/冷启动后继续上次游戏，身份及进度正确 | OEM 行为 PHYSICAL_DEVICE_REQUIRED |
| Battery Save | 游戏内保存；退出再开仍正确，不被读取旧 State 回滚 | 推荐实机 |
| Quick Save / Load | 明确变化前后保存读取，缩略图、进度对应 | 推荐实机 |
| Slot Save / Load | 四个槽分别保存/读取，无交叉覆盖 | 推荐实机 |
| Autosave / Quick Resume | Home 后返回、退出/重开，恢复最近成功 checkpoint | OEM 生命周期 PHYSICAL_DEVICE_REQUIRED |
| Fast Forward | 2×/4×/8×后恢复1×，恢复声音无持续杂音；8× best effort | 真实音频 PHYSICAL_DEVICE_REQUIRED |
| Rewind | 倒带后松开继续，画面/输入正常，Battery 不回滚 | 推荐实机 |
| Touch Controls | 滑动、多点、A+B/L+R、旋转/后台后无粘键，布局设置保持 | 触感 PHYSICAL_DEVICE_REQUIRED |
| Bluetooth controller | 系统配对后输入、触控合并、断开重连、无粘键 | PHYSICAL_DEVICE_REQUIRED |
| USB HID（如有） | 连接/断开重连、输入；没有设备填 NOT_TESTED | PHYSICAL_DEVICE_REQUIRED |
| 四显示模式 | Original/Sharp/GBA Color/LCD，Fit/Integer、背景，无黑屏或持续卡死 | 最终 renderer feel PHYSICAL_DEVICE_REQUIRED |
| Orientation | 竖屏→横屏→反横屏→竖屏，画面比例/控制布局/进度正确 | PHYSICAL_DEVICE_REQUIRED |
| 后台/前台/退出/重开 | Home 后自有声音停止；返回恢复，无重叠音频、黑屏、输入残留 | OEM lifecycle/audio PHYSICAL_DEVICE_REQUIRED |
| Library | 最近/搜索/排序/详情/重命名/移除；移除库记录不丢保存数据 | 推荐实机 |
| ZIP import | 合法单GBA ZIP导入、重复识别；取消不留下半条记录 | 推荐实机 |
| RTC | 支持游戏/探针的时间推进、后台/State/重开符合预期 | 最终游戏验证 PHYSICAL_DEVICE_REQUIRED |
| Tilt | 支持游戏/探针、校准、方向、旋转/后台恢复 | PHYSICAL_DEVICE_REQUIRED |
| Gyro | 支持游戏/探针、方向/强度、旋转/后台恢复 | PHYSICAL_DEVICE_REQUIRED |
| Solar | 支持游戏/探针、手动设置及真实传感器（如有）符合预期 | 真实光照 PHYSICAL_DEVICE_REQUIRED |
| 截图/偏好 | 私有截图、缩略图、显示/输入/外设偏好重启保持 | 推荐实机 |
| 30–60分钟真实游玩 | 单次实际游玩，记录时长/类型/中断，检查性能恶化/黑屏/杂音/丢档 | thermal/battery/audio PHYSICAL_DEVICE_REQUIRED |

代表性类型分别记录：platformer、action、audio-heavy、high-load/3D、save-heavy、
RTC/sensor。内部 probe 通过不能当作这些完整游戏类型通过。
热量/电池观察注明充电状态、亮度和后台条件；自动模拟器长测不提供实机热认证。
Rumble 继续 DEFERRED — V1 zero-permission policy。
突发进程终止仅保证最后成功 checkpoint，不保证最后每一帧。

用户回填：

```text
日期/机型/OEM/API：
APK SHA256：
实际游玩分钟/类型/中断：
各行：PASS / FAIL / NOT_TESTED（失败现象和最短复现步骤）：
HID/真实音频/传感器/热量电池：
未覆盖类型或硬件：
最终人工结论：PASS / FAIL / 尚未完成
```

只有用户真实完成并确认，才可更新 Manual Acceptance；仍有自动门槛阻塞时，
单独人工通过也不能把 Phase7 改成 READY_FOR_7_5。
