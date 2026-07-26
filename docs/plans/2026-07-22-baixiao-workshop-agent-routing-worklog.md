# 百晓工坊 Agent 路由重构工作日志

> Updated: 2026-07-26 12:14 CST  
> Source spec: `/baixiao_workshop_agent_routing_complete_plan.md`  
> Delivery rule: 未记录 RED、GREEN、全量回归、真实联测、CHANGELOG 与剩余风险的 Phase，不得标记完成。

## 交付纪律

- 复用现有 `IntentRouterAgent`、`ToolRegistry`、`OrchestratorGraph`、专用 Agent、SSE/AG-UI、后端学习闭环和前端 `WorkshopChat`，不创建同职责平行架构。
- 不启用自由 ReAct；所有循环、工具调用、修复与重规划都有硬上界。
- 先查现有接口和调用链，再通过失败测试冻结新增契约；禁止用纯前端假数据或伪 submission、伪课件引用完成演示。
- 当前在有大量既有未提交改动的 `main` 工作树继续执行，只触达本目标必要文件；不覆盖研究目录、现有 CHANGELOG 内容或 `publicAcceptance.spec.ts` 的他人改动。

## Phase 0 · 现状审计与基线冻结 — 完成

### Phase 开始宣誓

- 以瞎猜接口为耻，以认真查询为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以臆想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

背诵时间：2026-07-22，开始代码审计前。

### 已查询的现有接口、DTO、调用方与测试

- Graph：`schemas/intent.py`、`agents/intent_router_agent.py`、`tools/registry.py`、`graph/tool_loop.py`、`graph/orchestrator_graph.py`、`agents/orchestrator_agent.py`、`agents/dispatch.py`、`graph/tool_executor.py`、`graph/resource_graph.py`、现有 Debug/Tutor/Guide/Resource/Practice/MindMap/Path Agent。
- Backend：`intent/*`、`orchestrator/*`、`workshop/*`、`learner/*`，以及 Intent、WorkshopGraph、ResourceWorkshop、MemoryBrief 的 Controller/Client/Service 测试。
- Frontend：`WorkshopChat.vue`、`toolRouting.ts`、`useSseStream.ts`、`agui.ts`、`workshopActivity.ts` 与对应 Vitest/E2E。
- 交付资产：根计划、现有百晓工坊/Agentic worklog、`docs/project/CHANGELOG.md`、Playwright 配置和真实启动文档。

### 审计结论

1. 当前代码具备强复用基线，但计划中的统一 `RouteDecision`、`TaskPlan`、`TaskFamily`、`Complexity`、`Topology` 和 Workshop 上下文合同尚未落地。
2. `IntentRouterAgent` 已完成丰富的确定性预路由、置信度、风险与澄清；应在其输出后增加薄的确定性 route compiler/policy gate，而不是重写 Router。
3. `OrchestratorGraph` 已有 understand → plan → dispatch → reflect → bounded replan → compose，应扩展为 Supervisor/多 Agent 执行脊柱，不新建同职责 `WorkshopGraph`。
4. `ToolRegistry` 是唯一工具目录，已有 scope、风险、超时、重试、幂等与 fallback；缺口是 Agent 权限、所需上下文、结果合同、副作用/敏感数据元数据和跨调用硬预算。
5. 现有 Tutor、Debug、Guide、Resource、Practice、MindMap、Path Agent 均可复用；其中 `FIX_BUG` 的工具执行路径错误地落到 Tutor，Path dispatch 还在注入固定 mastery/无画像，须通过阶段测试修正。
6. 后端已存在真实资源生成、消费、微测、evidence、KC mastery、画像、记忆和 next action 闭环；个性化阶段负责贯通上下文，不重建业务表或假接口。
7. 前端当前仅 PLAN 默认进入 Orchestrator，secondary intent 只是建议 chip；Phase 2 才把统一拓扑和多 Agent 执行接入生产路径。
8. Phase 4 的 C3 三候选、A* 评分与一次重规划目前没有实现或专项测试，不能用既有 PathPlanner 代称完成。

### Dirty Tree 基线

- 分支/基线：`main` / `4ac24e29`。
- 工作树在本目标开始前已包含大量 Qwen/R-SAGE 研究、Graph semantic contract/guard、CHANGELOG 和 E2E 未提交改动。
- 本目标不会修改 `research/vllm-agent-ft-lab/**`、`todo.md`（已移至 `research/vllm-agent-ft-lab/docs/finetuning-pain-points-report-2026-07-16.md`），也不会覆盖 `frontend/e2e/publicAcceptance.spec.ts` 的现有差异。
- `docs/project/CHANGELOG.md` 只在顶部追加本目标条目；所有现有 2026-07-11 至 2026-07-13 研究记录原样保留。

### 基线验证日志

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-22 21:39 | Graph 全量 | `services/pythontopia-graph/.venv/bin/python -m pytest -q` | 通过：1273 passed, 1 skipped；11 warnings |
| 2026-07-22 21:40 | Frontend 类型 | `npm run typecheck` | 通过 |
| 2026-07-22 21:40 | Frontend 单测 | `npm run test -- --run` | 通过：231 files / 2253 tests |
| 2026-07-22 21:46 | Frontend lint | `npm run lint` | 通过：0 errors，44 个既有 warning |
| 2026-07-22 21:46 | RAG 全量 | `services/pythontopia-rag/.venv/bin/python -m pytest -q` | 通过：127 passed, 2 skipped |
| 2026-07-22 21:42 | Backend 全量 | `mvn -s /home/cypress/.m2/settings.xml -o test` | 未全绿：904 tests，0 failures，6 Testcontainers 启动错误；根因均为 Docker 随机宿主端口竞争，不是断言失败 |
| 2026-07-22 21:47 | Backend 失败类重跑 | `mvn -s /home/cypress/.m2/settings.xml -o -Dtest=CoursePackServiceIntegrationTest,DecisionTraceControllerTest,EvidenceMemoryServiceTest,LearningEvidenceGraphServiceTest,MemoryRetrievalSemanticIntegrationTest,RecommendationServiceTest test` | 通过：18 tests，0 failures/errors；确认首次 6 errors 为可重试的环境端口竞争 |

### Phase 0 验收

- [x] 根计划逐节核对，四阶段边界已冻结。
- [x] 现有接口、调用链、真实业务闭环和测试矩阵已审计。
- [x] Dirty tree 与不触达边界已记录。
- [x] Graph、RAG、Frontend 基线全绿；Backend 首跑环境失败及精确重跑证据均保留。
- [x] Phase 1 最小切片确定为：统一路由合同 + 确定性策略门 + 五类 MVP 任务 + Debug 真实 Agent 路由 + 固定工具治理/护栏。

### 剩余风险

- 后端全量在当前 WSL/Docker 环境存在随机宿主端口碰撞；每阶段保留首跑结果，并对失败类逐一重跑，不能把 targeted 通过写成 full 通过。
- 当前 Graph 存在本目标开始前的未提交 semantic IR/guard 改动；新增 route compiler 必须复用这些合同并避免覆盖。
- 计划没有给所有指标明确数值门槛；沿用已有 workshop golden 门：tool selection ≥90%、schema-valid ≥95%、unnecessary call ≤5%、clarification recovery ≥90%、safety leak=0，并为新增拓扑/预算设置 100% deterministic contract gate。

## Phase Map

| Phase | 范围 | 主要验收 | 状态 |
|---|---|---|---|
| 1 | 稳定路由 MVP | 统一 RouteDecision；五类核心任务；DIRECT/SINGLE_AGENT_TOOL；固定 allowlist；grounding/safety；真实 Debug 路由 | 完成 |
| 2 | 受控工具与多 Agent 编排 | Supervisor 复用现有 Orchestrator；SEQUENTIAL/PARALLEL/PLANNER_EXECUTOR；TaskPlan；中间结果持久化/恢复 | 完成 |
| 3 | 个性化路由与学习闭环 | KC、提交、错误、负荷、偏好、挫败、近期资源进入上下文；同输入不同画像产生可解释差异；闭环写回 | 完成 |
| 4 | 有界 ToT + A* 与恢复/指标 | 仅 C3 生成 3 候选；深度≤3；A* 确定性选优；最多一次重规划；失败恢复和指标导出 | 完成 |
| Final | 全链路验收 | 四端全量、真实 `./dev.sh`、专项 Playwright `--retries=0`、最终需求矩阵、CHANGELOG/风险边界 | 完成 |

