# Pythontopia 执行日志

> 本文档实时记录每个 working session 的状态，作为跨会话的"工作记忆"。
> 一定要常翻 `AGENTS.md` 与 `/home/cypress/Pythontopia/plan/` 下的阶段计划，避免方向偏离。
> 本文件下方的用户立场、Provider 决策和阶段列表是 2026-05 的历史记录，已由当前 `AGENTS.md` 与后续 CHANGELOG 更新覆盖。当前任务以 Web 为主，AI provider 为 DeepSeek；讯飞 Spark 与媒体出口已关闭。不要据旧日志启动非 Web 客户端或把旧阶段当作待办。
## 2026-10-16 当前续接：概念 KC 远征优先使用同 KC 客观题

- 浏览器探索中，KC-001 正常使用判断题；提交后 KC-002 在有同 KC 客观题时却收到了未作答代码预生成题。根因是取题服务在客观题兜底前先返回活动/预生成题。
- 新增 RED 用例证明无课程编程题且有审核客观题时，旧实现会返回 CODE。现在优先选同 KC 客观题，仍保留已签发客观题的刷新恢复；章一前沿只放行有同 KC 客观题的概念点。服务与控制器回归共 83/83，定向 Spotless 通过。
- 最新构建下使用普通学生 Web 注册，创建 10 层服务端远征；KC-001 判断题刷新恢复并以 1/1 通过。Twin 对该 KC 写入 PRACTICE_PASS、掌握度 0.14，真实 /profile-room 页面正常加载；浏览器错误与 5xx 均为 0。账号及级联数据已删除并核对为 0。
- 这轮没有重跑完整三阶段 Boss 通关；之前 KC-008 三阶段完整闭环仍有记录。没有调用 DeepSeek 或其它模型。KC-018/023/039/063 AI 候选入库、Twin A/B/7 日效果、生产运行态审计及性能仍留在唯一工作清单。


## 2026-10-16 当前续接：限制教师班级任务扇出写入

- 教师发布/归档班级任务会批量创建或撤回学生任务副本；现复用教师账号每分钟 100 次共享写预算，各操作占 1 次，并在批量写入前返回 429。角色与班级归属校验仍先执行；发布输入校验完成后才消耗额度。
- 两条路由的回归先 RED 后 GREEN，`ClassroomQuestServiceTest` 10/10 通过。没有模型请求、全栈重测或真实班级数据写入。
- 班级资料改名和加入码重置仍由教师角色及班级归属守卫保护，属于单行写，本次未增加预算。额度为单后端实例内存态；其余非 AI API 审计继续按唯一计划推进。

## 2026-10-16 当前续接：限制资源工坊学习写入

- 资源消费、微测和反馈接口现复用每账号每分钟 100 次共享写额度；微测持久化的 `operationEvidence` 另限 4096 UTF-8 字节，超限在读取资源、数据库写入和学习事件发布前返回 413。
- 超额路由哨兵先 RED 后 GREEN；资源工坊控制器与服务回归合计 39/39。没有 DeepSeek 请求、真实学习数据写入或全栈重测。
- 账号额度是单后端实例内存态；JSON 字节上限在 Spring 解析请求后执行，当前只保护资源检查持久化边界，不代表 HTTP 解析内存上限。剩余非 AI 写接口/日志审计、远征真实闭环、Web 动作矩阵、Twin 效果评估和工程整理继续按唯一计划推进，性能最后测。

## 2026-10-16 当前续接：限制证据快照写入突发

- `POST /api/learner/evidence-memory/snapshots` 会构建证据图、写历史行并重新组装响应；代码审计确认当前 Vue 页面没有调用方。现仅对这个 method/path 的来源 IP 设置每秒 1 次、突发 2；限流 key 对其它 `/api/` 请求为空，不影响普通 API 与 SSE。
- HTTPS/Nginx 契约先 RED 后 GREEN，23 项通过；完整公网 Nginx 配置通过 1.27 容器 `nginx -t`，脚本语法检查通过。未访问生产、未请求 DeepSeek/其它 provider，也未写业务数据。
- 这只补上一个有界路由的 IP 突发保护；全 API 按账号频率审计仍开放。

## 2026-10-16 当前续接：关闭媒体 provider 时提前失败

- 安全审计发现旧同步故事板接口会先探测 `ffmpeg`，随后才由恒关闭的图片 provider 拒绝；异步入口已有提前拒绝。本轮在共享合成服务入口加同一关闭检查，确保关闭状态下不启动外部进程、创建媒体文件或发起 provider 请求。
- 回归先 RED（旧路径未返回预期禁用错误），修复后 `StoryboardCompositionServiceTest` 7/7；真实 provider 配置仍固定关闭。未调用 DeepSeek、讯飞或写入业务数据。
- `frontend/src/api/media.ts` 导出的旧同步调用当前没有 Vue 页面调用方；异步路径仍由 `MediaJobService` 在落库前拒绝。全 API 按账号限频和完整日志扫描继续保持开放。

## 2026-10-16 当前续接：管理台校正与学习数据写入边界

- 按 Alethicode 实际 CSS 将管理台工具栏间距改为 12px、移除内容区 1440px 上限、窄屏内边距改为 16px；没有复制对方管理业务接口。管理页 Vitest 4 个文件 / 12 项、typecheck、定向 ESLint 与 `git diff --check` 通过。
- 学习事件批次现在拒绝 JSON `null`，每条 payload 的序列化 UTF-8 JSON 不得超过 4096 字节；超限批次在任何事件写入前整体拒绝。回归先 RED 后 GREEN，`LearningEventControllerTest` 5/5。
- 证据记忆快照写入按学习者行锁串行，并在每次新写后只保留最新 200 条；该表数据可从原始证据图重算。PostgreSQL `EvidenceMemoryServiceTest` 4/4，201 条写入后仅保留 200 条且最新记录存在。
- 本轮定向 Java Spotless、前端 typecheck/ESLint、相关 Vitest 与 `git diff --check` 通过。未发起 DeepSeek 请求、未更改业务用户学习数据或生产服务。
- 公共匿名方法规则单独新鲜复验：`PublicReadAccessTest` 1/1 通过，覆盖匿名读取/HEAD、登录与下载计数写入，以及 logout 和其它写方法必须认证；该 MockMvc 合同不代替全 API 限流审计。
- 下一步仍是全局非 AI API 频率审计、真实合格远征 run/刷新恢复、剩余 Web 动作、Twin A/B 与 7 日结果；工程收尾后再测性能。Maven 全量历史 7 个环境错误未重跑。

## 2026-10-16 当前续接：补齐公开认证 refresh 限流

- 审计公开认证入口时发现 `/api/auth/refresh` 没有命中 login/register 共用的 Nginx 来源 IP 限速；现将其纳入同一路由（5 次/秒、突发 10 次）。
- HTTPS 转发契约先 RED 后 GREEN，19 项通过；完整部署 Nginx 配置在 1.27 容器 `nginx -t` 通过（临时测试证书），Shell 语法与 `git diff --check` 通过。未验证生产 Nginx 运行态，没有 DeepSeek 请求。通用非 AI API 限流审计仍未完成；没有加全局 IP 限速。
- 权限复核只找到 3 个 `/api/internal/**` controller，均在方法内常量时间校验共享服务令牌；Tutor 附件与 checkpoint 端点也校验 owner subject。未发现新增匿名入口，无代码改动。
- 补跑 `AgentStrategyControllerSecurityTest`、`TutorWorkflowInternalAttachmentControllerTest`、`TutorWorkflowInternalCheckpointControllerTest`，共 7/7 通过；未运行全量 Maven。
- 当前唯一清单见 `docs/plans/2026-10-15-web-system-implementation-plan.md`。

## 2026-10-16 当前续接：错题矿洞窄屏触屏验收

- 真实 Chromium 在 320×780 下点击错题本切换按钮两次，`aria-pressed` 状态正确、页面无横向溢出；错题本及遗忘回声 API 均 HTTP 200，页面错误 0、模型请求 0。
- 临时学生 ID 726 已按 ID 和 `qa_bugmine_` 前缀删除，数据库残留计数 0；验收后已停止 Vite。

## 2026-10-16 当前续接：异常对象日志参数静态扫描

- Java 异常对象日志候选仅在硬关闭的旧讯飞 ASR/TTS/图片理解代码及课堂 SSE 初始发送 `IOException`；未看到把学生查询、代码、图片或消息直接作为日志参数。Graph/RAG 未发现 `logger.exception()`。
- 本项是窄范围静态扫描，未关闭多行/包装器日志总审计；没有代码改动或模型请求。

## 2026-10-16 当前续接：修正班级成员表字段展示

- Alethicode 对照确认 Pythontopia 的 `studentId` 是 `users.id`，并非学号；默认 API 只返回 ACTIVE，状态列中的“已移除”分支不可达。页面改为“用户编号”并移除该状态列。
- 组件回归先 RED 后 GREEN（1/1），`npm run typecheck` 与触及文件 ESLint 通过。未发起模型请求、未更改 API/数据库或学习数据。
- 本地完成这一项；其余优先级与未完成内容只看 `docs/plans/2026-10-15-web-system-implementation-plan.md`。

## 2026-10-16 当前续接：核对模型请求预算覆盖

- 追踪 Java Graph/生成调用链：Controller 使用 `@AiBudgeted` 或 service 在请求前 `reserve()`；反思与周评后台任务也先预留日额度。Boss 评测走沙箱，当前远征前沿选择走确定性 Learner Twin 策略。遗留讯飞媒资 API 当前关闭并失败关闭。
- 未发现绕过 DeepSeek 账号/全站日额度的可达模型调用路径。仅完成模型请求预算盘点；不代表所有非 AI API 都有通用限流，也未做压力测试或供应商请求。

## 2026-10-16 当前续接：收窄公共 API 匿名权限

- Spring Security 原按路径公开整个 `/api/auth/**`、课程、媒资资产与下载统计；测试夹具复现匿名 logout、课程写入和未列下载写入均可通过。
- 现将匿名权限限定到登录/注册/刷新、`POST /api/downloads/*/hit`、课程/媒资/下载 GET/HEAD 与健康检查 GET；其它方法走认证。内部 Graph API 仍由各 controller 常量时间校验服务令牌，媒资 URL 仍由 controller 校验签名或 owner。
- RED/GREEN 后 `PublicReadAccessTest`、`SecurityConfigCorsTest`、`MediaAssetControllerAuthorizationTest`、`AuthControllerTest` 共 15/15。仅本地定向测试，无模型请求、业务数据或生产配置变更。

## 2026-10-16 当前续接：脱敏判题触发的任务完成日志

- 新增判题通过后任务自动完成的哨兵回归，先复现旧日志中的 quest ID、submission ID 与异常正文。
- 日志现只保留 `BusinessException` 错误码。`SubmissionServiceTest` 6/6 通过；没有模型请求或业务数据写入。
- 与下方章测预生成、Orchestrator 日志续审合计 3 个测试类 11/11。本地安全日志审计仍未结束。

## 2026-10-16 当前续接：修复两个 fail-soft 异常日志泄露

- `ChapterTestPregenService` 与 `OrchestratorRunRecorder` 原先把异常对象写入日志；新增哨兵分别复现异常正文/堆栈泄露，现只记录异常类型，既有失败兜底行为不变。
- `ChapterTestPregenServiceTest`、`OrchestratorRunRecorderTest` 合计 5/5 通过；两个测试都断言日志无异常正文且无 throwable stack。
- 本次没有 DeepSeek 调用、业务数据写入或生产访问。总任务清单仍以 `docs/plans/2026-10-15-web-system-implementation-plan.md` 为准：安全续审、远征真实闭环、Web 路由动作矩阵、Twin 效果评估待继续；性能最后。

## 2026-10-16 当前续接：修复远征先修跨章节出题与结算

- 聚焦第 2 章或更高章节时，服务端策略可能选中更早章节的未掌握先修 KC；远征请求与结算此前又强制题目章节等于 run 聚焦章节，导致合法的先修题无法进入战斗或提交结算。
- 校验现依赖服务端 run 节点绑定的 KC、题目身份/指纹与判题证据，不要求先修 KC 与 focus chapter 相同。新增服务层与 PostgreSQL 集成回归覆盖跨章节出题和结算；与远征控制器、Boss、预生成测试合计 126/126 通过。65 KC 七章先修策略回归及 Twin/Boss 测试 63/63 通过。
- 真实远征闭环仍待有合格真实先修状态的账号、battle-compatible 题型和刷新恢复验证；本轮不调用 DeepSeek，不改写学习者数据。

## 2026-10-16 当前续接：修复 Twin 对第 1 章练习的错误导航

- 静态调用链发现 Twin 将已评估的第 1 章 KC 继续推荐到 `/expedition`，但远征开局只接受第 2 章以上的 KC；Review Hall 和学习分身共用同一导航 helper，因此两处都会遇到空前沿/错误目的地。
- 新回归先 RED 复现路由为 `/expedition?focus=1`，现将第 1 章练习引回主线地图，学习分身按钮文案同步为“前往主线巩固”。`useNextSteps` 11/11、`ProfileRoomPage` 6/6、typecheck 与触及文件 ESLint 通过。无模型请求、无业务数据写入。
- 远征状态伪造、节点绑定、Boss 三阶段、超时和队列取消已有后端/PostgreSQL/预生成测试覆盖（本轮已跑的相关合并套件 126/126）；真实合格账号的浏览器地图/战斗、缺题地图预检仍未验收。

## 2026-10-16 当前续接：由正常主线入口补齐 KC-003 编程练习

- 只读课程目录核对显示 KC-003 没有 course problem 或主线编程题。临时学生从 `/mainline/stage/1` 的“生成 AI 编程练习”按钮提交一次请求，HTTP 200；返回 KC 匹配、`aiGenerated=true`、`reviewed=true`，学生工作区可见，页面错误 0。
- 题面要求用 `print` 按行输出计算思维求解的五个步骤，与 KC-003“计算思维的方法与特征”相符；审核库聚合显示 1 条 `APPROVED`、`kind=NULL`，可由共享库提供给后续学生。第二次页面加载复用该批准题以检查公开题面，没有重复生成。此题是普通主线题，不算远征的 `dual_track`/`closed_loop` 覆盖。
- 未提交答案，没有掌握度/学习事件。因审核记录以 requester 外键保留，临时账号已停用并撤销 JWT 代次；该账号的预生成题行清零，审核题和审计来源保留。DeepSeek 内部 completion 数没有从这次学生请求单独计数。

## 2026-10-16 当前续接：NPC talk 请求边界与任务去重

- NPC 静态调用链确认节点、完成列表和 `SET_FLAG` 只写当前用户自己的对话存档，未发现任务、奖励或权限逻辑消费；Web 与 Android 使用不同对话树，不能只按 Web 数据硬编码服务端节点表。
- `NpcTalkRequest` 限制 node IDs 为 128 字符、effects 最多 32 项；每个 effect 的 type/key/value 上限为 64/128/1024 字符。三个越界控制器回归先 RED（原实现均返回 200），修复后 `NpcDialogControllerTest` 6/6 通过；触及 Java 文件定向 Spotless 与 diff 检查通过。无模型调用。
- AutoGen 对照确认普通 `/api/tutor/ask` 与六阶段导学都已使用 AutoGen；两者保留 Pythontopia 既有 SSE、附件和 checkpoint 合同，不移植不兼容的 Alethicode 异步 run API。导学面板现有请求期间显式“停止”按钮，abort 后可按原 requestId 恢复，不弹无响应错误；TutorWorkflowPanel 回归 8/8、typecheck、定向 ESLint 通过。管理台基础 token 与表格规格已对齐；页面编排差异保持在各自业务合同内。DeepSeek provider 审计没有发现可达的非 DeepSeek 模型请求或语音请求。

## 2026-10-16 当前续接：媒体合成队列拒绝后收敛为失败状态

- 有界 `mediaComposeExecutor` 满载时，`MediaJobService.submit()` 原先直接向上抛 `RejectedExecutionException`，新建的 `media_job` 因而保持 `PENDING`。新增哨兵回归先 RED，复现原异常。
- 现在队列拒绝会把该作业写为 `FAILED/VIDEO_COMPOSE_QUEUE_FULL`，并返回统一 503 错误；日志只记录作业 ID 与固定原因，不暴露请求内容。`MediaJobServiceTest` 11/11 通过，`git diff --check` 通过；无模型调用或业务数据写入。
- 静态核对同一媒体资源链：作业轮询/SSE 校验 owner，匿名媒资链接校验带 TTL 的 HMAC，登录 owner 可直接取流；无 owner 系统资产仍按设计公开。讯飞旧媒体 provider 当前 `isEnabled()` 固定 false，存在的 provider 异常原文日志不可由现行产品路径触发；若重新启用应先脱敏。本项未触发外部媒体服务。
- 上一项上传与异步 executor 联合回归也已新鲜复跑 42/42。仍不重复 DeepSeek 测试。

## 2026-10-16 当前续接：接入 BROWSE AI 预算并清理过期记录

