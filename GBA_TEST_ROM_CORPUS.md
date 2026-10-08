# GBA Lite — GBA Test ROM Corpus

**文档版本：** v0.1  
**用途：** GBA Lite 合法测试 ROM / Homebrew 测试语料规范  
**适用阶段：** Phase 3 起长期使用  
**项目原则：** 不分发商业 ROM，不分发 Nintendo BIOS，不默认把“源码公开”等同于“ROM 可自由再分发”

---

## 1. 目标

本测试语料库用于覆盖 GBA Lite 的：

```text
Core bring-up
CPU / ARM / Thumb
Memory / DMA / Timer / IRQ
PPU / Sprite / Window / Blend
Audio
Input
Fast Forward
Rewind
Save RAM
Save State
Lifecycle
Performance
Link（未来）
Renderer（未来）
```

目标不是建立“GBA ROM 下载库”，而是：

> 建立一套来源可追踪、许可证可验证、SHA-256 固定、适合回归测试的合法 GBA 测试 Corpus。

---

## 2. 法律与分发原则

### 2.1 永久禁止进入仓库

不得提交：

```text
商业 GBA ROM
商业游戏修改版 ROM
Nintendo 官方 BIOS
从商业 ROM 提取的数据
商业游戏截图基线
来源不明 ROM
无法确认分发授权的预编译 ROM
```

即使用户合法拥有商业游戏：

> ROM 也只能留在用户本地，不得提交 Git、上传 CI artifact 或加入测试资源包。

---

### 2.2 “开源源码”不等于“可自由分发 ROM”

必须分别确认：

```text
代码许可证
资源许可证
音乐许可证
字体许可证
预编译 ROM 分发条款
```

例如：

- GitHub 仓库公开；
- 源码是 MIT；
- 但音乐或图片可能是其他许可证。

因此不能只看仓库首页的 License badge 就决定把 `.gba` 放进项目。

---

### 2.3 Homebrew Hub 只作为索引

推荐索引：

```text
https://github.com/gbadev-org/games
https://hh.gbdev.io/games/gba
```

Homebrew Hub 的数据库本身使用 GPLv3，但其说明明确指出：

> 每个游戏、Homebrew、Demo、截图、资源和源码都有各自许可证，需要逐条确认。

因此：

```text
Homebrew Hub entry
        ↓
找到 upstream
        ↓
审核代码 + assets + ROM 分发条款
        ↓
才能决定是否进入 Corpus
```

---

## 3. Corpus 分级

采用四级策略。

### Tier A — 可直接进入仓库 / CI

必须满足：

```text
许可证明确
+
允许复制/分发
+
来源固定
+
无商业资产
+
可记录 SHA-256
```

优先使用：

- 本项目原创 ROM；
- MIT / BSD / CC0 等明确许可的测试 ROM；
- 可从源码确定性构建的 permissive 测试项目。

---

### Tier B — CI 可自动获取/构建，但不直接 Vendor ROM

适用于：

```text
许可证明确
但资源/许可证义务较复杂
或更适合从 upstream build
```

做法：

```text
manifest
↓
固定 upstream commit/tag
↓
fetch/build
↓
验证 SHA-256
↓
仅 CI workspace 使用
```

除非确认允许，否则生成 ROM 不提交仓库。

---

### Tier C — 人工本地测试

适用于：

```text
Homebrew 可免费获得
但我们没有确认再分发权
或 upstream 没有明确 License
```

只记录：

```text
名称
来源
测试用途
用户本地 SHA-256（可选）
```

不得：

```text
自动下载
提交 ROM
上传 CI
```

---

### Tier D — 用户自有商业 ROM

只用于个人设备兼容性测试。

规则：

```text
Never upload
Never commit
Never hash into public manifest if it leaks identifiable copyrighted corpus data unnecessarily
Never bundle
```

测试报告只写：

```text
“用户自有 RPG / 平台游戏 / 商业 title 本地验证”
```

必要时内部本地记录 hash，但不进入公共仓库。

---

# 4. 推荐目录

