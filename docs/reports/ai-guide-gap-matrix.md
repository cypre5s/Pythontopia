# AI 学习向导差距矩阵（对标 Alethicode AI Tutor）

> 审计日期：2026-06-12 ｜ 配套文档：`docs/reports/ai-guide-alethicode-lineage.md`（血缘矩阵，含读取范围与判定标准）
> 排序规则：P0 在前（影响主线演示正确性 / 教育目标 / 钱白花），P1 在后（体验与能力差距）。每条给出现状证据（路径 + 符号）、期望行为、建议改法、优先级。
> 四个聚焦方向：**A = packId=43《Python语言基础》本地化；B = 健壮性；C = 表达多样性；D = 与百晓工坊（Workshop）联动**。

---

## P0 差距

### GAP-A1 ｜ 路径拆解只把前 24/65 个 KC 喂给 LLM，后期章节知识点系统性失明

- **方向**：A（packId=43 本地化）
- **现状证据**：
  - `services/pythontopia-graph/pythontopia_graph/agents/orchestrator_agent.py` `_decompose_messages()`：`allowed = ", ".join(req.course_kc_index[:24]) or "（空，请勿编造 KC）"` —— 允许 KC 列表**头部截断到 24 个**。
  - 课程底座共 65 个 KC：`frontend/src/data/kcNames.ts` `KC_NAMES`（`PY43-KC-001` ～ 65 项，文件头注释「packId=43，65 KC，见 AGENTS.md 硬约束」）。
  - 传入顺序来自 `frontend/src/components/hud/GuideOverlay.vue` `send()`：`courseKcCodes: learning.kcs.length ? learning.kcs : chapterKcs` —— 即编号顺序，循环（PY43-KC-031 起）、函数、文件等中后期 KC 恰好排在第 24 位之后。
- **影响**：学生说「我想学循环 / 函数 / 文件读写」时，decompose 的 LLM 在允许列表里看不到对应 KC，只能回落 `_select_kcs()` 的兜底（`allowed[:2]` = 第 1 章绪论 KC），计划锚定错章节；`_ground_kcs()` 又会把 LLM 自行写出的合法后期 KC 保留（在 `allowed_set` 全集内），造成「prompt 白名单」与「校验白名单」口径不一致。
- **期望行为**：LLM 在拆解时能看到与学生目标相关的全部候选 KC（带中文名），且 prompt 白名单与 `_ground_kcs` 校验集口径一致。
- **建议改法**：把 `[:24]` 截断改为「按 `current_chapter` ± 1 章窗口 + `decaying_kcs/recent_failures` 强制并入」的相关性筛选；prompt 中以 `code=中文名` 形式注入（中文名可由 Java 侧随 `course_kc_index` 一并下传），并在注释里标明与 `_ground_kcs` 的口径关系。
- **优先级**：P0

### GAP-A2 ｜「测」步路径规划用写死的假快照，与该生真实掌握度无关

- **方向**：A（本地化 / 真数据红线）
- **现状证据**：`services/pythontopia-graph/pythontopia_graph/agents/dispatch.py` `_dispatch_path_planner()`：`PathPlannerRequest(user_id=1, pack_id=43, profile=PlannerProfileSnapshot(has_profile=False), kcs=kc_snapshots)`，其中每个 `PlannerKcSnapshot(kc_code=…, mastery_score=0.5, unlocked=True)` —— user_id 写死 1、掌握度统一 0.5、画像声明不存在。注意同文件 `_dispatch_resource(self, step, user_id, …)` 已经收到真实 `user_id`，而 path planner 分支签名 `_dispatch_path_planner(self, step, started)` 根本没接收它。
- **影响**：向导计划「测」步的 `PathPlannerAgent.plan()` 输出（`goal_summary`、节点数）对所有学生一样，违反 AGENTS.md「不做纯前端假数据冒充真实业务数据」的精神（这里是服务内假输入），也让 `dispatch_results` 里的「路径规划 OK」徽章具有误导性。
- **期望行为**：用真实 `user_id` + 该生各 KC 真实掌握度（来自后端 KC 掌握度 / ReviewSignal）做路径规划；拿不到数据时如实降级并在 summary 里说明「未接入掌握度，给出通用顺序」。
- **建议改法**：`_dispatch_path_planner` 增加 `user_id` 形参（调用点 `dispatch()` 已有）；掌握度经请求体下传（`OrchestratorRequest` 增加 `kc_mastery: dict[str, float]`，由 `OrchestratorService` 从后端聚合，模式同 `decayingKcs()`），Java 侧聚合失败降级为空 dict 并标注。
- **优先级**：P0

