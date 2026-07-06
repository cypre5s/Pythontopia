# Pythontopia · NPC 长期记忆 + 费曼反演(AI 学弟) 实现计划

> **状态**：实施蓝图（2026-06-09）· 对标软件杯 A3「个性化资源生成与学习多智能体系统」
> **范围**：统一解决两件事——①「NPC 记不住用户」②创新点 C「费曼反演（教一个镜像你错误的 AI 学弟）」。二者共用同一记忆底座，故合并为一份计划。
> **实施方式**：用 `executing-plans` / `subagent-driven-development`，**每个 Task 先写测试再实现，频繁提交**；每阶段「验收/测试」全绿才算完成。
> **窄化提示**：若只想先做记忆（不做费曼），做 Phase 0–3 即可独立交付价值（NPC 记住你）；Phase 4–6 为创新加分。

---

## 0. 北极星与赛题落点

**北极星**：让小镇的 NPC 从「静态脚本」升级为「**记得你、随你成长、可被追问**的角色」；并在此之上立起一个**会犯你同款错误、要你去教会它**的 AI 学弟（learning-by-teaching）。

| A3 评分点 | 本计划落点 |
| --- | --- |
| ① 对话式 ≥6 维动态画像 | 记忆/教学事件回流画像（Phase 5） |
| ② 多智能体协同 | NPC 对话 agent + 费曼 tutee agent 接入既有协同栈（Phase 3/4） |
| ③ 个性化推送 | 记忆条件化对话/推荐（Phase 3） |
| ④ 多模态智能辅导（加分） | NPC 记忆化答疑 + 费曼角色化辅导（Phase 3/4） |
| ⑤ 学习效果评估→动态调整（加分） | 真判题裁定教学成败→回流孪生（Phase 5） |
| 创新/可信 | 每条「记得」可溯源 event_id（玻璃盒，Phase 3/6） |

**硬约束（AGENTS.md）**：不启用 ReAct 循环；Agent 故障 fail-fast；新增周期/LLM 调用必须有「无网/无 key 规则兜底」，演示链路不断；不伪造课件页码/KC；业务层禁 `any`；单 worker；不复制商业像素素材；不造假数据冒充业务；不污染 git global；不擅自 push。

---

## 1. 现状基线（已核对真实代码，实施前必读）

- **记忆模块（后端，已存在）**：`learner/memory/MemoryScorer.java`（recency×importance×relevance，权重 0.4/0.3/0.3，半衰期 14 天）+ `MemoryRetrievalService.java`（扫近 200 事件打分取 topK）+ `MemoryController`（`GET /api/learner/memory`）+ `MemoryRow` + `dto/MemoryItemDto`。
  - 🔴 **relevance 仅 KC 字面匹配**（精确 1.0 / 同章 0.5 / 否则 0），**无语义向量**。
  - 🔴 记忆是 **user 级、非 (user, NPC) 级**；**NPC 不消费记忆**。
- **事件流底座**：`learning_event_log`（V121 + V134 importance）+ `EventImportance`（1–5）+ 镜像写入；`ReflectionService` 已在。
- **向量能力（在 RAG 侧）**：`services/pythontopia-rag/.../index/embedder.py`：`HashEmbedder`(默认词袋哈希) / `BgeSmallEmbedder`(可选，sentence-transformers) / `IflytekSparkEmbedder`(`NotImplementedError`)；`select_embedder()` 读 `PYT_RAG_EMBEDDER`。后端记忆**完全无 embedding**。
- **NPC（静态脚本）**：`data/npcs/<key>/{meta,dialog}.json`（13 个：coach_q / librarian_doc / class_president / sage_review / foreman_bug / mayor_py / workshop_master / **student_a** / professor / arena_guard / mine_keeper / captain_def），由 `sync-npc-data.mjs` 同步到 `frontend/public/data/npcs/`。**纯脚本对话，无记忆、无 LLM 个性化** → 「记不住用户」根因。`student_a` 可直接复用为费曼 AI 学弟。
- **错误记忆来源**：`learning_event_log`（practice_failed/failureKind、comeback_solved）+ Bug 矿洞 errorbook（V128 深度）+ 画像 `error_pattern` 维度。
- **真判题**：`SubprocessPythonSandbox`（python3 -I -B + 墙钟超时）。
- **迁移**：最新 `V139__resource_feedback.sql` → **新表从 V140 起**。

**迁移编号规划**：V140 pgvector+记忆向量；V141 npc_memory+npc_persona；V142 feynman_session+feynman_turn。

---

## Phase 0 · 记忆语义化地基（pgvector + 真实向量）

> 目标：让「记得」不再只靠 KC 字面匹配，加真实语义向量与持久向量存储。**P0 不做完，后面 NPC 记忆都不准。**

