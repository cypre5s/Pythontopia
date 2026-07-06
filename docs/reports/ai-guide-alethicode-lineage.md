# AI 学习向导 × Alethicode AI Tutor 文件级血缘矩阵

> 审计日期：2026-06-12 ｜ 审计对象：Pythontopia「AI 学习向导」（前端 AI 助教 GuideOverlay → 学习总管 Orchestrator 链）及其相邻链路（百晓工坊答疑链、训练场导学工作流链）
> 审计方式：只读静态审计，逐文件阅读双方源码；**所有结论均以「路径 + 符号名」级代码证据为准，无代码证据处不声称语义等价**。

---

## 0. 审计范围与判定标准

### 0.1 读取范围

**Pythontopia 侧（被审计方）**

| 区块 | 路径 |
| --- | --- |
| 向导前端 | `frontend/src/components/hud/GuideOverlay.vue`、`frontend/src/stores/guide.ts`、`frontend/src/api/orchestrator.ts`、`frontend/src/stores/workshopActivity.ts`、`frontend/src/composables/useSseStream.ts`（契约依赖） |
| 工坊前端 | `frontend/src/components/workshop/`（26 个组件，重点 `WorkshopChat.vue` / `AgentStudioStream.vue` / `ThinkingStream.vue` / `MarvisPanel.vue` / `PlanCard.vue` 等） |
| Java 后端 | `backend/src/main/java/com/pythontopia/orchestrator/`（5 文件）、`backend/src/main/java/com/pythontopia/tutor/`（8 文件 + dto） |
| Python 微服务 | `services/pythontopia-graph/pythontopia_graph/`（agents / graph / nodes / routes / schemas / llm / tools / sse_agui） |

**Alethicode 侧（血缘母本）**

| 区块 | 路径 |
| --- | --- |
| Java AI tutor 域 | `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/`（约 30 个子包） |
| tutor-graph 微服务 | `/home/cypress/Alethicode/services/tutor-graph/app/`（graph / nodes / clients / eval / runtime） |
| 前端面板 | `/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`（2567 行） |
| 范围外补充求证 | `/home/cypress/Alethicode/services/qa-graph/app/nodes/{grounding_critic,hallucination_detector}.py`（AGENTS.md 指认的防幻觉来源模块，仅为核实血缘读取头部，已在 §6 疑点中说明） |

### 0.2 分类枚举（每行只取其一）

| 枚举 | 判定标准 |
| --- | --- |
| `reused` | 存在逐字 / 逐结构一致的代码、常量或数据结构（拷贝级证据） |
| `adapted` | 有明确移植声明，或 prompt 文本 / 状态机 / schema 字段集可逐段对应，经裁剪改写 |
| `reimplemented` | 仅继承概念与架构思想，代码与具体内容均为新写，无逐段对应 |
| `missing` | Alethicode 在该维度有实现，Pythontopia 对应链路没有 |
| `not-applicable` | 一侧不存在对应物（Pythontopia 原创，或该维度与该文件无关） |

### 0.3 链路命名约定

- **向导链**：`GuideOverlay.vue` → `POST /api/orchestrator/turn/stream`（`OrchestratorController`）→ `pythontopia_graph.graph.orchestrator_graph.OrchestratorGraph`。
- **答疑链**：百晓工坊 `WorkshopChat.vue` → `POST /api/tutor/ask`（`TutorController` → `TutorService`）→ `pythontopia_graph.routes.tutor.tutor_ask_stream` + `TutorAgent`。
- **导学工作流链**：训练场 `TutorWorkflowController` → `pythontopia_graph.graph.tutor_workflow_graph.TutorWorkflowGraph`（6 阶段卡片）。
- Alethicode 对应物只有一条主链：`UnifiedAgentPanel.vue` → `AITutorWorkflowDomainService` / `TutorGraphClient`（Java）→ `tutor-graph/app/graph/builder.build_tutor_graph`。

---

## 1. 总体结论

**Pythontopia 的 AI 学习向导主链是「参考 Alethicode 架构思想的全新实现（reimplemented）」，不是「源自」Alethicode 的移植。** 真正源自 Alethicode（adapted 级，含 reused 片段）的是**训练场导学工作流链**：`tutor_workflow_graph.py` 模块注释明确写「移植 Alethicode tutor-graph」，其 `PHASE_TRANSITIONS` 字典与 Alethicode `app/graph/transitions.py` 的同名常量逐键逐值一致（reused），`tutor_workflow_cards.py` 注释声明「prompt 移植自 Alethicode tutor-graph 各阶段节点……防泄答硬约束逐条保留」。答疑链则处于中间态：课件证据 `[页 X]` 引用规则、防泄答 prompt 纪律、grounding/幻觉/安全三审思想可追溯至 Alethicode（`chat.py` 的课件页码引用约束、qa-graph 双审计节点），但 Pythontopia 用**确定式规则**重写了 LLM 审计（`tutor_review.py`），并新增了图解消毒、沙箱验证、辩论复核等 Alethicode 没有的能力。Alethicode tutor 的重型基建——Java 回调式 EvidencePack、coach plan 逐步证据评估、checkpoint 恢复 UI、bandit 动作重排、Langfuse/OTel 可观测、红队 CI 门禁——在向导链**普遍 missing**。

---

## 2. 十二维度血缘矩阵

> 每行一个「维度 × 链路」单元，分类列严格取 §0.2 枚举之一；双方证据均给出精确路径与符号。