### GAP-B1 ｜ Java SSE 总闸 120 秒 < 编排最坏耗时，长任务必然中途断流

- **方向**：B（健壮性）
- **现状证据**：
  - `backend/src/main/java/com/pythontopia/orchestrator/OrchestratorService.java` `turn()`：`new SseEmitter(120_000L)` —— 整条 SSE 120s 总闸。
  - `services/pythontopia-graph/pythontopia_graph/agents/dispatch.py` `_GOVERNANCE_TIMEOUT_S = 120.0` —— dispatch **单个子步**就允许跑满 120s；`graph/orchestrator_graph.py` `_node_dispatch` 是两批执行（watch/learn/assess 并发后再跑 practice），加上 understand / decompose / extract_profile / compose 至少 4 次 LLM 调用。最坏路径 ≫ 120s。
  - 对照：Alethicode 用异步 run + 事件读取（`/home/cypress/Alethicode/backend/.../aitutor/runtime/RedisStreamRunEventReader.java`、`graph/TutorGraphClient.java` `createRun()`/`getRunEvents()`），长任务不受单连接超时约束。
- **影响**：provider 慢时浏览器侧流被 Java 掐断（`failAssistant` 报「AI 助教请求失败」），而 Python 图继续跑完所有 LLM 调用——学生看到失败、token 钱照花、产物丢弃。
- **期望行为**：编排端到端预算 < SSE 总闸，且超预算时优雅收尾（发 `error` + 已完成步骤的部分 result）。
- **建议改法**：在 `OrchestratorGraph._invoke` 外层加 `asyncio.wait_for` 总预算（如 100s），超时发部分结果；`_GOVERNANCE_TIMEOUT_S` 按「单步 ≤ 总预算 / 步数」收缩（如 45s）；或将 Java `SseEmitter` 超时上调并补心跳（见 GAP-B3）。
- **优先级**：P0

### GAP-B2 ｜ 向导会话记忆 / 计划 / 画像信号全在 Python 进程内存，重启即蒸发且不可审计

- **方向**：B（健壮性）
- **现状证据**：
  - `services/pythontopia-graph/pythontopia_graph/graph/orchestrator_session.py`：模块级 `_STORE: dict[str, SessionState]`（文档自述「进程内单 worker 会话记忆」），`append_turns` / `set_plan` / `merge_profile_signals` 全部只写内存。
  - `backend/.../orchestrator/OrchestratorService.java` 类注释自述：「无 Repository——会话记忆在 Python 服务侧」。
  - checkpointer 默认内存：`graph/orchestrator_graph.py` `default_checkpointer()` 默认 `MemorySaver`，postgres 是可选且失败静默降级。
  - 对照组一（Alethicode）：`AITutorSessionDomainService.java` + `AiTraceService.recordTrace()`（`ai_workflow_event` 落库）+ `WorkflowCheckpointService.workflowCheckpointRestore()`。
  - 对照组二（本仓答疑链自己都有）：`backend/.../tutor/TutorService.java` `persistSession()` → `TutorSessionRepository.insert(...)`；导学工作流也有 Java 投影（`graph/tutor_workflow_session.py` 注释「Java 侧另有持久化投影（tutor_workflow_session/card 表）」）。
- **影响**：graph 服务重启（部署 / 崩溃）后学生的学习计划与「对话即画像」积累全部丢失；negotiate 意图依赖 `get_plan(session_id)`，丢失后「太难了」只会被当成新规划而非调整既有计划；教师端 / 复盘无任何向导对话审计数据。
- **期望行为**：向导多轮对话、当前 plan、画像信号有持久化与恢复路径，与答疑链同等待遇。
- **建议改法**：最小改法是复用答疑链模式——`OrchestratorGraphClient` 加 `onResult` 回调（同 `TutorGraphClient.askStream` 的 `Consumer<String> onResult`），Java 落 `orchestrator_session` 表（user_id / session_id / turn / plan jsonb / profile_signals jsonb）；Python 冷启动时优先信任客户端 history（该回退已存在：`orchestrator_graph._invoke` 的 `server_history if server_history else list(req.history)`），并补一个 GET 历史端点（见 GAP-C3）。
- **优先级**：P0

