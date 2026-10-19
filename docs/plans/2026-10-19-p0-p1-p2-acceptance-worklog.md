# 2026-10-19 P0/P1/P2 验收工作日志

## P0：真实学习旅程

- 使用一次性学生账号完成注册、Twin 首次读取、主线推荐与阶段跳转、官方题 #476 的 Piston 正式判题、Twin 更新、远征挑战、服务端结算及刷新恢复。
- Twin 首次读取为 0/65；Piston submission 247 对应题目 476，状态 `PASSED`，掌握事件 `PY43-KC-016 / PRACTICE_PASS` 已落库。远征结算后 Twin 又出现 `PY43-KC-001 / PRACTICE_PASS`。
- 对远征 `SETTLE_CHALLENGE` 原请求重放后，PostgreSQL 仍是 1 条 verified attempt、1 条 settlement，分数各为 100；刷新后服务端恢复相同 run 状态。
- 另一个 Piston submission 248 用于 Outbox 断连恢复，状态同样为 `PASSED`。AI 审核撤销题 Postgres 集成测试 1/1 通过；资源微测并发幂等 Postgres 集成测试 1/1 通过，前端 workshop API 幂等测试 5/5 通过。

## P1：Outbox 故障恢复

- 在本地 PostgreSQL 对学生画像行加事务锁，再经真实 `/api/submissions` 与 Piston 产生 submission 248。画像投影 outbox 244 提交后保持 `PENDING`、`attempt_count=0`；数据库确认应用线程正等待该行锁。
- 中断并终止本机后端进程组，提交请求收到真实 `RemoteDisconnected`。后端停止期间 outbox 244 仍在 PostgreSQL 中；重启 `./dev.sh --sandbox`、释放行锁后，调度器自动将其处理为 `APPLIED`，`attempt_count=1`，画像证据计数只增加一次。
- 现有 `ProfileDimensionProjectionOutboxPostgresTest` 全类 8/8 通过，覆盖重试退避、8 次失败隔离、修复重放、分页查询和用户内顺序。
- 按确认的“最近一次操作者/时间 + 累计次数”实现 P1：ADMIN-only `GET /api/admin/profile-projection-outbox/failed?page=0&size=50` 分页列出最早失败记录，`GET /{outboxId}` 读取状态；既有 PUT 修复接口记录当前管理员 ID 和时间。V226 扩展原 Outbox 表并增加失败列表索引。读取接口不返回画像 delta 或幂等键。
- 本地 PostgreSQL 准备 FAILED outbox 245，经真实 ADMIN 登录 HTTP 调用列表、详情、PUT 修复；PUT 返回 202，轮询详情最终为 APPLIED，`manualReplayCount=1`，`lastReplayedBy=848`、`lastReplayedAt` 非空，GET 响应无 delta/idempotency key。管理员测试账号提升权限后完成验收，随后删除账号及级联数据。
- Controller 测试 10/10、PostgreSQL 集成测试 8/8、后端全量 `mvn -B test` 1,775/1,775（0 failures/errors/skips）通过。Testcontainers 关闭后有现存后台维护任务连接拒绝日志；Surefire 最终 BUILD SUCCESS。

## P2：请求与窄屏体验

- `/world` 的 HUD、学习目标面板和伴学指南原先分别触发 `mainline/overview`。初始加载收拢至 `WorldPage`；新页面回归测试确认单点调用，真实浏览器请求从 3 次降为 1 次。
- 320px 和 375px 检查世界、冒险地图、关卡、训练、远征、工坊，均无页面横向溢出；不存在的关卡显示明确的“未解锁或不存在”提示，浏览器无 pageerror。
- 前端全量 Vitest：280 个测试文件、2,676 项通过；`npm run typecheck` 通过。对应代码提交 `f0376158` 已推送到 `origin/main`。

## 清理与未完成项

- 一次性账号 ID 848 已删除，确认 users 表无此用户；其 outbox 与用户数据随外键级联清理。临时凭据文件已删除。
- 本地开发栈已停止，数据库卷保留；启动钩子更新的 course43 manifest 时间戳已恢复。
- P0/P1/P2 验收范围已完成；P1 接口提供运维 API，不包含单独的管理员前端页面。
