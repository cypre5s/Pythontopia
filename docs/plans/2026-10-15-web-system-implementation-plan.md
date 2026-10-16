# Pythontopia 系统审计与改进 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the confirmed Web security leaks and finish evidence-based verification of the existing AutoGen tutor, expedition, learner twin, and admin flows without duplicating architecture.

**Architecture:** Keep Spring APIs, PostgreSQL state, event-specific AutoGen stages, DeepSeek-only model routing, server-owned learning evidence, and existing Vue/Phaser components. Make only narrow fixes in current modules; use existing deployment limits, migrations, test helpers, and UI primitives.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, Python 3.11+, FastAPI/LangGraph/AutoGen, Vue 3/TypeScript, Phaser 3, Vitest, Playwright.

## 执行范围与检查点

- 已读取用户提供的系统安全、无尽远征、数字孪生与全端质量要求；当前执行以 Web 为主，不操作 GitHub，也不审计 ECS 运行态。
- 本地检查点依次为 `9897b52d`、`feab5af1`，最新为 `c536e2d2 security: harden student write paths and judging`（36 个文件，含代码、回归测试、执行日志与本计划）；没有推送。附件 `/home/cypress/.codex/attachments/79c8a798-eda1-424e-a8dd-eae0a4154834/pasted-text-1.txt` 已读取，全部要求合并到下方唯一清单。`frontend/public/data/course43/manifest.json` 只有生成时间戳差异，保留在工作树中；后续工作以同一清单续记，不重复 DeepSeek 已验证路径。

## 总任务清单

本节是当前唯一交付清单；已有验证按仓库记录复用，不重复模型探针或已通过的等价测试。各任务详情和验证证据保留在下方。

### 接续摘要（2026-10-16）

| 工作流 | 状态 | 下一步 |
|---|---|---|
| DeepSeek 路由、文本/图片输入、文本输出、语音关闭 | 已完成并有静态与真实调用证据；不再重复通用调用 | 仅在具体产品链路改动时做一次定向验证 |
| AutoGen 导学助手、管理员样式、班级字段/API 对齐 | 已完成适配与对照；不复制不兼容 API | 只修复新发现的具体缺陷 |
| 本地安全修复 | 持续中；学生写入额度已覆盖主线、任务、存档、日历、技能、画像投影及远征开局/操作；执行隔离、判题排队和主线角色问题已修复 | 继续非 AI 写接口与多行日志审计、聊天历史保留；生产 Piston 网络隔离与访问控制未验证 |
| 无尽远征 | Phaser Boss 与服务端状态保护已实现 | 有真实先修证据后，补合格战斗题型、活动 run 恢复及地图/Boss 窄屏验收 |
| 学习分身 | P0 状态聚合、证据、周报与统一推荐已完成 | 等待真实样本后做 A/B 与 7 日复测，不以合成数据代替 |
| Web 可见交互 | 多条学生/教师/管理员路径已实测；本轮真实验收教师主线进度空态 | 补剩余页面动作、成功/失败态和远征 Boss 窄屏验收 |
| 工程整理与性能 | 尚未收尾 | 先完成安全/功能，再做最小工程整理；性能最后测 |

**当前执行顺序：** 非 AI API 安全尾项 → Web 可见动作矩阵与可行的远征闭环 → Twin 真实样本等待 → 工程整理 → 性能。不要重跑已通过的 DeepSeek 通用探针或无关套件。

### 当前状态快照（2026-10-16）

**已完成并可复用的证据：** DeepSeek 文本及合成 PNG 输入曾通过真实调用，Tutor 响应为文本；最近 Tutor 浏览器流程触发题目导读与无帮助反馈两个事件，2 次 DeepSeek completion 均 HTTP 200，并显示非空文本卡片。另已完成 DeepSeek provider 静态审计：在线 LLM 只允许 `api.deepseek.com` 与批准模型，TTS/ASR/讯飞图片服务恒关闭。本机 `.env` 与运行 Graph 的 provider/host/model 配置一致，密钥未输出；浏览器语音识别、录音采集和 ASR 上传的无调用 Web 代码已删除，后端 ASR 禁用合同保留。账号级用量锁、事件专属 AutoGen 阶段、run 检查点、附件隔离、Boss Phaser 场景、Twin P0、共享选题策略、AI 题库审核、主要管理页操作、安全基线及多项敏感日志修复均已有实现和定向验证。DeepSeek 文本/图片调用与 Tutor 产品链路均封账，不再重复；仅当具体产品代码改变且必须走模型路径时，才做一次该路径验证。本轮已将资源/章测提示词的动态画像、记忆、检索证据、审查和错题上下文从 `system` 移至 `user`，Graph 全量测试 1911 passed/1 skipped；并对实际章测 prompt 发起一次 DeepSeek completion，基于 3 页 RAG 证据解析出 1 道单选题。图片 Web 中继稳定性保留为未验证项。

**2026-10-16 本轮接续：** 没有 DeepSeek 请求。真实 Chromium 教师页点击“主线进度”，后端主线进度 API 返回 200、空成员状态正确、页面错误 0、模型请求 0；一次性教师与班级已按精确 ID 删除。Piston `nosocket` 现检查 `seccomp_load()`，失败时返回而不执行用户程序；构建阶段注入失败回归通过，重建后的本地沙箱烟测通过。兼容 `socketcall` 探针虽然返回负值，但脚本不辨别 errno/阻断层，不能据此认定是自定义过滤器拦截；生产网络 ACL 与每 job namespace 仍未验证。

**2026-10-16 判题排队超时：** 共享 `JudgeWorkerPool` 现最多等待 10 秒进入工作线程；超时取消并从队列移除任务，返回既有 `JUDGE_CAPACITY`/503，运行中的任务仍遵循各调用方自己的执行超时。新回归在旧实现先因 12 秒等待超时而失败，修复后 `JudgeWorkerPoolTest` 2/2 通过；无模型请求。

**2026-10-16 主线角色边界：** 主线学习、客观题、章节测验和小测控制器面向学生，但原先章节测验的 start/submit/latest 与主线进度接口仅靠 `anyRequest().authenticated()`，教师 JWT 也能进入并写入本人主线数据。现四组控制器统一要求 `STUDENT`；真实方法安全代理回归旧实现下教师调用成功，修复后 `MainlineControllerAuthorizationTest` 4/4 通过。没有模型请求。

**2026-10-16 审查去重：** AutoGen 六阶段导学、checkpoint、附件 owner/session 绑定已在 Pythontopia 实现；`/api/tutor/ask` 也通过 AutoGen 运行作答与审查阶段。两条路径保留各自 SSE/会话合同；Alethicode 新增的异步 run 生命周期不适配现有合同，无需重复移植。管理台共享样式与表格规格已对齐，剩余差异是业务页面编排与交互，不复制 Alethicode 不适用的指标/API。Provider 静态审计未发现可达的非 DeepSeek 模型或语音请求；本轮未重复 DeepSeek 调用。

**2026-10-16 本轮续接：** 管理台视觉按 Alethicode 实际 CSS 再校正工具栏间距、内容宽度与窄屏内边距；学习事件拒绝 JSON `null` 批次并限制单条序列化 payload 为 4096 字节；派生证据快照串行化同账号写入并保留最新 200 条。相关前端管理页 12/12、`LearningEventControllerTest` 5/5、PostgreSQL `EvidenceMemoryServiceTest` 4/4，typecheck、定向 ESLint/Spotless 与 diff 检查通过。公共匿名路由方法合同 `PublicReadAccessTest` 新鲜复验 1/1，仍不代表全 API 通用限流已完成。本轮没有 DeepSeek 请求。

**2026-10-16 R-14 与教师看板续接：** Graph 选路不再为未解锁 KC 生成 `PLANT_KC`，无可推荐 KC 时引导回主线；旧 `PLANT_KC` 记录保留历史类型，但新路径任务标题/链接与前端深链统一转到主线地图。旧数据未改写，课程 bootstrap 兼容接口暂留。Graph 定向回归 23/23、Ruff 通过；`LearningPathServiceTest` 新增旧类型回归，确认标题、链接和参数。教师「主线进度」失败重试分支在 Chromium 中用拦截 API 的临时响应验证：首次 503 后显示重试，重试后显示空态；共 11 个 mock 请求、0 个 AI 请求、0 个页面错误，不代表真实后端失败注入。没有 DeepSeek 请求。

**2026-10-16 本轮安全续审：** `POST /api/learner/evidence-memory/snapshots` 与 `/api/learner/probe` 现在复用每账号每分钟 100 单位写入额度，在构建证据图、落库或发布探针事件前拒绝超额请求。探针只更新自述媒介偏好、不增加能力证据；账号额度用于限制重复事件与画像投影。回归先 RED 后 GREEN，`EvidenceMemoryControllerTest`、`ProbeControllerSecurityTest` 与 `PassiveProfileProbeTest` 共 5/5；定向 Spotless 与 diff 检查通过。非 AI 写接口及多行日志全量审计仍开放；教师看板验收受本地 Web 未启动阻塞。本轮没有 DeepSeek 请求。

**2026-10-16 班级成员表修正：** Alethicode 对照发现成员 `studentId` 实际映射 `users.id`，但页面误标为“学号”；同时默认 API 只返回 ACTIVE 成员，表格的“已移除”状态不可达。现显示“用户编号”并删除不可达状态列，不改 API/数据库。回归先 RED 后 GREEN，组件测试 1/1、前端 typecheck 与触及文件 ESLint 通过；未调用模型。

**2026-10-16 AI 调用限额静态盘点：** 逐一追踪 Java Graph/生成调用点：用户触发的模型入口均由 `@AiBudgeted` 或调用前显式 `reserve()` 覆盖，背景反思/周评也走同一服务；Boss 判题调用是沙箱评测，远征前沿选择现为确定性 Learner Twin 规则。遗留讯飞媒资入口当前关闭并在服务层失败关闭。未发现绕过 DeepSeek 账号/全站日限额的可达模型请求。该盘点不等同于全 API 通用限流审计；后者仍开放。未运行测试或外部调用。

**2026-10-16 公共路由权限收窄：** `/api/auth/**`、`/api/course/**`、`/api/media/assets/**`、`/api/downloads/**` 原按路径整体匿名放行。现仅开放注册/登录/刷新、限定下载计数写入、课程/媒资/下载读取及健康检查方法；logout 和未列出的写方法需要认证。静态盘点命中的 3 个 `/api/internal/**` controller 均先用常量时间比较服务令牌，Tutor 附件/checkpoint 另校验 owner subject。媒体资产仍由 controller 校验签名或 owner。哨兵先复现匿名写入 200，再修复后 15/15 定向测试通过；后续补跑内部 controller 令牌/owner 测试 7/7；未调用模型。

**2026-10-16 公共认证限流补齐：** 复核公开认证写路由时发现 `/api/auth/refresh` 未匹配现有登录/注册来源 IP 限流；现纳入同一 Nginx 路由与 5 次/秒、突发 10 次规则。HTTPS 转发契约先 RED 后 GREEN；脚本 19 项通过，完整部署 Nginx 配置在 1.27 容器 `nginx -t` 通过（使用临时测试证书，不访问生产）。没有 DeepSeek 请求。全 API 通用限流审计仍开放，不增加可能影响共享网络课堂及 SSE 的全局 IP 总闸。

**2026-10-16 Tutor 取消交互：** `TutorWorkflowPanel` 新增仅在请求进行中显示的“停止”按钮，复用 SSE abort；中止后保留原 requestId/checkpoint 恢复入口，并阻止误报“无响应”。组件回归先 RED 后 GREEN，8/8 通过；`npm run typecheck`、触及组件的 ESLint 与 diff 检查通过。无模型请求。

**2026-10-16 NPC talk 输入边界：** 客户端仍拥有自己的节点/flag 存档值；静态追踪未发现这些字段被任务、奖励或权限消费，因此不按单一 Web 树破坏 Android 合同。现在把每次写入限制为节点 ID 128 字符、最多 32 个 effects，effect type/key/value 分别不超过 64/128/1024 字符；真实跨客户端语义仍未升级为可信学习或授权证据。控制器回归先 RED 后 GREEN，6/6 通过；定向 Spotless 与 diff 检查通过，无模型调用。

**2026-10-16 续接记录：** 后续提示词边界审计又覆盖 Tutor、debate、intent router、orchestrator、NPC、Feynman、reflection 和 personalization：动态画像、记忆、课件、模型生成上下文改为 `user` 数据，持久化对话拒绝 `system` role。定向 Graph 测试 298 passed，全量 Graph 1924 passed/1 skipped；Ruff 与 diff 检查通过，本轮无 DeepSeek 调用。另修复 Boss 缺题日志泄露题目 ID/用户 ID：哨兵测试先 RED，修复后目标测试通过；本地这一条已闭环。生产 Piston 访问控制仍需部署侧核验，不因源码修复或本地测试而标记完成。

**2026-10-16 执行线程续接：** 安全审计确认 Spring 同步发布学习事件时，反思任务复用 SSE `CallerRunsPolicy`；队列饱和会把 Graph 等待带进判题线程。现改为专用单 worker/32 项有界执行器，拒绝时只记录固定提示并丢弃 best-effort 反思；SSE 执行器行为不变。饱和回归先 RED 后 GREEN，`ReflectionTriggerTest` + `SseRelayExecutorTest` 9/9。未调用模型。

**2026-10-16 本轮续接：** 为 `ReflectionTrigger.counters` 加入 30 分钟定时清理，24 小时无活动后移除闲置计数，并保留至少一个冷却周期；专用反思池与 Spring 启动回归共 11/11。远征视口从宽屏缩至 320px 时自动切回列表并隐藏像素塔入口；页面及 Phaser 场景回归共 13/13，typecheck、定向 ESLint、diff 检查通过。窄屏缩放回归先 RED 后 GREEN。本地审计另发现视频进度限频窗口、NPC 寒暄缓存和教师主线聚合缓存的过期条目没有回收，现均增加定时清理；视频/NPC 定向回归 54/54、主线看板缓存回归 9/9。9 个受影响后端测试类合并复验 73/73；触及 Java 文件定向格式检查通过。本轮没有 DeepSeek 请求。

**2026-10-16 安全审计续接：** 匿名下载计数 `POST /api/downloads/{key}/hit` 可被重复调用污染展示计数。Nginx 公网入口新增来源 IP 限速（5 次/秒，突发 10 次，超限返回 429），路由只匹配 `hit` 写入路径，key 长度仍由后端校验。Nginx 1.27 容器的 `nginx -t` 首次发现 location 正则量词未加引号导致解析失败；简化入口正则后解析通过。HTTPS/Nginx 契约现有 12 项通过，Shell 语法和 diff 检查通过。没有 DeepSeek 请求。

