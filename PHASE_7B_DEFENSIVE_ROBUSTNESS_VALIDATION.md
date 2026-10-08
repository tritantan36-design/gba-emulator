# GBA Lite — Phase 7B Defensive Robustness Validation

**文档版本：** v1.0  
**阶段定位：** Phase 7B — 仅针对项目自有代码的防御性鲁棒性与内存安全验证  
**执行者：** Codex / 开发代理  
**上位约束：** `PROJECT_SPEC.md`  
**架构约束：** `ARCHITECTURE.md`  
**来源：** 从原 `PHASE_7_COMPATIBILITY_STABILITY_SECURITY_HARDENING.md` 拆分而来

---

## 0. Authorized Scope

本阶段是：

> **针对用户自有、完全本地的 Android GBA 模拟器项目进行防御性软件质量保障。**

所有测试严格限制于：

```text
当前仓库自己的代码
GBA Lite 自己的 ROM / ZIP / Save / State / Metadata 解析路径
本项目自己的 JNI / C / C++ / Kotlin / Java 代码
本地 synthetic malformed fixtures
合法可再分发测试 ROM
本项目自己的 Android Emulator / 用户自有设备
本项目自己的 CI
```

本阶段不包含：

```text
第三方系统渗透测试
互联网主机扫描
第三方服务漏洞利用
凭据获取
权限提升
恶意软件
持久化
规避检测
第三方攻击链
未经授权访问
漏洞武器化
```

如果执行环境、产品策略或工具限制某一测试：

```text
不要绕过
不要换成第三方目标
不要扩大范围
```

而是：

```text
标记 BLOCKED / NOT RUN
记录原因
继续其余允许执行的测试
```

---

# 1. 前置条件

优先在：

```text
Phase 7A = PASS / READY_FOR_7B
```

后执行。

如果 7A 尚未完成，但用户明确要求单独验证 7B：

```text
可以执行
```

但最终不得宣布 Phase 7 READY，除非 7A + 7B 均满足原始门槛。

---

# 2. 本阶段只负责

```text
ZIP malformed/security regression
Malformed GBA corpus
Native ROM bounded fuzz
ZIP bounded mutation fuzz
UBSan
ASan / HWASan
JNI ownership / memory safety
Release ELF hardening
Integer / size safety
Path safety
Parser fail-closed behavior
security-specific regression evidence
```

不重复做：

```text
UI
音频体验
常规渲染体验
普通生命周期压力
普通升级体验
常规 Library QA
普通 CI
```

这些属于 Phase 7A。

---

# 3. Defensive Testing Interpretation

本文件中：

```text
fuzz
malformed
ZIP traversal
ZIP bomb
UAF
double-free
ASan
UBSan
fail-closed
hardening
```

全部仅表示：

> **针对项目自有解析器和 native 代码的 defensive QA。**

不得扩展到第三方软件或外部网络目标。

---

# 4. JNI Ownership / Native Lifetime Audit

审计：

```text
native core
callbacks
registry ownership
VFile
state buffers
audio buffers
frame buffers
sensor callback objects
```

目标：

```text
no UAF
no double-free
no stale handle
no invalid lifetime crossover
```

如果发现问题：

```text
复现
定位 ownership
修复
增加 targeted regression
保留证据
```

---

# 5. ZIP Robustness Regression

对 GBA Lite 自己的 ZIP 导入路径执行原 Phase 6 fixtures：

```text
traversal
absolute path
drive path
duplicate name
case collision
unicode normalization
zip bomb
huge ratio
too many entries
encrypted
ZIP64
nested zip
corrupt central directory
truncated
CRC mismatch
```

目标：

```text
输入不能逃逸 app-private 路径
异常 archive 被拒绝或安全失败
资源使用有边界
无 crash
无 ANR
无 OOM
不污染数据库
不覆盖已有合法数据
```

不得对第三方 ZIP 服务进行测试。

---

# 6. Malformed GBA Corpus

建立本地 synthetic fixtures：

```text
0 byte
1 byte
191 byte
random 192 byte
oversized >32 MiB
bad header
truncated
all 0x00
all 0xFF
random content
```

不得出现：

