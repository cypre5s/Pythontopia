# Pythontopia 渐进重构与前沿技术统一路线图

> 初稿：2026-07-29；终审更新：2026-08-02
> 状态：架构建议定稿（尚未实施，实施仍需逐 Phase 设计与验收）
> 范围：Web、Spring Boot 后端、Graph/RAG Python 服务及共享 AI 运行时
> 目标：在保持现有业务行为和技术栈的前提下，完成基础重构，并将项目演进为协议原生、可编译、自适应、端云协同的 Learning Agent OS

## 1. 执行摘要

Pythontopia 不适合整体重写。当前 Vue 3、TypeScript strict、Pinia、Phaser 3、Java 21、
Spring Boot、FastAPI、Pydantic 2，以及 Graph 服务中的 LangGraph 1，仍然匹配项目。

真正需要治理的是：

1. 前端服务器状态缺少严格的用户隔离；
2. Java、Python、Web、Android 之间的契约仍可能漂移；
3. 数据库事务包裹远程 Agent 或判题执行；
4. SSE、超时、取消和终态语义存在重复实现；
5. 少数巨型组件和服务混合了展示、编排、契约与持久化；
6. Vue 与 Phaser、Graph 与运行时、RAG 构建与在线查询之间边界不够稳定；
7. 已有 AG-UI、MCP、DSPy、Evidence Memory 等能力尚未完全标准化或产品化。

因此采用两层路线：

- **第一层：渐进重构底座。** 通过 façade、adapter、纵向切片和绞杀式替换，先解决正确性、
  契约、事务、可恢复性和可观测性问题。
- **第二层：前沿技术跃迁。** 在稳定底座上引入 A2UI、真正的 MCP、Prompt/LoRA
  双编译器、概率化 Learner Twin、安全策略学习和端云协同推理。

总方向不是“再增加几个 AI 功能”，而是形成：

> **协议原生、可编译、自适应、端云协同、证据可审计的 Learning Agent OS。**

## 2. 当前基线与硬边界

### 2.1 已有基础

项目已经具备：

- 单例 Phaser Game 与 Vue 页面体系；
- Spring Boot 模块化单体、PostgreSQL/pgvector、Redis；
- Graph 服务使用 FastAPI + LangGraph 1 的有界多 Agent 工作流；RAG 服务保留自身独立的
  依赖锁定，不能把“Graph 已升级”外推成所有 Python 服务版本一致；
- 课程包 43、KC 图谱、RAG、证据引用与防幻觉审查；
- 八维 Learner Profile、Evidence Memory、FSRS-lite、学习闭环；
- Pyodide 本地预览与服务端正式判题双轨；
- OpenAPI、AG-UI 风格事件、MCP/A2A descriptor、OTel hooks；
- DSPy/GEPA 离线优化代码与 QLoRA/DPO/ORPO/vLLM 研究资产；
- Web、Android、Windows、微信共用后端契约。

这意味着后续工作应补齐缺失能力，不应为了“技术更新”替换已经合理的框架。

### 2.2 生产证据

[2026-07-28 公网全流程验收](../reports/2026-07-28-ecs-public-workflow-acceptance.md)
记录了 227/227 项通过，无失败或 flaky 样例，同时保留两个非阻塞告警：

- `MemoryEmbeddingWriter` 出现 `expected=2048 actual=1024`；
- `WeeklyReportService` 的 `resource_run` 查询出现过 `bad SQL grammar`。

