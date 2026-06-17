# 百晓工坊 Tool Use 成熟化 · 实施进度文档

> 实时维护的实施记录（防上下文丢失）。每完成一个阶段即更新本文档：做了什么、改了哪些文件、测了什么、证据、风险。

## 背景与目标

当前 `services/pythontopia-graph` 的 tool use 本质是**单轮意图分类器 + 静态 SSE 管线**：模型不"调用"带参数的工具，而是被 `IntentRouterAgent` 分类后路由到固定管线。对标 2026 前沿（Anthropic《Advanced Tool Use》《Context Engineering》、Claude Code 逆向、Graph Harness 论文）后确定的成熟化目标：

> **有界原生工具调用循环 + 工具收敛 + Tool Use Examples + right-altitude 描述**，跑在既有的有界图与强治理（harness / critic）里。前沿"规模特性"（Tool Search / PTC）因 17 工具规模不够 + 教育红线（no-shell）+ DeepSeek 不支持，缓做/排除。

### 红线（贯穿所有阶段）
- **有界**：非自由 ReAct，显式 step/budget 上限 + 停止条件 + 白名单，达上界诚实降级。
- **零回归**：新增能力一律 flag-gated 或纯加法，默认行为逐字节不变。
- **教育安全**：不给 shell、不泄完整答案、grounding/safety critic 照跑、外部内容带标签。
- **不推翻有界结构化图**（2026 论文背书），把原生工具调用装进去，不换成裸 ReAct。

### 前置验证（2026-06-17 实测）
DeepSeek（`api.deepseek.com`）`deepseek-v4-pro` 与 `deepseek-v4-flash` **均原生支持 function calling**：单轮 `tools`→`tool_calls`（finish_reason=tool_calls）、双轮 `tool_result` 回喂→最终答复（finish_reason=stop），并支持 prompt 缓存。MVP 强依赖满足，无需换 provider。

---

## 五阶段方案与进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| **A** | provider 原生工具调用（LlmResponse.tool_calls + 解析 + 能力探测 + 三 provider） | ✅ 完成 |
| **B** | registry → tool JSON schema + input_examples（每工具入参 schema） | ✅ 完成 |
| **C** | 有界原生工具调用循环引擎（flag 默认零回归） | ✅ 完成 |
| **D** | 工具收敛 17→9 + 执行器桥接（loop-only，加法非破坏） | ✅ 完成 |
| **E** | 工具调用 eval（多工具真实任务，守质量防回归） | ✅ 完成 |
| **F** | 混合架构：确定性路由器选择 + 有界循环编排 | ✅ 完成 |

> **六阶段完成 + flag-gated 接线（2026-06-17）。** 全量回归 **993 passed, 1 skipped**；新增 54 个测试，零回归。
> **关键纠偏**：held-out 改写实测发现"纯 LLM 选工具"在 DeepSeek 上仅 64%（过拟合措辞），
> 对标 Codex/Claude Code 后改为**混合架构**——选择交确定性路由器、循环只负责多步编排。

### 验证命令
```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
./.venv/bin/python -m pytest -q            # 单测
./.venv/bin/python -m ruff check pythontopia_graph/<改动文件>
./.venv/bin/python -m mypy pythontopia_graph/<改动文件>
```
> 注：`.venv` 为 Python 3.11.15；系统 `python3` 是 3.10，不满足 pyproject `>=3.11`。

### 全局回归基线（A+B+C 完成时，2026-06-17）
`./.venv/bin/python -m pytest -q` → **963 passed, 1 skipped**（跳过项为无关的 ragas 导入缺失，既有）。
新增 24 个测试（provider 8 + schema 6 + loop 10），provider/registry 改动全仓零回归。
既有 mypy 债务：`llm/provider.py` 4 处（`base_url/model` 的 `str|None`、Protocol `json_mode` 不匹配），本次未引入新错误（已 git stash 核验）。

---

## Phase A — provider 原生工具调用 ✅（2026-06-17）

