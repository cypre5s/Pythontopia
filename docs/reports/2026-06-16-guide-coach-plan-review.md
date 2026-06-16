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

## 第五批执行记录

第五批已完成训练场提交后的短邀请触发：

1. `GuideNudgeCard.vue`
   - AC 后文案为短复盘邀请，按钮触发 `AC_REVIEW`。
   - WA/CE/RE/TLE 后文案为错误分析邀请，按钮触发 `ERROR_FEEDBACK`。
   - 组件只在学生点击后 emit，不自动展开导学。

2. `TrainingPanel`
   - 正式提交返回判题结果后展示 nudge。
   - 点击继续后打开 `TutorWorkflowPanel`，并通过 `initialEventRequest` 传入一次性初始事件。

3. `TutorWorkflowPanel`
   - 复用已有 SSE 工作流执行逻辑消费 `initialEventRequest`。
   - 避免在训练场组件中复制 `/api/tutor/workflow/runs` 调用。

4. 验证
   - Focused frontend gate：
     `npm run test -- tests/components/training/GuideNudgeCard.spec.ts tests/components/TrainingPanel.spec.ts`
   - 结果：2 files，10 tests passed。
   - Frontend type gate：`npm run typecheck` 通过。

## 第六批建议

下一批继续补“manager agent 可见但不暴露推理链”的体验层：

1. 在 `TutorWorkflowPanel` 展示推荐下一步动作和简短理由，优先读取 graph 返回的
   `available_actions[].recommended/reason`。
2. 保留所有动作可点，推荐只是排序和提示，不锁死学生路径。
3. 增加 focused component test，覆盖推荐动作显示和非推荐动作仍可见。

## 第六批执行记录

第六批已完成推荐动作可见化：

1. DTO
   - `WorkflowActionDto` 新增可选 `recommended` 与 `reason` 字段。
   - 与 graph 侧 `WorkflowAction` 保持兼容；旧响应不带字段时 UI 不展示推荐块。

2. `TutorWorkflowPanel`
   - 新增“推荐下一步”提示块，展示推荐动作 label 与简短 reason。
   - 推荐动作按钮增加轻量标签和高亮。
   - 其它动作仍正常渲染、可点击，推荐不等于锁定路径。

3. 验证
   - 先写红测：`TutorWorkflowPanel.spec.ts` 要求推荐动作可见且普通动作仍保留。
   - Focused frontend gate：
     `npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts tests/components/training/GuideNudgeCard.spec.ts tests/components/TrainingPanel.spec.ts`
   - 结果：3 files，11 tests passed。
   - Frontend type gate：`npm run typecheck` 通过。
   - Graph focused gate：`python3 -m pytest -q tests/test_tutor_workflow_coach_graph.py`，3 passed。

## 第七批建议

下一批进入反馈回流，但建议继续拆小：

1. 后端先加 `POST /api/tutor/workflow/sessions/{sessionKey}/feedback`，写入
   `tutor_workflow_action_event`，只接受 card/action 级 feedback。
2. 前端再给卡片加“有帮助/没帮助”按钮；反馈失败只 toast 或静默，不阻塞导学。
3. 暂不做复杂推荐模型更新，只把反馈事件稳定沉淀，供后续 coach state 聚合。

## 第七批执行记录

第七批已完成反馈事件沉淀：

1. Backend
   - 新增 `TutorWorkflowFeedbackRequest`。
   - 新增 `POST /api/tutor/workflow/sessions/{sessionKey}/feedback`。
   - `TutorWorkflowRepository` 支持按 `sessionKey + userId` 查询活跃 session，避免跨用户写反馈。
   - `TutorWorkflowService.recordFeedback()` 将 `ACTION_CLICKED / CHECKPOINT_COMPLETED /
     CARD_HELPFUL / CARD_NOT_HELPFUL` 写入 `tutor_workflow_action_event`。
   - `ACTION_CLICKED` 发布 `LearningEvent.GuideActionClicked`。
   - `CHECKPOINT_COMPLETED` 发布 `LearningEvent.GuideCheckpointCompleted`。
   - `MemoryStreamMirror` 将 `guide_action_clicked / guide_checkpoint_completed` 镜像进
     `learning_event_log`，归属到 `coach_q` 记忆流。

