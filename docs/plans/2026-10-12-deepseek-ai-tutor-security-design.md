# DeepSeek 导学与学习数据安全设计

日期：2026-10-12 至 2026-10-13
状态：按用户已确认的“导师/工坊聊天由 DeepSeek 直接处理文字和图片”进入实施；本文件记录实施边界与验收线。

## 目标与验收

1. 所有生成式 AI 请求只经 DeepSeek；文字回答保持现有 RAG、证据引用、接地审查与安全审查。导师/工坊聊天可附 JPEG、PNG、GIF、WebP 图片，图像直接交给 DeepSeek 视觉模型，回复为文字。语音输入、TTS 和其他模型供应商不启用。
2. 保留 Pythontopia 的 Java API、DTO、数据库与学习流程。Alethicode 仅作为行为与界面参考，不移植其未提交且依赖自身状态表、Java 内部 API 的工作流。
3. 学生代码由隔离执行器运行；缺少安全执行器配置时，判题接口明确不可用，不回退到仅隔离网络的本地 subprocess。
4. 远征题目、KC、提交归属、计分及学习事件不能由可预测 ID 或前端载荷跨用户伪造；预生成有资源上限和失败回收。
5. Learner Twin 的周报按真实时间窗取数，客户端观察事件不冒充正式练习/掌握度证据；展示证据覆盖与来源，不把简单计数称为概率。
6. 管理台只展示有真实 Pythontopia 后端合同支撑的页面；沿用既有管理布局，不复制 Alethicode 中没有本项目 API 支撑的操作。

## 现状证据

### 模型与导学

- 本地 `deploy/.env` 已配置 OpenAI-compatible DeepSeek endpoint，密钥不提交到仓库。图像请求使用 `deepseek-flash`；当前官方模型列表明确其支持视觉，而 `deepseek-v4-pro` 不支持图像。图像请求限制格式、base64、数量和单图大小。
- Pythontopia 的 Tutor workflow 已真实使用 AutoGen `RoundRobinGroupChat`，以固定五阶段执行，并在阶段边界保存/恢复 AutoGen 原生 team state；恢复绑定原用户、会话、run 与请求指纹。它适配 Pythontopia 现有 Java DTO、投影、SSE 和持久化合同。Alethicode 脏工作树里的异步事件总线、内部 API 和表结构不作为对外合同复制；当前没有用户可见的 checkpoint 浏览/手动选择恢复页面。
- 普通 Tutor Ask 与用户可见的 AutoGen CHAT 均支持 DeepSeek 视觉输入：前端图片经 Java 转发，由 Graph 校验字节、MIME、Base64、数量和大小后交给视觉模型；回复为文本。图片只保存在当轮请求内存中，不落聊天记录或数据库。AutoGen 阶段间 JSON 信封会剔除图片，运行时再将图片注入各阶段 agent 的本地上下文。
- Alethicode 当前 AutoGen 实现主要是未提交工作树内容。审计只读其源码及 Codex 记录，没有修改或把未提交文件当作稳定接口。

### 安全与学习数据

- `BossService` 当前内存题目缓存以 `problemId` 全局索引。Graph 可按章节/阶段/KC 生成可重复 ID，评测路径存在先查进程缓存、后查用户数据库的顺序，需建立用户归属与唯一题目身份。
- `preferredKc` 当前可以覆盖课程映射；远征成绩、阶段快照等接口接收客户端状态。跨班级 leaderboard 聚合也可能把多个 ACTIVE 班级混合。
- `EndlessPregenService` 当前异步队列没有明确有界容量、并发配额或陈旧 PENDING 回收。
- 周报中的资源运行有时间窗，其他若干来源是累计/最新快照；客户端可写事件可能进入 Twin 记忆。画像 confidence 是计数启发式，不能解释为统计概率。
- 本地 Java subprocess 不是完整的学生代码隔离边界。Piston/Isolate 才是计划采用的生产执行边界；服务未配置时应 fail closed。
- `/admin` 有真实的 AI 运维摘要和题目审核队列，没有 Alethicode 的用户管理、公告、判题服务器、OJ、课程包管理等后端合同。