**2026-10-16 BROWSE 资源续审：** Graph 浏览槽满时现立即返回 429，不再在并发信号量上排队；Java 保留该状态码。单次预算显式为 45 秒，专用 Graph HTTP 读超时为 50 秒。同步远程浏览已移出数据库事务，只在结果返回后用短事务原子写入 run 与步骤。Graph BROWSE 执行器/路由 42 项、Java 客户端/服务/超时配置 25 项通过；Ruff、8 个触及 Java 文件的定向 Spotless 与 diff 检查通过。本轮没有模型调用。每账号总请求量、历史记录保留上限仍未设置；全局并发闸门降低突发资源积压，但不等同账号级配额。

**2026-10-16 上传与异步资源边界续审：** 原有服务端已限制单文件 5 MiB、每账号 20 个/50 MiB，解析器限制 PDF 100 页、DOCX 100 项/16 MiB 解压量及提取文本 10 万字符。新发现未指定 executor 的 `@Async` 使用 Spring 无界默认执行器；现提供共享 `taskExecutor`（2 worker/16 队列），覆盖章测、预防包、记忆向量化和上传索引。队列满时章测落 `FAILED`、预防包跳过而预测照常返回、记忆向量化跳过但继续 NPC 记忆、上传仍成功并记录固定日志。公网仅对精确 POST 上传路由按来源 IP 限制 2 次/秒、突发 5 次并返回 429，上传请求体限 6 MiB。追调用链另发现 frontend Nginx 默认 1 MiB 会挡住既有上传/语音上限，现调到与 Spring multipart 请求上限一致的 12 MiB。上传队列饱和会跳过向量索引，没有持久重试；直接提取文本仍随上传记录保存。RED/GREEN 后 executor、章测、预防包、记忆事件与上传定向回归合计 42/42，HTTPS/Nginx 契约 18 项通过，外层和 frontend Nginx 1.27 `nginx -t` 均通过。没有模型请求或业务数据写入；真实公网上传未联测。

**2026-10-16 媒体队列饱和恢复：** 继续盘查有界后台 executor 时发现，故事板视频任务先写 `PENDING` 再入有界合成队列；队列拒绝原先会把原始 `RejectedExecutionException` 返回调用方并留下永久 `PENDING` 行。回归先 RED；现将作业置为 `FAILED/VIDEO_COMPOSE_QUEUE_FULL` 并返回统一 503 `BusinessException`。`MediaJobServiceTest` 11/11 通过；无模型请求或业务库写入。

**2026-10-16 NPC 状态审计：** `POST /api/world/npcs/{npcKey}/talk` 的节点与 flag 仍由客户端声明，但只写当前用户自己的对话存档；静态调用链没有发现任务、奖励或权限消费。已对写入字段和 effects 数量加界限，控制器测试覆盖超长节点、超量 effects、超长 flag 值。由于 Android 与 Web 对话树不同，当前值不作为可信学习证据；未来若由节点触发奖励或权限，须先建立跨端服务端对话合同。

**远征地图窄屏复核：** 当前 Web 页在视口小于 900px 时隐藏像素塔切换按钮，初始使用列表，窗口缩窄时也强制回到列表；已有 1024→320 缩放回归。只读审计提出的“320px 仍可切像素塔”在当前工作树不可复现，不重复增加场景测试。真实地图与 Boss 战斗窄屏浏览器验收仍未完成。

**2026-10-16 窄屏浏览器续验状态：** 本地 Vite 打开 `/expedition` 后按路由守卫重定向到登录页，页面无脚本错误；当前 shell 未配置 E2E 学生凭证。未注册测试账号、未造掌握度/学习事件，也未访问现有真实学生账户，因此地图与 Boss 战斗的窄屏实体验收仍未开始；不能用这次登录页检查代替验收。

远征窄屏续验：320px 真实 Chromium 入口检查发现章节标签在横排空间不足时被挤成竖排；移动断点改为标签与下拉框纵向排列。定向页面测试 5/5、typecheck、定向 ESLint 与 diff 检查通过；修复后 Chromium 实测 viewport/document/body 均为 320px，标签宽 56px、下拉框宽 244px，保存退出按钮可见，页面错误 0、模型 API 请求 0，临时账号删除并确认前缀残留 0。首次脚本曾把 Vite 的 `tutor.ts` 静态模块误识别为模型 API，修正为只统计 `/api/` 请求后复验通过。另核对“出题等待时无法离开”报告：页面已有始终可用的“保存并返回小镇”，保存后可从当前节点恢复；该项无需增加重复按钮。此检查只覆盖远征入口页，不代表地图或战斗中的完整窄屏验收。

主线地图 320px Chromium 抽查通过：`/mainline-map` 页可见，document/body 均 320px，页面错误与模型 API 请求均为 0，临时账号已删除并确认残留为 0。此项只覆盖章节抽屉入口，不代表各学习阶段页或完整动作矩阵。

Graph 提示词静态续扫覆盖所有直接 `role=system` 构造点；除已修复的动态上下文路径外，没有发现新的学生文本/检索原文进入 system 的问题。剩余 system 内容为固定规则或服务端维护的范围元数据（例如课程 KC、证据页白名单）；这项是代码结构审查，不代表模型输出效果测评。

**2026-10-16 BROWSE 配额与保留收口：** `/api/browse/runs` 创建接口复用现有 `@AiBudgeted`，生产每天每账号最多 30 次、全站最多 300 次，上海时区重置；本地开发的 `PYT_AI_REQUEST_LIMITS_ENABLED=false` 保持无限制。每天 03:20 删除 90 天前 run，step 依现有外键级联。无新表；BROWSE controller/服务、共享预算与拦截器、真实 PostgreSQL 清理定向回归合计 32/32。此前浏览历史存量为 0。本轮没有模型请求。

**2026-10-16 判题比较与本地沙箱抽查：** Graph Boss reference/debug 自测此前用 `strip()` 忽略前导空格，正式评测用 `rstrip()` 保留前导空格，可能把格式错误答案审过。两条回归先 RED 后 GREEN，统一自测为 `rstrip()`。本地 Piston 实测 `/proc/1/environ` 不可读，环境变量中敏感名称计数为 0；15 万字符输出被 Piston 以 SIGKILL 终止，Graph 适配器最多返回 64 KiB。网络连接探针未成功，但以非零状态结束且未保留错误细节，因此不把它当作完整网络隔离证明。Graph 全量 1928 passed/1 skipped；Ruff、diff 检查通过。没有 DeepSeek 调用。

**2026-10-16 NPC 存档边界：** `GET state` 和 `POST talk` 过去接受任意格式有效的 `npcKey` 并懒初始化数据库行；现仅接受中央小镇现用 12 个 NPC，未知 key 返回 404 且不访问存储。12 个 key 覆盖 Web 的 7 个地图角色及 Android 地图的 12 个角色，补入原启动快照缺少的 `captain_def`，不删除旧行。服务/控制器回归 8/8，预算、BROWSE 与 NPC 联合定向测试 41/41，定向 Spotless 通过。对话节点/flag 仍由客户端提交，但只影响用户自己的对话存档；未发现被任务或权限逻辑消费，Web 与 Android 对话树合同仍待统一。

**2026-10-16 后端全量回归状态：** `mvn -o test` 运行 1498 项，1 项失败、7 项错误。失败为 `AiBudgetRouteCoverageTest` 仍断言 BROWSE 未受预算保护；修正为学生预算断言后，预算/BROWSE/NPC 定向合跑 41/41。全量没有重跑；7 个错误为 6 个 Testcontainers PostgreSQL 镜像启动失败和 1 个长期班级仿真 JDBC 连接失败，全量 Maven 当前不记通过。

**2026-10-16 Web NPC 地图核对：** Web `town_hub.json` 的 7 个 NPC 锚点与 `data/npcs/index.json` 中 7 个 `spawnMap: town_hub` 条目完全一致；其余 4 个 `spawnMap` 为 null，属于未放置角色，不是遗漏锚点。本项只读核对，无需补图。

**仍未完成：** 本地安全审计还需继续覆盖其余类别；有真实先修证据的第 2 章以上远征战斗题型、合格活动 run 的刷新恢复和异常恢复；剩余 Web 可见动作矩阵及远征地图/战斗真实浏览器窄屏验收（入口页、错题矿洞抽屉、像素塔窄屏切换已有验证）；Twin A/B 和 7 日延迟效果；全栈功能收敛后的工程与性能收尾。讲座页“AI 助教→返回小镇”已由本轮真实 Chromium 验收。Python 开发 venv 的当前 OSV 复扫只剩 `diskcache`（Mem0 可选依赖）与 `ragas`（评估可选依赖）两项，公告未列修复版本；两者不在重建后的 Graph/RAG 运行镜像中。生产 Piston ACL/ECS 和 DeepSeek 供应商账单不在本地可验证范围内。

**接续顺序：** 不再安排通用 DeepSeek 测试。继续处理能在本地闭环的安全问题，再补 Web 动作矩阵和远征地图/战斗窄屏；远征正式闭环必须等真实先修证据和合格题型，不能改造真实用户数据。Twin 效果评估等真实样本，工程与性能收尾仍排在功能之后；性能最后测。管理台壳层/配色和 Pythontopia 班级 API、DTO、数据库字段已通过只读对齐复核，不复制 Alethicode 未提交的页面与 schema。

### 当前工作清单

- [x] DeepSeek 文本和合成 PNG 输入、文本输出已有真实调用记录；Tutor 文本产品链路已有浏览器验证，不重复通用探针。
- [x] 班级成员表显示真实字段含义“用户编号”，移除默认查询下不可达的成员状态列；组件回归 1/1、typecheck 与定向 ESLint 通过。
- [x] AI/Graph 调用点与共享日预算静态盘点：可达模型入口由注解或显式 reserve 覆盖；Boss 评测是沙箱路径，媒资生成 provider 当前关闭。通用非 AI API 限流审计仍未完成。
- [x] 公共 refresh 路由并入 Nginx 认证入口来源 IP 限速（5 次/秒、突发 10 次）；HTTPS 转发契约先 RED 后 GREEN，19 项通过。认证以外的通用 API 限流审计仍开放。
- [x] 公共路由按 HTTP 方法收窄：匿名仅能访问注册/登录/刷新、下载 hit、课程和签名媒资读取及健康检查；其余写方法要求认证。RED/GREEN 后 SecurityConfig、资产授权与认证定向测试共 15/15。
- [x] Web 没有活跃语音输入调用点；已移除闲置 SpeechRecognition、MediaRecorder 与 ASR 上传客户端代码/专属测试。后端仍保留硬关闭 ASR 端点以兼容现有非 Web 合同；无模型请求。
- [x] 反思任务与 SSE 执行池隔离；队列满时跳过 best-effort 任务，不阻塞判题线程。
- [x] 清理超过 24 小时无活动的反思用户计数，定时每 30 分钟扫描一次。
- [x] 远征视口缩至 900px 以下时切回列表并隐藏像素塔切换；页面缩放回归覆盖 320px。
- [x] 视频进度限频窗口在 60 秒静默后定时删除，避免用户/关卡组合长期累积。
- [x] NPC 寒暄缓存过期后由 30 分钟清理任务删除；缓存有效期文档与 26 小时配置一致。
- [x] 教师主线看板缓存过期后每 5 分钟清理，避免保留过期整班聚合对象。
- [x] 公网 Nginx 对匿名下载计数写入按来源 IP 限速（5 次/秒、突发 10 次，超限返回 429）；仅保护 `hit` 路径，不影响计数读取和下载直出。
- [x] BROWSE 满载请求立即 429；Graph 调用移出数据库事务，run 与 steps 保持短事务原子写；Graph 45 秒执行预算与 Java 50 秒专用读取超时对齐。
- [x] BROWSE 创建接口复用生产账号每日 30 次/全站 300 次 AI 预算，本地按用户要求不限；90 天 run 清理级联删除 step，32 项 controller/服务/限额/数据库回归通过。
- [x] Graph Boss 自测与正式评测都只忽略行尾空白；reference 与 debug 前导空格回归 RED/GREEN，Graph 全量 1928 passed/1 skipped。`node deploy/scripts/test_dev_piston_sandbox.mjs` 本地烟测通过：Python 可执行、宿主文件不可见、job 临时目录正确、共享 `/tmp` 不可写、loopback Piston API 不可达、compat socketcall 返回负值、超时任务收到 SIGKILL。该脚本未核对 errno/阻断层，不证明自定义过滤器规则或生产网络 ACL。
- [x] Piston 禁网过滤器安装失败时 fail closed：构建时用 linker wrapper 注入 `seccomp_load() = -1`，确认目标程序不执行；旧代码回归先 RED，新镜像构建及重建后本地沙箱烟测通过。生产 Piston 网络 ACL、每 job namespace 和部署认证仍未核验。
- [x] Java AST 检查被请求线程中断或超时时强制终止其子进程；`StaticCodeCheckerTest` 先 RED 后 GREEN，16/16 通过。沙箱和浏览器页面外部模型请求均为 0。
- [x] 共享判题线程池中的任务最多排队 10 秒；超时返回 503 并移除未开始的任务。`JudgeWorkerPoolTest` 先 RED 后 GREEN，2/2 通过，无模型请求。
- [x] 主线学习、客观题、章节测验与小测接口按现有产品合同限制为 `STUDENT`；教师调用回归先 RED 后 GREEN，`MainlineControllerAuthorizationTest` 4/4 通过，定向 Spotless 通过。
  - 10/16 同域静态复查确认 `LearnerEvaluationController`、`LearningEventController`、`LearningPathController`、`ProfileInterviewController`、`QuestController` 均显式检查学生角色；班级写入由 `ClassroomService` 校验角色/班级归属。该项只读复核，没有新增代码或测试。
