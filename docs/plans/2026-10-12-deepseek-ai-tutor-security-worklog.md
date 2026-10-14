# DeepSeek 导学安全与数据边界工作日志

日期：2026-10-12 至 2026-10-14

## 2026-10-14 增量：在线模型出口与语音禁用复核

- 运行态 Graph provider 摘要为 `OpenAiCompatProvider`、HTTPS `api.deepseek.com`；Spark key 缺失，fine-tuned provider 关闭。provider 工厂对非 DeepSeek 地址/模型 fail-fast；研究实验室在线 client 有同样的 DeepSeek host/model allowlist，RAG 仅使用本地 hash 或本地 BGE embedding。
- TTS、ASR、讯飞文生图和讯飞图片理解的 properties 均固定 `isEnabled() == false`；17 个 Java 回归覆盖凭据非空仍不可用、媒体路由与预算分类。Web Speech/录音 composable 当前没有业务调用方。
- 经项目 provider 发起两次真实请求：文本精确回显和合成 PNG 颜色识别都成功并返回文本；`LlmResponse` 只含文本/JSON/tool-call 字段，没有图片字段。provider、研究实验室、RAG 相关定向测试共 91 项通过，另有 Graph 既有 pytest 标记警告。
- 复查 Tutor Markdown 外链图片风险：共享 `MarkdownView` 已删除跨域 `<img>` 并显示 alt 文本；现有页面回归隔离复跑 6 项通过。
- 未检查供应商 token 和账单；真实请求仅证明当前调用路径，不用于推算费用。

## 2026-10-14 补充：主线补星并发检查

- 检查 `MainlineRecoveryService`、`ChapterStarService` 的全部调用方后，确认用户/章节的每日远征补星检查与星级写入之间存在 TOCTOU 竞态：同一日两个并发请求都可能通过检查并写入重复审计事件。错题补星与核心星级重算也会争用同一份 JSON breakdown。
- 为用户/章节复用命名空间化的 PostgreSQL 事务 advisory lock；补星入口在资格检查前加锁，星级重算在读取已有 breakdown 前加同一把锁。没有增加迁移。
- 新 PostgreSQL 并发测试先在旧实现下复现双重发放，再通过断言只发放一次且只留一条事件；相关 14 项 Maven 定向测试通过。
- 未发起 DeepSeek 请求；项目当前的 DeepSeek 接口契约仍是接收图片并输出文本，不接入图片生成。

## 2026-10-14 补充：登录限流残余

- 入口目前仅有 Nginx 基于客户端 IP 的 5 次/秒、突发 10 次限速；多 IP 来源仍可对同一账号尝试口令。
- 本轮未加入账号锁定或进程内计数：前者可能被用于锁死目标账号，后者不能在多实例间提供可信限流。若要关闭此风险，需要共享状态的失败计数、渐进退避和安全恢复方案。

## 2026-10-14 增量：本地 Piston 最小权限

- 固定 Piston API v3.1.0 不启动 Isolate/cgroup；实际用低权 uid、`prlimit` 和 `nosocket` 执行任务。临时 Docker 探针以只读根文件系统、`no-new-privileges`、五项必要 capabilities、768 MiB/1 CPU/128 PID 上限及 256 MiB 临时目录运行。
- 探针确认 `print(42)` 成功，读取 `/etc/shadow` 与创建 socket 被拒，500 ms 超时被杀后 API 继续正常，超过 256 MiB 的运行内存参数被服务端拒绝。无 `CAP_SYS_ADMIN`。
- 本地 Compose 与安全契约同步收紧；当前 API 实测无 Authorization 与伪造 Bearer 的 runtimes 请求均为 HTTP 200。生产部署文档注明 Piston API v3.1.0 的 Bearer token 不生效，必须确认部署端/代理实际鉴权并限制私网访问。
- 未验证生产 Piston 的容器/内核隔离和逃逸；本地测试不能替代生产审计。

## 2026-10-14 增量：学习事件客户端信任边界

- `/api/events` 此前允许学生自选事件类型、`source` 和 `importance`，可把虚假的 `practice_failed` 作为高重要度 `graph` 事件写入学习记忆。现在与 `/api/learning/events` 共用客户端事件白名单，只允许 `quiz_finished` 和 `answer_feedback`；来源固定为 `frontend`，importance 由服务器计算。
- RED/GREEN：先增加伪造正式判题事件的控制器测试，旧实现返回 201；限制后拒绝请求且不触达服务。控制器/服务、JWT 角色刷新共 30 个定向测试通过；`LearningEventControllerTest` 3 项与真 PostgreSQL + Flyway `LoopRunServiceTest` 14 项通过，后者空库迁移至 V212，并验证持久化 source/importance。
- 服务器判题结果仍由正式提交业务链生成；本组事件策略改动没有触发 DeepSeek 请求。Piston 探针、浏览器联测与性能状态见同日其他记录。

## 2026-10-14 增量：刷新 token 角色一致性

- `AuthService.refresh` 之前未比较 JWT 中的角色与用户表当前角色；账号角色调整后，过滤器拒绝旧 token，但刷新会用旧 token 换发新角色 token。现在角色不匹配和 tokenVersion 失配一样返回 `REFRESH_TOKEN_INVALID`。
- TDD 回归在旧实现下失败（刷新尝试签发），修复后 `AuthServiceSecurityTest`、`JwtAuthenticationFilterTest` 定向运行通过。

## 2026-10-14 Piston signal-only 响应与当前全层复验

- 在本地受限 Piston 中运行 `uid/euid`、读取 `/etc/shadow`、创建 socket 和短超时样例：程序 uid/euid 为 1001，敏感路径与 socket 均返回 `PermissionError`。超时样例实际只返回 `signal=SIGKILL`，没有 `status` 或 `code`。
- Java `PistonPythonSandbox` 原先因缺少 status/code 把该结果判为 SYSTEM_ERROR；先新增 RED 回归（期望退出码 137，原实现返回 -1），再增加 signal 到 Linux 退出码的适配。`PistonPythonSandboxTest` 与 `JudgeCaseEvaluatorTest` 共 15 项通过，SIGKILL 继续由既有 evaluator 判为 TLE。
- 当前全量：Graph `1818 passed, 1 skipped, 13 warnings`；RAG `158 passed, 2 skipped`；Web `264 files, 2521 tests passed`，typecheck/build/管理端改动文件 ESLint 通过。
- 全量 Maven `1354 tests, 0 failures, 7 errors`。6 个 Testcontainers 测试容器因 Docker 随机宿主端口绑定冲突启动失败；另 `LongTermClassroomEffectSimulationTest` JDBC 被拒绝。Piston 定向 15 项已通过，未将全套 Maven 失败描述为代码通过。
- 尚未验证生产 Piston 内核隔离或逃逸、认证后学生/教师逐页 Playwright、ECS/公网、真实硬件及全仓静态类型/格式债务；本地探针只验证所列边界。

## 2026-10-14 Graph 沙箱 signal-only 结果

- 固定 Piston v3.1.0 在超时后仅返回 `signal=SIGKILL`；Graph 执行适配器此前把缺少 `code/status` 当成沙箱不可用，导致现有超时判定未生效。现在映射 Linux signal-only 退出码，SIGKILL/SIGXCPU 分别沿用退出码 137/152。
- RED/GREEN：新增 signal-only 回归后 Graph 沙箱、评估、自测定向套件共 37 项通过。保留升级 Piston 后区分超时与内存终止元数据的 shortcut。

## 2026-10-14 开发栈退出与 Web 角色浏览器联测

- 先在实际 `./dev.sh --sandbox` 启停中复现退出缺陷：PID 文件记录 874886，Maven/Spring Boot 运行于进程组 874888；Ctrl+C 后 :18080 仍健康。最小修复是后台 shell `exec setsid mvn`，不再让 PID 文件指向额外包装 shell。
- RED 断言确认文件 PID 与实际进程组不一致；GREEN 实测 PID/PGID 均为 888297，Ctrl+C 后进程组消失、健康端口关闭且 PID 文件删除。第二次修复后在普通开发栈重复启动/退出，PID/PGID 为 890991，退出清理再次通过。
- 真实 Chromium：`accountIsolation.spec.ts` 1/1；`teacherStudentFullFlow.spec.ts` 1/1（创建班级、发布班级任务、学生加入、Piston Python 3.12 正式判题、提交完成证据、教师进度同步）；管理端 3 个路由以真实 ADMIN 登录渲染，用户筛选和审核队列搜索可操作。冒烟仅读审核队列，没有发布或停用用户。
- 学习分身实页对新用户显示“还没有经判题确认的学习证据”，打开周报折叠并导航至详细画像均成功，无 pageerror。此浏览器轮未发 DeepSeek 请求。
- 本轮复验：前端 Vitest 全量退出码 0，`typecheck`、build、改动管理页 ESLint 通过；Graph pytest 1818 passed/1 skipped、RAG pytest 158 passed/2 skipped、两侧 Ruff 通过；判题/Boss/AI 额度 Maven 定向 60 tests 与两个 Piston Java 文件 Spotless check 通过。完整 Maven 历史运行仍为 7 个 Docker/JDBC 环境错误，未描述为全套通过。
- 本地数据库留下本次创建的测试账号与教师闭环课堂/任务记录；临时 ADMIN 已降回 STUDENT。尚未完成所有学生/教师页面矩阵、生产 Piston 内核逃逸、ECS/公网和全仓类型/格式债务验证。

