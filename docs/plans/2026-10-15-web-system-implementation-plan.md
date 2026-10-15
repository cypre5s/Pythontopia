# Pythontopia 系统审计与改进 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the confirmed Web security leaks and finish evidence-based verification of the existing AutoGen tutor, expedition, learner twin, and admin flows without duplicating architecture.

**Architecture:** Keep Spring APIs, PostgreSQL state, event-specific AutoGen stages, DeepSeek-only model routing, server-owned learning evidence, and existing Vue/Phaser components. Make only narrow fixes in current modules; use existing deployment limits, migrations, test helpers, and UI primitives.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL/Flyway, Python 3.11+, FastAPI/LangGraph/AutoGen, Vue 3/TypeScript, Phaser 3, Vitest, Playwright.

## 总任务清单

本节是当前唯一交付清单；已有验证按仓库记录复用，不重复模型探针或已通过的等价测试。各任务详情和验证证据保留在下方。

### 当前状态快照（2026-10-16；最近代码提交 `2cb67ce9`）

**已完成并可复用的证据：** DeepSeek 文本及合成 PNG 输入曾通过真实调用，Tutor 响应为文本；最近 Tutor 浏览器流程触发题目导读与无帮助反馈两个事件，2 次 DeepSeek completion 均 HTTP 200，并显示非空文本卡片。另已完成 DeepSeek provider 静态审计：在线 LLM 只允许 `api.deepseek.com` 与批准模型，TTS/ASR/讯飞图片服务恒关闭。账号级用量锁、事件专属 AutoGen 阶段、run 检查点、附件隔离、Boss Phaser 场景、Twin P0、共享选题策略、AI 题库审核、主要管理页操作、安全基线及多项敏感日志修复均已有实现和定向验证。DeepSeek 文本/图片调用与 Tutor 产品链路均封账，不再重复；仅当具体产品代码改变且必须走模型路径时，才做一次该路径验证。图片 Web 中继稳定性保留为未验证项。

**仍未完成：** 剩余本地安全类别审计；有真实先修证据的第 2 章以上远征题型、活动 run 刷新恢复和异常恢复；尚未覆盖的 Web 可见动作与窄屏交互；Twin A/B 和 7 日延迟效果；全栈功能收敛后的工程与性能收尾。Python 开发 venv 的当前 OSV 复扫只剩 `diskcache`（Mem0 可选依赖）与 `ragas`（评估可选依赖）两项，公告未列修复版本；两者不在重建后的 Graph/RAG 运行镜像中。生产 Piston ACL/ECS 和 DeepSeek 供应商账单不在本地可验证范围内。

