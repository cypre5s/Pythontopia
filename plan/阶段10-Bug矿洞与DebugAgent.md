# 阶段 10 — Bug 矿洞与 DebugAgent

## 1. 时间窗口与定位

- 建议日期：2026-06-08 ~ 2026-06-10（第 3 周末段，2~3 天，必须在阶段 09 收口后启动）。
- 角色：1 名 Python Agent 负责人 + 1 名后端 + 1 名前端。
- 定位：把"判题失败"变成"教学契机"。学生失败提交进入 Bug 矿洞副本，由 `DebugAgent` 给出反例、变量轨迹、错误假设和分层提示；提供"重新提交"入口而非直接给答案。

## 2. 本阶段目标

- 实现 `DebugAgent`：基于失败提交、题目隐藏测试（仅在沙箱内可见）、最近一次 RAG 检索，输出结构化调试卡片。
- 后端实现 Bug 矿洞 API：`POST /api/bug-mine/sessions`、`GET /api/bug-mine/sessions/{id}`、`POST /api/bug-mine/sessions/{id}/retry-submit`。
- 前端实现 Bug 矿洞场景与 `BugMinePanel.vue`，分四层提示：反例 → 变量轨迹 → 错误假设 → 分层提示。
- 严格遵守：
  - 不直接给可复制提交的完整答案。
  - 反例 stdin/expected 必须取自 hidden cases，但**只展示与失败 case 相关的最小信息**（必要时脱敏）。
  - 学生在 Bug 矿洞内的重新提交必须再走服务端判题，且记录 `source=BUG_MINE_RETRY`。

## 3. 上下游关系

- 上游：
  - 阶段 06 RAG（查"input"、"for"、"list"对应的常见错误段落）。
  - 阶段 07 资源生成（CodeLab 失败案例可作为参考）。
  - 阶段 09 失败提交与 `judge_result.trace`。
- 下游：
  - 阶段 13 复盘大厅展示"Bug 矿洞修复历史"。
  - 阶段 12 教师班级看板聚合高频 Bug 类型。

## 4. Alethicode 借用来源