## 2026-10-13 Tutor 断线重试幂等与多图部分失败恢复

- 每个新的 Tutor Web 动作生成 `requestId`，同一次失败重试复用该 ID。后端把已完成的精简文本结果随 V208 投影保存；重放返回原终态 SSE，不再次调用 Graph/DeepSeek。相同 ID 携带不同内容时返回 409；FAILED run 清除 checkpoint 后可按原 ID 重试。受限结果不包含上传图片字节。
- 多图中有上传成功项、失败项时缓存成功项的 attachment ID；重新提交只上传失败项，且上传过程中禁止删除待处理图片，避免附件额度重复消耗。
- RED/GREEN 与复验：`TutorWorkflowServiceTest` 30 项、Testcontainers `TutorWorkflowRepositoryIntegrationTest` 9 项、`TutorWorkflowControllerTest` 2 项通过，空库迁移至 V208；Web 全量 255 个测试文件 / 2516 项通过，`npm run typecheck` 与 `npm run build` 通过。`./dev.sh` 启动成功；Playwright 确认根页 HTTP 200、未登录后台正确转登录页、无浏览器异常。登录页 8 秒未到 `networkidle`，当时仍有 `/healthz` 和字体请求；DOM 已渲染且没有失败请求。本轮没有真实模型请求。
- ESLint 改动文件检查 0 errors，Tutor 面板仍有 51 项 Vue 格式 warnings。全仓 Maven Spotless 受 606+ 个其他文件的既有格式违规阻断，当前 Tutor Java 文件不在报告清单中。
- 本轮未启动全栈或发起 DeepSeek 请求；无尽远征性能仍待功能复核结束后测试。此前在对话中出现的 DeepSeek 凭证应轮换后再用于真实请求。

## 2026-10-13 远征服务端动作状态与 Web 续局

- Web 的节点进入、事件选择、遗物领奖、正式判题结算与主动结束均改走有类型的服务端 action；旧 `PUT` 只校验快照并返回 canonical state。Boss 题目与 run/node 绑定，结算只采用本人已签发题目和正式判题账本，不接受客户端分数、HP、路线或通关字段。
- 续局恢复挑战/事件时回到地图并允许重进服务端当前节点；领奖阶段保留并重建选项；结算请求失败时可用同一题重试。修复旧 `current` 节点无法继续、满遗物精英奖励阶段卡住、韧性扣血方向反转，以及 `second_wind` 未生效。
- V204 新增题目节点绑定和结算记录。迁移只废弃不能安全绑定节点的旧远征 PENDING/READY 题；保留活动局以避免破坏性重置，但旧 PUT 写入的节点状态、HP、气势、遗物和历史无法追溯可信。Android/Windows 未改接 Web 新开局/action 契约。
- 验证：后端定向 `mvn -Dtest=ExpeditionRunServiceTest,ExpeditionRunControllerTest,ExpeditionRunPostgresIntegrationTest,EndlessPregenServiceTest,BossServiceTest,BossControllerTest test`，85 tests 通过，Testcontainers 空库迁移至 V204；Web 全量 254 files / 2500 tests、`npm run typecheck`、`npm run build`、改动文件 ESLint 通过。全仓 `mvn spotless:check` 被其他 Java 文件的格式违规阻断，本次远征 Java 文件未列在违规清单中。
- 尚未完成：Boss 新阶段重复生成相同题目指纹会被 run 级防重放挡住；服务端 daily 一日一次与排行榜 daily 标记尚未接回。局内游戏积分加成仅在 Web 本地会话显示，不进入正式判题 ledger 或排行榜。按“功能优先、性能最后”未启动全栈、未发 DeepSeek 请求、未运行 Piston 或远征性能测试。

## 2026-10-13 远征服务端开局与 Web 接线

- Web 新局改走 `POST /api/world/expedition/run`：后端读取权威学习前沿、生成兼容现有 Roguelike schema 的 10 层可达地图并初始化 HP/气势/遗物/历史；前端 store 只接收服务端快照。`PUT` 不能再创建新 run 或改写已签发地图拓扑/题目配置，活动 run id 和章节绑定校验保留。
- API 契约补充 POST/PUT 语义；新增地图 DAG、前沿字段、角色/章节校验及 Web store/API 回归。后端定向 25 tests、前端定向 48 tests、TypeScript typecheck、定向 ESLint、8 个 Java 文件 Spotless 和 `git diff --check` 通过；PostgreSQL 集成 7 tests 通过，空库迁移到 V203。
- 当前客户端范围只接 Web：Android 与 Windows 仍调用旧 PUT 开局，现会收到 `EXPEDITION_RUN_START_REQUIRED`；未改这些客户端。其远征开局需后续接入 POST 才兼容。`PUT` 仍允许客户端改节点进度、当前位置、HP、气势、遗物和历史；这些字段不参与正式判题 ledger/成绩，但移动合法性与每日次数还未服务端验证。
- 按用户优先级未启动 Web 全栈、未请求 DeepSeek、未测远征性能。

## 2026-10-13 管理用户角色筛选

- 管理员用户列表原来只有文本搜索和账号状态筛选。新增角色下拉筛选，角色值沿用现有 `STUDENT` / `TEACHER` / `ADMIN`；Web API、ADMIN-only controller/service 与分页 repository 同步接收并应用角色条件，没有新增表或字段。
- RED/GREEN：后端服务测试先因新角色参数缺失编译失败；Web API 测试确认参数未发出、页面测试确认角色控件缺失。实现后管理员服务定向 Maven 测试通过；前端 API/页面 5 tests、TypeScript typecheck、定向 ESLint 通过；Graph Tutor/AutoGen 定向 36 tests 与 Ruff 通过；`git diff --check` 通过。
- 未启动 Web 全栈或真实 DeepSeek 请求；后续补充 PostgreSQL repository 集成测试，管理页端到端仍待联测。无尽远征性能照用户要求留待功能收尾后测。

## 2026-10-13 Tutor AutoGen 主链替换

- 常用 `/agents/tutor/ask/stream` 现在在既有 RAG 检索后，经固定顺序 AutoGen team 运行 `TutorAgent`、`grounding_critic`、`hallucination_detector`、`safety_critic`。回答阶段内保留原难题辩论和代码验证分支，SSE token 与终态字段不变；修订、图解、证据注、考考我出题/评卷及非流式 `TutorAgent.ask` 也经固定 handler 执行。
- 图片只由回答阶段闭包传给 DeepSeek vision；AutoGen 状态和 checkpoint 不包含图片字段或字节。关闭 SSE 时取消运行中的 team 与 provider stage。
- RED/GREEN：新增常用文本链路 AutoGen 固定阶段、图片不入状态且交给 vision、SSE 断连取消、非流式 `ask` 以及 quiz 出题/评卷路由测试；Tutor/AutoGen、修订和 orchestrator 定向 66 tests 通过，Graph 全量 `1801 passed, 1 skipped`，Ruff 全量通过。
- strict mypy 定向扫描仍报 3 个既有类型错误（TutorAgent 证据摘要返回值、Tutor 路由的 FastAPI state provider）；本次改动代码在修正泛型状态推断后没有新增 mypy 错误。
- 未启动 Web 全栈或发送真实 DeepSeek 请求；远征性能按用户顺序继续待测。

## 2026-10-13 功能优先补充

- 画像访谈发送失败时回滚乐观用户消息并保留输入；完成画像失败时显示错误并保持当前页面。首页班级列表加载失败显示错误和重试，不再误示为尚未入班；学习推荐卡使用原生按钮支持键盘操作。教师重置加入码期间禁用按钮，避免重复生成。画像屋删除 `[占位]` 并说明现有访谈功能。
- 判题 AST 预检改由共享有界工作池执行；线程池饱和拒绝为 503，避免 `CallerRunsPolicy` 在请求线程运行 Python。`ast_whitelist.py` 缺失时 fail closed，且 readiness 返回未就绪。
- RED/GREEN 与复验：前端定向 5 files / 19 tests、全量 253 files / 2492 tests、TypeScript typecheck、Vite production build 通过；ESLint 0 errors / 131 warnings。后端 StaticCodeChecker、JudgeWorkerPool、JudgeService、HealthController 定向 30 tests 通过；Graph 全量 1795 passed / 1 skipped，RAG 全量 158 passed / 2 skipped。`git diff --check` 通过。
- 按“功能优先、性能最后”未启动 Web 全栈、未发 DeepSeek 请求、未启动 Piston、未测无尽远征性能。全量 Maven、真实浏览器/模型联测仍未在本轮执行；Docker Testcontainers 主机端口冲突及真实 Piston 配置仍是后端验证限制。对话中出现过的 API key 应轮换后再用于生产。

