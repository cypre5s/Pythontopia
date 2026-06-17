# Agentic Loop 六框架融合 · 并发构建主契约（2026-06-17）

> **For Claude / Cursor:** 本文件是 **10 个并发 subagent 的唯一权威契约**。每个 agent 启动后**先完整读本文件**，只在自己「文件归属域」内写代码，跨域内容一律按本文件**冻结的 schema/接口**引用，**不得**修改非自己归属的文件。依赖编排（Phase 0 预置）与跨域接线（Phase 2 集成）由编排者完成。执行时请配合 superpowers:executing-plans / subagent-driven-development。

**Goal:** 在现有 LangGraph 编排骨架上，融合 6 个 AI 框架（browser-use / Langfuse+Ragas+DeepEval / GraphRAG·LightRAG / Mem0·Zep / DSPy-GEPA / GSAP·Rive·Motion），把「AI导学助手 + 百晓工坊 + AI导师」升级为一个**带护栏、可审计、非 ReAct** 的自主智能体闭环（Agentic Loop）。

**Architecture:** 闭环复用既有有界编排（`orchestrator_graph`：understand→plan→dispatch→reflect(N=1)→replan→compose）。新增能力以**正交侧挂**方式融入：记忆层喂 `understand` 上下文、GraphRAG 升级 `evidence`、BROWSE 作为白名单工具进 `dispatch`、Langfuse/评分经 OTel 旁路采集、DSPy 离线编译冻结 prompt 产物。运行时**不引入自由 ReAct**；自我改进推到离线。Spring Boot 持久化/权限/证据校验，Vue 只渲染。

**Tech Stack:** FastAPI · LangGraph v1 StateGraph · Pydantic structured outputs · LightRAG/GraphRAG · DSPy(GEPA) · Ragas · DeepEval · Langfuse(OTel-native) · Mem0/Zep · browser-use(Playwright) · Spring Boot 3 / Java 21 · PostgreSQL+pgvector / Flyway · Vue 3 strict · Pinia · Phaser 3 · GSAP / @rive-app/canvas / Motion · Vitest · pytest · JUnit 5。

---

> **Phase 0 状态（2026-06-17 已完成 · 非并发部分）**：迁移号顺延 **V169–V171**；新依赖按**真实最新稳定版**写入 `graph/rag pyproject + frontend package.json`（全进 optional 组、缺包 lazy-import/no-op；backend `pom.xml` 无需新依赖，走既有 HTTP client）；§3 冻结 stub 已建并通过语法校验：`tools/browse/contract.py`、`memory/contract.py`、`observability/eval_span.py`(no-op 骨架)、`schemas/agentic_loop.py`、`frontend/src/stores/learning/loopTypes.ts`。**Phase 1 的 ~19 路并发已派发并落地，四端单测/契约测试实测全绿；Phase 2 接线 5/8 已接、3/8 代码就位待终验**（实测对账见 worklog §9，2026-06-17）。并发上限按用户要求放宽到 **~20**。

## 0. 全员红线（违反即返工）

- **非 MVP**：每个 stream 必须交付**完整能力 + 完整测试矩阵**（单元 + 带 mock 集成 + 失败/边界/降级路径），不允许「占位测试」「happy-path only」「TODO 留白」。
- **不启用自由 ReAct**：所有"循环"必须**有界**（显式 step/retry 上限 + 停止条件 + 白名单工具），越界 fail-fast。自我优化只允许**离线**（DSPy 编译），运行时加载冻结产物，禁止运行时重编译/自我改 prompt。
- **不伪造课件**：不编造 packId=43 的页码/章节/题目/作者。学生上传与浏览器抓取的外部内容只进**带标签 context 通道**（如 `【浏览器读取自 {url}】…`），绝不混入课件 evidence/引用。
- **判题唯一权威**：Pyodide 预览 ≠ 正式判题；掌握度唯一来源仍是服务端 submission 判题。
- **fail-soft 观测 / fail-fast 业务**：观测层（OTel/Langfuse/eval）任何失败一律降级 no-op，绝不拖垮主链路；Agent 业务故障 fail-fast，不静默降级成自由聊天。
- **类型纪律**：前端 TS strict，业务层禁 `any`，Phaser/GSAP/Rive/browser 等第三方动态对象隔离在 adapter 层；Python snake_case 函数变量 / PascalCase 类，Agent 输出结构化（Pydantic）；Java 类 PascalCase、方法变量 camelCase、常量 UPPER_SNAKE_CASE，表/字段 snake_case，新 API 必须 DTO+校验+统一错误+测试。
- **资产合规**：Rive/Lottie 等新素材只用 CC0/CC-BY/CC-BY-SA/OFL；必须同步登记 `LICENSE-ART.md` 与 `frontend/public/assets/pixel/LICENSE.md`。不复制任何商业像素游戏素材。
- **单 worker**：生产/演示默认单 worker，闭环状态走 checkpoint/DB，不依赖进程内可变全局。
- **外科手术**：只改完成目标必须触达的文件，不顺手重构/格式化无关代码。不改非自己归属域。

## 1. 迁移号分配（最高现存 = V165；**并发协调**：另一计划 `2026-06-17-ai-guide-autonomous-planning-chain` 已占 V166–V168，故本 plan 顺延至 V169+；创建前再次确认无同号）

| 迁移号 | 归属 stream | 用途 |
|---|---|---|
| `V169__agent_memory_provider.sql` | **M1-mem-backend** 独占 | 记忆 provider 映射 / 召回审计（不重建已有 `memory_embedding`/`npc_memory`，仅加 provider 路由与外部记忆 id 映射） |
| `V170__browse_run.sql` | **B2a-browse-backend** 独占 | 浏览器自主导学会话与步骤日志（goal/allowlist/步骤/引用 url/状态） |
| `V171__agent_loop_run.sql` | **S2-loop-backend** 独占 | 闭环 run 审计：每轮 plan、命中工具、critic 结论、有界重试计数、token、eval 关联 trace id |
| `V172+` | 暂不分配 | 预留 |

> 仅上述 3 个后端 stream 建迁移，其余 dispatch 单元**不建迁移**。

## 2. 文件归属矩阵（单工作树，互不重叠）

