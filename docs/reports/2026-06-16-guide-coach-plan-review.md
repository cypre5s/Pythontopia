# AI 导学助手 Manager Agent Plan 批判性 Review

- Review 时间：2026-06-16（Asia/Shanghai）
- Review 对象：`docs/plans/2026-06-16-guide-coach-agent-education-agent-refactor.md`
- 目标：确认第一批实现边界，让 AI 导学助手变得更 agent，同时避免把稳定的判题/导学链路一次性改穿。

## 判断结论

计划方向成立：AI 导学助手不应只是 `AC -> 复盘`、`WA -> 错因分析` 的 if-else 状态机。更合理的形态是 bounded manager agent：

- deterministic gate 负责触发合法性、权限、安全边界、AC/WA 可用动作白名单；
- manager agent 负责在白名单内选择教育动作，例如题意解读、知识点复盘、执行轨迹、可视化、骨架、Parsons、AC 复盘、迁移练习；
- 每一步只产一张卡，且 AC/WA 后先主动说一句，等待学生点击继续；
- 输出结构化，保留审计摘要，不暴露 raw reasoning，不给完整答案或隐藏测试。

## 主要问题

1. **原计划任务切分过大**
   - Task 1-5 横跨 schema、agent、LangGraph 拓扑、guardrail、Java 持久化。
   - 如果第一批就接入主图和 DB，失败时很难判断是策略问题、卡片问题、SSE 问题还是投影问题。
   - 建议先独立证明 manager 决策层可测，再接图。

2. **“更 agent”不能只靠 LLM 决策**
   - 对 CE、AC、重复 WA 这类教育边界，确定性策略比 LLM 更可靠。
   - 真正的 agent 性来自“读取学生状态 + 工具白名单 + 教育目标 + 反馈回流”的决策闭环，而不是无条件问模型。
   - 因此第一批应保留 deterministic pre-rules，同时预留 LLM structured decision。

3. **Java 持久化要等 graph 结果稳定后再做**
   - `student_state`、`coach_decision` 的 JSON 结构如果边做边改，Flyway 和 Java DTO 会被频繁扰动。
   - 应先让 graph schema 稳定，再落迁移和 repository。

4. **前端 nudge 不应重复 SSE 逻辑**
   - TrainingPanel 只应该展示“主动一句话 + 继续按钮”。
   - 点击后应驱动既有 `TutorWorkflowPanel` 或其公开方法，而不是在 TrainingPanel 再写一套导学请求。

5. **guardrail critic 需要谨慎**
   - 防完整答案很重要，但纯启发式容易误伤骨架、执行轨迹、变量表。
   - 建议在 manager graph 接入后再加 critic，并先覆盖最明确的泄答场景。

## 第一批任务

已选择第一批任务如下，范围限定在 graph 服务：

1. **Coach schema**
   - 新增 `TutorStudentState`、`GuideCoachDecision`。
   - 扩展 `WorkflowRunRequest` / `WorkflowRunResult` 的 coach 字段，保持兼容。

2. **GuideCoachAgent 决策层**
   - 新增 `GuideCoachAgent` 与 `CoachContext`。
   - CE 优先错因诊断，WA 早期优先执行轨迹/错因，连续错误优先低负荷动作，AC 优先复盘再迁移。
   - LLM provider 只在非显然场景参与，且只能选择 `allowed_events`。

3. **Golden eval**
   - 固化 WA partial pass、CE、重复 WA、AC after retries 四类场景。
   - 验证不选白名单外动作、不选完整答案倾向动作、`hint_level <= 3`、`reason_summary` 非空且不含 raw reasoning 标记。

## 本批实现与验证

- 新增测试：
  - `services/pythontopia-graph/tests/test_tutor_workflow_coach_schema.py`
  - `services/pythontopia-graph/tests/test_guide_coach_agent.py`
  - `services/pythontopia-graph/tests/test_guide_coach_golden.py`
  - `services/pythontopia-graph/tests/fixtures/guide_coach_golden.json`
- 新增实现：
  - `services/pythontopia-graph/pythontopia_graph/agents/guide_coach_agent.py`
  - 扩展 `services/pythontopia-graph/pythontopia_graph/schemas/tutor_workflow.py`
- 验证：
  - 新增 coach 测试：13 passed。
  - 旧 tutor workflow + 新 coach 测试：23 passed。

## 下一批建议

下一批再进入 graph 主流程，任务顺序建议：

1. 在 `tutor_workflow_graph.py` 增加 `coach_decide` 节点，但保留 `coach_mode=false` 旧路径。
2. route result 透出 `coach_decision`、`student_state`、`agentic`。
3. 将 `available_actions` 按 coach 推荐排序，先只做 graph 测试。
4. 前端 `TutorWorkflowPanel` 只展示推荐动作，不改变 TrainingPanel 触发方式。
5. 最后再做 Java 持久化和 AC/WA nudge 卡。

## 第二批执行记录

第二批已进入 graph 主流程，但仍未触碰 Java DB 和前端 nudge：

