# NPC 长期记忆 + 费曼反演 · 详细设计（实施级）

> 配套 `2026-06-09-npc-memory-and-feynman-tutor-plan.md`。本文给到**文件清单 / 迁移 SQL / 接口签名 / 关键实现 / 验收 / 测试清单**，可直接照做。
> 实施纪律：**每个 Task 先写测试再实现，小步提交**；签名/SQL 为建议，落地时以「先写测试」为准微调。

## 迁移总览（最新 V139 → 新增 V140–V142）
| 版本 | 文件 | 内容 |
| --- | --- | --- |
| V140 | `V140__memory_embedding.sql` | pgvector 扩展 + `memory_embedding` |
| V141 | `V141__npc_memory.sql` | `npc_memory`(含 superseded_by) + `npc_persona` + `npc_affinity` |
| V142 | `V142__feynman_session.sql` | `feynman_session` + `feynman_turn` |

## 阶段依赖图
```
P0(向量地基) ─▶ P1((user,npc)记忆+人格) ─▶ P2(去重/时效/affinity) ─▶ P3(NPC记忆对话)
                                                                       └─▶ P4(费曼tutee) ─▶ P5(判题回流) ─▶ P6(前端/演示)
```

---

# Phase 0 · 记忆语义化地基（pgvector + 真实向量）

### 文件清单
- 新增 `backend/src/main/resources/db/migration/V140__memory_embedding.sql`
- 新增 `backend/src/main/java/com/pythontopia/learner/memory/EmbeddingClient.java`
- 新增 `backend/src/main/java/com/pythontopia/learner/memory/MemoryEmbeddingRepository.java`
- 改 `backend/.../learner/memory/MemoryScorer.java`（加语义重载）
- 改 `backend/.../learner/memory/MemoryRetrievalService.java`（加 `queryText` 路径）
- 改 事件镜像入库处（`MemoryStreamMirror` 或等价组件）：异步算 embedding 落库
- 新增 `services/pythontopia-rag/pythontopia_rag/routes/embed.py` + 改 `main.py` include
- 测试：`MemoryScorerTest`(扩展)、`EmbeddingClientTest`、`MemoryRetrievalSemanticIT`(Testcontainers pgvector)、`services/pythontopia-rag/tests/test_embed_route.py`

### 迁移 SQL
```sql
-- Purpose: 记忆流语义向量存储，支撑 NPC 记忆与费曼误解的语义相关性检索。
-- Tables affected: 启用 vector 扩展；新建 memory_embedding。
-- Irreversible risk: 回滚需 DROP TABLE + 可选 DROP EXTENSION（向量丢失，可由事件重算）。
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS memory_embedding (
  event_id   BIGINT PRIMARY KEY REFERENCES learning_event_log(id) ON DELETE CASCADE,
  embedding  vector(512) NOT NULL,
  model      VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_memory_embedding_cos
  ON memory_embedding USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```
> 维度固定 512，对齐 `BgeSmallEmbedder`（`PYT_RAG_EMBEDDER=bge`）。`hash`(1024)/DryRun 不写向量 → 检索回退 KC 匹配。

### 接口签名
```java
public interface EmbeddingClient {                       // 失败/离线 → Optional.empty()
  Optional<float[]> embed(String text);
  List<Optional<float[]>> embedBatch(List<String> texts);
}
public interface MemoryEmbeddingRepository {
  void upsert(long eventId, float[] embedding, String model);
  Optional<float[]> find(long eventId);
  List<ScoredEvent> searchByCosine(long userId, float[] query, int limit); // pgvector <=> 余弦
  record ScoredEvent(long eventId, double similarity) {}
}
// MemoryScorer 新增重载：relevance = max(kcRelevance, semanticSim)
public static ScoreBreakdown score(MemoryRow row, String focusKc,
    Set<String> sameChapterKcCodes, double semanticSim, Instant now);
// MemoryRetrievalService 新增
List<MemoryItemDto> retrieve(Long userId, String focusKc, String queryText, int limit);
```
```python
# services/pythontopia-rag/.../routes/embed.py
class EmbedRequest(BaseModel): texts: list[str]
class EmbedResponse(BaseModel): model: str; dim: int; vectors: list[list[float]]
@router.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest, request: Request) -> EmbedResponse: ...  # select_embedder()
```