| # | 维度 / 链路 | Pythontopia 证据（路径 + 符号） | Alethicode 证据（路径 + 符号） | 分类 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 1a | 画像上下文 · 向导链 | `frontend/src/components/hud/GuideOverlay.vue` `buildProfileBrief()`（取 `LearnerProfileDto.narrativeSummary` 或拼 major/baseLevel/goal/preference）→ `services/pythontopia-graph/pythontopia_graph/schemas/orchestrator.py` `OrchestratorRequest.profile_brief` → `agents/orchestrator_agent.py` `_understand_messages` / `_reply_messages`（注入 `学生画像: {brief}` system 消息） | `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerProfileProjector.java` `project()`（mastery_by_kc / weak_kcs / misconception_distribution / frustration_level / narrative / teaching_style）→ `/home/cypress/Alethicode/services/tutor-graph/app/nodes/prompts/learner_block.py` `assemble_learner_block()`（`[Learner Profile]/[Teaching Style Preference]/[Recalled Relevant Memories]/[Key KC Mastery]` 四段模板） | reimplemented | 同为「画像文本块注入 system prompt」思想；但向导链 brief 为前端拼接的一句话，无 KC 掌握度、无召回记忆、无教学风格 prompt 前缀，远薄于 `assemble_learner_block` |
| 1b | 画像上下文 · 答疑链 | `backend/src/main/java/com/pythontopia/tutor/TutorService.java` `resolveProfileBrief()`（调 `PassiveProfileService.profileBrief` 8 维富化 + `applyStyleOverride`）；`services/pythontopia-graph/pythontopia_graph/agents/tutor_agent.py` `_personalization_directives()` / `_STYLE_DIRECTIVES`（visual/logical/trial）/ `_KNOWLEDGE_DIRECTIVES`（入门/进阶/扎实）/ `personalization_note()` | 同上 `LearnerProfileProjector.project()`；`profile/LearningStyle.java` `toPromptPrefix()`；`profile/LearnerNarrativeSummaryService.java` `loadIfExists()` | reimplemented | 三型认知风格 → 讲法指令的思想同源（Alethicode `teaching_style_prompt`），但维度解析（`_parse_profile_brief` 解析 `维度: 值;` 字符串）与指令文本为新写 |
| 1c | 画像上下文 · 对话即画像（向导独有方向） | `agents/orchestrator_agent.py` `extract_profile()` / `PROFILE_DIMENSION_KEYS`（8 维 snake_case）/ `_heuristic_profile()`；`graph/orchestrator_graph.py` `_node_extract_profile` → `_persist` → `orchestrator_session.merge_profile_signals` | Alethicode 无「从自由对话每轮抽画像信号」的等价节点（`LearnerMemoryService` 是记忆存取而非逐轮维度抽取） | not-applicable | Pythontopia 原创方向；但信号只进 Python 进程内 session，未回写 Java `learner_profile.dimensions`（见差距矩阵 D-B6） |
| 2a | 对话记忆 · 向导链 | `frontend/src/stores/guide.ts` `historyForRequest`（客户端历史）；`graph/orchestrator_session.py` `append_turns()` / `get_history()`（进程内 dict + `asyncio.Lock`，`MAX_TURNS = 40`）；`agents/orchestrator_agent.py` `_history_messages()`（截 `[-12:]`） | `/home/cypress/Alethicode/services/tutor-graph/app/nodes/chat.py` `chat_node`（`node_outputs.chat.history`，截 `[-6:]`）+ `nodes/compact.py` `compact_node`（超 `COMPACT_K=6` 轮 LLM 压缩为 system 摘要）+ `/home/cypress/Alethicode/backend/.../aitutor/AITutorSessionDomainService.java`（会话域持久化） | reimplemented | 多轮历史注入思想一致；向导链无历史压缩（compact）、无落库（仅进程内），重启即失忆 |
| 2b | 对话记忆 · 跨会话语义记忆 | 答疑链有：`TutorService.resolveMemoryBrief()` → `MemoryRetrievalService.retrieve()`（近期学情要点）+ `resolveHistoryBrief()` / `buildHistoryBrief()`（`TutorSessionRepository.findByConversation`，6 轮、240 字截断）。向导链**无任何对应调用**（`OrchestratorService.turn` 只补 `decayingKcs`） | `profile/LearnerMemoryService.java`、`profile/LearnerMemorySemanticRetrievalService.java` `retrieveByContext()`（按当前 KC + 错误向量 top-K 语义召回）、`context/ConversationContextService.java` `listLastCards()` / `resolveReferences()` | missing | 以向导链论：Alethicode 的语义记忆召回与卡片引用记忆均无对应；答疑链有简化版（关键词链路而非向量语义召回） |
| 3a | 证据/RAG · 装配架构 | `backend/.../tutor/TutorWorkflowService.java` 类注释明示「与 Alethicode 源版的『graph 回调 Java 内部工具 API 组装 evidence_pack』不同，按本仓既有模式在转发请求体里直接下传 problem / diagnosis / profile_brief（graph 零回调），课件 RAG 证据由 graph 自取」；`nodes/evidence_fetcher.py` `HttpRagEvidenceFetcher.fetch()`（POST `PYT_RAG_URL /rag/query`，`via_prerequisite` 前置 KC 召回） | `/home/cypress/Alethicode/services/tutor-graph/app/nodes/evidence.py` `assemble_evidence_pack()` + `EVENT_EVIDENCE_REQUIREMENTS`（按事件声明证据需求，回调 `java_tools_client` 的 `get_workflow_context/get_diagnosis_evidence/get_learner_state/get_courseware_hits/get_similar_errors/get_last_cards/resolve_references`）；`/home/cypress/Alethicode/backend/.../aitutor/evidence/EvidencePackAssembler.java` `assemble()`；`retrieval/CoursewareRetrievalService.java` `retrieve()` | reimplemented | 架构方向相反（推 vs 回调拉）且为有意决策（注释自证）；证据种类收缩为「课件 RAG + 请求体携带的题面/判题」两类 |
| 3b | 证据/RAG · 向导回复落证 | `graph/orchestrator_graph.py` `_node_compose` → `agents/orchestrator_agent.py` `reply_stream()`：**不取证据**，prompt 写明「这是规划对话，没有课件证据时不要编造页码」；证据仅在 dispatch 子步使用（`agents/dispatch.py` `_dispatch_resource` / `_dispatch_tutor` 调 `evidence_fetcher.fetch`） | `nodes/chat.py` `_format_courseware_refs()`（主课件 / 旁证课件分级 + 页码引用约束）+ `evidence.py` `resolve_references`（`@courseware:<id>` 解析） | missing | 向导自身回复无课件证据通道（学生在向导里问概念时得不到 `[页 X]` 引用，需跳转工坊）；规划文案防编造纪律是 adapted 自防幻觉总原则的弱化版 |
| 3c | 证据/RAG · 答疑链 | `routes/tutor.py` `tutor_ask_stream`（`evidence_fetcher` 工位 + `run_with_governance("evidence_fetch", …, breaker_key="rag")`）；`agents/tutor_agent.py` `_build_messages()`（`[页 X·前置知识] (page_id=…)` 证据块）、`_build_citations()`（按 page_id 去重投影 `EvidenceCitation`） | `nodes/chat.py` SYSTEM_PROMPT（「必须基于『用户引用的课件原文』段落作答，并在文末以『（参考课件第 X 页）』形式标注页码」「旁证课件不允许直接引用页码」） | adapted | 「答案必须带页码引用 + 不得引用证据外页码」的纪律一脉相承；引用格式（`[页 X]` vs `（参考课件第 X 页）`）、证据获取方式（RAG 服务 vs Java 回调）改写 |
| 4a | 诊断 · 向导链失败重规划 | `graph/orchestrator_graph.py` `_route_after_dispatch`（`recent_failures` 非空且无 DebugAgent 步 → `replan`）、`_node_replan_failures` / `_build_debug_step`（插入「补练：矿洞修 Bug」步）；`agents/dispatch.py` `_dispatch_debug`（确定性补练规划，注释明确「不得伪造 counter_example 去硬调 analyse」） | `/home/cypress/Alethicode/services/tutor-graph/app/nodes/coach_plan.py` `coach_step_remediate_node` / `coach_replan_node`（计划内补救与转向）；`graph/builder.py` `_route_after_plan_evaluate`（评估不过 → remediate） | reimplemented | 「失败驱动改计划」思想对齐 coach 补救循环，但触发器（判题失败 KC 列表）与动作（插 DebugAgent 步）为新设计 |
| 4b | 诊断 · 细粒度错误诊断 | `agents/debug_agent.py` `DebugAgent.analyse()`（判题反例 + 变量轨迹 → 三层渐进提示，确定性）；`agents/tutor_workflow_cards.py` `error_diagnosis()`（移植版诊断卡 prompt） | `nodes/diagnosis.py` `error_feedback_node`（`_classify_error_taxonomy` 错误分类法、`_first_failed_test_case_full` 失败样例证据、`_similar_error_refs` 复发提醒「这个错误你之前犯过」、`_reflect_and_refine` critic→refine 双 LLM 质检、`_frustration_level` 挫败分级、`ANTI_CHEATING_GUARD`）；`retrieval/SimilarErrorRetrievalService.java` | adapted | 工作流链诊断卡 prompt 移植自 `diagnosis.py`（声明见 `tutor_workflow_cards.py` 模块注释）；但相似错题召回、复发检测、reflection 质检、反作弊模式四项能力被裁剪（见差距矩阵） |
| 5a | 安全 · prompt 防泄答纪律 | `agents/tutor_agent.py` `SYSTEM_PROMPT`（「不输出完整答案代码」+ 苏格拉底三级阶梯）；`agents/orchestrator_agent.py` `_reply_messages`（「绝不给出练习题的完整答案代码」）；`agents/tutor_workflow_cards.py` 各阶段 prompt（「不给完整代码」逐条保留，模块注释自证移植） | `nodes/chat.py` SYSTEM_PROMPT（「不给完整代码」「不退化为普通闲聊」）；`nodes/diagnosis.py` SYSTEM_PROMPT（「不给完整可提交代码、最终标准答案或可直接提交的关键表达式」） | adapted | 工作流卡 prompt 为移植；向导/答疑 prompt 为同纪律新写。判定 adapted 以移植声明 + 约束条目可对应为据 |
| 5b | 安全 · 输出侧审查 | `nodes/safety_critic.py` `safety_critic()`（ANSWER_LEAK_KEYWORDS / DISCRIMINATION_KEYWORDS / PII_PATTERNS / DANGEROUS_CODE，作用于 ResourceDraft）；`nodes/tutor_review.py` `safety_review()`（同规则作用于答案 markdown）；`agents/orchestrator_agent.py` `scan_safety()`（仅 4 词 `SAFETY_KEYWORDS`） | `nodes/output_sanitization.py` `strip_projection_visualize_helpers()`（投影前剥离辅助字段）；`nodes/chat.py` `referenced_card_ids` 白名单清洗（「防止 LLM 编造不存在的 card_id」）；`eval/red_team/assertions.py` `_eval_must_not_contain` / `ci_gate.py`（对抗回归 CI 门禁） | reimplemented | Pythontopia 是关键词/正则规则审查（零 LLM），Alethicode 是结构白名单 + 红队评测；两者无共享代码。红队 CI、班级 anti_cheating 在 Pythontopia 整体 missing（差距矩阵 B/C） |
| 6a | grounding · 资源/答案落证审查 | `nodes/grounding_critic.py` `grounding_critic()`（PageRef ∈ RAG 命中集）；`nodes/tutor_review.py` `grounding_review()`（`[页 X]` 命中证据页）/ `hallucination_review()`（百分比、4 位+整数须在证据文本中）；`nodes/semantic_grounding_verifier.py` `verify_answer_entailment()`（可选 LLM 蕴含判定）；`routes/tutor.py` critique→revise 闭环（`_build_revise_prompt` + 修订稿重跑三审） | tutor-graph 范围内：`nodes/chat.py` 页码引用约束 + card_id 白名单；`nodes/diagnosis.py` `_reflect_and_refine`。范围外补证：`/home/cypress/Alethicode/services/qa-graph/app/nodes/grounding_critic.py` `grounding_critic_node`（LLM 审计 + fail bucket insufficient/conflicting/citation_mismatch/out_of_scope）、`hallucination_detector.py` `hallucination_detector_node`（LLM 挑编造 claim） | reimplemented | 思想可追溯（AGENTS.md 指认 qa-graph 三文件为来源），但 Pythontopia 把「LLM 审计员」重写为「确定式规则 + 可选 entailment」，失败桶、判定逻辑均不同——无逐段对应，不构成 adapted |
| 6b | grounding · 计划 KC 落证（向导独有） | `graph/orchestrator_graph.py` `_grounding()`（plan 内 kc_codes ⊆ course_kc_index，违例 `UNKNOWN_KC:`）+ `agents/orchestrator_agent.py` `_ground_kcs()` / `_select_kcs()`；`nodes/timeline_grounding.py` | Alethicode 无「对计划步 KC 做白名单接地」的对应物（其 KC 来源是题目挂载 `coursewareRetrievalService.loadProblemKcIds`，天然受限） | not-applicable | Pythontopia 原创（对话式规划必须防 LLM 编 KC）；与 `diagnosis.py` 的「related_kcs 必须从【候选知识点】中选择」纪律精神一致 |
| 7 | 学习路径 | `agents/orchestrator_agent.py` `decompose()`（固定看/学/练/测四步，`PHASE_ORDER`）+ `_select_kcs()`（`decaying_kcs > recent_failures > recent_kc_codes` 优先级）+ `backend/.../orchestrator/OrchestratorService.java` `decayingKcs()`（`BugMineService.decayingKcCodes`，FSRS 到期 KC）+ `graph/orchestrator_graph.py` `_negotiate()` / `_negotiation_kind()`（lower/raise/skip/swap_example 四类协商）；`agents/path_planner_agent.py` `PathPlannerAgent.plan()` | `nodes/coach_plan.py` `coach_recommendation_node` / `coach_plan_start_node` / `coach_step_prompt_node` / `coach_step_evaluate_node`（PLAN_RESPONSE 按 evidence_type=text/sample_prediction/code_change/reflection 评估）/ `coach_step_complete_node`；`path/LearningPathOptimizerService.java`、`path/MasteryAdaptiveProblemSelector.java`、`policy/TutorActionPolicy.java` `decide()`、`review/FsrsSchedulerService.java` | reimplemented | 看/学/练/测闭环为 Pythontopia 教育映射的原创结构；FSRS 到期复习优先与 Alethicode `FsrsSchedulerService` 思想同源但实现独立（`BugMineService` 自有 ReviewSignal）。Alethicode coach 的「逐步学生证据评估→补救」在向导链无对应（计划步完成不验证学生证据，差距矩阵 A 向） |
| 8 | 执行反馈 | `nodes/code_runner.py` `run_python()`（`-I -S` 隔离解释器、DEVNULL stdin、超时 kill、输出截断）+ `agents/tutor_agent.py` `should_verify()` / `_build_verification_note()`（报错追问 + 代码围栏才实跑，结果注入 system）；向导链仅消费判题结果 KC 列表（`guide.ts` `setRecentFailures` → `OrchestratorRequest.recent_failures`） | `execution/ExecutionTraceService.java` / `PythonExecutionTraceService.java` / `SimplePythonTracer.java`（判题后端执行轨迹）+ `nodes/coding.py` `coding_node`（java_client 路由） | reimplemented | 同为「拿真实运行事实喂 LLM」，但形态不同：Pythontopia 进程内沙箱即时跑片段，Alethicode 走判题侧轨迹服务；无代码对应 |
| 9 | 可视化 | `agents/tutor_agent.py` `build_diagram()` / `_llm_diagram()` / `_validated_aid()` / `_sanitize_mermaid()` / `_sanitize_ascii()` / `_sanitize_svg()`（`_SVG_BANNED` 黑名单 + viewBox 强制 + 兜底 `_fallback_mermaid`）；`DIAGRAM_KEYWORDS` 按需触发；前端 `frontend/src/components/tutor/TutorVisualAids.vue`。向导链（GuideOverlay）自身无图解能力 | `nodes/visualize.py` `visualize_node`（intent 路由 Java）+ `DEFAULT_INTENT_BY_PHASE`；`visualize/VisualizeCapabilityService.java`、`MermaidValidator.java`、`SvgSanitizer.java`、`ChartConfigValidator.java`、`VisualizePromptCatalog.java` | reimplemented | 「服务端校验/消毒图源码再下发」思想一致（MermaidValidator/SvgSanitizer ↔ `_sanitize_*`），实现语言、位置、intent 体系全部重写；Alethicode 的 chart 配置图与 phase 默认 intent 无对应 |
| 10 | 故障处理 | `tools/harness.py` `run_with_governance()`（超时/指数退避/`CircuitBreaker`/`BudgetGuard`/fallback，永远返回 `ToolObservation`）；`agents/dispatch.py` `_governed_failure()`（`artifact.kind=dispatch_failure`）；`graph/orchestrator_graph.py` `_node_dispatch`（单步失败不拖垮全局 + SSE `error` 事件）、`default_checkpointer()`（postgres 不可用构造期降级 MemorySaver）；`routes/tutor.py` `_SseHeartbeat`（10s ping 防断连）；`graph/tutor_workflow_graph.py` `failure_bucket`（TRANSITION_VIOLATION / INSUFFICIENT_EVIDENCE / SCHEMA_VIOLATION / SYSTEM_ERROR） | `app/graph/state.py` `runtime_state` / `failure_bucket` 字段 + `contract/FailureBucket.java` / `contract/RuntimeContract.java`；`graph/builder.py` `_route_after_*`（FAILED 统一路由 `emit_failed` → `persist_projection`）；`WorkflowCheckpointService.java` `workflowCheckpointRestore()`；`graph/TutorGraphClient.java` resilience4j `@CircuitBreaker(name = "tutorGraph")` / `@Retry` | adapted | failure bucket 枚举名与「FAILED 必带 bucket + last_error」契约从 Alethicode 移植（工作流链 bucket 同名同义，`tutor_workflow_graph.py` 自证）；治理 harness（熔断/预算/观测）是对 resilience4j 注解能力的 Python 重写，扩展了预算与审计 |
| 11 | 可观测性 | `schemas/resource.py` `GraphTraceStep` + 各 route SSE `trace` 事件；`tools/harness.py` `_audit_log()`（每次工具调用 JSON 审计日志）+ `harness_stats()`（`GET /agents/harness/stats`）；`graph/orchestrator_graph.py` `turn_thread_id()` / `get_turn_state()`（LangGraph checkpointer 按轮快照、time-travel 排错）；`sse_agui.py`（AG-UI 事件语义化） | `app/observability.py` `configure_otel()`（OTel FastAPI/HTTPX/Logging instrument）；`nodes/langfuse_metadata.py` `build_langfuse_metadata()`（trace_id/session/run/phase 全链路元数据进 Langfuse）；`observability/AgentObservabilityService.java`、`AiTraceService.java` `recordTrace()`（`ai_workflow_event` 落库）/ `getQualityReport()` | reimplemented | Pythontopia 的观测面向前端动画（SSE trace）与进程内统计；Alethicode 的 OTel 分布式追踪、Langfuse LLM 调用账本、trace 落库与质量报表在 Pythontopia 全部 missing（差距矩阵 B 向） |
| 12a | 前端交互 · 向导面板 | `frontend/src/components/hud/GuideOverlay.vue`（气泡线程 + 流式 Markdown + `PHASE_LABEL` 看/学/练/测计划卡 + `panelRoute()` 面板深链 + `guide-clarify` 澄清块 + `guide-dispatch__chip` 多智能体徽章 + `profileUpdates` 提示）；`frontend/src/stores/guide.ts`（`beginAssistant/updateAssistantStream/finalizeAssistant/failAssistant` 流式状态机）；`frontend/src/composables/useSseStream.ts`（AG-UI 事件归一化 + `STATE_DELTA` 状态袋） | `/home/cypress/Alethicode/frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`（`timelineItems` 卡片时间线 + `checkpoint-marker` 可点恢复 + `runtime-banner-approval` 审批确认/拒绝 + `welcomeData`（`AITutorWelcomeService.getWelcome` 的 greeting/starter_actions/memory_tags）+ `submitFeedback` 👍👎❓ + `@card:`/`@courseware:` 引用菜单 + `ProfileDrawer` + `exportConversationMarkdown`） | reimplemented | 交互范式不同：向导是「对话 + 计划卡 + 深链」，Alethicode 是「卡片时间线 + 审批 + checkpoint 回滚」。欢迎个性化、逐卡反馈、checkpoint UI、画像抽屉在向导面板均无对应（差距矩阵 C/D 向） |
| 12b | 前端交互 · 工坊联动层 | `frontend/src/stores/workshopActivity.ts`（`start/syncTraces/syncPlan/finish`，GuideOverlay 与 WorkshopChat 共用「智能体协同」状态）；`frontend/src/components/workshop/WorkshopChat.vue`（PLAN 意图走同一 `/api/orchestrator/turn/stream`，行 280 `orchestratorSse`）；`ThinkingStream.vue` / `MarvisPanel.vue` / `AgentStudioStream.vue`（思考链/事务所动画消费 trace） | Alethicode 无「向导 overlay 与工坊双入口镜像同一编排流」的对应物（其只有单一 UnifiedAgentPanel） | not-applicable | Pythontopia 原创的双入口镜像设计；联动缺陷（双 sessionId 不互通等）见差距矩阵 D 向 |

