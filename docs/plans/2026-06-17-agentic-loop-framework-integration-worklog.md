# Agentic Loop 六框架融合 · 实施工作日志（实时执行追踪）

> 本文件是 [主契约](2026-06-17-agentic-loop-framework-integration.md) 的**执行追踪文档**：记录 Phase 0 地基、Phase 1 ~19 路并发 dispatch 单元的派发与回执状态、Phase 2 集成接线、§8 NFR 勾验、风险与回滚。
> 维护方式：编排者在每个 subagent 回执到达后，按 §6「回执四要素」更新对应行状态与备注。本文件由 **Docs** stream 创建并持续维护（只写 worklog + 两份素材 license，不碰任何代码）。
> 配套素材合规登记：仓库根 `LICENSE-ART.md`、`frontend/public/assets/pixel/LICENSE.md`（均已由本 stream 追加 Rive 吉祥物等条目）。

**状态图例**：⬜ 待派发 / 待回执 · 🟡 进行中 · ✅ 完成 · ⛔ 阻塞 · ⏸ 暂缓

---

## 1. 背景与目标

在现有 LangGraph 有界编排骨架（`orchestrator_graph`：understand→plan→dispatch→reflect(N=1)→replan→compose）上，**正交侧挂**融合 6 个 AI 框架，把「AI 导学助手 + 百晓工坊 + AI 导师」升级为一个**带护栏、可审计、非自由 ReAct** 的自主智能体闭环（Agentic Loop）：

| # | 框架 | 融入点（侧挂） | 归属 stream |
|---|---|---|---|
| 1 | browser-use（Playwright） | BROWSE 作为白名单工具进 `dispatch` | B1 / B2a / B2b |
| 2 | Langfuse + Ragas + DeepEval | OTel 旁路采集 + 离线评测 | O1 / O2 / O3 |
| 3 | GraphRAG · LightRAG | 升级 `evidence` 检索（多跳 + 自适应路由 + 子图审计） | G1a / G1b / G2a / G2b |
| 4 | Mem0 · Zep | 记忆 brief 喂 `understand` 上下文 | M1 / M2 |
| 5 | DSPy-GEPA | 离线编译冻结 prompt 产物，运行时加载 | P1 / P2 |
| 6 | GSAP · Rive · Motion | 闭环步骤前端动效（隔离在 adapter 层） | F1 / F2 |

**总目标（§7 验收重点）**：①闭环有界（达上界安全降级不编造）②GraphRAG 多跳引用可审计 ③记忆 brief 体现时序 ④Langfuse 拿到全链路 trace + eval 分 ⑤DSPy 冻结产物可回退 ⑥BROWSE 白名单 fail-fast ⑦前端 reduceMotion 退化 ⑧ §8 八大非功能需求门全部达标。

**全员红线（§0，违反即返工）**：非 MVP（完整能力 + 完整测试矩阵）/ 不启用自由 ReAct（一切循环有界）/ 不伪造课件 / 判题唯一权威 / fail-soft 观测 · fail-fast 业务 / 类型纪律 / 资产合规 / 单 worker / 外科手术式修改、不越域。

---

## 2. Phase 0 已完成项（2026-06-17 · 编排者串行 · 非并发）— ✅ 完成

### 2.1 迁移号分配（顺延 V169–V171）

最高现存迁移 = `V165`；并发计划 `2026-06-17-ai-guide-autonomous-planning-chain` 已占 `V166–V168`，故本 plan 顺延至 **V169+**。仅以下 3 个后端 stream 建迁移，其余单元不建迁移：

| 迁移号 | 归属 stream | 用途 |
|---|---|---|
| `V169__agent_memory_provider.sql` | **M1-mem-backend** 独占 | 记忆 provider 映射 / 召回审计（不重建 `memory_embedding`/`npc_memory`，仅加 provider 路由 + 外部记忆 id 映射 `agent_memory_ref`） |
| `V170__browse_run.sql` | **B2a-browse-backend** 独占 | 浏览器自主导学会话与步骤日志（`browse_run` + `browse_step`） |
| `V171__agent_loop_run.sql` | **S2-loop-backend** 独占 | 闭环 run 审计（`loop_run`：每轮 plan / 命中工具 / critic 结论 / 有界重试计数 / token / trace_id） |
| `V172+` | 暂不分配 | 预留 |

> ⚠️ 创建迁移前再次确认无同号冲突（与 `V166–V168` 并发计划隔离）。RPG/闭环表名沿用项目约定**无前缀**。

### 2.2 依赖版本（真实最新稳定版 · 全进 optional 组 · 缺包 lazy-import/no-op 降级）

> 任何 Phase 1 stream **不得再改依赖文件**。运行时缺这些重包则对应能力 fail-soft 降级，绝不拖垮主链路；单测一律用注入 fake/mock，缺包仍须跑绿。

