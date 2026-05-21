# Pythontopia 执行状态实时记录

> 该文档与 `CHANGELOG.md` 配合使用：CHANGELOG 记录“已完成”，本文档记录“正在做+下一步+卡点+最近 2 分钟动作”。
> 每完成一个关键节点都要追加；每 2 分钟最低汇报一次。

## 全局阶段进度

| 阶段 | 名称 | 状态 | 说明 |
| ---- | ---- | ---- | ---- |
| 00 | 仓库骨架 | 已完成 | 见 CHANGELOG |
| 01 | 课程包导出与种子数据 | 已完成 | 见 CHANGELOG |
| 02 | 账号、班级与小镇基础后端 | 已完成 | 见 CHANGELOG |
| 03 | 前端骨架与 Phaser 小镇 MVP | 已完成 | 见 CHANGELOG |
| 04 | 画像屋与 ProfileAgent | 已完成 | 见 CHANGELOG |
| 05 | 知识田与 KC 作物系统 | 已完成 | backend 26 / graph 11 / frontend 21 测试通过 |
| 06 | RAG 服务课件证据检索 | 已完成 | backend 29 / rag 18 测试通过 |
| 07 | 资源工坊与六类资源生成 | 已完成 | backend 41 / graph 31 / rag 18 / frontend 30 测试通过 |
| 08 | 学习路径、每日任务、公告板 | 已完成 | backend 52 / graph 40 / rag 18 / frontend 37 测试通过 |
| 09 | 训练场、Pyodide、服务端判题 | 已完成 | backend 72 / frontend 41 / graph 40 / rag 18 测试通过 |
| 10 | Bug 矿洞与 DebugAgent | 已完成 | backend 76 / frontend 44 / graph 45 / rag 18 测试通过 |
| 11 | 讯飞 Provider 接入 | 已完成 | graph 53 测试通过（含 8 Spark provider） |
| 12 | 班级架构与教师看板 | 已完成 | backend 87 / frontend 48 测试通过（见 CHANGELOG） |
| 13 | 学习效果评估与复盘大厅 | 已完成 | backend 100 / graph 59 / rag 18 / frontend 52 测试通过 |
| 14 | Docker Compose、视觉打磨、文档 | 已完成 | 6 service 编排 / 5 个脚本 / 6 类文档 / tokens.css / UX checklist |
| 15 | Beta 验收、视频、PPT | 已完成（文档/脚本/checklist） | 视频/PPT 本体需真人产出 |
| R-10 | 10 轮代码质量审查与性能优化 | 已完成 | 最终 234 测试通过 |
| R-11 | 根 README 重写为成熟教学游戏作品门面 | 已完成 | 单文件 30 → 147 行；R-0 baseline commit `59761c5` 建立 |
| R-12 ~ R-100 | Web 端持续小步成熟度迭代 | **冻结** | Web 端代码冻结在 R-11；用户决策转入 Android 化路线 |
| Android-R0 | Android 客户端启动：10 subagent 调研 + ADR-001 + roadmap | 已完成 | 详见 `android/docs/ADR-001-tech-selection.md` 与 `android/docs/roadmap.md` |
| Android-R1 ~ R17 | Android 客户端从 Gradle 骨架到 Beta | 进行中 | 详见 `android/docs/roadmap.md` |

## 当前状态：方向变更 — Web 端冻结于 R-11，Android-R0 已启动，等待 Android-R1 Gradle 骨架

## 阶段 15 — Beta 验收、演示视频与 PPT 收尾（文档与脚本部分已完成）

### 计划任务清单（详见 `plan/阶段15-Beta验收视频与PPT收尾.md`）
1. Beta 验收 checklist：`docs/beta_acceptance_checklist.md` 按 `plan.md` 第 9 节 + AGENTS.md 硬要求逐项打勾。
2. 演示视频脚本扩展为 `docs/release/demo_v1.md`（剪辑时间轴 + 字幕清单）；视频本体不入 git。
3. PPT 大纲 → 终稿（pptx + pdf 仅放 `docs/release/`，本仓库只留 outline.md 与脚本）。
4. 提交包：`docs/release/README.md` 列源码 tarball / docs / images / images_tar / checksum / 演示清单。
5. 全量回归报告 `docs/release/regression_report.md`：4 套件 + demo_smoke 退出码 + 耗时。
6. 已知缺陷 `docs/release/known_issues.md`：分演示阻断 / 非阻断两类。