- [x] NPC 状态/对话只接受已支持的 12 个中央小镇角色 key；未知 key 返回 404 且不触库，保留 Android `captain_def`。
- [x] Web `town_hub` NPC 锚点与目录中的 `spawnMap: town_hub` 完全相符（7/7）；未放置的 4 个角色保持不在地图。
- [x] `./dev.sh` 启动的 Spring API 显式绑定 `127.0.0.1`；运行态确认后端不再监听所有网卡，内部接口缺少服务令牌返回 401。
- [x] 认证入口限流静态复核：公网 Nginx 对 `/api/auth/login`、`/register`、`/refresh` 按 IP 限制 5 次/秒、突发 10 次；`AuthService` 另按账号记录登录失败并短时渐进退避，成功后清零。生产实例配置未远程核验。
- [x] 异步资源边界：未指定 executor 的 `@Async` 共用 2 worker/16 队列 `taskExecutor`；章测队列拒绝后置 `FAILED`，预防包拒绝不影响预测，记忆向量化拒绝不阻断 NPC 记忆，上传索引拒绝不影响已保存文件；媒体合成队列拒绝后作业置 `FAILED` 并返回 503。公网上传 POST 按 IP 2 次/秒、突发 5 次，入口请求体 6 MiB；frontend Nginx 与 Spring 12 MiB multipart 请求上限对齐。相关异步回归 42/42、媒体作业回归 11/11、Nginx 契约 18 项及双层 Nginx 1.27 语法检查通过；无模型请求。
- [x] 媒体访问静态复核：作业轮询与 SSE 都校验 owner；资产匿名 URL 使用带到期时间的 HMAC，登录 owner 可直连；无 owner 的系统级资产按设计公开。旧讯飞媒体 provider 的异常原文日志目前位于 `isEnabled() == false` 的不可达分支；若以后重新启用，需先脱敏。没有外部媒体调用或数据库写入。
- [x] Tutor AutoGen 取消操作：导学面板运行时可显式中止现有 SSE，请求身份与 checkpoint 保留以便恢复；回归 8/8、typecheck、定向 ESLint 通过。Tutor AutoGen 实现已覆盖普通问答与六阶段导学，不移植不兼容的 Alethicode run/event API。
- [x] 讲座页动作：真实 Chromium 学生打开 `/lecture/1`，切到 AI 助教侧栏，再点击“小镇”回到 `/world`；页面错误 0、Tutor 提问/资源生成 POST 0。临时学生 721 已删除并确认用户名残留 0。
- [ ] 继续本地安全审计；2026-10-16 已核验远征快照 PUT 的账号归属、服务端 run ID/章节/地图绑定和权威状态回读，`ExpeditionRunServiceTest` 11/11 通过；客户端 HP、分数、节点状态及历史不会写回。B 站时长筛选日志已移除原始搜索词，Graph 开发与镜像 Uvicorn access log 已关闭，公网 Nginx 仅记录 `$uri`；搜索路由 17/17、日志契约和 Compose 配置通过。重建本地 Graph 后，带合成 query 的 `/healthz` 返回 200 且运行日志无该标记。匿名路由方法合同本轮 `PublicReadAccessTest` 1/1；学习事件写入已限批次/单 payload，证据快照保留上限及用户锁回归通过。媒体同步合成入口现于图片 provider 关闭时、`ffmpeg` 探测前返回 503，`StoryboardCompositionServiceTest` 7/7；当前 Web 无该同步 API 页面调用方。10/16 对 Java/Graph/RAG 做了按敏感字段的同一行日志源码扫描：唯一 Java 候选位于 `isEnabled()` 恒为 false 的旧讯飞图像服务，调用在限流与日志分支前返回；Graph/RAG 未命中。该窄扫描不替代全量多行/包装器日志审计。其余安全审计仍开放；生产 Piston ACL/ECS 需部署侧核验，本轮未访问或修改生产主机。
  - 10/16 通用 API 限流续审：Nginx 已保护登录/注册/刷新、班级加入码、上传和下载计数；AI 入口另受账号预算，正式判题有 10 秒冷却。学习事件仍限两种非权威事件、批量最多 100 条，并新增单条序列化 payload 4096 字节边界与 null 批次拒绝（`LearningEventControllerTest` 5/5）。证据快照写入按用户行锁串行化、复用每账号每分钟 100 单位写入额度并保留最新 200 条（PostgreSQL `EvidenceMemoryServiceTest` 4/4；控制器额度回归另见本轮续审）；昂贵快照 POST 另按精确 method/path 配置来源 IP 每秒 1 次、突发 2，HTTPS/Nginx 契约 23 项与 Nginx 1.27 语法检查通过。通用非 AI API 的按账号请求频率仍未完成审计；不添加共享课堂可能误伤的全局 IP 总闸。
  - 10/16 本轮：学习事件单条/批次与工坊聊天历史追加写共用每账号滚动 60 秒 100 条额度，超限在触库前拒绝；聊天历史 JSON payload 限制为 64 KiB。RED/GREEN 后学习事件控制器、共享限流、历史服务定向测试 13/13，Spotless 通过。续审确认 `@EnableScheduling` 已启用，过期窗口会定时回收；payload 由 `JsonNode` 序列化后按 UTF-8 字节数检查。计数器仅在单 Spring 实例内存态，扩成多实例前应改用共享存储；全 API 非 AI 限流与日志审计仍未闭合。
  - 10/16 API 写入边界续审：`/api/events` 与 `/api/learning/events` 现共用账号每 60 秒 100 条及单条 4 KiB 限制，服务与两个入口回归 17/17；Tutor 偏好事件接入共享预算（3/3）；Bug 矿洞普通/遗忘回声新会话各预留 10 单位，拒绝/幂等回归 7/7、Spring/PostgreSQL 新建与重开 2/2。主线页打点具备批次上限、幂等并集和 KC 白名单；技能解锁按本人掌握度校验且幂等；日历事件按 userId 更新。Loop 检查点现要求 `stepIndex < run.maxSteps`，PostgreSQL 边界和既有产物往返各 1/1；浏览器 Loop/Agent Runtime 写入口共用每账号每分钟 100 单位预算，控制器回归 11/11，所有浏览器 JSON 审计字段另限 1 MiB、控制器测试增至 13/13。其它非 AI API 仍未逐项审完；Loop Runtime 长期记录保留策略待审。共享预算为单后端实例内存态，扩容前需共享化；全量 Spotless 仍受仓库既有格式漂移阻断。掌握度 N+1 和证据快照重复建图留待工程/性能阶段，不重复测试已有功能。
  - 10/16 Quest 证据续审：`page_read` 过去只确认 `course_page` 有该 ID，未检查学生进度；现要求当前 `userId` 的 `mainline_stage_progress.pages_read` 包含该存在页面。`resource` 过去只检查资源归属；现要求任务创建后存在同一学生的后端 `resource_consumed` 领域事件。`kc_mastery` 过去只比较客户端自报增量；现按同一学生、同一 KC、任务创建后的服务端 `applied_delta` 求和，限制到判题/微测/调试来源，客户端高报但服务端为 0 会拒绝。回归先 RED 后 GREEN，覆盖三类伪造，`QuestCompleteWithEvidenceTest` 12/12；本地 PostgreSQL 用无匹配哨兵值只读执行三条 SQL，结果为 0/false/false；其它非 AI API 仍待逐项审计。
  - 10/16 世界睡眠写入续审：`POST /api/world/sleep` 原先可无限调用，每次会更新存档并触发掌握度扫描；现复用每账号每分钟 100 单位写预算。先 RED 后 GREEN，超限 429 且服务不执行，`WorldAdvanceControllerTest` 2/2。
  - 10/16 世界位置边界续审：`PATCH /api/world/player` 过去允许把本人坐标写到地图外；DTO 现校验 `town_hub` 的 x=0..49、y=0..39。验证先 RED 后 GREEN，`PlayerStatePatchValidationTest` 2/2、触及文件定向 Spotless 通过。
  - 10/16 主线判题载荷续审：`PracticeAnswerRequest.answers` 过去仅限制非空，现限制最多 32 项、每项最多 8192 字符；判题仍是服务端确定性逻辑，正确答题仍幂等。先 RED 后 GREEN，`PracticeAnswerRequestValidationTest` 2/2，触及文件定向 Spotless 通过。
  - 10/16 Bug 矿洞写路由续审：会话读取与变式/重试按 userId 归属，重试/变式走正式提交的 10 秒账号冷却；DebugAgent 是确定性实现而非模型调用，沙箱工作共用有界 `JudgeWorkerPool`。新建普通会话与遗忘回声会话现各预留现有每账号每分钟 100 单位写入预算中的 10 单位，超限在沙箱处理/合成提交写入前返回 429；已存在会话重开不扣额度。`BugMineOpenThrottleTest` 与关闭守卫回归共 7/7，PostgreSQL/Spring 集成的新建与幂等重开 2/2，触及 4 个 Java 文件定向 Spotless 通过。计数仍是单后端实例内存态，扩容前需共享化；其余非 AI 写接口审计仍开放。
  - 10/16 日志审查续扫：扩展检查异常文本与堆栈使用；Tutor provider 错误对外为固定消息，旧 Xfyun ASR/TTS/图片理解的原始上游错误分支受 `isEnabled() == false` 硬关闭；上传解析错误只回给上传者。未确认新的跨用户敏感数据泄露，本项仍属定向静态检查，不等同全量日志审计。
  - 10/16 画像写入续审：`PUT /api/learner/profile/dimensions/{dimensionKey}` 过去允许 `scoreDelta: 0` 且无标签的请求，每次仍更新时间并追加永久 `profile_revision`。现拒绝该空更新，并让有效画像自评复用每账号每分钟 100 单位写预算；超限在触库前返回 429。`ProfileControllerTest` 与 `SelfDimensionRequestValidationTest` 共 7/7，触及 Java 文件定向 Spotless 通过；无模型调用或业务库写入。非 AI 写接口与完整日志审计仍未闭合。
  - 10/16 非 AI 写入口续审：费曼会话创建现占用同一账号写预算，`kcCode` 最多 64 字符；旧课程画像 bootstrap 路由保留兼容，但限学生且占用账号写预算；Boss 前沿及出题请求的 KC/媒体画像列表限制条数与白名单值。RED/GREEN 后 `FeynmanControllerSecurityTest` + `FeynmanStartRequestValidationTest` 8/8、`CurriculumControllerSecurityTest` 3/3、`BossControllerTest` 16/16，触及 Java 文件定向 Spotless 与 diff 检查通过。非 AI 写接口/全量日志审计仍开放；工坊历史保留期及单实例限流是剩余容量风险。
  - 10/16 路由清单未闭项：工坊聊天历史有每分钟预算和读取上限，但尚无历史保留上限；证据快照已同时受来源 IP 与每账号共享写入额度限制，并保留每用户 200 条；玩家位置允许重复更新本人存档，客户端按 500ms 合并写入，服务端限频需避免误伤正常移动，待后续吞吐阶段评估。10/16 只读续审确认日历处理按 `userId/eventId` 更新、技能解锁按掌握度且已解锁时幂等、远征成绩来自服务端判题账本；日历处理端点现增加每账号 1 单位预算，技能解锁也已接入 1 单位预算。SaveSlot 快照限 262144 字符并校验 JSON 对象与本人槽位，三个写端点现使用共享账号额度（建槽/切换 1、快照 10）。该预算仍是单实例内存态；Web 当前无存档槽 API 调用方。该检查不关闭全量限流审计；保留策略可能影响学习记录可见性，未擅自删除历史。
  - 10/16 Quest 写入口续审：开始/完成/跳过现各占每账号共享预算 1 单位；状态迁移以 UPDATE 受影响行数为准，开始只从 `OPEN` 推进，跳过遇到并发终结不再写重复进度。`QuestControllerTest`、`QuestServiceTest` 与状态 SQL 回归通过，账号预算仍是单实例内存态。
  - 10/16 主线进度写入口续审：单页/批量阅读、例题完成、两种补星及客观题作答现各占共享预算 1 单位；视频继续用专用关卡限频。控制器角色/额度、Mainline 与 Practice 服务回归 46/46。
- [x] NPC talk 只写本人存档，状态不参与服务端任务/奖励/权限判断；每次写入已有节点 ID、effects、type/key/value 字段长度校验。10/16 续审发现累计状态与请求频率仍无上限，现接入每账号每分钟 100 单位预算；每 NPC 最多存 128 个已完成节点和 64 个 flags，并在 `FOR UPDATE` 锁内读取后校验，避免并发绕过。数据目录中单角色最多 5 个节点。红测后 `NpcDialogControllerTest` 与世界 NPC `NpcDialogServiceTest` 共 12/12；Web/Android 对话树仍各自运行，状态不作为可信学习或授权证据。
- [ ] 补齐满足真实先修证据的第 2 章以上远征战斗题型，并验证有效活动 run 的刷新/异常恢复。选题策略先修回溯按课程候选总数有界；65 个 KC 跨 7 章链、`LearnerTwinServiceTest` 与 `BossServiceTest` 合计 63/63。另修复聚焦章节与先修 KC 实际章节不同导致出题/结算被误拒的问题；远征服务、PostgreSQL 集成、控制器、Boss 与预生成回归合计 126/126。真实先修账号的 battle-compatible 题型及有效 run 刷新恢复仍未完成。KC-003 原无主线编程题，已从第 1 章学生页经正常 AI 补题入口生成并自动批准入共享题库；题目是普通 `kind=NULL` 题，不满足远征 battle-compatible 类型，且没有正式判题/掌握度事件。KC-011 有已批准共享题（chapter 2 / stage 1 / kind `NULL` / difficulty 1），对应主线 stage ID 5；新学生未解锁第 2 章，按后端 `requireChapterUnlocked` 不应跳过门槛提交，因此仍未正式判题，本轮未调用模型。
  - 10/16 只读远征可行性复核（含 `focusChapter=null`）：11 个活跃的第 2 章以上挑战节点中，可信先修达标为 0；其中 7 个账号持有未结算 `CONSUMED` 题，4 个账号有 `READY` 题，身份均无法确认是临时 QA。审核共享库内第 2 章以上 `dual_track`/`closed_loop` 题为 0。不得消费未知账号 READY 题或操作其存档。单纯页面刷新走 GET 快照；点击“继续”会触发后台预取，可能调用模型，当前不执行。需要正常流程中真实先修达标且已确认授权的 QA 学生后再做联测。
  - 10/16 修正通用题库兜底题型：无题型元数据的课程编程题只用于普通代码练习/`dual_track`；`debug` 等专用交互没有兼容题时返回可重试服务错误，避免前端按样例猜测错误代码。回归先 RED 后 GREEN，`BossServiceTest` 55/55；真实先修账号和活动 run 联测仍待完成。
