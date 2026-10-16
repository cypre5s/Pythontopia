# Web 学习闭环与安全收尾设计

日期：2026-10-16
范围：Pythontopia Web、Spring Boot、Graph/RAG；不含 Android、Electron、微信端、ECS 或 GitHub 部署。

## 目标与现状基线

本轮目标是收口真实学习流程与已确认的安全/一致性缺口，不重建数字孪生、远征或管理平台。最近的工程记录和只读复核已确认：

- 22 个概念型 KC 已有同 KC 的审核选择、判断、填空题；不适合代码判题的 KC 使用这些客观题是正式规则。
- 普通学生已在浏览器完成无尽远征 Boss 三阶段、服务端判题结算及 Twin 学习结果回流；判题与掌握度事件的事务故障恢复已有 PostgreSQL 回归。不要重复开测试账号或重跑该通关。
- LearnerTwinService、AdaptivePracticePolicy、学习事件、KC 掌握度和 FSRS-lite 已组成统一状态与推荐闭环；当前缺的是长期学习效果对照，不是再加一套画像或 Agent。
- Tutor 已由固定阶段 AutoGen 运行，并有 checkpoint、取消和现有 API/SSE 持久化边界。Tutor 的 TRANSFER 仍只是反思/迁移卡片，没有可信题目 ID、正式判题绑定或提交回流。
- 管理台已有运维、题目审核、用户管理三个真实数据页面。Alethicode 管理端包含许多 Pythontopia 没有 API/权限/数据契约的页面；其当前改动也在未提交工作树中，因此不能直接复制或把它描述成稳定发布实现。
- 最近安全审计仍有两项可落地缺口：无尽远征自动选择客观题时，客观题已用尽后可能继续领取 READY/审核编程题；单条/批量学习事件批次逐项写库，批次中途数据库失败可能留下部分事件。

历史验证结果见 [整体任务清单](./2026-10-16-overall-work-checklist.md)、[远征与 Twin 研究设计](./2026-10-12-expedition-twin-research-design.md) 和 [Tutor AutoGen 工作日志](./2026-10-12-tutor-autogen-worklog.md)。这些历史结果只用于避免重复测试；本轮不把它们表述为当前重新运行的结果。

## 调研结论

