# Pythontopia 执行日志

> 本文档实时记录每个 working session 的状态，作为跨会话的"工作记忆"。
> 一定要常翻 `AGENTS.md` 与 `/home/cypress/Pythontopia/plan/` 下的阶段计划，避免方向偏离。
> 本文件下方的用户立场、Provider 决策和阶段列表是 2026-05 的历史记录，已由当前 `AGENTS.md` 与后续 CHANGELOG 更新覆盖。当前任务以 Web 为主，AI provider 为 DeepSeek；讯飞 Spark 与媒体出口已关闭。不要据旧日志启动非 Web 客户端或把旧阶段当作待办。

## 2026-10-15 当前续接

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