- [ ] 完成剩余 Web 可见动作矩阵与远征地图/战斗真实浏览器窄屏检查。10/16 临时学生的 `/mainline-map` 在 320px 加载成功、文档/页面宽度均为 320、浏览器错误 0；关闭章节详情后触摸 Phaser 画布能打开章节面板（选中第 3 章），模型请求 0。首次误点视觉隐藏的辅助列表未计通过。教师班级卡片 320px Playwright 布局回归 1/1，使用测试夹具，只证明窄屏显示和无页面横向溢出。`/bug-mine` 320×780 真实触屏下错题本收起/展开正常、无横向溢出，errorbook/echoes GET 200、浏览器错误 0、模型请求 0，临时用户 726 清理为 0。地图触屏项与错题矿洞抽屉窄屏项已闭合；远征 Boss 战真实窄屏与其余页面动作矩阵仍待验。本轮另以真实学生浏览器从世界目标 CTA 和地图章节入口进入 `/mainline/stage/1`，详情/视频/课件 GET 成功、浏览器错误 0；随后一次性学生按原速看满 7:20、逐页阅读原课件 40–45、提交 4 道客观题且 4/4 获服务端正确判定，刷新后仍显示 6/6 页和 4/4 题；相关提交 HTTP 200，浏览器错误 0，未调用模型。QA 用户 729 删除后，users、learning_event_log、mainline_stage_progress、kc_mastery、world_session、player_state、expedition_run 查询均为 0。这个第一章单节闭环不满足第 2 章以上远征先修条件。此前 PDF 翻页截图过早的问题来自自动化没有等待 PDF 渲染，等待条件修正后页面正常。
  - 10/16 `/guide` 上一篇/下一篇真实 Chromium 交互通过：`topic` 查询参数随正文切换，切页后正文滚动回顶，返回键路由恢复正确；页面运行错误 0。认证刷新及 Companion GET 由测试路由模拟，所有 API 均未触及真实后端，模型 API 请求与业务写入为 0。Guide pager 已验收，页面动作矩阵其余项仍开放。
  - [x] 教师班级看板「主线进度」页签：一次性本地教师创建空班，真实 Chromium 点击页签后显示空态，真实 API 返回 200 且 `members=[]`；按精确 classroom/user ID 清理并核对不存在。失败重试分支另用 Chromium 拦截 API：首次 503 显示可重试错误，第二次成功显示空态，11 个 mock 请求、0 AI 请求、0 页面错误；不作为真实后端失败注入证据。
- [ ] 等待足够真实样本后评估 Twin A/B 与 7 日延迟结果。
- [ ] 功能收敛后完成工程整理，最后再测性能；不重复已通过的 DeepSeek 测试。
- [ ] 后端全量 Maven 仍未通过：2026-10-16 新鲜复跑 `mvn -o test` 共 1509 项，0 个断言失败、7 个错误；6 个 Testcontainers 错误是 Docker 随机主机端口绑定返回 `address already in use`，长期仿真在全量运行中连接被重置。单独复跑长期仿真通过，`CoursePackServiceIntegrationTest` 仍随机端口冲突（46474）。只读诊断确认 Surefire 未配置并行，失败端口位于当前 `ip_local_port_range=44620-48715`，诊断时该范围观察到 44 个 socket 状态、失败端口无监听；端口占用发生时的具体进程仍未知，因此不归因为应用缺陷，也不能标记全量通过。未跳过或降级测试；不再为碰运气重跑全量。

- [x] 本地 AI 请求上限按用户要求关闭：`dev.sh` 在启动 Compose/Spring 前设置 `PYT_AI_REQUEST_LIMITS_ENABLED=false`，`TutorDailyRequestLimitService.reserve()` 因此直接返回、不写每日计数；生产 Compose 固定为 `true`。这关闭应用级每日上限，不改变 DeepSeek 供应商自身的额度或限流；本轮仅核对配置，没有发起请求。

2026-10-16 安全续审发现 `TutorVisualAids.vue` 的 Mermaid 分支直接将 Mermaid 输出写入 `v-html`，绕过已用于工坊导图的 CSSOM/DOMPurify 清洗。现改为先调用共享 `sanitizeMermaidSvgMarkup`，并关闭 HTML labels 以保留 SVG 文本标签。新组件红测先复现 `foreignObject`，修复后 Tutor 图解与 sanitizer 定向测试 4/4、前端 typecheck、触及文件 ESLint 和 diff 检查通过。没有模型请求；安全审计其余类别仍开放。

2026-10-16 前端 HTML 注入点续审：逐一检查当前 `src/` 下所有 `v-html`、`innerHTML` 写入，Tutor/Mermaid、工坊导图、讲义 Markdown 和课件页均在 DOM 写入前经过对应 SVG/HTML 清洗；未发现第二个绕过清洗的入口。此项是代码审查记录，不代表后端/第三方内容源的全量安全审计完成。

**本轮续接记录：** 对照了 Alethicode 与 Pythontopia 当前 Tutor AutoGen 阶段及事件集合；Pythontopia 保留 SSE、附件私有解析与 run 检查点，并按事件选择所需阶段。初次浏览器联测发现本地 Java 进程早于当前编译类启动，旧固定阶段校验导致反馈事件在 SSE 内失败；按 `./dev.sh --sandbox` 重启后，新鲜 Chromium 流程注册/登录、加载训练题、完成导读与“没帮助→换种解释”两事件均通过，DeepSeek completion 2/2 HTTP 200、文本卡片分别 278/348 字符、页面错误 0，临时账号已删除。此证据不替代历史 PNG 输入测试，也不重复运行通用模型探针。

本轮后续安全续扫复现 `WeeklyEvaluationScheduler` 将单个学生 ID 与异常正文写入失败日志；增加合成哨兵后先 RED，再改为只记录异常类型，定向 `WeeklyEvaluationSchedulerTest` 4/4。无模型请求。

本轮安全续扫复核了远征动作的 run 行锁、服务端判题证据与题目指纹幂等，以及上传配额的用户行锁，未发现这些路径的新问题。XSS 扫描发现 Mermaid `classDef` 可将 `position:fixed` 写入 SVG，`init.themeCSS` 可加入外部 `url()`，原清洗会保留两者；现用 CSSOM 移除危险声明和非 SVG 作用域规则，同时保留安全配色。真实 Mermaid 输出回归 3/3、typecheck 与定向 ESLint 通过。未调用模型。

日志续扫发现世界睡觉流程的遗忘曲线降级日志会包含学生 ID 与异常正文；现仅记录异常类型，降级流程和返回保持不变。`WorldAdvanceServiceTest` 哨兵先 RED 后 GREEN（1/1），`git diff --check` 通过；未调用模型。

继续搜索到期 KC 读取失败时，`OrchestratorService` 会把完整异常栈写入日志；该方法在 Tutor 请求中携带账号上下文。现仅记录异常类型并保留空列表降级，哨兵先 RED 后 GREEN，`OrchestratorServiceTest` 与 `WorldAdvanceServiceTest` 合跑通过。未调用模型。

学习事件补充审查发现判题失败时补查题目难度会把数据库异常正文写入 DEBUG 日志；现保留题目 ID 诊断字段，只记录异常类型并继续以空难度结算。哨兵先 RED 后 GREEN，`LearningEventListenerTest`、`OrchestratorServiceTest`、`WorldAdvanceServiceTest` 合跑通过。未调用模型。

启动 NPC 人格种子的数据库失败路径也会把异常正文写入启动日志；改为仅记录异常类型，失败仍不阻断应用启动。哨兵先 RED 后 GREEN，四类日志回归合跑通过。未调用模型。

记忆引用 best-effort 落库失败日志曾包含学生 ID 与数据库正文；同一服务的外部 provider fallback 也去掉原始异常消息，统一只记录异常类型。合成哨兵先 RED 后 GREEN，`MemoryProviderRouterTest` 与其余四类日志回归合跑通过；记忆返回和审计落库 fail-soft 行为不变。未调用模型。

继续检查远征预取发现失败异常会被写入学生可读的 `error_message`，且领取题/落库失败日志带账号、KC/题目标识及异常正文；现统一使用固定重试提示并只记异常类型。新增哨兵先 RED 后 GREEN，`EndlessPregenServiceTest` 19/19；与周报测试合跑 23/23。未调用模型。

另发现画像地图初始化失败会把 Graph 异常正文写入学生运行审计；现改为固定 `graph-service-error`。合成响应先 RED，`CurriculumMapperServiceTest` 1/1；本批三类服务合跑 24/24。未调用模型。

本地运行态抽查：Vite、Graph `/healthz`、RAG `/healthz` 与 `/readyz` 返回 200；Spring `/readyz` 返回 503（现有记录指向可选视频字幕能力不可用）。此状态不作为 Tutor 或业务链路联测通过的证据。

本轮按主线学生页面为无编程题的 KC-005 请求一次 AI 补充练习。正常题目路由返回 HTTP 200，题面符合“计算机发展历程与趋势”，`aiGenerated=true`、`reviewed=true`、`totalTestCases=3`；`boss_problem_review` 新增 `APPROVED/AUTOMATED` 共享记录，`kind=NULL`。页面错误为 0，未正式提交或产生学习事件；本次 completion 数未单独统计，也未做通用模型探针。两名未触发出题的临时账号已删除；请求者因审核来源外键限制改为 `DISABLED` 并撤销会话，测试预取记录已清理，批准题和审计来源保留。

本轮发现正在运行的 Graph/RAG 容器仍使用 FastAPI 0.115.14 与 Starlette 0.46.2，和源码依赖范围不符；按当前 Dockerfile 约束重建后切换两个本地服务，版本为 FastAPI 0.143.1、Starlette 1.8.0，健康/就绪 HTTP 200，`pip check` 均无冲突。Graph 镜像安装增加 120 秒超时与 5 次有限重试，解决 Aliyun 镜像短暂下载超时。开发 venv 同步当前依赖后，移除了不属于 Graph 的 `browser-use`/Google GenAI 残留，以及 RAG venv 中旧 LangGraph 残留；OSV 复扫从原环境的 25 个告警包收敛至 2 个仅存在于 Graph 可选开发 extra 的包（`diskcache`、`ragas`），当前公告未列修复版本，运行镜像不包含它们。

pytest 的临时目录漏洞 CVE-2025-71176 仅在 pytest 9.0.3 修复；Graph/RAG 的开发依赖约束现为 `pytest>=9.0.3,<10`、`pytest-asyncio>=1.3,<2`。同步后 `pip check` 通过；Graph 1902 passed/1 skipped，RAG 166 passed。没有 DeepSeek 请求。

额外安全检查：对 `PracticeSubmissionRepository`、`LearningResourceRepository`、`JdbcUploadChunkRepository`、`QuestRepository` 的动态 SQL 构造点做静态审查；拼接部分均为固定 SQL 片段，用户值通过参数绑定，无确认的 SQL 注入问题。邀请重置闭环也已由真实 Chromium 验证：教师重置后旧码 HTTP 404，新码入班 HTTP 200，页面错误 0，临时用户/班级/成员均清零；没有模型请求。

继续日志隐私审查：远征预取成功/失败、NPC 寒暄降级、Boss Twin 难度兜底、判题 SYSTEM_ERROR 日志曾记录学习者 ID 或异常正文；现只保留任务/提交标识、KC/题型上下文或异常类型。6 条哨兵断言先 RED，后续 7 个相关 Java 测试类共 142/142 通过。没有 DeepSeek 请求。全仓业务成功审计日志尚未逐项审查，不将本批结果视为完整日志审计。

2026-10-16 远征前置只读复核：本地审核库没有第 2 章以上 `dual_track`/`closed_loop` 题；KC-008 的真实先修链为 KC-004→KC-003→KC-008。满足 KC-003 掌握门槛的账号仅 1 个且没有活动 run，账号未被确认是 QA 数据，因此没有借用或改写。没有启动远征、生成题目或调用模型。该闭环继续保持待完成。

本地栈恢复记录：现有 Vite 占用 5173，所以 `./dev.sh --sandbox` 第二次启动失败；未结束现有 Vite，改为单独恢复后端，Vite/backend/Graph/RAG 健康检查均为 200，Piston 的 Python 3.12.0 runtime 可用。`.env` 中 JWT secret 是占位配置，当前服务使用进程内临时密钥，原登录 token 可能需要重新登录。课程 manifest 的生成时间戳已恢复；没有业务数据写入。