### 做了什么
让 LLM provider 支持 OpenAI 兼容的 function calling 协议（`tools` 入参 / `tool_calls` 出参 / `tool_result` 回喂），这是"工具被模型在循环里调用"的地基。

### 改动文件
- `pythontopia_graph/llm/provider.py`（+150 / −13）
  - `LlmResponse` 新增 `tool_calls: list[dict] | None`。
  - 新增 `_parse_chat_message(data)`：统一解析响应（工具轮 content=null 兼容；结构异常 fail-soft 空响应）。
  - 新增 `provider_supports_tool_calling(provider)`：能力探测；无能力位者回落意图分类器（零回归）。
  - `OpenAiCompatProvider`：`supports_tool_calling=True`；传 `tools` 时**不再强制 `response_format=json_object`**（两者互斥）；改用统一解析。
  - `DryRunProvider`：`supports_tool_calling=True` + `_dryrun_tool_turn`——离线确定性"工具回合→收到 tool 结果作答"，让有界循环可无网单测且可终止。
  - `SparkProvider`：`supports_tool_calling=False`（未验证，安全回落分类器）。
- 新增 `tests/test_provider_tool_calling.py`（8 用例）。

### 验证证据
- `pytest tests/test_provider_tool_calling.py test_openai_compat_provider.py test_spark_provider.py` → **23 passed**，既有 provider 测试零回归。
- `ruff check` → All checks passed。
- `mypy` → 4 errors，经 `git stash` 确认**全部为既有**（`base_url/model` 的 `str|None`、Protocol 的 `json_mode` 不匹配），本阶段零新增。

### 风险/取舍
- 既有 4 个 mypy 错误未修（超出本阶段范围、且涉及 Protocol 改动有回归风险）；记录在案。
- Spark 工具调用能力保守置 False；若后续验证支持可改。

---

## Phase B — tool JSON schema + examples ✅（2026-06-17）

### 做了什么
让 registry 能把 17 个工具导出为 **OpenAI 兼容 function-calling schema**（含 typed 入参 + Tool Use Examples），这样模型可在循环里以结构化 arguments 调用工具，而非走 IntentRouteResponse 旁路。

### 改动文件
- `pythontopia_graph/tools/registry.py`：
  - 新增集中映射 `PARAMS_BY_TOOL`（每工具入参 properties）、`REQUIRED_BY_TOOL`（必填）、`INPUT_EXAMPLES_BY_TOOL`（示例调用）——沿用 `RENDERER_BY_TOOL` 风格，**不改 17 个 `_spec` 调用**（纯加法）。
  - 新增 `tool_parameters(name)`、`to_tool_schema(spec)`、`tool_schemas_for_scope(scope)`。
  - OpenAI-compat 无 `input_examples` 槽位 → 示例折进 description（等价 Tool Use Examples）。
- `pythontopia_graph/tools/__init__.py`：导出 `to_tool_schema / tool_parameters / tool_schemas_for_scope`。
- 新增 `tests/test_tool_schema.py`（6 用例）。

### 关键设计
- 必填：`EXPLAIN_CODE/FIX_BUG` 需 `code`，`NAVIGATE` 需 `target`(enum=NAVIGATE_TARGETS)，`BROWSE` 需 `goal`；其余参数可选（dispatch 端缺省可工作）。
- 通用参数 `topic` + `kc_codes`（数组，强调"取自目录、不确定留空、禁编造"）。
- 作用域收缩与意图路由同源：`tool_schemas_for_scope("quiz")` 不含 BROWSE。

### 验证证据
- `pytest tests/test_tool_schema.py tests/test_tool_registry.py` → **12 passed**（含既有 registry 测试，零回归）。
- `ruff` → All checks passed；`mypy pythontopia_graph/tools/registry.py` → **Success: no issues**（registry 完全干净）。

---

## Phase C — 有界工具调用循环引擎 ✅（2026-06-17）