**接续顺序：** 不再安排通用 DeepSeek 测试。先完成可由现有代码与本地服务验证的安全/远征闭环，再补 Web 未覆盖动作和工程问题；长期效果依赖真实样本，性能最后测。只读复核确认管理台壳层/配色和 Pythontopia 班级 API、DTO、数据库字段已经满足本轮对齐目标；不复制 Alethicode 未提交的页面与 schema。

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
- [ ] 附件扩展安全审计：逐类闭合判题隔离与部署边界、远征状态篡改/幂等、媒体上传、SSRF、XSS、SQL、速率限制、依赖与敏感日志；只对确认问题修复并补回归。SSRF 当前代码复核覆盖唯一用户目标触发的 BROWSE 导航，服务端注入 host allowlist，Graph 每次导航/重定向验证公网 DNS、scheme、标准端口并将 Chromium DNS pin 到公网地址；对应 allowlist/executor 测试 58/58 通过。敏感日志已修复上传、索引、trace、Piston、Mem0/Zep、Graph retry、画像访谈、NPC 对话、RAG embedding、B 站问答/搜索、Feynman、BROWSE、Boss 出题/判题与章测 Graph 错误中的已复现泄露；RAG 启动/自愈/索引重建和 embedding 降级异常现只记录异常类型，503 返回固定说明，哨兵与定向测试共 33/33。其余已有修复包括本地 Piston Compose 配置与运行态仅接入专用 `judge-execution` 网络、宿主端口限制为回环、Python 3.12.0 执行冒烟成功（输出 4）、Java 适配器对远端失败 fail-closed；MetaReflection 调度异常和反馈汇总日志、Tutor/视频 Graph 兜底、DialogContext、MemoryRetrieval、LearningPath、WorldState 降级日志也已移除异常正文。新扫到 Boss 题目存档反序列化异常会把 payload 片段及 owner/problem 标识写入日志；现改为仅记异常类型，`EndlessPregenServiceTest` 17/17（RED/GREEN）。本轮修复本地运行容器与开发 venv 中的 FastAPI/Starlette 和 pytest 漏洞版本；Graph/RAG 全量测试分别 1902 passed/1 skipped、166 passed。OSV 复扫剩余 `diskcache` 与 `ragas` 两项可选开发依赖告警（当前无修复版本），不在运行镜像中。其余异常日志与生产 Piston ACL 仍待核实；当前 `/readyz` 整体因已记录的 `VIDEO_TRANSCRIPT_UNAVAILABLE` 返回 503，judge readiness 本身为 ready。
  - 10/16 公共异常日志续审：全局 500 与客户端断连处理器不再输出异常消息/堆栈或原始 URL，改记方法、Spring 路由模板、异常类型和 traceId；章星重算降级日志不再包含学生 ID/异常正文。两个测试类先 RED 后 GREEN，合计 10/10。全量安全审计仍开放。
  - 10/16 工坊日志续审：画像维度、周报洞察、两类记忆摘要及掌握度难度降级日志去掉学生 ID，只保留异常类型和原有 fallback。一个合成哨兵覆盖 5 种失败路径，先 RED 后 GREEN；`ResourceWorkshopServiceTest` 34/34。未发模型请求。
  - 10/16 前端 HTML 注入点续审：扫描所有 `v-html`/`innerHTML` 写入，确认实际写入前均经过 DOMPurify 或 Mermaid 专用清洗；未发现新增绕过点。后端与第三方内容边界仍在总审计中。
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
- [x] 管理台格式对齐：对照 Alethicode 的 `Home.vue`、`Panel.vue`、`style.less` 与 `common.less`，Pythontopia 的侧栏/窄屏导航、面板、控件、表格密度、分页、蓝灰色 token 与圆角均已落到现有 3 个真实 API 页面；管理台定向组件/页面测试与 320px Chromium 抽样已有通过记录。Pythontopia 继续使用自身类型化 DTO 和真实业务接口；Alethicode 额外菜单对应的课程包、配置、判题服务等模型不属于“格式对齐”，不创建空页面或复制旧数据结构。剩余页面动作检查由 Web 动作矩阵跟踪。
- [x] 管理员真实浏览器用户管理流程：登录、搜索、停用/恢复、禁用账号登录拒绝；临时账号已清理。
- [x] 运维概览将五个已知后端动作键映射为中文建议，原始键保留在提示文本中。
- [x] KC-004 根知识点 AI 补题：从正常主线练习按钮发起请求，通过 Piston 自测和现有质量审查后写入共享题库为 `APPROVED/AUTOMATED`。实际请求 6 个 DeepSeek completions（前 5 个候选自测输出不符，第 6 个通过）；未提交答案或新增掌握证据。
- [x] KC-002 主线 AI 补题：从第 1 章第 3 节正常学习页发起 1 次生成请求，题目 `APPROVED/AUTOMATED`（审核 ID 83）；第二名学生从共享题库取题并服务端判题 3/3。临时答题账号及其掌握事件已清理，审核请求者因审计外键保留在本地数据库。
- [ ] 远征闭环：补齐有效先修解锁后的第 2 章以上前沿 KC 的 battle-compatible 题型并验证有效先修账号从服务端活动 run 刷新恢复；同时复核地图覆盖预检、超时取消、失败恢复、Boss 多阶段与客户端状态伪造边界。当前根 KC 审核库普通题为 KC-001 `debug` 与 KC-001/002/004/006 `kind=NULL`；新增 KC-001 普通 AI 题由真实主线流程生成并自动审核，另一学生账号正式判题 3/3，掌握度事件为 0.14。上述普通题仍不适配战斗节点 `dual_track`/`closed_loop`。KC-006 主线题由审核 ID 84 自动批准，第二名学生从共享库取题并正式判题通过；KC-011 也已有普通主线自动批准题，但尚无正式学生判题或掌握度证据。缺题时会按节点 `challengeKind` 生成并经质量门禁后自动入共享库。已修复地图默认预取绕开全局批准题库的问题：按章节/stage/KC/题型/难度先复用已批准题，再进入用户现有 READY 队列；`EndlessPregenServiceTest` 16/16。KC-001/002/004/006 属第 1 章根 KC，远征当前会过滤第 1 章；它们的题型库存不是该闭环的开局覆盖验收对象；尚无合格活动 run 的端到端题型生成与刷新恢复证据。地图不预检题库覆盖，生成失败仍可重试或结束本局。最新本地只读聚合为 21 条 `status=ACTIVE` run（16 challenge、3 map、2 over），有 1 个账号达到候选先修掌握门槛，但其当前没有活动 run。测试/历史账号构成未知，不能据此推断真实用户完成率；先修候选链包括 KC-004→KC-003→KC-008 与 KC-002→KC-005→KC-015；须通过真实判题达到门槛，再验证可恢复 run。
  - 10/16 审核库只读复核：第 2 章当前有 KC-010 `fill_blank` 与 KC-010/011/012/013 的 `kind=NULL` 批准题；第 2 章以上仍无 `dual_track`/`closed_loop` 题。查询只聚合章节/KC/类型/数量，未读题面、隐藏测试或用户身份；未创建题目、判题或学情记录。合格先修账号与活动 run 的真实闭环仍待完成。
  - 10/15 通过 `/mainline/stage/3` 的正常 AI 补题入口为 KC-001 生成题目，浏览器 API HTTP 200，`aiGenerated=true`、`reviewed=true`、页面错误 0；同账号重新从审核库取题后正式判题 3/3。题目及审核 requester 已保留在本地库；QA 用户 ID 697 因审核审计外键不能删除。该学生 KC-001 掌握度只有 0.14，远征候选要求先修 KC 达到 0.6；没有重复作答凑分，也未尝试创建不合格 run。

