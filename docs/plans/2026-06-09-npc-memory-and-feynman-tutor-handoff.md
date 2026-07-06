# 实施 Handoff · NPC 长期记忆 + 费曼反演 AI 学弟

> **用法**：把本文件**全文**粘贴给新 agent，或直接让其「实现 `@docs/plans/2026-06-09-npc-memory-and-feynman-tutor-handoff.md`」。本文自包含，但**权威细节以两份计划文档为准**：
> - `docs/plans/2026-06-09-npc-memory-and-feynman-tutor-plan.md`（高层计划：每阶段实现/验收/测试）
> - `docs/plans/2026-06-09-npc-memory-and-feynman-tutor-detailed-design.md`（实施级：文件清单/迁移 SQL/接口签名/测试名 + Alethicode 抽取附录）

---

## 0. 你的任务（一句话）
为 Pythontopia 实现两件事：①让小镇 NPC「**记住用户**」（长期记忆）；②创新点 C「**费曼反演**」——一个会犯学生本人同款错误、要学生教会它的 AI 学弟（learning-by-teaching）。二者共用同一记忆底座，分 Phase 0–6 推进。

## 1. 项目背景（自包含）
- 仓库：`/home/cypress/Pythontopia`，**main 分支，不要去 worktree**。
- 项目：面向高校 Python 初学者的网页版像素学习小镇；参加**中国软件杯 A3「基于大模型的个性化资源生成与学习多智能体系统」**（截止 2026-06-30）。
- 技术栈：后端 **Java 21 / Spring Boot / Maven / Flyway / Postgres**（`:18080`，前缀 `/api`，JWT）；多智能体 **Python / FastAPI / LangGraph**（`services/pythontopia-graph`，`LlmProvider` 抽象含 `DryRunProvider` 离线兜底）；课件 RAG（`services/pythontopia-rag`）；前端 **Vue 3 + TS strict + Pinia + Phaser 3 + Vitest**。
- 网络：通不了走代理 `export HTTP_PROXY=http://127.0.0.1:7892 HTTPS_PROXY=http://127.0.0.1:7892`。
- Git：**不要污染 global config**，提交用 `git -c user.name="Pythontopia Dev" -c user.email="dev@pythontopia.local" commit ...`；**不要 push**；**不要 amend 已有 commit**。
- 工作区写文件若被 GateGuard 拦：先按其要求陈述 4 项事实再重试同一写入；若 Write 被工作区限制，改用 Shell heredoc 写文件。

## 2. 必读文件（动手前先读）
- `/home/cypress/Pythontopia/AGENTS.md`（硬约束：教育优先、不启用 ReAct、Agent fail-fast、不伪造课件引用、不造假数据、单 worker、业务层禁 `any`）
- 上方两份计划文档（plan + detailed-design）
- 现状关键源码（见 §3 已核实清单）

## 3. 现状基线（已核实真实代码，**别重新发现错误**）
- **记忆模块已存在**：`backend/.../learner/memory/MemoryScorer.java`（recency×importance×relevance，权重 0.4/0.3/0.3，半衰期 14 天）+ `MemoryRetrievalService.java`（扫近 200 事件取 topK）+ `MemoryController`（`GET /api/learner/memory`）+ `MemoryRow` + `dto/MemoryItemDto`。
  - 🔴 **relevance 仅 KC 字面匹配**（精确 1.0 / 同章 0.5 / 否则 0），**无语义向量**。
  - 🔴 记忆是 **user 级、非 (user,NPC) 级**；**NPC 不消费记忆**。
- **事件流**：`learning_event_log`（V121 + V134 importance）+ `EventImportance`（1–5）+ 镜像写入；`ReflectionService` 已在。
- **向量能力在 RAG 侧**：`services/pythontopia-rag/.../index/embedder.py`：`HashEmbedder`(默认) / `BgeSmallEmbedder`(可选 512 维) / `IflytekSparkEmbedder`(`NotImplementedError`)，`select_embedder()` 读 `PYT_RAG_EMBEDDER`。后端记忆**无 embedding**。
- **NPC 是静态脚本**：`data/npcs/<key>/{meta,dialog}.json`（13 个：coach_q/librarian_doc/class_president/sage_review/foreman_bug/mayor_py/workshop_master/**student_a**/professor/arena_guard/mine_keeper/captain_def），`sync-npc-data.mjs` 同步到 `frontend/public/data/npcs/`。**无记忆、无 LLM 个性化 → 这是「记不住用户」的根因**。`student_a` 复用为费曼 AI 学弟。
- **错误记忆来源**：`learning_event_log`(practice_failed/failureKind、comeback_solved) + Bug 矿洞 errorbook（V128）+ 画像 `error_pattern` 维度。
- **真判题**：`SubprocessPythonSandbox`（python3 -I -B + 墙钟超时），直接复用。
- **迁移**：最新 `V139__resource_feedback.sql` → **新表从 V140 起**（V140 记忆向量 / V141 npc_memory+persona+affinity / V142 feynman）。

