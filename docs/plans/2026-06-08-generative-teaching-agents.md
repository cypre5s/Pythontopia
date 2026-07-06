# 生成式教学智能体小镇（Grounded Generative Agents）实施计划（详版）

> **For Claude:** REQUIRED SUB-SKILL: 实施时用 executing-plans / subagent-driven-development，逐任务推进，**每步先写测试再实现**，频繁提交。每个 Task 末尾的「验收标准」是硬门槛，全绿才算完成。

**Goal:** 借鉴 Stanford《Generative Agents / Smallville》的认知架构（记忆流 → 检索 → 反思 → 规划 → 涌现协作），把 Pythontopia 的教学 agent 从「按钮背后的流水线函数」升级为**会记事、会反思、会串门、会主动找你**的小镇常驻角色——但**每条记忆都锚定真实学习事件、每次反思都写回可验证画像维度、且强制引用事件 id**，绝不引入无接地的自治幻觉。

**Thesis（差异点）：** 保留 Smallville 的认知架构，剥离它的幻觉：记忆 = 真实 `learning_event_log` 事件；反思 = LLM 综合但**必须引用 event id 且只能写 8 维画像白名单**；协作 = 真实资源生成/答疑/路径的可视化。命中赛题三大核心评分点（真·多智能体协同 + 依知识短板动态调整 + 效果评估），长在项目「证据优先/防幻觉」基因上。

**Tech Stack:** 后端 Spring Boot（Java 21）+ Flyway + JdbcTemplate；智能体 FastAPI + LangGraph（`services/pythontopia-graph`，`LlmProvider` 抽象，含 `DryRunProvider` 离线兜底）；前端 Vue 3 + TS strict + Pinia + Vitest + Phaser 3。

**硬约束：**
- 项目（`AGENTS.md`）：**不启用 ReAct 循环**；Agent 故障 **fail fast**；**不做无教育目的装饰**；不伪造课件页码；业务层禁 `any`；生产/演示默认**单 worker**（反思计数可用进程内状态）。
- 用户：**叙事性 + 前端可展示**；颠覆但实用。
- 成本红线：反思是唯一新增**周期性** LLM 调用 → N 事件阈值 + 冷却 + 无网/无 key 规则兜底，演示链路不断。

---

## 现状基线（已核对真实代码，实施前必读）

| 能力 | 落点（真实文件） | 关键签名 / 事实 |
| --- | --- | --- |
| 记忆流底座 | `learning_event_log`（`V121`） | 列 `(id BIGSERIAL, user_id, event_type VARCHAR(64), payload JSONB, source VARCHAR(32), created_at TIMESTAMPTZ)`；索引 `(user_id,event_type,created_at DESC)`、`(created_at DESC)` |
| 事件日志读写 | `LearningEventLogRepository` | `insert(userId, eventType, Map payload, source)`；`findByUser(userId, limit)`（返回 `List<Map<String,Object>>`，含 `payload::text`） |
| 学习事件（进程内） | `LearningEvent`（sealed）+ `LearningEventListener` | 11 种：`PracticeSucceeded/Failed(kcCode,problemId[,failureKind])`、`DebugResolved`、`ResourceConsumed(kcCode,resourceKind)`、`MediaConsumed`、`TutorStyleEngaged`、`ProbeChoiceMade`、`PracticeRetried/Abandoned`、`ComebackSolved(...,priorFails)`、`ProfileBootstrapped`；listener 现仅驱动 `MasteryEvaluator` |
| 画像回写靶点 | `PassiveProfileService` + `ProfileRepository` | `applyDimensionUpdate(userId, dimensionKey, DimensionDelta)` 写 `learner_profile.dimensions` JSONB + `profile_revision` 审计；`DimensionDelta.ofTags(scoreDelta, List<String> tags, source)`；8 维 key 见下；`ALLOWED_REVISION_SOURCES`（Java）+ `profile_revision_source_check`（DB）双闸 |
| 8 维 key | `PassiveProfileService.DIMENSION_KEYS` | `professional_background, knowledge_base, learning_goal, cognitive_style, learning_preference, error_pattern, practical_ability, emotion_persistence` |
| 检索注入 | `agents/personalization.py` | `resource_personalization_directive(ProfileSnapshot)`；`ProfileSnapshot{major, python_self_rated_level, media_priority, cognitive_style, error_patterns}`（`schemas/resource.py`） |
| graph 路由范式 | `main.py` + `routes/intent.py` | `app.state.llm_provider` 共享；路由内 `_provider(request)` 优先共享、回退 `provider_from_env()`；`LlmProvider.chat(messages, response_format={"type":"json_object"}, temperature=...)` → `LlmResponse{content_json, content_text}`；`DryRunProvider` 离线兜底 |
| Agent 模板 | `agents/intent_router_agent.py` | provider 注入 + `_chat_json` + JSON 围栏剥离 + 关键词兜底 + 白名单校验——**ReflectionAgent 照此写** |
| 前端剧场 | `components/workshop/AgentOfficeScene.vue` | agent 演 NPC、接力棒交接、想法气泡、进度/日志、交付证据卡，消费 SSE `plan`/`trace` |
| 主动陪伴雏形 | `stores/companion.ts` | `nudge` + `loadNudge()`（到期复习冒泡） |
| 最新迁移 | `db/migration/` | 最新 `V133`；新表从 **`V134`** 起 |