- [ ] Web 验收：学生、教师、管理员主要路由/动作矩阵仍需完成，逐项覆盖可见控件与成功/失败/空态。11 条主页面路由已有真实浏览器路由壳检查（0 页面错误、0 个 5xx）；当时为避免写入拦截了 `/api/game/bootstrap` 和认证刷新，因此不计为页面初始化或完整动作通过。刷新请求由每次整页加载时的 `hydrateFromStorage()` 正常触发，逐 URL 重载解释了多次请求；认证/角色守卫、管理员用户停用/恢复、World HUD 未评估 Twin → 主线地图另有真实验证。主线章节抽屉开合、进入学习页再返回通过 1/1；小镇 HUD/暂停菜单反复开关并进入工坊通过 1/1；主线章节键盘聚焦及 Enter 打开章节通过 1/1；指南页目录锚点更新 URL 并滚动到目标通过 1/1；本轮 `/library` 的「for循环」检索显示带页码证据，1/1；`/board?tab=classroom` 深链及班级→历史→今日页签切换通过 1/1；`/profile` 学习目标自评已更新，PUT 仅含 `scoreDelta`，`evidenceCount`/`confidence` 不变。`ProfileRoomPage.spec.ts` 已有页面层测试覆盖 Twin 主/备推荐与周报 insight/补强转到工坊；本轮空学情学生真实浏览器展开 `/profile-room` 近期变化、点击“生成下一步”并抵达 `/workshop`，工坊草稿已预填，周报 0 事件，模型/资源生成请求和页面错误均为 0。另一名临时学生通过真实 KC-016 编程题后，周报显示 1 条权威事件、1 个掌握变化；点击该 KC “补强”进入工坊并预填 KC 代码，没有模型请求或页面错误。临时账号（689、690、691）均已清理，KC-016 验收账号的判题与学情随账号级联删除。教师屋创建班级路径经真实浏览器验证：教师邀请码注册（201）、登录（200）、空班级态、新建班级并进入看板成功，页面错误 0；临时教师和班级分别核对为 0。此前新生点击“前往主线学习”到达 `/mainline-map`，页面错误 0，仅有认证刷新和学习状态读取，临时用户已清理。共享表格样式对齐已完成。管理台审核检索/驳回已有本地浏览器记录；当前无待审核项，不重复改变共享题库。本轮真实浏览器验证学生通过 `/home` 入班（HTTP 200），教师成员页看到并移除学生（HTTP 204），页面错误为 0，临时用户、班级和成员行均清理为 0；本轮另一真实 Chromium 路径完成班级任务发布与归档，HTTP 均为 2xx，页面经服务端刷新显示 `ARCHIVED`；最终直接 SQL 复核因 `status` 未限定表别名而失败，故 DB 状态交叉核验与页面错误计数不记为通过。临时用户、班级、任务清理计数均为 0。剩余 Web 页面动作与窄屏检查仍待验收。
  - 10/15 复习大厅新生真实浏览器：空周报、服务端“尚未评估”基础 KC 推荐均显示，点击费曼入口到 `/feynman`；评估/Twin API 为 200，页面错误 0、模型请求 0，临时用户 700 已清理并确认无残留。