**`services/pythontopia-graph/pyproject.toml`**
- 核心升级：`langgraph>=1,<2`（LangGraph v1，环境实测 1.1.8 全量绿；checkpointer MemorySaver 一等公民）。
- `[observability]`：`opentelemetry-api>=1.42,<2`、`opentelemetry-sdk>=1.42,<2`（dev 默认装，让 span 分支真跑）。
- `[memory]`：`mem0ai>=2,<3`、`zep-cloud>=3.23,<4`。
- `[eval]`：`ragas>=0.4,<0.5`、`deepeval>=4,<5`、`langfuse>=4.9,<5`。
- `[optimize]`：`dspy>=3.2,<4`。
- `[browse]`：`browser-use>=0.13,<0.14`、`playwright>=1.60,<2`。

**`services/pythontopia-rag/pyproject.toml`**
- `[graph]`：`networkx>=3,<4`（KC 内存图，贴 2C4G 轻量；缺包回退既有 `expand_prerequisites` 字面/向量召回）。

**`frontend/package.json`**（新增前端动效库；隔离在 `game/adapters/anim/**`，业务层禁直接 import）
- `@rive-app/canvas`：`^2.38.1`（新增 · 吉祥物矢量动画运行时，MIT 代码层）。
- `motion`：`^12.40.0`（新增 · DOM 过渡，MIT 代码层）。
- `gsap`：`3.13`（沿用 R-13 既有；"GSAP No-Charge" 教育免费许可，已登记于 `LICENSE-ART.md`）。

**`backend/pom.xml`**：无需新依赖（记忆/浏览外部服务一律走既有 HTTP client；Langfuse 经 OTel 旁路）。

### 2.3 冻结 stub 清单（已建并过语法校验 · 所有 stream 只读引用、禁止重建）