> ⚠️ 两条事件通道：① 进程内 `LearningEvent`（`ApplicationEventPublisher`）→ `LearningEventListener` → mastery；② 前端事件 → `LearningEventController` → `learning_event_log` 表。**Task 0.1 负责把通道①也镜像进表**，统一记忆流底座。

---

## 分期总览

| 阶段 | 内容 | Layer | 顶层验收 |
| --- | --- | --- | --- |
| Phase 0 | 记忆流底座：importance + 事件镜像入库 + 检索服务/API | A | `GET /api/learner/memory` 返回带 recency/importance/relevance 分解的 topK；`mvn test` 绿 |
| Phase 1 | 反思：ReflectionAgent + 触发 + 回写画像 + 落库洞察 | A | 攒够 N 事件触发一次反思，洞察引用 event id 并回写画像（source=REFLECTION）；离线规则兜底；`pytest`+`mvn test` 绿 |
| Phase 2 | 记忆条件化生成：检索/洞察注入资源·答疑·路径 | A | 资源/答疑 prompt 携带记忆要点；路径按洞察重排；`pytest` 绿 |
| Phase 3 | 小镇涌现协作：笔记本 / 顿悟 / 串门 / 主动找你 | B | 4 件前端可展示件落地；`typecheck`+`vitest`+`eslint` 绿 |

**全局 DoD：** 四端不受影响；`mvn -q -DskipTests compile` + 相关 `mvn test`、`python -m pytest -q`、`npm run typecheck` + 相关 `vitest`、`npx eslint` 全绿；无 `any` 扩散；反思无网/无 key 自动降级不报错；每条洞察可溯源到 event id。

---

# Phase 0：记忆流底座（纯后端，无 UI）

## Task 0.1：importance 列 + 事件镜像入库

**目标：** 给记忆流每条事件一个确定性「重要度」（1–5，写入即定，零 LLM），并把进程内 `LearningEvent` 镜像进 `learning_event_log`，统一记忆流。

**Files:**
- Create `backend/src/main/resources/db/migration/V134__learning_event_importance.sql`
- Create `backend/src/main/java/com/pythontopia/learner/event/EventImportance.java`
- Create `backend/src/main/java/com/pythontopia/learner/event/MemoryStreamMirror.java`（新增 `@Component`，`@EventListener` 各事件 → 写表）
- Modify `backend/src/main/java/com/pythontopia/learner/event/LearningEventLogRepository.java`（`insert` 增 `importance` 形参）
- Modify `backend/src/main/java/com/pythontopia/learner/event/LearningEventController.java`（前端入库路径补 importance）
- Test `backend/src/test/java/com/pythontopia/learner/event/EventImportanceTest.java`、`MemoryStreamMirrorTest.java`

**Step 1（先写迁移）：** `V134`：
```sql
-- Purpose: 记忆流（Grounded Generative Agents）重要度打分，用于 recency×importance×relevance 检索。
-- Tables affected: learning_event_log（加列）
-- Irreversible risk: 回滚需 DROP COLUMN importance（丢失历史重要度，可由 EventImportance 规则重算）。
ALTER TABLE learning_event_log
  ADD COLUMN IF NOT EXISTS importance SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE learning_event_log
  ADD CONSTRAINT learning_event_importance_range CHECK (importance BETWEEN 1 AND 5);
COMMENT ON COLUMN learning_event_log.importance IS '记忆流重要度 1-5（写入时由 EventImportance 规则定，越高越值得反思/检索）';
```

**Step 2（先写失败测试）：** `EventImportanceTest`
```java
assertThat(EventImportance.of("practice_failed")).isEqualTo(4);
assertThat(EventImportance.of("comeback_solved")).isEqualTo(5);
assertThat(EventImportance.of("resource_consumed")).isEqualTo(1);
assertThat(EventImportance.of("unknown_type")).isEqualTo(1); // 兜底
```
`MemoryStreamMirrorTest`：mock `LearningEventLogRepository`，发布 `new LearningEvent.PracticeFailed(7L,"PY43-KC-016",5L,"WA")` → 验证 `insert(7L, "practice_failed", payloadCaptor, "backend", 4)` 被调用，且 payload 含 `kcCode=PY43-KC-016`、`problemId=5`、`failureKind=WA`。