2. Frontend
   - `tutorWorkflow.ts` 新增 `recordTutorWorkflowFeedback()`，请求设置 `suppressNetworkToast`。
   - `TutorWorkflowPanel` 点击动作时非阻塞记录 `ACTION_CLICKED`。
   - 每张导学卡新增“有帮助 / 没帮助”轻反馈按钮，成功后显示“已记录”。
   - 反馈失败被吞掉，不阻塞 SSE 工作流。

3. 未做范围
   - 当时尚未把“后续提交是否 AC”自动归因到上一条导学动作；该缺口已在第九批用提交事件监听器补上。

4. 验证
   - Backend focused gate：
     `mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,CoursePackServiceIntegrationTest test`
   - 结果：14 tests，0 failures，0 errors；Flyway validated 83 migrations。
   - Frontend focused gate：`npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts`
   - 结果：3 tests passed。
   - Frontend type gate：`npm run typecheck` 通过。

## 第八批建议

下一批实现 Task 10：展示 bounded coach progress，但不展示隐藏推理链。

1. 使用 `useSseStream.events/traces` 中的节点名，投影为人话状态，例如：
   `coach.decide -> 我在选择最合适的下一步`，`coach.guardrail -> 我在检查提示是否安全`。
2. 只展示节点级进度和安全状态，不展示 prompt、chain-of-thought、内部打分细节。
3. 增加 focused component test，覆盖 trace 到人话进度的映射。

## 第八批执行记录

第八批已完成 bounded coach progress：

1. Graph trace
   - `TutorWorkflowGraph` 在 coach 模式下新增 `coach.load_state` 与 `coach.update_state`
     trace，明确区分“读取学生状态”和“更新导学状态”。
   - 卡片生成阶段新增 `coach.tool.<tool_name>` trace，例如 `coach.tool.execution_trace`，
     让 UI 可以展示“manager agent 正在调用哪个教育工具类别”。
   - 失败时同步结束 tool trace，避免前端留下悬空进度。

2. Frontend projection
   - `TutorWorkflowPanel` 将 trace node 投影为学生可理解的短句：
     “正在判断你卡在哪里”“正在选择下一步提示”“正在生成导学卡”“正在检查是否过度提示”等。
   - UI 只显示步骤级状态：进行中、完成、重新调整、未通过。
   - 不展示 raw node name、prompt、内部打分或 chain-of-thought。

3. 验证
   - Graph focused gate：
     `python3 -m pytest -q tests/test_tutor_workflow_coach_graph.py tests/test_sse_agui.py`
   - 结果：21 passed。
   - Frontend focused gate：
     `npm run test -- tests/components/training/TutorWorkflowPanel.spec.ts`
   - 结果：4 tests passed。
   - Frontend type gate：`npm run typecheck` 通过。

## 第九批建议

下一批进入 Task 11 终验，但先复核 acceptance checklist：

1. 已实现项用 focused/full tests 证明，不靠口头判断。
2. 对“后续提交是否更新 coach state”单独检查代码路径；如果仍只是动作反馈事件，需要补一个小批次。
3. 最后再跑 graph、backend、frontend 的最终验收组合，并记录未覆盖的真栈浏览器/ECS/多端范围。

## 第九批执行记录

第九批补齐“后续提交更新 coach state”：

1. Submission feedback listener
   - 新增 `TutorWorkflowSubmissionFeedbackListener`，监听
     `LearningEvent.PracticeSucceeded` 与 `LearningEvent.PracticeFailed`。
   - 若存在同题 active tutor workflow session，则读取现有 `tutor_workflow_agent_state`，
     回写 `attempt_count`、`latest_verdict`、`consecutive_errors`、`frustration_level`、
     `last_action_effective`。
   - AC 将连续错误清零并标记上一导学动作有效；WA/RE/TLE 等失败会累加连续错误并标记上一动作未见效。
   - 同步写入 `tutor_workflow_action_event`：`event_type=SUBMISSION_OBSERVED`。