## 本轮确认的问题和优先级

按当前仓库实际影响分级；“修复”只表示相关代码和回归已完成，不代替生产环境验收。

### P0

- 本轮没有确认 P0 问题。

### P1

- **确认并修复：Graph worker 直接执行学生代码。** 该路径可能以服务进程权限运行不可信程序；现改为 Piston/Isolate，缺失或故障时 fail closed。
- **确认并修复：Graph Boss 单测的 Fake Piston 在本机执行测试提交。** 假服务现只返回固定夹具结果，不执行传入源码；因此这组单测不再证明题目代码实际可运行，实际执行需隔离 Piston 集成验证。
- **确认并修复：审核题全局 ID 回退越权。** 猜测 `approved-{id}-v{version}` 可绕过当前用户题目签发记录；现在只允许当前用户实际签发并持久化的问题进入判题。
- **确认并修复：客户端可提交成绩或复用远征局。** 成绩不读客户端字段，run ID 由服务端签发；只汇总正式判题 ledger，结算后将 run 锁为 `SETTLED`。

### P2

- **确认并修复：预生成任务无界增长/陈旧 lease 堆积。** 增加队列、并发、单用户配额和过期回收。
- **确认并修复：客户端事件可冒充正式练习结果、周报混用时间窗。** 事件 allowlist 和权威事件统计阻止伪造练习成绩；Twin 周报限定近 7 天并说明来源/证据。
- **确认并修复：媒体 ID 可直接匿名读取。** 改为资源持有者鉴权或带签名访问链接；签名链接自身仍属于 bearer 凭证。
- **确认并修复：浏览工具把无界页面文本复制到 Python。** 在浏览器上下文内先截断再传回；这不限制 Chromium 解析大 DOM 的资源成本。
- **确认并修复：失效 JWT、公开角色注册和后台聚合越权边界。** 失效 Bearer 走一致 401，公开注册不允许 ADMIN，平台 runtime 聚合仅管理员可读。
- **确认并修复：同一远征题目在已通过后使用新提交 ID 重放会重复发布学情事件。** 现在仅首次失败与首次通过发布，后续重判保留结果但不重复累计 Mastery/Twin 证据。

### 已检查但未确认漏洞 / 遗留边界

- 本轮检查没有确认 SQL 字符串拼接注入；涉及用户值的查询使用绑定参数。Markdown、SVG 和 Mermaid 展示有 sanitizer/严格模式；未确认可利用 XSS。
- CORS 采用显式源配置；上传有格式/大小约束。未做依赖漏洞数据库扫描或完整动态安全测试，因此不能称为全面无漏洞。
- 浏览器 SSRF 仍有 DNS 校验与实际连接之间的重绑定窗口；没有把公网浏览、浏览器底层内存和分布式全局 LLM 配额标成已完全解决。
- **P2 游戏状态完整性边界：** 远征地图、节点、HP、遗物、移动和 phase 仍由客户端快照表达；后端不以这些字段发榜单奖励或学习进度。当前深度按服务端通过题数计数，不等于可信地图层数；真实 Boss 通关榜单字段仍关闭为 `false`。客户端仍可伪造续局展示与局内体力/遗物状态，需完整服务端地图动作状态机才能消除。
- 本地没有配置 Piston URL，因此没有真实沙箱和模型出题的全栈联测；生产 Piston 的 TLS、namespace、cgroup 和低权用户配置仍须部署验收。

## 方案比较与选择

| 方案 | 结果与代价 | 决定 |
|---|---|---|
| 只在环境里写 API key | 能配置文字模型，但无法保证图像路由、旧供应商关闭、沙箱与学习数据边界 | 不够完整 |
| 按 Alethicode Tutor 的 AutoGen 运行行为替换并适配现有合同 | Pythontopia 已采用 AutoGen 原生 team state、阶段 checkpoint、同请求恢复与取消；继续沿用本项目 API、SSE 和数据库边界，避免复制 Alethicode 未提交的事件总线和内部 API | 采用 |
| 复用 Pythontopia 的 Graph/Tutor/Java 合同，统一模型供应商，补齐图像合同和可信数据边界 | 变更集中，可对现有端点做回归，保持课程 RAG 和任务流 | 采用 |

