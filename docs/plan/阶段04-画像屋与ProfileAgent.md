# 阶段 04 — 画像屋与 ProfileAgent

## 1. 时间窗口与定位

- 建议日期：2026-05-28 ~ 2026-05-30（第 2 周第 1-3 天，3 天）。
- 角色：1 名 Python Agent 负责人 + 1 名后端 + 1 名前端。
- 定位：把"学生画像"从 schema 变成**可生成、可更新、可被消费**的真实能力，是个性化资源生成（阶段 07）、路径规划（阶段 08）、看板（阶段 12）和评估（阶段 13）的源头。

## 2. 本阶段目标

- 实现 `ProfileAgent`：以 LangGraph 编排对话访谈、信息抽取、画像合成、grounding/safety 检查。
- 后端落地画像访谈 API、画像查询/更新 API；保证画像数据真实入库 `learner_profile`、`profile_interview`、`profile_revision`。
- 前端实现"画像屋"场景与对话面板：学生进入画像屋 NPC 触发问答，问答结果实时显示阶段进度与已生成维度；完成后画像可在"我的画像"页查看。
- 画像至少覆盖 `AGENTS.md` 第 4 节 8 维：专业背景、知识基础、学习目标、认知风格、学习偏好、易错模式、实践能力、情绪与坚持度。
- 提供画像"被动更新"接口的占位（具体被动来源在后续阶段填充）。

## 3. 上下游关系

- 上游：
  - 阶段 01 课程包（KC 列表用于"知识基础"问诊）。
  - 阶段 02 画像 schema 与世界会话。
  - 阶段 03 前端骨架、对话框组件、Pinia learning store。
- 下游：
  - 阶段 07 资源生成所有 Agent 都将读取 `learner_profile`。
  - 阶段 08 `PathPlannerAgent` 使用画像维度选 KC。
  - 阶段 12 教师看板会聚合班级学生画像高频维度。
  - 阶段 13 评估会向 `profile_revision` 写入被动更新事件。

## 4. Alethicode 借用来源

