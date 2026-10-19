# 2026-10-19 P0/P1/P2 验收工作日志

## P0：真实学习旅程

- 使用一次性学生账号完成注册、Twin 首次读取、主线推荐与阶段跳转、官方题 #476 的 Piston 正式判题、Twin 更新、远征挑战、服务端结算及刷新恢复。
- Twin 首次读取为 0/65；Piston submission 247 对应题目 476，状态 `PASSED`，掌握事件 `PY43-KC-016 / PRACTICE_PASS` 已落库。远征结算后 Twin 又出现 `PY43-KC-001 / PRACTICE_PASS`。
- 对远征 `SETTLE_CHALLENGE` 原请求重放后，PostgreSQL 仍是 1 条 verified attempt、1 条 settlement，分数各为 100；刷新后服务端恢复相同 run 状态。
- 另一个 Piston submission 248 用于 Outbox 断连恢复，状态同样为 `PASSED`。AI 审核撤销题 Postgres 集成测试 1/1 通过；资源微测并发幂等 Postgres 集成测试 1/1 通过，前端 workshop API 幂等测试 5/5 通过。

## P1：Outbox 故障恢复

- 在本地 PostgreSQL 对学生画像行加事务锁，再经真实 `/api/submissions` 与 Piston 产生 submission 248。画像投影 outbox 244 提交后保持 `PENDING`、`attempt_count=0`；数据库确认应用线程正等待该行锁。
- 中断并终止本机后端进程组，提交请求收到真实 `RemoteDisconnected`。后端停止期间 outbox 244 仍在 PostgreSQL 中；重启 `./dev.sh --sandbox`、释放行锁后，调度器自动将其处理为 `APPLIED`，`attempt_count=1`，画像证据计数只增加一次。
- 现有 `ProfileDimensionProjectionOutboxPostgresTest` 全类 7/7 通过，覆盖重试退避、8 次失败隔离、修复重放和用户内顺序。
- 管理员失败任务查询、详情核对与重放审计接口尚未实现。审计存储方案待确认后继续。

## P2：请求与窄屏体验

- `/world` 的 HUD、学习目标面板和伴学指南原先分别触发 `mainline/overview`。初始加载收拢至 `WorldPage`；新页面回归测试确认单点调用，真实浏览器请求从 3 次降为 1 次。
- 320px 和 375px 检查世界、冒险地图、关卡、训练、远征、工坊，均无页面横向溢出；不存在的关卡显示明确的“未解锁或不存在”提示，浏览器无 pageerror。
- 前端全量 Vitest：280 个测试文件、2,676 项通过；`npm run typecheck` 通过。对应代码提交 `f0376158` 已推送到 `origin/main`。

## 清理与未完成项

- 仅使用一次性学生账号和本地开发栈；该账号仍需在全部 P1 管理员验收结束后删除。
- 本地栈当前运行以支持后续 P1 验收；启动钩子生成的课程 manifest 时间戳已恢复。
- 尚未完成 Outbox 管理 API、迁移及管理员 HTTP 端到端重放核验；因此整体 P1 仍未完成。