- BROWSE 创建接口现在复用 `@AiBudgeted`，加入已有的原子账号/全站 AI 每日预算：生产配置启用（每账号 30 次、全站 300 次，按上海日期重置）；`./dev.sh` 沿用本地无限制设置。没有新增计数表或第二套配额。
- 新增每日 03:20（上海时间）删除 90 天以前的 `browse_run`；现有外键级联删除对应 `browse_step`。PostgreSQL Testcontainers 回归实际删除 91 天前的 PENDING run 与 step，同时保留 89 天内 run；BROWSE controller、服务、共享预算与拦截器定向测试合计 32/32。RED 先复现接口未预留额度，GREEN 后通过。
- 本地开发库当前 BROWSE run/step 均为 0 条；生产数据未访问。`./dev.sh` 进程环境为 `PYT_AI_REQUEST_LIMITS_ENABLED=false`，生产 Compose 固定为 `true`。本轮无 DeepSeek 或其他模型请求。
- 为加载刚完成的预算接线，已按 `./dev.sh --down` / `./dev.sh --sandbox` 重启本地全栈，PostgreSQL 数据卷保留；Vite、Spring `/healthz`、Graph、RAG 均 HTTP 200，Spring 监听仍为回环地址，运行中 JVM 环境确认限额开关为 `false`。重启后 run/step 与讲座临时账号计数仍为 0。
- 上一轮讲座页浏览器脚本已结束且临时账号残留为 0，但工具没有保留可核对的浏览器通过结果；不计为验收完成，本轮不重复运行。

## 2026-10-16 当前续接：生产 Piston 只读核验受本机路由权限阻塞

- 尝试通过已配置的 ECS SSH 别名只读查看 Piston 容器网络与监听。当前 WSL 路由将目标发往 TUN；按仓库说明需要添加临时直连路由，但 `sudo -n` 因无本机管理员口令被拒绝。
- 没有修改路由或连接生产主机；生产 Piston ACL/ECS 仍未核验。后续可在直连路由已配置的环境补做。

## 2026-10-16 当前续接：限制本地开发后端监听范围

- 安全复核发现本地 `./dev.sh` 启动的 Spring API 监听 `*:18080`，虽然 Compose 暴露端口绑定回环，但这个原生开发进程会直接对局域网接口开放。
- `dev.sh` 现在导出 `SERVER_ADDRESS=127.0.0.1`。已重启开发栈并核对 `ss` 显示后端仅监听 IPv4 映射回环地址；后端、Graph、RAG、Vite health/root 请求均 HTTP 200，匿名访问内部策略接口返回 401。`bash -n dev.sh` 与 `git diff --check` 通过。
- 初次重启复用了仍存活的旧后端，随后也遇到旧 Vite 占用 5173；终止旧开发进程组后重新启动成功。当前完整开发栈在运行，数据库卷保留。没有 DeepSeek 请求。
- 本地安全审计仍未完成；生产 Piston ACL/ECS 及其他未审类别继续留在总清单。

## 2026-10-16 当前续接：图书馆翻页动作浏览器验收

- 使用一次性学生会话打开 `/library?docId=186&page=1`，真实点击“下一页”和“上一页”，页码面包屑与 URL 参数分别更新至第 2 页和第 1 页；页面错误为 0。
- 为避免触发视频资源的外部检索/摘要，浏览器内拦截 B 站搜索；未发 Tutor 或模型请求。页面自动发起的唯一写请求是会话恢复 `POST /api/auth/refresh`，没有判题或学习事件写入。
- 首次脚本在清理表名上出错，确认账号表实际为 `users` 后删除临时账号；一次关闭浏览器的脚本生命周期错误也已修正。最终清理删除 1 条用户记录，随后 `qa_library_` 残留计数为 0。没有 DeepSeek 请求。

## 2026-10-16 当前续接：匿名登录暴力尝试边界复核

- 只读检查确认公网 Nginx 对 `/api/auth/login` 与 `/api/auth/register` 按来源 IP 限制 5 次/秒、突发 10 次；`AuthService` 对已知账号以 15 分钟失败窗口累计尝试，随后施加最多 8 秒的渐进退避，成功登录清零。不存在用户名仍执行 dummy password hash，错误响应统一，降低账号枚举侧信道。
- 仓库已有 Nginx 契约断言及 `AuthServiceSecurityTest` 覆盖；本轮不重复运行既有测试，也没有修改代码。公网服务器的 Nginx 运行配置未远程核验。

## 2026-10-16 当前续接：BROWSE 并发与事务边界

- 只读安全审计确认 Graph BROWSE 的 Python semaphore 会让请求排队；Graph 默认预算 45 秒，Java 共用 Graph 客户端读超时 30 秒。`BrowseRunService.create()` 还在同步远程调用期间开启事务。
- 槽满时 Graph 现在立即返回 429，Java 将其映射为 `BROWSE_BUSY` 并保留 429。Graph 请求明确传入 45 秒，Java 使用独立 50 秒读超时。
- Graph 调用结束后才调用 `BrowseRunPersistenceService.persist()`；只有会话行和步骤行写入处于事务内，保持原子性。
- RED/GREEN 覆盖了饱和立即拒绝、HTTP 429 透传、执行超时字段和远程调用事务边界。Graph BROWSE 执行器/路由 42/42，Java `BrowseGraphClientTest`、`BrowseRunServiceTest`、`RestClientConfigTest` 共 25/25；Ruff、8 个 Java 文件定向 Spotless、`git diff --check` 通过。全仓 Spotless 仍报告 625+ 个文件格式违规；本轮未做批量格式化。无 DeepSeek 或其他模型调用。
- 未覆盖账号级总请求量、会话保留/清理策略；并发满载拒绝只能防突发排队，不能代替每账号总量配额。
- NPC 状态审计发现 talk 写接口信任客户端 `nodeId`、`nextNodeId` 与 `SET_FLAG`。确认影响仅是当前账号自己的对话存档，尚未确认奖励或权限绕过；Web 当前未调用，Android 的树与 `data/npcs` 不同，故先登记为待设计的共享后端兼容性问题，不按单一前端树加硬编码校验。
- 对“320px 仍可切像素塔”审计意见核对了当前页面：小于 900px 已隐藏切换控件，初始列表视图，resize 也切回列表；1024→320 页面回归已存在。当前代码无法经页面触发该布局冲突，没有重复加场景测试。活动地图与 Boss 战斗实机窄屏仍未验收。

## 2026-10-16 当前续接：匿名下载计数限流

- 安全复查发现 `/api/downloads/{key}/hit` 匿名累计接口没有公网限流，重复请求可污染运营展示值。前端只在用户点击时请求一次，后端只更新已 seed 的 key。
- Nginx 新增该写入路径的来源 IP 限流：5 次/秒、突发 10 次，超限返回 429；GET 计数与 `/downloads/` 文件直出不变。
- 契约先因缺 zone/location 失败；缓存的 Nginx 1.27 镜像 `nginx -t` 又发现未引号包裹的 `{1,64}` 会被解析为配置块。入口正则修正后，Nginx 解析通过，12 项 HTTPS/Nginx 契约通过；限流只匹配 hit 路径。未调用模型或写数据库。
- 开始下一项 320px 远征浏览器验收时，页面被路由守卫送到登录页；当前 shell 没有 E2E 学生凭证。没有注册账号或伪造先修证据，地图与 Boss 战斗验收仍开放；本轮无 DeepSeek 调用。

## 2026-10-16 当前续接：隔离学习反思任务

- 只读安全审计确认：提交事务同步发布学习事件，反思触发器共用 SSE `CallerRunsPolicy` 执行器；池与队列饱和时，反思会在判题线程等待 Graph。
- 为反思增加独立的单 worker、32 项有界队列；队列饱和时显式跳过。饱和回归先 RED 后 GREEN；反思与 SSE 执行器测试共 9/9。无模型请求。
- 随后为用户反思计数增加 30 分钟定时清理：至少 24 小时无活动后移除，且不早于配置的冷却周期。测试直接验证清理方法，不在每批学习事件后扫描整张 map。反思、SSE 执行器和 Spring 启动回归共 11/11；无模型请求。
- 远征视口从宽屏缩至 320px 时自动切回可读列表并隐藏像素塔切换入口。新增缩放回归先 RED 后 GREEN；页面与 Phaser 场景 13/13、typecheck、触及文件 ESLint、`git diff --check` 通过。此处未做真实 Chromium 地图/战斗画布验收。
- 安全续扫发现 `VideoProgressThrottle.windows` 的过期 user/stage 窗口和 NPC 寒暄缓存仅按需过期、不会回收；现分别按 60 秒和缓存 TTL 定时清理，并修正缓存注释与 26 小时配置不符。新增清理回归先 RED 后 GREEN；限频、主线、NPC 服务/控制器/预热任务与 Spring 启动测试共 54/54，通过。没有模型请求。
- 随后发现教师主线看板 TTL 缓存也仅在同一班级再次请求时重载，过期班级矩阵会常驻；现在每 5 分钟清理过期条目。新增回归先 RED 后 GREEN，`MainlineDashboardServiceTest` 9/9 通过。
- 收尾把反思/SSE、主线限频、NPC 和教师看板受影响测试合并复跑：9 个测试类共 73/73，含 Spring 启动 smoke；前端 2 个测试文件 13/13。Java 定向 Spotless、前端 typecheck/ESLint 与 diff 检查通过。没有 DeepSeek 请求。

## 2026-10-15 当前续接

- KC-005 主线“练”页正常 AI 补题返回 200，题面符合 KC、AI/审核标记为 true、`totalTestCases=3`；新 `APPROVED/AUTOMATED` 共享行落库，kind 为空。两账号删除，审核请求者停用保留审计，预取记录删除；未正式判题或写学习事件。模型 completion 数未单独统计。
- 记忆引用 best-effort 落库失败日志原先包含学生 ID/数据库正文；哨兵先 RED 后 GREEN，外部 provider fallback 和落库降级现只记异常类型，记忆返回语义不变。五类后端回归合跑通过，无模型请求。
- NPC 人格种子启动失败原先会记录异常正文；哨兵先 RED 后 GREEN，现只记异常类型并保留启动 fail-soft。`NpcPersonaSeederTest` 与其它三类日志回归合跑通过，无模型请求。
- 判题失败补查题目难度的数据库错误曾写入 DEBUG 异常正文；合成哨兵先 RED 后 GREEN，现只记异常类型并保留题目 ID/空难度兜底。三类后端测试合跑通过，无模型请求。
- Tutor 编排读取到期 KC 失败时原先会写完整异常栈；哨兵先 RED 后 GREEN，现只记录异常类型并保留空列表 fail-soft。`OrchestratorServiceTest` 与睡觉日志回归合跑通过。无模型请求。
- 世界睡觉的遗忘曲线降级日志曾包含学生 ID 与异常正文；哨兵测试先 RED，现只记录异常类型，`WorldAdvanceServiceTest` 1/1，睡觉返回路径仍正常，差异检查通过。无模型请求。
- XSS 续扫确认 Mermaid `classDef` 可产生 `position:fixed`，`init.themeCSS` 可注入外部 CSS URL；CSSOM 清洗已过滤布局覆盖、外部资源和 SVG 外作用域规则，同时保留安全配色。真实 Mermaid 输出回归 3/3、typecheck、定向 ESLint、diff 检查通过。远征 run 行锁/判题证据/题目指纹幂等与上传配额用户行锁复核未发现新问题；无模型调用。
- RAG KC 图数据库错误日志可能包含连接 URL/密码；合成 DSN 哨兵先 RED，现只记录异常类型，`test_kc_graph.py` 8/8。按仓库验收要求实际查询 `input 函数`、`for 循环`、`列表`，三项均 HTTP 200、有 5 条 page_id/document_id 引用；RAG `/healthz` 200，Ruff 通过，无 DeepSeek 调用。
- Postgres checkpointer 异常日志哨兵复现 DSN 密码泄露；现只记录异常类型，仍降级 MemorySaver。与记忆 brief/loop sink 合并定向测试 48/48，Ruff、差异检查通过；无模型调用。
- 编排异常日志续扫：memory brief provider 异常可把学生查询写入日志，loop record sink 异常可把存档错误正文写入日志。哨兵测试先 RED，改为只记异常类型后 `test_agentic_loop.py` 42/42，Ruff 与差异检查通过；fail-soft 行为不变，无模型请求。
- BROWSE 异常信息核查：底层浏览器异常可能带有 URL 查询 token，异常会进入结果文本；外层 executor 异常还会记录日志并投影到 SSE。哨兵测试先 RED，现结果与日志只保留异常类型，缺少依赖时仍给固定提示；两个测试文件共 79/79，Ruff、`git diff --check` 通过，无 DeepSeek 调用。
- Boss 题目存档日志审计：损坏 payload 的 Jackson 异常正文可能带出题目隐藏测试/参考解，外层读取失败还记录 owner/problem ID。回归先 RED，改为仅记录异常类型后 `EndlessPregenServiceTest` 全部 17/17 通过；`git diff --check` 通过，无数据库写入或模型调用。
- 对照当前 Alethicode `Home.vue`/`Panel.vue`/`style.less`/`common.less` 与 Pythontopia 管理台：240px 分组侧栏、蓝灰面板 token、16px 面板圆角、36px 控件、44px 表头、52px 行、窄屏导航和可滚动列表已在三个真实管理页面落地。沿用既有组件/320px 浏览器与管理台定向测试记录；不复制 Alethicode 未映射到 Pythontopia 数据模型的管理菜单或 API，也不改现有 DTO 合同。管理台“格式对齐”完成，剩余动作覆盖归入 Web 矩阵；无模型请求。
- 扩展本地判题隔离验证：运行既有 `node deploy/scripts/test_dev_piston_sandbox.mjs`，确认 Python 3.12.0、宿主文件隔离、job 私有临时目录、共享 `/tmp` 拒写、loopback 与 compat socket 阻断、超时 SIGKILL 均通过；脚本临时文件已清理。Docker inspect 确认 Piston API 容器非 privileged、只读 rootfs、capabilities 收紧、`no-new-privileges`、仅接入 `judge-execution`，端口只绑定回环。此项补足本地运行态隔离证据；生产 Piston ACL/ECS 与其余安全审计仍未验证，无模型请求。
- 课堂 DTO 对照覆盖基础班级、成员、任务和看板字段；修正前端 `RemediationResultDto.partial` 为必填，与后端 primitive boolean 一致。`npm run typecheck` 通过；Admin 与 Alethicode 字段对照仍待做，无模型请求。
- 安全续扫定位 NPC 定时预热任务把异常原文和活跃学生 ID 写入日志。先用异常哨兵让两条日志回归 RED，随后只保留异常类型；`NpcGreetingPrewarmJobTest` 6/6 通过。无模型请求。
- MetaReflection 调度异常日志改为记录异常类型，策略更新日志只保留 agent 与版本，不再记录反馈采用率/评分汇总；两个合成哨兵先 RED，`MetaReflectionServiceTest` 7/7 通过。无模型或数据库状态变更。
- DialogContext、MemoryRetrieval、LearningPath、WorldState 四个降级路径原先会把异常正文写入日志；合成哨兵先 RED 命中，现只记录异常类型，原有中性/空结果降级保持不变。四个测试类共 29/29 通过。
- 学习事件镜像、访谈画像同步和预防讲义失败路径的异常正文也可携带学生/资源数据；哨兵 RED/GREEN，`MemoryStreamMirrorTest`、`InterviewDimensionMirrorTest`、`PreventionPackServiceTest` 共 19/19 通过，无模型请求。
- 本地判题隔离复核发现 Piston 运行容器仍处于旧 `deploy_default` 网络，尽管 Compose 已声明专用网络；按当前文件只重建 Piston，确认运行态仅接入 `deploy_judge-execution`、宿主映射限于 `127.0.0.1:2000`。通过 Piston Python 3.12.0 HTTP 冒烟：输出 `4`、退出码 0；后端 `/readyz` 的 judge 项就绪，整体 readiness 仍因视频字幕服务不可用返回 503。生产 Piston ACL 未验证，无 DeepSeek 请求。
- 最新只读数据库计数为 21 条活动远征记录；满足候选先修门槛的账号 1 个、其中活动 run 为 0。查询只返回聚合计数，未创建、修改学习数据或读取账号身份。
- 补齐 `ProfileService` 画像访谈 SSE 日志脱敏：结果处理、流式传输、fallback 与 emitter 写入不再输出异常对象。结果持久化异常携带的合成学生文本哨兵先 RED 命中日志栈，`ProfileServiceFailureTest` 2/2 通过；原有非流式错误映射不变，无模型请求。
- 修复 `TutorWorkflowSubmissionFeedbackListener` 提交反馈与状态 JSON 解析的异常栈日志泄露。两个合成哨兵先 RED 命中，随后只记录异常类型；原有提交状态更新保持不变，`TutorWorkflowSubmissionFeedbackListenerTest` 6/6 通过，无模型请求。
- 修复 `WorkshopGraphClient` SSE 结果处理、上游读流、fallback、AG-UI payload 解析和错误事件发送路径会记录异常正文的问题。合成学生内容哨兵先 RED 出现在异常栈中，改为只记录异常类型后 `WorkshopGraphClientTest` 4/4 通过；未调用 Graph 模型或 DeepSeek。
- 复习大厅新生真实浏览器验收：空周报与服务端首个基础 KC 推荐加载成功，费曼入口导航至 `/feynman`；评估/Twin 接口返回 200，页面错误 0、模型请求 0。临时账号 700 已清理并核对为 0。首轮脚本因断言 API 不兼容和错误地假设推荐为空而失败，均未产生学习记录；按实际新生状态调整断言后完成。
- 收尾本轮 Java import 顺序：仅整理 15 个已改动源码/测试文件的 import 区，遵循静态/普通 import 分组与 ASCII 排序；`git diff --check` 通过，没有扩大成全仓格式化。
- 修复 `ReflectionService` 两类异常日志：Graph 反思和记忆读取异常原文可能包含学生输入/上游响应；两个合成哨兵先 RED 命中，再改为只记录异常类型。规则兜底与 fail-soft 行为不变；`ReflectionServiceTest` 8/8 通过，无模型请求。
- 延伸敏感日志回归：工坊学情上下文、NPC 问候、记忆 embedding、资源工坊、画像 fallback、Feynman 判分、Graph SSE、媒体任务和 Tutor relay 等 12 个定向测试类通过。另修复远征预生成失败日志会输出 Graph 错误正文；章测预生成、远征存档反序列化、远征题目预取三类共 29/29 通过。无 DeepSeek 请求。
- 修复 `MemoryBriefService` 降级日志：权威检索异常可能携带学生查询或上游响应正文。合成哨兵先 RED 命中日志，随后 4 个降级分支统一只记异常类型；`MemoryBriefServiceTest` 7/7 通过。无 DeepSeek 请求。
- 教师屋真实浏览器验收：临时教师经邀请码注册（201）并登录（200），看到真实空班级态，创建班级后进入该班看板；页面错误 0。测试期间未调用模型。临时教师和班级在本地数据库中分别查询为 0，确认级联清理完成。
- 收紧开发 Compose 判题边界：Piston 从共享默认网络移入 `judge-execution` 专用 bridge，仅 Compose backend 与 Piston 相连；保留 backend 默认网以访问 PostgreSQL、Redis 和 Graph/RAG。保留 Piston 服务出站以安装 Python runtime，提交代码仍由 `PISTON_DISABLE_NETWORKING=true` 禁网。本地安全合同先因缺少专网而 RED，配置完成后 `bash deploy/scripts/test_dev_compose_security_contract.sh` 全通过。未启动/重建容器；生产 Piston ACL 仍未验证，本轮 DeepSeek 请求 0 次。
- 修复远征地图预取遗漏共享已批准题：按章节、阶段、KC、题型和难度优先查询审核题库，命中后写入当前玩家 READY 队列；未命中继续现有 Graph 生成与审核路径。RED/GREEN：`EndlessPregenServiceTest` 15/15。没有发起 Graph 或模型调用。
- 修复 Tutor 会话落库、画像、班级、记忆、会话历史、工作流检查点和 SSE 结果投影失败路径可能包含学生数据或异常正文：日志只记录异常类型，投影异常传给 emitter 的也是固定错误文案。合成哨兵 RED/GREEN；`TutorServiceFailureTest` 2/2、`TutorMemoryBriefTest` 4/4、`TutorHistoryBriefTest` 6/6、`TutorWorkflowServiceTest` 33/33、`TutorWorkflowGraphClientTest` 5/5 通过；没有模型调用。
- Tutor/Alethicode 只读对照确认 Pythontopia 已有固定阶段 AutoGen、SSE、授权、附件和检查点链路；未发现需要迁移的代码缺口。管理台主要视觉规范已对齐，不移植 Alethicode 后端 API。
- **更正上述 Tutor 结论：** 更细的逐事件对照发现固定五阶段与 Alethicode 的事件专属阶段编排仍有差距。Tutor 现根据 event/coach 状态选择 AutoGen 阶段；状态校验阶段初始化 `effective_event`，Java checkpoint 校验支持动态索引且保留旧固定阶段恢复。Graph 49 项、Java Tutor 服务与 checkpoint controller 37 项、Ruff 及 `git diff --check` 通过；回归中发现并修复 AGENT_FEEDBACK 状态投影依赖 coach 阶段的问题。进一步确认断开会取消当前 AutoGen task 并释放 run；Java relay 关闭上游连接；同一 requestId/fingerprint 可恢复检查点，前端在 sessionStorage 暂存 25 分钟恢复请求，活动会话 API 返回已落库卡片。Graph cancellation/restore 3 项、通用 Tutor 图片脱离 AutoGen 状态 1 项、Java Graph SSE relay 定向测试通过。通用 Tutor 图片只在有界请求里临时转发，落库仅含问题/文本答案/引用；训练场工作流继续用 owner/session 私有附件 ID。独立 run API、event bus 和通用附件表不是当前闭环缺口；只待本轮新阶段排序的一次工作流端到端验证。本轮未调用 DeepSeek。
- 用新注册的空学情学生做真实浏览器验收：展开“近期变化”并点“生成下一步”，实际进入 `/workshop` 且 `composer-input` 收到 Twin 提示草稿；本周统计为 0 条事件、没有掌握增量，模型/工坊生成请求为 0、页面错误为 0。成功验收账号 690 删除并核对为 0。第一版浏览器脚本因 Playwright API 参数写法失败，临时账号 689 随即删除并核实；修正脚本后完成验收。
- 用另一名临时学生通过现有 KC-016 课程题并取得服务端 `PASSED`，周报汇总出 1 条正式练习事件与 1 个 KC 掌握变化；真实点击该 KC 的“补强”后进入工坊，草稿包含 `PY43-KC-016`。没有 Tutor/Graph/资源生成请求或页面错误；用户 691 及其判题/学情通过级联删除并核对不存在。
- 主线正常 AI 补题为 KC-001 生成并自动审核了一道普通编程题；页面返回 `aiGenerated=true`、`reviewed=true`，同账号从共享审核库复取后服务端正式判题 3/3。该账号的 KC-001 真实掌握度为 0.14，低于远征先修门槛，因此不重复提交同题凑分；QA ID 697 因题库审计外键保留。没有再做 DeepSeek provider 探针。
- 当前下一步见 `docs/plans/2026-10-15-web-system-implementation-plan.md`：补远征兼容题与合法活动 run 恢复，再完成未覆盖 Web 动作和工程收尾；性能最后测。