- 画像服务（Java）：
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/profile/`
- 画像迁移参考：
  - `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V41__learner_course_progress.sql`
  - `/home/cypress/Alethicode/backend/src/main/resources/db/migration/V63__add_learner_narrative_summary.sql`
- LangGraph 工作流参考：
  - `/home/cypress/Alethicode/services/tutor-graph/app/graph/builder.py`
  - `/home/cypress/Alethicode/services/tutor-graph/app/graph/state.py`
  - `/home/cypress/Alethicode/services/tutor-graph/app/nodes/evidence.py`
  - `/home/cypress/Alethicode/services/tutor-graph/app/nodes/diagnosis.py`
  - `/home/cypress/Alethicode/services/tutor-graph/app/nodes/output_sanitization.py`
- Grounding/Safety 参考：
  - `/home/cypress/Alethicode/services/qa-graph/app/nodes/grounding_critic.py`
  - `/home/cypress/Alethicode/services/qa-graph/app/nodes/hallucination_detector.py`

**抽取约束**：

- LangGraph 图结构必须重写为 Pythontopia 自己的节点（命名清晰、单一职责），**不**直接复用老节点名。
- 节点输入输出必须用 `pydantic` model；禁止节点返回字符串大段输出。
- ReAct 循环禁用；多轮访谈只通过显式状态机推进。

## 5. 任务清单

### 5.1 画像维度规范

- T04-01 在 `/home/cypress/Pythontopia/docs/product/profile_schema.md` 描述 8 维及子字段。示例：
  - `major`：枚举 `CS`/`非CS-理工`/`非CS-人文`/`其他`。
  - `base_level`：`{ programming_years: int, languages_known: string[], python_self_rated_level: 1-5 }`。
  - `learning_goal`：`{ kind: 'EXAM'|'PROJECT'|'INTEREST'|'JOB', deadline_iso: string|null, target_kc_codes: string[] }`。
  - `cognitive_style`：`{ field_dependent: 0-1, sequential_vs_holistic: -1..1 }`。
  - `learning_preference`：`{ media_priority: ['video'|'text'|'mindmap'|'code'], session_minutes_per_day: int }`。
  - `error_patterns`：`{ frequent_kc_codes: string[], common_categories: string[] }`。
  - `practice_capability`：`{ avg_attempts_per_problem: number, success_rate: 0-1 }`。
  - `emotion_persistence`：`{ resilience: 0-1, recent_mood: 'positive'|'neutral'|'negative' }`。
- T04-02 在 `services/pythontopia-graph/pythontopia_graph/schemas/profile.py` 中用 `pydantic.BaseModel` 完整定义对应类型。
- T04-03 后端 `com.pythontopia.learner.profile` 同步定义 Java records，字段命名 `camelCase`，并在 JPA `learner_profile.base_level` 等 JSONB 字段做 `ObjectMapper` 注册。

### 5.2 ProfileAgent（pythontopia-graph）

- T04-10 在 `services/pythontopia-graph/pythontopia_graph/agents/profile_agent.py` 实现：
  - 输入：`ProfileAgentInput(user_id, interview_session_id, last_turn_history, last_user_message, course_kc_index)`。
  - 输出：`ProfileAgentOutput(next_question | profile_draft | done, dimensions_filled, evidence_anchors, safety_result, grounding_result)`。
- T04-11 LangGraph 节点（`pythontopia_graph/graph/profile_graph.py`）：
  - `node_context_loader`：加载本次访谈历史与已部分填充的画像。
  - `node_question_planner`：根据"已填充维度"决定下一问题；问题模板必须 grounding 到 KC 列表或 8 维定义，禁止凭空发挥。
  - `node_response_parser`：把学生回答解析到候选维度字段（`pydantic` 校验）。
  - `node_profile_synthesizer`：当所有 8 维都达到最低置信度时，合成 `LearnerProfileDto`。
  - `node_grounding_critic`：检查输出字段是否有 KC code 不存在、是否有学生未陈述的虚构信息；不通过则返回 `next_question` 再问。
  - `node_safety`：检查问题文本是否含敏感内容；不通过则 fail fast 并写 `safety_result.failed`。
  - `node_persistor`：将变更写入 `profile_interview`、`profile_revision`（含 diff）。
- T04-12 LangGraph 状态机**禁止 ReAct 循环**，节点之间走显式 edge；最大轮数 30，超出强制 `done=true`。
- T04-13 在 `services/pythontopia-graph/pythontopia_graph/main.py` 注册 `/agents/profile/turn` 路由：
  - 请求：`ProfileTurnRequest{user_id, interview_session_id, user_message, history}`。
  - 响应：`ProfileTurnResponse{assistant_message?, profile_draft?, done, dimensions_filled, evidence, safety, grounding}`。
- T04-14 单元测试 `services/pythontopia-graph/tests/test_profile_agent.py`：
  - 用 `FakeLLM` 注入预设响应，验证 8 维全部填充后 `done=true`。
  - 验证 grounding 失败（虚构 KC code）时图返回 `next_question`，不写库。
  - 验证 safety 失败时 fail fast。

### 5.3 LLM Provider 抽象

- T04-20 在 `pythontopia_graph/llm/provider.py` 定义：
  - `class LlmProvider(Protocol): async def chat(messages, **kwargs) -> LlmResponse`。
  - `LlmResponse` 必须为 `pydantic` model，包含 `content_json`（结构化）或显式 `content_text`。
- T04-21 实现 `DryRunProvider`（开发/CI 使用，返回 deterministic 模拟响应）和 `OpenAiCompatProvider`（开发 fallback）。
- T04-22 讯飞 Provider 在阶段 11 实现，本阶段提供 `SparkProviderStub`：拒绝调用并抛 `NotImplementedError("阶段11接入")`，避免遗忘。
- T04-23 从 `ProfileAgent` 入口注入 `LlmProvider`，禁止节点内部直接 `import openai` 或硬编码 base URL。

### 5.4 后端画像 API

- T04-30 `com.pythontopia.learner.profile`：
  - `controller/ProfileInterviewController`：
    - `POST /api/learner/profile/interview`（学生）：开启或恢复访谈，返回 `interviewSessionId`、当前历史、当前 draft。
    - `POST /api/learner/profile/interview/{sessionId}/turn`：转发到 `pythontopia-graph` `/agents/profile/turn`，校验 token、`userId` 一致；记录 `resource_run`（详见 5.7）。
    - `POST /api/learner/profile/interview/{sessionId}/finish`：把 final draft 持久化到 `learner_profile`、关闭 `profile_interview`。
  - `controller/ProfileController`：
    - `GET /api/learner/profile`：返回当前画像 DTO（若 8 维都为空，返回 204）。
    - `GET /api/learner/profile/revisions?limit=10`：返回最近的画像 diff，按时间倒序。
  - `service/ProfileService`：
    - `applyDraft(userId, draft)`：写入 `learner_profile`，并向 `profile_revision` 写一条 `source=INTERVIEW` 的 diff。
    - `applyPassiveRevision(userId, source, diff)`：占位接口，供阶段 07/09/13 调用（本阶段只测试空实现）。
  - `dto/`：`LearnerProfileDto`、`ProfileTurnRequest`、`ProfileTurnResponse`、`ProfileRevisionDto`。
- T04-31 增加 Flyway 迁移 `V020__learner_profile_indexes.sql`：为 `learner_profile.user_id`、`profile_revision.user_id`、`profile_interview.session_id` 增加合适索引。
- T04-32 `application.yml` 增加 `pythontopia.services.graphUrl=${PYT_GRAPH_URL:http://localhost:18091}`，禁止硬编码。

### 5.5 资源运行落库

- T04-40 Flyway 迁移 `V021__resource_run.sql`：
  - 表 `resource_run`：`id`、`user_id`、`agent_name`、`scenario`（例：`PROFILE_INTERVIEW`）、`input_summary` JSONB、`output_artifact` JSONB、`evidence_page_ids` BIGINT[]、`safety_result` JSONB、`grounding_result` JSONB、`status`（`SUCCESS`/`PARTIAL`/`FAILED`）、`created_at`。
- T04-41 `com.pythontopia.agent.run.ResourceRunRecorder`：
  - 通用工具，所有调用 `pythontopia-graph` 的后端服务必须用 `record(agentName, scenario, input, output, evidence, safety, grounding, status)`。
- T04-42 `ProfileInterviewController.turn` 在调用 graph 服务前/后调用 `ResourceRunRecorder`。

### 5.6 前端画像屋

- T04-50 路由 `/profile-room`：渲染 `ProfileRoomScene`（Phaser）+ `ProfileInterviewPanel.vue`（Vue DOM 覆盖）。
- T04-51 `frontend/src/game/scenes/ProfileRoomScene.ts`：固定 NPC，触发对话即激活右侧/底部的 `ProfileInterviewPanel`。
- T04-52 `frontend/src/components/profile/ProfileInterviewPanel.vue`：
  - 显示对话历史、当前问题、输入框。
  - 显示 8 维进度条（数据来自 `dimensions_filled`）。
  - 显示 grounding/safety 状态图标（仅显示状态，不显示原始 critic 文本）。
  - 完成访谈后跳转 `/profile`。
- T04-53 路由 `/profile`：`ProfilePage.vue`，展示完整画像（DTO 各字段卡片化展示，纯 DOM，可复制）。
- T04-54 `frontend/src/stores/learning.ts` 中新增 `profile` 子模块：`loadProfile`、`openInterview`、`sendTurn`、`finishInterview`。
- T04-55 `frontend/src/api/learner.ts` 增加访谈与画像查询 typed 函数。
- T04-56 前端测试：
  - `tests/components/ProfileInterviewPanel.spec.ts`：模拟 8 维全部填充后弹"完成访谈"按钮。
  - `tests/stores/learning.profile.spec.ts`：mock API，验证 `dimensions_filled` 增量更新。

### 5.7 安全与防幻觉

- T04-60 grounding 失败时：
  - 后端 `resource_run.status=PARTIAL`，前端 panel 显示"仍需补充信息"。
- T04-61 safety 失败时：
  - 后端 `resource_run.status=FAILED`，前端 panel 显示"内容审查失败"，并自动结束本次访谈（不写入 `learner_profile`）。
- T04-62 整条链路无任何"沉默 fallback 到普通聊天"路径；如 LLM Provider 返回非结构化或非合规 JSON，节点直接抛错并冒泡。

## 6. 数据输入与输出

- 输入：
  - 学生在画像屋的对话回答。
  - 课程 KC 索引（来自阶段 01）。
- 输出：
  - `learner_profile` 行（每个学生 1 行）。
  - `profile_interview` 行（每次访谈 1 行）。
  - `profile_revision` 行（每次画像更新 1 行）。
  - `resource_run` 行（每次 Agent 调用 1 行）。
  - 前端"我的画像"页可读快照。

## 7. 验收标准

### 7.1 功能验收

- 新学生进入画像屋 → 与 NPC 完成访谈 → 8 维全部填充 → `GET /api/learner/profile` 返回完整画像。
- 学生中途退出再次进入，能从上次进度继续（`POST /api/learner/profile/interview` 返回未完成 session）。
- 访谈完成后 `GET /api/learner/profile/revisions` 至少返回 1 条 `source=INTERVIEW` 的 diff。
- `resource_run` 至少记录 1 条 `agent_name=ProfileAgent`、`status=SUCCESS` 的运行。

### 7.2 防幻觉与安全验收

- 在 `DryRunProvider` 注入"虚构 KC code"响应，访谈接口返回继续提问，不写入 `learner_profile`，且 `resource_run.status=PARTIAL` 记录 grounding 失败原因。
- 在 `DryRunProvider` 注入"含敏感内容"响应，访谈直接终止；`resource_run.status=FAILED`、前端显示审查失败。
- LLM Provider 缺失时启动失败（fail fast），而不是降级为普通聊天。

### 7.3 工程验收

- 后端：`mvn test` 通过；`ProfileInterviewControllerTest`、`ProfileServiceTest` 覆盖访谈开启/恢复/完成 3 种状态。
- Python：`pytest -q` 通过；`test_profile_agent.py` 覆盖 grounding 失败、safety 失败、正常完成 3 个用例。
- 前端：`npm run typecheck`、`npm run test` 通过；`ProfileInterviewPanel` 与 store 之间无 `any`。
- 画像 DTO 字段与 `services/pythontopia-graph` 中 pydantic 模型字段一致；任何不一致必须用契约测试发现。

## 8. 验证命令

```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
python -m pip install -e ".[dev]"
python -m pytest -q
PYT_LLM_PROVIDER=dryrun uvicorn pythontopia_graph.main:app --host 0.0.0.0 --port 18091 --workers 1 &

cd /home/cypress/Pythontopia/backend
mvn test
PYT_GRAPH_URL=http://localhost:18091 mvn spring-boot:run &

# 端到端
TOKEN=$(curl -s -X POST http://localhost:18080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"stu01","password":"P@ssw0rd"}' | jq -r .token)

SID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:18080/api/learner/profile/interview | jq -r .interviewSessionId)

curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"userMessage":"我是计算机大一，学过 C 语言，目标是期末考试通过"}' \
  http://localhost:18080/api/learner/profile/interview/$SID/turn | jq

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:18080/api/learner/profile | jq

cd /home/cypress/Pythontopia/frontend
npm run typecheck
npm run test
```

## 9. 风险与回滚

- 风险：8 维问题过多，访谈疲劳；缓解：`node_question_planner` 优先合并相关问题，并允许学生跳过非核心维度（标记为 `LOW_CONFIDENCE`）。
- 风险：LLM 返回 JSON 不合规；缓解：`response_parser` 使用 `pydantic.ValidationError` 触发 retry（最多 2 次），失败则 fail fast。
- 风险：前端访谈窗口在弱网中断；缓解：前端按 `sessionId` 续接，并显示重连状态。
- 风险：画像中含个人敏感信息（如真实姓名、身份证）；缓解：safety 节点必须含 PII 检查。
- 回滚：`learner_profile`、`profile_interview`、`profile_revision`、`resource_run` 表均可单 user 清空恢复初始状态。

## 10. 不做事项

- 不做画像跨课程合并（pack_id 永远=43）。
- 不做语音输入。
- 不在 ProfileAgent 调用任何外部知识库（除 KC 索引）。
- 不允许 LLM Provider 缺失时静默降级。
- 不允许画像数据在前端被纯本地编辑后直接覆盖后端（必须经过 `applyDraft` 服务端）。
- 不复制 Alethicode 老画像 Java 类直接 import；必须重写为 Pythontopia 包。