### 关键实现
- 镜像入库异步算 embedding（线程池/`@Async`），失败 `log.warn` 吞掉，**不阻断事件发布**。
- `queryText` 为空 → 完全走现状（零回归）；非空且有向量 → 余弦并入 relevance。

### 验收
- [ ] 空库 Flyway 迁移零报错；`vector` 扩展可用，`memory_embedding` 就位。
- [ ] 语义近、KC 不同的事件，`queryText` 查询 relevance>0（语义生效）。
- [ ] hash/DryRun 无向量时旧路径不破坏；`GET /api/learner/memory` 行为不变。
- [ ] embedding 写失败不影响事件发布。

### 测试
- `MemoryScorerTest`：`score(...,semanticSim=0.8,...)` → relevance≥0.8；`semanticSim=0` → 回退 KC 匹配（现有用例全绿）。
- `EmbeddingClientTest`：超时→empty；维度=512；批量顺序一致。
- `MemoryRetrievalSemanticIT`（`pgvector/pgvector` 镜像）：插 3 事件+向量 → 余弦 topK 顺序正确。
- `test_embed_route.py`：bge/hash 维度；空 texts；DryRun 不抛。

---

# Phase 1 · (user, NPC) 作用域 + NPC 核心人格

### 文件清单
- 新增 `V141__npc_memory.sql`
- 新增 `backend/.../npc/NpcMemoryRepository.java` / `NpcMemoryService.java`
- 新增 `backend/.../npc/NpcPersona.java` / `NpcPersonaRepository.java` / `NpcPersonaSeeder.java`
- 新增 `backend/.../npc/NpcAffinityRepository.java` / `NpcAffinityService.java`
- 改 事件监听处：按事件类型 `remember` 到对应 NPC
- 测试：`NpcMemoryServiceTest`、`NpcPersonaSeederTest`、`NpcAffinityServiceTest`

### 迁移 SQL（含 Phase 2 字段，避免多一次迁移）
```sql
-- Purpose: NPC 个性化记忆/人格/好感度，支撑「NPC 记住你」。
-- Tables affected: 新建 npc_memory / npc_persona / npc_affinity。
-- Irreversible risk: drop 丢失 NPC 记忆与好感度（可由 learning_event_log 重建关联）。
CREATE TABLE IF NOT EXISTS npc_memory (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  npc_key VARCHAR(40) NOT NULL,
  event_id BIGINT NOT NULL REFERENCES learning_event_log(id) ON DELETE CASCADE,
  salience DOUBLE PRECISION NOT NULL DEFAULT 0.5,
  superseded_by BIGINT NULL REFERENCES npc_memory(id) ON DELETE SET NULL,  -- Phase 2
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_recalled_at TIMESTAMPTZ NULL,
  UNIQUE (user_id, npc_key, event_id)
);
CREATE INDEX IF NOT EXISTS idx_npc_memory_user_npc
  ON npc_memory(user_id, npc_key, created_at DESC);
CREATE TABLE IF NOT EXISTS npc_persona (
  npc_key VARCHAR(40) PRIMARY KEY,
  display_name VARCHAR(80) NOT NULL,
  core_md TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS npc_affinity (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  npc_key VARCHAR(40) NOT NULL,
  score DOUBLE PRECISION NOT NULL DEFAULT 0,
  tier SMALLINT NOT NULL DEFAULT 0,   -- 0..4
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, npc_key)
);
```

### 接口签名
```java
public interface NpcMemoryService {
  void remember(long userId, String npcKey, long eventId, double salience);
  List<MemoryItemDto> recall(long userId, String npcKey, String focusKc, int limit);
}
public interface NpcPersonaService {
  NpcPersona load(String npcKey);
  void seedFromMeta();           // 启动从 data/npcs/<key>/meta.json 种子（幂等）
}
public interface NpcAffinityService {
  void bump(long userId, String npcKey, double delta);
  int tier(long userId, String npcKey);   // 0..4
}
```