- 反例与提示设计参考：
  - `/home/cypress/Alethicode/services/qa-graph/app/nodes/grounding_critic.py`
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/execution/PythonExecutionTraceService.java`
  - `/home/cypress/Alethicode/backend/src/main/java/com/alethicode/service/aitutor/visualize/VisualizeCapabilityService.java`

**抽取约束**：

- 老 `PythonExecutionTraceService` 仅参考"如何捕获变量轨迹"思路。Pythontopia 在沙箱内使用 `sys.settrace` 或 `bdb` 自定义实现，且仅在 Bug 矿洞流程中启用。
- 不直接复制老 OJ 的提示模板，重写一份严格"分层提示"。

## 5. 任务清单

### 5.1 schema 与配置

- T10-01 Flyway 迁移 `V070__bug_mine.sql`：
  - `bug_mine_session`：`id`, `user_id`, `submission_id` UNIQUE, `problem_id`, `status`（`OPEN`/`RESOLVED`/`ABANDONED`），`opened_at`, `closed_at`, `quest_id NULL`。
  - `bug_mine_card`：`id`, `session_id`, `card_type`（`COUNTER_EXAMPLE`/`VARIABLE_TRACE`/`WRONG_ASSUMPTION`/`HINT_LAYER_1`/`HINT_LAYER_2`/`HINT_LAYER_3`），`payload` JSONB, `created_at`。
  - `debug_case` 表已在阶段 09 建好，本阶段直接使用。
- T10-02 `application.yml` 增加 `pythontopia.bugMine.maxRetriesPerSession=3`、`pythontopia.bugMine.hintMaxLayer=3`、`pythontopia.bugMine.counterExampleAllowedFields=["stdin","expected_stdout"]`。

### 5.2 变量轨迹与反例采集

- T10-10 `com.pythontopia.judge.trace.PythonTraceCollector`：
  - 在沙箱中以"轨迹模式"重新执行学生代码：用 `sys.settrace` 收集每行最后的变量赋值（仅基础类型与短字符串）。
  - 单次轨迹大小限制 200 行；超出截断，避免 payload 过大。
  - 输出结构化 `VariableTrace{steps: [{line, locals: {name: repr}}]}`，`repr` 走安全截断（默认 200 字符）。
- T10-11 `com.pythontopia.judge.trace.CounterExampleSelector`：
  - 从 `course_problem.hidden_test_cases` 中挑出最先失败的 case；返回 `{stdin, expected_stdout, your_stdout, diff_summary}`。
  - 若 case 为非公共数据（教师特别标注 `private=true`），返回脱敏后的小样例并标记 `redacted=true`。
- T10-12 `PythonTraceCollector` 与 `CounterExampleSelector` 都不暴露 REST，仅供 `BugMineService` 调用。

### 5.3 DebugAgent（pythontopia-graph）

- T10-20 `pythontopia_graph/agents/debug_agent.py`：
  - 输入：`DebugAgentInput{user_id, problem_summary, counter_example, variable_trace, code, profile_brief, kc_codes}`。
  - 输出：`DebugAgentOutput{wrong_assumption: WrongAssumption, hint_layers: list[HintLayer], counter_example: CounterExampleCard, variable_trace_summary: TraceSummary, retry_advice: RetryAdvice}`，全部为 `pydantic` 模型。
  - LangGraph 图：`load_inputs → reasoner → grounding_critic → safety → assembler`。
  - 提示词显式禁止"输出完整可复制答案"：safety 节点匹配"如下完整代码"、"复制即可通过"等模式时直接拒绝。
- T10-21 路由 `POST /agents/debug/analyze`：返回 `DebugAgentOutput`。
- T10-22 Hint Layer 设计：
  - Layer 1：让学生猜"你以为...，但实际..." 的引导问题。
  - Layer 2：定位错误所在的几行 + 相关 KC 段落引用页码。
  - Layer 3：给出"修改方向"的伪代码，但不能直接给可粘贴运行的代码。
- T10-23 grounding 检查：reasoner 中引用的 KC code 必须存在；引用的页码必须能在 RAG 命中。

### 5.4 后端 Bug 矿洞服务

- T10-30 `com.pythontopia.bugmine`：
  - `controller/BugMineController`：
    - `POST /api/bug-mine/sessions`：DTO `BugMineOpenRequest{submissionId}`，仅允许该提交属于当前学生、状态为非 `AC`；自动调用 `PythonTraceCollector` + `CounterExampleSelector` + graph 服务 `/agents/debug/analyze`，生成 4 张卡片并落 `bug_mine_card`。
    - `GET /api/bug-mine/sessions/{id}`：返回 session + cards；layer hint 默认锁住，按学生点击逐层解锁。
    - `POST /api/bug-mine/sessions/{id}/cards/{cardId}/reveal`：标记某张卡为已查看（用于评估）。
    - `POST /api/bug-mine/sessions/{id}/retry-submit`：DTO `{code}`，等价于 `POST /api/submissions` 但 `source=BUG_MINE_RETRY`，提交计入 `maxRetriesPerSession`。
    - `POST /api/bug-mine/sessions/{id}/close`：标记 `RESOLVED`（如再次提交 AC）或 `ABANDONED`（学生放弃）。
  - `service/BugMineService`：
    - 写 `bug_mine_session`、`bug_mine_card`、`debug_case`。
    - 重提交 AC 时：把 session 标 `RESOLVED`，触发 `MasteryEvaluator.applyEvent(userId, kcCode, DEBUG_FIX, +0.05)`。
  - `dto/`：`BugMineSessionDto`、`BugMineCardDto`（按 `layer_revealed` 字段控制是否返回 payload）。

### 5.5 前端 Bug 矿洞

- T10-40 `frontend/src/game/scenes/BugMineScene.ts`：场景为像素矿洞背景，玩家进入后弹出 `BugMinePanel.vue`。
- T10-41 `frontend/src/components/bugmine/BugMinePanel.vue`：
  - 顶部：失败的题目摘要 + 上次提交代码（可复制到训练场）。
  - 中部 4 列卡片：
    - 反例卡片：显示 stdin、expected、your_stdout、diff_summary。
    - 变量轨迹卡片：交互式查看（折叠展开），每步显示 line + locals。
    - 错误假设卡片：显示"你可能以为..."。
    - 分层提示卡片：默认显示 Layer 1；点击"再要一条提示"展开 Layer 2/3；调用 `POST /cards/{cardId}/reveal`。
  - 底部：内嵌训练场编辑器（与阶段 09 共享组件），按"重试提交"调 `POST /retry-submit`。
- T10-42 `frontend/src/stores/learning.ts` 新增 `bugMine` 子状态：`activeSession`, `cards`, `retries`, `lastResult`。
- T10-43 `frontend/src/api/bugMine.ts` typed client。

### 5.6 与 quest / 路径联动

- T10-50 路径节点 `FIX_BUG`：阶段 08 的 `PathPlannerAgent` 可以生成"修复最近某次失败"任务，本阶段补：
  - `payload={submissionId}`，前端点击直接打开 Bug 矿洞。
  - quest `COMPLETED` 必须等 Bug 矿洞 session `RESOLVED`。
- T10-51 失败提交后，前端 TrainingPanel 显示"进入 Bug 矿洞"按钮（实际由本阶段实现）。

### 5.7 安全与边界

- T10-60 不允许学生触发非自己提交的 Bug 矿洞 session（403）。
- T10-61 同一提交只能开一次 session（数据库唯一约束）；要再分析必须重新提交。
- T10-62 Hint Layer 3 文本必须经 safety 节点验证"无完整可复制代码"；触发警告则降级为只展示"修改方向短语"。
- T10-63 当 RAG 在该 KC 上无证据时，Hint 中标注"建议参阅课件"但不编造章节。

### 5.8 测试

- T10-70 Python：
  - `test_debug_agent.py`：mock 失败案例，断言三层 hint 都符合 schema，且 Layer 3 无完整代码。
  - safety 测试：注入"完整答案"响应 → 拒绝并报错。
- T10-71 后端：
  - `BugMineServiceTest`：开启 session、4 卡片落库、限重试。
  - `BugMineControllerTest`：跨用户 403、AC 后 RESOLVED、quest 联动。
- T10-72 前端：
  - `BugMinePanel.spec.ts`：Layer 1 默认展开、Layer 2/3 需点击；重试提交流程。

## 6. 数据输入与输出

- 输入：
  - 失败 `practice_submission` + `judge_result`。
  - `course_problem.hidden_test_cases`（仅服务端可见）。
  - 学生代码、画像、KC。
- 输出：
  - `bug_mine_session`、`bug_mine_card`、`debug_case`。
  - 新的 `practice_submission`（`source=BUG_MINE_RETRY`）。
  - 若修复成功：`kc_mastery_event source=DEBUG_FIX`、quest `COMPLETED`。
- 接口：
  - `POST /api/bug-mine/sessions`、`GET /api/bug-mine/sessions/{id}`、`POST /api/bug-mine/sessions/{id}/cards/{cardId}/reveal`、`POST /api/bug-mine/sessions/{id}/retry-submit`、`POST /api/bug-mine/sessions/{id}/close`。
  - graph `POST /agents/debug/analyze`。

## 7. 验收标准

### 7.1 功能验收

- 学生 WA 后在 TrainingPanel 点"进入 Bug 矿洞"：
  - 30 秒内生成 4 张卡片，`bug_mine_session.status=OPEN`。
  - 反例卡片包含 stdin/expected/your_stdout/diff_summary。
  - 变量轨迹卡片 ≤ 200 行。
  - 错误假设卡片为结构化短文本。
  - Hint 默认仅显示 Layer 1。
- 学生点击 Layer 2 → `POST /cards/{id}/reveal`；返回 payload；Layer 3 同理。
- 学生在 Bug 矿洞内重试 AC：
  - session `RESOLVED`，KC mastery +0.05。
  - quest 若关联且为 `FIX_BUG`，自动 `COMPLETED`。
- 单 session 重试超过 `maxRetriesPerSession=3`：返回 429，提示重新提交主训练场。

### 7.2 防答案泄露验收

- 自动化测试在 100 次随机用例下确认 Hint Layer 3 无连续 ≥ 8 行的可粘贴 Python 代码片段。
- safety 节点拦截"完整答案"模式时记录到 `resource_run.safety_result.failed`。

### 7.3 工程验收

- `pytest -q`、`mvn test`、`npm run test` 通过。
- 业务层 0 `any`；后端 DTO 不暴露未解锁的 hint payload。
- `bug_mine_card.payload` JSON 大小 ≤ 16KB。

## 8. 验证命令

```bash
cd /home/cypress/Pythontopia/services/pythontopia-graph
python -m pytest -q

