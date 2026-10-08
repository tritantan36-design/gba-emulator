# Phase 7.5 UI / UX Polish Report

2026-10-08，Asia/Shanghai。代码实现完成；最终定向回归和远程CI结果见 N。
**Manual Acceptance PENDING_USER；Phase7 / Phase7.5 NOT_READY；Phase8 NOT_STARTED；V1 NOT_READY。**

## A. Summary

完成 Round A（Player/Pause/Settings/navigation）及 Round B（Home/Library/Details/states/About）。
用户明确要求“做完7.5我一起验收”，因此按 ADR-020 覆盖文档中的 Phase7 READY-first 及
Round A 人工暂停顺序。此安排不把未完成的 Phase7 缺口算作通过，不启动 Phase8。
UI候选代码 `400b3bc7ac8202993d81c234e0e7b039659cb58d`，分步提交；之后的测试同步修正不改变候选产品字节。
新普通候选0.7.5/code8，Debug开发签名沿用原本机证书；Release unsigned。
最终Debug19762158bytes，API36保留数据升级和冷启动后实际安装SHA与交付完全相同。
Release重新构建刷新Git revision到894ffb4；ZIP payload对比只有
META-INF/version-control-info.textproto变化，其余内容与已审计包逐字节相同。

## B. Scope / boundaries

只改 app 与 feature-player 的呈现及受影响的 instrumentation 定位/回归。
Core/API、Session、Renderer、Audio、Input、Storage、Data、third_party共3064文件逐字节
哈希对比PASS（0修改/0新增）。Room schema、Game ID、存档格式、ZIP/ROM验证、DataStore
合同、输入路由、传感器注册、冻结工具链/依赖/lock/权限/网络均未改。
ADR-020在实现前记录。新增版本标识仅在app build及About。
PROJECT_SPEC.md、ARCHITECTURE.md保留；来源/APK审计继续严格执行。

## C. Visual tokens

共享 GbaUi.kt：背景#f5f5f2、表面#fdfdfb、正文#20232b、次级#626570、强调#66519c；
石墨触控#333842、A/B#864b68，游戏菜单暗色透明表面。间距4/8/12/16/24/32dp；
圆角10/14/20dp及胶囊；elevation0/2/6dp；文字28/24/21/16/14/12/11sp。
原创24dp Canvas统一线条图标，未新增图标库、下载图或联网封面。

## D. Player portrait

保留原画面3:2与Surface/lifecycle合同；原生7个控件。D-pad石墨底板及方向凹槽，
A/B强调色圆面，L/R扁平肩键，Start/Select与菜单胶囊。Menu最小48dp。
没有无映射X/Y/L2/R2。原始profile位置、拖动/单个/整体缩放/透明度可保存。

## E. Player landscape

横屏/反横屏使用同一套绘制与profile；原viewport/Fit/Integer继续负责画面比例。
L/R、D-pad及A/B保持层级，Menu避开主要游戏区域；安全区与沉浸系统栏保留。
浅色App系统栏对比已修正。横屏Pause三列两行，避免六操作挤出屏幕。

## F. Pause

暗色透明面板 + dim，六个主要操作：继续、保存、读取、显示与控制、快进/倒带、退出。
竖屏两列；子页独立顶部返回。截图/完整设置作为次要操作。
保存/读取：Quick独立主按钮 + 4槽缩略图、时间、空状态；空槽读取禁用。
快进2/4/8×持续、关闭恢复1×及按住；倒带保留既有hold模型。
所有保存/读取仍走Session，暂停/输入释放/继续合同未绕过；仅增加结果提示。

## G. Settings

根页7分类紧凑行、图标、实际偏好摘要。显示模式/缩放/背景可选中，控制页内布局预览
可拖动、单个/整体尺寸与透明度、恢复默认及保存。小屏/大字体编辑器可滚动。
音频/游戏运行/存档仅解释真实可用行为；无假开关。外设嵌入原有操作内容。
关于0.7.5及本地许可证。未增加手柄重映射或实际Rumble。

## H. Bottom Navigation

首页/游戏库/设置三个入口，轻量线条图标与淡色选中态。只在根页显示；详情、设置
二级、布局和许可页隐藏，统一顶部返回。系统返回逐级退回，避免重复文字返回。

## I. Home

Continue主卡突出，显示真实最近记录/本地缩略图及恢复入口；最近5项，查看全部。
添加游戏位于TopBar。数据/API仍由现有model/repository提供。

## J. Library

