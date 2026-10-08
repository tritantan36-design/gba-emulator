# Phase 7B Lite — Deferred Advanced Hardening

日期：2026-10-08（Asia/Shanghai）。

统一状态：**DEFERRED — PRE_PUBLIC_RELEASE_HARDENING**。
本清单针对自用、离线、GBA only、零权限版本；不取消普通导入、数据保护和 native 生命周期回归。

| 项目 | Reason Deferred | When Needed | Suggested Future Phase |
|---|---|---|---|
| libFuzzer / coverage-guided mutation | Lite 使用固定本地 fixture；不再运行历史 parser fuzz 入口 | 公开分发或持续维护前 | Phase 8.5 / Pre-Public-Release Hardening |
| ZIP mutation fuzz campaign | 本轮执行固定 corrupt/truncated/CRC/path/size/count fixture | 扩大 ZIP 支持范围或公开分发前 | Advanced Hardening |
| ASan | 本轮不新建 runtime/toolchain；历史尝试与失败日志保留 | 公开分发前需要独立内存安全验证时 | Advanced Hardening |
| HWASan | 当前设备/工具链未建立此专项路径 | 有合适设备并进入公开发布准备时 | Advanced Hardening |
| Linux/WSL sanitizer campaign | 不为 Lite 建立新的跨平台 campaign | 准备依赖级 sanitizer 审计时 | Advanced Hardening |
| Extended malformed corpus | 本轮仅固定、有限的基础异常输入 | 扩大支持范围或公开分发前 | Advanced Hardening |
| Advanced Release ELF hardening | 保留现有构建与权限门槛，不新增 ELF 专项深挖 | 正式对外发布前 | Phase 8.5 / Pre-Public-Release Hardening |
| Long-duration fuzz / fuzz farm | 与当前自用阶段规模不匹配；不执行长期 mutation | 有持续维护资源和公开用户规模时 | Advanced Hardening |
| Fully instrumented mGBA UBSan investigation | 已有 Android gbaUbsan 开关执行范围与依赖全插桩不同；上游 COMPILE_OPTIONS 覆盖继承选项 | 全依赖插桩验收前，必须处理历史 hash.c:32 signed-shift finding | Advanced Hardening |

历史发现不得写成已消失：原始 parser 小文件 `vfame.c:63` 边界问题的生产入口已有
Lite 最小长度/owned backing 防护及固定 JNI 回归；这不等于修复上游 parser。
全插桩历史 `util/hash.c:32` signed-left-shift finding 未修复，不能用 bridge UBSan 的结果关闭。
原始失败证据保持不变。没有重新执行 fuzz、ASan/HWASan 或更换措辞尝试恢复延期任务。

重新进入上述专项时，重新审核可用环境与授权范围；本清单不预先授权网络测试、第三方目标、
漏洞利用或外部服务测试。Phase 8 正式签名/RC/V1.0 门槛仍独立保留。