## Phase 1 · 稳定路由 MVP — 完成

### Phase 开始宣誓

- 以瞎猜接口为耻，以认真查询为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以臆想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

背诵时间：2026-07-22 21:49 CST，Phase 1 编码前。

### 本阶段边界

- 只实现统一 `RouteDecision` 最小合同、确定性 policy gate、五类 MVP 任务的 DIRECT/SINGLE_AGENT_TOOL、固定 Agent/工具白名单、低置信澄清和真实 Debug 路由。
- 复用现有 `IntentRouterAgent`、`ToolRegistry`、专用 Agent 与 GuardPipeline；本阶段不引入 Supervisor、多 Agent 拓扑、个性化搜索、ToT 或 A*。
- 先创建合同/策略失败测试并记录 RED，再写生产实现。

### 验收定义

- 每个 route 都包含稳定 route id、task family、complexity、topology、primary/supporting Agents、required/optional/forbidden tools、预算/审查策略、output 与 fallback。
- 概念/课件问答、Debug、资源、练习、路径五类核心业务映射到现有 Agent 和现有工具；未知 Agent/工具、预算越界和上下文缺失在执行前被拒绝或澄清。
- Debug 有可信代码/错误上下文时使用现有 DebugAgent；缺上下文时澄清，严禁伪造 submission/problem/verdict。
- 旧 `IntentRouteResponse` 字段保持兼容；Graph、Backend、Frontend 合同测试覆盖新增字段。

### RED 证据

| 时间 | 命令 | 预期失败 |
|---|---|---|
| 2026-07-22 21:55 CST | `services/pythontopia-graph/.venv/bin/python -m pytest -q tests/test_workshop_route_contract.py tests/test_workshop_policy_gate.py tests/test_intent_route.py` | collection 失败 2 项：`ModuleNotFoundError: No module named 'pythontopia_graph.routing'`；证明统一 route compiler/policy gate 尚不存在，测试确实先于实现 |
| 2026-07-22 21:58 CST | Backend Intent 三类定向 Maven | testCompile 失败：`InteractionContext`、`routeDecision()` 与第 13 个请求字段尚不存在 |
| 2026-07-22 21:58 CST | Frontend `WorkshopChat.spec.ts` + `toolRouting.spec.ts` | 2 个预期失败：高置信服务器澄清仍错误执行 Tutor；自由文本 0.55 边界仍沿用旧 0.75 逻辑 |
| 2026-07-22 22:02 CST | Backend `ResourceWorkshopServiceTest` | 32 tests 中 3 个预期失败：缺 safety、缺 grounding、无 `NO_EVIDENCE` 的 PARTIAL 均被旧逻辑错误发布；显式 NO_EVIDENCE 用例通过 |

### 实现与复用边界

#### Graph：统一 RouteDecision 与确定性策略门

- 新增 `schemas/workshop_route.py`：冻结 task family、C0-C3、五类 topology、Agent/工具选择、六项硬预算、审查策略、输出 schema 与 fallback 的结构化合同；route id 由规范化输入稳定生成。
- 新增 `routing/workshop_policy.py`：在现有 `IntentRouterAgent` 结果后编译/校验路由，不重写语义 Router；Phase 1 只允许 `DIRECT` 与 `SINGLE_AGENT_TOOL`。
- 扩展唯一 `ToolRegistry`：登记 Agent allowlist、必需上下文、结果 schema、敏感数据、副作用与执行模式；未知 Agent/工具、allowlist 越权、预算超限、schema 漂移均在执行前拒绝。
- 五类 MVP 映射复用现有 Tutor/Courseware、Debug、Resource、Practice、Path Agent；`FIX_BUG` 从错误的 Tutor 分组移到 `DebugAgent + BugMine`，且必须同时有当前用户的已验证失败提交与代码/错误事实。
- 自由文本澄清阈值统一为 `< 0.55`；显式 slash/能力/chip 只跳过语义消歧，仍经过 scope、上下文、权限和预算门。

#### Backend：可信上下文、兼容 DTO 与发布门

- `IntentRouteRequest` 增加 interaction/requestedIntent；新增 `WorkshopRouteDecision` DTO，并把 route decision 透明代理到旧 `IntentRouteResponse`，旧字段继续兼容。
- `IntentService` 复用 `PracticeSubmissionRepository` 校验 `latestSubmissionId` 必须属于当前 STUDENT 且状态为 FAILED；成功后用数据库 Submission/JudgeResult 覆盖前端代码、题目和 verdict，非法/越权/非失败引用一律剥离并进入澄清。
- `IntentGraphClient` 对旧 Graph 响应或 Graph 不可用采用 fail-closed 澄清，不伪造全零“已审计决策”。
- `ResourceWorkshopService` 继续复用现有 run/resource 流程，但发布门改为安全与 grounding 都显式通过；PARTIAL 仅允许显式 `no_evidence=true`，null/缺字段不再默认为通过。

#### Frontend：所有入口统一策略门与真实 Debug 链

- `WorkshopChat` 的自由文本、slash、能力启动器、澄清选择、纠错 chip 与 secondary chip 全部重新进入 `/api/intent/route`，路由错误 fail closed，不再静默降级 Tutor。
- 服务端 `needsClarification`、精确提示和候选优先；前端 0.55 边界只作为兼容兜底。
- `/修报错` 先读真实错题本，只上送 submission 引用；后端校验通过后调用既有 `openBugMineSession`，由 `BugMineService → DebugAgentClient → /agents/debug/analyze` 建立调试会话，最后通过 Pinia 私有入口进入 `/bug-mine`，从未调用 Tutor SSE。
- 新增独立 `e2e/baixiaoAgentRouting.spec.ts`，不修改已有 dirty `publicAcceptance.spec.ts`。

### 审查阻断项与修复

第一次独立代码审查给出 7 个 Important：Debug 合同/执行分裂、强制入口绕过策略、前端 Debug 上下文不可信、课程目录误判所有 ASK、0.75 旧阈值残留、validator/schema 双事实源、旧响应伪造 audited decision。以上逐项增加失败测试并修复；复审结论为 **READY**，无 Important 遗留。

### GREEN 与定向验证

| 时间 | 范围 | 命令/证据 | 结果 |
|---|---|---|---|
| 2026-07-22 | Graph route/policy/registry/executor | 相关 176 tests + scoped `ruff check` | 176 passed；Ruff all checks passed |
| 2026-07-22 | Backend Intent/Workshop | Intent 12 tests；Resource+Intent+Workshop 49 tests | 全部通过 |
| 2026-07-22 | Frontend Workshop | `WorkshopChat`、upload、tool routing 定向联跑 | 44 passed |
| 2026-07-22 | Frontend 类型 | `npm run typecheck` | 通过 |