### GAP-B6 ｜「对话即画像」信号不回写后端 8 维画像，闭环断裂

- **方向**：B（健壮性 / 教育闭环），兼属 A（画像本地化）
- **现状证据**：
  - 抽取与展示存在：`agents/orchestrator_agent.py` `extract_profile()`（维度限定 `PROFILE_DIMENSION_KEYS` 8 个 snake_case key，与 `learner_profile.dimensions` 命名一致）；`GuideOverlay.vue` 渲染「已随学更新画像：…」（`data-testid="guide-profile-updates"`）。
  - 但落点只有 Python 进程内：`graph/orchestrator_graph.py` `_persist()` → `orchestrator_session.merge_profile_signals()`；全仓检索 `pythontopia_graph` 无任何对 `/api/learner/profile` 的回写调用；`OrchestratorGraphClient.java` 只转发 SSE，不解析 result（无 `onResult` 回调）。
  - 对照：后端被动画像的正规入口是 `backend/.../learner/profile/service/PassiveProfileService.java`（经 `LearningEvent` 监听更新 dimensions，AGENTS.md 约定）；Alethicode 侧画像信号经 `LearnerProfileProjector.persistSnapshot()` 落 `ai_learner_profile_snapshot`。
- **影响**：向导告诉学生「已随学更新画像」，但 `PassiveProfileService.profileBrief()`（答疑链、工作流链的画像源）永远看不到这些信号——既是功能性谎言，又浪费了每轮一次的 `extract_profile` LLM 调用。
- **期望行为**：置信度达阈值的 `ProfileSignal` 进入 `learner_profile.dimensions` 被动更新管道，向导/答疑/工作流共享同一份画像。
- **建议改法**：Java 侧 `OrchestratorGraphClient` 增加 result 解析回调，把 `profile_updates`（confidence ≥ 0.6）转发为 `LearningEvent`（新增事件类型 `GuideProfileSignal`），由 `PassiveProfileService` 消化；前端文案在未落库前改为「本轮识别到画像线索」以免误导。
- **优先级**：P0

### GAP-D1 ｜ 向导与工坊 PLAN 各用一个 sessionId，同一学生两份互不相通的「学习总管」

- **方向**：D（工坊联动）
- **现状证据**：
  - 向导：`frontend/src/stores/guide.ts` `readSessionId()`（localStorage key `pyt_guide_session_v1`，跨页面持久）。
  - 工坊 PLAN：`frontend/src/components/workshop/WorkshopChat.vue` 行 293 `const conversationId = ref<string>(genId())`（每窗口随机），行 1083 体内 `sessionId: conversationId.value`，与向导走**同一端点** `/api/orchestrator/turn/stream`（行 280 注释「与 GuideOverlay 同端点，独立实例避免串台」）。
  - 服务端按 session 分仓：`graph/orchestrator_session.py` `_STORE[session_id]`；协商依赖会话内计划：`graph/orchestrator_graph.py` `_node_build_plan` 中 `stored_plan = await get_plan(req.session_id)`。
- **影响**：学生在工坊里让总管排了计划，回到向导说「太难了，换个例子」——向导会话里 `get_plan` 为空，协商分支失效，重新生成一份不相干的计划；`merge_profile_signals` 的画像积累同样分裂成两份。多智能体协同的「单一总管」叙事在演示中会穿帮。
- **期望行为**：同一学生在向导与工坊面对的是同一个总管会话（至少同一份当前计划与画像信号）。
- **建议改法**：会话主键改为服务端派生——`OrchestratorGraphClient.turnStream` 已有兜底 `"u" + userId + "-" + …`，可直接统一为 `"u{userId}"`（计划与画像按学生归并，对话 turns 仍可按前端 sessionId 分线程）；或前端让 WorkshopChat 复用 `useGuideStore().sessionId`。
- **优先级**：P0

### GAP-D2 ｜「学」步生成的资源用完即弃：不落库、无 resource_id、工坊资源库里看不见