1. **Coach 节点接入**
   - `TutorWorkflowGraph` 新增 `coach.decide` 节点。
   - `validate_transition` 仍先校验学生请求事件；coach 只在合法白名单中选择 `effective_event`。
   - `coach_mode=false` 保留旧路径。

2. **Effective Event**
   - 卡片生成、课件证据获取和结果事件使用 `effective_event`。
   - 典型场景：学生点击 WA 继续后，请求事件是 `ERROR_FEEDBACK`，coach 可选择 `EXECUTION_TRACE`，先让学生看见程序实际行为。

3. **推荐动作排序**
   - `available_actions` 支持 `recommended/reason`。
   - coach 推荐动作存在于当前阶段动作内时，会排到第一位并带推荐原因。

4. **SSE Result 透出**
   - `WorkflowRunResult` 透出 `coach_decision`、`student_state`、`agentic`。
   - route 结果中的 `event` 改为实际生成卡片的事件。

5. **验证**
   - 新增 `services/pythontopia-graph/tests/test_tutor_workflow_coach_graph.py`。
   - Focused：`25 passed in 0.37s`。
   - Graph 全量：`647 passed, 1 warning in 36.39s`。

## 第三批建议

下一批建议继续保持小步：

1. 增加 pedagogical guardrail critic，先覆盖最明确的泄答场景。
2. 接入 graph 生成卡片后的 guardrail 检查，但只做一次失败报错，不急着引入自动 revise。
3. 再跑 graph 全量；稳定后再进入 Java 持久化。

## 第三批执行记录

第三批已完成 pedagogical guardrail 接入，并按原计划支持一次重试：

1. **GuideGuardrailCritic**
   - 新增 `services/pythontopia-graph/pythontopia_graph/agents/guide_guardrail_critic.py`。
   - 当前覆盖高置信违规：
     - `skeleton_code` 无 TODO / 空位，疑似可直接提交；
     - `error_diagnosis` 输出完整修正代码；
     - `transfer_problem` 携带 `reference_solution/test_cases/hidden_test_cases/solution/answer`；
     - `knowledge_review` 的课件引用结构异常。

2. **Graph 接入**
   - `TutorWorkflowGraph` 在卡片生成后执行 `coach.guardrail`。
   - 第一次失败时将原因追加到重试上下文，重新生成一次。
   - 第二次仍失败则返回 `GUIDE_GUARDRAIL_FAILED`，不静默降级为普通聊天。

3. **验证**
   - 新增 `services/pythontopia-graph/tests/test_guide_guardrail_critic.py`。
   - 扩展 `services/pythontopia-graph/tests/test_tutor_workflow_coach_graph.py`，覆盖泄答卡被打回后重试成功。
   - Focused：`30 passed in 0.35s`。
   - Graph 全量：`652 passed, 1 warning in 35.30s`。

## 第四批建议

下一批进入 Java 持久化，但仍建议小步拆：

1. 先加 Flyway 表和 repository round-trip 测试。
2. 再让 `TutorWorkflowService` 请求 graph 前带入 persisted `student_state`。
3. 最后持久化 graph 返回的 `student_state/coach_decision` 和 action event。

## 第四批执行记录

第四批已完成 Java 侧状态持久化：

1. **数据库**
   - 新增 `V165__tutor_workflow_agent_state.sql`。
   - 新表：
     - `tutor_workflow_agent_state`：按 session 持久化 `state_json/latest_decision_json`；
     - `tutor_workflow_action_event`：记录导学动作事件，按 `(session_id, seq DESC)` 建索引。

2. **Repository**
   - `findAgentState(sessionId)`。
   - `upsertAgentState(sessionId, stateJson, decisionJson)`。
   - `insertActionEvent(sessionId, seq, eventType, actionKey, payloadJson)`。

3. **Service**
   - `run()` 请求 graph 前放入 `coach_mode=true`。
   - 若已有 persisted state，则将 `student_state` 注入 graph body。
   - `persistProjection()` 读取 graph result 中的实际 `event`、`student_state`、`coach_decision`。
   - 卡片落库事件从请求事件改为 graph 返回的实际事件，适配 `ERROR_FEEDBACK -> EXECUTION_TRACE` 这类 manager 决策。
   - 每张生成卡记录 `CARD_GENERATED` action event。

4. **验证**
   - 扩展 `TutorWorkflowServiceTest`，覆盖 state restore、state/decision upsert、action event。
   - Focused backend gate：
     `mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,CoursePackServiceIntegrationTest test`
   - 结果：12 tests，0 failures，0 errors；Flyway validated 83 migrations。

## 第五批建议

下一批进入前端“主动一句话，等待点击继续”：

1. 新增 `GuideNudgeCard.vue`，只展示短邀请，不自动触发导学。
2. `TrainingPanel` 在 AC/WA/CE/RE/TLE 后展示 nudge。
3. 点击继续后再打开/驱动 `TutorWorkflowPanel`，不要在 TrainingPanel 复制 SSE 逻辑。