### 全量回归与真实联测

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-22 22:28 CST | Graph 全量 | `python3 -m pytest -q` | 1290 passed, 3 skipped；1 个 AnyIO HTTP 422 弃用 warning |
| 2026-07-22 22:29 CST | Graph Phase 1 lint | `ruff check` route/schema/intent/registry/executor 及相关 tests | 通过 |
| 2026-07-22 22:41 CST | Backend 全量首跑 | `mvn -s /home/cypress/.m2/settings.xml -o test` | 913 tests，0 failures，6 errors；均为 Testcontainers 随机 host port `address already in use` |
| 2026-07-22 22:41 CST | Backend 失败类重跑 | 6 个失败类合并 `-Dtest=... test` | 18 tests，0 failures/errors；BUILD SUCCESS |
| 2026-07-22 22:41 CST | Frontend 全量 | `npm run typecheck`；`npm run lint`；机器可读全量 Vitest | typecheck 通过；lint 0 errors/44 warnings；231 files、688 suites、2256 tests 全部通过 |
| 2026-07-22 22:28 CST | RAG 全量 | `python3 -m pytest -q` | 129 passed |
| 2026-07-22 22:25 CST | 本地全栈健康 | `./dev.sh` 后检查 5173/18080/18091/18092 `/healthz` | 四端均返回 `{"status":"ok"}` |
| 2026-07-22 22:26 CST | Graph 实服务路由 | 真实 internal route 请求：概念答疑；缺上下文 FIX_BUG | 概念答疑 → TutorAgent；FIX_BUG → 精确澄清且不执行工具 |
| 2026-07-22 22:27 CST | 浏览器专项 E2E | `PYT_E2E_REGISTER=1 npx playwright test e2e/baixiaoAgentRouting.spec.ts --project=chromium --retries=0` | 1 passed；真实注册、失败判题、route decision、BugMine/DebugAgent、页面跳转、Tutor 零调用全部通过 |
| 2026-07-22 22:28 CST | 独立浏览器探针 | Python Playwright 临时探针（执行后删除） | `phase1 browser acceptance: PASS` |
| 2026-07-22 22:35 CST | 变更格式 | `git diff --check` | 通过 |

### Phase 1 验收

- [x] 统一 RouteDecision 最小合同、稳定 route id、五类 MVP task family 与 C0-C3 合同已落地。
- [x] Phase 1 topology 严格限制为 DIRECT/SINGLE_AGENT_TOOL；Agent/工具/schema/预算均由唯一 registry/policy gate 校验。
- [x] 低置信、缺 Debug 上下文、旧 Graph 和 Graph 故障全部 fail closed 澄清。
- [x] Grounding/Safety 发布门 fail closed；无证据 PARTIAL 必须显式披露。
- [x] `/修报错` 真实浏览器链已证明复用已验证 submission、DebugAgent 与 BugMine，Tutor SSE 调用数为 0。
- [x] Graph、Frontend、RAG 全量通过；Backend 首跑环境端口冲突与失败类绿灯复跑证据均保留。
- [x] 独立审查 READY、CHANGELOG 和本工作日志已更新。

### Phase 1 剩余风险

- 全仓 `ruff check pythontopia_graph tests` 仍会命中一个本目标之前的 `tests/test_agentic_ab.py:5` 未使用 `pytest` 导入；Phase 1 触达文件的 scoped Ruff 全绿。遵守外科手术边界，本阶段不夹带该无关清理。
- Backend Testcontainers 在当前 WSL/Docker 环境仍有随机宿主端口竞争；业务断言 0 failures，且全部失败类已成功复跑，但这不等价于一次无环境抖动的全量 exit 0。
- Phase 1 只证明单 Agent 路由；SEQUENTIAL/PARALLEL/PLANNER_EXECUTOR、TaskPlan 和中间结果持久化属于 Phase 2，不提前宣称完成。

## Phase 2 · 受控工具与多 Agent 编排 — 完成

### Phase 开始宣誓

- 以瞎猜接口为耻，以认真查询为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以臆想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

背诵时间：2026-07-22 22:43 CST，Phase 2 接口复核与编码前。

### 本阶段边界

- 复用现有 `OrchestratorGraph`、`OrchestratorAgent`、dispatch、reflect、bounded replan、SSE/AG-UI 与后端 orchestrator turn 存储，不新建同职责平行 Supervisor 服务。
- 增加统一 `TaskPlan`，放行并验证 SEQUENTIAL/PARALLEL/PLANNER_EXECUTOR；支持“总结+练习、诊断+补救、资源+路径、知识图谱+微测”四类复合任务。
- 工具调用继续由 ToolRegistry/RouteDecision 硬预算约束；最多 8 次工具、每工具 2 次、4 次 Agent、深度 3、并行 3、重规划 1 次。
- 先查询并冻结现有编排/存储接口，再增加失败测试；本阶段不引入 P3 个性化差异，也不启用 P4 ToT/A*。

### RED 证据

| 时间 | 命令/场景 | 预期失败 |
|---|---|---|
| 2026-07-22 | `test_workshop_task_plan.py`、`test_orchestrator_compound_task_plan.py` | 统一 TaskPlan/DAG、三种多 Agent topology 和 authoritative route 执行入口尚不存在 |
| 2026-07-22 | Backend route token / orchestrator recorder tests | route decision 无执行期签名，loop run 也未持久化 checkpoints/agents/tools/replay |
| 2026-07-22 | `WorkshopChat.spec.ts` compound/runtime cases | 复合请求仍拆成多次调用，计划终态与 runtime run id 不可见 |
| 2026-07-22 | 真实“总结后出题” Playwright | Graph 空进程内 KC 集合导致 `Unknown KC code: PY43-KC-031`，证明执行链未使用请求中的权威 pack43 目录 |
| 2026-07-22 | 真实“诊断后补救” Playwright | 后端对隐式补全后的 interaction 签名，而前端只能回传原始 null，执行期返回 403；证明签名消息与客户端原始上下文未对齐 |
| 2026-07-22 | TaskPlan 课程锚点测试 | recent/decaying KC 都为空时生成两个无 KC 步骤，未回退至权威 `course_kc_index` |

### 实现与复用边界

#### Graph：authoritative RouteDecision 驱动统一 TaskPlan

- 在既有 `OrchestratorGraph` 上增加 `TaskPlan`、依赖 DAG、fan-out/fan-in 和结构化步骤状态；不创建平行 Supervisor。
- 签名 RouteDecision 进入 authoritative execution path 后跳过旧 understand/reflect/replan，避免已经审计的 topology 被二次语义路由改写。
- `SEQUENTIAL`、`PARALLEL`、`PLANNER_EXECUTOR` 分别按依赖顺序、就绪层并发和 PLAN→EXECUTE 执行；同一步失败最多原样重试一次，禁止无界 ReAct。
- 四类复合任务复用既有 Tutor/Debug/Resource/Path/MindMap/Practice Agent；KC 锚点按 recent → decaying → 权威课程目录首项回退。

#### Backend：不可伪造执行令牌与真实运行记录

- 新增独立 HMAC route execution secret、10 分钟 TTL、常量时间验签；令牌绑定 user、route、原始 message 和原始 client interaction，服务端安全补全只用于 Graph 路由，不制造前后端签名漂移。
- `InteractionContextVerifier` 在执行前再次校验当前用户的 submission/problem/code/verdict 事实，前端字段不能扩大权限或覆盖数据库事实。
- `OrchestratorRunRecorder` 复用 `agent_loop_run` 与既有 runtime artifact 服务，先创建 run，再记录 plan/agent/tool checkpoint、终态和 replay；前端拿到的 runtime run id 可由真实 `/api/ai/loop-runs/{id}/replay` 查询。
- `OrchestratorService` 从 `CoursePackService` 重新装载唯一 pack43 KC 目录并过滤 recent KC，不信任客户端自报课程权威；Graph 在配置集合为空时使用请求课程目录完成执行校验。

#### Frontend：一次复合调用与运行态可见性

- 复合任务只提交一次 signed route + orchestrator turn，不再把 supporting intent 拆成第二条独立请求。
- `WorkshopChat` 展示 TaskPlan 步骤终态、streaming 状态和真实 loop run id；SSE `runtime_run` 事件写回消息卡片，便于浏览器验收和运行回放。
- 专项 E2E 覆盖单 Agent Debug、顺序“总结后出题”和自由文本“诊断后补救”；所有数据来自真实注册、正式判题、Graph、PostgreSQL 和 replay API。

### 审查阻断项与修复

首次独立复核结论为 **NOT READY**：发现 signed route 仍可能经过旧 reflect、PlannerExecutor 没有 PLAN 首步、route secret/消息绑定不够强、运行记录未真实可发现、Graph 空 KC authority、前端缺 runtime id，以及服务端隐式 interaction 签名漂移。以上均先增加失败测试再修复；最终独立复核结论为 **READY**。