- **方向**：D（工坊联动）
- **现状证据**：
  - `agents/dispatch.py` `_dispatch_resource()`：直接 `agent.design(request, evidence)` 拿 `ResourceDraft`，只把 `title/summary/type/section_count` 摘要塞进 `DispatchResult.artifact`，**草稿本体被丢弃**。
  - 持久化节点存在但不在此路径：`nodes/output_persistor.py` 的 `build_response` 仅被 `graph/resource_graph.py` 引用（工坊 `/api/workshop/resources/stream` 流水线）；全仓检索确认 dispatch 路径无其他 persist 调用。
  - 前端表现：`GuideOverlay.vue` 的 `guide-dispatch__chip` 只显示「讲义设计师 ✓」+ title hover；对照工坊链 `WorkshopChat.vue` 资源流结束后可经 `getWorkshopResource` / `ResourceDetailPanel.vue` 打开正文。
  - 对照（Alethicode）：每张卡经 `nodes/projection.py` `persist_projection` 落库，`UnifiedAgentPanel.vue` 时间线可回看、可 `@card:<id>` 引用。
- **影响**：向导计划「学」步宣称「已生成《XXX》讲义」，学生却没有任何入口读到这份讲义（点「去书房」看到的是资源库存量，不含本次产物）——LLM 成本白花，且涉嫌「假完成」。
- **期望行为**：dispatch 产出的资源与工坊资源同库同详情页，DispatchResult 携带 `resource_id`，计划步可直接「查看产物」。
- **建议改法**：`_dispatch_resource` 改走 `resource_graph` 的「design → 三审 → persist」全流程（治理包装保留）；`DispatchResult.artifact` 增加 `resource_id`；`GuideOverlay.vue` 的 chip 升级为可点击（路由 `/workshop` 并经 `openResourceId` 打开详情）。
- **优先级**：P0

---

## P1 差距

### GAP-A3 ｜ 向导 prompt 只给 KC 编码不给中文名，回复文案依赖前端兜底替换

- **方向**：A（本地化）
- **现状证据**：`agents/orchestrator_agent.py` `_decompose_messages()` 仅注入 `允许 KC 列表: PY43-KC-0xx, …`（裸编码）；`_default_detail()` 直接把编码拼进文案（`"，覆盖 " + "、".join(kcs)`）。前端用 `frontend/src/data/kcNames.ts` `humanizeKcText()` 在 `GuideOverlay.vue`（title/detail/content 渲染处）做兜底替换。
- **影响**：LLM 在不知道编码含义的情况下选 KC，准确率受损；任何未经 `humanizeKcText` 的出口（如 `dispatch_results.summary`、SSE trace note）仍会把 `PY43-KC-031` 暴露给学生。
- **期望行为**：LLM 看到 `PY43-KC-031=for 循环与遍历` 式映射；服务端产出的面向学生文案直接使用中文名。
- **建议改法**：`OrchestratorRequest` 的 `course_kc_index` 升级为 `[{code, name}]`（Java 侧从 `course_kc.kc_name` 取，向后兼容旧字符串），prompt 与 `_default_detail` 用中文名渲染、校验仍用 code。
- **优先级**：P1

### GAP-A4 ｜「看」步标题承诺 B 站视频，dispatch 实际产出的是故事板脚本

- **方向**：A（本地化 / 诚实性），兼属 C
- **现状证据**：`agents/orchestrator_agent.py` `_DEFAULT_TITLE["watch"] = "看视频：B站精选讲解 + 微课"`、`_default_detail()` watch 模板「观看 B站精选讲解视频与微课」；而 `agents/dispatch.py` `_resolve_resource_type()` 对 watch 步返回 `"STORYBOARD"` → `StoryboardAgent` 生成分镜脚本，`DispatchResult.summary` 报「已生成「…」(STORYBOARD)」。真实视频能力在别处：`WorkshopChat.vue` 引用 `BilibiliVideoCards.vue`（VIDEO 意图）与 lecture 面板。
- **影响**：计划卡说「看视频」、协同徽章说「已生成故事板」、点「去上课」到 lecture 面板——三处口径不一，学生困惑；故事板这次生成同样不落库（GAP-D2）。
- **期望行为**：watch 步要么真正挂接视频检索（按 KC 取 B 站精选 / 微课资源），要么文案如实改为「故事板预习脚本」。
- **建议改法**：优先把 watch 步 dispatch 改为调用课程视频检索（零 LLM、按 `step.kc_codes` 查视频表），artifact 带视频列表；fallback 才用 StoryboardAgent 且 summary 写明「视频暂缺，已生成故事板脚本代替」。
- **优先级**：P1