```text
test-rom/
├── internal/
│   ├── bringup/
│   ├── persistence/
│   └── input/
│
├── manifests/
│   ├── core-tests.json
│   ├── video-tests.json
│   ├── audio-tests.json
│   ├── gameplay-tests.json
│   └── future-link-tests.json
│
├── fetch/
│   ├── fetch_test_roms.py
│   └── README.md
│
├── licenses/
│   └── THIRD_PARTY_ROM_LICENSES.md
│
└── local/
    └── .gitignore
```

`local/`：

```text
*
!.gitignore
```

所有 Tier C / D ROM 都只放这里。

---

# 5. Manifest Schema

建议：

```json
{
  "id": "mgba-suite",
  "name": "mGBA Test Suite",
  "category": [
    "cpu",
    "timing",
    "ppu"
  ],
  "tier": "A",
  "source": "https://github.com/mgba-emu/suite",
  "sourceType": "git",
  "revision": "<pinned commit>",
  "license": "MIT",
  "redistribution": "allowed-with-license",
  "build": {
    "required": true,
    "command": "<documented reproducible build command>"
  },
  "expectedSha256": {
    "rom-name.gba": "<sha256>"
  },
  "ci": true,
  "notes": "Pin source and generated ROM hashes."
}
```

---

# 6. SHA-256 原则

所有进入自动测试的 ROM：

```text
必须固定 SHA-256
```

升级 ROM 时必须：

```text
explicit commit
+
manifest change
+
new SHA-256
+
review
```

禁止：

```text
download latest
```

或：

```text
GitHub master → 自动执行
```

而不固定 commit。

---

# 7. Tier A — 首选硬件 / Core Test ROM

## 7.1 GBA Lite 自制 ROM

**状态：Tier A / 必须保留**

当前已有：

```text
bringup ROM
persistence ROM
```

许可证：

```text
Apache-2.0
```

用途：

```text
Core boot
Frame output
Audio
Input
Save RAM
Save State
Quick Resume
Persistence regression
```

优势：

> 完全由项目控制，是最可靠的基础 smoke test。

建议继续新增：

```text
input-multitouch-test.gba
frame-timing-test.gba
audio-pattern-test.gba
save-pattern-test.gba
```

---

## 7.2 mGBA Test Suite

Upstream：

```text
https://github.com/mgba-emu/suite
```

许可证：

```text
MIT
```

推荐级别：

```text
Tier A
CI = YES
Priority = P0
```

主要用途：

```text
CPU
Memory
Timing
PPU
Hardware edge cases
```

项目策略：

```text
pin commit
→ source build
→ record generated ROM SHA-256
```

不要依赖不固定版本的预构建 ROM。

---

## 7.3 NanoBoyAdvance Hardware Tests

Upstream：

```text
https://codeberg.org/nba-emu/hw-test
```

镜像/项目信息可从 NBA 项目确认。

许可证：

```text
BSD-3-Clause
```

推荐：

```text
Tier A
CI = YES
Priority = P1
```

用途：

```text
DMA
Timer
PPU
Timing
Hardware edge cases
```

注意：

NBA 组织的 GitHub 仓库已归档，canonical upstream 可能位于 Codeberg。

Manifest 必须记录实际使用来源与 commit。

---

# 8. Tier B — 强烈推荐测试项目

## 8.1 FuzzARM

Upstream：

```text
https://github.com/DenSinH/FuzzARM
```

许可证：

```text
GPL-3.0
```

用途：

```text
ARM
THUMB
ALU
load/store
CPU instruction behavior
```

推荐：

```text
Tier B
CI = YES
Priority = P1
```

策略：

- 不把它的代码混入 GBA Lite 自有代码；
- 作为独立测试程序执行；
- 如果分发其 ROM/源码，遵守 GPL-3.0；
- 建议 fetch 固定 release/commit；
- 保留 License。

---

## 8.2 160p / 240p Test Suite

Upstream：

```text
https://github.com/pinobatch/240p-test-mini
```

GBA 部分称：

```text
160p Test Suite
```