- 新增账号级登录失败退避：V215 加入失败计数、15 分钟窗口和 1/2/4/8 秒封顶冷却；匿名登录仍回通用错误，成功登录复位。
- TDD 回归在旧代码下因仓储方法不存在而失败；实现后 `AuthServiceSecurityTest` 与 `UserEmailRegistrationMigrationTest` 共 19 项通过，临时 PostgreSQL 空库迁移至 V215。触及 Java 文件 Spotless 定向检查、`git diff --check` 通过。
- 恢复本地全栈并通过 Vite → Spring → Graph 发出两次真实 DeepSeek Tutor SSE 请求。文本校验词复述正确；合成 PNG 的黄色背景和蓝色方形识别正确；会话各持久化一条非空文字回答，响应无图片数据。临时用户和记录已清理。本地后端 AI 每日入口限额实测 `false`；供应商调用 token/费用未核对。
- 新生远征入口修复：保留 `EXPEDITION_NO_PRACTICE_PROBLEMS` 错误码，起始页给出“前往主线地图”按钮，后端说明先完成已解锁章节的学习与测评。页面/状态 RED 2 项后 GREEN 58/58，BossServiceTest RED 后 GREEN 53/53；typecheck、定向 ESLint、`git diff --check` 通过。Playwright 注册临时新生后真实创建远征收到 422，点击入口到达主线地图，页面错误为 0，账号已删除。
- 完整前端回归初次发现两个旧测试文件与当前实现契约不符：Boss mock 缺少 `newBossProblemRequestId`，Admin shell test 仍断言 584f997b 前的 CSS 变量名。仅更新测试桩/断言，针对两文件 15/15、全量 274 files / 2591 tests 通过；全仓 ESLint、`vue-tsc` 与生产 build 通过。Vite 构建仍提示 Pyodide 依赖 externalize Node builtin 与使用 eval，未影响产物生成。
- 后端全量 `mvn test` 复跑为 1413 项、0 assertion failures、7 errors：6 个 Testcontainers 集成类因 Docker 发布随机宿主端口报 `address already in use`，长期仿真类 PostgreSQL 连接 reset。7 个受影响类合并隔离复跑为 7 项、0 failures、6 errors；长期仿真 1/1 通过，6 个容器类仍被相同端口冲突挡住。
- 对账号退避做边界复核后新增失败计数与指数封顶断言；新测试先红（20 次追加后计数为 30，预期上限 7），封顶实现后 `AuthServiceSecurityTest` + 空库迁移回归 19/19 通过，V215 保持可从空库升级。
- Graph provider 直接真实调用 DeepSeek 两次：文本请求精确返回校验串；合成 PNG 识别为黄色背景、蓝色正方形；两个响应均为 Python 字符串，无图片块和工具调用。这是 provider 级验证，不替代 Web Tutor 联测；费用/token 未核对。
- 已纠正文档状态：历史三阶段 Phaser Boss 浏览器胜利测试对有合格先修证据的账号通过；新生需要先完成真实主线学习与测评，未伪造掌握证据。
- 本次未运行性能测试。下一步完成当前工作区全栈回归和工程质量收敛，并继续核对 Tutor live 对照与学习首题入口。
- 复核 Tutor 用量入口：普通 `/api/tutor/ask` 与固定阶段 workflow 都在请求模型前调用 `reserve(userId)`，不按 conversationId 计量。部署默认 30 次/账号/日及全站 300 次/日，`./dev.sh` 明确关闭应用级限额；本轮 `mvn -q -Dtest=TutorDailyRequestLimitServiceTest test` 退出码为 0。供应商自身余额/速率限制仍独立生效。
- 用户指出重复模型探针偏离整体任务后，停止新增 DeepSeek 调用。此前本轮 provider 级两次真实调用均成功：`PYT-TEXT-641` 原样返回；合成 PNG 识别为蓝圆和黄底。上一次 Tutor Web SSE 仍超时，本轮没有重复 Web 请求。
- 管理台可访问性按 W3C WAI 数据表与 `aria-current` 指引复核；现有两张表的列标题均有 `scope="col"`，分页当前页使用 `aria-current="page"`，本项无需代码修改。
- 数字孪生选题职责复核确认：`BossService.selectFrontier` 读取服务端 `LearnerTwinService.frontier`，由 `AdaptivePracticePolicy` 排序；Boss 难度通过同一 policy 的 `recommendedDifficulty` 计算。该服务类已有规则单测与此前 46 项 Twin 定向回归，无需再造第二套策略。
- 建立总任务清单与优先顺序于 `docs/plans/2026-10-15-web-system-implementation-plan.md`；当前代码检查点 `36bb7abe`、最新文档检查点 `c492f6ae`，未推送/部署。下一步聚焦远征活动 run 恢复和 Web 路由动作矩阵，不重复验证已通过的模型 provider 能力。
- 为 Web 路由动作矩阵补无模型浏览器检查：9 个学生保护入口在匿名状态全部去 `/login`；真实新注册学生访问 `/admin/operations`、`/admin/users`、`/teacher`、`/teacher/review` 均去 `/world`。临时账号 id 640 已退出并删除，数据库确认用户行数为 0。现有公网验收登录守卫/登录页测试 2/2 通过。
- Piston 生产配置静态核对：Compose 要求配置 Piston URL、Python 版本和 token，ECS 预检仅允许 HTTPS 或契约认可的私网 URL；`bash deploy/scripts/test_piston_http_host_contract.sh` 已通过。Piston API 本身不验证 Bearer token，生产必须通过私网 ACL 或鉴权代理限制访问。ECS 运行态未核实；本机 SSH 直连路由需要不可用的 sudo 密码，未修改远端配置。
- 工坊 `quiz_finished` 事件已由客户端写入通用审计日志，但后端周报权威统计和画像监听不采信 frontend 来源。删除暗示该事件将来可直接更新画像/任务 evidence 的过时 TODO，改为说明客户端 KC/轮次数据不可作为可信证据；无运行逻辑改动。
- 查看本地课程可用题：无先修 KC-001/002/004/006 都没有静态编程题，也没有已批准 AI 编程题；所以无法用新生合法创建远征 run 验证刷新恢复，保持待办而不补造 mastery。
- 生产只读 SSH 未尝试：`ip route get` 显示目标走 TUN；按 AGENTS 需要为当前 eth2 网关加 host route，但 sudo 要求未提供的本机密码。记录阻塞后转回本地工作，未连接/修改 ECS。
- 在 `frontend/src/` 扫描显式 `any`/`as any` 与空命名 `void` 函数，未发现违规项；空箭头只用于 fullscreen Promise 错误捕获及测试待决 Promise。不为统一表面风格批量重命名或格式化。
- 继续异常日志审计：Feynman 对话/找茬、BROWSE、B 站检索、Boss 生成/判题远端异常正文曾进入日志；章测 Graph 的原始错误也会被预生成日志记录。合成标记先触发 RED，再将日志改为异常类型或稳定错误消息。定向 GREEN：FeynmanGraphClientTest 4/4、BrowseGraphClientTest 5/5、BilibiliVideoServiceTest 2/2、BossServiceTest 53/53、ChapterTestGraphClientTest 2/2。模型调用数 0。
- Web 清单审查确认 `ProfileRoomPage.spec.ts` 已有页面层覆盖 Twin 主/备推荐和周报 insight/补强到工坊；本轮不重复运行组件测试或模型路径。它们仍缺真实浏览器的 insight/补强动作验收；Web 矩阵继续按清单选择未覆盖操作。
- Chromium 真实新生打开 `/profile-room`，点击“前往主线学习”到 `/mainline-map`；推荐来自服务端 0/65 学习状态，页面错误 0，仅发生认证刷新和学习状态读取。临时用户 688 删除后核对计数为 0，没有题目、Tutor 或判题请求。
- 下一步转到有合法学习证据的远征活动 run 恢复/兼容题验收；如没有满足先修且活动 run 的可用样本，则先用正常判题路径构建，不写入伪造掌握证据。性能继续最后。

## 2026-10-14 当前续接

- Twin/远征先修数据复核发现 V125 把未提供语义关系的旧课程 seed 按编号串成线性链，导致新学生第一章只有 KC-001 可选。新增 V214 按课程包 JSON 修正 65 个 KC 关系；回归先红（独立根 KC 1 个而非 4 个）后绿。策略、Twin、Boss、地图与课程库相关回归 66 项通过；空库 Flyway 集成类 4/4 通过，开发库关系与 JSON 逐条比较 65/65 一致，隔离 Spring 启动已将开发库 Flyway 版本推进到 214。
- 门禁实施前，修正关系后的真实本地远征创建 API 曾返回 10 层、25 个挑战节点及 4 个第一章根 KC；临时 run 与学生已清理。真实 DeepSeek Boss API 对 KC-002 与 KC-012 各发起一次出题操作，均未产出通过审查的可用题并返回 503；没有将不合格题写入共享题库，临时学生与审核候选已清理。新玩家的远征可用编程题覆盖仍未解决，不能据此声称完整通关。
- 后续定位出题 503 的第二个根因：Graph 的难度审查可将候选从请求难度 1 校准到 3，但仍标记通过；Java 自动入库要求与目标难度一致，因此拒收。Graph 编排现将校准后偏离目标的候选纳入既有 6 次限额重试，并把差异反馈给下一次生成；不会放宽质量门禁。回归先红后绿，Graph Boss 子集 238 passed，Ruff 通过。
- 修复后真实 DeepSeek Web API 对 KC-012 执行 6 次候选尝试（4 次难度不符/接地拒绝、1 次回显拒绝、最终通过）；API 返回 difficulty=1、3 个测试用例、`reviewed=true` / `aiGenerated=true`，数据库写入 `APPROVED/AUTOMATED`。另一名临时学生复用同一题库记录，HTTP 200 且 Graph 没有新增生成成功记录；复用账号已清理，审核 requester 按外键保留。
- 随后确认课程题库 KC-010、KC-013 均无编程题。KC-010 的 6 个真实 DeepSeek 候选全部被 KC 接地/语法范围审查拒绝，未插入审核题；KC-013 在 4 次候选后通过 Graph/Piston 检查，开发库记录为 `APPROVED/AUTOMATED`、难度 1、3 个测试用例，审核理由包含结构、自测、KC 接地、难度与相似度检查通过。该结果验证缺题 KC 可在真实练习请求中生成并复用，但目前只实证 KC-013；拒绝候选不会进入共享题库。
- 手动恢复 :18080 时第一次误用了新生成的临时服务间令牌，与现有 Graph/RAG 容器令牌不一致，导致一次尚未到模型的 503。之后从现有容器恢复共享令牌；当前本地模型计数限制关闭，Piston 已配置，backend/Graph/RAG 服务令牌匹配，:18080/:18091/:5173 均健康。
- 为避免把没有 Python 编程目标的第一章概念题放进代码战，远征前沿现在排除第一章 KC；若无后续且满足先修的 KC，服务端返回 `EXPEDITION_NO_PRACTICE_PROBLEMS`，提示回主线小测/练习。Boss/Twin/选题/地图 63 项测试先红后绿；临时 Spring 实例与恢复后的本地 :18080 开发服务均通过真实 HTTP 验证新学生开局返回 422、未建 run，开发库确认临时账号与 run 均清理。
- 本轮一次真实 DeepSeek 视觉 provider 调用返回纯文本；同/流式解析回归 3 passed。调用为 provider 级，不是浏览器重复测试；供应商实际 token/费用仍未核对。
- DeepSeek 图片输入/文字输出边界覆盖 Graph 和 Web：同步与 SSE 响应只取文本块；Tutor 不再请求 Mermaid/SVG 图解，`visual_aids` 兼容字段固定为空；Tutor、画像/复盘总结、学习向导、工坊讲义、答疑和 AI 题目文本的 Markdown 图片均显示替代文字，可信课件图默认不变。真实 `POST /api/tutor/ask` 图片输入复验返回非空文字、识别黄色背景/蓝色方形、`visual_aids=0`，临时学生和 Tutor 记录已清理。Graph 所有 Tutor/provider 回归 203 passed / 1 条既有 asyncio 标记警告；前端全量 Vitest 271 files / 2576 tests、typecheck、触及文件 ESLint、build 通过。画像总结图片边界回归先红后绿。
- 最新补充：DeepSeek 同步与 SSE provider 都只传文本块；混合文本/图片响应的回归确认图片块不会到达客户端，不调用图片生成接口。真实 provider smoke 两次成功（文本 + 合成 PNG 识别），响应均为文本；此次 provider 的请求体和模型未变，因此未再发送模型请求。
- Boss 重复请求修复已通过真实 Web API 复验：同类 KC-001 请求不再重放 Graph 的 HTTP 生成响应。首轮六次候选均被拒并返回 503（修复前同类请求曾产生 12 次模型 HTTP 200）；加上只记录审查错误码的诊断后，第二轮五次模型 HTTP 200，返回 `reviewed=true`、`aiGenerated=false` 的同 KC Golden 题。两轮临时学生均已删除。首轮为何未通过回退、第二轮能通过仍待复现。
- 对应 Java `BossGraphClientTest`、`BossServiceTest`、`EndlessPregenServiceTest` 共 67 项通过；Graph Boss 全套 237 项、Ruff、修改 Java 文件 Spotless 检查均通过。全量 Maven 尚未稳定通过，见下方已记录的容器端口冲突与长期仿真连接错误。
- 最新验证：前端 270 files / 2568 tests、typecheck 与 build 通过；ESLint 0 errors / 7 warnings。Graph 全量 1842 passed / 1 skipped；RAG 全量 158 passed / 2 skipped。教师班级列表 320px Chromium 布局测试通过，使用模拟 API。
- 安全与交互：Graph 隐藏测试失败现在只返回通用提示，不泄露隐藏 expected/actual；Twin 周报呈现最近失败与百分点单位；首页班级加入表单在班级列表 API 失败时仍可用；教师班级页签支持 ARIA tabs 键盘操作。
- 远征：旧 `/endless` 预生成等待已有请求并轮询 READY，防止生成竞态；结算期间阻止重复提交；显示服务端题目难度。随机事件节点集成测试 fixture 已修，`ExpeditionRunPostgresIntegrationTest` 隔离 28/28 通过；Phaser Boss 三阶段真实浏览器胜利链路已通过（见下方记录）。
- 未完成：KC-002 与 KC-010 等被拒 KC 的可用题覆盖、Twin A/B 与 7 日延迟效果、DeepSeek 账单/token 对账、稳定全量 Maven 复验；性能后置。串行 Maven 全量为 1400 tests / 0 failures / 7 errors；6 个 pgvector Testcontainers 类仍因 Docker 绑定 `0.0.0.0:48xxx` 报 `address already in use`，长期仿真类在全量运行时 JDBC 连接失败。隔离复跑 7 类中长期仿真 1/1 通过、6 个容器类仍失败。生产 Piston/ECS 与非 Web 客户端按 Web 优先级暂缓。

