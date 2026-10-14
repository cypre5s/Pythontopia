# Web 学习闭环后续改造设计

**状态：** 调研与设计已完成；实现按下方阶段推进。  
**范围：** 仅 Web 主链路 `frontend/`、`backend/`、`services/` 与相关 `docs/`；不改小程序、原生客户端、Electron 或部署到 ECS。  
**目标：** 在复用现有 AutoGen Tutor、KC/FSRS-lite、判题、远征和管理页面的基础上，修复已证实的正确性问题，并完成可复核的全栈验证。

## 1. 已确认的产品与技术边界

- 在线文本与视觉理解统一调用 DeepSeek；视觉图片只作为用户输入，助手结果只消费文本。不得把文本中的图像链接误当成模型生成的二进制图片。
- 本地 `OpenAiCompatProvider` 已限制 `api.deepseek.com` 与 DeepSeek 模型白名单；非文本响应块被过滤。DeepSeek 官方 Vision 文档说明 `deepseek-flash` 接收图片与文字，Chat Completions 响应的 assistant `content` 为文本字段：[Vision](https://api-docs.deepseek.com/guides/vision/)、[Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/)。
- 讯飞文生图、图片理解、语音识别与 TTS 属性当前均固定返回 `false`；RAG 使用本地 hash/BGE embedding，不调用第三方 embedding API。保留这些关闭状态，不新增模型服务。素材图片只用于本地 Web 游戏展示。
- Pythontopia Tutor 已使用 AutoGen `RoundRobinGroupChat(max_turns=1)`；保留现有 Java SSE/API、Pydantic DTO、V199 checkpoint 和前端事件契约。Alethicode 的近期实现用于核对运行语义，不整套复制其 thread/checkpoint 表、API 或未提交工作树。
- 当前学习状态、画像证据、周报和 FSRS-lite 聚合大体已有可信实现；沿用 `LearnerTwinService` 与 `AdaptivePracticePolicy`，不重复建表或引入 BKT/DKT、强化学习或在线 Agent。
- Web 为唯一客户端工作重点。页面之外的题面、代码和学习反馈继续保留在可读 DOM 中；游戏演出只消费服务端确认后的结果。

## 2. 当前代码证据与已知缺口

### 2.1 需要修复的正确性问题

1. **AI 题审核与远征资格脱节（P1）。** 预生成和同步出题都调用 `approveAutomaticallyIfNotDuplicate`，但忽略 `false` 或入库错误继续把题目置为 READY/签发。之后 verified attempt 先记分，审核状态只控制学习事件，因此失败审核的 AI 题可能增加远征分数。现有 `EndlessPregenServiceTest.reviewedAiProblemMovesPendingJobToReadyAndAddsItToSharedBank` 未 stub boolean，Mockito 默认 `false` 仍断言 READY。
2. **前端自动推荐仍有第二套 KC 规则。** `useNextSteps` 从前端 mastery snapshot 挑薄弱点，未过滤 `assessed=false`；`useEndlessRun` 自动把客户端 weak KC 写入 `preferredKc`，可能压过服务端的到期复习/连败优先级。
3. **Tutor 检查点陈旧锁需做针对性验证。** Java 在同一 session 出现超过 4 分钟未更新的 RUNNING 时会标 INTERRUPTED。Alethicode 使用续租，Pythontopia 目前按阶段检查点更新时间。现有 SSE 超时/断连回调可能已经覆盖大多数慢请求；先增加慢调用、超时、同幂等键恢复回归，再按复现结果决定是否加一个只续用 `updated_at` 的 run lease。
4. **远征预加载等待是功能边界问题。** 浏览器最多等 180 秒，后端同一 Graph 请求最多可能经历一次重试，完成时间可超过客户端轮询窗口。后台任务有状态与重试，不是永久卡死；功能正确性通过后再修正等待/继续轮询行为。

### 2.2 已落地、无需重做的部分

- Twin 未评估知识点保留 `null`，来源由已验证学习事件决定；自评 DTO 不接收 evidence/metadata；周报使用同一上海时区七日窗口、独立统计总事件和最多 30 条记忆，并用真实 `applied_delta` 汇总增量。
- Mastery 事件有幂等去重；ReviewState 与真正练习时间独立；画像页主/备行动和周报按钮已有页面级路由测试。
- Boss Phaser 场景、单 Phaser.Game、Kenney CC0 冲击音效、判题成功后命中/失败不扣假血已实现。当前缺口是应用层审核资格与成绩账本的一致性，以及最终多阶段 Boss 的真实浏览器实战复核。
- 管理台已有运维、题目审核、用户三个真实 API 页面；侧栏、色板和表格尺寸接近 Alethicode。尚无同视口视觉对照，不能宣称完全一致。此次仅按“格式”做视觉对齐，不复制 Alethicode 中 Pythontopia 没有数据/API/权限支撑的管理模块。

### 2.3 安全审计边界

- 只读审计未确认客户端伪造远征状态、媒体资源越权、判题沙箱绕过、SSRF、SQL 或明确的 Tutor 取消竞态；仍要用回归测试锁定已覆盖边界。
- 故事板异步媒体队列的拒绝后 PENDING 问题被现有 `isImageGenerationEnabled=false` 前置门禁挡住；图片/TTS 供应商没有启用，本轮不为不可达功能引入新的计费限额系统。
- 可选 Langfuse Compose profile 使用示例级弱默认密钥，生产 Compose 不启用、UI 绑定 loopback。可清理敏感 fallback，但不扩大为 Langfuse 服务重构。
- Mermaid/SVG 清理需要浏览器验证。离线课程包和已下载游戏素材不是模型输出。

## 3. 实施决策

### A. 远征审核和计分共用同一资格

- AI 题只有经本地自动审核成功，或与已批准题记录匹配后才可以进入 READY/签发；审核入库报错时 fail closed，按既有题库兜底，否则明确失败。
- 消费旧 READY 题与开始计分前再次确认当前批准状态，阻止教师撤销自动批准后继续记分/推进 Boss。
- 保留服务端测试结果、run、节点签发和幂等 ledger 的现有权威性；不引入客户端分数或新表。

### B. KC 决策使用一个服务端入口

- 小镇继续使用自身的主线进度来源，但 KC 补强建议只消费 Twin 的服务端推荐；无评估证据时显示“尚未评估”，不从默认 mastery 值推断弱点。
- 远征自动开局不再隐式提交客户端 weak KC。只有学生明确点选 KC 时才发送 `preferredKc`；后端已有明确偏好语义继续保留。

### C. Tutor 优先复用，按证据补生命周期

- 保留现有事件阶段和 projection，不追求复制 Alethicode 的代码结构。
- 核对每种事件的 AutoGen 阶段、保存/恢复、取消、SSE 断流、重复请求和图片隐私契约；先写回归。只有长调用跨过当前 4 分钟阈值的测试能复现误抢占时，才为活动 run 定时 touch 现有 `updated_at`，不加字段/表。

### D. Admin 视觉对照，不造空模块

- 在相同桌面与窄屏视口对比侧栏、顶栏、表单、表格、分页、空态、错误态、危险操作确认和键盘焦点；修正与 Alethicode 格式的实际差异。
- 保留 Pythontopia 品牌、当前 API 和权限模型；不创建只有菜单没有后端数据的页面。

### E. Web 工程可读性

- 只整理本轮触达的代码：统一已有 DTO/变量名、移除本轮发现的死分支或过期注释、修正 lint/type/test 中与改动相关的问题。
- 不对全仓库做无目的格式化；先按 OWASP API 风险检查对象/功能权限、资源消耗与字段授权，按 WCAG 2.2 核对键盘焦点和可访问标签：[OWASP API Top 10](https://api-security.owasp.org/editions/2023/en/0x11-t10/)、[WCAG 2.2](https://www.w3.org/TR/WCAG22/)。
- 学习状态用确定性、可解释规则；间隔练习依据现有 FSRS-lite 与检索练习闭环，不把实验性模型当作已验证增益。来源：[Anki FSRS 指南](https://docs.ankiweb.net/deck-options.html)、[检索练习综述](https://pubmed.ncbi.nlm.nih.gov/26151629/)、[分散练习元分析](https://pubmed.ncbi.nlm.nih.gov/16719566/)、[UNESCO GenAI 教育指南](https://www.unesco.org/en/articles/guidance-generative-ai-education-and-research)。

## 4. 验收顺序与门槛

1. **定向回归先行：** AI 题自动审核 false/异常/撤销批准时不得 READY、不得进入排行榜或推进 Boss；普通已批准题和 Golden 题仍可正常远征。
2. **Tutor：** 真实 DeepSeek 文本与图像输入均返回文本；同一 session 的并发、超时、取消、断流、幂等重试不会留下两个活动 run 或旧 checkpoint 覆盖新状态。报告 AutoGen 阶段与 Alethicode 的逐项差异。
3. **Twin：** 未评估 KC 不作为弱项；主页/画像/复习大厅/远征自动选择一致；明确用户 KC 偏好仍有效。历史日志不替代本轮测试。
4. **Admin 和 Web QA：** 实际 API 下检查关键列表/筛选/分页/审核/用户状态操作和错误态；同视口截图复核布局。关键页面支持键盘操作，状态有可读标签。
5. **Boss browser flow：** 实际运行多阶段 Boss，提交通过与失败各一次；命中演出只在服务端判题通过后发生，失败反馈只展示真实远征 HP；不读取/显示 hidden tests 或答案。
6. **工程与最终性能：** 先完成全部功能再做压测；只测必要的 Tutor/Twin/Boss Web 主链路，并报告实测调用数、耗时分位数、资源与失败样本。性能目标不作为未测事实。

## 5. 研究与项目参照

- DeepSeek 官方视觉与响应契约见上文；本地模型 provider 与真实调用日志以仓库测试和 `docs/operations/execution_status.md` 为证据。
- Alethicode 当前 `HEAD=10a97fe`，其工作区有大量未提交文件；本地 Codex rollout `01a12f26-6bab-7371-8763-06f922c1b13b` 记录 AutoGen 生命周期改动与未完成整体验收。仅将其作为实现参照，不合并其脏工作区。
- 学习数字孪生研究仍强调有效性验证、隐私与教学场景结合；首期只优化已有学习状态，不做人格/情绪推断、复杂因果预测或新模型部署：[数字孪生教育系统综述](https://www.frontiersin.org/journals/education/articles/10.3389/feduc.2026.1955212/abstract)、[UNESCO 指南](https://www.unesco.org/en/articles/guidance-generative-ai-education-and-research)。

## 6. 暂不作为本轮完成条件

- 个性化选题的旧规则对照组、七日延迟复测和统计校准需要真实学生样本与时间，当前只能列为待验证，不伪造效果结论。
- 生产公网、真实学生账户、外部计费后台和 ECS 不属于本轮本地 Web 验收范围。
- 只修复和验证 Web 现有页面；不复制全部 Alethicode 管理功能，不增加在线 Agent、微服务、模型训练任务或数字孪生表。
