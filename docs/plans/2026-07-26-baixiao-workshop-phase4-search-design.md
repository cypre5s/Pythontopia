# 百晓工坊 Phase 4 有界搜索与恢复 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 只为 C3 复杂规划请求生成恰好三条受权候选 Agent 路径，以 `f=g+h+penalty` 确定性选优，并在执行失败时最多切换一次候选路径，同时导出真实运行指标。

**Architecture:** 在现有 `compile_route_decision` 后增加薄的确定性搜索层，候选只能重排 RouteDecision 已授权的工具和 Agent，不让模型生成或泄露 chain-of-thought。`compile_task_plan` 消费被选候选；C3 执行失败时复用当前 Orchestrator 的有界重规划点，只重排尚未成功的步骤，已成功副作用不重复执行。C0-C2 保持现状。

**Tech Stack:** Python 3.11、Pydantic v2、FastAPI、LangGraph、pytest；Java 21/Spring DTO 透明代理；Vue 3/TypeScript/Playwright。

---

## 冻结设计

### 方案比较

1. **推荐：Route compiler 后的确定性 C3 搜索层。** 权限、工具集合、深度和重规划上限继续由现有 `RouteDecision`/`ToolRegistry` 决定；三候选只是已授权步骤的不同顺序，最容易审计和回放。
2. **把 ToT/A* 放进 LLM Router。** 候选数量、工具名和预算可能漂移，还会诱导暴露模型思维过程，不采用。
3. **另建 Search Supervisor。** 会复制 `OrchestratorGraph`、TaskPlan 和运行记录，违反单一编排脊柱，不采用。

### C3 判定

- C0-C2 行为保持不变。
- 只有一个请求同时包含至少三个明确学习动作，且包含顺序/路径规划诉求时，编译为 `C3 + PLANNER_EXECUTOR`。
- 根计划典型句“先总结，再根据错误出题，并告诉我先做哪一道”确定性补齐 `HANDOUT + PRACTICE + PLAN`。
- 低置信、缺权限或缺上下文仍优先澄清，不进入搜索。

### 候选与 A* 合同

- `RouteSearch.strategy = BOUNDED_TOT_ASTAR`。
- `candidates` 必须恰好 3 条，每条最大深度 3；工具集合必须与 `RouteDecision.tools.required` 相同，Agent 必须来自唯一 `ToolRegistry`。
- 候选模板为规划/诊断优先、知识重构优先、练习探测优先；只保存公开路径和数值分解，不保存隐藏推理。
- 分数为 `total_cost = g_cost + h_cost + penalty`，按 total、candidate id 稳定选最小值。
- 高负荷/高挫败惩罚练习探测优先；重复错误降低规划优先的剩余估计；近期同类资源提高重复生成惩罚。

### 一次重规划

- C3 某一步失败后，切换到下一条最低分候选，重排仍未成功的步骤；已成功步骤不再执行。
- 重规划次数硬限制为 1；第二次失败返回部分结果并阻塞依赖，不进入自由 ReAct。
- C2 既有 `PLANNER_EXECUTOR` 的“同一步重试一次”语义保持不变。

### 指标

- 复用 `GET /agents/harness/stats`，新增 `routing` 快照：总路由、C3 路由、三候选集合、A* 选择、执行数、Agent/Tool 数、重规划、fallback、P50/P95 延迟及相应比率。
- 只导出运行时可观测量；Intent Accuracy、Top-1 Accuracy 等需要标注真值的指标继续由离线 golden eval 计算，不伪造线上准确率。

## Task 1：冻结 C3 搜索合同与 RED

**Files:**

- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/workshop_route.py`
- Create: `services/pythontopia-graph/tests/test_workshop_c3_search.py`
- Modify: `services/pythontopia-graph/tests/test_workshop_route_contract.py`

1. 先写失败测试，断言 C0-C2 的 `search is None`，典型 C3 请求具有恰好三候选、深度不超过 3、分数和稳定选中项。
2. 运行：
   `services/pythontopia-graph/.venv/bin/python -m pytest -q tests/test_workshop_c3_search.py tests/test_workshop_route_contract.py`
3. 预期 RED：`RouteSearch`/`search` 尚不存在。
4. 最小实现 Pydantic 合同；非 C3 默认 `None`。
5. 重跑并记录 GREEN。

## Task 2：确定性 C3 判定、候选生成与策略校验

**Files:**

- Modify: `services/pythontopia-graph/pythontopia_graph/routing/workshop_policy.py`
- Modify: `services/pythontopia-graph/tests/test_workshop_c3_search.py`
- Modify: `services/pythontopia-graph/tests/test_workshop_policy_gate.py`

1. 先增加 RED：典型三目标句变为 C3；两目标 Phase 2 句仍为 C2；三候选不能扩大工具/Agent 权限；同输入/画像同 route id 和选择。
2. 实现最小的第三动作提取和三种受权顺序，计算 `g/h/penalty`。
3. 在 `validate_route_decision` 强制：非 C3 禁止 search，C3 必须三候选、每条工具集合相同、深度/Agent/预算合法、selected id 存在且为最低分。
4. scoped Ruff 与定向 pytest 全绿。

## Task 3：TaskPlan 消费候选与一次真实候选切换

**Files:**

- Modify: `services/pythontopia-graph/pythontopia_graph/schemas/orchestrator.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/routing/workshop_task_plan.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/orchestrator_graph.py`
- Create: `services/pythontopia-graph/tests/test_orchestrator_c3_replan.py`
- Modify: `services/pythontopia-graph/tests/test_orchestrator_compound_task_plan.py`

1. 先写 RED：TaskPlan 步序等于 selected candidate；第一步成功、第二步失败时切下一候选；成功步骤调用一次；失败步骤最多两次；`replans_used == 1`；第二次失败返回 PARTIAL。
2. 给 TaskPlan 增加 initial/active/replanned candidate id。
3. 增加纯函数重排“已完成 + 下一候选剩余步骤”，重新生成依赖但不引入新工具。
4. C3 使用候选切换；C2 保留既有同一步重试。
5. 运行新旧 TaskPlan/Orchestrator 定向测试，确认旧 Phase 2 合同不回归。

## Task 4：运行指标复用现有 harness

**Files:**

- Create: `services/pythontopia-graph/pythontopia_graph/routing/workshop_metrics.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/agents/intent_router_agent.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/graph/orchestrator_graph.py`
- Modify: `services/pythontopia-graph/pythontopia_graph/routes/intent.py`
- Create: `services/pythontopia-graph/tests/test_workshop_routing_metrics.py`

1. 先写 RED：快照初始为零；C2/C3 路由、三候选、执行、重规划、fallback 和 P50/P95 都从真实记录计算。
2. 使用锁保护进程内计数；测试提供显式 reset，生产单 worker 与仓库约束一致。
3. 把快照追加到现有 `/agents/harness/stats` 的 `routing` 字段，不创建重复 endpoint。
4. 定向测试与 intent route HTTP 测试全绿。

## Task 5：Java/TypeScript 合同与可理解 UI

**Files:**

- Modify: `backend/src/main/java/com/pythontopia/intent/dto/WorkshopRouteDecision.java`
- Modify: `backend/src/test/java/com/pythontopia/intent/IntentGraphClientTest.java`
- Modify: `backend/src/test/java/com/pythontopia/intent/RouteExecutionTokenSignerTest.java`
- Modify: `frontend/src/api/intent.ts`
- Modify: `frontend/src/components/workshop/WorkshopChat.vue`
- Modify: `frontend/src/components/workshop/__tests__/WorkshopChat.spec.ts`

1. 先写 Java/Frontend RED：snake_case 搜索合同透明转 camelCase，签名包含 search；计划卡只显示“已比较 3 条受限路径并选择…”，不显示内部画像原始信号或隐藏推理。
2. 增加 DTO，不创建新 API。
3. 跑 Backend Intent/Signer、Frontend WorkshopChat 与 typecheck。

## Task 6：全量回归、真实浏览器与文档

**Files:**

- Modify: `frontend/e2e/baixiaoAgentRouting.spec.ts`
- Modify: `docs/plans/2026-07-22-baixiao-workshop-agent-routing-worklog.md`
- Modify: `docs/project/CHANGELOG.md`

1. 真实注册学生路由典型 C3 句，断言 C3、三候选、A* 选中、TaskPlan 实际顺序和 UI 可读说明。
2. 用可控 Graph 单测制造失败，证明候选切换一次；浏览器不伪造生产失败。
3. 跑 Graph/RAG/Frontend/Backend 全量；Backend 容器错误逐类隔离复跑。
4. `./dev.sh` 重启后跑专项 Chromium 三次与完整百晓套件一次。
5. 独立代码审查；只在无 Critical/Important 后更新 worklog、CHANGELOG 和最终需求矩阵。

> 当前会话遵循用户指定的 dirty `main` 工作树；不执行本技能模板中的自动 commit，也不触达研究目录、`todo.md` 或既有 `publicAcceptance.spec.ts` 差异。

## 已知限制（独立审查记录）

- **恢复边界信任 `status=="done"`**：签名只覆盖决策，不能证明运行时步骤状态；公共 HTTP 路径每轮由 `_node_build_plan` 重新编译全 pending 计划，因此该信任只在进程内恢复/checkpoint 路径生效。能直接写进程状态或 checkpoint 存储的攻击者可以伪造 done 跳过执行——该攻击面等价于已拥有服务进程控制权，属接受的取舍。
- **执行期 search 严格重算相等**：route 与执行窗口内学习事实（如异步判题落库）变化会使整轮 fail-closed 失败；重试会拿到基于新事实的新路由。这是防伪造设计的有意可用性代价。
- **指标为进程内计数**：与生产单 worker 约束一致；进程重启归零，跨实例聚合需外部采集。
