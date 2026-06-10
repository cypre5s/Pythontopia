# Pythontopia

> 面向高校 Python 初学者的网页版像素学习小镇。
> 学生在单一中央小镇 `town_hub` 中通过对话画像、课件阅读、资源工坊、训练场提交、Bug 矿洞调试和复盘大厅，把一门完整的《Python 语言基础》课程走完一遍。教育优先，游戏承载。

当前验收以本仓库测试命令为准：frontend lint / typecheck / test / build，backend compile / test / package，graph/rag pytest，以及真实浏览器走查。

---

## 1. 项目定位

- **课程底座**：`packId=43`《Python 语言基础》，7 文档 / 561 页 / 7 章 / 65 KC / 46 示例 / 39 题，全部内置在 `data/course-pack-43/`。
- **教学闭环**：画像 → 看课件/视频/RAG → 资源工坊 → 训练场 → Bug 矿洞 → 复盘大厅 → 路径重排，全部以真实数据驱动，不做纯前端假数据。
- **多智能体**：11 个 Agent（Profile / CurriculumMapper / ResourceDesigner / MindMap / Practice / CodeLab / Storyboard / PathPlanner / Debug / GroundingCritic / Safety），全部结构化输出 + 课件证据审查。
- **轻量班级**：教师创建班级 / 发布班级任务 / 班级 KC 热力图 / 高频误区 / 资源使用看板。
- **双轨执行**：Pyodide 即时预览 + 服务端正式判题（subprocess 沙箱 + 静态检查 + 限频 + 隐藏用例不下发）。

## 2. 游戏 → 教学映射

| 游戏元素 | 教育语义 |
| --- | --- |
| KC 任务 | Knowledge Component 学习目标 |
| 看 | 课程视频 / PPT / 课件问答（RAG） |
| 学 | 资源工坊生成并消费学习资源 |
| 练 | 训练场提交真实题目判题 |
| 测 | 掌握度达到阈值 |
| 病害 / 矿洞事件 | 提交失败 → 进 Bug 矿洞 |
| 复盘大厅 | 周期评估 → 路径重排 |

每个玩法都对应教育闭环中的一个动作，没有纯装饰系统。详见 `AGENTS.md`「游戏化规则」。

## 3. 技术栈

| 层 | 技术 | 备注 |
| --- | --- | --- |
| 后端 | Java 21 + Spring Boot 3.3 + Maven + Flyway + PostgreSQL 15 + Redis | DTO 严格分离，统一 `ApiError`，Testcontainers 集成测试 |
| 前端 | Vue 3 + Vite + TypeScript strict + Pinia + Vitest | 业务层禁 `any`；Phaser 3 隔离在 adapter 层 |
| 游戏渲染 | Phaser 3 | R-14 单一中央 `town_hub`（50×40）+ `mine_south` Bug 支线 + NPC + 建筑交互 |
| Agent 服务 | Python 3.11 + FastAPI + LangGraph | 全部 pydantic 结构化输出，不启用 ReAct |
| RAG 服务 | Python 3.11 + FastAPI + 向量检索 + pgvector | `X-Internal-Token` 保护重建端点 |
| 部署 | Docker Compose（6 服务） + 5 个一键脚本 | 演示数据自带，幂等可重入 |
| LLM Provider | 讯飞 Spark（演示主链路）/ OpenAI-Compat（开发期 fallback） | 切换由 `PYT_LLM_PROVIDER` 控制 |

## 4. 项目结构

```text
backend/                    Spring Boot 后端（auth / classroom / workshop / submission / bugmine / evaluation 等）
frontend/                   Vue 3 + Phaser 3 网页端（pages/ components/ stores/ api/ dto/ game/ styles/）
services/
  pythontopia-graph/        多智能体工作流（11 Agent + safety + grounding + 7 子图）
  pythontopia-rag/          课件 RAG（chunker + embedder + store + safety）
data/
  course-pack-43/           Python 语言基础完整课程种子数据
  demo/                     演示账号 / 班级 / 任务 + 幂等 seed 脚本
assets/                     像素素材（CC0）+ LICENSE 记录
deploy/                     docker-compose.yml + .env.example + 5 个 deploy 脚本
docs/                       系统设计 / 测试报告 / 演示脚本 / PPT 大纲 / Beta 验收 / AI 协作披露
docs/release/               Beta v1 提交包导览 + 回归报告 + 已知缺陷 + R-10 总结
plan/                       16 个阶段计划（00 ~ 15 + 阶段总览）
.cursor/ .github/           Cursor 规则与 GitHub workflow 占位
```