### 做了什么
新增**自包含、有界**的原生工具调用循环引擎：模型在循环里自主调用带参数的工具，看 `tool_result` 再决定下一步，直到 `end_turn` 或触上界。默认无人调用 → 零回归；编排接线留待 Phase D（flag-gated）。

### 改动文件
- 新增 `pythontopia_graph/graph/tool_loop.py`：
  - `ToolCall` / `ToolLoopStep` / `ToolLoopResult`（Pydantic 结构化）。
  - `ToolExecutor` Protocol（注入式：把工具调用映射到既有 dispatch，返回回喂文本）。
  - `coerce_tool_calls()`：白名单收敛（仅注册表内工具，未知工具丢弃）+ arguments fail-soft 解析。
  - `ToolCallingLoop.run()`：有界循环（`max_steps` + `BudgetGuard` 三道预算）；每次工具执行经 `run_with_governance`（超时/重试/熔断）；治理失败回喂结构化 error；触上界/预算耗尽诚实停止；provider 无能力 → `no_tool_calling`（调用方回落分类器）。
- 新增 `tests/test_tool_loop.py`（10 用例）。

### 关键设计（红线对齐）
- **严格有界**：`max_steps` + `BudgetGuard`(LLM 次数/token/墙钟)；`max_steps<1` 构造期 fail-fast。
- **白名单**：模型臆造的工具一律丢弃，绝不执行。
- **fail-soft**：工具执行失败回喂 `{"error":...}` 让模型换策略，不裸抛打断循环。
- **零回归**：纯新增模块，默认无人调用。

### 验证证据
- `pytest tests/test_tool_loop.py` → **10 passed**（能力回落 / 单轮闭环 / 上界停止 / 预算停止 / 未知工具过滤 / 执行失败回喂 / arguments 解析 / 构造期校验）。
- `ruff` → All checks passed；`mypy tool_loop.py` → 仅 provider.py 既有 4 错（跨 import 带出），`tool_loop.py` 本身零错。

---

## Phase D — 工具收敛 17→9 + 执行器桥接 ✅（2026-06-17）

### 做了什么
把 17 个意图**收敛为 9 个高杠杆、参数化工具**（仅用于循环工具表，loop-only，不改意图路由/前端/Android），并实现执行器把收敛工具调用桥接到既有派发机制。

### 改动文件
- 新增 `pythontopia_graph/tools/consolidated.py`：
  - `ConsolidatedTool` + 9 个收敛工具（`ask(mode)` / `quiz(mode)` / `make_resource(kind)` / `make_practice(kind)` / `plan_study` / `my_space(kind)` / `video` / `navigate` / `browse`）。
  - `consolidated_tool_schemas(scope)`（scope 收缩：quiz 仅 ask/quiz）+ `expand_tool_call(name,args)`（收敛工具 → 旧意图，选择器 fail-soft 回落）。
- 新增 `pythontopia_graph/graph/tool_executor.py`：
  - `RegistryToolExecutor`：生成类（ASK/资源/PRACTICE/PLAN）经**既有** `AgentDispatcher` 真派发并回带 summary；前端/交互/外部类（VIDEO/PROGRESS/MY_STUDY/NAVIGATE/REVIEW/ASKME/BROWSE）回结构化指令。
  - `StepDispatcher` Protocol（注入 fake 做零 LLM 测试）。
- `pythontopia_graph/graph/tool_loop.py`：引擎改为**接受注入** `tools`/`valid_names`（默认回落 legacy 17，保 Phase C 测试）；`_run_one` 治理参数对注册表外的收敛工具名用安全默认值（解耦 legacy 注册表）。
- 新增 `tests/test_consolidated_tools.py`(7) + `tests/test_tool_executor.py`(9)。

### 收敛映射（17→9）
ask←{ASK,EXPLAIN_CODE,FIX_BUG}；quiz←{ASKME,REVIEW}；make_resource←{HANDOUT,MINDMAP,READING,STORYBOARD}；make_practice←{PRACTICE,CODE_LAB}；my_space←{PROGRESS,MY_STUDY}；plan_study/video/navigate/browse 各 1。