## 2026-10-13 BROWSE DNS 重绑定修复

- Chromium 启动前按白名单主机解析并验证公网地址，使用 `--host-resolver-rules` 固定连接目标；域名规则按长度降序，避免宽域覆盖更具体的域。URL 层原有 scheme、端口、allowlist 和 DNS 公网检查继续保留。
- 回归先确认失败，再验证 Chromium 收到固定规则，且白名单解析为私网时不启动浏览器。Graph BROWSE executor、allowlist、route 定向测试 60 项通过，Ruff 通过。
- Graph 全量测试 `1795 passed, 1 skipped`；Web 全量 Vitest、`npm run typecheck` 和 `npm run build` 均通过。
- 未运行真实 Chromium 网络集成测试；固定映射将域名子域也指向已验证的父域 IP，遇到托管在不同 IP 的子域时可能无法访问，但不会回退到浏览器自行解析。
- Tutor 账号级请求额度需要避免擅自决定影响课堂和模型费用的日限额，等待用户选定策略；全栈和无尽远征性能仍按用户顺序留待后续。

## 2026-10-13 Web 安全和恢复补丁

- 私人媒体能力 URL 新增 24 小时过期值，并将资产 ID 与过期值一起纳入 HMAC；旧 `?k=` 仅在 `created_at + 24h` 内过渡兼容。Nginx 自定义 access log 只记录 `$uri`，不记录 query；release 安装和回滚同步管理 log format。已有超过 24 小时的资源 URL 需经现有 DTO/作业接口重新取新 URL。
- 上传解析添加 100 页 PDF 上限、PDF 临时文件流缓存、DOCX 解压累计 16 MiB / 最多 100 个 ZIP 条目，以及所有格式最多 100,000 个提取字符。超限仍落入既有 FAILED 状态。
- 运维管理页的长动作/Trace/相关页面增加完整 title；四个教师数据 Tab 在独立请求失败后可按原 loader 重试。
- Graph README 去掉已禁用的本地 Qwen fallback，改为文/图 DeepSeek 配置和 `deploy/.env` 说明。
- 验证：Maven 上传、媒体链接、无尽远征集成 5 类 / 42 tests 通过，Testcontainers 空库迁移到 V202；Web 5 个文件 / 21 tests、`npm run typecheck` 通过；Graph provider 41 tests 通过、1 条现有 async 标记 warning；Nginx 安全日志契约和 release/rollback shell 检查通过。
- 暂未关闭：Tutor 端点没有跨实例的账号级花费预算，策略待用户选择；浏览器 DNS 重绑定窗口已增加 Chromium 公网解析固定，但尚未做真实浏览器网络集成验证。对话中已展示的 DeepSeek key 应先轮换后再用于生产。无尽远征性能仍依照用户顺序留到最后。

## 2026-10-13 远征学情防重放与判题测试边界

- 同一远征题目的正式判题结果仍可从失败更新为通过，账本仍取通过状态与最高分；学情事件仅在第一次判题和第一次通过时发布。以不同 `attemptId` 重放已通过题目不会再产生 Mastery/Twin 事件。判定在活动 run 行锁内完成，避免并发重复发布。
- Graph Boss 测试夹具此前使用本机 Python 子进程执行提交代码。现改为按已知输入返回固定假结果，未知源码不执行；增加 marker 与 `subprocess.run` guard，golden 题库测试不再声称覆盖实际运行正确性。
- 验证：`mvn --settings .mvn/settings.build.xml -q -Dtest=ExpeditionScoreServiceTest,BossServiceTest,ExpeditionRunPostgresIntegrationTest test` 通过（3 类 / 43 项，Testcontainers 空库迁移到 V201）；Graph `tests/agents/boss` 209 passed，Ruff 通过；Admin shell/users Web 定向 10 tests 通过，`npm run typecheck` 通过。
- 未验证：没有启动 Piston、Web 全栈或 DeepSeek 请求。Fake Piston 回归只保证测试不执行学生提交，generated/golden 代码的真实执行正确性仍须真实隔离 Piston 集成覆盖。
- 未解决：节点拓扑、移动、HP、遗物和 phase 仍来自客户端快照；它们不进入官方成绩、通过题数或 `cleared` 奖励。要把这些局内字段升级为服务端权威状态仍需单独设计并覆盖完整地图动作流程。

## 2026-10-13 功能优先补丁

- 远征画像信号补上可信度达标的专业背景、学习目标/目标 KC、认知风格和每日学习时长；前端、Java 与 Graph DTO 对齐，Java 画像来源会覆盖已登录请求中的客户端画像值。目标 KC 只对 pack 43 有效且未掌握的点加权；自评偏好仅调整题面表达，难度仍由正式掌握度和练习记录主导。
- 画像页按 schema 的 `-1..1` 与 `0..1` 刻度显示。Graph 对 `preferredKind` 限定为现有 9 种题型；Java 请求体、嵌套画像和预取状态入口也校验白名单。RED 确认未知题型原来会进入 Graph 并返回题目，GREEN 后 Java 返回 400、Graph 返回 422。
- 出题场景提示从花田/收菜替换为中央小镇的图书馆、工坊、课堂和公告板，遵循 R-14 单 hub 设定。
- 验证：画像/Boss/控制器 3 个定向 Maven 测试类通过；Graph 远征选择、命题工厂和 Boss 路由 37 tests 通过；Web 画像、Twin、管理员和路由现有 6 个测试文件 / 48 tests 通过，本轮 Vue 类型检查通过；前端生产构建、定向 ESLint、Graph Ruff、`git diff --check` 通过。
- 按“功能先、性能后”没有启动 Web 全栈或发 DeepSeek 请求，远征性能仍待有可用正式判题沙箱后测量。

## 2026-10-13 收尾补测（本轮）

- 远征前沿使用服务端正式练习事件时间做间隔重复排序；Web 停止发送本地历史 KC，后端旧 `recentKcs` 字段仅用于兼容请求格式并忽略。Graph 排序函数不再接受客户端近期列表。
- 远征功能修正：同步出题遇到相同维度预生成 `PENDING` 时返回冲突；审核过的后端 Golden 题按 `reviewed` 接受；Graph 故障不发通用 KC 占位题；成绩未获得服务端结算时不能开始下一局，支持重试。
- 修复远征存档写入/弃局顺序与迟到判题响应竞态，避免旧请求覆盖新局；本地离线快照不反写服务端活跃局。主线视频/课件在服务端达标后通过唯一标记发布 `MediaConsumed` 学习事件。
- 开发 Compose 仅绑定本机回环；`./dev.sh` 为缺失、占位或过短内部令牌生成仅本次启动有效的随机值，不写回 `.env`。
- 验证：Web 定向 5 files / 76 tests 与 TypeScript 检查通过；后端运行摘要、Boss、主线进度及 marker repository 定向测试通过；Graph 前沿选择器 9 tests 与 Ruff 通过；PostgreSQL Testcontainers 从空库应用全部 116 个迁移到 V201；令牌生成、Compose 回环契约、Compose 配置和 shell 语法检查通过；`git diff --check` 通过。
- 本轮按“功能先、性能后”未启动全栈、未发 DeepSeek 请求、未运行 Piston 或远征性能基准；相关真实性能数据继续待测。本轮未新增素材。此前对话中的 DeepSeek key 仍需轮换。

## 2026-10-13 当前功能复验

- Tutor 课件检索失败现在直接终止当前工作流，不再把无证据导学卡交给模型生成；新增 fail-fast 回归。管理员账号列表/启停 API 与 Web 页面复用现有 `users.status`、`token_version`，停用即撤销旧会话；refresh 按 JWT user ID 取用户、请求鉴权核对数据库角色，登出增加服务端令牌撤销。Workshop 上传限制为每人最多 20 个、合计 50 MiB，并以用户行锁串行额度检查。
- 运维治理卡片在只有活动统计、没有审查记录时显示 WARN/待核验，不再误报 PASS；管理台窄屏抽屉补 Escape、焦点循环和返回焦点。正式判题 Graph 不可用现在返回 503，避免把“没有判题结果”呈现成答错。
- 当前快照全量验证：前端 Vitest 全量、TypeScript 类型检查、Vite build 通过；ESLint 0 errors / 131 warnings。Graph `1786 passed / 1 skipped`，Ruff 通过；RAG `158 passed / 2 skipped`；生产 AI/readiness 契约通过。后端全量 Maven `1228` tests、0 assertion failures、7 Testcontainers errors；空库迁移成功到 V200，7 类串行重跑中 6 类仍因 Docker host 动态端口已占用无法启动，1 类通过。新增功能相关定向后端、前端测试通过。
- 当前 turn 没有启动 Web 全栈、没有新增 DeepSeek API 请求，也没有测远征性能。此前本任务的 2 次真实 DeepSeek 文/图导师请求与 3 项 Playwright 记录仍见上节；本轮改动未改变该模型请求路径。对话中暴露过的 API key 仍应轮换。
- 没有新增像素素材：本轮变化集中于功能、认证、管理和防错；不需要补素材。

## 2026-10-13 后续功能收尾