许可证：

```text
GPL-2.0-or-later
```

用途：

```text
display
scroll
color
audio sync
lag
visual timing
```

推荐：

```text
Tier B
CI = selected tests
Manual visual = YES
Priority = P1
```

它尤其适合 Phase 4 Renderer。

注意：

> GPL 许可没有问题，但必须保留许可证并满足分发义务，所以默认不建议把 ROM 随 App 发布。

---

# 9. Gameplay / Homebrew Corpus

这类 Corpus 目的不是验证 mGBA CPU 本身，而是覆盖真实游戏 workload：

```text
复杂输入
真实音频
连续场景
性能
State/Rewind
存档
UI 体验
```

---

## 9.1 BeatBeast

Upstream：

```text
https://github.com/afska/beat-beast
```

仓库许可证：

```text
MIT
```

但项目明确包含：

```text
#licenses/
third-party assets / audio
```

因此：

```text
Tier B
Priority = P0 for audio/gameplay
```

用途：

```text
Audio
PCM
Audio/video sync
Input latency
Frame pacing
Fast Forward recovery
Rewind
```

推荐做法：

```text
审核 #licenses
→ pin commit
→ source build
→ CI/local ROM
```

在未完成 assets license audit 前：

> 不把编译后的 ROM 直接 Vendor 到 GBA Lite 仓库。

---

## 9.2 Varooom 3D

Upstream：

```text
https://github.com/GValiente/butano
games/varooom-3d
```

Butano engine：

```text
zlib
```

Varooom 3D：

- 完整源码随 Butano 提供；
- 项目说明其游戏 assets 有 Creative Commons 授权；
- third-party 内容需要查看 `credits/`。

推荐：

```text
Tier B
Priority = P0 for performance
```

用途：

```text
高负载 GBA 场景
60 FPS 3D
Frame pacing
Fast Forward
Renderer
CPU/GPU stress
Long-run stability
```

策略：

```text
pin Butano commit
→ audit game credits
→ build locally / CI
```

---

## 9.3 GBA Microjam '23

Upstream：

```text
https://github.com/gbadev-org/microjam23
```

仓库：

```text
MIT
```

用途：

```text
多个小型场景
不同图形模式
不同输入
快速兼容性 breadth test
```

推荐：

```text
Tier A/B（需确认生成 ROM 内各 assets 是否全部由仓库 MIT 覆盖）
Priority = P1
```

在完成资产审计前按 Tier B 管理。

---

# 10. Tier C — 很值得人工测试，但不要自动 Vendor

## 10.1 Celeste Classic GBA

Upstream：

```text
https://github.com/JeffRuLz/Celeste-Classic-GBA
```

用途非常好：

```text
平台跳跃
D-pad
A/B
Input latency
Multi-touch
Rewind
Fast Forward
```

但当前 upstream 仓库页面没有清晰显示一个覆盖整个项目的许可证。

同时该作品源于 Celeste Classic 的移植，涉及原作：

```text
code
art
audio
characters
```

因此 GBA Lite 策略：

```text
Tier C
CI = NO
Vendor = NO
```

可以：

- 用户自己获取/构建；
- 本地体验验证。

不要因为 `gba-eval` 可以分发它，就假设该授权自动转授给 GBA Lite。

---

## 10.2 BlindJump

awesome-gbadev 将其作为代表性 Homebrew，并特别说明：

```text
Link Cable multiplayer
fully digital audio
```

因此未来测试价值很高：

```text
Digital audio
Adventure gameplay
Link Cable
```

但在没有单独完成 upstream license / ROM redistribution audit 前：

```text
Tier C
```

未来 Phase Link 时优先重新审核。

---

## 10.3 Goodboy Advance

用途：

```text
传统 Homebrew compatibility
真实游戏流程
不同图形/音频路径
```

在许可证与 ROM 再分发条款未单独确认前：

```text
Tier C
```

---

## 10.4 Anguna / Waimanu / Spout 等

这些项目被公开 Homebrew corpus 使用，也可以作为人工 breadth test。