### 事件 → NPC 归属映射（`remember` 派发）
| 事件 | NPC | 含义 |
| --- | --- | --- |
| practice_* | `coach_q`(出题官) | 记住你做过/错过的题 |
| resource_consumed | `librarian_doc`(馆员) | 记住你查过的资料 |
| debug_resolved | `foreman_bug`(矿洞工头) | 记住你修过的 bug |
| evaluation | `sage_review`(复盘贤者) | 记住你的复盘 |
| profile_bootstrapped | `mayor_py`(镇长/总管) | 记住你是谁 |
| taught_ai_*(P5) | `student_a`(AI 学弟) | 记住你教过它什么 |

### 验收
- [ ] 换 `npcKey` 得不同记忆视角；换 `userId` 拿不到他人记忆（隔离）。
- [ ] 每 NPC 有 `core_md`，种子幂等；跨会话稳定。
- [ ] `recall` 空记忆返回空、不抛。

### 测试
- `NpcMemoryServiceTest`：隔离 / topK / salience 排序 / 空态。
- `NpcPersonaSeederTest`：meta→persona 种子 + 重复 seed 不重复插。
- `NpcAffinityServiceTest`：bump 累积 + tier 分档边界。
- 空库迁移 + `mvn -q test`。

---

# Phase 2 · 自编辑去重 + 时效 + 关系演变

### 文件清单
- 改 `NpcMemoryService.remember`（写前去重）+ 新增 `supersede`
- 新增 `NpcMemoryDedup.java`（余弦近重判定，纯函数）
- 测试：`MemoryDedupTest`、`MemorySupersedeTest`、`AffinityTierTest`

### 接口签名
```java
// remember 内部：先 dedup 再决定 insert / bump
void supersede(long oldMemoryId, long newMemoryId);     // 旧记忆标 superseded_by，保留历史
// NpcMemoryDedup
static boolean isNearDuplicate(float[] a, float[] b, double threshold); // 默认 0.92
```

### 关键实现
- 写入前：对同 (user,npc) 近窗口记忆做余弦近重检测（用 `memory_embedding`）；命中 → `salience += 0.1`（封顶 1.0）+ 刷新 `last_recalled_at`，**不新增行**。
- 矛盾事实：`掌握 KC-X` 入库时，把该 (user,npc,KC) 的 `在 KC-X 反复错` 记忆 `supersede`（不物理删）。
- affinity：`remember` 成功 +0.05；成功教学(P5) +0.2；tier = floor(score/1.0) clamp 0..4。

### 验收
- [ ] 近重复不堆叠（计数可断言）；salience 上升。
- [ ] 矛盾事实：旧标 `superseded_by`，历史可查、不返回在 recall。
- [ ] affinity 单调、tier 分档正确。

### 测试
- `MemoryDedupTest`：余弦 0.93 命中 / 0.91 不命中（阈值边界）。
- `MemorySupersedeTest`：supersede 后 recall 不含旧、历史查询含旧。
- `AffinityTierTest`：累积→tier 跃迁；`mvn -q test`。

---

# Phase 3 · 记忆注入 NPC 对话 + 玻璃盒

### 文件清单
- graph：新增 `agents/npc_dialog_agent.py`、`schemas/npc_dialog.py`、`routes/npc_dialog.py`；改 `main.py`
- backend：新增 `npc/NpcDialogService.java`、`NpcDialogController.java`、`NpcDialogGraphClient.java`
- frontend：新增 `components/npc/NpcMemoryBadge.vue`、`api/npc.ts`；改 NPC 对话面板
- 测试：`test_npc_dialog_agent.py`、`NpcDialogServiceTest`、`NpcMemoryBadge.spec.ts`

### 接口签名
```python
class RecalledMemory(BaseModel): event_id: int; summary: str; kc_codes: list[str] = []
class Turn(BaseModel): role: str; content: str
class NpcDialogInput(BaseModel):
  npc_key: str; persona_core: str
  memories: list[RecalledMemory] = []
  user_message: str; history: list[Turn] = []
class NpcDialogOutput(BaseModel):
  reply_md: str; cited_event_ids: list[int] = []; grounded: bool = False
class NpcDialogAgent:
  def __init__(self, provider: LlmProvider) -> None: ...
  async def reply(self, inp: NpcDialogInput) -> NpcDialogOutput: ...  # DryRun→静态兜底标记
```
```java
public interface NpcDialogService {
  NpcDialogResponse talk(long userId, String npcKey, String message);  // recall→graph→落库→bump affinity
}
// POST /api/npc/{npcKey}/dialog  (requireStudent)
```

