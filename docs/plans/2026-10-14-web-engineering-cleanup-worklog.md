# Web 工程可读性与规范清理工作日志

## 2026-10-14 后端全量复验

- 最新 `mvn -o test`：1392 tests、0 assertion failures、7 errors。6 个 Testcontainers PostgreSQL 容器在 Docker 宿主端口映射时报告 `address already in use`，长期仿真类在全量运行中连接被拒。
- 将 7 个失败类隔离复跑：长期仿真、DecisionTrace、CoursePack、EvidenceMemory、LearningEvidenceGraph、MemoryRetrievalSemantic、Recommendation 合计 20/20 tests 通过。全量套件仍不稳定，未标记通过；未修改系统端口范围或 Docker 配置。

## 2026-10-14 管理后台主题隔离

- Playwright 复核发现全局像素按钮阴影覆盖到管理端；在管理 layout 作用域取消 `button` 阴影与按压位移，恢复 Alethicode 管理控件外观。
- 红绿验证：管理样式回归先失败，再通过；管理台 5 个测试文件 / 31 项、typecheck、定向 ESLint 通过。三个页面桌面 1440px/窄屏 320px 无页面溢出，移动抽屉和状态对话框交互通过。

## 2026-10-14 DeepSeek 与 KC 出题真调用续验

- `./dev.sh` 的运行中 Spring 环境确认应用级 AI 次数限制关闭；部署配置仍保留账号/全站每日限制。模型供应商余额和自身限流不受此开关控制。
- 经正式 Tutor SSE 路径完成新文本与 PNG 图片 DeepSeek 调用：文本回显“青竹 83”，图片识别为黄色背景、蓝色正方形；答案均通过 Tutor 会话接口确认已持久化。Graph 最近观察窗口共 13 条 provider completion HTTP 200 日志、无 provider error；该数量不是供应商账单或 token 用量。
- 正式 Boss 学生接口生成了 `PY43-KC-001` Stage 2/3 候选；Graph 自检通过意味着结构、grounding、相似度检查及 Piston 参考程序用例通过，但不代表题目适合据此更新历史 KC 掌握度。候选多为回显或拼接固定标签，审核 API 已将 QA 的 5–9 号记录拒绝，未进入 approved bank。
- Grounding 新增对纯 input/print 回显的 AST 拒绝（含额外固定标签），先看到新增测试失败，再通过修复；Boss Graph 228 tests、Ruff 全量通过。临时 run 已清除；QA 账号和拒绝审计记录保留在本地数据库。
- `useChallengeJudge` 轮询从 5 秒延长到与 Boss Graph 对齐的最多 180 秒（5 秒间隔、36 次）；新测试在 PENDING 超过 5 秒后变 READY，验证不会重复请求 challenge。远征相关 3 个 Vitest 文件 / 9 tests、typecheck 和改动文件 ESLint 通过。
- 未测 DeepSeek 实际账单/token、未发布上述题目、未完成全量 Maven 与最终 Phaser 多阶段真实浏览器验收。一次进程环境检查的工具输出可能包含运行时凭据，建议轮换可能出现在输出中的凭据。

## 2026-10-14 全量验证续跑

- 前端全量 Vitest 270 files / 2555 tests 通过；全量 ESLint 0 errors / 7 warnings；`npm run build` 成功。构建仍有 Pyodide Node 内置模块 externalize、`eval` 与 chunk size 警告，性能工作按要求后置。
- Graph 全量 1836 passed / 1 skipped，Ruff 全量通过。后端最新全量 `mvn test` 报告 1383 tests、0 assertion failures、7 errors：6 个 Testcontainers Postgres 类因 Docker 宿主机端口已占用未能启动，长期仿真随之 JDBC 连接失败。
- 6 个冲突类串行复跑 19 tests 全通过，长期仿真单独复跑 1 test 通过；本轮相关后端回归测试最新 86 项通过。故本轮改动的目标用例有通过证据，但全量 Maven 仍因 Docker 端口冲突失败。
- 修正 AI 题的画像可信度：待教师审核的题可以作为练习，但不会发布 KC 掌握事件；审核状态不可读取或题目内容未精确匹配已批准版本时 fail closed，判题结果仍返回并提示未计入画像。TDD RED 先复现旧行为，当前 BossService + 审核仓储、Tutor 限额、预生成、Graph client 和周报关键回归共 86 tests 通过。
- 仍未完成：Phaser Boss 最终多阶段真实浏览器判题旅程；Twin A/B 与 7 日延迟复测；DeepSeek 供应商账单/token 核对；生产 Piston 网络隔离与 ECS 公网验证。一次早期进程环境输出可能含运行时凭据，建议轮换可能出现于该输出的凭据。