- [x] 已落地的安全基线：凭据通配 CORS、媒体错误脱敏、注册角色白名单、教师邀请码、DISABLED 账号拦截、JWT tokenVersion 撤销、公网注册/登录/加入码限流契约；Auth/JWT/CORS 有回归测试。
- [x] 课堂加入码账号限速：30 次/10 分钟；Tutor 工作流错误对学生脱敏。
- [x] Tutor 用量锁按用户账号与上海自然日生效；本地开发明确关闭应用日限额，部署默认开启。
- [x] DeepSeek 文本与合成 PNG 图片输入均有历史真实调用成功记录，Tutor 输出为文本；Graph 所有生成路由复用 DeepSeek host/model 白名单。RAG 向量使用本地 hash/BGE，讯飞 embedding 仅是不可选的未实现占位；TTS、ASR、旧讯飞文生图/图片理解硬关闭。较新的请求另出现 Tutor relay 超时和 DeepSeek 503，当前不重复探针，稳定性仍未验证。
- [x] 无尽远征采用 Phaser Boss 场景，包含判题受击/失败体力反馈及登记过的 CC0 音效；真实三阶段通关已有浏览器记录。
- [x] AI 编程题复用现有审核与共享题库；KC-010 已真实判题通过，KC-004、KC-002、KC-006、KC-011 均由正常学生补题路径生成并经自动审查入库。KC-002 另由第二名学生从已批准题库取题，服务端 3/3 判题通过；KC-006 由另一名学生从共享题库取题并经服务端判题通过，临时账号、学习事件与掌握度事件已清理。KC-011 目前只有自动批准记录，尚无正式学生判题或掌握度证据。KC-002/004/006 的普通主线题 `kind=NULL`，不满足远征战斗题型。
- [x] Twin P0 状态聚合、证据可信度、周报真实增量、共享 AdaptivePracticePolicy 和带证据/下一步的学习分身页面已有代码与定向验证。
- [x] 首页与学习分身对 Twin 推荐使用同一目的地规则；未评估 KC 返回主线、到期复习进入复习、已评估 KC 进入远征。
- [x] 世界 HUD 中未评估 Twin 推荐的真实浏览器点击：关闭可选探针后鼠标展开目标面板并到达主线地图，0/65 已评估、掌握证据未改变、浏览器错误为 0；修复 `role="button"` 标题被拖动手势拦截的问题。
- [x] 远征对服务端不可判定的无效题提供重新出题操作，不扣体力也不提交结算；加入 HUD 拖拽指针回归后，5 个受影响前端测试文件共 76 项、typecheck、ESLint、diff 检查通过。
- [x] AutoGen 对照审查确认 Pythontopia 保留自有 Tutor SSE、授权、附件和 V199/V203/V208 run 检查点契约，并按事件选择阶段；不移植 Alethicode 的 V124/thread dispatcher。
- [x] 既有只读复核未发现确认的 P0/P1 代码缺陷；认证、主要 IDOR、判题 fail-closed 与 CORS 有实现和测试落点。本轮复查确认注册/教师邀请码、停用账号/JWT 代次撤销、Piston 默认与 subprocess fail-closed、排行榜按服务端 verified attempt 结算均有代码实现。生产 Piston 若无私网 ACL 或鉴权代理，仍有未授权执行风险；本地无法代表生产确认。
- 明细见“总任务清单”安全审计项：逐类闭合判题隔离与部署边界、远征状态篡改/幂等、媒体上传、SSRF、XSS、SQL、速率限制、依赖与敏感日志；只对确认问题修复并补回归。远征快照 PUT 的篡改边界已由代码审查和 `ExpeditionRunServiceTest` 11/11 核验：客户端进度不会写回，服务端只返回权威状态。SSRF 当前代码复核覆盖唯一用户目标触发的 BROWSE 导航，服务端注入 host allowlist，Graph 每次导航/重定向验证公网 DNS、scheme、标准端口并将 Chromium DNS pin 到公网地址；对应 allowlist/executor 测试 58/58 通过。敏感日志已修复上传、索引、trace、Piston、Mem0/Zep、Graph retry、画像访谈、NPC 对话、RAG embedding、B 站问答/搜索、Feynman、BROWSE、Boss 出题/判题与章测 Graph 错误中的已复现泄露；RAG 启动/自愈/索引重建和 embedding 降级异常现只记录异常类型，503 返回固定说明，哨兵与定向测试共 33/33。其余已有修复包括本地 Piston Compose 配置与运行态仅接入专用 `judge-execution` 网络、宿主端口限制为回环、Python 3.12.0 执行冒烟成功（输出 4）、Java 适配器对远端失败 fail-closed；MetaReflection 调度异常和反馈汇总日志、Tutor/视频 Graph 兜底、DialogContext、MemoryRetrieval、LearningPath、WorldState 降级日志也已移除异常正文。新扫到 Boss 题目存档反序列化异常会把 payload 片段及 owner/problem 标识写入日志；现改为仅记异常类型，`EndlessPregenServiceTest` 17/17（RED/GREEN）。本轮修复本地运行容器与开发 venv 中的 FastAPI/Starlette 和 pytest 漏洞版本；Graph/RAG 全量测试分别 1902 passed/1 skipped、166 passed。OSV 复扫剩余 `diskcache` 与 `ragas` 两项可选开发依赖告警（当前无修复版本），不在运行镜像中。其余异常日志与生产 Piston ACL 仍待核实；当前 `/readyz` 整体因已记录的 `VIDEO_TRANSCRIPT_UNAVAILABLE` 返回 503，judge readiness 本身为 ready。
  - 10/16 动态提示词边界：资源画像/记忆、审查反馈和检索证据，以及章测画像、侧重点、错因、已用题目和审查意见现作为 `user` 数据；章测证据页 ID 白名单及题量/输出/去重规则留在 `system`。已用题目的单条上下文保留原有 300 字上限。Graph 全量测试 1911 passed/1 skipped、Ruff 与 diff 检查通过。另对真实课件 RAG 证据的章测 prompt 发起 1 次 DeepSeek completion，解析出 1 道单选题；不再重复通用探针。
  - 10/16 Tutor 历史角色边界：发现 `TutorTurn.role` 任意字符串会原样变成模型消息角色；现限制为 `user`/`assistant`。回归先 RED 后 GREEN，Tutor SSE 与流异常定向测试 16/16。安全类别审计仍未完成。
  - 10/16 关联 Graph 历史角色静态扫描：Orchestrator/Profile 请求历史已用 `Literal` 限定 `user`/`assistant`，`DebateRound` 为内部固定阶段角色；未发现第二条请求历史升权路径。该结果不代表动态 prompt 字段审计完成。
  - 10/16 公共异常日志续审：全局 500 与客户端断连处理器不再输出异常消息/堆栈或原始 URL，改记方法、Spring 路由模板、异常类型和 traceId；章星重算降级日志不再包含学生 ID/异常正文。两个测试类先 RED 后 GREEN，合计 10/10。全量安全审计仍开放。
  - 10/16 工坊日志续审：画像维度、周报洞察、两类记忆摘要及掌握度难度降级日志去掉学生 ID，只保留异常类型和原有 fallback。一个合成哨兵覆盖 5 种失败路径，先 RED 后 GREEN；`ResourceWorkshopServiceTest` 34/34。未发模型请求。
  - 10/16 本次续审：章测预生成与 Orchestrator 的 fail-soft 日志不再附带上游异常正文/堆栈，仅保留异常类型；判题触发的任务自动完成失败日志不再包含学习者任务 ID、提交 ID 或异常正文，仅保留业务错误码。哨兵 RED 后 GREEN，三个测试类共 11/11。敏感日志审计仍未完整闭合。
  - 10/16 前端 HTML 注入点续审：扫描所有 `v-html`/`innerHTML` 写入，确认实际写入前均经过 DOMPurify 或 Mermaid 专用清洗；未发现新增绕过点。后端与第三方内容边界仍在总审计中。
  - 10/16 Markdown XSS 回归：`MarkdownView` 新增恶意 Markdown 测试，断言脚本、`onerror` 和 `javascript:` 链接属性被清除；定向 Vitest 3/3 通过。仅闭合前端富文本渲染子项，安全总审计仍开放；没有模型调用。
  - 10/16 附件边界静态复核：工坊上传有扩展名白名单、5 MiB 单文件与每用户配额行锁；PDF 限页/文本长度，DOCX 限条目数及 16 MiB 解压量；Tutor 图片校验签名、5 MiB、用户/会话配额与 TTL 读取。现有测试源码覆盖主要拒绝和 owner/session 分支，本轮未重跑旧套件。未发现已证实漏洞；第三方解析器与生产代理限制仍需单独核实。
  - 10/16 SQL 拼接面静态复核：发现的动态片段均为固定可选条件、枚举映射的排序列/方向或按列表长度生成的 `?` 占位符；用户查询值继续走参数绑定。未发现用户输入进入 SQL 语法的路径；仅覆盖源码拼接点盘点。
  - 10/16 限流静态复核（部分）：正式判题以用户锁串行且 10 秒 1 次；AI 操作默认有账号日额度与全站 provider 日额度，`dev.sh` 按本地开发要求关闭；Nginx 对认证入口、班级邀请码、上传及下载计数写入限 IP。公共 refresh 路由已补齐并解析验证。未盘点所有 API 的通用限流，不据此关闭该审计类别。
  - 10/16 异常对象日志参数扫描：Java 命中只在硬关闭的旧讯飞 ASR/TTS/图片理解路径上的异常栈，以及课堂 SSE 首次发送失败的 `IOException`；日志参数未直接包含查询、代码、图片或用户消息。Graph/RAG 未发现 `logger.exception()`。这只是异常参数窄扫描，未覆盖所有包装器/多行动态字段，安全总审计仍开放；本轮没有代码改动。
  - 10/15 运行已有 `node deploy/scripts/test_dev_piston_sandbox.mjs`：Python 3.12.0 执行成功；宿主私有文件不可见、临时文件限定在 job 目录、共享 `/tmp` 写入被拒、loopback/compat socket 访问被阻断、超时进程以 SIGKILL 终止；临时标记文件已清理。Docker 运行态检查确认 Piston API 容器非 privileged、只读 rootfs、丢弃全部默认 capabilities、`no-new-privileges`、仅接入 `judge-execution`，并仅在 `127.0.0.1:2000` 暴露；该检查只覆盖本地开发运行态，完整安全审计及生产 ACL 仍未关闭。未发模型请求。
  - 10/15 续扫：周报定时任务的单人失败日志不再输出学生 ID 或异常正文，只记录异常类型；哨兵先 RED 后 GREEN，`WeeklyEvaluationSchedulerTest` 4/4。无模型请求。
  - 10/15 续扫远征预取：失败状态不再保存上游异常正文；claim/persist 异常日志只记异常类型。合并验证 `EndlessPregenServiceTest` 19/19、`WeeklyEvaluationSchedulerTest` 4/4；生产行为以外未调用模型。
  - 10/15 画像地图初始化失败审计：`resource_run.output_artifact` 固定记录错误码，不再持久化 Graph 异常正文；`CurriculumMapperServiceTest` 1/1，三类服务测试合跑 24/24。
  - 10/15 XSS 续扫：真实 Mermaid 渲染确认 `classDef` 的定位样式与 `init.themeCSS` 的外部 URL 会穿过原 SVG 清洗；现通过 CSSOM 过滤布局覆盖、外部资源与 SVG 外作用域规则，安全颜色仍保留。`sanitizeSvg.spec.ts` 3/3、typecheck、定向 ESLint 通过。未发模型请求。远征动作行锁与上传配额用户行锁复核未发现新问题。
  - 10/15 日志续扫：世界睡觉的遗忘曲线失败日志不再写学生 ID 或异常正文，只保留异常类型；哨兵先 RED 后 GREEN，`WorldAdvanceServiceTest` 1/1，睡觉返回路径仍正常。未发模型请求。
  - 10/15 编排日志续扫：Tutor 请求读取到期 KC 失败不再写完整异常栈；只记录异常类型并保留空列表 fail-soft。哨兵先 RED 后 GREEN，`OrchestratorServiceTest` 与睡觉日志测试合跑通过。未发模型请求。
  - 10/15 学习事件日志续扫：题目难度读取失败不再记录数据库异常正文；保留题目 ID 和空难度结算兜底，只记录异常类型。哨兵先 RED 后 GREEN，三个定向后端测试类合跑通过。未发模型请求。
  - 10/15 启动日志续扫：NPC 人格种子失败只记录异常类型，仍 fail-soft 不阻断启动；哨兵先 RED 后 GREEN，四类日志回归合跑通过。剩余命中集中在已硬关闭的讯飞 provider 与不含学生上下文的资源/SSE 读取错误；整体安全审计仍开放。
  - 10/15 记忆审计续扫：`agent_memory_ref` best-effort 写入失败不再记录学生 ID 或数据库错误正文；同服务的外部 provider fallback 同样仅记异常类型。哨兵先 RED 后 GREEN，五类日志回归合跑通过。
- [x] 禁用媒体 provider 不分配无用请求状态：旧图片生成 provider 固定关闭；原入口会在返回 unavailable 前先创建 per-user rate-limit counter，第 51 次请求再错误返回 429。RED/GREEN 调整顺序后，`XfyunImageServiceTest` 2/2，重复调用始终返回 disabled 错误，无 DeepSeek/上游调用。
  - TTS 端点也有 HTTP 层回归：旧讯飞凭据下 `POST /api/media/tts` 返回 503 `TTS_DISABLED`，在服务启用检查前不创建 WebSocket。`MediaTtsControllerTest` 与 `TtsPropertiesTest` 合计 3/3 通过；没有外呼。
- [x] 本轮安全续扫修复：MemoryBrief 网络异常和 schema 校验日志不再输出学生查询、记忆原文或异常栈；上传配额在 `getBytes()` 前检查，超额请求不再额外复制文件到堆。RED/GREEN：`test_memory_brief_client.py` 27/27、`UploadServiceTest` 18/18。未发 DeepSeek 请求。
- [x] NPC 定时预热失败日志已去掉异常原文和学生 ID，只记录异常类型；合成哨兵先 RED 后 GREEN，`NpcGreetingPrewarmJobTest` 6/6。无模型请求。
- [x] BROWSE 底层执行与外层派发异常不再把异常原文（例如含 token 的 URL）写入结果、SSE 步骤或日志；缺依赖保留固定提示。两条哨兵回归先 RED 后 GREEN，`test_browse_executor.py` 与 `test_agentic_loop.py` 共 79/79，Ruff 通过。无模型请求。
- [x] 编排记忆 brief 与 loop 记录 sink 的失败日志不再输出学生查询或存档异常正文；两个哨兵先 RED 后 GREEN，`test_agentic_loop.py` 42/42、Ruff 与差异检查通过。无模型请求。
- [x] 编排 Postgres checkpointer 连接失败日志不再输出异常正文或 DSN 密码；哨兵先 RED 后 GREEN。与记忆 brief/sink 回归合并验证 `test_agentic_loop.py`、`test_orchestrator_checkpoint.py` 共 48/48，Ruff 与差异检查通过。
- [x] RAG KC 前置图数据库失败日志只保留异常类型；哨兵先 RED 后 GREEN，`test_kc_graph.py` 8/8。真实本地 RAG 查询 `input 函数`、`for 循环`、`列表` 均 HTTP 200、返回 5 条带 page_id/document_id 的引用；RAG health 200，Ruff 与差异检查通过，无 DeepSeek 请求。
- [x] 敏感学习数据日志脱敏：上传解析、上传语料索引、学生调试轨迹、Piston 远端错误、Mem0/Zep 错误响应、通用 Graph retry、画像访谈、NPC 对话、RAG embedding、B 站问答/搜索、Feynman、BROWSE、Boss 出题/判题、章测 Graph 请求、ReflectionService、Tutor 落库/画像/班级/记忆/历史上下文/提交反馈、MemoryBriefService 降级、工作流检查点及 SSE 结果投影失败均不记录文件名、学习文本、学生输出/代码、模型回答、会话标识或异常原文；投影异常传给 SSE emitter 时改用固定错误文案。RED/GREEN 套件分别为 `UploadServiceTest` 17/17、`UploadCorpusIndexingServiceTest` 1/1、`PythonTraceCollectorTest` 1/1、`PistonPythonSandboxTest` 10/10、`Mem0MemoryProviderTest` 4/4、`ZepMemoryProviderTest` 3/3、`GraphClientRetryHelperTest` 1/1、`ProfileServiceFailureTest` 2/2、`NpcDialogGraphClientTest` 2/2、`EmbeddingClientTest` 7/7、`BilibiliInsightServiceTest` 4/4、`FeynmanGraphClientTest` 4/4、`BrowseGraphClientTest` 5/5、`BilibiliVideoServiceTest` 2/2、`BossServiceTest` 53/53、`ChapterTestGraphClientTest` 2/2、`TutorServiceFailureTest` 2/2、`TutorMemoryBriefTest` 4/4、`TutorHistoryBriefTest` 6/6、`TutorWorkflowServiceTest` 33/33、`TutorWorkflowGraphClientTest` 5/5、`MemoryBriefServiceTest` 7/7、`ReflectionServiceTest` 8/8、`WorkshopGraphClientTest` 4/4、`TutorWorkflowSubmissionFeedbackListenerTest` 6/6。
  - 10/15 续扫：工坊学情上下文、NPC 问候、记忆 embedding、资源工坊、画像 fallback、Feynman、Graph SSE、媒体任务、Tutor relay、章测预生成、远征存档反序列化和远征预取的异常日志不再输出上游/学生内容；新增 3 个测试类 29/29，`WorkshopGraphClientTest` 4/4、`TutorWorkflowSubmissionFeedbackListenerTest` 6/6。其他异常路径和生产 Piston ACL 仍在审计中。