### 关键实现
- prompt：人格口吻 + 注入 `memories`（要求引用 event_id）+ 「不泄答、不编造课件页」；`safety_critic` 复用。
- provider 不可用 → 回退该 NPC 静态 `dialog.json`（`grounded=false`，演示不断）。
- 落 `npc_dialog_state`（V101）；`cited_event_ids` 回填前端「它记得你」徽章。

### 验收
- [ ] 对「连错两题的你」与「刚通关的你」回复不同，且引用真实 event_id。
- [ ] graph 挂 → 静态兜底不崩。
- [ ] 不输出练习完整答案（safety 拦）。

### 测试
- `test_npc_dialog_agent`：有记忆→含要点+cited_event_ids；空记忆→通用；provider 抛→兜底（grounded=false）；泄答词被拦。
- `NpcDialogServiceTest`：recall→graph→落库链 + affinity bump；SSE（若启用）透传锁定。
- `NpcMemoryBadge.spec.ts`：徽章渲染 + 空态 + reduced-motion。

---

# Phase 4 · 费曼反演 AI 学弟（镜像你的错误）

### 文件清单
- 新增 `V142__feynman_session.sql`
- graph：`agents/feynman_tutee_agent.py`、`schemas/feynman.py`、`routes/feynman.py`；改 `main.py`
- backend：`feynman/MisconceptionExtractor.java`、`FeynmanSessionService.java`、`FeynmanController.java`、`FeynmanGraphClient.java`、`FeynmanRepository.java`
- 测试：`test_feynman_tutee_agent.py`、`MisconceptionExtractorTest`、`FeynmanSessionServiceTest`

### 迁移 SQL
```sql
-- Purpose: 费曼反演会话——学生教一个镜像其错误的 AI 学弟。
-- Tables affected: 新建 feynman_session / feynman_turn。
-- Irreversible risk: drop 丢失教学会话历史（可重开）。
CREATE TABLE IF NOT EXISTS feynman_session (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kc_code VARCHAR(40) NOT NULL,
  tutee_state JSONB NOT NULL DEFAULT '{}',   -- {misconceptions:[{tag,evidence_event_ids,resolved}]}
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',-- OPEN|TAUGHT|JUDGED_PASS|JUDGED_FAIL
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS feynman_turn (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES feynman_session(id) ON DELETE CASCADE,
  role VARCHAR(8) NOT NULL,        -- TUTEE|STUDENT
  content TEXT NOT NULL,
  taught_fix TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_feynman_session_user
  ON feynman_session(user_id, created_at DESC);
```

### 接口签名
```java
public interface MisconceptionExtractor {
  List<Misconception> extract(long userId, String kcCode);   // 真实错误史→误解集，每条带 evidenceEventIds
  record Misconception(String tag, List<Long> evidenceEventIds, boolean resolved) {}
}
public interface FeynmanSessionService {
  FeynmanSession start(long userId, String kcCode);          // tutee 初始犯错
  FeynmanTurn teach(long sessionId, String explanation);     // 学生教 → tutee 更新
}
// POST /api/feynman/sessions  /  POST /api/feynman/sessions/{id}/teach
```
```python
class Misconception(BaseModel): tag: str; evidence_event_ids: list[int]; resolved: bool = False
class FeynmanTuteeInput(BaseModel):
  kc_code: str; misconceptions: list[Misconception]
  student_teaching: str | None = None; history: list[Turn] = []
class FeynmanTuteeOutput(BaseModel):
  reply_md: str; updated_misconceptions: list[Misconception]; produced_code: str | None = None
class FeynmanTuteeAgent:
  async def step(self, inp: FeynmanTuteeInput) -> FeynmanTuteeOutput: ...
```

### 关键实现
- `MisconceptionExtractor` 来源：`learning_event_log`(practice_failed.failureKind) + Bug 矿洞 errorbook + 画像 `error_pattern`；**只用真实数据**，无错误史→空集（不编造）。
- tutee prompt：持续犯指定误解、问「为什么/怎么改」、**被正确教导才改**（Reflect-Respond 风格）；DryRun → 规则版误解脚本兜底。

