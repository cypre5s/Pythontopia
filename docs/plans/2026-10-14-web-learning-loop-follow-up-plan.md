# Web 学习闭环后续实施计划

设计依据：[Web 学习闭环后续改造设计](2026-10-14-web-learning-loop-follow-up-design.md)。依照用户“功能优先、性能最后”要求分阶段交付；每阶段记录变更、定向测试和运行日志，再进入下一阶段。

## Phase 1 — 远征题目审核与计分一致性

1. 为 `EndlessPregenServiceTest` 增加审核 false、审核抛出数据访问异常用例；先确认当前测试能暴露 READY 缺口。
2. 修正 `EndlessPregenService`：只有审核成功或已批准的精确重复项才进入 READY；失败进入现有 FAILED 状态。
3. 为 `BossServiceTest` 增加同步签发审核失败、READY 题被撤销批准、无学习/远征分等回归；必要时以 Golden/已批准题兜底。
4. 修正签发与 verified-attempt 入口，让远征评分和 Boss 阶段推进都受当前审核资格约束。
5. 运行远征单元测试与相关 Postgres 集成测试；更新 changelog/worklog 后提交本阶段。

## Phase 2 — 统一 Twin 与远征的 KC 推荐

1. 为 `useNextSteps.spec.ts` 增加 assessed/unassessed 对照用例，确保默认值不构成薄弱证据。
2. 将 `useNextSteps.ts` 的 KC 选择改为消费现有 Twin 推荐；保留主线任务进度计算。
3. 为 `useEndlessRun` 加自动开局与显式 KC 选择回归；自动策略交给服务端，学生明确选择时保留 preferredKc。
4. 运行 Twin/Home/ReviewHall/Boss 相关前后端回归，更新日志并提交。

## Phase 3 — AutoGen Tutor 生命周期核对与补齐

1. 按事件类型比对 Alethicode AutoGen 阶段与 Pythontopia `tutor_workflow_graph.py`、Java projection；保留现有 API/DTO/V199。
2. 增加长请求、SSE 超时/断流、取消后同幂等键重试、重复请求与 checkpoint CAS 回归。
3. 若长调用确实越过 4 分钟抢占边界，复用 checkpoint `updated_at` 实现活动 run 续租，不新增数据库字段或表；否则记录现有超时保护已足够的实测证据。
4. 运行 Graph Tutor/provider 与 backend Tutor tests；本阶段至少执行一轮真实 DeepSeek 文本和图片输入全栈调用，确认文本结果与附件隐私边界。
5. 更新 changelog/worklog 并提交。

## Phase 4 — 管理台与 Web 交互核验

1. 用 Alethicode 当前本地实现作只读参照，对 Pythontopia 运维、题目审核、用户管理页面做同视口桌面/窄屏截图对照。
2. 只修复可观察到的版式差异，不添加没有真实 API 的管理入口。
3. 用真实本地后端核验列表/筛选/分页/审核决定/用户状态切换/错误态与键盘焦点；不以 mock-only 浏览器记录冒充联测。
4. 更新管理员和 Web 工程日志并提交。

## Phase 5 — 最终 Web 联测与工程清理

1. 在真实本地全栈上完成 Tutor 文本/图像输入、画像推荐、远征题目审核门槛、Boss 通过/失败演出闭环。
2. 运行受影响模块的完整单测、类型检查、lint、构建和集成测试；全量套件若遇环境错误，隔离重跑并报告实际结果。
3. 对本轮触达文件做命名、注释、死代码和可访问性审查；不全仓机械格式化。
4. 完成多阶段真实 Boss 浏览器流程，检查控制台和网络错误；更新 changelog/worklog 并提交。

## Phase 6 — 性能测量（最后）

1. 先定义本地 Web 主链路基线，再对 Tutor/Twin/Boss 必要接口采样。
2. 记录样本量、模型调用数、P50/P95、失败率及是否含冷启动；模型测试总量保持可审计并避免无目的循环。
3. 只修复有数据支持的瓶颈；重新测量并记录环境限制。未跑的生产/公网指标继续标记待测。