- [x] 本轮补齐 Graph 路由兜底日志：Tutor 工作流意外失败、B 站元数据抓取和概述生成异常都只记录异常类型，不输出异常原文或 traceback；Tutor SSE fail-fast、视频概述 fail-soft 保持原行为。`test_tutor_workflow.py` + `test_video_insight_route.py` 27/27，Ruff 通过。
- [x] MetaReflection 调度失败日志仅记录异常类型，反馈汇总策略日志不再输出采用率/评分等可关联汇总；RED/GREEN，`MetaReflectionServiceTest` 7/7。
- [x] 四条后端降级日志脱敏：DialogContext 偏好、MemoryRetrieval 同章 KC、LearningPath 错误模式和 WorldState 连击读取失败均只记录异常类型，原降级结果不变；合成哨兵先 RED 后 GREEN，四个测试类 29/29。
- [x] 学习事件镜像、访谈画像同步和预防讲义生成失败日志仅记录异常类型；合成学生/资源哨兵先 RED 后 GREEN，三个测试类 19/19，原事件隔离和后台 fail-soft 行为不变。
- [x] 依赖漏洞盘点：前端 lockfile 的 npm audit 为 0 项；Docker 约束下 Python Graph/RAG 运行时依赖及 Maven 95 个 compile/runtime JAR 的 OSV 查询曾均为 0 项。10/15 更新实际运行容器与两个开发 venv 后，FastAPI 0.143.1、Starlette 1.8.0、pytest 9.1.1、pytest-asyncio 1.4.0；Graph/RAG `pip check` clean，OSV 合并扫描只报 Graph 可选 `memory`/`eval` extra 带入的 `diskcache 5.6.3` 与 `ragas 0.4.3` 两项，当前无修复版本且不在运行镜像。Graph 1902 passed/1 skipped、RAG 166 passed。OWASP Dependency-Check 13.0.0 因缺 NVD API key 未生成报告，不能作为扫描结果；完整安全清单仍开放。
- [x] 管理运维页读取失败恢复：现有页面展示错误并提供“重试”；组件回归 `AdminOperationsPage.spec.ts` 3/3，真实本地 Chromium 通过路由模拟首读 503、重试 200、错误消失与数据展示（页面错误 0）。鉴权刷新与运维 API 均为模拟，未验证后端授权。
- [x] 教师班级详情成员读取失败恢复：组件回归 `ClassroomDetail.spec.ts` 13/13；本地 Chromium 对成员 GET 模拟首读 503、重试 200，错误区域可见、成员恢复显示，页面错误 0。鉴权与 API 均模拟，不计后端授权联测。
- [x] 管理台共享表格表头/圆角/分页已按 Alethicode 样式收敛；保留手动跳页和现有数据字段。
- [x] 课堂核心、成员、任务和看板字段已与后端 DTO/迁移对照；发现前端 `RemediationResultDto.partial` 可选而后端必返，已改为必填并通过 `npm run typecheck`。
- [x] 管理台格式对齐：对照 Alethicode 的 `Home.vue`、`Panel.vue`、`style.less` 与 `common.less`，Pythontopia 的侧栏/窄屏导航、面板、控件、表格密度、分页、蓝灰色 token 与圆角均已落到现有 3 个真实 API 页面；最新校正工具栏间距为 12px、移除 1440px 内容宽度上限、窄屏内边距为 16px。管理台定向页面测试 12/12、typecheck、定向 ESLint 与 diff 检查通过。Pythontopia 继续使用自身类型化 DTO 和真实业务接口；Alethicode 额外菜单对应的课程包、配置、判题服务等模型不属于“格式对齐”，不创建空页面或复制旧数据结构。剩余页面动作检查由 Web 动作矩阵跟踪。
- [x] 管理员真实浏览器用户管理流程：登录、搜索、停用/恢复、禁用账号登录拒绝；临时账号已清理。
- [x] 运维概览将五个已知后端动作键映射为中文建议，原始键保留在提示文本中。
- [x] KC-004 根知识点 AI 补题：从正常主线练习按钮发起请求，通过 Piston 自测和现有质量审查后写入共享题库为 `APPROVED/AUTOMATED`。实际请求 6 个 DeepSeek completions（前 5 个候选自测输出不符，第 6 个通过）；未提交答案或新增掌握证据。
- [x] KC-002 主线 AI 补题：从第 1 章第 3 节正常学习页发起 1 次生成请求，题目 `APPROVED/AUTOMATED`（审核 ID 83）；第二名学生从共享题库取题并服务端判题 3/3。临时答题账号及其掌握事件已清理，审核请求者因审计外键保留在本地数据库。
- 明细见“总任务清单”远征项：补齐有效先修解锁后的第 2 章以上前沿 KC 的 battle-compatible 题型并验证有效先修账号从服务端活动 run 刷新恢复；同时复核地图覆盖预检、超时取消、失败恢复、Boss 多阶段与客户端状态伪造边界。当前根 KC 审核库普通题为 KC-001 `debug` 与 KC-001/002/004/006 `kind=NULL`；新增 KC-001 普通 AI 题由真实主线流程生成并自动审核，另一学生账号正式判题 3/3，掌握度事件为 0.14。上述普通题仍不适配战斗节点 `dual_track`/`closed_loop`。KC-006 主线题由审核 ID 84 自动批准，第二名学生从共享库取题并正式判题通过；KC-011 也已有普通主线自动批准题，但尚无正式学生判题或掌握度证据。缺题时会按节点 `challengeKind` 生成并经质量门禁后自动入共享库。已修复地图默认预取绕开全局批准题库的问题：按章节/stage/KC/题型/难度先复用已批准题，再进入用户现有 READY 队列；`EndlessPregenServiceTest` 16/16。KC-001/002/004/006 属第 1 章根 KC，远征当前会过滤第 1 章；它们的题型库存不是该闭环的开局覆盖验收对象；尚无合格活动 run 的端到端题型生成与刷新恢复证据。地图不预检题库覆盖，生成失败仍可重试或结束本局。最新本地只读聚合为 21 条 `status=ACTIVE` run（16 challenge、3 map、2 over），有 1 个账号达到候选先修掌握门槛，但其当前没有活动 run。测试/历史账号构成未知，不能据此推断真实用户完成率；先修候选链包括 KC-004→KC-003→KC-008 与 KC-002→KC-005→KC-015；须通过真实判题达到门槛，再验证可恢复 run。 跨章节先修缺陷已修复：请求/结算以活动 run 节点绑定的 KC 与题目证据校验，不再要求前置 KC 的实际章节等于聚焦章节；相关远征服务、PostgreSQL 集成、控制器、Boss 与预生成定向回归 126/126。真实有效 run 的题型生成和刷新恢复仍未验收。 已有后端/PostgreSQL/预生成覆盖：服务端地图与快照权威、节点可达性、结算题目指纹幂等、Boss 三阶段、预取超时/失败/排队取消；跨章节修复相关套件 126/126。缺题地图覆盖预检及真实合格账号的浏览器地图/战斗仍待验收。 10/16 通过正常学生主线入口为无题 KC-003 生成一题并自动审查为 `APPROVED`，复核题面与 KC 相符；未提交答案。该题 `kind=NULL`，不计入战斗题型覆盖。
  - 10/16 审核库只读复核：第 2 章当前有 KC-010 `fill_blank` 与 KC-010/011/012/013 的 `kind=NULL` 批准题；第 2 章以上仍无 `dual_track`/`closed_loop` 题。查询只聚合章节/KC/类型/数量，未读题面、隐藏测试或用户身份；未创建题目、判题或学情记录。合格先修账号与活动 run 的真实闭环仍待完成。
  - 10/16 远征真实浏览器验收可行性：后端健康、Graph、RAG、Web 均 HTTP 200；后端就绪仍因视频字幕 503。临时 QA 学生登录成功，但主线第 2 章锁定，首节要求真实观看 7:20 视频、阅读 6 页并完成 4 道题。未写入掌握度、提交或远征 run；账号已删除并核对为 0。未绕过先修门槛，活动 run 刷新恢复继续待验证；没有模型调用。
  - 10/15 通过 `/mainline/stage/3` 的正常 AI 补题入口为 KC-001 生成题目，浏览器 API HTTP 200，`aiGenerated=true`、`reviewed=true`、页面错误 0；同账号重新从审核库取题后正式判题 3/3。题目及审核 requester 已保留在本地库；QA 用户 ID 697 因审核审计外键不能删除。该学生 KC-001 掌握度只有 0.14，远征候选要求先修 KC 达到 0.6；没有重复作答凑分，也未尝试创建不合格 run。

- 明细见“总任务清单”Web 验收项：学生、教师、管理员主要路由/动作矩阵仍需完成，逐项覆盖可见控件与成功/失败/空态。11 条主页面路由已有真实浏览器路由壳检查（0 页面错误、0 个 5xx）；当时为避免写入拦截了 `/api/game/bootstrap` 和认证刷新，因此不计为页面初始化或完整动作通过。刷新请求由每次整页加载时的 `hydrateFromStorage()` 正常触发，逐 URL 重载解释了多次请求；认证/角色守卫、管理员用户停用/恢复、World HUD 未评估 Twin → 主线地图另有真实验证。主线章节抽屉开合、进入学习页再返回通过 1/1；小镇 HUD/暂停菜单反复开关并进入工坊通过 1/1；主线章节键盘聚焦及 Enter 打开章节通过 1/1；指南页目录锚点更新 URL 并滚动到目标通过 1/1；本轮 `/library` 的「for循环」检索显示带页码证据，1/1；`/board?tab=classroom` 深链及班级→历史→今日页签切换通过 1/1；`/profile` 学习目标自评已更新，PUT 仅含 `scoreDelta`，`evidenceCount`/`confidence` 不变。`ProfileRoomPage.spec.ts` 已有页面层测试覆盖 Twin 主/备推荐与周报 insight/补强转到工坊；本轮空学情学生真实浏览器展开 `/profile-room` 近期变化、点击“生成下一步”并抵达 `/workshop`，工坊草稿已预填，周报 0 事件，模型/资源生成请求和页面错误均为 0。另一名临时学生通过真实 KC-016 编程题后，周报显示 1 条权威事件、1 个掌握变化；点击该 KC “补强”进入工坊并预填 KC 代码，没有模型请求或页面错误。临时账号（689、690、691）均已清理，KC-016 验收账号的判题与学情随账号级联删除。教师屋创建班级路径经真实浏览器验证：教师邀请码注册（201）、登录（200）、空班级态、新建班级并进入看板成功，页面错误 0；临时教师和班级分别核对为 0。此前新生点击“前往主线学习”到达 `/mainline-map`，页面错误 0，仅有认证刷新和学习状态读取，临时用户已清理。共享表格样式对齐已完成。管理台审核检索/驳回已有本地浏览器记录；当前无待审核项，不重复改变共享题库。本轮真实浏览器验证学生通过 `/home` 入班（HTTP 200），教师成员页看到并移除学生（HTTP 204），页面错误为 0，临时用户、班级和成员行均清理为 0；本轮另一真实 Chromium 路径完成班级任务发布与归档，HTTP 均为 2xx，页面经服务端刷新显示 `ARCHIVED`；最终直接 SQL 复核因 `status` 未限定表别名而失败，故 DB 状态交叉核验与页面错误计数不记为通过。临时用户、班级、任务清理计数均为 0。剩余 Web 页面动作与窄屏检查仍待验收。 新增真实导航回归修复已评估的第 1 章 KC 继续练习误入远征的问题：远征不开放第 1 章，现回到主线地图；`useNextSteps` 11/11、`ProfileRoomPage` 6/6、typecheck 与定向 ESLint 通过。
  - 10/16 `/library?docId=186&page=1` 翻页动作真实 Chromium 续验：下一页、上一页均更新页码和查询参数，页面错误 0；B 站检索被拦截，Tutor/模型接口为 0。页面唯一写请求是会话恢复 `POST /api/auth/refresh`，未提交作答或学习事件；临时学生已删除，`qa_library_` 用户残留计数为 0。
  - 10/16 `/bug-mine` 空态真实 Chromium 观察：注册后进入独立矿洞页，标题、`暂无错题` 与未选会话空态可见；错题本和遗忘回声 GET 均 200，1280px 无横向溢出，页面错误 0。临时用户已删除。自动脚本最后一个过滤条件把 Vite `/src/api/tutor.ts` 静态模块误认成 Tutor API，进程因此以断言错误退出；已从捕获路径分离出实际 `/api/*` 请求，未发现判题或模型端点。此项记为浏览器观察，不记为脚本全绿。
  - 10/16 `/bug-mine` 窄屏真实浏览器补验：320×780 触屏收起/展开错题本，页面无横向溢出；`/api/bug-mine/errorbook` 与 `/echoes` 均 200，页面错误 0、模型请求 0。一次性用户 ID 726 已按 ID/QA 前缀精确删除并确认残留为 0。
  - 10/15 复习大厅新生真实浏览器：空周报、服务端“尚未评估”基础 KC 推荐均显示，点击费曼入口到 `/feynman`；评估/Twin API 为 200，页面错误 0、模型请求 0，临时用户 700 已清理并确认无残留。