搜索、游戏数、既有排序，轻量缩略图行、最多两行名称及一行辅助信息、详情入口。
现有LazyColumn与2MiB ArtworkCache继续使用；图片IO解码、256KiB/1024边界不变。
无网络封面或大图缓存。重名/重复/不可用文件仍由现有合同处理。

## K. Game Details

单一TopBar、缩略图/名称、突出开始或继续、时长/时间、存档和游戏设置入口。
重命名/重新绑定分组；移除保留确认，继续保留所有保存数据与截图。

## L. Empty / Loading / Error

统一空状态插画/短说明/选择操作；真实导入中spinner/禁用重复添加/取消；
实际多ROM ZIP错误显示操作未完成/原因/重新选择/关闭。未造成功数据或隐藏异常。
截图的空库使用呈现fixture，不清真实数据库；加载与错误来自实际测试provider。

## M. Accessibility

正文与次级颜色分层、选中check不只靠颜色；可点击按钮/行≥48dp，Canvas控件最小
48dp且几何共用绘制/命中。新测试实际渲染并触摸旧圆形外的肩键/胶囊区域及取消释放。
320×568dp、fontScale1.3定向测试检查布局保存和返回可达。
菜单/返回/游戏缩略图具有描述，触控View有组合描述；Canvas每个游戏键尚无独立
TalkBack虚拟节点，完整读屏操作及真机舒适度未认证，须人工记录。

## N. Regression

冻结工具链：普通Debug/unsignedRelease、test、lint、Activity测试编译及依赖导出PASS；
最终构建1m11s。JVM XML共75例，0失败/0错误/0跳过（含debug/release重复执行范围）。
最终source/APK audit105项PASS：arm64 only、权限[]、无ROM/ZIP/BIOS、测试类或sanitizer泄漏。
app lint0错误/20警告（16冻结依赖更新提示+既有ABI/backup/KTX）；feature-player
0错误/4警告（既有ABI/ViewConstructor与新Configuration尺寸/Modifier参数建议）。未升级依赖或压制审计。

自动回归保留真实失败与重跑：

| 批次 | 实际结果 | 解释 |
|---|---|---|
| API29初始20例 | 13通过/7失败 | 固定按钮被错误performScrollTo，测试定位已修正 |
| API29第二批12例 | 10通过/2失败 | 详情Lazy项需容器滚动；慢provider Home返回15秒超时 |
| 正式arm64/API36 15例 | 13通过/2失败，138.724秒 | 新增4项UI全部通过；背景设置菜单需等待异步暂停；性能门槛失败 |
| API36隔离重跑2例 | 1通过/1失败，64.791秒 | 背景设置/实际边距/重启持久化PASS；性能仍FAIL |
| 最终API36补充4例 | 4/4 PASS91.086秒 | 最终Home脚本、背景/实际边距、快进hold/倒带、Battery/Quick/4槽/恢复 |
| API36旧Phase7同机对照 | 0通过/1失败，48.085秒 | 旧包也低于既有性能门槛，不能据此证明新版无性能影响 |

正式候选16个不同功能用例已通过（分批，不能冒充单次16/16）：4个新增UI/几何/大字体、
3个Activity/SAF、2个外设、详情CRUD/设置导航、慢provider重建/Home、四模式细线图案/
原始截图、Home自有音频停止恢复、背景偏好实际边距/重启持久化，以及最终补测快进hold/倒带与Battery/Quick/4槽/恢复。
API29先前已通过触控滑动/多点/取消、合成HID、7次方向切换/真实可见画面、Battery/
Quick/4槽/后台/重建/Resume；初始RoundA1/1 PASS24.478秒。API29最终签名一致包4个新增UI用例均PASS；5例批次4通过/1失败（66.317秒），
慢provider错误文本已正确产生，但后台返回观察到旧Activity仍CREATED、focus=false；
原15秒限制及数据断言未放宽。定向改用测试shell并沿用既有Renderer Home的SINGLE_TOP以返回同一Activity，
**1/1 PASS11.939秒**；最终API29为4个UI + 1个Home用例分批通过，不称单次5/5。
这修正测试实例观察方式，不是Core、导入或产品lifecycle变更。
中间因Android用户目录切换造成的Debug证书冲突安装失败未作为新候选证据；
最终普通包及TEST-ONLY补测均明确沿用旧开发证书，无卸载/清数据。