**Step 3（实现）：**
- `EventImportance`：静态 `Map<String,Integer>`：`practice_failed/practice_abandoned=4`、`comeback_solved/debug_resolved=5`、`practice_succeeded=3`、`tutor_style_engaged/probe_choice_made=2`、`resource_consumed/media_consumed=1`、`profile_bootstrapped=2`、`practice_retried=2`；`of(type)` 未命中返回 `1`。
- 约定 `event_type` 规范名（snake_case，对齐 `EventImportance` key）：每个 `LearningEvent` record → 字符串 + payload Map。
- `MemoryStreamMirror`：对每种 `LearningEvent` 写 `@EventListener`，组 payload Map（含 kcCode/problemId/failureKind/resourceKind 等），调 `repo.insert(userId, type, payload, "backend", EventImportance.of(type))`。**异常吞掉**（记忆镜像失败不得影响主链路，`try/catch` + `log.warn`）。
- `LearningEventLogRepository.insert(...)`：改为 5 形参（加 `int importance`），SQL `INSERT ... (user_id,event_type,payload,source,importance) VALUES (?,?,?::jsonb,?,?)`；更新 `LearningEventController` 调用点：`insert(userId, dto.eventType(), dto.payload(), "frontend", EventImportance.of(dto.eventType()))`。

**Step 4（跑测试）：** `cd backend && mvn -q -o -Dtest='EventImportanceTest,MemoryStreamMirrorTest' test`（Expected: PASS）。
**Step 5（提交）：** `feat(memory): event importance scoring + mirror in-process LearningEvent into memory stream`

**✅ 验收标准 0.1（逐条可验）：**
1. `V134` 在空库 + seed 流程跑通（`./dev.sh` 或 Flyway migrate 无报错）。
2. `EventImportanceTest` 4 个用例全绿；未知类型兜底=1。
3. 发布任一进程内 `LearningEvent` 后，`learning_event_log` 新增一行 `source='backend'` 且 `importance` 与规则一致（`MemoryStreamMirrorTest` 绿）。
4. 前端入库路径（`LearningEventController`）写入的行 importance 非空（=规则值）。
5. 镜像写表抛异常时不影响事件发布（测试：repo.insert 抛 → 事件处理不抛）。
6. `mvn -q -DskipTests compile` 通过；无 `any`/原始类型滥用。

---

## Task 0.2：MemoryRetrieval 检索（近因 × 重要性 × 相关性）+ API

**目标：** 实现可单测的纯函数打分检索，暴露「agent 关于你的笔记本」只读 API。

**Files:**
- Create `backend/.../learner/memory/MemoryScorer.java`（纯静态打分，无依赖）
- Create `backend/.../learner/memory/MemoryRetrievalService.java`
- Create `backend/.../learner/memory/dto/MemoryItemDto.java`
- Create `backend/.../learner/memory/MemoryController.java`
- Modify `LearningEventLogRepository.java`（加 `findRecentForMemory(userId, windowSize)` 返回结构化行：id/eventType/payload(JsonNode)/importance/createdAt）
- Test `MemoryScorerTest.java`、`MemoryRetrievalServiceTest.java`

**Step 1（打分纯函数，先写测试）：** `MemoryScorerTest`
```java
// recency: 越新越高（同重要同相关时新事件排前）
// relevance: focusKc 命中 payload.kcCodes → 1.0；同章 → 0.5；否则 0
// importance: importance/5
// score = 0.4*recency + 0.3*importance + 0.3*relevance
MemoryRow recentLow = row(1, "resource_consumed", List.of("PY43-KC-001"), 1, now);
MemoryRow oldHigh   = row(2, "practice_failed",   List.of("PY43-KC-016"), 4, now.minusDays(10));
// focusKc=PY43-KC-016 时 oldHigh 因相关+重要应超过 recentLow
assertThat(MemoryScorer.score(oldHigh, "PY43-KC-016", now).total())
    .isGreaterThan(MemoryScorer.score(recentLow, "PY43-KC-016", now).total());
// focusKc=null（无聚焦）时按 recency+importance
```
返回 `ScoreBreakdown(double recency, double importance, double relevance, double total)`。`recency = 0.5^(ageDays/14)`（半衰期 14 天，对齐画像 `DECAY_HALF_LIFE_DAYS`）。

**Step 2（service）：** `MemoryRetrievalService.retrieve(userId, focusKc, limit)`：
- 取候选窗口 `findRecentForMemory(userId, 200)`（限制扫描量）；
- 逐行 `MemoryScorer.score(row, focusKc, Instant.now())`；
- 按 `total` 降序取 `limit`（默认 8，clamp 1..30）；
- 映射 `MemoryItemDto{eventId, eventType, summaryZh, kcCodes, createdAt, importance, recencyScore, importanceScore, relevanceScore, totalScore}`，`summaryZh` 由 eventType+payload 拼中文摘要（如「练习失败：PY43-KC-016（WA）」）。