### 验收
- [ ] 学弟初始错 = 该生真实易错点（溯源 event_id），非通用错。
- [ ] 正确教→`resolved=true`+产出代码；错误教→不改。
- [ ] 无网/无 key → 规则兜底不崩。

### 测试
- `test_feynman_tutee_agent`：注入误解→犯对应错；正确教→改；错误教→不改；provider 抛→兜底。
- `MisconceptionExtractorTest`：真实事件→误解集；无错误史→空。
- `FeynmanSessionServiceTest`：start/teach 落库 + 状态机。

---

# Phase 5 · 真判题裁定 + 评估回流

### 文件清单
- backend：新增 `feynman/FeynmanJudgeService.java`；改 `LearningEvent`（加 TaughtAiSucceeded/Failed）、`EventImportance`、监听装配；改 `safety_critic`（防作弊）
- 测试：`FeynmanJudgeIT`、`FeynmanAntiCheatTest`

### 接口签名
```java
public interface FeynmanJudgeService {
  Verdict judge(long sessionId);   // 取 tutee produced_code → SubprocessPythonSandbox 跑隐藏用例
}
// LearningEvent sealed 新增：
record TaughtAiSucceeded(long userId, String kcCode, long sessionId) implements LearningEvent {}
record TaughtAiFailed(long userId, String kcCode, long sessionId) implements LearningEvent {}
// EventImportance：taught_ai_succeeded=5, taught_ai_failed=3
```

### 关键实现
- pass → publish `TaughtAiSucceeded` → `MemoryStreamMirror`(入流) + `MasteryEvaluator`(难度加权↑) + 画像(`practical_ability`↑)；fail → `TaughtAiFailed`(标 error_pattern)。
- 防作弊：教学输入过 `safety_critic`，拦「直接给完整代码/answer_key」→ 不计成功。

### 验收
- [ ] 学弟代码过隐藏用例 → 成功事件落库 + 该 KC 掌握度 delta>0（可断言）。
- [ ] 贴答案被拦、不计成功。
- [ ] 事件可溯源（session_id + event_id）。

### 测试
- `FeynmanJudgeIT`（端到端）：误解→教→产代码→判题→事件→掌握度 delta。
- `FeynmanAntiCheatTest`：贴答案被拦。
- 5 类提交回归（AC/WA/CE/TLE/越权）不破坏既有判题。

---

# Phase 6 · 前端 / 演示

### 文件清单
- 新增 `frontend/src/components/feynman/FeynmanPanel.vue`、`api/feynman.ts`、`stores/feynman.ts`
- 改 NPC 对话面板挂 `NpcMemoryBadge.vue`；复用既有玻璃盒组件
- 改 `app/router.ts`（费曼入口）+ 复盘大厅「测」入口
- 测试：`FeynmanPanel.spec.ts`、`NpcMemoryBadge.spec.ts`(P3 已建则扩展)

### 关键实现
- 费曼面板：学弟对话 + 学生「教学」输入 + 学弟改写代码 + 判题结果动画 + 玻璃盒（误解→你的教学→判题→证据）。
- NPC 记忆条：可展开 recency/importance/relevance + event 来源；`--pyt-*` 令牌、`prefers-reduced-motion`、窄屏单列。

### 验收
- [ ] 一条龙演示跑通（见 plan §演示故事线），全程可溯源、零假数据。
- [ ] reduced-motion 无动画；窄屏可用。

### 测试
- `FeynmanPanel.spec.ts` / `NpcMemoryBadge.spec.ts`：渲染 + 空态 + 判题结果分支。
- `npm run typecheck && npm run lint && vitest --run` 绿。

---

## 全局验证命令（每阶段末）
```bash
# 后端
cd /home/cypress/Pythontopia/backend && mvn -q -DskipTests compile && mvn -q test
# graph / rag
cd /home/cypress/Pythontopia/services/pythontopia-graph && python -m pytest -q
cd /home/cypress/Pythontopia/services/pythontopia-rag   && python -m pytest -q   # 需 Python>=3.11
# 前端
cd /home/cypress/Pythontopia/frontend && npm run typecheck && npm run lint && npx vitest --run
# 迁移：空库初始化 + seed 验证（含 V140 pgvector 扩展）
```

