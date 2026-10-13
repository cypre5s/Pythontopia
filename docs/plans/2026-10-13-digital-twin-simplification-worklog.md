# Pythontopia 数字孪生精简优化工作日志

日期：2026-10-13
范围：Web、共享后端与现有 Graph Tutor；未新增 Twin 状态表、Agent 或模型，仅为账号限额加日计数表，并为学习事件增加幂等键和实际增量字段。

## 完成项

### 可信学习者状态与画像

- 新增 `LearnerTwinService` 只读聚合服务及 `GET /api/learner/twin/state`，汇总 pack 43 的 KC/先修关系、已评估掌握度、练习证据、复习到期状态和用户目标，不新建数字孪生状态表。
- `/api/learner/mastery` 增加 `assessed` 标志；远征星图按与 Twin 相同的已验证来源口径显示测评状态，不从 `confidence > 0` 推断。
- 默认/初始化分数不代表已评估能力；证据不足的掌握度和置信信息保持未评估状态。
- `SelfDimensionRequest` 不再接受客户端证据增量与任意 metadata，自评仅更新主观维度。
- 周报统一 Asia/Shanghai 周区间和 `[start, end)` 统计；本周事件总数与最多 30 条相关记忆分离；当前掌握度与可由本周证据重建的变化分离；不充分时返回“暂无可比数据”；掌握度历史按批次读取。

### 统一选题和远征连续性

- 新增纯规则 `AdaptivePracticePolicy`，按到期复习、连续独立练习失败、目标薄弱 KC、低置信 KC 与未评估 KC 的顺序筛选，满足先修条件并排除本局已用 KC，输出难度档位及推荐理由。
- `LearnerTwinService` 与 `BossService.selectFrontier()` 共用策略；远征决策使用后端画像/掌握度/复习状态，不采信客户端 `profileSignals` 作为权威学习状态。分身推荐携带用户选择的 KC，服务端仅在先修条件合格的候选中将其优先，确保该 KC 成为远征第一题。
- Graph 出题异常或返回空题时回退到同 KC 的现有课程题库；Graph 判题不可用时复用现有本地静态检查和判题器。两个路径都没有匹配的可信题目/可用判题能力时仍返回明确不可用错误。
- 已验证的正式提交事件携带服务器生成的 submission ID；V210 可空幂等键与部分唯一索引阻止同一提交跨业务入口（正式提交与资源微测）重复增加掌握度或复习次数。
- 画像投影也对正式练习和同提交的操作检查共用 submission 幂等键，防止 `practical_ability` 被同一作答重复提高。
- V211 另存实际发生的掌握度变化（分数封顶后），周报只对具有完整实际变化记录的窗口求和；旧事件没有 `applied_delta`，变化显示为空。

### 学习分身 Web 页面

- `ProfileRoomPage` 聚合为学习分身入口，展示真实测评数/掌握 KC、知识星图、薄弱/到期依据、一个主推荐和来自服务端第二项的替代行动；只有一项推荐时隐藏替代按钮。
- 未测 KC 呈现为“尚未评估”，不把数据库默认 0 显示成失败；推荐携带精确 KC 进入无尽远征。到期推荐优先打开对应错题/遗忘回声；若没有可用历史题，则转到该 KC 的远征练习。
- 周报近期变化、画像访谈设置与洞察笔记收进次级区域；TwinWeekly 的提示操作交给既有工坊导航。
- 复用现有像素角色与星图，无新增素材、游戏场景、Agent 或模型。

### Tutor 请求额度

- V209 新增轻量账号/日期计数表；Tutor 文本聊天与工作流请求在新请求进入 Graph 前原子预留额度，同账号跨 conversationId 共用 Asia/Shanghai 每日 30 次上限。
- 成功重放的既有请求不重复计费；保留 90 天请求计数后由定时任务清理。该限制是请求次数上限，不是 token 或金额成本上限。

## 验证记录

- 后端：
  `mvn --settings .mvn/settings.build.xml -q -Dtest=AdaptivePracticePolicyTest,LearnerTwinServiceTest,TwinWeeklyControllerTest,BossServiceTest,ExpeditionRunControllerTest,TutorDailyRequestLimitServiceTest,WeeklyReportServiceTest,WeeklyReportServicePostgresIntegrationTest,MasteryEvaluatorTest,ResourceWorkshopServiceTest,PassiveProfileRevisionListenerTest,ProfileDimensionProjectionOutboxPostgresTest test`
  结果：12 类、138 tests，0 failures、0 errors、0 skipped；Testcontainers 覆盖幂等、跨模块重复、画像投影、封顶后增量、历史不可比、事务回滚和每日并发限额，空库迁移到 V211。