**Step 3（API）：** `MemoryController` `GET /api/learner/memory?focusKc=&limit=`，`requireStudent(user)`（沿用其它 controller 范式），返回 `List<MemoryItemDto>`，仅 `user.userId()` 自己的数据。

**Step 4（跑测试）：** `mvn -q -o -Dtest='MemoryScorerTest,MemoryRetrievalServiceTest' test`。
**Step 5（提交）：** `feat(memory): recency×importance×relevance retrieval + GET /api/learner/memory`

**✅ 验收标准 0.2（Phase 0 总验收）：**
1. `MemoryScorerTest`：近因/相关/重要三类边界用例全绿；公式权重 0.4/0.3/0.3 固化。
2. `focusKc` 命中项的 relevance=1.0、同章=0.5、无关=0（可单测）。
3. `GET /api/learner/memory` 返回 topK 且每项含 `recencyScore/importanceScore/relevanceScore/totalScore` 分解；按 totalScore 降序。
4. `limit` clamp 到 1..30；只返回本人数据（换 userId 拿不到他人记忆）。
5. 非学生角色访问被拒（沿用 `requireStudent`）。
6. `mvn -q -DskipTests compile` + 上述测试全绿。

---

# Phase 1：反思（综合洞察 → 回写画像）

## Task 1.1：graph ReflectionAgent（单次有界 LLM，grounded，fail-fast）

**目标：** 读 topK 记忆 → LLM 综合出 1–3 条**引用 event id**的学情洞察；维度白名单；无证据则空；provider 失败抛（不退化闲聊）。**非 ReAct 循环**：一次调用，无自治回环。

**Files:**
- Create `services/pythontopia-graph/pythontopia_graph/schemas/reflection.py`
- Create `services/pythontopia-graph/pythontopia_graph/agents/reflection_agent.py`
- Create `services/pythontopia-graph/pythontopia_graph/routes/reflection.py`
- Modify `services/pythontopia-graph/pythontopia_graph/main.py`（`app.include_router(reflection_router)`）
- Test `services/pythontopia-graph/tests/test_reflection_agent.py`

**Step 1（schema）：** `schemas/reflection.py`
```python
class MemoryItem(BaseModel):
    event_id: int
    event_type: str
    kc_codes: list[str] = Field(default_factory=list)
    importance: int = Field(ge=1, le=5, default=1)
    created_at: str
    summary: str = ""

class Insight(BaseModel):
    text: str = Field(min_length=2, max_length=200)
    dimension_key: Literal["error_pattern", "cognitive_style", "emotion_persistence",
                           "practical_ability", "knowledge_base"]
    tags: list[str] = Field(default_factory=list)
    evidence_event_ids: list[int] = Field(min_length=1)  # 必引用证据
    confidence: float = Field(ge=0, le=1, default=0.5)

class ReflectionInput(BaseModel):
    user_id: int
    memories: list[MemoryItem] = Field(default_factory=list)

class ReflectionOutput(BaseModel):
    insights: list[Insight] = Field(default_factory=list)
```

**Step 2（先写失败测试）：** `test_reflection_agent.py`
```python
async def test_returns_empty_without_memories_and_no_llm_call():
    spy = CountingProvider()           # chat 被调用即记数
    out = await ReflectionAgent(spy).reflect(ReflectionInput(user_id=1, memories=[]))
    assert out.insights == [] and spy.calls == 0   # 空记忆不调 LLM

async def test_parses_and_filters_insights():
    mems = [MemoryItem(event_id=10, event_type="practice_failed",
                       kc_codes=["PY43-KC-016"], created_at="...", summary="WA")]
    fake = FakeProvider(content_json={"insights": [
        {"text":"列表别名可变性反复出错","dimension_key":"error_pattern",
         "tags":["mutable_alias"],"evidence_event_ids":[10],"confidence":0.8},
        {"text":"无证据洞察","dimension_key":"error_pattern","evidence_event_ids":[],"confidence":0.9},
        {"text":"非法维度","dimension_key":"foo","evidence_event_ids":[10]},
        {"text":"引用了不存在的事件","dimension_key":"error_pattern","evidence_event_ids":[999]},
    ]})
    out = await ReflectionAgent(fake).reflect(ReflectionInput(user_id=1, memories=mems))
    assert len(out.insights) == 1                       # 只保留合法 + 证据⊆输入
    assert out.insights[0].tags == ["mutable_alias"]

async def test_provider_failure_propagates():           # fail-fast
    with pytest.raises(Exception):
        await ReflectionAgent(RaisingProvider()).reflect(ReflectionInput(user_id=1, memories=mems))
```