```text
native crash
OOM
ANR
```

要求：

```text
fail closed
bounded allocation
no persistent corrupt metadata
no partial import
```

---

# 7. Native ROM Bounded Fuzz

建立至少一个：

```text
bounded ROM parser fuzz harness
```

目标路径只允许：

```text
ROM open
hardware detection
VFile
与上述路径直接相关的项目自有 glue
```

优先：

```text
libFuzzer / clang fuzz target
```

可执行环境：

```text
Linux CI
WSL
Android sanitizer-supported path
```

若 Windows 本地工具链阻塞：

```text
尝试 Linux CI / WSL
```

仍不可执行：

```text
BLOCKED
```

不得因为 fuzz 受阻而寻找外部目标。

记录：

```text
duration
iterations / executions
seed
corpus
crashes
timeouts
OOM
unique findings
```

只有真正 fuzz 运行后才允许写：

```text
fuzz PASS
```

---

# 8. ZIP Bounded Mutation Fuzz

仅针对项目自己的 ZIP parser/import path。

Mutation corpus 覆盖：

```text
central directory
sizes
flags
names
CRC
descriptor
```

记录：

```text
duration
iterations
corpus
seed
crashes
timeouts
OOM
```

如果只运行 deterministic malformed fixtures：

```text
不得写 fuzz passed
```

应写：

```text
deterministic malformed fixtures PASS
ZIP fuzz NOT RUN / BLOCKED
```

---

# 9. UBSan

必须重跑：

```text
JNI full
Activity native path
ROM corpus subset
save/load
rewind
orientation-related native lifecycle
```

记录：

```text
toolchain
build flags
executed targets
runtime
findings
stack traces
fixes
regression result
```

只有实际运行成功才允许：

```text
UBSan PASS
```

---

# 10. ASan / HWASan

再次解决历史 blocker。

优先路径：

```text
Android ASan / HWASan
Linux host ASan
WSL host ASan
```

至少尝试一种真正可执行路径。

只有实际执行成功才允许：

```text
ASan PASS
```

否则：

```text
ASan BLOCKED
```

或：

```text
ASan NOT RUN
```

禁止：

```text
把普通测试通过写成 ASan PASS
```

---

# 11. Native Release Hardening

重新检查 Release ELF：

```text
RELRO
BIND_NOW
NX GNU_STACK
stack protector
FORTIFY if applicable
PIE
```

该检查只针对：

```text
本项目 Release ELF
```

不是对第三方 binary 做漏洞研究。

记录：

```text
binary
architecture
hardening property
result
tool/output evidence
```

---

# 12. Integer / Size Audit

专项审计：

```text
size_t
uint32_t
int
JNI jsize
file length
state size
ZIP size
frame size
```

防止：

```text
overflow
truncation
negative size
signed/unsigned mismatch
unsafe allocation size
```

对发现的问题：

```text
修复
新增 boundary regression
```

---

# 13. Path Safety

所有 App-private 路径：

```text
ROM
Save
State
Screenshot
Thumbnail
Temp
```

必须：

```text
derived from controlled identifiers
not arbitrary user path
remain inside app-private storage
```

验证重点：

```text
canonicalization
separator handling
absolute path rejection
drive path rejection
unicode/case collision behavior
temp finalization
```

---

# 14. Parser / Persistence Fail-Closed

以下解析遇到不确定或损坏：

```text
ROM
ZIP
State
Battery
Metadata
Settings
```

必须：

```text
reject
or safe fallback
preserve prior valid data
avoid partial commit
```

禁止：

```text
在不可验证状态下继续写入
```

---

# 15. Security-Specific Storage Failure Checks

与 Phase 7A 普通 IO failure 不重复。

这里只确认在异常输入与解析失败场景下：

```text
no unsafe rename
no overwrite of valid generation
no path escape
no unbounded retry
no partial trusted metadata
```

---

# 16. Security Regression Evidence

保存到：

```text
docs/reports/evidence/phase7/security/
```

至少：

```text
zip regression logs
malformed ROM results
fuzz build logs
fuzz run logs
UBSan logs
ASan/HWASan logs
native hardening audit
integer-size audit
path safety audit
targeted regression results
```