- Graph `ChatEventData` 改为拒绝未知字段；图片继续只通过有 owner/session 校验的附件 ID 传输。新增嵌套图片载荷绕过回归，先确认 RED，再修复为 GREEN。
- Tutor SSE 将终态投影移到结果事件转发之前；Java 投影异常会通过错误终止传给调用方，不再被吞掉并呈现为成功。新增服务层传播和 Graph 中继顺序测试，均完成 RED/GREEN。
- 学生画像自评接口拒绝非空 metadata 和自由文本 tag；`cognitive_style` 只接受画像页已有的 `visual`、`logical`、`trial` 选项，教师/管理员路径保持原行为。控制器回归测试先有 3 项按预期失败，改动后 4 项控制器测试及 3 项 DTO 校验测试通过。
- 修复无聚焦章节的全局/每日 run 签题时误拒合法章节；固定章节 run 仍拒绝换章。排行榜将账本 `verifiedDepth` 标为“通过题数”，不再暗示地图层数。
- Alethicode 对照仅做只读核验：其当前 HEAD 为 `10a97fe`（2026-05-20），AutoGen runtime 及若干 Tutor/Admin 文件处于 dirty/untracked 工作树，不能作为稳定提交基线；对应 Codex rollout `01a12f26-6bab-7371-8763-06f922c1b13b`（10/12）记录文字实跑与合成图片调用。Alethi 按事件组装更完整的 Tutor 阶段并保存原生 AutoGen team checkpoint、处理恢复/取消；Pythontopia 已接入五阶段 AutoGen 并可跨事件从 DB 恢复，但行为未完全 parity。没有从 Alethicode 脏工作树复制文件。
- 阶段验证：Graph 全量 `1769 passed, 1 skipped`，Ruff 通过；RAG 全量 `157 passed, 2 skipped`；后端画像/Twin/Tutor/远征/审核定向 14 类共 124 tests passed，Testcontainers 空库迁移到 V198；Web Vitest `251 files / 2461 tests` 通过、TypeScript 类型检查与 Vite production build 通过，ESLint 0 errors / 131 warnings。10/13 收尾复验的更新结果见文末。
- `deploy/.env` 中 API key 仅以布尔值确认已配置，秘密未输出且扫描未在项目文件发现明文。对话中暴露过的 key 应轮换。
- 当前限制：远征地图节点、HP、遗物和 phase 仍是客户端快照；判题 ledger 保护排行榜分数和通过题数，但没有服务端地图动作状态机。Web Boss 提交现带稳定 `attemptId` 并持久幂等，未传 ID 的旧客户端仍保留兼容但没有重放去重。Lecture 的 PPT 通过资源消费 API 写服务端事件；视频观看仍只在本页会话标记。Mem0/Zep 配置固定走本地记忆；可选 Langfuse/OTLP、浏览器检索、B 站元数据和远程 Piston 仍属于独立外部出口。

## 实施

- 保留 Pythontopia 当前 Java API、Graph Tutor/RAG 和五阶段 AutoGen 工作流；工坊文字/图片问答通过导师 SSE 直接进入 DeepSeek。图片限制 JPEG/PNG/GIF/WebP、最多 3 张、单张 5 MiB，校验真实字节和 Base64，且只用于当轮请求。
- 只保留 DeepSeek 生成 provider；开发 Compose 与 `.env.example` 删除旧微调模型配置。Piston 成为正式判题 provider，未配置时 readiness 不通过且提交不会落回本机子进程。
- 修复无效 Bearer token 的统一认证失败路径。远征题实例绑定用户，KC 必须来自课程目录，计分从服务端判题结果写入并要求活动 run；leaderboard 的分数、深度来自同一局。
- 有界远征预生成线程池、排队和单用户额度；清理陈旧 lease，避免失败/过期任务无限堆积。
- Twin 仅统计近 7 天后端/Graph 权威事件；客户端事件采用 allowlist，不能伪造练习成功/失败。掌握度显示当前值、本周 delta、证据数量及来源。
- 管理台仅保留 API-backed 的 AI 运维摘要与审核队列。

## 2026-10-13 功能复验补丁

- Boss Web 提交新增 UUID `attemptId`；相同用户、相同 ID、相同请求体重放返回已保存判题结果，不重跑 Graph、不重复发布学习事件；同 ID 改代码返回 409。回归测试确认 Graph HTTP 180 秒请求仍在运行时，3 分钟旧 claim 不回收，5 分钟后才允许崩溃恢复，租约设为 4 分钟。
- 远征存档新增字段/集合上限与 64 KiB 序列化快照上限；这限制客户端快照对后端存储的占用，不改变它仍由客户端表达的地图状态事实。
- Lecture 打开已有 HANDOUT 时复用工坊资源消费 API 记录 `ResourceConsumed`；消费记录同步失败会提示，但不阻止读取讲义。视频观看仍保持会话标记，避免把客户端自报播放冒充权威进度。
- TrainingPanel 在卸载时取消尚未触发的草稿去抖保存，防止路由切换或换账号后旧页面仍写入代码草稿。新增卸载回归先失败，再修复；组件测试启用自动卸载以避免跨用例状态污染。
- 收尾复验：全栈 Web Playwright 3 项通过（小镇 HUD/导航、主线地图学习往返、指南桌面分页与手机目录）；DeepSeek 真实导师链路分别用文本与合成图片完成 2 次请求，均返回文本且图片颜色识别正确。Graph 1782 passed / 1 skipped、RAG 158 passed / 2 skipped；前端全量 Vitest 通过，类型检查与生产构建通过，ESLint 0 errors / 131 warnings。Maven 全量 1217 项无断言失败但有 7 个 Testcontainers 环境错误；对应 7 个集成类串行重跑通过，Boss 幂等 PostgreSQL 测试 4 项通过并迁移空库到 V200。AI readiness 契约通过。
- `dev.sh` 遇到空值或公开占位教师邀请码时改为本次启动生成临时随机值，不写回磁盘，避免默认演示配置使后端拒绝启动。
- 无尽远征性能指标仍待测：本机未配置或安装 Piston，Graph 自测在此状态会 fail closed 并重试后落到 golden fallback，测出来的数据不代表正常判题路径。官方 Piston 本地运行方式需 `--privileged` 容器；本轮未启动。全栈功能和 DeepSeek 文图功能已验证，性能仍排最后等待可用沙箱与授权。

## 验证

- 后端变更相关定向 Maven：91 tests passed；Testcontainers 将空库从 V001 链路迁移到 V193。
- 前端全量 Vitest、`npm run typecheck` 通过；ESLint 0 errors、116 warnings。
- Graph 全量 pytest：1735 passed、1 skipped；模型路由与导师图片/provider 更新后的定向 pytest：36 passed。RAG 全量 pytest：156 passed、2 skipped。Ruff 通过。
- `deploy/scripts/test_production_ai_readiness_contract.sh`、`docker compose config --quiet` 与 `git diff --check` 通过。
- 本地配置核验只输出布尔值和非秘密模型名：API key 存在于 Git 忽略的 `deploy/.env`，非忽略仓库文件未发现该值。

## 未验证与限制

- Maven 全量运行 1139 项时有 5 failures、7 errors；修正测试 provider/实例 ID 夹具后，相关 91 项通过。另 6 个 Testcontainers 集成类因 Docker host port `address already in use` 无法启动，后端全量仍未通过。
- 没有发起真实 DeepSeek 请求、全栈浏览器验收或生产 Piston 执行。对话中曾展示的 API key 应轮换后再用于生产。

## 收尾补丁与复验

- 远征补星请求从客户端自报答对数改为 `{ chapter, runId }`；后端按当前用户的活动 run、服务端已发题目章节和正式判题记录计算门槛。每局 12 次判题额度在活动 run 行锁内检查；主线补星入口进入章节聚焦远征，独立练习不再申领远征星。
- `/api/ai/runtime/operations` 的平台聚合限制为 ADMIN；STUDENT 仍只能看本人证据，TEACHER 收到拒绝。管理台增加退出并回登录页。
- 图片可单独发送，composer 自动附上讲解请求，继续复用 DeepSeek vision 与文本 SSE 回复。
- 本轮复验：后端补星、远征计分、管理员权限等定向 54 tests 全通过；前端全量 248 files / 2444 tests、类型检查通过；ESLint 0 errors / 114 warnings；最新 Admin/图片上传定向 5 tests 通过。生产 AI/readiness 契约、Compose 配置和 `git diff --check` 通过。
- 未进行真实 DeepSeek API 请求、浏览器全栈联测或生产 Piston 执行。无尽远征真实出题 P50/P95、缓存命中率、题目重复率、失败恢复率和玩法完成率均未测；没有用推测或静态测试结果代替实测。Maven 全量仍有既有 failures/errors 与 Docker Testcontainers 端口冲突，按模块定向结果交付；对话中暴露的 key 需轮换。

## Web 导师图片入口补齐（2026-10-12）