---

## 3. Pythontopia 文件级血缘表

> 覆盖任务指定读取范围内的全部文件（同类 dto/小文件合并行）。「主维度」标注该文件主要承载的 §2 维度编号。

### 3.1 前端

| 文件 | 主维度 | 分类 | Alethicode 对应物与依据 |
| --- | --- | --- | --- |
| `frontend/src/components/hud/GuideOverlay.vue` | 12a/1a/7 | reimplemented | 功能上对位 `UnifiedAgentPanel.vue`（同为 AI 学习助手主面板），但无任何代码/模板对应；交互范式重设计 |
| `frontend/src/stores/guide.ts` | 2a/12a | reimplemented | 对位 UnifiedAgentPanel 的 `messages`/`timelineItems` 状态管理；`historyForRequest` 过滤逻辑为新写 |
| `frontend/src/api/orchestrator.ts` | 7/12a | reimplemented | 契约（`OrchestratorResult.plan/dispatch_results/profile_updates/safety_result/grounding_result`）为 Pythontopia 新设计；Alethicode 无对话式编排端点 |
| `frontend/src/stores/workshopActivity.ts` | 12b | not-applicable | Pythontopia 原创镜像 store，Alethicode 无对应 |
| `frontend/src/components/workshop/WorkshopChat.vue` | 12b/3c | reimplemented | 16 工具分发中枢为原创；`@` 课件引用（`buildMentionItems`）与 Alethicode `UnifiedAgentPanel.vue` 的 `@card:`/`@courseware:` 引用（`atProviders`、`buildCardItems`）思想同源、代码新写 |
| `frontend/src/components/workshop/AgentStudioStream.vue`、`ThinkingStream.vue`、`MarvisPanel.vue`、`ToolCallTimeline.vue`、`ToolTraceLine.vue` | 11/12b | reimplemented | 消费 SSE trace 渲染拟人化工作室；Alethicode 用 `runtime-banner` + 卡片时间线呈现运行态，无工作室动画 |
| `frontend/src/components/workshop/PlanCard.vue`、`ProgressCard.vue`、`ClarifyBubble.vue`、`QuizModeBar.vue`、`SlashCommandMenu.vue`、`ChatComposer.vue`、`ChatHistoryRail.vue`、`WelcomePanel.vue`、`WorkshopAvatar.vue` | 12b | reimplemented | 计划卡对位 coach plan 卡（`coach_plan.py` 产物的前端形态）；澄清气泡/考核条/斜杠命令为原创交互 |
| `frontend/src/components/workshop/AnswerFeedback.vue` | 12b | adapted | 对位 `UnifiedAgentPanel.vue` `submitFeedback(item, 'helpful'|'unhelpful'|'confusing')` 三键反馈，交互语义一致、实现新写 |
| `frontend/src/components/workshop/EvidenceTag.vue` + `frontend/src/components/tutor/CoursewareEvidence.vue` | 3c | adapted | `CoursewareEvidence.vue` 文件注释自证「仿 Alethicode 课件问答的引用卡」 |
| `frontend/src/components/workshop/{HandoutView,MindMapView,PracticeList,CodeLabView,ReadingList,StoryboardView,PptView,ResourceDetailPanel,ResourceListPanel,MarvisPanel}.vue` | 12b | not-applicable | 资源工坊视图，Alethicode AI tutor 无资源生成工坊形态 |