## 红线复述（实施全程）
- 不启用 ReAct；新增 LLM/周期调用必有无网/无 key 兜底；误解/记忆 100% 真实数据（event_id 溯源）零合成；不泄答；业务层无 `any`；迁移空库验证；不污染 git global；不擅自 push。

---

## 附录 · 可从 Alethicode 抽取的 API/模块（缺啥从这拿）

> 路径前缀 `/home/cypress/Alethicode/`。抽取时**保留来源说明**（AGENTS.md「Alethicode 来源模块」约定）。已逐文件核实。

| 我方待造能力 | Alethicode 来源 | 抽取 / 适配要点 |
| --- | --- | --- |
| **P0 embedding client/端点** | `services/alethicode-rag/app/rag/embeddings.py`（`build_embedding_func` / `build_embedder(settings)` → `async embed(text)->list[float]`，带 `.dim/.model`）+ `app/config.py`（`RagSettings.EMBEDDING_*`） | 现成 **智谱 embedding-3 / 2048 维 / OpenAI 兼容**（`open.bigmodel.cn/api/paas/v4`）。抽其 OpenAI 兼容 embedding 调用填我方 `/embed`。 |
| **P0 pgvector/halfvec 索引范式** | `RagSettings.vector_index_type=HNSW_HALFVEC` + `hnsw_m/ef`（config.py 注释明示「2048 维超 pgvector HNSW 2000 维上限，必须 halfvec」） | **修正 V140**：若用 embedding-3(2048) 则 `embedding vector(512)` → `embedding halfvec(2048)` + `USING hnsw (embedding halfvec_cosine_ops)`，不能用普通 ivfflat/vector。 |
| **P3/4 句子级语义接地** | `services/qa-graph/app/nodes/token_grounding_verifier.py` | **纯规则、零 LLM**：拆句→token 覆盖率→grounding_score→hallucination_score→trace。直接复用为 NPC 对话/费曼教学「每句可溯源」审查（把 `retrieval_hits` 换成 recalled memories / 课件页）。fail-soft 不改写答案。**比原计划「P3.4 接 LLM entailment」更省更稳。** |
| **P3/4 防幻觉全套** | `qa-graph/app/nodes/{grounding_critic,hallucination_detector,synthesizer,auditor}.py` | 你方 graph 已有同名节点（本就抽自此）；NPC/费曼新产出复用同一审查链。 |
| **P3/4 对话 agent 范式** | `services/tutor-graph/app/nodes/{chat,dispatch_generative,coach_plan,diagnosis,evidence}.py`、`clients/llm_client.py`、`graph/state.py`、`nodes/prompts/learner_block.py` | LangGraph 节点/对话/状态机/learner-block prompt 范式，作 `npc_dialog_agent.py` / `feynman_tutee_agent.py` 的结构骨架。 |
| **P5 判题** | 你方 `SubprocessPythonSandbox`（本就抽自 Alethicode `service/submission`） | 无需再抽，直接复用。 |
| **记忆流本体** | ⚠️ **Alethicode 无对应模块**（其 Redis/Memgraph 是视频流缓存与知识图谱，非 learner 记忆） | 记忆流为 Pythontopia 原创（建在 Generative Agents 上），**无可抽源**——保持自研。 |

### 因 Alethicode 现状的两点决策
1. **embedding 维度二选一**（接口按 `build_embedder` 抽成**可换源 client**）：
   - **选 A**（低摩擦/离线/零成本，默认）：`BgeSmallEmbedder` 512 维 + `vector(512)` + ivfflat（我方现成）。
   - **选 B**（抽 Alethicode/生产级）：智谱 embedding-3 2048 维 + **`halfvec(2048)` + HNSW**（需 `EMBEDDING_API_KEY`）。
   - **建议**：默认 bge-512 离线兜底，有 key 时切 embedding-3；**V140 列类型随所选维度定**（512→`vector` / 2048→`halfvec`）。
2. **语义接地直接抽 `token_grounding_verifier`**（零 LLM），替换原 P3.4 的「LLM entailment」方案——更省 token、更稳、更契合防幻觉基因。