这些结果决定了重构方法必须是“保持行为、小步替换、持续验证”，不能把现有已通过链路推倒重来。
本文沿用[腾讯云文章](https://cloud.tencent.com/developer/article/2357616)所强调的渐进式重构思想。
这里的 227/227 只代表该次发布验收，不证明系统长期无 flaky，也没有覆盖账号切换、沙箱逃逸、
崩溃恢复等本文新增风险。

### 2.3 资源约束

[生产 Compose](../../deploy/docker-compose.production.yml)中核心服务内存上限合计约
3744 MiB，当前 ECS 为 2C4G。不能继续把 vLLM、Piston、Judge0、Phoenix、Langfuse、
OTel Collector 等重型运行时全部塞入同一宿主机。

默认约束：

- 当前 ECS 保留核心 Web、Backend、Graph、RAG、PostgreSQL、Redis；
- GPU 推理采用独立节点或外部服务；
- 正式判题采用独立隔离节点；
- AI 实验分析平台优先外置；
- 浏览器和移动端只承担适合端侧的小模型与本地计算。

### 2.4 状态阅读约定

除非某一节明确标注“已实现”或“设计完成”，本文中的方案均为**尚未实施的建议**：

| 标记 | 含义 |
|---|---|
| 已有基础 | 当前 checkout 已存在，后续方案应复用 |
| 设计完成 | 已有批准的设计文档，但不能据此宣称代码已完成 |
| 建议 | 已完成代码审阅，属于候选方向；尚未完成依赖选型、独立设计、实现与验证 |
| Shadow | 已接入但不影响生产决策，只做新旧结果对账 |
| Canary | 通过离线门禁后，仅对受控小流量生效 |
| 远期候选 | 只有达到本文件的启动条件后才进入设计 |

术语约定：

- `P0/P1/P2` 表示问题紧迫度，不等于实施批次；
- `Phase 0A–0D、1–7` 表示有前置依赖的实施顺序；
- `F1–F4` 表示前沿能力工作流；
- `Track X` 表示不阻塞主线的实验候选。

统一状态清单：

| 编号 | 能力 | 当前状态 | 目标状态 | 所属批次 |
|---|---|---|---|---|
| B1 | 账户历史隔离 | `DESIGNED` | `PROD` | Phase 0A |
| B2 | 跨语言契约内核 | `PROPOSED` | `PROD` | Phase 1 |
| B3 | 短事务与幂等执行 | `PROPOSED` | `PROD` | Phase 2 |
| B4 | 统一流式 Transport | `PROPOSED` | `PROD` | Phase 1 |
| B5 | Workshop 纵向切片 | `PROPOSED` | `PROD` | Phase 3A |
| B6 | 类型化 GameBridge | `PROPOSED` | `PROD` | Phase 3C，非 F1/F2 阻塞项 |
| B7 | 可恢复 Agent 运行 | `PROPOSED` | `SHADOW → PROD` | Phase 2 |
| B8 | RAG 不可变快照 | `PROPOSED` | `SHADOW → PROD` | Phase 3B |
| F1 | AG-UI/A2UI/MCP | 风格事件与 descriptor 已有，标准实现未完成 | `CANARY → PROD` | Phase 4 |
| F2 | Prompt/LoRA 双编译器 | 编译代码与研究资产已有，正式制品未完成 | `SHADOW → CANARY` | Phase 5 |
| F3 | 概率 Learner Twin/策略学习 | 规则画像与证据链已有，概率模型未上线 | `SHADOW`，策略部分为 `CONDITIONAL` | Phase 6–7 |
| F4a | Pyodide Worker | 主线程运行 | `PROD` | Phase 0B |
| F4b | 独立正式判题 | 应用内子进程且可能 fail-open | `PROD` | Phase 0C |
| F4c | 端侧小模型快路 | `PROPOSED` | `SHADOW` | Track X |
| G1 | 学习数据隐私治理/KMS | `PROPOSED` | `PROD` | Phase 0D–1，Phase 6 前置 |
| X1 | C2PA 内容凭证 | `CONDITIONAL` | 试点 | Track X |

## 3. 统一设计原则

### 3.1 与腾讯云原文原则的逐项对应

本文不是只引用“渐进重构”一词，而是把原文五项原则落实为可检查的工程约束：

| 腾讯云原文原则 | 本路线图中的工程化落点 |
|---|---|
| 保持功能一致性 | 先建 characterization test；保持 REST、SSE、正式判题和学习证据权威边界 |
| 小步前进 | 原 Phase 0 拆成 0A–0D 独立发布单元；每个实施切片只处理一个主风险面并可单独回滚 |
| 逐步改进 | façade、adapter、strangler、shadow、canary；不整体重写，不一次替换全部调用方 |
| 持续测试 | RED→GREEN、契约/故障注入/浏览器/安全专项门禁，以及受影响真实闭环复测 |
| 保持开放沟通 | 每个 Phase 必须有窄 ADR、实时 worklog、CHANGELOG、负责人、停止条件和剩余风险 |

### 3.2 保持行为

任何重构必须先定义 characterization test，保持现有 REST、SSE、页面和学习证据行为。
新旧实现需要在同一输入上可对账。

### 3.3 小步替换

优先使用：

- façade 保持旧入口；
- adapter 隔离第三方库；
- shadow mode 对比新旧结果；
- canary 按用户或任务灰度；
- feature flag 快速回滚；
- strangler pattern 逐入口迁移。

### 3.4 单一事实源

- Java 对外 REST API 以 SpringDoc/OpenAPI 为事实源；
- Graph/RAG 内部结构化输入输出以 Pydantic JSON Schema 为事实源；
- Java↔Python 边界必须指定唯一 owner，并由 owner 生成或校验另一侧 DTO，不能双向手写；
- Tool Registry 是 Agent 工具 schema 的事实源；
- 业务数据库中的学习事件是产品行为与证据的权威源，xAPI/LRS 只是异步分析镜像；
- 服务器正式判题是掌握度证据的事实源；
- 课程包 43 和证据页是课程内容事实源。

契约权威与生成方向：

| 边界 | Owner | 产物 | 消费方 |
|---|---|---|---|
| 对外 REST | Spring Controller/DTO | SpringDoc OpenAPI artifact | Web、Android、Windows、微信 |
| Java→Graph/RAG | Pydantic request/response model，逐接口明确 owner | 冻结 JSON Schema | Java client contract test 与 adapter |
| Agent Tool | Python Tool Registry | Tool JSON Schema artifact | Graph runtime、Java MCP adapter、A2UI Catalog |
| 多端学习行为 | Java learning-event domain | xAPI mapping | LRS 分析镜像、Learner Twin 离线任务 |

CI 必须验证 artifact hash、旧客户端兼容和生成物零手改；不能让 Java MCP Server 复制一份与
Python Tool Registry 独立演化的 tool schema。

### 3.5 不重复造轮子

优先采用官方 SDK 或成熟开源组件。项目只实现：

- Pythontopia 领域 schema；
- 教育权限和证据规则；
- adapter 与 deterministic compiler；
- 评测集、晋级门禁和回滚策略。

本文列出的外部组件是优先候选，不等于已经批准的依赖。每个组件进入实现前必须有一份窄 ADR，
只选择一个主实现，并记录固定版本或镜像 digest、许可证、CVE/维护活跃度、资源基准、退出条件
和替代路径。

### 3.6 教育效果优先

任何模型、协议或运行时升级都必须能够落到：

- 更准确的知识状态；
- 更可靠的学习路径；
- 更强的证据引用；
- 更低的认知负担；
- 更可解释的教学决策；
- 更稳定的真实练习评估。

### 3.7 总体方案比较

| 总体方案 | 收益 | 主要代价/风险 | 决策 |
|---|---|---|---|
| 整体重写 | 可以统一表面结构 | 重做 227 项已验收行为、四端契约和教学证据链；迁移期双系统成本最高 | 拒绝 |
| 只修已知 Bug | 短期最便宜 | 契约、事务、流式和模块边界继续累积，前沿能力只能旁路堆叠 | 拒绝 |
| 拆更多微服务 | 团队边界看似清晰 | 2C4G 资源不足，分布式一致性与运维复杂度超过收益 | 拒绝 |
| 渐进模块化 + 协议/模型/学习平面升级 | 保持现有行为，可逐步回滚并形成技术壁垒 | 需要持续门禁和阶段纪律，见效分批出现 | 采用 |

本表是总体 ADR 结论；每个外部组件和 Phase 仍需独立窄 ADR，不能用本文件替代实施设计。

## 4. 目标架构

下图是目标态，不是当前已上线拓扑；组件实际状态以 2.4 的统一状态清单为准。

```text
Web / Android / Windows / 微信
  ├─ 传统确定性页面
  └─ A2UI 白名单生成式学习界面
             │
             ▼
       AG-UI 事件流
             │
             ▼
Spring Boot API / MCP Server
  ├─ REST 资源接口
  ├─ 身份、班级、证据与权限
  ├─ 幂等任务状态机
  └─ Explicit Outbox / Lease-based Worker
             │
             ▼
LangGraph 有界编排
  ├─ Tool Registry / Policy / Guard
  ├─ Prompt + LoRA 双编译制品
  ├─ LiteLLM Model Gateway
  └─ 外部合作方需要时才使用 A2A
             │
       ┌─────┴───────────┐
       ▼                 ▼
学习智能平面          安全执行平面
xAPI / LRS            Piston (IOI Isolate)
pyBKT Shadow          Pyodide Worker
VW + OPE              Edge Small Models
DoWhy                 OpenInference / OTel
       │                 │
       └──────┬──────────┘
              ▼
      Evidence / Audit / Eval Gates
```

八项基础重构与前沿能力的依赖关系如下。“必需”表示进入对应生产试点前必须完成，
“支撑”表示可以并行、但对应能力扩大到生产范围前需要；F4 拆开后不再把浏览器 Worker、
独立判题与端侧小模型误读成同一套前置条件：

| 基础重构 | F1 协议/UI | F2 双编译器 | F3 Learner Twin | F4a Pyodide Worker | F4b 独立判题 | F4c 端侧模型 |
|---|---|---|---|---|---|---|
| B1 账户隔离 | 必需：个性化 UI 不能串号 | — | 必需：学习数据按人隔离 | — | — | 支撑：端侧缓存隔离 |
| B2 契约内核 | 必需：AG-UI/A2UI/MCP schema | 必需：结构化输出契约 | 支撑：事件 schema | — | 支撑：执行 DTO | 必需：多端模型输入输出 |
| B3 短事务 | 必需：UI action/长任务 | 支撑：离线任务与晋级 | 必需：事件可靠写入 | — | 支撑：异步提交与重试 | 支撑：云端 fallback |
| B4 流式 Transport | 必需：AG-UI 事件 | 支撑：模型路由观测 | 支撑：策略解释流 | 支撑：取消与 trace | 支撑：异步进度 | 支撑：取消与 trace |
| B5 纵向切片 | 必需：Tool/Action 边界 | 支撑：按 Agent 评测与路由 | 支撑：教学动作边界 | — | — | 支撑：端云职责分离 |
| B6 GameBridge | 支撑：游戏内生成 UI | — | 支撑：游戏学习事件 | — | — | 必需：游戏内端侧能力隔离 |
| B7 可恢复 Agent | 支撑：长任务恢复 | 必需：artifact/run 可追踪 | 支撑：策略版本回放 | — | — | 支撑：云端长任务 fallback |
| B8 RAG Snapshot | 支撑：稳定证据组件 | 必需：可重复 holdout | 支撑：教材版本一致性 | — | — | — |

因此 Phase 5 的最小 `guide_coach_decision` 试点必须完成 B2、B7、B8，但不以 B5 为硬前置；
B5 在编译器扩大到 Workshop 多 Agent 路由前升级为必需。这与 8 节的主线保持一致。

学习事件治理、OpenInference/OTel 和隐私治理是横向前置，不单独冒充“第九项基础重构”。

## 5. 第一层：八项渐进重构

## 5.1 P0：前端服务器状态按用户隔离

### 问题

[workshopHistory.ts](../../frontend/src/api/workshopHistory.ts) 的缓存当前只以 `limit`
为键；会话切换虽然会重置 Pinia，但不会清理该模块缓存，存在同一浏览器切换账号时短暂串读历史的风险。

### 方案

- 缓存键改为 `userId + limit`；
- 登录、登出统一取消在途历史请求并清空缓存；
- 晚到响应必须同时通过请求世代号与当前 `userId` 校验；
- `KeepAlive` 页面按账号重建；
- 后端仍只信任 JWT，前端 `userId` 不作为授权依据。

### 当前状态

详细设计已经批准，见
[百晓工坊历史缓存账户隔离设计](2026-07-29-workshop-history-account-isolation-design.md)；
当前为“设计完成、实现待执行”。

### 第一刀与验收

- 先写 A 登录加载历史、退出、B 登录、重新进入工坊的真实浏览器 RED 用例；
- 再实现所有者键、取消与清理；
- 保持同用户 60 秒缓存和手动刷新行为不变。

## 5.2 P0：建立可执行的跨语言契约内核

### 问题

[OpenAPI 说明](../../openapi/README.md)明确当前文件是精选子集，可能与 SpringDoc 漂移；
AG-UI 事件、Embedding 维度、Java/Python DTO 仍存在手工重复定义。

### 方案

- SpringDoc 和 Pydantic Schema 作为边界 DTO 事实源；
- 只生成 DTO，不生成或替换现有业务 service；
- 保留 Axios、Retrofit 与各端 adapter；
- CI 增加 OpenAPI breaking diff、生成物 diff、schema lint；
- Java、Python、TypeScript 对关键 payload 做 round-trip contract test；
- Embedding 增加显式 `EmbeddingProfile(model, dimension, version)`；
- AG-UI 按官方事件契约精确实现，`STATE_DELTA` 使用规范 JSON Patch。

### 不采用

- 不复制整套 DTO 到每个客户端；
- 不让 Graph、Web、Android 各自维护第二份 schema；
- 不因为契约治理而切换 GraphQL。

## 5.3 P0：拆开数据库事务与 Agent/判题执行

### 问题

[ResourceWorkshopService.java](../../backend/src/main/java/com/pythontopia/workshop/service/ResourceWorkshopService.java)
在事务中等待 Graph；
[SubmissionService.java](../../backend/src/main/java/com/pythontopia/submission/service/SubmissionService.java)
可能持有数据库资源等待 Python 判题。

### 方案

统一为三阶段幂等状态机：

```text
短事务：创建 PENDING + idempotencyKey
           ↓
事务外：调用 Graph / RAG / Judge
           ↓
短事务：完成 SUCCEEDED / FAILED，写 evidence 与 outbox
```

- HTTP 首期仍可同步等待，避免一次性改变前端体验；
- 画像、记忆、统计等可恢复投影改为 after-commit 可重放事件；
- 跨 HTTP 长任务使用显式业务状态表、outbox 和 lease-based scheduler；
- Spring Modulith Event Registry 只用于同应用内 after-commit 投影和失败重提，不把它当远程任务队列；
- 数据库用唯一约束保护 `scope + operation + idempotencyKey`，并保存 request digest；
- 同 key、不同 digest 必须拒绝，不能错误复用结果；
- 下游 Graph/Judge 必须支持同 key 去重或按 key 查询既有结果；
- 恢复 worker 使用 lease、过期时间和 fencing token，阻止旧 worker 晚到覆盖；
- 保存 attempt、next retry、指数 backoff 和人工可检查的 dead-letter 状态；
- 暂不上 Kafka。

Event Registry 只能辅助本地事务后的事件投递，不能提供跨 HTTP 的 exactly-once。设计目标是
“at-least-once 调度 + 幂等副作用 + 可查询结果”，不能只靠一个 `PENDING` 字段宣称无重复。

### 验收

- 远程超时期间数据库连接和行锁不会持续占用；
- 重试不会重复生成资源、重复计分或重复提升掌握度；
- 故障注入覆盖“下游成功、Java 落库前崩溃”和“lease 过期后旧 worker 晚到”；
- 进程崩溃后 PENDING 可扫描恢复，且结果能按 idempotency key 对账；
- 旧 API 响应保持兼容。

## 5.4 P1：统一 SSE、超时、取消与终态语义

### 问题

后端同时存在
[GraphSseConsumer](../../backend/src/main/java/com/pythontopia/common/sse/GraphSseConsumer.java)
和
[GraphStreamRelay](../../backend/src/main/java/com/pythontopia/tutor/GraphStreamRelay.java)，
前端也存在多套手写 SSE parser。

### 方案

建立单一 `GraphStreamTransport`：

- 统一 connect、首包、idle、overall deadline；
- 统一 heartbeat、event sequence、runId/threadId；
- 浏览器取消传播到 Spring，再传播到 Graph；
- 持久化 run 状态机必须恰好一个终态，事件序列可按 sequence 重放；
- 标准化 `RUN_ERROR`、业务失败和用户取消；
- provider 支持取消时记录 cancel acknowledgement；不支持时进入明确降级并限制残余 token/时间预算；
- 同步 HTTP 调用逐端点迁移到 Spring `RestClient`/`@HttpExchange`；
- 保持 Spring MVC，不整体切换 WebFlux。

### 验收

- 支持取消的 provider 在预算内返回 acknowledgement；不支持取消时残余 token/时间不超过 Phase
  设计中冻结的上限；
- 网络断开不改变持久化 run 的唯一终态，重连可重放缺失事件；
- 同一 `traceId` 可贯通浏览器、Java、Graph 与 RAG；
- 原 SSE payload 在迁移窗口内保持兼容。

## 5.5 P1：按业务能力做纵向切片

### 问题

[WorkshopChat.vue](../../frontend/src/components/workshop/WorkshopChat.vue) 和
[ResourceWorkshopService.java](../../backend/src/main/java/com/pythontopia/workshop/service/ResourceWorkshopService.java)
同时承担状态、展示、业务编排、契约与持久化职责。

### 方案

前端保留原组件作为 façade，逐个抽取：

- conversation runtime；
- history repository；
- tool/result renderer；
- evidence panel；
- resource generation command；
- cancellation lifecycle。

后端按业务能力拆成：

- generation；
- publication；
- consumption；
- micro-check；
- learning-loop；
- learner-context。

使用 Spring Modulith/ArchUnit：

1. 先报告现有依赖违规；
2. 再阻止新增循环依赖；
3. 最后逐个消除已有违规。

### 边界

保持模块化单体，不拆更多**业务微服务**。Piston、模型网关、LRS 等安全或平台基础设施只有在
资源隔离确有需要时才作为独立部署单元，不能借此前提把普通业务模块继续服务化。

## 5.6 P2：为 Phaser 建立类型化 GameBridge

### 问题

[WorldScene.ts](../../frontend/src/game/scenes/WorldScene.ts) 直接依赖 router、Pinia、API
和音频，部分场景还读取 Pinia 内部状态，Vue 与 Phaser 存在反向穿透。

### 方案

建立窄接口：

```text
Vue/Pinia → WorldSnapshot → Phaser
Phaser → TravelRequested / NpcInteracted / QuestSelected → Vue
```

- Phaser 只消费不可变 snapshot；
- Phaser 不直接导航、不直接写 Pinia、不直接调用业务 API；
- adapter 负责坐标、方向和动态第三方对象；
- 业务层继续禁止扩散 `any`。

第一步只迁移加载进度和旅行事件，不同时触碰移动、碰撞、存档。

## 5.7 P2：将有界 Agent 运行升级为可恢复运行

### 问题

[orchestrator_graph.py](../../services/pythontopia-graph/pythontopia_graph/graph/orchestrator_graph.py)
生产默认仍可能使用进程内 checkpoint；重启会丢失单轮执行状态。

### 方案

- 统一 `runId`、`threadId`、`idempotencyKey`；
- 先 shadow 启用 LangGraph PostgresSaver；
- 每个有副作用步骤必须幂等；
- checkpoint 记录计划、工具结果、证据引用和安全状态；
- 明确保留周期、个人数据清理和恢复权限；
- 恢复后仍需进入 Grounding、Safety、Schema Critic。

### 边界

- 保持有界循环；
- 不启用开放式 ReAct；
- 不让 LLM 自己维护无限任务队列；
- 不用 A2A 替代内部 LangGraph。

## 5.8 P2：将 RAG 索引改为不可变快照

### 问题

[builder.py](../../services/pythontopia-rag/pythontopia_rag/index/builder.py)
重建阶段仍存在 `clear → upsert → BM25 build` 的可见窗口，并发查询可能读到半成品。

### 方案

建立不可变 `IndexSnapshot`，至少包含：

- `buildId`；
- corpus hash；
- embedding model/dimension；
- 课程包版本；
- KC graph 版本；
- BM25/vector 构建状态；
- golden evaluation 结果。

后台完整构建新快照，通过 golden、A/B 和引用完整性门禁后原子切换 active snapshot，
并保留上一版本快速回滚。

### 边界

当前 hybrid retrieval、RRF 和 GraphRAG 已足够先进，不因此更换向量数据库。

## 6. 第二层：四项技术跃迁

## 6.1 F1：生成式 UI 与开放 Agent 协议

### 目标

将百晓工坊从“Agent 返回文本或固定卡片”升级为“Agent 生成安全、可操作的学习界面”。

### 现成组件

- [AG-UI](https://docs.ag-ui.com/concepts/events)：Agent 到前端的标准事件流；
- [A2UI v0.9.1](https://a2ui.org/)：当前 production release 的声明式生成界面协议，
  但项目生态仍较早期，必须固定协议与 renderer 版本；
- [MCP Java SDK 2.0](https://github.com/modelcontextprotocol/java-sdk)：截至 2026-08-02 的当前 GA
  主版本，可实现 Spring Boot MCP Server，跟随 MCP `2025-11-25` 规范并以 Streamable HTTP
  为主；引入前仍须验证其与项目 Spring Boot 3.3.6/Spring AI 组合的兼容矩阵；
- [MCP Apps](https://github.com/modelcontextprotocol/ext-apps)：可携带交互 UI 的扩展；当前官方
  AppBridge/helper 主要是 TypeScript，不能据此宣称 Java SDK 已经原生支持 MCP Apps；
- [A2A Python SDK](https://github.com/a2aproject/a2a-python)：跨组织 Agent 互操作。

### 项目接入

[ProtocolGatewayService.java](../../backend/src/main/java/com/pythontopia/protocol/ProtocolGatewayService.java)
目前是工具、资源、Prompt 和 Agent Card 的 descriptor，并非真正可互操作的 MCP/A2A 服务。
[ToolSpec](../../services/pythontopia-graph/pythontopia_graph/tools/registry.py)
已经包含 renderer、result schema、权限和风险元数据，可以直接派生 A2UI Catalog 与 MCP schema。

### 最小试点

仅开放：

- `KcEvidenceCard`；
- `LearningPlanCard`；
- `MicroCheckCard`。

先明确两条互不混用的信任边界：

1. **站内 Web 生成 UI：** Graph 通过 AG-UI/SSE 发送 A2UI；组件 action 只携带 action id
   与结构化参数，仍调用现有 REST command，沿用 JWT、CSRF/重放保护、Tool Policy、证据校验和后端授权。
2. **外部 MCP：** 窄 ADR 固定 MCP 规范版本（首选 `2025-11-25`）和 Streamable HTTP；
   用户委托访问按 [MCP Authorization](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)
   走 OAuth，落实 Protected Resource Metadata、授权服务器发现、
   `resource`/audience 绑定并禁止 token passthrough。API key 只允许作为显式受限的机器凭证兼容通道，
   不得调用用户私有或需要委托授权的资源。首期 Java Server 只暴露只读 Resource 和无副作用 Tool；
   `readOnly` 注解只是提示，服务端仍必须逐请求鉴权、鉴别资源 audience 并写审计日志。
3. **MCP Apps：** 不属于首期 Java Server 交付。后续单独评估 TypeScript AppBridge/sidecar，
   或等待 Java 官方支持，并通过规范一致性测试后再立项。

Web 端先固定 A2UI/renderer 版本，完成“Vue host adapter 消费 Lit custom elements”的兼容性
spike，明确 `isCustomElement`、DOM property、CustomEvent 与生命周期，再进入 canary，不自研
Vue renderer。Android、Windows、微信继续使用现有确定性页面和结构化 fallback；
只有官方或成熟 renderer 满足各端要求后才另立设计，不让 Phase 4 被“四端同时生成 UI”阻塞。

### 安全边界

- 模型不得生成或执行任意 HTML/JavaScript；
- 只能使用白名单组件；
- 限制 surface 深度、节点数、文本长度、更新频率和总 payload；
- URL 采用协议/domain allowlist，Markdown 必须 sanitize，页面启用匹配的 CSP；
- action 带 nonce/idempotency key，防止重复点击和重放；
- 每个组件必须通过键盘、读屏、对比度和 reduced-motion 验收；
- 协议解析或 renderer 失败时回退到可读、可复制的确定性 HTML/Markdown；
- 学生画像和证据资源按用户、班级、角色 scope；
- A2A 只在出现真实外部合作方时使用；
- A2UI v1.0 仍为 Candidate 时不作为生产事实源。

## 6.2 F2：Prompt 与 LoRA 双编译器

### 目标

将 Prompt 和模型 adapter 从人工经验配置升级为离线编译、评测、签名、灰度和回滚的制品。

### 现成组件

- [DSPy/GEPA](https://dspy.ai/)：Prompt 程序优化；
- [LiteLLM Gateway](https://docs.litellm.ai/)：统一模型入口、预算、路由和 fallback；
- [vLLM V1](https://docs.vllm.ai/en/stable/usage/v1_guide/)：LoRA、结构化输出、
  prefix caching、speculative decoding；
- 项目现有 QLoRA、DPO/ORPO、SAGE-IR 和 TutorBench 研究资产。

### 编译链

```text
真实失败样本
   ├─ DSPy/GEPA 编译 Prompt
   └─ QLoRA/DPO 编译专家 Adapter
              ↓
TutorBench + grounding + safety + holdout
              ↓
签名、版本化、可回滚 artifact
              ↓
LiteLLM → 云模型 / vLLM 专家 LoRA
```

当前
[compile.py](../../services/pythontopia-graph/pythontopia_graph/optimize/compile.py)
已经支持 GEPA 离线编译，但 `optimize/artifacts/` 尚无正式制品。

### 最小试点

- 只优化 `guide_coach_decision`；
- 定义 `fast`、`reasoning`、`structured`、`local-adapter` 四个稳定模型别名；
- 冻结 train/dev/真正 holdout；
- 首期只做 shadow/canary；
- 每个 artifact 保存数据版本、metric、模型、成本、延迟与回滚版本。

Phase 5 设计必须先用当前生产版本跑出 baseline，再冻结数值门禁；本总路线不凭空填写通用阈值。
最低规则是：

- deterministic schema、泄答和安全硬规则必须全部通过；
- grounding/任务正确率的置信下界不得低于已批准的非劣界；
- p95 延迟、单次 token 和费用不得超过 Phase 预算；
- 分 Agent、KC、长短输入分别报告，不能只看总平均；
- 任一硬规则回归、成本越界或 canary 错误率越界立即回滚。

### 边界

- 仅允许离线编译；
- 禁止运行时自我改写；
- 网关只负责供应商与模型路由，不接管教育安全逻辑；
- vLLM/GPU 不部署到当前 2C4G ECS。

## 6.3 F3：概率化 Learner Twin 与安全策略学习

### 目标

把当前规则式掌握度与推荐排序升级为概率化知识状态和可离线评估的教学策略。

### 当前缺口

[MasteryEvaluator.java](../../backend/src/main/java/com/pythontopia/learner/mastery/MasteryEvaluator.java)
仍以固定 delta 更新掌握度；
[MainlinePracticeService.java](../../backend/src/main/java/com/pythontopia/mainline/practice/MainlinePracticeService.java)
只持久化答对题目，答错尝试、顺序、耗时等信息不足以支持可靠的知识追踪和策略评估。

### 现成组件

1. [xAPI 2.0](https://github.com/adlnet/xAPI-Spec) 统一多端学习事件；
2. [Yet SQL LRS](https://yetanalytics.github.io/lrsql/overview.html) 存储 xAPI statement；
3. [pyBKT](https://github.com/CAHLR/pyBKT) 估计 KC 掌握概率、猜测率、失误率和学习率；
4. [EduStudio](https://edustudio.ai/) 离线比较 BKT、AKT、simpleKT 等模型；
5. [Vowpal Wabbit](https://vowpalwabbit.org/docs/vowpal_wabbit/python/latest/tutorials/python_Contextual_bandits_and_Vowpal_Wabbit.html)
   在教师批准的安全候选动作中做 contextual bandit；
6. [Open Bandit Pipeline](https://zr-obp.readthedocs.io/en/latest/) 做 off-policy evaluation；
7. [DoWhy](https://www.pywhy.org/dowhy/main/index.html) 在明确因果图、可识别性和敏感性假设下
   估计干预效应；观测数据不能单独“证明因果”。

其中 pyBKT 适合 Phase 6 shadow；EduStudio、Vowpal Wabbit、OBP 和 DoWhy 是 Phase 7
候选工具链，不是已经批准的生产组合。Phase 7 必须另做维护活跃度、版本、方法假设和替代路径 ADR。

### 分阶段边界

#### 数据阶段

扩展采集前必须先批准学习数据治理清单：

- 告知与同意/适用的教育场景合法依据；
- 目的限制与字段最小化；
- 学生、教师、管理员的可见范围；
- 数据保留期、导出、更正和删除；
- 删除向 LRS、checkpoint、训练集、artifact、缓存和备份的传播；
- 班级/租户隔离和研究数据去标识；
- 真实失败样本进入训练集前的审批、脱敏和数据版本记录。

通过后再记录：

- 每次尝试及正误；
- KC、题目与难度；
- 响应时间和顺序；
- session/context；
- 当时策略版本；
- 候选动作集合；
- 最终选择动作；
- action propensity；
- 后续学习结果。

xAPI statement 通过 after-commit/outbox 异步镜像到 Yet SQL LRS。LRS 使用独立节点或研究环境，
不加入当前 2C4G 核心 Compose；业务数据库仍保留完成学习闭环所需的权威记录。

#### Shadow 阶段

pyBKT 只预测、不影响现有 `MasteryEvaluator`，对比：

- calibration；
- AUC/log loss；
- next-question correctness；
- 与固定 delta 的分歧；
- 教师可解释性。

#### Safe Policy 阶段

Contextual bandit 只能在教师批准、适用条件明确的安全动作集合中选择：

- 继续练习；
- 更换资源；
- 进入复盘；
- 降低难度；
- 请求澄清；
- 重新规划。

这些动作并非天然等价；每个动作必须有适用条件、伤害上限和教师 kill switch。任何新策略必须先证明：

- 历史或探索数据具有足够 action coverage/positivity；
- propensity 可信且可重算；
- importance weight 与 effective sample size 诊断通过；
- 校准、分组公平性和最坏组伤害不劣于门禁；
- OPE 下置信界优于已批准基线；
- 线上 canary 可立即回到确定性策略。

不满足任一条件时只保留 pyBKT shadow，不启动策略学习。

### 禁止

- 数据不足时直接上线深度知识追踪；
- 用开放式强化学习自由生成教学动作；
- 用 LLM 学生模拟器代替真实学生数据；
- 把“学习风格”标签当作强因果依据。

## 6.4 F4：端云协同异构 AI 计算平面

### 目标

按风险、隐私、延迟和算力，把任务分配到浏览器、Android、云端模型和独立执行节点。

```text
浏览器 / Android
PII 预检、意图分类、语义相似、离线微提示
                    ↓
云端
RAG、复杂推理、资源生成、证据审查、正式策略
                    ↓
独立安全执行节点
正式 Python 判题
```

### 端侧现成组件

- Web：[Transformers.js](https://huggingface.co/docs/transformers.js/en/guides/webgpu) 或
  ONNX Runtime Web，WebGPU 失败时回退 WASM；
- Android：[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)；
- Pyodide：官方
  [Web Worker 模式](https://pyodide.org/en/stable/usage/webworker.html) + Comlink。

当前 [pyodide.ts](../../frontend/src/game/adapters/pyodide.ts) 仍在 UI 线程同步调用
`runPython()`，而 [pyodideBudget.ts](../../frontend/src/services/pyodideBudget.ts) 所写的
`Worker.terminate()` 超时兜底尚不存在。Phase 0B 必须迁入 Worker：超时直接 terminate
并重建，验证无限循环不能冻结页面；这也是它属于可信基线而非普通性能优化的原因。

端侧首期只允许：

- PII/敏感信息预检；
- 低风险意图候选排序；
- 本地语义相似；
- 弱网环境下的轻量提示分类。

正式路由、RAG 引用、资源发布、掌握度和判题结果仍由服务端决定。

### 独立判题

[SubprocessPythonSandbox.java](../../backend/src/main/java/com/pythontopia/submission/judge/SubprocessPythonSandbox.java)
当前在 `unshare` 不可用时会退回普通子进程，且不做文件系统隔离。

采用内部封装 IOI Isolate 的 [Piston](https://github.com/engineer-man/piston)，不是把二者当成
两个等价方案：

- 在独立 Piston 上线前，生产隔离探测失败必须使正式判题 readiness 失败或显式禁用正式提交，
  不能继续 fail-open；
- 新增 `PistonPythonSandbox` adapter；
- 保持现有 `PythonSandbox` 与 `SandboxRunResult` 契约；
- 只安装 Python runtime；
- 使用独立宿主、cgroup v2 和固定镜像 digest；
- 显式设置 CPU、内存、pids、文件、输出与超时上限，不能沿用未限制内存的默认值；
- 入口只绑定私网，不公开 Piston 端口；由受控 ingress/service adapter 提供 mTLS 或 HMAC；
- 节点规格和并发度由恶意样例与峰值负载基准确定，不预先把 1C1G/1C2G 写成已验证容量；
- 不把 privileged Piston 容器部署到当前应用宿主机。

Phase 0C 安全门禁必须覆盖读取应用文件、读取 `/proc/1/environ`、云元数据访问、fork bomb、
输出炸弹、磁盘写满、超时、隔离逃逸和受控并发压测。该门禁通过前，不得把独立判题标记为
`PROD`。

### 观测

采用 [OpenInference](https://github.com/Arize-ai/openinference) + OpenTelemetry：

- 自动 instrument LangGraph、模型、RAG 与 Tool；
- W3C Trace Context 贯通 Java、Graph、RAG；
- 只上传 runId、KC、工具、模型、token、延迟、证据哈希和结果；
- 不上传姓名、完整 Prompt、学生代码或原始课件；
- Phoenix/Langfuse 只外置，不在当前 ECS 自托管。

## 7. 隐私必选项与高辨识度增强

学习数据治理和敏感字段保护是 Phase 6 扩展行为采集的前置条件，不是比赛装饰。C2PA
不处理学生数据权利，因此仍属于独立可选试点。

### 7.1 必选：学习数据治理与阿里云 KMS 信封加密

优先加密 `workshop_upload.extracted_text` 等高敏、查询边界明确的字段：

- KMS 管理 KEK；
- 每条记录使用独立 DEK；
- encryption context 绑定 `userId + table + rowId + schemaVersion`；
- 双写、双读、后台迁移；
- KMS 不可用时 fail closed。

不自行实现密码算法，也不优先加密依赖 JSONB GIN 查询的整个画像对象。
KMS 只解决静态密文与密钥管理，不能代替目的限制、访问控制、删除传播、审计和研究伦理；
这些要求以 6.3 的数据治理清单为准。

### 7.2 可选：C2PA 可验证学习资源供应链

对生成图片和视频试点
[C2PA Content Credentials](https://spec.c2pa.org/specifications/specifications/2.3/explainer/Explainer.html)：

- 对实际媒体字节计算 digest；
- 记录模型、Agent run、证据页、grounding/safety 与父资源 digest；
- 使用官方 `c2patool` 生成或嵌入 manifest；
- 不写学生身份、完整 Prompt 或原始答案；
- 公共资源可内容寻址，私有资源使用租户隔离的 HMAC/盐。

## 8. 统一实施顺序

每个 Phase 的设计稿都必须给出部署位置及 RSS、CPU、IOPS、磁盘、数据库连接和外部费用预算。
当前 ECS 在发布负载下必须保留至少 20% 的**实测**内存余量且不发生 swap；达不到时应外置组件、
缩减范围或先扩容，不能用容器 hard limit 之和代替容量验证。

| Phase | 主题 | 主要交付 | 部署/资源边界 | 独立退出门禁 |
|---|---|---|---|---|
| 0A | 已知正确性热修 | 账户隔离、Embedding 维度修复、周报 SQL 修复 | 只改既有 Web/Backend；三项可分提交和回滚 | 跨账号 Vitest/Chromium；真实 embedding 与 SQL 回归均无告警 |
| 0B | 浏览器执行隔离 | Pyodide Web Worker、超时 terminate/rebuild | 不改变正式判题权威；按现有前端发布 | 无限循环不冻结 UI；预览三态与既有用例兼容 |
| 0C-1 | 正式判题 fail-closed | 隔离探测失败时 readiness 失败或显式禁用提交 | 仍在当前宿主，只缩紧安全行为 | unshare/隔离失效时绝不执行普通子进程；产品给出诚实不可用态 |
| 0C-2 | 独立正式判题 | 固定 digest 的 Piston adapter 与私网认证 | 独立宿主/cgroup v2；不与应用同机 | 攻击矩阵、并发、资源上限、故障回退全部通过后才替换旧 sandbox |
| 0D | 学习数据治理基线 | 数据清单、目的/保留/导出/删除传播、研究去标识 | 先做制度与数据流验收，不新增 LRS | 删除演练覆盖业务库、缓存、遥测、训练集与备份策略 |
| 1 | 契约、流式与隐私内核 | OpenAPI 门禁、标准 AG-UI、统一 Transport、Trace Context、KMS 首字段试点 | OTel 外部导出，不在当前 ECS 自建观测平台 | 跨语言契约、取消/重放、KMS 故障和旧客户端兼容通过 |
| 2 | 事务与可恢复运行 | 幂等状态机、outbox、lease/fencing、PostgresSaver shadow | 复用 PostgreSQL 前先测连接、IOPS 与备份增量 | 每个崩溃点、重复投递、fencing、DLQ 与恢复演练通过 |
| 3A | Workshop 模块边界 | 纵向切片、Tool/Action façade、ArchUnit | 保持模块化单体 | 旧入口兼容，模块规则和工坊真实闭环通过 |
| 3B | RAG 发布边界 | 不可变 Snapshot、离线门禁、原子切换 | 双快照超预算时外置构建 | golden/引用完整性通过，切换与回滚均可重复 |
| 3C | 游戏边界 | 类型化 GameBridge | 可与 3A/3B 并行，不阻塞 F1/F2 | Vue/Phaser 双向越界由类型和架构测试阻断 |
| 4 | 协议原生前台 | A2UI Web 三组件、站内 REST action、外部只读 MCP | 使用现有 Web/Backend；不要求四端同时实现 | threat model、规范一致性、可访问性与确定性 fallback 通过 |
| 5 | AI 双编译器 | LiteLLM 路由、GEPA artifact、专家 LoRA shadow | 网关/GPU 外置 | 冻结 holdout 的质量、成本、延迟与回滚门禁通过 |
| 6 | 学习世界模型 | 数据权利验收、xAPI/LRS 镜像、完整尝试事件、pyBKT shadow | LRS/训练任务外置，业务证据留当前库 | 数据权利验收 + 校准/预测/解释性 shadow 报告通过 |
| 7 | 安全策略学习 | VW bandit、OBP OPE、DoWhy 敏感性分析、教师 kill switch | 离线训练/评估优先；只允许受控 canary | positivity/ESS/公平性/OPE 下界与 kill switch 全部通过 |

`Track X` 不在主线时序内：端侧 ONNX/LiteRT 小模型和 C2PA 可以独立 spike，通过各自
go/no-go 后再决定是否形成新 Phase。

### 推荐主线

若技术前沿性优先，推荐：

```text
     ┌→ 0B ────────────┐
0A ──┼→ 0C-1 → 0C-2 ──┼→ Phase 1 → Phase 2
     └→ 0D ────────────┘              ├─ Phase 3A → Phase 4 A2UI/MCP
                                      ├─ Phase 3B → Phase 5 双编译器
                                      └─ Phase 6 pyBKT → Phase 7 OPE

Phase 3C GameBridge 与 Track X 并行，不阻塞上述两条前沿主线。
```

端侧小模型和 C2PA 可作为并行实验，但不能挤占主链路资源；数据治理和 KMS 不是可选项。

## 9. 跨 Phase 验收门禁

### 9.1 行为门禁

- 原 REST/SSE 契约在迁移窗口内兼容；
- 正式判题、掌握度和课程证据权威边界不变；
- 不新增纯前端假数据；
- 不绕过 Grounding、Safety、Schema Critic；
- C0/C1/C2 等现有路由不因前沿试验发生无关回归。

### 9.2 测试门禁

所有 Phase 共用：

- 对被改变行为先建立 RED characterization test；
- 运行触达模块的单元、集成和静态检查；
- 运行受影响的真实跨服务 smoke；
- `git diff --check`；
- 设计稿预先写明负责人、数值阈值、停止条件和回滚命令。

专项门禁按影响范围启用，不要求每个 Phase 无差别运行所有语言套件：

| 影响面 | 必须增加的专项验证 |
|---|---|
| 契约 | schema/breaking diff、Java/Python/TypeScript round-trip、旧 Web/Android/微信客户端兼容 |
| 数据库 | Testcontainers 真实 SQL、expand-contract、回滚或前滚、备份恢复、锁与连接池 |
| Web/UI | typecheck、lint、Vitest、真实浏览器点击流、跨账号/刷新/取消 |
| Graph/RAG | pytest、golden/holdout、证据一致性、超时与依赖故障注入 |
| 事务/恢复 | 每个崩溃点、重复投递、lease/fencing、backoff/dead-letter |
| 判题沙箱 | 文件/网络/元数据访问、fork/output/disk bomb、逃逸、超时和并发 |
| A2UI/MCP | XSS/URL/CSP、payload DoS、action 重放、权限越界、可访问性和确定性 fallback |
| 模型/策略 | 统计置信区间、分组公平性、最坏组伤害、成本/延迟、kill switch |
| 隐私 | 跨租户、最小权限、导出、删除传播、日志/遥测/训练集去标识 |
| 资源 | 峰值 RSS、CPU、IOPS、磁盘、连接、背压、72 小时 soak 或 Phase 指定等价负载 |

影响正式学习闭环时至少重复两次无重试 E2E。

完整 227 项公网矩阵作为 nightly/发布门禁，不继续把所有 PR 测试堆入一个长套件。

### 9.3 Shadow 与晋级门禁

新模型、新策略、新索引和新协议均需要：

```text
offline fixture
  → frozen holdout
  → shadow
  → canary
  → production
```

每级必须记录：

- artifact/policy/index version；
- 输入数据版本；
- 质量指标；
- grounding/safety；
- p50/p95 延迟；
- token/基础设施成本；
- 失败分类；
- 回滚目标。

### 9.4 运行门禁

- `/livez` 只判断进程存活；
- `/readyz` 检查数据库/Flyway、当前 RAG snapshot、Graph→RAG；
- 外部 LLM 短暂故障不能触发应用重启风暴；
- 每个持久化 run 恰好一个终态，客户端断线重连可重放事件；
- 每个副作用操作有 idempotency key；
- 生产镜像必须真正安装和启用声明的观测依赖。

### 9.5 文档门禁

每个 Phase：

- 独立设计文档；
- 实时 worklog；
- `docs/project/CHANGELOG.md`；
- 已验证/未验证/剩余风险；
- 是否影响比赛演示链路；
- 仅提交本 Phase 触达文件。

## 10. 明确拒绝的方向

当前不建议：

- Vue 改 React/Nuxt；
- 替换 Phaser；
- Spring MVC 全改 WebFlux；
- JDBC 全改 JPA；
- 拆更多业务微服务；
- SSE 全换 WebSocket；
- 为事件投递直接引入 Kafka；
- 再引入一套 Agent 编排框架替代 LangGraph；
- 为每个内部 Agent 节点套 A2A；
- 自行实现 MCP、A2UI renderer、模型网关或判题沙箱；
- 启用开放式 ReAct；
- 运行时自动修改 Prompt 或模型；
- 数据不足时上线深度知识追踪或强化学习；
- 在浏览器部署完整 WebLLM 导师；
- 在当前 2C4G ECS 自托管所有判题、推理和观测服务；
- 跨用户缓存生成答案或个性化资源；
- 将 Pyodide 预览升级为正式判题证据；
- 使用区块链/IPFS 保存学生私有学习记录；
- 当前引入联邦学习、同态加密或 TEE 推理。

依赖升级应走独立发布列车，一次只升级一个生态，不与业务重构混成一次大提交。

## 11. 决策与触发条件

| 候选能力 | 启动条件 |
|---|---|
| 独立 Piston | fail-closed 临时措施生效；独立节点、攻击测试、并发和认证门禁完成 |
| A2UI Web 试点 | AG-UI 精确契约、Tool schema、站内 threat model、Lit/Vue spike 和 fallback 完成 |
| 真正 MCP | 与站内 A2UI 分离；固定规范版本与 Streamable HTTP；OAuth 资源发现/audience/no-token-passthrough、受限机器 API key、用户/班级 scope、重放防护和审计边界明确 |
| A2A | 出现真实 LMS、学校或外部 Agent 合作方 |
| LiteLLM | 明确模型别名、retry 所有权、预算和外置部署位置 |
| vLLM LoRA | 独立 GPU 节点和冻结 holdout 完成 |
| pyBKT | 数据治理/删除传播通过；错误尝试、顺序、耗时和 KC 事件完整 |
| Contextual Bandit | 数据治理完成；action coverage、propensity、有效样本量和 OPE 下置信界通过 |
| 深度 KT | 数据规模、跨学期稳定性和可解释性优于 pyBKT |
| Web/Android 端侧模型 | 设备矩阵、包体、内存、WebGPU/WASM fallback 通过 |
| KMS 字段扩展 | 数据分类、访问路径、查询需求、双写/双读和恢复演练完成 |
| C2PA | 生成媒体链稳定，证书和密钥生命周期明确 |

## 12. 最终建议

Pythontopia 最有价值的前沿性，不是拥有最多的 Agent 或最新的模型名称，而是把现有能力组织成：

1. **协议原生：** AG-UI、A2UI、MCP 对外可互操作；
2. **运行可靠：** 短事务、幂等、可恢复、可观测、安全执行；
3. **模型可编译：** Prompt 与 LoRA 都经过离线评测和制品晋级；
4. **学习可建模：** 从固定 delta 走向概率化 Learner Twin；
5. **策略可验证：** 推荐决策先离线评估，再做带明确假设的因果估计与敏感性分析；
6. **计算可分层：** 端侧快路、云端推理、独立判题各守边界；
7. **证据可审计：** 课程引用、模型版本、策略版本和学习结果可追溯。

推荐优先形成三条壁垒：

- **A2UI + MCP：** 最直观的产品与协议跃迁；
- **DSPy + 专家 LoRA 双编译器：** 最强的 AI 工程壁垒；
- **Learner Twin + OPE：** 最难复制的教育科研壁垒。

所有实施仍遵循一个底线：

> 先通过小步、可回滚重构把边界变可靠，再让前沿技术进入主链路。
