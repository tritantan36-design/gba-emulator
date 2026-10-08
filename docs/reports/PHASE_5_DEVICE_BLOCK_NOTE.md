# Phase 5 本轮设备阻断记录

2026-10-07。本文件为执行观察记录，不是新增自动 PASS 日志。

ADB 检测到原 OPPO PLG110（serial 3B15AV01Z0000000）。普通 JNI 完整 15 项通过。
准备 Activity 时，dumpsys window policy 显示 showing=true、SCREEN_STATE_OFF，
已请求用户解锁并保持亮屏。未在锁屏状态把 Activity 回归记为通过。

UBSan JNI 本轮前 11 项完成，第 12 项既有倒带测试开始后停止推进。
读取测试进程 PID 28605 的 /proc 自身线程等待点：JUnit 线程 28688 和 worker
线程 28933 均为 do_freezer_trap，表明观察时它们处于 Android 后台冻结等待。
SIGQUIT 提示栈已写入 tombstoned；/data/anr 无读取权限，debuggerd 提示 root required，
未获取 root 或绕过权限。相关 logcat 保留在 phase5-ubsan-jni-diagnostic.log。
该观察不能证明不存在其他缺陷，必须在解锁前台条件下重跑整轮。

使用 am force-stop 仅停止独立 JNI 测试包 dev.gbalite.coremgba.test，未卸载或清空应用，
未改用户存档。保留不完整 UBSan 日志，不标为 15 项通过；恢复普通构建待后续验收。

## 后续重跑结果

用户解锁后，新增仅位于 androidTest 的前台 JNI 测试 Activity，使用 KEEP_SCREEN_ON，
不增加权限、后台服务或生产代码捷径。相同普通 JNI 15 项在 42.191 秒完整通过；
UBSan JNI 15 项在 45.066 秒完整通过，未出现 sanitizer trap。
证据为 phase5-normal-foreground-jni.log 与 phase5-ubsan-foreground-jni.log。
这次重跑关闭该 JNI 执行阻断，但不证明所有可能冻结问题已永久消除。