### GREEN、全量回归与真实联测

| 时间 | 范围 | 命令/证据 | 结果 |
|---|---|---|---|
| 2026-07-22/23 | Graph 全量 | `.venv/bin/python -m pytest -q` | 1314 passed, 1 skipped；11 warnings |
| 2026-07-22 | RAG 全量 | `.venv/bin/python -m pytest -q` | 127 passed, 2 skipped |
| 2026-07-22 | Frontend 全量 | `npm run test -- --run` | 231 files / 2262 tests passed |
| 2026-07-23 | Frontend 类型/格式 | `npm run typecheck`；scoped `prettier --check` | 通过 |
| 2026-07-22 | Frontend lint | `npm run lint` | 0 errors；112 warnings（既有/风格告警） |
| 2026-07-22 | Backend Phase 2 定向 | Intent/Orchestrator/Workshop/Recorder + PostgreSQL migration | 54 tests passed；空库 100 migrations 通过 |
| 2026-07-23 | Backend 全量首跑 | `mvn test` | 930 tests，0 failures，4 Testcontainers 动态端口启动 errors |
| 2026-07-23 | Backend 失败类逐类复跑 | 4 个失败类逐类离线执行 | 13 tests，0 failures/errors；每类重新执行空库 100 migrations |
| 2026-07-23 | Graph scoped lint | Phase 2 production/tests `ruff check` | All checks passed |
| 2026-07-23 | 真实 Chromium 联测 | `PYT_E2E_REGISTER=1 npx playwright test e2e/baixiaoAgentRouting.spec.ts --project=chromium --workers=1 --retries=0` | 3 passed（2.5m） |
| 2026-07-23 | 仓库静态门 | `git diff --check`；`bash -n dev.sh deploy/scripts/preflight_ecs.sh` | 通过 |

### Phase 2 验收

- [x] SEQUENTIAL/PARALLEL/PLANNER_EXECUTOR 与统一 TaskPlan/DAG 已由确定性合同覆盖。
- [x] authoritative signed route 不再被旧语义环节改写；工具/Agent/深度/并行/重规划预算继续硬限制。
- [x] 执行 token 绑定当前用户与原始请求事实；服务端课程/提交补全不信任前端声明。
- [x] loop run、checkpoint、Agent、Tool、终态和 replay 真实持久化并可由浏览器读取。
- [x] 三条真实浏览器链路通过，独立复核 READY，CHANGELOG 与本日志同步完成。

### Phase 2 剩余风险

- Backend 全量仍受当前 WSL/Docker 的 Testcontainers 随机宿主端口碰撞影响；首跑 4 errors 已原样保留，逐类 13 tests 全绿不等价于单次全量 exit 0。
- route 与 execution 之间如果学生又产生一次更晚 submission，执行期会看到更新事实；所有权和失败状态仍重新校验，不能扩大权限，但可能改变“latest”语义。
- `OrchestratorRunRecorder` 为避免审计故障拖垮教学主链采用 fail-soft；数据库不可用时可能缺失运行审计，现有告警会暴露该状态。
- secret 配置已拒绝默认占位和过短值，但运行时无法测量运维人员实际 secret 的信息熵。

## Phase 3 · 个性化路由与学习闭环 — 完成

### Phase 开始宣誓

- 以瞎猜接口为耻，以认真查询为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以臆想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

背诵时间：2026-07-23 00:32 CST，Phase 3 现有画像与闭环接口审计前。

### 本阶段边界

- 只把现有 KC 掌握度、历史错误、认知负荷、学习偏好、当前挫败和近期资源使用事实贯通到路由、TaskPlan 和结果解释；不新增画像平行表或伪造学习事实。
- 同一请求在不同可信画像下产生确定、可解释、受预算约束的策略差异，并继续复用现有 Profile/Memory/Resource/Path/Practice/Workshop 闭环写回。
- 先查询后端真实接口与事件写回链，再以失败测试冻结上下文合同和差异规则；本阶段不启用 Phase 4 的 ToT/A*。

### RED 证据

| 时间 | 命令/场景 | 预期失败 |
|---|---|---|
| 2026-07-23 | Graph 个性化 route、真实 dispatch 定向测试 | `LearnerRoutingContext`、`RoutePersonalization`、同输入差异策略和真实画像 dispatch 尚不存在，测试在合同/导入处失败 |
| 2026-07-23 | Backend Intent/Orchestrator 学习上下文测试 | `LearnerRoutingContext`、聚合服务、Graph request `learner_context` 与 observation 写回尚不存在，test compile 失败 |
| 2026-07-23 | Frontend `WorkshopChat.spec.ts` | plan 卡片没有服务端个性化解释，新增 `plan-personalization` 断言失败 |
| 2026-07-26 | 真实 Chromium 个性化场景首跑 | 第二次正式失败提交命中真实 `SUBMISSION_THROTTLED`（10 秒一次）；证明测试必须遵循后端提交契约，未通过绕过限流或伪造历史解决 |
| 2026-07-26 | 真实 Chromium 个性化场景第二次运行 | 路由、上下文和页面均已成功，仅验收文案误写为“降低并发”，实际冻结文案为“短步骤串行执行并关闭并发” |

### 实现与复用边界

#### Backend：服务端权威学习上下文

- 新增 `WorkshopLearningContextService`，只聚合现有 `MemoryBriefService`、`PassiveProfileService`、`MasteryService`、`PracticeSubmissionRepository` 与 `LearningEventLogRepository`；未新增画像表或第二事实源。
- `LearnerRoutingContext` 携带 KC 掌握度、近期正式终态提交、错误模式、认知负荷、挫败、学习偏好和已消费资源；提交只采用 `PASSED/FAILED`，排除 `PENDING/RUNNING/ERROR`，资源事实只读取 backend/graph 写入的 `resource_consumed`，不把浏览器观察事件或“生成过”冒充“学习过”。
- 路由记忆通过 `buildAuthoritativeBrief` 只召回 backend/graph 事件并绕过 Mem0/Zep 等外部 provider；普通用户记忆仍保留既有前端观察事件与外部 provider 行为，安全收缩没有误伤用户可见召回。
- Intent 与 Orchestrator 在服务端按当前学生重新构建上下文并覆盖客户端画像/失败历史；Graph 调用统一传递 `learner_context`，客户端不能伪造掌握度或情绪事实。
- 路由完成只记录 `workshop_route_selected` 观察事件，明确 `masteryWrite=false`；路由本身不改 KC 掌握度，正向学习写回仍由正式提交、资源消费等既有领域事件负责。
- 单一画像来源故障采用可观测的局部 fail-soft，其他权威事实仍可参与路由；不静默降级为普通聊天或制造默认“优秀画像”。

#### Graph：确定性个性化策略与真实 Agent 输入

- 在现有 RouteDecision 增加 `RoutePersonalization`；策略只由可信上下文确定性派生，不让 LLM 自由决定权限、工具或预算。
- 高认知负荷/挫败触发 `SCAFFOLD_FIRST`，把 PARALLEL 收敛为 SEQUENTIAL 且 `max_parallel_agents=1`；弱 KC 触发 `REMEDIATION_FIRST`，视觉偏好触发 `VISUAL_FIRST`。
- 个性化只调整教学顺序、脚手架和并发，不删除用户明确请求的 HANDOUT/MINDMAP 等工具；工具权限、grounding、安全和总预算继续沿用 Phase 1/2 硬门。
- route id 对有意义的学习事实做稳定指纹，但排除只表示快照时间的 `as_of`，避免相同事实因时钟变化生成伪差异。
- `ResourceGraph` 接收真实画像；`PathPlannerAgent` 接收真实 mastery/confidence、近期资源、提交和错误模式，移除固定 mastery 与空画像占位。

#### Frontend：只展示可理解解释

- `WorkshopChat` 的 TaskPlan 卡片展示服务端第一条个性化解释，并通过 `data-testid="plan-personalization"` 提供稳定验收锚点。
- 页面不渲染 `cognitive_load`、`frustration_level` 等原始内部信号；用户只看到“为什么改变步骤/并发”的自然语言说明。
- DTO 对 personalization 保持可选，继续兼容旧 Graph/旧历史消息。