---

# 17. 不得伪造

不得声称：

```text
ASan PASS
UBSan PASS
fuzz PASS
ZIP bomb regression PASS
path traversal regression PASS
UAF fixed
double-free fixed
```

除非真正执行并有证据。

如果某项受环境限制：

```text
BLOCKED
NOT RUN
```

并说明：

```text
attempted path
blocking reason
impact on Phase 7 readiness
next safe action
```

---

# 18. No Safety Bypass

如果系统或工具拒绝执行某个测试：

```text
不要绕过平台限制
不要换目标
不要规避分类
不要使用未授权环境
```

直接：

```text
记录 BLOCKED
继续其余本地 defensive QA
```

单项阻塞不等于整个 7B 立即停止。

---

# 19. Security-Specific Hard Blockers

任一出现：

```text
native crash on bounded malformed input
UAF
double free
OOM on bounded malformed input
ZIP traversal escape
ZIP bomb bypass
unsafe arbitrary path construction
integer overflow leading to unsafe allocation/write
state/battery corruption caused by malformed input
parser writes after validation failure
```

必须：

```text
Phase 7B = NOT READY
```

---

# 20. 可接受的非阻断状态

以下本身不一定阻断：

```text
ASan BLOCKED due unsupported environment
one fuzz path BLOCKED
some sanitizer path unavailable
```

但必须结合原 Phase 7 的硬门槛判断。

如果原任务书明确要求：

```text
ASan or explicit BLOCKED
```

则：

```text
明确 BLOCKED + 充分原因 + 其他替代验证完成
```

可继续评估 Phase 7 是否满足进入 Phase 8 的条件。

不得自行降低原始门槛。

---

# 21. Phase 7B 报告

生成：

```text
docs/reports/PHASE_7B_DEFENSIVE_ROBUSTNESS_VALIDATION_REPORT.md
```

必须包含：

```text
A. Authorized Scope
B. Changed Code
C. JNI Ownership
D. ZIP Robustness Regression
E. Malformed GBA Corpus
F. Native ROM Fuzz
G. ZIP Fuzz
H. UBSan
I. ASan / HWASan
J. Native ELF Hardening
K. Integer / Size Audit
L. Path Safety
M. Fail-Closed Behavior
N. Findings
O. Fixed Findings
P. Remaining Risks
Q. Blocked / Not Run
R. Evidence Index
S. Phase 7B Decision
```

---

# 22. 与 Phase 7A 的最终合并

当 7A 与 7B 都结束后，更新总报告：

```text
docs/reports/PHASE_7_COMPATIBILITY_STABILITY_SECURITY_REPORT.md
```

总报告不得重新执行已完成项目，只整合证据与最终结论。

最终逻辑：

```text
if Phase 7A == PASS
and Phase 7B == PASS or meets original explicit BLOCKED allowance
and no original hard blocker remains
then:
    Phase 7 = PASS / READY
else:
    Phase 7 = NOT READY
```

---

# 23. Phase 8 Gate

只有 Phase 7 总状态为：

```text
PASS / READY
```

才允许进入：

```text
Phase 8 — Release Candidate / Signing / V1.0
```

否则：

```text
Phase 8 — NOT STARTED
```

---

# 24. Codex 执行行为

现在开始执行 Phase 7B。

要求：

```text
只针对当前项目自己的代码和本地测试输入
不要访问第三方目标
不要执行网络扫描
不要进行外部漏洞利用
不要只输出计划
不要伪造 sanitizer/fuzz 结果
```

在允许范围内：

```text
读取代码
构建本地 fixture
运行 bounded defensive tests
修复项目自身问题
添加 targeted regression
保存证据
生成报告
```

如果某一步被平台限制：

```text
标记 BLOCKED / NOT RUN
继续其余项目
```

---

# 25. Final Reminder

Phase 7B 的核心是：

> **验证 GBA Lite 自己的解析器、native glue 和本地数据路径在异常输入与内存边界下能够安全失败，而不是对任何第三方系统进行安全测试。**

不要扩大范围，不要绕过系统限制，不要降低原 Phase 7 的验收要求。
