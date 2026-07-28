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
- [ ] 详细实施计划

## Phase 1：恢复后端基线

状态：待开始。

## Phase 2：费曼教学链

状态：待开始。

## Phase 3：费曼真判题链

状态：待开始。

## Phase 4：AI Runtime 与 TutorBench

状态：待开始。

## Phase 5：全链路验收与工作树收敛

状态：待开始。
