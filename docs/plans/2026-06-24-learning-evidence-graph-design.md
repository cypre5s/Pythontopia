# Learning Evidence Graph 深度设计

> 目标：把 SAG 的 SQL 动态超边、GraphRAG/DRIFT 的局部子图、Agentic RAG 的轨迹评测、MemGraphRAG/HippoRAG 的长期记忆思想，收敛为 Pythontopia 可落地的学习证据图中台。

## 背景

Pythontopia 现在已有多条“证据链”：

- 课件 RAG：`graph_trace`、`subgraph`、page citation。
- 学习事件：`learning_event_log`、`LearningEvent`、`MemoryStreamMirror`。
- 工坊资源：`resource_run`、`learning_resource`、`resource_learning_check`。
- 掌握度：`kc_mastery_event`、`kc_mastery`、review state。
- 个性化：memory retrieval、reflection、recommendation、TutorBench-lite。

问题是这些链路仍分散：RAG 能说明“为什么补这页”，资源检查能说明“为什么给 mastery delta”，推荐能说明“为什么推这个 KC”，但没有统一的证据图 API 来回答：

```text
这个学生 -> 哪些事件 -> 哪些 KC/资源/题目/页码/错误类型 -> 产生了什么学习效果？
```

## 设计取舍

推荐采用 **SQL-native Learning Evidence Graph**：

1. 不引入独立图数据库，先用 PostgreSQL/JdbcTemplate 动态构建局部子图。
2. 不把已有表复制成新事实表，先从现有权威表投影 node/edge。
3. 不让 RAG 独占图谱，所有学习闭环共用一个 evidence graph。
4. 不做开放 ReAct，自主性限定为“证据检索、评测、推荐解释、记忆重排”。

这相当于把 SAG 的“event/entity + query-time SQL hyperedge”改造成教育域版本：

```text
event = 学习事件 / 资源检查 / agent run / mastery event
entity = user / kc / resource / problem / page / error_kind / agent
hyperedge = 查询时按 user + focusKc + evidence window 动态连接出的局部学习证据子图
```

## 架构

```text
LearningEvidenceGraphRepository
  -> SQL union 投影现有表
  -> nodes: USER, EVENT, KC, RESOURCE, CHECK, MASTERY_EVENT, PAGE, PROBLEM, ERROR_KIND, AGENT_RUN
  -> edges: ACTED, TOUCHES_KC, COVERS_KC, CHECKS_RESOURCE, GROUNDED_BY_PAGE,
            HAS_ERROR_KIND, UPDATES_MASTERY, GENERATED, KC_RELATION, SEQUENCE_NEXT

LearningEvidenceGraphService
  -> buildForUser(userId, focusKc, limit)
  -> summarize metrics:
       nodeCount, edgeCount, touchedKcCount, groundedPageCount,
       checkCoverageRate, masteryEvidenceRate, remediationRate,
       isolatedEventCount, graphCompletenessScore

LearningEffectTraceService
  -> append evidenceGraph metrics to summary

TutorBenchEvaluator
  -> add cases:
       evidence_graph_connectivity
       grounded_personalization
       trajectory_traceability

Memory / Recommendation
  -> use evidence graph focus signals:
       recent failed KC, repeated remediation, grounded pages, check outcomes
  -> recommendation reason cites event ids / page ids / check ids
```

## Route A：Learning Evidence Graph

第一阶段不新增大表，只新增 backend 包：

```text
backend/src/main/java/com/pythontopia/learner/evidence/
```

核心 DTO：

- `EvidenceNodeDto(id, type, label, refType, refId, attributes)`
- `EvidenceEdgeDto(fromId, toId, relation, weight, attributes)`
- `LearningEvidenceGraphDto(userId, focusKc, nodes, edges, metrics)`
- `EvidenceGraphMetricsDto(...)`

SQL 子图来源：

- `learning_event_log`：事件节点，payload 抽 `kcCode/kcCodes/resourceId/problemId/failureKind`。
- `learning_resource`：资源节点，连接 KC、page、created_by_run_id。
- `resource_learning_check`：检查节点，连接 resource/KC/status/remediation。
- `kc_mastery_event`：掌握度事件节点，连接 KC/source/delta。
- `resource_run`：agent run 节点，连接 agent/status/page evidence。
- `course_kc_edge`：KC-KC 关系边，复用 `prerequisite/co_occur`。

## Route B：Trajectory-Level Evaluation

TutorBench-lite 从 3 个 check 扩成“学习轨迹评测”：

1. `multi_turn_learning_closure`：资源、消费、检查、mastery 串联。
2. `anti_cheat_remediation`：失败检查不刷分，且有补救。
3. `evidence_consistency`：mastery delta 必须有检查证据。
4. `evidence_graph_connectivity`：局部图不是散点，事件能连到 KC/资源/页码。
5. `grounded_personalization`：推荐/记忆理由能追溯到真实事件或页码。
6. `trajectory_traceability`：agent run / RAG trace / resource check 至少有一条可审计路径。

量化指标：

- `graphCompletenessScore`: 多项 evidence 子指标平均值。
- `connectedEvidenceRate`: 非孤立事件比例。
- `groundedPageCount`: 可回课件页的节点数。
- `checkCoverageRate`: 已消费资源中完成 check 的比例。
- `learningLiftProxy`: check mastery events / consumed resource count。

这些不是长期 A/B 的替代品，但能在本地证明方向是对的：相比“只看资源/只看 RAG 命中”，统一证据图能给出更多可审计、可闭合、可量化的学习证据。

## Route C：Memory-Aware Personalization

记忆侧不再只按 `recency × importance × relevance` 排序，而加入 evidence graph 信号：

- failed check / failed practice 提升相关 KC 的近期权重。
- 有 page grounding 的资源优先于无证据资源。
- 同一 KC 的 repeated remediation 表示需要更具体的操作证据或迁移练习。
- 推荐理由输出 `evidenceEventIds`、`evidencePageIds`、`checkIds`，不再只有自然语言。

第一阶段先在 backend service 输出这些信号，前端可后续复用到“为什么推荐/为什么复习”玻璃盒。

## 错误处理

- 查询不到图：返回空 nodes/edges + metrics 全 0，不抛 500。
- 某一类表缺数据：该类边缺失，但其他来源继续构图。
- payload JSON 字段不齐：跳过该字段，不伪造 KC/页码/题目。
- focusKc 不存在：仍返回用户最近证据图，但 `focusKc` 只作为过滤/加权提示。

## 验收标准

1. 给定一个学生的资源生成、消费、微测、掌握度事件，evidence graph 能构出 resource -> check -> KC -> mastery 的路径。
2. 给定 RAG/agent run 的 page evidence，图里能出现 PAGE 节点并连接 run/resource。
3. 给定 `course_kc_edge` 的 `co_occur`，图能把相关 KC 作为关系边，而不是把它伪装成直接证据。
4. TutorBench 新增 graph/eval cases，并能在对照数据里体现“证据图版分数 > naive 版分数”。
5. 记忆/推荐能拿到 evidence graph 信号，理由至少能引用真实事件 id 或 page id。
6. 后端定向测试、空库 Flyway 迁移、前端类型检查按影响范围通过。