**Step 3（实现，仿 `IntentRouterAgent`）：**
- System prompt（强约束）：「你是学情反思器，只输出 JSON。基于给定记忆条目综合 1–3 条关于学习模式的洞察。每条**必须**在 `evidence_event_ids` 引用输入记忆里出现过的 event_id，不得编造；`dimension_key` 只能取 {error_pattern, cognitive_style, emotion_persistence, practical_ability, knowledge_base}；证据不足就返回 `{"insights":[]}`。不得输出题目答案/课件页码。」
- 记忆为空 → 直接返回 `ReflectionOutput(insights=[])`，**不调用 provider**。
- `provider.chat(messages, response_format={"type":"json_object"}, temperature=0.2)`；JSON 解析复用 `IntentRouterAgent._strip_json_fence`/`_coerce_json` 思路。
- 过滤：`dimension_key ∈ 白名单`、`evidence_event_ids` 非空且 ⊆ 输入 memory id 集；不满足丢弃；最多保留 3 条。
- provider 抛异常 → 不捕获，向上传播（fail-fast）。

**Step 4（route）：** `routes/reflection.py` 仿 `intent.py`：`_provider(request)` + `@router.post("/agents/reflection", response_model=ReflectionOutput)`；`main.py` include。
**Step 5（跑测试 + 提交）：** `cd services/pythontopia-graph && python -m pytest -q tests/test_reflection_agent.py`；提交 `feat(graph): grounded ReflectionAgent (memory→insight, cites event ids)`。

**✅ 验收标准 1.1：**
1. 空记忆 → 空洞察且 **0 次** LLM 调用。
2. 非法 `dimension_key`、空 `evidence_event_ids`、引用输入外 event_id 的洞察被丢弃。
3. 合法洞察保留 ≤3 条且 tags/confidence 正确解析。
4. provider 抛 → 异常传播（不返回闲聊/占位）。
5. `POST /agents/reflection` 路由可调通（`pytest` 覆盖 + DryRun 下返回空 insights 不报错）。

---

## Task 1.2：反思触发 + 回写画像 + 落库洞察（后端）

**目标：** 攒够 N 个新事件触发一次反思；洞察落 `learner_insight` + 回写画像（source=`REFLECTION`）；无 graph 走规则兜底不抛。

**Files:**
- Create `backend/src/main/resources/db/migration/V135__learner_insight_and_reflection_source.sql`
- Modify `PassiveProfileService.java`（`ALLOWED_REVISION_SOURCES` 加 `"REFLECTION"`）
- Create `backend/.../learner/reflection/LearnerInsightRepository.java` + `dto/LearnerInsightDto.java`
- Create `backend/.../learner/reflection/ReflectionGraphClient.java`（POST `{graphUrl}/agents/reflection`）
- Create `backend/.../learner/reflection/ReflectionService.java`
- Create `backend/.../learner/reflection/ReflectionTrigger.java`（`@EventListener` 计数 + 阈值/冷却）
- Test `ReflectionServiceTest.java`、`ReflectionTriggerTest.java`

**Step 1（迁移）：** `V135`
```sql
-- Purpose: 反思洞察落库 + 允许 profile_revision.source='REFLECTION'。
-- Tables affected: learner_insight（新建）、profile_revision（放宽 source check）
-- Irreversible risk: drop learner_insight 丢历史洞察；source check 回退需移除 REFLECTION。
CREATE TABLE IF NOT EXISTS learner_insight (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  text VARCHAR(300) NOT NULL,
  dimension_key VARCHAR(40) NOT NULL,
  tags JSONB NOT NULL DEFAULT '[]',
  evidence_event_ids BIGINT[] NOT NULL DEFAULT '{}',
  confidence DOUBLE PRECISION NOT NULL DEFAULT 0.5,
  source VARCHAR(16) NOT NULL DEFAULT 'GRAPH',  -- GRAPH | RULE（兜底）
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_learner_insight_user ON learner_insight(user_id, created_at DESC);

ALTER TABLE profile_revision DROP CONSTRAINT IF EXISTS profile_revision_source_check;
ALTER TABLE profile_revision ADD CONSTRAINT profile_revision_source_check
  CHECK (source IN ('INTERVIEW','SUBMISSION','DEBUG','RESOURCE_FEEDBACK','QUEST',
                    'CLASSROOM','EVALUATION','PRACTICE_PASS','PRACTICE_FAIL','DEBUG_FIX','REFLECTION'));
```
> 实施前先 `\d profile_revision` 或搜历史迁移确认现有约束**确切名称**与枚举，以本迁移为准对齐（上面枚举来自 `PassiveProfileService.ALLOWED_REVISION_SOURCES` + 新增 REFLECTION）。