### GAP-B3 ｜ 编排 SSE 无心跳：dispatch 单步长静默期易被代理/浏览器掐线

- **方向**：B（健壮性）
- **现状证据**：`routes/orchestrator.py` `_orchestrator_stream_generator()` 只产事件不产 ping；对照同仓答疑链 `routes/tutor.py` 的 `_SseHeartbeat`（`_HEARTBEAT_S = 10.0`、`_PING = ": ping\n\n"`、`_pings_while()`）。dispatch 子步之间靠 trace/STATE_DELTA 续命，但单个 LLM 子调用最长 120s（`_GOVERNANCE_TIMEOUT_S`）期间无任何字节下行。
- **期望行为**：与答疑链一致的 ≤10s 心跳。
- **建议改法**：把 `_SseHeartbeat` 抽到公共模块（或直接在 `_orchestrator_stream_generator` 的 `queue.get()` 上加 `asyncio.wait_for(timeout=10)` 超时发 ping）。
- **优先级**：P1

### GAP-B4 ｜ 向导回复的安全/接地结果只标注不处置，前端也不渲染

- **方向**：B（健壮性 / 防泄答）
- **现状证据**：
  - `graph/orchestrator_graph.py` `_assemble()`：`scan_safety(reply_md)` 结果仅写入 `safety_result`，**reply_md 原样下发**；`grounding_result` 同理（仅 `UNKNOWN_KC` 标注）。
  - `agents/orchestrator_agent.py` `SAFETY_KEYWORDS` 只有 4 词，远窄于 `nodes/tutor_review.py` `safety_review()`（泄答 + 歧视 + PII 正则 + 危险代码）。
  - 前端：`frontend/src/api/orchestrator.ts` 定义了 `safety_result/grounding_result`，但 `GuideOverlay.vue` 与 `guide.ts` `finalizeAssistant()` 均不消费这两个字段。
  - 对照：答疑链有完整处置——`routes/tutor.py` 三审失败触发 `_build_revise_prompt` critique→revise，修订稿重跑三审才替换。
- **期望行为**：向导回复至少过答疑链同款 `safety_review`；失败时遮罩/修订而非照发。
- **建议改法**：`_node_compose` 后调用 `tutor_review.safety_review(reply_md)`；失败走一轮有界修订（复用 `_build_revise_prompt` 模式），仍失败则以确定式安全文案替换；前端在 `safety_result.passed=false` 时显示提示徽标。
- **优先级**：P1

### GAP-B5 ｜ 刷新页面丢整条向导线程：前端不持久化消息，服务端历史也没有读取端点

- **方向**：B（健壮性），与 GAP-B2 配套
- **现状证据**：`frontend/src/stores/guide.ts` 只持久化 `sessionId`（`SESSION_KEY = 'pyt_guide_session_v1'`），`messages` 仅内存；`routes/orchestrator.py` 只有 `POST /agents/orchestrator/turn(/stream)`，没有按 session 读历史的 GET 端点（Python 侧 `orchestrator_session.get_history()` 已有现成函数，但无人暴露）；`backend/.../orchestrator/OrchestratorController.java` 同样只有 `turnStream`。对照：答疑链有 `TutorService.listConversation()` 供前端回放，工坊有 `ChatHistoryRail.vue` 历史回看。
- **期望行为**：重开页面后向导能恢复最近 N 轮对话与当前计划。
- **建议改法**：补 `GET /agents/orchestrator/session/{session_id}/state`（history + plan + profile_signals，Python 三个 getter 现成）→ Java 代理 → `guide.ts` `openPanel()` 时拉取回放；中长期与 GAP-B2 的落库方案合并。
- **优先级**：P1

### GAP-C1 ｜ 欢迎语与建议 chips 全静态，错失「记忆唤醒」开场