### 系统化调试与测试隔离

- Graph 全量首次仅有一个复合路由测试失败：测试只替换 LLM，却让默认 HTTP `EvidenceFetcher` 访问真实 RAG，报 `ConnectError`。
- 用最小静态 evidence 实验确认根因后，只为该测试注入 `StaticEvidenceFetcher + RagHit`；生产仍保持 evidence 服务 fail-fast，没有为测试增加静默降级。
- Playwright 首次失败保留后端 10 秒正式提交限流事实；最终测试按仓库既有规范跨过 11 秒窗口，再制造第二条真实失败历史。

### 审查阻断项与修复

- 第一次独立复核结论为 **NOT READY**，发现两个 Important：浏览器可直接写入的 `learning_event_log` 会污染路由记忆/资源事实；`PENDING/RUNNING` submission 会被投影成 `passed=false` 并误计为失败。
- 先增加编译失败与真实 PostgreSQL/Flyway 验收，再把路由上下文收敛到 backend/graph 事件与 `PASSED/FAILED` 终态提交；新增直接单测证明权威 brief 对外部 `MemoryProviderRouter` 零交互。
- 真实 Chromium 进一步注入两条伪失败和一条伪资源消费事件，RouteDecision 的 `PARALLEL/STANDARD` 与 route id 均保持不变；随后两次真实失败仍正确触发脚手架式串行。
- 独立复审最终结论为 **READY**，无 Critical/Important 遗留。

### GREEN、全量回归与真实联测

| 时间 | 范围 | 命令/证据 | 结果 |
|---|---|---|---|
| 2026-07-23 | Graph 个性化/dispatch 定向 | route、orchestrator、dispatch 相关测试 + scoped Ruff | 30 passed；Ruff all checks passed |
| 2026-07-26 | Graph 全量 | `python -m pytest -q` | 1319 passed, 1 skipped；11 warnings |
| 2026-07-23/26 | Backend 定向与审查修复 | Intent、LearningContext、MemoryBrief/Retrieval、Orchestrator、Stage09、共享调用方 | 原定向 24 passed；审查修复 23 passed；共享调用方 87 passed；权威 provider 零交互 6/6 passed |
| 2026-07-26 | Backend 全量首跑 | `mvn -s /home/cypress/.m2/settings.xml -o test` | 930 tests，0 failures，6 Testcontainers `pgvector:pg16` 启动 errors |
| 2026-07-26 | Backend 失败类逐一复跑 | `CoursePackServiceIntegrationTest`、`DecisionTraceControllerTest`、`EvidenceMemoryServiceTest`、`LearningEvidenceGraphServiceTest`、`MemoryRetrievalSemanticIntegrationTest`、`RecommendationServiceTest` | 六类逐一串行全部通过：18 tests，0 failures/errors；确认全量错误为容器启动竞争 |
| 2026-07-26 | Frontend 全量 | `npm run test -- --run`；`npm run typecheck` | 231 files / 2263 tests 全部通过；typecheck 通过 |
| 2026-07-26 | RAG 全量 | `python -m pytest -q` | 127 passed, 2 skipped |
| 2026-07-26 | 本地全栈健康 | `./dev.sh` 后检查 5173/18080/18091/18092 `/healthz` | frontend、backend、graph、rag 均返回 `{"status":"ok"}` |
| 2026-07-26 | 个性化 Chromium 重复验收 | `PYT_E2E_REGISTER=1 ... --grep "同一复合请求..." --repeat-each=3 --workers=1 --retries=0` | 3/3 passed；每次使用新学生，伪前端事件不改变路由，两次真实失败提交后策略才改变 |
| 2026-07-26 | 百晓路由完整 Chromium 套件 | `PYT_E2E_REGISTER=1 npx playwright test e2e/baixiaoAgentRouting.spec.ts --project=chromium` | 4/4 passed（2.1m）：Debug、顺序资源、抗事件污染的个性化串行、诊断补救全部通过 |
| 2026-07-26 | 静态门禁 | Frontend `typecheck`/scoped Prettier；`git diff --check` | 全部通过；Backend Spotless 因镜像仓库无法解析未缓存插件依赖而未执行，离线与 7892 代理两次尝试均保留失败证据 |

### Phase 3 验收

- [x] KC、正式终态提交、错误模式、负荷、挫败、偏好与近期已消费资源由服务端权威聚合并进入 Intent/Orchestrator/Graph；浏览器观察事件不能污染路由。
- [x] 同一复合请求在无失败历史时为 PARALLEL/STANDARD；两次真实失败后为 SEQUENTIAL/SCAFFOLD_FIRST/max parallel 1，明确工具集合保持不变。
- [x] ResourceGraph/PathPlanner 使用真实画像与学习事实，移除固定 mastery/空 profile 占位。
- [x] 路由只写 observation，不越权修改 mastery；既有正式学习领域事件继续负责画像/KC 闭环。
- [x] 前端展示可读个性化原因且不泄露原始内部信号。
- [x] Graph、Frontend、RAG 全量通过；Backend 0 assertion failures、6 个容器错误与逐类 18/18 绿灯证据均如实保留；真实 Chromium 新场景 3/3、完整套件 4/4；独立复审 READY。

### Phase 3 剩余风险

- Backend 单次全量仍受当前 WSL/Docker 的 Testcontainers `pgvector:pg16` 启动竞争影响；930 tests 为 0 failures/6 errors，6 个错误类逐一 18/18 通过，但不能写成单次 Maven 全绿。
- Backend Spotless 插件依赖未在本机缓存，离线与经 7892 联网两次都无法从当前 Maven 镜像读取 artifact descriptor；Java 编译/测试与 `git diff --check` 已通过，但本阶段不能宣称 Spotless gate 已执行。
- 学习上下文聚合依赖多个已有存储，局部源故障会减少个性化信号；路由 observation 和服务日志可追踪，但当前前端不展示“某信号源暂不可用”。
- 负荷/挫败规则是可审计的确定性阈值，不是临床或心理判断；只能用于教学节奏与脚手架，不应被解释为学生能力定性。
- 本阶段没有实现搜索候选、A*、失败恢复指标或一次重规划；这些仍严格属于 Phase 4。

## Phase 4 · 有界 ToT + A*、恢复与指标 — 进行中

### Phase 开始宣誓

- 以瞎猜接口为耻，以认真查询为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以臆想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

背诵时间：2026-07-26 12:15 CST，Phase 4 现有恢复、重规划与指标接口审计前。

### 本阶段冻结设计

- 已批准设计与逐任务实施计划：`docs/plans/2026-07-26-baixiao-workshop-phase4-search-design.md`。
- 只在明确包含三个学习动作与顺序规划诉求的 C3 请求中启用；C0-C2 不携带搜索合同。
- 候选只能重排 RouteDecision 已授权的三个工具/Agent，不允许搜索阶段扩大权限；公开候选只含路径与 `g+h+penalty` 数值，不保存模型隐藏思维。
- C3 失败时最多切换一次下一候选，已成功步骤不重复；C2 原有同一步有界重试保持不变。
- 指标复用现有 `/agents/harness/stats`，不创建平行观测端点；线上只记录可观测量，准确率继续由离线真值集计算。

### Task 1 · C3 搜索合同 — 完成

| 时间 | 证据 | 结果 |
|---|---|---|
| 2026-07-26 | RED：先运行 `test_workshop_c3_search.py + test_workshop_route_contract.py` | collection 失败：`ImportError: cannot import name 'RouteSearch'`，证明测试先于合同 |
| 2026-07-26 | GREEN：同一目标 + broader `test_workshop_*.py` + scoped Ruff | 初版 16 passed / broader 36 passed；Ruff 通过 |
| 2026-07-26 | 双阶段独立审查 | 规格复核通过；质量复核发现越界测试同时破坏三个字段，Task 暂不关闭 |
| 2026-07-26 | 审查修复后目标复验 | 38 passed；Ruff 通过；路径长度、三个独立深度边界、负数/NaN/Inf 均有独立拒绝证据 |
| 2026-07-26 | 质量复审 | **Approved**：无 Critical/Important/Minor；跨字段策略校验按边界进入 Task 2 |