- [x] Web 错误态抽样：真实 Chromium 临时学生打开 `/chapter-test/99`，看到明确 1–7 章范围提示并点击返回主线地图；测评 API、模型 API、判题 API 为 0，页面错误为 0，临时账号已删除。有效章节自动调用 `prepare` 且可能触发出题，因此本次只走无效章节分支。
- [x] Web 页面动作续验：题库按 KC 筛选、题号搜索、清空恢复和“去做”进入训练题均由真实浏览器验证；复盘大厅未评估 Twin 推荐已修复并复验进入主线地图。`/examples` 已退役，路由静态重定向到 `/problems` 并保留 `kcCode` 筛选；其题库筛选动作已有浏览器证据。相关临时账号已清理，已验证动作页面错误与模型请求均为 0。
- [x] 题库深链边界：有效 KC 暂无编程题时保留 `kcCode` 并显示“0 题”；未知 KC 仍回退到全部。回归先 RED 后 GREEN，`ProblemListPage` 10/10、typecheck、定向 ESLint 通过。修复后真实 Chromium 验证 `/examples?kcCode=PY43-KC-001` 跳转并保留所选 KC；320px 下无横向溢出，页面错误与模型请求均为 0。
- [x] 窄屏抽样：320px Chromium 检查 `/mainline/stage/2`、`/problems`、`/profile-room`、`/library`，页面宽度均未溢出；学习分身“近期变化”折叠区在窄屏可展开。页面错误与 5xx 为 0，没有业务写入或模型请求，临时账号已清理。此为抽样，不代表完整路由矩阵。
- 明细见“总任务清单”工程收尾项：按全 Web 审查清单继续修复可复现的命名、交互、错误态、可访问性和窄屏缺陷；strict TypeScript 源码扫描未发现 `any` / `as any` 或空命名 `void` 函数；工坊 `quiz_finished` 的过时 TODO 已改为说明客户端事件不作可信证据，未改运行逻辑。已对照 Google Java Style、Vue 官方风格、typescript-eslint 命名规则与 OWASP Logging Cheat Sheet；仅整理本轮新增 Java imports 并应用敏感日志要求。全仓 Spotless 与本轮触及文件的定向检查都发现既有格式差异；未保留格式化造成的无关改动，也未做批量格式化。
- [x] Maven 全量失败已做报告归因：记录的 7 个错误里，6 个是 Testcontainers Docker 动态宿主端口绑定失败，1 个是长期仿真的 PostgreSQL 连接重置；0 个断言失败。尚未修复 Docker/连接环境，也未重跑全量套件。
- [x] Tutor SSE 长等待的本地运行态根因已定位并修复：Graph 源文件比正在运行的容器新，Uvicorn 命令没有自动重载，旧进程未加载心跳改动。开发 Compose 限定监视 Graph 源码目录并启用 reload；Compose 契约先因缺少 reload 断言失败，修复后通过；重建容器后 `/healthz` 200、容器 healthy；临时 `.py` 文件触发 WatchFiles 重载后删除，服务仍健康。
- 范围外/未核验：生产公网边缘限流、Piston ACL 与 ECS 运行态按用户要求不作为当前任务重点；Piston API 本身不验证 Bearer token，只有生产侧私网 ACL/鉴权代理能关闭该风险。当前仅维护本地配置和源码边界证据，不访问远端或部署。
- 明细见“总任务清单”Twin 效果项：在不伪造画像的前提下设计并执行 Twin A/B 与 7 日延迟复测；样本不足时保持待测，不宣称提升。
- [ ] 后续增强（已按精简计划暂缓）：未来分岔的真实学习结果评估与 BKT 概率模型不属于当前闭环验收门槛。
- [x] DeepSeek Web Tutor 全链路文本验收：运行态先出现 Graph 意图路由 401，核实为 Java 缺内部令牌、Graph 使用 `.env` 占位令牌；按 `./dev.sh` 重启后两端共享临时强令牌。单次 Playwright 请求命中 `/agents/intent/route` 与 `/agents/tutor/ask/stream`，均 HTTP 200，非空文本断言通过（1/1，7.2 秒）；临时账号已清理。不要重复 provider 探针或图片调用。
- 明细见“总任务清单”性能项：最后执行；功能、安全和工程验证收敛后测量出题 P50/P95、缓存命中与失败恢复，不把目标值写成实测。


### 2026-10-15 最新续接

- 本地提交 `99126642` 已完成；当时工作树干净。退役 `/examples` 改为静态重定向，旧页面及过期测试删除，KC 查询参数保留。路由集成回归 27/27 通过。
- 后续审计确认上传解析失败日志曾写入原始文件名与底层异常；已按 RED/GREEN 修复，`UploadServiceTest` 17/17。当前工作树包含该代码/测试及清单更新，尚未提交。
- 前端 `npm audit` 从默认镜像得到 404（不支持 audit API）；指定官方 npm registry 后完成，0 项漏洞，未改 lockfile。
- 本轮未发起任何 DeepSeek 请求；文本/PNG 真实调用成功和后续 relay 超时、空 JSON、503 按历史记录保留，不重做通用探针。
- 当前续接补了复习大厅真实新生路径（空周报、服务端尚未评估 KC 推荐、费曼入口），API 200、页面错误 0、模型请求 0，临时账号清理为 0；并修复画像访谈 SSE、工坊 SSE 与 Tutor 提交反馈异常正文进入日志的问题，定向回归分别 2/2、4/4、6/6。另收齐本轮已改动 Java import 顺序，`git diff --check` 通过。
- 先推进附件扩展安全审计与远征真实恢复缺口；Twin 现有 P0 与管理台共享表格改造按已完成记录复用，不重复实现。

### 调研结论与实施边界（2026-10-15）