但每个 ROM 必须单独核验 upstream 权利。

默认：

```text
Tier C
```

不要批量复制其他 Corpus 的 ROM 文件。

---

# 11. `gba-eval` 的使用方式

参考项目：

```text
https://github.com/mechanize-work/gba-eval
```

它是非常有价值的“测试 Corpus 设计参考”。

其 Legal 文档明确：

```text
不分发商业 ROM
使用 SHA-256 标识本地用户 ROM
包含硬件测试 ROM
包含 Homebrew ROM
每个 ROM 使用各自许可证或明确授权
```

其中 hardware corpus 包括：

```text
armwrestler
fuzzarm
jsmolka
destoer
tonc
nba-hw
mgba-suite
240p-test-suite
sound demos
```

Homebrew corpus 包括：

```text
celeste-classic
heartwrench-advance
anguna
another-world
goodboy-advance
blindjump
chip-advance
spout
waimanu
piugba
meteorain
trogdor
xniq
bulletgba
varooom-3d
collie-defense
```

### 重要规则

不要：

```text
直接复制 gba-eval/corpus/roms/
```

正确方式：

```text
参考它的 corpus 设计
↓
逐个回到 upstream
↓
确认授权是否适用于我们
↓
独立 pin / build / fetch
```

因为：

> 对 `gba-eval` 的“explicit written permission”不一定自动适用于 GBA Lite。

---

# 12. awesome-gbadev 的用途

索引：

```text
https://github.com/gbadev-org/awesome-gbadev
```

它维护 GBA 开发社区推荐项目。

其 Emulator Development / Testing 包含：

```text
mGBA test suite
GBA Suite
240p-test-mini
NBA hardware tests
```

Homebrew 部分包含：

```text
Goodboy Advance
Celeste Classic
GBADoom
BlindJump
Tigermoth
Duster
OpenLara
```

用途：

> 发现候选项目，而不是作为 ROM 法律授权来源。

---

# 13. 第一批建议 Corpus

## P0 — 每次核心回归

```text
1. GBA Lite bringup ROM
2. GBA Lite persistence ROM
3. mGBA Test Suite
```

目标：

```text
fast
deterministic
CI mandatory
```

---

## P1 — 常规完整 CI / Nightly

```text
4. NBA Hardware Tests
5. FuzzARM
6. selected 160p Test Suite
```

目标：

```text
CPU
timing
PPU
visual/audio
```

---

## P2 — Gameplay Regression

优先：

```text
7. BeatBeast
8. Varooom 3D
9. Microjam '23
```

前提：

```text
license/assets audit completed
```

测试：

```text
audio
frame pacing
performance
FF
rewind
input
long run
```

---

## P3 — 人工体验

```text
Celeste Classic GBA
BlindJump
Goodboy Advance
Anguna
Waimanu
```

默认：

```text
local only
```

---

# 14. Phase 对应测试矩阵

| Corpus | P2 Persistence | P3 Player | P4 Renderer | P5 Sensors | Link Future |
|---|---:|---:|---:|---:|---:|
| Internal bringup | ✅ | ✅ | ✅ | - | - |
| Internal persistence | ✅ | ✅ | - | - | - |
| mGBA suite | ✅ | ✅ | ✅ | 部分 | 部分 |
| NBA hw-test | - | ✅ | ✅ | - | - |
| FuzzARM | - | ✅ | - | - | - |
| 160p Test Suite | - | ✅ | ✅✅ | - | - |
| BeatBeast | - | ✅✅ | ✅ | - | - |
| Varooom 3D | - | ✅ | ✅✅ | - | - |
| Microjam '23 | - | ✅ | ✅ | - | - |
| Celeste Classic | - | ✅✅ | ✅ | - | - |
| BlindJump | - | ✅ | ✅ | - | ✅✅ |

---

# 15. Phase 3 当前推荐重点

当前项目正在 Phase 3。

建议立即使用：

### 自动

```text
Internal bringup
Internal persistence
mGBA suite
```

### 如授权审计完成后自动

```text
BeatBeast
Varooom 3D
```