### 如何实现
1. **迁移 `V140__memory_embedding.sql`**：`CREATE EXTENSION IF NOT EXISTS vector;`；新建 `memory_embedding(event_id BIGINT PK→learning_event_log, embedding vector(512), model VARCHAR, created_at)`（独立表，避免改动 learning_event_log 主表）。注明不可逆风险（drop 需重算）。
2. **后端取向量**：graph/rag 侧暴露 `POST /embed`（复用 `embedder.py`，生产置 `PYT_RAG_EMBEDDER=bge`，512 维，本地零 API 成本）；后端 `EmbeddingClient`（带超时 + 失败降级）。事件镜像入库时（`MemoryStreamMirror`）异步算 embedding 写 `memory_embedding`，失败吞掉不阻断主链路。
3. **`MemoryScorer` 升级 relevance**：新增可选 `semanticSim`（查询向量 vs 记忆向量余弦），`relevance = max(KC匹配分, semanticSim)`；权重不变（0.4/0.3/0.3）；**无 embedding 时回退现有 KC 匹配**（向后兼容）。
4. `MemoryRetrievalService.retrieve` 增可选 `queryText`，有则先 embed 再按余弦参与打分。

### 如何验收
- [ ] 空库 Flyway 迁移零报错；`vector` 扩展可用，`memory_embedding` 就位。
- [ ] 语义相近但 KC 不同的事件，按 `queryText` 查询能被召回（relevance>0），证明语义检索生效。
- [ ] 无 embedding（hash/DryRun）时旧 KC 匹配路径仍工作，`GET /api/learner/memory` 行为不破坏。
- [ ] embedding 写入失败不影响事件发布。

### 如何测试
- 扩展 `MemoryScorerTest`：注入向量→语义命中使 relevance 提升；无向量→回退 KC 匹配（现有用例不破坏）。
- `EmbeddingClientTest`：超时降级、维度一致、DryRun 兜底不抛。
- Testcontainers 集成测试用 `pgvector/pgvector` 镜像跑真迁移 + 余弦查询。
- `cd services/pythontopia-rag && pytest`（embedder/route）+ `cd backend && mvn -q test`。

---

## Phase 1 · 记忆按 (user, NPC) 作用域 + NPC 核心人格

> 目标：每个 NPC 拥有「它对你的记忆」和稳定人格 → NPC 记住你。

### 如何实现
1. **迁移 `V141__npc_memory.sql`**：`npc_memory(id, user_id, npc_key, event_id, salience DOUBLE, created_at, last_recalled_at)`（外键 users/learning_event_log）+ `npc_persona(npc_key PK, core_md TEXT, updated_at)`（身份/口吻/教学风格，种子自 `data/npcs/<key>/meta.json`）。
2. **`NpcMemoryService`**：
   - `remember(userId, npcKey, eventId, salience)`：把全局记忆事件**关联**到具体 NPC（如「证据馆员 librarian_doc」记住你查过的页、「出题官 coach_q」记住你做过的题）。
   - `recall(userId, npcKey, focus, k)`：复用 `MemoryRetrievalService` 打分 + `npc_key` 过滤 → 该 NPC 视角 topK。
3. **人格核心块**：从 meta.json 同步 `npc_persona.core_md`；可被「教学事件」轻量增补（纯文本追加，不调 LLM）。

### 如何验收
- [ ] 换 `npcKey` → 拿到不同记忆视角；换 `userId` → 拿不到他人记忆（隔离）。
- [ ] 每个 NPC 有 `core_md` 人格，跨会话稳定。
- [ ] `recall` 空记忆返回空且不报错。

### 如何测试
- `NpcMemoryServiceTest`：(user,npc) 隔离、topK 正确、空态友好、salience 排序。
- `NpcPersonaTest`：meta→persona 种子加载 + 增补幂等。
- 空库迁移 + `mvn -q test` 绿。

---

## Phase 2 · 自编辑去重 + 时效（关系/掌握度随时间演变）

> 目标：记忆不堆叠、会更新、有有效期；NPC 对你的判断随时间变化（借 Mem0 自编辑 + Zep 时效思想，纯规则实现，零额外 LLM）。

### 如何实现
1. **自编辑去重**：`npc_memory` 写入前做近重复检测（语义余弦 > 阈值 0.92 → 合并并提升 `salience`，不新增）。
2. **时效/作废**：`npc_memory` 增 `superseded_by BIGINT NULL`；矛盾事实新覆旧（如「已掌握 KC-016」作废「在 KC-016 反复错」），**保留历史**（不物理删）。
3. **关系强度** `affinity`：由交互频次 + 成功教学累积（纯计数规则）；NPC 招呼语随 affinity 分档。