**Step 2（先写失败测试）：**
- `ReflectionTriggerTest`：连发 7 个事件 → `ReflectionService.reflect` **未**被调用；第 8 个 → 被调用 1 次；冷却窗口内再发 8 个 → 不再调用。
- `ReflectionServiceTest`：
  - graph 返回 1 条洞察 → `PassiveProfileService.applyDimensionUpdate(userId, "error_pattern", deltaCaptor)` 被调用且 `delta.source()=="REFLECTION"`、`delta.tagsAdd()` 含洞察 tags；`LearnerInsightRepository.insert(...)` 被调用且 `source="GRAPH"`。
  - graph 抛异常 → 走规则兜底：取最近记忆里最高频 `failureKind`/弱 KC → 产出至多 1 条 `error_pattern` 洞察，`source="RULE"`，**不抛**。
  - graph 返回空 insights → 不回写、不落库、不抛。

**Step 3（实现）：**
- `PassiveProfileService.ALLOWED_REVISION_SOURCES` 增 `"REFLECTION"`。
- `ReflectionGraphClient`：用既有 `GraphServiceProperties.graphUrl()` + `RestClient/WebClient`（与现有 graph 客户端一致），POST `MemoryItem` 列表，解析 `ReflectionOutput`；超时/非 2xx 抛受检异常。
- `ReflectionService.reflect(userId)`（`@Async` 或线程池，避免阻塞事件发布线程）：
  1. `memories = memoryRetrievalService.retrieve(userId, null, 12)`；空则直接 return。
  2. `try { insights = graphClient.reflect(userId, memories) } catch { insights = ruleFallback(memories) }`。
  3. `if insights.isEmpty() return;`
  4. 逐条：`applyDimensionUpdate(userId, ins.dimensionKey(), DimensionDelta.ofTags(0.0 或 +0.02, ins.tags(), "REFLECTION"))` + `insightRepo.insert(userId, ins, sourceTag)`。
  - `ruleFallback`：统计 memories 中 `event_type=practice_failed` 的 `payload.kcCode` 频次，最高者 → 一条 `error_pattern` 洞察，`tags=[kcCode]`，`confidence=0.4`，`evidence_event_ids=`这些事件 id。
- `ReflectionTrigger`：`@EventListener` 监听 `LearningEvent`（基类）；`ConcurrentHashMap<Long, Counter>`，`Counter{int sinceLast; Instant lastReflectedAt}`；阈值 `N=8`、冷却 `1h`（`@Value` 可配 `pythontopia.reflection.threshold/cooldownMinutes`）；命中即提交 `reflectionService.reflect(userId)` 到 `SseRelayExecutor` 同款线程池或新 `@Async`。**进程内状态**（单 worker，符合项目约束，注释说明）。

**Step 4（跑测试 + 空库迁移验证）：** `mvn -q -o -Dtest='ReflectionTriggerTest,ReflectionServiceTest' test`；并跑一次空库 Flyway 迁移确认 `V134/V135` OK。
**Step 5（提交）：** `feat(reflection): triggered reflection writes back profile + persists insights, offline rule fallback`

**✅ 验收标准 1.2：**
1. 第 8 个事件才触发一次反思；冷却内不重复触发（`ReflectionTriggerTest` 绿）。
2. graph 有洞察 → 回写画像 `source=REFLECTION`（可在 `profile_revision` 查到）+ `learner_insight` 落库 `source=GRAPH`。
3. graph 不可用 → 规则兜底产出 `source=RULE` 洞察，**全程不抛**、不阻塞事件主链路。
4. graph 返回空 → 不回写不落库。
5. `V135` 空库迁移成功；`profile_revision` 接受 `REFLECTION`；旧 source 仍合法。
6. 反思在独立线程执行，不阻塞 `LearningEvent` 发布（事件发布同步返回）。

---

## Task 1.3：洞察读 API + 进画像消费

**Files:**
- Create `backend/.../learner/reflection/InsightController.java`（`GET /api/learner/insights?limit=`，requireStudent，仅本人）
- Modify `services/pythontopia-graph/.../schemas/resource.py`（`ProfileSnapshot` 加 `recent_insight: str = ""`）
- Modify `agents/personalization.py`（有 `recent_insight` 时 directive 追加一行「学情洞察：{recent_insight} → 据此针对性讲解/出题」）
- Modify 后端资源请求装配处（把最新 1 条 `learner_insight.text` 填入 `profile_snapshot.recent_insight`）
- Test `InsightControllerTest.java`、扩展 `services/pythontopia-graph/tests/test_personalization.py`