## 4.1 客户端（4 端共用一套后端）

自 R-14 起，Pythontopia 从「Web + Android 双端」扩展为面向 **4 类客户端**（Web / Android / Windows / 微信）共用 **同一套 Spring Boot 后端**（`:18080`，前缀 `/api`，JWT Bearer）。微信端含 **小程序**（学习陪伴）与 **小游戏**（像素小镇）两个工程，故共 5 个客户端目录。

| 平台 | 目录 | 技术栈 | 像素小镇 | 快速开始 |
| --- | --- | --- | --- | --- |
| Web | `frontend/` | Vue 3 + Phaser 3 + TS | 有 | `cd frontend && npm ci && npm run dev` |
| Android | `android/` | Kotlin + Compose + LibGDX | 有 | `cd android && ./gradlew assembleDebug` |
| Windows | `windows/` | .NET 8 WinUI 3 + MonoGame | 有（建设中） | `cd windows && dotnet build` |
| 微信小程序 | `wechat-miniprogram/` | 原生小程序（TS） | 无（学习陪伴） | 微信开发者工具导入该目录 |
| 微信小游戏 | `wechat-minigame/` | 原生小游戏（TS + canvas2d） | 有 | 微信开发者工具（小游戏）导入该目录 |

各端构建细节以其自身 README 为准；**共享后端**的启动见下方 §5。客户端接入后端的**唯一权威契约**见 [`docs/clients/api-contract.md`](./docs/clients/api-contract.md)，跨端总览见 [`docs/clients/README.md`](./docs/clients/README.md)。

## 5. 快速开始

### 5.1 一键演示（推荐）

```bash
cd deploy
cp .env.example .env                                # 填入 PYT_DB_* / PYT_JWT_SECRET / PYT_TEACHER_INVITE_CODE / PYT_LLM_PROVIDER
docker compose up -d --build                        # 启动 6 个服务（postgres / redis / backend / graph / rag / frontend）
bash scripts/init_rag_index.sh                      # 课件向量索引一次性构建
bash scripts/seed_demo_classroom.sh                 # 创建 demo 教师 + 3 学生 + 班级任务（幂等）
bash scripts/demo_smoke.sh                          # 4 项健康检查 + 课程包 43 可读性验证
```

打开 <http://localhost:8080> 即可登录演示账号。

清理：

```bash
bash deploy/scripts/teardown_demo.sh
docker compose -f deploy/docker-compose.yml down -v
```

### 5.2 本地开发

```bash
cd backend && JAVA_HOME=/path/to/jdk-21 mvn -s .mvn/settings.xml test    # 跑 101 测试
cd frontend && npm ci && npm run typecheck && npm run lint && npm run test
cd services/pythontopia-graph && python -m pip install -e ".[dev]" && python -m pytest -q
cd services/pythontopia-rag && python -m pip install -e ".[dev]" && python -m pytest -q
```

约束以 `AGENTS.md` 为准（前端 TS strict / 业务层禁 `any` / 后端 DTO + Flyway / Agent 不用 ReAct 等）。

## 6. 测试与质量基线

> 基线随 Infinity Living World 验收（2026-06-09）更新；旧版「234」已严重过时。

| 套件 | 通过 / 总数 | 备注 |
| --- | --- | --- |
| Backend（Spring Boot） | 464 / 464 | 含 Testcontainers 端到端 acceptance + 限频并发/补救/预言家/反思等 |
| Graph（FastAPI + LangGraph） | 380 / 380 | 含 11 Agent 单测 + 编排真接力 + 语义接地 entailment |
| RAG（FastAPI + 向量） | 21 / 21 | 含 chunker / embedder / 索引重建 / 安全词 |
| Frontend（Vue + Vitest） | 1514 / 1514 | 142 文件，含 Pinia store / API client / 组件 spec |
| **合计** | **2379 / 2379** | 0 失败 / 0 错误（typecheck 0 / lint 0 error） |

完整命令矩阵见 `docs/test_report.md`，最新一轮回归见 `docs/release/regression_report.md`。

## 7. 关键文档

