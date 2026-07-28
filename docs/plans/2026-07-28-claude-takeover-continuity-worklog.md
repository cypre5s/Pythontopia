# Claude Code 未完成任务接手工作日志

> 开始时间：2026-07-28
> 当前分支：`main`
> 接手基线：`a8e51f10`

## 交付纪律

- 以瞎猜接口为耻，以认真查询为荣。
- 以模糊执行为耻，以寻求确认为荣。
- 以臆想业务为耻，以人类确认为荣。
- 以创造接口为耻，以复用现有为荣。
- 以跳过验证为耻，以主动测试为荣。
- 以破坏架构为耻，以遵循规范为荣。
- 以假装理解为耻，以诚实无知为荣。
- 以盲目修改为耻，以谨慎重构为荣。

上述要求在每个阶段开始前重新核对。

## Phase 0：接手审计与设计

### 已查询

- Claude Code 最新主会话与两个失败子代理会话；
- `main` 最近提交、Changelog、百晓工坊路由工作日志；
- 后端 Flyway 迁移、费曼 session/graph/judge、JudgeWorkerPool；
- AI Runtime summary/operations、学习证据图、TutorBench；
- 前端费曼 API/Pinia/组件和 Runtime operations 调用链；
- Claude 遗留工作树的 staged/unstaged diff。

### 权威发现

1. 百晓工坊 Phase 1–4 与 ToT/A* 已完成，不重复建设。
2. 当前真正未完成项是：
   - 后端 Flyway 版本冲突；
   - 费曼教学与真判题的十项正确性缺陷；
   - 教师 Runtime 数据主体错误；
   - TutorBench 补救率与零失败语义错误。
3. Claude 的费曼 WIP 未完成 RED/GREEN，且普通前置查询不足以保证并发计分幂等，不能直接合并。
4. 根目录 `A*.md` 为既有未跟踪文件，排除在所有提交之外。

### 基线验证

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:05 CST | 后端全量 | `mvn -s /home/cypress/.m2/settings.xml clean test` | 1042 tests，0 failures，94 errors；根因是四个重复 V186，其他为 ApplicationContext 连锁错误 |

### 用户确认

- 教师 AI Runtime 采用全平台聚合状态，不新增学生/班级选择功能。
- 批准采用“在 `main` 上按证据重新 TDD 实现”的设计。
- 最终所有本任务工作树改动必须收敛到 `main`。
- 工作树只关注并合并最近一周（2026-07-21 至今）有 tip 提交活动的分支；
  更早历史工作树不纳入本次合并，也不删除。

### Phase 0 状态

- [x] 当前仓库与 Claude Code 进度核对
- [x] 失败基线复现
- [x] 方案比较与设计批准
- [x] 设计文档落盘
- [x] 详细实施计划

## Phase 1：恢复后端基线

### 修改

- 保留 `V186__learner_profile_cognitive_style_jsonb.sql`；
- 将另外三个冲突迁移改为连续且唯一的 `V188`、`V189`、`V190`；
- JWT 会话代次注释同步指向 `V188`；
- 新增纯文件系统测试，阻断重复 Flyway 版本再次进入主分支。

### TDD 与验证

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:19 CST | RED | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FlywayMigrationVersionUniquenessTest test` | 1 test / 1 failure；准确列出 4 个 `V186` |
| 2026-07-28 12:20 CST | 增量构建诊断 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FlywayMigrationVersionUniquenessTest,CognitiveStyleColumnMigrationTest,LoopRunServiceTest test` | 唯一性测试通过，但 `target/classes` 残留旧迁移使集成测试失败；确认必须 clean 验证迁移重命名 |
| 2026-07-28 12:21 CST | GREEN | `mvn -s /home/cypress/.m2/settings.xml clean -Dtest=FlywayMigrationVersionUniquenessTest,CognitiveStyleColumnMigrationTest,LoopRunServiceTest test` | 105 个迁移从空库执行到 `V190`；19 tests，0 failures，0 errors |

状态：完成。

## Phase 2：费曼教学链

### 修改

- Java Graph 请求补齐既有 Python `history` 合同；
- 读取本会话最近 12 条历史，在 6000 字符预算内保留最新上下文；
- 删除按解释长度伪造成功与代码的 `fallbackStep`，Graph 不可达统一 502；
- 空误解列表保持 `OPEN`，以 `noticeMd` 区分系统提示和学弟回复。