### 3.2 Java 后端

| 文件 | 主维度 | 分类 | Alethicode 对应物与依据 |
| --- | --- | --- | --- |
| `backend/.../orchestrator/OrchestratorController.java` | 12a | reimplemented | 注释自述「镜像 TutorController」（指 Pythontopia 自家答疑控制器）；Alethicode 入口为 `AITutorWorkflowDomainService` 事件 API，无 SSE 代理对话端点 |
| `backend/.../orchestrator/OrchestratorService.java` | 7/2a | reimplemented | `decayingKcs()` 注入 FSRS 到期 KC 是原创；「无 Repository——会话记忆在 Python 服务侧」与 Alethicode `AITutorSessionDomainService` 落库路线相反 |
| `backend/.../orchestrator/OrchestratorGraphClient.java` | 10 | reimplemented | SSE 直透转发（`GraphSseConsumer.consume` + 事件原样转发）；对位 Alethicode `graph/TutorGraphClient.java` 的 WebClient JSON RPC（createThread/createRun/listCheckpoints/resumeRun），通信模型完全不同 |
| `backend/.../orchestrator/dto/{OrchestratorTurnRequest,OrchestratorTurnMessage}.java` | 7 | reimplemented | 字段（currentChapter/recentFailures/courseKcCodes）为 Pythontopia 课程语境新设计 |
| `backend/.../tutor/TutorController.java`、`TutorService.java`、`TutorSession.java`、`TutorSessionRepository.java`、`dto/TutorAskRequest.java`、`dto/TutorSessionDto.java`、`dto/StyleEngagedRequest.java` | 1b/2b/3c | reimplemented | 会话落库（`persistSession` 解析 `answer_md/evidence_page_ids/grounding_status`）与 Alethicode 会话域思想同源；班级 copilot brief（`resolveClassroomBrief`，学生角色早退红线）、视频 brief（`buildVideoBrief`）、讲法回流（`recordStyleEngaged` → `LearningEvent.TutorStyleEngaged`）为原创 |
| `backend/.../tutor/TutorGraphClient.java` | 3c/8 | reimplemented | 看图答疑（`enrichContextWithImages` + 讯飞图片理解 + `IMAGE_UNDERSTAND_PROMPT` 防泄答转录约束）为 Alethicode 无的能力 |
| `backend/.../tutor/{TutorWorkflowController,TutorWorkflowService,TutorWorkflowGraphClient,TutorWorkflowRepository}.java`、`dto/{TutorWorkflowCardDto,TutorWorkflowRunRequest,TutorWorkflowSessionDto}.java` | 3a/4b/10 | adapted | `TutorWorkflowController.java` 注释自证「移植 Alethicode tutor workflow」；`TutorWorkflowService.java` 注释明示与源版 evidence_pack 回调架构的差异（推送式重构）；`COURSE_PACK_ID = 43L` 写死课程底座 |

