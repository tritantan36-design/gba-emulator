# Phase 7 Remote CI execution

> Phase7.5后续的0.7.5候选、真实新CI与分批定向结果见 [7.5报告](PHASE_7_5_UI_UX_POLISH_REPORT.md)。
> 本文保留Phase7/0.7.0历史范围，不替代最新人工交接及开放性能项。

2026-10-08，Asia/Shanghai。**Remote CI PASS**。
Repository: [tritantan36-design/gba-emulator](https://github.com/tritantan36-design/gba-emulator)；
branch `main`；tested commit `280782965cc2ceb4a3354ba8e26cdde1072aaae7`。
Workflow `Phase 0-7A ordinary QA`，push实际触发。
[Run 37751328064](https://github.com/tritantan36-design/gba-emulator/actions/runs/37751328064)
conclusion `success`；build job 2026-10-08T08:39:54Z–2026-10-08T08:46:31Z，**397秒**。

| Job / step | Result |
|---|---|
|build|SUCCESS|
|冻结Temurin / 固定Android tools安装|SUCCESS，版本未升级|
|Debug/unsigned Release、JVM、lint、JNI/Activity测试编译、依赖导出|SUCCESS；本次新增opt-in游戏测试编译包含在内|
|host native PCM queue / source-built mGBA|SUCCESS，1/1 + 7/7|
|permission/dependency/license/source/APK audit|SUCCESS；严格校验保持开启|
|artifact export|SUCCESS|
|可选self-hosted ARM64 device|SKIPPED；原workflow仅显式dispatch device_tests时运行。不是设备测试PASS|

[Artifact 11537839324](https://github.com/tritantan36-design/gba-emulator/actions/runs/37751328064/artifacts/11537839324)：
`phase7-ordinary-qa-280782965cc2ceb4a3354ba8e26cdde1072aaae7`，23,325,972 bytes，
查询时未过期，retention 14天。包含远程Debug/unsigned Release、JVM XML、lint、
运行时依赖、host CTest日志和本次source audit。远程包与本地候选分别保留来源，
不把Linux产物当作本地候选相同SHA。本次原始run/jobs/artifact JSON已保存在本地evidence；历史日志ZIP保留。
本次日志ZIP下载的网络限制另记于文末。

## 原始失败与实际修复

| Run | Result / fix |
|---|---|
|[37738293531](https://github.com/tritantan36-design/gba-emulator/actions/runs/37738293531)|FAIL：setup-java无法解析冻结21.0.12.1+1；改为官方同版本Linux归档，固定SHA256并核对release|
|[37738443457](https://github.com/tritantan36-design/gba-emulator/actions/runs/37738443457)|FAIL：runner无sdkmanager；显式bootstrap固定cmdline-tools19.0及原冻结SDK/NDK/CMake|
|[37738609530](https://github.com/tritantan36-design/gba-emulator/actions/runs/37738609530)|FAIL：严格验证缺Linux AAPT2；核验Google官方同版本文件，补精确SHA256|
|[37739040680](https://github.com/tritantan36-design/gba-emulator/actions/runs/37739040680)|FAIL：Phase6基线只有被忽略的本地路径；导出原始记录到docs/baselines/phase6.json，保留原始capture SHA并移除本机路径/git状态|
|[37740246698](https://github.com/tritantan36-design/gba-emulator/actions/runs/37740246698)|SUCCESS：上述修正及兼容测试variant配置的独立远程验证|

没有关闭测试、降低断言、删除依赖验证、跳过失败job或绕过许可审计。
CI使用固定官方工具归档；源码/第三方版本没有升级。基线公开导出保留实际原记录
与SHA；不是新造历史fixture。CI audit写入本次build/reports，防止上传旧审计报告。

Warnings：上游ARM decoder signedness编译警告；upload-artifact底层Node
punycode/url.parse弃用提示。均未导致失败，未通过屏蔽警告修改第三方核心。
本地lint 0错误/4警告。原始失败及warning文本保留。

用户提供目标URL后，连接origin并保留原始main初始提交
`edd2590be74688b79656ee5975f4b52fc513a944`，不force-push。
源码发布前3242文件约49.9MB审计、2953固定vendor文件SHA核对；原始设备日志、
截图、历史APK/State和整个本地evidence目录不发布。无keystore/凭据/商业ROM/BIOS。

**CI PASS不等于Phase7 READY**。新增完整homebrew执行见gameplay报告；部分类型仍未覆盖，API29 SAF显示风险
MONITORED，Phase2无历史资产NOT_TESTED，人工验收PENDING_USER；7.5/8未开始。
文档收尾提交不改变以上受测代码；本报告的PASS明确绑定上述代码提交与run。

本轮新增测试仅在显式指定并暂存已审查哈希ROM时执行；CI编译而不下载或运行第三方游戏。
上次文档提交214296c也真实通过run37741229539，artifact11533139625。
最新代码run37751328064的build、host、审计、artifact均SUCCESS；可选device按原条件SKIPPED。
API29本地游戏测试与远程CI是不同证据。原始日志ZIP下载遇SSL EOF，run/jobs/artifact
JSON已经保存；该下载限制不改变GitHub已完成SUCCESS的结论，历史日志仍保留。
