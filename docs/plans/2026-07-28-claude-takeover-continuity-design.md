# Claude Code 未完成任务接手与学习闭环连贯性设计

> 日期：2026-07-28  
> 状态：已获用户批准  
> 实施原则：不增加大功能；复用现有接口；先查证、再测试、后实现；所有任务工作树最终收敛到 `main`

## 1. 背景与权威基线

Claude Code 在 `main` 完成了主线断点续学入口、角色移动方向惯性与行走帧率修正，并完成前端类型检查、单测和两条真实浏览器验收。后续“深化现有功能、检查逻辑正确性与流程连贯性”的收尾被登录过期中断。

本次接手以当前仓库和运行结果为权威，而不是直接相信旧会话结论：

- 当前分支：`main`，接手基线 `a8e51f10`。
- 根目录 `A*.md` 是用户/既有未跟踪文件，不纳入本任务提交。
- 后端当前全量基线：
  - 命令：`mvn -s /home/cypress/.m2/settings.xml clean test`
  - 结果：`Tests run: 1042, Failures: 0, Errors: 94, Skipped: 0`
  - 根因：四个迁移共同占用 Flyway 版本 `V186`；大量 `ApplicationContext` 错误是连锁症状。
- Claude 留下两个未完成工作树：
  - 后端基线恢复：已定位迁移冲突，但未完成最终全量验证。
  - 费曼、AI Runtime、TutorBench：约千行 WIP，尚未完成 RED/GREEN，不直接合并。

## 2. 方案选择

### 方案 A：直接合并 Claude WIP

优点是速度快；缺点是 WIP 未完成测试先行，费曼成功幂等只做了普通前置查询，仍可能被并发请求重复计分。拒绝。

### 方案 B：在 `main` 按证据重新 TDD 实现

把 Claude WIP 只当作缺陷线索，重新核对接口、建立失败测试、实施最小修复、逐阶段回归。能够保持改动外科手术式且保留可靠证据。采用。

### 方案 C：重构费曼和 Runtime 整体架构

虽然可以统一更多模型，但会扩大数据契约和工作量，违反“不增加大功能”。拒绝。

## 3. 总体架构

不新建平行业务系统，继续复用：

- Flyway 迁移链和现有 PostgreSQL Testcontainers 验收；
- `FeynmanSessionService`、`FeynmanGraphClient`、`feynman_turn`；
- `FeynmanJudgeService`、`JudgeCaseEvaluator`、`JudgeWorkerPool`；
- `AiRuntimeSummaryService`、现有 Runtime operations 端点和面板；
- `LearningEffectTraceRepository`、`TutorBenchEvaluator`；
- Vue/Pinia 费曼状态与现有 `RuntimeOperationsPanel`。

改动分成五个可独立验证的阶段，每个阶段同步更新工作日志和 Changelog，并做窄提交。

## 4. 阶段设计

### 阶段 1：恢复后端基线

- 保留已有且有文件名耦合测试的 `V186__learner_profile_cognitive_style_jsonb.sql`。
- `users_token_version`、`classroom_kc_snapshot_learning_count`、`drop_retired_economy_and_fast_travel` 顺延为 V188–V190。
- 更新代码注释和测试中的精确迁移引用。
- 用迁移版本唯一性测试、真实空库 Flyway 和后端全量测试证明修复。

### 阶段 2：费曼教学链

数据流：

```text
学生讲解
→ 事务内读取既有 feynman_turn 历史
→ 调用现有 Graph history 契约
→ Graph 成功：保存 TUTEE 轮与误解状态
→ Graph 不可达：502 + 整个事务回滚
```

规则：

- 删除“文本长度达到 12 就算教会”的本地伪成功。
- Graph 不可达返回 `FEYNMAN_TUTEE_UNAVAILABLE`，不落假回复、假代码或画像失败。
- 历史按时间正序发送，限制轮数和字符预算，只裁旧历史，不裁本轮讲解。
- 空误解集合不满足 `allResolved`；状态保持 `OPEN`，返回诚实的系统提示，引导先做题形成真实错误证据。

### 阶段 3：费曼真判题

判题结果使用四态：

- `PASSED`：真实通过，允许一次掌握度和画像回流；
- `FAILED`：学生产出代码真实未通过；
- `ERROR`：沙箱、用例或判题基础设施故障，不归罪学生；
- `NA`：概念模式没有代表题，属于中性说明。