- [x] Web 错误态抽样：真实 Chromium 临时学生打开 `/chapter-test/99`，看到明确 1–7 章范围提示并点击返回主线地图；测评 API、模型 API、判题 API 为 0，页面错误为 0，临时账号已删除。有效章节自动调用 `prepare` 且可能触发出题，因此本次只走无效章节分支。
- [x] Web 页面动作续验：题库按 KC 筛选、题号搜索、清空恢复和“去做”进入训练题均由真实浏览器验证；复盘大厅未评估 Twin 推荐已修复并复验进入主线地图。`/examples` 已退役，路由静态重定向到 `/problems` 并保留 `kcCode` 筛选；其题库筛选动作已有浏览器证据。相关临时账号已清理，已验证动作页面错误与模型请求均为 0。
- [x] 题库深链边界：有效 KC 暂无编程题时保留 `kcCode` 并显示“0 题”；未知 KC 仍回退到全部。回归先 RED 后 GREEN，`ProblemListPage` 10/10、typecheck、定向 ESLint 通过。修复后真实 Chromium 验证 `/examples?kcCode=PY43-KC-001` 跳转并保留所选 KC；320px 下无横向溢出，页面错误与模型请求均为 0。
- [x] 窄屏抽样：320px Chromium 检查 `/mainline/stage/2`、`/problems`、`/profile-room`、`/library`，页面宽度均未溢出；学习分身“近期变化”折叠区在窄屏可展开。页面错误与 5xx 为 0，没有业务写入或模型请求，临时账号已清理。此为抽样，不代表完整路由矩阵。
- [ ] 工程收尾：按全 Web 审查清单继续修复可复现的命名、交互、错误态、可访问性和窄屏缺陷；strict TypeScript 源码扫描未发现 `any` / `as any` 或空命名 `void` 函数；工坊 `quiz_finished` 的过时 TODO 已改为说明客户端事件不作可信证据，未改运行逻辑。已对照 Google Java Style、Vue 官方风格、typescript-eslint 命名规则与 OWASP Logging Cheat Sheet；仅整理本轮新增 Java imports 并应用敏感日志要求。全仓 Spotless 与本轮触及文件的定向检查都发现既有格式差异；未保留格式化造成的无关改动，也未做批量格式化。
- [x] Maven 全量失败已做报告归因：记录的 7 个错误里，6 个是 Testcontainers Docker 动态宿主端口绑定失败，1 个是长期仿真的 PostgreSQL 连接重置；0 个断言失败。尚未修复 Docker/连接环境，也未重跑全量套件。
- [x] Tutor SSE 长等待的本地运行态根因已定位并修复：Graph 源文件比正在运行的容器新，Uvicorn 命令没有自动重载，旧进程未加载心跳改动。开发 Compose 限定监视 Graph 源码目录并启用 reload；Compose 契约先因缺少 reload 断言失败，修复后通过；重建容器后 `/healthz` 200、容器 healthy；临时 `.py` 文件触发 WatchFiles 重载后删除，服务仍健康。
- 范围外/未核验：生产公网边缘限流、Piston ACL 与 ECS 运行态按用户要求不作为当前任务重点；Piston API 本身不验证 Bearer token，只有生产侧私网 ACL/鉴权代理能关闭该风险。当前仅维护本地配置和源码边界证据，不访问远端或部署。
- [ ] Twin 效果：在不伪造画像的前提下设计并执行 Twin A/B 与 7 日延迟复测；样本不足时保持待测，不宣称提升。
- [ ] 后续增强（已按精简计划暂缓）：未来分岔的真实学习结果评估与 BKT 概率模型不属于当前闭环验收门槛。
- [x] DeepSeek Web Tutor 全链路文本验收：运行态先出现 Graph 意图路由 401，核实为 Java 缺内部令牌、Graph 使用 `.env` 占位令牌；按 `./dev.sh` 重启后两端共享临时强令牌。单次 Playwright 请求命中 `/agents/intent/route` 与 `/agents/tutor/ask/stream`，均 HTTP 200，非空文本断言通过（1/1，7.2 秒）；临时账号已清理。不要重复 provider 探针或图片调用。
- [ ] 性能：最后执行；功能、安全和工程验证收敛后测量出题 P50/P95、缓存命中与失败恢复，不把目标值写成实测。


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
- 附件要求中的 Pythontopia Tutor AutoGen 替换已经满足：当前 Tutor 使用固定阶段 AutoGen，保留本仓 SSE、授权、附件与 run checkpoint 契约；Alethicode 的 V124/thread dispatcher 不兼容，不复制。
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

