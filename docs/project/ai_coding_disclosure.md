# AI Coding 工具披露（阶段 14）

> 比赛披露要求：列出 AI 协助生成的模块、人工编写的模块、所用 AI Coding 工具与场景，以便评审复核。

## 1. 使用的 AI Coding 工具

| 工具 | 形态 | 用途 |
| --- | --- | --- |
| Cursor IDE / agent 模式 | IDE + 多步 agent | 主要驾驶舱：阶段规划、代码生成、测试、文档 |
| Composer 2.5 fast / Claude Opus | 大模型 | 代码生成与重构 |
| Codex CLI | 命令行 | 局部代码片段补全（少量） |

## 2. AI 协助生成的模块

以下模块由 AI 协助生成（Prompt 模板 + 代码骨架），人工审阅、修正与测试：

- 后端：
  - `com.pythontopia.workshop.*`、`com.pythontopia.submission.*`、`com.pythontopia.bugmine.*`、
    `com.pythontopia.learner.path.*`、`com.pythontopia.classroom.dashboard.*`、
    `com.pythontopia.learner.evaluation.*`。
- Python graph：`pythontopia_graph/agents/*.py`、`pythontopia_graph/nodes/*.py`、prompt 模板与 grounding。
- Python rag：`pythontopia_rag/chunker.py`、`pythontopia_rag/embedder.py`、`store.py`、`safety.py`。
- 前端：`@/components/board/*`、`@/components/workshop/*`、`@/components/bugmine/*`、
  `@/components/training/*`、`@/components/review/*`、Pinia store 切片。
- 数据迁移 SQL：`V001~V092`（人工对照 Alethicode 抽取后改写）。
- 测试：约 70% 测试用例由 AI 生成骨架，人工补充关键断言。

## 3. 由人工编写的模块

- 项目硬约束与边界：`AGENTS.md`、`plan.md`、`plan/阶段0X-*.md`。
- 课程包种子导出脚本与种子 SQL：`data/course-pack-43/export/*`、`data/course-pack-43/build_seed_sql.py`。
- 关键架构决策与硬约束（双轨判题、不启用 ReAct、不复制星露谷素材、班级架构轻量化等）。
- 演示脚本、UX 走查 checklist 与文档结构。
- 各阶段验收清单与 CHANGELOG 的内容审核。

## 4. AI 协作过程注意事项

- AI 输出的所有数字、KC code 与课件引用均经过 `GroundingCriticAgent`、`SafetyAgent` 二次校验，
  避免虚构。
- AI 不直接接入真实学生数据；演示账号统一 `demo-` 前缀，演示后用 `teardown_demo.sh` 清理。
- 所有真实 LLM provider 凭据（讯飞 Spark）只放在 `.env`（被 `.gitignore` 排除），代码与文档不出现明文。
- AI 不修改 git history / push 远端 / 部署生产，所有动作落在本地仓库与本地 Docker。

## 5. 与 Alethicode 的关系

Pythontopia 是 Alethicode 资产抽取后的**独立新项目**。允许参考的模块在 `AGENTS.md` 中列出。
**禁止**直接复制 Alethicode 的旧前端、Vuex、散装 API 字段、未迁移到 strict TS 的代码或未授权素材。
保留来源说明的目录：

- 课程包 schema 与迁移（`/home/cypress/Alethicode/backend/.../db/migration/V22-V26, V40`）。
- RAG 与 grounding 节点（`/home/cypress/Alethicode/services/alethicode-rag`、`qa-graph/nodes`）。
- 多智能体编排参考（`/home/cypress/Alethicode/services/tutor-graph`）。
- 班级管理参考（`/home/cypress/Alethicode/backend/.../service/classroom`）。

## 6. 声明

本项目所有 AI 生成代码均由项目成员审阅，作者对最终代码质量与教育内容负责。AI 不能替代人工对教育目标
与课程映射的把关。
