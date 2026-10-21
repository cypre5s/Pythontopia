# 周报 resource_run 真实 SQL 契约实施计划

> 对应设计：`docs/plans/2026-07-29-weekly-report-resource-run-sql-contract-design.md`

## Phase 1：真实数据库 RED

1. 新建 `WeeklyReportResourceRunIntegrationTest`；
2. 启动 `pgvector/pgvector:pg16`，对空库执行完整 Flyway 迁移；
3. 插入当前用户近期记录、当前用户过期记录和其他用户近期记录；
4. 调用生产 `WeeklyReportService.build`，断言只返回本人近期资源运行；
5. 运行单测试并记录预期失败与实际数据库错误证据。

验收：失败来自生产查询无法把目标记录映射进周报，而不是测试编译、fixture 或迁移错误。

## Phase 2：最小实现与定向 GREEN

1. 将查询的 `Instant` 参数显式转换为 `Timestamp`；
2. 不改 SQL 文本、schema、DTO 和 fail-soft 策略；
3. 运行新集成测试；
4. 联跑 `WeeklyReportServiceTest`、`TwinWeeklyControllerTest`。

验收：真实 SQL 合同和既有单元/控制器合同全部通过。

## Phase 3：完整验证与收口

1. 执行 Backend 全量测试；
2. 隔离复跑任何容器环境错误或失败测试类；
3. 执行受影响文件格式/差异检查；
4. 更新 `docs/project/CHANGELOG.md` 与小步重构工作日志；
5. 只提交本切片的生产、测试和跟踪文档。

验收：提交边界干净，业务断言无回归，未验证项与剩余风险明确。