并发幂等不依赖普通“先查后写”：

1. 判题前已是 `JUDGED_PASS` 时回放既有成功结论。
2. 真实 AC 后执行条件更新：仅当当前状态不是 `JUDGED_PASS` 才转换为通过。
3. 只有成功取得状态转换权的事务才能写掌握度和 `TaughtAiSucceeded`。
4. 并发落后请求得到条件更新 0 行后只回放结论。

其他规则：

- 产出代码只检查 `answer_key` 泄答标记；`def/import/代码围栏` 只适用于教学文本反作弊。
- Markdown 围栏剥离后送入现有 `JudgeCaseEvaluator`。
- Feynman 控制器直接从 Tomcat 调用 service，不处于 `JudgeWorkerPool` 内；因此提交到该有界池不会形成嵌套等待。
- 前端教学超时晚于后端 Graph connect+read 上界；判题超时覆盖最多十个用例的执行上界。
- 概念模式和已通过会话禁用判题按钮；Graph 失败保留讲解原文供重试。

### 阶段 4：AI Runtime 与 TutorBench

Runtime 数据主体：

- 学生调用现有 operations 端点：读取本人数据，主体标记 `SELF`。
- 教师/管理员调用同一端点：读取全平台聚合数据，主体标记 `PLATFORM`。
- 不增加学生选择器或新端点。
- 聚合查询只返回计数/状态，不下发单个学生身份或学习明细。
- 使用一次聚合查询读取现有学习事件、资源、微测、掌握度和资源运行事实，避免逐学生 N+1。
- 前端面板显示主体标签，防止把教师本人的空证据误读成平台状态。

TutorBench：

- `remediationRate = remediatedFailedChecks / failedChecks`。
- `failedChecks == 0` 表示补救维度 N/A；不因“从未失败”判负。
- 仍独立检查掌握度增长是否超过通过微测允许的上限。

### 阶段 5：连贯性与交付验收

重点复核两条闭环：

```text
费曼教学 → Graph 多轮理解 → 代码产出 → 真判题
→ 掌握度/画像只在真实成功后回流
```

```text
学习事件/资源/微测/掌握度
→ Runtime SELF 或 PLATFORM 聚合
→ 学生/教师面板显示正确数据主体和状态
```

并回归 Claude 已完成的：

- 主线断点续学入口；
- 角色移动方向惯性、连续行走和 walk/run 帧率；
- 工坊快速旅行 gate。

## 5. 错误处理

- Agent/Graph 不可达：fail fast，返回统一业务错误，不静默降级。
- 判题系统错误：`ERROR`，不写失败事件、不降低画像。
- 无用例：平台数据缺陷，`ERROR`。
- 概念模式：`NA`，不显示红色失败卡。
- Runtime 平台无任何证据：`INSUFFICIENT_EVIDENCE`，主体仍明确为 `PLATFORM`。
- 聚合查询异常：沿用统一后端错误处理，不伪造 PASS。

## 6. 测试与验收

每项实现遵循 RED → GREEN → 回归：

- 后端专项：迁移唯一性、Feynman session/graph/judge、Runtime service/controller、effect repository、TutorBench。
- 前端专项：Feynman API/store/component、Runtime operations API/component/教师页。
- 全量：后端 Maven、前端 typecheck/lint/test、Graph/RAG pytest。
- 运行态：`./dev.sh` 四服务健康检查。
- 浏览器：新增费曼连贯性专项 E2E，并复跑 Claude 已通过的主线/主城浏览器验收。
- 静态：`git diff --check`、窄范围格式检查。

## 7. 工作树与提交边界

- `main` 是最终交付分支。
- 每个阶段只暂存本阶段精确文件，不使用宽泛 `git add -A`。
- 结束前枚举 Git worktree，只纳入分支 tip 提交时间位于最近一周
  （2026-07-21 至 2026-07-28）的工作树：
  - 本任务相关改动必须进入 `main`；
  - 已合并历史工作树验证其 HEAD 是 `main` 祖先；
  - 无关、未验证或只有本地脏改动的历史工作树不盲目并入产品代码，并在最终报告明确列出。
- tip 提交时间早于 2026-07-21 的历史工作树不属于本次合并范围，也不删除。
- 不删除用户工作树，不清理用户未跟踪文件，除非用户另行明确授权。