## 目标设计

### DeepSeek 请求路径

统一保留现有 `OpenAiCompatProvider`。普通文字按已配置的文本/快档选择；有图像的请求明确选择 `deepseek-flash`。图片只进入本轮模型调用，不写入聊天历史、工作流 checkpoint、课件附件库或学习事件。由 Graph schema 在信任边界检查真实图片字节、媒体类型、张数及大小；后端 DTO 与前端 API 使用显式图片 DTO。

DeepSeek key 只由本机忽略文件提供，compose 示例只含变量名和空值。运行配置明确关闭 Spark、讯飞 ASR/TTS/图片服务、外部 embedding 和本地微调回退；禁用供应商不能被 readiness 或业务代码静默切回。

### Tutor / AutoGen

保留 `TutorWorkflowGraph` 当前五阶段固定 AutoGen 执行以及现有 Java 工作流 API、SSE 事件和投影。工作流 CHAT 已接收图片并送至视觉模型；图片不进入 stage 间的 JSON 状态或持久化投影。浏览器断开会取消上游模型任务；不能在 Java relay 丢弃客户端写异常后继续消费整条模型流。

Pythontopia 当前已在同一 workflow session 下按相同请求指纹自动恢复中断 run，并在阶段边界调用 AutoGen `save_state` / `load_state`。暂不增加 checkpoint 浏览或手动选择恢复 API：现有 Tutor 页面按会话继续交互，自动恢复覆盖已验证的主链路；如果后续要给学生或教师手动管理历史 run，再单独定义权限与 UX。

### 判题、远征与画像

- 生产/默认判题提供方设为 Piston。未配置 Piston 时应用可启动并报告判题不可用；执行时返回系统错误，绝不启用本机 subprocess。
- 题目缓存及持久化评测查询始终绑定 `userId + server problem id`。已审核题库保留共享内容来源，但给每次发放使用不可预测的用户题目实例 ID，避免共享题目缓存成为跨用户 oracle。
- KC 覆盖值必须属于课程包 43 且属于请求章节；正式判题只根据服务端保存的题目 KC 写 mastery 事件。
- 预生成使用现有表和 executor 机制，增加硬性并发/排队上限、字段长度/枚举校验、PENDING 租约过期回收及过期记录清理；达到上限明确拒绝或返回既有状态，不扩容成无界异步任务。
- leaderboard 和存档投影必须从服务端验证的 run/判题事实生成；不能把客户端 `score/clear/depth/relics` 当奖励凭据。若当前没有服务端逐步验证的 run 合同，则不把此类客户端快照计入公开榜。
- leaderboard 查询明确绑定一个班级，且同一成绩行的 score/depth 来自同一局。
- 最近练习时间从正式练习/判题事件计算，不使用会被 DECAY 更新的 `last_event_at`。
- 客户端只能写 allowlist 中的观察事件。练习成功、失败、mastery 等成效由正式服务端操作产生；周报数据来自同一 7 天窗口。置信字段改成可解释的证据覆盖展示，或明确标注为当前启发式，不承诺概率含义。

### 管理台

保留现有 `/admin` 路由和三项真实功能（AI 运维、题目审核、用户管理）。复用现有 grouped sidebar、标题区和 responsive drawer，补足登出入口并将 Alethicode 风格颜色限制在 admin scope。只有已有 API 返回真实数据的页面才进入导航；不新增假数据卡片，也不仿建没有本项目后端支持的 Alethicode 页面。

## 分阶段实施与验证