## 4. 缺的 API 从 Alethicode 抽取（保留来源说明）
路径前缀 `/home/cypress/Alethicode/`：
- **embedding client（P0）**：`services/alethicode-rag/app/rag/embeddings.py`（`build_embedder(settings)` → `async embed(text)->list[float]`，智谱 embedding-3 / 2048 维 / OpenAI 兼容）+ `app/config.py`（`RagSettings.EMBEDDING_*`）。
  - ⚠️ 若用 embedding-3(2048)：超 pgvector 普通 HNSW 2000 维上限，**必须 `halfvec(2048)` + HNSW**（见 config 注释）。
- **句子级语义接地（P3/4）**：`services/qa-graph/app/nodes/token_grounding_verifier.py`（纯规则零 LLM；拆句→token 覆盖率→grounding_score→hallucination_score→trace）。**优先抽它替代「LLM entailment」**。
- **防幻觉/对话范式**：`qa-graph/app/nodes/{grounding_critic,hallucination_detector,synthesizer,auditor}.py`、`tutor-graph/app/nodes/{chat,dispatch_generative,coach_plan,diagnosis,evidence}.py`、`clients/llm_client.py`、`nodes/prompts/learner_block.py`。
- **记忆流本体**：⚠️ Alethicode 无对应模块（其 Redis/Memgraph 非 learner 记忆）→ **保持自研**。

## 5. 关键决策（动手前定）
- **embedding 维度二选一**（接口按 Alethicode `build_embedder` 抽成**可换源 client**）：
  - 默认 **选 A**：`BgeSmallEmbedder` 512 维 + `vector(512)` + ivfflat（离线零成本，推荐先用）。
  - 有 `EMBEDDING_API_KEY` 时 **选 B**：智谱 embedding-3 2048 维 + `halfvec(2048)` + HNSW。
  - **V140 列类型随所选维度定**。
- **语义接地直接抽 `token_grounding_verifier`**（零 LLM）。

## 6. 分阶段任务（每阶段：实现 → 验收 → 测试；细节见 detailed-design）
> 依赖：P0 → P1 → P2 → P3 →(P4 → P5)→ P6。**先做 P0–P3（记忆，独立价值=NPC 记住你）**；P4–P6（费曼）为创新加分。

- **P0 记忆语义化地基**：V140 `memory_embedding`（pgvector）；后端 `EmbeddingClient`/`MemoryEmbeddingRepository`；`MemoryScorer` 加语义重载（`relevance = max(KC匹配, 余弦)`，无向量回退）；rag 加 `POST /embed`。
  - 验收：空库迁移过；语义近/KC 不同事件可召回；无向量回退不破坏 `GET /api/learner/memory`；embedding 写失败不阻断事件。
  - 测试：`MemoryScorerTest`(扩展)、`EmbeddingClientTest`、`MemoryRetrievalSemanticIT`(Testcontainers `pgvector/pgvector`)、`test_embed_route.py`。
- **P1 (user,NPC) 作用域 + 人格**：V141 `npc_memory`(含 superseded_by)+`npc_persona`+`npc_affinity`；`NpcMemoryService.remember/recall`、`NpcPersonaService.seedFromMeta`、`NpcAffinityService`；事件→NPC 归属映射（practice_*→coach_q，resource_consumed→librarian_doc，debug_resolved→foreman_bug，evaluation→sage_review，taught_ai_*→student_a）。
  - 验收：换 npcKey 得不同视角、换 userId 隔离；每 NPC 有 core_md；空记忆不抛。
  - 测试：`NpcMemoryServiceTest`/`NpcPersonaSeederTest`/`NpcAffinityServiceTest` + 空库迁移。
- **P2 自编辑去重 + 时效 + 关系**：写前余弦近重（>0.92→提 salience 不新增）；矛盾事实 `supersede`（留史）；affinity 累积+分档(0..4)。
  - 验收：近重不堆叠、矛盾事实旧标 superseded 历史可查、affinity 单调。
  - 测试：`MemoryDedupTest`/`MemorySupersedeTest`/`AffinityTierTest`。
