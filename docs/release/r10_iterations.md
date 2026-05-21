# R-10 代码质量与性能迭代日志

> 阶段 15 完成后启动的 10 轮小步审查，目标：让 Pythontopia 更像一个成熟的教学游戏作品。
> 每轮聚焦 1~3 项可验证改动，跑相应测试确认无回归。**不加大功能模块**。

## R1 — graph & SQL grounding 修复（合入阶段 13）

- `EvaluationNarrativeAgent`：修复 `NUMBER_PATTERN` 把 KC code 内 `43` / `-001` 误判为越界数字；新增 `_number_aliases` 同时保留 `0.10` 与 `0.1` 两种 narrative 写法；`recent_events.detail` 中的数字纳入 allowed 集。
- `EvaluationRepository.countAcceptedSubmissionsAfter` / `findRecentEvents` 中的 `'ACCEPTED'` 改为 `'AC'`，对齐 `V060` 表约束。
- Stage13 集成测试用 `MockRestServiceServer.ignoreExpectOrder(true)` + `ExpectedCount.times(2)` 支持单测试内多次评估。
- 验证：graph 59/59，backend 100/100。

## R2 — frontend router 全量懒加载

- `src/app/router.ts` 除 `LoginPage` / `TownPage` 外，所有页面改 `() => import(...)` 动态导入；初始 chunk 体积下降。
- 验证：`npm run typecheck && lint && test` -> 25/52 通过。

## R3 — 教学游戏 onboarding 文案

- `ReviewHallPanel` 顶部 hint 解释"复盘大厅在教育闭环中的位置 + 触发条件"，空列表 hint 提示"先种 KC、提交练习再来评估"。
- `BoardPanel` 顶部 hint 引入"播种→浇水调度站"教学比喻。
- `WorkshopPanel` 顶部 hint 明确"资源工坊是浇水/施肥环节，超纲必显示"。
- 验证：vitest 25/52 通过。

## R4 — 防御编程（小步）

- `LearnerEvaluationService.compute` 已具备 narrative graph 失败 fail-fast；planner 失败时把错误注入 `pathDiffSummary` 便于回溯。
- 边界用例覆盖：周期内 0 事件 → PARTIAL；narrative 失败 → 422；DRAFT 缺失 accept → 409；重复 accept → 409。

## R5 — frontend store action 测试覆盖

- 新增 `tests/stores/learning.evaluation.spec.ts` 4 个测试：`loadEvaluations / runEvaluationNow / acceptEvaluationPath / keepCurrentEvaluationPath`。
- 验证：vitest 26 files / 56 tests 通过。

## R6 — backend planDraft fail-fast 用例补强

- `LearningPathServiceTest.planDraftRequiresProfileSameAsPlan`：无画像调用 `planDraft` 必须 `PROFILE_REQUIRED` 并阻止任何 DRAFT 写入。
- 验证：`mvn test` 101/0/0。

## R7 — 错误码与文案统一

- 现有错误码体系：`PROFILE_REQUIRED` / `PATH_*` / `EVALUATION_*` / `RESOURCE_*` / `BUG_MINE_*` / `SUBMISSION_*` / `RAG_*`，按业务域归一。
- frontend toast / `actions.disabled` 状态消化所有 4xx 错误码，避免演示时弹 JSON。

## R8 — 已知缺陷文档化

- `docs/release/known_issues.md` 明确演示阻断/非阻断分类、工程债与未来工作；演示前 24h 必检 SLA 写明。

## R9 — 文档闭环

- `docs/release/regression_report.md` 反映 R-10 后最终测试数（234 通过）。
- `docs/release/README.md` 提交包导览清单与制作流程。
- `docs/release/r10_iterations.md`（本文件）记录每轮改动。

## R10 — 最终总结

详见 `docs/release/r10_summary.md`。

## 测试结果（R-10 收尾）

| 套件 | 数量 |
| --- | --- |
| Backend (Spring Boot) | **101 / 101** |
| Graph (FastAPI + LangGraph) | **59 / 59** |
| RAG (FastAPI + pgvector) | **18 / 18** |
| Frontend (Vue 3 + Vitest) | **56 / 56** (26 files) |
| **合计** | **234 / 234 通过 / 0 失败** |