| 来源 | 可支持的结论 | 对本项目的取舍 |
|---|---|---|
| [Dunlosky 等：有效学习技术综述](https://www.psychologicalscience.org/journals/pspi/1529100612453266/) | 练习测试与分散练习在多年龄、能力和材料类型中有较强证据；复读/划线的效用较低。 | 优先保证题目可判、反馈真实、复习有间隔。题目可以是代码或同 KC 选择/判断/填空；不把“必须写代码”当作学习有效性的代理。 |
| [Khajah 等：How deep is knowledge tracing?](https://arxiv.org/abs/1604.02416) | BKT 扩展在特定数据集评估中可达到与 DKT 相近的预测表现；复杂序列模型的收益依赖数据与基线。 | 首期继续使用现有确定性掌握度、学习事件和 FSRS-lite；不部署 DKT/AKT 或训练新模型，也不把启发式置信度称为概率。 |
| [Frontiers 2026：工程教育数字孪生系统综述](https://www.frontiersin.org/journals/education/articles/10.3389/feduc.2026.1955212/abstract) | 现有研究常是案例或探索性设计，经过验证的学习结果测量有限。 | Twin 展示证据来源与数据不足；不宣称预测分数或心理状态。长期效果需真实样本及延迟测验，不用单次浏览器闭环冒充成效。 |
| [Slay the Spire 官方介绍](https://store.steampowered.com/app/646570/Slay?l=english) | 路线选择提供风险/安全取舍，遭遇与奖励随局变化。 | 只借鉴可读分支和明确取舍；课程内容、题目、地图素材与数值仍由 Pythontopia 自有系统决定，不复制专有内容，也不声称其机制能提升学习效果。 |

## 设计决定

### 1. 保持一个可信学习状态与一个选题入口

真实判题和服务端学习事件是 KC 能力证据；访谈与自评仅表达目标/偏好；生成式 AI 只可提出建议。沿用现有 LearnerTwinService、AdaptivePracticePolicy、ReviewStateService、KC 表和题库，不新增在线 Agent、微服务或重复掌握度表。没有证据的 KC 保持“尚未评估”。

无尽远征的题型由 KC 与可用课程题决定：可编程 KC 走代码判题；不适合代码的 KC 使用同 KC 审核 CHOICE/JUDGE/FILL。自动选择客观题时，一旦该 KC 的客观池耗尽就返回明确的不可用错误，不能从 READY、审核库或 Graph 取不匹配的代码题。重复题继续由服务端 fingerprint 规则处理。

### 2. 将批量事件作为一个原子提交

`POST /api/learning/events/batch` 保留既有类型白名单、字段校验、16/512 KiB body 上限、条数上限与单条去重语义；同一批写入放进一个数据库事务。任意一条持久化失败时整批回滚，客户端可安全重试。只增加失败中途的 PostgreSQL 回归，不新增表或后台补偿任务。

### 3. Tutor AutoGen 与 TRANSFER

保留 Pythontopia 已有固定阶段 AutoGen、Java/SSE 契约、owner 校验、附件隔离、原生团队 checkpoint 与断流取消。Alethicode 当前 AutoGen 改动位于未提交工作树；只补齐经过验证且与本项目接口兼容的实际能力，不复制其数据库、Redis/WebSocket 或管理页面实现。

TRANSFER 的首期安全条件是：题目由服务端从可信题源选取或生成并审核，判题数据只保存在服务端，题目绑定到原用户、Tutor session 和源 KC；前端仅得到公开题面与 opaque ID；正式判题 attempt 能被服务端关联回原 Tutor session，并按幂等学习事件回流 Twin。非编程 KC 必须使用适用的客观题。若现有表和 attempt 路由不能无歧义表达这条绑定，就先保持反思卡片，不允许通过把 `TRANSFER_ATTEMPT` 改成 `true` 来伪造迁移证据。

### 4. 管理台与安全/界面收尾

管理端只覆盖已有真实后端能力，复用 Pythontopia 的 DTO、角色校验和数据库字段；可以匹配 Alethicode 的共享视觉模式与交互，但不复制其 Pythontopia 无契约的菜单或用占位按钮伪装完成。完整安全清单以可复现漏洞为入口，已修复项不重复跑；生产代理、外部 Piston ACL 与 ECS 明确不属于本地验收范围。

## 实施顺序与验收

1. 对无尽远征客观题分支补 RED 用例：题库有该 KC 的审核客观题但均已签发，同时 READY/审核库存在代码题，要求返回客观题池耗尽错误且不领取/生成代码题。补 FILL 的题面投影和服务端判分回归。定向 Boss tests、PostgreSQL 相关集成和 Spotless 通过。
2. 为学习事件批量端点补“第 N 条插入失败”的 PostgreSQL RED/GREEN；验证前序行没有残留、整批重放只有一份事件。运行相关 controller/service/repository 测试与 Spotless。
3. 基于现有数据库与判题契约决定 TRANSFER 是否能零新增持久化地安全闭环。若需要改造，先列出最小绑定存储与外部契约影响，再按 RED/GREEN 覆盖 owner、session、题目 ID、重复 attempt、失败 attempt、隐藏测试不外泄及 Twin 事件。测试范围只覆盖变更链路。
4. 依据现有真实 API 页面核对管理台可操作控件与窄屏状态；仅修复确认无响应、DTO 不一致或可读性问题。新增改动做定向 Vue tests、typecheck、ESLint；不添加没有后端能力的 Alethicode 页面。
5. 更新 changelog、工作日志与本清单，清楚标注实际测试、静态审计和待验证内容。性能指标最后进行；没有启动可用 Graph/Piston 的完整 Web 全栈时，不填写远征 P50/P95、命中率或恢复率。

## 明确保留的限制

- 不重跑已经完成的普通 STUDENT Boss 浏览器通关、Twin 回流、已完成的 DeepSeek 文本/图片输入探针或重复模型生成。
- 不访问 ECS/GitHub；不处理 Android、Electron、微信端；不重新设计像素场景。
- 7 日延迟学习结果、新旧推荐 A/B、生产 Piston ACL、生产网络/日志和供应商账单需真实样本或外部访问，仍标待测，不用单元测试替代。