### 如何验收
- [ ] 重复事实不堆叠（去重计数可断言）。
- [ ] 矛盾事实：旧记忆被标 `superseded_by`，历史仍可查。
- [ ] affinity 随交互单调变化，分档正确。

### 如何测试
- `MemoryDedupTest`：相似阈值边界（刚好命中/不命中）。
- `MemorySupersedeTest`：覆盖链 + 历史保留。
- `AffinityTest`：累积与分档；`mvn -q test` 绿。

---

## Phase 3 · 记忆注入 NPC 对话 + 玻璃盒可溯源

> 目标：NPC 对话从静态脚本升级为「记忆条件化个性化对话」，且每句「记得」可溯源到 event_id。

### 如何实现
1. **graph 新增 `agents/npc_dialog_agent.py`**：输入 = `persona core_md` + `recalled memories`(含 event_id) + 学生消息；受约束 prompt（用人格口吻、引用记忆、**不泄答、不编造课件页**）；**DryRun/provider 不可用 → 回退该 NPC 的静态 `dialog.json`**（演示不断）。
2. **后端 `NpcDialogService`**：`recall` 记忆 → 调 graph → 落 `npc_dialog_state`（V101）；SSE 可选透传。
3. **前端**：NPC 对话面板显示「💬 它记得你：…」徽章（点开看 event 来源），复用 `--pyt-*` 令牌、`prefers-reduced-motion`。
4. **安全/接地**：复用 `safety_critic`；记忆引用须带真实 event_id，否则标「通识/寒暄」。

### 如何验收
- [ ] 同一 NPC 对「连错两题的你」与「刚通关的你」说不同的话，且引用真实 event_id。
- [ ] graph 不可用 → 回退静态脚本，不崩。
- [ ] NPC 不输出练习完整答案（safety 拦截）。

### 如何测试
- `test_npc_dialog_agent`：注入记忆→回复含记忆要点；空记忆→通用寒暄；provider 抛→静态兜底；泄答词被拦。
- `NpcDialogServiceTest` + SSE 透传锁定测试。
- 前端 spec：记忆徽章渲染 + 空态 + reduced-motion 无动画。
- `pytest` + `mvn test` + `npm run typecheck && vitest` 绿。

---

## Phase 4 · 费曼反演：AI 学弟（镜像你的错误）

> 目标：把 `student_a` NPC 变成「会犯你同款错误的 AI 学弟」，学生通过教它来学。复用 Phase 1–3 记忆/对话栈。
> 文献依据：protégé effect（Chase/Biswas 2009）；TeachYou/AlgoBo 的「预设误解 + Reflect-Respond」（arXiv 2309.14534）——**我们的差异化 = 误解取自学生本人真实错误史**；并补 ChatGPT-LBT 记录的「纠错能力无提升」短板（arXiv 2412.15226）。

### 如何实现
1. **迁移 `V142__feynman_session.sql`**：`feynman_session(id, user_id, kc_code, tutee_state JSONB, status, created_at)` + `feynman_turn(id, session_id, role, content, taught_fix TEXT, created_at)`。
2. **易错档案抽取** `MisconceptionExtractor`：从该生 `learning_event_log`(practice_failed/failureKind) + Bug 矿洞 errorbook + 画像 `error_pattern` → 组「该生易错集」（参照文献 ESS 认知状态，**填真实个人数据，零合成**），每条带 evidence event_id。
3. **graph `agents/feynman_tutee_agent.py`**：人设 = 持续犯指定误解、主动问「为什么/怎么改」、**被正确教导后才改**（参照 Reflect-Respond）；DryRun 兜底。
4. 复用 Phase 1–3：学弟 = 一个有 (user,npc=student_a) 记忆的 NPC。

### 如何验收
- [ ] AI 学弟初始犯的错 = 该生真实易错点（可溯源 event_id），非通用初学者错。
- [ ] 学生给出正确解释 → 学弟「学会」（tutee_state 更新）；错误解释 → 不改。
- [ ] 无网/无 key → 走规则版误解脚本，不崩。

### 如何测试
- `test_feynman_tutee_agent`：注入易错档案→学弟犯对应错；被正确教→改；被错误教→不改；provider 抛→兜底。
- `MisconceptionExtractorTest`：真实事件→误解集映射；无错误史→空集（不编造）。
- `pytest` + `mvn test` 绿。

---

## Phase 5 · 真判题裁定 + 评估回流（教学成功→更新孪生）

> 目标：「你教会了」由真实判题客观裁定，结果回流掌握度/画像（评估闭环，补 LBT 纠错短板）。