## Task 6: Audit Web interaction and admin table behavior

**Files:**
- Check: `frontend/src/pages/admin/`, `frontend/src/components/admin/`, major student/teacher Web routes, shared loading/error/empty-state components
- Test: existing Vitest and Playwright suites

**Steps:** Build a route/action checklist from current routes; use Playwright to click visible actions and verify navigation, success/error/loading state, focus and narrow-screen overflow. Fix only confirmed dead actions/layout defects using existing components. Do not add pages without a real backend contract.

**Status:** Partial: fresh Twin → mainline navigation, empty-state Twin weekly plan → workshop, evidence-backed KC remediation → workshop, teacher classroom creation/dashboard entry, and 320 px overflow checks passed. The remediation test used a genuine KC-016 server-judged pass to create the weekly evidence, then deleted the temporary student and its data. Both Twin actions prefetched drafts without Tutor/Graph/resource-generation calls or page errors. The teacher browser check registered a disposable teacher (HTTP 201), logged in (HTTP 200), verified the empty state, created a classroom and entered its dashboard with no page errors; separate DB queries confirmed the teacher and classroom were cleaned up. The shared draggable hook now lets ARIA button clicks pass without starting a drag. Admin component/page suites passed (8 files / 36 tests), admin user API and router suites passed (2 files / 22 tests), and current full frontend typecheck, lint, and production build passed. In this continuation, nine protected routes redirected an anonymous browser to `/login`; a student was redirected away from four admin/teacher routes. A temporary local admin then searched a temporary user, disabled it (login returned 401), re-enabled it (login returned 200), and both accounts were deleted. At 320 px the admin page document stayed 320 px wide with no page errors. W3C table and pagination semantics are present. Shared table headers are now centered, the radius uses the existing 16px panel token, and pagination is centered while retaining manual page jump; the five affected table/user/review specs pass (26 tests). Five known operations keys now render as readable Chinese recommendations, with raw values retained in tooltips; `AdminOperationsPage` passes 5 tests. Fresh `npm run typecheck` and file-scoped ESLint pass. Fresh real-browser action checks passed HUD/pause-menu → workshop (1/1), keyboard focus/Enter → mainline chapter (1/1), and guide TOC anchor navigation (1/1); each used one disposable student, deleted after use, with no AI/model request or judging. The latest Chromium run verified student join-by-code (HTTP 200) and teacher member listing/removal (HTTP 204), with zero page errors and zero residual test rows. A separate disposable classroom task was published and archived (both HTTP 2xx); after the store refreshed from the server, the UI showed `ARCHIVED`. The follow-up DB query failed because its joined `status` column was unqualified, and the script stopped before reporting its page-error counter; cleanup still verified zero residual user/classroom/quest rows. The current continuation also verified teacher join-code reset: the old code was rejected (404), the new code joined successfully (200), and cleanup left zero temporary user/classroom/member rows. Remaining work: major route/action matrix and narrow-screen checks outside previously tested pages. Admin review search/rejection has earlier real-browser evidence; the current queue has no pending item, so a new decision-write check is deferred until there is a safe disposable record.