- Web：`npm run typecheck` 通过；画像页、Bug 矿洞、远征 API/store、星图 5 个测试文件 / 77 tests 通过；缺少历史复习候选、推荐 KC 远征路由和请求传递均有回归测试。
- Graph：`tests/test_tutor_image_input.py`、`tests/test_tutor_ask_stream.py`、`tests/test_tutor_workflow.py`、`tests/test_openai_compat_provider.py` 共 61 tests 通过；有一条既有 `asyncio` 标记警告。
- 部署契约：生产 AI readiness、Piston HTTP 主机、共享演示账号契约脚本通过。
- `git diff --check` 通过。

## 未完成与风险

- 远征重复题抑制已补齐当前 run 范围：根据本局已签发题目指纹过滤预生成题和审核题，Graph 返回重复题时改用同 KC 的未用 Golden 题；若没有可用题，返回明确可重试错误。跨 run 或时间窗级重复过滤仍未实现。
- Tutor 选择的限额是每账号每天 30 次请求；工作流一次请求可触发多个模型调用，本限额不构成 token 或金额支出上限。
- 未实现未来路径对比、BKT、效果对照实验或无尽远征性能指标；按用户要求性能最后测。
- 首轮没有启动全栈浏览器联测；后续补充联测已通过：本地 `./dev.sh` 全栈打开 `/profile-room`，等待 `networkidle` 后由真实 Twin API 渲染未评估空态与主推荐，浏览器错误及 HTTP 错误均为 0。未发起新的 DeepSeek 请求。DeepSeek 文本/图片接入与先前文图链路验收见本阶段前序 changelog，不应当作本轮新请求证据。
- 周报/记忆投影仍依赖既有事件投影链路；若投影服务外部持久化失败，Twin 的权威掌握度读取仍基于掌握度账本，但记忆时间线可能缺项。
- 对话中粘贴的 DeepSeek API 密钥已暴露，应在 provider 控制台轮换后再做真实请求；工作日志不保存密钥。
- 未修改无关的 HTTP/HTTPS、部署与历史路线工作树内容；未提交代码。

## 2026-10-13 补充复核

- 收敛远征难度入口：`BossService` 调用 Twin 的 `AdaptivePracticePolicy` 结果；缺少候选或读取失败时退到有界阶段难度，不接受客户端难度作为权威值。
- 按用户与远征 run 查询已签发题目指纹，过滤重复预生成/审核题；Graph 重复题用未用 Golden 题兜底。无可用 Golden 题时返回 `BOSS_REPEAT_PROBLEM_UNAVAILABLE`，避免继续发同一题。
- NPC 对话与寒暄的路径变量加 1–40 字符白名单约束，超长路径在进服务层前返回 404。
- 回归：后端定向 224 tests、0 failures/errors；前端串行全量 256 files / 2522 tests 与 typecheck 通过；RAG 158 passed / 2 skipped；Graph Tutor 61 项、AutoGen/附件 17 项通过；完整 npm audit 为 0 vulnerabilities。
- 本地 Twin 状态接口预热后顺序采样 30 次：30/30 HTTP 200，P50 15.36 ms、P95 18.49 ms、最大 21.06 ms。此数据只描述本机状态聚合端点，不代表远征实时生成或判题性能。
- 无尽远征端到端性能、真实 Piston 执行、ECS/公网运行态仍未验证；本轮没有调用 DeepSeek。粘贴到对话中的 API key 应轮换后再做真实模型测试。

## 2026-10-13 补充实施与功能复验

- 收紧画像事实边界：访谈/自评只表达专业背景、已有经验、目标与媒介偏好；答题失败不再推断情绪、韧性或认知风格。无可信测评证据的 KC 保持“尚未评估”；反思输出仅作为易错建议，旧的情绪/能力洞察不会再被当作事实呈现。
- 复盘大厅从 `/api/learner/twin/state` 读取同一服务端 next step，展示推荐 KC、难度与理由；到期项进入对应复习入口，其余项进入同 KC 远征。新增页面行为回归，避免复盘、分身与远征自行计算优先级。
- DeepSeek 配置检查：git 忽略的 `deploy/.env` 已有可用凭证与文本/视觉档配置，文件权限为 `0600`，未修改或回显密钥；Graph 正式 lifespan 会先加载该文件。Tutor 图像由现有 Web 上传控件经 Java 原样转发到 Graph，Graph 校验并将文本与 PNG 合入同一用户消息后选视觉档，回复保持文本形式；无需新建图片服务、Agent 或素材。
- 真实 API 冒烟共发起 5 次请求（远低于 30 次上限）：两次按 80 token 的短试探没有可见终答，一次 SSE 诊断确认短 token 被推理内容耗尽；再按 Tutor 实际 2048 token 上限各发一次文本与合成图片请求，均返回非空中文文本。文本答复为 `Python 中 2 加 3 等于 5。`；视觉请求识别了合成蓝紫色方块并返回颜色文本。此项只验证通路，不代表视觉问答质量评估。
- 新鲜验证：`npm run typecheck` 通过；相关 Vitest 4 文件 / 7 tests 通过；后端 21 类 / 214 tests，0 failures、0 errors、0 skipped；Graph 定向回归 146 passed、1 条既有 `asyncio` 标记警告；复盘推荐组件 ESLint 无警告，画像分身定向 ESLint 无错误；`git diff --check` 通过。变更工作树扫描未发现 API key 格式的字符串。
- 本轮没有重启全栈浏览器，也未重测性能；此前状态聚合接口的 30 次本地采样不代表无尽远征性能。无尽远征跨 run/时间窗重复题抑制、未来路线对比、BKT 仍暂缓。对话中粘贴的密钥已暴露，应立即轮换；后续请求应改用轮换后的凭证。

