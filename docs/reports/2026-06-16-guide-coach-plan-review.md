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
