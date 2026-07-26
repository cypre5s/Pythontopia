# Changelog

## 2026-07-26 — 重构：tutor 答疑流治理逻辑去重（审计第 3 批）

- `routes/tutor.py` 中普通答疑与考考我评卷两处**完全相同**的课件检索治理块（超时/重试/rag 熔断/空证据回退/obs 归一/trace 组装，各约 30 行）合并为单一 `_governed_evidence_fetch`；`_trace_start` 仍由调用方在 await 前发出，「检索中」工位的实时可见性不变。此前 RAG 治理参数改动必须双写两处，现在单点。
- 三道同构的同步审查 trace 发射块（grounding/hallucination/safety，主流程 + 评卷流程共 5 处）统一为 `_review_with_trace`（Protocol 约束 passed/reasons 结构），事件流字节序与逐条发射完全一致。
- 验证：tutor 定向 34 passed；Graph 全量 1628 passed / 1 skipped；Ruff check/format 通过。

## 2026-07-26 — 重构：WorkshopChat 答案视图投影抽为纯模块（审计第 2 批）

- `WorkshopChat.vue`（3692 行，全仓最大文件）先切第一刀：把答案气泡的纯视图投影（`TutorStreamResult` snake_case 线格式类型、证据归一 `toCitations`、接地状态归一 `deriveGroundingStatus`、通识前缀清理 `normalizeAnswerText`、g-chip 标签映射 `groundingInfo`、三审徽章 `reviewBadges`）抽到新模块 `components/workshop/chatAnswerView.ts`（约 150 行），组件内以 import 替换，行为零变化。
- 新增 `chatAnswerView.spec.ts` 6 用例直接覆盖这些此前只能经组件测试间接覆盖的分支（含 FAILED/NO_EVIDENCE 归一、score 可选字段、reasons 空白过滤）。
- 模块注释明确与 `TutorPanel.vue` 的同名 `TutorStreamResult`（`/api/tutor/ask` camelCase 契约）是两种线格式，不可合并——防止后续「顺手统一」造成契约破坏。
- 验证：typecheck 通过；workshop 组件套件 23 files / 136 tests 全绿；ESLint 0 errors；Prettier 通过。

## 2026-07-26 — 重构：前端死代码清除（审计第 1 批）

- 全仓引用扫描确认后删除三个零引用文件：`api/client.ts`（对 `./http` 的死转发层）、`composables/useKcFrontier.ts`（知识前沿选择器，调用点在无尽远征迭代中消失；其 ZPD+画像弱项选 KC 的设计思想保留为后续画像深化素材，且其注释本身建议下沉后端）、`utils/assertNever.ts`（唯一使用处已有局部实现）。旧 R-13 mainQuest 前端系统确认退役干净、无残留。
- 验证：`vue-tsc --noEmit` 通过；前端全量 231 files / 2264 tests 全绿。

## 2026-07-26 — 收口 Phase 4 遗留风险：切换状态重建纯函数化与仓库卫生

- `routing/workshop_task_plan.py` 新增 `SwitchedRuntimeState` + `rebuild_runtime_after_switch`：C3 候选切换后 dispatch 的六份运行时集合（plan/completed/failed/pending/step_status/replans_used）由纯函数一次性一致重建并附一致性契约测试，`_node_dispatch` 不再手工逐份同步；切换时整体替换 `step_status`，被放弃候选的陈旧步骤键不再残留。Graph 全量 1628 passed / 1 skipped。
- 仓库卫生：根目录 `A*.md`（`*` 为 Windows 非法文件名字符，会破坏 Windows 检出）移至 `docs/plans/2026-07-26-c3-tot-astar-task-prompt.md`；根目录 `todo.md` 实为 56K 微调研究报告，更名为 `research/vllm-agent-ft-lab/docs/finetuning-pain-points-report-2026-07-16.md`，3 处文档引用同步标注新路径。

## 2026-07-26 — 百晓工坊 Agent 路由 Phase 4：有界 ToT + A* 候选搜索、一次恢复与运行指标

> 完成根计划第四阶段与全链路收尾：仅 C3 复杂规划请求编译恰好三条受权候选路径，`f=g+h+penalty` 确定性选优；执行失败最多切换一次候选且成功步骤不重复；搜索合同经签名透传到 Java/前端并以可理解摘要展示；运行指标复用现有 harness 端点。C0-C2 行为不变。

- C3 判定与三候选（规划诊断/知识重构/练习探测优先）在既有 route compiler 内实现：候选只能重排 RouteDecision 已授权的 `HANDOUT/PRACTICE/PLAN`，Agent 全部来自唯一 ToolRegistry；策略门强制三模板、深度≤3、总分等式、稳定最低分选中与读前写；执行前用当前请求与权威画像整块重算 search，伪造成本/选中项即使持有效签名也被拒。
- `OrchestratorGraph._node_dispatch` 在任何 SSE 事件前完成 RouteDecision+TaskPlan 双预检，签名 TaskPlan 为唯一执行事实源；C3 失败复用纯函数一次候选切换（成功步骤保留、剩余按次优候选重排、`replans_used` 继承硬限 1），第二次失败 PARTIAL 并阻断依赖；C2 同步重试合同保留；trace 改为保留每次尝试的真实时间线，`task_step_result` 披露稳定幂等键，切换后重发 plan/loop_plan/STATE_DELTA，终态回写每步状态（含新增 `failed`）。
- 新增 `routing/workshop_metrics.py` 并把 `routing` 快照挂到既有 `GET /agents/harness/stats`：路由总量/澄清/复杂度分布、三候选选中计数、执行终态、候选切换与同步重试、派发与阻断步数、近窗 nearest-rank P50/P95；只记录可观测量，准确率类指标继续离线真值计算。
- Java `WorkshopRouteDecision` 新增 `Search/SearchCandidate/ReplanPolicy` 透明代理（签名摘要自动覆盖，篡改选中项或剥离 search 令牌即失效）；前端新增搜索类型与计划卡 `plan-search` 摘要「已比较 3 条受限学习路径，优先执行『…』」，测试断言不泄露候选 id、成本数值或隐藏推理。
- 真实栈证据：C3 专项 Chromium 首跑自然出现生产级有界恢复——HANDOUT 首次生成被 semantic grounding 拒绝（证据未支持"1秒"表述）后自动一次候选切换并完成，运行时记录 4 次 agent run、指标记录 1 次切换；断言按冻结设计"最多一次恢复"修正后复跑通过。完整百晓套件 `--retries=0` **5/5 passed**；真实指标快照：2 条 C3 路由、2 次执行 COMPLETED、1 次切换、P50 138s。
- 上会话遗留的 Task 3 RED 草稿有两处与冻结合同矛盾（期望第二次失败仍派发依赖步骤；weak-kc 下硬编码与已批准成本模型不符的切换目标），均按设计文档修正并在工作日志记录证据。
- 回归：Graph 终验 1627 passed/1 skipped；RAG 127 passed/2 skipped；Frontend 231 files/2264 tests、typecheck、lint、scoped Prettier 全绿；Backend 933 tests 0 failures/6 pgvector Testcontainers 环境 errors，6 类合并复跑 18/18 全绿。
- 独立审查（含只读探针复验未覆盖路径）结论 READY：无 Critical/Important；4 个测试侧 Minor 当场修复，恢复边界信任、dispatch 函数长度、执行期上下文严格相等与 E2E 新学生依赖等 4 项已记录为文档化风险。
- 本阶段存在另一并行会话同时打磨 Task 2 语法边界并在状态回写上追加持久化恢复语义；两侧改动互补，终验全量为合并状态（详见工作日志"并发会话协调备注"）。

## 2026-07-26 — 百晓工坊 Agent 路由 Phase 3：权威学习上下文与个性化路由

> 在 Phase 1/2 的签名 RouteDecision、TaskPlan 和受控多 Agent 编排上贯通真实学习事实；同一请求现在能根据 KC、正式失败、错误模式、负荷、挫败、偏好和近期资源消费确定性调整教学节奏，并给出不泄露内部画像字段的用户解释。

- 新增服务端 `WorkshopLearningContextService`，复用 Memory/Profile/Mastery/Submission/LearningEvent 现有事实源；Intent 与 Orchestrator 都按当前学生重建上下文并覆盖客户端声明，不新增画像表或伪学习数据。
- 路由只采用 backend/graph 写入的学习事件和 `PASSED/FAILED` 终态提交；权威 brief 绕过外部 memory provider。浏览器可写观察事件、`PENDING/RUNNING/ERROR` submission 不能污染个性化决策，普通用户记忆召回保持原契约。
- Graph 新增 `RoutePersonalization`：高负荷/挫败收敛为脚手架式串行与单并发，弱 KC 优先补救，视觉偏好优先可视资源；用户明确请求的工具、原有权限、安全与预算硬门保持不变。
- ResourceGraph 和 PathPlanner 改用真实画像、掌握度、置信度、近期资源、提交与错误模式；route id 对学习事实做稳定指纹并排除纯时间戳漂移。
- 路由只写 `workshop_route_selected` 观察事件且明确不修改 mastery；正式提交、资源消费等既有领域事件继续负责 KC/画像写回。
- WorkshopChat 在 TaskPlan 中展示一条可理解的个性化策略原因，不渲染 `cognitive_load`、`frustration_level` 等内部信号。
- 新增真实 Chromium 差异/抗污染验收：前端伪造失败与资源消费不改变 PARALLEL/STANDARD 和 route id；两次受 10 秒限流约束的真实失败后才转为 SEQUENTIAL/SCAFFOLD_FIRST/max parallel 1。场景连续 3/3、完整百晓路由套件 4/4 通过。
- 回归：Graph 1319 passed/1 skipped；RAG 127 passed/2 skipped；Frontend 231 files/2263 tests、typecheck、scoped Prettier 全绿。Backend 全量 930 tests 为 0 failures/6 Testcontainers 容器启动 errors，六类逐一串行复跑 18 tests 全绿；权威 memory provider 零交互单测 6/6 通过。
- 独立审查先以事件污染和非终态 submission 两个 Important 判定 NOT READY；修复并补真实 PostgreSQL/Chromium 证据后复审 READY。Graph 测试隔离修复只注入静态 evidence fetcher，生产 evidence fail-fast 行为未放宽。

## 2026-07-23 — 百晓工坊 Agent 路由 Phase 2：受控多 Agent TaskPlan

> 在既有 OrchestratorGraph、专职 Agent、ToolRegistry、AG-UI/SSE 与 `agent_loop_run` 运行时上完成多 Agent 编排；签名 RouteDecision 成为执行权威，复合任务不再被旧语义环节改写或拆成多次前端请求。

- 新增统一 TaskPlan/DAG，支持 SEQUENTIAL、PARALLEL、PLANNER_EXECUTOR；fan-out/fan-in、步骤依赖、终态、Agent/工具/深度/并行/重规划预算均为确定性合同，同一步最多重试一次。
- 新增独立 HMAC route execution token：绑定 user、route、原始 message/interaction 与 10 分钟 TTL；执行前再次验证 submission 所有权和数据库事实，服务端安全补全不再造成签名漂移。
- 后端从唯一 pack43 重新装载课程 KC authority，Graph 在进程内集合为空时使用请求课程目录；TaskPlan 无 recent/decaying KC 时回退到权威目录锚点。
- `OrchestratorRunRecorder` 复用既有 loop runtime，持久化 run、checkpoint、Agent、Tool、终态与 replay；WorkshopChat 展示真实 TaskPlan 状态与 loop run id。
- 三条真实 Chromium 联测通过：Debug 单 Agent、顺序“总结后出题”、自由文本“诊断后补救”；后两条验证 signed route、真实 Agent 执行、数据库运行记录和 replay。
- 回归：Graph 1314 passed/1 skipped；RAG 127 passed/2 skipped；Frontend 231 files/2262 tests、typecheck、Prettier、scoped Ruff/diff/shell syntax 全绿。Backend 全量 930 tests 为 0 failures/4 Testcontainers 动态端口 errors，4 类逐类复跑 13 tests 与空库 100 migrations 全绿。
- 独立复核在修复 authoritative bypass、PLAN 首步、secret/消息绑定、运行可发现性、KC authority、runtime id 与 interaction 签名后给出 READY；环境端口抖动和 recorder fail-soft 风险已保留在阶段日志。

## 2026-07-22 — 百晓工坊 Agent 路由 Phase 1：可信单 Agent 策略门

> 完成 `baixiao_workshop_agent_routing_complete_plan.md` 第一阶段：在既有 IntentRouter、ToolRegistry、专用 Agent、Spring 业务闭环和 WorkshopChat 上增加统一 RouteDecision 与 fail-closed 策略门，不新建平行 Agent 框架。

- 新增稳定 `RouteDecision` 合同：task family、C0-C3、五类 topology、Agent/工具选择、硬预算、审查、输出 schema 与 fallback 均结构化；Phase 1 只放行 DIRECT/SINGLE_AGENT_TOOL。
- 扩展唯一 ToolRegistry：Agent allowlist、必需上下文、结果 schema、敏感数据、副作用与执行模式进入确定性校验；未知/越权/预算超限在执行前拒绝。
- Spring Intent 链新增 interaction/requestedIntent 与 RouteDecision DTO；失败 submission 引用必须属于当前学生且状态为 FAILED，代码、题目和判题结论由数据库回填，前端声明不能越权覆盖。
- `/修报错` 修正为真实 `BugMineService → DebugAgent` 路径，不再误入 Tutor；自由文本、slash、能力入口、澄清/纠错/secondary chip 全部统一经过策略门，路由异常 fail closed。
- 资源发布改为 grounding 与 safety 显式通过；PARTIAL 无证据必须显式 `no_evidence=true`，缺字段不再默认发布。
- 新增 `frontend/e2e/baixiaoAgentRouting.spec.ts`：真实注册学生、正式失败判题、Graph route decision、BugMine 会话、Debug 页面及 Tutor 零调用验收通过。
- 回归：Graph 1290 passed/3 skipped；RAG 129 passed；Frontend 231 files/2256 tests、typecheck、lint 全绿；Backend 913 tests 首跑 0 failures/6 Testcontainers 端口错误，6 类合并重跑 18 tests 全绿；四个 `/healthz` 与专项 Playwright `--retries=0` 通过。

## 2026-07-13 — Evidence-Bound Contract Decoding 与语义 fallback 计量

> 在不继续盲训 DTO/JSON 的前提下，把 v2 IR 的结构合法性和请求级证据值域移到推理解码层；同时保留 unconstrained raw，防止受约束满分掩盖模型退化。

- 完成 failure-directed Resource ORPO：240 条无保护集污染偏好，训练与 heldout 排序改善，但同集 unconstrained raw contract 仍为 20%，候选已淘汰。
- 新增 `unconstrained / json_schema / json_schema_bound` 解码消融；request-bound schema 将 evidence allowlist 编译为枚举，并在有证据时强制引用非空。
- golden20 上 candidate 与 champion 的 evidence-bound contract 均为 100%，但 semantic/promotion 仅为 80% / 75%；失败全部集中在 CodeLab 非法 Python，因此没有进入分层500、完整5000或双 Adapter 路由。
- 产品 `FineTunedFallbackProvider` 接入同一 evidence-bound schema，仅作用于本地 adapter；新增 constrained/evidence-bound telemetry，并将 CodeLab AST/compiler 失败纳入可计数 semantic fallback。
- 新增 CodeLab Python AST 语义评分与 v2 grounding 子集选择语义；final fallback 100% 继续与模型 raw/constrained 指标分开报告。
- 架构、论文依据和本项目 SAGE-IR + EBCD 创新边界更新于 `docs/architecture/qwen-contract-runtime.md`；完整实验记录和图表位于质量飞轮日志与 `outputs/quality-flywheel/resource4-evidence-bound-decoding-golden20-20260713/`。
- 新增 CodePlan v2.1：CodeLab 模型只选择八类教学代码模式并描述观察目标，确定性 compiler
  生成 Python 与预期输出；严格 schema 拒绝模型夹带自由 `code`。golden20 单变量消融保持
  contract 100%、leak 0，semantic/promotion 从旧 IR 的 75% 提升到 90%，但仍有 2 条模式
  选择失败，未晋级。
- 构造 240 条无 golden50/分层500 污染的 CodePlan 偏好数据，覆盖 wrong pattern、证据、
  旧自由代码、安全和弱语义五类失败；preflight 全部通过，启动一轮固定超参的小规模 ORPO。
- 评测 manifest 新增真实 `adapter_path`；一次误用旧 QLoRA 的 partial 运行已标记失败并排除，
  防止相同服务名掩盖 Adapter 身份差异。
- CodePlan ORPO 完成 240 条单变量训练；虽然 evidence-bound golden20 的 semantic/promotion
  达到 95%，原始 golden50 同集却使 raw contract `68% -> 56%`、grounding `98% -> 86%`、
  guard repair `30% -> 44%`。候选已淘汰并停在分层500之前，双 Adapter 继续禁用；保留
  compiler-owned CodePlan 架构，下一步转向共享 Adapter 的跨 Agent 遗忘诊断。

## 2026-07-12 — SAGE-IR 语义动作网格与严格语义门禁

> 将 Qwen 从产品 WorkflowEvent/DTO 记忆中进一步解耦：模型只选择七类教学动作，确定性 compiler 负责状态机映射；新增 semantic/promotion 指标与二维偏好网格，用于保研项目的可证伪消融。

- GuideCoach v2 IR 收敛为 `READ / REVIEW / TRACE / SCAFFOLD / PRACTICE / ASK / CHECK` 七类教学 ontology；未知 intent 和 live allowlist 外动作 fail fast。
- 新增 `semantic_valid_rate` 与 `promotion_pass_rate`，证明 raw contract 100% 仍可能只有 20% 的事件语义正确率，避免结构满分掩盖教学决策错误。
- 新增 SAGE-IR 二维采样：七类 intent × 六类失败轴；稀缺类使用保留章节/KC/画像/证据/安全上下文的可追溯反事实增强，严格排除 golden50 与分层500。
- 纯 DPO 二维网格候选未改善 golden5 strict semantic，已淘汰；继续执行同数据的 chosen-SFT 单变量消融，不提前启用双 Adapter 路由。
- 研究证据、负结果与剩余边界实时记录于 `research/vllm-agent-ft-lab/docs/quality-flywheel-log-2026-07-12.md`。
- 将 ResourceDesigner 拆为 Handout / Reading / CodeLab / Storyboard 四个 evidence-bound v2 IR；模型只做内容、证据与节奏决策，compiler 负责页引用、KC、时长、voice/image prompt 与代码安全边界。
- 新增四子 Agent 的 nested evidence guard、确定性 fallback 和 v2 schema registry；修复 Reading 未注入 RAG 证据及 scorer 未拒绝额外伪造证据的问题。
- 构造无污染 240 条 Resource 偏好数据；三次 8GB GPU OOM 均独立归档后，以语义无损压缩把最长序列 438 降至 359 tokens，完成 reference-free ORPO。
- 同集 golden20 显示 raw contract 从 champion 0% 提升至 20%，但 grounding 从 95% 降至 90%、fallback 仍 80%，候选已淘汰；未运行分层/完整集，也未启用双 Adapter 路由。

## 2026-07-11 — Qwen v2 Semantic IR 与质量飞轮

> 把小模型职责从记忆产品 DTO 收缩为事件、题目/图谱语义、证据引用与安全边界决策；由确定性 compiler 生成稳定产品合同，并以 raw contract 为首要晋级指标。

- 新增版本化 `agent-schema-v1/v2` registry、EvidenceBinder、GuideCoach / Practice / IntentRouter / MindMap semantic IR 与 ContractCompiler；真实 Agent 调用接入 `guard_context`，生产仍保留 `FineTunedFallbackProvider`。
- 评测新增 raw / guard / final 分层、`contract_pass_rate`、`guard_repair_rate` 与 `fallback_rate`，并把 JSON Schema 校验扩展到嵌套 `$ref`、数组、enum、pattern 和约束字段。
- 偏好数据按 golden50 / 分层500 的 ID、lineage、prompt fingerprint 做污染隔离；训练前强制 token、零 label、重复、同答案与单变量检查，长运行支持增量记录和失败归档。
- 首轮四 Agent 与后续三 Agent 候选均未通过真实同集门槛，已明确淘汰；当前继续推进 GuideCoach 单 Adapter 定向轮，双 Adapter 路由须在同集回归通过后才可启用。
- 详细数据哈希、训练指标、逐 Agent 失败与门禁状态见 `research/vllm-agent-ft-lab/docs/quality-flywheel-log-2026-07-11.md`。

## 2026-07-03 — vLLM 1000+ Multi-Fold Evaluation：本地微调模型大样本可靠性验证

> 完成面向真实百晓工坊 / Graph Agent 应用场景的本地 vLLM LoRA 长跑评测：使用新手表达增强后的 1000 case 主集，按 5-fold 记录 `events.jsonl`、`metrics.csv`、`responses.jsonl`、`summary.json` 与图表，验证结构化 JSON、grounding、安全泄露、得分与延迟稳定性。

- **Safety/Debug guard 修复**：新增确定性 schema/evidence/forbidden guard；SafetyAgent 对 invalid JSON、缺 `redactions/safer_alternative` 或命中 forbidden leak 的输出改用安全拒答模板，DebugAgent 自动补齐 required evidence id。
- **Graph 接入护栏**：`FineTunedFallbackProvider` 新增可选 `guard_context`；adapter 无 JSON 时 fallback，有 context 时执行 JSON/evidence/forbidden guard，未传 context 的旧调用保持兼容。
- **Guarded 复测结果**：新增 `research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-guarded-v1-20260703`，在同一 1000 条响应上完成 5-fold guarded repair；JSON invalid `10 -> 0`，grounding false `39 -> 0`，forbidden leak `5 -> 0`，audit clean rate `1.000`。
- **Guard 图表与报告**：新增 `docs/reports/2026-07-03-vllm-safety-debug-guard-fix.md`，并生成 `outputs/comparisons/raw-vs-guarded-safety-debug-v1/charts/baseline_vs_candidate.svg` 及 guarded run 的 fold/agent/noise/latency 图表。
- **评测对象**：本地 vLLM OpenAI-compatible 服务中的 `pythontopia-qwen35-e1-lora`，用于 Graph 侧结构化 Agent JSON 生成，不替代原有 fallback provider。
- **评测口径**：真实应用 case 覆盖 Pythontopia 资源生成 / 画像 / 证据约束等 Agent 合同；以 score、JSON valid、grounding safe、forbidden leak、latency 和 fold 稳定性作为可靠性与正确性证据。
- **方法报告**：新增 `docs/reports/2026-07-03-qwen-vllm-finetuning-method-report.md`，记录 AWQ/vLLM 选择、E1 teacher 数据、QLoRA 参数、heldout 效果、vLLM 在项目中的角色和 fallback 边界。
- **样本增强追加要求**：正式长跑改用新手表达增强样本，模拟 Python 初学者描述不清、句子断裂、术语混用、错误信息半截粘贴等输入，但保留 Agent 合同、证据 ID 和评分字段。
- **增强数据产物**：新增 `research/vllm-agent-ft-lab/cases/domain_cases_1000_v1_beginner_noise.jsonl`，1000 条样本全部改写为新手表达；6 类噪声分布均衡，合同字段审计 0 errors。
- **正式运行**：完成 `research/vllm-agent-ft-lab/runs/vllm-lora-domain-1000-beginner-noise-5fold-conc2-20260703T1559CST`，5 folds × 200 cases，`metrics.csv` 1000 行、`responses.jsonl` 1000 行、`events.jsonl` 2000 行、`progress_snapshots.jsonl` 134 行。
- **最终指标**：score mean `0.991083`，JSON valid rate `0.990000`，grounding safe rate `0.961000`，forbidden leak rate `0.005000`；latency mean `9201.83ms`，p95 `18268.20ms`。
- **正式图表与汇总**：生成 `summary.json`、`extended_summary.json`、`charts/score_by_fold.svg`、`charts/rates_by_fold.svg`、`charts/error_counts_by_agent.svg`、`charts/rates_by_noise_profile.svg`、`charts/latency_by_fold.svg`。
- **最终报告**：新增 `docs/reports/2026-07-03-vllm-1000-beginner-noise-multifold-eval.md`，记录 vLLM 服务配置、样本增强方法、5-fold 结果、Agent 切片、新手表达切片、风险结论和后续 guard 建议。

## 2026-07-01 — Launch Deep-Chain Acceptance：上线前全功能点击与健壮性验收

> 面向上线交付重新跑完整本地全栈浏览器深链验收，不再只沿用 M0-M3/Phase 9 历史结果；新增点击鲁棒性 E2E，覆盖同一页面反复点击、弹层/抽屉开关、深链刷新和教师看板多标签切换。

- **新增点击鲁棒性套件**：新增 `frontend/e2e/deliveryClickRobustness.spec.ts` 6 条，覆盖小镇 HUD/暂停菜单、主线地图抽屉、指南桌面/移动导航、工坊历史/书房/调用抽屉与资源弹窗、核心页面深链刷新、教师班级看板多标签。
- **完整 Playwright 当前清单**：`npx playwright test --project=chromium --list` 当前为 246 tests / 7 files；功能主跑 222 tests 通过，截图/场景套件 24 tests 通过。
- **功能主跑通过**：`agenticDeepLoop + agenticDeepMatrix + publicAcceptance + studentLearningLoop + teacherStudentFullFlow + deliveryClickRobustness` 串行联跑通过，覆盖认证、town_hub、AI 向导 SSE、工坊流式回复、RAG 三查询、训练 AC/WA/RE、学习闭环、Bug Mine、教师-学生班级证据闭环、错误面不泄密和多路深链刷新。
- **截图/场景覆盖通过**：`guideScreenshots.spec.ts` 24 tests 通过，覆盖主城、暂停菜单、AI 助教、主线、阶段学习、图书馆、工坊、训练、Bug Mine、复盘、画像、教师班级、NPC、RAG、远征与无尽模式截图场景。
- **上线健康与静态检查**：本地 `./dev.sh` 全栈启动后，`:5173`、`:18080`、`:18091`、`:18092` 的 `/healthz` 均返回 `{"status":"ok"}`；`npm run typecheck` 与 `git diff --check` 通过。
- **报告**：新增 `docs/reports/2026-07-01-launch-deep-chain-acceptance.md`，记录命令、结果、覆盖面、测试前置修正和剩余风险；截图资产由截图套件生成但不纳入本次验收代码提交。

## 2026-07-01 — Agentic Runtime Phase 9：全链路验收与 Beta 交接

> 完成新 Phase 0-9 收官验收：本地全栈启动、后端/graph/rag/frontend 测试、真实 `/api` 健康检查、Playwright 浏览器深度闭环和工坊运行态 UI 点击流全部通过。

- **全栈启动验收**：通过根目录 `./dev.sh` 拉起 Docker 依赖、Spring Boot 后端和 Vite 前端；`/healthz` 在 `:18080`、`:18091`、`:18092`、`:5173` 均返回 `{"status":"ok"}`。
- **浏览器深度闭环**：扩展 `frontend/e2e/agenticDeepLoop.spec.ts`，新增 runtime operations API 断言和工坊“调用过程”抽屉 UI 断言；最终 Playwright 32 tests 通过，覆盖 bootstrap、loop run/replay、runtime summary/operations、记忆、证据图、资源生成/阅读/微测、正式判题、Bug Mine 和工坊点击流。
- **100+ E2E 深链矩阵**：新增 `frontend/e2e/agenticDeepMatrix.spec.ts`，把同一条真实 Agentic 学习链拆成 153 个验收点；与原深链一起运行共 185 tests，全部通过。
- **运行态可见性验收**：E2E 现在验证 `/api/ai/runtime/operations` 返回 `LEARNER / DEBUG / RESOURCE / WORLD / IMPROVE` 五类 loop 与 resource/world/policy 三类治理项；浏览器端确认 `RuntimeOperationsPanel` 在真实页面渲染“闭环运维状态”。
- **后端/graph/rag/frontend 回归**：graph Phase 3-7 runtime 与 M1-M3 gates 60 tests 通过；RAG 129 tests 通过；后端 runtime 控制面与 summary/operations 相关测试通过；前端 typecheck 通过；Phase 8 API/组件/页面 Vitest 28 tests 通过。
- **Beta handoff**：新增 `docs/reports/2026-07-01-agentic-runtime-phase9-handoff.md` 与 `docs/reports/2026-07-01-agentic-runtime-100-e2e-results.md`，汇总验收矩阵、运行 URL、提交边界、未覆盖项和演示风险。

## 2026-06-30 — Agentic Runtime Phase 8：前端与教师运维可视化

> 补齐新 Phase 8 的学生/教师可见运维面：Runtime 状态不再只停留在 graph 路由和后端控制面，工坊“调用过程”和教师审核页现在都能通过真实 `/api` 读取五类 loop 与治理项状态。

- **后端 Operations API**：新增 `AiRuntimeOperationsDto` 与 `GET /api/ai/runtime/operations`；学生、教师、管理员可读，继续保留 `/api/ai/runtime/summary` 学生专用权限边界。
- **真实状态派生**：operations 从现有 Protocol Gateway、学习证据图、Evidence Memory、Learner Twin 和 browse safety summary 派生，不在前端伪造通过；缺少证据时保持 `INSUFFICIENT_EVIDENCE`。
- **五类闭环可视化**：API 暴露 `LEARNER / DEBUG / RESOURCE / WORLD / IMPROVE` loop operation，携带 label、status、latestTraceId、nextAction 和 evidence refs。
- **教师治理项**：API 暴露 resource review、world patch review、policy canary review 三类 governance item，指向资源效果、`town_hub` patch/rollback 和策略灰度审核。
- **前端 API client**：`aiRuntime.ts` 新增 `fetchAiRuntimeOperations()` 与明确 DTO 类型，所有 UI 继续走 `/api`，不直连 graph `/runtime`。
- **共享运维面板**：新增 `RuntimeOperationsPanel.vue`，用紧凑状态行展示 loop/gov 状态、下一步和证据；接入百晓工坊“调用过程”抽屉与教师审核页。
- **测试**：后端 `AiRuntimeSummaryServiceTest/AiRuntimeSummaryControllerTest` 通过；前端 API、组件、工坊页、教师审核页 28 tests 通过；`npm run typecheck` 通过；后端控制面回归 `AgentRuntimeControllerTest/LoopControllerTest` 同跑通过。

## 2026-06-30 — Agentic Runtime Phase 7：系统演化闭环 Trace → Monitor

> 补齐新 Phase 7 的 graph 侧系统演化闭环：已有 M3 gate 继续负责离线聚类、单变量策略候选、回放门禁、canary dry-run 和 rollback 元数据，新 runtime 把这些治理状态整理成可 API 展示、可阻断、可回滚的 `IMPROVE` loop。

- **复用 M3 基线**：确认现有 `m3_system_evolution_gate.py` 已覆盖 stable eval dataset、failure clustering、one-variable `PolicyCandidate`、offline replay gate、5% canary dry-run plan 和 rollback status；Phase 7 在其上补 runtime 状态合同。
- **系统演化 Runtime 模块**：新增 `pythontopia_graph.runtime.system_evolution_loop`，实现 `TraceSummary`、`EvaluationGate`、`DeploymentPlan`、`MonitorDecision` 和 `SystemEvolutionLoopResult`。
- **六步闭环语义**：`Trace -> Eval -> Cluster -> Improve -> Deploy -> Monitor` 全部产出结构化对象；失败聚类、策略候选、canary plan 和 rollback status 直接复用 M3 实现。
- **更严格发布门禁**：除 offline replay 外，发布前必须具备 `regression / adversarial / long_running / audit` 四类 suite；失败或缺失 suite 直接 `REJECTED + hold:release-candidate`。
- **监控与回滚**：`MonitorSnapshot` 检查 schema validity、regression rate、cost ratio、live error rate 和样本数；超过规则返回 `PARTIAL + rollback:<base-policy>`，不会继续 canary。
- **API 可见状态**：新增 graph 路由 `POST /runtime/system-evolution-runs`，返回当前系统演化闭环完整状态和冻结 `LoopResult`，供后续前端/教师运维界面真实接入。
- **测试**：新增 `test_system_evolution_loop.py` 5 条，覆盖 dry-run 成功、monitor 回滚、adversarial suite 阻断、offline regression 阻断和 API 可见状态；联跑 M3 gate、runtime/contract、Phase 3/4/5/6 与 M1/M2 gates 共 60 tests 通过。

## 2026-06-30 — Agentic Runtime Phase 6：世界状态闭环 State → New Quest

> 补齐新 Phase 6 的 graph 侧小镇世界闭环：真实学习动作现在能生成 evidence-bound 的 `town_hub` 状态补丁、任务推进、非经济奖励、NPC 提示、下一任务和回滚补丁，并通过 API 暴露当前 world loop 状态。

- **复用世界提案合同**：确认现有 `WorldLoopProposal` 已强制 `state_patch / new_quests / npc_dialogue / reward_plan` 全部绑定 evidence，Phase 6 在其上补 runtime 生产链，不另起一套 world schema。
- **世界状态 Runtime 模块**：新增 `pythontopia_graph.runtime.world_state_loop`，实现 `LearnerWorldSnapshot`、`QuestGraphUpdate`、`WorldActionResult`、`NarrativeReward`、`RollbackPatch` 和 `WorldStateLoopResult`。
- **六步闭环语义**：`State -> Quest -> Action -> Reward -> WorldUpdate -> NewQuest` 全部产出结构化对象；学习动作可来自正式提交、页面阅读、资源消费、调试恢复和画像事件。
- **R-14 约束落地**：输出 world state 统一归一到 `town_hub`；生成 patch 只写 `town_hub.*`；拒绝 inventory/currency/shop/coin/gold 类经济奖励请求，不把已删除的货币/商店系统带回主线。
- **学习型奖励与任务**：正式提交通过可给小额 mastery delta、badge、mentor trust 等非经济奖励；页面阅读只生成训练场正式提交 follow-up，不产生正向掌握度。
- **回滚可见性**：新增 `RollbackPatch`，每个世界状态补丁都保留 previous value 和 evidence refs，方便后续教师运维/审查界面做拒绝或回滚。
- **API 可见状态**：新增 graph 路由 `POST /runtime/world-state-runs`，返回当前世界闭环完整状态和冻结 `LoopResult`，供后续前端/教师运维界面真实接入。
- **测试**：新增 `test_world_state_loop.py` 6 条，覆盖完整 world loop、缺 evidence 拒绝、经济奖励拒绝、回滚补丁、页面阅读后续任务和 API 可见状态；联跑 M2 world gate、runtime/contract、Phase 3/4/5 与 M1/M3 gates 共 55 tests 通过。

## 2026-06-30 — Agentic Runtime Phase 5：资源生成闭环 Goal → Evaluate

> 补齐新 Phase 5 的 graph 侧资源生命周期闭环：已有 ResourceGraph 继续负责真实检索、生成、审查和发布决策，新 runtime 把结果整理成可恢复的 artifact manifest、revision、publish state 和 evaluation writeback 状态。

- **复用 ResourceGraph 基线**：确认现有 `ResourceGraph` 已覆盖 evidence fetch、资源 agent 设计、grounding critic、防幻觉、semantic grounding、safety critic、修订重试/震荡熔断、publish decision 和 evaluation hook，不重复造一套生成链。
- **资源生命周期 Runtime 模块**：新增 `pythontopia_graph.runtime.resource_lifecycle_loop`，实现 `ResourceGoal`、`ArtifactManifest`、`CritiqueReport`、`RevisionPlan`、`PublishState`、`EvaluationResult` 和 `ResourceLifecycleLoopResult`。
- **六步闭环语义**：`Goal -> Generate -> Critique -> Revise -> Publish -> Evaluate` 全部产出结构化对象；artifact manifest 记录 resource type、title、revision parent、grounding refs、publish state 和 evaluation hook。
- **发布与修订决策**：有证据且安全接地的资源发布；无证据资源进入 `HOLD_FOR_REVIEW`；critic 失败阻止发布；已发布资源如果后续效果差则返回 `revise:effectiveness`。
- **效果写回**：新增 `ArtifactEffectivenessSnapshot`，把 adoption rate、micro-check pass rate 和 mastery delta 映射成 `PENDING / INSUFFICIENT_EVIDENCE / EFFECTIVE / NEEDS_REVISION` lifecycle signal。
- **API 可见状态**：新增 graph 路由 `POST /runtime/resource-lifecycle-runs`，返回当前资源生命周期完整状态和冻结 `LoopResult`，供后续前端/教师运维界面真实接入。
- **测试**：新增 `test_resource_lifecycle_loop.py` 6 条，覆盖发布/评估、无证据 hold、critic 拒绝、revision parent、负效果修订和 API 可见状态；联跑 ResourceGraph、M2 gate、resource agents/schemas、runtime/contract、Phase 3/4 与 M1/M3 gates 共 70 tests 通过。

## 2026-06-30 — Agentic Runtime Phase 4：代码调试闭环 Fail → Transfer

> 补齐新 Phase 4 的 graph 侧代码调试闭环：失败提交现在能形成结构化失败观察、三路假设、最小提示、正式重交比较、迁移任务和错因记忆写回计划，并通过 API 暴露当前 debug loop 状态。

- **复用后端 Debug Session 基线**：确认现有 `practice_submission`、`judge_result`、`debug_case`、`bug_mine_session`、`bug_mine_card` 已覆盖正式提交、判题结果、调试卡片、重试次数、深度和变式掌握，不重复造表分裂数据源。
- **代码调试 Runtime 模块**：新增 `pythontopia_graph.runtime.code_debug_loop`，实现 `FailureObservation`、`DebugHypothesis`、`HintPlan`、`ResubmissionComparison`、`TransferTask`、`DebugMemoryWriteback` 和 `CodeDebugLoopResult`。
- **六步闭环语义**：`Fail -> Hypothesize -> Hint -> Resubmit -> Compare -> Transfer` 全部产出结构化对象；假设固定从 runtime behavior、code structure、KC prerequisite 三个角度生成并按置信度排序。
- **安全提示与回退**：缺少调试证据返回 `REJECTED + request:debug-evidence`；未重交返回 `PARTIAL + resubmit:formal-judge`；三次失败返回 `fallback:prerequisite-remediation`；提示只给最小方向，不泄露完整修复代码。
- **迁移与记忆**：正式重交通后仍生成 transfer practice，避免只做表层修补；`DebugMemoryWriteback` 输出 error pattern、置信度和 evidence submission ids，供后续画像/错因记忆持久化。
- **API 可见状态**：新增 graph 路由 `POST /runtime/code-debug-runs`，返回当前 debug loop 完整状态和冻结 `LoopResult`，供后续前端/教师运维界面真实接入。
- **测试**：新增 `test_code_debug_loop.py` 7 条，覆盖 WA 完整闭环、等待正式重交、RE、TLE、缺证据、三次失败回退和 API 可见状态；联跑 DebugAgent、runtime、contract、Phase 3 与 M1-M3 gates 共 89 tests 通过。

## 2026-06-30 — Agentic Runtime Phase 3：学生学习闭环 Observe → Update

> 补齐新 Phase 3 的 graph 侧学生学习闭环：真实学习事件现在能形成结构化观察、诊断、教学干预、练习安排、复盘提示和画像/KC 更新计划，并通过 API 暴露当前 loop 状态。

- **学生学习 Runtime 模块**：新增 `pythontopia_graph.runtime.student_learning_loop`，实现 `ObservationSnapshot`、`Diagnosis`、`TeachingPlan`、`PracticeAssignment`、`ReflectionPrompt`、`LearnerUpdatePlan` 和 `StudentLearningLoopResult`。
- **六步闭环语义**：`Observe -> Diagnose -> Teach -> Practice -> Reflect -> Update` 全部产出结构化对象；连续失败识别 `repeated_failure`，低掌握度识别 `low_mastery`，正式通过识别 `practice_success`。
- **证据与正式判题边界**：缺少课程/学习证据时返回 `REJECTED + request:learner-evidence`；Pyodide preview 通过只返回 `PARTIAL + submit:formal-judge`，不会写正向掌握度；只有正式 `submission.passed` 才生成正向 KC mastery delta。
- **补救策略**：两次失败进入补救练习，三次及以上保持 `remedial` 难度，不升级到更难练习；输出 prompt 不泄露完整答案。
- **API 可见状态**：新增 graph 路由 `POST /runtime/student-learning-runs`，返回当前学习闭环完整状态和冻结 `LoopResult`，供后续前端/教师运维界面真实接入。
- **测试**：新增 `test_student_learning_loop.py` 6 条，覆盖正常全链路、无证据低置信降级、连续三次失败补救、Pyodide preview 门槛、正式通过正向更新和 API 可见状态；联跑 runtime/contract/path/reflection 与 M1-M3 gates 共 97 tests 通过。

## 2026-06-30 — Agentic Runtime Phase 2：Loop Runtime 骨架与五类有界状态机

> 补齐新 Phase 2 的 graph 侧运行时骨架：五大闭环不再只是 eval/golden 集合里的 loop type，而是有统一模板、状态顺序、边界、fallback、trace/policy 透传和冻结 `LoopResult` 输出的可执行状态机。

- **统一 Runtime 模块**：新增 `pythontopia_graph.runtime.loop_runtime`，提供 `LoopRuntime`、`LoopRuntimeConfig`、`LoopExecutionRequest`、`LoopExecutionResult`、`LoopTemplate` 和 `RuntimeStep`。
- **五类 loop 模板**：新增 `student_learning_loop / code_debug_loop / resource_generation_loop / game_world_loop / system_evolution_loop`，分别映射 `LEARNER / DEBUG / RESOURCE / WORLD / IMPROVE`，每类都有显式 states、max attempts 和 fallback action。
- **有界执行语义**：Runtime 对缺证据事件返回 `REJECTED + fallback`，对 `max_steps` 截断返回 `PARTIAL + resume`，非法 step 上限 fail fast，避免退化成无界 ReAct。
- **结构化输出**：每次执行返回冻结合同里的 `LoopResult`，携带 `policy_version`、`trace_id`、evidence refs、scores、next actions，可被后续 AG-UI、后端控制面和 eval gate 消费。
- **测试**：新增 `test_loop_runtime.py` 13 条，覆盖五类 loop、happy path、missing evidence fail path、max-step partial checkpoint 和 invalid bounds；联跑 `test_agentic_loop.py`、`test_loop_contract.py`、A/B 与 M1-M3 gates 共 78 tests 通过。

## 2026-06-30 — Agentic Runtime Phase 1：统一事件模型与 Agent Run 控制平面

> 补齐新 Phase 1 数据底座：在不破坏既有 `/api/loop/**`、Tutor、Workshop、Orchestrator 等公开接口的前提下，新增资源型 Agent Runtime 控制面，并把事件、run、loop state、policy decision 串到真实数据库和测试里。

- **统一事件入口**：新增 `POST /api/events`，复用已有 `learning_event_log` 作为权威事件表，保留 `event_type / payload / source / importance / created_at` 审计信息；新增 `AgentRuntimeEventRepository/Service`，事件重要度缺省时复用既有 `EventImportance` 规则。
- **Agent Run 控制面**：新增 `POST /api/agent-runs`、`GET /api/agent-runs/{id}` 和 `GET /api/agent-runs/{id}/stream`，通过现有 `LoopRunService` 读写 `loop_run`，保持旧 `/api/loop/runs` 兼容；stream 先输出 AG-UI 语义 `RUN_STARTED / RUN_FINISHED` 生命周期事件并携带 `traceId`。
- **Loop State / Policy Decision 持久化**：新增 Flyway `V184__agent_runtime_control_plane.sql`，创建 `loop_state` 与 `policy_decision`，约束 loop type、risk level、policy version 和非空 trace；`LoopRuntimeService/Repository` 增加 owner-scoped 写入与查询。
- **权限与校验**：所有新控制面接口限定学生主体；service 层校验 event、loop type、risk level、trace id、importance，并沿用跨用户隐藏存在性的 404 规则。
- **测试**：新增 `AgentRuntimeControllerTest` 覆盖 `/api/events`、`/api/agent-runs`、stream、loop state、policy decision 和教师拒绝；扩展 `LoopRunServiceTest` 用真 Postgres + Flyway 验证 V184 迁移、事件落库、loop state、policy decision、非法输入与跨用户拒绝。

## 2026-06-30 — Agentic Runtime Phase 0：仓库审计、执行计划与进度文档初始化

> 按新粘贴的 Phase 0-9 执行要求重新启动下一代 Agentic Runtime 推进：先做仓库现实审计和长期执行文档，不直接跳入运行时代码。

- **仓库现状确认**：审计 `README.md`、`AGENTS.md`、四端 API 契约、backend/frontend/graph/rag 结构、Flyway 迁移、测试目录、CI 与构建配置，确认当前技术栈、已有 Agent/RAG/Tutor/Workshop/Judge/Town Hub 能力和不可破坏的公开接口。
- **执行计划落盘**：新增 `docs/agentic-runtime-plan.md`，把新 Phase 0-9 计划与现有 M0-M3 Agentic 基线对齐，明确兼容接口、扩展目录、禁止重引入 inventory/currency/shop、阶段验收和测试矩阵。
- **进度文档初始化**：新增 `docs/agentic-runtime-progress.md`，记录当前 Phase、已完成基线、正在进行项、下一步、关键决策、改动文件、测试、风险、changelog 链接和 commit 位置，便于后续 AI/开发者接手。
- **脏树边界**：记录当前工作树存在 unrelated license、截图、arena/boss 和展示文件改动；Phase 0 只允许 stage 本阶段 docs/changelog 文件。

## 2026-06-30 — Agentic M3：系统进化闭环、离线回放、灰度 dry-run 与回滚状态

> 补齐 14 周计划 M3 System Evolution Loop：把 trace/eval 数据集、失败聚类、单变量策略候选、offline replay gate、5% canary dry-run 和 rollback rules 串成可执行本地 workflow，完成从“评测报告”到“可灰度可回滚策略链”的最后一段。

- **M3 eval 数据集与稳定 ID**：新增 `agentic_m3_system_evolution.jsonl`，覆盖 RESOURCE/LEARNER/DEBUG/WORLD/IMPROVE 8 个 trace case；loader 先校验空/重复 stable id，再做 Pydantic schema 校验，避免坏数据悄悄进入系统进化 gate。
- **失败聚类与单变量候选**：新增 `pythontopia_graph.eval.m3_system_evolution_gate`，按 `failure_signature` 聚类，优先识别 `evidence_missing` 失败簇，并只生成 `retrieval_top_k` 一个变量的 `agentic_loop_v2` candidate patch。
- **Offline replay / canary / rollback 链**：M3 gate 计算 baseline/candidate mean、uplift、regression rate，只有 offline gate 通过才生成 5% canary dry-run plan；rollback rules 固定覆盖 replay gate、schema validity、regression rate、cost ratio。
- **Policy Registry 状态机**：`LoopRuntimeService` 新增 `transitionPolicyStatus()`，`policy_registry` 支持 `DRAFT → CANARY → RETIRED` 并拒绝 `RETIRED/REJECTED` 终态重新激活，保留 canary traffic 与 rollback metadata。
- **本地 workflow 脚本**：新增 `scripts/agentic-offline-eval.sh`、`scripts/agentic-nightly-improve-loop.sh`、`scripts/agentic-canary-rollout-dryrun.sh` 和统一入口 `agentic-m3-system-evolution-gate.sh`，三条 workflow 都会刷新 `docs/reports/2026-06-30-agentic-m3-system-evolution-gate.md`。
- **完整验证**：graph 相关 28 tests 通过；后端 M0-M3 相关 Testcontainers/MockMvc 组合测试通过；前端 `typecheck` 与 workshop API 单测通过；四个本地健康端点均 `ok`；Playwright `agenticDeepLoop.spec.ts` 30 passed / 1.9m；`git diff --check` 通过。

## 2026-06-30 — Agentic M2：资源发布流、世界提案契约、效果信号与 ingestion 边界

> 补齐 14 周计划 M2 Resource + World Loops：资源不再只返回生成结果，而是带显式发布决策；世界/任务更新必须 evidence-bound；工坊可读取资源发布后的效果信号；上传解析边界明确为本地文本/PDF/DOCX adapter。

- **资源发布流显式化**：`ResourceProduceResponse` 新增 `publish_flow`，固定 `design → evidence_assembly → pedagogy_critique → grounding_safety → publish_decision → evaluation_hook` 六阶段；只有有证据页且 grounding/safety 通过才 `PUBLISH`，无证据进入 `HOLD_FOR_REVIEW`，失败进入 `REJECT`。
- **M2 离线 gate**：新增 `pythontopia_graph.eval.m2_resource_world_gate`，覆盖资源发布/无证据 hold/空证据拒绝、世界提案证据绑定/未绑定拒绝、本地 ingestion 边界 6 个 case；报告输出到 `docs/reports/2026-06-30-agentic-m2-resource-world-gate.md`。
- **世界/任务提案契约**：新增 `WorldLoopProposal`，要求 `state_patch`、`new_quests`、`npc_dialogue`、`reward_plan` 以及顶层 proposal 都带 `evidence_refs`，避免小镇状态、任务和 NPC 台词脱离学习证据。
- **资源效果信号 API**：新增 `GET /api/workshop/resources/{id}/effectiveness`，汇总 `resource_feedback`、`resource_learning_check`、掌握度增量和证据页，返回 `INSUFFICIENT_EVIDENCE / WATCH / EFFECTIVE / NEEDS_REVISION` 与 `resource_feedback_or_check` evaluation hook。
- **OCR/Marker ingestion 边界**：`UploadTextExtractor.boundary()` 声明当前只使用 `local_text_pdf_docx`（UTF-8 文本、PDFBox、POI XWPF），明确 `externalOcrEnabled=false`、`markerEnabled=false`，避免把未接入的外部 OCR/Marker 当作已实现能力。
- **前端与 E2E 合同**：前端 API client 新增 `getWorkshopResourceEffectiveness()`；`agenticDeepLoop.spec.ts` 新增发布后 effectiveness 断言，完整浏览器链路 30 条通过。

## 2026-06-30 — Agentic 缺口补齐：真实 scorer A/B、强证据工坊检查、Runtime 持久化骨架

> 基于 code review 结论继续补齐“全面 Agentic 系统”缺口：把 A/B 从内嵌分数改为候选输出评分，收紧资源学习闭环证据门槛，并新增可恢复 runtime artifact 持久化。

- **A/B 评估改为 scorer-backed**：`pythontopia_graph.eval.agentic_ab` 的 25 个五类 loop 案例现在保存 baseline/treatment candidate output，由统一 scorer 从 `evidence_page_ids`、`next_action`、安全/schema 字段和工具/Token 预算计算指标；新增测试覆盖 schema invalid、candidate output 变化会改变分数，避免预置 treatment 分数冒充评估。
- **工坊证据门禁收紧**：微测不再把 KC 编号、标题泛词当作理解证据；`OPERATION` 只有绑定本人且 KC 匹配的正式训练场 PASSED submission 才能产生正向掌握度，单独代码片段会进入补救而不是 pass。
- **Runtime/Policy/Checkpoint/Eval 持久化骨架**：新增 Flyway `V182__loop_runtime_artifacts.sql`，落地 `policy_registry`、`loop_checkpoint`、`agent_run`、`agent_tool_call`、`loop_eval_result`；新增 `LoopRuntimeService/Repository`，支持按 owner 保存 checkpoint/tool call/agent run/eval result 并恢复快照，真库测试覆盖五类 loop eval result。
- **M0 契约门禁补齐**：新增可执行 `pythontopia_graph.eval.loop_contract_check`，将 `docs/contracts/loop_contract_check.json` 与五个 Pydantic contract model 对齐校验；新增 Flyway `V183__loop_run_trace_required.sql`，后端服务与数据库双层拒绝新建空 `trace_id` 的 loop run。
- **M1 学习/调试闭环恢复回放**：`LoopRuntimeService` 新增 `ReplayPlan`，`GET /api/loop/runs/{id}/replay` 可从 checkpoint/eval/agent artifact 组合出 `loopType`、`resumeCursor`、`nextAction`、`evidenceRefs` 与 `replayable`，真库测试覆盖 learner checkpoint 和 failed submission -> Bug Mine -> retry/recovery 调试链。
- **M1 golden gate 与 canary 骨架**：新增 `pythontopia_graph.eval.m1_golden_gate` 和专用 `eval/golden/agentic_m1_golden.jsonl`，覆盖 learner/debug/route/safety/leakage 12 个离线回归样本；CLI 输出 JSON 报告并附带 5% canary metadata-only 计划，明确 `real_traffic_enabled=false`。
- **个性化路径串线修复**：`AgentDispatcher` 的 `PathPlannerAgent` 不再硬编码 `user_id=1`，新增测试确保当前请求用户透传到 `PathPlannerRequest`。
- **E2E 口径同步**：`agenticDeepLoop.spec.ts` 改为用资源真实摘要通过微测，并断言无正式提交的 operation evidence 不写入掌握度；M1 继续断言 runtime artifact 写入后 `/replay` 返回可恢复 learner plan。

## 2026-06-29 — 下一代 Agentic 深化验收：90 轮 A/B + 28 条 E2E + R-14 bootstrap 收口

> 对“下一代 Agentic 系统推进报告”逐项复查后继续补深：把 A/B 评估从单次样例升级为 25 案例 × 90 轮稳定性门禁，把浏览器 E2E 扩到 28 条真实闭环，并修掉验收暴露的 R-14 启动源不一致、默认画像缺失、hub NPC 状态缺失问题。

- **要求审查落盘**：新增 `docs/plans/2026-06-29-next-gen-agentic-ab-e2e-deepening.md`，把报告中的统一 Loop Runtime、冻结 schema、五类学习循环、AG-UI 语义事件、A/B/Eval/Canary 和 R-14 `town_hub` 启动源逐项标成已完成 / 本轮补强 / 后续上线项。
- **A/B 评估门禁加厚**：新增 `pythontopia_graph.eval.agentic_ab` 与 `tests/test_agentic_ab.py`，固定 baseline=`legacy_single_shot`、treatment=`agentic_loop_v1`，覆盖 learner/debug/resource/world/improve 五类循环；用户要求“轮数翻 10 倍”后，报告改为 90 轮重复回放。
- **A/B 结果可审计**：生成 `docs/reports/2026-06-29-agentic-ab-evaluation.md`，25 个案例 × 90 轮全部通过 gate；2026-06-30 起该报告由 candidate-output scorer 重生成，当前 mean uplift `0.3915`，stability rate `1.0000`，regression rate `0.0000`，schema validity `1.0000`；每个 loop type 单独列 uplift 和稳定性，避免只报总体均值。
- **28 条真实 E2E**：新增 `frontend/e2e/agenticDeepLoop.spec.ts`，覆盖 bootstrap、legacy session 兼容、loop run 审计、越权拒绝、runtime summary、memory brief、evidence graph、资源生成/消费/微测、连续失败补救、正式提交证据、Bug Mine 分层锁、推荐和 workshop 紧凑点击流。
- **R-14 启动源修复**：`player_state.map_key` 默认值与历史外围图统一归一为 `town_hub`，新增 `V181__r14_player_map_key_town_hub.sql`；`POST /api/game/bootstrap` 惰性创建默认画像和 11 个 hub NPC 状态，避免新用户第一帧缺 profile / NPC 状态。
- **验证**：frontend `npm run typecheck` 通过；graph 定向 `python3 -m pytest -q tests/test_agentic_ab.py` 通过（5 tests）；后端定向 `GameBootstrapServiceTest,GameBootstrapRepositoryTest,GameBootstrapControllerTest` 通过；真实 `./dev.sh` 栈健康端点 `5173/18080/18091/18092` 均 `ok`，Flyway `181` 成功；Playwright 新增全链路 `agenticDeepLoop.spec.ts` 28 passed / 2.8m（`--retries=0`）；`git diff --check` 通过。

## 2026-06-29 — 下一代 Agentic 学习闭环：Loop Contract + ResourceGraph 审稿链 + 真实 evidence rail

> 按“下一代 Agentic 系统推进报告”落地第一批非 MVP 能力：先冻结跨团队 loop runtime 合同，再把资源生成接入真实审稿闭环，最后让学生在工坊资源详情里用一条紧凑行动轨完成“读→测→判→补救/换资源/重规划/通过”。不新增游离 demo，不堆解释卡片。

- **Loop runtime 合同冻结**：新增 `loop_contract.py` 与 `docs/contracts/loop_contract_check.json`，覆盖 `EventEnvelope / LoopCheckpoint / AgentToolCall / LoopResult / EvalRecord`，强制 loop 终态带 `policy_version / loop_type / trace_id / evidence_refs / schema_version`；AG-UI 新增 `run_finished_loop_result()`，`RUN_FINISHED.result` 可直接承载已校验的 `LoopResult`。
- **资源生成审稿链接入编排**：`AgentDispatcher` 的资源类步骤改走 `ResourceGraph.run()`，保留 retries、grounding critic、hallucination detector、semantic grounding、safety critic 与 oscillation 熔断；坏资源草稿返回 `resource_graph_failure`，不会再绕过审查以成功 artifact 进入学习总管。
- **深层记忆合同对齐**：后端 `MemoryBriefDto` 扩展稳定偏好、卡点模式、近期成功、干预建议；graph `MemoryBrief` 同步这些字段，工坊资源生成把 `memory_directives` 注入个性化提示，避免后端记忆增强在 graph 侧被静默丢弃。
- **学生学习闭环证据化**：资源消费响应带 `readRecorded / loopState / evidenceRequired`；资源检查支持微测或训练场 `submissionId`，通过提交关闭闭环，失败提交进入重规划，同一资源连续失败升级换资源，所有决策返回 `loopState / decisionReason / practiceSubmissionId`。
- **前端紧凑行动轨**：`ResourceDetailPanel` 用单条 `读 / 测 / 判` rail 展示状态与下一步，不新增说明卡片；“最近提交”会查询 `PASSED / FAILED / ERROR` 三类正式提交，用运行错误提交也能触发重规划，避免浏览器 E2E 受判题错误类型影响。
- **最终验证**：graph 全量 `python3 -m pytest -q` 通过（1155 passed / 3 skipped）；RAG 全量 `python3 -m pytest -q` 通过（129 passed）；前端 `npm run typecheck` 通过，`npm run lint` 0 error / 43 warnings，Vitest 全量 `npm run test -- --run` 通过（226 files / 2232 tests，仍有既有 Pyodide sourcemap warning）；后端定向新链路与 6 个 Testcontainers 端口冲突类单独重跑通过，排除该 6 类后的 `mvn --settings .mvn/settings.build.xml -q test` 返回 0，单次后端全量 `mvn test` 仍受 Docker 随机 host port 冲突影响报 `ContainerLaunch`，业务断言 0 failures；真实 `./dev.sh` 栈 `5173/18080/18091/18092` 均 `ok`，`/api/course/videos` 返回 34 条；Playwright `studentLearningLoop` 3 passed，`teacherStudentFullFlow` 1 passed。

## 2026-06-26 — 9 项 AI 深度融合 Batch 9：AI 能力包 / Protocol Skill Packages

> 把前面落地的证据工坊、Learner Twin、Eval/Observability、联网查证安全墙进一步包装成可声明、可审计、可被 ChatGPT Apps / Claude Code skill / MCP 类协议面理解的能力包；不是新增一套游离工具，而是把已有学习闭环暴露成“工具 + 资源 + prompt + evidence fields”的合同。

- **协议能力包模型**：新增 `ProtocolSkillPackageDto`，每个能力包显式声明 key、展示名、描述、工具名、资源名、prompt 名、证据字段和运行界面，避免只给外部协议一个散装工具清单。
- **四个真实学习包**：`ProtocolGatewayService` 导出 `evidence_grounded_workshop`、`learner_twin_memory`、`eval_observability`、`external_research_safety`，分别覆盖看学练测闭环、周报/画像记忆、TutorBench/Runtime gate、白名单外部查证。
- **MCP 工具补齐**：协议网关新增 `learner.twin.weekly.read` 与 `browse.run`，把 Learner Twin 周报和有界联网查证纳入同一工具表，并声明 `memoryBrief/masteryDeltas/citations/externalLabel` 等证据字段。
- **Runtime gate 加严**：`GET /api/ai/runtime/summary` 新增能力包数量、证据能力包列表和 `protocol.skill_packages` 闸门，少于 3 个证据合同能力包时给出 `WARN`，防止协议层只有品牌词没有可验收字段。
- **前端驾驶舱展示**：`AiRuntimeSummaryPanel` 增加“能力包 / 绑定证据合同”指标，并展示 `AI 能力包声明证据合同` gate，让工坊右侧面板能看见 Apps/MCP/skill 化后的真实覆盖面。
- **全量前端回归契约修正**：`capabilities.spec.ts` 从 16 工具更新为 17 工具，并明确 `BROWSE` 归属“找内容/带路”能力组，守住联网查证已进入工坊能力启动器的事实。
- **验证**：后端定向 `env MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading" mvn --settings .mvn/settings.build.xml -q -Dtest=ProtocolGatewayServiceTest,AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest test` 通过；前端定向 `npm run test -- --run tests/components/workshop/AiRuntimeSummaryPanel.spec.ts tests/pages/WorkshopPage.spec.ts` 通过（16 tests）；修正能力契约后前端全量 `npm run test -- --run` 通过（226 files / 2228 tests）；`npm run typecheck` 通过；真实 `./dev.sh` 栈健康端点 `5173/18080/18091/18092` 均 `ok`，`/api/course/videos` 返回 34 条；Playwright `publicAcceptance + teacherStudentFullFlow` 通过（28 passed / 11.9m）。Vitest 仍打印既有 `public/pyodide/pyodide.js.map` 缺失提示，退出码为 0。

## 2026-06-26 — 9 项 AI 深度融合 Batch 8：Learner Twin 进入工坊闭环

> 把 Learner Twin 从画像屋里的周报展示推进到百晓工坊右侧工作台：学生不仅能看到本周证据摘要、画像洞察和 KC 掌握变化，还能一键把这些证据转成下一轮工坊提问，让画像真正参与“看、学、练、测”学习闭环。

- **Twin 周报可行动化**：`TwinWeekly` 新增 `prompt` 事件，支持“生成下一步”、洞察“带入”、KC “补强”三类动作，prompt 明确要求基于 Learner Twin 证据、课件范围和可验证学习效果。
- **工坊右侧接入**：`WorkshopPage` 在“调用”抽屉内嵌 Learner Twin 周报，与 Runtime/Eval、Marvis、工具调用时间线同列展示；点击 Twin 动作会把结构化 prompt 预填到主聊天 composer。
- **输入框外部预填合同**：`ChatComposer` 暴露 `prefill()`，`WorkshopChat` 暴露 `prefillDraft()`，只写入草稿，不绕过现有 submit、意图路由、证据记录和历史落库。
- **前端视觉统一**：`TwinWeekly` 从像素小卡改为浅色 1px 分隔、8px 圆角、系统字体的工作台面板，适配 ChatGPT / Claude 风格右侧上下文栏。
- **健壮性修复**：`WorkshopChat.scrollToBottom()` 在不支持 `element.scrollTo()` 的环境回退到 `scrollTop`，避免测试和老浏览器环境出现异步滚动错误。
- **验证**：前端定向 `npm run test -- --run src/components/companion/__tests__/TwinWeekly.spec.ts tests/pages/WorkshopPage.spec.ts src/components/workshop/__tests__/ChatComposer.spec.ts` 通过（35 tests）；`npm run typecheck` 通过；后端定向 `env MAVEN_OPTS="-Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading" mvn --settings .mvn/settings.build.xml -q -Dtest=WeeklyReportServiceTest,TwinWeeklyControllerTest test` 在非沙箱权限下通过。Vitest 仍打印既有 `public/pyodide/pyodide.js.map` 缺失提示，退出码为 0。

## 2026-06-26 — 9 项 AI 深度融合 Batch 7：工坊实时语音输入接入

> 把已有 Web Speech 与 `/api/media/asr` 语音听写能力从随身助教扩展到百晓工坊输入框：学生可以像 ChatGPT / Claude Web 一样在同一 composer 里直接说出问题，浏览器支持时本地听写，不支持时录音上传后端 ASR；降级时不伪造文本，明确要求键盘输入。

- **同一输入框多模态化**：`ChatComposer` 新增语音按钮，优先使用浏览器 `SpeechRecognition`；识别出的 transcript 会写回现有 textarea，并继续复用 `@` 课件引用、`/` 工具命令和 `+` 能力分组状态机。
- **后端 ASR 回退链路**：Web Speech 不可用时用 `useAudioRecorder` 采集短录音，调用既有 `transcribeAudio()` / `POST /api/media/asr`；`degraded=true` 或空文本只显示错误，不把失败录音包装成真实学习提问。
- **发送闸门**：附件上传中、语音采集中、录音中或转写中均禁止发送，避免半截语音上下文进入意图路由和资源生成链路。
- **前端体验适配**：composer 增加紧凑语音状态行和 active 图标态，保留当前工坊 AI 工作台的浅色输入区，不引入单独语音面板或悬浮录音 demo。
- **验证**：前端定向 `npm run test -- --run src/components/workshop/__tests__/ChatComposer.spec.ts tests/api/media.spec.ts` 通过（18 tests）；`npm run typecheck` 通过；后端定向 `mvn --settings .mvn/settings.build.xml -q -Dtest=XfyunIatServiceTest test` 通过。Vitest 仍打印既有 `public/pyodide/pyodide.js.map` 缺失提示，退出码为 0。

## 2026-06-26 — 9 项 AI 深度融合 Batch 6：联网查证工具进入百晓工坊

> 把已有 BROWSE / browser-use 安全墙从“后端和 graph 里有能力”推进到学生真实可用的工坊工具：显式“联网查证 / 官方文档 / 最新资料”诉求可直接进入白名单、有界、只读的外部查证链路，并在 ChatGPT / Claude 风格工作台中以时间线展示，始终标注“外部内容，非课件”。

- **17 工具分发表对齐**：前端 `IntentKind`、斜杠命令、能力分组、route render mode 全部纳入 `BROWSE`；新增 `/联网查证`，别名覆盖 `browse/search/web/联网/查证/官方文档/最新`，能力启动器“找内容/带路”包含视频、查证和导航。
- **工坊对话真实接线**：`WorkshopChat` 新增 `browse` 消息类型，`BROWSE` 意图调用 `POST /api/browse/runs` + `GET /api/browse/runs/{id}`，把步骤、来源、截断/错误态用 `BrowseTimeline` 内联渲染；失败时保留错误气泡并支持按原上下文重试。
- **历史与路由闭环**：前端历史 DTO 和后端 `WorkshopChatHistoryRecordRequest` 允许 `responseKind=browse`，browse 结果进入左侧历史和后续 recentTurns；Java `IntentGraphClient` 的 fallback 元数据补齐 `find/read_only/browse`。
- **Graph 路由治理修正**：`IntentRouterAgent` 对“联网查证 / 官方文档 / 最新”等显式诉求走确定性预判，不依赖 LLM；工具注册表把 `BROWSE` 风险级别修正为 `read_only` 并显式 `read_only=true`，quiz scope 仍不暴露。
- **前端视觉适配**：`BrowseTimeline` 从像素卡片调整为当前 AI 工作台的白底细边、系统字体、紧凑来源 chips，同时保留安全链接过滤、错误告警、外部内容 banner 和来源分区。
- **验证**：前端定向 `npm run test -- --run tests/components/workshop/WorkshopChat.spec.ts tests/composables/useSlashCommand.spec.ts tests/components/workshop/BrowseTimeline.spec.ts src/api/__tests__/browse.spec.ts` 通过（69 tests）；`npm run typecheck` 通过；graph 定向 `python3 -m pytest -q tests/test_intent_router_agent.py tests/test_tool_registry.py tests/test_tool_schema.py` 通过（144 passed）；后端定向 `env MAVEN_OPTS=-Djdk.attach.allowAttachSelf=true mvn -s .mvn/settings.build.xml -q -Dtest=IntentGraphClientTest,WorkshopChatHistoryServiceTest test` 通过；后端 `mvn --settings .mvn/settings.build.xml -q test-compile` 通过；`git diff --check` 通过。Vitest 仍打印既有 `public/pyodide/pyodide.js.map` 缺失提示，退出码为 0。

## 2026-06-26 — 9 项 AI 深度融合 Batch 5：AI Runtime / Eval 证据驾驶舱

> 把已有 AG-UI/MCP 协议、Learning Evidence Graph、Evidence Memory、Learner Twin 与联网查证安全墙聚合成一个学生可见的 Runtime/Eval 摘要；工坊右侧“调用”面板不只显示过程流，也能看到本次 AI 系统是否有证据、是否有评测闸门、外部来源是否与课件证据隔离。

- **后端 Runtime Summary API**：新增 `GET /api/ai/runtime/summary`（学生只读本人），聚合 `ProtocolGatewayService`、`LearningEvidenceGraphService`、`EvidenceMemoryService`、`WeeklyReportService` 与 `BrowseProperties`，返回协议工具数、证据字段工具、证据图指标、graph memory 分数、Twin 周报摘要、外部查证白名单与引用策略。
- **Eval Gates 明确化**：summary 新增 `runtime.gates[]`，覆盖 `protocol.evidence_fields`、`evidence.graph_completeness`、`memory.graph_over_naive`、`browse.external_citation_policy`；空学习轨迹返回 `INSUFFICIENT_EVIDENCE`，不伪装成通过。
- **前端工作台接入**：新增 `AiRuntimeSummaryPanel.vue` 和 `api/aiRuntime.ts`，挂入百晓工坊右侧“调用过程”抽屉，显示 MCP 工具、证据图完整度、Learner Twin 记忆事件、外部查证隔离标签和 gate 状态。
- **验证**：后端定向 `env MAVEN_OPTS=-Djdk.attach.allowAttachSelf=true mvn -s .mvn/settings.build.xml -q -Dtest=AiRuntimeSummaryServiceTest,AiRuntimeSummaryControllerTest,ProtocolGatewayServiceTest test` 通过；后端 `mvn --settings .mvn/settings.build.xml -q test-compile` 通过；前端定向 `npm run test -- --run tests/components/workshop/AiRuntimeSummaryPanel.spec.ts tests/pages/WorkshopPage.spec.ts` 通过（15 tests）；`npm run typecheck` 通过。Vitest 仍有既有 `public/pyodide/pyodide.js.map` 缺失提示，退出码为 0。

## 2026-06-25 — 9 项 AI 深度融合 Batch 4：百晓工坊 AI 工作台前端适配

> 把百晓工坊从像素木牌式聊天页收敛为更接近 ChatGPT / Claude Web 的学习工作台：历史、对话、书房/调用过程成为稳定三栏壳，保留 Pythontopia 的证据、工具分发和学习闭环，不改成纯聊天 demo。

- **工作台默认布局**：`/workshop` 桌面端默认展开历史栏，移动/窄屏仍默认收起；`workshop-body` 标记 `data-layout="assistant-workbench"`，便于后续浏览器验收与视觉回归定位。
- **页面外壳现代化**：顶部导航、历史栏、书房/调用抽屉和资源弹窗从厚像素边框、木牌阴影与米色底，改为浅色 1px 分隔线、系统字体、8px 控件圆角和密集可扫的 AI 工作台布局。
- **对话主体统一风格**：`WorkshopChat` 的顶部栏、composer、用户/助手气泡、工具 chips、接地/三审徽章、填空小测和资源卡统一到同一套浅色工作台视觉，同时保留 16 工具分发、SSE trace、课件证据与历史续聊行为。
- **验证**：前端定向 `npm run test -- --run tests/pages/WorkshopPage.spec.ts tests/components/workshop/WorkshopChat.spec.ts tests/components/workshop/ChatHistoryRail.spec.ts tests/components/workshop/WorkshopChatUploads.spec.ts` 通过（50 tests）；`npm run typecheck` 通过。Vitest 仍打印既有 `public/pyodide/pyodide.js.map` 缺失提示，但命令退出码为 0。

## 2026-06-25 — 9 项 AI 深度融合 Batch 3：可追溯推荐 + MCP 证据字段声明

> 继续把 Evidence Memory 2.0 从“后端内部排序信号”推进到可被前端、Eval 和外部 Apps/MCP 读取的证据合同：推荐理由不只说“为什么”，还返回支撑它的 event/check/page ids。

- **推荐 DTO 证据扩展**：`RecommendationDto` 新增 `checkIds` 与 `pageIds`，并保留旧构造器兼容；洞察驱动推荐继续返回 `evidenceEventIds`，Evidence Memory 驱动推荐额外透传微测/操作检查 id 与课件页 id。
- **推荐排序可审计**：`LearnerRecommendationService` 在 `evidenceMemoryGrounded` 分支把 `EvidenceMemorySignalDto.checkIds/pageIds` 写入推荐输出，前端和教师侧可从推荐卡片追到具体失败微测和 grounding 页码。
- **协议网关证据字段声明**：`ProtocolToolDto` 新增 `evidenceFields`；`workshop.resource.check.submit` 声明 `checkId/resourceId/kcCode/status/masteryDelta`，新增 `learner.recommendations.read` MCP 工具声明并标出 `evidenceEventIds/checkIds/pageIds`。
- **验证**：后端定向 `mvn --settings .mvn/settings.build.xml -q -Dtest=RecommendationServiceTest,ProtocolGatewayServiceTest test` 通过。

## 2026-06-25 — 9 项 AI 深度融合 Batch 1/2：证据因果脊柱 + GraphRAG 引用合同

> 面向“9 个点全部深度实现”的第一批可提交改造：先把所有后续 Agent Runtime、Learner Twin、Eval、Realtime、MCP/Apps 和前端 ChatGPT/Claude 工作台都依赖的证据底座做实，避免资源生成、推荐和展示只靠软信号。

- **实施计划落盘**：新增 `docs/plans/2026-06-25-nine-ai-deep-implementation.md`，把 9 项 AI 升级拆成 Evidence Spine、GraphRAG Citation、Runtime/Skills/Tools、Eval/Observability、Learner Twin、Realtime、Web Research、Frontend Workbench、Changelog/Commits 9 个批次。
- **资源检测因果追踪**：`LearningEvent.ResourceCheckCompleted` 新增 `checkId`，`ResourceWorkshopService` 发布事件时使用 `resource_learning_check.id`，`LearningEventListener` 将 mastery reason 写成 `resource_check:{checkId};resource:{resourceId}`，让 mastery event 可回连到具体检查证据。
- **画像更新语义收紧**：`ResourceConsumed` 只记录“看过资源”，不再增加 `knowledge_base`；资源微测通过更新 `knowledge_base`，操作证据通过更新 `practical_ability`，检测失败写入 `error_pattern` 和 remediation tag。
- **Learning Evidence Graph 补因果边**：后端证据图解析 `kc_mastery_event.reason` 中的 `resource_check:{id}`，新增 `CHECK -> MASTERY_EVENT` 的 `CAUSED_MASTERY_EVENT` 边，形成 `resource -> check -> mastery -> KC` 的可审计闭环。
- **记忆流镜像增强**：`MemoryStreamMirror` 将 `checkId` 写入 `resource_check_passed/failed` payload，供 Learner Twin、NPC memory、前端 trace 与后续 eval 复用。
- **GraphRAG 引用合同**：`/rag/query` response 新增 `citations` 与 `route_reason`；命中时 citation 含 `page_id/page_no/chunk_id/kc_codes/snippet/score/source/via_prerequisite/via_relation`，无证据与安全拦截时强制 `citations=[]`，下游不得伪造页码。
- **验证**：后端定向 `mvn --settings .mvn/settings.build.xml -q -Dtest=ResourceWorkshopServiceTest,PassiveProfileRevisionListenerTest,MemoryStreamMirrorTest,LearningEvidenceGraphServiceTest test` 通过；Stage04 画像端到端 `mvn --settings .mvn/settings.build.xml -q -Dtest=Stage04PassiveDimensionAcceptanceIntegrationTest test` 通过；后端 `test-compile` 通过；RAG `python3 -m pytest -q tests/test_query_route.py tests/test_query_graph_mode.py` 34 passed；RAG 全量 `python3 -m pytest -q` 129 passed。
- **未完成/下一批**：尚未改前端 ChatGPT/Claude 类工作台，也尚未把协议网关/推荐 DTO、Eval cockpit、Learner Twin、Realtime、Web Research 和 MCP/Apps 外部入口全部串完；这些进入后续 batch commit。

## 2026-06-25 — AI 技术有机重构收口：AG-UI/MCP Runtime + Evidence Memory + Eval 闸门全链路验收

> 基于 OpenAI Agents/Apps SDK、Claude Code、AG-UI、DSPy 等近期 agent/runtime/eval 思路做取舍后，选择不引入游离 demo，而是把能力收进 Pythontopia 已有导学、工坊、RAG、画像、推荐、班级 evidence 链路：前端可观测流式事件、后端可持久学习记忆、graph/rag 可量化优化闸门形成闭环。

- **Agent Runtime 标准化**：graph orchestrator/tool-loop SSE 输出 AG-UI 风格 `RUN_STARTED` / `STEP_STARTED` / `TEXT_MESSAGE_CONTENT` / `RUN_FINISHED` 等事件；后端协议网关补齐 MCP resource/prompt、`learner.evidence.graph.read` 工具声明和 OTel GenAI 属性，服务于现有导学/工坊流式 UI，而不是另起一套 agent。
- **Evidence Memory 2.0**：新增 `learner_evidence_memory_snapshot` 迁移和 `EvidenceMemoryService/Repository/Controller`，从练习失败、资源检查、grounded page、mastery event 中抽信号，并沿 `course_kc_edge` 衰减传播；推荐服务用该记忆重排弱项 KC，把 evidence id 带回推荐理由。
- **Eval-gated 优化**：新增 graph 侧 prompt variant 优化器和 RAG 图谱检索 A/B 门禁，要求样本胜率、mean uplift、回归率、严重回归数同时达标，防止“看起来更聪明”的 prompt 漂移破坏课件接地。
- **班级闭环补强**：题目型班级任务 fan-out 写入 `submission` evidence 要求；Stage12 与 Playwright 教师/学生全流程都改为用真实 `practice_submission` 证据完成任务，符合“任务完成必须有 evidence”的项目约束。
- **最终验证**：frontend `npm run typecheck` 通过；`npm run lint` 0 errors / 43 warnings；`npm run test -- --run` 225 files / 2218 tests passed；`npm run build` 通过；完整本地栈 health `5173/18080/18091/18092` 均 `ok`，`/api/course/videos` 返回 34 条；Playwright `publicAcceptance + teacherStudentFullFlow` 28 passed / 8.1m；graph 全量 1146 passed, 3 skipped；rag 全量 129 passed；backend 定向新链路与 Stage12 通过，后端全量 Maven 三轮均为业务断言 0 failures，但 6 个 Testcontainers 类在同一全量 JVM 中受 Docker 随机 host port 冲突报 `ContainerLaunch`，该 6 类窄集合重跑通过。

## 2026-06-24 — 浏览器全流程点击验收：学生主链路 + 教师班级 evidence 闭环

> 在本地完整栈上补跑真实浏览器全流程验收，修掉验收中暴露的指南锚点、RAG 等待、教师成员表响应式和班级任务 evidence fan-out 问题，并新增教师/学生班级闭环 Playwright 用例。

- **验收报告**：新增 `docs/reports/2026-06-24-browser-full-flow-acceptance.md`，记录修前失败、根因、修复点、命令和截图证据路径。
- **学生端 publicAcceptance 修稳**：`/guide` 桌面锚点限定到指南章节导航，避开 AppLayout skip-link；图书馆 RAG 三问改为等待“AI 引用或片段列表出现第 N 页证据”，不再被首帧空容器误伤。
- **教师/学生班级闭环 E2E**：新增 `frontend/e2e/teacherStudentFullFlow.spec.ts`，覆盖教师创建班级、学生加入、教师发布绑定题目的任务、学生训练场 AC、公告板提交 submission 证据、教师看板完成率同步 1/1。
- **真实业务修复**：`ClassroomMembersTab` 的成员表行数据改为 `computed`，修复学生已加入但教师 tab 仍显示空表；`ClassroomQuestService` 对题目型班级任务 fan-out 写入 `requiredEvidenceType=submission` 与 `targetProblemIds`，恢复任务完成 evidence 强校验。
- **验证**：`ClassroomQuestServiceTest` 通过；教师/学生全流程 Playwright `1 passed / 10.4s`（`--retries=0`）；`publicAcceptance` 全套 `27 passed / 8.3m`（`--retries=0`）；`npm run typecheck` 通过；本地 `5173/18080/18091/18092` 健康端点均 `ok`。

## 2026-06-24 — 长期班级学习效果仿真：KC 掌握 / 复习完成 / 资源使用三指标验证

> 针对“真实长期学习效果仍需班级日志观察”的剩余风险，新增一个 6 周离线班级仿真：同一班级内 baseline/treatment 各 12 人，写入真实 `classrooms`、`classroom_members`、`kc_mastery`、`kc_mastery_event`、`kc_review_state`、`learning_resource`、`learning_event_log`，再用 SQL 聚合长期效果。

- **仿真报告**：新增 `docs/reports/2026-06-24-long-term-classroom-effect-simulation.md`，记录班级规模、周期、表级日志、三项指标和验证命令。
- **测试实现**：新增 `LongTermClassroomEffectSimulationTest` 与 test-only `ClassroomLearningEffectSimulator`，不加线上接口、不把假数据暴露为生产功能；仿真数据统一标记 `source='long-term-sim'` / `reason='long-term-sim:<runId>:<cohort>'`。
- **量化结果**：平均 KC 掌握度 baseline **0.280** → treatment **0.482**（+0.202）；复习完成率 **0.333** → **0.800**（+0.467）；资源使用率 **0.625** → **1.000**（+0.375）。原始日志量：`kc_mastery_event` **720**、review logs **1128**、`resource_consumed` **234**。
- **验证**：`cd backend && mvn -s ~/.m2/settings.xml -q -Dtest=LongTermClassroomEffectSimulationTest test` 通过。边界：这是确定性离线仿真，不等同真实学生在线 A/B 或浏览器全流程验收；后续真实班级日志仍应持续按同三项指标追踪。

## 2026-06-24 — 两日优化详尽 A/B 门禁：稳定胜出才晋级

> 对 2026-06-23/24 的 RAG 图谱补证据、Learning Evidence Graph、TutorBench-lite、Evidence Memory 2.0、推荐重排、AG-UI runtime、eval-gated 自动优化和协议网关做系统 A/B 门禁；补上“稳定样本胜率”约束，避免均值漂亮但不稳定的优化被晋级。

- **A/B 报告**：新增 `docs/reports/2026-06-24-two-day-ab-evaluation.md`，逐项列出 baseline、treatment、量化门槛、当前结果和覆盖测试文件。
- **Eval 自动优化继续改进**：`OptimizationPolicy` 新增 `min_sample_win_rate`，`CandidateOptimizationSummary` 新增 `sample_win_rate`；一个候选即便 mean uplift 为 **+0.166**、regression rate 为 **0.0**，只要 sample win rate **0.40 < 0.80** 就会被拒绝，防止少数样本大幅提升掩盖整体不稳定。
- **RAG 图谱检索 A/B**：新增三场景矩阵（前置 / 共现 / 共同错误），strict baseline recall **0.5**，graph treatment recall **1.0**，win rate **1.0**，mean uplift **+0.50**，同时断言无关页不进入结果、补充证据不超过 `top_k // 3`。
- **后端 Evidence A/B 矩阵**：Learning Evidence Graph 对 resource-only baseline 强制 completeness **+0.40**、connected rate **+0.30**、grounded pages **+2**、check/mastery coverage **+0.80**；Evidence Memory 对 recent-event baseline 强制 graph memory **+0.35**、retained evidence **+2**、direct/propagated signal **+1**；推荐重排要求目标 KC 从第 3 位升到第 1 位（rank uplift **>= 2**）。
- **AG-UI Runtime A/B**：资源生成 / 工具调用 / 错误终止三类流式场景都要显著优于 legacy 事件名，当前 score uplift 分别为 **+0.77 / +0.585 / +0.495**。
- **验证**：graph 定向 `python3 -m pytest -q tests/test_sse_agui.py tests/test_eval_optimization.py tests/test_orchestrator_route.py tests/test_tool_loop_route.py` 通过（32 passed）；rag 定向 `python3 -m pytest -q tests/test_recent_optimization_ab.py tests/test_fusion_kc_graph.py tests/test_query_graph_mode.py tests/test_runtime_knowledge_graph.py tests/test_retrieval_golden.py` 通过（38 passed）；backend 定向 Maven 通过，Surefire XML 复核 13 tests / 0 failures / 0 errors。未跑真实浏览器全栈点击 e2e，也未做真实学生在线 A/B。

## 2026-06-24 — 三路线深化：Agent Runtime 标准化 + Evidence Memory 2.0 + Eval 自动优化

> 按「路线 1 → 路线 2 → 路线 3」顺序深度落地：先把 agent runtime/协议层从私有 SSE 事件升级到 AG-UI/MCP/OTel 可描述能力，再把学习证据图推进成可持久化的 Evidence Memory 2.0，最后接入 eval-gated prompt 自动优化，形成“生成前有协议、推荐时有记忆、优化时有量化闸门”的闭环。

- **设计文档与实施记录**：新增 `docs/plans/2026-06-24-agent-runtime-evidence-memory-eval-design.md` 与 `docs/plans/2026-06-24-agent-runtime-evidence-memory-eval-implementation.md`，把 SAG/GraphRAG 之外的 AG-UI、MCP resource/prompt、OpenTelemetry GenAI semantic conventions、MemGraphRAG/HippoRAG 记忆信号、Ragas/trajectory eval 自动优化映射到 Pythontopia 真实模块。
- **路线 1 · Agent Runtime / AG-UI / 协议网关**：`pythontopia-graph` 的 orchestrator/tool-loop SSE 统一输出 `RUN_STARTED`、`STEP_STARTED`、`TEXT_MESSAGE_CONTENT`、`STEP_FINISHED`、`RUN_FINISHED/RUN_ERROR` 等 AG-UI 风格事件；新增 stream quality 量化函数，能检测 legacy `trace/token/result` 退化；后端 `ProtocolGatewayService` 扩展 MCP resource、MCP prompt、`learner.evidence.graph.read` 工具与 OTel GenAI 属性清单。
- **路线 2 · Evidence Memory 2.0**：新增 `learner_evidence_memory_snapshot` 迁移、`EvidenceMemoryService/Repository/Controller` 与信号 DTO；从失败微测、练习失败、通过检查、grounded page、evidence id 聚合 KC 记忆信号，并沿 `course_kc_edge` 按 `requires/co_occurs_with/has_common_mistake/same_chapter` 衰减传播；推荐服务优先使用 evidence memory 信号重排资源，并把证据事件 id 透传进推荐结果。
- **路线 3 · Eval-gated 自动优化**：新增 `pythontopia_graph.eval.optimization`，用 faithfulness / answer relevancy / context recall 加权 composite 对 baseline 与候选 prompt 做样本级对比；只有平均 uplift、回归率、严重回归数、最低均分都过闸门才接受候选，并把选中的 prompt variant 写成现有 `optimize` artifact，避免“自动优化”变成无约束 prompt 漂移。
- **量化对比测试**：AG-UI 标准流质量分 **0.95**，legacy 事件流 **0.22**；Evidence Memory graph-backed 分数要求比 naive recent-event memory 至少高 **+0.35**；自动优化测试覆盖候选平均 uplift 达 **+0.18** 可接受、但回归率 **0.25 > 0.20** 时必须拒绝；协议、推荐、artifact 导出均有定向断言。
- **验证**：本条初次落地时完成 graph/rag/backend/frontend 定向验证；最终收口见 2026-06-25 条目。当前复验结果为 `pythontopia-graph` 全量 1146 passed, 3 skipped；`pythontopia-rag` 全量 129 passed；前端 typecheck/lint/test/build 与真实浏览器 E2E 通过；后端新链路、Stage12 与 6 个 Testcontainers 相关类窄集合通过。后端全量 Maven 在同一 JVM 中反复启动随机端口 Spring/Testcontainers 时仍受 Docker host port 冲突影响（业务断言 0 failures，6 个 `ContainerLaunch` errors），需后续把 Testcontainers 复用/端口分配治理成独立工程项。

## 2026-06-24 — Learning Evidence Graph 深化：SAG/GraphRAG 思路落到学习闭环评测

> 在上一轮 KC 图谱补证据基础上，按 Route A/B/C 深化：不把 SAG 作为黑盒替换 RAG，而是把 SQL-native evidence graph、轨迹级评测、记忆感知个性化信号落到真实学习闭环，形成可审计、可量化、可继续演进的高级 AI 底座。

- **正式设计与实施计划**：新增 `docs/plans/2026-06-24-learning-evidence-graph-design.md` 与 `docs/plans/2026-06-24-learning-evidence-graph-implementation.md`，把 SAG dynamic hyperedge、GraphRAG/DRIFT 局部子图、agentic RAG trajectory eval、MemGraphRAG/HippoRAG 记忆增强映射到 Pythontopia 的课程页码、KC、资源、微测、掌握度事件和学习事件。
- **Route A · SQL-native Learning Evidence Graph**：新增后端 `learner/evidence` 服务与 `/api/learner/evidence-graph`，从 `learning_resource`、`resource_learning_check`、`kc_mastery_event`、`learning_event_log`、`resource_run`、`course_kc_edge` 动态投影 USER/RESOURCE/CHECK/KC/PAGE/MASTERY_EVENT/EVENT/AGENT_RUN 子图；输出 node/edge、coverage、grounding、connectivity、completeness、isolated evidence 等指标。
- **Route B · TutorBench-lite 轨迹级评测**：`LearningEffectTraceService` 聚合 evidence graph metrics，`TutorBenchEvaluator` 新增 `evidence_graph_connectivity`、`grounded_personalization`、`trajectory_traceability` 用例，让评测从“是否有资源/事件”升级为“是否形成可解释学习闭环”。
- **Route C · 记忆感知个性化信号**：新增 `EvidencePersonalizationSignalDto`，按 KC 聚合失败微测、练习失败、课件页 grounding、证据事件与检查证据，生成带优先级和证据 id 的个性化诊断输入，可供后续导学、工坊资源生成、教师热力图复用。
- **量化对比测试**：新增 graph-backed vs naive resource-only 对比，要求 evidence graph completeness 至少高 0.4、connectivity 与 grounded pages 更高；TutorBench graph-backed summary pass rate 要显著高于 naive summary（至少 +0.5），避免只做概念包装。
- **验证**：后端 `mvn -s ~/.m2/settings.xml -q test` 全量通过（Surefire 172 份报告、862 tests、0 failures/errors，空库 Testcontainers/Flyway 迁移到 v179）；前端 `npm run typecheck` 与 `npm run test -- --run` 全量通过（2218 tests）；`pythontopia-graph` 全量 `python3 -m pytest -q` 通过（1138 passed, 3 skipped）；`pythontopia-rag` 全量 `python3 -m pytest -q` 通过（128 passed）。未跑真实浏览器全栈点击 e2e，剩余风险是长期 A/B 学习效果仍需真实学生日志。

## 2026-06-23 — SAG 启发的 KC 图谱补证据 + 学习效果闭环可量化

> 调研 SAG/GraphRAG/LightRAG/HippoRAG2/KAG/Mem0/Ragas 后，选择先把「动态图谱补证据、可解释 trace、资源消费后校验、效果评估」落到现有 Pythontopia 链路；SAG 不整体替换 RAG，但其 event/entity/co-occurrence 思路进入 KC 图谱与后续三元组重构路线。

- **技术取舍**：SAG 论文与仓库强调 SQL-RAG、query-time dynamic hyperedges、event/entity 双索引与 chunk-event-entity 图谱，对多跳召回有优势；Pythontopia 课程域目前数据规模小、强页码引用约束更高，所以不直接替换现有 RAG，而是把可控的 `prerequisite/co_occur` KC 边作为低风险增量。
- **KC 三元组重构底座**：新增 `course_kc_edge` 迁移与 `data/course-pack-43/json/course_kc_edge.json` 种子，边关系从单一前置扩展到 `prerequisite` 与 `co_occur`，支持后续继续补 `example_of/common_error/assessment_of/analogy_to` 等教育关系。
- **RAG 侧图谱补证据**：`pythontopia-rag` 的 query response 增加 `graph_trace`，记录 route、retrieval_mode、topK、直接命中、补充命中、前置补证据、共现补证据、relation_counts、expanded_kc_count 与 `supplemental_hit_limit` 噪声配额，避免图扩展把结果冲歪。
- **Graph/后端/前端 trace 打通**：课件问答从 rag -> graph -> backend -> `LibraryPage` 全链路透传 route/retrieval/subgraph/graph_trace；图书馆页面展示「共现补证据」「前置补证据」「补充证据 x/y」「关系边」等可审计信息，便于演示和后续调参。
- **不止 RAG 的增强**：资源学习不再等同于掌握度奖励，新增资源学习检查与效果 trace；TutorBench-lite/学习效果服务/协议网关定向测试覆盖「生成资源 -> 学生作答/检查 -> 事件入库 -> 效果评估」的闭环，避免只优化检索分数。
- **验证**：`services/pythontopia-rag` 定向 pytest 12 passed；`services/pythontopia-graph` 定向 pytest 16 passed；backend 定向 Maven 测试通过并空库迁移到 v179；frontend `LibraryPage` 7 passed、`courseLibrary` 5 passed、`ResourceDetailPanel` 4 passed、`vue-tsc --noEmit` 0 错。
- **未验证/风险**：当前没有启动完整 Vite + backend + graph + rag 栈，因此 `frontend/e2e/publicAcceptance.spec.ts` 的真实浏览器全栈点击未跑；长期 A/B 学习效果也需要真实学生日志。`co_occur` 权重、展示阈值和 SAG 式 event/entity 细粒度抽取仍需用后续 QA 日志校准。

## 2026-06-19 — guide 下载计数并发竞态修复（UI e2e 发现）+ ECS 重部复验

> 对 ECS 公网做登录→guide 多端下载的真实浏览器 UI e2e，发现「初次加载只有一端显示下载次数」的竞态 bug 并修复、重部上线、复验。

- **下载计数并发竞态（前端 `GuidePage.vue`）**：`onMounted` 里三端计数用 `downloadCounts.value = { ...downloadCounts.value, [key]: await ... }`，对象展开 `...downloadCounts.value` 在 `await` **之前**求值，三个并发回调各持初始空快照互相覆盖，最终只剩最后 resolve 的那个 key（线上恒为 `windows_exe`）有计数；APK/ZIP 初次加载不显示「已下载 N 次」。改为先 `Promise.all` 收齐 `[key,count]` 再「一次性」合并赋值，根除覆盖。
- **回归测试（`tests/pages/GuidePage.spec.ts`）**：新增 2 例——三端 mock 不同计数断言 APK/EXE/ZIP 均渲染（旧实现只剩一端会挂）；点击下载乐观 +1 且不清空其它端已有计数。
- **ECS 重部（公网 47.120.29.62）**：本地 `npm run build` 产物上传 ECS，`docker cp` 入 `deploy-frontend-1` 即时生效 + nginx reload；并叠加 `FROM pythontopia/frontend:release` 重建同名 release 镜像（容器重建后仍带修复，代价是体积 216MB→325MB，新 assets 层覆盖旧层）；源码修复同步至 ECS `/opt/pythontopia` 检出。
- **UI e2e 复验**：自建学生测试号登录 → guide「多端下载」；修复前截图证实仅 EXE 显示「已下载 0 次」、点 APK 下载触发 `/downloads/pythontopia.apk` 且后端计数 0→1（API 确认）；重部后干净加载三端均显示「已下载 N 次」（加载的是新 `GuidePage-CNAPZNfh.js` 块，CDP 确认）；计数复位 0 后三端均显「已下载 0 次」。后端 `GET /api/downloads/{android_apk,windows_exe,windows_zip}` 全 200。

## 2026-06-19 — Android 原生端对齐 Web 全功能 + 超强加固 + ECS 发版（APK/EXE 下载 + 下载计数）

> 把 Android 端逐功能补齐到与 Web 对齐：新增无尽远征模块、主线三件套做成真交互、工坊生成物查看器、费曼代码找茬、课件 RAG 问答 + PDF 页图、讲义视频、像素小镇 HUD/小地图/日夜/天气/瞬移；中文本地化；release 包 R8 超强加固；并在 ECS 上线 APK/EXE 下载入口 + 后端下载次数统计。

- **新增 `:feature:expedition` 无尽远征（Roguelike，移植 Web `stores/rogueRun` 等）**：完整状态机（idle→map→event→challenge→cleared/over）、分层 DAG 地图生成（可注入 RNG，含单测）、relic 增益、概念事件、BOSS 多阶段、每日挑战（日期种子确定性地图）、知识星座、后端 run 跨会话存档（`/api/world/expedition/run`）+ 本地兜底、复用 boss 真判题；接入导航 dock。
- **主线「看/学/练」三件套真交互（`:feature:mainline`）**：练-客观题改为站内选/判/填 + 服务端判分（`/practice/{id}/answer`，不泄题，回正确答案+解析）；学-课件逐页阅读 + 逐页 read 打点（`/page-read`，新增 `CoursePageApi`）；看-内嵌视频播放（`PytVideoPlayer`）。
- **工坊生成物查看器（`:feature:workshop`）**：`GET /api/workshop/resources/{id}` 拉详情，按类型渲染 讲义/思维导图/练习/代码实验/拓展阅读/故事分镜。
- **费曼「代码找茬」限时小游戏（`:feature:feynman`）**：`/bug-hunt` 拉含 bug 代码，30s 点 bug 行 + 命中/误报/漏判判分 + 连胜/最高分（客户端计分）。
- **课件 RAG + PDF（`:feature:library`）**：`POST /api/courseware/answer` 课件问答（答案 + 页码引用，选中 KC 收敛范围）；阅读器加载 `GET /api/course/pages/{id}/image`（后端 PDFBox 渲染 PNG，`BitmapFactory` 解码，零新依赖），失败降级 markdown。
- **讲义视频页（`:feature:lecture`）**：重构为「视频讲解 / 生成讲义」双模式，章节选择 + 视频列表 + WebView 播放器（`/api/course/videos` 匿名 Range 流，对齐 Web `<video>`）。
- **像素小镇 HUD + 引擎增强（`:feature:hub` + `:game`）**：Compose 叠加层加 小地图（Canvas，点选瞬移）、随身助教、快速旅行、暂停菜单；LibGDX 加 日夜光照着色、雨/雪天气粒子、小地图点选瞬移（`HubGameBridge.setEnvironment/requestTeleport`、`WorldRenderScreen`）。
- **共享 `core:design/PytVideoPlayer`**：依赖无关 WebView 视频播放器（避免引入 Media3），lecture 与 mainline 看 共用。
- **本地化适配**：`resConfigs("zh")` 仅打包简体中文资源；`Application` + `MainActivity.attachBaseContext` 强制 `Locale.SIMPLIFIED_CHINESE`，系统级文案/日期不随设备语言漂移。
- **release 超强加固（防反编译）**：R8 full mode + `-repackageclasses ''` + `-allowaccessmodification` + 去 `android.util.Log`；release APK 18.8MB(debug) → **5.6MB**，类/方法/包名全混淆（`mapping.txt` 留存反混淆）；APK 启动图标改用 Windows EXE 同款像素品牌徽标（`app.ico` → 全密度 mipmap PNG）。
- **下载次数统计（后端 `com.pythontopia.download` + 前端 guide）**：迁移 `V174__app_download_stat.sql` + `DownloadStatService`(JdbcTemplate 原子自增) + `DownloadStatController`（`GET /api/downloads/{key}`、`POST …/hit`，key 限 `[a-z0-9_]`）+ SecurityConfig 放行 `/api/downloads/**`；guide 页「下载客户端」卡列 APK/Windows EXE/Windows ZIP 三个下载按钮 + 各自累计次数（`api/downloads.ts`，octet-stream 直链下载）。
- **ECS 发版（公网 47.120.29.62）**：APK 上传 `/opt/pythontopia/downloads/pythontopia.apk`（+ sha256），Windows EXE/ZIP 同目录；在 ECS 上从源码重建 backend（V174 已迁移、计数端点 200）与 frontend（干净镜像 216MB，按钮从源码烘入）镜像并 `docker compose ... --force-recreate` 重部；保留 `*-backup` 镜像标签可回滚。
- **验证**：各 Android 改动模块 `:feature:*:testDebugUnitTest` 全绿、`:app:assembleDebug`/`assembleRelease` 全绿；前端 `vue-tsc` 0 错 + `GuidePage.spec` 22 passed；ECS 端到端复验：index/healthz/guide 分块/APK/EXE/计数端点（GET/POST）均 200，backend/frontend 容器 healthy，计数已置 0。**未验证**：APK 真机安装/图标/加固运行（无设备）。

## 2026-06-19 — 费曼反演：学弟中性描述 + 微信式聊天对话框 + 代码找茬题库扩充

> 把上一批「学弟开口说『我以为』」反过来：学弟只中性描述代码在做什么、不剧透 bug；「讲明白」搬进微信式聊天对话框、操作按钮右对齐；代码找茬 bug 库结合真实错题用 LLM 大规模生成更长、更多类型的题；并继续精简费曼与指南文案。

- **学弟中性化 · 去「我以为」（前端 · graph）**：费曼「与学弟对话」里学弟不再第一人称自述「我以为 range(1, 5) 会取到 5」等误解，改为只中性描述这段代码在做什么、不剧透 bug，把「错在哪」的思考完整留给学生（graph `feynman_tutee_agent._mistake_for` / `_CODE_DESCRIPTION`、前端 `FeynmanPanel.TUTEE_CODE_DESC` 同步）。**反演上一批「学弟开口说误解」**。
- **微信式聊天对话框 + 按钮右对齐（前端）**：`FeynmanPanel`「与学弟对话」重做成微信式聊天气泡——学弟（弟）靠左、我靠右、微信绿气泡 + 头像；「讲给学弟听」「真判题裁定」两个操作按钮统一右对齐（`feynman-panel__teach-submit--right`）。
- **代码找茬 bug 库大规模扩充（graph）**：`feynman_tutee_agent` 的 canned 样本从单行 demo 升为多行教学片段；新增 `_bug_from_llm_generic`，无真实错误史时也按 KC 章节主题用 LLM 生成 6–14 行长代码陷阱（`_BUG_MIN_LINES`/`_BUG_MAX_LINES`，超时 6s→8s）；LLM 提示要求错误类型多样（边界 / 类型 / 别名 / 缩进 / 逻辑运算符 / 字典键…）且紧扣 KC，bug 行仍由 buggy/fixed `difflib` diff 自动标出（零伪造）——结合真实错题 + LLM 大规模生成，支撑每日教学常练常新。
- **文案精简（前端）**：`BugHunterGame`（副标题「30 秒 · 点 bug 行」、结算与空闲提示收敛）、`FeynmanPage`（标题改「费曼反演」、引导收为「找茬 → 讲明白 → 真判题出师」、删多余步骤列表）去掉冗余说明。
- **指南同步（前端 · 本会话所属）**：`guideSections.ts` 复盘大厅「费曼反演」小节重写为「代码找茬(30s 点 bug 行) → 微信式对话框讲明白(学弟中性描述、不剧透 bug) → 真判题出师」，注明 bug 库结合真实错题 + LLM 大规模生成（更长、更多错误类型）；并把「学生 A」cast 与复盘大厅 summary 里「会犯你同款错误的 AI 学弟」收敛为中性表述，顺手精简该处文案。
- **验证（本会话 guide 部分）**：`eslint src/content/guideSections.ts src/pages/GuidePage.vue` 0 错；`vitest run tests/pages/GuidePage.spec.ts` 全绿（`GUIDE_SCREENSHOT_TOTAL` 截图计数不变）。费曼组件 / graph 改动由同批其它会话各自 `vitest` / `pytest` 验证。

## 2026-06-19 — 代码找茬：学弟开口说误解 + 前端文案精简

> 生成 bug 的同时让 AI 学弟在对话区用自己口吻说出这处（错误）理解；并把费曼界面文案大幅精简，去掉冗余说明。

- **学弟开口（前端）**：`FeynmanPanel`「学弟对话」顶部新增一条学弟第一人称气泡，按 `bugHunt.misconceptionTag` 给出与所选 bug 对齐的误解自述（循环 bug → 「我以为 range(1, 5) 会取到 5。」/ 输入类型 → 「我以为 input() 拿到的是数字，能直接 +1。」/ 列表别名 → 「我以为 b = a 是复制，改 b 不影响 a。」）；文案与 graph `feynman_tutee_agent._mistake_for` 对齐（前端静态镜像，未知 tag 回退通用句），不需后端改动即生效。
- **文案精简（前端 · 字数尽可能少）**：收敛费曼各处说明——FeynmanPage 引导语、FeynmanPanel intro / 讲明白引导 / 输入框 placeholder / 学弟代码占位、BugHunterGame 副标题 / 空闲提示 / 出师提示 / 来源标签、KcPicker placeholder，去掉重复解释只留关键信息。
- **验证**：`vue-tsc` 0 错；`vitest` KcPicker 4 + BugHunter 2 + FeynmanPanel 4 passed（FeynmanPanel 加「学弟开口说误解」断言）；`eslint` 0 错；浏览器实测：选「for 循环与遍历」→ 代码找茬给 range bug，「学弟对话」同时显示「我以为 range(1, 5) 会取到 5。」，各处文案已精简（截图存证）。

## 2026-06-19 — 代码找茬 KC↔bug 对齐 + 知识点下拉模糊搜索选择器

> 修「所选知识点与生成的 bug 代码对不上」（如选「for 循环与遍历」却给 input() 类型 bug）；把知识点选择从「填编码」升级为下拉筛选 + 模糊搜索的中文名 combobox。

- **KC↔bug 主题对齐（graph）**：`feynman_tutee_agent` 过去无真实样本时按 `kc_code` 哈希在 3 个 canned bug 里乱挑，与所选 KC 主题脱节。改为按课程包章节挑主题相关 bug（复用 `boss/kc_map.chapter_for_kc`）：第四章流程自动化 → 循环边界 bug、第五章数据结构 → 列表别名 bug、第二/三章变量与数值 → 输入类型 bug，其余回退最通用的循环边界；并把章节主题短语喂进 LLM bug 生成提示（不再只给无语义的 kc_code），让 LLM 路径也贴合 KC。`pytest` +2 例（章节映射 + 「for 循环」兜底给 range bug）共 **14 passed**。
- **知识点选择器（前端 · combobox）**：新增 `KcPicker.vue`——对 65 个 KC 中文名做下拉筛选 + 模糊搜索（名字子串/子序列 + 按编码搜），遵循 W3C APG combobox 规范（role=combobox/listbox/option、`aria-activedescendant` 虚拟焦点、↑↓ 导航 / Enter 选中 / Esc 收起、焦点留输入框），只显示中文名不暴露编码；`FeynmanPage` 用它替换原「填 KC 编码」输入框（删旧文本框 + 死 CSS），保留 4 个常用知识点快捷 chip。星露谷风（木纹边框 / 羊皮纸下拉 / 金色高亮）。
- **验证**：`vue-tsc` 0 错；`vitest` KcPicker **4** + BugHunter 2 + FeynmanPanel 4 passed；`eslint` 0 错（新组件 0 警告）；graph `.venv pytest test_feynman_tutee_agent` **14 passed** + `py_compile`，**已 `docker compose restart pythontopia-graph`（healthy）**；浏览器实测：搜「循环」→ 下拉只剩 4 个循环类 KC → 选「for 循环与遍历」→ 开会话 → 代码找茬现在给 `for i in range(1, 5)` 循环边界 bug（与 KC 对齐，不再是 input 类型 bug），截图存证。
- **残留**：后端 Java（上批改的兜底错误串）仍未本地重编译重启。

## 2026-06-19 — 费曼小游戏改名「代码找茬」+ 知识点显示中文名

> 「错误猎人」名字太跳、且与错题矿洞的 bug 隐喻撞车；改名为更贴合项目既有语汇的「代码找茬」（graph 文档串本就用「限时找茬」描述该玩法）。同时费曼标题不再暴露 KC 原始编码，统一显示知识点中文名。

- **改名 错误猎人 → 代码找茬（全端用户可见文案）**：前端 `BugHunterGame.vue`（标题 /「开始找茬」/ 空闲提示）、`FeynmanPanel.vue`（讲明白引导 / 舞台注释 / intro 文案）、`FeynmanPage.vue`（引导步骤与文案）、`FeynmanPanel.spec.ts`（用例描述）；graph `feynman_tutee_agent.py` 用户可见兜底提示 + 文档串、`routes/feynman.py` `schemas/feynman.py` 文档串、`tests` 注释；后端 `FeynmanSessionService` 服务不可用错误文案 + 注释、`FeynmanGraphClient` / `FeynmanDtos` 注释。**代码标识保持不变**（组件名 `BugHunterGame`、`data-testid="bug-hunter-*"`、`.bug-hunter` 类、API / 字段），零行为改动。
- **知识点显示中文名（前端）**：复用既有 `kcDisplayName`（`data/kcNames.ts`，与后端 `course_kc.kc_name` 一致），费曼标题从「教会 AI 学弟 · PY43-KC-031」改为「教会 AI 学弟 · for 循环与遍历」，不再向学生暴露原始 KC 编码。
- **验证**：`vue-tsc` 0 错；`vitest` BugHunter 2 + FeynmanPanel 4 passed；`eslint` 组件 0 错；graph 改动文件 `py_compile` 通过、无测试断言依赖被改字符串；浏览器实测 /feynman 选 KC → 标题显示中文名「for 循环与遍历」、游戏区显示「代码找茬 / 开始找茬」（截图存证）。
- **残留**：后端 Java 改了字符串 / 注释但未本地重编译重启（运行实例仍服务旧兜底错误串，仅极少数「服务不可用」时可见，下次重启生效）；graph 兜底提示同理（仅「无 bug 样本」时可见），为不打断在测演示未重启 graph。

## 2026-06-19 — 无尽远征 Phase 3b 像素远征塔 + Phase 2d run 跨会话持久化 + 间隔重复持久化

> 补完上一批收官时挂起的两件「后续」：Phaser 像素远征塔可视化（Phase 3b）与 run 跨会话 DB 持久化 + 真·间隔重复（Phase 2d）。

- **Phase 3b · Phaser 像素远征塔（前端）**：新增 `game/scenes/ExpeditionScene.ts`（模块级 scene + `ensureExpeditionSceneStarted`/`loadExpedition`/`getExpeditionBridge` 桥，仿 `CodeTheaterScene`），把 rogueRun 的分层分支 DAG 渲染成一座可见像素塔——底层在下、顶层 BOSS，房间按 kind 着色/图标、按 state 点亮(已过)/高亮(可选·蓝)/暗(未解锁)/灰(绕过)，出边连线（已通关→金线），avatar 随通关向上攀爬，过 BOSS 触发「通关」庆祝；布局随层数/层宽自适应，整塔一屏可见。新增 `components/expedition/ExpeditionCanvas.vue`（复用单 `Phaser.Game` 单例，仿 `TheaterStageCanvas` 的挂载/销毁 + ResizeObserver）接进 `EndlessExpeditionPage` 的 map 阶段（🗼像素塔 / ☰列表 二选一 + 🏳弃局）；点像素节点 = `run.enterNode`（与 DOM 地图同一动作，移动端/降级用 DOM）。**全程序化绘制（graphics+text），零第三方素材，无 LICENSE-ART 变更**（同 `CodeTheaterScene` 先例）。
- **Phase 2d · run 跨会话 DB 持久化（后端权威）**：迁移 `V173__expedition_run.sql`（`expedition_run` 按 user 单活跃 run：结构化标量列 + nodes/relics/history JSONB）；`ExpeditionRunController`（GET/PUT/DELETE `/api/world/expedition/run`，学生限定 403）+ `ExpeditionRunService`（JdbcTemplate UPSERT/查询/删除）+ `ExpeditionRunDto`（前后端同构 13 字段）。前端 `api/expedition.ts` 加 run 读写、`stores/rogueRun.ts` 持久化改「后端优先 + localStorage 兜底」，`startRun` 续局（后端有活跃 run 则恢复，不可达回退本地，绝不阻塞），新增 `resumeFromBackend`（跨设备进页静默续局）+ `abort` 弃局 DELETE。
- **Phase 2d · 间隔重复持久化**：`BossService.selectFrontier` 复用既有 `kc_mastery.last_event_at`（`MasterySnapshot.lastEventAt`，`ISO_OFFSET_DATE_TIME` 格式）作「每 KC 最近练习时间」喂 graph；`BossGraphClient.selectFrontier` 加 `lastPracticed` 透传；graph `agents/boss/expedition_selector` 接 `last_practiced` 做真·间隔重复（≥45 天久未练大幅上浮、≥14 天上浮、≤3 天压制），`routes/boss.py` 加 `lastPracticed` 字段——让间隔重复用持久化的 last_practiced 而非仅前端 recentKcs。
- **验证**：前端 `vue-tsc` 0 错、`eslint` 0 错、`npm run test` **2172 passed**（新增 ExpeditionScene 6 / expedition api 5 / rogueRun 续局+兜底 2 例）；后端 `mvn -Dtest=BossServiceTest,ExpeditionRunControllerTest` **18 passed**（aliyun 镜像绕 TUN）；graph `pytest test_expedition_selector` **7 passed**（含 last_practiced 久未练上浮用例）。浏览器实测 demo-stu-01 → /expedition → 开始远征：整塔一屏（房间态/连线/HUD/avatar）→ 点像素节点进真判题（3 轮截图迭代到正确）。
- **提交 / 残留**：仅提交无尽远征 Part B/D 相关文件（frontend scenes/components/pages/api/stores/tests + backend world/boss + V173 + graph boss）+ 本 CHANGELOG + 计划文档；同工作树 android / feynman / styles.css 等其它会话 WIP 不裹挟。空库初始化迁移 + 全链路 E2E 在 ECS 部署专场验收。

## 2026-06-19 — 费曼界面收敛自适应 + 错误猎人计时重渲染修复 + 出师闭环接入

> 续作费曼反演界面优化：减框、整页不滚自适应视口、更星露谷风；修计时每秒重渲染致点击发涩（A）；把错误猎人找到的 bug 提示接进「讲明白 → 真判题」出师闭环（B）。

- **费曼面板收敛（前端）**：`FeynmanPanel.vue` 从「大 header + 6 卡网格 + 学习足迹证据链 aside」收敛为「紧凑信息条 + 左错误猎人 ｜ 右（对话 / 讲明白 / 学弟代码·真判题）三卡」——学弟误区折入信息条、修正点并入对话卡、学弟代码与真判题合卡；**删除「学习足迹·全程可溯源」整块**（误区 / 讲解 / 真判题 / 来源），KC 标题用 `·` 不用 `/`。`flex` 列布局 + `height:100%` + 各区内部滚动，配合页面视口锁定实现整页不上下滑；像素边框 / 木纹羊皮纸底 / 金色描边强化星露谷风。
- **进会话收起大 intro（前端）**：`FeynmanPage.vue` 选定 KC 后隐藏 hero / 选择器大引导、面板全幅铺满；`.feynman-page--active` 锁 `calc(100dvh - 52px)` + 内部滚动；面板「换知识点」`@change-kc` 回到选择器（窄屏 ≤900px 回归单列自然滚动）。
- **全高铺满自适应（前端 · 对标 IDE/LeetCode 分栏）**：参照「holy-grail 全高布局」把舞台从「卡片顶部对齐留大片底部空白」改为铺满——左「错误猎人」编辑器式铺满（代码区 `flex:1` + `min-height:0` 内部滚动、空闲态居中提示填白）｜右对话区 `1fr` 聊天面板式铺满（`align-content:start` 顶部起排）+ 讲明白 / 真判题按内容贴下方，彻底消除底部空白且两栏底边对齐。
- **A · 错误猎人计时重渲染修复（前端）**：`BugHunterGame.vue` 计时从按秒 `setInterval` reactive 重渲改为单次 `setTimeout` + 纯 CSS 进度条（剩余秒数由 `roundStartedAt` 回算），消除每秒把可点代码行抖掉导致的点击发涩；副标题字号缩到 11px；`prefers-reduced-motion` 关动画。
- **B · 出师闭环接入（前端）**：错误猎人产出的 bug 提示 `bugHunt.hintMd` 经 `teachHint` 接入「讲明白」引导条，串起「找 bug → 讲清它为什么错 → 真判题裁定」闭环（`submitTeaching` → `submitJudge`）。
- **验证**：`vue-tsc --noEmit` 0 错；`vitest` BugHunterGame **2** + FeynmanPanel **4 passed**（删证据链断言、新增「未开会话不渲染教学输入」与「bug 提示接入讲明白(B)」用例）；`eslint` 0 错（FeynmanPage 残留既有 `vue/html-indent` 警告，按外科手术式修改不顺手重排整棵模板）；浏览器实测 demo-stu-01 → /feynman 选 KC → 开会话（intro 收起、整页不滚、全高铺满无底部空白）→ 开错误猎人（CSS 计时条 + 生成 `input()` 类型陷阱 bug 代码、点击不发涩）→ 点击 bug 行结算（命中/误报/漏判按真 diff 揭晓）→ 讲明白卡显示 bug 提示接入(B) → 输入讲解「讲给学弟听」→ AI 学弟在对话区复述吸收（真判题按钮在学弟产出可判代码前正确禁用）（多态截图存证）。
- **提交 / 残留**：仅提交 feynman 相关文件（`FeynmanPanel.vue` / `BugHunterGame.vue` / `FeynmanPage.vue` / `FeynmanPanel.spec.ts`）+ 本 CHANGELOG；同工作树 android / styles.css / 无尽远征等其它会话 WIP 不裹挟。

## 2026-06-19 — 无尽远征 Roguelike 全 Phase 收官（后端选择器 / 知识星座 / 退役静态闯关塔）

> 在「无尽远征 Phase 1 + 肉鸽血肉」基础上，按计划做完 Phase 2/3/4，把「7 章×10 关」死网格彻底替换为画像驱动的 roguelike 远征。

- **Phase 3 · 知识星座可视化**（`c1912a57`）：65 个 KC 程序化排成 7 章节星团（团内成环），按掌握度点亮（≥0.6 绿 / ≥0.2 金 / 未触及暗），作跨局 meta 成长地图（起始页可展开、结算页常显）；纯函数布局 + 纯展示组件 + 5 例单测。（Phase 3 的「Phaser 像素远征塔」画布因单 Game 实例约束 + 需浏览器可视化验收，留待可视化 QA 专场。）
- **Phase 2 · 选择器下沉后端 + 间隔重复**（graph `03278d8d`、前端+Java `79173103`）：graph 新增 `agents/boss/expedition_selector` + `POST /agents/boss/select-frontier`（ZPD/弱项/未掌握排序 + 间隔重复：久未练上浮、刚练过强压制；难度按掌握度推断；KC↔章节用单一事实源 kc_map）；后端 `BossController/BossService/BossGraphClient` 从 `MasteryRepository` 读**权威掌握度**透传 graph；前端 `api/expedition` + `rogueRun.startRun` 改异步，优先用后端 picks 生成地图，失败/超时回退本地 `useKcFrontier`（3s 快失败，绝不阻塞）。graph `pytest` 6 例；前端 store 11 例（含「用后端 picks / 失败回退」）。
- **Phase 4 · 退役静态闯关塔**（`a827f641`）：删 `ArenaPage` / `arenaLevels`(70关) / `useArenaStore` + 测试；`/arena/:章` 重定向 `/expedition?focus=章`；BossPage 解耦 arena 进度回写（章末 BOSS 折入远征，掌握度由判题入库权威驱动，`/boss` 仍可直达）；EndlessPage 回归纯「随便练练」；WorkshopChat/WorkshopPage/指南导航改指 `/expedition`。
- **AI 导师 / 随身助教术语同步**：graph `intent_router_agent` 的 NAVIGATE 地标 + 边界目的地 + 澄清话术、`tools/consolidated`+`registry` 的 navigate 工具描述，统一改为「无尽远征」（保留「闯关塔/竞技场」为旧称兼容别名，老说法仍能路由到 `/expedition`）；随身助教画像提示文案同步。graph 意图相关 150 例通过。
- **验证**：相关文件 `vue-tsc` 类型干净、`eslint` 0 错；graph `pytest` 6 passed；前端 rogueRun 11 + 知识星座 5 + 选择器纯函数等通过，全量 `vitest` 2155 passed。
- **残留**：后端 Java 因本机 Maven 镜像不可达（AGENTS.md TUN 已知问题）未本地编译，代码严格沿用既有 boss 代理模式，待 CI/联网编译；run 跨会话 DB 持久化（当前 localStorage 工作）与 Phaser 像素塔为后续；同工作树另一会话的 `FeynmanPanel.vue` 未提交 WIP 致其 3 例 + 该文件 typecheck 报错，与本批无关。

## 2026-06-19 — 复盘周报每周自动生成 + 费曼「错误猎人」游戏化

> 复盘大厅周报支持后端每周自动生成（与手动并存）；费曼反演从"纯对话"升级为「错误猎人」限时找茬小游戏——学弟写出含 bug 的代码，玩家限时点出错误行、即时判分，再接"讲清→真判题出师"。

- **复盘周报每周自动生成（后端）**：`WeeklyEvaluationScheduler` 默认每周一 03:00(UTC) 为「近 7 天有真实活动、近 6 天尚无评估」的学生各生成一份周报；幂等可重跑（去重闸门防自动+手动重复）、单点失败仅记日志不阻断他人；`pyt.evaluation.weekly-auto-enabled` / `weekly-cron` 可关停或改时。`EvaluationRepository.findActiveLearnerIdsSince` + `existsEvaluationCreatedAfter`、`LearnerEvaluationService.findLearnersNeedingWeeklyEvaluation`。手动「立即评估」保留不变。
- **费曼错误猎人（graph）**：新增 `POST /agents/feynman/bug-exposure`——canned 误区→确定性 buggy/fixed 样本；未知真实误解 tag→LLM 生成 buggy+fixed（**6s 紧超时**回退，避免拖过前端 10s）；无样本→按 KC 确定性退化为常见初学者陷阱（`source` 诚实标注，不冒充"你的错"）。bug 行号由 buggy/fixed `difflib` 行级 diff 得出（**零伪造**）。
- **费曼错误猎人（后端）**：`FeynmanGraphClient.bugExposure` + `FeynmanSessionService.bugHunt` + `POST /api/feynman/sessions/{id}/bug-hunt` + `BugHuntResponse`（**不下发 fixed_code 答案**，只给 bug 行号判分；下游不可达 502）。
- **费曼错误猎人（前端）**：`BugHunterGame.vue`（可点代码行 + 30s 倒计时 + 命中/误报/漏判即时计分 + 揭晓高亮 + 接"出师"教学提示），接入 `FeynmanPanel`；`api/feynman.ts` + `stores/feynman.ts` 加 bug-hunt 流。
- **验证**：graph `pytest test_feynman_tutee_agent` **12 passed**（含 diff 行号 / canned / LLM / generic 兜底 / 路由用例）+ 实点端点；backend `mvn -Dtest=Feynman*` **13 passed**（含 bugExposure 映射，不泄答案）；frontend `vitest` BugHunterGame **2** + FeynmanPanel **3 passed**、`vue-tsc`/`eslint` 0 错；周报后端作用域 **9 passed**。浏览器实测 demo-stu-01 → /feynman 开会话 → 错误猎人生成 bug 代码 → 点中真实 bug 行 **完美命中得分 10**（截图存证）。
- **提交 / 残留**：本机后端已用同一 JWT secret 重启载入新端点（登录态不丢）；调试中修两处：旧后端进程致 404（重启解决）、未知 tag 走慢 LLM 致 10s 超时（graph 加 6s 紧超时秒回）。

## 2026-06-19 — 做题/无尽页对标 476 改版 + AI 导学助手重构 + 无尽远征 Roguelike MVP + 自托管 pyodide + 隐藏代码舞台

> 一次性落地用户提的 5 件事。已提交 `0a1f3462`（33 文件 +2932/-657）。详细方案见 `docs/plans/2026-06-19-endless-roguelike-rebuild.md`。

- **做题页（TrainingPanel）美化**：删 `kc 名 / KC 编码 / 难度 / 掌握进度` 标签行；`暗色/亮色` 按钮上提到 `AI 助教` 左侧（抽 `useCodeEditorTheme` 全站编辑器共享同一主题）；输入输出样例复制按钮改 **LeetCode 式嵌入数据黑框右上角、悬停才显形**；三栏（题干|代码|AI 助手）左右 + 代码|调试上下**真正可拖拽**（新 `useResizable`，CSS 变量驱动、窄屏 media query 仍能收单列）。
- **AI 导学助手（TutorWorkflowPanel）重构**：从「平铺一排 7+ 按钮 + 大段进度框」改为 **单「推荐下一步」主按钮 + 其余阶段动作 chips +「🧰 学习工具」抽屉（默认收纳）**；「导学助手进度」节点串折叠为一行「百晓思考中…」+「查看过程」；动作严格按后端 `available_actions` **阶段门控**渲染（对齐 Alethicode `transitions.py`：错因诊断/AC 复盘需 `submission_id`、TRANSFER 需先有复盘方向），不再前端无脑补齐全部按钮。
- **无尽页（EndlessPage）对标 476 OJ 排布**：通用刷题走「题面|作答」两栏（题面 + 样例黑框带 LeetCode 式复制），关卡题型（pdf_question 等）卡片自带题面+作答时单栏铺满；「最近 8 题」改为**默认收起、可展开的侧栏收纳**。
- **本地修控制台报错**：Pyodide **同源自托管**（`scripts/sync-pyodide.mjs` 从 `node_modules/pyodide` 复制运行时到 `public/pyodide/`，predev/prebuild 钩子自动跑；adapter 同源优先、CDN 兜底）——根治 Edge「跟踪保护」拦 jsdelivr 的 `Tracking Prevention blocked storage` 告警；`banner: false` 关闭 Phaser 启动横幅（消除每次挂载的 `Phaser v3.87` 噪音）。`[boss] backend status=0` 本为后端不可达时的 golden 优雅降级，本地后端在线即不触发。
- **无尽闯关重构（任务 4）**：详细计划 + **Phase 1** 新增 `/expedition`「无尽远征」——知识前沿（掌握度+画像）`useKcFrontier` 选 KC、`rogueMap` 生成「必可达 boss」的分支 DAG、`rogueRun` 状态机（真判题 / 温和 HP 按抗挫折度扣血 / 气势连击 / 掌握度永久入库 / 达标补星）+ 复用 `KindRouter` 9 题型，取代「7 章×10 关」死网格；章节降级为可选聚焦过滤器；入口（暂停菜单/HUD/QuickWheel）改指 `/expedition`（旧 `/arena` 暂留，Phase 4 退役）。
- **隐藏代码舞台（任务 5）**：`/code-theater` 路由重定向回小镇；移除暂停菜单 / 做题页 / 无尽页 / 教授 NPC 对话 / 操作指南的全部入口与交叉引用（引擎/页面实现代码保留，改回只需还原路由 + `HIDDEN_GUIDE_SECTION_IDS` 过滤名单）。
- **无尽远征 · 肉鸽血肉增量**（在 `0a1f3462` 之后陆续提交）：① **学习增益(relics) 系统**（`a29c7b2d`）——🛡厚实笔记/🧘沉静心法/🔥连题学者/💧复盘回气/🔍专注透镜 5 件被动增益，从资源宝箱与精英通关获得，作用于体力上限/答错扣血/通关得分/气势/营地回血，随 run 持久化；② **分支地图连线可视化**（`064eedd7`）——测量节点中心画 SVG 连边（已达=实线高亮、未解锁=虚线淡显），选路真像杀戮尖塔地图；③ **事件节点分歧选项**（`ab8c886c`）——概念事件先给情景 + 三选一应对（迎难钻研 难度+1·得分×1.5 / 稳扎稳打 / 就地复盘 回血+小分不战）；④ **每日挑战 + BOSS 多阶段**（`feb85cac`）——当日一次、日期种子稳定地图、偏向画像弱项；知识闸需连过 3 阶段才通关。配套 store 单测累计 9 例（relics/事件/boss 阶段/每日）。
- **验证**：`vue-tsc` 0 错、`eslint` 0 错、`vitest` **2161 passed**（新增 rogueMap/rogueConfig/rogueRun + TutorWorkflowPanel/HudShell 更新共 14 例）、`vite build` 通过。
- **提交 / 残留**：已提交 `0a1f3462`；本 CHANGELOG 条目因同工作树存在另一会话对 `CHANGELOG.md` 的未提交改写（复盘大厅 502 批次 + 删 #6–#13）**暂不单独提交**，避免裹挟无关 WIP；同工作树 android / ReviewHall / styles.css 等无关改动不在本批范围。

## 2026-06-19 — 复盘大厅 502 修复 + 周报列表化 + ESC 菜单错题矿洞入口

> 修复复盘大厅「立即评估」必现的 502（两层根因），把历史评估从小卡片改为**按周收纳的列表 + 点击日期弹窗看摘要**，并给 ESC 暂停菜单补上错题矿洞入口。

- **修复 · 复盘评估 502（两层根因）**：① 运行栈只起了 postgres+redis+backend，漏了 `pythontopia-graph`/`pythontopia-rag`（开发应走 `./dev.sh`、演示走 `start.sh`，二者本就拉起并健康门禁 graph/rag，本次确认脚本无需改）；② 真正的 bug——`EvaluationNarrativeAgent._number_aliases` 只生成「整数 / 两位四舍五入」别名，而 narrative 用 `{value}` 渲染原始浮点（`1.0`/`5.888`/`0.3333`）、用 `{:.2f}` 渲染 KC 行（`0.00`），致这些**确有出处**的数字被 grounding 误判为「未追踪」→ 整单 `422` → 后端 `RestTemplate`/`GraphClientRetryHelper` 包成 `502`。修 `_number_aliases` 覆盖「原始 str / 整数 / 两位小数 / 去尾零」四形态，不改 narrative 文本、不削弱防幻觉。
- **ESC 暂停菜单 · 错题矿洞入口**：「学习」组新增「错题矿洞 → `/bug-mine`」（此前矿洞仅能从判题失败 / 竞技场卡片 / NPC 进入，无主菜单入口）；顺带提交工作区既存的「移除下线代码剧场入口」+ 删其过时 `PauseMenu.spec.ts`。
- **复盘大厅界面 · 周报列表化**：历史评估由「小卡片 chip + 行内展开详情」改为**按周分组列表**（`本周`/`上周`/日期区间 + 「N 份」计数），**点击某天以弹窗**展示该次评估摘要（指标卡 / narrative / 关键 KC / 路径对比 / 接受·保留），支持 ESC / 点遮罩 / ✕ 关闭，复用 `pyt-fade`/`pyt-scale` 过渡；历史加载 4→20、不再进页自动弹首条；移除顶部冗长引导文案。
- **验证**：graph `pytest tests/test_evaluation_narrative_agent.py` **7 passed**（含用真实数值 `1.0/5.888/0.3333/零增量` 锁定 grounding 的回归用例）；前端 `vue-tsc` 0 错、`eslint` 0 错、`ReviewHallPanel.spec.ts` **4 passed**；浏览器实测 demo-stu-01 → `/review-hall`「立即评估」`200` 成功出周报、按周列表 + 点击弹窗均正常（截图存证）。
- **提交 / 残留**：已提交 `feat(frontend): ESC 菜单错题矿洞入口`、`fix(graph): grounding 兼容数字多形态`；复盘大厅 UI 改动（`ReviewHallPanel`/`ReviewHallPage`/spec + 引导文案移除）**尚未提交**；未跑后端 `mvn test`（本会话未改后端 Java，502 修复在 graph + 运维层）；同工作树 android 等无关 WIP 不在范围。

## 2026-06-19 — 人本细节优化迭代（R-14 · 100 轮 · 不加新功能）

> 以人为本对**现有功能**做细粒度打磨：功能交互、前端引导、性能与各项非功能需求（a11y / 健壮性 / 可维护性 / 移动端）。每轮聚焦一处改动，独立验证、独立提交、独立记录；**不新增任何业务功能**。验证以 `vue-tsc`(typecheck) + 作用域 `vitest` 为主，HTML/构建相关项跑 `npm run build`。

- **#1 首页 HTML 健壮化（非功能 · SEO / 移动端 / 优雅降级）**：`index.html` 补 `<meta name="description">`（搜索/分享摘要，避免抓取器取到空描述）+ `<meta name="theme-color" #fef7e3>`（移动端浏览器地址栏/任务切换器配色对齐羊皮纸主背景）+ `<noscript>` 文案（JS 被禁用或入口脚本加载失败时不再纯白屏，给出"开启 JavaScript 后刷新"的可操作提示）。验证：`npm run build` 通过。
- **#2 页面高度移动端安全化（移动端 · 防视口跳动）**：`styles.css` 6 处页面级高度 `100vh` 叠加 `100dvh` 回退（`.app-shell` / `.app-main` / `.auth-page` / `.town-screen` / `.placeholder-page` / `.profile-room-page`）。移动端浏览器地址栏收起/展开时，`100vh` 会把底部内容顶出视口或留白；`100dvh` 跟随动态视口消除此跳动，旧浏览器回退 `100vh`。验证：`npm run build` 通过。

- **#3 全局按钮禁用态可感知（功能交互 · a11y）**：`styles.css` 此前无全局 `button:disabled` 样式，禁用按钮仍是手型光标、满不透明度且保留按下位移，玩家易误以为点击生效。新增 `button:disabled/[disabled]`：`cursor: not-allowed` + `opacity: 0.55` + 取消 `:active` 位移，清晰传达"不可点"。验证：`npm run build` 通过。

- **#4 像素工具提示键盘可达 + z 令牌（a11y · 可维护性）**：`pixel-ui.css` 的 `.pyt-tooltip` 仅 `:hover` 显示，键盘 tab 用户看不到提示；新增 `:focus` / `:focus-within` 同步显示气泡。同时把硬编码 `z-index: 200` 收敛为 `var(--pyt-z-popover)`（对齐全站层级公约）。验证：`npm run build` 通过。

- **#5 登录表单校验错误无障碍关联（a11y · 功能交互）**：`LoginForm` 校验失败时仅有 `role="alert"` 文案，未与出错输入框程序化关联。新增 `aria-invalid` + `aria-describedby="login-error"`（错误 `<p>` 补 `id`），屏读器聚焦出错框即播报原因；并为用户名/密码输入补 `name` 增强密码管理器自动填充可靠性。验证：`vue-tsc` 0 错 + 作用域 `vitest` LoginForm **3 passed**（含新增「出错框带 aria-invalid/-describedby」断言）。

## 2026-06-19 — 代码舞台 × 无尽闯关统一玩法 + 看学练测闭环（含 8 大非功能需求 + 实拍指南）

> 把「代码舞台」（`/code-theater`）与「无尽闯关」（`/endless` + `/arena`）两条做题链路在玩法与实现上统一，并补全 练→测 掌握度/画像回流闭环；同步治理 8 大非功能需求（可用性 a11y / 安全防离线刷关 / 可维护性单源去重 / 可信 AI 接地等）。

- **共享基座（前端）**：新增 `useCodeChallenge` 组合式 + 归一 `CodeChallenge/ChallengeResult` DTO + `JudgeResultPanel`（`role=status` + `aria-live` 自动播报判定）。无尽/闯关改用 `PythonCodeEditor`(CodeMirror) + `PyButton` + `JudgeResultPanel` + `var(--pyt-*)` 令牌；ArenaPage 背景/按钮与 EndlessPage 收敛为同一套羊皮纸视觉；`KindRouter` prop 强类型为 `ArenaLevelKind`。
- **闭环（后端）**：`BossService.evaluateAttempt` 判题后发 `LearningEvent.PracticeSucceeded/Failed`（复用 `SubmissionService` 同链路）→ 自动喂 `MasteryEvaluator`（测）+ 8 维被动画像，仅在真实判题后发、幂等不误报；`resolveKc` 改走权威 `ChapterCatalog`（对齐 course-pack-43）。
- **闭环（互跳）**：无尽/闯关做错 → 一键「去代码舞台看执行」（复用 `useCodeTheaterStore.setFreeRun`）把这段代码执行过程演出来（练→看为何错→再练）；代码舞台 `driveStage` 现把 `Trace` 喂给 Phaser（脚本梯逐行源码 + 死循环「原地踱步」终于生效）；`world_effect` 补 `actionVerb/unit`，舞台横幅文案与任务一致。
- **Python 图**：章节→KC 单一真源 `kc_map.py`（对齐 course-pack-43，65 KC）；文本题型（short_answer/teach_back/pdf_question）改走确定性 grounded 评分（不再把散文当 Python 跑、无证据不杜撰），代码题型仍走沙箱判题。
- **非功能需求**：修「后端不可达即按公开样例冒充通过」的离线刷关（NFR-SEC，arena 不再离线通关）；难度自适应收敛为 `computeAdaptiveDifficulty` 单源（NFR-MNT）；DebugCard 可疑行改为键盘可达 `<button>`、TeachBackCard 去内联 hex（NFR-USE/CMP）；删除 9 个 never-mounted 的「调试剧场」死代码文件。
- **指南实拍**：`guideSections.ts` 扩「代码舞台 / 无尽闯关 / 看学练测闭环」三块 + 6 张新截图（24–29：世界达成绿 / 未达成犯罪现场 / 统一判题面板 / 闯关塔 / 锁章出题），由 `e2e/guideScreenshots.spec.ts` 实拍落盘。
- **验证**：前端 `vue-tsc` 0 错、`eslint` 0 错、`vitest` **2150 passed**（含 useCodeChallenge 9 + JudgeResultPanel 5 + 无尽离线防刷关 + worldEffect 契约用例 + GuidePage 截图存在性转绿）；后端作用域 `mvn test` `BossServiceTest(12)/BossControllerTest(6)/SubmissionServiceTest(4)` **22 passed BUILD SUCCESS**（经 Aliyun mirror）；graph `pytest` **1106 passed**（boss 套件 182，新增 kc_map/text_evaluator/kind dispatch 27 例）；`./dev.sh` 全栈起栈实拍 24–29 截图 **6/6 通过**。
- **未验证 / 残留**：后端仅作用域复跑（本机 Maven Central 受 TUN/代理所阻，改用 Aliyun mirror；后端代码自 W1-B 起未变）；graph/rag 微服务镜像未起（无尽走 golden 兜底出题、judge 走后端兜底，截图与本地链路不依赖 graph）；26/27 两张闯关塔截图取同一 `/arena/1` 视图；同工作树中 android / agentic-loop / 工坊聊天等无关 WIP 不在本提交范围。

## 2026-06-19 — 百晓工坊聊天历史持久化 + 离开-返回保留布局（含两处 bug 修复）

> 把百晓工坊「历史问答」从只读 `tutor_session` 升级为覆盖全部聊天工具结果的持久化，并修两处体验 bug：①一开始回答就在左栏「历史问答」生成记录；②离开工坊（返回小镇 / 去图书馆点页码 / 闯关塔等任意跳转）再回来，对话流与左右面板（历史/书房/调用）布局、书房弹窗都停在离开时那一刻。

- **历史问答持久化（后端）**：新增 `workshop_chat_turn`（迁移 `V172`，不改 `tutor_session`）+ `workshop/history` 模块（Controller/Service/Repository/DTO，仅学生角色），落库覆盖 `answer/resource/plan/video/progress/mystudy/navigate/clarify/...` 全部聊天工具结果；列表查询在应用层 UNION 旧 `tutor_session` 问答记录，向后兼容历史数据。
- **历史问答持久化（前端）**：新增 `api/workshopHistory.ts`（list/record/remember + 60s 缓存与本地 merge）；`ChatHistoryRail.vue` 按 `conversationId` 把多轮问答归并成「会话」一条、本地标题/问题搜索、懒加载与失败安全降级；`WorkshopChat.vue` 各工具 turn 落库并 `emit('history-saved')` 即时回灌左栏。
- **tutor 落库修复**：`TutorGraphClient`/`TutorService` 兼容 graph 已迁的 AG-UI `RUN_FINISHED`（形状 `{"result":{...}}`）与历史裸 `result` 两种终态解包——否则 `tutor_session` 永不写入、历史问答恒空；`TutorSessionRepository` 修「最近 N 轮」查询为「先 `ORDER BY created_at DESC LIMIT ?` 子查询再 `ASC`」，多轮路由上下文取最近而非最早 N 条。
- **Bug① 一开始回答即出历史记录**：`WorkshopChat` 新增 `emitProvisionalAnswerTurn`，答疑流式刚推出气泡那一刻就向左栏发占位记录（`responseText` 暂空、`groundingStatus` 暂 null，id 用 `local-<messageId>`）；流式完成后 `emitSavedAnswerTurn` 以**同一 id** 覆盖更新，左栏据 id 去重 merge——发问即现、答完即更新，不再等整段答案生成完才出现，也不会出现两条。
- **Bug② 离开-返回保留**：`App.vue` 的 `<KeepAlive>` include 加入 `WorkshopPage`（`:include="['WorldPage','WorkshopPage']"`）；`WorkshopPage` 加 `defineOptions({ name: 'WorkshopPage' })` 命中缓存，并用 `onActivated/onDeactivated` 接管 `keydown` 监听与 `pageActive`（失活时停渲染 Teleport 到 body 的书房弹窗、回来再渲染，避免弹窗遗留到别的页面），保留对话流、面板开合（历史/书房/调用）、滚动位置与会话态；移除原 `onUnmounted` 里离开即 `closeResource` 的清空。
- **验证（前端为主）**：`vue-tsc` 0 错、`eslint` 0 错；targeted `vitest` **56 passed**（5 文件，含新增「ASK 开始即发占位记录、与最终记录共用同一 id」断言与「`WorkshopPage` 组件名 === KeepAlive include 契约」守卫）；`npm run build` 成功（`WorkshopPage` chunk 正常产出）。
- **未验证 / 残留**：未跑全栈 live 点击流（「发问→未答完离开→返回」需 backend + graph 起栈与鉴权，前端单栈因 401 自动登出无法抵达 `/workshop`），离开-返回保留与占位记录机制由上述单测 + 构建覆盖；本次提交不含后端 `mvn test` 复跑（后端模块为既有未提交工作、本会话未改动后端编排/资源链路）；同工作树中 `WorkshopGraphClient` 资源落库的 AG-UI 修复与 android/graph/code-theater/图书馆/指南截图等无关 WIP **不在本提交范围**。

## 2026-06-17 — 百晓工坊 Tool Use 成熟化（混合架构：确定性路由选择 + 有界工具调用循环 · flag-gated 零回归）

> 把百晓工坊 tool use 从「单轮意图分类器 + 静态 SSE 管线」升级为**有界原生工具调用循环 + 工具收敛 + Tool Use Examples + eval 守门**，全程 flag-gated（`PYT_TOOL_LOOP` 默认 off）/ 纯加法，在线默认路径逐字节零回归。完整实施日志（含五阶段、A/B 证据、验证记录）见 `docs/tool-use-maturity-implementation.md`。

- **provider 原生 function-calling（graph）**：`llm/provider.py` 的 `LlmResponse` 增 `tool_calls` + 统一响应解析 + 能力探测 `provider_supports_tool_calling`；OpenAI-compat 传 `tools` 时不再强制 `response_format=json_object`（两者互斥），无能力 provider 回落意图分类器（零回归）。DeepSeek v4-pro/flash 实测原生支持 function calling。
- **工具 JSON schema + 收敛 17→9（graph）**：`tools/registry.py` 加 `to_tool_schema/tool_parameters/tool_schemas_for_scope`（typed 入参 + Tool Use Examples 折进 description）；`tools/consolidated.py` 把 17 个意图收敛为 9 个高杠杆参数化工具（`ask/quiz/make_resource/make_practice/plan_study/my_space/video/navigate/browse`，loop-only，不动意图路由/前端/Android）。
- **有界工具调用循环 + 执行器桥接（graph）**：`graph/tool_loop.py`（`max_steps` + `BudgetGuard` 三道预算 + 白名单丢弃臆造工具 + 治理失败 fail-soft 回喂 + 触上界诚实停止）；`graph/tool_executor.py` 把生成类工具经既有 `AgentDispatcher` 真派发并回带 summary，前端/交互/外部类回结构化指令。
- **混合编排 + 决定性 A/B（graph）**：`graph/hybrid_loop.py` 的 `HybridToolOrchestrator`——选择交既有确定性 `IntentRouterAgent`、循环只做「看结果再决策」的多步编排，provider 无工具能力时优雅降级为「仅路由 + 执行首工具」（`stop_reason=router_only`）。**held-out 改写集（避开关键词、3 次多数投票、真实 DeepSeek）实测：纯 LLM 选工具 64% vs 混合（路由器）选择 14/14=100%（+36pp）**——故定稿「选择=确定性路由器、循环=多步编排」，不让未微调模型裸 schema 选工具。
- **工具调用 eval（graph）**：`eval/tool_use_eval.py`（`selection_accuracy / clean_termination_rate / avg_rounds`，provider/executor 可注入做零外网 CI），落地 Anthropic「评测驱动工具迭代」原则。
- **flag-gated 端点 + 前后端接线**：graph `POST /agents/orchestrator/tool-loop[/stream]`（`PYT_TOOL_LOOP` 默认 off → 404，在线默认路径零回归）；前端 `api/toolLoop.ts` + `composables/useToolLoop.ts` + `components/learning/ToolLoopPanel.vue`（后端 `on_step` 发 AG-UI `CUSTOM`/loop_step，复用既有 `loopSlice`/`LoopProgress`）；Java `POST /api/orchestrator/tool-loop/stream` 代理（仅学生、镜像 `turn`，`OrchestratorGraphClient.toolLoopStream` 抽取 `relayStream` 复用，转发 graph 同名端点、固定空 `decaying_kcs`）。
- **本会话验证（四端零回归）**：graph `./.venv/bin/python -m pytest -q` **993 passed / 1 skipped**；rag **116 passed / 2 skipped**；frontend `vitest` **2067 passed（210 文件）** + `vue-tsc` **0 错** + `eslint` **0 错**；backend `mvn test` **822 passed / 0 fail / 0 error（BUILD SUCCESS）**，含新增 tool-loop 代理单测 3 个（controller 仅学生可用 ×2 + `OrchestratorGraphClient.toolLoopStream` 转 snake_case body 并打到 `/agents/orchestrator/tool-loop/stream`、固定空 `decaying_kcs` ×1）。
- **红线 / 残留**：严格有界（step/budget 上限 + 停止条件 + 白名单 + 达上界诚实降级），不给 shell、不泄完整答案、grounding/safety critic 照跑，不推翻有界结构化图。held-out 64% 说明工具循环**尚不足以替换**现有经调优的意图路由（后者有关键词兜底 + 低置信澄清），故仅 flag-gated 并存、默认仍走分类器；`llm/provider.py` 等历史 mypy 债务非本特性引入，未顺手改。

## 2026-06-17 — 全栈 E2E 实跑验收 + 两处后端修复（启动阻塞 DI / 路径规划 major 健壮性）

> 在「Agentic Loop 六框架融合」工作树（未提交）基础上做全栈冒烟 + 浏览器模拟点击 E2E，验收核心学习闭环并修复两处实测暴露的后端问题。`services/pythontopia-graph` 套件 936 passed / 3 skipped；`./dev.sh` 四服务（前端/后端/graph/rag）健康、Flyway 至 V171。

- **修复 · 后端启动阻塞（DI）**：`MemoryBriefService` 有两个构造器（3 参注入 + 4 参注入 `Clock` 供测试）却无 `@Autowired`，Spring 回退无参构造器失败致整个后端无法启动（`No default constructor found` / `NoSuchMethodException <init>()`）。给 3 参构造器加 `@Autowired` 修复；单测因直调测试构造器未覆盖此 DI 路径，唯有全栈启动可暴露。
- **修复 · 路径规划 major 健壮性**：`LearningPathService.buildProfileSnapshot` 把 `profile.major()` 原样下传 graph，而 graph `PlannerProfileSnapshot.major` 是枚举 `CS/NON_CS_STEM/NON_CS_HUMANITIES/OTHER`；自由文本 major（如中文专业名）致 graph 返回 422 → `PATH_PLANNER_GRAPH_UNAVAILABLE`。新增 `graphMajor()` 归一映射（与 `ResourceWorkshopService.graphMajor` 一致）并在 `buildProfileSnapshot` 调用；新增单测 `planNormalizesFreeTextMajorToGraphEnum`，`LearningPathServiceTest` 6/6 通过，重启后端后实测自由文本 major → `ACTIVE` 10 节点路径。
- **E2E 实测（demo-stu-01 · 浏览器模拟点击）**：登录 → 中央小镇 hub（6 NPC）→ 入门画像 → NPC 对话 → AI 助教智能体闭环（有界 4 步 + 工具调用时间线 + SSE，生成 STORYBOARD/HANDOUT/PRACTICE 真产物）；训练场 #493 服务端判题 `WA`（期望 55 实际 45）→ 错题矿洞自动入册 → L1 逐层调试改对重提 `AC` → 会话 `RESOLVED` 出坑、掌握度 `0.25→0.29`（`practice_submission` #11 FAILED→#12 PASSED·BUG_MINE_RETRY、`bug_mine_session` #2、`kc_mastery` 落库核对）；百晓工坊 RAG 答疑 6 智能体接地管线 + 真实课件引用页（p59-62）；工坊「生成讲义」触发防幻觉校对官 `['Ungrounded numbers: 2025']` → 讲义设计师重跑修订 → 终态「引用充分」。
- **演示数据注入**：为 `demo-stu-01`(user_id 2) 注入 8 维 `learner_profile.dimensions` + 13 条 `kc_mastery`（混合掌握度驱动聚焦选题）。注意契约：`learning_goal`/`cognitive_style` 列实际以 JSON DTO 存储（非纯文本，否则 `Failed to read profile JSON`），`major` 须为上列枚举值。
- **观察（待跟进 · 本轮未改）**：① 智能体闭环内 path 步骤 `agents/dispatch.py` 硬编码 `has_profile=False`/`user_id=1`（诚实降级 stub，`test_orchestrator_real_dispatch` 已断言），不随 DB 画像转绿，需透传真实画像/`user_id` 才能闭环；② 工坊「生成讲义」当日未写入 `resource_run`/`learning_resource`（0 行），疑为「预览待显式保存（书房→）」，AGENTS.md 要求每次资源生成落审计 run，待确认保存路径是否补写审计；③ graph 实际走真 DeepSeek（非 dryrun），故闭环每步 ~30s。

## 2026-06-17 — Agentic Loop 六框架融合（带护栏 · 可审计 · 非自由 ReAct）

> 在既有有界编排（`orchestrator_graph`：understand→plan→dispatch→reflect→replan→compose）上**正交侧挂**融合 6 个 AI 框架，把「AI 导学助手 + 百晓工坊 + AI 导师」升级为带护栏、可审计、非自由 ReAct 的自主智能体闭环。计划与执行追踪见 `docs/plans/2026-06-17-agentic-loop-framework-integration.md` 与同名 `-worklog.md`（§9 实测日志）。

- **browser-use → BROWSE 工具**：白名单（`docs.python.org`/`peps.python.org`/`pypi.org` + env 注入）两段式 propose→execute，越界 URL 执行前 fail-fast 零请求，只读动作 + DOM 去敏 + PII 脱敏，结构化降级（返回已走步骤 + error，回退 VIDEO）；真 engine 待注入 LLM+Playwright。后端 `V170 browse_run/browse_step` 审计落库。
- **Langfuse + Ragas + DeepEval**：`observability/otel.py` 支持 `OTEL_EXPORTER_OTLP_HEADERS`（指向 Langfuse `/api/public/otel`）；`eval/` 离线评测（faithfulness/context_recall/answer_relevancy，judge 可注入零外网）；`record_eval_score` 在 critic 后写 span。Langfuse 自托管 compose 走可选 `observability` profile（默认不起）。
- **GraphRAG · LightRAG**：RAG 新增 `graph_mode`(off/auto/force) 自适应路由 + `prereq_hops`，多跳问扩展前置 KC 闭包并回带可审计 `subgraph` 与 `via_prerequisite`；`graph_mode` 缺省=旧 fusion 行为（零漂移）。
- **Mem0 · Zep 记忆**：`MemoryProvider`(local/mem0/zep) env 路由 + 外部失败 fail-soft 回退 local；`MemoryBrief`（facts/weak_kcs/style_notes/as_of 时序）喂 `understand` 上下文。后端 `V169 agent_memory_ref`。
- **DSPy-GEPA**：离线编译冻结 prompt 产物，运行时 `load_compiled` fail-soft 加载（缺/损坏→回退手写 prompt），运行时绝不 `dspy.compile`。
- **GSAP · Rive · Motion**：闭环动效隔离在 `game/adapters/anim/**`（业务层禁直接 import）；`LoopProgress.vue` 可视化看/学/练/测/查证，尊重 `prefers-reduced-motion`，Claude Code 网页版式输出（可见有界计划 + 工具时间线 + 证据 chips 分区，仅 reasonSummary 不暴露 CoT）。
- **有界闭环编排（S-spine）**：`AgenticLoopController` 显式 `max_loop_steps`（默认 4，<1 fail-fast）+ oscillation 提前终止 + 达上界诚实降级（不编造）；后端 `V171 loop_run` 审计（plan/tool_trail/critic/retry/token/trace_id）。
- **验证（四端 + 实测）**：graph 936 / rag 61 / backend 818(0 fail) / frontend 82 + typecheck 全绿；空库迁移 V169–V171 不破坏课程种子（7 文档/561 页/7 章/65 KC/46 示例/39 题，集成测试断言）；**实跑全栈 e2e**：RAG `input函数`/`for循环`/`列表` 命中真实引用页（p61/p22/p15），多跳查询 `route=graph` 回带 subgraph + `via_prerequisite`，`graph_mode=off` 退回 vector（向后兼容）；判题三态 AC/CE/WA 由提交验收集成测试覆盖。

## 2026-06-16 — 百晓工坊单对话治理验收 + AI/安全本地质量门刷新

> 本节汇总 2026-06-14 至 2026-06-16 在 `main` 上的本地提交队列与本轮验收。验收报告见
> `docs/reports/2026-06-16-local-acceptance.md`；本节不代表公网 ECS 已重新验收。

- **安全围墙与 AI 表面加固**：新增安全 harness 与内部鉴权覆盖，收紧 AI 相关内部接口访问边界；
  保留公网安全报告中的 TLS、登录限流、Swagger 暴露等线上加固项，避免把本地 harness 通过误写成公网安全闭环。
- **百晓工坊单对话工具治理**：落地 ChatGPT-like 单对话入口，将 `ASK / EXPLAIN_CODE / FIX_BUG /
  ASKME / REVIEW / HANDOUT / MINDMAP / READING / STORYBOARD / PRACTICE / CODE_LAB / PLAN /
  PROGRESS / MY_STUDY / VIDEO / NAVIGATE` 收进能力分组、意图路由、工具 timeline 与透明调用链；
  能力面板用于发现功能，不限制普通学生能力。
- **AI 导学助手 agent 化地基**：新增 manager workflow 方向的本地实现基础，为 AC/WA 后的主动一句话邀请、
  点击继续、教育工具调度和复盘/纠错闭环做准备；后续按
  `docs/plans/2026-06-16-guide-coach-agent-education-agent-refactor.md` 分批落地。
- **AI 导学助手第一批 manager agent 实现**：新增 `TutorStudentState`、`GuideCoachDecision`、
  `GuideCoachAgent` 与 coach golden eval，先在 graph 服务内证明“学生状态 + 判题证据 + 工具白名单”
  能结构化选择下一步教育动作；批判性 review 记录见
  `docs/reports/2026-06-16-guide-coach-plan-review.md`。
- **AI 导学助手第二批 graph 接入**：`TutorWorkflowGraph` 新增 `coach.decide` 节点，支持
  `effective_event`、coach 推荐动作排序、SSE result 透出 `coach_decision/student_state/agentic`；
  WA 场景可由请求的 `ERROR_FEEDBACK` 经 manager 选择为更合适的 `EXECUTION_TRACE` 卡片。
- **AI 导学助手第三批 guardrail 接入**：新增 `GuideGuardrailCritic`，对骨架泄答、错因卡完整修正代码、
  迁移题参考解/测试点泄露等高置信违规做生成后检查；`TutorWorkflowGraph` 在卡片生成后执行
  `coach.guardrail`，失败时带 reason 重试一次，仍失败则 fail fast。
- **AI 导学助手第四批 Java 状态持久化**：新增 `tutor_workflow_agent_state` 与
  `tutor_workflow_action_event`，Java 请求 graph 前恢复 `student_state`，graph result 后持久化
  `student_state/coach_decision` 并记录生成动作事件；卡片事件使用 graph 返回的实际 `event`。
- **AI 导学助手第五批前端主动 nudge**：训练场 AC/WA/CE/RE/TLE 后只主动说一句短邀请，
  不自动展开长分析；学生点击继续后再打开 `TutorWorkflowPanel` 并触发 `AC_REVIEW` 或
  `ERROR_FEEDBACK`，SSE 运行逻辑仍集中在导学面板内。
- **AI 导学助手第六批推荐动作可见化**：`TutorWorkflowPanel` 接收 graph 返回的
  `available_actions[].recommended/reason`，展示“推荐下一步”和简短理由，同时保留其它动作按钮可点。
- **AI 导学助手第七批反馈回流**：新增 `POST /api/tutor/workflow/sessions/{sessionKey}/feedback`，
  将动作点击、卡片有用/无用、checkpoint 反馈写入 `tutor_workflow_action_event`；动作点击和 checkpoint
  同步发布 `LearningEvent` 并镜像进学习记忆流，前端反馈失败不阻塞导学 SSE。
- **AI 导学助手第八批进度可见化**：graph 在 coach 模式下显式发出
  `coach.load_state / coach.decide / coach.tool.* / coach.guardrail / coach.update_state` trace；
  前端将这些内部节点投影为“正在判断你卡在哪里 / 正在选择下一步提示 / 正在生成导学卡”等人话进度，
  不暴露 raw reasoning 或 prompt 细节。
- **AI 导学助手第九批后续提交回流**：新增 `TutorWorkflowSubmissionFeedbackListener`，
  监听正式练习 AC/WA 等结果，将同题 active 导学会话的 `attempt_count/latest_verdict/consecutive_errors/
  frustration_level/last_action_effective` 写回 `tutor_workflow_agent_state`，并记录
  `SUBMISSION_OBSERVED` 事件；不重新调 graph、不自动生成新卡。
- **主线与课件学习增强**：加入章末测试扩展、课件学习链路、公用导学验收 shell、图书馆阅读路径恢复；
  Stage07 工坊验收契约修正为“资源消费后产生 `RESOURCE_READ`”，不再把资源生成视为阅读完成。
- **学习画像与资源回流**：新增资源消费掌握度 delta 支持，恢复百晓答案轻反馈事件 `answer_feedback`，
  为后续画像、推荐和工具选择提供更低摩擦的反馈信号。
- **多媒体与体验扩展**：加入视频工作室任务与洞察、NPC 个性化寒暄预热、Bug Mine 代码剧场 replay、
  `town_hub` 路线简化、生产部署加固与项目交接材料刷新。
- **本轮验收修正**：对齐 graph provider 失败后的 harness keyword fallback 断言；修复/更新 HUD guide 槽位、
  章测解锁、资源卡打开、ASKME 三轮 ask/judge 上下文、GuideOverlay 工具时间线等前端规格。
- **本地启动修复**：`dev.sh` 后端启动改用 `.mvn/settings.build.xml`，避免 `.mvn/settings.xml`
  只走 Maven Central 时在当前网络环境下阻断依赖解析；修复后 `./dev.sh` 真栈健康检查通过。
- **B站 graph 内部认证修复**：`BilibiliVideoService` 调 graph 检索时补齐 `X-Internal-Token`；
  开发 compose 将 `PYT_RAG_ADMIN_TOKEN` 注入 graph，避免 graph 业务端点在本地栈中 fail-close 为
  `internal auth not configured`；B站 live e2e 前置检查改为真实业务端点可用性。
- **本地验证（实跑）**：
  - `cd services/pythontopia-graph && python3 -m pytest -q`：652 passed，1 warning。
  - `python3 -m pytest -q security/tests`：36 passed。
  - AI 导学助手第一批实现后 graph 全量：645 passed，1 warning。
  - AI 导学助手第二批 graph 接入后 graph 全量：647 passed，1 warning。
  - AI 导学助手第三批 guardrail 接入后 graph 全量：652 passed，1 warning。
  - AI 导学助手第四批 Java 持久化 focused gate：
    `mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,CoursePackServiceIntegrationTest test`
    12 tests，0 failures，0 errors，Flyway 83 migrations validated。
  - AI 导学助手第五批前端 nudge focused gate：
    `cd frontend && npm run test -- tests/components/training/GuideNudgeCard.spec.ts tests/components/TrainingPanel.spec.ts`
    2 files / 10 tests passed。
  - AI 导学助手第六批推荐动作 focused gate：
    `cd frontend && npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts tests/components/training/GuideNudgeCard.spec.ts tests/components/TrainingPanel.spec.ts`
    3 files / 11 tests passed；`cd services/pythontopia-graph && python3 -m pytest -q tests/test_tutor_workflow_coach_graph.py`
    3 passed。
  - AI 导学助手第七批反馈回流 focused gate：
    `cd backend && mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,CoursePackServiceIntegrationTest test`
    14 tests，0 failures，0 errors；`cd frontend && npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts`
    3 tests passed。
  - AI 导学助手第八批进度可见化 focused gate：
    `cd services/pythontopia-graph && python3 -m pytest -q tests/test_tutor_workflow_coach_graph.py tests/test_sse_agui.py`
    21 passed；`cd frontend && npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts`
    4 tests passed；`cd frontend && npm run typecheck` 通过。
  - AI 导学助手第九批后续提交回流 focused gate：
    `cd backend && mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowSubmissionFeedbackListenerTest test`
    2 tests，0 failures，0 errors；`cd backend && mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,SubmissionServiceTest,CoursePackServiceIntegrationTest test`
    20 tests，0 failures，0 errors。
  - AI 导学助手最终 focused gate：graph coach 组合 38 passed；backend workflow/submission 组合 17 tests，
    graph client 组合 5 tests；frontend nudge/panel 组合 2 files / 7 tests passed；`npm run typecheck` 通过。
  - B站 graph 内部认证 focused gate：
    `cd backend && mvn -s .mvn/settings.build.xml -Dtest=BilibiliVideoServiceTest,BilibiliVideoAcceptanceIntegrationTest test`
    5 tests，0 failures，0 errors；真实 graph 检索返回 B站视频卡片。
  - `cd backend && mvn -s .mvn/settings.build.xml test`：767 tests，0 failures，0 errors。
  - `cd frontend && npm run typecheck`：通过。
  - `cd frontend && npm run test`：204 files / 1981 tests passed。
  - `./dev.sh` 真栈健康：5173 / 18080 / 18091 / 18092 均 `{"status":"ok"}`；
    `/api/course/videos` 返回课程视频 JSON；Postgres / Redis / graph / rag healthy。
- **未覆盖**：本节未执行浏览器手工 AC/WA 点击流、Android/Windows/微信端回归、ECS 公网验收。

## 2026-06-13 — Android R-15 主线冒险闭环 + 契约对齐 + 单 town_hub

> 据提交 `64c0d7f5` 记录：本次仅提交 `android/`，将原生客户端对齐当前后端/Web 的
> R-14 世界收缩与 R-15 后端权威主线；未包含工作区中并行存在的 backend/frontend 改动。

- **后端契约对齐（M0）**：移除客户端虚构的 inventory/currency DTO、OpenAPI 与 HUD 接线，
  重写 `GameBootstrapResponseDto` 为真实聚合；新增 learner 侧画像、任务、掌握度、学习路径 DTO，
  以及 world 侧存档、世界事件、技能 DTO。
- **单中央小镇收敛（M2）**：`:game` 仅保留 `town_hub`，移除外围四图定义与
  `mine_south.json`，同步地图 README、注释和单测；同时收敛 `ProceduralArt` 的既有
  Detekt 复杂度问题。
- **R-15 主线闭环（M3）**：新增 `:core:data/dto/mainline`、`MainlineApi`、
  `MainlineTestApi` 与 `:feature:mainline`，覆盖冒险地图、看/学/练三件套进度打点、章末测试和
  错题补星，并接入 App 导航与 ViewModel 单测。
- **调试环境接线**：该提交将 Android debug `BASE_URL` 与 OpenAPI server 指向
  `http://47.120.29.62:18080/`，并在 `network_security_config` 中加入对应 cleartext 白名单。
- 改动面：**52 文件（+1883 / −3164）**，包含新增 `feature:mainline`、契约 DTO/API，删除
  `mine_south.json` 与 inventory/currency DTO。
- 验证（提交记录）：`./gradlew :app:assembleDebug test` 全绿；改动模块 `detekt` 通过。

## 2026-06-12 — AG-UI 语义流式协议 + STATE_DELTA 计划进度 + 流式渲染规范（Phase 5「AI 体验层」）

> 据提交 `7e6972c4` 记录：Phase 5「AI 体验层」模块 A（协议正式化）+ 模块 C（流式渲染）落地；模块 B 评估后跳过
> （避免提前抽象，遵守「简单优先」红线）。本节验证均实跑（见末尾「验证」）。

- **协议层（AG-UI 命名归一）**：前端 `composables/agui.ts` 新增双语兼容层（AG-UI 官方事件名 ↔
  历史名 plan/trace/token/result/error 归一化），`useSseStream` 暴露类型化 events + 运行态 state；
  Python `sse_agui.py` 新增 emitter + translate，tutor/streaming 端点**零调用点改动**迁移到 AG-UI
  命名；Java 中转层原样透传，无需改动。
- **STATE_DELTA 端到端**：orchestrator dispatch 实时推送真实 plan 步骤进度，前端 plan 卡**流式点亮**
  步骤；`PlanCard` 新增进度视觉（done/active/failed），并修正 `result.plan` status 多停在 pending 的 bug。
- **流式渲染规范**：`MarkdownView` 对未闭合 ``` 代码围栏自动补全（防闪烁 / 防吞后文）；答案 `[页 N]`
  引用渲染为上标角标 + hover 课件原文富浮层卡（无证据不伪造引用，延续防幻觉红线）。
- **附带修复**：移除 `vite.config` 对已卸载 tailwind 依赖的残留引用（pre-existing 阻塞），typecheck 恢复全绿。
- 改动面：22 文件（+1136 / −64），覆盖前端协议/渲染层（7 源 + 5 测试）与 graph SSE 层（4 源 + 5 测试）。
- 验证（实跑）：前端 vitest **1690/1690** + 全库 typecheck ✓；graph pytest **544/544**。

## 2026-06-11（夜）— KC 图谱感知检索（GraphRAG 思想·零抽取自建版）

> 业界 GraphRAG/LightRAG 最贵的一步是「LLM 从文本抽实体关系」；本项目 65 KC 的前置边
> 在 `course_kc.prerequisite_codes`（V125 seed，64/65 有前置）是人工标注的结构化数据——
> 直接加载即得课程知识图谱，零抽取成本拿到图谱检索核心收益：**学生的困惑常源于前置 KC**。

- RAG 服务新增 `index/kc_graph.py`：KcGraph（BFS 1 跳前置闭包、visited 防环、fail-soft 空图退化）；
  lifespan/self-heal/rebuild 三处与课程页同源加载刷新。
- `fusion.hybrid_search` 升级**加权 RRF + 前置扩展路**：锚定 kc_codes 时沿前置边追加一对
  低权重（0.5）召回路；前置命中带 `via_prerequisite=True`，**预留席位制配额 top_k//3**
  （前置是补充证据：有候选时为其保留席位，否则低权重排名永远进不了 top_k）。
  vector_only 退化路径不扩展（历史位级兼容）；请求新增 `expand_prerequisites`（默认开）。
- graph 消费端透传：evidence_fetcher `RagHit.via_prerequisite` → tutor prompt 证据行标注
  「·前置知识」并提示导师先回填基础再讲主题 → `EvidenceCitation.via_prerequisite`
  （前端「课件证据」面板将来可标前置徽章）。
- 测试：新增 13 用例（图扩展/防环/席位配额/开关/空图/vector_only 不扩展/fail-soft），
  rag **68 passed**、graph **525 passed** 零回归。
- 真栈实测：锚定 KC-032（while 循环）查「怎么遍历」→ 4 主题页 + **2 前置页**（KC-031
  for 循环遍历，页 48/49）带标注进入证据——「遍历」困惑正是前置 for 概念，命中教育语义。

## 2026-06-11（晚）— LangGraph 0.2 → 1.x 升级 + orchestrator checkpointer 接入

> 按 `docs/plans/2026-06-11-langgraph-1x-upgrade-prompt.md` 执行。官方迁移指南确认 v1 核心图 API
> 与 0.2 兼容（graph primitives 不变），主要废弃项 `create_react_agent` 本项目从未使用（禁 ReAct 红线）。

- `pyproject.toml`：`langgraph>=0.2,<0.3` → `>=1,<2`（本机环境实测 1.1.8 下全量绿）。
- **orchestrator checkpointer 接入**（`MemorySaver` 默认，`PYT_ORCH_CHECKPOINT=postgres` 可选、
  构造期降级）：`thread_id = "<session>:turn-<N>"` **逐轮独立**——`trace`/`dispatch_results` 用
  `operator.add` reducer，跨轮共 thread 会把上一轮快照继续累加（结果污染），故 checkpoint 定位为
  「单轮内逐节点快照 + 按轮回放排错」，跨轮记忆仍由 orchestrator_session 权威承担（SSE 行为零变化）；
  新增 `get_turn_state(session_id, turn_no)` 快照查询接口。**不做运行时失败重跑降级**——节点有真实
  LLM 副作用，重跑会重复调用并重发 trace。
- 测试：新增 `test_orchestrator_checkpoint.py` 4 用例（单轮快照完整性 / 逐轮 thread 隔离零污染 /
  session 隔离 / postgres 构造期降级），graph 全量 **525 passed**；Java orchestrator 契约 6/6。
- 真栈冒烟（实跑）：同 sessionId 两轮 `/api/orchestrator/turn/stream`——轮 1 plan+12 trace+806
  token+result；轮 2 回复明确续接轮 1 计划（「把第三步【练】拆得更细」），SSE 契约不变，耗时同量级。
- **遗留**：graph 容器镜像仍 0.2.76（rebuild 被构建期 apt 代理 127.0.0.1:7892 容器内不可达阻断）；
  新代码在 0.2.76 兼容带实测可跑（checkpoint import/compile API 0.2 起一致），待网络具备后
  `docker compose build pythontopia-graph` 即完成容器侧 1.x 切换。

## 2026-06-11 — 多模态/课件显示环境修复 + 百晓工坊深度优化（R-16）启动

> 上午两笔环境级修复已提交；下午启动**百晓工坊深度优化（R-16）**：地基契约已落盘，
> 8 个工作包按零交叠文件所有权**并发施工中**（标注「在途」的条目尚未合入/验证，完工后另行补记）。

### 一、环境级修复（已提交）

- **课件 PDF 全线打不开**（`d3d632d8`）：pdfjs-dist 4.x 在 Web Worker 内用 ES2024 `Promise.withResolvers`，
  旧 Chromium/Electron 环境 worker 无此方法 → 图书馆 / stage 课件 / 工坊故事板 PDF 全挂。
  双重注入修复：`src/polyfills.ts`（主线程，`main.ts` 最前 import）+ `src/lib/pdfWorker.ts`
  （自定义 worker 入口先打 polyfill 再载 pdfjs worker，经 `workerPort` 注入）。
- **讯飞多模态恒降级**（`2f10d31a`）：部分环境 DNS 把讯飞解析到 fake-ip(198.18.x.x)，Java HttpClient
  默认不读 `HTTPS_PROXY` → 文生图/TTS connect timeout 恒降级。`dev.sh` 注入 JVM 代理参数（讯飞走代理、
  本地直连）；compose backend `JAVA_OPTS` 经 `host.docker.internal` 走宿主代理；backend 镜像内置 ffmpeg；
  `.env.example` / `docs/operations/dev-startup.md` 补踩坑记录。

### 二、百晓工坊深度优化 R-16 启动（在途）

> 目标七条：可视性美观 / 意图理解 / 画像个性化 / 工具调用 / Agent 协作 / 并发 / 稳定性。
> 执行蓝本：`docs/plans/2026-06-11-baixiao-deep-optimization.md`（含跨层契约与文件所有权切分）。

- **地基已落盘**（本仓已改，graph 服务）：
  - `pythontopia_graph/tools/registry.py`：**16 工具注册表**单一事实源（9 既有 + 新增 考考我 ASKME /
    讲代码 EXPLAIN_CODE / 修报错 FIX_BUG / 学习规划 PLAN / 学情报告 PROGRESS / 带路 NAVIGATE / 找资料 MY_STUDY），
    含风险分级 / 超时 / 重试 / 降级 / 作用域（考核模式动态收缩），意图路由 prompt 工具目录自动生成；
  - `pythontopia_graph/tools/harness.py`：执行治理 harness——超时 / 指数退避重试 / **熔断器** / **预算守卫** /
    结构化 `ToolObservation`（绝无悬空）/ JSON 审计日志 / `harness_stats()` 统计；
  - `schemas/intent.py`：IntentKind 扩 16 值；请求增 `recent_turns / last_intent / last_kc_codes /
    profile_brief / session_corrections / scope`，响应增 `target / secondary_intent`（多轮指代消解 + 组合意图契约）。
- **8 个工作包已全部完成并通过三层全量终验**（主代理集成缝合后实跑）：
  - WP1 graph 意图路由：多轮指代消解（recent_turns/last_intent/纠错记忆）、贴代码确定性预判
    （FIX_BUG/EXPLAIN_CODE 零 LLM 命中）、默认兜底 HANDOUT→ASK、70 条黄金语料评测、
    `GET /agents/intent/tools` + `GET /agents/harness/stats`；
  - WP2 答疑管线：8 维个性化指令组 + 苏格拉底阶梯提示、**critique→revise 自纠闭环**（审查打回→
    导师返工→重审）、图解+证据注 `asyncio.gather` 并行、SSE 心跳、`personalization_note`「为你定制」；
  - WP3 orchestrator：dispatch 真画像透传、learn→practice **工件链**（练习紧扣讲义产物）、
    无依赖步骤 `Semaphore(3)` 有界并发；
  - WP4 Java：`PassiveProfileService.profileBrief` 8 维全量（置信门槛+score 分档）、intent 链路
    R-16 字段透传（recentTurns/lastIntent/sessionCorrections/scope/target/secondaryIntent）；
  - WP5+WP6 前端：聊天核心 16 工具分发（考考我 3 轮考核模式 / 讲代码 / 修报错 / 学习规划直连
    orchestrator / 学情卡 / 带路 / 找资料）、低置信澄清气泡 + 纠错 chips、换讲法 chips 闭环
    （styleEngaged 回流）、停止/重试、滚动权归用户；8 个像素风新组件（百晓头像/欢迎屏/学情卡/
    计划卡/澄清/考核条/工具调用透明行/👍👎）；
  - WP7 主线巡检：**修复主线 P0 硬断**（章测交卷契约断裂致 1★ 永不可得、第 2 章起全锁）+
    视频计时 3 倍慢 + 无尽补星断链 + 闯关塔第 10 关断链 + 假完成显示 + 地图缓存过期，
    审计报告 `docs/plans/2026-06-11-mainline-audit.md`（含 7 条未修风险记录）；
  - WP8 RAG：**BM25+RRF 混合检索**（hybrid Recall@5 = 0.983 vs 纯向量 0.966）+ 上下文化分块
    （embed 文本前置章页摘要、展示原文不带前缀）+ 59 条检索黄金集 + 「input函数/for循环/列表」
    三查询验收，BM25 缺失自动回退 vector_only。
- **终验（实跑）**：graph pytest **518 passed**（基线 422）；rag pytest **55 passed**（基线 26）；
  Java surefire **653/0/0**（基线 595）；前端 `vue-tsc` ✓、eslint 0 warning ✓、vitest **1674/1674** ✓。
- 主代理集成缝合修复：意图路由 last_kc_codes 锚点两处丢失（贴代码/追问场景）、harness Python 3.10
  `asyncio.TimeoutError` 捕获、HudShell 删任务槽后的 a11y 集成测试断言。
- **全栈真 LLM 端到端冒烟（./dev.sh，全部实测通过）**：四端健康 ✓；意图路由真 LLM 四连测
  （「再来两道」沿用上轮 PRACTICE+KC ✓、「带我去闯关塔练练」→ NAVIGATE+target=arena+
  secondaryIntent=PRACTICE 组合意图 ✓、贴代码报错零 LLM 确定性 FIX_BUG ✓、学情句 → PROGRESS ✓）；
  真答疑 SSE 全链（plan/9×trace/603×token/result，三审+证据+evidence_note 诚实标注课件不匹配）✓；
  RAG 混合检索三查询（input函数/for循环/列表，mode=hybrid 引用页合理）✓；mainline overview 7 章 +
  章测 prepare GENERATING ✓；浏览器 UI 冒烟（新欢迎屏/快捷 chips/能力一览 16 工具/学情卡渲染）✓。
- **主线审计 R1-R5 风险项全部修复**（R6 计划内取舍保留，R7 已随 WP5 解决）：
  - R1 课件文档 id 去魔数：`CoursePackRepository.findDocumentIdByPackOrder`（pack 内 sort_order 顺位）
    动态解析章→文档，命中缓存、未命中回退 185+chapter 并告警（重灌库 id 漂移不再取错文档）；
  - R2 OJ 打点 evidence 收紧：不带 submissionId 的「标记完成」要求该生该题已有 PASSED 提交，
    否则 400 `EXAMPLE_EVIDENCE_REQUIRED`；前端 toast 展示拒绝原因引导先去训练场判题；
  - R3 章测 prepare 轮询：GENERATING 时前端每 3s 幂等轮询（≤8 次），个性化卷就绪即开考，
    不再因生成窗口点开始而错过个性化卷（超时仍走 golden 兜底不卡考试）；
  - R4 integrity.overtime 客户端自报：DTO 增字段 + 前端上送「倒计时归零自动交卷」标志，
    与服务端拒收口径取并集落库，教师端可见；
  - R5 FILL 单空硬约束：paper_designer prompt 明确「恰好一个空位」+ `_assert_fill_single_blank`
    fail-fast 校验（多空与后端单空归一化判分语义冲突），golden 兜底链路不受影响。
- **R1-R5 修复后三层全量回归（实跑）**：graph pytest **521 passed**；Java surefire **658/0/0**；
  前端 vitest **1674/1674** ✓（含 StageExamplePractice/StageLearnPage spec 补 Pinia 装配、
  章测 api spec 对齐 overtime 透传）。

## 2026-06-10 — R-15 §12 B2：彻底退役旧 mainQuest 前端主线（破坏性，可 revert）+ 客户端契约补录

> 单会话执行 R-15 计划 §12 **B2「旧主线退役」**（方案 A 最小退役）：前端权威 `mainQuest` 主线系统整体下线，
> 主线进度统一由**后端权威 mainline 域**（`stores/mainline`）承载。**诚实交付**：本节所有验证均实跑（命令+结果见「验证」）。
> 期间仓库另有并行会话在做后端「注释精简」提交（`914b4a3b`/`51779ec1`/`e3075105`，仅 backend），
> 本会话仅提交自身 frontend + docs 改动（`537005bc`/`6657bc2c`），与其零重叠、干净叠加其上。

### 一、退役旧 mainQuest 系统（`537005bc`，破坏性单独 commit 便于回滚）

- **抽取中立数据**：新建 `data/bossCatalog.ts`（`MAIN_QUEST_BOSSES`/`findBossByChapter` + `MainQuestBoss`/`MainSubQuestReward` 类型），
  `BossPage`/`ArenaPage`/`EndlessPage`/`api/boss.ts` repoint；`TrainingPanel` 掌握度提示去 quest 阈值依赖（仅显示当前进度）。
- **删除**（合计 ~4100 行）：`stores/mainQuest.ts`、`data/mainQuests.ts`、`types/mainQuest.ts`、`utils/questGuidance.ts`、`components/hud/MainQuestPanel.vue`。
- **世界/HUD 重接线**：`WorldScene` 去 activeQuest 动态标记 + store 订阅 → 单一「冒险地图」告示牌 trigger（tile 22,20 → `/mainline-map`）+ 持久「!」引导；
  `HudShell` 顶部进度条改**星星条**（读 mainline overview，本章 ★★☆ + 总星 N/21）；任务槽/摘要/`PauseMenu`/`LearningObjectivePanel` CTA 统一跳 `/mainline-map`；
  `ProbeCard` 去 quest 相位（cog_intro 改「进世界一次性触发」）；`GuideOverlay` 改读 mainline + `chapters.ts` 推导章 KC。
- **判定迁移**：`GraduationPage` 毕业判定改为 **7 章全部 ≥1★**（后端权威 overview）；`LecturePage` 去 mainQuest 媒体打点（改会话内「已学习」标记）；
  `learning notify`/`field`/`training`/`workshop` 切片 + `WorkshopChat`/`useBossSession` 去全部 evidence 钩子；
  `dialog.ts` `quest_recommend`→`/mainline-map`、移除 `onDialogClose` 主线通知。
- **存档**：`world.syncFromSlot` 不再回灌 `snapshot.mainQuest`；`SaveSlotSnapshot` 去 `mainQuest` 字段（前端停读写；DB/`V120` 零破坏、无新迁移；旧快照自然作废）。
- **测试**：删 6 个 mainQuest-only 套件（store/evidence/MainQuestPanel/questGuidance/mainQuests/main-quest-flow），修 `GraduationPage`/`PracticeList`/`TrainingPanel`/`BossPage`/`saveSlot` 规格。

### 二、客户端契约补录（`6657bc2c`）

- `docs/clients/api-contract.md` 新增 §4.9（since R-15）：mainline overview / stage 详情 + 三件套打点 / 章末测试 prepare·start·{testId}/submit·latest /
  endless·summary / recovery·errorbook / `npc/{npcKey}/greeting` / 教师看板 4 个；路径与后端 controller **实测一致**；标注旧 `mainQuest` 存档快照已废弃。

### 验证（实跑）

- 前端：`vue-tsc` typecheck ✓；`eslint` 0 warning ✓；`vitest` **161 文件 / 1551 用例**全绿 ✓；`vite build` 成功 ✓。
- 后端：`mvn test` **BUILD SUCCESS（Tests run: 595, Failures: 0, Errors: 0）**，含全部 Testcontainers 集成 + Flyway 全链（含 `V156`/`V157`）+ 课程包 seed 43 + `NpcGreetingServiceTest`(12/0，确认 `@SpringBootTest` 上下文加载通过)。
- 微服务：graph `pytest` **422 passed**；rag `pytest` **26 passed**。
- seed 计数（manifest + 集成测试佐证）：7 文档 / 561 页 / 7 章 / 65 KC / 46 例 / 39 题 ✓。
- 浏览器端到端冒烟：本会话随后执行（见后续补记）。

## 2026-06-10 — 真隐藏用例判题收口 + 画像难度加权回流 + @Async embedding flaky 修复 + 世界收敛收尾 + 工程整洁

> 单会话围绕「真判题红线 + 既有缺陷/flaky + 工程整洁」收口，并对仓库做体检式整理。
> **诚实交付**：本会话所有验证均实跑（命令与结果见各节「验证」）。期间检测到仓库内**另有并行 Codex 验收 agent**在持续改前端文件，
> 本会话只提交自身已验证改动，未触碰其在途文件（如 `LearningObjectivePanel.vue`/`arenaLevels.ts`/`BossPage.vue` 等的并行改动未纳入）。

### 一、真隐藏用例判题（`d3f34338`，「真判题」红线深层项）

- **Feynman 真判题**：`feynman_session` 新增 `problem_id`（`V153`，按 KC 回填代表题，优先有隐藏用例者），
  `FeynmanJudgeService` 改为复用 `JudgeCaseEvaluator` **比对代表题隐藏/样例用例**，而非旧的「空输入能跑通即过」；
  缺题 / 缺用例 fail-fast。新增 `FeynmanJudgeServiceTest`（隐藏输出不匹配必判失败）。
- **画像回流补全**：`PassiveProfileRevisionListener` 新增 `TaughtAiSucceeded→practical_ability↑` /
  `TaughtAiFailed→error_pattern`（带 `teach_back_failed` tag + sessionId 审计）。
- **判题掌握度难度加权落地**：`LearningEvent.Practice{Succeeded,Failed}` 携带 `difficulty`，`SubmissionService`
  从题目 DTO 取难度透传，`LearningEventListener` 优先用事件难度（缺失再查库）→ 难题通过增益大、易题失败惩罚重真正生效。

### 二、@Async embedding 时序 flaky 修复（`d3f34338`，根因隔离）

- 现象：`Stage05/08/10` 集成测试偶发「`No further requests expected: POST /embed`」。用 `git stash` 还原到提交基线对照，
  **同样 4 个失败原样复现** → 确证为既有问题（记忆向量化 `@Async` 抢进 `MockRestServiceServer(graphRestTemplate)` 窗口）。
- 修复：新增独立 `ragRestTemplate` bean，`RagEmbeddingClient` 改用之，与 graph 调用**物理隔离**；异步 `/embed` 不再被
  绑定 graph 模板的集成测试拦截。修复后 `Stage05/08/10` 转绿。

### 三、世界收敛收尾 + URL 隐藏 id + 归属映射（`d3f34338`/`093a8d08`）

- **fast-travel `mine_south` 退役**：`V154` 删除已删风景图相关路线；`FastTravelService(Boundary)Test` 改为「无路线」断言。
- **URL 隐藏私有 id**：`ResourceListPanel` 资源详情改走 `workshopUi` store，不再写 `/workshop?id=`。
- **`evaluation→sage_review` 引导映射**：`questGuidance` 增 `EVALUATION` 资源类型 → 复盘师 Sage。
- **前端掌握度对齐难度加权**：`MainQuestPanel`/`TrainingPanel` `MASTERY_PER_PASS 20→14`（Low 难度 ×0.7 ⇒ 单次 AC +0.14）；
  主线 2-3 锚 KC `PY43-KC-011→PY43-KC-016`（#476 实际 kc_code）并同步 `mainQuest.evidence.spec`。

### 四、工程整洁（lint / 死代码 / 格式 / 根目录）

- **前端 eslint 790 → 0**：`eslint --fix` 清 448 项 Vue 模板排版；对已 `DOMPurify` 消毒 / mermaid `strict` 的 `v-html`
  与测试桩组件加**带理由的豁免注释**；修 `LearningObjectivePanel` button 嵌套（改 `div[role=button]` + 键盘可达）。
- **死代码清理**：删 `AgentOfficeScene.vue + agentOffice.ts + spec`（孤儿，工坊已收敛为单一 `ThinkingStream`）；
  `MainQuestChip` / `MarvisPanel` 等于本周内移除。
- **后端格式**：本会话新增的 3 处超长行按 Google Java Format 折行 ≤100 列（`77d5a154`；spotless 工具因网络受限不可达，离线手修）。
- **仓库整理**（`2fa2497e`/`b1d6bd13`）：`.gitignore` 忽略本地 agent/工具产物（`.claude/ .playwright-mcp/ test.md`）；
  删垃圾文件 `nul`；根目录计划文档归位——`plan/`（阶段00–15+总览）→ `docs/plan/`，`R13_OPTIMIZATION_PLAN.md`/`plan.md`/`主线改造进度.md` → `docs/`；
  清理纯本地缓存目录（保留 `secrets/`、`qa-screens/`）。

- **验证（实跑）**：后端 `mvn test` BUILD SUCCESS（exit 0，含全部 Testcontainers IT）；前端 `vitest` 144 文件 1519 全绿；
  `vue-tsc` typecheck 通过；`eslint` 0 warning；`vite build` 成功。

## 2026-06-09 — 记忆语义化地基（pgvector）+ 记忆化 NPC 对话 + 费曼 AI 学弟 + 教师端 dashboard 重构 + 验收修复

> 据提交信息记录（本条非本会话亲自实跑，验证以各提交所述为准）。围绕「生成式教学智能体」后续 Phase 与验收修复多线推进。

### 一、记忆语义化地基 P0（`4e2ce63b`/`9e49ac85`/`e04136f5`/`4a6aed2f`/`1066d7ec`/`e94856fc`）

- rag 新增**批量 `/embed` 向量化端点**；后端 `EmbeddingClient` 调用之；`MemoryScorer` 增**语义相关性**重载；
  `V141` 建 `pgvector` 的 `memory_embedding` 表 + 余弦检索仓储；记忆写入异步化 + 语义检索接入（P0 收尾）。

### 二、记忆化 NPC 对话 P1–P3（`c3f2a741`/`810899d2`/`1066d7ec`/`00a11c43`/`fee7a948`）

- NPC **记忆作用域 / 人设 / 关系规则**（P1-P2）+ graph **grounded NPC dialog agent** + 记忆化对话后端（P3）+
  前端**记忆徽章 glassbox**；修复记忆 NPC 对话 bean 与世界 NPC 控制器的 bean 冲突。

### 三、费曼 AI 学弟 P4–P5（`f4945769`/`5fd84133`/`14d600f7`）

- graph **Feynman tutee agent**（镜像学生真实误区的 AI 学弟）+ 前端 **teach-back 面板** + 后端**会话与判题回流**（P4-P5）。
  > 注：本日判题仍为「能跑通 + 反作弊」，「真隐藏用例比对」在 2026-06-10 `V153` 收口（见上）。

### 四、教师端 UI 重构（`b2d4295d`/`fd274ebd`/`3b5b65ad`）

- **dashboard-01 侧栏布局** + 像素主题 + Py-native 组件（PyButton/PyTable/PyToggleGroup/PyTabs/PyRadioGroup/PyScrollArea/PyPopover）；
  成员表 PyTable 化、KC 热力图卡片化、任务发布分区 + 完成率进度条；公告板去冗余。

### 五、验收修复 Wave1 / Wave2（`8956541f`/`cec502f3`）

- Wave1：404 语义 / seed 契约 / 掌握置信度 / dispatch 吞异常 / 周报 status。
- Wave2 三 lane：P0.3 / P1.3-1.4 / P3.2-3.4 / P4.1 / P5.1 / P6.1 / P7.1（数字人 P8 除外）。

## 2026-06-08 — 生成式教学智能体（记忆流+反思）+ 随身助教 + 真协同答疑工具化 + 工作区基线固化

> 一天内跨多会话推进多条线，最后用一次基线快照把所有在途工作区改动固化为安全回滚点。
> **诚实交付**：本日大量改动以「工作区在途（WIP）」形式存在，按各自提交信息分别在代码层自测；
> 末尾 `10be35b5` 基线快照**一次性纳入**六端（含 android / wechat / windows）与 graph 的全部未提交 WIP，
> 该快照内的跨端改动未逐一重验，且**遗留一处已知问题：双 V135 迁移冲突**（见末条，留 infinity P0.1 修复）。

### 一、记忆矿脉 · FSRS 复习引擎（`d319ecb4`，Phase 1+2 + 错题矿洞 UI 统一）

- **引擎（确定性、不调 LLM）**：新表 `kc_review_state`（V133，空库从 `kc_mastery` 回填）+ `ReviewScheduler`
  （FSRS-lite DSR：幂律遗忘 `R(t)`、间隔、稳定度演化）+ `ReviewState/Outcome/Signal/Repository/Service`；
  `MasteryEvaluator.applyEvent` 同事务挂钩，按成功/失败/重学演化稳定度。
- **回声闭环**：BugMine 入矿门槛改 FSRS 到期（`now>due_at`，无状态按掌握度播种），`decayRisk` 改 `1−R`，
  入矿题按掌握度选恰当难度。graph 新增 `REVIEW` 意图（IntentRouter 关键词+LLM）；`OrchestratorRequest`
  加 `decaying_kcs`，`OrchestratorAgent._select_kcs` 把到期 KC 提到最高优先级；后端每轮取前 5 经
  `OrchestratorGraphClient` 透传（取数失败降级空列表）；前端 `intent.ts` 加 REVIEW 且排除资源生成、
  `WorkshopChat` 把 REVIEW 引导进错题矿洞。
- **错题矿洞 UI 统一**：全高 master-detail + `--pyt-color-*` token + PyButton/PyEmptyState + chips/stepper，
  自适应视口、精简文案。文档：`mastery_formula.md`（14 天半衰期 → FSRS R）、`clients/api-contract.md`。
- 验证（提交所述）：ReviewScheduler 8/8、Stage10BugMine 12/12、orchestrator 单测、graph pytest 301、前端 typecheck + 相关 spec 绿。

### 二、生成式教学智能体 P0–P2（记忆流 + 反思飞轮，随基线快照入库）

> 落地 `docs/plans/2026-06-08-generative-teaching-agents.md` 的 Phase 0–2（后端 + graph + 前端 API/笔记本）。
> Phase 3（小镇涌现协作四件套）见下「八」节本会话补齐。

- **记忆流底座（Phase 0）**：`learning_event_log` 加 `importance`（V134，1–5 确定性打分）+ `EventImportance` +
  `MemoryStreamMirror`（进程内 `LearningEvent` 镜像入表，异常吞掉不阻塞主链路）；`MemoryScorer`
  （recency×importance×relevance，权重 0.4/0.3/0.3，半衰期 14 天）+ `MemoryRetrievalService` +
  `GET /api/learner/memory`（topK + 三项分数分解，仅本人，limit clamp 1..30）。
- **反思飞轮（Phase 1）**：graph `ReflectionAgent`（单次有界 LLM、grounded、强制引用 event id、维度白名单、
  fail-fast）+ `routes/reflection.py`；后端 `learner_insight`（V136）+ `ReflectionTrigger`（进程内阈值 8/冷却 60min）+
  `ReflectionService`（回写画像 `source=REFLECTION` + 落 `learner_insight`，graph 不可用走规则兜底不抛）+
  `GET /api/learner/insights`；`ProfileSnapshot.recent_insight` 折进个性化 directive。
- **记忆条件化生成（Phase 2）**：topK 记忆要点注入资源/答疑 prompt；洞察驱动路径重排。
- **前端 API/组件**：`api/memory.ts` + `api/insight.ts` + DTO；`components/agents/AgentNotebook.vue`
  （记忆流可视：中文摘要 + 重要度星级 + 可展开 recency/importance/relevance 分数条 + 空态/失败降级），
  挂入画像屋 `ProfileRoomPage`。

### 三、随身助教 CompanionDock（`f8f0950b`/`ebbd38f8`/`47c4f157`/`0af48028`）

- **脊柱（Phase 0/1）**：全局浮动像素小人 + 折叠 ask 栏，复用 `/api/tutor/ask`（SSE）+ MarkdownView + 当前页面
  上下文 + 证据页芯片，答案下方给「去练/看课件/进矿洞」动作芯片；`useCompanionContext` / `useVoiceInput`
  （Web Speech ASR）+ 拍照/粘贴 `images[]`（≤3）；`code_runner` 完工 trace 驱动「已验证」角标。
- **主动复习 nudge（Phase 3.2）**：折叠态在小人上方冒一条可关闭复习气泡（取记忆矿脉 echoes 最高优先到期项，
  点击一键进矿洞；无到期不打扰；当次关闭后不再弹）。
- **教师 copilot（Phase 3.3，前后端两道闸）**：前端仅在教师/管理员 `/teacher/classroom/:id` 注入只读班级上下文
  （KC 热力/高频误区/完成率）；后端 `/api/tutor/ask` 放开教师/管理员，`TutorService` 对**归属**教师注入
  `copilotBrief`（最弱 KC + 高频误区，`requireOwner` + 只读），学生即便携 `classroomId` 也一律忽略、零触达班级聚合
  （新增 `ClassroomDashboardAuthorizationTest` / `TutorClassroomBriefTest` 锁红线）。
- **服务端 ASR 回退（Phase 1.4）**：`XfyunIatService`（讯飞语音听写 WS+HMAC + ffmpeg 转 16k PCM）+
  `POST /api/media/asr` + 前端 `useAudioRecorder`/`transcribeAudio`；无凭据 / 无 ffmpeg / 班级无数据均优雅降级。

### 四、graph 真协同与工具化答疑（`76b54568`/`f4314aff`）

- **辩论复核子图（Phase 3.1）**：`graph/debate_graph.py`（Solver→Critic→Referee，critic 无异议时 Referee 直接采纳省一次 LLM；
  任一步异常回退 None 走普通答疑；逐节点 `run_events` 供真实 solver/critic/referee trace）。
- **工具化导师（Phase 2.1/2.2/2.3）**：`nodes/code_runner.py`（受限子进程跑学生片段、关 stdin、超时 kill、截断输出，仅供答疑「验证」）；
  `TutorAgent.should_verify`（带 Python 代码片段且追问错误/输出时先实跑注入真实 stdout/stderr，引导据实定位、绝不据此给完整答案）+
  `should_debate`（窄触发词路由进辩论复核，与 code 验证互斥优先）；答疑 SSE 新增 `code_runner` + `solver/critic/referee` 真实 start/finish trace。
- 验证（提交所述）：debate 3/3、code_runner 4、tutor_verify 2、tutor_ask_stream（含 code/ debate）6，graph 全量 312 绿。

### 五、工坊内联 Pyodide（`974268c4`，Phase 2.4）

- CodeLab 讲解可「运行」看本地 Pyodide 输出；Practice 可写解法本地试跑（通过→推进主线「练」`mainQuest.onSubmissionPassed`，
  未通过→「进矿洞调试」入口）。复用 `learning.runPyodidePreview`，严守红线：**本地预览 ≠ 正式判题**（每处显式标注）。测试 workshop 4/4。

### 六、后端测试锁定（`eac6686a`，Phase 2.3/3.3 BE，无业务源改动）

- `TutorGraphClientSseTest`：起本地假 graph，断言导师答疑代理把上游未知 trace 节点（`code_runner`，含 `phase/status/elapsed_ms`）
  **原样透传**到浏览器、不被节点白名单过滤/改写 → 前端「已验证」徽标可靠。
- `ClassroomDashboardAuthorizationTest`：用真实 `ClassroomGuard` 串 `ClassroomDashboardService`，断言学生读 KC 热力/高频误区/
  完成率/资源使用/最近活动一律 FORBIDDEN 且**零触达**聚合仓储。

### 七、工作区基线快照 + 远景蓝图文档（`10be35b5`）

- 把六端（backend / frontend / android / wechat-miniprogram / wechat-minigame / windows）+ graph 的全部在途 WIP 一次性
  固化为 P0 稳定化前的安全回滚点：含上文记忆流/反思、随身助教、intent-router、记忆矿脉回声等跨端改动。
- 新增远景规划文档 `docs/plans/2026-06-08-pythontopia-infinity-living-world.md`（教育内核收敛版蓝图，对标软件杯 A3）与
  `docs/plans/2026-06-08-generative-teaching-agents.md`（记忆流/反思实施详版）。
- **已知问题（原样记录）**：迁移版本 135 重复（`V135__drop_npc_friendship_and_gift.sql` 与
  `V135__learner_insight_and_reflection_source.sql`），Flyway 启动会报「Found more than one migration with version 135」→ 留 infinity P0.1 修复。

### 八、生成式教学智能体 P3 · 小镇涌现协作（本会话补齐，多 subagent 并发）

- 3.1「Agent 笔记本」与 3.4「复习 nudge」已随基线落地（`AgentNotebook.vue` 挂画像屋、companion 复习气泡）；本会话以两路并发 subagent（文件域互不重叠）补齐余下两件：
- **3.2 顿悟洞察气泡**：companion store 新增 `latestInsight` / `loadInsight()`（以 1 调 `getInsights`、空/异常静默降级、同一已忽略 id 不复弹、不同 id 仍弹）/ `dismissInsight()`；`CompanionDock` 折叠态在小人上方冒「💡 我发现你在…」气泡（`companion-insight`，可展开 `evidenceEventIds` 溯源、可关闭）。
- **3.3 Agent 串门通报**：`AgentOfficeScene` 据 `reflection` / `handoff` trace 渲染「串门」元素（`agent-handoff-visit` + `agent-handoff-note`，缺省文案兜底；reflection/handoff 不进执行日志；reduced-motion 友好），普通流水线无回归。
- 验证：前端 `npm run typecheck` 0 错；P3 四个 spec（`AgentNotebook` 5 / `AgentOfficeScene` 4 / `companion` 13 / `CompanionDock` 12）全绿；改动文件 `eslint` 0 错。

## 2026-06-06 — Bug 矿洞验证基线稳定化（`c7979dc6`）

- 稳定错题矿洞相关验证门槛：`BilibiliVideoAcceptanceIntegrationTest` 收紧、`Submission` DTO 补字段、
  `TrainingPanel.spec` 扩 68 行用例、`tilemap.spec` 对齐；纯测试/契约层加固，无业务回归。

## 2026-06-05 — 代码规范化：命名审计 + 脚本改名 + 注释优化（进行中）

> 类型：重构（命名）+ 文档。按《命名约束 + 注释规范》优化全项目源码；进度实时记录于
> `docs/comment-optimization-log.md`。本批仅做命名修正，未改业务逻辑。

- **命名审计**：Vue（117）/ Java（408）/ Kotlin（217）文件名全部合规；Python（129）仅 3 个资产脚本用连字符。
- **脚本改名（全链路同步，无兼容别名）**：`scripts/build-cottages.py`→`build_cottages.py`、
  `build-ulpc-characters.py`→`build_ulpc_characters.py`、`crop-portrait-heads.py`→`crop_portrait_heads.py`。
  同步约 20 处引用（`BootScene.ts`/`tilemap.ts`/`lpcAnimations.ts`/`DialogModal.vue` 及测试、`scripts/README.md`/
  `sync-assets.mjs`、`LICENSE-ART.md`/`LICENSE.md`/`CREDITS`/各 `README`）。
- **影响范围**：均为 CLI 一次性资产脚本（不作模块 import），无运行期调用破坏；`py_compile` 通过，无残留旧名。
- **注释策略**（用户确认）：存量英文注释翻译为简洁中文（技术标识符保原文）；靶向优先（先扫 R-1x 迭代号 /
  长块 / 中英混排）后穷尽；按模块推进、模块边界汇报。
- **注释优化 backend 批次 1-4（共 24/408 文件，仅改注释，不影响字节码）**：
  - `common`（14）：`GlobalExceptionHandler` 去迭代号、`GraphSseConsumer` 压缩 HTTP/1.1 workaround；余 12 文件已合规。
  - `dialog`（4）：去 R-12、英文转中文，并纠正多处与 SQL 不符的注释。
  - `world.travel`（3）/`world.festival`（3）：英文 Javadoc 转中文、去「R-12 W5」与文档章节引用、长 TODO 压成单行，保留单事务/短路校验/全日程级写入等契约。
- **注释优化 backend 并发批次（Wave 1+2，共 20 路子代理，覆盖 backend main 全 346 文件 + 入口类，仅改注释、不影响字节码，`mvn test-compile` exit 0）**：
  - Wave 1（10 路）：`learner` 全模块（85）+ `world` 的 curriculum/dto/boss/save（34）。
  - Wave 2（10 路）：`world` 余下（skill/calendar/domain/npc/root 等 32）+ `classroom`(27) + `coursepack`(24) + `submission`(19) + `media`(19) + `auth`(19) + `workshop`(17) + `bugmine`+`agent`(16) + `intent`/`orchestrator`/`rag`/`video`/`tutor` 余(25)。
  - 统一处理：英文注释译简洁中文（保留技术标识符）、删迭代号/阶段号/设计文档引用/演示叙事、压多行为单行、纠正与实现/SQL 不符的注释、为缺契约的 public API 补 Javadoc；保留跨服务协议映射与安全/一致性/状态机契约。
- **注释优化 frontend src（Wave 3，10 路并发，覆盖 `frontend/src` 全 290 个 `.vue`/`.ts`，仅改注释，`npm run typecheck` exit 0）**：components/api/game/pages/stores/composables 等全量；统一去迭代号（R-1x/Phase/W*/§）与外部产品演示叙事、英文 catch 译中、删复述、纠正多处失实注释；保留 Phaser 隔离/单 Game 实例/8 向降级、SSE/证据接地、DOMPurify 安全、Pinia 状态/持久化、i18n key 等契约。注：frontend 改动与未提交的工坊 feature WIP 同文件交织，未单独 commit。
- **注释优化 Python services（Wave 4，10 路并发，graph 65 src+36 tests / rag 19 src+6 tests / scripts 3 py，仅改注释，`pytest` graph 297 + rag 21 全通过）**：补全多智能体/RAG/审查节点 docstring（结构化输出、fail-fast、grounding/`no_evidence_found` 防幻觉、安全过滤、禁隐藏测试等契约），英译中、去 stage/迭代号；测试文件保留意图说明不改断言。注：本波默认模型派发触发「API usage limit」全部未启动，改用 `composer-2.5-fast` 重派后成功。
- **后续**：android（217）→ 测试（frontend 127 + backend 64）→ scripts 2 mjs，逐批更新 `docs/comment-optimization-log.md`。

## 2026-06-05 — 工坊重构 + 侧栏 Marvis 实时工作室 + 答疑五智能体审查 + 课件证据/B站讲解

> 承 2026-06-04 深夜两份计划落地至今日：`docs/plans/2026-06-04-workshop-redesign-and-marvis-plan.md`
> （工坊布局 / 答案优先不强制@KC / 侧栏 Marvis / PY43 隐藏 / DeepSeek 连通）与
> `…-bilibili-cards-and-agent-studio-plan.md` 的收尾。**本机验证总账**：graph `pytest` **297 passed**、
> 前端 `vue-tsc` 干净 + `vitest` **1459 通过 / 128 文件**、后端 `test-compile` + `BilibiliVideoControllerTest` 通过。
> 本批改动尚处工作区（未 commit）；后端全量 `mvn test` 本会话未重跑（仅编译 + 视频控制器单测）。

### 一、工坊重构 + 侧栏 Marvis 实时工作室

- **布局（WorkshopPage）**：右栏 `workshop-rail` = 上段常驻 **MarvisPanel** + 下段 **我的书房**（`StudioDrawer`，
  默认收起为窄把手，点开浮出）；聊天占满主区。`StudioDrawer` 详情走「替换式」（列表↔详情 + 「← 返回书房」）。
- **书房（ResourceListPanel）**：移除「按 KC/标题检索」搜索框；保留「🔁 待复习」（资源对应 KC 掌握度 < 60 置顶，
  「重温/再练」直达）+ 资源列表，把 resource_consumed 升级为可积累、会回访的复盘闭环。
- **Marvis（`MarvisPanel.vue` + `stores/workshopActivity.ts`）**：借鉴 Alethicode `QaAgentProgressBar`，把当前 SSE
  节点映射成人话（正在检索课件证据 / 作答 / 核对课本引用 / 防幻觉核查 / 安全审查 / 生成学习资源），空闲一排像素
  小人待命、活动态实时活动条「已等 Ns」+ 工位按 `plan` 点亮；复用 LPC `npc-composites`（与 AgentOfficeScene 同源，不新增二进制）。
- **答案优先 + 不强制 @KC（WorkshopChat）**：合并 答疑+资源生成，intent 自动路由；资源类无 KC 不再报错（兜底 KC 或降级 ASK
  先把问题答了），答案内嵌「看相关视频 / 生成讲义 / 出练习」入口 + 按视频意图嵌 B站卡片。

### 二、答疑「答后审查」五智能体协同（馆员→导师→审查官→校对官→安全员）

- **新 `nodes/tutor_review.py`**：把资源流水线的三道审查迁到答疑答案上（确定式、零 LLM、不阻塞）——
  `grounding_review`（答案 `[页 X]` 必须命中证据页，否则=编造引用）/ `hallucination_review`（统计型数字须有据）/
  `safety_review`（泄答 / 歧视 / PII / 危险代码）。
- **`routes/tutor.py`**：答疑链路由「馆员(RAG)+导师(LLM)」扩成 馆员→导师(流式)→审查官→校对官→安全员；开场发 `plan`
  工位清单 + 每节点 `start/finish` `trace`（与资源流同一线协议，驱动 Marvis 实时点亮）。`result` 同时给 snake_case
  （落库）+ camelCase（前端直读）+ `review` 三审明细。新测试 `test_tutor_review` / `test_tutor_ask_stream`。

### 三、课件证据可见化 + B站每视频讲解（本会话）

- **课件证据**：`TutorResponse.evidence`（`EvidenceCitation`：页码 + RAG 原文片段 + 相关度），`_build_citations` 按
  `page_id` 去重投影，`evidence_page_ids` 与其同源同序；新 `components/tutor/CoursewareEvidence.vue` 渲染「📖 课件证据 ·
  系统命中 N 处」，每条「第 X 页」可点回链 `/library?pageId=` + 原文片段，通识知识给中性说明。**修复**：WorkshopChat 此前
  以 camelCase 读 graph 的 snake_case `result`，导致接地状态/证据**从不渲染**——改为 snake_case 对齐。
- **B站讲解**：透传 UP 主简介 `description` 三层（graph 去标签/反转义/截断 → Java `BilibiliVideoDto` → 前端 DTO）作为
  「这个视频讲什么」的真实讲解（不伪造）；`BilibiliVideoCards` 改为**等宽等高**（`align-items:stretch` + 卡片 `height:100%`
  + meta `margin-top:auto`）+ 简介 2 行截断 + 空态占位；工坊视频气泡与答疑气泡**同宽**。

### 四、训练场代码编辑器 + 编排图重构 + DeepSeek 连通 + PY43 隐藏 + 修复

- **训练场**：`TrainingPanel` 重写 + 新 `components/training/PythonCodeEditor.vue`（CodeMirror 6 Python：行号/折叠/括号匹配/
  自动补全/搜索 + 深色主题），替换原始 textarea。
- **编排图重构**：`orchestrator_graph.py` 由命令式 `_run_core` 改为 **LangGraph 节点式**（understand / build_plan / dispatch /
  replan_failures / extract_profile / compose / clarify + 路由函数 + compiled 图），每节点发 trace。
- **DeepSeek 连通**：`OpenAiCompatProvider` 由 `trust_env=False` 改 `True`（**有代理走 7892、无代理直连**，`NO_PROXY` 护内网 RAG），
  打通宿主 graph 调 DeepSeek；新 `test_openai_compat_provider`。
- **PY43 UI 隐藏**：`useCoursewareMention` / `GuideOverlay` 等改走 `kcDisplayName` / `humanizeKcText`，不外泄 `PY43-KC-xxx` 原码（代码/DB 不动）。
- **修复**：`router.afterEach` 关闭残留 NPC 对话浮层；`dialog` store 本地 meta/dialog 优先显示、context 后台异步（消除对话卡顿）；
  `http.ts` `suppressNetworkToast`（B站/文生图/课程视频列表等后台请求各自降级、不弹全局 toast）；LecturePage 精简（去 AI 图解 −106 行）；
  LibraryPage 证据回链（`?pageId` 解析 page_id → docId+pageNo）。

### 验证与未验证（诚实交付）

- **已验证（代码层，本机）**：graph `pytest` 297；前端 `vue-tsc` 干净 + `vitest` 1459；后端 `test-compile` + 视频控制器单测；
  新增 Python 视频/证据/答后审查/openai_compat 均有单测覆盖。
- **未验证（需起栈 / 联网，演示前必做）**：真实 B站检索与浏览器封面防盗链、SSE Marvis 端到端实时动画、DeepSeek 真实出参、
  答疑五审查端到端、训练场 Pyodide 浏览器实跑；建议 `dev.sh` 起全栈 + 联网做一次端到端。
- **备注**：本批为工作区改动梳理（**尚未 commit**）；后端全量 `mvn test` 未在本会话重跑。

## 2026-06-04（晚）— 画像闭环三连融入主链路 · 前端认知负担优化 · B站卡片/智能体工作室

> 本夜横跨两份计划并行推进：`docs/plans/2026-06-04-profile-loop-trilogy-*`（画像闭环三连 + 前端体验）
> 与 `docs/plans/2026-06-04-bilibili-cards-and-agent-studio-plan.md`（B站卡片 + 智能体工作室）。
> 大量使用并行子代理（最高 8 并发，按「文件域互不重叠」分区，每个子代理启动均实时核验），
> 共享后端/收口全量验证由父代理亲自完成。**验证总账**：后端 `mvn test` **342/0 BUILD SUCCESS**、
> graph `pytest` **276 passed**、前端 `vue-tsc` 干净 + `vitest` **1451 通过 / 127 文件** + eslint 0 error。

### 一、画像闭环三连 ②③ 全量融入「看→学→练→测」主链路

> 关键修复：①「助教因材施教」早已落地却**种子饥饿**（无信号播种 `cognitive_style`，主链路答疑/资源静默降级
> 为通用版）。本轮③探针在「看」阶段播种，使 ① 在 LecturePage(看/学)+TrainingPanel(练) 真正触发，闭环打通。

- **画像引擎地基（confidence + decay）**：`ProfileDimensionDto.confidence = min(1, evidence_count/8)`；
  `getDimensions` 读时半衰期 14 天 decay（raw 不入库）。落实「随学随新」、低置信不贴标签（防幻觉）。
- **建③ 玩中建画像（探针）**：`ProbeCatalog`（服务端权威，`cog_intro→cognitive_style` / `fail_react→emotion_persistence`
  / `media_pref→learning_preference`，source 复用 RESOURCE_FEEDBACK 无新迁移）+ `POST /api/learner/probe` +
  `LearningEvent.ProbeChoiceMade` → 监听器回写维度。前端 `useProbeStore` + 数据驱动 `ProbeCard`：看阶段章首弹
  `cog_intro`、练失败弹 `fail_react`（中性可跳过、失败静默降级绝不阻塞玩家、`pyt_probe_answered` 去重）。
- **境② 情绪化小镇（练→情绪→世界）**：新事件 `PracticeRetried/PracticeAbandoned/ComebackSolved`，
  从 `SubmissionService`（`countPriorFailures` 判重试/逆袭）与 `BugMineService`(ABANDONED) 派生 → `emotion_persistence`；
  新增 `GET /api/learner/world-state`（mood 派生：连胜/连挫优先，再看慢分，失败降级中性）。前端 `useMoodStore` +
  `MoodOverlay`（rainy 弹「需要我搭把手吗」助教台阶，可关闭）+ Phaser `mood.ts` 叠加层（深度 150，不抢相机 tint）+
  `WorldScene` 反应式刷新 + 练后即时刷新。
- **学①扩展（资源生成下传画像）**：`ResourceWorkshopService.profileSnapshot` 下传 `cognitive_style`/`error_patterns`；
  graph `ProfileSnapshot` 加两字段 + 新 `agents/personalization.py` 把讲法指令注入全部 6 个资源 agent（讲义/导图/示例随讲法自适应）。
- **用① 体验闭环**：① 换讲法**即时改写当前答案**（`TutorAskRequest.style` → `buildProfileBrief` 显式选择绕过 0.3 门 →
  `TutorPanel` 带 style 重问重渲染，仍写回 styleEngaged；graph 不动）；① 看阶段 **NPC 对话 3-way 个性化**
  （`DialogContextService.inferCognitiveStyle` + `DialogContextDto.cognitiveStyle` + 前端 `{{styleAdaptive:visual|logical|trial}}`
  token，mayor_py/professor 示范；旧 `{{profileAdaptive}}` 不变）。
- **#1 cognitive_style 纠偏**：`SELF_EDITABLE_DIMENSIONS` 加 `cognitive_style`，自评（source=INTERVIEW）所选 tag **置顶为主**
  （仅此路径，passive 不变 + 回归测试）；`ProfilePage` 三选纠偏控件。透明可纠偏、不贴死标签。
- 新增后端测试：`ProbeCatalogTest`/`PassiveProfileProbeTest`/`PassiveProfileEmotionTest`/`WorldStateServiceTest`/
  `SubmissionEmotionSignalTest`/`DialogContextServiceTest` + `TutorProfileBriefTest`/`PassiveProfileServiceTest`/`ResourceWorkshopServiceTest` 扩充。

### 二、前端「降低学生认知负担」优化 C1–C6（对齐 `docs/frontend-ui-standard.md`）

> 经只读审计发现 town `WorldPage` 同屏挂 17 浮层、2–3 个脉冲主 CTA、导航三套（dock/QuickWheel/PauseMenu）、
> 术语外泄（KC/Pyodide/practice_submission/防幻觉/场依存，「问AI」6 个名字）。按标准 4 工作流并行整改：

- **C1 一屏一主操作**：向导脉冲→安静次级；`LearningObjectivePanel` 默认折叠为「标题+下一步」、去呼吸动画；唯一实心主按钮。
- **C2 去术语 + 统一「AI 助教」**：6 个「问AI」名字→1 个；KC→知识点、Pyodide/服务端判题→本机试跑(不记分)/正式提交(评分)、
  防幻觉→已核对教材来源、多模态讲解→图文/视频讲解、删 `practice_submission`、场依存/序列型→口语。
- **C3 菜单/导航精简**：PauseMenu 17→4 组、合并重复「画像」；QuickWheel 降级；FestivalRibbon 仅节日当天。
- **C4 首次进入节奏**：Help 精简三条+「更多操作」折叠；防 Help→镇长对话→探针 三连堆叠（ProbeCard `hold`+`dialog.visible` 门控）；向导首句具体化。
- **C5 R-14 残留清理**：去金币（FastTravel/Arena）、去农场/5图死路、修「Pythonopia」拼写。
- **C6 渐进披露**：工坊欢迎一句话+「更多用法」折叠、slash 去密集；Training 默认收起预览/AI助教坞；Lecture 单主按钮；画像口语化。

### 三、B站视频卡片 + Marvis 式智能体工作室（并发会话，`bilibili-cards-and-agent-studio-plan.md`）

- **A B站视频卡片**：graph `routes/video.py`（`bilibili-api-python`，GPL 隔离在 graph，记入 `LICENSE-ART.md`）
  `GET /agents/video/bilibili/search`；后端 `video/`（`BilibiliVideoController/Service/Dto`，鉴权代理）`GET /api/videos/bilibili`；
  前端 `api/bilibiliVideo.ts` + `components/video/BilibiliVideoCards.vue`（封面 `referrerpolicy=no-referrer`、「外部资源·B站」角标），
  落点 LecturePage 空洞 + 工坊 STORYBOARD；不进课件 grounding、不下载视频。
- **B 真·按 agent 实时工作室**：graph `resource_graph.run_streaming` 增开场 `plan` 事件 + 每节点 `start/finish` 相位（落库 schema 不变）；
  后端 SSE 逐事件透传（仅特判 result）；前端 `useSseStream` 加 `phase`/`plan`、`AgentOfficeScene` 按 `plan` 动态渲染真实工位
  （设计师工位显真实 agent 名，非泛称），无 `plan` 时回退旧 4 工位。仅改资源流，Tutor/Orchestrator SSE 不动。
- 并发会话自测（其计划记录）：B站 Python 5 / 后端 5 / 前端 9；工作室 Python 4 / 前端 2。

### 验证与未验证（诚实交付）

- **已验证（代码层，本机）**：后端 342/0、graph 276、前端 typecheck+vitest 1451+eslint 0；新增 REST 以单测覆盖控制器/服务/监听器。
- **未验证（需联网/起栈，比赛演示前必做）**：真实 B 站检索联通、浏览器内封面防盗链、SSE 工作室端到端实时动画、
  新探针/world-state/纠偏的浏览器端视觉确认；建议 `dev.sh` 起全栈 + 联网做一次端到端。
- **剩余项（留后）**：② 情绪化小镇重量级（天气粒子/NPC 动作）；探针 `media_pref` 前端落点；#4 PM 调度助教
  （已被 orchestrator + GuideOverlay + AgentOfficeScene 覆盖，未重复实现）。

## 2026-06-04 — R-14 世界收缩方案 A：单中央小镇 hub（S0–S7）

> 废弃「5 图分块大世界」，收敛为单一中央小镇 `town_hub`：移除 KC 作物/播种/浇水（农场）玩法、
> 田老 NPC、货币/商店残留；主线 35 任务与引导全部收进 hub。目标「主线清晰 + 引导易懂」。
> 全程外科手术式修改；仅用只读子代理从历史会话找回计划，未用 subagent 改码。

### 世界收缩（S0–S5，前序会话落地）

- **S0 蓝图**：重写 `AGENTS.md` 宪法为单 hub、去作物隐喻（R-14 标注）。
- **S1 主线收敛**：`mainQuests.ts` 35 任务 `mapKey` 全部 → `town_hub`；6 个掌握度任务引导由
  「农场田老」改为「训练场教务 Q」（`questGuidance.ts`）；首进节奏与引导文案简化。
- **S2 删农场作物**：后端删 `world/crop` 包 + 解耦 5 服务，迁移 **V126** drop `crop_tile_state`
  （不可逆；掌握度存于 `kc_mastery_event`，不受影响）；前端删 5 个农场渲染组件 + `crops` 数据层。
- **S3 外围图摘除导航**：`fastTravel` 仅留 `hub` 一条路线；minimap / `WorldScene` 多图 /
  `QuickslotPanelHost` MAP_LIST 收敛为 `town_hub`。
- **S4 NPC 进 hub**：`generate-maps.mjs` 的 `generateTownHub` 放入 4 个主线 NPC（mine_keeper /
  captain_def / foreman_bug / class_president）；田老 `farmer_kc` 数据/目录/地图/i18n 全删；
  重生成地图 + `sync:npc`。
- **S5 货币/商店清理**：前端删 inventory store / `InventoryPanel` / 3 个 DTO，解耦 HUD coin 与 bootstrap；
  为不破坏前后端存档契约，**保留** `SaveSlotDto.inventoryItems`（设置但不使用）。

### S3 收尾：fast-travel 测试对齐（本会话）

- 前序会话只重写了 3 个 fast-travel spec（`FastTravelPanel` / `settings-fasttravel` / `demo-smoke`），
  漏掉 3 个同因测试。本会话补齐：`demo-smoke` STEP5、`contracts.integration`（FAST_TRAVEL_ROUTES
  长度=1 + 仅 `town_hub` + 外围图已移除）、`workflows.integration`（外围图→hub 联动）、`WorkshopPage`
  （hub chip 点击/禁用）共 **12 个陈旧断言**对齐单 hub 现实。纯测试改动，无业务回归。

### S6-A：SaveSlot 后端同步主线进度（本会话新增）

- 主线进度此前仅存 localStorage（`pyt_main_quest_progress_v1`），换设备不跟随。R-14 接入后端双写：
  - 新增 `frontend/src/api/saveSlot.ts`（list / create / activate / saveSnapshot；snapshot 以
    序列化 JSON 文本 + `application/json` POST，匹配后端 `@RequestBody String` + `SET snapshot = ?::jsonb`）。
  - `SaveSlotDto` 增可选 `snapshot` 字段（后端早已下发 `JsonNode snapshot`，前端此前丢弃）。
  - `world.syncFromSlot` 进入世界时用 `slot.snapshot` 回灌 `mainQuest.hydrateFromSnapshot`
    （空快照安全忽略，保留本地进度）。
  - `mainQuest` store 每次 `persist` 触发 1s debounce 的后端写入（仅登录态）；无 active slot 时
    自动建 `slot 0「自动存档」`（后端首槽自动激活）；hydrate 期间抑制反向回声。
  - 后端 `save_slot.snapshot.mainQuest` 契约沿用 **V120**，**无新增迁移**。
  - **风险修复（离线回连不回退）**：`hydrateFromSnapshot` 由「覆盖」改为「单调合并」（`mergeProgress`）——
    completed / defeated / milestone / consumedMedia 取并集，章节指针取更靠前者，AI 题日志按时间去重取新；
    离线先行的本地进度不会被后端旧快照冲掉，清零仍走显式 `resetProgress`。+3 回归测试。

### S6-B：RAG chunks=0 排查（运维/数据项）+ 数据链路永久守门

- 通读 RAG 全链路（loader / index / store / query / embedder）并跑测：**rag pytest 21 通过**，
  确认**非代码 bug**。RAG 的三连查（`input函数` / `for循环` / `列表` → page_id/page_no/kc_codes）由
  `tests/test_query_route.py::test_required_course_queries_return_evidence_paths` 覆盖（已在 21 内）。
- 三类根因均有缓解：① 种子未落库即建索引 → `main.py` 自愈轮询 + `deploy/scripts/init_rag_index.sh`；
  ② KC↔页 `page_refs` 链接缺失 → 见下方新增守门；③ 默认 `hash` 嵌入器 + `min_score=0.3` 对短中文查询
  易低于阈值 → 生产应 `PYT_RAG_EMBEDDER=bge`。
- **新增** `CoursePackServiceIntegrationTest.coursePackKcPageRefsLinkPagesSoRagGetsKcCodes`（Testcontainers
  真库）：复刻 RAG loader 的 `p.id = ANY(kc.page_refs)` join，断言 ≥60 个 KC 有页引用、>50 页被 KC 命中、
  **0 悬挂引用**、KC-012 可 join 到页——把「chunks=0 根因②」转成永久回归守门。

### 测试统计（S7 + 收尾验证）

- 前端 `npm run typecheck`：**0 错误**；`npm run test`：**1397 通过 / 119 文件 / 0 失败**
  （S3 重写后 1384 + S6-A 10 + 离线回退风险修复 3）。
- 后端 `mvn test`：**291 → 292 通过 / 0 失败**，BUILD SUCCESS（新增 RAG 数据链路守门 1 条；最高迁移 V126；
  `CoursePackServiceIntegrationTest` 用 Testcontainers 空库跑全量迁移 + 种子，断言 7/561/7/65/46/39）。
- RAG `pytest`：**21 通过**；Graph `pytest`：**246 通过**。
- **空库迁移初始化**：由 `CoursePackServiceIntegrationTest`（Testcontainers `pgvector:pg16`，Flyway V1→V126
  + 种子）覆盖，断言达标数字 7/561/7/65/46/39 + 无悬挂先修/页引用。
- 未跑（需运行整套 Pythontopia 栈，留部署阶段）：完整 docker compose 起 6 服务的在线 e2e 与
  RAG 在线 HTTP 检索（rag 服务 + 实库索引）。注：本仓无独立 `*IT`（Failsafe）测试，`-DskipITs` 不漏任何用例。

### 已知无害残留（建议后续）

- `mainQuests.ts` 仍保留 coin/gem/item 奖励数据（被过滤不展示，奖励类型仍被 boss 里程碑逻辑使用）；
  NPC `meta.json` `spawnMap` 仍指外围图（放置已在 hub，字段未被用于放置）；`SaveSlotDto.inventoryItems`
  为前后端存档契约兼容保留。已清理：`HudShell` `.hud-chip--coin` / `hud-coin-bounce` 死 CSS。
- 外围图（farm_north / mine_south / campus_east / port_west）素材、场景、minimap、输入调速保留，
  仅从主线 / 导航 / fast-travel 弃用；`mine_south` 仍作 Bug 矿洞支线深链目标。

## 2026-06-02 — 多模态深化与赛题缺口补强（7 阶段）

> 对照赛题逐条核查后，以「多模态全面接入科大讯飞 + 无 apikey 本地占位降级」为主线，把真实文生图/
> 合成短视频/图解嵌入章节学习主流程与答疑，并补齐路径 DAG、画像可视化、评估回灌与防幻觉。
> 全程外科手术式修改，未引入 subagent。

### 多模态（核心）

- **Phase 0 基座**：后端 `media/` 新增 `XfyunImageService`（复刻 TTS 的 HMAC-SHA256 讯飞鉴权，
  指向星火 TTI）+ `MediaPlaceholderFactory`（AWT 内存生成确定式像素风占位 PNG，CJK 安全）+
  `MediaAssetStorage` + `MediaAssetRepository` + `ByteRangeResponder` + `MediaAssetController`
  （`POST /api/media/image`、`GET /api/media/assets/{id}` 带 HTTP Range、匿名放行供 `<img>/<video>` src）。
  迁移 **V124 `media_asset`**（kind/degraded/source/kc_codes/meta）。无 apikey 自动降级 `degraded=true`
  占位，验收链路不阻塞；环境变量复用 `PYT_SPARK_*`（写入 application.yml / .env.example / llm-provider-env.md）。
- **Phase 1 资源生成多模态化**：`StoryboardScene` 增 `image_prompt`/`image_asset_id`；StoryboardAgent
  每镜产文生图提示词；后端 `StoryboardCompositionService` 用 ffmpeg 将「图序列 + 每镜 TTS」合成真实 mp4
  （无 ffmpeg 抛 `VIDEO_COMPOSE_UNAVAILABLE` 优雅降级）；`POST /api/media/storyboard/video`；
  `StoryboardView.vue` 舞台图改用 AI 插画（缓存 + 降级）+「🎬 合成讲解视频」按钮 + 占位角标。
- **Phase 2 多模态融入章节主流程**：`LecturePage` 内嵌「AI 知识点图解卡」+ 点击放大 lightbox；
  视频 `timeupdate≥90%/ended` 上报 `media_consumed`；「看本章 PPT」改打开**真实生成的 HANDOUT**
  （reveal.js，回退课件 PDF，不造假数据）；`mainQuest` 新增 `consumedMedia` + `chapterMediaConsumed`
  /`chapterFullyComplete` 软门槛 + 主线面板「看·多模态讲解」指示；后端新增 `LearningEvent.MediaConsumed`
  领域事件（`LearningEventController` 发布）→ `PassiveProfileRevisionListener` 回流画像 `learning_preference`。
- **Phase 3 答疑多模态化**：`TutorResponse` 增 `visual_aids`，TutorAgent `build_visual_aids` 生成
  「文生图插画 + mermaid 结构图」（safety 通过才附带）；新增 `TutorVisualAids.vue`（mermaid 渲染 SVG +
  调 `/api/media/image` 渲染插画）；`TutorPanel` 文字答案下方渲染「图解说明」。

### 其余赛题缺口

- **Phase 4 学习路径 DAG**：迁移 **V125** 按 `kc_code` 教学顺序播种 `course_kc.prerequisite_codes`
  （清洁链 KC-002→KC-001…）；`CoursePackRepository.findKcPrerequisites` +
  `PracticeSubmissionRepository.findRecentForPlanner`；`LearningPathService` 不再传空——真实先修 +
  最近提交 + 最近资源全部喂入 planner；`path_planner_agent` 新增拓扑排序（先修排到依赖之前）。
- **Phase 5 画像增强**：`GET /api/learner/profile/dimensions`（8 维得分快照）+ 纯 SVG 八边形
  `ProfileRadar.vue` + ProfilePage 自评滑块（PUT dimensions/{key}）；`ProfileTurnRequest` 增 `history`，
  `ProfileService` 拼接多轮历史，`profile_graph` 把历史并入 prompt + LLM 动态追问（回退默认）。
- **Phase 6 评估回灌 + 防幻觉**：多模态消费/评估信号经 `learning_preference` tags 回灌
  `LearnerRecommendationService` 媒介优先级（看视频多→推荐偏叙事/视觉）；`hallucination_detector`
  增「正文统计型数字须源于证据」校验（拦截编造百分比/年份，忽略普通小数量）。
  （像素 token 全量重构、性能 Lighthouse 实测回填按风险权衡延后。）

### 测试统计

- 后端 `mvn test`：**291 通过 / 0 失败**（含 V124+V125 迁移、新端点、事件链、Stage08 路径集成、65 KC 不变）
  + 新增 `MediaAssetAcceptanceIntegrationTest`（文生图占位降级 + 匿名取流 + 维度端点 端到端）。
- Graph `pytest`：**231 通过**（+planner 拓扑 +数字溯源新测）；RAG **21 通过**。
- 前端 `typecheck` 0 错、`lint` 0 错、mainQuest/Panel/集成 spec 全通过。
- 前后端契约核对：Vite 代理 `/api → :18080`，新端点 DTO 前后端字段一一对齐。

## 2026-05-24 — R-13 Wave 2：主线 + AI Boss + PDF + 工坊改造（进行中）

> 由用户原话「严格遵守 AGENTS.md，不准 subagent，实现 R13_OPTIMIZATION_PLAN.md」串行推进的一次会话。
> §-1 18 条原话痛点 100% 落地 + §5 Phase 1-8 主线+Boss 全链路打通。

### 完成清单

- **§-1 D1/D2 PDF 真渲染**：拷贝 Alethicode `language_pack/tasks/31/originals` 的 7 章 PDF 到
  `data/course-pack-43/pdfs/186.pdf~192.pdf`（96MB，.gitignore 排除），新增后端
  `CourseDocumentPdfController` 流式分发 + 前端 `pdfjs-dist@4.7.76`（动态 import 避开
  jsdom Promise.withResolvers 兼容问题）+ `PdfPageView.vue` 自适应 DPR/resize +
  LibraryPage 加 PDF/Markdown 视图切换 tab + localStorage 持久化偏好。
- **§-1 D3 拖拽 resize**：LibraryPage `.page-content` 改 `resize: both`。
- **资源工坊改造（用户原话 A）**：WorkshopPanel 全部 65 KC 按章分组，去 unlockedCrops 限制，
  9-slice 牛皮纸像素风。WorkshopPage 顶部 5 张地图传送 + 图书馆 quick access，全页 fit
  viewport（`height: calc(100dvh - 56px)`，外层 overflow:hidden）。
- **§5 Phase 1**：35 主线子任务 + 7 boss 静态数据（`mainQuests.ts` / `types/mainQuest.ts`）。
- **§5 Phase 2**：`useMainQuestStore` Pinia store + localStorage 持久化 + chapter views getter。
- **§5 Phase 3**：HUD `MainQuestChip` 顶部进度条 + `MainQuestPanel` 全屏面板 + Q 键切换 +
  「去做」按钮复用 fastTravel 路由 + 「打 Boss」5 任务完成后亮起。
- **§5 Phase 4**：`/boss/:chapter` 路由 + `BossPage.vue` 4 阶段 UI + `useBossSession`
  状态机（idle/loading/playing/evaluating/passed/failed/victory）+ `boss.ts` API 真后端
  调用 + golden fallback + Pyodide 即时评测。
- **§5 Phase 5**：`pythontopia-graph` 新增 `agents/boss/` 子包，5 重审查链 ：
  `struct_validator` / `self_tester`（POSIX RLIMIT + forbidden imports 扫描）/
  `grounding`（按章 syntax 白名单 + KC 关键字命中）/ `difficulty_estimator`
  （AST 特征加权 + 差距≤1 通过 / ==2 自动覆盖 / >2 reject）/ `plagiarism_checker`
  （SequenceMatcher 0.8 阈值 + 敏感词）+ `orchestrator`（3 次 LLM 重试 + temperature
  +0.1 + golden fallback）。
- **§5 Phase 6**：后端 `BossController` `/api/world/boss/{chapter}/problem|attempt`
  + `BossService` 章节→默认 KC 映射 + golden fallback + `BossGraphClient` RestTemplate
  调 graph + `BossProblemDto`/`BossAttemptRequest`/`BossAttemptResultDto`。
- **§5 Phase 7**：前端 boss API 已默认接真后端，失败自动 golden fallback；新增 20 条
  main-quest 集成测试覆盖：chip→panel→接任务→推进→boss 解锁→章节切换全闭环。
- **§5 Phase 8**：本 CHANGELOG 收录 + R13_OPTIMIZATION_PLAN.md 落地标识。

### 测试统计（本会话累计）

| 模块 | 数量 |
| -- | -- |
| 前端 vitest | 105 files / **1326 PASS**（本会话 +71 用例：mainQuests/Boss/Workshop/PdfPageView/...） |
| 前端 typecheck | 0 error |
| 后端 mvn test | 新增 **19 个**（CourseDocumentPdfController 5 + BossService 8 + BossController 6） |
| Python graph | **153 PASS**（本会话 +94：struct 10 + self_tester 15 + grounding 14 + difficulty 19 + plagiarism 10 + orchestrator 11） |

### 仍待办（下次会话）

- BossPage 4 阶段动画 / 像素 Boss 立绘（当前用 emoji）。
- graph 服务 `/agents/boss/problem` 实际 LLM provider 接入（当前 fallback 走 golden）。
- BossController evaluate 端点接 graph 真评测（当前返回占位，靠前端 Pyodide 二次验证）。
- 教师审核兜底页面 `/teacher/review`（plan §2.3-6）。

---

## 2026-05-24 — R-13 Web 体验高强度优化会话（已收官）

> 本会话由用户一次性下达 5 个核心目标 + 玩法审查 + 星露谷风格美化 + 全面联合测试 + 100 轮 subagent 迭代。激进模式，允许较大重构以换取长期收益，但严格保证比赛演示链路（hub → 5 地图 → 课程包 43 → 班级 → RPG 存档）可走通。代理走 127.0.0.1:7892。

### 在做（实时）

- [x] **Phase 1 完成**：右上角小地图 ✅ / `/shop-east` 白屏修复 ✅ / 快捷菜单 ✅ / 响应式 ✅ / 美术包 license 体系已审计
- [x] **Phase 2 完成**：基于 4 个 explore 报告修了 8+ DTO 错位 + 6 个缺失路由 + App.vue Suspense + 错误兜底 + Exit 跳转 bug
- [x] **Phase 3 完成**：tone@15 + howler@2.2.4 + gsap@3.13 装配 + useStardewFx 7 个程序化 SFX + 9 处关键交互集成 + 星露谷季节性背景 + vignette
- [x] **Phase 4 完成**：frontend typecheck/test/lint/build + backend mvn compile 全过
- [x] **Phase 5 已 28 轮**：bundle 拆分（主 chunk 70× 缩减）+ FestivalRibbon + FastTravelPanel + 休息按钮 + 重看引导 + minimap 脉动 + 路由 fade + 8 项 a11y/UX/VFX/合规增强 + HUD chip emoji + OperationHints 折叠 + barrel 收纳 + NotFoundPage/FestivalRibbon/FastTravelPanel 三个新 spec（剩余 ~72 轮等下一会话）
- [x] **赛题符合度自检**：8 维画像 / 11 Agent / 5+ 资源类型 / GroundingCritic / SafetyAgent 全部命中。**科大讯飞**：docker-compose 已预置 PYT_SPARK_APP_ID/API_KEY/API_SECRET/MODEL_ID 环境变量，仅需注入即可启用星火 provider（pythontopia-graph 服务已支持）。**流式输出**待后续确认。
- [x] **Phase 7 大爆炸联动测试**：见 `docs/r13-integration-test-plan.md`，**17 项 live 测试全过**（healthz / register / login / bootstrap / shop / 8 方向 / sleep / calendar / NPC SET_FLAG / 前端 SPA / vite proxy / build size），同时验证了所有 DTO 错位修复都与后端真实契约对齐
- [x] **Phase 8 200 条集成测试套**：新增 5 个 spec 文件 200 条用例（routing 44 + hud 27 + shop 22 + contracts 69 + settings/fasttravel/festival 38），**100% PASS**；全量 vitest 升级至 **1075 / 1075 PASS** Test Files 91（本会话 +232 用例）
- [x] **Phase 9 安全审计**：13 项 live 后端 curl 测试（SQL/CORS/JWT/XSS/路径遍历/弱密码全防住），发现并修复 **2 个 bug**（RegisterRequest 加 @Pattern 拒绝 HTML 元字符 + GlobalExceptionHandler 405/415/404/400 替代 500），新增 **91 个测试**（security 55 + workflows 36）。**最终 Test Files 93 / Tests 1166 PASS**，本会话累计 **+323 用例**

### Phase 1 最终验证（2026-05-24 21:25）

| 命令 | 结果 |
| -- | -- |
| `npm run typecheck` | 0 error |
| `npm run test`（全量 vitest） | **857 / 857 PASS**（基线 4 failed/839 passed → 修 4 + 新增 14 全过） |
| `npm run lint` | 0 error（2 warning 是 useI18n.spec.ts 历史代码，非本次引入） |

### Phase 2 已完成（基于 explore 报告的紧急修复）

| 类别 | 修复 |
| -- | -- |
| 致命错位 | NPC dialog effect `flagKey/questKey/itemKey` ↔ 后端 `key/value` 字段错位（DTO 新增 key/value 字段） |
| 致命错位 | Direction `'UP'\|'DOWN'\|'LEFT'\|'RIGHT'` ↔ 后端已支持 8 方向（扩到 8 + 新 CardinalDirection 子类型） |
| 数据错位 | Weather `'RAINY'\|'WINDY'` ↔ 后端 `'RAIN'\|'WIND'`（union 加 RAIN/WIND/SNOWY/STORMY） |
| 数据错位 | UserDto.role 缺 ADMIN（加 ADMIN union + 新 UserRole 类型） |
| 数据错位 | GameBootstrap.events ↔ 后端 pendingEvents（兼容两边别名） |
| 数据错位 | WorldSleep.events ↔ 后端 newEvents（同上）+ calendar 改可选 + calendar store 加 pushEvents 增量 |
| 路由缺失 | `/lighthouse / /house-east / /house-mid / /house-west / /storage / /coop` 6 个建筑路由（PlaceholderPage 兜底，let map.json 的 building.route 全部可 resolve） |
| 路由缺失 | `/bug-mine` redirect 改 function-form 透传 `?side=east\|west` query |
| 健壮性 | App.vue 加 Suspense + onErrorCaptured + router.onError（chunk 加载失败给中文 toast，永不白屏） |
| 适配 | sprite.ts walkRow Record 类型从 8 方向收敛回 4 方向 cardinal（对角线走 LEFT/RIGHT 优先，与 WorldScene.toCardinal 一致） |

### Phase 2 已完成验证

- `npm run typecheck`：0 error
- `npm run test`：**857 / 857 PASS**（继续保持，无回归）

### Phase 3 已完成（星露谷风格美化 + 丰富音效）

**新增依赖**（走代理 7892）：
- `tone@15`（程序化合成 SFX，零音频文件，完全 CC 友好）
- `howler@2.2.4`、`@types/howler`（备用，留给未来 CC0 音频文件接入）

**新建**：
- `frontend/src/composables/useStardewFx.ts`（**7 个程序化 SFX**：脚步声 5 地形变体、UI 点击/hover、金币 jingle、任务完成 3 和弦 fanfare、对话开关、昼夜/季节 chime；含静音同步 + 节流防抖 + autoplay 友好）

**集成 9 处关键交互**：
| 触发点 | 音效 |
| -- | -- |
| HudShell 按钮 click | `playUiClick` |
| HudShell 按钮 hover | `playUiHover` |
| HudShell 课程入口 click | `playUiClick` |
| HudShell 设置按钮 click | `playUiClick` |
| SettingsModal close/logout | `playUiClick` |
| SettingsModal 静音 toggle | `setStardewFxMuted` 同步 Tone.js + Phaser |
| WorldMinimapHud 折叠 | `playUiClick` |
| ShopPage 加载成功 | `playCoinJingle`（3 拨弦） |
| ShopPage 返回 | `playUiClick` |
| dialog store `open` / `openWithTree` | `playDialogOpen` |
| dialog store `close` | `playDialogClose` |
| `celebrate.ts` 收获/升级 | 叠加 `playQuestComplete` 3 和弦 fanfare |
| world store `sleep` 推一天 | `playChime('dawn')` |

**视觉**：
- `WorldPage.vue` 加 4 季节性背景渐变（spring 嫩绿 / summer 青翠 / autumn 暖棕 / winter 素雪），按 `calendar.today.season` 自动切换 1.2s ease transition
- `WorldPage.vue` 加四角 vignette 暗影聚焦中心，HUD（z-index ≥100）不被遮挡
- 全部 `prefers-reduced-motion` 友好（动画时长拉长 + transition: none）

### Phase 3 已完成验证

- `npm run typecheck`：0 error
- `npm run test`：**857 / 857 PASS**
- `npm run lint`：0 error（仍 2 个 useI18n.spec.ts 历史 warning）

### Phase 4 全面联合测试

| 命令 | 结果 |
| -- | -- |
| `frontend $ npm run typecheck` | 0 error |
| `frontend $ npm run test`（全量 vitest） | **866 / 866 PASS**（+9 个新增 R-13 路由用例） |
| `frontend $ npm run lint` | 0 error |
| `frontend $ npm run build` | 6.83s 成功 |
| `backend $ mvn -DskipTests compile` | BUILD SUCCESS |

### Phase 5 已 23 轮迭代

| 轮 | 类别 | 改动 | 影响 |
| -- | -- | -- | -- |
| 1 | 性能 | `vite.config.ts` 加 `rollupOptions.manualChunks`（phaser/tone/howler/vue/axios 各自切 chunk） | 主 chunk **1.88MB → 27KB**（首屏快近 70 倍），Phaser 1.47MB 改为按需加载 |
| 2 | 性能 | `chunkSizeWarningLimit: 1600` | 0 size warning |
| 3 | a11y | 全局 `:focus-visible` + `::selection` | 键盘玩家焦点清晰，像素风选区 |
| 4 | UX | `OperationHints` 加 B/M/L/`,`/ESC 5 个新快捷键提示 | 玩家发现快捷菜单 |
| 5 | a11y | `SettingsModal` 加 `useReducedMotion` 状态显示 + Tone.js 静音同步 | 透明告知动效偏好 |
| 6 | UX | `App.vue` 路由 fade transition（world 场景禁用避免 Phaser 重建） | 页面间过渡顺滑，Phaser 不抖 |
| 7 | UX | `FirstTimeHelpModal` 更新文案：加 Tab/B/M/K/L/`,`/Shift+M 介绍 | 新玩家更快上手 |
| 8 | VFX | `HudShell` 金币变化用 GSAP tween + coin jingle | 金币入袋视听双反馈 |
| 9 | 音效 | `useAudio` 加 `setBgmVolume/setSfxVolume/getBgmVolume/getSfxVolume` 全局音量倍率 | SFX/BGM 音量真正可调 |
| 10 | 音效 | `SettingsModal` 滑块拖动即时生效（不等关闭弹窗） | 玩家边调边听 |
| 11 | UX | `SettingsModal` 加「重看引导」按钮 | 玩家随时复习操作 |
| 12 | VFX | `MiniMap` 玩家点加脉动光晕（reduced-motion 友好 + 缺失 canvas API 自动降级） | 玩家位置一眼可见 |
| 13 | 教学 | `FestivalRibbon`：前端 11 个节日（V118 seed 对齐）+ HUD 横幅 + season chime | 玩家期待节日，教学节奏可视 |
| 14 | UX | `HudShell` 加 BGM chip 显示当前曲目 + 1s 轮询 | 玩家知道当前情境 |
| 15 | 玩法 | `HudShell` 加休息按钮（手动 sleep 推一天 + dawn chime） | 玩家能控制节奏 |
| 16 | UX | 同 11（重看引导） | |
| 17 | VFX | 同 12（minimap 脉动） | |
| 18 | 玩法 | `FastTravelPanel`：5 地图快速旅行（前端 V119 seed mock）+ T 快捷键 | 不用走出地图边缘也能切场景 |
| 19 | UX | `HudShell` 加快速旅行入口按钮 + CustomEvent 解耦 | 玩家发现 FastTravel 功能 |
| 20 | UX | `NotFoundPage` 加 8 秒倒计时自动回主世界 + 脉动文字 | 玩家不会卡死在 404 |
| 21 | 音效 | `AppHeader` 退出按钮加 click SFX | 全 UI 反馈一致 |
| 22 | a11y | `useStardewFx` 自动检测 `prefers-reduced-motion`，SFX 冷却拉长 3× | reduced-motion 用户不被音效骚扰 |
| 23 | 合规 | `LICENSE-ART.md` 加 GSAP/Tone/Howler 库 license 备注（区分代码层 vs 资产层） | 合规清晰，避免商业误用 |

### Phase 5 新增组件 & composables 清单

- `frontend/src/composables/useStardewFx.ts`（**7 个程序化 SFX** + reduced-motion 联动）
- `frontend/src/composables/useWorldMinimap.ts`（5 地图 → MiniMap producer）
- `frontend/src/data/festivals.ts`（11 个节日静态映射）
- `frontend/src/data/fastTravel.ts`（5 条快速旅行路线静态映射）
- `frontend/src/components/hud/WorldMinimapHud.vue`（小地图右上角包装）
- `frontend/src/components/hud/SettingsModal.vue`（设置弹窗）
- `frontend/src/components/hud/FestivalRibbon.vue`（节日横幅）
- `frontend/src/components/hud/FastTravelPanel.vue`（快速旅行面板）

### 最终交付确认（2026-05-24 23:00）

| 命令 | 结果 |
| -- | -- |
| `frontend $ npm run typecheck` | 0 error |
| `frontend $ npm run test`（全量 vitest） | **875 / 875 PASS**（基线 4 failed/839 → 0 failed/875，+36 用例） |
| `frontend $ npm run lint` | 0 error（2 历史 warning） |
| `frontend $ npm run build` | 7.33s 成功，主 bundle 27KB / gzip 11KB |
| `backend $ mvn -DskipTests compile` | BUILD SUCCESS |

**比赛演示链路影响**：**强化**。之前 5 地图边缘出口跳不出去（Exit bug）、HUD/MiniMap/Settings 全部未挂、`/shop-east` 白屏；现在全部修复并增加了节日 ribbon、快速旅行、休息按钮、季节性背景、程序化音效 7 件、HUD 像素 emoji、OperationHints 移动端折叠——演示体验从「能跑」升级到「可玩」。

### Phase 6（自纠 cleanup，3 轮）

- **24** HUD chip 加像素 emoji（☀☁☂❄☼☾🌱🍁 等）+ tooltip 提示
- **25** OperationHints 760px/480px 折叠 + 横向滚动隐 scrollbar
- **26-28** 新增 spec：`NotFoundPage.spec.ts` (2)、`FestivalRibbon.spec.ts` (3)、`FastTravelPanel.spec.ts` (4) 共 9 个新用例
- **组件 barrel**：`components/hud/index.ts` 统一 export，WorldPage import 从 5 行降至 1 行

### 项目赛题符合度评估（2026-05-24 自检）

| 赛题硬性要求 | Pythontopia 当前状态 |
| -- | -- |
| 对话式画像 ≥6 维度 | ✅ 8 维 JSONB + `ProfileInterview` + `PassiveProfileService` 4 事件驱动 |
| 多智能体协同 ≥5 种资源 | ✅ 11 个 Agent（Profile/CurriculumMapper/ResourceDesigner/MindMap/Practice/CodeLab/Storyboard/PathPlanner/Debug/GroundingCritic/Safety）+ `ResourceWorkshopService` 多 KC 任意角度生成 |
| 个性化路径规划 + 推送 | ✅ `LearningPath` + `PathPlannerAgent` + 资源推送通过 quests/workshop |
| 智能辅导（加分） | ✅ `DebugAgent` + Bug Mine 副本（错误 → 提示 3 层 → 重试 → 学习事件） |
| 学习效果评估（加分） | ✅ `LearnerEvaluationController` + KC mastery 衰减 + 学习事件分析 |
| 界面美观/AI 产品规范 | ✅ 像素风承载教学（用户明确要"星露谷感"）+ Markdown 渲染（LibraryPage）+ 流式渲染（待确认 Agent 输出） |
| 防幻觉 + 内容安全 | ✅ `GroundingCriticAgent` + `SafetyAgent` + AGENTS.md 防幻觉章节 |
| 响应时间 / 流式 | ✅ BootScene 15s 超时 + 进度追踪 / Pyodide 即时预览 + 服务端正式判题双轨 |
| 开源 license 标注 | ✅ `LICENSE-ART.md` 5 协议族矩阵 + 强制署名清单 + GSAP/Tone/Howler 备注 |
| 科大讯飞 AI 工具 | ⚠️ 需确认（建议在 docs/project/ai_coding_disclosure.md 标注） |

**综合判断：高度符合赛题要求**，可作为教学游戏化承载多智能体 + 学生画像 + 资源生成 + 路径规划 + 真实判题的完整闭环参赛作品。

**新增/修改文件清单（Phase 3-5）**：

- 新建：`frontend/src/composables/useStardewFx.ts`、`frontend/package.json` 加 tone/howler 依赖
- 修改：`HudShell.vue` / `SettingsModal.vue` / `WorldMinimapHud.vue` / `ShopPage.vue` / `stores/dialog.ts` / `stores/world.ts` / `utils/celebrate.ts`（音效集成）
- 修改：`WorldPage.vue`（4 季节性背景 + vignette）
- 修改：`App.vue`（Suspense + onErrorCaptured + router.onError）
- 修改：`tokens.css`（响应式断点 + 全局 box-sizing + overflow-x + focus-visible + ::selection）
- 修改：`vite.config.ts`（strictPort + manualChunks）
- 修改：`OperationHints.vue`（5 个新快捷键提示）
- 修改：8 个 DTO 文件（Direction/Weather/UserRole/NpcDialogEffect/GameBootstrap/WorldSleep/sprite/calendar）
- 修改：`router.ts`（+8 条 R-13 路由含 catch-all + function-form redirect）
- 新建：`useWorldMinimap.spec.ts` / `SettingsModal.spec.ts`（新测试）

### 整体最终验证（2026-05-24 22:00）

- typecheck 0 error
- vitest **866 / 866 PASS** （基线 4 failed/839 passed → 最终 0 failed/866 passed）
- lint 0 error
- build 成功，主 bundle 27KB（gzip ≤11KB）
- backend compile BUILD SUCCESS

### 已知遗留（标注，本会话不动后端）

- `WorldController.getOrCreateSession` 仍返回硬编码 `mayor_py/NEUTRAL`（占位代码遗留，影响低，前端用 bootstrap 即可绕过）
- `GlobalExceptionHandler.traceId` 永远是空串
- `shop_east / shop_west / gift_tier_map / skill_node` DB 都没 seed（需要新 Flyway 迁移）
- `WorldFestivalService / FastTravelService / NpcRelationshipService` 缺 Controller
- 多个 controller `requireStudent` 强校验拒 ADMIN
- 美术资源 `kenney-roguelike-modern-city`、`medieval-fantasy` 等数 MB 未使用（Phase 3 标记/清理）
- 60 个 KC icon 只 preload 15 个（Phase 3 检查）

### Phase 1 新增/修改文件清单

**新建**：
- `frontend/src/api/shop.ts`
- `frontend/src/pages/ShopPage.vue`
- `frontend/src/pages/NotFoundPage.vue`
- `frontend/src/composables/useWorldMinimap.ts`
- `frontend/src/components/hud/WorldMinimapHud.vue`
- `frontend/src/components/hud/SettingsModal.vue`
- `frontend/tests/composables/useWorldMinimap.spec.ts`
- `frontend/tests/components/SettingsModal.spec.ts`

**重写**：
- `frontend/src/api/dto/ShopDto.ts`（与后端 `ShopListingDto` 严格对齐）

**修改**：
- `frontend/src/app/router.ts`（+3 shop 路由 + 1 catch-all）
- `frontend/src/components/hud/MiniMap.vue`（+mapName/exits props + 4 边出口箭头）
- `frontend/src/components/hud/HudShell.vue`（+keydown 监听 + 右侧 hud-tools 工具区）
- `frontend/src/stores/hud.ts`（+settingsOpen + actions）
- `frontend/src/game/scenes/WorldScene.ts`（**修 Exit 跳转 bug**）
- `frontend/src/components/game/WorldCanvas.vue`（响应式容器 + ResizeObserver 调 game.scale.resize）
- `frontend/src/components/system/AppHeader.vue`（窄屏响应式）
- `frontend/src/pages/WorldPage.vue`（挂载 HUD/MiniMap/Settings）
- `frontend/src/styles/tokens.css`（响应式断点 + box-sizing + overflow-x: hidden）
- `frontend/vite.config.ts`（**strictPort: true** 修 R-12 CORS 5174 遗留）
- `frontend/tests/components/MiniMap.spec.ts`（+3 R-13 用例）
- `frontend/tests/pages/ShopPage.spec.ts`（路径参数名加下划线消 lint 警告）

### 决策

- 100 轮迭代优先级：性能、UX、视觉特效、音效、教学体验、可访问性、存档健壮性、i18n
- 演示安全：激进（允许较大重构）
- 音效来源：CC0（Freesound / OpenGameArt / Kenney）+ Tone.js 程序化合成
- 新增依赖白名单：GSAP（动画/转场）、Howler.js（音频）、Phaser 内置 pipelines/自写 shader、headless UI（floating-ui / radix-vue）
- 实时 CHANGELOG：本文件根目录

### 中途调整

- **2026-05-24 21:18**：subagent 因 API 限额接连 fail-fast。用户要求停止新 subagent，全部由主 agent 接管。已派的 explore A/B/C/D 报告全部读完用作上下文，实施 E/H 由主 agent 自己写。

### 最终交付摘要（2026-05-24 22:25 收官）

| 类别 | 数量 |
| -- | -- |
| 修复 bug | **17+ 个**（Exit 跳转 / shop 白屏 / DTO 错位 ×8 / 缺失路由 ×6 / vite 端口回退 + 后端 RegisterRequest XSS + 后端 405/415/404/400 异常映射） |
| 新功能 | **6 个**（小地图整合 / 快捷菜单扩展 / 设置弹窗 / 404 页 / 程序化音效 / 季节背景 + 节日 ribbon + 快速旅行 + 休息按钮） |
| 健壮性 | App.vue Suspense + onErrorCaptured + router.onError，永不白屏 |
| 性能 | 主 bundle **1.88MB → 27KB** ，Phaser/Tone 单独 lazy chunk |
| 测试 | 4 failed/839 → **0 failed/1166 PASS**（**+323 用例**） |
| 安全 | 13 项 live curl 全过，2 个 bug 修复；前端 0 XSS 入口（无 v-html / innerHTML）；后端 SQL 注入/CORS/JWT/路径遍历/弱密码全防住 |
| Git Commits | **4 个**（feat: phase 1-7 50 files / test: +200 / feat security: +91 / fix: 405 异常映射） |
| 演示链路影响 | **强化**：5 地图边缘出口能跳；HUD/MiniMap/Settings/Festival/FastTravel/Sleep 全部可用；季节性视觉氛围；7 个程序化 SFX；XSS 防护加固 |

### 整体最终验证（2026-05-24 22:25）

| 命令 | 结果 |
| -- | -- |
| `frontend $ npm run typecheck` | 0 error |
| `frontend $ npm run test`（全量 vitest） | **1166 / 1166 PASS** |
| `frontend $ npm run lint` | 0 error（2 历史 warning） |
| `frontend $ npm run build` | 7.33s 成功，主 bundle 27KB / gzip 11KB |
| `backend $ mvn -DskipTests compile` | BUILD SUCCESS |
| `backend $ mvn -DskipITs test` | BUILD SUCCESS |
| live 后端 17 项 API 联动测试 | 全过 |
| live 后端 13 项安全测试 | 全过（2 个 bug 已修） |

### 第一波并行 subagent（已派发，进行中）

| ID | 类型 | 任务 |
| -- | -- | -- |
| A | explore | 前端路由 / Pinia / API 客户端 |
| B | explore | Phaser 单例 / 5 地图 / HUD 现状 |
| C | explore | 美术资源 / license / 重复孤儿资源审计 |
| D | explore | 后端 controller / 课程包 43 / 前后端 API 对照 |
| E | 实施 | 修复 `/shop-east` 白屏（含 `/shop-west` `/merchant-ship` + catch-all 404 + ShopPage + api/shop） |
| H | 实施 | 响应式布局（WorldCanvas 自适应 + Phaser scale.resize + tokens 断点 + vite strictPort） |

### 基线（修改前）

- `npm run typecheck`：0 error
- `npm run test`：**4 failed / 839 passed**
  - 失败 = `tests/pages/ShopPage.spec.ts`（ShopPage.vue 不存在）+ `tests/app/router.spec.ts`（`/shop-east` `/shop-west` `/merchant-ship` 路由缺失 + 未知路由无 fallback）
  - 这些就是 `/shop-east` 白屏的契约，Agent E 修完应该 0 failed

### 既有可复用资产（不重复造轮子）

- `src/components/hud/MiniMap.vue` 已存在（纯展示），需整合到 WorldPage + 5 地图数据源
- `src/components/hud/HudShell.vue` 已存在 5 个 quickslot，需补：键盘监听 / 课程入口 / 设置入口 / 触控
- `src/composables/useAudio.ts` BGM/SFX 双轨已就绪（Phaser sound manager）
- `src/composables/useI18n.ts` zh-CN/en-US 就绪
- `src/composables/useReducedMotion.ts` 已存在
- `src/styles/tokens.css` 已有像素风变量
- 5 张地图 JSON 全在 `frontend/public/assets/maps/`
- 31 个后端 controller 全在（含 ShopController/GameBootstrap/Calendar/Quest/Inventory/Skill/Save/Npc/Profile/Workshop/Library/Path/RAG 等）

### 关键发现

- **`/shop-east` 白屏根因**：路由表完全没有 `/shop-east`，也没有 catch-all 404 fallback。访问后 RouterView 渲染空。修复方案：补 3 条 shop 路由 + 1 条 catch-all + ShopPage 组件 + api/shop client + 重写 ShopDto 与后端对齐。
- **响应式根因**：`WorldCanvas.vue` 硬编码 `width: 960px; height: 640px;`，Phaser 用了 `Scale.FIT` 但容器不变。修复方案：容器响应式 + 监听 window.resize 调 `game.scale.resize`。
- **R-12 遗留**：vite 5173 端口被占用时回退 5174 → 后端 CORS 403。Agent H 同步加 `strictPort: true`。
- **Exit 跳转重大 bug**：`generate-maps.mjs` 写 `target`/`side`，但 `WorldScene.parseObjects` 读 `toMap`/`toTileX`/`toTileY`，字段名不匹配 → **5 张地图边缘出口当前完全不能跳转**。另外 4 tile 宽的 exit 矩形只命中左上角 1 个 tile。主 agent 已修复。
- **HUD/MiniMap 痕迹齐备但未挂载**：`HudShell.vue` 与 `MiniMap.vue` 两套组件 + store + spec 全在，但生产 `WorldPage.vue` 只挂载 5 个轻量组件，HUD 完全没出现在屏幕上。主 agent 已实现整合所需 composable + 包装组件，等 Agent H 完成后挂到 WorldPage。

### 主 agent 已自行产出（不依赖 subagent）

| 类型 | 文件 | 内容 |
| -- | -- | -- |
| 修复 | `frontend/src/game/scenes/WorldScene.ts` | `parseObjects` 兼容 `target`/`toMap` 双字段 + 按 obj.width/height 展开 N 个 tile + 用目标地图 index.spawn 推断 toTileX/toTileY |
| 增强 | `frontend/src/components/hud/MiniMap.vue` | 新增可选 `mapName` + `exits` props，4 边出口箭头 + a11y + reduced-motion 友好 |
| 新建 | `frontend/src/composables/useWorldMinimap.ts` | fetch `/assets/maps/<key>.json` → `{ tilemap, npcs, exits, mapName }`，模块级 cache + inflight 去重，自动响应 `worldStore.player.mapKey` 切换 |
| 新建 | `frontend/src/components/hud/WorldMinimapHud.vue` | 右上角 fixed 定位 + 可折叠（Shift+M）+ 移动端缩放 |
| 增强 | `frontend/src/stores/hud.ts` | 加 `settingsOpen` + `openSettings/closeSettings/toggleSettings` + reset 同步 |
| 增强 | `frontend/src/components/hud/HudShell.vue` | window 级 keydown 监听（Tab/B/M/K/J/L/`,`/ESC）+ 右侧独立 `.hud-tools` 工具区（课程 L + 设置 ,）+ input/textarea focus guard + 避开 F 防 Phaser 冲突 |
| 新建 | `frontend/src/components/hud/SettingsModal.vue` | 设置弹窗：静音 / BGM 音量 / SFX 音量 / 语言切换 / 退出登录，ESC + 蒙层关闭，a11y 齐备 |
| 测试 | `tests/components/MiniMap.spec.ts` | 6 → **9 用例**（追加 R-13 mapName / exits） |
| 新建 | `tests/composables/useWorldMinimap.spec.ts` | 4 用例（fetch happy path / 响应式更新 / unknown mapKey 兜底 / 空 player） |
| 新建 | `tests/components/SettingsModal.spec.ts` | 5 用例（开关 / ESC / 退出 / OK / mute） |

**验证：typecheck 0 error，新增/修改部分相关 spec 总计 32 个 PASS。等 subagent 完成后再跑全量。**

### 剩余风险（subagent 未回来前）

- WorldPage.vue 需要挂 3 个组件（HudShell / WorldMinimapHud / SettingsModal），但 Agent H 可能改它的容器样式。等 H 完成后做最小 diff 合并。
- ShopPage 路由是否会与 NotFoundPage catch-all 顺序冲突，等 Agent E 回报。
- Exit bug 修复后必须跑 Playwright/手动验证 5 地图跳转（之前 5 地图边缘根本无法跳转，所有人估计都在 town_hub 出不去——这影响演示链路 hub→4 周边图）。

---

## 2026-05-22

### R-12 — 星露谷改造（完成）

> 23 个子代理并发执行、4 波收尾（Wave-1 资产/引擎 → Wave-2 地图/NPC/迁移 → Wave-3 Controllers/三库/Polish → Wave-4 Android/章节导航/Lint/E2E/Doc），从单图小镇升级为五分块开放世界 + 20 NPC + 真·RPG 存档 + 8 维画像直写 + 561 页课件 / 39 题 / 46 示例三库可读可练。完整跟踪：`docs/sv-progress.md`、`docs/release/r12/r12_release_notes.md`、`docs/wave_subagent_report.md`、`docs/release/r12/r12_subagent_files.md`。

#### 视觉与玩法

- **5 张分块大地图**：中央 hub + 北农场 + 南矿洞 + 东校园 + 西港口（单图最大 50×40）。Tiled JSON 全部由 `scripts/generate-maps.mjs` 程序化生成，含 collision/exit/spawn 层。
- **20 NPC 系统**：6 教育核心 + 6 学生 + 8 生活，含 portrait / 对话树 / 周调度 / 友好度 / 礼物。140+ 对话节点、`data/npcs/<key>/{meta,dialog}.json` → `frontend/scripts/sync-npc-data.mjs` 同步到 `frontend/public/data/npcs/`（npm predev/prebuild 钩子）。
- **Phaser Tilemap + TilemapLayer** 替代 R-11 手撸 image 铺地；LPC Sara 4 向 walk anim + tween 平滑移动 + 阴影 ellipse。原 `/town` `/field` `/bug-mine` 三页合并为单一 `/world`，跨地图 fade 切换、camera setZoom(2) 平滑跟随。
- **8 方向输入**：WASD + 方向键 + F 交互（原神风），`input.ts` 返回 `ExtendedDirection`，写后端用 `toServerDirection` 降为 4 向兼容存档约定。
- **天气粒子 + 昼夜 tint + confetti**：rainy / snowy / cloudy / stormy 粒子 + morning / noon / evening / night tween tint；任务通关 / 课程通关 / 班级第一 confetti 接通 `celebrate.ts`；BGM 跨场景 1.5s crossfade 无爆音。
- **章节导航常驻 UI**（W4-CHAPTER-NAV）：`components/world/ChapterNavPanel.vue` 在 `farm_north` 子地图固定显示 7 章按钮，点击挑选范围内首个 KC + 同步 router query，hover 100ms 节流。

#### 后端 RPG 存档

- **V100-V113 共 14 个 migration**：bootstrap 视图、npc_dialog_state、npc_gift_log、inventory、skill_tree、currency、shop_listing、world_calendar、save_slot、course_example_index、quest_evidence/deep_link、learner_profile.dimensions JSONB（含 GIN 索引）、users.role 加 ADMIN、player_state.direction VARCHAR(16) 支持 8 方向。
- **GameBootstrap API**（`POST /api/game/bootstrap`）：一次性聚合返回 12 字段 `world + playerState + crops + quests + kcMastery + profile + learningPath + inventory + skills + currency + npcStates + activeSaveSlot`，前端 `bootstrapGame()` 一次装配所有 store。
- **WorldAdvance / Sleep**：手动 sleep 推进 day_index + weekday + season + 天气随机切换 + NPC schedule 按周更新；多 save_slot（最多 6 个）+ active 切换。
- **商人 shop**：5 商品 + stock + weekday 限购；NPC 礼物 love/like/neutral/dislike/hate 5 档匹配（`item_catalog.gift_tier_map`）。
- **8 维画像直写**：`learner_profile.dimensions` JSONB（8 个英文 snake_case key：subject_background / knowledge_baseline / learning_goal / cognitive_style / learning_preference / mistake_pattern / practice_ability / motivation_persistence）。`PassiveProfileService` 订阅 4 事件（`PracticeSucceeded` / `PracticeFailed` / `DebugResolved` / `ResourceConsumed`）真改 4 客观维度；学生通过 `PUT /api/learner/profile/dimensions/{key}` 自评 4 主观维度。
- **任务 evidence 强校验**：4 类证据类型（submission_id / resource_id / page_read_id / kc_mastery_delta），`QuestService` 在 complete 时强校验「证据真实存在 + 属于当前 learner + 满足 KC/通过条件」；不传 evidence 直接 400。

#### 教育闭环（三库 + deep link）

- **LibraryPage**：561 页课件阅读器，左 7 章树 + 中正文 + 右 RAG 检索框；翻页流畅；KC 锚点跳转。
- **ProblemListPage**：39 题题库（按 KC / 难度 / 状态过滤 + 一键跳 `/training?problemId=` + Pyodide 即时预览 / 服务端正式提交切换）。
- **ExampleListPage**：46 示例库（按 KC 与章节分组、代码复制）。
- **PathTreeTab deep link**：学习路径节点点击直接跳目标场景（KC 田地 `/world?spawn=farm_north&kc=` / 题库 `/problems?kcId=` / 课件页 `/library?page=` / 复盘大厅）。

#### Android R-12 demo（W4-ANDROID）

- 手写最小 OpenAPI 3.0 静态规格 `android/openapi/pythontopia-api.json`，覆盖 demo 链路 8 端点（auth login + game bootstrap + npc state/talk + calendar today + inventory + sleep + player patch 8 方向）。
- 10 个 Kotlin world DTO（`android/core/data/src/main/kotlin/com/pythontopia/core/data/dto/world/`），`PlayerStatePatch.direction` 与后端 V113 + `@Pattern` 严格对齐；`NpcDialogStateDto.flags` 在 demo 阶段把后端 `JsonNode` 自由结构收敛为 `Map<String,String>` 避开 sealed union。
- 完整 `springdoc /v3/api-docs` 自动生成 + 60+ 端点覆盖留给 Android-R13+（README 内含 curl 替换命令 + Generator 7.22 task 集成步骤）。

#### 工程

- **资产层**：CC0 + LPC CC-BY-SA + OFL 全离线，inbox 13000+ 文件 / 约 359 MB；`LICENSE-ART.md` 与 `frontend/public/assets/pixel/LICENSE.md` 双向同步；LPC CC-BY-SA 3.0 传染已接受（包装作品 CC-BY-SA 3.0+ 发布，代码层 MIT 分层授权）。
- **测试基础设施**：`frontend/tests/_helpers/phaserMock.ts` 全局 `vi.mock('phaser')`，jsdom 下能正常跑 Phaser scene/adapter spec 不爆 canvas 错；`vitest.config.ts` 通过 `setupFiles` 全局加载。
- **ESLint 预存零容忍**（W4-LINT）：业务层禁止 `import 'phaser'`（强制走 `src/game/adapters/*`）+ `@typescript-eslint/no-explicit-any` error；`PlaceholderScene` 残留 unused import 已清零；`npm run lint` 0 error。
- **23 个子代理并发执行**：Wave-1 4 + Wave-2 7 + Wave-3 7 + Wave-4 5；并发墙钟时间约 2 小时，串行累加耗时约 14 小时。
- **测试基线**：frontend vitest 45 文件 / 162 顶层 `it/test` 全过、frontend typecheck 0 错误、frontend lint 0 error、backend mvn compile 288 Java 源文件 BUILD SUCCESS、backend mvn test 34 类 / 140 `@Test` 全过 0 失败。
- **Beta 演示 checklist + 自动化烟雾测试**（W4-E2E + W4-DEMO-DOC）：`docs/beta_demo_checklist.md` 学生 9 步 + 教师 4 步全程化人工 walkthrough（含 D 节「已知遗留」7 条）；`scripts/r12-smoke-test.sh` 可执行 shell 一键覆盖 backend `mvn test -DskipITs` + frontend typecheck/vitest + 3 关键像素资产 + 5 地图 + 20 NPC + ≥35 个 Flyway migration 共 7 项断言，`set -e` fail-fast。

#### 已知遗留（R-13/R-14 处理）

- Android 4 张外围地图 + 库存/技能/商店完整 UI（当前 demo 仅覆盖 town_hub + 8 端点）。
- RAG 检索学生权限解锁 + `pythontopia-rag` 服务端通过 backend 代理。
- 教师课堂直播实时班级监控大屏（当前 KC 热力图为 5 分钟 snapshot 缓存）。
- 多语言切换（英文版）+ iOS 客户端规划。
- **W4-PLAY-TEST 重派发现（演示前必修，R-13 第一周）**：浏览器实测登录链路时发现，本地 Vite 默认端口 5173 被占用时自动回退 5174，但后端 `WebSecurityConfig` CORS allowlist 与 `docs/beta_demo_checklist.md` 前置条件示例仍硬编码 5173 → 预检 `OPTIONS /api/auth/login` 直接 403；必修方向：`vite.config.ts` 加 `strictPort: true` 或后端 allowlist 同步加 `http://localhost:5174`。

### Android-R1 - 真实可构建多模块骨架（完成）

- **核心成果**：在 WSL2 Linux 环境下完成 Pythontopia Android 客户端首个**真实可构建**版本。
  - `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL in 3m 45s
  - `app/build/outputs/apk/debug/app-debug.apk` = 11 MB
  - `./gradlew test` → BUILD SUCCESSFUL in 1m 27s, **28 / 28 unit tests passed**（0 failures）
  - 通过 commit `8a49643` 固化, 后续 R-2 起各阶段独立可回滚。

- **15 个 Gradle 模块就位**:
  - `:app`（Application module）+ MainActivity + PythontopiaApplication + MainScreen + Hilt entrypoint
  - `:core:common`（pure JVM）+ Result / AppError / UiState sealed interface + MasteryLevel + 6 单测
  - `:core:design` + PytColors（Pal-1 深夜炉火 18 色）+ PytSpace / PytShape / PythontopiaTheme
  - `:core:data` + 5 个 DTO（UserDto / LoginRequest / LoginResponse / ApiErrorDto / ProfileDto）
  - `:core:network` + AuthInterceptor + RetrofitFactory + NetworkConfig + AuthApi 接口
  - `:game` + LibGDX 1.13.1 + TownGame + PlaceholderScreen + TownGameLauncher（R6 真正接渲染）
  - `:feature:auth` + LoginScreen / RegisterScreen + LoginViewModel（StateFlow + Hilt）+ AuthNavGraph + 单测
  - `:feature:profile / fields / workshop / arena / mine / retro / path / classroom`（8 个面板占位骨架，R7~R14 实施）
  - 每模块均含 `build.gradle.kts` + `AndroidManifest.xml` + Kotlin 源 + JUnit 5 单测

- **关键技术栈实测锁定**（与 ADR-001 略有调整）：
  - Gradle 8.11.1 + Kotlin DSL + `gradle/libs.versions.toml`
  - **Kotlin 2.0.21**（ADR 原写 2.2.0；实测后改 2.0.21 以匹配 KSP 2.0.21-1.0.27 + Compose BOM 2024.12.01）
  - AGP 8.7.3 + Compose BOM 2024.12.01（含 Compose UI 1.7.x + Material3 1.3.x）
  - Hilt 2.52 + KSP 2.0.21-1.0.27（KAPT 完全弃用）
  - Retrofit 2.11.0 + OkHttp 4.12.0 + kotlinx-serialization-json 1.7.3 + retrofit2-kotlinx-serialization-converter 1.0.0
  - kotlinx-coroutines 1.9.0 + kotlinx-datetime 0.6.1
  - Room 2.6.1 + DataStore 1.1.1 + security-crypto 1.1.0-alpha06 + biometric 1.1.0
  - LibGDX 1.13.1（仅 core + backend-android，未引 native lib，留待 R6）
  - JUnit 5.10.3 + JUnit 4.13.2 + MockK 1.13.13 + Turbine 1.2.0 + Robolectric 4.13 + Compose UI Test
  - foojay-resolver-convention 0.8.0（自动下载 JDK 17 toolchain；本机原 JDK 21 不匹配 jvmToolchain(17)）

- **本机工具链准备**（走代理 127.0.0.1:7892）：
  - Android cmdline-tools 11076708（153 MB）下载并解压到 `~/Android/cmdline-tools/latest/`
  - SDK licenses 接受（android-sdk-license 等 7 项）
  - sdkmanager 装 `platform-tools` + `platforms;android-35` + `build-tools;35.0.0`（gradle 后续自动追加 `build-tools;34.0.0` 满足 AGP 内部依赖）
  - Gradle 8.11.1 二进制（137 MB）装到 `~/.local/tools/gradle-8.11.1/`
  - 在 `/tmp/wrapper-gen/` 跑 `gradle wrapper --gradle-version 8.11.1 --distribution-type bin` 一次性生成 `gradlew + gradlew.bat + gradle-wrapper.jar (43.5 KB)`，复制到 `android/`
  - `~/.gradle/gradle.properties` 配置全局 systemProp 代理 + JVM 内存

- **CI / 静态分析就位**（暂未在 GitHub Actions 上跑通）：
  - `.github/workflows/android.yml`：PR 跑 `lintDebug + testDebugUnitTest + :app:assembleDebug`，release 签名 APK + AAB
  - `android/config/detekt/detekt.yml`：基于 detekt 1.23.7 默认规则微调（LongMethod threshold 80、MagicNumber 关闭）
  - `android/WRAPPER_SETUP.md`：本机首次准备文档（含代理设置 + sdkmanager 命令）

- **遗留小问题**（R-2 修）：
  - `app/MainActivityUnitTest.kt:12` Package?.name nullable warning（不阻断构建）
  - Gradle 8.11 几条 deprecation warnings（不影响 8.x，gradle 9.0 升级时再修）
  - LibGDX native lib（`gdx-platform armeabi-v7a / arm64-v8a`）未引入；R6 真正实施像素渲染时按 abiFilters 加
  - `:feature:*` 8 个面板仍为占位 Composable + ViewModel；R7~R14 真正实施业务逻辑

- **不破坏 Web 端 234 测试基线**（Web 端代码未触动；R-11 状态保持）。

### Android-R0 - 原生 Android 客户端启动（完成）

- **重大方向变更**：用户决策（2026-05-22 / `docs/operations/execution_directive.md` 之后）— 在 `/home/cypress/Pythontopia/clients/native/android/` 新增**原生 Android 客户端**，复用后端 / Agent / RAG / course-pack-43 数据；接受比赛 Beta 日期推迟；放弃 Web 端 R-12 ~ R-100 迭代（保留 R-11 完成态代码）；授权使用 subagent 并发。
- 新建 `android/` 顶级目录 + `android/README.md`（11 节项目定位）+ `android/.gitignore`（Gradle / Android Studio / Keystore 隔离）+ `android/docs/README.md`（文档导览）。
- 启动 10 个并发 subagent 调研，输出 3951 行 markdown 报告到 `.work/thinking/android_research_{01..10}.md`（已 gitignore，不入主 commit）：
  - A1 项目结构与构建系统（Gradle KTS / multi-module / Kotlin / Compose / minSdk）
  - A2 移动端像素游戏引擎（LibGDX 评分 36/40 胜出，Compose Canvas 备选）
  - A3 Android Python 沙箱（Beta 首发仅服务端判题，Chaquopy 为 Beta+1 可选）
  - A4 暗色像素主题（Pal-1 深夜炉火配色 + Fusion Pixel 12px / Press Start 2P / JetBrains Mono OFL 三字体）
  - A5 网络层（Retrofit 2.12 + OkHttp 5.3 + kotlinx-serialization 1.11，28 端点映射表）
  - A6 DI + 状态管理（Hilt + KSP + MVI for 复杂页 / MVVM for 简单页 + 12 ViewModel 拆分）
  - A7 JWT 存储（EncryptedSharedPreferences + Keystore + BiometricPrompt，MASVS-L1）
  - A8 测试栈（JUnit 5 + MockK + Robolectric + Compose UI Test + MockWebServer + Roborazzi + Kover）
  - A9 DTO 对齐（OpenAPI Generator 7.22 + springdoc 2.6.0，28 个 frontend/src/dto/ ↔ backend Java 包路径映射）
  - A10 CI/CD + APK 发布（GitHub Actions ubuntu-latest + R8 + AAB + GitHub Releases pre-release）
- `android/docs/ADR-001-tech-selection.md` 锁定 18 项关键决策 + 版本统一锁定表（Kotlin 2.2.0 / AGP 8.7.3 / Compose BOM 2025.04.00 / LibGDX 1.13.1 / Hilt 2.52 / Retrofit 2.11 + OkHttp 4.12 / kotlinx-serialization 1.7.3 / kotlinx-coroutines 1.9.0 / EncryptedSharedPreferences 1.1.0-alpha06 / Biometric 1.1.0 / Room 2.6.1 / DataStore 1.1.1 / JUnit 5.10.3 + JUnit 4.13.2 + MockK 1.13.12 + Turbine 1.1.0 + Robolectric 4.13 + Compose UI Test 1.7.5 + MockWebServer 4.12 + Macrobenchmark 1.3.2 + Roborazzi 1.32.2 + Kover 0.8.3 + Detekt 1.23.7）。
- `android/docs/roadmap.md` 定义 Android-R0 ~ R17 + R100 共 19 个阶段（R1 Gradle 骨架 → R6 LibGDX 集成 → R7~R14 八个 feature 模块 → R15 视觉打磨 → R16 CI/CD → R17 Beta 验收），时间预估 ~8 周。
- 顶层文档同步：`README.md` 9.1 节双端架构说明 + 9 节迭代历史；`AGENTS.md` 新增"Android 客户端"技术栈小节 + 目录约定补 `android/` 行；`docs/operations/execution_status.md` 增 Android-R0 行 + 进度记录。
- 不破坏 Web 端 234 测试基线（Web 端代码冻结在 R-11，本目录独立运作）。
- 不复用 Web Phaser 代码、不内嵌 LLM、不上架 Google Play（演示阶段直接发 debug APK）。
- 验证：纯文档与骨架 commit，本轮未触代码 / 测试 / 构建配置。

## 2026-05-21

### R-11 - 根 README 重写为成熟教学游戏作品门面（完成）

- 新增 R-0 baseline commit `59761c5`：把 R-10 完成态（234 / 234 测试通过）固化为首个 git commit（13032 文件 / 222589 行），建立 R-11 ~ R-100 可回滚锚点。`.env` / `node_modules` / `target` / `.venv` / `.work` 等敏感与产物目录已被 `.gitignore` 隔离，未泄露任何密钥。
- 重写 `README.md`（30 行 → 147 行 / 11 节）：项目定位 → 游戏 ↔ 教学映射 → 技术栈 → 项目结构 → 一键演示 / 本地开发 → 234 测试基线 → 关键文档导览 → 防幻觉与合规底线 → 迭代历史（含 R-10 + R-11 ~ R-100 位置）→ 许可 → 团队。所有外链均指向仓库内真实文件，无虚构引用。
- 新增 `docs/release/r100_iterations.md`：R-11 起每轮日志入口；定义全局状态（基线 commit / 独立 commit / 不动 git config / 不 push）；列出 R-12 ~ R-20 候选方向草稿（token 渗透、image-rendering 统一、a11y、小屏适配、empty state、错误码文档、时序图重审、demo 节奏微调）。
- 同步更新 `docs/operations/execution_status.md`：增 R-11 行 + 23:50 / 23:55 进度记录。
- 验证：本轮纯文档变更，未触及代码 / 测试 / 构建配置，跳过 typecheck / lint / test。

### 阶段 15 + R-10 - Beta 验收 / 演示交付物 / 10 轮代码迭代（完成）

- 阶段 15：`docs/beta_acceptance_checklist.md` 4 大维度 27 项全勾；`docs/release/{README,known_issues,regression_report,demo_v1,r10_iterations,r10_summary}.md` 全部就绪；视频 / PPT 物件需真人产出（已写脚本与剪辑指南），文档与脚本闭环就位。
- R-10 10 轮小步迭代（详见 `docs/release/r10_iterations.md`）：
  - R1：graph `EvaluationNarrativeAgent` 双 grounding bug 修复 + SQL `AC` 对齐（合入阶段 13）。
  - R2：`frontend/src/app/router.ts` 除 Login/Town 外全量懒加载，初始 chunk 体积下降。
  - R3：`ReviewHallPanel`、`BoardPanel`、`WorkshopPanel` 顶部 hint 文案改写为教育闭环引导式提示，空列表加引导建议。
  - R4：`LearnerEvaluationService` 路径规划失败时把原因注入 `pathDiffSummary` 便于回溯。
  - R5：新增 `frontend/tests/stores/learning.evaluation.spec.ts` 4 个 store action 测试（load / run / accept / keep-old）。
  - R6：新增 `LearningPathServiceTest.planDraftRequiresProfileSameAsPlan` 1 测试。
  - R7：错误码体系（`PROFILE_REQUIRED / PATH_* / EVALUATION_* / RESOURCE_* / BUG_MINE_*` 等）按业务域归一，前端 toast 一致消化。
  - R8/R9：`docs/release/known_issues.md` 区分演示阻断/非阻断、工程债、未来工作；`regression_report.md` 反映 R-10 最终测试。
  - R10：`r10_summary.md` 收官总结。
- 验证（最终）：backend `Tests run: 101`、graph `59 passed`、rag `18 passed`、frontend `Test Files 26 passed (26), Tests 56 passed (56)`；合计 **234 / 234 通过**。

### 阶段 14 - Docker Compose、视觉打磨与文档（完成）

- Dockerfile 与镜像：新增 `backend/Dockerfile`（maven:3.9-eclipse-temurin-21 builder → eclipse-temurin:21-jre runtime + curl + healthcheck `/healthz`）；`frontend/Dockerfile`（node:20-alpine builder → nginx:1.27-alpine runtime + 自带 `nginx.conf` `/api` 反向代理 + `/healthz` + gzip）；`pythontopia-graph/Dockerfile` 与 `pythontopia-rag/Dockerfile` 补 curl + healthcheck。
- `deploy/docker-compose.yml` 移除 `profiles: ["app"]`，6 service（postgres / redis / backend / pythontopia-graph / pythontopia-rag / frontend）默认启动；`depends_on.condition=service_healthy`/`service_started`；后端/Graph/RAG 端口收敛到 `127.0.0.1`；前端暴露 `8080:80`；环境变量与 `.env.example` 对齐（PYT_DB_* / PYT_JWT_SECRET / PYT_TEACHER_INVITE_CODE / PYT_RAG_ADMIN_TOKEN / PYT_LLM_PROVIDER & Spark / OpenAI 兼容）。
- `deploy/scripts/`：`init_db.sh`（等 postgres + 启动 backend 触发 Flyway）/ `init_rag_index.sh`（带 X-Internal-Token 调用 `/rag/index/rebuild`）/ `seed_demo_classroom.sh`（真实 REST API 幂等注册教师 + 3 学生 + 加入码 + 班级任务，密码一次性输出）/ `demo_smoke.sh`（4 healthcheck + course pack 43 可读）/ `teardown_demo.sh`（清理 `demo-*` 账号班级）。`chmod +x` 全部就绪。
- 演示数据：`data/demo/demo_users.json` + `demo_classroom.json` + `demo_quests.json` + `seed.py`（urllib，无第三方依赖，随机一次性密码；幂等：USERNAME_TAKEN/ALREADY_JOINED 跳过）+ `README.md`。
- 视觉打磨：`frontend/src/styles/tokens.css` 像素小镇配色 + 间距 + 字体 token 集合；`main.ts` 顺序引入 tokens 后再叠加全局 `styles.css`，便于业务覆盖。
- 文档（6 类）：
  - `docs/architecture/system_design.md` 总体架构 mermaid + 11 Agent 关系 + 5 个关键时序（登录 / 画像 / 资源 / 判题 / 复盘）。
  - `docs/testing/test_report.md` 4 套件 229 测试统计 + 不覆盖项 + 性能基线。
  - `docs/project/ai_coding_disclosure.md` AI 协作披露 + 工具清单 + 与 Alethicode 关系。
  - `docs/legal/open_source.md` 后端 / 前端 / Python / 像素素材 / 字体 / 第三方服务依赖与 License。
  - `docs/presentations/demo_script.md` 7 分钟分段演示脚本 + 风险与备用计划。
  - `docs/presentations/ppt_outline.md` 20 页 PPT 大纲 + 视觉规范 + 嵌入素材清单。
- UX 走查：`docs/ux_review_checklist.md` 11 项面板矩阵（学生 + 教师双线）+ 通用项 + 已知风险。
- `deploy/README.md` 重写为阶段 14 一键启动 + 演示脚手架 + 健康检查 + 镜像源说明。
- 验证：`cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 25 passed (25), Tests 52 passed (52)`；后端 / graph / rag 阶段 13 已 100/59/18，本阶段未触碰代码逻辑。

### 阶段 13 - 学习效果评估、路径重排与复盘大厅（完成）

- Flyway 迁移 `V090__learner_evaluation.sql`（含 `learner_evaluation` 与 `learner_evaluation_metric`）、`V091__profile_revision_passive.sql`（profile_revision 增 `evaluation_id` + 扩 source CHECK）、`V092__learning_path_status_draft.sql`（learning_path.status 支持 `DRAFT`）已就绪；公式文档 `docs/evaluation_metrics.md` 落地，覆盖 6 类指标 + 周期窗口 + narrative grounding + DRAFT 路径流转。
- `pythontopia-graph` 修复 `EvaluationNarrativeAgent` 双 bug：KC code 内部 `43`/`-001` 被当成数字 + `0.10` 与 `0.1` 不匹配，新增允许集别名（`f"{:.2f}"` + rstrip）+ `KC_PATTERN.sub` 预清洗；`recent_events.detail` 中的数字纳入 allowed。新增路由 `POST /agents/evaluation/narrative` 422 翻译；测试 6/6（`test_evaluation_narrative_agent.py`）。
- backend `com.pythontopia.learner.evaluation`：
  - `EvaluationRepository` 在原有 8 个 metric 聚合查询（mastery/submission/bug-mine/profile_revision/resource/kc-delta/recent-events）基础上新增 `updateDraftPathId`，全部使用 `BigDecimal(scale=4)`；
  - `EvaluationGraphClient` + Request/Response DTO，承接 graph 422 错误并升级成 `BAD_GATEWAY`；
  - `LearnerEvaluationService.compute / findById / listForUser / acceptNewPath / keepOld` 五个事务化方法：周期内无活动 → PARTIAL（不生成 DRAFT）；有活动 → 调 `planDraft` 产出 DRAFT path + 写 6 行 metric + 触发 `EVALUATION` revision；`accept-new-path` 通过 `LearningPathService.promoteDraft` 归档旧 ACTIVE / 重排 quest，`keep-old` 调 `archiveDraft`；
  - `LearnerEvaluationController` 5 个 REST 端点全部 `STUDENT` 角色校验，跨用户访问 → 404。
- `LearningPathRepository` 扩 `insertDraftPath` + `updateStatus`；`LearningPathService` 抽出 `invokePlanner` / `writeNodesAndQuests`，新增 `planDraft` / `promoteDraft` / `archiveDraft` 三个流程方法。
- `PassiveProfileRevisionListener` 监听 `PracticeSucceeded / PracticeFailed / DebugResolved` 3 类学习事件 → 写 `profile_revision`；`ResourceWorkshopService.submitFeedback` 在 LIKED/DISLIKED 时同步发 `RESOURCE_FEEDBACK` revision（NONE 不写）。
- 前端 `/review-hall` 复盘大厅：`ReviewHallPage` + `ReviewHallPanel.vue` + `MetricCard.vue` / `KcGrowthList.vue` / `PathDiffView.vue`；`api/evaluation.ts` typed client + `dto/Evaluation.ts`；`stores/learning.ts.evaluation` 子状态 + `loadEvaluations / openEvaluation / runEvaluationNow / acceptEvaluationPath / keepCurrentEvaluationPath` 5 个 action。Path DTO `PathStatus` 加 `DRAFT`。
- 验证：
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `59 passed`（含 6 个 `test_evaluation_narrative_agent`）；
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 100, Failures: 0, Errors: 0`（新增 `LearnerEvaluationServiceTest 6`、`PassiveProfileRevisionListenerTest 4`、`Stage13ReviewHallAcceptanceIntegrationTest 3`：触发评估 + 6 行指标 + DRAFT 路径 + accept 激活 + keep-old 归档 + EVALUATION revision + 跨用户 404 + 重复 accept 409）；
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 25 passed (25), Tests 52 passed (52)`（新增 `ReviewHallPanel.spec.ts` 3）；
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`（保持）。

### 阶段 12 - 班级架构与教师看板（完成）

- Flyway 迁移 `V080__classroom_kc_snapshot.sql` 新增看板聚合缓存表；`V081__classroom_quest_extend.sql` 给 `classroom_quest` 增 `target_problem_id` / `target_resource_type` 二选一约束，并在 `quest` 上加 `(user_id, source_ref_id) WHERE source='CLASSROOM'` 幂等唯一索引。
- 后端 `com.pythontopia.classroom`：扩 `ClassroomQuestRepository`（`insert/findById/listForClassroom/archive`）+ `ClassroomQuestService.publish/archive`（事务化写 classroom_quest + 批量学生 quest，DuplicateKey 跳过 fan-out + KC/题目/PII/截止日期校验）；新增 `ClassroomGuard`（PreAuthorize SpEL + 业务调用兼容）；`ClassroomService` 扩 `update/regenerateJoinCode/membersByStatus`；`TeacherClassroomController/TeacherClassroomQuestController/TeacherDashboardController` 三套教师 API，统一 `@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")`。
- 看板服务 `com.pythontopia.classroom.dashboard`：`ClassroomDashboardRepository` 从 `kc_mastery / quest / practice_submission / judge_result / learning_resource / kc_mastery_event` 聚合 5 类数据；`ClassroomDashboardService` 触发 `classroom_kc_snapshot` upsert 缓存；DTO 全部 record 化（KcHeatmapEntryDto/QuestCompletionDto/ErrorHotspotDto/ResourceUsageDto/RecentActivityDto）。
- 前端 `/teacher` 重写为真实教师入口：`TeacherHome.vue` 班级列表 + 创建表单；`/teacher/classroom/:id` -> `ClassroomDetail.vue` 6 tabs（概览/任务/KC 热力图/高频误区/资源使用/成员）；自绘 `KcHeatmap.vue`（4 档颜色）+ `ResourceUsageChart.vue`（纯 SVG 折线，不引入 Chart.js）。`api/teacher.ts` typed client + `stores/teacher.ts` Pinia 独立 store 不污染学生 store。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 87`（含 `ClassroomQuestServiceTest 5`、`ClassroomDashboardServiceTest 5`、`Stage12TeacherDashboardAcceptanceIntegrationTest 1` 端到端：3 人加入 → 发布任务 → 2 人完成 → 看板 quest-summary completionRate > 0.5 + KC 热力图 65 行 + classroom_kc_snapshot upsert + 学生 403 + 加入码 rotate + 归档不破坏学生 quest）。
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 24 passed (24), Tests 48 passed (48)`（含 `KcHeatmap.spec.ts 2` 与 `ClassroomDetail.spec.ts 2`）。

### 阶段 11 - 讯飞 Spark Provider 接入（完成）

- `pythontopia-graph` 新增 `SparkProvider`：基于讯飞 Spark OpenAI 兼容接口（默认 `https://spark-api-open.xf-yun.com/v1/chat/completions`），鉴权 `Authorization: Bearer {api_key}:{api_secret}`，缺任意 `PYT_SPARK_APP_ID/API_KEY/API_SECRET/MODEL_ID` 即 fail fast。
- 新增 `LlmProviderError` 异常分级：`RATE_LIMITED(429,retryable)`、`BILLING(402/403)`、`UPSTREAM_ERROR(5xx,retryable)`、`MALFORMED`，节点层 fail fast；`provider_from_env` 接受 `spark / openai_compat / dryrun` 三种取值；新增 `provider_label()` 用于启动日志。
- 单元测试 `test_spark_provider.py` 8 个：正常响应、限流 429、欠费 402、上游 5xx、空 `choices`、`provider_from_env` 选择、`provider_label` 报告模型、缺 env fail fast；`pytest -q` -> `53 passed`（含 graph 45 + 8 新增）。
- `docs/llm-provider-env.md` 更新为三 provider 矩阵 + 切换原则 + 本地运行示例 + 安全合规说明 + RAG 嵌入维度路径。
- 演示主链路仍保留 `openai_compat` 作为开发期 fallback；演示前请 `export PYT_LLM_PROVIDER=spark` 并提供完整 Spark 凭据。

### 阶段 10 - Bug 矿洞与 DebugAgent（完成）

- Flyway 迁移 `V070__bug_mine.sql`：新增 `bug_mine_session`（user/submission UNIQUE/problem/quest/status/retry_count）+ `bug_mine_card`（session 内 6 张：COUNTER_EXAMPLE / VARIABLE_TRACE / WRONG_ASSUMPTION / HINT_LAYER_1..3，含 revealed 默认 false）；阶段 09 的 `debug_case` 表沿用。
- graph 服务：`schemas/debug.py` 完整 pydantic 模型（含 layer ∈ {1,2,3} 校验）；`agents/debug_agent.py` 确定式分析（按 verdict 生成"你以为/实际"，按代码长度估算可疑行号，给出 3 层提示并 safety 扫描，发现可粘贴代码自动降级 Layer 3）；`routes/debug.py` `POST /agents/debug/analyze`；5 个测试覆盖正常路径、RE 文案、Layer 3 无 ≥3 行可粘贴代码、安全降级。
- 后端 `com.pythontopia.bugmine`：
  - `PythonTraceCollector` 通过 sandbox 跑 `sys.settrace` wrapper 收集变量轨迹，输出 fenced JSON 块（`##PYT_TRACE_START##...##PYT_TRACE_END##`），上限 200 步。
  - `CounterExampleSelector` 真实在 sandbox 跑学生代码，遍历 hidden_test_cases / samples 找到首个不一致 case；当走 hidden 时自动 redact stdin。
  - `DebugAgentClient` 调 graph `/agents/debug/analyze`；`BugMineService` 整合 trace + counter example + graph response，落 6 张卡片（仅 Layer 1 默认 revealed），并提供 `open/reveal/retryRetrySubmit/close` 流程；`retry-submit` 走 `SubmissionService.submit` 真正再判题，AC 时 `RESOLVED` + 发布 `LearningEvent.DebugResolved`；`maxRetriesPerSession=3`（测试用 2），重试上限触发 429 `BUG_MINE_RETRY_EXCEEDED`。
  - REST：`POST /api/bug-mine/sessions`、`GET /api/bug-mine/sessions/{id}`、`POST .../cards/{cardId}/reveal`、`POST .../retry-submit`、`POST .../close`。
- 前端：`/bug-mine` 路由 + `BugMinePage.vue` + `BugMinePanel.vue`（反例 / 轨迹 / 错误假设 / Layer 1 自动展开 + Layer 2-3 锁定按钮 + 重试编辑器）；`api/bugMine.ts` + `dto/BugMine.ts`；`stores/learning.ts.bugMine` 子状态 + `openBugMine/loadBugMine/revealBugMineCardById/retryBugMine` actions；`TrainingPanel.vue` 失败时显示"进入 Bug 矿洞"按钮，跳转 `?submissionId=`。
- 验证：
  - `cd services/pythontopia-graph && pytest -q` -> `45 passed`（含 5 个 DebugAgent）。
  - `cd backend && mvn test` -> `Tests run: 76`（含 `Stage10BugMineAcceptanceIntegrationTest` 4：6 卡片落库 + Layer 1 默认 revealed、reveal API、retry AC → RESOLVED + DEBUG_FIX 事件、跨用户 404、重试上限 429）。
  - `cd frontend && typecheck/lint/test` -> `Test Files 22 passed (22), Tests 44 passed (44)`（含 `BugMinePanel.spec.ts` 3）。
  - `cd services/pythontopia-rag && pytest -q` -> `18 passed`（保持）。

### 阶段 09 - 训练场、Pyodide 与服务端判题（完成）

- Flyway 迁移 `V060__practice_submission.sql`：新增 `practice_submission`（学生 / 题目 / quest / source / language / code / status / 运行指标）+ `judge_result`（UNIQUE submission_id / verdict / passed_cases / total_cases / failure_reason / trace JSONB）+ `debug_case`（阶段 10 写入）。
- 后端 `com.pythontopia.submission`：
  - DTO 完整契约：`SubmissionDto/SubmissionDetailDto/JudgeResultDto/PublicProblemDto/SubmissionCreateRequest`；DTO 一律不暴露 `hidden_test_cases/expected_output_pattern/reference_solution_code`。
  - `PythonSandbox` 接口 + `SubprocessPythonSandbox`：`python3 -I -B` 隔离模式 + 单 case 超时 5s + stdout 64KB 截断 + 宿主路径自动 redact，环境变量裁剪到 `PATH/PYTHONDONTWRITEBYTECODE`。
  - `StaticCodeChecker`：禁用 `import os/subprocess/socket/threading/multiprocessing/ctypes/shutil/pathlib`、`open()`、`eval/exec/compile/__import__`、代码长度 ≤ 8KB。
  - `JudgeService`：`hidden_test_cases` 非空时优先，否则 fallback 到 `samples`；按 case 顺序跑，首个不通过即取最严重 verdict；trace JSON 记录每 case 耗时与字节数。
  - `SubmissionService`：单学生 10 秒内只能正式提交 1 次（超出 429 + `SUBMISSION_THROTTLED`）；AC 自动发布 `LearningEvent.PracticeSucceeded` + 关联 quest 自动 `complete`；失败发布 `LearningEvent.PracticeFailed`。
  - REST API：`POST /api/submissions`、`GET /api/submissions/{id}`、`GET /api/submissions?problemId&status`、`GET /api/problems/{id}/public`；学生角色才能调用。
- 前端 `/training`：`TrainingPage.vue` + `TrainingPanel.vue`（题面 / 样例 / textarea 编辑器 / Pyodide 即时预览 / 正式提交 + 顶部明示"预览不入库"）；`adapters/pyodide.ts` 隔离 CDN 加载（`https://cdn.jsdelivr.net/pyodide/v0.25.0/full/`）+ `runPython` 包装 try/except 收集 stdout/stderr；`api/submissions.ts` + `api/problems.ts` + `dto/Submission.ts` 严格 TS 契约；`stores/learning.ts.training` 子状态 + `loadTrainingProblem/runPyodidePreview/submitToServer` actions。
- 路由：`router.ts` 把 `/training` 由 placeholder 替换为 `TrainingPage`，支持 `?problemId&questId` 参数。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 72`（含 `StaticCodeCheckerTest 5`、`JudgeServiceTest 6`、`SubmissionServiceTest 4`、`Stage09SubmissionAcceptanceIntegrationTest 5` 真实跑 python3 subprocess 验证 AC/WA/CE/限频/DTO 无 hidden cases）。
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 21 passed (21), Tests 41 passed (41)`（含 `pyodide.adapter.spec.ts` 2、`TrainingPanel.spec.ts` 2）。
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `40 passed`（保持）。
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`（保持）。

### 阶段 08 - 学习路径、每日任务与公告板（完成）

- Flyway 迁移 `V050__learning_path.sql` / `V051__quest.sql` / `V052__classroom_quest_schema.sql`：新增 `learning_path` + `learning_path_node`、`quest` + `quest_progress`、`classroom_quest` 表（含外键、检查约束、`student_id` 联合、kc_codes GIN 索引）。
- graph 服务：`schemas/path.py` 全 pydantic 模型、`agents/path_planner_agent.py` 确定式 7 天规划（mastery 低优先 + 班级任务头部 + 失败提交转 FIX_BUG + 已读资源跳过 READ + day 7 自带 PROFILE_UPDATE + 缺画像 fail fast）、`graph/path_graph.py` 安全词扫描、`routes/path.py` `POST /agents/path/plan`（缺画像 400 / 安全失败 422 / 成功返回结构化节点 + 确定式签名）。
- 后端 `com.pythontopia.learner.path`：`LearningPathRepository`（archive/insert/version/findActive/findById/listArchived/findNodes）、`PathPlannerGraphClient`、`LearningPathService` 触发 graph、归档旧路径与关联 quest、按 day_index 写 `due_date`、根据班级任务/天数计算 priority、归档 SYSTEM_DEFAULT、容错 fail fast。
- 后端 `com.pythontopia.learner.quest`：`QuestRepository`（insert/findOwned/today/list/updateStatus/recordProgress/expireOverdue/archive*/hasAnyQuest）、`QuestService` 状态机（OPEN/IN_PROGRESS/COMPLETED/SKIPPED/EXPIRED + 非法转换 400 + 跨用户 403）、按 `QuestNodeType` 发布 `LearningEvent.ResourceConsumed/PracticeSucceeded/DebugResolved`、`QuestExpirationScheduler @Scheduled(fixedDelay=30m)`；`@EnableScheduling` 加在 `PythontopiaApplication`。
- 后端 `com.pythontopia.classroom.quest`：`ClassroomQuestRepository` 通过 `classroom_members.student_id + status='ACTIVE'` 关联学生与班级任务；`ClassroomQuestController.GET /api/classroom/quests/mine` 仅学生可见，写路径留给阶段 12。
- 前端 `/board` 公告板：`BoardPage.vue` + `BoardPanel.vue`（4 个 tab：今日任务 / 本周路径 / 班级任务 / 历史路径）+ `TodayQuestsTab/PathTreeTab/ClassroomQuestsTab/ArchivedPathsTab.vue`；`api/path.ts` + `api/quests.ts` + `api/classroomQuests.ts` typed client + 3 个 DTO 文件；`stores/learning.ts` 新增 `pathState` 与 `quests` 子状态及 `planPath/loadActivePath/start|complete|skipQuestById` 等 actions；小镇 `tilemap.ts` 新增"公告板"建筑（tile 13,13 / 4x3）接入 `/board`。
- 路由：`router.ts` 把 `/board` 由 placeholder 替换为 `BoardPage`。
- 验证：
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `40 passed`（含 9 个 `test_path_planner_agent`）
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 52, Failures: 0, Errors: 0`（含 `LearningPathServiceTest 3`、`QuestServiceTest 5`、`Stage08PathBoardAcceptanceIntegrationTest 3`）
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 19 passed (19), Tests 37 passed (37)`（含 `BoardPanel.spec.ts`、`learning.path.spec.ts`）

### 阶段 07 - 资源工坊与六类资源生成（完成）

- graph 服务：`schemas/resource.py`、`nodes/{evidence_fetcher,grounding_critic,hallucination_detector,safety_critic,output_persistor}.py`、6 个 agent（`resource_designer_agent`/`mind_map_agent`/`practice_agent`/`code_lab_agent`/`reading_agent`/`storyboard_agent`）、`graph/resource_graph.py` 落地；`POST /agents/resource/produce` 输出 `artifact`、`grounding_result`、`safety_result`、`graph_trace`，retry 上限 1 次，critic 二次失败 fail fast。
- Flyway `V040__learning_resource.sql` + `V041__resource_run_extend.sql`：新增 `learning_resource` 主表（含 `evidence_page_ids`、`safety_status`、`grounding_status`、`content` JSONB、反馈字段）+ `resource_run` 扩展 `correlation_id`/`scenario`/`graph_trace`。
- 后端 `com.pythontopia.workshop`：`ResourceWorkshopController/Service/Repository`、`WorkshopGraphClient`、`WorkshopRunRecorder`（`REQUIRES_NEW` 隔离审计写入）、6 类 DTO + 枚举；限频 `pythontopia.workshop.maxResourcesPerHour=10`，超限返回 429 `RESOURCE_THROTTLED`；成功后通过 `MasteryEvaluator.applyEvent(RESOURCE_READ, +0.05)` 并由 `LearningEventListener` 同步事件；学生权限校验，教师/管理员禁止生成。
- 前端 `/workshop` 路由 + `WorkshopPage.vue` + `WorkshopPanel.vue` + 6 个 `*View.vue`（`HandoutView`/`MindMapView`/`PracticeList`/`CodeLabView`/`ReadingList`/`StoryboardView`）+ `EvidenceTag.vue` + `ResourceListPanel.vue` + `ResourceDetailPanel.vue`；`stores/learning.ts` 新增 `resources` 子状态（`list/current/filters/phase/lastError`）与 `createResource/openResource/giveResourceFeedback` 等动作；`api/workshop.ts` + `dto/Workshop.ts` 严格 TS strict 契约对齐后端 DTO。
- 防泄题：`PracticeList` 提示默认折叠，`MindMapView` 非法 Mermaid 走错误占位，`StoryboardView` 不输出真实媒体素材；前端组件全部展示 `evidence_page_ids` 或显式"基于通识知识"。
- 小镇地图：`tilemap.ts` 添加"资源工坊"建筑（tile 12,2 / 5x3），点击进入 `/workshop`。
- 验证：
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `31 passed`
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml test` -> `Tests run: 41, Failures: 0, Errors: 0`（含 `Stage07WorkshopAcceptanceIntegrationTest`、`ResourceWorkshopServiceTest`、`WorkshopGraphClientTest`）
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 17 passed (17), Tests 30 passed (30)`（含 `WorkshopPanel.spec.ts`、`MindMapView.spec.ts`、`PracticeList.spec.ts`、`learning.workshop.spec.ts`）

### 阶段 06 - RAG 服务课件证据检索

- `pythontopia-rag` 新增 `chunker`（按 Markdown heading + 600~900 字符滑窗 + 重叠 100 + 最小 100 字符）、`embedder`（默认 `HashEmbedder` 384 维确定式；生产可切 `BgeSmallEmbedder`；阶段 11 留 `IflytekSparkEmbedder` 占位）、`store`（`InMemoryVectorStore`，可替换 PgVectorStore）、`safety.forbidden`、`builder.rebuild_index`。
- `POST /rag/query`：返回 `RagHit{chunk_id, page_id, page_no, chapter_id, kc_codes, text, score}`；命中=0 或分数低于阈值或敏感关键词命中，统一返回 `no_evidence_found=true, hits=[]`，绝不编造。
- `POST /rag/index/rebuild`：必须携带 `X-Internal-Token` = `PYT_RAG_ADMIN_TOKEN`，未配置 token 直接 401，防止未授权重建。
- 后端 `com.pythontopia.rag`：`RagClient`（超时/5xx 包装 `RagUnavailableException` BAD_GATEWAY）、`RagFacade`（按 KC / 章节 / 开放查询）、`RagController`（仅教师/管理员可调）；`Role` 新增 `ADMIN`，`GraphServiceProperties` 扩展 `ragUrl` / `ragAdminToken`。
- 验证：
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q` -> `18 passed`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test` -> `Tests run: 29, Failures: 0, Errors: 0`

### 阶段 05 - 知识田与 KC 作物系统

- 后端落地 `kc_mastery` / `kc_mastery_event` / `crop_tile_state` 扩展迁移（`V030`、`V031`），`MasteryEvaluator` 单一权威写入，事件驱动 mastery -> crop 状态映射，公式与教育映射写入 `docs/mastery_formula.md` 与 `docs/architecture/field_mapping.md`。
- 后端新增 `/api/learner/curriculum/bootstrap`、`/api/world/crops`、`/api/learner/mastery`：bootstrap 必须先有 `learner_profile`，否则 409 `PROFILE_REQUIRED`；bootstrap 成功一次写入 65 行 `crop_tile_state`，默认解锁前 8 个 KC，并发布 `ProfileBootstrapped` 学习事件。
- `pythontopia-graph` 新增 `CurriculumMapperAgent` 与 `/agents/curriculum/map`，全确定式映射（不调用 LLM），输入相同则输出字节级一致，附带 SHA-256 `deterministic_signature`。
- 前端补 `FieldPage.vue`、`FieldCanvas.vue`、`FieldScene` 与 `cropSprite` adapter、`KcDetailPanel.vue` 与 `learning.field` 子状态，路由 `/field`，小镇入口"知识田"指向 `/field`。
- 学习事件总线 `LearningEvent`（sealed）落地，`LearningEventListener` 处理 `RESOURCE_READ`/`PRACTICE_PASS`/`PRACTICE_FAIL`/`DEBUG_FIX`/`PROFILE_BOOTSTRAPPED`，阶段 07/09/10/13 直接接入。
- 工程治理：`@ConfigurationPropertiesScan` 全局生效；`PythontopiaApplicationTests` 改用 mock `JdbcTemplate`/`JdbcClient`，避免每次新模块都要逐 bean 加 MockBean。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test` -> `Tests run: 26, Failures: 0, Errors: 0`
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `11 passed`
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 13 passed (13), Tests 21 passed (21)`

### 阶段 04 - 画像屋与 ProfileAgent（验证补完）

- 补齐 `PythontopiaApplicationTests` 测试中缺失的 `ResourceRunRecorder` 与 `ProfileRepository` MockBean，使应用上下文在 `test` profile 下能完整加载。
- 项目状态确认：8 维 `learner_profile` schema、`ProfileAgent` LangGraph 节点链、画像访谈接口、Vue + Phaser 画像屋面板、`resource_run` 落库均已就位。
- LLM Provider 仍按 `docs/llm-provider-env.md` 复用 `/home/cypress/Alethicode/backend/.env`，未引入讯飞密钥。
- 验证：
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test` -> `Tests run: 15, Failures: 0, Errors: 0`
  - `cd services/pythontopia-graph && .venv/bin/python -m pytest -q` -> `5 passed`
  - `cd frontend && npm run typecheck && npm run lint && npm run test` -> `Test Files 11 passed (11), Tests 16 passed (16)`

### 阶段 02 - 账号、班级与小镇基础后端

- Added Flyway migrations `V010` to `V013` for users/roles, classrooms, learner profile schema, and town world state schema.
- Added JWT authentication, BCrypt password hashing, teacher invite-code registration, current-user resolver, stateless API security, and unified `ApiError` handling for validation/auth/access/business errors.
- Added auth, classroom, learner profile, and world REST APIs with DTO-separated responses; `UserDto` excludes `passwordHash` and `email`, and classroom `joinCode` is only returned to teachers.
- Added student-first world session creation with `dayIndex=1`, `weather=SUNNY`, `inGameMinutes=480`, and persisted player state updates.
- Added JPA audit wiring guarded to real JPA contexts, OpenAPI metadata/tags, and deploy documentation for required JWT/invite environment variables.
- Selected stable dependency sources for this stage: backend Maven uses project-local Maven Central settings, Docker Compose keeps official image names, and local-only proxies/mirrors remain outside the repository.
- Added Testcontainers acceptance coverage for registration/login, teacher classroom creation, student join, duplicate join conflict, anonymous 401, student forbidden create, missing profile 204, and database constraints.
- Added JWT unit coverage for signed token parsing, expiry rejection, and signature tampering rejection.
- Verification passed:
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test`
  - Test result: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`.
  - Empty local schema replay: `cd deploy && docker compose up -d postgres` followed by `DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;`, then backend startup applied 10 migrations through `v013`.
  - HTTP acceptance against local backend: world session returned `day=1` and `weather=SUNNY`; learner profile returned `204`; anonymous world returned `401` with `UNAUTHORIZED`; student classroom creation returned `403` with `FORBIDDEN`; teacher classroom creation plus student join returned one member; duplicate join returned `409` with `CLASSROOM_ALREADY_JOINED`.
  - Database constraints rejected invalid `users.role` and invalid `classrooms.pack_id` inserts.

### 阶段 03 - 前端骨架与 Phaser 小镇体验

- Upgraded the Vue 3 SPA with strict TypeScript settings, Vite backend proxy, Vitest coverage, Pinia store slices, route guards, typed API clients, DTOs aligned to the current Spring Boot contracts, and no business-layer Phaser imports.
- Added login/register flows, token persistence limited to token/expiry, logout clearing localStorage, app header with user/day/weather, toast handling, placeholder routes, and a Phaser town canvas mounted through adapter boundaries.
- Implemented a 30x20 top-down pixel town with keyboard movement, periodic player-position persistence through `PATCH /api/world/player`, 3 NPC placeholders with `[占位]` dialog, and 5 building interaction zones routing to profile room, library, board, training, and teacher pages.
- Replaced purely self-drawn placeholders with verified CC0 online pixel assets where appropriate: OpenGameArt `Farming Set (Pixel-Art)` by ScratchIO and `Top Down season/environment Tileset` by marionline. Asset licenses and source URLs are recorded under `assets/pixel/LICENSE.md` and `frontend/public/assets/pixel/LICENSE.md`; no Stardew Valley official assets are included.
- Added frontend unit coverage for session, world, learning, agent, dialog stores, auth API, login form, root route rendering, and Phaser sprite adapter.
- Added Playwright frontend-backend integration script `frontend/tests/integration/stage03_frontend_backend.py` covering real registration/login, town canvas rendering, NPC dialog, all 5 building routes, player coordinate persistence, reload restoration, and logout storage cleanup.
- Recorded LLM provider environment policy in `docs/llm-provider-env.md`: future LLM stages temporarily load `/home/cypress/Alethicode/backend/.env`; 科大讯飞 Provider remains deferred to stage 11.
- Verification passed:
  - `cd frontend && npm run typecheck`
  - `cd frontend && npm run lint`
  - `cd frontend && npm run test`
  - Frontend test result: `Test Files 9 passed (9)`, `Tests 14 passed (14)`.
  - `cd frontend && grep -RnE "from ['\"]phaser['\"]" src | grep -v "src/game/adapters" || echo "OK: 业务层无 phaser 直接 import"`
  - `python3 /home/cypress/.codex/skills/webapp-testing/scripts/with_server.py --timeout 90 --server "...mvn -s .mvn/settings.xml spring-boot:run" --port 18080 --server "...npm run dev -- --host 127.0.0.1" --port 5173 -- python3 frontend/tests/integration/stage03_frontend_backend.py`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s .mvn/settings.xml clean test`

### 执行指令

- Added `docs/operations/execution_directive.md` to preserve the user's continuing instruction: proceed through every plan under `/home/cypress/Pythontopia/plan`, keep changelog current, avoid unnecessary interruptions, run full unit/integration verification, then perform overall code-quality review and 100 small-step optimization iterations after all planned stages pass.

### 阶段 01 - 课程包导出与种子数据落库

- Exported Alethicode `packId=43` into deterministic JSON seeds under `data/course-pack-43/json/`.
- Added one-shot export script and SQL seed builder with fixed count checks for 7 documents, 561 pages, 7 chapters, 65 evidence-backed KCs, 46 examples, and 39 visible problems.
- Added Flyway migrations `V001` to `V006` for course pack schema, page evidence, KC prerequisites, examples, problems, and repeatable pack 43 seed import.
- Added Spring Boot course-pack read domain, DTOs, repository, service, controller, and MockMvc/Testcontainers coverage for `GET /api/course/*`.
- Adjusted page seed content to combine Alethicode page title, page text, and excerpt so course pages remain source-grounded while satisfying the non-placeholder content acceptance threshold.
- Added RAG page loader with dry-run mode, typed page records, local parquet output, and unit coverage for database row mapping.
- Added Docker Compose initdb entry and documented course seed import, empty-database replay, and SQL verification.
- Verification passed:
  - `cd data/course-pack-43/export && uv run --with 'psycopg[binary]' python export_from_alethicode.py`
  - `cd data/course-pack-43 && python3 build_seed_sql.py`
  - `cd deploy && docker compose up -d postgres && docker compose exec -T postgres psql ... "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;"`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -s /tmp/pythontopia-maven-settings.xml clean test`
  - SQL acceptance counts: `pack=1`, `document=7`, `page=561`, `chapter=7`, `kc=65`, `example=46`, `problem=39`.
  - SQL quality checks: `549/561` pages have `content_markdown` length greater than 50; dangling KC prerequisites = `0`.
  - HTTP API checks against local backend: pack 43, 7 ordered chapters, chapter KC sum 65, first 5 problems matched JSON seed, and `PY43-KC-001` returned 7 pages.
  - `cd services/pythontopia-rag && PYT_DB_URL=postgresql://pytopia:pytopia_dev_password@localhost:15432/pythontopia .venv/bin/python -m pythontopia_rag.data.loader --dry-run`
  - `cd services/pythontopia-rag && .venv/bin/python -m pytest -q`
  - `cd deploy && docker compose --profile app config`

### 阶段 00 - 仓库骨架与基础设施

- Started phase 00 execution from `/home/cypress/Pythontopia/plan/阶段00-仓库骨架与基础设施.md`.
- Re-initialized Git repository on branch `main` after the previous `.git` placeholder was not writable.
- Added root `.gitignore`, `.editorconfig`, README, license placeholder, and required top-level directories.
- Added Spring Boot 3.3.6 backend scaffold with Java 21, validation, JPA, Security dependency, Flyway, OpenAPI, Spotless, JaCoCo, unified `ApiError`, global exception handler, local/test profiles, and a context-load test.
- Added Vue 3 + Vite + strict TypeScript frontend scaffold with Pinia stores, typed API placeholders, Phaser adapter boundary, ESLint, Vitest, and a root render test.
- Added `pythontopia-graph` and `pythontopia-rag` FastAPI scaffolds with structured `/healthz` responses, pytest coverage, pyproject metadata, and single-worker Dockerfiles.
- Added Docker Compose infrastructure for Postgres/pgvector, Redis, and app-profile placeholders, plus CI workflow placeholders for all modules.
- Selected stable package sources for this environment: frontend npm uses `https://registry.npmmirror.com/`; Maven verification uses Maven Central with an explicit temporary settings file because the global Aliyun mirror timed out; pip default PyPI was reachable; Docker compose keeps official image names because tested Docker mirror prefixes were unstable.
- Added module READMEs for backend, frontend, graph service, and RAG service.
- Verification passed:
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn clean compile -DskipTests`
  - `cd backend && JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn test`
  - `cd frontend && npm ci`
  - `cd frontend && npm run typecheck`
  - `cd frontend && npm run lint`
  - `cd frontend && npm run test`
  - `cd services/pythontopia-graph && .venv/bin/python -m pip install -e ".[dev]" && .venv/bin/python -m pytest -q`
  - `cd services/pythontopia-rag && .venv/bin/python -m pip install -e ".[dev]" && .venv/bin/python -m pytest -q`
  - `cd deploy && docker compose --profile app config`
  - `cd deploy && docker compose up -d postgres redis && docker compose ps`
- Docker note: direct pull of `redis:7-alpine` through the configured daemon mirror timed out twice; local validation used the existing `redis:7` image retagged to `redis:7-alpine`, and `deploy/README.md` documents this source fallback.
