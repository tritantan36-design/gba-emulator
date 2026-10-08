# Phase7 + Phase7.5 合并人工验收

2026-10-08。状态：**PENDING_USER**。此表不预填通过。
本次安装普通 arm64-v8a Debug 0.7.5 / code8；unsigned Release 仅校验归档，不能直接安装。
不要安装 compat / TEST-ONLY 包。不要为签名冲突卸载或清除旧数据，先反馈错误。

代码：`400b3bc7ac8202993d81c234e0e7b039659cb58d`。
Debug SHA256：`014cd2f3a71b881478b3b33b146690d52b9339c948f903681e50fe09949dd324`。
Release SHA256：`61c2f7fbb18c72a700200c45f53e72347bd7154481467aa14890882dbb13ed40`。
同目录 SHA256SUMS.txt 可核对。无需提供 ROM 或 BIOS，使用自己的合法本地游戏。

建议顺序：安装并核对版本 → 首页/游戏库/设置浏览 → 一次30–60分钟实际游玩，穿插存读档、快进、倒带、旋转及后台 → 回填。
每项填 PASS / FAIL / NOT_AVAILABLE / PENDING_USER；失败写最短复现步骤。
缺少硬件或游戏不算通过。实际游玩时长、游戏类型、充电/亮度及中断情况单独记录。

| 项目 | 操作与通过条件 | 结果 |
|---|---|---|
| 启动/SAF/ZIP | 系统文件选择器导入合法 GBA / 单游戏 ZIP，重复不多条，取消不留半条 | PENDING_USER |
| 首页 | Continue 清楚、最近游戏可达，不遮挡状态栏 | PENDING_USER |
| 游戏库 | 搜索/排序/详情/重命名正常，长名称不挤出操作 | PENDING_USER |
| 详情/移除 | 主按钮明显，管理操作有层级；移除不丢存档 | PENDING_USER |
| 空/加载/错误 | 提示和操作明确、可取消/重新选择 | PENDING_USER |
| 设置导航 | 7分类；二级只有顶部返回，底栏只在根页；系统返回逐级正确 | PENDING_USER |
| 设置实际生效 | 显示、输入、外设偏好重开保持；关于/许可可读 | PENDING_USER |
| 小屏/大字体 | 调大字体，文字不覆盖按钮，全部操作可滚动到 | PENDING_USER |
| 竖屏控件 | D-pad/A/B/L/R/Start/Select清楚，菜单可达，视觉与点击位置一致 | PENDING_USER |
| 横屏/反横屏 | 同一风格，无黑屏、画面比例变化或遮挡 | PENDING_USER |
| 多点/滑动 | 方向滑动、A+B、L+R，多指松手/旋转/后台无粘键 | PENDING_USER |
| 布局编辑 | 竖/横独立；拖动、单个/整体大小、透明度、默认、保存重开一致 | PENDING_USER |
| Pause | 六操作易找，暂停画面冻结，继续恢复；返回子页不误恢复 | PENDING_USER |
| Quick Save/Load | 保存后改变进度，再读取，缩略图/时间/进度对应 | PENDING_USER |
| 四个手动槽 | 分别保存读取，不互相覆盖；空槽不可读 | PENDING_USER |
| 游戏内 Battery | 游戏内保存、退出重开仍在，旧即时存档不回滚正常存档 | PENDING_USER |
| 自动恢复 | Home返回、退出冷启动 Continue 恢复最近成功 checkpoint | PENDING_USER |
| 快进 | 2/4/8×持续与按住，恢复1×及声音正常，8×尽力模式 | PENDING_USER |
| 倒带 | 按住倒带、松手继续，无粘键/黑屏，Battery不回滚 | PENDING_USER |
| 四显示模式 | Original/Sharp/GBA Color/LCD、Fit/Integer、黑白背景实际正常 | PENDING_USER |
| 真机音频 | 正常游玩、快进切回、后台无杂音/重叠音频/后台持续出声 | PENDING_USER |
| 蓝牙手柄 | 配对、触控合并、断开重连无粘键 | PENDING_USER |
| USB HID（如有） | 连接、输入、断开重连 | PENDING_USER |
| RTC（支持游戏） | 时间推进、后台/State/重开符合游戏预期 | PENDING_USER |
| Tilt/Gyro（支持游戏） | 校准、方向、旋转及后台恢复正确 | PENDING_USER |
| Solar（支持游戏） | 手动值和真实光照（设备支持时）正确 | PENDING_USER |
| 截图 | 保存私有游戏截图，不混入触控和菜单；缩略图正确 | PENDING_USER |
| 30–60分钟游玩 | 无性能持续恶化、黑屏、丢档、杂音，记录热量/电池和条件 | PENDING_USER |
| 视觉/舒适度 | 字体、对比、按钮反馈、单手/双手触感是否接受 | PENDING_USER |

请分别记录 platformer / action / audio-heavy / high-load-3D / save-heavy / RTC-sensor 的类型与实际分钟。
现有模拟器/探针结果不能替代真机 HID、声音、热量、触感或完整游戏类型。
本次模拟器加速性能门槛未全部通过，旧包对照也失败；仍为独立开放项，不能仅以视觉验收代替。
已知限制：API26/29真实ARM与第二OEM未测；API29历史SAF短暂黑帧仍MONITORED；
Phase2历史State缺档；action/audio-heavy/save-heavy覆盖PARTIAL，RTC/sensor完整游戏MISSING。
Rumble仍DEFERRED，保持零权限。未收到你明确接受这些限制前，不写READY。

回填模板：

```text
日期/机型/OEM/API：
安装包SHA256：
实际游玩游戏类型/分钟/中断：
充电/亮度/热量电池：
上表各项结果（失败注明最短复现步骤）：
缺少的游戏类型或硬件：
UI视觉/触控舒适度是否接受：
已知限制是否接受（逐项）：
最终人工结论：PASS / FAIL / 尚未完成
```

Phase7 与7.5目前仍 NOT_READY，Phase8 NOT_STARTED。完成此表后结合其余缺口判定。
