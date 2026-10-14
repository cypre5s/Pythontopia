# Pythontopia 执行日志

> 本文档实时记录每个 working session 的状态，作为跨会话的"工作记忆"。
> 一定要常翻 `AGENTS.md` 与 `/home/cypress/Pythontopia/plan/` 下的阶段计划，避免方向偏离。
> 本文件下方的用户立场、Provider 决策和阶段列表是 2026-05 的历史记录，已由当前 `AGENTS.md` 与后续 CHANGELOG 更新覆盖。当前任务以 Web 为主，AI provider 为 DeepSeek；讯飞 Spark 与媒体出口已关闭。不要据旧日志启动非 Web 客户端或把旧阶段当作待办。

## 2026-10-14 当前续接

- 本地 `./dev.sh` 默认关闭应用级 AI 每日次数限制，当前后端进程环境已实测为 `PYT_AI_REQUEST_LIMITS_ENABLED=false`；部署配置仍保留上限。Boss 判题 10 秒提交保护是独立的防重复机制。
- KC 编程题生成链路已补齐课程库上下文：后端权威查找 KC 名称/摘要，Graph 用它构造提示并按摘要术语校验；真实 DeepSeek 生成题通过 Graph/Piston 自测与服务端正式判题后，进入教师 `PENDING` 队列。未自动批准。
- 已实际调用 Tutor 文本和图片流，数据库会话答案非空且图片识别正确；验证完成后删除了本轮 4 个合成学生账号、关联 Tutor 会话/审核记录及用户数据。
- 当前续验：前端全量 Vitest 270 files / 2555 tests 通过，typecheck、全量 lint（0 error / 7 warning）和生产 build 通过；Graph 1836 passed / 1 skipped、Ruff 全量通过。
- 后端最新全量 Maven 1383 项、0 assertion failures、7 errors：6 个 Testcontainers Postgres 类的 Docker 宿主机端口绑定冲突及其引发的 1 个 JDBC 错误。6 类串行复跑 19 项通过，长期仿真单独复跑 1 项通过；本轮相关 Boss/Tutor/Twin/审核测试 86 项通过。Maven 全量仍不可标记为通过。
- 学情可信度补强：教师批准前的 AI 题可练习但不发 KC 掌握事件；仅与已批准题完整 payload 一致的题能入学情，查询失败时继续返回判题并跳过事件，反馈会说明暂不计入画像。新测试先复现旧行为；最新 Boss/Tutor/Twin/审核关键回归 86 项通过。
- 下一步：完成最终 Phaser Boss 多阶段真实浏览器旅程与 Twin A/B / 7 日延迟效果核验；核对供应商实际用量，验证生产 Piston/ECS。性能仍后置；不要把模型自检/Piston 自测等同于教师题目质量批准。

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
