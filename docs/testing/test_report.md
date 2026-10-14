# Pythontopia 测试报告（阶段 14）

> 反映阶段 00~13 通过测试的状态。每次发布前需重跑并刷新本文件。

## 1. 测试套件总览

| 套件 | 命令 | 通过数 | 备注 |
| --- | --- | --- | --- |
| Backend (Spring Boot) | `cd backend && mvn test` | **100 / 100** | 含 14 个 Stage 集成测试与 9 个服务/单元测试 |
| Graph (FastAPI + LangGraph) | `cd services/pythontopia-graph && pytest -q` | **59 / 59** | 含 ProfileAgent / Path / Debug / Evaluation 等 |
| RAG (FastAPI + pgvector) | `cd services/pythontopia-rag && pytest -q` | **18 / 18** | chunker / embedder / store / safety |
| Frontend (Vitest) | `cd frontend && npm run typecheck && npm run lint && npm run test` | **52 / 52** in 25 files | 含 BoardPanel / WorkshopPanel / ReviewHallPanel 等组件测试 |

总计：**229 个测试通过 / 0 失败 / 0 错误**。

## 2. 覆盖范围

### 2.1 学生端核心流程

- 注册 / 登录：JWT、邀请码、密码 BCrypt（`Stage02AcceptanceIntegrationTest`）。
- 画像访谈：8 维 + grounding/safety（`Stage04ProfileAcceptanceIntegrationTest`）。
- 知识田 bootstrap：65 KC × `crop_tile_state`、`MasteryEvaluator` 单写（`Stage05CurriculumBootstrapIntegrationTest`）。
- 资源工坊：6 类资源、限频、grounding 失败 fail-fast（`Stage07WorkshopAcceptanceIntegrationTest`）。
- 学习路径 + 公告板：`PathPlannerAgent`、quest 状态机、班级任务（`Stage08PathBoardAcceptanceIntegrationTest`）。
- 训练场 + 判题：Piston adapter / fail-closed、hidden cases、限频 429、双轨执行（`PistonPythonSandboxTest`、`Stage09SubmissionAcceptanceIntegrationTest`）。
- Bug 矿洞：6 卡片、Layer 1 默认 revealed、重试 AC → `DEBUG_FIX` 事件（`Stage10BugMineAcceptanceIntegrationTest`）。
- 复盘大厅：6 metric / DRAFT path / accept / keep / 跨用户 404 / 重复 accept 409（`Stage13ReviewHallAcceptanceIntegrationTest`）。

### 2.2 教师端

- 班级 CRUD、加入码 rotate、班级任务 publish、看板 5 类聚合（`Stage12TeacherDashboardAcceptanceIntegrationTest`）。

### 2.3 服务端 Agent

- ProfileAgent：5 测试。
- CurriculumMapperAgent：6 测试。
- ResourceGraph + 6 个 agent：12 测试（含 hidden_tests 不暴露断言）。
- PathPlannerAgent：9 测试。
- DebugAgent：5 测试。
- EvaluationNarrativeAgent：6 测试（含 grounding 拒绝越界数字 / KC）。
- SparkProvider：8 测试。

### 2.4 RAG

- 18 测试覆盖 chunker / embedder / vector store / safety 词表 / token 鉴权。

## 3. 已知不覆盖项

- 真实 LLM 网络调用：CI / 单元测试默认 `PYT_LLM_PROVIDER=dryrun`，演示前手动用 Spark 跑一次。
- 浏览器 E2E：阶段 03 留下 `frontend/tests/integration/stage03_frontend_backend.py` Playwright 脚本，需要 Postgres + Backend 真实运行；阶段 14 demo_smoke.sh 替代。
- Phaser 渲染：`canvas` 输出由 jsdom 不能完整模拟，仅在 adapter 层做单测。

## 4. 性能基线（建议演示前抽样）

| 接口 | 期望 P95 | 备注 |
| --- | --- | --- |
| `POST /api/auth/login` | < 200ms | BCrypt cost 10 |
| `POST /api/learner/curriculum/bootstrap` | < 600ms | 65 KC 一次 upsert |
| `POST /api/workshop/resources` | < 5s | 包含 RAG + Graph，演示前可热身 |
| `POST /api/submissions` | < 2s | 独立 Piston 服务；隔离边界依赖部署配置，本地未配置时 fail closed |
| `POST /api/learner/evaluations` | < 2s | 6 SQL 聚合 + Graph |

## 5. 复测命令

```bash
cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test
cd ../frontend && npm run typecheck && npm run lint && npm run test
cd ../services/pythontopia-graph && .venv/bin/python -m pytest -q
cd ../pythontopia-rag && .venv/bin/python -m pytest -q
```