- 做题页 AutoGen CHAT 与主线锚定问答增加图片选择、预览/移除、格式/数量/大小限制；普通 Tutor 面板支持图片单发并自动补上提问语句。
- AutoGen CHAT 把图片作为 OpenAI-compatible image parts 交给 DeepSeek vision tier；Graph 复用 Tutor 的媒体类型、真实签名、base64、5 MiB 与最多 3 张校验。Java 只持久化问题文本，图片不进入历史卡片或对话记录；回复仍为文本。
- RED/GREEN：Graph Tutor 图片/工作流 schema 与 SSE 测试 23 passed；Web 三个导师面板回归 12 passed；Vue 类型检查、定向 Ruff、`git diff --check` 通过。定向 ESLint 0 errors，保留 AutoGen 面板原有模板格式 warnings。
- 按用户指示先完成功能，不启动本地全栈性能测试、不调用真实 DeepSeek API；浏览器真实模型联测和远征 P50/P95 等指标继续待测。

## 2026-10-13 补充：provider 与访谈写入边界

- 删除可单独构造并向任意地址发请求的 Spark provider/export；移除 Graph 测试读取 Alethicode `.env` 的 helper。DeepSeek 项目配置优先于通用 `OPENAI_*` / `LLM_*` 别名。
- 删除可直接请求智谱 embedding API 的 RAG provider；选择器只接受本地 hash/BGE，并继续拒绝讯飞/智谱配置名。
- 画像行锁前确保默认画像行存在，避免首轮并发维度事件因 `FOR UPDATE` 锁空集而整块 JSONB 丢更新。
- 访谈提交通过 `status = 'OPEN'` 条件更新抢占一次性完成权；事务落库移到独立 `ProfileDraftService`，避免 `ProfileService` 自调用绕过 Spring 事务代理；同事务内写 legacy profile、八维镜像和 revision，重放不重复覆盖。已完成/已中止的访谈不再接收新 turn，也不会被后续失败回调改成 ABORTED。
- 并发开启访谈通过 UPSERT 返回唯一 OPEN 会话；V198 将历史重复会话归档为 ABORTED，并建立每用户唯一 OPEN 会话索引。
- 验证：后端画像与 Twin 定向 5 类 / 48 tests；空库迁移至 V198；Graph 全量 1768 passed / 1 skipped、RAG 全量 157 passed / 2 skipped。未运行 Maven 全量、真实 DeepSeek、全栈浏览器或远征性能测试；性能指标仍待实测。

## 2026-10-13 补充：DeepSeek 模型白名单与外部记忆出口

