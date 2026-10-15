# Pythontopia 执行日志

> 本文档实时记录每个 working session 的状态，作为跨会话的"工作记忆"。
> 一定要常翻 `AGENTS.md` 与 `/home/cypress/Pythontopia/plan/` 下的阶段计划，避免方向偏离。
> 本文件下方的用户立场、Provider 决策和阶段列表是 2026-05 的历史记录，已由当前 `AGENTS.md` 与后续 CHANGELOG 更新覆盖。当前任务以 Web 为主，AI provider 为 DeepSeek；讯飞 Spark 与媒体出口已关闭。不要据旧日志启动非 Web 客户端或把旧阶段当作待办。

## 2026-10-15 当前续接

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
- 建立总任务清单与优先顺序于 `docs/plans/2026-10-15-web-system-implementation-plan.md`；最新本地检查点 `36bb7abe`，未推送/部署。下一步聚焦远征活动 run 恢复和 Web 路由动作矩阵，不重复验证已通过的模型 provider 能力。
- 为 Web 路由动作矩阵补无模型浏览器检查：9 个学生保护入口在匿名状态全部去 `/login`；真实新注册学生访问 `/admin/operations`、`/admin/users`、`/teacher`、`/teacher/review` 均去 `/world`。临时账号 id 640 已退出并删除，数据库确认用户行数为 0。现有公网验收登录守卫/登录页测试 2/2 通过。
- Piston 生产配置静态核对：Compose 要求配置 Piston URL、Python 版本和 token，ECS 预检仅允许 HTTPS 或契约认可的私网 URL；`bash deploy/scripts/test_piston_http_host_contract.sh` 已通过。Piston API 本身不验证 Bearer token，生产必须通过私网 ACL 或鉴权代理限制访问。ECS 运行态未核实；本机 SSH 直连路由需要不可用的 sudo 密码，未修改远端配置。
- 查看本地课程可用题：无先修 KC-001/002/004/006 都没有静态编程题，也没有已批准 AI 编程题；所以无法用新生合法创建远征 run 验证刷新恢复，保持待办而不补造 mastery。
- 生产只读 SSH 未尝试：`ip route get` 显示目标走 TUN；按 AGENTS 需要为当前 eth2 网关加 host route，但 sudo 要求未提供的本机密码。记录阻塞后转回本地工作，未连接/修改 ECS。
- 在 `frontend/src/` 扫描显式 `any`/`as any` 与空命名 `void` 函数，未发现违规项；空箭头只用于 fullscreen Promise 错误捕获及测试待决 Promise。不为统一表面风格批量重命名或格式化。

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
