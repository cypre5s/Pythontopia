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

- 新增纯规则 `AdaptivePracticePolicy`，按到期复习、连续独立练习失败、目标薄弱 KC、低置信 KC 与未评估 KC 的顺序筛选，满足先修条件并排除本局已用 KC，输出难度档位及推荐理由。此处没有按题目指纹排除重复题。
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

- 未实现题目指纹级近期重复抑制；当前规则只排除本局已用 KC，现有远征指纹用于防止重复题目重复计分，不等于阻止再次发题。
- Tutor 选择的限额是每账号每天 30 次请求；工作流一次请求可触发多个模型调用，本限额不构成 token 或金额支出上限。
- 未实现未来路径对比、BKT、效果对照实验或无尽远征性能指标；按用户要求性能最后测。
- 未启动全栈浏览器联测；未发起新的 DeepSeek 请求。DeepSeek 文本/图片接入与先前两次文图链路验收见本阶段前序 changelog，不应当作本轮新请求证据。
- 周报/记忆投影仍依赖既有事件投影链路；若投影服务外部持久化失败，Twin 的权威掌握度读取仍基于掌握度账本，但记忆时间线可能缺项。
- 对话中粘贴的 DeepSeek API 密钥已暴露，应在 provider 控制台轮换后再做真实请求；工作日志不保存密钥。
- 未修改无关的 HTTP/HTTPS、部署与历史路线工作树内容；未提交代码。