- **方向**：C（表达多样性）
- **现状证据**：`frontend/src/stores/guide.ts` `WELCOME` 常量（固定一句「要不要我带你做第 1 章第一步？」，连章节都写死「第 1 章」）；`GuideOverlay.vue` `suggestions` 计算属性只有两组固定数组（needsUserInput 与否各 4 条）。对照：Alethicode `/home/cypress/Alethicode/backend/.../aitutor/profile/AITutorWelcomeService.java` `getWelcome()` 按 `ai_learner_memory`（error_pattern、confidence>0.5）生成 greeting / starter_actions / memory_tags，`UnifiedAgentPanel.vue` `welcomeData` 渲染记忆标签。
- **期望行为**：开场按「主线进度 + 到期 KC + 易错记忆」生成（如「上次 while 循环卡住了，要不要先补练？」），建议 chips 跟随当前计划状态。
- **建议改法**：零 LLM 即可——Java 新增 welcome 端点聚合 `mainline` 进度 + `BugMineService.decayingKcCodes` + `PassiveProfileService.profileBrief`，模板化生成欢迎语与 3-4 个动态 chips；`guide.ts` `ensureGreeting()` 改为异步拉取、失败回落现有静态 `WELCOME`。
- **优先级**：P1

### GAP-C2 ｜ 向导回复人设单一，不复用答疑链已有的「因材施教指令组」

- **方向**：C（表达多样性）
- **现状证据**：`agents/orchestrator_agent.py` `_reply_messages()` 固定人设「语气像耐心的班主任」，画像仅以原文 `学生画像: {brief}` 注入，无任何风格分支。对照组一（本仓答疑链）：`agents/tutor_agent.py` `_personalization_directives()` 按认知风格（`_STYLE_DIRECTIVES` visual/logical/trial）/ 知识基础三档（`_KNOWLEDGE_DIRECTIVES`）/ 目标 / 偏好 / 易错 / 情绪生成至多 6 条独立 system 指令，外加 `personalization_note()`「为你定制」说明。对照组二（Alethicode）：`learner_block.py` `assemble_learner_block()` 的 teaching_style prompt、`rlhf/PromptVariantSelector.java` `selectVariant()`（按反馈学习的 prompt 变体）。
- **期望行为**：向导按画像调讲法（视觉型先给结构、挫败时先肯定再安排），回复带「为你定制」可见说明。
- **建议改法**：把 `TutorAgent._personalization_directives` / `personalization_note` 抽到共享模块（如 `agents/personalization.py` 旁），`_reply_messages` 注入同款指令组；`OrchestratorResult` 增加 `personalization_note` 字段、`GuideOverlay.vue` 渲染。
- **优先级**：P1

### GAP-C3 ｜ 兜底计划模板四句话写死 + 协商只有 4 类关键词，负反馈不积累

- **方向**：C（表达多样性）
- **现状证据**：`agents/orchestrator_agent.py` `_DEFAULT_TITLE` / `_default_detail()`（watch/learn/practice/assess 各一条固定模板，provider 离线或 LLM 输出不全时每轮长一样）；`graph/orchestrator_graph.py` `_negotiation_kind()` 仅 lower/raise/skip/swap_example 四类、`_annotate()` 只是在 detail 后拼一句括号注记，计划结构不变；没有任何「学生不满意 → 策略调整记录」的回流（对照 Alethicode `profile/StrategyFeedbackService.java`、`policy/ContextualBanditReranker.java` `rerank` 的反馈学习，及 `UnifiedAgentPanel.vue` `submitFeedback` 三键反馈直达后端）。
- **期望行为**：兜底模板按 KC/章节有变化；协商动作真正改变步骤内容（如 swap_example 触发资源重生成）；「太难了」类信号写入学习事件供画像与后续规划消费。
- **建议改法**：模板池按章节主题参数化；`_negotiate` 的 lower/raise 同步调整 `kc_codes`（如 lower 时收窄到单 KC）并对受影响步骤重新 dispatch；新增 `LearningEvent`（negotiation kind + step phase）回流 `PassiveProfileService`。
- **优先级**：P1

### GAP-D3 ｜ 计划步「前往」深链丢上下文：到工坊后 KC 锚定与意图全部重来