### 3.3 Python 微服务（pythontopia_graph）

| 文件 | 主维度 | 分类 | Alethicode 对应物与依据 |
| --- | --- | --- | --- |
| `agents/orchestrator_agent.py` | 1a/1c/5a/7 | reimplemented | 对位物为 coach_plan + chat 的组合概念；understand/decompose/extract_profile/reply 四方法、协商关键词表、画像维度别名表均为新写 |
| `graph/orchestrator_graph.py` | 4a/6b/7/10/11 | reimplemented | LangGraph `StateGraph` + 条件边 + checkpointer 的工程模式与 `graph/builder.py` 同源（LangGraph 通用范式），节点集（understand/build_plan/dispatch/replan/extract_profile/compose/clarify）与 Alethicode 节点集（ingest/validate_transition/assemble_evidence/…/persist_projection）无一对应 |
| `graph/orchestrator_session.py` | 2a | reimplemented | 对位 `AITutorSessionDomainService` + LangGraph thread state；进程内 dict 方案为新写 |
| `agents/dispatch.py` | 7/10 | reimplemented | 进程内子 agent 调度器；Alethicode 对应概念是 `builder.py` `_route_after_evidence` 的事件→节点路由 + `dispatch_generative.py`，无代码对应 |
| `agents/tutor_agent.py` | 1b/3c/5a/8/9 | reimplemented | prompt 纪律承袭（见 2 维度 5a 行），其余（图解三型消毒、沙箱验证、证据注、个性化指令组、辩论触发词）为新增能力 |
| `agents/tutor_workflow_cards.py` | 4b/5a | adapted | 模块注释自证「prompt 移植自 Alethicode tutor-graph 各阶段节点（reading/ideating/skeleton/diagnosis/ac_review/chat），保留全部防泄答硬约束」；改动：三 mentor 角色（Nene/Yoshino/Kanna，见 `diagnosis.py` `mentor_role` 默认 "Yoshino"）收敛为「百晓」、jsonschema 换 pydantic |
| `graph/tutor_workflow_graph.py` | 4b/10 | adapted | 模块注释自证「移植 Alethicode tutor-graph」「阶段迁移规则原样移植（transitions.py）」；其中 `PHASE_TRANSITIONS` 常量与 `/home/cypress/Alethicode/services/tutor-graph/app/graph/transitions.py` `PHASE_TRANSITIONS` **逐键逐值一致**（READING/IDEATING/CODING/ERROR_FEEDBACK/AC_REVIEW/TRANSFER 六态允许集完全相同）——该常量为 reused 级证据；事件集 16→8 裁剪、`validate_transition` 去掉 language/pending_human_action 校验 |
| `graph/tutor_workflow_session.py` | 2a | adapted | 注释自证「与 Alethicode get_last_cards(limit=5) 对齐」（`MAX_CARDS` 注释）；对位 `ConversationContextService.listLastCards()` |
| `routes/orchestrator.py`、`routes/streaming.py`、`sse_agui.py` | 11/12a | reimplemented | SSE 队列生成器 + AG-UI 编码为新写；Alethicode 走 `runtime/event_bus.py` + Redis Stream（`runtime/RedisStreamRunEventReader.java`）异步 run 模型 |
| `routes/tutor.py` | 3c/5b/6a/9/10 | reimplemented | 馆员→导师→三审→修订的流水线为原创编排；三审思想见 6a 行 |
| `routes/tutor_workflow.py` | 4b | adapted | 文件注释自证「移植 Alethicode tutor workflow」，同步 SSE 单请求替代源版异步 run + WebSocket |
| `nodes/evidence_fetcher.py` | 3a | reimplemented | 对位 `CoursewareRetrievalService.retrieve()` + `evidence.py` courseware_hits；HTTP RAG + 前置 KC 召回为新写 |
| `nodes/{grounding_critic,hallucination_detector,safety_critic,semantic_grounding_verifier,tutor_review}.py` | 5b/6a | reimplemented | 思想追溯 qa-graph `grounding_critic_node` / `hallucination_detector_node`（LLM 审计），重写为确定式规则；`semantic_grounding_verifier.verify_answer_entailment` 是向 LLM 审计形态的部分回归 |
| `nodes/code_runner.py` | 8 | reimplemented | 对位 `PythonExecutionTraceService.java` / `SimplePythonTracer.java`；沙箱子进程方案为新写 |
| `nodes/output_persistor.py`、`nodes/timeline_grounding.py` | 6/11 | not-applicable | 资源流水线专用，Alethicode AI tutor 无资源产物持久化对应物 |
| `tools/harness.py`、`tools/registry.py` | 10 | reimplemented | 对位 resilience4j 注解（`@CircuitBreaker`/`@Retry` 于 `graph/TutorGraphClient.java`）+ `AiTutorQuotaService.java`；预算守卫/审计日志/熔断半开为 Python 自研 |
| `llm/provider.py` | 10 | reimplemented | 对位 `clients/llm_client.py`（Langfuse 集成版）；Pythontopia 版无 Langfuse |
| `graph/debate_graph.py`、`agents/{feynman_tutee_agent,npc_dialog_agent,evaluation_narrative_agent,reflection_agent,reading_agent,handout_agent,mind_map_agent,code_lab_agent,storyboard_agent,practice_agent,video_insight_agent,paper_*,boss/*}.py` | — | not-applicable | 辩论复核、费曼学伴、NPC 对话、试卷流水线、Boss 战等均为 Pythontopia 原创玩法，Alethicode 读取范围内无对应物 |
| `agents/{profile_agent,curriculum_mapper_agent,path_planner_agent,intent_router_agent,personalization}.py`、`graph/{profile_graph,path_graph,resource_graph}.py` | 1/7 | reimplemented | 画像访谈/课程映射/路径规划与 Alethicode `profile/` 域（`LearningTwinService`、`LearnerProfileProjector`）同题材；结构化访谈图、意图路由（`intent_router_agent.py` 确定性预判 + 治理包装）为新写 |
| `schemas/*.py` | — | reimplemented | 例外：`schemas/tutor_workflow.py` 为 adapted（注释自证「移植自 Alethicode tutor-graph 的 contracts/tutor_workflow/cards/*.schema.json」「与 Alethicode EVENT_OUTPUT_KEY 对齐」「等价 Alethicode CARD_KEY_TO_SCHEMA 强校验映射」） |

---

## 4. reused 级证据清单（逐字对应）

| Pythontopia | Alethicode | 内容 |
| --- | --- | --- |
| `graph/tutor_workflow_graph.py` `PHASE_TRANSITIONS`（6 键） | `app/graph/transitions.py` `PHASE_TRANSITIONS`（6 键） | 六个阶段的允许迁移集合逐键逐值一致（如 `"AC_REVIEW": frozenset({"AC_REVIEW", "TRANSFER"})`） |
| `graph/tutor_workflow_graph.py` `TransitionError.__init__`（`Illegal transition: {phase} -> {event}` 消息格式） | `app/graph/transitions.py` `TransitionError.__init__` | 异常类签名与消息模板一致（Pythontopia 去掉了 reason 之外的 language 校验） |
| `schemas/tutor_workflow.py` `EVENT_OUTPUT_KEY` / 卡片字段集 | tutor-graph `contracts/tutor_workflow/cards/*.schema.json` + `nodes/diagnosis.py` 输出字段（root_cause / what_program_is_doing / expected_behavior / fix_direction / related_kcs / error_pattern / teaching_goal / checkpoint_prompt / reflection_prompt 等） | 卡片字段名集合对应（声明见模块注释；jsonschema→pydantic 转写） |
| `agents/tutor_workflow_cards.py` 各阶段 prompt 的防泄答条目（「不给完整代码」「related_kcs 只能从【候选知识点】中选择，禁止编造课程外知识点」） | `nodes/diagnosis.py` SYSTEM_PROMPT（「related_kcs 必须从【候选知识点】中选择最贴合的 1~3 个，禁止编造课程外知识点」）、`nodes/chat.py` SYSTEM_PROMPT（「不给完整代码」） | 关键约束语句近逐字保留 |

**未发现** 向导链（GuideOverlay / guide.ts / orchestrator.* / orchestrator_graph / orchestrator_agent / dispatch / orchestrator_session）与 Alethicode 任何文件存在 reused 级对应。

---

## 5. 结论复述（按「真正源自 vs 仅参考」口径）

| 链路 | 判定 | 一句话依据 |
| --- | --- | --- |
| 向导链（GuideOverlay → OrchestratorGraph） | **仅参考**（reimplemented） | 无移植声明、无逐段对应、无 reused 证据；继承的是「LangGraph 编排 + 画像注入 + 防泄答 + failure bucket + trace」的工程思想 |
| 导学工作流链（训练场 6 阶段卡） | **真正源自**（adapted，含 reused 常量） | 三处模块注释自证移植，`PHASE_TRANSITIONS` 逐字一致，卡片字段集对应 |
| 答疑链（百晓工坊 ask） | **参考为主、局部承袭**（reimplemented，prompt 纪律 adapted） | 课件页码引用纪律与防泄答条目可对应；三审由 LLM 审计重写为确定式规则；图解/沙箱/辩论为新增 |

---

## 6. 未解决疑点

1. **qa-graph 为范围外来源**：grounding/幻觉维度的 Alethicode 母本在 `services/qa-graph/app/nodes/`（AGENTS.md「Alethicode 来源模块」明示），不在本次指定读取范围（aitutor / tutor-graph / UnifiedAgentPanel）内。本审计仅读取其两个文件头部以核实血缘方向，未做逐函数对照；若需把 6a 行升格为 adapted/reused 判定，需对 qa-graph 做专项对照。
2. **git 历史未核查**：按纪律仅做了只读静态对照，未运行 `git log --follow` 追溯文件首次提交说明；上表「移植声明」均来自源码内注释，无法排除注释与实际拷贝路径不一致的情况。
3. **`frontend/src/components/workshop/__tests__/` 未逐一审读**：测试文件不影响血缘判定，未计入矩阵。
4. **Alethicode `AITutorServiceImpl.java` 等 impl 大文件仅按符号清单核对**：`impl/` 下 8 个实现类（如 `AITutorWorkflowDomainServiceImpl.java`）体量大，本审计以接口名 + 目录职责为证据粒度，未逐方法对照；不影响「向导链无对应移植」结论，但其内部若有与 Pythontopia 工具函数同构的私有方法，可能遗漏个别 reused 片段。