## 2026-10-14 远征新手开局复核

- 复核真实新手路径：后端权威策略在先修证据不足时退回最早前置 KC；该 KC 没有课程编程题，远征会拒绝开局。通过真实本地 Piston 提交，临时学生账号的 KC016 题 476 判题通过，但第 5 章远征仍返回 422，因为 KC001–015 还没有已验证掌握证据。课程页阅读不会生成客观掌握度；已有工坊资源小测会发布可追溯的掌握事件，但新建资源依赖 Graph。本地 Graph 停止且 DeepSeek 请求账务未核，本轮不调用模型，不伪造掌握度，也不绕过先修条件。
- 只修正 `EXPEDITION_NO_PRACTICE_PROBLEMS` 的说明，使其引导学生先完成前置知识小测/练习，或切换到包含课程题的章节；补充回归后，先观察到旧提示导致测试失败，再运行 `mvn -o -Dtest=BossServiceTest,AdaptivePracticePolicyTest,LearnerTwinServiceTest test`：55 项通过。改动的两个 Java 文件通过定向 `spotless:check`。
- 通过只输出聚合计数的容器日志筛选，确认 Graph 有 32 次 DeepSeek completion HTTP 200（4 次 Tutor/workflow、28 次远征预生成）；另外此前有 4 次失败尝试未获得 HTTP 响应，约 36 次尝试，超过 30 次限制。供应商 token/费用明细无法从本地日志推断；Graph 保持停止，没有新增请求。
- 清理本探测创建的三个确切 `codexboss` 测试用户（其中一个仅有一条通过判题的测试提交）；删除前确认其受限外键表没有引用。
- 尚未完成最终 Boss 多阶段真实浏览器判题演出、工坊前置小测的 live Graph 路径、DeepSeek 供应商用量核对、生产 Piston 网络隔离、稳定的全量 Maven 复跑和 Twin A/B/延迟效果验证。

## 2026-10-14