- AI 编程题自动入库已完成真实 Web 全栈验证：Graph 五重审查/Piston 全用例自测通过后写入 `APPROVED/AUTOMATED`；新学生同 KC 请求命中该记录且没有再次调用模型。V213 迁移已在本地数据库生效。未启用 Piston 的首次真实模型请求因 `sandbox_unavailable` 拒收并回退 Golden（6 次模型 HTTP 200），没有污染题库。
- 自动入库调用点覆盖 Boss 现场生成与 Endless 预生成；`review_source` 区分自动与教师审核，教师可撤下自动批准题，人工终态仍不可改写。入库 requester 测试账号为匿名随机账户，因审计外键保留；复用学生已清理。
- 当前审核相关后端回归：76 项 PostgreSQL/服务/出题/预生成定向测试通过，另 `BossProblemReviewServiceTest` 7 项复跑通过；Web 相关 18 项、typecheck 与改动文件 ESLint 通过。本地进程 `PYT_AI_REQUEST_LIMITS_ENABLED=false`，全栈通过 `./dev.sh --sandbox` 保持运行。
- 尚未完成：Twin 学习效果 A/B 与 7 日延迟复测、DeepSeek 账单/token 对账、生产 Piston/ECS 验证；性能仍后置。Maven 全量测试尚未稳定通过，最新失败原因见下方续验记录。
- 本地 `./dev.sh` 默认关闭应用级 AI 每日次数限制，当前后端进程环境已实测为 `PYT_AI_REQUEST_LIMITS_ENABLED=false`；部署配置仍保留上限。Boss 判题 10 秒提交保护是独立的防重复机制。
- KC 编程题生成链路已补齐课程库上下文：后端权威查找 KC 名称/摘要，Graph 用它构造提示并按摘要术语校验；真实 DeepSeek 生成题通过 Graph/Piston 自测与服务端正式判题后，进入教师 `PENDING` 队列。未自动批准。
- 已实际调用 Tutor 文本和图片流，数据库会话答案非空且图片识别正确；验证完成后删除了本轮 4 个合成学生账号、关联 Tutor 会话/审核记录及用户数据。
- 当前续验：前端全量 Vitest 270 files / 2561 tests 通过。最新后端全量 `mvn test` 为 1389 项、0 assertion failures、7 errors：6 个 Testcontainers PostgreSQL 动态端口绑定失败（Docker 报 `address already in use`），长期仿真类在全量运行中数据库连接失败；该仿真类本轮隔离复跑通过。`TutorDailyRequestLimitServiceTest` 定向复验通过；模型真调用结果见下方“本地请求限额与模型真调用续验”。
- 本轮补强 Tutor：带证据的强事实声明经现有语义核验器，受调用预算/熔断治理；常规回答仍走规则审查。真实 DeepSeek 文本、合成 PNG 识图、Tutor 文本响应与语义反例核验均成功；图片作为输入，模型响应为文本。宿主机直连图片遇 TLS 超时，Graph 容器代理链路复验成功。Mermaid/SVG 图解是文本源码，由 Web 本地渲染，并非模型返回图片。
- 修复教师屋管理员班级查询/创建权限；管理员查看所有班级并取得管理用加入码，教师/学生查询范围不变。验证：Graph Tutor/provider/图片/AutoGen 定向 102 passed（1 条既有 pytest 标记警告）；后端 `ClassroomServiceTest`、`ClassroomRepositoryTest`、`TutorDailyRequestLimitServiceTest` 通过；`git diff --check` 通过。
- 按 Google Java Format 整理本轮 4 个 Java 文件，定向 `spotless:check` 通过。全仓 Spotless 检查仍报告 614 个其他历史文件格式不一致，本轮未对其做批量重写。
- 再跑全量 Maven：1400 tests / 0 failures / 7 errors；6 个 Testcontainers 类在 Docker 绑定随机宿主机端口时报告 `address already in use`，长期仿真类在全量运行中 JDBC 连接失败、隔离复跑通过。因此后端全量结果仍未通过。
- `deploy/scripts/test_production_ai_readiness_contract.sh` 与 `test_piston_http_host_contract.sh` 通过。尝试生产 ECS 只读验证时发现当前 TUN 路由需改走 eth2；本机 `sudo -n` 要求密码，未能加临时路由，因此没有声称检查过生产实例。
- 学情可信度补强：教师批准前的 AI 题可练习但不发 KC 掌握事件；仅与已批准题完整 payload 一致的题能入学情，查询失败时继续返回判题并跳过事件，反馈会说明暂不计入画像。新测试先复现旧行为；最新 Boss/Tutor/Twin/审核关键回归 86 项通过。
- Phaser Boss 多阶段真实浏览器旅程现已完成：三阶段 AI 题绑定、正式判题、服务端受击结算、胜利页与 Phaser 单实例连续性均通过；命中音效 HTTP 200，0 个浏览器 JS 错误。阶段间通过既有预生成/轮询取题，进入加载时清空上一阶段题目。
- 最新 Playwright Tutor 真调用识别了文本校验词及合成图片的蓝色方形/黄色背景，2 条会话可回读；临时测试用户、关联记录和图片均清理。本地 Spring 进程仍为 `PYT_AI_REQUEST_LIMITS_ENABLED=false`，供应商本身费用和速率限制不受影响。
- 最新受影响 Web 回归 4 files / 59 tests、typecheck、触及文件 ESLint、`git diff --check` 全通过。
- 下一步：核对 DeepSeek 供应商实际用量；完成 Twin A/B / 7 日延迟效果核验、稳定全量 Maven 复验和生产 Piston/ECS 验证。性能仍后置；AI 自测/Piston 自测不等于教师题目质量批准。

### 2026-10-14 本地请求限额与模型真调用续验

- 安全读取运行进程环境确认 `PYT_AI_REQUEST_LIMITS_ENABLED=false`；Tutor 每账号 30 次/日与全站 300 次/日计数在 `./dev.sh` 本地模式被跳过，部署默认仍启用。模型服务自身的余额、请求速率限制和单操作重试/幂等保护仍有效。
- 通过正式 `/api/tutor/ask` SSE 重新调用 DeepSeek 文本与图像输入：文本准确复述校验词；合成 PNG 被识别为黄色背景、蓝色正方形；两条回答均能从 Tutor 会话 API 回读。不是 mock 响应。
- 通过正式 Boss 学生 API 做 `PY43-KC-001` Stage 2/3 出题，真实候选经 Graph 与 Piston 自测后返回；然而候选是原样回显/拼接固定标签，不能作为该历史 KC 的可信掌握证据。教师审核 API 将 QA 记录 5–9 全部拒绝，未发布到全局 approved bank。
- Graph 对纯输入回显增加静态拒绝规则（包括仅追加固定标签的回显）。RED 用例先复现旧代码通过，GREEN 后 Graph Boss 228 项通过，Ruff 全量通过；当前观察窗口日志有 13 条 HTTP 200 completion，0 条 provider error。日志行数不含可靠的 token/账单金额。
- 临时远征 run 已通过 API 弃局并确认 204；QA 账号及拒绝审计记录保留在本地数据库。注意：一次早期进程环境检查使用了宽泛进程列表命令，工具输出可能包含运行时凭据；不再重复输出，建议轮换可能出现在该输出中的凭据。
- 前端预生成状态轮询由 10 次 / 500 ms（5 秒）延长到 36 次 / 5 秒（180 秒），与 Boss Graph 读取超时窗口对齐，避免后台仍生成时提示学生手动重试。新页面级回归先复现 5 秒超时，之后远征相关 3 文件 / 9 tests、Vue typecheck、触及文件 ESLint 通过。

### 2026-10-14 工程验证续跑

- 前端全量 Vitest 270 files / 2555 tests 通过；全量 ESLint 0 errors / 7 pre-existing warnings；`npm run build` 成功。Build 有 Pyodide 内置模块、`eval` 和 chunk size 警告，暂不进入性能优化。
- Graph 全量 1836 passed / 1 skipped，Ruff 全量通过。后端最新全量 `mvn test` 为 1383 tests / 0 failures / 7 errors；6 个 pgvector Testcontainers 用例遇 Docker host port `address already in use`，另一个长期仿真用例因容器未启动而 JDBC 失败。
- 串行重跑上述 6 个集成类：19 tests 全通过；长期仿真单独重跑：1 test 通过；BossService、BossGraphClient、EndlessPregen、TutorDailyRequestLimit、WeeklyReport、审核仓储最新 86 项通过。全量 Maven 整体状态仍为失败（本地 Docker 端口冲突），不能写成全绿。
- AI 出题待审修复：学生仍可练习候选题，但待审/拒绝题不再更新 KC 掌握度；仅与审核记录完整内容一致的教师批准题才可发布学习事件。审核状态无法读取时跳过画像事件，并在判题反馈告知用户。RED/GREEN 回归完成；最新 86 项相关后端测试通过。
- 最终 Phaser Boss 多阶段真实浏览器旅程、Twin A/B / 7 日延迟效果、DeepSeek 供应商账单/token、生产 Piston/ECS 验证仍未完成；性能按用户要求后置。

## 0. 用户立场（不变约束）

1. 严格遵守 `/home/cypress/Pythontopia/AGENTS.md`。
2. 完成 `plan/` 目录下全部阶段计划，每阶段必须满足"验收标准"。
3. 完成后做 100 轮代码质量审查与性能优化（小步迭代，不擅自扩功能；目标：让项目更像"成熟教学游戏作品"）。
4. 每个阶段 + 整体优化都要有单测 + 联合测试。
5. LLM 现阶段复用 `/home/cypress/Alethicode/backend/.env`，阶段 11 已接入讯飞 Spark Provider，演示前切换。
6. 前端按星露谷"像素小镇"风格，但素材必须开源/CC0，禁止官方资产。
7. 可以使用 subagent 提升并发；2 分钟级进度推送。
8. 维护本文档，同步 `docs/project/CHANGELOG.md`（中文、简洁）。

## 1. 阶段进度快照

| 阶段 | 名称 | 状态 |
| ---- | ---- | ---- |
| 00 | 仓库骨架与基础设施 | ✅ 已完成 |
| 01 | 课程包导出与种子数据落库 | ✅ 已完成 |
| 02 | 账号、班级与小镇基础后端 | ✅ 已完成 |
| 03 | 前端骨架与 Phaser 小镇 MVP | ✅ 已完成 |
| 04 | 画像屋与 ProfileAgent | ✅ 已完成（含验证补完） |
| 05 | 知识田与 KC 作物系统 | ✅ 已完成 |
| 06 | RAG 服务课件证据检索 | ✅ 已完成 |
| 07 | 资源工坊与六类资源生成 | ✅ 已完成 |
| 08 | 学习路径、每日任务与公告板 | ✅ 已完成 |
| 09 | 训练场、Pyodide 与服务端判题 | ✅ 已完成 |
| 10 | Bug 矿洞与 DebugAgent | ✅ 已完成 |
| 11 | 讯飞 Provider 接入 | ✅ 已完成 |
| 12 | 班级架构与教师看板 | ✅ 已完成 |
| 13 | 学习效果评估与复盘大厅 | ⏳ 待做 |
| 14 | Docker Compose 部署、视觉打磨与文档 | ⏳ 待做 |
| 15 | Beta 验收、视频与 PPT 收尾 | ⏳ 待做 |

## 2. 当前 working set

- 当前焦点：**阶段 13 — 学习效果评估、路径重排与复盘大厅**
- 阶段 12 已完成 ✅ → 见 CHANGELOG。
- 阶段 13 任务列表：
  - [ ] T13-01 V090 `learner_evaluation` + `learner_evaluation_metric`
  - [ ] T13-02 V091 `profile_revision` 加 `evaluation_id`
  - [ ] T13-11 V092 `learning_path.status` 接受 `DRAFT`
  - [ ] T13-10 `LearnerEvaluationService` 5+ 指标 + accept/keep
  - [ ] T13-20 `EvaluationNarrativeAgent`（仅写文字、不改数字）
  - [ ] T13-30 `ProfileService.applyPassiveRevision`（5 类 source）
  - [ ] T13-40 `ReviewHallPanel.vue` + 路由 + store + api

## 3. 接下来要做（按顺序）

1. 阶段 12 全部落地 + 验证（mvn test / npm run test）。
2. 阶段 13 评估 + 复盘大厅 + 画像被动更新。
3. 阶段 14 部署 + 视觉打磨 + 文档。
4. 阶段 15 Beta 验收 + 演示脚本 + 提交包 + 回归报告。
5. 100 轮小步迭代：教学游戏成熟度方向（视觉、节奏、文案、引导、错误处理、可访问性、文档完整度），不擅自加功能模块。

## 4. 决策与风险

- 不使用 ReAct，按确定式 + 结构化输出；多智能体输出全部 pydantic。
- 像素素材只用 OpenGameArt CC0 与 itch.io 标注开源的素材，严禁星露谷官方资产；下载到 `assets/pixel/` 并维护 `LICENSE.md`。
- LLM Provider 优先 `openai_compat`（来自 Alethicode .env），演示前 `export PYT_LLM_PROVIDER=spark`。
- 班级任务发布事务化：同一事务内写 `classroom_quest` + 批量 `quest`；幂等键 `(classroom_quest_id, user_id)`；用唯一约束兜底。
- 看板查询 ≤ 1 秒；班级规模 ≤ 50 不引入异步。

## 5. 历史完成（与 docs/project/CHANGELOG.md 对齐）

详见 `/home/cypress/Pythontopia/docs/project/CHANGELOG.md`，每阶段完成后追加"完成 / 验证命令 / 验证结果"三段。

# 2026-10-14 — DeepSeek 默认模式与本地调用验证

- 按用户要求本地开发不限账号/全站每日 AI 请求数；`dev.sh` 在 Java 进程启动前导出 `PYT_AI_REQUEST_LIMITS_ENABLED=false`，部署默认值保持 `true`。运行中后端配置已核实为 `false`，关闭开关的单测验证不访问用量表。
- 对照 Alethicode `services/tutor-graph/app/clients/llm_client.py` 与 10 月 11–12 日 Codex 工作记录：其 DeepSeek Chat 与视觉调用明确发送 `thinking: disabled`；Pythontopia 已有等价固定阶段 AutoGen Tutor，并保留自己的 SSE/API/附件/checkpoint 契约，不引入第二个 Tutor 微服务。
- 真实模型诊断发现默认 thinking + 极小输出预算可能只产生 reasoning 而没有正文；新增回归后先观察到 2 项失败，再令 `OpenAiCompatProvider.chat` 与 `chat_stream` 默认关闭 thinking、允许显式覆盖，两个 RED/GREEN 用例通过。
- 变更后使用真实 DeepSeek 文本模型和视觉模型，通过 TutorAgent 及 FastAPI Tutor AutoGen/SSE 各执行文本与合成 PNG 图片请求；4 次真实请求均成功，流包含 `STEP_STARTED`、`RUN_FINISHED` 与正确答案。ASGI SSE 测试使用空 RAG fixture，DeepSeek 上游调用是真实请求。
- 定向 Graph pytest 62 passed / 1 既有警告；Java `TutorDailyRequestLimitServiceTest` 通过；`git diff --check` 通过。重启 Compose Graph 容器后，通过本地真实 HTTP Tutor SSE 再次验证文本和图片输入；仍未从浏览器验证 Vite→Java→Graph 持久化链路，也未核对供应商账单或 token 用量。
- 本阶段开始前的工作树快照已本地提交：`5b512edc`；没有推送 GitHub。Alethicode 工作树存在大量未提交内容，本轮仅读取相关 Tutor/管理台文件和 Codex 日志，未修改。