[真实GitHub CI run37757207803](https://github.com/tritantan36-design/gba-emulator/actions/runs/37757207803)
对UI代码400b3bc结论SUCCESS，build408秒；冻结工具安装、Debug/unsignedRelease/JVM/lint、
JNI/Activity测试编译、host native1+7、严格来源/APK审计及artifact输出均SUCCESS。
可选self-hosted device按原workflow条件SKIPPED，不能当设备PASS。
[Artifact11540892030](https://github.com/tritantan36-design/gba-emulator/actions/runs/37757207803/artifacts/11540892030)
23,440,213bytes，查询未过期；本机候选使用本机开发证书，不能与CI APK混用SHA。
最后测试同步提交a4ee1be仅改Home返回与焦点/诊断，已本机编译及API29/API36运行；文档提交不改变被测产品源码。

## O. Performance

无每帧Compose轮询，按键反馈100ms仅输入变化启动，detach取消；原渲染线程不变。
行复用与缩略图缓存有界。API29 500条纯metadata滚动/搜索测得745ms，无ROM读取/DB插入。
正式API36记录1×显示约59–60fps；但原既有加速门槛203.0735fps未全部通过：
首次Sharp4×195.8167fps；隔离GBA Color4×188.8364fps；旧Phase7对照GBA Color8×201.2566fps。
不放宽断言、不改Core/Renderer/调度。两包libgba_bridge.so SHA完全相同
`5d5d2b7224c041168380ef74858450a72448355c825ccca03c877bff50ad185b`。
这证明测试环境的旧包也有门槛失败，不能直接归因UI，也不能排除新版性能影响；
**性能资格仍OPEN/NOT_PASS，需要受控性能定位或实际ARM设备验证。**
原始日志与renderer metrics保留在ignored evidence；本轮未声称四模式加速性能PASS。
模拟器/官方ARM translation不提供真机GPU、音频听感、热量或电池结论。
不重跑已通过的Phase7长测/JNI/UBSan；旧结果仅复用对应引擎未改范围。

## P. Screenshots

本机ignored `docs/reports/evidence/phase7_5/round-a/`11张：portrait、landscape、reverse
landscape、Pause root/save/load/fast-forward、Settings root/display/controls、bottom navigation。
Round B7张在round-b：home/library/details/empty/loading/error/about。
另有320dp/1.3字体settings/layout证据。均实际截图；红屏是合法内部persistence测试画面，
不是游戏体验/显示色彩认证。完整游戏缩略图属于之前本机许可测试，未上传公开仓库。
最终18张均来自已核对SHA的正式arm64/API36候选，另2张是相同候选内320dp/1.3字体
的呈现约束fixture（右侧/下方留出的真实屏幕区域不是产品黑屏）。全部20张已逐页查看。
详情截图等待输入法退出，空状态截图在测试空库列表，未清真实数据。

## Q. Manual Acceptance

用户要求最后一起测，全部仍PENDING_USER。
[合并清单与回填](PHASE_7_AND_7_5_MANUAL_ACCEPTANCE.md)。新候选在本机ignored
`evidence/phase7_5/manual-handoff/`；旧Phase7候选和报告保留历史身份，不混用SHA。
Debug：`014cd2f3a71b881478b3b33b146690d52b9339c948f903681e50fe09949dd324`；Release：`61c2f7fbb18c72a700200c45f53e72347bd7154481467aa14890882dbb13ed40`。

## R. Known Issues

Phase7 API29历史SAF黑帧可恢复但根因仍MONITORED；历史GPU/驱动风险仍保留。
本次加速性能门槛未通过（旧包同机也失败），属OPEN/NOT_PASS，不能被人工视觉通过覆盖。
本轮小屏与UI回归不构成该根因修复。完整Dark Mode、自定义主题、联网封面未实现，
这些是文档允许保留项。实际Rumble继续DEFERRED（零权限策略）。

## S. Risks

真实HID/声音/触感、第二OEM/API26/29ARM、长时间热量电池待用户。
Phase2原始历史State缺档NOT_TESTED；action/audio-heavy/save-heavy完整游戏覆盖PARTIAL，
RTC/sensor完整游戏MISSING，probe不能代替。尚无用户接受限制记录。
签名仅开发测试，未开始production signing；不同电脑/CI Debug证书可能不同。

## T. Unfinished

人工合并验收与上述Phase7独立缺口开放；不能声称只剩UI视觉确认。
性能门槛OPEN/NOT_PASS，需进一步受控定位或真实ARM设备验证；未请求新增能力或Phase8工作。

## U. 是否允许进入 Phase8

**否。Phase7/7.5 NOT_READY；Manual PENDING_USER；Phase8 NOT_STARTED；V1 NOT_READY。**
UI实现完成和模拟器自动验证不代替用户真机验收、游戏广度及限制判定。