### 还要做
- [ ] `docs/beta_acceptance_checklist.md` 全勾。
- [ ] `docs/release/known_issues.md` 列已知缺陷与未来工作。
- [ ] `docs/release/regression_report.md` 4 套件命令 + 退出码 + 耗时快照。
- [ ] `docs/release/README.md` 提交包导览。
- [ ] R-10 启动 10 轮小步迭代（阶段 15 完成后立即并入）。

### 卡点 / 注意
- 比赛文件中不得包含真实 LLM 密钥；PPT/视频中不得包含真实学生姓名。
- 演示前 24 小时冻结代码，只允许文档与演示数据修改。
- R-10 必须遵守 AGENTS.md 边界，不擅自加大功能模块；每轮迭代要小步、可验证、可回滚。

## 最近进度汇报记录

- 18:46 收到用户重申长期指令；继续阶段 07。修正 execution_status.md（阶段 06 已完成，进入 07）。
- 18:55 阶段 07 graph：V040/V041 + resource schema + 5 节点 + 6 agent + ResourceGraph + `/agents/resource/produce`；graph `pytest -q` -> `31 passed`。
- 19:02 阶段 07 backend：workshop 模块 + Stage07 Testcontainers 全过；backend `mvn test` -> `Tests run: 41`。
- 19:10 阶段 07 前端：`/workshop` + WorkshopPage + 9 个 workshop 组件 + workshop store/api/dto + 4 个 Vitest 全过；frontend `typecheck/lint/test` -> `17 files / 30 tests passed`；阶段 07 验收完成。
- 19:10 进入阶段 08：学习路径、每日任务与公告板。
- 19:20 阶段 08 graph：path schemas + PathPlannerAgent + PathGraph + `/agents/path/plan`；graph `pytest -q` -> `40 passed`（含 9 个 path planner 测试）。
- 19:30 阶段 08 backend：V050/V051/V052 + 三模块（path/quest/classroom-quest）+ 单测 + Stage08 Testcontainers；`mvn test` -> `Tests run: 52`。修复 classroom_members.student_id 列名后整体通过。
- 19:35 阶段 08 前端：`/board` BoardPage + BoardPanel + 4 个 tab + path/quests/classroom DTOs + path/quests API + store 子状态 + Town 入口 + 2 套 Vitest；`typecheck/lint/test` -> `Test Files 19 / Tests 37`；阶段 08 验收完成。
- 19:35 进入阶段 09：训练场、Pyodide、服务端判题。
- 19:42 阶段 09 backend：V060 + submission/judge 模块（SubprocessPythonSandbox + StaticCodeChecker + JudgeService + SubmissionService + Public/SubmissionController）+ 15 个单测全过。
- 19:45 阶段 09 集成测试 Stage09SubmissionAcceptanceIntegrationTest 5/5（含真实 python3 subprocess 跑出 AC/WA/CE/限频 429/DTO 无 hidden cases）；`mvn test` -> `Tests run: 72`。
- 19:50 阶段 09 frontend：`/training` TrainingPage + TrainingPanel + Pyodide adapter (CDN 0.25.0) + submissions/problems API + dto + store.training 子状态 + Vitest 4 个；`typecheck/lint/test` -> `Test Files 21 / Tests 41`；阶段 09 验收完成。
- 19:50 进入阶段 10：Bug 矿洞与 DebugAgent。
- 20:00 阶段 10 graph：debug schemas + DebugAgent + safety 降级 + `/agents/debug/analyze`；graph 测试 45/45。
- 20:05 阶段 10 backend：V070 + bug_mine 模块（PythonTraceCollector + CounterExampleSelector + DebugAgentClient + BugMineService + BugMineController）+ Stage10 集成测试 4/4；`mvn test` -> Tests run: 76。
- 20:10 阶段 10 frontend：`/bug-mine` + BugMinePage + BugMinePanel + api/bugMine + bugMine store + TrainingPanel 失败入口；frontend 44/44。
- 20:10 进入阶段 11：讯飞 Provider 接入。
- 20:15 阶段 11 完成：SparkProvider（OpenAI 兼容接口）+ LlmProviderError 分级 + provider_from_env/provider_label + 8 个 spark 测试 + docs/llm-provider-env.md 更新；graph 53/53。
- 20:15 进入阶段 12：班级架构与教师看板。
- 21:00 阶段 12 完成（见 CHANGELOG 2026-05-21）：V080/V081 + classroom-quest 写路径 + dashboard 5 聚合 + 教师 6 tabs；backend 87 / frontend 48 全过。
- 21:05 进入阶段 13：学习效果评估、路径重排与复盘大厅。计划并行实施：graph EvaluationNarrativeAgent + backend evaluation 服务 + frontend ReviewHallPanel。
- 22:10 阶段 13 完成：graph 修复 NUMBER/KC pattern 双计 bug + 6 单测；backend 新增 evaluation service/controller/listener + 13 单测/集成；frontend 复盘大厅 + 3 子组件 + 4 spec；全量 backend 100 / graph 59 / rag 18 / frontend 52 通过。
- 22:10 进入阶段 14：Docker Compose 部署、视觉打磨与文档。
- 22:25 阶段 14 完成：4 套 Dockerfile + nginx 反代 + 6 服务 compose；5 个 deploy 脚本 + demo seed.py；tokens.css；6 类核心文档 + UX checklist；前端 typecheck/lint/test 52/52 通过。
- 22:25 进入阶段 15：Beta 验收 + 演示视频脚本 + PPT 大纲 + 提交包目录。
- 22:35 阶段 15 文档完成：beta_acceptance_checklist / release/README / regression_report / known_issues / demo_v1；视频与 pptx 本体需真人录制制作（已写脚本与剪辑指南）。
- 22:35 启动 R-10：10 轮小步代码质量审查与性能优化，聚焦"让项目更像成熟教学游戏作品"。
- 22:50 R-10 完成：router 全量懒加载、教学闭环引导文案、复盘 store + planDraft 单测补强、文档闭环；最终 backend 101 / graph 59 / rag 18 / frontend 56 = 234 测试通过。
- 22:50 全部阶段交付完毕；视频与 PPT 物件需真人按 `docs/demo_script.md` 与 `docs/release/demo_v1.md` 录制制作。
- 23:50 R-0 baseline commit `59761c5`：把 R-10 完成态固化为首个 git commit（13032 文件 / 222589 行），建立 R-11 ~ R-100 可回滚锚点。
- 23:55 R-11 完成：根 `README.md` 重写为成熟教学游戏作品门面（30 → 147 行，11 节）；新建 `docs/release/r100_iterations.md`；同步本文件与 `CHANGELOG.md`。本轮无代码改动，无需跑测试套件。
- 00:00 用户决策方向变更：在本项目下新增 `android/` 文件夹做原生 Android 客户端；接受比赛 Beta 日期推迟；放弃 Web 端 R-12 ~ R-100 迭代（保留 R-11 完成态代码）；授权使用 subagent 并发。
- 00:05 Android-R0 启动：新建 `android/` + `android/README.md` + `android/.gitignore` + `android/docs/README.md`；启动 10 个并发 subagent 调研（Gradle / 像素引擎 / Python 沙箱 / 暗色主题 / 网络 / DI 状态 / JWT 安全 / 测试 / DTO 对齐 / CI/CD），输出到 `.work/thinking/android_research_{01..10}.md`。
- 00:08 10 个 subagent 全部完成，3951 行调研报告就位。
- 00:12 Android-R0 ADR-001 + roadmap.md 落地：18 项关键决策锁定（Kotlin 2.2.0 / AGP 8.7.3 / Compose BOM 2025.04.00 / LibGDX 1.13.1 主渲染 / 仅服务端判题 / Hilt + KSP / Retrofit 2.11 + OkHttp 4.12 / EncryptedSharedPreferences + BiometricPrompt / JUnit5 + Robolectric + Compose UI Test / OpenAPI Generator 7.22 / GitHub Actions ubuntu-latest）；roadmap 列 Android-R0 ~ R17 + R100 共 19 个阶段。