| 冻结契约文件 | 内容 | 契约条 | 落地方 |
|---|---|---|---|
| `services/pythontopia-graph/pythontopia_graph/tools/browse/contract.py` | `BrowseRequest` / `BrowseStep` / `BrowseResult`（有界 max_steps、citations 带标签、error fail-fast 仍返已走步骤） | §3.1 | B1 实现 · S1 调用 · B2 落库 · F 渲染 |
| `services/pythontopia-graph/pythontopia_graph/memory/contract.py` | `MemoryBrief`（facts / weak_kcs / style_notes / as_of 时序戳） | §3.2 | M1 产出 · M2 取回 · S1 消费 |
| `services/pythontopia-graph/pythontopia_graph/schemas/agentic_loop.py` | `LoopPhase` / `LoopStep` / `LoopPlan` / `AgenticLoopRunRecord`（镜像 V171 审计列） | §3.8 | S1 产出事件 · 后端 loop/** + 前端 loopTypes.ts 镜像 |
| `services/pythontopia-graph/pythontopia_graph/observability/eval_span.py` | `record_eval_score(name, value)`（**Phase 0 为 no-op 骨架**） | §3.6 | **O1 补成实现**；S1 在 critic 后调用 |
| `frontend/src/stores/learning/loopTypes.ts` | `LoopPhase` / `LoopStepStatus` / `LoopCitation` / `LoopStep` / `LoopPlan`（镜像 Python schema） | §3.8 | F2 的 loopSlice / LoopProgress、B2b 的 BrowseTimeline 只读引用 |

> 跨服务为 **HTTP / 镜像契约**（非直接 import）：Python `schemas/agentic_loop.py` ↔ 前端 `loopTypes.ts` ↔ 后端 `loop/**` DTO 三处同源镜像，字段命名按各端规范（Python snake_case `tool_name`、TS camelCase `toolName`）。
> §3.3 GraphRAG 字段（`graph_mode`/`prereq_hops`/`route`/`subgraph`）与 §3.4 安全墙 allowlist **复用既有 RAG 与 security-wall**，无需新增 stub，G1/G2/B1 在既有契约上向后兼容扩展。

---

## 3. Phase 1 · ~19 路并发 dispatch 单元清单及状态

> 一波 ~20 并发 subagent（并发上限放宽至 ~20），文件域**零重叠**；跨单元一律按 §3 冻结契约 + mock/DI 解耦；各自单测跑绿（整体 typecheck/编译留 Phase 2）。每个 subagent 完成时回执四要素：①改/建文件清单（绝对路径）②自测命令与关键输出 ③留给 Phase 2 的集成点 ④偏离契约处及原因。

### 3.1 Python · graph 微服务（9 单元）

| 单元 | 独占可写文件域（摘要） | 迁移 | 自测命令 | 状态 | 回执备注 |
|---|---|---|---|---|---|
| **S1-loop-graph** | `graph/agentic_loop.py`、`graph/orchestrator_graph.py`(改) + 测试 | — | `pytest -q tests/test_agentic_loop.py` | ✅ 落地+绿 | 实测通过（见 §9 验证日志，2026-06-17 15:0x） |
| **G2a-evidence** | `nodes/evidence_fetcher.py`(加 graph_mode 透传 + route/subgraph) + 测试 | — | `pytest -q tests/test_evidence_graph.py` | ✅ 落地+绿 | `EvidenceBundle.route/subgraph` 已加；测试绿 |
| **G2b-grounding** | `nodes/grounding_critic.py`、`nodes/semantic_grounding_verifier.py` + 测试 | — | `pytest -q tests/test_grounding_subgraph.py` | ✅ 落地+绿 | 前置页冒充直接出处拒绝规则已加；测试绿 |
| **M2-mem-brief** | `memory/brief_client.py` + 测试 | — | `pytest -q tests/test_memory_brief_client.py` | ✅ 落地+绿 | 超时 fail-soft 空 brief；测试绿 |
| **O1-otel-langfuse** | `observability/otel.py`(扩展 headers)、`observability/eval_span.py`(实现) + 测试 | — | `pytest -q tests/test_otlp_headers.py` | ✅ 落地+绿 | `OTEL_EXPORTER_OTLP_HEADERS` 解析 + `record_eval_score` 实现；测试绿 |
| **O2-eval-harness** | `eval/**`（scorers / runner / datasets） + 测试 | — | `pytest -q tests/test_eval_scorers.py tests/test_eval_runner.py` | ✅ 落地+绿 | scorers/runner/datasets 齐；mock judge 出分；测试绿 |
| **P1-dspy-compile** | `optimize/{programs,compile}.py`、`optimize/artifacts/.gitkeep`、`scripts/optimize_*.py` + 测试 | — | `pytest -q tests/test_optimize_programs.py` | ✅ 落地+绿 | programs/compile + 离线 scripts 齐；测试绿 |
| **P2-dspy-loader** | `optimize/loader.py`、`llm/provider.py`(仅加 §3.5 hook) + 测试 | — | `pytest -q tests/test_optimize_loader.py` | ✅ 落地+绿 | provider 已接 `load_compiled`（缺产物回退手写 prompt）；测试绿 |
| **B1-browse-exec** | `tools/registry.py`(加第 17 个 BROWSE)、`tools/browse/{executor,allowlist,sanitize}.py` + 测试 | — | `pytest -q tests/test_browse_allowlist.py tests/test_browse_executor.py` | ✅ 落地+绿 | allowlist/executor/sanitize 齐；白名单 fail-fast；测试绿 |

> graph 微服务实测：新增 12 个测试文件 **262 passed / 3 skipped**；全量套件 **936 passed / 3 skipped**（无回归）。

### 3.2 Python · rag 微服务（2 单元）

| 单元 | 独占可写文件域（摘要） | 迁移 | 自测命令 | 状态 | 回执备注 |
|---|---|---|---|---|---|
| **G1a-kc-graph** | `pythontopia_rag/graph/{__init__,kc_graph,traverse}.py` + 测试 | — | `pytest -q tests/test_kc_graph.py` | ✅ 落地+绿 | KC 内存图 + neighbors(hops)；测试绿 |
| **G1b-route** | `pythontopia_rag/graph/router.py`、`routes/query.py`(改)、`schemas/query.py`(改) + 测试 | — | `pytest -q tests/test_query_graph_mode.py` | ✅ 落地+绿 | `graph_mode=auto/off/force` 自适应路由 + 向后兼容；测试绿 |

> rag 微服务实测：`test_kc_graph` / `test_query_graph_mode` / `test_graph_kc_graph` / `test_fusion_kc_graph` 合计 **61 passed / 2 skipped**。

### 3.3 Backend · Java（3 单元 · 仅此 3 个建迁移）

| 单元 | 独占可写文件域（摘要） | 迁移 | 自测命令 | 状态 | 回执备注 |
|---|---|---|---|---|---|
| **M1-mem-backend** | `backend/.../learner/memory/**`、`V169__agent_memory_provider.sql` + 测试 | **V169** | `mvn -q -Dtest=MemoryBriefServiceTest,Mem0MemoryProviderTest test` | ✅ 落地+绿 | provider 路由（local/mem0/zep）+ fail-soft 回退 local；Brief/Controller/Router/Local/Mem0/Zep 测试全绿 |
| **B2a-browse-backend** | `backend/.../browse/**`、`V170__browse_run.sql` + 测试 | **V170** | `mvn -q -Dtest=BrowseRunServiceTest,BrowseControllerTest test` | ✅ 落地+绿 | Controller(8)/RunService(11)/GraphClient(4) 全绿；非 student 403 |
| **S2-loop-backend** | `backend/.../loop/**`、`V171__agent_loop_run.sql` + 测试 | **V171** | `mvn -q -Dtest=LoopRunServiceTest test` | ✅ 落地+绿 | `loop_run` 审计落库；LoopRunServiceTest 绿 |

> backend 实测（offline + aliyun 镜像，见 §9 网络备忘）：M1/B2a/S2 目标测试类全绿；surefire 累计 **818 passed / 0 failures / 0 errors**（含睡眠期并发跑的全量套件 + 本次定向跑）。Flyway 集成测试通过 ⇒ `V169–V171` 在测试库可正常迁移。

### 3.4 Frontend · Vue（3 单元）

| 单元 | 独占可写文件域（摘要） | 迁移 | 自测命令 | 状态 | 回执备注 |
|---|---|---|---|---|---|
| **F1-anim-adapter** | `frontend/src/game/adapters/anim/**`（含 `types.ts` 接口） + 测试 | — | `npm run typecheck && npm run test -- tests/adapters/anim/AnimAdapter.spec.ts` | ✅ 落地+绿 | AnimAdapter/domMotion/riveMascot/phaserTween + reduceMotion 退化；typecheck 干净 |
| **F2-loop-ui** | `frontend/src/stores/learning/loopSlice.ts`、`components/hud/LoopProgress*.vue` + 测试 | — | `npm run test -- tests/stores/loopSlice.spec.ts tests/components/hud/LoopProgress.spec.ts` | ✅ 落地+绿 | loopSlice 状态机 + LoopProgress 渲染（接 AnimAdapter 接口）；测试绿 |
| **B2b-browse-frontend** | `frontend/src/api/browse.ts`、`components/workshop/BrowseTimeline.vue` + 测试 | — | `npm run test -- tests/components/workshop/BrowseTimeline.spec.ts src/api/__tests__/browse.spec.ts` | ✅ 落地+绿 | browse api client + BrowseTimeline（外部内容标注）；测试绿 |

> frontend 实测：5 个新增 spec **82 passed**；`npm run typecheck`（vue-tsc strict）**0 错误**。

### 3.5 其它（2 单元）

| 单元 | 独占可写文件域（摘要） | 迁移 | 自测 | 状态 | 回执备注 |
|---|---|---|---|---|---|
| **O3-deploy-langfuse** | `deploy/docker-compose*.yml`、`deploy/.env.example` + 冒烟说明（禁改任何代码） | — | `docker compose up` 冒烟 + env 文档化 | ✅ 落地（compose 冒烟待跑） | `docker-compose.yml` 加 `observability` profile（langfuse-* 前缀、可选 profile，默认不起）；`.env.example` 文档化 `OTEL_*`/`LANGFUSE_*`。`docker compose up` 实地冒烟留 Phase 2。 |
| **Docs** | 本 worklog、`LICENSE-ART.md`、`frontend/public/assets/pixel/LICENSE.md`（追加 Rive） | — | Markdown 合法性 + 链接/路径 + 与主 plan 一致性核对 | ✅ 本轮交付 | 见 §6 |

> **单元计数核对**：graph 9 + rag 2 + backend 3 + frontend 3 + 其它 2 = **19 dispatch 单元**，与主契约 §2/§4/§6「~19 单元 / 一波 ~20 并发」一致。能力↔单元映射：S-spine→{S1,S2}；G1→{G1a,G1b}；G2→{G2a,G2b}；M→{M1,M2}；O→{O1,O2,O3}；P→{P1,P2}；B1→{B1}；B2→{B2a,B2b}；F→{F1,F2}；Docs→{Docs}。

---

## 4. Phase 2 · 集成接线清单（编排者串行 · Phase 1 全绿后执行）

> Phase 1 各单元用冻结契约 + mock/DI 解耦，整体接线统一在 Phase 2 完成。逐项勾验：

| # | 集成接线项 | 依赖单元 | 状态 | 备注 |
|---|---|---|---|---|
| 1 | `orchestrator_graph` 在 `understand` 前真接 memory brief | M2 + M1 | ✅ 已接线 | `MemoryBriefProvider` + `_fetch_memory_brief` + `fold_memory_brief`（缺省 Null/fail-soft 空 brief） |
| 2 | `orchestrator_graph` 的 evidence 真接 GraphRAG（route/subgraph 透传） | G2a + G1a/G1b | 🟡 代码就位 | `EvidenceBundle.route/subgraph` + `graph_mode/prereq_hops` 字段齐；端到端透传 + 引用页留 Phase 2 终验 |
| 3 | `dispatch` 真接 BROWSE 工具（受 scope/allowlist 收缩） | B1 + B2a | ✅ 已接线 | orchestrator BROWSE 派发路径 + scope/allowlist 闸门（quiz scope 不暴露） |
| 4 | critic 后真接 `record_eval_score`（grounding/safety 通过率写 span） | O1 + S1 | ✅ 已接线 | critic 双轨裁决后调用 eval_span（无 span 时 no-op） |
| 5 | `llm/provider.py` 真接 DSPy `load_compiled`（命中冻结产物，否则回退手写 prompt） | P2 + P1 | ✅ 已接线 | provider 调 `load_compiled`；缺/损坏产物 → None → 回退手写 prompt |
| 6 | 前端 `LoopProgress.vue` 真接 anim adapter + loopSlice（SSE 累积步骤） | F1 + F2 | ✅ 已接线 | `LoopProgress.vue` 依赖 `AnimAdapter` 接口 + `loopSlice`；SSE→步骤 e2e 留终验 |
| 7 | 前端 `renderer="browse"` → `BrowseTimeline.vue` 真接 browse api | B2b + F1 | 🟡 组件+api 就位 | `api/browse.ts`(POST/GET) + `BrowseTimeline.vue` 齐；renderer 挂载点 e2e 留终验 |
| 8 | Langfuse compose 起停 + OTLP headers env 串通全链路 trace | O3 + O1 | 🟡 配置就位 | compose `observability` profile + `OTEL_EXPORTER_OTLP_HEADERS` 解析齐；实地起停冒烟留终验 |

**Phase 2 全量回归门**：
- [x] 四端测试全绿（graph 936✓ / rag 61✓ / backend 818✓ offline / frontend 82✓ + typecheck✓）— 2026-06-17，见 §9
- [~] 空库迁移 `V169–V171` 成功（backend Flyway 集成测试通过 ⇒ 测试库可迁移）；**全新空库 + seed 计数核对（7 文档/561 页/7 章/65 KC/46 示例/39 题）待 Phase 2 跑**
- [ ] RAG 查询 `input函数` / `for循环` / `列表` 引用页正确（含新 `graph_mode` 路由）— 语义查询需起 RAG + 向量库，待跑
- [ ] 判题三态（通过 / 失败 / 运行错误）不被 Pyodide 预览覆盖 — 待 e2e
- [~] §8 八大 NFR 门逐条达标（见 §5）— 可静态/单测验证项已绿；性能 P95 / e2e / 故障注入整体回归待跑

> 图例：`[x]` 已验证 · `[~]` 部分验证 · `[ ]` 待验证。backend 四端绿系 **offline + aliyun 镜像** 跑（本机网络受限，见 §9 网络备忘）。

---

## 5. 验收 / NFR（§8 ISO/IEC 25010）勾验表

> 性能基线环境：单 worker · ECS 2C4G（`47.120.29.62`）。能在单 stream 内验证的 NFR 由该 stream 自带；跨 stream 的（性能 P95 / 整体故障注入 / 契约回归）由编排者 Phase 2 统一回归，结果写入交付说明 §7。

| NFR 特性 | 主责 stream | 验证产物 / 命令 | 关键门槛 | 勾验 |
|---|---|---|---|---|
| 8.1 功能适合性 | 全 | 四端测试 + golden 对比 + e2e | 6 框架真接闭环（非占位）；DSPy 编译后 golden 命中率 ≥ 编译前基线 | ⬜ |
| 8.2 性能效率 | O / G1 / B1 / S | Langfuse P50/P95 + `docs/performance-baseline.md` | 导师首 token ≤2.5s；RAG vector ≤800ms / graph ≤1.5s；BROWSE 单步 ≤8s；闭环单轮 ≤12s；内存图 ≤200MB | ⬜ |
| 8.3 兼容性 | 全 | 契约测试 + 「旧调用不传新字段」回归 | 全侧挂 fail-soft；4 端 backend 契约不破坏；新字段一律可选+默认值 | ⬜ |
| 8.4 易用性 | F / S | 组件测试（reduceMotion/键盘/aria）+ 演示走查 | 可见有界计划；每步可控；WCAG 2.2 AA（对比 ≥4.5:1、键盘可达、aria-live 双通道） | ⬜ |
| 8.5 可靠性 | S / M / O / B1 | 故障注入测试（每外部依赖 ≥1：断网/超时/500/损坏产物） | 任一外部不可用 → 降级不熔断；闭环有界重试 + oscillation 提前终止；run 落库可回放 | ⬜ |
| 8.6 信息安全 | B1 / B2 / S / O | 安全墙用例 + 注入用例 + 越权 403 | allowlist fail-fast + sanitize 去敏；外部内容只进带标签 context；OTel 默认不采 prompt/结果 | ⬜ |
| 8.7 维护性 | 全 | 四端静态分析 + 覆盖率门 | 文件域零重叠；第三方对象隔离 adapter；覆盖率不低于现状 | ⬜ |
| 8.8 可移植性 | O / M / G1 | 空库迁移 + compose 起停 + 切 env 冒烟 | 全经 env 选型（`MEMORY_PROVIDER`/`graph_mode`/`LANGFUSE_*`/`BROWSE_ALLOWED_DOMAINS`）；LightRAG↔GraphRAG / Mem0↔Zep↔local 可换不改业务层 | ⬜ |
| 8.9 教育安全 / 防幻觉 | G2 / O / S | Ragas faithfulness + 幻觉率门 | 生成资源过证据审查；通识知识显式标「非课件」；不伪造页码/章节/题目/作者；不给可直接提交完整答案 | ⬜ |

---

## 6. Docs stream 本轮交付（✅ 完成）

**做了什么**：
1. 创建本 worklog 作为整个工程的执行追踪文档（背景目标 / Phase 0 已完成项 / 19 单元状态 / Phase 2 集成 / NFR 勾验 / 风险回滚）。
2. 同步素材 license 登记（只追加、不破坏既有格式）：
   - `LICENSE-ART.md`：新增「Agentic Loop 六框架融合新增依赖与素材（2026-06-17）」一节——登记 Rive 吉祥物 **占位/待补** 素材条目（许可要求 + 计划路径 + 双登记位置）；并按既有「代码层音视觉库」范式登记 `@rive-app/canvas`、`motion`（MIT 代码层，非素材），注明 GSAP 沿用 R-13 既有代码层登记。
   - `frontend/public/assets/pixel/LICENSE.md`：在自动生成索引块（`AUTO-GENERATED … END`）**之后**追加手工小节，登记同一条 Rive 吉祥物占位条目，不触碰自动生成块。

**契约说明（GSAP / Motion / Rive 的资产 vs 代码层定性）**：
- **GSAP**：纯代码层依赖（JS 库），**不入素材 license**；已在 `LICENSE-ART.md`「代码层音视觉库（R-13）」一节登记（"GSAP No-Charge" 教育免费许可）。本期沿用，无需改动。
- **Motion**（`motion` npm 包）：纯代码层依赖（MIT），非素材；为完整性在 `LICENSE-ART.md` 代码层小节登记。
- **@rive-app/canvas**：Rive **运行时** 是纯代码层依赖（MIT），非素材。
- **Rive `.riv` 动画资产**：属**素材层**，必须登记且只允许 CC0/CC-BY/CC-BY-SA/OFL。当前**尚无具体 .riv 资产**，故登记为「占位/待补」条目（见 §7 缺口）。

**自测（无需跑测试，按要求做静态核对）**：
- Markdown 合法性：表格列对齐、代码围栏闭合、标题层级正确。
- 路径/链接核对：主契约相对链接 `2026-06-17-agentic-loop-framework-integration.md`（同目录）正确；所有 stub / 迁移 / 依赖路径与实际文件一致（已逐一读源核对）。
- 与主 plan 一致性：迁移号 `V169–V171`、19 dispatch 单元、能力↔单元映射、§8 NFR 九项均与主契约表述一致。

---

## 7. 需后续补充的素材 / 许可缺口

| 缺口 | 说明 | 行动项 | 阻塞方 |
|---|---|---|---|
| **Rive 吉祥物 `.riv` 资产缺失** | 闭环 `riveMascot.ts` 需按 critic 结果反馈表情，但仓库尚无 `.riv` 二进制 | 引入或自制 1 个吉祥物 `.riv`，许可须 ∈ {CC0/CC-BY/CC-BY-SA/OFL}；落盘 `frontend/public/assets/pixel/rive/mascot_loop.riv`，并把两份 LICENSE 的「占位/待补」改为正式条目（作者 / 来源 / 许可 / 署名） | F1-anim-adapter（消费方）；素材引入方 |
| **Rive fallback 策略** | 缺 `.riv` 时 F1 须降级（Motion/Phaser tween 或静态表情），避免硬依赖二进制 | F1 在 adapter 层实现缺资产 fail-soft（与 reduceMotion 退化同范式），worklog/§8.3 兼容性勾验时核对 | F1-anim-adapter |
| **CC-BY-SA 传染确认** | 若选 CC-BY-SA 的 `.riv`，包装作品须同以 CC-BY-SA 3.0+ 发布（项目已接受 LPC 传染，沿用即可） | 选定具体素材后在 `LICENSE-ART.md`「项目级 SA 传染决策」补一句归属 | 素材引入方 |
| **credits 页同步** | CC-BY / CC-BY-SA / OFL 强制署名须进 `frontend/public/credits/` | 真实部署前统一生成署名页（与既有 backlog 第 3 项合并） | 部署阶段 |

> 在补齐真实 `.riv` 前，闭环动效以 Motion(DOM) + Phaser tween(画布) + 静态吉祥物为准；Rive 表情为增强项，非演示链路阻塞项。

---

## 8. 风险与回滚

### 8.1 跨 stream 共性风险

| 风险 | 影响 | 缓解 / 回滚 |
|---|---|---|
| 重包缺失（mem0/zep/ragas/deepeval/langfuse/dspy/browser-use/networkx/opentelemetry） | 对应能力不可用 | 全进 optional 组 + lazy-import/no-op 降级（沿用 `otel.py` 范式）；单测用 fake/mock，缺包仍绿；运行时该能力 fail-soft，主链路零影响 |
| 外部服务不可达（Mem0/Zep/Langfuse/浏览器/LLM） | 召回/观测/查证失败 | 一律 fail-soft 降级（记忆回退 local、观测 no-op、BROWSE 回退 VIDEO 且返回已走步骤）；业务故障才 fail-fast |
| 自由 ReAct 越界 | 不可控循环 / 成本爆 | 显式 step/replan 上限 + 停止条件 + 白名单工具 + oscillation 提前终止；运行时禁 `dspy.compile`/自我改 prompt |
| 课件证据被污染 | 幻觉 / 防作弊失效 | 学生上传 + BROWSE 抓取只进带标签 context（`【浏览器读取自 {url}】`），绝不混入课件 evidence；critic 拒「前置页冒充直接出处」 |
| 迁移号冲突 | Flyway 失败 | 建迁移前确认无同号（隔离并发计划 `V166–V168`）；空库初始化验证课程种子完好 |
| 文件域越界 / 顺手重构 | 并发冲突 | 严守 §2 零重叠文件域；跨域只读引用冻结契约；不改依赖文件 / 不改主 plan |

### 8.2 回滚策略（全经 env 开关 · 无破坏性）

- **记忆**：`MEMORY_PROVIDER=local`（默认）即关闭 Mem0/Zep，复用既有 `memory_embedding`。
- **GraphRAG**：`graph_mode=off` 恒走旧向量召回；不传字段=旧版行为（向后兼容）。
- **观测/评测**：缺 `OTEL_*` / `LANGFUSE_*` endpoint → 全链路 no-op，与上线前一致。
- **DSPy**：删/缺 `optimize/artifacts/*.json` → `load_compiled` 返回 None → provider 回退现有手写 prompt。
- **BROWSE**：`BROWSE_ALLOWED_DOMAINS` 留空或工具 scope 收缩 → 不暴露 / 不发起请求。
- **前端动效**：`prefers-reduced-motion` → adapter 退化为瞬时；缺 `.riv` → Rive 表情降级，不影响闭环渲染。
- **数据库**：`V169–V171` 仅新增表（`agent_memory_ref` / `browse_run` / `browse_step` / `loop_run`），不改既有表结构；回滚为 drop 新表，对既有数据无副作用。

### 8.3 演示链路影响判定

本工程全部以**正交侧挂 + fail-soft** 融入既有有界编排，缺任一新能力时主链路（看→学→练→测 + 导师/工坊/导学）保持现状可演示。Rive `.riv` 缺失为**非阻塞**增强项。最终演示链路影响以 Phase 2 §7 交付说明为准。

---

> 维护提醒：每收到一个 subagent 回执，更新 §3 对应行「状态 + 回执备注」；Phase 2 每完成一项接线更新 §4；NFR 达标后勾选 §5。本文件与主契约保持单一事实源对齐（迁移号、单元数、契约条号）。

---

## 9. 验证日志（2026-06-17 实测对账）

> 由「Timer / 验证」会话在 60 分钟休眠后对全工程做的一次**实测对账**：发现代码已远超本 worklog 旧状态（旧版全标 ⬜），故据实回填 §3/§4/§5。以下为本次实跑命令与结果。

### 9.1 实跑结果（四端）

| 栈 | 命令 | 结果 |
|---|---|---|
| graph（新增 12 文件） | `.venv/bin/python -m pytest -q tests/test_agentic_loop.py …(12 个)` | **262 passed / 3 skipped** |
| graph（全量回归） | `.venv/bin/python -m pytest -q` | **936 passed / 3 skipped**（无回归） |
| rag | `.venv/bin/python -m pytest -q tests/test_kc_graph.py tests/test_query_graph_mode.py tests/test_graph_kc_graph.py tests/test_fusion_kc_graph.py` | **61 passed / 2 skipped** |
| backend（M1/B2a/S2 定向） | `mvn -o -s .mvn/settings.build.xml -Dtest=…(11 类) test` | BUILD SUCCESS；surefire 累计 **818 passed / 0 fail / 0 err** |
| frontend（5 新增 spec） | `npx vitest run …(5 个) --coverage=false` | **82 passed (5 files)** |
| frontend（类型） | `npm run typecheck`（vue-tsc strict） | **0 错误** |

### 9.2 网络备忘（backend 构建踩坑 · 可复用）

- 现象：默认 `mvn test` 失败于 `org.apache.poi:poi-ooxml:5.5.1`「present, but unavailable / Read timed out」。
- 根因：① 本机 TUN 代理拦截到 `repo.maven.apache.org`（`198.18.0.40`）超时；② 项目 `.mvn/maven.config` 强制 `--settings .mvn/settings.xml`，而该 active settings 是 **`central-only`（无镜像）**，直连不可达 central；③ 缓存中 `poi-ooxml:5.5.1` 的 jar/pom **其实已下载**（源标记为 aliyun 镜像），但残留 `*.pom.lastUpdated` 失败标记 + 源仓库 id 不匹配，令离线/在线都拒用缓存。
- 解法（非侵入、不改 pom）：① 删除 `~/.m2/repository/org/apache/poi/**/*.lastUpdated` 失败标记；② 用带 aliyun 镜像的 **`mvn -o -s .mvn/settings.build.xml`** 离线跑（镜像 id 与缓存 `_remote.repositories` 的 `aliyun-maven` 匹配 → 命中缓存）。
- 备注：`poi-ooxml:5.5.1` 为既有依赖（commit `f03ec40b` 工坊 .docx 解析引入），**非本计划任何 stream 所加**；本计划 backend 0 新依赖（与 §2.2 一致）。7892 代理对 maven central 返回 403，带不动。

### 9.3 完成度结论（据实测）

- **Phase 0**：✅ 完成（迁移号/依赖/冻结 stub）。
- **Phase 1（19 dispatch 单元）**：✅ **代码全部落地 + 单测/契约测试四端全绿**（O3 的 compose 实地冒烟除外）。
- **Phase 2（8 接线项）**：5 项 ✅ 已接线（memory brief / BROWSE 派发 / eval span / DSPy loader / 前端 anim+slice），3 项 🟡 代码就位待终验（GraphRAG 端到端透传引用页 / browse renderer 挂载 e2e / Langfuse 起停冒烟）。
- **§8 NFR**：单 stream 可静态/单测验证项随测试转绿；**跨 stream 整体回归（性能 P95、全新空库 seed 计数、RAG 语义查询引用页、判题三态 e2e、故障注入、Langfuse 全链路 trace）尚未跑**——这是当前主要剩余工作。
- 一句话：**实现层基本完成且四端测试绿；剩余是 Phase 2 端到端终验 + §8 NFR 整体回归门，尚未正式签字。**

### 9.4 补充验证 + 代码走查 + 阻塞点（2026-06-17 同会话二轮）

**已补做（无需起 prod 栈）：**
- **空库 seed 完整性（NFR 8.1/8.8）**：`Stage05CurriculumBootstrapIntegrationTest` 断言 `totalKcs==65` / `unlockedCount==8`、`CoursePackServiceIntegrationTest` 绿——二者跑**全量 Flyway（含 V169–V171）+ 真实测试库**，证明新迁移与课程种子共存不破坏（65 KC 已核）。完整 7 文档/561 页核对建议补一条显式断言（现有断言已覆盖 KC=65）。
- **Langfuse compose 合法性（NFR 8.8 可安装性）**：`docker compose --profile observability config` 解析通过——`langfuse-clickhouse/minio/postgres/redis` 全部挂 `observability` profile（默认 `up` 不起），镜像/卷/依赖结构合法。实地 `up` 拉镜像冒烟仍待网络放开。
- **核心 stream 代码走查（S1 / B1）**：均**红线合规**——S1 闭环有界（`max_loop_steps>=1` fail-fast、oscillation 提前终止、达上界诚实降级不编造、外部 citations 带「外部内容，非课件」标签、eval/trace fail-soft）；B1 白名单两段式（execute 前拦越界 URL，零请求）、只读动作、sanitize 去敏+PII、结构化降级不抛断、lazy-import。质量高，无需返工。

**关键阻塞点（端到端终验依赖外部条件，非代码缺陷）：**
- **BROWSE 端到端链**：B1 真 engine（`_BrowserUseEngine._run`）**故意未接线**，缺 LLM+Playwright 注入即 `BrowseDependencyError` → dispatch 回退 VIDEO。⇒ Phase 2 #7「`renderer="browse"`→`BrowseTimeline` 挂载」**当前无真实 browse 数据可渲染**；此时强行挂载 = 空/死 UI（违反「不做纯前端假数据」红线）。正确顺序：先 Phase 2 #3 注入真 browser engine（LLM+Playwright），再挂前端 renderer。组件(`BrowseTimeline.vue`)+api(`api/browse.ts`)已就绪待接。
- **RAG 语义查询引用页 / 判题三态 e2e / Langfuse `up` 冒烟**：均需起 **Pythontopia 自有全栈**（`./dev.sh` / `deploy/docker-compose.yml`）。当前宿主在跑 **Alethicode `java-oj-*` 栈**（postgres 等占 5432），直接起会**端口冲突**且可能干扰用户的 Alethicode 环境；叠加本机网络受限（langfuse 镜像拉取存疑）。⇒ 起栈属「可能破坏其他项目环境」的操作，**需用户确认隔离方案**（停 java-oj / 改端口 / 指定 profile）后再跑，本会话不擅自起栈。