**Step 1–3：** TDD：(1) `test_personalization`：`ProfileSnapshot(recent_insight="X 反复出错")` → directive 含「学情洞察」与该文本；空则不含。`InsightControllerTest`：插 2 条洞察 → API 倒序返回、含 `evidenceEventIds`、仅本人。(2) 跑失败 (3) 实现。
**Step 4–5：** 绿 + 提交 `feat(reflection): expose insights + fold latest insight into personalization directive`

**✅ 验收标准 1.3（Phase 1 总验收）：**
1. `GET /api/learner/insights` 倒序返回洞察，含 `text/dimensionKey/tags/evidenceEventIds/confidence`，仅本人。
2. `ProfileSnapshot.recent_insight` 非空时进资源/答疑个性化 directive；空时行为不变（不破坏既有 6 资源生成）。
3. `pytest`（`test_personalization`）+ `mvn test`（`InsightControllerTest`）绿。

---

# Phase 2：记忆条件化生成（让记忆真正改变产物）

## Task 2.1：检索要点注入资源 / 答疑 prompt

**目标：** 生成资源 / 答疑前取 topK 记忆要点，折进 prompt，使产物贴合真实学情。

**Files:**
- Modify `agents/personalization.py`（`personalization_messages` 增可选 `memory_highlights: list[str]` → 折成「该生近期学情：A；B；C」一条 system）
- Modify 后端答疑/资源装配（`TutorService` / `ResourceWorkshopService`）：生成前调 `MemoryRetrievalService.retrieve(userId, focusKc, 5)`，把 `summaryZh` 列表透传 graph（`context` 追加或请求体新增字段）
- Test 扩展 `test_personalization.py`、相关后端 service 测试

**实现要点：** 仅**追加** system 提示，不改 agent 兜底/结构化输出（与 `personalization.py` 现有注释一致，避免副作用）。后端注入走 `context`/`focus_hint` 既有通道，避免改 graph 契约。

**✅ 验收标准 2.1：**
1. 传入 `memory_highlights` 时 directive 出现「近期学情」+ 要点；为空时 directive 不变。
2. 答疑/资源装配确实调用了记忆检索（后端测试断言调用 + 注入 context）。
3. 不破坏既有 6 类资源结构化输出（`pytest` 资源相关用例仍绿）。

## Task 2.2：洞察驱动路径重规划

**Files:**
- Modify `agents/path_planner_agent.py` 或 `LearningPathService`：存在 `error_pattern` 新增高频坑（来自最新洞察/画像 tags）时，把对应 KC 的「练/复盘」节点**前置/加权**
- Test：洞察存在 → 路径节点顺序/优先级变化；无洞察 → 回退原排序

**✅ 验收标准 2.2（Phase 2 总验收）：**
1. 给定一条 `error_pattern` 洞察（弱 KC=X）→ 规划结果里 X 的练/复盘节点顺序前移（可断言）。
2. 无洞察 → 路径与改造前一致（回归不破坏）。
3. `python -m pytest -q`（graph）+ 相关后端测试绿。

---

# Phase 3：小镇涌现协作（Layer B，叙事 + 前端可展示）

> 复用为先：fork `AgentOfficeScene.vue` 的角色/接力棒/气泡逻辑与已审计 LPC NPC 立绘，**不新增二进制素材**；reduced-motion 友好；窄屏自适应；`--pyt-*` 设计令牌。

## Task 3.1：「Agent 的笔记本」——记忆流可视

**Files:**
- Create `frontend/src/api/memory.ts`（`getMemory(focusKc?, limit?)` → `MemoryItemDto[]`，DTO 与后端对齐）
- Create `frontend/src/components/agents/AgentNotebook.vue`（时间线列「关于你的观察」：中文摘要 + 重要度星级（importance 颗星）+ 可展开 recency/importance/relevance 分数条；空态友好）
- 挂入口：画像屋 / 工坊已有面板加一个「📓 Agent 笔记本」按钮
- Test `frontend/tests/components/AgentNotebook.spec.ts`

**TDD：** mock `getMemory` 返回 2 条 → 断言渲染 2 条、星级=importance、点击展开显示三项分数；返回空 → 渲染空态文案。

**✅ 验收标准 3.1：**
1. 打开笔记本看到真实记忆条目（摘要 + 星级），点开有三项分数分解。
2. 空态有友好文案，不报错。
3. `npm run typecheck` + `vitest`（AgentNotebook.spec）+ `eslint` 绿；无 `any`。

## Task 3.2：「顿悟」洞察动画

**Files:**
- Create `frontend/src/api/insight.ts`（`getInsights(limit?)`）
- Modify `frontend/src/stores/companion.ts`（加 `latestInsight: Ref<...>` + `loadInsight()`；节流，不重复打扰）
- Modify `frontend/src/components/companion/CompanionDock.vue`（有新洞察时小人头顶冒一次「💡 我发现你在 X 上…」气泡 `data-testid="companion-insight"`，可点开看 evidence 事件，可关闭）
- Test 扩展 `frontend/tests/stores/companion.spec.ts`、`CompanionDock.spec.ts`

