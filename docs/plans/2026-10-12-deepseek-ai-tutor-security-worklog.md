# DeepSeek 导学安全与数据边界工作日志

日期：2026-10-12

## 实施

- 保留 Pythontopia 当前 Java API、Graph Tutor/RAG 和五阶段 AutoGen 工作流；工坊文字/图片问答通过导师 SSE 直接进入 DeepSeek。图片限制 JPEG/PNG/GIF/WebP、最多 3 张、单张 5 MiB，校验真实字节和 Base64，且只用于当轮请求。
- 只保留 DeepSeek 生成 provider；开发 Compose 与 `.env.example` 删除旧微调模型配置。Piston 成为正式判题 provider，未配置时 readiness 不通过且提交不会落回本机子进程。
- 修复无效 Bearer token 的统一认证失败路径。远征题实例绑定用户，KC 必须来自课程目录，计分从服务端判题结果写入并要求活动 run；leaderboard 的分数、深度来自同一局。
- 有界远征预生成线程池、排队和单用户额度；清理陈旧 lease，避免失败/过期任务无限堆积。
- Twin 仅统计近 7 天后端/Graph 权威事件；客户端事件采用 allowlist，不能伪造练习成功/失败。掌握度显示当前值、本周 delta、证据数量及来源。
- 管理台仅保留 API-backed 的 AI 运维摘要与审核队列。

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