### 验证证据
- `pytest test_consolidated_tools test_tool_executor test_tool_loop` → **26 passed**（含 Phase C 回归）。
- `ruff` clean；`mypy` 两新文件**本身零错**（仅 provider.py / orchestrator.py 既有债务跨 import 带出）。
- 全量 **979 passed**。

---

## Phase E — 工具调用 eval ✅（2026-06-17）

### 做了什么
新增聚焦"工具选择质量"的离线 eval（Anthropic「评测驱动工具迭代」原则落地），在有界循环上跑真实任务集，度量工具选择正确率 / 干净终止率 / 无未知工具 / 平均轮数。

### 改动文件
- 新增 `pythontopia_graph/eval/tool_use_eval.py`：
  - `ToolUseTask` / `ToolUseCaseResult` / `ToolUseEvalReport`（pass_rate / selection_accuracy / clean_termination_rate / avg_rounds）。
  - `DEFAULT_TASKS`（7 条真实诉求→期望收敛工具）+ `run_tool_use_eval(provider, executor, tasks)`。
  - provider/executor 可注入：CI 用 scripted 确定性 provider 零外网；离线可对真实 DeepSeek 跑对账。
- 新增 `tests/test_tool_use_eval.py`(4)：perfect provider 全过、wrong provider 准确率 2/7、空 plan 检出零选择、默认任务引用合法工具。

### 验证证据
- `pytest test_tool_use_eval` → **4 passed**；`ruff`/`mypy` 干净。

---

## Phase F — 混合架构（确定性路由 + 有界循环编排）✅（2026-06-17）

### 为什么（对标 Codex / Claude Code 的实证结论）
联网核实：Codex/Claude Code 的工具选择稳健性**主要来自"模型在环后训练"**（模型针对自己那套工具专门微调）——这条 **DeepSeek 用不了**。它们还靠：工具少而通用 + bash 兜底、system prompt 引导、"能确定就别交给 LLM"（确定性优先）、歧义即澄清。

→ 推论：对未微调的 DeepSeek，"纯 LLM 从 schema 选工具"有结构上限（held-out 实测 64%）。**正确架构是混合**：选择交既有确定性路由器（已是该范式：预判+关键词+LLM+澄清），循环只做"看结果再决策"的多步编排。

### 改动文件
- `tools/consolidated.py`：加 `consolidated_for_legacy(intent)`（旧意图→收敛工具反向映射，喂种子用）。
- `graph/tool_loop.py`：`ToolCallingLoop.run` 加 `forced_first_tool`（种子：路由器选定的首工具先确定性执行一步，不耗模型预算，再进循环编排）。
- 新增 `graph/hybrid_loop.py`：`RouterSelection` / `ToolSelector` Protocol / `IntentRouterSelector`（包既有 `IntentRouterAgent`）/ `HybridToolOrchestrator`：路由选→（澄清则短路）→种子进循环→多步编排；provider 不支持工具调用则**优雅降级**为"仅路由+执行首工具"(`stop_reason=router_only`)。
- 新增 `tests/test_hybrid_loop.py`(6)。

### 验证证据
- `pytest test_hybrid_loop test_tool_loop` → **16 passed**；`ruff`/`mypy` 干净。
- 全量 **990 passed, 1 skipped**，零回归。

#### 🎯 决定性 A/B（held-out 改写集，3 次多数投票，真实 DeepSeek）
同一组"避开关键词"的改写提示词上：

| 选择方式 | held-out 选择率 |
|---|---|
| 纯 LLM 工具循环 | **64%** |
| **混合端（IntentRouter 选择）** | **14/14 = 100%** |

- 路由器把纯循环全挂的模糊改写（"梳理成一张图"→make_resource、"按什么顺序学"→plan_study、"带我过一遍"→quiz、"能跑起来看效果的小例子"→make_practice）**全部选对**。
- **机理**：专用**分类器**（必须输出一个意图）没有 agentic 循环里"要不要调工具"的犹豫/弃权，对未微调模型的工具选择远胜"裸 schema 选择"。这定量证实了混合架构的正确性（+36pp）。
- 触发澄清 0/14：本组改写虽模糊但路由器有把握；真正低置信时会反问而非误选。