**TDD：** mock `getInsights` 有/无 → 有则气泡渲染含洞察文本、可关闭后不再弹；无则不渲染。

**✅ 验收标准 3.2：**
1. 有新洞察 → 冒一次可关闭「顿悟」气泡，能查看 evidence；无 → 不打扰。
2. 关闭后本次会话不再弹（去抖）。
3. `vitest` 覆盖有/无两分支；typecheck/eslint 绿。

## Task 3.3：「Agent 串门」——涌现协作可视

**Files:**
- Modify `frontend/src/components/workshop/AgentOfficeScene.vue`：当出现反思/交接相关 trace（如新 `reflection` node 或既有 handoff）时，让一个 agent NPC **走到**另一工位演「通报」气泡（"这位同学在 X 反复卡壳，重点照顾"），复用现成接力棒/走位/气泡动画
- （如需）后端答疑/资源流在注入记忆/洞察时 emit 一条 `trace{node:"reflection"/"handoff", ...}`；确认 Java SSE 代理原样转发
- Test 扩展 `AgentOfficeScene` 的 spec：注入含 `reflection`/handoff 的 traces → 断言串门元素渲染

**✅ 验收标准 3.3：**
1. 注入相关 trace → 出现「串门通报」动画/气泡（从一个工位到另一个）。
2. 无相关 trace → 退回普通流水线展示（不回归）。
3. reduced-motion 下无动画；`vitest` 绿。

## Task 3.4：「NPC 主动找你」——主动陪伴升级为角色行为

**Files:**
- Modify `frontend/src/components/companion/CompanionDock.vue` / 小镇层：把到期复习 nudge 从「弹窗」升级为「NPC 走过来敲你」的角色化呈现（一条可关闭、可一键进矿洞/去练）
- Test 扩展现有 nudge 用例

**✅ 验收标准 3.4（Phase 3 总验收）：**
1. 到期/反复错时 NPC 角色化主动出现、可一键跳转、可关闭、不打扰。
2. 四件套（笔记本/顿悟/串门/主动）叙事连贯，前端仍是「轻组件 + 复用剧场」，无新增独立仪表盘。
3. `npm run typecheck` + 相关 `vitest` + `npx eslint` 全绿；无 `any` 扩散。

---

## 一条龙演示故事线（给评委）
1. 小明主线卡「列表推导式」（看→学→练→测），反复错 → 事件落记忆流（importance=4）。
2. 攒够阈值 → 馆员/总管「顿悟」：综合「小明在'可变对象别名'反复栽」（引用 event id）→ 回写画像。
3. 下次找工坊 → 事务所剧场里 agent **串门通报**该洞察 → 设计师出**靶向**讲义/题；答疑也带记忆要点。
4. 点开 agent **笔记本**全程可溯源；教师端（已做的 copilot）看全班该 KC 热力一键生成补救包。
→ 评委看到：真·多智能体协同 + 依短板动态调整 + 效果评估，且证据可溯、无幻觉。

## 成本 / 风险 / 回退
- **LLM 成本**：仅「反思」新增周期调用 → N 事件阈值 + 冷却 + 去抖；检索/重要度零 LLM。
- **离线/无 key**：反思走规则兜底（最高频 failureKind/弱 KC → error_pattern tag），`source=RULE`，演示链路不断；graph 不可用 fail-fast 但 ReflectionService 兜底不抛。
- **防幻觉红线**：每条洞察强制引用 `evidence_event_ids`，维度白名单，无证据则空。
- **不启用 ReAct 循环**：反思单次有界（触发→一次综合→落库），非自治循环。
- **SSE 透传**：Layer B 若新增 `reflection` trace，确认 Java 代理转发；不透传则前端降级为普通进度，不报错。
- **进程内反思计数**：依赖单 worker（项目约定）；多 worker 需迁到 Redis/DB，已注释标注。
- **不做**：完整 Smallville 自由模拟、无教育目的装饰、独立仪表盘、前端散 `any`。

## 执行交接
- 建议独立 worktree；**Phase 0/1 先做且可独立交付**（记忆流 + 反思即具「依短板动态调整 + 效果评估」硬价值）。
- 每个 Task 完成即跑对应 `pytest/mvn/typecheck/vitest/eslint` 并提交；Phase 末跑该阶段总验收清单。
- 迁移改动（`V134/V135` + `profile_revision_source_check` 放宽）须「空库初始化 + 导入 seed」验证。
- 实施前确认 `profile_revision_source_check` 的**确切约束名与现有枚举**，以 `V135` 对齐。