### TDD 与验证

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:23 CST | RED 1 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanGraphClientTest test` | 编译失败：缺少 `ConversationTurn` 与四参数 `step`，证明 history 合同尚未实现 |
| 2026-07-28 12:24 CST | RED 2 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanSessionServiceTest,FeynmanSessionServiceSecurityTest test` | 编译失败：缺少 history-aware 调用与 `noticeMd`，新行为测试有效 |
| 2026-07-28 12:25 CST | GREEN | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanGraphClientTest,FeynmanSessionServiceTest,FeynmanSessionServiceSecurityTest,FeynmanControllerSecurityTest test` | 12 tests，0 failures，0 errors |
| 2026-07-28 12:27 CST | 格式门诊断 | `mvn -s /home/cypress/.m2/settings.xml spotless:check` | Maven 镜像无法解析 Spotless 运行时依赖；按约定经 7892 代理重试仍未进入格式检查，非代码格式失败；`git diff --check` 另行通过 |

状态：完成。

## Phase 3：费曼真判题链

### 修改

- 教学文本防作弊与 AI 产出代码防泄答分离，合法完整程序不再被 `def/import` 误伤；
- Markdown 代码围栏在送入沙箱前剥离；
- 复用 `JudgeWorkerPool` 执行沙箱评测；
- 裁定扩为 `PASSED / FAILED / ERROR / NA`，保留 `passed` 兼容字段；
- 缺用例、`SYSTEM_ERROR`、worker 异常均不写失败状态或失败学习事件；
- `JUDGED_PASS` 通过条件更新原子认领，只有首个成功请求回流掌握度和成功事件；
- 失败条件更新不得覆盖并发已经写入的 `JUDGED_PASS`；
- 公开/隐藏用例失败信息不回显期望输出。

### TDD 与验证

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:31 CST | RED 1 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanAntiCheatTest test` | 编译失败：缺少代码专用泄答检查与围栏剥离 |
| 2026-07-28 12:33 CST | RED 2 | `mvn -s /home/cypress/.m2/settings.xml -Dtest=FeynmanAntiCheatTest,FeynmanJudgeServiceTest test` | 编译失败：缺少四态 DTO、worker 注入与新防泄答方法 |
| 2026-07-28 12:35 CST | 夹具修正 | 同上 | 17 项已通过，1 项 Mockito 重设桩时误执行旧 Answer；改用 `doThrow`，未修改生产语义 |
| 2026-07-28 12:36 CST | GREEN 1 | 同上 | 18 tests，0 failures，0 errors |
| 2026-07-28 12:36 CST | GREEN 2 | `mvn -s /home/cypress/.m2/settings.xml -Dtest='com.pythontopia.feynman.*Test' test` | 费曼后端全量 30 tests，0 failures，0 errors |

### 前端事实投影

- API DTO 接收 `noticeMd` 与四态判题 `status`，教学/判题超时分别为 35 秒/60 秒；
- Pinia 直接使用后端状态，只有真实 `PASSED/FAILED` 改写当前会话状态；
- 教学失败保留输入并提供重试，系统 notice 与学弟台词分离；
- 概念模式和已通过会话禁用真判题；
- `FAILED / ERROR / NA` 分别使用失败、警示、中性视觉语义。

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-28 12:39 CST | RED | `npx vitest run tests/components/FeynmanPanel.spec.ts tests/api/feynman.spec.ts tests/stores/feynman.spec.ts` | 15 项中 9 项预期失败，命中旧超时、布尔强转、缺重试/notice、概念和已通过仍可判题 |
| 2026-07-28 12:41 CST | GREEN | 同上（`--coverage.enabled=false`） | 15 tests，0 failures |
| 2026-07-28 12:41 CST | 类型 | `npm run typecheck` | 通过 |
| 2026-07-28 12:43 CST | 格式与静态检查 | 定向 `prettier --check`、`eslint` | Prettier 通过；ESLint 0 errors / 2 个既有 Vue 模板换行 warnings |
| 2026-07-28 12:43 CST | 格式后回归 | 同一组 Vitest | 15 tests，0 failures |

状态：完成。

## Phase 4：AI Runtime 与 TutorBench

状态：待开始。

## Phase 5：全链路验收与工作树收敛

状态：待开始。