2. 边界
   - 该监听器不重新调用 graph、不主动生成新卡，避免把“状态回流”变成开放自动循环。
   - 没有 active session 时直接跳过；状态 JSON 异常时重建最小 state 并 fail-soft 记录日志。

3. 验证
   - 先写红测：
     `TutorWorkflowSubmissionFeedbackListenerTest` 初始因缺少监听器编译失败。
   - 单测转绿：
     `mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowSubmissionFeedbackListenerTest test`
   - 结果：2 tests，0 failures，0 errors。
   - 后端 focused gate：
     `mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,SubmissionServiceTest,CoursePackServiceIntegrationTest test`
   - 结果：20 tests，0 failures，0 errors；Flyway validated 83 migrations。

## 第十批建议

现在可以进入 Task 11 final verification：

1. 逐条核验 acceptance checklist。
2. 跑 guide coach 相关 graph/backend/frontend 组合测试。
3. 再按用户要求执行更大的本地项目验收；若真栈浏览器或 ECS 不跑，需要明确列为未覆盖。

## 第十批执行记录

第十批已完成最终验收和本地健康检查：

1. Acceptance checklist
   - AC/WA/CE/RE/TLE 后短 nudge 等待点击：由 `GuideNudgeCard` 和 `TrainingPanel` 规格覆盖。
   - 点击继续进入 manager 决策：`TutorWorkflowGraph` 的 `coach.decide` 与 `effective_event` 测试覆盖。
   - 结构化决策与持久化：`TutorWorkflowServiceTest` 覆盖 `student_state/coach_decision` restore 与 upsert。
   - 推荐动作排序：graph 与 `TutorWorkflowPanel` 测试覆盖 `recommended/reason`。
   - 后续提交更新 state：`TutorWorkflowSubmissionFeedbackListenerTest` 覆盖 AC 与失败提交回流。
   - guardrail 与迁移题边界：`GuideGuardrailCritic` 和 coach graph 测试覆盖泄答打回。
   - AG-UI 进度可见但不泄露推理链：`test_sse_agui.py` 与 `TutorWorkflowPanel.spec.ts` 覆盖自然语言投影。

2. Focused verification
   - Graph：
     `python3 -m pytest -q tests/test_tutor_workflow_coach_schema.py tests/test_guide_coach_agent.py tests/test_tutor_workflow_coach_graph.py tests/test_guide_guardrail_critic.py tests/test_guide_coach_golden.py tests/test_sse_agui.py`
   - 结果：38 passed。
   - Backend workflow/submission：
     `mvn -s .mvn/settings.build.xml -Dtest=TutorWorkflowServiceTest,TutorWorkflowSubmissionFeedbackListenerTest,SubmissionServiceTest test`
   - 结果：17 tests，0 failures，0 errors。
   - Backend graph client：
     `mvn -s .mvn/settings.build.xml -Dtest=TutorGraphClientSseTest,TutorGraphClientFailureTest test`
   - 结果：5 tests，0 failures，0 errors。
   - Frontend：
     `npm run test -- tests/components/training/GuideNudgeCard.spec.ts tests/components/training/TutorWorkflowPanel.spec.ts`
   - 结果：2 files，7 tests passed；`npm run typecheck` 通过。

3. Full local verification
   - Graph full：652 passed，1 warning。
   - Backend full：762 tests，0 failures，0 errors。
   - Frontend full：204 files / 1981 tests passed；typecheck 通过。
   - 本地真栈：`./dev.sh` 启动后 5173 / 18080 / 18091 / 18092 healthz 均 ok，`/api/course/videos`
     返回课程视频 JSON，Postgres / Redis / graph / rag 均 healthy。

4. 发现并修复的环境问题
   - `./dev.sh` 原本使用 `.mvn/settings.xml` 启动后端，该配置只走 Maven Central；当前网络环境下
     `poi-ooxml` 依赖解析会被 `repo.maven.apache.org` 超时阻断。
   - 已将启动脚本改为 `.mvn/settings.build.xml`，复用项目已有 Maven 镜像配置。

5. 未覆盖
   - 未执行浏览器手工 AC/WA 点击流。
   - 未执行公网 ECS 验收。
   - 未执行 Android / Windows / 微信端回归。
