# Pythontopia Beta 验收 Checklist

> 依据 `plan.md` 第 9 节与 `AGENTS.md` 硬要求逐项打勾，Beta 提交前必须全部 `OK`。
> `NEED_FIX` 项必须挂 issue 并在 `docs/release/known_issues.md` 中复述。

## 1. 功能验收

| # | 项目 | 验收命令 / 操作 | 状态 |
| --- | --- | --- | --- |
| 1 | 学生注册 + 登录 → 进入小镇 | `bash deploy/scripts/seed_demo_classroom.sh` 后用 `demo-stu-01` 登录 | OK |
| 2 | 教师创建班级 + 发布班级任务 | 演示班级 `Pythontopia 演示班` 与班级任务样例 | OK |
| 3 | 学生完成 8 维画像 | 画像屋对话生成 8 维结构化输出 | OK |
| 4 | 学生种植 ≥ 10 个真实 KC | `/api/learner/curriculum/bootstrap` 触发 65 KC | OK |
| 5 | 生成 ≥ 6 类资源 | `/workshop` 各类资源至少 1 份成功 | OK |
| 6 | 每个资源带课件引用或显式超纲说明 | grounding_status / evidence_page_ids 必须非空或 `NO_EVIDENCE` 标记 | OK |
| 7 | Pyodide 即时运行可用 | `/training` 任意题预览 | OK |
| 8 | 服务端判题入库 | `practice_submission` + `judge_result` 表实际写入 | OK |
| 9 | 失败提交可进入 Bug 矿洞 | WA/RE 提交触发 `/bug-mine` 入口 | OK |
| 10 | 班级看板展示真实聚合数据 | `/teacher/classroom/:id` 6 tabs 全有数据 | OK |
| 11 | 学生复盘评估并接受新路径 | `/review-hall` 立即评估 + 接受新路径 | OK |

## 2. 工程验收

| # | 项目 | 验收命令 | 状态 |
| --- | --- | --- | --- |
| 1 | `frontend` TypeScript strict 通过 | `npm run typecheck` | OK |
| 2 | `frontend` Vitest 通过 | `npm run test` | OK (52/52) |
| 3 | `backend` Maven test 通过 | `mvn test` | OK (100/100) |
| 4 | `pythontopia-graph` pytest 通过 | `pytest -q` | OK (59/59) |
| 5 | `pythontopia-rag` pytest 通过 | `pytest -q` | OK (18/18) |
| 6 | Docker Compose 一键启动 | `docker compose up -d --build` | OK |
| 7 | 6 service 全部 healthy | `docker compose ps` | OK |
| 8 | `demo_smoke.sh` 通过 | `bash scripts/demo_smoke.sh` | OK |
| 9 | 无未授权素材 | `assets/pixel/LICENSE.md` 全部 CC0/OFL | OK |
| 10 | 无伪造课件引用 | grounding 严格 fail-fast，单元测试覆盖 | OK |
| 11 | 无纯前端假数据冒充真实业务数据 | 所有 store 数据走真实 API | OK |

## 3. 教育验收

| # | 项目 | 状态 |
| --- | --- | --- |
| 1 | KC=种子 / 资源=浇水 / 练习=除草 / Bug=病害 / 掌握=成长 映射写在 `docs/architecture/field_mapping.md` 与 `docs/mastery_formula.md` | OK |
| 2 | 班级任务真实推送到学生公告板，学生完成会反映到看板 | OK（Stage12 验证）|
| 3 | narrative_summary 不出现未在 metrics 中的数字 / KC code | OK（agent grounding + 6 测试）|
| 4 | 复盘大厅显式标注 "AI 生成解释" | OK（panel UI tag）|
| 5 | 班级排名仅匿名编号 | OK（不持久化到 metric 表）|

## 4. 合规验收

| # | 项目 | 状态 |
| --- | --- | --- |
| 1 | 项目 License 已选择（默认 MIT） | NEED_FINALIZE（阶段 15 替换 LICENSE 占位）|
| 2 | 第三方依赖 License 清单 | OK（`docs/legal/open_source.md`）|
| 3 | AI Coding 披露 | OK（`docs/project/ai_coding_disclosure.md`）|
| 4 | 演示账号一次性密码、演示后清理 | OK（`teardown_demo.sh`）|
| 5 | `.env` 不入 git | OK（`.gitignore` 含 `.env`）|

## 5. 性能基线

抽样指标（演示前再跑一遍）：

- `POST /api/auth/login` P95 < 200ms。
- `POST /api/learner/curriculum/bootstrap` P95 < 600ms（65 KC 一次 upsert）。
- `POST /api/workshop/resources` P95 < 5s（含 RAG + Graph）。
- `POST /api/submissions` P95 < 2s（subprocess sandbox）。
- `POST /api/learner/evaluations` P95 < 2s（6 SQL 聚合 + graph 调用）。

## 6. 最终复测命令

```bash
cd /home/cypress/Pythontopia/backend && mvn -s .mvn/settings.xml test
cd ../frontend && npm run typecheck && npm run lint && npm run test
cd ../services/pythontopia-graph && .venv/bin/python -m pytest -q
cd ../pythontopia-rag && .venv/bin/python -m pytest -q
cd ../../deploy && docker compose up -d --build
bash scripts/init_rag_index.sh
bash scripts/seed_demo_classroom.sh
bash scripts/demo_smoke.sh
```

每条命令的退出码记录在 `docs/release/regression_report.md`。
