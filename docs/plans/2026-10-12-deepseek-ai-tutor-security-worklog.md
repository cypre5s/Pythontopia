# DeepSeek 导学安全与数据边界工作日志

日期：2026-10-12 至 2026-10-13

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
