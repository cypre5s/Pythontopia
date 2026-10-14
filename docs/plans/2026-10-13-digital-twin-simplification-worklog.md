# Pythontopia 数字孪生精简优化工作日志

## 2026-10-14：研究与治理原则复核

- 在线复核 Jisc 的学习分析实践规范（2026-09-09 更新）与 UNESCO 教育/研究生成式 AI 指南（2026-01-16 更新）：画像用途应以学生受益为中心，说明指标和数据用途、验证数据质量、限制数据访问并最少化采集；学生应能查看/纠正相关信息，AI 分析需保留人类监督与质疑路径。[Jisc](https://www.jisc.ac.uk/guides/code-of-practice-for-learning-analytics)、[UNESCO](https://www.unesco.org/en/articles/guidance-generative-ai-education-and-research)。
- 人本学习分析系统综述也指出学生能动性、隐私、安全、可靠性与信任仍是需要验证的设计要点；该文是研究综述，不构成当前产品已经有效的实证。[Alfredo et al., 2024](https://doi.org/10.1016/j.caeai.2024.100215)。
- 与本项目实现对应：正式判题是 KC 能力证据；自评与偏好保持主观来源；AI 洞察只解释或建议；证据不足显示未评估；当前实现没有推断心理状态。下一步推荐附依据并可由学生选择，学习成效对照实验尚未完成。
- 这些来源支持透明、最少化与可质疑原则，不证明当前选题策略的因果效果；BKT、未来路线概率和学习效果 A/B 仍按原方案暂缓。

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

## 2026-10-14 DeepSeek 实网文图验证与额度覆盖补充

- 本地 `deploy/.env` 凭证直连 DeepSeek OpenAI 兼容接口，分别发送文本与合成 PNG 图片，共 2 次请求：均 HTTP 200 且以文本回复；文本请求使用 13 tokens，图片请求使用 210 tokens。视觉回答识别出黄色背景与蓝色中心图形。请求没有经过本地应用认证/路由，因此只证明 DeepSeek 文图 API 与当前模型配置可用，不替代 Tutor Web 全链路联测。
- 预算审计补齐两个先前漏标的用户入口：有结果时会调用摘要模型的 B 站视频搜索，以及触发多个资源生成的教师补救。Reflection、NPC 每日预热、Oracle 预防包在后台外发前共用同账号上海时区每日 30 次额度；达到额度后阻止模型请求，Reflection 保留确定性规则兜底。移除纯确定性浏览/学习路径/评估与禁用媒体端点上的额度标记。
- 回归：新增额度路由覆盖测试，并验证后台额度耗尽时不会调用 Graph；四个相关测试类与路由测试共 30 项通过，改动 Java 文件 Spotless 检查通过。
- 额度语义仍是每个用户操作/后台生成任务预留一次，不是 DeepSeek 外发 HTTP 调用、token 或金额硬上限；一项工作流仍可能发出多次模型调用或重试。需要硬支出上限时，应将计量放到 Graph Provider 外发边界并传递可信账户身份。

## 2026-10-14 管理台审核队列竞态修复

- 审核列表刷新期间禁用通过/驳回操作，并在弹窗入口再次检查 loading 状态，避免正在返回的旧队列覆盖刚提交的审核结果。
- 回归用例先在旧实现上失败（刷新期间按钮没有禁用），修复后 `AdminProblemReviewsPage.spec.ts` 9 项通过；相关 Vue ESLint 与 `npm run typecheck` 通过。

## 2026-10-14 Tutor 全栈文图调用与启动鉴权修复

- 用 Playwright 经本地 Web/Vite 与 Spring Boot 后端认证后，分别向现有 `/api/tutor/ask` 发送文字和合成 PNG 图片，各完成一次真实 DeepSeek Tutor 调用；两次 HTTP 200、收到 `RUN_FINISHED`，文字答复 `5`，图片答复识别黄色背景和蓝色中央图形并以文本返回。浏览器 `pageerror` 为 0，测试临时账号与其 Tutor 会话已清理。
- 首轮冒烟定位到全栈启动契约问题：`dev.sh` 先启动 Graph 容器、后生成仅本次有效的 `PYT_RAG_ADMIN_TOKEN`，于是后端与 Graph 的内部令牌不同，Graph 以 401 拒绝请求。将令牌解析移至 Compose 启动之前，并在现有 Compose 安全契约中增加顺序断言；`--no-deps` 若无法复用有效固定令牌则提前报错，避免与已运行 Graph 产生静默鉴权失败。干净停止本地后端/依赖后重启，全栈应用调用通过。
- 回归：额度路由覆盖、Reflection、PreventionPack、NPC 预热/问候与 CORS 五个测试类共 30 tests 通过；`bash -n dev.sh deploy/scripts/test_dev_compose_security_contract.sh`、`test_dev_compose_security_contract.sh`、`test_resolve_dev_internal_token.sh` 均通过。最后重跑完整 `mvn test` 为 1345 tests、0 assertion failures、7 errors，未达到 `BUILD SUCCESS`：6 个 pgvector Testcontainers 类在绑定 Docker 随机宿主端口时遇 `address already in use`，另 1 个 JDBC 连接错误。随后定向复跑 7 个受影响类，6 个端口错误复现，`LongTermClassroomEffectSimulationTest` 单独通过。
- 未验证生产 CORS 配置、公网/ECS、真实硬件或线上性能；Tutor Markdown 仍允许外部图片 URL，访问者 IP/请求会暴露给图片主机，需后续隐私收紧。账号每日 30 次是操作额度，不是模型调用、token 或费用硬上限。对话中粘贴的 DeepSeek 密钥应轮换。

## 2026-10-14 判题与配额安全增量

- Boss 判题请求在 DTO 与服务层都要求 UUID `attemptId`。服务先核验当前用户签发的题目，再 claim 幂等记录；无效 ID 保持兼容提示但不写永久记录。Boss 成功/失败事件携带 `boss-attempt:<userId>:<UUID>`，掌握度与画像投影均按此键去重；新增同 UUID 跨用户测试，防止全局唯一 outbox 键吞掉第二位用户的事件。
- Graph 评测若返回 `runtimeError=sandbox_unavailable`，复用已有 Java/Piston 本地判题；本地环境也不可用就返回 `BOSS_EVALUATOR_UNAVAILABLE`，不占用远征得分次数、不发布练习失败事件。
- V212 增加跨账号每日 300 次 AI 入口请求闸门，并保留每账号每日 30 次限制。两项额度同一事务预留，上海时区日界线一致，保留 90 天计数。
- 验证：Boss Controller/Service、额度服务、画像事件监听器共 71 单测通过；Testcontainers 3 项通过，其中 400 个跨账号并发请求仅接受 300 个，并验证 Boss 学习事件回放不会重复更新；空库 Flyway 到 V212 通过。
- 额度仍按被标注的入口操作计数，不能换算为 DeepSeek 外发次数、token 或费用；单个操作可能触发 Graph 重试和多轮生成。Android/Windows 客户端未改，Boss API 使用前需补传 `attemptId`。本轮没有重跑完整 Maven、Web 全量、ECS/公网、真实 Piston 故障注入，也没有做远征实时生成/判题性能采样。

## 2026-10-14 补充：额度跨日一致性

- 账号与全站额度预留共用同一次上海日期读取，避免跨午夜请求分别计入相邻两天。
- TDD 回归先稳定复现账号日期 `2026-10-14`、全站日期 `2026-10-15` 不一致；修复后额度服务与路由标记覆盖共 7 项通过。定向 Spotless 检查通过。

## 2026-10-14 Boss 投影与媒体 provider 复核

- Boss 事件幂等键补入 `userId`。回归测试让两个用户复用同一个 attempt UUID，分别得到独立画像投影键；复核 PassiveProfileRevisionListener 后确认其稳定事件键均已包含用户 ID。
- AI 媒体路由复核确认讯飞图片生成、图片理解、TTS 和 ASR 均由 `isEnabled() == false` 硬关闭，相关服务不会发起外部调用。保留 DeepSeek 文本/视觉输入与 TTS 关闭策略；路由覆盖测试锁定禁用状态，避免未来启用 provider 后仍被误分类为无额度成本。
- 验证：`BossServiceTest` 与 `AiBudgetRouteCoverageTest` 共 45 项单测通过，`ProfileDimensionProjectionOutboxPostgresTest` 2 项 PostgreSQL 集成通过。此次没有真实 provider 请求；媒体 provider 的关闭契约由代码与单元测试验证。

## 2026-10-14 Boss Golden 兜底全栈复核

- 本地全栈实测发现 Graph 重试助手的 `BusinessException` 包装破坏了 `BossGraphClient` 声明的 `RestClientException` 契约，令服务端没有进入同 KC Golden 题回退。客户端现保留原异常为 cause 并恢复契约；回归测试先失败于实际异常类型，再通过。
- 独立全栈库空库迁移至 V212。浏览器真实登录并创建远征地图；服务端 Boss API 对 `PY43-KC-041` 返回课程 Golden 题，正式 Piston 判题验证错误答案、正确答案和相同 attemptId 回放。`BossGraphClientTest`、`BossServiceTest`、`BossProblemReviewServiceTest`、`EndlessPregenServiceTest` 共 61 tests 通过。
- 本地正式判题顺序 10 次：P50 77.32 ms，最近秩 P95 81.95 ms；测量路径是 Graph 沙箱不可用后的 Java/Piston 兜底，不是 AI 出题或线上性能。Graph provider 被限制到本机拒绝端口，本轮 DeepSeek 请求为 0。
- 边界：若目标 KC 没有匹配的现有课程编程题（例如 `PY43-KC-001`），服务端继续 fail closed 返回 503；不跨 KC 伪造题目关联。完整 Boss UI 通过判题后的命中演出，本次未从浏览器实际走到远征最终 Boss 节点；已有 Phaser 场景状态回归与先前合成状态浏览器检查仍是该分支的证据。

## 2026-10-14 无尽远征实测与 Boss JSON 出题修复

- 本地真实 Boss 出题首次 6 次 DeepSeek 调用均未得到可审核题目，端点回退 Golden。查阅 DeepSeek thinking/JSON 文档后，发现结构化生成仍使用默认 thinking；在 Boss 的 JSON 调用关闭 reasoning。推断默认 reasoning 消耗有限输出预算可能导致空 content；API 没有暴露可验证的内部 token 分配，本结论仅由失败模式和后续单次成功复测支持。
- 回归测试先在缺少 `reasoning_effort` 参数时失败，再验证参数值为 `none`；Boss 生成工厂目录 217 tests 通过。修改后的本地真实调用只发 1 次 DeepSeek 请求，HTTP 200、`aiGenerated=true`、`reviewed=true`，端到端 2937.10 ms，成功生成并通过自动审查。本次远征性能采样共 7 次外发模型请求，未超过 30 次上限。
- 本地 Spring 远征 run 创建顺序采样 10 次，P50/P95 16.18/22.75 ms，返回 27 个节点。Piston Python 3.12 直接执行样本耗时 20.27 ms，退出码 0、stdout 为 `5`。这不是正式 Boss 答题提交延迟，也不是线上或真实硬件基准。
- 远征地图/Phaser 帧率沿用同日此前 Chromium 样本（地图就绪 1.89 s、Boss 帧间隔 P50/P95 16.7/16.8 ms；合成结算状态），本次未重复浏览器地图采样。ECS/公网、真实用户视觉质量与完整 Maven 成功仍未验证。
- 工程命名/格式检查以 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)、[Vue props guide](https://vuejs.org/guide/components/props)、[typescript-eslint naming-convention](https://typescript-eslint.io/rules/naming-convention/) 为参考，限于本轮触及文件，不触发全仓重排。已删除临时性能账号并停止本地开发容器；数据卷保留。
- 停止开发栈后重跑完整后端 `mvn test`：1353 tests、0 failures、7 errors，`BUILD FAILURE`。Surefire 确认 6 个 pgvector Testcontainers 因 Docker 动态宿主端口 `address already in use` 无法启动，另 `LongTermClassroomEffectSimulationTest` 遇 JDBC 连接拒绝；单线程重跑没有消除该环境问题。

## 2026-10-14 收尾复验：未评估 KC 诊断入口

- 真实新手画像的 frontier 仅包含首个未评估 KC；若其先修也未评估，策略允许该 KC 以难度 1 进入诊断练习。Boss 题目入口同时过滤无课程编程题的 KC；显式聚焦无题 KC 时返回 `EXPEDITION_NO_PRACTICE_PROBLEMS`，不伪造题目或能力证据。
- 本轮后端相关测试隔离复跑：`AdaptivePracticePolicyTest` 6 项、`BossServiceTest` 45 项、`ExpeditionRunPostgresIntegrationTest` 27 项通过；Testcontainers 空库迁移到 V212。全量 Web 266 个文件 / 2533 项 Vitest 通过，typecheck 通过；全量 Maven 1359 项中 0 assertion failures、7 个环境级 errors（6 个 pgvector 容器启动失败、1 个长期仿真数据库连接拒绝）。
- 功能复验结束后，经本地 Vite 代理连续启动远征 14 次（2 次预热，12 个计时样本）：P50 20.14 ms、P95 24.75 ms，地图 29 个节点；未发起 DeepSeek 请求，临时账号与存档已删除。此处只测建局接口，不代表 Graph 出题、正式判题或线上延迟。
- Twin 的学习成效 A/B、BKT/未来路线概率、ECS/公网和真实用户长期效果仍属计划暂缓或未验证项；不得以本次功能回归代替成效评估。

## 2026-10-14 续接收尾复验

- 修复通用 `/api/world/boss/select-frontier` 的选题边界：先取最多 40 个有序 Twin 候选，再筛选 pack 43 中具有同 KC 课程编程题的知识点，最后按请求数量截取。新手首候选 KC001 无题时不会再泄漏为可玩候选；聚焦第一章仍明确返回 422 `EXPEDITION_NO_PRACTICE_PROBLEMS`。不创建跨 KC 代题或伪造题源。
- Graph 离线 `answer_relevancy` scorer 不再让 DeepEval 自动选择默认 provider。未显式注入评测模型时返回 skipped；在线 DeepSeek provider 未改变。
- 本地 DeepSeek 凭据安全复核只输出配置字段：API key 存在、地址为 `https://api.deepseek.com`、文本模型 `deepseek-v4-pro`、视觉模型 `deepseek-flash`；`deploy/.env` 被忽略且未跟踪。实际直连模型 2 次文本、1 次合成 PNG 图片请求均 HTTP 200。第一次文本未匹配逐字断言，第二次正确解释 `input()`；图片识别黄色背景与蓝色图形。本轮没有打印凭据。
- 验证：前端 266 files / 2533 tests、typecheck、build 通过；全量 ESLint 0 errors / 7 warnings。Graph 全量 1823 passed / 1 skipped、Ruff 全量通过；DeepEval scorer 定向 21 passed / 1 skipped。BossService 定向 46 项通过。资产同步检查通过。
- 后端全量 `mvn test`：1360 tests、0 failures、7 errors。6 个 pgvector Testcontainers 错误在 Surefire XML 中证实为 Docker 动态端口绑定 `address already in use`；`LongTermClassroomEffectSimulationTest` 则是容器连接拒绝。定向 BossService 组独立 `BUILD SUCCESS`。这些环境错误仍未解决。
- 按“功能最后、性能收尾”的顺序，本地 Vite → Spring 远征建局采样 2 次预热、12 次计时：P50 21.01 ms、最近秩 P95 22.63 ms、25 个地图节点；未调用 DeepSeek。该样本只覆盖建局 API，不代表题目生成、判题、线上负载或其他硬件性能。
- 仍未完成：完整 10 层 Boss 多阶段真实判题浏览器旅程；pack 43 第一章 KC001–KC007 没有编程题，不能通过错配素材完成。Twin 学习效果对照、BKT/未来路线概率以及生产 Piston/ECS/公网验证继续暂缓，性能采样不覆盖它们。

## 2026-10-14 Twin 先修与跨局重复题修正

- 自适应策略改为阻止任何先修尚未评估/未达标的候选，避免把未知状态当作掌握；学生会先看到可评估的基础 KC。
- 已消耗题目指纹在当前远征和此前 7 天的远征之间共同排重，7 天前的题目重新可用。窗口有意保持固定；小题库可能短期无可用题，后续应依据真实复用数据再调整。
- `AdaptivePracticePolicyTest` 6 项、`LearnerTwinServiceTest` 2 项、`BossServiceTest` 46 项及 `ExpeditionRunPostgresIntegrationTest` 28 项通过，共 82 项。集成测试覆盖跨 run 排除及旧指纹过期。
- 完整 Maven 仍未通过：此前全量运行记录为 7 个环境级错误，定向回归不能替代全量构建；Twin 学习效果 A/B、BKT 和未来路径概率未做。