## 2026-10-14 — 浏览器真实模型闭环

- 通过 Playwright 在本地 Web 注册和登录临时学生账号，从工坊发送一条文本问题及一张合成 PNG 图片；浏览器确认两个助手答复、图片载荷进入 `/api/tutor/ask`，后端 `/api/tutor/sessions` 返回两条持久化记录。
- 清理临时账号及 Tutor 历史；数据库前缀检查确认没有残留测试账号。
- 第一次脚本的颜色断言与 Tutor 的苏格拉底式答疑不匹配；第二次断言提前命中用户气泡，过早选择图片。修正为等待助手流结束后，第三次浏览器闭环通过。
- 本地真实 DeepSeek 文本与图片路径已覆盖到 Vite → Spring → Graph → 会话持久化。账单/token 用量仍未核对，Graph 内部每轮工作流的供应商调用数不由这个浏览器断言计数。

## 2026-10-14 — Learner Twin P0 复核与画像证据展示

- 自评请求只开放 `scoreDelta` / `tagsAdd`，学生仅能更新目标与偏好；证据计数和 metadata 由服务端事件维护。WeeklyReportService 使用一致的 Asia/Shanghai 7 日窗口，分别查询全量事件统计和有上限的记忆摘要，KC 增量通过批量事件查询获取。
- ProfileRoom 已将服务端 mastery confidence 作为“证据充分度”单独展示，并说明它不是答题正确概率；此前推荐、周报按钮与真实学习导航/工坊事件处理均有页面级测试覆盖。
- 新 UI 测试先失败后通过。当前验证：后端 57 项（含 PostgreSQL Testcontainers 集成）通过；Twin 页面 Vitest 9 项、TypeScript typecheck 通过。
- 尚未完成个性化学习效果 A/B、7 日延迟复测，以及更大样本校准。不得把当前规则策略描述为已证明提高学习成绩。

## 2026-10-14 — DeepSeek 图片输入/文本输出续验

- 对本地 `OpenAiCompatProvider` 发起 1 次真实 `deepseek-flash` 视觉请求，输入为 64×64 合成蓝色方块 PNG；返回非空 Python `str`，同步响应对象不含图片块。
- `tests/test_openai_compat_provider.py` 的多模态同步/流式文本过滤与未配置视觉模型用例：3 passed，33 deselected。
- 此次为 Graph provider 级调用，不是 Tutor 浏览器链路复测；未记录或读取供应商 token/账单用量。

## 2026-10-15 — 班级加入码限速续接

- 后端加入服务先补 RED 用例：连续错误码后第 31 次请求仍查询并返回 404；已加入学生重复请求也未限制，返回 409。新增每账号滑动窗口后，两类行为都在数据库查询前于第 31 次被拒绝为 429。
- 公网 Nginx 的来源 IP 限速已由 `test_https_forwarded_proto_contract.sh` 与 `verify_nginx_dual_protocol.sh` 覆盖。本轮新增 Java 账号层，不动邀请码格式和数据库。
- 验证：`mvn -q -Dtest=ClassroomServiceTest,JoinCodeAttemptThrottleTest test`、HTTPS 转发契约、Nginx 双协议发布检查与 `git diff --check` 通过。
- 风险：账号窗口是进程内状态，当前适用单 worker；生产公网入口实际运行态仍未从 ECS 确认。

## 2026-10-15 — Tutor 工作流 SSE 错误信息隔离

- 新增回归注入包含数据库主机/密码文本的异常；修复前 SSE 把异常原文发给客户端，RED 复现成功。
- 修复后在服务端记录堆栈，SSE 返回 `TUTOR_WORKFLOW_FAILED` 与固定通用文案。
- 验证：Tutor workflow、AutoGen runtime、附件、教练图、Task 7 事件、Tutor 流式答疑和图片输入测试 93 passed；Ruff 检查通过。

## 2026-10-15 — 管理台共享表格样式对齐

- 依据 Alethicode 管理台和现有 Pythontopia 组件，只调整共享表格表头居中、圆角复用 16px 面板 token、分页居中；保留手动跳页、当前 DTO 字段和已有键盘/窄屏行为。详情对话框、窄屏导航此前已具备，不重复实现。
- RED/GREEN：先观察 `AdminShellStyle.spec.ts` 对旧表格圆角的断言失败；修改后 AdminShellStyle、AdminTable、AdminPagination、AdminUsersPage、AdminProblemReviewsPage 五个定向文件共 26 项通过。
- 样式阶段结束时尚未验证登录管理员的写操作；后续用户状态浏览器联测见下一节。其余 Web 路由动作继续按总清单处理，不进行 DeepSeek 重复探针。

## 2026-10-15 — 管理员用户状态浏览器联测

- 本地临时账号通过真实登录进入 `/admin/users`，搜索另一临时账号并执行停用/恢复；停用期间登录返回 401，恢复后登录返回 200。
- 浏览器测得表头居中、表格圆角 16px、分页居中；320px 视口下 document 宽度为 320px，页面错误 0。
- 删除临时管理员和临时用户，级联记录一并清理。本次仅验证本地用户管理 API/UI，没有调用模型 API；AI 题目审核的真实写入仍需安全的临时审核记录。

## 2026-10-15 — 管理台运维动作文案

- 后端学习闭环快照返回五个 `open:` / `evaluate:` / `review:` 机器键，页面此前直接展示原值。现在将已知键映射为中文建议，原始值仍可从元素 title 读取；未知值继续原样展示。
- 新增的映射测试先因显示机器键而失败；修复后 `AdminOperationsPage.spec.ts` 5/5 通过。未调用模型 API。
- `npm run typecheck` 与本轮触及 Vue/TypeScript 文件的 ESLint 通过。

## 2026-10-15 — 路由壳联测拦截原因

- 上一轮通过 Vite 实际打开 11 条主路由，浏览器页面错误和 5xx 均为 0；因路由测试拦截非 GET 写请求，`/api/game/bootstrap` 和 `/api/auth/refresh` 被挡，故仅记路由壳已渲染，不记完整初始化/动作验证。
- 源码确认 `session.hydrateFromStorage()` 在每次整页启动时会后台刷新 token；测试逐 URL 使用 `page.goto` 反复启动 Vue 应用，触发多次刷新。该请求由测试拦截造成，现有证据不支持产品刷新循环，无需改认证代码。
- 当前本地 `boss_problem_review` 没有 `PENDING` 项；不对既有已审核题目做破坏性操作，也不为制造测试记录重复调用 DeepSeek。Web 动作矩阵仍开放，后续只补尚无真实证据的动作。
- 本次没有修改运行时代码、没有发起 DeepSeek 请求、没有重跑路由矩阵。

## 2026-10-15 — 图书馆 RAG 页面动作

- 在本地全栈以临时学生账号打开 `/library` 并查询「for循环」；带页码证据可见，Playwright 1/1 通过。学生角色的服务端 RAG 查询按权限回退本地检索，没有触发 DeepSeek。
- 删除精确匹配的临时 QA 用户并确认记录为 0。其余 Web 页面动作仍按总清单逐项补齐；本轮不重复 Tutor、远征或性能测试。

## 2026-10-15 — 公告板深链与页签切换

- 以临时学生进入 `/board?tab=classroom`，确认班级任务深链选中正确；再切换历史路径、今日任务，目标内容和 `aria-selected` 状态均正确。
- Playwright 页面错误 0、Tutor/Agent/工坊请求 0。临时用户已删除并验证不存在。

## 2026-10-15 — 画像自评不增加能力证据

- 临时学生在 `/profile` 调整“学习目标”自评。浏览器捕获的 PUT 仅含 `scoreDelta`；重新读取画像后 `evidenceCount` 与 `confidence` 均未变化，页面错误 0。
- 临时用户已删除；没有正式练习、掌握度事件或模型请求。

## 2026-10-15 — 执行器边界与远征恢复样本核对

- 源码核对发现本地 Piston 端口已限定为 `127.0.0.1:2000`，开发 Compose 安全契约断言了回环地址；生产 Compose 不托管 Piston，要求外部 HTTPS 或契约认可的私网 HTTP。Piston 服务本身不校验 Bearer，因此生产端仍须由真实 ACL/鉴权代理保护；没有访问 ECS。
- 只读数据库聚合：21 条 `status=ACTIVE` 记录（challenge 16、map 3、over 2）；满足第 2 章以上前沿先修条件的学生 1 个，该账号没有活动 run。未查询身份字段。此样本不能验证刷新续局，也不能代表实际用户完成率。
- 没有 DeepSeek 请求、数据写入或运行时代码改动；当前需通过正常学习路径取得合格的活动 run 样本后再做唯一一次恢复验收。

## 2026-10-15 — 复盘大厅 Twin 推荐路由修复

- 真浏览器新生 0/65 点击复盘大厅推荐 KC-001 后进入 `/expedition?focus=1&kcCode=PY43-KC-001`；这是错误入口，因为未评估 KC 应返回主线。
- 复用 `routeForTwinRecommendation` 统一首页、学习分身与复盘大厅目的地。回归先 RED（收到远征路由）后 GREEN（`ReviewHallPanel` 2/2）；`npm run typecheck`、定向 ESLint 通过。
- 修复后临时学生浏览器验收进入 `/mainline-map`，主线地图可见，页面错误 0、模型请求 0；账号删除后计数为 0。没有 DeepSeek 请求或正式学习数据写入。

## 2026-10-15 — KC-002 正常主线补题、共享入库与服务端判题

- 配置核查：讯飞图片生成/图片理解、TTS、ASR 的 properties 均固定 `isEnabled() == false`；控制器与分镜合成都调用受此开关保护的服务，没有发现可绕过调用点。未改代码、未发媒体请求。
- 新生页面从 `/mainline/stage/3` 点击 KC-002 AI 补题，唯一一次正常页面生成请求 HTTP 200，浏览器错误 0；题目 `APPROVED/AUTOMATED`，审核记录 ID 83，题型为 `kind=NULL`。没有重复探针。
- 第二名临时学生经同一主线页面读取已批准 KC-002 题，未生成新题；服务端正式判题 HTTP 200，3/3 通过，产生一条 `practice_succeeded` 与 KC-002 `+0.140`。确认后删除该学生，事件与掌握度记录计数归零。
- 生成题的请求者因 `boss_problem_review.requester_id ON DELETE RESTRICT` 保留为本地 QA 账号（用户 ID 675）；该账号没有学习事件，不作为画像证据。
- 远征仍不能据此验收：KC-002 主线题为 `kind=NULL`，不满足 Boss `dual_track`/`closed_loop` 节点；活动 run 样本与真实先修证据仍待。没有重复 DeepSeek provider/Tutor 探针，也未做性能测试。

## 2026-10-15 — AI provider 跨服务静态核查

- Graph 的生成式模型路由统一通过 `provider_from_env()`；provider 固定要求 `https://api.deepseek.com`，模型白名单也限制为 DeepSeek。Boss 命题通过同一 provider 注入，没有独立厂商客户端。
- RAG 无外部 embedding API：默认 `hash`，可选仅本地缓存权重 BGE；讯飞 Spark embedding 只有抛 `NotImplementedError` 的占位类，`select_embedder()` 对讯飞/智谱等 provider fail fast。
- 前端没有直接调用生成式模型厂商域名；后端残留讯飞媒体类仍固定禁用（上一条已核查）。本次仅源码/配置静态检查，没有模型请求或 API 改动。

## 2026-10-15 — 已退役示例库入口核对

- `/examples` 页面已由 router `beforeEnter` 迁移到 `/problems`，并只保留字符串形式的 `kcCode` 查询参数；题库 KC 筛选此前已有真实浏览器证据。
- 本轮一次 Playwright 探索误等已退役页面的标题而超时；未触发模型或业务写入。临时登录账号已删除并确认不存在，不重复重跑等价的题库筛选动作。

## 2026-10-15 — 有效零题 KC 的题库深链筛选

- 上次浏览器路由发现 `/examples?kcCode=PY43-KC-001` 重定向保留了 URL 参数，但题库加载后清空筛选。只读核对确认 KC-001 存在于课程 KC 元数据，当前编程题 fixture 没有该 KC 的题。
- `ProblemListPage` 现在以完整 KC 元数据区分“有效但零题”与“未知 KC”；有效零题 KC 保留在当前筛选选项并显示 `0 题`，未知 KC 延续既有的清除行为。
- 回归测试先 RED（下拉值从 `PY43-KC-001` 变为空）后 GREEN。定向测试 10/10，`npm run typecheck` 和触及文件 ESLint 通过；此前单独 `git diff --check` 通过。
- 修复后真实 Chromium 在 320px 下复验 `/examples?kcCode=PY43-KC-001`：跳转到 `/problems`，所选 KC 保留，结果为 0 题；页面无溢出、脚本错误或模型请求。
- 同一轮抽样检查 `/mainline/stage/2`、`/problems`、`/profile-room`、`/library`，均无 320px 横向溢出、页面错误或 5xx；没有业务写入或模型请求。用户 ID 678 与本轮临时账号均已清理。

## 2026-10-15 — KC-006 主线 AI 编程练习入库

- 只读查询确认静态 `course_problem` 和 `boss_problem_review` 均无 KC-006 题。通过正常主线第 1 章“计算与计算科学”页面点击 AI 补题一次；HTTP 200、页面展示可编辑的 Python 输出练习、浏览器错误 0。
- 生成题用 `print` 输出“计算就是对符号串按规则做变换。”和“计算是科学研究工具。”；响应 `reviewed=true`、`aiGenerated=true`，界面显示“AI · 5 重审查”。审核表记录 ID 84，`decision=APPROVED`、`review_source=AUTOMATED`、`kc_code=PY43-KC-006`、难度 1、`kind=NULL`。它已在现有共享 AI 审核题库中，不新增表或迁移。
- 本轮未提交代码，没有新增学习事件或掌握度。请求者 ID 679 因审核表 requester 外键保留；账号没有作为学习证据。题型为空意味着该批准题还不能覆盖要求 `dual_track` / `closed_loop` 的远征节点。
- 这是一个正常主线出题流程，不是通用 provider 探针；没有再次发起生成或做性能测试。

## 2026-10-15 — KC-006 跨学生共享题库与正式判题

- 第二名临时学生从主线 KC-006 的正常“生成 AI 编程练习”入口取得审核 ID 84 的共享题，编辑两行输出代码后提交。响应 `reviewed=true`、`aiGenerated=true`；判题端点 HTTP 200，页面显示服务端通过，浏览器错误 0。
- 本地审核表仍只有 1 条 KC-006 记录；测试产生 1 条 `learning_event_log` 和 1 条 `kc_mastery_event`。按该临时用户 ID 删除后核对用户、Boss attempt、学习事件、掌握度事件计数均为 0；共享审核记录 ID 84 保留。
- 该动作验证共享库复用与正式判题，不重复模型生成。`kind=NULL` 仍是远征兼容缺口。

## 2026-10-15 — KC 状态校正与 Twin 窄屏折叠动作

- 交叉核对当前计划、执行状态与 Changelog 后，将 KC-011 状态由“最新生成失败、未入库”更正为“后续已有 `APPROVED/AUTOMATED` 入库成功，但没有正式学生判题或掌握度证据”；较早的失败日志保留为历史，不重跑生成。
- 只读核实远征题型链路：`RogueRunStore.beginChallenge()` 传入节点题型，`useChallengeJudge` 发送 `preferredKind`；Boss 服务精确读取同 KC/题型批准题，未命中时传题型给 Graph。端到端仍缺兼容题与合法活动 run。
- Docker Graph 容器 healthy；`127.0.0.1:8000` 没有宿主机端口映射。本轮未调用模型或写入远征数据。
- 320px Chromium 在 `/profile-room` 打开“近期变化”折叠区；前后文档宽度均为 320px，页面错误 0。脚本过滤器最初误把 Vite 静态模块 `/src/api/tutor.ts` 当成模型 API，实际模型端点请求为 0。测试用户 685、686 均已清除；用户、学习事件、掌握事件、访谈记录计数为 0。

## 2026-10-15 — 汇总清单与提交

- 完成用户要求的本地提交：`99126642 fix: consolidate problem routes and review actions`；提交后工作树干净。
- `/examples` 退役页旧测试改为路由契约测试，静态重定向保留 KC 筛选；`npx vitest run tests/integration/routing.integration.spec.ts` 27/27 通过。
- 更新唯一主任务清单，合并附件中的安全、远征、Twin 与全 Web 验收范围；DeepSeek 状态引用既有真实调用与错误记录，本轮 0 次调用。
- 本轮只读检查确认注册角色/邀请码、DISABLED 与 JWT 代次撤销、subprocess fail-closed、服务端远征 verified-attempt 结算均已有实现；对应后端套件尚未在本轮重跑。Piston 生产网络 ACL/鉴权、扩展安全审计、battle-compatible KC 题与活动 run 恢复、完整路由动作矩阵、Twin A/B 与 7 日复测、性能仍未完成。