| Stream | 独占可写文件域 | 只读依赖（冻结契约见 §3） | 禁止触碰 |
|---|---|---|---|
> ~19 个 dispatch 单元（一波 ~20 并发 subagent）。按服务分组，文件域**零重叠**；跨单元一律按 §3 冻结契约（Phase 0 已预置）+ mock/DI 解耦。

**Python · graph 微服务**

| Stream | 独占可写文件域 | 只读依赖 | 禁止触碰 |
|---|---|---|---|
| **S1-loop-graph** | `graph/agentic_loop.py`、`graph/orchestrator_graph.py`(改) + 测试 | §3.1 exec_mode、§3.2 `memory/contract.py`、§3.3 EvidenceBundle、§3.6 `eval_span`、`schemas/agentic_loop.py`(Phase0) | registry.py、tools/browse/**、nodes/*、llm/provider.py、eval/** |
| **G2a-evidence** | `nodes/evidence_fetcher.py` + 测试 | §3.3 query 契约 | RAG 微服务、orchestrator_graph.py、grounding_critic.py |
| **G2b-grounding** | `nodes/grounding_critic.py`、`nodes/semantic_grounding_verifier.py` + 测试 | §3.3 subgraph | evidence_fetcher.py、RAG 微服务 |
| **M2-mem-brief** | `memory/brief_client.py` + 测试 | §3.2 `memory/contract.py`(Phase0 只读) | backend、contract.py(只读) |
| **O1-otel-langfuse** | `observability/otel.py`(扩展)、`observability/eval_span.py`(实现) + 测试 | §3.6 | eval/**、agents/*、pyproject |
| **O2-eval-harness** | `eval/**`(scorers/runner/datasets) + 测试 | §3.6、golden(只读) | observability/** |
| **P1-dspy-compile** | `optimize/{programs,compile}.py`、`optimize/artifacts/.gitkeep`、`scripts/optimize_*.py` + 测试 | §3.5、`test_guide_coach_golden.py`(只读) | provider.py、loader.py、agents/* |
| **P2-dspy-loader** | `optimize/loader.py`、`llm/provider.py`(仅加 hook) + 测试 | §3.5 | optimize/programs.py、agents/* |
| **B1-browse-exec** | `tools/registry.py`(加 BROWSE)、`tools/browse/{executor,allowlist,sanitize}.py` + 测试 | §3.1 `contract.py`(Phase0 只读)、§3.4 | orchestrator_graph.py、harness.py |

**Python · rag 微服务**

| Stream | 独占可写文件域 | 只读依赖 | 禁止触碰 |
|---|---|---|---|
| **G1a-kc-graph** | `pythontopia_rag/graph/{__init__,kc_graph,traverse}.py` + 测试 | `V125` 前置种子(只读) | routes/query.py、schemas/query.py、builder.py 既有逻辑 |
| **G1b-route** | `pythontopia_rag/graph/router.py`、`routes/query.py`(改)、`schemas/query.py`(改) + 测试 | 既有 expand_prerequisites、`graph/kc_graph.py`(只读) | graph/kc_graph.py、graph/traverse.py |

**Backend · Java（仅此 3 个建迁移）**

| Stream | 独占可写文件域 | 只读依赖 | 禁止触碰 |
|---|---|---|---|
| **M1-mem-backend** | `backend/.../learner/memory/**`、`V169__agent_memory_provider.sql` + 测试 | §3.2、`PassiveProfile*`/`TutorMemoryBrief`(只读) | profile/mastery 既有核心、npc_memory 既有逻辑 |
| **B2a-browse-backend** | `backend/.../browse/**`、`V170__browse_run.sql` + 测试 | §3.1、§3.4 | tutor/workshop 既有端点 |
| **S2-loop-backend** | `backend/.../loop/**`、`V171__agent_loop_run.sql` + 测试 | `schemas/agentic_loop.py`(镜像) | tutor 既有 |

**Frontend · Vue + Docs**

| Stream | 独占可写文件域 | 只读依赖 | 禁止触碰 |
|---|---|---|---|
| **F1-anim-adapter** | `frontend/src/game/adapters/anim/**`(含 `types.ts` 接口) + 测试 | §3.7 | WorldCanvas/phaserGame 单例、components/** |
| **F2-loop-ui** | `frontend/src/stores/learning/loopSlice.ts`、`components/hud/LoopProgress*.vue` + 测试 | `loopTypes.ts`(Phase0)、§3.8、`anim/types.ts`(只读) | adapters/anim 实现、workshopSlice、package.json |
| **B2b-browse-frontend** | `frontend/src/api/browse.ts`、`components/workshop/BrowseTimeline.vue` + 测试 | `loopTypes.ts`(Phase0)、§3.1 | loopSlice.ts |
| **Docs** | worklog、`LICENSE-ART.md`、`frontend/public/assets/pixel/LICENSE.md`(追加 Rive) | 全仓只读 | 任何代码文件 |

**O3-deploy-langfuse**（可与上表并发）：`deploy/docker-compose*.yml`、`deploy/.env.example` + 冒烟说明；禁止改任何代码。

\* 依赖文件（`graph/rag pyproject.toml`、`frontend/package.json`）已由 **Phase 0 改完**（backend `pom.xml` 无需新依赖）；任何 stream **不得再改依赖文件**。`schemas/agentic_loop.py`、`tools/browse/contract.py`、`memory/contract.py`、`observability/eval_span.py`、`frontend/.../loopTypes.ts` 已由 **Phase 0 预置**，所有 stream **只读引用、不重建**（O1 仅把 `eval_span.py` 的 no-op 骨架补成实现）。

---

## 3. 冻结契约（Phase 0 预置 stub；所有 stream 只读引用，禁止各自重定义）

### 3.1 工具注册表 BROWSE（S/B1/B2/F 共享）

在 `tools/registry.py` 的 16 工具表后**追加第 17 个**（B1 落地），其余 stream 按此引用：

```python
_spec(
    "BROWSE",
    "联网查证",
    "在白名单域内自主浏览官方文档/判题站并回带可引用摘要（外部内容，非课件证据）",
    ("去 python 官网查 sorted 的稳定性", "看看这道题在官方文档怎么说"),
    "orchestrator_sse",          # 复用学习总管流，不新建 SSE 通道
    "navigational",
    "high",
    timeout_s=60.0,
    max_retries=0,               # 浏览器步骤不重试（副作用 + 慢），失败回退 VIDEO
    fallback="VIDEO",
    scopes=("default",),         # 考核 scope 不暴露
    open_world=True,
    destructive=False,
    idempotent=False,
    renderer="browse",           # F 新增 renderer；前端映射 BrowseTimeline.vue
    visibility="default_hidden",
)
```

执行契约（B1 实现，S 调用，B2 落库，F 渲染）：

```python
# services/pythontopia-graph/.../tools/browse/contract.py  (Phase 0 stub)
class BrowseRequest(BaseModel):
    goal: str
    allowed_domains: list[str]          # 仅安全墙 allowlist 交集；空=拒绝
    max_steps: int = 6                  # 有界
    user_id: int

class BrowseStep(BaseModel):
    index: int
    action: Literal["navigate", "read", "search", "stop"]
    url: str = ""
    summary: str                        # 该步可读摘要（不含敏感 DOM）
class BrowseResult(BaseModel):
    steps: list[BrowseStep]
    final_summary: str
    citations: list[str]                # 命中 url，带标签注入 context，不进课件引用
    truncated: bool = False             # 触 max_steps
    error: str = ""                     # fail-fast 时填，steps 仍返回已走部分
```

后端 REST（B2，前缀 `/api`，JWT，`requireStudent`）：
- `POST /api/browse/runs` body `{goal, max_steps?}` → `BrowseRunDto`（服务端注入 allowlist，调 graph，落 `V170`）
- `GET /api/browse/runs/{id}` → `BrowseRunDetailDto{...steps[], citations[]}`

### 3.2 记忆 brief（M 产出，S 消费）

```python
# services/pythontopia-graph/.../memory/contract.py (Phase 0 stub)
class MemoryBrief(BaseModel):
    facts: list[str] = []          # 时序压缩后的稳定事实（来自 Zep 时序图 / Mem0 召回）
    weak_kcs: list[str] = []       # 近期反复受阻 KC
    style_notes: list[str] = []    # 认知风格/偏好提示（来自 8 维画像）
    as_of: str                     # ISO 时间戳；体现"时序"
```
- 注入点：`OrchestratorRequest.context["memory_brief"]`（S 在 `understand` 前读取；缺省空 brief，向后兼容）。
- 后端契约：`GET /api/learner/memory/brief?query=...` → `MemoryBriefDto`（M 落地；内部按 `MEMORY_PROVIDER=local|mem0|zep` 路由，**默认 local 复用既有 memory_embedding**，fail-soft 回退 local）。

### 3.3 GraphRAG 检索（G1 产出，G2 消费）

> **复用既有、不重复造**：项目已上线 `RagQueryRequest.expand_prerequisites`(默认 True) + `RagHit.via_prerequisite` + `RagQueryResponse.retrieval_mode`(`"hybrid"|"vector_only"`) 的前置图召回。G1 在此之上**扩展**：多跳遍历 + 自适应路由 + 子图审计；`retrieval_mode` 枚举**新增 `"graph"`**；`expand_prerequisites` 保留为兼容别名（`graph_mode != "off"` 时等价生效）。同理 `OrchestratorRequest.decaying_kcs`（FSRS/记忆矿脉）已存在，闭环复用、不重建。

`POST /rag/query` 请求**新增可选字段**（G1，向后兼容）：
```jsonc
{ "query": "...", "top_k": 6, "kc_codes": [], "min_score": 0.0,
  "graph_mode": "auto",     // off | auto | force ；auto=简单走向量、多跳走图（自适应路由）
  "prereq_hops": 1 }        // 前置 KC 扩展跳数，上界 2
```
响应 `hits[]` 复用既有 `RagHit`（含已存在的 `via_prerequisite`），**新增**：
```jsonc
{ "hits": [...], "no_evidence_found": false,
  "route": "vector",                                  // vector | graph | hybrid（可审计）
  "subgraph": [ {"from_kc":"KC07","to_kc":"KC12","relation":"prerequisite"} ] }
```
`EvidenceBundle`（G2 扩展，S/critic 只读）新增：`route: str = "vector"`、`subgraph: list[KcEdge] = []`。`grounding_critic` 在有 `subgraph` 时把"前置链路"纳入可审计引用，但**不得**把前置页当作直接证据冒充答案出处。

### 3.4 安全墙 allowlist（B1/B2 共享，复用既有 security-wall）

冻结初始白名单（演示安全）：`docs.python.org`、`docs.python.org/zh-cn`、`peps.python.org`、`pypi.org`（只读），以及本项目自有判题站点 host（由 env `BROWSE_ALLOWED_DOMAINS` 提供，逗号分隔）。allowlist 外的域：executor 直接拒绝并 fail-fast，不发起请求。

### 3.5 DSPy 冻结产物（P 产出，provider 加载）

```python
# services/pythontopia-graph/.../optimize/loader.py (P 落地, fail-soft)
def load_compiled(name: str) -> CompiledPrompt | None: ...
#   name ∈ {"guide_coach_decision", "practice_generate"}
#   读 optimize/artifacts/{name}.json（GEPA compile 导出）；缺文件/损坏 → None
# provider 侧：若 load_compiled 返回非 None，则用冻结 instruction+demos；否则用现有手写 prompt。
# 运行时绝不调用 dspy.compile / GEPA（仅离线 scripts/optimize_*.py 跑）。
```

### 3.6 评测与 Langfuse（O 落地，全局旁路）

- Langfuse 接入：**零业务改动**，经 `observability/otel.py` 既有 OTLP 导出 → 指向 Langfuse `/api/public/otel`。O 扩展 `_maybe_attach_otlp_exporter` 支持 `OTEL_EXPORTER_OTLP_HEADERS`（Basic auth `pk:sk`）。env：`LANGFUSE_HOST`/`LANGFUSE_PUBLIC_KEY`/`LANGFUSE_SECRET_KEY`。
- 离线评测：`eval/scorers.py` 暴露 `score_faithfulness`(Ragas)、`score_answer_relevancy`(DeepEval)、`score_context_recall`(Ragas)；`eval/runner.py` 跑 golden 数据集 → 结果可推 Langfuse score（REST，fail-soft）。
- 在线轻量分：`observability/eval_span.py:record_eval_score(name, value)` 写当前 span 属性 `pythontopia.eval.{name}`（**Phase 0 已预置 no-op 骨架，O1 落地实现**），S1 在 critic 后调用记录 grounding/safety 通过率。

### 3.7 前端渲染（F 落地）

- 闭环步骤事件经**既有** SSE（`orchestrator_sse`）；F 新增 `loopSlice.ts` 累积 `LoopStep{phase, label, status, toolName}` 并由 `LoopProgress.vue` 渲染（GSAP/Motion 过渡）。
- `renderer="browse"` → `BrowseTimeline.vue`（B2 建组件，F 提供动效适配）。
- 动画隔离：`game/adapters/anim/AnimAdapter.ts` 暴露 `playStepIn/playStepDone/celebrate`，内部分别用 Motion(DOM)/Rive(吉祥物)/Phaser tween(画布内)；业务层只调 adapter，禁直接 import gsap/rive。

### 3.8 Agent 输出格式（对齐 Claude Code 网页版；S 产出事件 / F 渲染）

借鉴 Claude Code 网页版 agent 输出的可读结构，按本项目教育场景与红线裁剪（**只展示摘要、不展示原始 CoT**）：

- **可见有界计划**（开头）：「我打算分 N 步陪你处理」+ 每步 checkbox 状态（pending/active/done/failed），呼应既有 `TutorEpisodePlan`；明示"有界 N 步"。
- **工具调用时间线**：每次工具一张可折叠卡（图标 + `zh_label` + `invocation_label`→`completion_label` + 耗时 + 状态徽章），**复用既有** `frontend/.../workshop/ToolCallTimeline`（见 `tests/components/workshop/ToolCallTimeline.spec.ts`）+ registry 的 `invocation_label/completion_label/renderer`。
- **证据引用 chips**：课件引用（页码可点，跳书房）与外部引用（BROWSE `citations`，统一标注「外部内容，非课件」）**分区**呈现，不混淆。
- **流式 token + 可中断**：compose 阶段流式输出，提供 stop。
- **reason_summary only**：每步仅一句可审计摘要，**禁原始 CoT/隐性逐步推理**（§0 红线 + `guide_coach` 既有约束）。
- **错误态显式**：失败步红色徽章 + 可读 error + fallback 提示（如 BROWSE→VIDEO），不静默吞。

冻结类型（**Phase 0 已创建** `frontend/src/stores/learning/loopTypes.ts`；F2 的 `loopSlice.ts`、B2b 的 `BrowseTimeline.vue` 只读引用，后端 `loop/**` DTO 与 `schemas/agentic_loop.py` 同源镜像）：

```ts
type LoopPhase = 'plan' | 'recall' | 'retrieve' | 'act' | 'critique' | 'compose'
type LoopStepStatus = 'pending' | 'active' | 'done' | 'failed'
interface LoopCitation { kind: 'courseware' | 'external'; ref: string; label: string }
interface LoopStep {
  id: string
  phase: LoopPhase
  label: string                 // 中文可读：来自 registry zh_label / 阶段名
  toolName?: string             // 命中工具（BROWSE/HANDOUT/...）
  status: LoopStepStatus
  durationMs?: number
  citations?: LoopCitation[]
  reasonSummary?: string        // 仅一句；禁原始 CoT
  error?: string
}
interface LoopPlan { steps: LoopStep[]; bounded: true; maxSteps: number }
```

---

## 4. 各 Stream 深度任务（TDD；每个 stream 自带完整测试矩阵）

> 通用节奏（每个子任务）：①写失败测试 → ②跑红 → ③实现 → ④跑绿 → ⑤commit。**禁止**先实现后补测试。每个 stream 至少覆盖：正常路径、边界、失败/降级、契约兼容。
>
> **粒度映射**：本节按*能力*组织（10 块）；§2/§6 把每块拆成 1–N 个 **dispatch 单元**（共 ~19，对应 ~20 并发 subagent）。每个 subagent 只取 §2 一行的文件域，深度要求看对应能力小节；同能力多单元按 §2「独占文件域」切分，互不重叠。映射：S-spine→{S1,S2}；G1→{G1a,G1b}；G2→{G2a,G2b}；M→{M1,M2}；O→{O1,O2,O3}；P→{P1,P2}；B1→{B1}；B2→{B2a,B2b}；F→{F1,F2}；Docs→{Docs}。

### Stream S-spine：有界 Agentic Loop 编排 + 审计

**Files** — Create: `graph/agentic_loop.py`、`schemas/agentic_loop.py`、`backend/.../loop/{LoopController,LoopRunService,LoopRunEntity,LoopRunRepository}.java`、`V171__agent_loop_run.sql`；Modify: `graph/orchestrator_graph.py`（挂载 memory_brief 读取、BROWSE 可派发、eval span）。Test: `tests/test_agentic_loop.py`、`backend/.../loop/LoopRunServiceTest.java`。

深度要求（非 MVP）：
1. `AgenticLoopState`：`loop_step`, `max_loop_steps`(默认 4), `tool_trail: list[str]`, `critic_verdicts`, `replan_count`(复用既有 reflect/replan 上界), `memory_brief`, `eval_scores`。
2. 闭环节点：在 `understand` 前注入 `memory_brief`（§3.2，缺省空）；`dispatch` 工具集纳入 BROWSE（§3.1，受 scope/allowlist 收缩）；`reflect→replan` 沿用既有 N=1 有界，**新增** `loop_step` 总上界与"无进展提前终止"（参照 `resource_graph` 的 oscillation 检测）。
3. critic 闸门：dispatch 产物经 grounding/safety 后才 compose；不过且未达上界→有界重试；达上界→给出诚实「未达证据标准」降级回复，不编造。
4. 审计落库（`V171` + `LoopRunService`）：每轮 plan、tool_trail、critic 结论、retry 数、token、关联 Langfuse trace id。
5. 测试矩阵：①正常单轮闭环；②触发一次有界 replan 后成功；③达 `max_loop_steps` 安全降级（不编造）；④BROWSE 在 quiz scope 被收缩不可派发；⑤memory_brief 缺省兼容；⑥审计记录字段齐全；⑦critic 拒绝→不 compose 越权内容。

`V171` 关键列：`loop_run(id, user_id, session_id, turn, max_steps, steps_used, replan_count, tools_used text[], grounding_pass bool, safety_pass bool, input_tokens, output_tokens, trace_id, created_at)`。

### Stream G1-graphrag-rag：KC 知识图谱 + 自适应路由（LightRAG 思路）

**Files** — Create: `pythontopia_rag/graph/{kc_graph.py,router.py,traverse.py}`；Modify: `routes/query.py`、`schemas/query.py`。Test: `tests/test_kc_graph.py`、`tests/test_query_graph_mode.py`。

深度要求：
1. KC 图：由 `V125` 前置种子 + chunk 的 `kc_codes` 共现构图（节点=KC，边=prerequisite/co-occur）。提供 `neighbors(kc, hops)`。
2. 自适应路由（§3.3 `graph_mode=auto`）：查询命中单 KC/事实型→纯向量；多 KC/「为什么/区别/先学」类多跳→图扩展前置 KC 再向量召回并标 `via_prerequisite=True`；`force` 恒走图，`off` 恒向量。
3. 返回 `route` 与 `subgraph`（可审计）。LightRAG 风格双层（局部 chunk + KC 关系）但**轻量**，贴 2C4G；不强依赖外部图库，优先内存图 + 既有 store。
4. 测试矩阵：①单 KC→route=vector；②多跳→route=graph 且 subgraph 非空、含 via_prerequisite；③force/off 行为；④无前置种子时优雅退化为向量；⑤向后兼容（不传 graph_mode 时行为=旧版）；⑥前置扩展跳数上界裁剪。

### Stream G2-grounding-graph：图感知证据 + 防幻觉升级

**Files** — Modify: `nodes/evidence_fetcher.py`(加 graph_mode 透传 + EvidenceBundle.route/subgraph)、`nodes/grounding_critic.py`、`nodes/semantic_grounding_verifier.py`。Test: `tests/test_evidence_graph.py`、`tests/test_grounding_subgraph.py`。

深度要求：
1. `HttpRagEvidenceFetcher.fetch` 增 `graph_mode`/`prereq_hops` 透传；`EvidenceBundle` 加 `route`/`subgraph`（§3.3），`StaticEvidenceFetcher` 同步支持以便测试。
2. `grounding_critic`：前置链路证据可作"为什么先学 X"的可审计解释来源，但答案主张仍须命中**直接证据页**；新增「前置页冒充直接出处」的拒绝规则。
3. 测试矩阵：①graph_mode 透传正确；②有 subgraph 时引用审计含前置链路；③前置页被误用为直接出处→critic 拒绝；④no_evidence 时不编造；⑤旧调用（无新字段）兼容。

### Stream M-memory：Mem0/Zep 记忆 adapter（接进既有画像/记忆）

**Files** — Create: `backend/.../learner/memory/{MemoryProvider.java,LocalMemoryProvider.java,Mem0MemoryProvider.java,ZepMemoryProvider.java,MemoryBriefService.java,MemoryController.java}`、`V169__agent_memory_provider.sql`、`services/pythontopia-graph/.../memory/{contract.py,brief_client.py}`。Test: `MemoryBriefServiceTest.java`、`Mem0MemoryProviderTest.java`(MockWebServer)、`tests/test_memory_brief_client.py`。

深度要求：
1. `MemoryProvider` 接口：`recall(userId, query, k)`、`remember(userId, event)`；`MEMORY_PROVIDER` env 路由（`local` 默认复用既有 `memory_embedding`；`mem0`/`zep` 外部）。任一外部失败 **fail-soft 回退 local**。
2. `MemoryBriefService` 聚合：外部召回事实 + 8 维画像（`learner_profile.dimensions`）弱项 + `kc_review_state` 到期 → `MemoryBrief`（§3.2），体现**时序**（`as_of` + Zep 时序图优先）。
3. `V169`：`agent_memory_ref(id, user_id, provider, external_id, kind, created_at)` 映射外部记忆 id；不删既有表。
4. graph 侧 `brief_client.py`：HTTP 取 brief 注入 orchestrator context（S 消费），超时 fail-soft 空 brief。
5. 测试矩阵：①local 召回；②mem0 provider（MockWebServer）正常；③外部 500→回退 local；④brief 聚合含 weak_kcs/style_notes/as_of；⑤画像缺失时优雅；⑥graph client 超时→空 brief 不报错。

### Stream O-observability-eval：Langfuse 接入 + Ragas/DeepEval 评测

**Files** — Modify: `observability/otel.py`(headers 支持)、`deploy/docker-compose*.yml`(Langfuse+clickhouse/minio 或精简单容器)、`deploy/.env.example`；Create: `observability/eval_span.py`、`eval/{scorers.py,runner.py,datasets/}`。Test: `tests/test_eval_scorers.py`、`tests/test_otlp_headers.py`、`tests/test_eval_runner.py`。

深度要求：
1. `_maybe_attach_otlp_exporter` 支持 `OTEL_EXPORTER_OTLP_HEADERS`（Basic auth）；缺包/缺 endpoint 仍 no-op（保持既有降级语义）。
2. `record_eval_score(name, value)`：写当前 span（无 span 时 no-op）。
3. `eval/scorers.py`：Ragas faithfulness/context_recall + DeepEval answer_relevancy/toxicity，**LLM judge 可注入 mock**（测试零外网）。
4. `eval/runner.py`：跑 `eval/datasets/*.jsonl` golden → 汇总 + 可推 Langfuse（REST fail-soft）。
5. deploy：Langfuse 自托管 compose（标注 `TELEMETRY_ENABLED=false`），文档化 env。
6. 测试矩阵：①scorer 用 mock judge 出分；②runner 汇总正确；③OTLP headers 拼装正确；④无 endpoint→no-op；⑤Langfuse 不可达→eval 仍出本地分不报错。

### Stream P-dspy：GEPA 离线编译导学/出题 prompt

**Files** — Create: `services/pythontopia-graph/pythontopia_graph/optimize/{programs.py,compile.py,loader.py,artifacts/.gitkeep}`、新建 `services/pythontopia-graph/scripts/optimize_guide_coach.py`、`services/pythontopia-graph/scripts/optimize_practice.py`；Modify: `services/pythontopia-graph/pythontopia_graph/llm/provider.py`(仅加 §3.5 loader hook)。Test: `tests/test_optimize_loader.py`、`tests/test_optimize_programs.py`。

深度要求：
1. `programs.py`：把 `GuideCoachDecision` 选择、`PRACTICE` 生成包成 `dspy.Module`（结构化签名，与既有 Pydantic schema 对齐）。
2. `compile.py`：GEPA（`reflection_lm` + 反馈型 metric：复用 grounding/safety/golden 命中作 feedback），导出冻结产物到 `artifacts/{name}.json`。**离线 only**。
3. `loader.py`：fail-soft 加载（§3.5）；provider 命中则用冻结 instruction+demos，否则回退现有手写 prompt。
4. metric 用 `test_guide_coach_golden.py` 既有 golden（只读）+ critic 反馈。
5. 测试矩阵：①loader 缺文件→None→回退；②loader 读合法产物→CompiledPrompt；②损坏 json→None 不抛；③programs 结构化输出 schema 合法；④provider hook 不破坏现有调用（无产物时行为不变）；⑤离线 compile 冒烟（小样本、mock LM）。

### Stream B1-browse-graph：browser-use 窄工具化 executor

**Files** — Modify: `tools/registry.py`(加 §3.1 BROWSE)；Create: `tools/browse/{contract.py 由Phase0,executor.py,allowlist.py,sanitize.py}`。Test: `tests/test_browse_allowlist.py`、`tests/test_browse_executor.py`。

深度要求：
1. `allowlist.py`：域白名单交集（§3.4）；非白名单 fail-fast，绝不发请求。
2. `executor.py`：封装 browser-use（Playwright），**有界**（`max_steps`）、只读动作（navigate/read/search），输出 `BrowseResult`；DOM 摘要经 `sanitize.py` 去敏（不回传脚本/隐藏域/PII）。可注入 fake browser 供测试（零真实联网）。
3. registry：BROWSE spec 完整治理元数据；`intent_catalog_lines` 自动纳入（quiz scope 不暴露）。
4. 测试矩阵：①白名单内 goal→受界步骤+citations；②白名单外→拒绝 fail-fast；③超 max_steps→truncated；④sanitize 去敏；⑤executor 异常→error 字段 + 已走步骤；⑥registry 目录含 BROWSE 且 quiz scope 不含。

### Stream B2-browse-backend：浏览导学落库 + 前端时间线

**Files** — Create: `backend/.../browse/{BrowseController,BrowseRunService,BrowseRunEntity,BrowseStepEntity,*Repository,*Dto}.java`、`V170__browse_run.sql`、`frontend/src/api/browse.ts`、`frontend/src/components/workshop/BrowseTimeline.vue`。Test: `BrowseRunServiceTest.java`、`BrowseControllerTest.java`(MockMvc)、`frontend/tests/components/workshop/BrowseTimeline.spec.ts`、`frontend/src/api/__tests__/browse.spec.ts`。

深度要求：
1. REST（§3.1）：注入 allowlist、调 graph BROWSE、落 `V170`、统一错误。
2. `V170`：`browse_run(id, user_id, goal, status, final_summary, truncated, error, created_at)` + `browse_step(id, run_id, idx, action, url, summary)`。
3. 前端 `BrowseTimeline.vue`：步骤时间线 + 可点引用 url（外链标记"外部内容，非课件"）+ 状态。
4. 测试矩阵：①创建 run 落库；②detail 含 steps/citations；③非 student 403；④graph 失败→统一错误不 500；⑤前端渲染步骤/引用/截断态；⑥api client DTO 类型。

### Stream F-frontend-anim：GSAP/Rive/Motion 闭环动效

**Files** — Create: `frontend/src/game/adapters/anim/{AnimAdapter.ts,domMotion.ts,riveMascot.ts,phaserTween.ts}`、`frontend/src/components/hud/LoopProgress.vue`、`frontend/src/stores/learning/loopSlice.ts`。Test: `frontend/tests/adapters/anim/AnimAdapter.spec.ts`、`frontend/tests/components/hud/LoopProgress.spec.ts`、`frontend/tests/stores/loopSlice.spec.ts`。

深度要求：
1. `AnimAdapter`：`playStepIn/playStepDone/celebrate/reduceMotion`；第三方对象隔离（业务禁直接 import gsap/rive）；尊重 `prefers-reduced-motion`。
2. `loopSlice`：消费 orchestrator SSE 累积 `LoopStep[]`，状态机（pending→active→done/failed）。
3. `LoopProgress.vue`：闭环阶段可视化（看/学/练/测/查证），GSAP/Motion 过渡；Rive 吉祥物按 critic 结果反馈表情。
4. **Claude Code 网页版输出格式**（§3.8）：渲染可见有界计划（带 checkbox 状态）+ 工具调用时间线（复用 `ToolCallTimeline`）+ 证据 chips 分区 + 流式可中断 + 仅 `reasonSummary`（绝不渲染原始 CoT）+ 显式错误态/fallback 提示。
5. **无障碍（WCAG 2.2 AA）**：`prefers-reduced-motion` 下 adapter 退化为瞬时；状态徽章带 `aria-live`/`role=status` 与文字标签（不靠颜色单通道）；时间线键盘可达可折叠；对比度 ≥ 4.5:1。
6. 测试矩阵：①adapter 在 reduceMotion 下退化为瞬时；②loopSlice 状态机迁移；③SSE 事件→步骤渲染；④Rive/gsap 用 mock 不实际加载；⑤计划/时间线/引用 chips/错误态四类渲染；⑥aria-live + 键盘折叠可达；⑦绝不渲染 CoT（仅 reasonSummary）；⑧无 any（typecheck）。

### Stream Docs：worklog + 资产 license

**Files** — Create worklog；追加 `LICENSE-ART.md` / `frontend/public/assets/pixel/LICENSE.md`（Rive 吉祥物等新素材，仅 CC0/CC-BY/OFL）。

---

## 5. 自测命令（每个 agent 在自己范围内必须跑绿）

- Python graph：`cd services/pythontopia-graph && python -m pytest -q <你的测试文件>`
- Python rag：`cd services/pythontopia-rag && python -m pytest -q <你的测试文件>`
- 后端：`cd backend && mvn -q -Dtest=<YourTestClass> test`
- 前端：`cd frontend && npm run typecheck && npm run test -- <你的测试文件>`
- 迁移类改动：空库初始化 + 导入 seed（验证 7 文档/561 页/7 章/65 KC/46 示例/39 题不被破坏）。

无法整体编译（依赖他人未落地接线）时，至少保证**自己模块单测**（fixtures/mock）通过，并在回执里写明待 Phase 2 接线的集成点。

## 6. 执行编排（给 Cursor 的并发调度图）

```text
Phase 0（编排者串行）✅ 已完成（2026-06-17）：
  - 迁移号顺延 V169–V171（避开并发计划占用的 V166–V168）
  - 依赖清单：graph/rag pyproject + frontend package.json 加新依赖（真实最新稳定版，
    全进 optional 组 / 缺包 lazy-import；backend pom 无需新依赖）
  - 冻结 stub 已建并过语法校验：tools/browse/contract.py、memory/contract.py、
    observability/eval_span.py(no-op)、schemas/agentic_loop.py、frontend/.../loopTypes.ts
Phase 1（~19 dispatch 单元 / 一波 ~20 并发 subagent，互不重叠；每 agent 先读本契约）：
  graph:    S1-loop-graph, G2a-evidence, G2b-grounding, M2-mem-brief,
            O1-otel-langfuse, O2-eval-harness, P1-dspy-compile, P2-dspy-loader, B1-browse-exec
  rag:      G1a-kc-graph, G1b-route
  backend:  M1-mem-backend, B2a-browse-backend, S2-loop-backend
  frontend: F1-anim-adapter, F2-loop-ui, B2b-browse-frontend
  其它:     O3-deploy-langfuse, Docs
  依赖通过 §3 冻结契约 + mock/DI 解耦；各自单测跑绿（整体 typecheck/编译留 Phase 2）
Phase 2（编排者串行，集成接线）：
  - orchestrator_graph 真接 evidence(G2a) / brief(M2) / BROWSE(B1) / eval span(O1)
  - provider 真接 loader(P2)；前端 LoopProgress 真接 anim(F1)+slice(F2)+browse(B2b)
  - 全量回归：四端测试 + 空库迁移(V169–V171) + RAG 查询(input函数/for循环/列表)引用页
    + 判题三态 + §8 NFR 门
```

每个 subagent **完成时必须返回**：①改/建文件清单（绝对路径）②自测命令与关键输出 ③留给 Phase 2 的集成点 ④任何偏离本契约处及原因。

### 并发 subagent 提示词模板（编排者按此派发）

```text
你是 <Stream 名> 的并发构建 agent。先完整读 docs/plans/2026-06-17-agentic-loop-framework-integration.md。
只在「§2 文件归属矩阵」里属于 <Stream 名> 的文件域内写代码；跨域内容一律按 §3 冻结契约引用，禁止修改非本域文件与依赖文件。
按 §4 对应小节 TDD 落地（先写失败测试），交付完整能力 + 完整测试矩阵（含失败/降级/边界），非 MVP。
跑绿 §5 对应自测命令。完成时按 §6 回执四要素返回。遵守 §0 全员红线（尤其：非自由 ReAct、不伪造课件、观测 fail-soft、业务 fail-fast、类型纪律）。
```

## 7. 验收（交付说明必含）

做了什么 / 验证了什么（贴四端测试 + 评测分）/ 哪些未验证 / 剩余风险 / 是否影响比赛演示链路。重点回归：①闭环有界（达上界安全降级不编造）②GraphRAG 多跳引用可审计 ③记忆 brief 体现时序 ④Langfuse 拿到全链路 trace + eval 分 ⑤DSPy 冻结产物可回退 ⑥BROWSE 白名单 fail-fast ⑦前端 reduceMotion 退化。**外加 §8 八大非功能需求门全部达标。**

---

## 8. 非功能需求（ISO/IEC 25010 八大质量特性 · 跨 stream 硬验收门）

> 本节是**全 stream 强制**的质量门。每个 stream 自测须覆盖与本域相关的指标；编排者 Phase 2 做整体 NFR 回归并在交付说明逐条勾验。指标尽量**可度量、可测、有归属**。性能目标基线环境：单 worker · ECS 2C4G（`47.120.29.62`）。

### 8.1 功能适合性（Functional Suitability）
- **完备性**：6 框架能力全部落地并真正接入闭环（非占位、非演示假数据）。
- **正确性**：闭环达上界→诚实降级不编造；GraphRAG 多跳引用可回溯子图；判题唯一权威不被 Pyodide 预览覆盖。
- **适当性**：每个新工具/服务至少服务画像 / 路径 / 资源生成 / 练习评估 / 班级教学之一（教育优先）。
- **度量门**：四端测试全绿；DSPy 编译后 golden 命中率 **≥ 编译前基线**（不退化）；闭环关键路径 e2e 通过。
- **归属/验证**：全 stream；`test_agentic_loop.py` + golden 对比 + `frontend/e2e/publicAcceptance.spec.ts`。

### 8.2 性能效率（Performance Efficiency）
- **时间行为（P95 目标）**：导师首 token ≤ 2.5s；RAG 查询 vector ≤ 800ms / graph ≤ 1.5s；BROWSE 单步 ≤ 8s 且整体有界 ≤ `max_steps×8s`；闭环单轮 ≤ 12s。
- **资源利用**：GraphRAG 内存图 ≤ 200MB；dispatch 有界并发维持 Sem=3；BROWSE 单实例**串行**（浏览器进程防内存爆）。
- **容量**：演示并发 ≥ 20 学生不雪崩（provider 限流由既有 harness 熔断兜底）。
- **归属/验证**：O（出 P50/P95，Langfuse）+ G1 + B1 + S；在 `docs/performance-baseline.md` 追加本闭环基线。

### 8.3 兼容性（Compatibility）
- **共存**：观测 / eval / 记忆 / DSPy / BROWSE 全部 **fail-soft**——缺依赖或缺外部服务时主链路零影响（严格沿用 `otel.py` 降级范式）。
- **互操作**：4 端共用 backend 契约不破坏（`docs/clients/api-contract.md`）；新端点/字段一律**向后兼容**（可选 + 默认值）；遵 OTel GenAI 语义约定 + Langfuse OTLP 标准。
- **归属/验证**：全 stream；契约测试 + "旧调用不传新字段" 回归（G2/M/B2 各一例）。

### 8.4 易用性（Usability）← 用户重点
- **可学习性**：闭环开头给可见有界计划（§3.8）；首次出现 BROWSE/GraphRAG 附一句能力说明。
- **可操作性**：每步学生可控（继续 / 追问 / 停止），不自动连跳；BROWSE 失败回退 VIDEO。
- **用户错误防护**：考核 scope 收缩工具防逃避作答；危险域 fail-fast。
- **界面美学 + 一致性**：像素小镇风 + Claude Code 网页版 agent 输出格式（§3.8）统一。
- **无障碍（WCAG 2.2 AA）**：尊重 `prefers-reduced-motion`；对比度 ≥ 4.5:1；键盘全可达；状态用 `aria-live`/文字双通道，不单靠颜色。
- **归属/验证**：F + S；F 组件测试含 reduceMotion / 键盘 / aria；演示脚本走查。

### 8.5 可靠性（Reliability）
- **成熟性**：观测 / eval 不引入任何崩溃路径。
- **可用性**：Mem0/Zep/Langfuse/浏览器任一不可用 → 降级，不熔断主链路。
- **容错**：闭环有界重试 + oscillation 提前终止（参照 `resource_graph`）；BROWSE 异常返回**已走步骤 + error**。
- **可恢复**：闭环 run 落库（`V171`）+ LangGraph 单轮 checkpoint，崩溃点可查、按轮回放。
- **归属/验证**：S + M + O + B1；**故障注入测试**：每个外部依赖至少 1 例（断网 / 超时 / 500 / 损坏产物 → 优雅降级）。

### 8.6 信息安全（Security）
- **机密性**：OTel 默认不采集 prompt/参数/结果（既有约束）；记忆/浏览内容按 `user_id` 隔离；JWT + `requireStudent`。
- **完整性**：BROWSE 域 allowlist + `sanitize` 去敏 + 只读动作；学生上传/外部抓取**只进带标签 context**，绝不污染课件证据。
- **可问责**：闭环审计（`V171`）+ Langfuse trace 全链路可追。
- **抗滥用 / 防注入**：不输出完整可提交答案 / 隐藏测试 / 原始 CoT；外部内容标签隔离 + critic 复核防 prompt 注入。
- **归属/验证**：B1 + B2 + S + O；复用 `2026-06-14-security-wall` 用例 + 注入用例 + 越权 403。

### 8.7 维护性（Maintainability）
- **模块化**：§2 文件归属零重叠；第三方对象隔离 adapter 层；冻结契约单一事实源（registry / §3 stub）。
- **可复用**：记忆 provider / anim adapter / eval scorer 接口化可换实现。
- **可分析性**：OTel span + Langfuse + 闭环审计三位一体定位。
- **可修改 + 可测试**：TDD 全覆盖；DSPy 离线产物可回退；ruff/mypy（如启用）/Detekt/ESLint/typecheck 全绿。
- **度量门**：覆盖率不低于现状（后端 Kover / 前端 vitest coverage / py pytest）。
- **归属/验证**：全 stream；四端静态分析 + 覆盖率门。

### 8.8 可移植性（Portability）
- **适应性**：全部经 env 选型——`MEMORY_PROVIDER` / `graph_mode` / `PYT_ORCH_CHECKPOINT` / `LANGFUSE_*` / `BROWSE_ALLOWED_DOMAINS`；本地 / ECS / 容器一致。
- **可安装性**：`deploy/docker-compose*.yml` 一键起（Langfuse 走可选 profile，不强依赖）；根 `./dev.sh` 不漏 Docker 依赖。
- **可替换性**：LightRAG↔GraphRAG、Mem0↔Zep↔local、Langfuse↔任意 OTLP backend 可换不改业务层。
- **归属/验证**：O + M + G1；空库迁移 + compose 起停 + 切 provider env 冒烟。

### 8.9 教育安全 / 防幻觉（ISO 25010:2023 Safety 扩展 · 本项目核心）
- 所有生成资源过证据审查；通识知识须显式标注「非课件」；不伪造页码/章节/题目/作者；不给可直接提交的完整答案（除任务类型明确允许且受教师视角限制）。
- **归属/验证**：G2（grounding）+ O（faithfulness 分）+ S（critic 闸门）；Ragas faithfulness ≥ 阈值、幻觉率 ≤ 阈值（阈值在 O 的 `eval/datasets` 基线确定后冻结）。

## 9. NFR 归属与回归矩阵（编排者 Phase 2 逐格勾验）

| NFR 特性 | 主责 stream | 验证产物 / 命令 |
|---|---|---|
| 8.1 功能适合性 | 全 | 四端测试 + golden 对比 + e2e |
| 8.2 性能效率 | O / G1 / B1 / S | Langfuse P50/P95 + `performance-baseline.md` |
| 8.3 兼容性 | 全 | 契约测试 + 旧调用回归 |
| 8.4 易用性 | F / S | 组件测试(reduceMotion/键盘/aria) + 走查 |
| 8.5 可靠性 | S / M / O / B1 | 故障注入测试（每外部依赖 ≥1） |
| 8.6 信息安全 | B1 / B2 / S / O | 安全墙 + 注入用例 + 越权 403 |
| 8.7 维护性 | 全 | 静态分析 + 覆盖率门 |
| 8.8 可移植性 | O / M / G1 | 迁移 + compose + 切 env 冒烟 |
| 8.9 教育防幻觉 | G2 / O / S | Ragas faithfulness + 幻觉率门 |

> **NFR 测试归属原则**：能在单 stream 内验证的 NFR（如 F 的无障碍、B1 的 allowlist、O 的 fail-soft）由该 stream 自带；跨 stream 的（性能 P95、整体故障注入、契约回归）由编排者 Phase 2 统一回归，结果写入交付说明 §7。