### 人工

```text
Celeste Classic GBA
```

主要测试：

```text
Fast Forward
Rewind
Multi-touch
D-pad slide
Frame pacing
Audio recovery
Orientation
Long play
```

---

# 16. 自动测试不要依赖视觉人工判断

自动 Corpus 应优先输出：

```text
PASS/FAIL register state
memory result
known test result
frame hash
audio sample hash
save hash
```

不要让 CI 通过：

```text
“看截图感觉差不多”
```

来判定核心正确性。

---

# 17. Frame Hash

对确定性 Test ROM：

建议保存：

```text
frame N
→ RGBA bytes
→ SHA-256
```

Manifest：

```json
{
  "frame": 300,
  "sha256": "..."
}
```

用于检查：

```text
Renderer/Core regression
```

但注意：

- 不把商业游戏 frame hash 作为公共资产；
- 只用于合法 Test/Homebrew ROM。

---

# 18. Audio Hash

对于确定性测试 ROM：

```text
capture PCM window
→ normalize format
→ SHA-256
```

用于：

```text
Audio regression
```

Phase 3 的 Oboe/FF/Rewind 测试可以额外验证：

```text
no stale queued audio
no unexpected silence
```

---

# 19. Gameplay 自动化

不要试图自动“通关游戏”。

Gameplay Corpus 自动化只需：

```text
boot
wait N frames
inject known input script
capture frame/audio/state
check no crash
```

例如：

```json
{
  "atFrame": 120,
  "press": ["START"]
}
```

---

# 20. 输入脚本

建议格式：

```json
[
  {"frame": 60, "down": ["START"]},
  {"frame": 62, "up": ["START"]},
  {"frame": 180, "down": ["RIGHT"]},
  {"frame": 240, "down": ["A"]},
  {"frame": 250, "up": ["A"]},
  {"frame": 300, "up": ["RIGHT"]}
]
```

这样 Corpus 可以重放。

---

# 21. CI 分层

### PR CI

只跑：

```text
Internal
mGBA selected smoke
```

目标：

```text
< 几分钟
```

---

### Main Branch

跑：

```text
完整 Tier A
selected Tier B
```

---

### Nightly / Manual

跑：

```text
全部合法 Corpus
长时间 gameplay
performance
renderer
audio
```

---

# 22. 网络原则

GBA Lite V1 App 本身仍然：

```text
No INTERNET
```

测试 Fetch Script 属于：

```text
developer tooling
```

而不是 Android App。

允许开发机/CI 获取 upstream 测试源码，但：

> 绝不能因此给 App 增加 INTERNET permission。

---

# 23. Fetch Script 安全

`fetch_test_roms.py` 必须：

- 固定 HTTPS upstream；
- 固定 tag/commit；
- 验证 archive/source SHA-256；
- 验证最终 ROM SHA-256；
- 禁止执行下载包中的任意脚本，除非属于经过审核的 build 流程；
- 不从随机 ROM 网站下载；
- 不 fallback 到镜像站；
- hash 不匹配立即失败。

---

# 24. License Manifest

建议维护：

```text
test-rom/licenses/THIRD_PARTY_ROM_LICENSES.md
```

每个自动 Corpus 记录：

```text
Name
Author
Upstream
Revision
License
Asset license
ROM redistribution
Attribution
Source offer requirement
```

---

# 25. ROM 更新规则

任何 Corpus ROM 升级：

必须 PR 显示：

```text
old revision
new revision
old SHA
new SHA
license changes
test baseline changes
```

禁止 bot 静默升级。

---

# 26. 商业游戏兼容测试

可以由开发者自己进行，但：

```text
不进入 CI
不进入 Repo
不进入 Public Report 附件
```

公开报告可写：

```text
用户本地合法商业 ROM：
- RPG 类：启动/存档/FF 通过
- Platformer 类：输入/Rewind 通过
```

无需附 ROM。

---

# 27. BIOS 原则

测试默认继续：

```text
mGBA HLE / skip BIOS
```

Nintendo GBA BIOS：