1. **配置与 Tutor：**验证供应商唯一性、文字/图像路由、图片 DTO 校验与附件不落历史；Graph tutor 和 workflow 定向测试、Java client/service、前端工坊组件与 typecheck。
2. **认证与判题隔离：**无效 JWT 走统一 401；Piston 未配置 fail closed；生产 Compose 密钥/教师邀请/RAG token 必须显式配置。运行认证、配置、判题沙箱测试和配置脚本静态校验。
3. **远征可信边界：**题目缓存跨用户/同一用户重名回归、KC allowlist、成绩来源、班级隔离、pregen 并发/回收。运行 Boss/pregen/score 相关 Java 集成测试；确认 Flyway 空库迁移。
4. **Learner Twin：**伪客户端事件拒绝或降权、7 天周报真实窗口、掌握度不被时间衰减伪装成练习、来源与证据展示。运行服务测试和 PostgreSQL 集成测试。
5. **管理台与全栈：**API-backed 页面与登出路由回归、严格类型检查、lint、组件测试；能启动时用 `./dev.sh` 做真实链路浏览器验收。

每阶段需保留当前快照测试输出和 changelog/worklog。没有可用 DeepSeek key 验证时，不声称真实模型联测成功；当前 key 已在对话中暴露，完成配置后建议撤销并换新 key。

## 研究与外部依据

- DeepSeek 官方[视觉理解文档](https://api-docs.deepseek.com/guides/vision/)描述图文混合输入和支持的图片格式；官方[模型与价格列表](https://api-docs.deepseek.com/quick_start/pricing/)区分当前视觉模型与不支持图像的文本模型。具体可用模型应以账户/API 当前返回为准。
- [Piston](https://github.com/engineer-man/piston) 使用 Isolate 提供隔离执行；[Isolate](https://github.com/ioi/isolate) 描述其面向程序竞赛的沙箱机制。单纯子进程或禁网不构成多租户代码执行隔离。
- 知识追踪以时序作答证据建模；原始 [Deep Knowledge Tracing 论文](https://papers.nips.cc/paper/5654-deep-knowledge-tracing.pdf)不支持将固定事件计数解释为概率。间隔复习的节奏设计可参照[分布式练习元分析](https://pubmed.ncbi.nlm.nih.gov/16719566/)，但项目应基于真实练习时间而非日历衰减时间。
- 学习分析需说明数据来源、目的、访问边界和学生权益；参考 [Jisc 学习分析实践准则](https://www.jisc.ac.uk/guides/code-of-practice-for-learning-analytics)。本轮采用用户级数据隔离和证据可追溯作为实现约束。

## 不在本轮范围

不发布 ECS/GitHub、不写 Alethicode、不启用语音/TTS、不新增多课程后台或 ReAct、不把图片作为永久档案保存。真实 DeepSeek API、Android/微信各端、生产 Piston 安装与硬件负载压测需具备相应运行环境后验收。

## 2026-10-13 功能优先复核补充

- 当前 `deploy/.env` 中 DeepSeek 兼容配置项已存在且非空；本次只检查配置项是否存在，不读取或打印密钥值。服务端模型出站由固定 DeepSeek host/model allowlist 控制；RAG embedding 固定为本地实现，讯飞 ASR/TTS/图片生成/图片理解路径不可启用。
- Alethicode Tutor 的 AutoGen 原生运行与阶段恢复能力已移植并适配 Pythontopia 合同，不再把它描述为“未整体替换”。用户可见 checkpoint 浏览/手动恢复不是当前已实现功能，也不是本轮验收项。
- 管理端当前有 AI 运维、题目审核、用户管理三个真实 API-backed 页面。用户列表表格已收敛到 44px 表头、52px 行和 16px/10px 单元格留白；长用户名/显示名截断时保留原文 title，窄屏继续使用横向滚动。
- Twin 将原来的“置信度”显示改为“证据充分度”，并在提示中说明这是平台启发式指标、不是答题正确概率；底层 API/排序字段保持兼容。
- 最新用户优先级为先完成功能与交互复核、性能最后。无尽远征 P50/P95、缓存命中率、题目重复率、失败恢复率和完成率保持待测；本轮不启动 Web 全栈、不消耗 DeepSeek 请求配额。