### 接线（A · flag-gated，2026-06-17）
- `routes/orchestrator.py` 新增 **`POST /agents/orchestrator/tool-loop`**（非流式）与 **`/tool-loop/stream`**（SSE：增量 `loop_step` + 终态 `result`），由 `PYT_TOOL_LOOP` 开关（**默认 off → 404**，在线默认路径零回归）。
- `tool_loop.py`/`hybrid_loop.py` 加 `on_step` 回调供 SSE 增量推送；`ToolLoopStep` 经 `model_dump` 发出。
- `tests/test_tool_loop_route.py`(3)：flag off→404、flag on→端到端运行（路由器有把握选 HANDOUT→种子 make_resource→循环）、SSE 发 `loop_step`+`result`。
- 路由验证发现并保留的真实行为：stub LLM 低置信时路由器**触发澄清**（hybrid 短路返回澄清，不硬猜）——证明"歧义就问"链路打通。

### 前端渲染（A · 复用既有 Agentic Loop UI，2026-06-17）
- 后端 `on_step` 改发 **AG-UI `CUSTOM`/loop_step（`LoopStep` 形状：phase=act、status done/failed、toolName/reasonSummary）**，与既有 `loopSlice` 冻结契约一致 → 直接复用 `LoopProgress.vue`。
- 前端新增（与 `GuideOverlay` 的 orchestrator 接线同范式）：
  - `api/toolLoop.ts`：`HybridResult`/`ToolLoopResult` 镜像类型 + `/api/orchestrator/tool-loop/stream`。
  - `composables/useToolLoop.ts`：`useSseStream` + `createLoopSlice` + `watch(events→syncFromEvents)`，暴露 `plan/status/runError/result/start/stop`。
  - `components/learning/ToolLoopPanel.vue`：渲染 `LoopProgress` + 最终答复/澄清 + 工具轨迹。
  - `tests/composables/useToolLoop.spec.ts`(4)：CUSTOM/loop_step 喂入、RUN_FINISHED 完成、RUN_ERROR 失败、start/stop 接线。
- 验证：`vitest` useToolLoop 4 + loopSlice 13 + LoopProgress 16 = **33 passed**；`vue-tsc` 全项目 **0 错误**。
- **剩余连接件**：`/api/orchestrator/tool-loop/stream` 的 **Java 代理**（OrchestratorController 增并行转发）——前端/ graph 两端已就绪，差这一跳。

### 架构决策（记录）
- **不**用纯 LLM 工具循环替换意图路由器（held-out 64% 不及现有调优路由）。
- 选择 = 确定性混合路由器（held-out 实测 100%）；编排 = 有界循环；二者经 `HybridToolOrchestrator` 组合，`PYT_TOOL_LOOP` flag-gated，默认不改在线路径。
- 残留共性弱项（plan/review/mindmap 的模糊改写）：靠路由器的**低置信澄清**兜底，而非堆描述关键词（避免过拟合）。

---

## 完成总结（2026-06-17）

百晓工坊 tool use 从"单轮意图分类器"升级为**有界原生工具调用循环 + 收敛工具表 + Tool Use Examples + eval 守门**，全部 **flag-gated / 加法**，默认行为零回归。

| 维度 | 改造前 | 改造后 |
|---|---|---|
| 工具调用 | 分类器选 1 个意图 | 模型在有界循环里自主调带参工具、看结果再决策 |
| 工具表 | 17 个碎片化重叠 | 循环侧收敛为 9 个高杠杆参数化工具 |
| 入参 | 走 IntentRouteResponse 旁路 | typed JSON schema + Tool Use Examples |
| 质量守门 | 无 tool-use eval | `run_tool_use_eval` 多任务量化选择质量 |
| 红线 | — | 严格有界 + 白名单 + harness 治理 + 诚实停止 + 教育安全不变 |

