# Pythontopia 小步重构工作日志

> 开始时间：2026-07-29
> 当前分支：`refactor/workshop-history-account-isolation`
> 总体输入：用户提供的八方向重构审阅
> 当前切片：P0-1 百晓工坊历史缓存账户隔离

## 交付纪律

- 每次只处理一个可验证坏味道，不夹带技术栈升级或无关格式化；
- 所有生产改动先有能正确失败的测试；
- 每个切片结束前执行影响范围内完整回归、类型检查、lint 和真实运行态验收；
- `docs/project/CHANGELOG.md` 只记录已经验证的结果，本日志实时记录 RED/GREEN 过程；
- 根目录用户/既有未跟踪文件不纳入任何提交。

## Slice 1：百晓工坊历史缓存账户隔离

### 基线

| 时间 | 范围 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | 前端全量基线 | `npm run test -- --run` | 242 files / 2412 tests passed；0 failures |

### 设计与计划

- 设计：`docs/plans/2026-07-29-workshop-history-account-isolation-design.md`
- 实施：`docs/plans/2026-07-29-workshop-history-account-isolation-implementation.md`
- 采用显式 `userId + limit` 缓存键、可取消读取、Session 生命周期清理、组件晚到结果隔离和
  KeepAlive 账户级重建；后端继续以 JWT 为唯一授权事实源。

### TDD 证据

| 时间 | 阶段 | 命令 | 结果 |
|---|---|---|---|
| 2026-07-29 | Vitest RED | `npx vitest run tests/api/workshopHistory.spec.ts tests/stores/session.spec.ts tests/components/workshop/ChatHistoryRail.spec.ts tests/pages/WorkshopPage.spec.ts src/test/App.test.ts` | 9 个预期失败：owner key、abort/clear、session cleanup、组件 owner 切换、KeepAlive 重建均未实现；其余 26 passed |
| 2026-07-29 | KeepAlive RED 校准 | `npx vitest run src/test/App.test.ts` | 初始 WorkshopPage 挂载 1 次，user 101→202 后仍为 1 次；1 expected failure / 3 passed |
| 2026-07-29 | Chromium RED | `BASE_URL=http://127.0.0.1:5173 npx playwright test e2e/workshopHistoryAccountIsolation.spec.ts --project=chromium --retries=0` | 预期失败：B 登录后 A 唯一历史 marker 仍持续存在，expected 0 / received 1 |
| 2026-07-29 | Focused GREEN | `npx vitest run tests/api/workshopHistory.spec.ts tests/stores/session.spec.ts tests/components/workshop/ChatHistoryRail.spec.ts tests/pages/WorkshopPage.spec.ts src/test/App.test.ts` | 5 files / 35 tests passed；owner key、清理取消、晚到结果、会话切换、组件重载与 KeepAlive 重建全部转绿 |
| 2026-07-29 | Chromium GREEN | `BASE_URL=http://127.0.0.1:5173 npx playwright test e2e/workshopHistoryAccountIsolation.spec.ts --project=chromium --retries=0` | 1 passed；同一 SPA/浏览器内 A 登出、B 登录后，A 唯一历史 marker 为 0 |
| 2026-07-29 | 前端全量 | `npm run test -- --run` | 242 files / 2418 tests passed；0 failures |
| 2026-07-29 | 类型检查 | `npm run typecheck` | 通过；`vue-tsc --noEmit` 0 errors |
| 2026-07-29 | ESLint | `npm run lint` | 通过；0 errors / 114 warnings，warnings 均不在本切片修改行 |
| 2026-07-29 | 格式检查 | `npx prettier --check --single-quote <11 个本切片前端文件>` | 新建 E2E 已格式化并单文件通过；10 个既有文件的 HEAD 版本本身均有 whole-file Prettier drift，为保持外科手术式修改未夹带全文件格式化 |

### Slice 1 收口

- 已完成：owner-aware 缓存、统一取消/清理、旧响应世代隔离、Session 生命周期绑定、
  ChatHistoryRail owner 切换、WorkshopPage 显式 owner、KeepAlive 账户级重建与真实浏览器回归。
- 未重跑：Backend、Graph、RAG 全量测试；本切片未修改其代码、数据库或 HTTP 合同。真实浏览器验收
  使用了本地 Backend / Graph / RAG 栈，覆盖了注册、登录、JWT 记历史与读取历史的实际链路。
- 剩余风险：Prettier whole-file 基线漂移仍存在；本切片没有扩大业务风险，但后续应单独安排纯格式化
  批次，避免与行为重构混在同一提交。
- 比赛演示影响：正向。公用浏览器换学生账号时不再显示上一位学生的工坊历史或保活页面状态；
  单账号工坊历史、JWT 授权与后端接口保持不变。

状态：Slice 1 完成，等待进入下一小步。