### 如何实现
1. 学弟「学完」后产出代码 → 复用 `SubprocessPythonSandbox` 跑该 KC 隐藏用例：**全过 = 教学成功**。
2. 成功/失败 → 发新 `LearningEvent`（`taught_ai_succeeded` / `taught_ai_failed`，含 kcCode + sessionId）→ `MemoryStreamMirror` 入记忆流 + `MasteryEvaluator`（难度加权）+ 画像（`practical_ability` ↑ / `error_pattern` 标记）。
3. **防作弊**：学生不能把完整答案直接塞给学弟 → `safety_critic` 拦「直接给完整代码/answer_key」；教学须是「解释/纠正」而非「贴答案」。

### 如何验收
- [ ] 学弟代码过隐藏用例 → 成功事件落库 + 该 KC 掌握度上升（可断言 delta）。
- [ ] 直接喂答案被拦，不计教学成功。
- [ ] 事件可溯源（session_id + event_id）。

### 如何测试
- 集成测试（端到端）：易错档案→教→产代码→判题→事件→掌握度 delta。
- `FeynmanAntiCheatTest`：贴答案被拦。
- 5 类提交回归（AC/WA/CE/TLE/越权）不破坏既有判题；`mvn test` 绿。

---

## Phase 6 · 前端 / 演示（NPC 记忆可视 + 费曼场景 + 玻璃盒）

> 目标：把「NPC 记住你」与「教 AI 学弟」做成可演示的小镇场景。复用既有剧场/对话组件，不新增二进制素材。

### 如何实现
1. NPC 对话面板「它记得你」记忆条 + 可展开 recency/importance/relevance + event 来源（玻璃盒）。
2. 费曼场景：`student_a` NPC 对话 + 学生「教学」输入框 + 学弟改写代码 + 判题结果动画 + 玻璃盒（误解→你的教学→判题）。
3. 接入主线/复盘大厅（「测」新增费曼模态入口）。

### 如何验收
- [ ] 一条龙：进小镇 → NPC 喊「你上次在 KC-016 卡住了」→ 教 AI 学弟 → 判题通过 → 孪生更新；全程可溯源、零假数据。
- [ ] `prefers-reduced-motion` 无动画；窄屏单列。

### 如何测试
- 前端 spec：记忆面板 / 费曼面板 / 玻璃盒渲染 + 空态。
- `npm run typecheck && npm run lint && vitest` 绿。

---

## 全局 DoD（每阶段都要满足）

- 四端不受影响；`mvn -q -DskipTests compile` + 相关 `mvn test`、`pytest -q`、`npm run typecheck` + `vitest`、`eslint` 全绿。
- 业务层无 `any` 扩散；Phaser/第三方动态对象隔离在 adapter。
- 新增 LLM/周期调用必有「无网/无 key 规则或静态兜底」，演示链路不断。
- 每条记忆/对话/教学产出可溯源 `event_id`（无证据显式标「通识/寒暄」）。
- 不启用 ReAct；迁移（V140–V142）一律「空库初始化 + 导入 seed」验证；不污染 git global；不擅自 push。

## 成本 / 防幻觉 / 回退红线

- **embedding**：优先 `bge` 本地（零 API 成本）；可切讯飞。
- **NPC/学弟 LLM**：DryRun + 静态 `dialog.json` 兜底；自编辑/时效/affinity 纯规则零 LLM。
- **误解来源 100% 真实数据**（event_id 溯源），零合成学生。
- **不泄答**：safety_critic 全程拦 answer_key/完整代码。

## 一条龙演示故事线（给评委）

1. 学生主线在「列表推导式 KC-016」连错两题 → 事件落记忆流（importance=4）。
2. 进小镇，**出题官 coach_q** 主动招呼：「你上次在 KC-016 的可变对象别名上栽过，今天稳一点？」（引用真实 event）。
3. 去「测」：遇到 **AI 学弟 student_a**，它**正好犯你同款错**（镜像你的易错史）。
4. 你**教它**：解释「list 是引用不是拷贝」→ 学弟改写代码 → **真判题裁定**过了用例 = 你真的会了。
5. 成功回流：掌握度↑、画像 `practical_ability`↑、记忆更新；下次 coach_q 改口「你已经搞定 KC-016 了」。
6. 全程点开**玻璃盒**：误解→你的教学→判题→证据，可溯源、无幻觉。

## 执行交接

- 建议独立 worktree；**Phase 0 先做完**（向量地基）。
- 每 Task 先写测试、小步提交；阶段末跑该阶段「验收/测试」清单。
- 先交付 Phase 0–3（记忆，独立价值=NPC 记住你），Phase 4–6（费曼）作为创新加分。
- 触碰记忆打分 / 判题主链属核心改动面，先写测试。