- 新增可选 `RouteSearch`、恰好三条 `RouteSearchCandidate`、公开 Agent/Tool 路径、有限数值成本、固定评分公式和一次候选切换策略。
- C0-C2 默认 `search=None`；Task 1 只冻结 Pydantic 形状，未提前实现候选编译或跨字段策略。

### 已批准设计与实施任务

- 详细设计与 TDD 任务表：`docs/plans/2026-07-26-baixiao-workshop-phase4-search-design.md`。
- 采用现有 route compiler 后的确定性 C3 搜索层；不把候选搜索塞进 LLM Router，也不新建第二套 Supervisor。
- 候选只能重排 RouteDecision 已授权的三个工具/Agent，公开路径与 `g+h+penalty` 数值，不保存或展示隐藏 chain-of-thought。
- C3 最多切换一次候选；C2 既有 `PLANNER_EXECUTOR` 同一步重试语义不变；全程禁止自由 ReAct。

### Task 1 · C3 搜索合同 — 完成

| 时间 | 证据 | 结果 |
|---|---|---|
| 2026-07-26 | 先写 `test_workshop_c3_search.py` 后运行 C3 + route contract | RED：collection 失败，`ImportError: cannot import name 'RouteSearch'` |
| 2026-07-26 | 最小 Pydantic 合同 + 审查修复后定向联跑 | GREEN：C3 + route contract + policy gate 49 passed；全部相关边界测试 58 passed；scoped Ruff/diff check 通过 |

- 新增可选 `RouteSearch`、恰好三条 `RouteSearchCandidate`、深度/路径长度 3 上界、公开 Agent/工具路径、四项有限非负数值成本、固定 A* 公式与一次候选切换策略。
- C0-C2 默认 `search=None`；C3-only、授权集合、总分等式和 selected 最优等跨字段规则留给 Task 2 的 policy gate。
- 第一次代码质量复核以“一个测试同时让 agent path、tool path、depth 三项越界，会产生假绿”为 Important 阻断；修复为逐项独立越界，并补空/超长路径元素、负数、NaN/Inf 测试。
- 规格复核与代码质量复审最终均 **APPROVED**，无 Critical/Important/Minor 遗留。

### Task 2 · C3 判定、候选编译与策略门 — 完成

| 时间 | 证据 | 结果 |
|---|---|---|
| 2026-07-26 | 典型三动作、否定/描述/历史引用、候选扩权、成本分解与策略门 RED/GREEN | C3/Policy/Route/Intent HTTP 最终 **310 passed** |
| 2026-07-26 | 相邻编排联跑 | `test_orchestrator_c3_replan.py + test_orchestrator_compound_task_plan.py` **31 passed** |
| 2026-07-26 | 非 Orchestrator workshop/route 回归 | **522 passed / 1090 deselected**；Task 3 草稿单独验收，不混入 Task 2 结论 |
| 2026-07-26 | scoped Ruff + `git diff --check` | 通过 |
| 2026-07-26 | 独立规格复核三轮 | 初审发现诊断门、四来源低置信与 probe 成本偏差；逐项修复后 **SPEC COMPLIANT**，独立准入样例 8 passed |
| 2026-07-26 | 独立质量复核四轮 | 先后阻断安全策略/当前请求绑定、低置信 C2 夹带、元询问/关系从句/撤销误判；最终 **APPROVED**，独立选择性测试 34 passed，无 Critical/Important/Minor |

- 只有 `HANDOUT + PRACTICE + PLAN` 三个当前明确请求、顺序诉求及消息/权威上下文中的真实错误或困难信号同时存在才补为 C3；“没有困难/不困难”、第三方描述、历史引用、元询问、关系从句和撤销均 fail-closed。
- 固定三候选为规划诊断、知识重构和练习探测优先；`g=Σ(base×(1+0.1×index))`，`h/penalty` 只使用冻结的权威画像信号。标准上下文选择练习探测优先，高负荷且近期错误选择规划诊断优先。
- 三候选只能重排 `HANDOUT/PRACTICE/PLAN` 既有权限，Agent 全部由 `ToolRegistry` 映射；策略门强制三模板、Agent/Tool、深度、预算、稳定最低分、完整 forbidden 补集、grounding/safety/evidence 和禁止无证据常识。
- C3 校验必须带当前请求；进入 Orchestrator 的决策会用当前消息与权威 learner context 重算完整 search，消息不匹配或伪造成本/selection 在任何 SSE/工具副作用前被拒绝。跨服务来源完整性继续复用 Java `RouteExecutionTokenSigner`，不新造第二套签名协议。
- 低置信 C2/C3 在 composer/slash/launcher/contextual suggestion 四种来源均先澄清；Phase 1 的确定性单工具入口保持兼容。
- 本 Task 关闭候选判定、评分和策略门；TaskPlan 候选消费、失败后切换一次及运行指标仍分别属于 Task 3/4。

### Task 3 · TaskPlan 消费候选与一次真实候选切换 — 完成

| 时间 | 证据 | 结果 |
|---|---|---|
| 2026-07-26 15:3x | 继承草稿 RED：`test_orchestrator_c3_replan.py` 4 failed、`test_orchestrator_compound_task_plan.py` 5 failed | 事件前预检、权威计划、时间线 trace、幂等键披露、预算继承均未实现 |
| 2026-07-26 15:4x | 实现后定向 | 两文件 **31 passed**；相邻 workshop/route 定向 **310 passed** |
| 2026-07-26 15:5x | 审查前 Graph 全量基线 | **1611 passed / 1 skipped**；11 warnings（既有） |
| 2026-07-26 16:3x | 独立规格/质量初审 | 两路均发现同一 Important：恢复计划已有 `done` 前缀时，后续失败触发候选切换会遗漏旧成功集合并重复派发 |
| 2026-07-26 16:4x | 精确恢复 RED/GREEN + 状态机补强 | 新测试先复现 `PRACTICE` 重跑，修复后成功工具不再进入 calls；PENDING/COMPLETED/PARTIAL/FAILED 与 step 状态绑定 |
| 2026-07-26 16:4x | 最终 Orchestrator/TaskPlan 定向 | **114 passed**；scoped Ruff + `git diff --check` 通过 |
| 2026-07-26 16:4x | 独立规格/质量复审 | **SPEC COMPLIANT / APPROVED**；独立 4 passed / 39 passed，无 Critical/Important/Minor |