- **P3 记忆注入 NPC 对话 + 玻璃盒**：graph `npc_dialog_agent.py`（注入 recalled memories，引用 event_id，不泄答，DryRun 回退静态 `dialog.json`）；后端 `NpcDialogService`（recall→graph→落 `npc_dialog_state`→bump affinity）；前端 `NpcMemoryBadge.vue`「它记得你」。
  - 验收：对不同学情回复不同且引用 event_id；graph 挂回退不崩；不泄答。
  - 测试：`test_npc_dialog_agent`/`NpcDialogServiceTest`/`NpcMemoryBadge.spec.ts`。
- **P4 费曼 AI 学弟**：V142 `feynman_session`+`feynman_turn`；后端 `MisconceptionExtractor`（从真实错误史抽误解集，每条带 event_id，无错误史→空）；graph `feynman_tutee_agent.py`（持续犯指定误解、问为什么、被正确教导才改，DryRun 兜底）。
  - 验收：学弟初始错=该生真实易错点（溯源）；正确教→改、错误教→不改；离线兜底不崩。
  - 测试：`test_feynman_tutee_agent`/`MisconceptionExtractorTest`/`FeynmanSessionServiceTest`。
- **P5 真判题裁定 + 回流**：`FeynmanJudgeService`（学弟产代码→`SubprocessPythonSandbox` 跑隐藏用例）；新事件 `TaughtAiSucceeded/Failed`→记忆流+掌握度(难度加权)+画像；`safety_critic` 防"贴答案"作弊。
  - 验收：过用例→成功事件+掌握度 delta>0；贴答案被拦；事件可溯源。
  - 测试：`FeynmanJudgeIT`(端到端)/`FeynmanAntiCheatTest` + 5 类提交回归不破坏。
- **P6 前端/演示**：`FeynmanPanel.vue`(教学输入+学弟改写+判题动画+玻璃盒)、NPC 记忆面板、路由/复盘入口。
  - 验收：一条龙演示跑通（见 plan §演示故事线）、可溯源零假数据、reduced-motion 无动画。
  - 测试：`FeynmanPanel.spec.ts` + `npm run typecheck && lint && vitest --run`。

## 7. 硬约束 / 红线（全程）
- 不启用 ReAct 循环；Agent 故障 fail-fast；**新增 LLM/周期调用必须有无网/无 key 规则或静态兜底**，演示链路不断。
- 误解/记忆 **100% 真实数据 + event_id 溯源，零合成学生**；不伪造课件页码/KC；不泄答（safety 拦 answer_key/完整代码）。
- 业务层禁 `any`；Phaser/第三方动态对象隔离 adapter；单 worker（进程内状态注释说明）。
- 迁移（V140–V142）一律「空库初始化 + 导入 seed」验证；不复制商业像素素材。
- 抽取 Alethicode 代码须保留来源说明。

## 8. 验证命令（每阶段末跑对应套件）
```bash
cd /home/cypress/Pythontopia/backend && mvn -q -DskipTests compile && mvn -q test
cd /home/cypress/Pythontopia/services/pythontopia-graph && python -m pytest -q
cd /home/cypress/Pythontopia/services/pythontopia-rag   && python -m pytest -q   # 需 Python>=3.11
cd /home/cypress/Pythontopia/frontend && npm run typecheck && npm run lint && npx vitest --run
# 迁移：空库初始化 + seed（含 V140 pgvector/halfvec 扩展）
```

## 9. 工作模式
- **每个 Task 先写测试再实现**（TDD），跑对应套件绿后**立即细粒度 commit**（一个 Task 一个 commit，不积累，不 amend）。
- commit message 用约定式：`feat(memory): ...` / `feat(npc): ...` / `feat(feynman): ...` / `test(...)` / `chore(db): V14x ...`，用上文 `git -c user.name=... -c user.email=...` 形式。
- 触碰记忆打分 / 判题主链属核心改动面，先写测试、小步走。
- 不询问/不需用户确认，直接干；仅遇「叫醒条件」才停。

## 10. 叫醒用户的条件（仅遇到才停）
- 需要架构级颠覆性决策（如改记忆/判题主链契约）。
- 测试大面积红线（>20 失败）。
- 任何会破坏比赛演示链路的判断。
- embedding 维度选型（512 vs 2048）若涉及拉取外部 key/付费，停下确认。

## 11. 完成后输出
最终汇报：commit 列表 + 各套件测试通过数 + 已落地能力清单（NPC 记忆 / 费曼闭环）+ 一条龙演示是否跑通 + 剩余 backlog。