| 文档 | 用途 |
| --- | --- |
| [`AGENTS.md`](./AGENTS.md) | 项目硬约束 + 目录约定 + 防幻觉规则 + 不做事项 |
| [`docs/system_design.md`](./docs/system_design.md) | 总体架构 mermaid + 11 Agent 关系 + R-14 单 hub 关键时序 |
| [`docs/demo_script.md`](./docs/demo_script.md) | 7 分钟分段演示脚本 + 风险与备用计划 |
| [`docs/test_report.md`](./docs/test_report.md) | 4 套件命令矩阵 + 覆盖与未覆盖项 + 性能基线 |
| [`docs/beta_acceptance_checklist.md`](./docs/beta_acceptance_checklist.md) | 27 项 Beta 验收清单 |
| [`docs/ai_coding_disclosure.md`](./docs/ai_coding_disclosure.md) | AI 协作披露 + 工具清单 + 与 Alethicode 关系 |
| [`docs/open_source.md`](./docs/open_source.md) | 第三方依赖 / 像素素材 / 字体 License |
| [`docs/release/README.md`](./docs/release/README.md) | Beta v1 提交包导览 |
| [`plan/阶段总览.md`](./plan/阶段总览.md) | 16 个阶段计划入口 |

## 8. 防幻觉与合规底线

- 所有 Agent 生成的学习资源都经过 `GroundingCriticAgent` 与 `SafetyAgent`，命中即写明 `evidence_page_ids` 或显式标注「基于通识知识」。
- RAG 命中分数低于阈值或触发敏感词时，统一返回 `no_evidence_found=true, hits=[]`，**绝不编造课件引用**。
- 隐藏测试用例不下发到前端 DTO；判题失败统一脱敏宿主路径。
- 像素素材全部来自 OpenGameArt CC0 与 itch.io 标注开源条目，每个目录配 `LICENSE.md`，**不复制《星露谷物语》官方素材**。
- 演示账号统一 `demo-` 前缀虚构身份，提交包不含真实学生 PII 与真实 LLM 密钥（`.env` 已 gitignore）。

## 9. 迭代历史

| 阶段 | 名称 | 状态 |
| --- | --- | --- |
| 00 ~ 15 | 仓库骨架 → Beta 验收 / PPT 收尾 | 已交付，见 `CHANGELOG.md` |
| R-10 | 10 轮 Web 端代码质量与性能小步迭代 | 已完成，见 `docs/release/r10_summary.md` |
| R-11 | 根 README 重写为成熟教学游戏作品门面 | 已完成，见 `docs/release/r100_iterations.md` |
| R-12 | 星露谷化 Web 改造 | 已完成；5 图/作物旧方案已被 R-14 推翻 |
| R-14 | 世界收缩方案 A | 进行中：主线收敛为单一 `town_hub`，外围图与农场/作物/货币/商店从主线移除 |
| Android-R0 | Android 客户端启动：10 subagent 调研 + ADR-001 + roadmap | 已完成，见 `android/docs/` |
| Android-R1 ~ R17 | Android 客户端从骨架到 Beta | 进行中，见 `android/docs/roadmap.md` |

视频与 PPT 物件由真人按 `docs/demo_script.md` 与 `docs/release/demo_v1.md` 录制制作，不入 git。

## 9.1 多端架构（重要）

自 R-14 起，Pythontopia 从单端 Web 项目演进为 **Web / Android / Windows / 微信** 多端共用后端：

- **Web 端**（`frontend/`）：Vue 3 + Phaser 3，当前主线演示端；R-14 收敛为 `town_hub` 单 hub。
- **Android 端**（`android/`）：Kotlin + Jetpack Compose + LibGDX，复用后端 / Agent / RAG。
- **Windows 端**（`windows/`）：.NET 8 + WinUI 3 + MonoGame，建设中。
- **微信端**（`wechat-miniprogram/` + `wechat-minigame/`）：小程序做学习陪伴，小游戏做像素小镇。
- **后端**（`backend/` + `services/`）：多端共用唯一真理源，作为 REST API 提供方继续演进。
- **详见**：`docs/clients/api-contract.md`、`docs/clients/README.md`、`android/README.md`、`android/docs/ADR-001-tech-selection.md`。

## 10. 许可

详见 [`LICENSE`](./LICENSE) 与 [`docs/open_source.md`](./docs/open_source.md)。

## 11. 团队

3 人 + AI 协作开发；所有 AI 生成代码与文档均经人工审阅并对最终质量负责（详见 `docs/ai_coding_disclosure.md`）。
