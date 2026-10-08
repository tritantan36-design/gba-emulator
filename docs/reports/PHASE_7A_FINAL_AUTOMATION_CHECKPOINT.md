# Phase 7A 最终自动验证检查点

日期：2026-10-08，Asia/Shanghai。

**Phase 7A：NOT READY。当前可用环境的本轮普通回归已结束；不是阶段 PASS。**

## 本轮实际结果

| 验证 | 实际结果 |
|---|---|
|API34 software 完整 JNI 原批次|14 PASS / 1 FAIL，48.159s；按键释放基线失败|
|原按键方法未改断言单独复验|1 PASS，2.773s；不据此删除失败|
|API34 software 完整 Activity 批次|32 PASS / 3 FAIL / 10 opt-in SKIP，545.451s；总计45项|
|修正后 host 完整 JNI 首次复验|14 PASS / 1 FAIL，38.799s；4×吞吐116.23fps未达原阈值|
|修正后 Activity 三个失败方法|3 PASS，86.362s；包含原四模式性能断言|
|空闲构建进程正常停止后完整 JNI|15 PASS，42.941s；未降低性能断言|
|离线构建 / app lint|PASS；126s；lint 0 errors / 4 warnings|

Activity 原整批仍为 FAIL；后续定向结果证明三项修正/复验通过，不称为重新通过单次完整45项批次。已有有效的60分钟长测、50次Home、100次实际模式切换、RTC、升级、JVM、host与IO证据继续复用。

## 修改与证据局限

- `JniTest.kt` 增加已知无按键红色 RGBA 基线前置条件、逐按键实际 RGBA/metrics 日志；保留两秒按下/松开要求。启动白色或未完成帧不能当作游戏基线，但未记录第一次失败的 RGBA，不能宣称其历史根因已经证明。
- `core-mgba/build.gradle.kts` 将独立 JNI 测试 APK 的 targetSdk 从26明确为36，与应用现有值一致；实际 APK 元数据及旧目标警告窗口日志已保存。冻结 AGP8.13 仍接受此 DSL，未来 AGP9 的弃用提示不通过升级工具链消除。
- `LibraryExperienceTest.kt` 使用正常 Home shell 按键并等待实际停止生命周期，替代未检查的 global action 与固定750ms假定；仍检查清理、索引、LastSession及旧文件保持。
- 无生产源码、权限、核心、存档格式或架构变化；unsigned Release SHA-256 仍为 `77dbada49b1099b96ead34de3cc39eb92fc06f4e252ba1d33af685d3861d4fb7`。
- 主机可用内存约1.5GiB；仅正常停止已确认属于本项目且空闲的Gradle daemon，未停止其他应用。前后性能变化不是根因证明，不删除软件后端4×或host瞬时吞吐失败。
- 模拟器切换时的快照锁超时、未完成启动时的安装失败均保留；确认正常启动后才实际复验。没有删除锁、修改镜像或驱动。

## 未满足门槛与执行顺序

用户明确反馈人工验收尚未完成，并要求在7B结束后一起验收；也再次确认没有远程仓库/CI。二者未豁免。旧ARM API覆盖、第二OEM、广泛合法游戏、Phase2历史State及host图形停顿风险也保留。

先保存本检查点，再依照用户的联合人工验收安排单独开展7B；这是7B任务书第1节允许的7A尚未READY时的独立执行。不得称为7A已PASS、READY_FOR_7B或Phase7已READY。Phase8未启动，Release未正式签名。

原始日志位于 `docs/reports/evidence/phase7/7a-final-gate-*`；用户决定见 `7a-final-gate-user-decisions.json`。旧暂停及恢复归档均不覆盖。
