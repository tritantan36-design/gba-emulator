# Phase 7A-0 完成与 Phase 7A 恢复检查点

日期：2026-10-08（Asia/Shanghai）。本轮测试已结束，项目模拟器已关闭，手机未被操作。

**7A-0：PASS_WITH_NOTE / Infrastructure READY。Phase 7A 与 Phase 7：NOT READY。**
7B 仍暂停；Phase 7.5 / 8 未启动；V1 未就绪，Release 未正式签名。

## 本轮实际完成

- 建立官方 SDK → API 34 AVD → install / launch / SAF / instrumentation → 日志与截图链路；API 34 / 36 各 3 项最小测试通过。API 26 / 29 的 ARM ABI 限制如实保留，GMD 评估后延期。
- 暂停点的五项失败方法，在 API 34 上原断言定向复验通过：JNI 2/2，21.911 秒；Activity 3/3，86.557 秒。没有宣称重新通过完整 15 / 43 项测试批次。
- 为原有 ViewModel 生命周期队列增加完成确认，避免将周期存档的临时 PAUSED 当作后台指令完成；保留后台连续 5 秒零帧推进断言。先前 8 轮回归通过，之后在软件后端完成 50 轮 Home / 存档争用。
- 独立验证 100 次实际显示模式变化，覆盖四种模式，每次检查真实非黑画面，并保持同一个 Surface 上下文（52）。与 50 轮 Home 测试合计 2/2，通过，337.991 秒。此处画面检查不替代像素黄金值或最终人工体验。
- 同一官方 API 34 镜像，使用 SDK software / Google SwiftShader 后端，完成独立真实 60 分钟长测：instrumentation **PASS 3614.31 秒**；指标 3612378 ms、62 个样本、60 个循环样本、最终 `complete`。约 01:38:38 至 02:38:51，未拼接其他运行。
- 长测完成 50 轮正横屏 / 反横屏 / 竖屏、Home / 恢复及 ROM 切换，共 150 次方向切换。120 次模式选择仅有 90 次有效变化，因此另设上述 100 次真实变化测试。倒带分支条件触发，未单独计数，不宣称执行了 60 次倒带；有效的独立倒带回归证据另行保留。
- 新候选两次干净 unsigned Release 构建逐字节一致，依赖一致；Debug / 测试包构建、lint（0 errors / 4 warnings）与普通 APK 来源权限审计通过。未新增权限、升级核心 / 冻结工具链或改变存档格式。

## 资源与证据局限

完整软件长测起始 / 采样峰值 / 结束：

| 指标 | 起始 | 采样峰值 | 结束 |
|---|---:|---:|---:|
|RSS KiB|246300|269620|269476|
|Native heap bytes|27697856|32336704|28567456|
|Java used bytes|7713168|16086592|7322568|
|FD|151|160|157|
|OS threads|40|53|46|
|JVM threads|32|49|43|

结束采样 Session 为 STOPPED，但当时 renderer 仍为纹理 1 / 程序 1 / 缓冲 2 / Surface 1；未宣称所有 GL 对象已释放。测试 runner 结束后被动查询未发现应用 PID，这不证明进程终止前每个对象的释放时点。循环边界采样可能漏掉瞬时峰值；未证明绝对无泄漏。每个 core 的音频 underrun 计数会重置，采样峰值 1146、停止后 0 不是零 underrun 或人工音质通过。

当前捕获的应用日志未检出 FATAL / native crash / ANR 或明确 GL 错误；不是所有 GPU 驱动的认证。已保存并查看软件后端的实际游戏截图，测试彩色画面及触控可见。

## 失败历史不覆盖

- 手机长测按用户要求停止：815002 ms / 14 个循环样本，非 60 分钟通过；未重启手机长测。
- API 31 暂停批次的 2 项 JNI / 3 项 Activity 失败仍保留。
- API 34 host 首次运行在 1226261 ms / 20 个循环样本失败，后台帧数 2482→2483；随后补充实际生命周期完成确认。
- host 再次运行在 3004375 ms / 48 个循环样本失败，出现约 99 秒图形停顿、onStop 延后及 Home 等待超时。原始根因尚未确认；软件后端通过不等于修复了此问题。未修改 Windows 驱动、功能或电源方案。
- 初始 7A-0 的归档、历史失败日志、原始测试数据与旧 APK 哈希保留。旧 Release SHA-256 `079ad749f9b0dbb227b77a0854372729fae485329856ea459fb151144f387696` 不变。

新候选 unsigned Release SHA-256：`77dbada49b1099b96ead34de3cc39eb92fc06f4e252ba1d33af685d3861d4fb7`。

## 保留的门槛与下一项

实际远程 CI 未运行（无远程仓库 / 基础设施）；API 26 / 29 等 ARM 应用覆盖、第二 OEM、更广泛合法游戏与 Phase 2 历史 State 缺失；最终人工及硬件反馈仍未收到。此前“会自行验收”不记为 PASS。host 图形停顿风险及暂停的 7B 发现未关闭。未新增 fuzz / sanitizer / Release ELF 专项或正式签名。

后续需补齐这些证据及环境，再判断 7A 是否 READY_FOR_7B；不得因模拟器链路就绪而跳过门槛。日常合适自动测试优先模拟器，真实手机仅用于明确的硬件 / 最终人工项目。当前没有继续运行的测试批次。

## 保存与 Git

归档：`D:/GPT/artifacts/gba-emulator/phase7/resume-20261008/`，包含报告、源码 / 证据哈希、相关源码与脚本、APK 和新增证据。历史暂停归档 207 个文件的哈希复核结果单独保存。

仓库仍无 HEAD / 首次提交、无远程仓库，项目文件未跟踪。采用文档与 SHA-256 检查点记录，未创建混入全部历史的首次提交，未回滚、清理或上传源码。

完整详情：[7A-0 报告](PHASE_7A_0_ANDROID_EMULATOR_INFRASTRUCTURE_REPORT.md)、[7A QA 报告](PHASE_7A_COMPATIBILITY_STABILITY_RELEASE_QA_REPORT.md)、[Phase 7 总报告](PHASE_7_COMPATIBILITY_STABILITY_SECURITY_REPORT.md)。