```text
不得进仓库
不得进 CI
不得上传
```

如果某个 accuracy test 必须官方 BIOS：

测试应标记：

```text
LOCAL_ONLY_REQUIRES_USER_BIOS
```

并在缺少 BIOS 时跳过。

---

# 28. 初始实施任务

Codex 后续可执行：

```text
1. 创建 test-rom/manifests/
2. 创建 Corpus manifest schema
3. 将现有原创 ROM 纳入 Tier A
4. 集成 mGBA suite（固定 commit）
5. 加入 license inventory
6. 添加 SHA-256 verification
7. 建立 selected smoke runner
8. 输出 CORPUS_REPORT.md
```

暂时不要：

```text
批量下载 Homebrew Hub
批量抓 itch.io
批量复制 gba-eval ROM
下载商业 ROM
```

---

# 29. 推荐候选清单

| ROM / Suite | 类型 | Tier | License 状态 | CI | 主要用途 |
|---|---|---:|---|---:|---|
| GBA Lite bringup | 自研 | A | Apache-2.0 | ✅ | boot/frame/audio/input |
| GBA Lite persistence | 自研 | A | Apache-2.0 | ✅ | Save RAM/State |
| mGBA Test Suite | Hardware test | A | MIT | ✅ | Core/timing/PPU |
| NBA hw-test | Hardware test | A | BSD-3-Clause | ✅ | accuracy/timing |
| FuzzARM | CPU test | B | GPL-3.0 | ✅ | ARM/Thumb |
| 160p Test Suite | AV test | B | GPL-2.0-or-later | ✅/manual | video/audio |
| BeatBeast | Game | B | MIT repo + asset audit | ✅ after audit | audio/input/FF |
| Varooom 3D | Game | B | zlib code + CC/third-party audit | ✅ after audit | performance |
| Microjam '23 | Games | B | MIT repo; verify assets | ✅ after audit | breadth |
| Celeste Classic GBA | Game | C | redistribution unclear | ❌ | input/Rewind |
| BlindJump | Game | C | verify upstream | ❌ initially | audio/link |
| Goodboy Advance | Game | C | verify upstream | ❌ initially | compatibility |
| Anguna | Game | C | verify upstream | ❌ initially | gameplay |
| Waimanu | Game | C | verify upstream | ❌ initially | gameplay |

---

# 30. 当前结论

GBA Lite 的测试策略不应是：

> “找一堆 ROM 看能不能玩。”

而应是：

```text
自研 deterministic tests
        +
硬件 accuracy suites
        +
合法 Homebrew workloads
        +
用户本地商业 ROM 人工兼容验证
```

四层结合。

优先级：

```text
P0  数据安全 / Smoke
P1  Core regression
P2  Player / Performance
P3  Broad compatibility
```

最终目标：

> 每次改 mGBA adapter、Renderer、Audio、Input、Persistence 或 Core version 时，都能知道“具体哪一种 GBA 行为发生了回归”，而不是直到用户玩游戏时才发现问题。

---

# 31. 参考来源

以下为建立本 Corpus 的主要公开来源：

```text
Homebrew Hub GBA database
https://github.com/gbadev-org/games

awesome-gbadev
https://github.com/gbadev-org/awesome-gbadev

mGBA Test Suite
https://github.com/mgba-emu/suite

NanoBoyAdvance hw-test
https://codeberg.org/nba-emu/hw-test

FuzzARM
https://github.com/DenSinH/FuzzARM

240p / 160p Test Suite
https://github.com/pinobatch/240p-test-mini

BeatBeast
https://github.com/afska/beat-beast

Butano / Varooom 3D
https://github.com/GValiente/butano

GBA Microjam '23
https://github.com/gbadev-org/microjam23

Celeste Classic GBA
https://github.com/JeffRuLz/Celeste-Classic-GBA

gba-eval Corpus / Legal reference
https://github.com/mechanize-work/gba-eval
```

许可证与再分发条件在真正 Vendor / Fetch 前必须再次以固定 revision 的 upstream 文件为准。