- `_node_dispatch` 在任何 SSE 事件与副作用之前强制 RouteDecision 存在并 `validate_task_plan`；签名 TaskPlan 是唯一执行事实源，state 中被伪造的 plan 不再进入 plan/loop_plan 事件。
- C3 失败恢复复用 `switch_task_plan_candidate_once`：成功步骤保留且只调用一次，剩余步骤按次优候选重排，`replans_used` 从传入计划继承并硬限 1；切换后失败步骤按新候选 Agent 重试一次，第二次失败返回 PARTIAL 并阻断依赖（被阻断步骤发 `loop_step failed / dependency_failed`，不再派发）。C2 同一步重试合同不变。
- 候选切换从全量 step status 收集成功前缀，而不是只看本轮 `results_by_id`；因此恢复 TaskPlan 已标记 `done` 的步骤在后续首次切换时仍不会重跑。切换纯函数把成功前缀写回 `done`，dispatch 恢复入口直接纳入 completed。
- 当前生产 `/runs/{id}/replay` 是只读审计 ReplayPlan，不会把旧 TaskPlan 重新送回 Graph；ResourceGraph 只返回内存 draft，真正耐久化边界是 Java recorder 收到 `task_step_result`。若未来增加可执行跨进程 resume，必须同时恢复依赖 artifact 摘要，并把幂等键下沉到资源发布/写库边界；此未来边界不冒充当前已实现能力。
- trace 改为真实时间线：dispatch/evaluate/replan/阻断标记按发生顺序保留每次尝试；`task_step_result` 事件补 `idempotency_key`；候选切换后重发 plan、loop_plan 与 STATE_DELTA；终态把每步 status 回写进 TaskPlan（`StepStatus` 增补 `failed`，Java recorder 按字符串处理不受影响）。
- 草稿测试修正一：`test_c3_failure_switches_candidate_once_and_second_failure_is_partial` 原期望第二次失败后仍派发依赖步骤且状态 `[done, done, failed]`，与冻结设计"第二次失败返回部分结果并阻塞依赖"及已批准的线性依赖纯函数合同直接矛盾；修正为 `[done, failed, skipped]`，第三步只保留未派发的阻断 trace。
- 草稿测试修正二：`test_c3_second_failure_blocks_candidate_dependents_without_dispatch` 在仅 weak-kc 上下文硬编码期望切换到知识重构优先，但冻结成本模型下练习探测优先（2.77）低于知识重构（2.78）；按设计"高负荷/高挫败惩罚练习探测优先"改用 `weak_kcs + cognitive_load=HIGH` 上下文（plan 2.76 < rebuild 2.93 < practice 3.52），期望与冻结模型一致且仍覆盖"第二次失败阻断依赖"场景。
- 草稿测试修正三：`loop_step` 事件载荷是前端 `loopSlice` 的 camelCase 合同（`toolName`），草稿误读 `tool_name` 会对所有事件 KeyError；测试改用 `.get("toolName")`。

### Task 5 · Java/TS 搜索合同透传与可理解 UI — 完成

> 执行顺序说明：另一活跃会话正在修改 Task 4 相邻的 graph 路由文件，为避免并发冲突，先交付与其零交集的 Task 5；Task 4 顺延，不改变两者验收标准。

| 时间 | 证据 | 结果 |
|---|---|---|
| 2026-07-26 16:0x | Java RED：`mvn -o -Dtest=IntentGraphClientTest,RouteExecutionTokenSignerTest test` | testCompile 失败：`search()` 与 `Search/SearchCandidate/ReplanPolicy` 记录不存在 |
| 2026-07-26 16:0x | Java GREEN：Intent+Signer+Service+Controller+Orchestrator 定向 | **31 tests，0 failures**（含 C1 `search=null` 断言与既有套件） |
| 2026-07-26 16:0x | Frontend RED：`WorkshopChat.spec.ts` 新 C3 计划卡用例 | 1 failed / 44 passed：`plan-search` 元素不存在 |
| 2026-07-26 16:0x | Frontend GREEN + 类型 | WorkshopChat/Uploads/toolRouting **52 passed**；`vue-tsc --noEmit` 通过 |

- `WorkshopRouteDecision` 新增 `Search/SearchCandidate/ReplanPolicy` 记录（`@JsonAlias` snake→camel 透明代理），旧 8/9 参构造器保持兼容；C0-C2 与旧 Graph 响应 `search` 为 null。
- 执行令牌摘要经 `objectMapper.valueToTree(decision)` 自动覆盖 search；新增测试证明篡改 A* 选中项或剥离 search 合同都会使已签发令牌验签失败，跨服务来源完整性不新造第二套协议。
- 前端 `intent.ts` 新增 `WorkshopRouteSearch/WorkshopRouteSearchCandidate` 类型；计划卡新增 `data-testid="plan-search"` 摘要「已比较 3 条受限学习路径，优先执行『…』」，只引用公开候选 label 与数量，测试断言不透出候选 id、成本数值或内部推理。

### Task 4 · 运行指标复用现有 harness stats — 完成

| 时间 | 证据 | 结果 |
|---|---|---|
| 2026-07-26 16:1x | RED：先写 `test_workshop_routing_metrics.py` | collection 失败：`ModuleNotFoundError: pythontopia_graph.routing.workshop_metrics` |
| 2026-07-26 16:1x | GREEN：指标定向 + 相邻回归 | metrics 5 passed；intent/orchestrator 相邻 **183 passed** |
| 2026-07-26 16:2x | Graph 全量 | **1624 passed / 1 skipped**（含并行会话最新改动） |
| 2026-07-26 16:2x | scoped Ruff check/format | 通过 |

- 新增 `routing/workshop_metrics.py`：锁保护进程内计数 + 显式 `reset`（与生产单 worker 约束一致）；快照含路由总量/澄清/复杂度分布、C3 三候选与 A* 选中项计数、执行终态、候选切换与同步重试、派发与阻断步数、近窗（512 样本）nearest-rank P50/P95 延迟及澄清率/C3 率/PARTIAL 率/切换率。
- 记录点：`IntentRouterAgent.route` 唯一返回点记录已编译决策与路由延迟；`_node_dispatch` 仅对签名 TaskPlan 执行记录终态（派发数=真实尝试次数、阻断步不计派发）。只记可观测量，不落学生原始输入；Intent Accuracy 等真值指标仍由离线 golden 评测负责。
- `GET /agents/harness/stats` 在既有 `tools/breakers` 载荷上新增兄弟字段 `routing`，不创建平行端点；HTTP 测试证明既有字段原样在线。

### Task 6 + Final · 全量回归、真实浏览器与文档 — 完成

| 时间 | 范围 | 命令/证据 | 结果 |
|---|---|---|---|
| 2026-07-26 16:20 | Backend 全量首跑 | `mvn -s ~/.m2/settings.xml -o test` | **933 tests，0 failures，6 errors**；全部为 pgvector Testcontainers 启动竞争 |
| 2026-07-26 16:2x | Backend 失败类合并复跑 | 6 类 `-Dtest=...` | **18 tests，0 failures/errors；BUILD SUCCESS** |
| 2026-07-26 16:1x | Frontend 全量 | `npm run test -- --run`；`typecheck`；`lint`；scoped Prettier | **231 files / 2264 tests 通过**；typecheck 通过；lint 0 errors/112 既有 warnings；Prettier 通过 |
| 2026-07-26 16:1x | RAG 全量 | `.venv/bin/python -m pytest -q` | **127 passed, 2 skipped** |
| 2026-07-26 17:0x | Graph 全量（终验，含并行会话最新改动与审查修复） | `.venv/bin/python -m pytest -q` | **1627 passed, 1 skipped**；11 warnings（既有） |
| 2026-07-26 16:4x | 真实全栈 | `./dev.sh --down` 后重启；清理一个僵死 4h 的旧 Vite（TCP 可连但 HTTP 永不响应并占住 5173） | 四端 `/healthz` 与 Vite 200 全部就绪，graph 容器 bind mount 重启加载新代码 |
| 2026-07-26 16:5x | C3 专项 Chromium 首跑 | `PYT_E2E_REGISTER=1 npx playwright test ... --grep "典型 C3" --retries=0` | 首跑暴露真实有界恢复：HANDOUT 首次生成被 semantic grounding 拒绝（证据未提及"1秒"）→ 一次候选切换重派 → 完成；原断言只认快乐路径 3 次派发，按冻结设计"最多一次恢复"修正为 3-4 次且终态全成功 |
| 2026-07-26 16:5x | C3 专项复跑 | 同命令 | **1 passed（2.8m）**：C3 合同、三候选、A* 最低分选中、practice-first 步序、plan-search UI、签名令牌、运行时记录全部真实验证 |
| 2026-07-26 17:0x | 完整百晓套件 | `PYT_E2E_REGISTER=1 npx playwright test e2e/baixiaoAgentRouting.spec.ts --project=chromium --workers=1 --retries=0` | **5 passed（3.9m）**：Debug、顺序资源、C3 三候选执行、个性化串行、诊断补救 |
| 2026-07-26 16:5x | 真实运行指标 | `GET :18091/agents/harness/stats`（X-Internal-Token） | `routing`: 2 条 C3 路由（均选 practice-first）、2 次执行 COMPLETED、**1 次真实候选切换**、7 次派发、P50 138s/P95 170s |
| 2026-07-26 17:0x | 静态门禁 | `git diff --check`；scoped Ruff/Prettier | 通过 |