- 官方 DeepSeek 文档当前列出 `deepseek-flash` 与 `deepseek-v4-pro`；provider 对主模型/快档只接受这两个 ID，视觉档只接受支持图片输入的 `deepseek-flash`。离线 DSPy 优化入口复用同一白名单，并将 CLI 示例从已退役的旧名称改为当前名称。依据：[List Models](https://api-docs.deepseek.com/api/list-models/)、[Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing/)（DeepSeek 官方文档）。
- `MEMORY_PROVIDER` 即使配置为 `mem0` 或 `zep` 也固定落到 local；Tutor/brief 的 recall 与 remember 不会向这些服务发送学习记录。RAG 仍仅选本地 hash/BGE，远程 embedding 选择值拒绝；BGE 通过 `local_files_only` 禁止运行时下载权重。
- 核对 Java 媒体出口：讯飞图片生成、图片理解、ASR 和 TTS 属性始终 `isEnabled=false`；图片输入走 Graph DeepSeek 视觉模型。补充 TTS service 层回归，旧讯飞凭据仍立即返回 `TTS_DISABLED`。
- 验证：Graph 全量 1777 passed / 1 skipped，RAG 全量 158 passed / 2 skipped；Java 记忆、讯飞媒体与故事板定向测试 32 passed；生产 AI/readiness shell 契约、ruff check、`git diff --check` 通过。未发真实 DeepSeek 请求。

## Graph 导师代码验证执行边界修复（2026-10-12）

- 确认 `nodes/code_runner.py` 曾用 Graph worker 身份直接启动学生代码，绕过 Java 正式判题的 Piston/Isolate 隔离。现统一调用共享远程沙箱适配器；未配置、超时或服务故障时 fail closed，并明确提示“代码没有实际执行”。TutorAgent 不再把沙箱不可用说成已运行。
- Java 旧 `subprocess` provider 保留兼容识别但永久拒绝执行；唯一正式执行 provider 是独立 Piston/Isolate。更新判题需求、设计、系统架构和当前测试报告，移除旧的 `unshare` 失败后无隔离回退描述。
- 集成测试改用只匹配已知题面的确定性测试沙箱，不执行测试提交代码；Piston 协议与 fail-closed 仍由 adapter 单元测试覆盖。
- 验证：Graph 代码验证/远征评测相关 pytest 34 passed，Ruff 通过；Piston 与禁用本地执行器 7 tests 通过；训练场和矿洞 PostgreSQL 集成测试 18 tests 通过；`mvn -DskipTests test-compile` 与 `git diff --check` 通过。
- 未验证：没有启动真实 Piston/Isolate 恶意样本；Graph/Java 到生产隔离服务的 TLS、namespace、chroot、cgroup 与低权用户配置仍须部署环境验收。

## 审核题库题目 ID 越权修复（2026-10-12）

- 发现正式判题在“当前用户签发题目不存在”时，会退回 `approved-{reviewId}-v{version}` 全局审核题库 ID 恢复隐藏用例；review ID 可枚举，绕过 `user_id` 题目归属，允许直接触发他人审核题的判定与学习事件。
- 删除该全局回退。审核题必须先经 `/problem` 针对当前用户签发唯一 ID 并写入其 `pregenerated_problem` 行；进程重启后只从该用户自己的持久记录恢复。删除无调用者的 repository fallback。
- TDD：新增越权回归先失败，证据为 `graphClient.evaluateAttempt` 被全局 ID 触发；修复后 `BossServiceTest` 22 passed、PostgreSQL `BossProblemReviewRepositoryPostgresTest` 3 passed。

## 远征结算、页面文本上限与 Twin 证据说明（2026-10-12）

- 远征 run ID 由服务端签发并锁定；客户端不能在活动存档中换 ID。记分不读取客户端分数字段，结束时仅按正式判题 ledger 汇总并将 run 原子标记为 `SETTLED`，结算后的 run 不能重复提交成绩；新局由服务端签发新 ID。新增 V194 状态约束及 PostgreSQL 空库迁移/结算回归。
- 浏览器研究工具此前先把完整 `body.inner_text()` 拉到 Python 再截断；改为在页面中只提取限长文本，控制送入 Python 进程的 DOM 文本副本。浏览器自身仍需加载目标 DOM/资源。
- Twin 周报为 KC 增加与掌握变化同时展示的证据置信度。远征选择器复用同一服务端置信度，对低置信度 KC 给予最多 8 分探索加权并标注“证据待确认”；置信度是启发式事件指标，不是校准概率。
- 新增 [远征与 Twin 调研/设计记录](./2026-10-12-expedition-twin-research-design.md)，核实当前固定 10 层（含 Boss）边界、现有自适应特征、Twin 周报事实来源，并记录 BKT/DKT/AKT、间隔练习与教育游戏化资料及适用性。
- 验证：Graph selector pytest 8 passed，Ruff 检查通过；Java 远征 run/score、Boss、Twin 与 Runtime 10 个测试类合计 55 passed；`ExpeditionRunPostgresIntegrationTest` 将空库迁移到 V194 并验证换 ID 拒绝、客户端分数忽略、结算和新局 ID；前端远征 store 36 tests、TwinWeekly 5 tests、Vue 类型检查通过；浏览器文本预算测试包含在 browse executor 35 项回归中。
- 仍待验证：真实 Web 全栈 / 浏览器联测、DeepSeek 真实请求、远征性能指标和生产 Piston。节点拓扑、HP、遗物与路径状态仍来自客户端快照，当前不用于排行榜成绩/通关发奖；服务端尚未建地图动作状态机。浏览器底层 DOM 内存也未实测。
- 性能闸门检查：本地服务当前未运行，`deploy/.env` 未设置 `PYT_PISTON_URL`。Graph 题目自测在沙箱缺失时 fail closed，单个问题最多重试 6 次再回退 golden；此状态下不发起付费压测，避免把预算花在已知降级路径。补测前需要本地可用的 Piston/Isolate 和已轮换的 DeepSeek 凭证。

## Web 导学恢复、远征绑定与审核列表（2026-10-12）

- Graph 重启后从 Java 投影恢复最近的 Tutor 卡片、聊天历史和下一步方向；输入端限制消息长度、图片事件范围和数量。AutoGen 阶段信封不序列化图片字节，图片仅在本次运行内存中回注各阶段。
- 无尽远征预生成身份增加 `stage` 与 `runId`：唯一任务冲突通过 `ON CONFLICT DO NOTHING` 处理，避免捕获唯一键异常后 PostgreSQL 事务进入 aborted 状态；READY 结果需匹配请求章节/阶段，更新同时绑定用户、阶段、run 和重试次数。
- 正式判题核对已签发题目的章节/阶段，拒绝已经结算的远征；判题 ledger 写入成功后才发布 LearningEvent，额度用尽与 run 过期返回不同错误。V195 为旧预生成行回填阶段并新增 run/阶段索引。
- 预生成按用户加事务 advisory lock 后检查在途配额，避免并发请求绕过上限；远征结算或放弃时关闭关联的待处理/就绪任务并释放配额。
- Web 取题状态请求新增可选 stage/run ID 参数；stage 可省略以兼容旧客户端。远征存档响应返回账本派生的 `score` / `verifiedDepth`，Web 将核验统计与局内体验分分开展示，离线存档不会标成核验成绩。
- 管理题目审核列表增加按申请时间、题面、申请人升降序；后端排序字段/方向使用固定白名单，排序变化回第一页。
- 已执行定向验证：前端 5 个文件、68 tests 通过；`npm run typecheck` 通过，定向 ESLint 0 errors。Graph 导学 24 tests 与 Ruff 通过。后端变更相关定向 Maven 累计 74 tests 通过（包括阶段/run 绑定、结算/放弃清理预生成任务及配额锁的 PostgreSQL 集成复验），空库 Flyway 迁移到 V195 成功。
- 不启动本地全栈、不发真实 DeepSeek 请求或性能压测；远征 P50/P95、缓存命中率、重复率、恢复率与完成率留待后续实测。地图节点、HP、遗物仍是客户端存档状态，未用于服务端排行榜分数或判题深度。

## Tutor 计划恢复、引用授权与管理台外观（2026-10-12）

- Graph Tutor 为 `AGENT_FEEDBACK`、`COMPACT`、`PLAN_RECOMMEND`、`PLAN_START`、`PLAN_RESPONSE`、`PLAN_STEERING` 补齐事件 DTO 与确定性状态处理；计划控制携带 revision，过期请求返回 `PLAN_STALE`，新计划使用新 ID。
- Java 仅从当前用户会话卡片解析 `@card:<id>`，仅从 pack 43 解析 `@courseware:<pageId>`；忽略客户端提交的 `references`，拒绝不存在或越权内容，并将引用数限制为 5、课件摘录限制为 2,000 字符。
- AutoGen 阶段通过受限 JSON 状态信封保存/恢复 student state、episode plan 和最多 20 轮纯文本历史；图片字节不会进入状态、卡片或检查点。V197 以 `(session_id, run_id)` 唯一键保证 Graph 终态投影的 marker、session、agent state、card 和 action event 在同一事务中提交，重复终态回调不重复写入。
- 正式提交事件更新已有状态 envelope，并仅在当前 ACTIVE 计划步骤匹配期望证据时推进步骤；状态与提交观察 action 原子写入。`LearningEvent` 当前不携带 submission ID，因此无法区分同一正式提交事件被精确重放与两次相同判定提交，重放可能重复计数；后续需给事件增加稳定提交 ID 才能做精确去重。
- 管理台沿用现有 API 与权限，仅调整侧栏、窄屏导航、面板和审核表格外观；不新增虚构菜单或 API。
- 当前验证：Java 定向 9 个类 / 103 tests 全通过，Testcontainers 空库迁移到 V197；Graph 6 个文件 / 58 tests 与 Ruff 通过；Web 定向 7 个文件 / 38 tests、TypeScript typecheck 通过；ESLint 0 errors / 41 warnings；`git diff --check` 通过。
- 项目配置核验只输出布尔值：DeepSeek OpenAI-compatible endpoint/key 已配置于被忽略的 `deploy/.env`；研究 runner 中原有的硬编码凭证已改为读取 `PYT_OPENAI_API_KEY`。
- 未运行全栈浏览器联测、真实 DeepSeek 请求或无尽远征性能测试。复核时本地 compose 无运行服务且 `PYT_PISTON_URL` 未配置，因此真实判题路径不可用；无尽远征 P50/P95、缓存命中、重复率、恢复率与完成率继续待测。密钥曾出现在对话中，应轮换后再用于生产。

## 2026-10-13 补充：远征同题跨 ID 防重复计分

- 远征正式判题按权威 KC、题面、样例与排序后的判题用例计算 SHA-256 指纹，忽略随机签发 ID、提示、难度和参考解；相同题在同一用户的同一 run 下合并到原 ledger 行，保留原 `problem_id`，避免影响题目来源核对。
- V202 为非空指纹建立 run 范围唯一索引；同局提交先锁定 run 行，再查同题指纹，因此新签发 ID 的重试只更新最高分/通过状态，不新建计分或学习证据。
- 验证：`BossServiceTest` 32、`ExpeditionScoreServiceTest` 8、`ExpeditionRunPostgresIntegrationTest` 5，共 45 tests 通过；Testcontainers 空库迁移到 V202。后端全量运行 1240 tests，0 failures、7 errors：6 个 PostgreSQL 容器因宿主机随机端口 `address already in use` 未能启动；长周期班级模拟在全量运行中临时数据库连接中断，单独重跑通过。
- V202 前活动局中既有 ledger 行的指纹保持 NULL，不对历史 JSON 做不可靠重算；若旧题从未重判且在升级后的同一旧 run 再次签发，仍有一次重复计分窗口。新 run 和新写入记录不受此限制。

## 2026-10-13 补充：Tutor AutoGen 隐私与管理用户筛选

- 将 `autogen_core` / `autogen_core.events` 日志级别设为 WARNING；安装版本的运行时源码显示 INFO 会输出完整消息内容。新增日志级别测试，先验证其在默认 root WARNING 下也会失败，再验证修复通过。
- Tutor 终态投影和失败时将 AutoGen checkpoint 状态体清空为 `{}`；INTERRUPTED 保留恢复状态。V203 清理已有 COMPLETED/FAILED 行中的 checkpoint JSON，不删除卡片或业务投影。Testcontainers 集成验证续跑阶段仍保留状态、成功/失败终态清空，并将空库迁移到 V203。
- 管理员用户页新增角色筛选，服务端分页 SQL 按真实 `users.role` 过滤；定向后端和 Web 测试通过，详见当天 changelog。
- 当前验证：Graph Tutor/AutoGen 定向 50 tests、Ruff 通过；Tutor workflow PostgreSQL 集成 8 tests（先迁移到 V202 并播种旧终态文本，再应用 V203 验证清除）、远征 run/service 与 PostgreSQL 集成共 12 tests、管理员 repository PostgreSQL 集成与服务单测通过；后端变更文件 Spotless 检查通过；Web 用户管理/API 5 tests、类型检查与定向 ESLint 通过。
- 尚未清理或检查生产运行日志；INTERRUPTED checkpoint 仍为续跑而保留，暂无过期清理策略。无尽远征的路线、体力、遗物、胜利状态仍由客户端快照驱动，题目尚未绑定可达节点，服务端权威动作状态机仍是 P1 未完成项。按用户要求暂不做远征性能测试。

## 2026-10-13 补充：远征客户端终态拒绝

- `/api/world/expedition/run` 当前仅接受 `map/event/challenge/reward` 续局阶段；直接 PUT `idle/cleared/over` 在触库前返回 400。GET 将升级前已有活动行中的其他 phase 归一为 `map`。写入和旧行读取 RED 回归均证明旧代码会接受终态，修复后 `ExpeditionRunServiceTest` 6 项与 Testcontainers `ExpeditionRunPostgresIntegrationTest` 6 项通过，空库迁移至 V203。
- 这个小补丁只阻止客户端把终态写入可续局后端快照；它没有把节点拓扑、移动、体力、遗物和实际 Boss 通关迁到服务端。上述远征动作状态机、题目与可达节点绑定仍是 P1 未完成项，不据此宣称通关/分数伪造风险已完全修复。

## 2026-10-13 本地 Web 启动回归

- 本地全栈启动发现 `StaticCodeChecker` 有两个构造器但没有明确注入构造器，Spring 因而尝试调用无参构造并阻止后端启动。新增 Spring 容器装配回归，先复现 `No default constructor found`，再为配置构造器标记 `@Autowired`。
- 修复后后端 `/healthz` 返回 200；Vite 5173 已有本项目进程运行，因此 `./dev.sh` 的前端启动阶段提示端口占用并退出，随后复用已运行的 Web 进程完成浏览器测试。
- `StaticCodeCheckerTest` 15 tests passed；`deliveryClickRobustness.spec.ts` 的小镇 HUD、主线地图/学习往返、指南桌面/移动目录 3 项 Playwright passed。另以浏览器登录临时学生并从页面真实开局，确认服务端返回并渲染 10 层 / 29 节点地图；浏览器拦截 1 次 Boss 预取，未请求出题服务。
- 本轮没有 DeepSeek 请求或远征性能测量。服务端远征节点动作、题目与可达节点绑定、权威 HP/奖励仍未完成；本地自动注册的临时 E2E 学生账号留在开发数据库中。

## 2026-10-13 功能复核：每日远征与判题策略

- V205 新增按 `user_id + Asia/Shanghai challenge_date` 唯一的每日远征领取表；服务端创建日挑战前检查并写入领取，弃局不会释放当日名额。迁移回填当天已有的 daily run。榜单结算现在从锁定的服务端 run 读取 `is_daily`，不再固定写 `FALSE`。
- Web 本地日期按上海日界线显示；后端拒绝跨设备重复领取时，同步本地日期状态并显示明确提示。本地标记仅影响显示，不能绕过服务端限制。
- 修复随机地图没有精英节点的分布缺口：若 9 个常规层抽样未包含精英，则在 Boss 前最后一层替换一个节点为精英。新增 256 个固定 seed 的性质回归。
- Graph 的 AST 策略检查补上直接 `__import__` 与 `importlib`；这只是纵深策略检查，Piston/Isolate 仍是唯一执行安全边界，缺配置依旧 fail closed。
- 修复相同题面导致 Boss 后续阶段卡住：服务端签发 ID 编入 Boss 阶段，verified attempt 与结算指纹按题面、节点、阶段派生；同阶段重试继续合并，旧阶段题无法在新阶段判题。V205 现有指纹列足够，无需加迁移；旧未标记 ID 仅兼容第一阶段。
- 本机忽略文件 `deploy/.env` 中过短的教师邀请码已随机重置到满足启动校验的长度；未打印或提交其值。DeepSeek 请求未运行。
- RED/GREEN：Boss 阶段回归先失败于 phase 2 仍停留 phase 2，修复后后端五类测试 79 passed，Testcontainers 空库迁移到 V205；Web store 45 passed、typecheck 与定向 ESLint 通过；Graph 策略/导师文图链路 102 passed、Ruff 通过；9 个 Java 文件定向 Spotless 通过。全仓 Spotless 仍有其他文件的既有格式问题。
- 尚未验证：本轮没有启动 Web 全栈、没有发 DeepSeek 请求或执行 Piston，也未测无尽远征性能。Boss 重复题阶段推进已由数据库集成回归覆盖；对话中曾展示的 DeepSeek key 应轮换后再用于生产。

## 2026-10-13 补充：拦截远征生成代码的 os 模块

- Graph Boss 自测/评估共用 AST 扫描器新增 `os` 禁止项，`import os; os.system(...)` 会在交给 Piston 前被拒绝。扫描器不替代 Piston/Isolate 的实际隔离边界。
- RED/GREEN：新增恶意调用回归先失败后通过；Boss 自测、评估及路由 43 tests 通过，Ruff 通过。
- 未启动全栈、未调用 DeepSeek 或 Piston；无尽远征性能仍待功能复核完成后测试。

## 2026-10-13 补充：远征恢复与 Web 学习状态可靠性

- Web 远征：`over` 终局进入可恢复保存，并保留待结算状态；刷新后重试同一局排行榜结算。日挑战续局先恢复已有 run，普通远征不能被日挑战开局覆盖。Boss 已清除节点决定胜利展示；挑战重试保留首次难度。
- Web 画像与教学：Twin 周报和八维画像加载失败展示可重试错误态；教师快速切换班级后，前一班级迟到的响应不再覆盖当前成员、任务或看板数据。
- 后端安全与数据一致性：媒体读取、流式输出拒绝空用户或非所有者；掌握度事件按用户/KC 获取 PostgreSQL 事务级锁后再读写快照，避免并发增量互相覆盖。远征正式判题账本上限调整为 36，匹配当前最多 12 个挑战阶段、每阶段可换题重试的有界容量。
- Graph Boss 自测扫描拒绝 `os` 导入，阻断参考解调用 `os.system`；Piston/Isolate 仍是代码执行的安全边界。
- 当前复验：Web 定向 Vitest 5 文件 / 86 tests、`npm run typecheck`、定向 ESLint 通过；后端计分/媒体/掌握度/Twin 定向 Maven 测试退出码为 0，Testcontainers 空库迁移到 V205；Graph `test_self_tester.py` 18 tests 与定向 Ruff 通过。
- 本轮没有启动全栈、发起 DeepSeek 请求、调用 Piston 或测性能。此前对话中暴露的 DeepSeek 凭证应轮换后再用于真实请求。
- 预生成 pending 已有最多 10 次、每 500ms 的前端轮询；尚未覆盖后台任务主动取消与前台生成超时后的重复请求保护。Twin 仍是可解释的 7 日只读摘要而非预测模型；画像被动事件持久化的失败补偿/重放尚无 outbox。这些缺口需要单独设计与验收。

## 2026-10-13 补充：DeepSeek 模型锁定与部署门禁

- OpenAI 兼容 provider 拒绝调用方通过 `model=` 覆盖运行配置；只允许通过 `model_tier` 选择受限的 chat/fast/vision 模型，避免请求绕过 DeepSeek 模型 allowlist。流式和非流式均有回归覆盖。
- 新增共享部署模型契约；ECS preflight 和 release installer 会拒绝不支持的文本/fast/视觉模型名，且留空 fast 档继续使用默认回落。allowlist 与 Graph provider 一致：文本/fast 为 `deepseek-v4-pro` 或 `deepseek-flash`，vision 仅 `deepseek-flash`。
- 官方文档确认 `deepseek-v4-pro` 为文本输入/输出，`deepseek-flash` 支持图像输入并输出文本；[模型列表](https://api-docs.deepseek.com/api/list-models/) 和 [Vision 指南](https://api-docs.deepseek.com/guides/vision/) 是本次核对依据。
- 本地忽略文件 `deploy/.env` 已配置 `openai_compat`、DeepSeek endpoint 及文本/fast/vision 模型字段；仅检查是否存在密钥，不读取其值。本轮没有启动服务或调用 API；对话中暴露过的密钥应轮换后再发起真实请求。
- RED/GREEN 后验证：provider、策略与导师文图输入 57 tests 通过（含 1 项既有 pytest asyncio 标记警告）；AutoGen Tutor runtime/workflow、附件、图像和流式响应 73 tests 通过；Web 工坊/伴学图像提交 4 个测试文件 / 64 tests 通过；部署模型契约、生产 AI readiness 契约、部署脚本 `bash -n` 通过。没有运行全栈联测或无尽远征性能测量。

## 2026-10-13 补充：HTTP 入口、注册隐私与预生成恢复

- Nginx 80 端口现在只响应 ACME challenge，其余请求固定 308 到可信 IP 的 HTTPS；避免把密码、JWT 或业务请求经明文转发，也不使用客户端提供的 Host 构造跳转目标。发布、回滚与公网验收脚本同步校验新入口。
- 公网 `/api/classrooms/join` 限制为每来源 IP 5 次/分钟并返回 429；生产流量必须经过主机 Nginx。认证、停用账号和 JWT 代次已有的门禁保持原样。
- 邮箱目前是可选且未验证的资料字段，不被登录、恢复或查询使用。V206 删除 `users_email_key`，保留 `username` 唯一约束，避免通过注册响应枚举已登记邮箱；没有修改已有邮箱值。
- 预生成服务单实例启动时将遗留 PENDING 行改为 FAILED，用户可重试；异步任务出队后先检查 PENDING 状态和 attempt，已取消/被替代任务不会再调用 Graph。已经进入阻塞 HTTP 调用的任务不能主动取消，但数据库条件更新仍阻止其覆盖新状态。
- TDD：邮箱重复插入的 Testcontainers 回归在 V206 前因 `users_email_key` 失败，迁移后通过；空库迁移到 V206，邮箱可复用而用户名仍唯一。预生成回归覆盖启动清理、排队任务取消和旧 attempt 晚到，`EndlessPregenServiceTest` 9 passed，`ExpeditionRunPostgresIntegrationTest` 22 passed。Nginx 入口契约、回滚选择和 HTTPS 转发契约脚本通过；临时 Nginx 1.27 容器验证 Host 不可控、ACME HTTP-01 可用且加入码接口连续请求触发 429。
- 未执行全栈浏览器、真实 DeepSeek/Piston 请求或性能测试。Piston 远程服务的低权身份、网络/文件隔离尚无实际运行时证据；继续以 fail closed 为当前安全行为。

## 2026-10-13 补充：远征成绩终局与通关状态

- 复核 Web 远征服务端动作链后发现两个真实缺口：排行榜固定把 `cleared` 写成 `FALSE`；成绩接口即使 run 仍在 `map/challenge` 阶段也能直接结算。
- 成绩结算查询现在锁定且只接受 `phase='over'` 的服务端活动 run；`cleared` 由服务端 JSONB 地图的 Boss 节点状态计算，客户端快照不能改变这两个事实。
- TDD：三阶段 Boss 集成回归先暴露排行榜 `cleared=false`；提前结算回归先暴露活动 run 被置为 `SETTLED`。修复后 `ExpeditionScoreServiceTest` 10 tests 与 `ExpeditionRunPostgresIntegrationTest` 22 tests 通过；Testcontainers 空库迁移到 V206。普通主动结束记录 `cleared=false`，三阶段 Boss 通过记录 `cleared=true`。
- 仍未验证生产 Piston/Isolate 的实际隔离属性；未发新 DeepSeek 请求，也未测远征性能。此前对话中出现的 API 凭证应先轮换。

## 2026-10-13 补充：训练场 Pyodide Worker 打包与运行回归

- 训练场开发态发现未知动态导入 `/pyodide/pyodide.mjs` 被 Vite 改写为 `?import`，命中 public 文件后返回 500。Worker 改为从 npm 包静态导入 Pyodide ESM 与 asm 模块，显式传入 `createPyodideModule`；Vite Worker 输出切到 ESM 以支持依赖分块。同步脚本仅复制本地 WASM、Python 标准库和锁文件，并清除旧 JS 运行时文件。
- RED/GREEN：浏览器回归在旧实现下因 Vite 错误遮罩无法运行；重启 Vite 后用 `print("pyodide-e2e-ok")` 实际执行并显示结果。生产构建后的 Worker 也在 Chromium 中加载 `/pyodide/` 数据并输出 `pyodide-prod-ok`。
- `npm run build`（含 vue-tsc）通过。Pyodide 上游 ESM 引入的 Node builtin externalization 和 `eval` 警告仍会在构建时出现；生产 Worker 实测正常。本轮没有请求 DeepSeek/Piston，也未测无尽远征性能。

## 2026-10-13 补充：Web 依赖审计与全量构建

- `npm audit` 首次发现 7 项生产依赖高危公告；更新 Vue、Axios 和受影响依赖，并升级 Vite/Vitest、ESLint Vue 插件、jsdom 与 TypeScript ESLint。Mermaid 子依赖 KaTeX 固定至 `0.18.2`，npm 官方 registry 的完整审计结果为 0 vulnerabilities。
- Vitest 4 全量运行复现 ClassroomDetail 用例间调用次数串扰；该组件已经有请求中互斥保护，问题来自 hoisted mock 未清空。测试 `beforeEach` 增加 `vi.clearAllMocks()` 后，单文件 9 tests 及全量 255 文件 / 2512 tests 通过；全量学习 store 单测 4 tests 独立通过，teardown 报错未复现。
- `npm run build`（含 vue-tsc 与 Vite production build）通过；全量 ESLint 0 errors、131 warnings。锁文件只恢复版本和 integrity 均未变化包的原 registry URL，保留实际依赖版本变更。
- 没有启动全栈、发起 DeepSeek/Piston 请求或测无尽远征性能。前端测试环境使用 jsdom 29，Node 20 需为 20.19+；Piston/Isolate 的操作系统隔离仍没有本机运行证据。

## 2026-10-13 补充：Tutor 安全边界复核

- Piston 请求设置 `trust_env=False`，避免学生代码、标准输入和服务间令牌受进程代理变量影响而发往代理。Tutor 不再输出完整学习画像；视频代理日志不再包含可能带凭证的 URL。
- Boss 路由对缺失 DeepSeek 配置 / DryRun fail fast；显式上游故障仍按已记录的 reviewed=false golden 策略返回。题目自检/审查失败后若 golden KC 与请求 KC 不同，Graph 返回 `BOSS_PROBLEM_KC_MISMATCH`；Java 入库边界也拒绝错 KC。
- Tutor CHAT 回归现在确认当前问题标题、题面、样例输入输出、输出说明、阶段和图片持续进入当轮提示。`deploy/.env` 存在被 Git 忽略的 DeepSeek 配置；本轮没有读取凭证或发起模型请求。
- 验证：Graph 全量 `1812 passed, 1 skipped, 13 warnings`；Boss 定向路由/沙箱回归及 Ruff 通过；后端 `BossServiceTest` 35 项通过，前端全量 256 文件 / 2517 tests、typecheck、定向 ESLint、build 通过。
- ASR 控制器现在先检查服务开关；关闭时不访问上传内容。启用时，超过 8MiB 的音频在 `getBytes()` 前返回 `ASR_AUDIO_TOO_LARGE` / 413。回归用例先在旧实现下失败，修复后控制器与服务 6 项定向测试通过；全仓 Spotless 仍被 607 个其他 Java 文件的格式问题阻断，ASR 控制器和测试不在违规清单中。
- 未关闭：Tutor 账号级费用/请求额度策略仍等用户选择；生产 Piston/出口网络隔离仍未实测。对话中出现过的密钥需轮换后再用于生产。无尽远征性能照用户要求留到最后；本轮没有真实 DeepSeek 请求。

## 2026-10-14 额度策略落实与当前快照复核

- 用户将 Tutor 账号级额度周期与上限交由项目决定。复核当前实现：V209 按账号/上海自然日计数，Tutor 普通问答和工作流在 Graph 前预占，每账号每日 30 次；V212 同事务增加全站每日 300 次入口上限；计数保留 90 天。改变 conversationId 不改变账号计数。
- `mvn -q -Dtest=TutorDailyRequestLimitServiceTest test` 当前快照退出码 0。额度耗尽响应 HTTP 429，业务路径在 Graph 客户端调用前结束。
- 这限制的是入口请求次数，不是工作流内部模型调用次数、token 或金额。供应商账单/Token 用量仍需核实；本轮没有调用 DeepSeek。

## 2026-10-14 研究和调试 CLI 外发边界

- 研究共享 HTTP 客户端及 Graph provider 路由均限制 DeepSeek HTTPS 主机与 allowlist 模型；研究客户端不跟随 HTTP 重定向。baseline、A/B、教师投票、Graph 路由和诊断 CLI 移除 key/internal token 的 argv 参数，改为环境配置；旧 Qwen 在线端点因共享客户端检查而在发送前失败。
- RED/GREEN 回归覆盖非 DeepSeek 地址、端口、userinfo、模型、重定向和五个 CLI 帮助输出中的 secret 参数。研究全量非训练套件 `321 passed`、Ruff 与 `git diff --check` 通过；两个需要 `torch` 的训练模块因当前测试环境未安装 `torch` 而未收集成功。
- 本轮未发起模型或网络 API 请求；已确认的外部请求数与供应商实际账单仍未核对。

## 2026-10-14 Bug Mine 事件去重

- Bug Mine retry 通过时，`SubmissionService` 自动完成关联 FIX_BUG 任务与 `BugMineService` 关闭会话会各发一个 `DebugResolved`。改为有已验证 submission 证据的任务完成不再重复发事件，让 RESOLVED 会话作为该调试事件的唯一来源；无 submission 的旧任务完成行为保留。
- TDD：新增真实 Testcontainers 调用链回归，旧代码写入 2 条 `DEBUG_FIX` 后失败，修复后精确断言 1 条通过。`QuestServiceTest` 8 项及该 Bug Mine 集成用例通过。
- 未触发模型调用。章测并发判题、Boss 唯一 attemptId 请求限流、Boss 控制器角色边界、Boss 完整浏览器判题旅程及外部额度核对仍待处理。

## 2026-10-14 续：章测并发、Boss 入口与 Graph 故障兜底

- 章测提交在服务端锁定对应学生的测试记录直到本地判题与写入结束；请求最多 100 道，答案 key 最多 128 字符、value 最多 8192 字符。数据库并发回归证明同一测试不能并行重复判题。
- Boss 判题以 user 行锁串行化新 attempt ID 的账号请求，并施加 10 秒窗口限流；同一 attempt ID 的重放仍走幂等结果。Boss 出题、预生成、状态与判题入口统一拒绝非学生身份。
- Graph 熔断重试最终抛出的 `BusinessException` 原先绕开了 Boss 的 `RestClientException` 本地执行兜底。现在在 Graph client 边界映射成已有传输异常；RED/GREEN 回归覆盖 Graph 连续返回 503 后的异常类型。当前代码下，正式 Boss 判题 API 使用本地 Piston 成功通过 1/1 样例。
- 浏览器从真实登录开始检查远征：普通题通过，另一次错误只按服务端结果扣 2 点体力，再用新题通过；继续体验了复盘与休息节点，期间没有浏览器异常。已通过的 Phaser 单测确认 Boss scene 会复用唯一 Game/canvas、成功结算才触发命中回调，失败状态不伪造 Boss 受伤。
- 定向验证：`QuestServiceTest` 8、`Stage10BugMineAcceptanceIntegrationTest` 12、`ChapterTestServiceTest` 19、`ChapterTestSubmitConcurrencyIntegrationTest` 1、Boss 幂等 PostgreSQL 5、`BossControllerTest` 11、`BossGraphClientTest` 2、`BossServiceTest` 46，共 104 项通过；前端 Boss 场景和画布切换 5 项、typecheck、`git diff --check` 通过。
- 尝试用第二个合成账号直开第 5 章远征时，服务端按真实先修画像返回 `EXPEDITION_NO_PRACTICE_PROBLEMS`：仅有一个真实通过记录且掌握度不足以满足先修条件，回退候选也没有课程编程题。没有伪造掌握度；两个临时测试账号及其级联学习数据已从本地数据库删除。
- 未完成项：最终 Boss 节点的浏览器真实判题与命中动画旅程没有跑完；用户尚未完成先修评估的账号可能无法从高章直接开始远征。该限制需要有真实教学流程的产品决策，不用假数据掩盖。未发起新的 DeepSeek 请求；历史约 36 条应用日志与供应商用量仍未核对，因此继续保持 Graph 停止。