**全量回归基线：993 passed, 1 skipped（新增 54 测试，零回归）。** 含 flag-gated 接线 `POST /agents/orchestrator/tool-loop[/stream]`（`PYT_TOOL_LOOP`，默认 off）。

**架构定稿（对标 Codex/Claude Code 后）**：选择=确定性混合路由器（DeepSeek 上最稳健，held-out 证明纯 LLM 选择只有 64%）；编排=有界工具调用循环；`HybridToolOrchestrator` 组合二者，flag-gated。

### A/B 真实验证（DeepSeek v4-pro，工具选择正确率，2026-06-17）

经历"单次测不准 → 扩大任务集 → 多数投票去噪"三步，得到**定论性正向结果**：

| 测量方法 | 旧 17 工具 | 新 9 收敛 | 说明 |
|---|---|---|---|
| 单次 n=10 | 90% | 80% | 噪声大，收敛看似更差（误导） |
| 单次 n=20, temp=0 | 95% / 90% | 85% | 仍抖动，单次不可信（同配置两跑不同） |
| **3 次多数投票 n=20, temp=0** | **85%** | **95%** | **去噪后定论：收敛 +10pp** |

- **in-distribution（与 eval/描述措辞对齐）：收敛 95% > 旧表 85%（3 次多数投票）**。旧表失败全是 None（重叠工具致犹豫不敢承诺）。

#### ⚠️ Held-out 改写验证（关键修正，2026-06-17）
质疑："把描述换个同义说法不就又触发不了？"（过拟合担忧）→ 用**避开关键词的改写**提示词重测（plan 用"按什么顺序学"、review 用"带我过一遍"，均不含"计划/规划/复习"），3 次多数投票：

| 提示词分布 | 旧 17 | 新 9 | 结论 |
|---|---|---|---|
| in-distribution（含关键词） | 85% | 95% | 收敛领先（但部分靠措辞对齐） |
| **held-out 改写（无关键词）** | **64%** | **64%** | **打平——+10pp 优势消失** |

- **质疑成立**：之前 +10pp 有相当部分是**过拟合 eval 措辞**；换改写后蒸发。
- **共性硬骨头**（两表都 None/错）：`mindmap`「梳理成一张图」、`codelab`「能跑的小例子」、`plan`「按什么顺序学」、`review`「带我过一遍」——这些意图**天然措辞模糊**，靠堆关键词只会 whack-a-mole（已据此把描述改回**语义+边界**式，不再镜像 eval 措辞）。
- **修正后的诚实结论**：收敛(17→9) 至多**与旧表持平、稳健性未证明更优**；真正稳健的正向是 ① 新增能力、② 零回归、③ 可度量/可迭代（held-out 才是可信泛化估计）。
- **对"接进生产"的含义**：held-out 64% 说明工具循环**尚不足以替换现有经调优的意图路由**（后者有关键词兜底+澄清）；接线前应先提升选择稳健性（更好的 system prompt / few-shot / 低置信澄清），并以 held-out 为准绳。

#### 方法论沉淀
- 推理模型即使 `temp=0` 也有 ±10pp run-to-run 抖动 → **单次 eval 不可信，须多次多数投票**。
- **必须用 held-out 改写集**评估，否则会把"过拟合措辞"误当能力提升（本次亲历）。

### 尚未做（后续可选）
- **编排正式接线**：把循环接进 `routes/orchestrator` 取代/并存分类器（需 flag `PYT_TOOL_LOOP`），并把"前端指令"在 SSE 响应里承载——本批为"引擎 + 桥接 + eval"就绪，未改在线 SSE 默认路径。
- L1 系列（记忆 brief 接线 / BROWSE 真引擎 / 闭环审计落库）与既有 provider.py / orchestrator.py 的历史 mypy 债务（4+1 处）。
- 前沿规模特性（Tool Search / PTC）：待 MCP 工具规模上来再上；PTC 因教育红线基本排除。