## 2026-10-13 最终边界与回归补测

- Tutor 记忆只转发 `自述媒介偏好`；失败答题不再推断挫败/韧性，画像中的情绪或固定学习风格字段不参与 Tutor 个性化事实、能力评估或远征伤害。自评回归确认请求不能写入系统证据。
- 收敛远征同一节点的未结算题目：刷新复用已签发问题，并发 Graph 结果只落一份；更旧的问题在新题签发后无法入 verified attempt，也无法结算。失败伤害固定为 2，再应用现有遗物效果；低/高韧性自评产生相同结果。
- Graph 未配置真实 provider 时拒绝 DryRun 并对功能请求返回 503。Piston HTTP allowlist 移除云元数据与 IPv6 link-local 网段；教师班级说明限制对齐到 1000 字。`EndlessPregenService` 在旧迁移版本启动时先检查预生成表是否存在，避免测试/分阶段迁移数据库启动失败。
- 验证：远征后端 `ExpeditionRunPostgresIntegrationTest`、`ExpeditionScoreServiceTest`、`BossServiceTest`、`EndlessPregenServiceTest`、`ExpeditionRunControllerTest` 共 95 项通过；最终情绪边界 Testcontainers 用例也单独复跑通过。前端全量 260 files / 2504 tests、typecheck、build 通过；lint 0 errors、178 warnings。Graph 全量 1814 passed / 1 skipped，RAG 158 passed / 2 skipped；六项部署契约脚本通过。
- 后端全量 `mvn -q test` 运行 1327 项：0 assertion failures、7 errors。6 项 pgvector Testcontainers 无法绑定动态宿主端口（Docker `address already in use`），另 1 项连接对应容器失败；隔离重跑相关类共 8 项，仍有 5 项同类容器错误、0 assertion failures。需在 Docker 端口稳定的环境补齐全量证据。
- 本轮未产生新的 DeepSeek 请求，也未做远征性能测量；Twin 聚合接口旧的 30 次本地采样不能替代远征 P50/P95。跨 run 重复题抑制、未来路线对比、BKT、效果对照实验仍暂缓。公开过的 API key 应轮换。

## 2026-10-14 Tutor 与生产安全补充

- 训练场工作流 `CHAT` 现在携带当前编辑器代码；前端、Java 与 Graph 均拒绝超过 20,000 字符的代码，Tutor prompt 将代码按不可信学生内容处理，不作为指令或课程证据。
- 费曼学伴的教学与找茬入口复用 Tutor 账号/上海时区每日 30 次计数；额度耗尽时不调用 Graph。工作坊上传超出个人存储额度时在解析文件前返回错误。
- 生产 Graph 显式关闭可选内部鉴权；ECS 预检和安装脚本拒绝公开开发数据库密码。未连接 ECS，也未执行部署。
- 验证：后端 14 个相关单测类与 2 个 PostgreSQL/Testcontainers 集成类共 192 项通过；Web 260 个测试文件 / 2506 项与 typecheck 通过；Graph 全量 1816 passed / 1 skipped；生产 AI/readiness 与安装密钥契约脚本通过。
- 无尽远征性能采样、Piston 容器隔离冒烟、全栈浏览器联测仍未完成；本轮未发起 DeepSeek 请求。对话中暴露过的 API key 应轮换后再执行有费用的远征性能测量。

## 2026-10-14 Twin 与远征闭环补充

