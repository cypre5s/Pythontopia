# 周报 resource_run 真实 SQL 契约设计

> 日期：2026-07-29
> 切片：P0-2b
> 状态：设计确认，等待 RED

## 1. 问题与证据

ECS 公网全流程验收曾记录 `WeeklyReportService` 查询 `resource_run` 时出现
`bad SQL grammar`。服务会捕获查询异常并返回空列表，因此整份周报仍能生成，但“本周资源运行”
片段会静默缺失。

当前代码与数据库事实如下：

- `V021__resource_run.sql` 已提供查询所需的 `user_id`、`agent_name`、`scenario`、`status`、
  `evidence_page_ids`、`created_at` 列，后续迁移未移除这些列；
- `created_at` 类型为 `TIMESTAMPTZ`，索引覆盖 `user_id, created_at DESC`；
- 查询文本的字段名、过滤条件、排序和限制均与 schema 一致；
- `WeeklyReportService` 将 `java.time.Instant` 直接作为 `JdbcTemplate` 可变参数传入；
- 同仓其他 PostgreSQL 时间范围查询先使用 `java.sql.Timestamp.from(...)` 再绑定。

因此本切片把根因假设收敛为：PostgreSQL JDBC 无法从未显式声明 SQL 类型的 `Instant`
参数推断 `TIMESTAMPTZ` 绑定类型，而不是 schema 漂移或 SQL 字段错误。真实数据库测试必须先证明
这一点，不能继续用 mock `JdbcTemplate` 代替 SQL 契约。

## 2. 目标与非目标

### 目标

1. 在应用全部 Flyway 迁移的真实 PostgreSQL/pgvector 上执行生产查询；
2. 最近 7 天内、当前用户的成功资源运行进入周报；
3. 其他用户和时间窗外记录不进入周报；
4. `evidence_page_ids` 的数组长度稳定映射为 `evidenceCount`；
5. 保留当前倒序与最多 10 条的产品合同；
6. 保留资源片段 fail-soft，不让既有局部查询异常拖垮整份周报。

### 非目标

- 不新增或修改数据库迁移；
- 不修改周报 REST API 或 DTO；
- 不改变 7 天 UTC 日期边界；
- 不重构整个 `WeeklyReportService` 的仓储层；
- 不借机处理其他周报聚合源或事务边界。

## 3. 最小方案

在 `WeeklyReportService` 中继续使用现有参数化 SQL，仅将：

```java
weekStart
```

改为：

```java
Timestamp.from(weekStart)
```

这与仓库内其他 `TIMESTAMPTZ` 范围查询一致，既保留参数化查询，也避免向 SQL 文本拼接时间。
异常捕获和空片段降级保持不变。

新增一个专用 Testcontainers 集成测试：

- 使用 `pgvector/pgvector:pg16`；
- 对空库执行完整 Flyway 迁移；
- 构造当前用户近期记录、当前用户过期记录和其他用户近期记录；
- 通过真实 `JdbcTemplate` 调用 `WeeklyReportService.build`；
- 断言只返回当前用户的近期记录，并验证 agent、scenario、status、证据计数。

原有 Mockito 单元测试继续负责记忆、洞察、掌握度聚合的轻量分支覆盖；新测试负责 SQL、
类型绑定和结果映射的跨层合同。

## 4. 验收标准

- 修复前：真实 PostgreSQL 测试因资源列表为空而 RED，日志出现当前查询失败；
- 修复后：新集成测试 GREEN；
- `WeeklyReportServiceTest` 与 `TwinWeeklyControllerTest` 保持 GREEN；
- Backend 全量测试没有业务断言回归；若 Testcontainers 仍受本机随机端口冲突影响，必须逐个隔离
  复跑失败测试类并保留证据；
- `git diff --check` 通过；
- changelog 与工作日志同步记录 RED/GREEN、未验证项和演示链路影响。

## 5. 风险

- 真实 Testcontainers 会增加单类测试启动时间，但这是覆盖 JDBC 驱动、迁移和数组映射所需成本；
- 继续 fail-soft 意味着未来其他 SQL 违约仍可能只表现为空片段；本切片通过真实合同测试降低已知
  查询的风险，不扩大到全周报错误策略重构；
- 本地修复通过后仍需随下一次 ECS 发布复核原生产告警是否消失。