- **方向**：D（工坊联动）
- **现状证据**：`GuideOverlay.vue` `panelRoute()` 对 `tutor`/`workshop` 一律返回裸 `'/workshop'`（不带 query）；`goToPanel()` 仅 `router.push(route)`。而工坊侧已具备承接能力：`WorkshopChat.vue` 的 `ComposerSubmitPayload` 支持 `manualKcCodes` / `forcedIntent` / `contextHint`（文件头注释 §0 分发表）。计划步本身有 `step.kc_codes` / `step.resource_type` / `step.title`（`schemas/orchestrator.py` `PlanStep`），全部在跳转时丢弃。
- **影响**：学生点「去百晓工坊」后要自己重新描述需求、重新选 KC——「总管派工单、工坊接单」的协同叙事断在最后一米。
- **期望行为**：深链携带步骤上下文，工坊进门即预填（如自动锚定 `PY43-KC-031` + 预选 HANDOUT 意图 + 预填标题）。
- **建议改法**：`panelRoute` 改为 `/workshop?kc=…&intent=…&hint=…`；WorkshopPage 挂载时解析 query 注入 ChatComposer 初值（只预填不自动发送，保留学生确认权）。
- **优先级**：P1

### GAP-D4 ｜ 向导消息流缺少逐条反馈通道，工坊已有的 AnswerFeedback 没有接到向导

- **方向**：D（工坊联动），兼属 C
- **现状证据**：工坊答疑消息有 `frontend/src/components/workshop/AnswerFeedback.vue`（107 行，三键反馈组件）；`GuideOverlay.vue` 的助手气泡没有任何反馈控件（模板中仅 plan/clarify/dispatch/profile 四个附件块）。对照 Alethicode `UnifiedAgentPanel.vue` `submitFeedback`（每张卡片 helpful/unhelpful/confusing + `feedbackMap` 状态）与 `events/LearningEventPublisher.java` 事件回流。
- **影响**：向导（学生最高频的 AI 触点之一）产生不了任何质量反馈数据，规划好坏无从度量，也无法喂给 GAP-C3 的策略调整。
- **建议改法**：在 `GuideOverlay.vue` 助手气泡尾部复用 `AnswerFeedback.vue`；反馈经 `postLearningEvent`（`frontend/src/api/learningEvent.ts`，WorkshopChat 已在用）落 `LearningEvent`，标注 sessionId + 该轮 plan 摘要。
- **优先级**：P1

---

## 汇总

| 编号 | 方向 | 一句话 | 优先级 |
| --- | --- | --- | --- |
| GAP-A1 | 本地化 | decompose 允许 KC 截断 24/65，后期章节失明 | P0 |
| GAP-A2 | 本地化 | 测步路径规划用 user_id=1 / mastery=0.5 假快照 | P0 |
| GAP-B1 | 健壮性 | SSE 120s 总闸 < dispatch 最坏耗时，断流且钱白花 | P0 |
| GAP-B2 | 健壮性 | 向导会话/计划/画像信号仅进程内存，重启蒸发 | P0 |
| GAP-B6 | 健壮性 | 对话即画像不回写 learner_profile.dimensions，闭环断裂 | P0 |
| GAP-D1 | 工坊联动 | 向导与工坊 PLAN 双 sessionId，计划/画像分裂 | P0 |
| GAP-D2 | 工坊联动 | dispatch 生成的资源不落库、学生无入口可看 | P0 |
| GAP-A3 | 本地化 | prompt 只给 KC 编码不给中文名 | P1 |
| GAP-A4 | 本地化 | 看步承诺 B 站视频、实际产故事板 | P1 |
| GAP-B3 | 健壮性 | 编排 SSE 无心跳 | P1 |
| GAP-B4 | 健壮性 | 安全/接地只标注不处置、前端不渲染 | P1 |
| GAP-B5 | 健壮性 | 刷新丢线程、无历史读取端点 | P1 |
| GAP-C1 | 表达多样性 | 欢迎语/建议 chips 全静态 | P1 |
| GAP-C2 | 表达多样性 | 回复人设单一，不复用因材施教指令组 | P1 |
| GAP-C3 | 表达多样性 | 兜底模板写死 + 协商不积累反馈 | P1 |
| GAP-D3 | 工坊联动 | 深链丢 KC/意图上下文 | P1 |
| GAP-D4 | 工坊联动 | 向导无逐条反馈通道 | P1 |

> 备注：本矩阵不含修复实施，全部为审计建议；任何改法落地前需按 AGENTS.md「先思考，再编码」原则评估对 R-14 主线演示链路的影响。