## 2026-10-15 — 上传失败日志隐私修复

- 根因：`UploadService` 在解析失败时把客户端原始文件名与解析器异常消息写入应用日志；前者可包含个人文件命名，后者可能携带内部解析细节。
- 回归先 RED：Logback 捕获到合成敏感文件名和解析器详情；GREEN 后日志只记录内部用户 ID 与已白名单校验的扩展名。对上传者可见的失败记录未变。
- `JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -q -Dtest=UploadServiceTest test`：17/17 通过；`git diff --check` 通过。没有模型请求。

## 2026-10-15 — 上传语料索引日志脱敏

- `UploadCorpusIndexingService` 原先将向量化/分块异常原文写入 warn 日志；回归先 RED 并捕获到模拟的私密提取文本，随后仅记录 upload ID 与异常类型。
- `JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -q -Dtest=UploadCorpusIndexingServiceTest test`：GREEN 1/1。保留现有 fail-soft 行为，没有新增网络或模型请求。

## 2026-10-15 — 前端依赖漏洞盘点

- 默认 npm registry mirror 的 audit endpoint 返回 404（该镜像未实现安全审计接口）；改用官方 `https://registry.npmjs.org` 对当前前端 lockfile 执行一次审计，结果 0 项漏洞，未修改依赖或 lockfile。
- 本结果只覆盖 Web 前端 npm 依赖；Python Graph/RAG 与 Maven 依赖扫描尚未执行，不扩大结论。

## 2026-10-15 — Python 与 Maven 运行时依赖审计