cd /home/cypress/Pythontopia/backend
mvn test
mvn spring-boot:run &

TOKEN=...
# 假设已存在失败提交 SID
SID=...
SESSION=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"submissionId\":$SID}" \
  http://localhost:18080/api/bug-mine/sessions | jq -r .id)

curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:18080/api/bug-mine/sessions/$SESSION | jq

CARD=$(curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:18080/api/bug-mine/sessions/$SESSION | jq -r '.cards[] | select(.cardType=="HINT_LAYER_2") | .id')

curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:18080/api/bug-mine/sessions/$SESSION/cards/$CARD/reveal | jq

curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"print(int(input())+1)"}' \
  http://localhost:18080/api/bug-mine/sessions/$SESSION/retry-submit | jq
```

## 9. 风险与回滚

- 风险：变量轨迹引入显著性能开销；缓解：仅在 Bug 矿洞流程中开启 trace，主判题路径不开启。
- 风险：DebugAgent 引用 hidden case 内容过多导致泄题；缓解：CounterExampleSelector 控制只暴露当前失败 case，且 hint 不引用其他 case。
- 风险：网络抖动导致 graph 调用超时；缓解：retry 1 次，失败 fail fast，前端显示重试按钮。
- 回滚：删除 `bug_mine_session`、`bug_mine_card` 单 user 可还原；保留 `debug_case` 作长期诊断数据。

## 10. 不做事项

- 不在 Bug 矿洞内显示其他学生的提交。
- 不直接展示 hidden cases 全集。
- 不输出完整可复制运行的解题代码。
- 不在 Bug 矿洞内绕过服务端判题（必须经 `/retry-submit`）。
- 不为 Bug 矿洞引入 ReAct 循环（多步推理在 Agent 内通过显式节点完成）。