- 首跑 C3 E2E 的"失败"实为生产级证据：真实 LLM 生成被 grounding 审查拒绝后，系统按 Task 3 合同自动执行一次候选切换并完成任务——R13 的工具/审查失败恢复在真实栈自然发生并被运行时记录与指标捕获。
- 独立代码审查（含只读探针复验两条未覆盖路径）结论 **READY**：无 Critical/Important；8 个 Minor 中 4 项当场修复（恒真延迟断言、HTTP 测试 provider 泄漏、c3_replan 指标隔离 fixture、`sameStepRetries` 正向覆盖），其余 4 项记录如下。

### Phase 4 验收

- [x] 仅 C3 编译恰好三条受权候选（深度≤3），`f=g+h+penalty` 确定性选最低分；C0-C2 `search=None` 且行为不变。
- [x] TaskPlan 消费选中候选；执行失败最多一次候选切换，成功步骤不重复执行，第二次失败 PARTIAL 并阻断依赖；C2 同步重试合同保留。
- [x] 搜索合同经 Java DTO 签名透传与前端类型贯通；计划卡只展示候选数量与选中 label，不泄露候选 id/成本数值/隐藏推理。
- [x] 运行指标复用 `/agents/harness/stats`：路由/复杂度/三候选选中/执行终态/切换/派发/阻断/P50/P95 全部来自真实记录；离线真值指标不伪造。
- [x] 四端全量 + 真实 `./dev.sh` 栈 + 专项 Chromium `--retries=0` 5/5 + 独立审查 READY；CHANGELOG 与本日志同步。

### Phase 4 剩余风险（含审查 Minor 记录）

- Backend 单次全量仍受 WSL/Docker pgvector Testcontainers 启动竞争影响（0 failures/6 errors，6 类复跑 18/18 全绿）；不能写成单次 Maven 全绿。
- 恢复语义信任 TaskPlan 步骤 `status=="done"` 作为不重派边界：签名无法证明运行时状态，公共 HTTP 路径总是重新编译全 pending 计划，风险仅存在于可直接写进程状态/checkpoint 的攻击面（审查 Minor，已知取舍）。
- `_node_dispatch` 现约 400 行、候选切换需手工同步重建六份运行时集合；当前由测试+审查探针验证正确，建议后续把"切换后状态重建"提为纯函数（审查 Minor，收尾期不夹带编排核心重构）。**已收口（2026-07-26 后续会话）**：`rebuild_runtime_after_switch` 纯函数 + `SwitchedRuntimeState` 契约测试落地，dispatch 改为整体重建（陈旧候选步骤键不再残留），Graph 全量 1628 passed / 1 skipped。
- 执行期对 learner_context 严格重算相等：route 与执行窗口内学习事实变化（如异步判题落库）会 fail-closed 整轮失败，可重试拿新路由；这是防伪造的有意取舍（审查 Minor）。
- C3 E2E 的 practice-first 选中断言依赖 `PYT_E2E_REGISTER=1` 全新学生；复用账号时历史事实会合法翻转 A* 选择（已在测试内注释）。
- 指标为进程内计数（生产单 worker 约束一致），进程重启归零；跨实例聚合需外部采集。

### 并发会话协调备注

- 2026-07-26 15:4x 起检测到另一活跃 Claude 会话（`claude --dangerously-skip-permissions`，PID 475727）持续修改 `workshop_policy.py`、`test_workshop_c3_search.py`、`test_workshop_policy_gate.py`（Task 2 边界打磨），并在本会话 Task 3 状态回写之上追加了持久化恢复语义（done 步骤不重派）与 TaskPlan 状态一致性校验。两侧改动互补，终验 Graph 全量 1627 passed 为合并状态。
- 后续任何会话开工前请先阅读本日志：Phase 4 Task 1-6 均已完成，勿重复实现。

## 最终需求矩阵

| ID | Requirement | Existing Interface Reused | Implementation | Targeted | Full | E2E | Status / Risk |
|---|---|---|---|---|---|---|---|
| R01 | L0/L1 统一真实上下文入口 | Intent request + verified submission | interaction/requestedIntent + backend learning context hydration | YES | YES | YES | P3 PROVEN |
| R02 | L2 确定性权限、安全、预算门 | ToolRegistry / Spring ownership | deterministic policy + fail closed | YES | YES | YES | P1 CORE PROVEN |
| R03 | 八类任务 + MULTI_TASK/CHAT/UNKNOWN | IntentRouterAgent | stable task family contract + compound route | YES | YES | YES | P2 CONTRACT PROVEN；P3 personalized routing proven |
| R04 | C0-C3 + 五类 topology | OrchestratorGraph | deterministic topology compiler + TaskPlan | YES | YES | YES | P2 PROVEN |
| R05 | Agent 选择、低置信澄清 | IntentRouterAgent | compiler + 0.55 + exact clarification | YES | YES | YES | P1 PROVEN |
| R06 | P1 五类 MVP 业务 | existing specialist Agents | deterministic mappings + real Debug | YES | YES | YES | P1 PROVEN；browser deep proof for Debug |
| R07 | P2 多 Agent 拓扑 | OrchestratorGraph/dispatch | sequential/parallel/planner-executor | YES | YES | YES | P2 PROVEN |
| R08 | RouteDecision/TaskPlan/持久化恢复 | agent_loop_run/runtime artifacts | signed route + DAG + recorder/replay | YES | YES | YES | P2 PROVEN；recorder DB failure remains fail-soft |
| R09 | 工具 allowlist/校验/副作用/预算 | ToolRegistry | metadata + validator + runtime hard budgets | YES | YES | YES | P2 PROVEN |
| R10 | Schema→Grounding→Safety→Repair→Fallback | GuardPipeline / existing critics | registry schema + publication fail closed | YES | YES | YES | P1 grounding/safety proven；repair chain continues P2 |
| R11 | P3 个性化差异与解释 | Profile/Memory/Mastery/submission/resource events | authoritative learner context + deterministic strategy + UI explanation | YES | YES | YES | P3 PROVEN |
| R12 | P4 C3 三候选/A*/一次重规划 | RouteDecision / ToolRegistry / OrchestratorGraph | deterministic 3-candidate compiler + public A* cost + runtime one-switch dispatch + signed Java/TS contract + plan-search UI | YES | YES | YES | P4 PROVEN：单元/合同/编排/Java/TS/真实 Chromium（含生产级一次真实切换） |
| R13 | 低置信、空 RAG、工具/模型/Critic 失败恢复 | fallback contract + GuardPipeline + C3 switch | 低置信澄清（P1）、无证据显式披露（P1）、C3 审查失败→一次候选切换→PARTIAL/阻断（P4）；模型失败回退沿用既有 provider fallback | YES | YES | YES | P4 PARTIALLY PROVEN：真实 E2E 自然出现 grounding 拒绝→切换→完成；空 RAG/模型失败路径沿用 P1 既有证据 |
| R14 | 画像/KC/记忆/推荐/路径/教师分析闭环 | existing backend loop | authoritative read context + observation-only route writeback + real Resource/Path dispatch | YES | YES | YES | P3 ROUTING LOOP PROVEN；teacher analytics unchanged |
| R15 | 路由/工具/输出/系统/教学指标 | /agents/harness/stats + 离线 golden eval | routing 运行快照（路由/复杂度/三候选/执行/切换/派发/阻断/P50/P95）；准确率类指标继续离线真值计算 | YES | YES | YES | P4 RUNTIME METRICS PROVEN（真实栈快照含 1 次真实切换）；教学效果类指标依赖长期数据，超出本目标 |
| R16 | 每阶段宣誓、RED/GREEN、full/E2E/CHANGELOG | this worklog | Phase 0 + Phase 1 + Phase 2 + Phase 3 + Phase 4/Final | YES | YES | YES | COMPLETE：全部四阶段与 Final 均含宣誓、RED/GREEN、全量、真实 E2E、CHANGELOG 与风险记录 |