- Python：按 Graph `.[browse]` 与 RAG 核心安装约束生成临时运行时清单，再用 `pip-audit` 的 OSV 服务分别审计；Graph 72 项、RAG 28 项，均为 0 项已知漏洞。此前本机开发 venv 扫描仍有 25 个不同包名、212 条 advisory 行，包含可选 eval/dev 依赖及同包/跨环境重复；当前开发环境未升级。
- Maven：通过 `dependency:tree -Dscope=runtime` 解析 95 个 `compile/runtime` JAR，向 OSV `/v1/querybatch` 按 Maven PURL 查询，0 项已知漏洞；测试 scope 不在本次范围。
- OWASP Dependency-Check 13.0.0 插件已解析并启动，但 NVD 全量数据更新因本地没有 NVD API key 失败，报告未生成。该失败不是“无漏洞”结果；本记录只依据 OSV 的精确版本查询。参考：[OSV querybatch API](https://google.github.io/osv.dev/post-v1-querybatch/)、[OWASP Dependency-Check 使用说明](https://dependency-check.github.io/DependencyCheck/dependency-check-maven/)。

## 2026-10-15 — BROWSE SSRF 安全墙复核

- 服务端 `BrowseRunService` 只向 Graph 发送学生目标与服务端配置的域白名单；Graph 的 `validate_network_url` 对初始 URL 与每次导航/重定向检查 scheme、凭据、端口、域名和 DNS 公网地址，Chromium host resolver pin 到通过公网校验的地址，document-only GET route 阻断其他资源。
- 复用现有回归：`services/pythontopia-graph/.venv/bin/python -m pytest -q tests/test_browse_allowlist.py tests/test_browse_executor.py`：58/58 通过，无新增代码。只覆盖 BROWSE SSRF 安全墙，不代表整套安全审计完成。

## 2026-10-15 — 调试轨迹与 Piston 错误日志脱敏

- 回归先 RED：畸形 trace JSON 让 Jackson 错误把学生输出 token 带入 warn 日志；Piston 模拟 502 响应把提交代码回显进日志。
- GREEN 后，`PythonTraceCollector` 只记异常类型，`PistonPythonSandbox` 的执行与 readiness 错误也只记异常类型；原有 fail-closed 结果未改变。
- `PythonTraceCollectorTest` 1/1、`UploadCorpusIndexingServiceTest` 1/1、`PistonPythonSandboxTest` 10/10 通过。配合 `UploadServiceTest` 17/17，已闭合本轮发现的四个敏感数据日志点；其他日志和完整安全审计仍待检查。

## 2026-10-15 — Java 定向格式检查

- 首次全仓 `mvn spotless:check` 失败，错误清单涉及 600 多个既有 Java 文件；没有对全仓运行 `spotless:apply`。
- 对本轮文件的定向 `spotless:check` 同样发现既有格式差异；为避免把历史排版改动混入日志脱敏修复，撤销格式化带来的无关行变化。格式基线仍未收敛，后续按工程清理任务单独处理。

## 2026-10-15 — 外部 HTTP 错误日志脱敏

- Mem0/Zep 公共 HTTP 基类曾把异常原文（含远端 500 响应体）写入 warn 日志并拼入上层异常消息；合成学生记忆标记先 RED 复现，修复后只记录异常类型、抛稳定异常消息。`Mem0MemoryProviderTest` 4/4、`ZepMemoryProviderTest` 3/3。
- Graph 重试助手曾把远端异常原文写入 retry DEBUG；合成响应标记先 RED 复现，修复后只记录异常类型。`GraphClientRetryHelperTest` 1/1。
- 本轮回归均为本地隔离调用，没有请求 DeepSeek。其他直接 Graph/HTTP 客户端仍有异常消息日志候选，完整安全审计未结束。

## 2026-10-15 — 画像访谈日志脱敏

- `ProfileService` 向 Graph 发送学生自由文本；异常消息可能包含下游响应。模拟敏感标记先 RED 复现，修复后只记录异常类型，稳定错误码与用户提示不变。
- `JAVA_HOME=/home/cypress/.local/tools/jdk-21 mvn -q -Dtest=ProfileServiceFailureTest test`：GREEN 1/1。没有 DeepSeek 请求；其余直连客户端和完整安全审计仍待处理。

## 2026-10-15 — NPC、记忆向量与 B 站问答日志脱敏

- NPC Graph 对话、RAG 记忆向量与 B 站视频问答的上游错误都能回显请求/响应文本；三个合成标记均先 RED 命中旧日志，再改为只记录异常类型。
- 定向 GREEN：`NpcDialogGraphClientTest` 2/2、`EmbeddingClientTest` 7/7、`BilibiliInsightServiceTest` 4/4。没有 DeepSeek 请求或业务数据写入。
- 仍有其他直连 Graph/HTTP 客户端与本地媒体/持久化降级日志未逐项确认；完整安全审计保持未完成。

## 2026-10-15 — 章节测评无效路由恢复

- 本地全栈 Chromium 以临时学生打开 `/chapter-test/99`，页面显示 1–7 章范围错误；点击“返回冒险地图”到达 `/mainline-map`，页面错误 0。
- 该分支在挂载时跳过测评 `loadLatest/prepare`；测评、模型与判题 API 请求均为 0，仅有一次认证刷新。过滤器曾命中 `/src/components/tutor/...` 的 Vite 本地模块 GET，按请求类型复核并未算作模型 API。
- 临时学生删除后数据库行计数为 0。仅验收无效章节错误态与恢复动作，不代表有效考试完整流程。

## 2026-10-15 — 官方工程规范对照

- Google Java Style Guide 规定 import 分为静态/普通两组并按 ASCII 顺序排列；据此修正 `EmbeddingClientTest` 与 `BilibiliInsightServiceTest` 的新增 import 顺序。仓库 Java 命名继续遵守 AGENTS 的类 PascalCase、成员 camelCase 规则。
- OWASP Logging Cheat Sheet 建议不要直接记录源码、敏感个人信息、访问令牌等；本轮用合成标记回归并从上传、记忆、画像访谈、NPC、RAG 与外部服务异常日志中移除原文。
- Vue 官方风格指南和 typescript-eslint 命名规则已加入工程收尾参考；本轮无 Vue/TS 行为改动。全仓 Spotless 及定向检查已有历史格式差异，不做全仓重写。
- 参考：[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)、[Vue Style Guide](https://vuejs.org/style-guide/)、[typescript-eslint naming-convention](https://typescript-eslint.io/rules/naming-convention/)、[OWASP Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)。

## 2026-10-15 — 班级成员加入与移除真实浏览器验收

- Chromium 临时教师建班，学生从 `/home` 输入小写加入码，真实接口成功（`POST /api/classrooms/join` HTTP 200）。
- 教师“成员”页显示学生；确认弹窗后移除成功（HTTP 204），成员页返回真实空态，浏览器页面错误为 0。
- 首次自动化误等旧 `/town` 地址；路由实际重定向 `/world`，修正等待条件后复测通过。临时用户、班级及成员行清理后分别计数为 0。无模型请求；教师任务发布/归档后续结果见执行状态记录。

## 2026-10-15 — 教师任务发布与归档浏览器动作

- 临时教师在真实班级看板发布任务后点击归档；两个写接口均返回 2xx，前端 store 重新读取服务器列表后呈现 `ARCHIVED`，归档按钮消失。
- 直接数据库复核 SQL 因 `status` 列未限定表别名而报错，页面错误计数也未输出；不把这两项标成已验证。清理后临时用户、班级和任务计数均为 0。没有模型请求。

## 2026-10-15 — 禁用图片 provider 请求计数修复

- `XfyunImageService.generate()` 在确认图片 provider 固定关闭前先计入 per-user 日限流表；同一账号第 51 次请求会错误返回 `IMAGE_RATE_LIMITED`，并为关闭的服务保留用户计数。
- 将 disabled 检查提前。回归 RED 先在第 51 次命中错误限流，GREEN 后 51 次均稳定返回 `IMAGE_PROVIDER_UNAVAILABLE`；`XfyunImageServiceTest` 2/2。
- 代码静态审查同时确认 Graph 题目/答案执行走隔离 Piston、应用 subprocess 沙箱 fail closed、Graph LLM provider 强制 DeepSeek host/model、TTS/ASR/旧讯飞图片能力开关关闭。没有模型调用、数据库写入或 ECS 操作。

## 2026-10-15 — MemoryBrief 日志与上传配额顺序修复

- Graph MemoryBrief 的网络异常日志曾直接写异常原文（可包含带学生 query 的 URL），schema 校验日志还会输出 Pydantic traceback 与无效 `input_value`。两条哨兵回归先 RED 后 GREEN；网络日志只记录异常类型，schema 失败只记录固定类别，`test_memory_brief_client.py` 27/27。
- 上传服务曾在额度锁定前复制完整文件字节。把额度查询移到 `getBytes()` 前，拒绝超额请求时避免这次额外堆内存复制；Spring multipart 文件大小仍先受 8 MB 上限、业务文件仍受 5 MB 上限约束。回归先 RED 后 GREEN，`UploadServiceTest` 18/18。
- 本轮未发 DeepSeek 请求，未触发上传数据库写入。生产限流/部署边界与其他未审计路径仍开放。

## 2026-10-15 — 管理运维页读取失败重试回归

- `AdminOperationsPage` 已有失败提示与重试按钮；新增组件回归验证首次加载失败后可重试，并在成功响应后清除错误、展示运维数据。
- 本地 Chromium 打开 `/admin/operations`，路由模拟认证刷新、运维 GET 503 后重试 200；错误清除、统计数据展示，API 请求两次，页面错误 0。`AdminOperationsPage.spec.ts` 3/3，定向 ESLint 通过。
- 鉴权与运维读取均由浏览器路由模拟，不是后端授权或 API 联测；无模型请求和业务写入。

## 2026-10-15 — Tutor 与视频概述异常日志脱敏

- Tutor 工作流端点兜底原本通过 traceback 记录异常正文；B 站元数据和概述 Agent fail-soft 路径也记录 traceback。合成敏感标记回归确认旧日志会输出异常内容。
- 三处现在只记录异常类型，Tutor 固定 SSE 错误与视频降级返回保持不变。`test_tutor_workflow.py`、`test_video_insight_route.py` 共 27/27，Ruff 通过。
- 全程使用 stub，不调用 DeepSeek 或 B 站外部接口；完整安全审计未结束。

## 2026-10-15 — 教师班级详情重试回归

- 覆盖班级成员初次读取失败后，点击重试并恢复成员数据；`ClassroomDetail.spec.ts` 13/13。
- 本地 Chromium 打开 `/teacher/classroom/9`，模拟首个成员 GET 503、重试 GET 200；错误区可见，重试后班级成员展示，页面错误 0。
- 教师认证刷新与页面 API 都由浏览器路由模拟，未做后端授权联测或业务写入；没有模型请求。

## 2026-10-15 — Tutor 事件阶段真实浏览器联测

- 首次流程在旧 Java 运行进程上通过导读，但“没帮助→换种解释”没有返回 AI 回复卡。对照进程、源码和编译类时间发现 Java 进程早于本次动态检查点校验构建；旧版本仍按固定索引校验事件专属阶段，不能据此判定新代码失败。该次临时账号及级联 Tutor 数据已删除。
- 按 `./dev.sh --down` 与 `./dev.sh --sandbox` 重启本地全栈（数据库卷保留），确认 backend/Graph/RAG/Piston/Vite 健康端点均为 HTTP 200；Spring `/readyz` 仍因可选视频字幕能力返回 503。新 Java 进程晚于当前编译类。
- 新鲜 Chromium 学生流程：注册 201、登录 200、题目读取 200、Tutor 会话读取 200；题目导读与卡片反馈后的 `AGENT_FEEDBACK` 两条 SSE 均 200，DeepSeek completion 日志 2/2 为 HTTP 200；前端收到 278 与 348 字符的非空文本卡片，页面错误 0。临时学生及级联学习数据已删除。
- 本次只验证 Tutor 产品流程，没有重复通用文本/PNG 探针；图片输入历史证据保持原记录，图片中继稳定性仍未验证。

## 2026-10-15 当前续接：周报定时任务错误日志脱敏

- 周报批处理失败日志曾输出学生 ID 和 exception message；哨兵回归先 RED，确认两类敏感内容都会进入日志。
- 当前仅记录异常类型；学生级失败仍隔离并计入批次失败数，provider 日限额停止规则不变。`WeeklyEvaluationSchedulerTest` 4/4，RED/GREEN。无模型请求。

## 2026-10-15 当前续接：远征预取错误信息与日志边界

- 生成失败异常原文会写入学生可读 `error_message`；领取题及持久化已展示题的失败日志会输出 user/KC/problem 标识与数据库异常正文。合成哨兵与状态检查先 RED。
- 生成失败状态现固定显示“题目预加载失败，可重试”；两个异常日志只保留异常类型。题目可重试和失败不置 READY 的行为不变。`EndlessPregenServiceTest` 19/19，含周报测试合跑 23/23。无模型请求。

## 2026-10-15 当前续接：画像地图失败审计脱敏

- `CurriculumMapperService.bootstrap` 失败时曾将 Graph 异常正文写入 `resource_run.output_artifact`。合成响应先 RED，确认运行记录会保存 sentinel。
- 现在只记录固定 `graph-service-error`；失败仍返回原有 `CURRICULUM_MAPPER_UNAVAILABLE`。`CurriculumMapperServiceTest` 1/1，与周报和远征服务测试合跑 24/24。无模型或业务数据库请求。

## 2026-10-15 当前续接：Python 运行环境依赖安全

- 实际 Graph/RAG 容器此前仍使用 FastAPI 0.115.14、Starlette 0.46.2，未与当前 `pyproject.toml` 保持一致。按当前约束重建后，Graph/RAG 均运行 FastAPI 0.143.1、Starlette 1.8.0；健康/就绪接口 HTTP 200，容器 healthy，`pip check` 无破损依赖。镜像下载增加 120 秒超时与 5 次重试以适应 Aliyun 镜像吞吐。
- 两个本地开发 venv 同步到当前约束；Graph 中未声明的 `browser-use`/Google GenAI 包、RAG 中旧 LangGraph 栈已清理。Fresh OSV audit 当前只剩 Graph 可选 extra 的 `diskcache 5.6.3`（CVE-2025-69872）与 `ragas 0.4.3`（CVE-2026-6587），公告未列修复版本；两者未装入运行镜像。
- pytest 8.4.2 命中 CVE-2025-71176；Graph/RAG 开发约束改为 pytest `>=9.0.3,<10` 与 pytest-asyncio `>=1.3,<2`，本机同步为 pytest 9.1.1 / pytest-asyncio 1.4.0。官方 pytest 9.0.3 发布说明记载该版本修复不安全临时目录问题，pytest-asyncio 官方发行记录注明 1.3 支持 pytest 9。
- 变更后 `pip check` 两边通过；Graph 1902 passed、1 skipped、2 warnings，RAG 166 passed、1 warning。OSV 复扫针对两套 venv 合并执行；未触发 DeepSeek、未写业务数据库。

## 2026-10-15 当前续接：班级加入码重置闭环与 SQL 静态核查

- Chromium 临时教师在班级页重置加入码后，页面显示新码与成功提示；学生用旧码加入返回 HTTP 404，用新码加入返回 HTTP 200。页面错误 0，数据库确认临时用户、班级、成员行均为 0。无模型请求。
- 审阅 `PracticeSubmissionRepository`、`LearningResourceRepository`、`JdbcUploadChunkRepository`、`QuestRepository` 的动态 SQL：动态拼接只选择固定 SQL 片段，用户值经参数绑定；未发现确认 SQL 注入问题。此项只覆盖这些动态构造点，不代表完整安全审计。

## 2026-10-15 当前续接：远征、NPC、Boss 与判题日志隐私

- 新增回归先 RED：远征预取成功/失败、NPC 寒暄降级、Boss Twin 难度兜底、判题 `SYSTEM_ERROR` 日志中捕获学习者 ID 或模拟异常正文，共 6 条失败断言。
- 修复后日志改为保留任务/提交标识、KC 或 NPC 上下文、异常类型，不再记录学习者 ID 或判题失败正文；功能降级和“不发学习事件”行为不变。
- `EndlessPregenServiceTest`、`NpcGreetingServiceTest`、`BossServiceTest`、`SubmissionServiceTest`、`MainlineServiceTest`、`UploadServiceTest`、`ReflectionTriggerTest` 合计 142/142 通过。`git diff --check` 通过。未调用模型或写入业务数据库。其余业务操作审计日志仍需按用途审查，不以本批替代全仓安全审计。

## 2026-10-16 — 核对 Web 小镇 NPC 锚点

- Web `town_hub.json` 有 7 个 NPC 锚点，逐一与 `data/npcs/index.json` 核对后，正好匹配全部 `spawnMap: "town_hub"` 角色；目录另有 4 个角色的 `spawnMap` 为 null，不是地图漏项。
- 只读检查，无代码改动、测试或模型调用。把结论记入唯一工作清单，避免重复补图审计。

## 2026-10-16 — NPC 存档 key 白名单与后端全量回归结果

- 安全复核发现 `GET /api/world/npcs/{npcKey}/state` 与 `POST /talk` 接受任意格式有效的 key，首次访问会写入当前用户的新状态行。现共用 repository 的中央小镇 12 key 清单，加入 Android 地图已有的 `captain_def`；未知 key 由 service 返回 404，且不触库、不清理历史行。
- 两条未知 key 回归先 RED（旧代码触发空 Mock 返回的 NPE，并发生 repository 调用），GREEN 后 unknown state/talk 均被拒绝且 repository 零交互；Android `captain_def` 仍可读。NPC service/controller 8/8；合并 BROWSE、AI 预算、NPC 定向合跑 41/41；定向 Spotless、`git diff --check` 通过。
- 后端 `mvn -o test` 全量为 1498 项、1 失败、7 错误。唯一断言失败是 `AiBudgetRouteCoverageTest` 仍认为 BROWSE 创建路由不应预算保护；更新为学生预算断言后，定向 41 项通过。未重跑全量。其余 7 个错误为 6 个 pgvector Testcontainers 启动失败和 `LongTermClassroomEffectSimulationTest` 的 JDBC 连接拒绝；全量不记为通过。
- 对话节点、完成列表及 flags 仍由客户端提交，但只写当前用户自己的对话存档，静态搜索未发现下游任务、奖励或权限消费；Web/Android 对话树合同尚未统一，后续需合同层面再处理。
- 本轮未发 DeepSeek 请求。

## 2026-10-16 — Graph Boss 自测空白规则与本地 Piston 抽查

- 对照 reference/debug 自测与玩家判题路径，确认自测原先对 stdout 和期望值调用 `strip()`，会忽略前导空格；正式评测使用 `rstrip()`。两条回归先 RED 后 GREEN，现保持两处自测与正式评测一致。
- 本地 Piston 运行态抽查：`/proc/1/environ` 读取失败；执行环境敏感变量名计数为 0；打印 150,000 字符以 SIGKILL 结束，Graph 适配器返回上限为 65,536 字符。UDP socket 探针未成功连接，但退出为非零且本次未保留 stderr，因此不记作完整网络隔离证明。
- Boss 自测定向 20/20；Graph 全量 1928 passed、1 skipped、2 warnings；Ruff 与 `git diff --check` 通过。无 DeepSeek 请求。

## 2026-10-16 — 修正远征长先修链回归夹具

- 将远征选题策略的 65 KC 回归构造为跨 7 章的先修链，确保聚焦第 7 章时必须回溯 64 层才能抵达基础 KC。复验发现旧夹具把前 64 个候选都放在第 1 章，因候选回退直接命中根 KC，未覆盖原固定深度缺陷；现修正夹具。
- `AdaptivePracticePolicyTest`、`LearnerTwinServiceTest`、`BossServiceTest` 合计 63/63 通过，`git diff --check` 通过。只验证了共享选题策略；远征战斗题型与有效活动 run 的刷新恢复仍未验收。没有模型请求或业务数据写入。

## 2026-10-16 当前续接：提示词边界、远征日志与窄屏

- Graph 动态画像、记忆、检索证据及模型生成上下文改为 user 数据；NPC/Feynman 持久化对话拒绝 system role。定向 298 passed，全量 Graph 1924 passed/1 skipped，Ruff 与 diff 检查通过。没有 DeepSeek 调用。
- Boss 缺题日志改为固定缺题提示，不再输出题目 ID/用户 ID。回归先 RED 后 GREEN；`BossServiceTest#missingProblemPublishesNothing` 通过。
- 真实 Chromium 320px 检查发现远征入口的“聚焦章节”标签被挤成竖排。修复后页面测试 5/5、typecheck、定向 ESLint 通过；浏览器确认 document/body 320px、标签 56px、选择框 244px、保存退出按钮可见、页面错误 0、模型 API 请求 0。临时学生删除且数据库前缀计数为 0。
- 初次浏览器检查脚本把 Vite 的 tutor 静态模块误判为模型 API；改为只检查 `/api/` 路径后复核通过。此前“生成时无法离开节点”判断也已纠正：现有顶栏保存退出一直可用，返回小镇后可恢复远征，无需增加重复按钮。
- 本次窄屏仅覆盖远征入口，活动地图与战斗布局仍待检查；没有真实先修条件的高章远征活动 run 不做合成验证。
- 后续另检查 `/mainline-map` 320px 视口：document/body 宽度均为 320px，页面错误 0、模型 API 请求 0；临时账号删除并核对残留为 0。地图和 Boss 战斗的窄屏检查仍未完成。
- Graph 提示词静态续扫复核了直接 system-role 构造点；未发现新的学生文本/检索原文进入 system 的路径。剩余动态范围条目限于服务端维护的课程 KC、证据页白名单等元数据，其他为固定规则。此为代码结构审查，没有模型调用。

## 2026-10-16 — 上传解析与异步索引资源边界

- 复核已有上限：服务端单文件 5 MiB、每账号 20 个/50 MiB；PDF 页数 100；DOCX 最多 100 个 ZIP 项、解压后 16 MiB；最终提取文本 100,000 字符。解析器既有测试未重复运行。
- 新发现未指定 executor 的 `@Async` 会回退到无界线程执行器。现提供共享 `taskExecutor`，限制为 2 worker/16 队列，覆盖章测、预防包、记忆向量化与上传索引。队列满时章测状态改为 `FAILED`；预防包拒绝不影响预测返回；记忆向量化拒绝不再跳过 NPC 记忆且拒绝日志不包含用户 ID；上传仍返回已保存记录并记录固定提示。
- 公网入口仅对精确 `POST /api/workshop/uploads` 按来源 IP 限速 2 次/秒、突发 5 次，超限返回 429；该路径请求体限制 6 MiB。追查双层代理发现 frontend Nginx 默认 1 MiB，现与 Spring `max-request-size=12MB` 对齐，恢复既有 5 MiB 文件及语音上传能力。
- 执行器、章测、预防包、记忆事件、上传控制器与索引服务回归合计 42/42；HTTPS/Nginx 契约 18 项、Shell 语法和 diff 检查通过。Nginx 1.27 对公网边缘与 frontend 配置分别 `nginx -t` 通过。队列满时上传向量索引会丢弃，当前无持久重试。无 DeepSeek 请求、真实公网联测或业务数据写入。

## 2026-10-16 — 讲座页助教与返回导航验收

- 真实 Chromium 注册临时学生后进入 `/lecture/1`，加载讲座页并展开 AI 助教侧栏，再用“小镇”按钮返回 `/world`；两个目标页面和嵌入侧栏均可见。
- 页面错误 0，Tutor 提问与资源生成 POST 为 0；页面读取了课程视频、章节/KC、Tutor 会话和本地 B 站列表接口。没有答题、消费资源或上报学习事件。
- 临时学生 `qa_lecture_nav_66871830`（ID 721）已删除，按用户名复查残留为 0。

## 2026-10-16 — 后端全量 Maven 复跑

- 新鲜执行 `mvn -o test`：1509 项、0 个断言失败、7 个错误。6 个 Testcontainers PostgreSQL 容器由 Docker 随机映射主机端口时返回 `address already in use`；长期仿真在全量套件运行中连接重置。
- 单独复跑 `LongTermClassroomEffectSimulationTest` 通过；单独复跑 `CoursePackServiceIntegrationTest` 仍发生随机主机端口冲突（46474）。未定位占用端口的具体进程，故只记录为环境错误，不标记后端全量通过，也不跳过测试。
- 本轮未调用 DeepSeek、未改业务代码、未操作生产环境。

## 2026-10-16 — 远征快照客户端篡改边界核验

- 审阅 `PUT /api/world/expedition/run` 与全部调用点：接口只接受本人当前活跃 run 的服务端 ID，校验章节及地图拓扑后读取并返回数据库快照；请求里的 HP、分数、节点进度、遗物和历史均不落库。节点动作另经带用户行锁的服务端命令处理。
- 定向执行 `mvn -o -Dtest=ExpeditionRunServiceTest test`：11 项通过、0 失败、0 错误，包含伪造进度字段后仍返回规范快照的断言。没有发现需修复的代码问题。
- 该证据只覆盖快照 PUT；不代表活动 run 的真实浏览器刷新、战斗结算与完整远征状态机均已端到端验收。没有模型调用或业务数据写入。

## 2026-10-16 — B 站搜索词日志隐私修复

- 发现 Graph 视频检索放宽时长筛选时将 `effective_keyword` 写入 INFO；此外 Uvicorn 默认 access log 会记录请求 query。公网 Nginx 已配置为只记 `$uri`，但这不能覆盖 Graph 自身日志。
- 搜索词哨兵测试先 RED；现日志只记录筛选放宽及结果数，Graph 开发 Compose 与镜像启动均加 `--no-access-log`。既有 Nginx `$uri` 契约保持不变。
- `tests/test_video_route.py` 17/17，Nginx/Graph 日志契约脚本、`docker compose config -q`、Uvicorn CLI 参数检查、定向 Ruff 与 `git diff --check` 通过。测试使用模拟 B 站响应；无外部视频或模型调用。
- 重建仅本地 Graph 容器以应用运行命令配置；`/healthz` 加合成 query 标记返回 HTTP 200，容器命令带 `--no-access-log`，两分钟容器日志中未发现标记。没有重启其他服务。

## 2026-10-16 — Maven 端口错误只读诊断

- 未重跑 Maven。确认 Surefire 没有并行配置；Docker 错误端口（46413–46453、46474）处于当前 Linux 本地端口分配范围 44620–48715。诊断时该范围有 44 个 socket 状态，但失败端口均无监听者；无法回溯失败发生时的具体占用进程。
- 将 Testcontainers `address already in use` 保留为未解决的环境错误，不据此修改业务代码或声称全量测试通过，也不继续重复碰运气重跑。

## 2026-10-16 — KC-011 与主线地图窄屏续验

- 只读核对共享审核库元数据：KC-011 已批准题为 chapter 2 / stage 1 / kind `NULL` / difficulty 1；对应主线 stage ID 5，第二章解锁检查位于 `MainlineService.stageDetail`。新学生没有第二章资格，本轮没有直访受限练习、提交题目或更改学习记录，也没有调用 DeepSeek。
- 真实 Chromium 学生注册后打开 `/mainline-map`：320px 下地图页面及章节列表正常，`documentElement.scrollWidth` 和 `body.scrollWidth` 均为 320、页面错误 0、API 只有认证、刷新与主线 overview 读取。关闭章节详情后触摸 Phaser 画布（96, 550）选中第 3 章并重新打开详情；画布触屏链路通过，页面宽度仍为 320。模型请求 0。
- 首次误点的是默认视觉隐藏的 `.map-nodes-a11y` 镜像列表，被画布/抽屉拦截；这是错误测试入口，不是触屏故障，未计入通过数。临时学生已删除并确认用户名残留为 0。没有代码改动或模型请求。

## 2026-10-16 — 收敛 Web 输入为文本与图片

- 全仓确认 `useVoiceInput`、`useAudioRecorder` 和 `transcribeAudio` 没有活跃 Web 调用点；移除闲置浏览器 SpeechRecognition/MediaRecorder 实现、ASR 上传 helper 和只覆盖这些代码的测试。后端 ASR endpoint 保持硬关闭，以兼容既有非 Web API 合同。
- Provider 只读审计确认本机 `.env` 与运行 Graph 均使用 DeepSeek provider、host 和 allowlisted 模型；未读取或输出密钥。本轮没有重复 DeepSeek 调用。
- 验收：ChatComposer 无语音控件回归 14/14、前端 typecheck、`media.ts` 与该组件测试的定向 ESLint、残留引用扫描和 diff 检查通过。DeepSeek 文本/图片、后端 ASR 和非 Web 客户端行为未重测或修改。

## 2026-10-16 — 远征真实验收资格只读复核

- 只读 Postgres 聚合覆盖 `focusChapter=null` 的后期挑战 run：11 个活跃的第 2 章以上挑战节点中，先修 KC 有真实通过事件且达到门槛的节点为 0；7 个账号当前题为未结算 `CONSUMED`，另 4 个账号题为 `READY`。账号身份未知，不能作为 QA 学情样本或消费 READY 题。
- 审核共享库第 2 章以上 `dual_track`/`closed_loop` 题数量为 0。活动 run 页面刷新只读服务端快照；“继续”会启动后台预取，可能调用 DeepSeek。本次只读检查未操作业务数据或调用模型，等待已确认 QA 学生经正常学习达到先修门槛。

## 2026-10-16 — 敏感字段日志窄扫描

- 对 Java、Graph、RAG 的单行日志语句按凭证、查询、提示词、内容、payload、文件名和用户标识做源码搜索。唯一 Java 候选是旧讯飞图片理解分支的数值 userId 日志；`ImageUnderstandingProperties.isEnabled()` 固定返回 false，入口在抵达该日志前即返回，因此当前运行路径不可达。Graph/RAG 此窄扫描无命中。
- 没有代码改动或测试；该同一行搜索不能覆盖跨行 logger 调用或封装日志，全仓日志审计仍未完成。没有模型请求或业务数据写入。

## 2026-10-16 — 本地 Piston 沙箱隔离复验

- 运行现有 `node deploy/scripts/test_dev_piston_sandbox.mjs`，Python 3.12.0 冒烟通过，并验证宿主文件不可见、每个 job 的临时目录隔离、共享 `/tmp` 不可写、宿主 Piston loopback 不可达、x86_64 兼容 socket syscall 被拦截及超时作业 `SIGKILL`。
- 这只覆盖本地开发容器，不证明对任意公网目标的网络 ACL 或生产 Piston 鉴权。本次没有外网请求、DeepSeek 请求或业务数据写入。

## 2026-10-16 — 教师班级卡片窄屏布局

- `npx playwright test e2e/teacherResponsive.spec.ts --project=chromium --retries=0`：320 CSS px 视口下教师班级卡片可见，document 宽度不超过 viewport，1/1 通过。
- 此测试拦截认证刷新与班级列表 API，仅验证布局，不作为真实教师账号/班级动作证据。无模型调用和业务数据写入。

## 2026-10-16 — 远征通用题库兜底题型校验

- 新回归先 RED：Graph 不可用且存在普通课程编程题时，`debug` 请求仍会收到该题；前端会按样例输入合成错误代码，无法保证符合原题意。
- 服务端现在只允许无题型元数据的课程题用于无指定题型或 `dual_track` 请求。专用题型仍优先复用同题型的审核题/预生成题或走 Graph；无兼容题时返回可重试服务错误，不创建错误题目记录。
- `cd backend && mvn -o -Dtest=BossServiceTest test`：55/55 通过。此前第一次整类运行因新测试的严格 Mockito stub 未被安全过滤分支使用而报 1 个测试错误；将仅供描述的 fixture 改为 lenient 后重跑通过。未调用 DeepSeek、未写学习数据；真实账号远征刷新恢复仍待验。

## 2026-10-16 — 通用 API 限流覆盖初盘

- Nginx 已为登录/注册/刷新、班级加入码、上传和下载计数配置路由限流；AI 入口有账号日预算，正式判题有 10 秒用户冷却。
- `POST /api/learning/events/batch` 最多接收 100 条，但 payload 未设单条大小；`POST /api/learner/evidence-memory/snapshots` 每次持久化一行派生 JSONB，源码中未发现调用方或清理任务。两者都是已认证写入口，是否加细粒度约束须先结合真实调用/保留意图判断。
- 本项为静态盘点，没有改代码、跑测试、写数据库或调用模型；通用非 AI API 限流审计继续开放，不做全局 IP 闸门。

## 2026-10-16 当前续接：学生追加写入边界与 Stage 站内导航验收

- 客户端只允许上报 `quiz_finished` / `answer_feedback`；学习事件单条/批次与工坊聊天历史追加写现按学生账号共用滚动 60 秒 100 条额度，超额在持久化前返回 429，并自动清理过期窗口。限额为单 Spring 实例内存态，扩容前需共享计数器。工坊历史 `responsePayload` 限制为 64 KiB，超限返回 413。
- 回归先 RED 后 GREEN：`LearningEventControllerTest` 6/6、`StudentWriteThrottleTest` 2/2、`WorkshopChatHistoryServiceTest` 5/5；触及 Java 文件 Spotless 检查通过。
- 真实 Chromium 从小镇“下一步”与地图章节入口都进入 `/mainline/stage/1`，详情、视频与课件 GET 正常，页面/控制台错误 0、模型请求 0。首轮受首次探针遮挡，正确预置已回答状态后确认是测试入口问题，没有更改路由代码。临时 QA 用户 ID 728 删除并验证不存在；该用户学习事件与判题提交均为 0，临时凭据文件已清除。
- 未完成仍见唯一清单：全 API 非 AI 安全/限流审计、远征真实先修战斗及刷新恢复、剩余 Web 动作和 Boss 窄屏、Twin A/B 与 7 日样本、工程整理；性能最后测。本轮没有 DeepSeek 调用。

## 2026-10-16 — 画像自评空更新与写入限频

- 静态审计发现 `PUT /api/learner/profile/dimensions/{dimensionKey}` 接受 `scoreDelta: 0` 且无标签的请求；每次都会更新画像时间并追加永久 `profile_revision`。有效自评此前也未占用账号写入预算。
- 现将零分差且无标签判为空更新，并在角色/标签验证通过后复用 `StudentWriteThrottle`；预算耗尽时返回 429，画像服务不执行。
- `mvn -o -Dtest=ProfileControllerTest,SelfDimensionRequestValidationTest test`：7/7 通过；触及 3 个 Java 文件的定向 Spotless 与 `git diff --check` 通过。没有模型调用或业务数据库写入。

## 2026-10-16 — 指南页分页真实浏览器验收

- 使用本地 Vite 与 Chromium，从 `/guide?topic=town-basics` 点击「下一篇」再点「上一篇」；主题查询参数、正文主题与返回顶部行为正确，页面运行错误 0。
- 认证刷新和 Companion GET 使用测试路由模拟，所有 `/api/` 均被拦截；没有访问后端、模型调用或业务写入。仅闭合指南分页这一动作项。
- 第一次脚本未进应用页面：Playwright glob 错将 Vite 的 `/src/api/*.ts` 模块当成业务 API 并回 JSON。改用只匹配 origin 根 `/api/` 的路由后复验通过；这是测试脚本范围问题，没有修改产品代码。

## 2026-10-16 — 非 AI 写入口与 Boss 列表边界续审

- `/api/feynman/sessions` 过去可无限新增记录且 `kcCode` 无长度限制；现占用每账号每分钟 100 单位学生写入预算，并限制输入为 64 字符。`/api/learner/curriculum/bootstrap` 保留兼容路由，但现限学生并复用同一写入预算；检查未发现当前 Web 调用 `bootstrapField()`。
- `/api/world/boss/select-frontier` 的 `recentKcs` 和 `exclude` 各限制为最多 65 个规范 KC 编码。Boss 出题请求中的 `weakKcCodes`、`targetKcCodes`、`mediaPriority` 也限制列表大小和白名单，拒绝超量或提示注入字符串后才调用服务。
- 红测确认费曼输入/预算、课程画像角色/预算及 Boss 列表边界均可绕过；修复后 `FeynmanControllerSecurityTest` + `FeynmanStartRequestValidationTest` 8/8、`CurriculumControllerSecurityTest` 3/3、`BossControllerTest` 16/16 通过；触及 Java 文件定向 Spotless 与 `git diff --check` 通过。没有 DeepSeek 请求、真实数据库写入或外部服务访问。
- 剩余风险：账号写入预算为单 Spring 实例内存态；工坊聊天历史仍无生命周期保留上限；证据快照已接入账号预算；`PATCH /api/world/player` 可重复更新本人位置，需结合正常移动调用频率评估。还有非 AI 写路由与完整日志使用未逐项审完。性能项 `MasteryService` N+1 和证据图重复构建按计划留到功能/安全收尾后；不删除学习历史，以免破坏学生复盘。

## 2026-10-16 — 记录下一项 Web 动作验收

- 清单与页面源码对照后，下一项选教师班级看板「主线进度」页签：只读加载成员×章节进度，包含空态及请求失败重试。
- 现有记录中的 QA 教师班级已清理；本轮前端 `:5173` 未运行，后端 `/readyz` 返回 503。未注册账号、创建班级、打开浏览器或写入数据；待本地依赖可用且有获授权 QA 班级时验收。无模型调用。

## 2026-10-16 — 收紧证据快照与学习探针写入频率

- `/api/learner/evidence-memory/snapshots` 与 `/api/learner/probe` 现在复用每账号每分钟 100 单位的共享写入额度，超限在构建证据图或发布事件前返回 429。探针只更新自述媒介偏好、不增加能力证据；重复事件仍可能在额度内发生。
- 两条控制器回归均先 RED 后 GREEN；与现有 `PassiveProfileProbeTest` 合并为 5/5 通过，触及 Java 文件定向 Spotless 与 `git diff --check` 通过。本轮没有 DeepSeek 请求或业务数据写入。
- 非 AI 写路由和多行日志审计仍未完成；账号额度是单 Spring 实例内存态。

## 2026-10-16 — 远征状态写入口额度

- 无尽远征开局会读取服务端学习前沿并新建 run，玩家 typed action 会变更服务器权威进度；两条写路由过去未使用共享账号写预算。现在各占每账号每分钟 100 单位预算中的 1 单位，超额在服务/前沿选择前拒绝。
- 快照 PUT 只校验既有 run 并返回服务器状态；Boss 判题另有 10 秒账号级限频和 attemptId 幂等；两者保留既有保护，不叠加限流。`./dev.sh` 设置 `PYT_AI_REQUEST_LIMITS_ENABLED=false`，本地 DeepSeek 请求不受日额度限制。
- 新回归在旧代码下先 RED（超额开局未触发拒绝、超额操作仍 200），修复后 `ExpeditionRunControllerTest` 13/13 通过。没有模型调用或业务数据写入；`git diff --check` 通过。

## 2026-10-16 — 非 AI 写路由续查

- 只读筛查确认班级加入码使用账号级专用节流，下载计数由公网 Nginx 按来源 IP 限速，Tutor 内部 checkpoint 校验服务令牌并绑定用户，TutorBench 是学生可触发的确定性评估。媒体作业授权与 provider 关闭状态沿用已记录的验证。
- 玩家坐标写入只更新当前账号自己的 town_hub 位置，Web store 把连续移动合并至最多约每 500ms 一次；现有共享 100 次/分额度会误伤正常连续移动，因此不在这里套用。它仍需基于吞吐数据决定专用服务端上限。未运行测试、没有模型请求或业务数据写入。

## 2026-10-16 — 续接本地沙箱 fail-closed 与教师看板验收

- Piston `nosocket.c` 原先忽略 `seccomp_load()` 失败并继续执行目标程序。新增 linker-wrapper 回归，在构建时强制加载失败并断言 `execvp()` 不被调用；旧实现先 RED，新镜像构建通过。重建后运行一次 `node deploy/scripts/test_dev_piston_sandbox.mjs`，本地执行、文件/临时目录、loopback 与超时烟测通过。
- 兼容 `socketcall` 探针返回负值，但原脚本不显示 errno，无法确认是自定义 `nosocket` 还是 Docker seccomp 阻断；不把它作为过滤器或生产网络隔离证明。每 job namespace、生产 Piston ACL/鉴权仍未核验。
- 真实 Chromium 教师页点击“主线进度”并显示空班级状态；后端 API HTTP 200、members 0、页面错误 0、模型请求 0。临时教师 ID 730 与班级 ID 37 已按精确 ID 删除并确认无残留。
- `StaticCodeChecker` 在调用线程取消时现在强杀并等待 AST 检查子进程结束；超时/异常清理也共用该处理。新回归用可记录 PID 的慢速测试解释器验证中断后子进程退出，旧实现 RED、修复后 `StaticCodeCheckerTest` 16/16 通过。
- 本轮没有 DeepSeek 请求。继续项仍以 `docs/plans/2026-10-15-web-system-implementation-plan.md` 的唯一清单为准：非 AI 写入口/多行日志审计、远征真实先修战斗与刷新恢复、Web 动作矩阵、Twin 真实样本、工程整理；性能最后。

## 2026-10-16 — 限制判题任务排队时间

- 共享 `JudgeWorkerPool` 过去只限制队列容量；任务入队后调用线程可能无限等待。现在最多等 10 秒进入工作线程，超时取消并移除排队任务，复用现有 `JUDGE_CAPACITY`/503 响应。
- 新回归在旧代码中因 12 秒等待超时而失败；修复后 `JudgeWorkerPoolTest` 2/2 通过，覆盖容量饱和与过期任务移除。触及的 Java 文件定向 Spotless 与 `git diff --check` 通过，没有模型请求。

## 2026-10-16 — 收紧主线学习接口角色

- 主线学习、客观题、章节测验和小测控制器面向学生；其中主线与测验的部分接口没有角色限制，教师 JWT 可进入本人名下的学习/考试流程。四组控制器现统一要求 `STUDENT`。
- 教师角色调用回归覆盖四类入口，先 RED 后 GREEN，`MainlineControllerAuthorizationTest` 4/4；触及文件定向 Spotless 与 `git diff --check` 通过。无模型请求。
- 同域只读复核确认 learner evaluation/events/path/profile interview/quest 已有学生角色检查，班级写入在服务层校验角色与归属；此项未运行测试。

## 2026-10-16 — 校正定向格式检查范围

- 复核发现先前传入 Spotless 的相对路径筛选未命中目标文件。改用绝对路径正则，仅格式化本轮 7 个 Java 文件；随后精准 `spotless:check` 7/7 clean，`JudgeWorkerPoolTest` + `MainlineControllerAuthorizationTest` 合跑 6/6，`git diff --check` 通过。

## 2026-10-16 — 限制 NPC 对话存档累计增长

- `POST /api/world/npcs/{npcKey}/talk` 原先没有账号写入预算；客户端可通过重复提交不同 node ID 和 `SET_FLAG` 键，持续扩大该账号的 `npc_dialog_state` 数组与 JSONB。现复用每账号每分钟 100 单位写预算，并限制单 NPC 最多 128 个已完成节点、64 个 flags。
- 更新在读取当前行时获取 `FOR UPDATE` 锁，避免并发请求同时通过累计上限检查；超限请求返回 `NPC_DIALOG_STATE_LIMIT_REACHED`，不会写节点或 flags。每个已支持 NPC 的课程数据实际最多 5 个节点，设定上限保留了充足扩展空间。
- 回归先 RED 后 GREEN：控制器与世界 NPC 服务 12/12，通过的 Maven 命令也发现同名 memory NPC service 的 2 项测试，共计 14/14；触及 Java 文件定向 Spotless 5/5 clean。没有 DeepSeek 调用或业务数据库写入。未做 PostgreSQL 并发集成验证；预算计数仍为单实例内存态。

## 2026-10-16 — 修正语音关闭后的 multipart 注释

- 只读 provider 审查确认前端语音入口已移除，后端 ASR/TTS 硬关闭；`application.yml` 仍把通用 multipart 上限解释为 ASR 录音回退。现改为说明这是传输上限，端点仍执行更严格的文件/内容校验；配置值不变。
- 静态审查未读取密钥、未请求模型；本次只改注释，`git diff --check` 通过。provider 路由结论不替代运行实例环境验证。

## 2026-10-16 — R-14 旧种植任务回到主线

- Graph 路径规划不再为锁定 KC 生成 `PLANT_KC`；没有已解锁 KC 时指引用户从主线地图继续。历史 `PLANT_KC` enum、数据库约束和行数据保留；路径任务的标题、路由、前端个人/班级深链指向 `/mainline-map`，不再带旧农场 `focus` 参数。
- 删除已无调用方的 `LearningPathService.ensureDefaultsForNewStudent()` 及其中的种植默认任务。只读本地计数确认旧 quest/path 有少量 `PLANT_KC` 行；没有更新、迁移或删除用户数据。课程 bootstrap 兼容 API 与映射服务暂留，Web 当前没有调用方。
- 新增旧节点后端路径回归；首跑复现标题仍为“解锁 KC”，修复实际任务标题映射后 `LearningPathServiceTest` 通过。Graph path planner 与 evaluation narrative 定向测试 23/23、Ruff 通过；前端深链/路径/热力图三个 spec 13/13 通过，typecheck、定向 ESLint 与 diff 检查已通过。
- 教师「主线进度」失败重试另经 Chromium 用 API 拦截响应检查：首次 503 呈现错误与重试入口，重试后显示空态；11 个请求均为 mock，AI 请求 0、页面错误 0。真实后端 503 注入未做。无 DeepSeek 请求。

## 2026-10-16 — 存档槽写入纳入账号额度

- `POST /api/world/saves/{slotIndex}`、`/activate` 与 `/snapshot` 原先未占用现有账号级写入预算。现在建槽/切换各占 1 单位；受 262144 字符 JSON 上限约束的快照写入占 10 单位，超限请求统一 429，且在进入 service/数据库前拒绝。
- `SaveSlotControllerTest` 先 RED（旧代码仍返回成功），修改后覆盖三个写入口共 3 个拒绝分支，确认服务零调用。只读调用方检查发现 Web 只有未被页面调用的 API helper；Windows 仅用列表/激活，Android 文档标为未接线。没有执行存档写入、没有模型请求。预算与既有守卫相同，为单实例内存态。

## 2026-10-16 — 日历事件更新纳入账号额度

- `POST /api/world/calendar/events/{eventId}/process` 过去没有写入频率限制，虽然 SQL 按 `(eventId, userId)` 约束本人事件且重复处理幂等，仍会重复执行数据库 UPDATE。现每次处理先占用账号共享预算 1 单位，超限 429 在服务层前返回。
- `CalendarControllerTest` 先 RED 后 GREEN，确认拒绝状态、守卫被调用且服务零交互；`SaveSlotControllerTest` 与日历控制器回归合跑 2/2 通过。没有事件写入或模型请求。账户窗口是单实例内存态。

## 2026-10-16 — 公告板任务写口限流与状态竞态保护

- Quest `start`/`complete`/`skip` 原先没有共享账号写入预算；现在每次各占 1 单位，教师角色仍在预算前拒绝。`start` 的 SQL 状态条件收紧为仅 `OPEN`，并检查受影响行数；`skip` 也检查条件更新结果，另一请求已终结时返回 409、不会追加进度。完成路径原有证据验证与并发 CAS 保持不变。
- `QuestControllerTest`、`QuestServiceTest`、`QuestRepositoryStatusGuardTest` 合计 13/13 通过；旧代码下限流回归和 start/skip 并发哨兵均先 RED。没有业务数据库写入或模型请求。完整 API 限流审计仍开放。

## 2026-10-16 — 技能解锁纳入账号额度

- 活跃 Web HUD 的 `POST /api/world/skills/{skillKey}/unlock` 原先只做掌握度门槛与幂等检查，缺少请求预算；现每次先占用共享预算 1 单位，超限在技能服务和掌握度查询前返回 429。
- `SkillControllerTest` 先 RED 后 GREEN，确认守卫被调用且超限时 service 零交互。没有技能写入或模型请求。计数窗口仍为单实例内存态。

## 2026-10-16 — 主线学习写口纳入账号额度

- 主线单页/批量阅读、例题完成、远征补星、错题补星和客观题作答过去未占共享预算；现各占 1 单位，额度不足返回 429 且不进入服务。视频进度保留现有按 user/stage 的专用节流。
- 扩展 `MainlineControllerAuthorizationTest` 覆盖五类进度/补星写路由及客观题作答的预算拒绝，同时确认教师被方法安全拒绝时不消耗额度。与 `MainlineServiceTest`、`MainlinePracticeServiceTest` 合计 46/46；没有数据库业务写入或模型请求。共享预算为单实例内存态。

## 2026-10-16 — 玩家位置写口独立限速

- Web store 将位置 PATCH 合并到约每 500ms 一次；服务端现对每个账号设置独立的 1 秒 10 次滑动窗口，超限请求在 `WorldService` 前返回 429，不消耗普通学习写入额度。
- 回归先复现旧路由第 31 次仍返回 200，确定限额后验证第 11 次被拒：`WorldControllerTest` + `StudentWriteThrottleTest` 4/4 通过；触及 Java 文件定向 Spotless 与 `git diff --check` 通过。无模型请求或业务数据写入。
- 账号计数器仍是单 Spring 实例内存态。全 API 非 AI 写路由/日志审计、真实远征资格与恢复、剩余 Web 动作、Twin 真实样本和工程收尾仍按唯一清单推进；性能最后测。

## 2026-10-16 — 限制导学工作流反馈写入

- `/api/tutor/workflow/sessions/{sessionKey}/feedback` 过去可无限追加事件行，且允许 Map payload 任意大。现复用每账号每分钟 100 次共享写额度，并在入库前把序列化事件限制为 4096 字节；超限分别返回 429/413，不写 action event，也不发布学习事件。
- 回归在旧代码下分别复现第 101 次仍被调用和 5000 字节 payload 仍被写入；修复后 `TutorWorkflowControllerTest` + `TutorWorkflowServiceTest` 38/38 通过，`git diff --check` 通过。定向 Spotless 检查命中触及文件既有的大段格式漂移，未重排无关代码。无模型调用或业务数据写入。
- 持久化反馈事件仍无生命周期清理；账号额度依赖单 Spring 实例内存态。非 AI 写接口/日志审计、真实远征闭环、其余 Web 动作、Twin 样本、工程整理仍在唯一清单中；性能最后测。