- 参考 [Vue Style Guide](https://vuejs.org/style-guide/)、[typescript-eslint 命名规则说明](https://typescript-eslint.io/rules/naming-convention/) 与 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)，沿用仓库现有约定：Vue 多属性元素分行，TypeScript/Java 使用项目既定标识符命名，Python 继续使用 snake_case。
- 运行前端全量 ESLint 后有 177 条 warning、0 error。使用现有 ESLint 自动修复模板属性顺序/换行、缩进及多行内容布局，warning 降至 7、error 保持 0；这 7 条为可选 prop 默认值提示和账号隔离测试中的多组件 mock。`assessed` 不设空数组默认值是有意保留：未传入时仍按 mastery keys 兼容旧调用方。
- 对 `@typescript-eslint/naming-convention` 做只读试运行：30 条命中主要来自测试重置 hook、Phaser 场景类/浏览器 API 构造器、`ref_`/`route_` 局部名和测试标记。没有把一刀切规则升为仓库错误，避免误报正常的外部名称；本次新增 TypeScript/Vue 与 Python 名称遵守仓库约定，API DTO 的 snake_case 字段不改名。
- 验证：Web 全量 Vitest 265 files / 2523 tests 通过；TypeScript typecheck 与生产构建通过。Graph 全量 1822 passed / 1 skipped，Ruff 全仓通过；`git diff --check` 通过。
- 生产构建保留 Vite 对 Pyodide Node 内置模块 externalize 和若干大 chunk 的现有 warning；未调整包拆分或运行时加载策略，因为这属于性能工作，按用户要求后置。
- 后端当前快照全量 `mvn test` 为 1356 tests、0 failures、7 errors，6 个 pgvector Testcontainers 在映射宿主端口时遇 `address already in use`，另 1 个长期仿真 JDBC reset；单独重跑后长期仿真通过，但 6 个容器用例仍撞端口。当前本机 ephemeral port range 仅为 `44620 48715`，数据库流量产生的 TIME-WAIT 端口与 Docker 随机映射重叠。本轮没有改系统网络配置；Boss 改动的 61 项定向 Maven 测试通过。
- 全仓 Spotless 仍有历史 Java 格式债务。此次聚焦 Web，不做 622 个 Java 文件的批量重排，也不重命名跨语言 API DTO。Twin 学习成效对照、完整 Boss 最终节点的浏览器实战演出、生产 Piston/ECS 与公网验证仍未完成。

## 2026-10-14 收尾复验

- 当前快照 Web Vitest 为 266 files / 2533 tests，通过；TypeScript typecheck 通过；全量 ESLint 0 errors / 7 warnings，warning 仍限于未改文件中的可选 prop 提示和账号隔离测试 mock。远征地图/挑战入口 Chromium E2E 2 项通过，`git diff --check` 通过。
- 本轮改动涉及的后端类隔离复跑 78 项通过。全量 Maven 为 1359 tests、0 assertion failures、7 errors；6 个 pgvector Testcontainers 在启动阶段失败，另一个长期仿真用例因 PostgreSQL 连接拒绝失败。没有将全量结果标记为通过。
- 工程命名/格式遵循 Google Java、Vue 与 typescript-eslint 官方指引；保留已有 7 条 lint warning，不在此次功能收尾中扩大成全仓格式重排。功能验证完成后，经 Vite 代理对远征建局做 12 个计时样本，P50 20.14 ms、P95 24.75 ms；不包含模型生成与正式判题延迟。

## 2026-10-14 增量复验

- 对齐管理台侧栏导航字号至 Alethicode 当前的 15px，并用样式契约测试固定这一差异。
- Phaser Boss 场景的成功反馈测试扩展为玩家冲刺与像素命中爆点；复用现有素材与单 Phaser 实例。
- 当前相关 Web 回归 4 files / 14 tests、typecheck、production build 与改动文件 ESLint 通过。未做管理台全栈截图，也未完成最终 Boss 多阶段真实判题浏览器旅程。

## 2026-10-14 AI 题自动入库与 DeepSeek 全栈验证

- 沿用 `boss_problem_review`，V213 增加 `review_source`。已审核 AI 题只有在章节、阶段、KC、请求难度一致且含参考解、至少 3 条覆盖样例与隐藏测试的情况下自动批准；事务级 advisory lock 保证同题并发请求去重。
- Boss 现场题和 Endless 预生成均复用仓储入口。教师/管理员审核页显示自动来源，并可确认或撤下自动批准题；人工审核终态保持不可改写。
- 真实验证先发现开发栈未启动 Piston，DeepSeek 的 6 次真实 HTTP 200 候选都因 `sandbox_unavailable` 被审查拒绝并回退 Golden。改用 `./dev.sh --sandbox` 后，真实候选通过 Piston 后入库；另一名学生命中相同共享题，无第二次生成请求。审计 requester 随机匿名账号保留，复用账号已清理。
- 验证：审核仓储、V213 schema、Boss、预生成与服务定向后端测试 76 项通过；审核 DTO 服务类 7 项复跑通过。管理端、教师端和 API Vitest 18 项通过，typecheck、改动文件 ESLint 通过；本地数据库版本为 V213。
- 本轮没有跑全量 Maven、供应商账单/token 核对、Twin A/B/7 日学习效果或生产 Piston/ECS；性能按用户要求后置。

## 2026-10-14 收尾复验

- 修复 Tutor Markdown 外站图片隐私：DOMPurify 清洗后只保留 data:image 与同源图片，外站图片替换为 alt 文本；新增回归测试。
- 管理台用户停用确认可用 Escape 取消、确认后刷新列表并恢复焦点；列表请求失败会清空旧数据；运维页支持手动刷新。Playwright 对用户页和运维页完成桌面与窄屏检查。
- 依据官方 [Vue Style Guide](https://vuejs.org/style-guide/)、[Google TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html) 和 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) 核查新增/触及代码；保留项目 PascalCase、camelCase 与 strict TypeScript 约定，不对全仓强推 Google 专属规则或批量重排 Java。
- 最新 Web 验证：269 files / 2541 tests、typecheck、production build 通过；ESLint 0 errors / 7 warnings（2 条未改组件可选 prop 提示、5 条账号隔离测试多组件提示）；`git diff --check` 通过。生产构建仍报告既有 Pyodide Node 内置模块 externalize、`eval` 与大 chunk 提示。
- 全量 Maven 最新结果：1382 tests、0 assertion failures、6 errors；5 个 Testcontainers 在 Docker 分配随机宿主端口时报 `address already in use`，长时课堂模拟测试 PostgreSQL 连接 reset。当前没有断言失败，但全量 Maven 不是通过状态；受影响容器类与长时测试此前的隔离重跑记录为通过。
- Tutor 真调用的历史记录为 8 次 provider 尝试（包括失败连接与低 token 探索），文本流、图片流及 workflow 结构化图片路径均有成功响应。后续本地 Graph 端口复核发现 `18091` 实际健康；远征预生成意外产生 28 条成功的 chat-completion HTTP 日志。合计可能为 36，可能超过 30 次上限；停止了 Graph 容器，不再发模型请求。日志不是供应商账单，token/费用总量待核对。
- 最后完成无模型本地性能采样：Vite 代理到 Spring 的远征建局 2 次预热 + 12 个计时样本，P50 24.26 ms、P95 31.65 ms、25 节点；无 DeepSeek、Graph 出题或正式判题调用。演示账号此前没有活动局；采样后删除全部临时 run，GET 再查为 204。
- 尚未完成：完整最终 Boss 多阶段真实判题浏览器旅程、Twin A/B 与延迟复测成效、生产 Piston/ECS/公网；SSRF 允许域的真实公网页面因本地 DNS 将域名解析为非公网 IP 而未启动，防护按预期 fail closed。

## 2026-10-14 工程安全收尾复验

- 凭据处理：RAG 初始化脚本不再提供默认管理 token 或全量 export `.env`；token 通过 curl 配置输入流传递，不出现在 curl argv。Graph 优化 CLI 删除 `--api-key` 参数。分别用 mock curl 和参数测试确认缺 token 时不发请求、凭据不在进程参数中；OWASP 建议生产使用托管 secret store，因为环境变量仍可能被同机进程或日志获取。
- 媒体链路：图片 provider 关闭时提交立即返回不可用，不创建悬挂任务；TTS 关闭时生成明确静音产物且不报告降级，已开启 TTS 的异常不再吞掉。后端定向 16 项通过。
- 管理台：共享面板移动断点收起工具栏空白；桌面 1440px 双列、390/320px 单列且无水平溢出，手动刷新实际发出 API 请求（布局浏览器检查使用模拟响应）。账号隔离、教师创建班级/学生加入/证据任务提交与教师看板两条 Playwright 流程通过。
- 最新验证：Web `269 files / 2541 tests`、typecheck、build 通过；ESLint 0 errors / 7 existing warnings；Graph `1826 passed / 1 skipped`（跳过项依赖可选 ragas/VertexAI 包）；全量 Maven `1369 tests / 0 assertion failures / 7 errors`，6 个 Docker/Testcontainers 宿主端口绑定错误及 1 个长时仿真 PostgreSQL reset。
- 工程规范依据补充 [W3C WCAG 2.2 Reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html)、[Linux proc(5)](https://man7.org/linux/man-pages/man5/proc_pid_cmdline.5.html)、[OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html) 与 [typescript-eslint naming-convention](https://typescript-eslint.io/rules/naming-convention/)；用 320px reflow 检查和 argv 凭据测试验证相关选择。继续遵守现有跨语言命名约定，不加宽泛规则、不重排无关 Java。
- 性能最后复测走 Vite 代理创建远征：2 次预热 + 12 个样本，P50 20.81 ms / 最近秩 P95 22.02 ms，地图 22–32 节点；未触发模型或正式判题，14 个临时 run 均 DELETE 204。该值只代表建局 API，不代表 Boss 出题/判题或生产负载。
- 真实 API 探针中，章节 1 因无可用课程编程题返回 `EXPEDITION_NO_PRACTICE_PROBLEMS`；章节 5 的 `PY43-KC-041` 成功创建地图并清理。没有把它计作 Boss 完整浏览器旅程。
- 剩余未验证：最终 Boss 全流程真实提交演出、Twin A/B 与延迟复测、生产 Piston/ECS/公网；全量 Maven 受本地容器启动与数据库连接环境限制未整体通过。临时 E2E 账号及教师课堂演示数据留在本地开发库。

## 2026-10-14 本轮增量复核

- 媒体图片 provider 禁用检查提前到缓存读取之前，并增加缓存视频不得绕过禁用策略的回归；停用的配音 Agent 显示回退到通用工作节点。管理台两个列表在刷新失败时清空旧快照，避免继续对陈旧数据执行操作。
- 按 TDD 先复现以上四处缺陷，再修复；媒体与管理台定向 Vitest 28 项、`MediaJobServiceTest` 通过。前端全量 Vitest 269 files / 2542 tests、typecheck、build 均通过；ESLint 0 errors / 7 个既有 warning。Graph 全量为 1826 passed / 1 skipped。
- 真实浏览器注册后打开 `/expedition?focus=5`，创建了 25 节点地图并实际取得 Golden 题目，未出现浏览器 JS 错误。本次还尝试继续解题，但题型/题目随地图变化，最终 Boss 多阶段判题与 Phaser 受击演出没有完成端到端确认；测试账号留在本地库。
- Maven 最新全量记录为 1369 tests、0 assertion failures、7 environment errors；串行隔离后仍有 3 个 Testcontainers 宿主端口绑定错误。未把 Maven 全量标记为通过。DeepSeek 本轮无新增请求，真实请求累计数仍为 8。
- 最后在停止 Graph 后复测本地 Vite → Spring 建局：2 次预热 + 12 个计时样本，P50 37.80 ms、最近秩 P95 65.70 ms，地图 22–29 节点；14 个临时 run 均删除（HTTP 204），活跃 run 最终检查为 204。之前 Graph 运行期间的两轮 P95 为 558.20 / 2132.90 ms，可能受并发预生成影响。建局接口自身是确定性 KC 聚合与地图生成，没有调用模型；样本不代表 Boss 出题、正式判题或线上负载。
- Graph 曾在 `localhost:18091` 健康，本轮远征预生成意外产生 28 条成功的 chat-completion HTTP 日志；与此前 8 次 Tutor provider 尝试累计可能为 36，可能超过用户 30 次上限。已停止 Graph，且不再发模型请求；供应商账单/token 总量仍需核对。此前注册的 `bosscheck.*` / `perfcheck.*` 本地测试账号留在开发库。

## 2026-10-14 最后增量复验

- Tutor 执行轨迹复用现有 Pyodide 浏览器解释器；保存首个样例 stdin 的空格和换行，失败时明确提示且不发起服务端/Graph 请求。该卡片标明仅供本地预览，不影响判题和掌握度，也不提交工作流反馈。
- 运维页审核路径仅对白名单站内路由启用 RouterLink，未知路径维持纯文本；运维侧栏改用内联 SVG 语义图标。运维数据刷新失败时不保留上次快照。
- 最新全量 Web 结果：Vitest 269 files / 2546 tests、`npm run typecheck`、`npm run build` 通过；`npm run lint` 0 errors / 7 warnings，均位于未改组件或账号隔离测试。构建仍报告已有 Pyodide externalize、eval 与大 chunk 提示。
- Tutor 轨迹测试 mock 了 Pyodide 服务，未验证真实浏览器解释器加载；无模型性能样本没有覆盖此次前端改动，未重测。没有发起 DeepSeek 请求；潜在 36 次请求事件与未知供应商账单仍为风险，Graph 保持停止。

## 2026-10-14 管理台审核页补测与边界复核

- AI 出题审核页补充页面级回归，覆盖搜索与状态筛选的服务端参数、审核意见必填、基于当前 `version` 提交决策，以及加载失败时清除过期行。管理台页面测试 4 文件 / 9 项通过；随后前端全量 Vitest 270 文件 / 2549 项通过；`npm run typecheck`、新增测试 ESLint 和 `git diff --check` 通过。没有变更业务 API 或视觉布局。
- 安全复核未确认 P0 或本地可复现的代码级 P1。定向 Maven 140 项、Graph 判题 37 项、Compose 沙箱安全契约通过。生产 Piston endpoint 的私网 ACL/代理 token 校验仍需部署证据；本地 Compose Piston 仅监听 localhost 且阻断外联。媒体生成禁用状态下，队列拒绝可遗留 PENDING 属于 P2。
- Alethicode 比对确认 Pythontopia 已具备固定阶段 AutoGen Tutor，应该保留 Pythontopia 的 API/SSE、V199 会话级检查点和附件/事件契约。管理台布局遵循相同视觉约定，但只保留本项目有真实 API 的运维、用户与题目审核页。
- 未发 DeepSeek 请求。此前本地日志仍显示约 28 条远征预生成请求，加上 8 次 Tutor/provider 尝试可能达到 36；账单与 token 总量未核。Graph 保持停止；完整 Boss 多阶段实战和 Twin A/B/延迟效果仍未验证。

## 2026-10-14 AI 配额预留时序修复

- `AiBudgeted` 原 MVC 拦截器在 Spring MVC 解析/校验参数和控制器角色检查之前预留额度，导致格式错误或越权请求也消耗配额。改为方法拦截器，在请求参数验证后检查认证用户及端点允许角色，再预留额度；未新增表或迁移。
- 去掉使用此配额标记且依赖 `@Validated` AOP 参数校验的控制器类注解，改由 Spring MVC 原生方法校验在方法拦截器之前执行；新增 `HandlerMethodValidationException` 到统一 400 错误响应的映射。
- TDD 回归在修复前确认有效、无效和教师越权请求都会预留；修复后后端定向 35/35 通过，范围内 Spotless 与 `git diff --check` 通过。
- Maven 全量 1393 tests、0 failures、6 环境错误；5 个 Testcontainers 失败类隔离 16/16、长期仿真类隔离 1/1 通过。剩余环境问题仅在全量串跑时触发，尚未取得稳定全量全绿结果。
# 2026-10-14 — 本地模型额度与 KC 出题闭环续接

- 最新用户指示覆盖此前“达到 30 次就停止”的本地开发约束：`./dev.sh` 明确将 `PYT_AI_REQUEST_LIMITS_ENABLED` 默认设为 `false`；部署默认值仍为 `true`。已从运行中的 Spring 进程环境读取到 `false`。
- 原因复盘：Graph 之前只收到 KC code，缺失真实课程上下文，导致章节关键词放行无关题。新增 pack 43 单 KC 查询，BossGraphClient 传递名称/摘要；Graph 将其注入 prompt，并从摘要拆出具体术语做接地。已有未知 KC 查询会 fail closed，BossService 保留 Golden/课程题回退。
- 真实模型结果：首次全栈出题有 5 个 DeepSeek HTTP 200 候选因旧的“必须逐字出现 KC 标题”检查或章节语法限制被拒，最终回落 Golden。一次直接 DeepSeek 检查发现模型产物确实用了 KC 摘要中的“神威·太湖之光”，而标题没有逐字复述 KC 名称；因此接地改为标题或摘要术语匹配，并把“至少使用摘要具体术语”写入 prompt。
- 修改后真实 `POST /api/world/boss/1/problem` 成功返回 AI 编程题，`reviewed=true`、`aiGenerated=true`，题面提及中国超级计算机及“神威·太湖之光”；Graph/Piston 自测通过，临时审核记录为 `PENDING`。真实错误提交为 0/3，参考实现为 3/3、分数 100。十秒内连交曾被既有 `BOSS_ATTEMPT_RATE_LIMITED` 拒绝，等待限流窗后正确提交通过；未改动这一防滥用策略。
- Tutor 全栈真实文本与合成 PNG 图片各完成一次 DeepSeek 调用，回复成功持久化；图片答复识别黄色背景和蓝色方形。验证后删除本轮 4 个合成学生账号、3 条候选及相关 Tutor/判题记录。
- 验证结果：Graph Boss 225 passed；Ruff 全量通过；Maven `BossGraphClientTest`、`BossServiceTest`、`EndlessPregenServiceTest`、`BossProblemReviewRepositoryPostgresTest` 共 65 passed；`TutorDailyRequestLimitServiceTest` 通过；`git diff --check` 通过。当前工作树仍有此前任务的其他未提交修改，未作清理或重置。
- 尚未完成：学习 Twin P0–P3 计划、KC 全面个性化覆盖和 A/B 效果对照、Boss 最终 Phaser 浏览器旅程、全量 Maven 稳定复跑、Web 全量回归、全仓工程整理及素材授权/登记复核。队列候选仍须教师批准才能加入全局题库。


## 2026-10-14 — 本地 DeepSeek 调用续验

- 本地开发关闭账号级和全站每日 AI 请求计数；部署限额未变。运行中 Spring 环境和 `TutorDailyRequestLimitServiceTest` 均核实该行为。
- Pythontopia 已具备与 Alethicode 当前 Tutor 方向一致的固定阶段 AutoGen 编排；继续复用本项目 SSE、会话持久化、V199 checkpoint、附件授权和 API 字段契约，不复制第二个 Tutor 服务。
- 将 DeepSeek Chat/SSE 默认请求参数设为 `thinking: disabled`，Unit RED/GREEN 通过。变更后真实文本和图片 Tutor 调用分别经 TutorAgent 与 Tutor AutoGen/SSE 实际通过；图片为本地合成 PNG，无个人内容。
- 已重启 Compose Graph 服务，经其本地 HTTP Tutor AutoGen/SSE 路由真实验证文本与图片。之后 Playwright 真实登录 Web 工坊发送文本与 PNG 图片，确认两个答复、图片请求载荷与两条持久化会话；清理临时学生账号。测试脚本前两轮等待条件不正确，第三轮通过。
- 管理后台 Vitest 9 项、前端 typecheck、Graph 定向 pytest 62 项、Ruff 和本地不限额 Maven 单测通过。静态核对未发现管理台 DTO/API/数据库字段错位；布局色彩尺寸与 Alethicode 的 Admin tokens 对齐。
- 仍未完成：学习 Twin P0–P3 计划、KC 个性化覆盖与 A/B 效果对照、Boss 最终 Phaser 浏览器旅程、全量 Maven 稳定复跑、Web 全量回归、全仓工程整理及素材授权/登记复核。账单/token 用量未核对。

## 2026-10-14 — Learner Twin P0 复核与 UI 补齐

- 核对 `SelfDimensionRequest`、`ProfileController`、`PassiveProfileService`：客户端不能提交 evidenceIncrement 或任意 metadata，学生直写仅限主观目标/偏好；当前事件计分仍由服务端 `LearningEvent` 路径处理。
- 核对 WeeklyReportService/Repository：所有来源使用统一 Asia/Shanghai 七日区间；全量事件数与最多 30 条相关记忆分离；当前分数和本周 `applied_delta` 分开；未能重建的周增量返回 null；通过两次批量查询读取所有 KC 快照与事件，不逐 KC 打库。
- Twin 主页面已将 confidence 显示为独立的“证据充分度”，并说明非正确概率；未评估 KC 继续显示无测评值。补充页面级回归，先 RED 后 GREEN。
- 验证：画像/Twin Java 定向 57 tests 通过（含 Testcontainers PostgreSQL 周报集成）；ProfileRoom/TwinWeekly 9 tests、前端 typecheck 通过。
- 本阶段静态与测试检查确认推荐和周报操作已导航至真实复习/远征/工坊。学习效果 A/B、7 日延迟复测及足够样本的校准仍待做。

## 2026-10-14 — Boss 多阶段实战与视觉模型续验

- 真实浏览器验证暴露阶段切换问题：Boss 下一阶段同步出题会超过前端 15 秒超时，页面还保留上一阶段题。复用现有预生成/轮询路径预热后续 Boss 阶段，并在开始取题时清空旧题；页面在服务端 ENTER_NODE 的过渡帧继续挂载同一 Phaser 场景。
- 测试先 RED 后 GREEN；定向 Vitest 4 files / 59 tests、TypeScript typecheck、触及文件 ESLint 和 `git diff --check` 通过。
- Playwright 全栈三阶段实战通过：AI 题阶段 ID 分别为 1/2/3，正式判题 3/3 HTTP 200 且通过，服务端每阶段确认受击，Phaser 画布单实例连续复用，胜利结算正常，CC0 音效 HTTP 200，浏览器无 JS 错误。验证用临时学生账号和远征数据已删除。
- 最新 Tutor 浏览器真调用：文本校验词正确；合成图中蓝色方形与黄色背景均被识别；2 条回答持久化。临时学生、Tutor 数据与图片文件均清理。
- `./dev.sh` 本地后端环境 `PYT_AI_REQUEST_LIMITS_ENABLED=false`；此项只关闭本地应用内每日计数，Provider 自身的余额、速率限制及费用仍然有效。
- 尚未完成：Twin 学习效果 A/B 与 7 日延迟复测、DeepSeek 账单/token 核对、稳定全量 Maven 复验，以及生产 Piston/ECS/公网验证。性能仍按用户要求置后。

## 2026-10-14 — KC AI 出题审查与真实 Web 复测

- 真实请求发现：历史类 KC 的 AI 候选可用“读入任意成就名并追加固定说明”绕过只拦纯输入回显的检查，且未审题会被当作 Golden 回退返回。已加强常量输出与权威 KC 术语接地校验，并让失败审查诊断进入有界重试 prompt；摘要里的 `format` / `input` 等 Python API 词仍能用于证明代码类 KC 落地。
- Graph 只在审查通过时返回题目；失败时返回 `BOSS_PROBLEM_GENERATION_UNAVAILABLE`。正常 Boss 服务仅在找到同 KC 的课程题时可自行兜底，不会把别的 KC 题冒充目标题。
- 正向真实调用：课程库确认 `PY43-KC-012` 没有静态编程题，DeepSeek 首轮生成问候语输入输出题并通过五层审查，返回 `aiGenerated=true` / `reviewed=true`；正常路径自动审核通过并写入共享题库。负向复测 `PY43-KC-001` 的 6 个候选被拒，未进入题库、未生成学习记录。两项都由临时学生账号发起；验后删除该账号及其题目/审核记录。
- 验证：`tests/agents/boss` 234 passed；改动文件 Ruff 通过；`git diff --check` 通过。Graph 容器日志确认 DeepSeek API HTTP 200；Web API 成功题目响应没有 reference solution / test cases。开发请求限额为 false。
- 未完成：`PY43-KC-001` 尚无合格编程题；Phaser 远征端到端答题和画像回流未由本次候选验证；Maven 全量最新仍为 1392 tests / 7 环境错误（相关隔离用例 20/20）；Twin A/B、7 日复测、供应商账单/token 对账和生产 Piston 未验证。性能按用户要求后置。

## 2026-10-14 — 安全审计续修与 DeepSeek 文本输出边界复核

- DeepSeek 聊天和 SSE provider 回归覆盖图片作为输入、仅提取文本输出、丢弃多模态图片输出块；AutoGen Tutor 继续复用同一 provider。定向回归 65 passed，1 个测试标记警告。随后按用户要求发出 2 次真实低 token DeepSeek 请求：纯文本与合成黄底蓝方块图片识别均成功，视觉响应为纯文字且未包含图片 payload/Markdown 图片。
- RED/GREEN：Graph Boss 评测新增最多 10 个用例、代码 8192 字符、单用例输入/期望输出各 8192 字符及 256 字符题目 ID 上限；Web Boss 请求代码上限从 10000 对齐到判题器的 8192。越界请求在进入评测/服务层前返回 422/400。Boss Graph 全部回归 243 passed。
- RED/GREEN：为学习路径生成和学习评估两个真实调用 Graph/DeepSeek 的接口补上学生角色级日额度预留；路由覆盖与额度拦截器 Maven 定向测试通过。开发脚本仍明确将本地限额设为 false；生产 Compose 固定覆盖为 true，避免 `.env` 中的本地值关闭生产上限。生产 AI/readiness 合约通过。
- 生产 Compose：直接使用当前本地 `.env` 的 `config -q` 因未配置 `PYT_ROUTE_EXECUTION_SECRET` 而失败；提供仅用于语法检查的临时占位变量后 `docker compose config -q` 通过，未输出或读取密钥值。
- 本次只读安全审计未确认 P0；已修正两条 DeepSeek 路径绕过日额度与 Graph Boss 评测无界用例/字段长度。仍需关闭的发现：登录仅按 IP 限速、注册无累计来源额度、教师邀请码熵取决于部署值、CORS 接受广泛 origin pattern；这些没有在本轮改动。
- 仍未验证：生产 Piston 的实际网络 ACL/运行隔离、provider token/账单、Twin A/B 与 7 日延迟学习结果、生产/Nginx 运行配置。没有访问 ECS/GitHub；性能留到功能与安全验证收尾后。

## 2026-10-14 — 最新全量复验

- DeepSeek 的契约是图片只作输入、输出只保留文本；本轮两次真实调用（纯文本和合成图像识别）都返回文本。Provider/Tutor/SSE 定向测试 65 passed；这些结果不能证明供应商账单或 token 总量。
- Graph Boss 全量 243 passed，Python lint 通过；后端 Boss 与 AI 额度定向 Maven 测试 19 passed。全量 Maven 为 1401 tests、0 assertion failures、7 环境错误；6 个 Testcontainers 用例遇 Docker 宿主端口绑定冲突，长时仿真单测在全量运行时数据库连接被拒，单独重跑仿真类 1/1 通过。
- Web 最新 Vitest 271 files / 2576 tests、typecheck、build 通过；lint 为 0 errors / 5 warnings，警告位于既有账号隔离测试。build 仍报告 Pyodide 的 Node 内建模块 externalize、eval 和大 chunk 警告。同步脚本只改了课程清单生成时间戳，已恢复该构建副产物。
- `git diff --check` 通过。未完成或不可本地确认：生产 Piston 隔离与 Nginx 实际配置、供应商用量对账、Twin A/B 与 7 日延迟结果；性能按约定暂缓。没有访问 ECS 或推送 GitHub。

## 2026-10-14 — Piston 判题响应上限

- Java 与 Graph 的 Piston 适配器先前会完整读取 JSON 响应后才截断 stdout/stderr。新增 1 MiB 流式响应上限，超限时 fail closed；Graph 每次最多保留 1 MiB 加单个 64 KiB 分块，Java 最多读取 1 MiB + 1 byte 来检测超限。
- RED/GREEN：两个超大响应回归先失败，修改后通过。Graph Boss fixtures 随适配器由 `httpx.post` 切到 `httpx.stream`；Graph Boss 全组 244 passed、Ruff 通过；Java Piston/Judge 定向 39 passed。
- 该上限约束适配层的响应缓冲，不替代 Piston 的 OS 隔离或部署网络 ACL；生产运行配置仍未验证。
- 当前快照 Maven 全量：1405 tests、0 assertion failures、6 环境错误；5 个 Testcontainers PostgreSQL 类因 Docker 动态宿主端口 `address already in use` 未启动，长期仿真类因 PostgreSQL 连接拒绝失败。没有把全量标记为通过。
