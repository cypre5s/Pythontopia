# R-11 ~ R-100 持续小步成熟度迭代日志

> 接续 `r10_iterations.md`。每轮聚焦 1 ~ 2 项可验证、可独立 commit 的小改动，
> 严格遵守 `AGENTS.md`「外科手术式修改」与「不擅自加大功能模块」两条硬约束。
>
> 主题方向：让 Pythontopia 更像一个**成熟的教学游戏作品**——门面、引导、视觉一致性、可访问性、文档质感、错误处理、性能微调。

## 编号约定

- **本文件**：记录代码 / 文档 / 前端体验 / 测试覆盖等**文档与代码轮**，编号 `R-11 / R-12 / R-13 ...`
- **`assets/inbox/buildings/MANIFEST.md` 等素材清单**：记录像素素材下载、license 复核、风格统一等**素材轮**，编号 `R-100 #1 / #2 / ...`（独立命名空间）。
- 两套编号互不依赖，可在不同 working session 并行推进；commit message 用 `chore(rNN)` vs `chore(assets-rNN)` 区分。
- 若未来出现交叉（如素材轮需要改 frontend 代码），统一遵守"外科手术式修改"原则，只动必须触达文件。

## 全局状态

- 基线 commit：`59761c5` (R-0)，冻结 R-10 完成态（234 / 234 测试通过）。
- 每轮独立 commit，commit message 以 `chore(rNN): ...` 起头，便于回滚定位。
- 不修改 `git config`、不 push 远端、不 force push。
- 每轮结束后追加本文件 + 同步 `docs/operations/execution_status.md` + `docs/project/CHANGELOG.md`。

## R-11 — 根 README 重写为成熟教学游戏作品门面（已完成）

### 动机

R-10 收尾后根 `README.md` 仅 30 行，仅含目录树 + 开发命令骨架，对外门面与
项目的"教学游戏作品"定位严重不匹配。新读者无法在 README 内一眼看出：

- 教育闭环 7 步是什么。
- 游戏元素 ↔ 教育语义如何映射（KC = 种子等）。
- 234 / 234 测试基线在哪里。
- 11 个 Agent / 双轨判题 / 防幻觉硬约束。
- 一键演示路径与本地开发路径分别怎么跑。

### 改动

- 文件：`README.md`（30 行 → 147 行）。
- 11 节结构：项目定位 → 游戏 ↔ 教学映射 → 技术栈 → 项目结构 → 快速开始 →
  测试基线 → 关键文档 → 防幻觉与合规底线 → 迭代历史（含 R-11 ~ R-100 位置）→
  许可 → 团队。
- 所有外链均指向仓库内真实文件，无虚构引用：`AGENTS.md` / `docs/architecture/system_design.md`
  / `docs/presentations/demo_script.md` / `docs/testing/test_report.md` / `docs/beta_acceptance_checklist.md`
  / `docs/project/ai_coding_disclosure.md` / `docs/legal/open_source.md` / `docs/release/README.md`
  / `plan/阶段总览.md` / `docs/project/CHANGELOG.md` / `LICENSE`。
- 引用 234 / 234 测试基线、`packId=43` 课程定量数字、6 服务 Docker Compose、
  11 个 Agent 列表，全部与 `docs/release/r10_summary.md` 与 `docs/project/CHANGELOG.md` 对齐。

### 不做（边界）

- 不改任何代码、不改测试、不改 Vue / Java / Python 业务逻辑。
- 不新增功能模块，不引入新依赖。
- 不动既有文档内容，仅在 R-11 内**新增** `docs/release/r100_iterations.md`
  并**追加**修改 `execution_status.md` / `docs/project/CHANGELOG.md`。

### 验证

- 单一文件 markdown 文档变更，无构建/测试相关代码改动，跳过 typecheck / lint / test。
- 文档链接均为仓库内文件相对路径，已逐一核对存在性。
- 后续轮次若触及代码，将按相应套件跑测试（mvn test / vitest / pytest）。

### R-12 ~ R-100 候选方向（未来轮次草稿，按需排期）

| 轮次 | 方向 | 单文件 / 多文件 | 风险 |
| --- | --- | --- | --- |
| R-12 | `frontend/src/styles.css` 颜色 / 边框迁移到 `tokens.css` 的 `--pyt-color-*` token | 多文件 | 低 |
| R-13 | 全局 `image-rendering: pixelated` 规则覆盖到所有 `<img>` 像素素材 | 多文件 | 低 |
| R-14 | 主面板（Workshop / Board / ReviewHall / BugMine / Training）键盘可达性与 aria-label 巡检 | 多文件 | 中 |
| R-15 | `<= 768px` 小屏适配巡检（演示笔记本通常是 1280×800，但备用机可能更窄） | 多文件 | 低 |
| R-16 | 所有 Pinia store action 的 loading / error 子状态命名与文案一致性 | 多文件 | 低 |
| R-17 | 关键面板 empty state 引导文案补强（与 R-3 的 hint 文案分层呼应） | 多文件 | 低 |
| R-18 | backend `ApiError` 错误码文档化（生成 `docs/error_codes.md`） | 单文件 | 极低 |
| R-19 | `docs/architecture/system_design.md` 时序图重审与口径统一 | 单文件 | 低 |
| R-20 | `docs/presentations/demo_script.md` 7 分钟演示节奏微调 + 视频字幕清单交叉对齐 | 单文件 | 低 |
| R-21 ~ R-100 | 视情况排期：测试覆盖补强 / 文案润色 / 文档导航 / 部署文档 / 演示数据完善等 | - | - |

> 每轮真正开始前先确认「是否仍符合外科手术式修改 + 不扩功能」原则，
> 必要时调整方向，不为凑数硬改。