**Engineering scan:** The current Web `src/` tree has no explicit `any`, `as any`, or empty named `void` functions in the scanned TypeScript/Vue source. Empty arrow callbacks found were two fullscreen error catches and intentionally pending test promises. No broad rename or formatting pass was justified by the findings.

## Task 7: Run final affected-surface verification and report measured limits

**Commands:**
- `cd backend && mvn -q -Dtest=SecurityConfigCorsTest,StoryboardCompositionServiceTest test`
- `cd services/pythontopia-graph && .venv/bin/python -m pytest -q <focused tutor/boss tests>`
- `cd frontend && npm run test -- <affected specs> --coverage.enabled=false && npm run typecheck && npm run lint && npm run build`
- `git diff --check` and `deploy/scripts/test_https_forwarded_proto_contract.sh`

Run performance sampling last. Separate deterministic API timing from Graph/DeepSeek and formal judging; report request counts and clean up disposable rows. Never describe prior or synthetic data as current performance evidence.

**Status:** Affected-surface checks pass: frontend Twin/Boss recovery, admin components/pages/API/router, typecheck, lint, and production build; backend CORS/media focused tests (8), Graph Boss suite (257), Graph full suite (1871 passed, 1 skipped), RAG full suite (158 passed, 2 skipped), Nginx contract checks (9), and `TutorDailyRequestLimitServiceTest` pass. The current continuation added a combined five-file frontend regression run (76 tests). Surefire XML confirms the recorded serial Maven run had 0 assertion failures and 7 environment errors: six Testcontainers containers could not bind random Docker host ports (`address already in use`) and the long-term simulation lost its PostgreSQL connection. No full Maven rerun was made during this continuation. A fresh single Web text flow now passed after aligning local Graph/Java internal-token runtime configuration; it reached both intent routing and Tutor SSE with HTTP 200 and returned non-empty text. A separate KC-004 Web question request passed after sandbox alignment; this used one UI request and six DeepSeek completions because the first five candidates failed output self-tests. Performance remains intentionally unmeasured until the remaining functional/runtime checks close.

## Task 8: Protect classroom join codes per authenticated account

**Status:** Complete in `36bb7abe`. Existing Nginx source-IP rate limiting remains; backend additionally limits each signed-in account to 30 code attempts per 10 minutes before database lookup. RED/GREEN service tests, HTTPS proxy contract, and Nginx dual-protocol checks passed. `JoinCodeAttemptThrottleTest` and `ClassroomServiceTest` passed. Process-local state assumes the documented single-worker deployment; ECS runtime was not inspected.

## Task 9: Redact unexpected Tutor workflow errors

**Status:** Complete in `36bb7abe`. Unexpected exceptions are logged server-side with stack traces and returned to the browser as a fixed `TUTOR_WORKFLOW_FAILED` envelope. The regression test reproduced credential-like text in the old SSE response before the fix. Tutor workflow, AutoGen, attachment, coach graph, task events, ask stream, and image-input suites passed (93); Ruff passed.

## Current environment note

The configured SSH destination resolves through the TUN default route. The repository's WSL instructions require adding a direct host route, but this session cannot obtain the local sudo password, so no ECS SSH connection was attempted. Production Piston and Nginx runtime state remains unverified. Static production configuration requires the Piston URL/version/token and restricts the URL to HTTPS or a private address; the Piston API does not validate its Bearer token, so the real network ACL or an auth-checking proxy must be confirmed before production sign-off. Repository configuration was not changed.