- 检索练习与分散练习在综述中被评为高效用方法，因此优先修好正式判题、复习到期和错误后的再练闭环；不以单次问卷或生成式模型改写掌握度。[APS 综述](https://www.psychologicalscience.org/publications/journals/pspi/learning-techniques.html)
- 学习分析综述指出隐私、学生控制和可信度仍有明显缺口；学习分身只展示可追溯证据与置信度，不推断人格/心理状态，不在样本不足时给效果预测。[Human-centred LA/AIED systematic review](https://doi.org/10.1016/j.caeai.2024.100215)
- 2026 年教育数字孪生综述指出概念定义常被混用、学习影响证据仍需加强；本项目采用轻量、行为驱动的“状态—策略—分身”闭环，BKT 和未来模拟暂缓到校准数据与评估方案具备后再做。[Frontiers review](https://www.frontiersin.org/journals/education/articles/10.3389/feduc.2026.1955212/abstract)
- Mega Crit 在 GDC 分享中描述了用数据驱动平衡并结合玩家反馈迭代《Slay the Spire》；这里借鉴“记录选路/失败/退出并据真实数据调平”的方法，不把其玩法结构当作教育有效性证据。[GDC Vault](https://www.gdcvault.com/play/1025731/-Slay-the-Spire-Metrics)

### 统一实现边界

- Learner State 由服务端已验证判题、练习事件、KC mastery 与 ReviewState 聚合；自评只影响目标/偏好，AI 洞察不改能力事实。复用现有 `LearnerTwinService` 与 `AdaptivePracticePolicy`，不新增画像表、在线 Agent 或模型训练。
- Expedition 的路线、难度、复习与补救依赖同一服务端学习状态；提交、体力、结算、排行榜和学习事件不得由客户端数值直接决定。题目优先查审核库与预生成队列，生成故障必须有有限恢复入口。
- Twin 页面展示状态证据、置信度和推荐理由；不展示未评估 KC 的虚假 0/50%、不推断人格/心理状态，不对成绩收益做未经校准的预测。
- 前端动作验收按学生/教师/管理员真实可见控件矩阵推进；性能采样安排在安全、功能和页面闭环后。

### 2026-10-15 当前续接快照

- 开始时 HEAD：`d5e4823b`，工作树干净；只读 Tutor/Alethicode、远征与安全审查未重跑模型或云端操作。随后只做了 RAG/公告板/画像的 Web 功能核验，跟踪文档有未提交更新。
- 早期快照把固定阶段 AutoGen 视为足够的替换；更细的逐事件对照发现这结论过宽。当前已按事件选择 AutoGen 阶段并保留旧检查点恢复。断开取消由 SSE 上下游中止传递；相同请求的检查点恢复由 25 分钟 sessionStorage 请求封套支撑；刷新后由活动会话投影恢复卡片。通用 Tutor 图片受格式/体积限制，不进入落库字段、AutoGen 状态或 SSE；训练场工作流继续用 owner/session 绑定的附件 ID。Pythontopia 沿用这些现有合同，不新增 event bus、cancel API 或通用图片表。Alethicode 的 V124 表不会照搬。
- 本轮已有的历史 DeepSeek 文本/PNG 成功不能证明服务持续稳定；随后出现过 relay 超时、空 JSON 与 503。只在具体未完成产品路径变化后再做一次有针对性的调用，不重复通用探针。
- 本轮审查未发现确认的高危代码缺陷。Piston 生产访问控制、符合先修条件的活动远征 run 恢复、KC 题型覆盖、Web 动作矩阵与 Twin 学习成效仍开放；性能继续最后。
- Piston 本地部署复核确认开发 Compose 只绑定 `127.0.0.1:2000`，现有安全契约也断言回环绑定；生产 Compose 不托管 Piston，依赖外部 URL 且只接受 HTTPS 或私网 HTTP。外部 Piston 是否真正有鉴权代理/网络 ACL 无法由源码确认，本轮未访问 ECS。

### 2026-10-15 本轮页面动作

- 本地 Vite/Spring/Graph/RAG 服务可用；学生账号访问 `/library` 并查询「for循环」，浏览器验收 1/1，得到带页码证据。学生角色的服务端直接 RAG 端点受限，页面走本地检索回退；该动作未调用 DeepSeek。
- `/board?tab=classroom` 深链选中班级任务页签，再切换历史路径和今日任务；目标内容和 `aria-selected` 状态正确，页面错误 0、模型请求 0，临时账号已清理。
- `/profile` 学习目标滑块提交一条自评；浏览器请求仅有 `scoreDelta`，后端重新读取后 `evidenceCount`/`confidence` 与提交前一致，页面错误 0，临时账号已清理。
- 临时 QA 学生已按精确用户名删除并确认计数为 0。没有重复跑 Tutor/DeepSeek、远征或性能测试。

### 2026-10-15 题库深链筛选修复

- 浏览器发现 `/examples?kcCode=PY43-KC-001` 正确重定向到 `/problems`，但该有效 KC 当前没有编程题，页面误将它当作未知 KC 并清空筛选。
- 保留题库 KC 元数据中的有效筛选，在下拉框显示该 KC 的“0 题”选项；不存在的 KC 仍清除。新增回归先 RED（筛选值为空）后 GREEN。
- `npx vitest run tests/pages/ProblemListPage.spec.ts`：10/10；`npm run typecheck`、定向 ESLint、`git diff --check` 通过。用户 678 清理核对为 0。
- 这条零题目边界只跑组件回归，没有重复浏览器路线或任何 DeepSeek 请求。接续工作转向清单中的 KC-006 远征题覆盖。

### 接续顺序

1. 先完成本地安全审计中尚未逐类闭合的执行隔离、媒体/鉴权、异常日志和边缘限流代码；只修复可复现问题，生产 Piston ACL/ECS 不在本轮范围。
2. 再补可达的第 2 章以上远征前沿 KC battle-compatible 题型，按真实先修路径取得合格掌握状态后验证活动 run 刷新恢复；不伪造掌握度。
3. 完成尚未覆盖的 Web 路由、可见控件、错误态和窄屏矩阵；管理员停用/恢复与审核检索已有证据，不重做。
4. 工程收尾只修复可复现问题。DeepSeek 文字/图片输入和 Tutor 文本真实调用已有历史成功记录，不重复通用探针；仅在具体产品链路、配置或传输再次改变时做一次定向验证。
5. Twin A/B 与 7 日复测等待真实样本；样本不足时标记待测。功能与工程项目收敛后才采样性能。

### 2026-10-15 当前清单续接

- 当前 HEAD：`49ee162a fix: secure AI streams and expedition prefetch`；续接开始时工作树干净。此后只更新本清单与执行状态，没有产生代码改动。
- 附件要求中的 Pythontopia Tutor AutoGen 替换已经满足：普通 Tutor 问答和六阶段导学均经 AutoGen；保留本仓 SSE、授权、附件与 run checkpoint 契约。导学面板现有显式中止和同 requestId 恢复；Alethicode 的 V124/thread dispatcher 与异步 run API 不兼容，不复制。
- 本轮静态复核确认 Java `SubprocessPythonSandbox` 是 fail-closed 退役适配器；Graph 题目/答案运行经 Piston 远程隔离，Piston 不可用时失败关闭。AST import 检查只作前置策略，不能替代执行隔离。未运行沙箱攻击测试，未发 DeepSeek 请求。
- 真实 DeepSeek 文本/PNG 调用已在此前全栈记录中成功，同时历史存在 relay 超时、空内容及 503；不再重复提供商探针。

---

## Task 1: Reject credentialed wildcard CORS origins

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/auth/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/pythontopia/auth/security/SecurityConfigCorsTest.java`

**Steps:** Add a failing assertion that a subdomain wildcard is rejected; run `mvn -q -Dtest=SecurityConfigCorsTest test`; change allowed origin patterns to exact origins and reject any `*`; verify configured exact origins still match; rerun the focused test and Spotless for touched Java.

**Status:** Code and CORS regression tests complete in `f2176675`. Focused Maven tests pass; focused Spotless still reports existing formatting drift in the touched Java files, so whole-file formatting churn was not kept.

## Task 2: Stop returning raw media composition exceptions

**Files:**
- Modify: `backend/src/main/java/com/pythontopia/media/StoryboardCompositionService.java`
- Test: `backend/src/test/java/com/pythontopia/media/StoryboardCompositionServiceTest.java`

**Steps:** Add a test using a temporary failing ffmpeg stub that emits a sentinel path; verify RED exposes the sentinel; return a stable generic `VIDEO_COMPOSE_FAILED` message while retaining the server log; verify GREEN, TTS stays disabled, and run the focused test.

**Status:** Complete in `f2176675`; the sentinel test and media service tests pass. The focused Spotless limitation above also applies.

## Task 3: Lock existing public-edge request limits with a regression check

**Files:**
- Modify: `deploy/scripts/test_https_forwarded_proto_contract.sh` or create a focused shell contract test under `deploy/scripts/`
- Check: `deploy/nginx/pythontopia.conf`

**Steps:** Add assertions for registration/login and classroom-join `limit_req_zone`/locations; run the script before and after the minimum test-only change; document that enforcement is at the public Nginx boundary and production edge behavior still needs deployment verification. Do not add a DB rate-limit table.

**Status:** Complete in `f2176675`; all nine HTTPS/Nginx contract assertions pass. Public-edge runtime behavior remains unverified.

## Task 4: Align Tutor event orchestration and run lifecycle

**Files:**
- Check: `services/pythontopia-graph/pythontopia_graph/llm/provider.py`
- Check: `services/pythontopia-graph/pythontopia_graph/graph/autogen_tutor_runtime.py`
- Check: `services/pythontopia-graph/pythontopia_graph/routes/tutor.py`
- Check: `services/pythontopia-graph/pythontopia_graph/routes/tutor_workflow.py`
- Test: existing Tutor AutoGen, checkpoint, attachment, and provider tests

**Steps:** Verify supported model host/name allowlists, that image input reaches DeepSeek but response schema remains text-only, and that voice is disabled. Match event-specific AutoGen stages, then compare cancellation, checkpoint restore, reconnect/replay, and private image transport while preserving Pythontopia ownership and API contracts. Run focused Python and Java tests. Do not migrate Alethicode's V124 table or copy its controller wholesale.

**Status: Partial, behavior adapted to existing contracts.** Static model/host allowlists, DeepSeek image-input/text-only response filtering, and disabled voice output remain confirmed. AutoGen selects stages per event; Graph Tutor workflow/runtime/attachment tests pass (49), Java service/checkpoint tests pass (37), and Ruff passes. Current tests also confirm workflow stage restore/disconnect cancellation (3), the general Tutor image is omitted from AutoGen state (1), and Java SSE relays close on downstream disconnect. Same-request recovery is retained in sessionStorage for 25 minutes; a fresh-page session snapshot restores persisted cards. General Tutor images are bounded and are not persisted; the workflow path uses owner/session-bound attachment IDs. A fresh Chromium product flow after this stage change passed: registration/login, problem load, active session, READING card, and AGENT_FEEDBACK text card all returned successfully; the two event streams and two DeepSeek completions returned HTTP 200, with no page errors and the disposable user deleted. The earlier first attempt used a stale Java process and is not counted as a pass. No separate cancel API, event bus, or image table is added because current user flows already cancel/recover and keep image bytes out of persisted state. Image Web relay stability remains unverified.

## Task 5: Verify the existing expedition and twin loop in the browser

**Files:**
- Check: `frontend/src/pages/ExpeditionPage.vue`, Phaser Boss scene, `frontend/src/pages/ProfileRoomPage.vue`
- Check: `backend/src/main/java/com/pythontopia/world/boss/ExpeditionRunService.java`, `BossService.java`, `LearnerTwinService.java`, `AdaptivePracticePolicy.java`
- Test: existing boss scene, expedition run, Twin page/service tests

**Steps:** Exercise map entry, challenge answer, server settlement, pass/fail visual result, reload/resume, and one Twin recommendation in local Web stack; inspect API/browser state. Reuse reviewed/golden tasks where available. If a KC has no programming problem, allow its normal student-facing AI generation path; only retain it in the shared bank after the existing quality gate and automatic approval pass. Do not bypass expedition prerequisites or invent mastery. Record the final Boss journey as pending if no valid accessible multi-phase task exists.

**Status:** Prior Web evidence records a real three-phase Boss victory, server settlement, and Phaser hit sequence. Twin home/profile routes now share one tested destination rule. An undecidable/stale expedition problem now has a tested replacement-question path that preserves health and settlement state. Current focused rerun passed 76 tests across Twin navigation, HUD drag, and expedition page/store, plus typecheck, touched-file ESLint, and `git diff --check`. A fresh read-only development DB aggregate previously showed 10 approved review rows across chapters 1–2; this continuation added one KC-005 `APPROVED/AUTOMATED` row from the normal student-facing AI practice path (`totalTestCases=3`), bringing that inventory to 11. Its kind is `NULL`; all rows remain ordinary/unsupported for battle, and there is no `dual_track`/`closed_loop` inventory in chapter 3+. The on-demand Graph generation → quality review → automatic bank approval path exists and has prior individual KC generation evidence. This coverage does not satisfy the separate legal-run test. The remaining browser gap is a real later-chapter learner with a live server run and at least one battle-compatible question; do not create synthetic mastery to bypass it. The latest read-only aggregate confirms one account meets the KC-003 prerequisite threshold but has no active run and is not identified as disposable QA, so it was left untouched. Local `/readyz` remains 503 from the known optional video transcript dependency, while Vite, backend health, Graph, RAG, Postgres, and Piston are available after local startup recovery.

10/16 320px Chromium entry-page check fixed a vertically compressed focus-chapter label and confirmed the existing save-and-exit route remains available. The legal later-chapter map/battle flow still needs a real qualifying learner and compatible question.

## Task 6: Audit Web interaction and admin table behavior

**Files:**
- Check: `frontend/src/pages/admin/`, `frontend/src/components/admin/`, major student/teacher Web routes, shared loading/error/empty-state components
- Test: existing Vitest and Playwright suites

**Steps:** Build a route/action checklist from current routes; use Playwright to click visible actions and verify navigation, success/error/loading state, focus and narrow-screen overflow. Fix only confirmed dead actions/layout defects using existing components. Do not add pages without a real backend contract.

**Status:** Partial: fresh Twin → mainline navigation, empty-state Twin weekly plan → workshop, evidence-backed KC remediation → workshop, teacher classroom creation/dashboard entry, and 320 px overflow checks passed. The remediation test used a genuine KC-016 server-judged pass to create the weekly evidence, then deleted the temporary student and its data. Both Twin actions prefetched drafts without Tutor/Graph/resource-generation calls or page errors. The teacher browser check registered a disposable teacher (HTTP 201), logged in (HTTP 200), verified the empty state, created a classroom and entered its dashboard with no page errors; separate DB queries confirmed the teacher and classroom were cleaned up. The shared draggable hook now lets ARIA button clicks pass without starting a drag. Admin component/page suites passed (8 files / 36 tests), admin user API and router suites passed (2 files / 22 tests), and current full frontend typecheck, lint, and production build passed. In this continuation, nine protected routes redirected an anonymous browser to `/login`; a student was redirected away from four admin/teacher routes. A temporary local admin then searched a temporary user, disabled it (login returned 401), re-enabled it (login returned 200), and both accounts were deleted. At 320 px the admin page document stayed 320 px wide with no page errors. W3C table and pagination semantics are present. Shared table headers are now centered, the radius uses the existing 16px panel token, and pagination is centered while retaining manual page jump; the five affected table/user/review specs pass (26 tests). Five known operations keys now render as readable Chinese recommendations, with raw values retained in tooltips; `AdminOperationsPage` passes 5 tests. Fresh `npm run typecheck` and file-scoped ESLint pass. Fresh real-browser action checks passed HUD/pause-menu → workshop (1/1), keyboard focus/Enter → mainline chapter (1/1), and guide TOC anchor navigation (1/1); each used one disposable student, deleted after use, with no AI/model request or judging. The latest Chromium run verified student join-by-code (HTTP 200) and teacher member listing/removal (HTTP 204), with zero page errors and zero residual test rows. A separate disposable classroom task was published and archived (both HTTP 2xx); after the store refreshed from the server, the UI showed `ARCHIVED`. The follow-up DB query failed because its joined `status` column was unqualified, and the script stopped before reporting its page-error counter; cleanup still verified zero residual user/classroom/quest rows. The current continuation also verified teacher join-code reset: the old code was rejected (404), the new code joined successfully (200), and cleanup left zero temporary user/classroom/member rows. Remaining work: major route/action matrix and narrow-screen checks outside previously tested pages. Admin review search/rejection has earlier real-browser evidence; the current queue has no pending item, so a new decision-write check is deferred until there is a safe disposable record.

10/16 320px `/expedition` start-page view now has a readable stacked chapter filter, zero horizontal overflow, and a visible save/exit action; `/mainline-map` chapter drawer also rendered at 320px without page overflow. These checks do not cover active expedition map/challenge pages or the complete route/action matrix.

**Engineering scan:** The current Web `src/` tree has no explicit `any`, `as any`, or empty named `void` functions in the scanned TypeScript/Vue source. Empty arrow callbacks found were two fullscreen error catches and intentionally pending test promises. No broad rename or formatting pass was justified by the findings.

## Task 7: Run final affected-surface verification and report measured limits

**Commands:**
- `cd backend && mvn -q -Dtest=SecurityConfigCorsTest,StoryboardCompositionServiceTest test`
- `cd services/pythontopia-graph && .venv/bin/python -m pytest -q <focused tutor/boss tests>`
- `cd frontend && npm run test -- <affected specs> --coverage.enabled=false && npm run typecheck && npm run lint && npm run build`
- `git diff --check` and `deploy/scripts/test_https_forwarded_proto_contract.sh`

Run performance sampling last. Separate deterministic API timing from Graph/DeepSeek and formal judging; report request counts and clean up disposable rows. Never describe prior or synthetic data as current performance evidence.

**Status:** Affected-surface checks pass: frontend Twin/Boss recovery, admin components/pages/API/router, typecheck, lint, and production build; backend CORS/media focused tests (8), Graph Boss suite (257), Graph full suite (1871 passed, 1 skipped), RAG full suite (158 passed, 2 skipped), Nginx contract checks (9), and `TutorDailyRequestLimitServiceTest` pass. The current continuation added a combined five-file frontend regression run (76 tests). Surefire XML confirms the recorded serial Maven run had 0 assertion failures and 7 environment errors: six Testcontainers containers could not bind random Docker host ports (`address already in use`) and the long-term simulation lost its PostgreSQL connection. No full Maven rerun was made during this continuation. A fresh single Web text flow now passed after aligning local Graph/Java internal-token runtime configuration; it reached both intent routing and Tutor SSE with HTTP 200 and returned non-empty text. A separate KC-004 Web question request passed after sandbox alignment; this used one UI request and six DeepSeek completions because the first five candidates failed output self-tests. Performance remains intentionally unmeasured until the remaining functional/runtime checks close.

10/16 latest affected checks: `BossServiceTest#missingProblemPublishesNothing` passed after its sentinel exposed the old identifiers; Graph suite is current at 1924 passed/1 skipped; remote User contexts remain structurally covered. Frontend `EndlessExpeditionPage.spec.ts` 5/5, typecheck, touched-file ESLint, and a 320px Chromium check passed. No DeepSeek request was made in this continuation; performance remains unmeasured.

## Task 8: Protect classroom join codes per authenticated account

**Status:** Complete in `36bb7abe`. Existing Nginx source-IP rate limiting remains; backend additionally limits each signed-in account to 30 code attempts per 10 minutes before database lookup. RED/GREEN service tests, HTTPS proxy contract, and Nginx dual-protocol checks passed. `JoinCodeAttemptThrottleTest` and `ClassroomServiceTest` passed. Process-local state assumes the documented single-worker deployment; ECS runtime was not inspected.

## Task 9: Redact unexpected Tutor workflow errors

**Status:** Complete in `36bb7abe`. Unexpected exceptions are logged server-side with stack traces and returned to the browser as a fixed `TUTOR_WORKFLOW_FAILED` envelope. The regression test reproduced credential-like text in the old SSE response before the fix. Tutor workflow, AutoGen, attachment, coach graph, task events, ask stream, and image-input suites passed (93); Ruff passed.

## Current environment note

The configured SSH destination resolves through the TUN default route. The repository's WSL instructions require adding a direct host route, but this session cannot obtain the local sudo password, so no ECS SSH connection was attempted. Production Piston and Nginx runtime state remains unverified. Static production configuration requires the Piston URL/version/token and restricts the URL to HTTPS or a private address; the Piston API does not validate its Bearer token, so the real network ACL or an auth-checking proxy must be confirmed before production sign-off. Repository configuration was not changed.

## 2026-10-16 当前续接状态

- 已完成：本续接为 Graph Boss 生成题增加判题边界（最多 10 个用例、每项输入/输出和参考代码最多 8192 字符），结构与编排测试 50/50、Ruff 通过，超限用例未触发 Piston；修复 `/api/events` 限流绕过，服务及两个入口测试 17/17；玩家位置、主线答案分别增加边界（2/2、2/2）；真实完成并清理第一章首节浏览器学习闭环。新增 Bug 矿洞新会话账号预算（定向 7/7、Spring/PostgreSQL 2/2）、Tutor 偏好事件预算（3/3）、Loop 检查点 `maxSteps` 边界（PostgreSQL 新边界与既有产物回归各 1/1），并给 Loop Runtime 浏览器写入口加 100 单位/分预算与 1 MiB JSON 字段上限（控制器 13/13）；Quest `page_read`、`resource` 与 `kc_mastery` 均改为核对服务端证据（`QuestCompleteWithEvidenceTest` 12/12，本地 PostgreSQL 哨兵查询通过）；世界睡眠推进复用每账号写预算（`WorldAdvanceControllerTest` 2/2）。本续接未发起 DeepSeek 请求。
- 本轮新完成：证据快照与学习探针写入口复用每账号每分钟 100 单位预算；定向回归 5/5、定向 Spotless、diff 检查通过；没有 DeepSeek 请求。
- 本轮续接：远征开局与 typed action 写入口各纳入共享账号额度 1 单位；超额请求在写服务前返回 429。`ExpeditionRunControllerTest` 先 RED 后 GREEN，13/13 通过；章节测验重复提交已有状态幂等，普通代码提交有 10 秒账号限频；`./dev.sh` 已关闭本地 AI 日请求额度。本轮没有模型请求。
- 本轮静态路由筛查：班级 join 已有账号级加入码节流、下载计数已有公网 Nginx IP 节流、Tutor 内部 checkpoint 校验服务令牌和用户归属、TutorBench 仅学生可触发且为确定性评估；媒体 provider 关闭/作业授权已有先前验证。`PATCH /api/world/player` 是高频本人位置存档写入，Web 客户端合并为最多约每 500ms 一次；不套用共享 100 次/分额度以免正常移动受阻，保留为需按吞吐选择专用服务器节流的风险项。此轮只读检查，未重放旧路线。
- 未完成：全 API 非 AI 限流审计与其余本地安全类别（高频玩家位置写入的服务端专用节流仍待按吞吐确定）；远征合格真实账号的恢复/战斗闭环；未覆盖的 Web 路由与动作矩阵；Twin A/B 与 7 日复测；工程收尾，性能按计划最后测量。Loop Runtime 历史保留策略仍待审。生产 Piston ACL/ECS 与线上 Nginx 运行态未核验；全量 Spotless 仍受仓库已有格式漂移阻断，backend 全量 Maven 未验证全绿。
- 下一步：继续未审非 AI 写接口与 Web 动作矩阵；已经完成的首节闭环及 QA 数据清理不重复执行。远征完整浏览器验收须使用真实先修进度与可用题型；性能最后测；除非 DeepSeek 请求路径或配置实质变更，不重复模型调用验证。