- 今日学习推荐直接复用 `LearnerTwinService` 的确定性 KC 顺序；反思洞察、失败记忆和证据图仅用于说明依据，避免与 Twin/远征产生第二套排序。`SelfDimensionRequest` 仅接受主观分数增量与标签；当前能力证据仍来自已验证判题事件。
- 周报用权威事件聚合计算本周总量，最多 30 条相关记忆只用于 KC 与摘要；当前掌握度和有完整 `applied_delta` 的周变化分开展示，不可重建的历史差值为空。周报时间边界统一为上海时区的 `[start, end)`，掌握度事件按 KC 批量读取。
- 分身页面已有真实星图、证据来源、尚未评估提示、服务端主/备推荐和近期变化折叠区；主/备动作分别路由到复习或指定 KC 的无尽远征，周报提示落入既有工坊。页面集成测试验证了这些导航。
- 远征保留稳定请求 ID，超时后读取 canonical run 确认已提交动作，确认前不重复提交；精确维度的并发生成互斥，缓存上限 256，恢复时清理超过一天的 READY 预生成题。今日推荐、Twin 与 Boss 选题共用策略。
- 复用现有账号每日 30 次计数，覆盖用户触发的模型端点，包括图片和分镜生成；没有新增 Agent、模型、微服务或数据库迁移。日额度限制的是 HTTP 操作次数，不能限制单次工作流内部的模型调用、token 或金额。
- 验证：后端 24 个定向单测/集成测试类共 217 项通过，含周报与画像投影 Testcontainers 回归；Web 260 个测试文件 / 2509 项通过，`npm run typecheck`、改动文件 ESLint、`git diff --check` 通过。本轮没有 DeepSeek 请求。
- 未完成：无尽远征真实生成与正式判题的 P50/P95、缓存命中、失败恢复和重复率测量；未来路线对比、BKT 与对照实验仍暂缓。未重启本地全栈浏览器、未运行 Piston 容器冒烟或 ECS/公网验证。DeepSeek key 已在对话中暴露，轮换后再做有费用的真实性能采样。

## 2026-10-14 最终复验与工程整理

- 无尽远征 Boss 节点已集成 Phaser Boss 场景；地图与 Boss 共用一个挂载 canvas / Phaser.Game，复用项目立绘。仅收到已完成的服务端判题结算后才演出命中；失败读取实际远征 HP。命中音效来自 Kenney Impact Sounds 的 CC0 `impactSoft_medium_000.ogg`，已经加入 `LICENSE-ART.md`、运行时素材 license 和资产同步 manifest。
- 真正 DeepSeek Tutor 调用：文本问题与合成图片问题各一次，均返回非空文本答案。图片属于输入通路冒烟，不能代表图片理解质量评测。性能采样阶段没有模型请求。
- 本地全栈浏览器实测：`/expedition?focus=1` 建图与一张 Phaser canvas 正常；Boss 切场景后 canvas 元素不变，章节立绘与 CC0 音效文件 HTTP 200，浏览器 pageerror / requestfailed 均为 0。Pass 与 fail 演出检查通过，其中浏览器 Boss 状态由测试脚本注入，不是实际 Boss 判题提交；正式判题路径由后端与页面回归测试覆盖。另有本地 Piston 正式 Python 判题 Playwright E2E 1 项通过。
- 无 DeepSeek 的本地性能采样条件：Chromium headless，WebGL，1440×900 CSS px，DPR 1，Vite + Spring 全栈。远征 run POST 通过 Vite 代理实测 12 次，P50 23.32 ms、P95 27.37 ms；UI 从点击开局到地图与 canvas 可见约 1886.9 ms。地图 requestAnimationFrame 间隔 P50/P95 16.7/33.4 ms；Boss 为 16.7/16.8 ms。采样局含 23 节点。run API 不涉及出题 Graph 调用；页面性能脚本拦截题目预生成，避免触发模型。它只代表本地开发机 headless 采样，不是线上或移动设备目标值。
- 工程清理参考官方 Vue 风格指南、typescript-eslint 命名约定说明及 Google Java Style；改为可读的 Boss 状态分支/状态播报，修复改动页面 ESLint warning，并用 Spotless 只格式化 AI 额度拦截器、配置、BossService 与推荐服务。定向前端 ESLint 无告警；定向 Spotless 检查通过；完整 Web 263 文件 / 2516 tests、typecheck、build 与资产同步检查通过。最终 Boss 用例 4 文件 / 17 tests 通过；后端额度拦截、Boss 与推荐服务本轮 46 tests 通过。
- 后端全量 `mvn test` 1337 项、0 assertion failures、7 errors：6 个 pgvector Testcontainers 动态宿主端口绑定冲突，1 个关联 JDBC 连接被拒；受影响 7 个类逐类串行重跑全部通过。不是全量 Maven BUILD SUCCESS。全仓 `npm run lint` 为 0 errors / 177 warnings；全仓 `spotless:check` 报另外 622 个格式不合规文件。仅整理改动范围，未对整个仓库做大规模 reformat。
- 这轮测试临时注册的 9 个本地账号已删除，开发容器已停止。未来路线对比、BKT、学习成效对照实验与 ECS/公网验证仍暂缓；用户在对话中粘贴过的 DeepSeek key 应轮换后再使用。
