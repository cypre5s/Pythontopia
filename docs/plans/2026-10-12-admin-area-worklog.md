# 管理后台最小实现工作日志

## 2026-10-12

- 新增 ADMIN-only `/admin` shell，复用现有 `AppLayout` 和 Vue Router；分组侧栏只列平台运行、内容审核下的两个已实现入口，窄屏时改为可关闭抽屉。
- 管理区的蓝灰色、卡片、表格和状态样式均限定在 admin 组件内；没有修改全局游戏颜色 token。
- AI 运维页读取 `GET /api/ai/runtime/operations` 的 DTO 展示学习者证据覆盖、闭环和治理状态，并提供真实请求的错误重试。
- 题目审核页使用服务端状态筛选、文本搜索和分页，展示审核 DTO 中的公开题面、样例、申请/审核人和状态；审批调用 `PATCH /api/teacher/problem-reviews/{reviewId}`，提交必填原因及乐观并发 `version`，使用服务端响应更新状态。没有展示隐藏测试或答案，也没有伪造队列数据。
- `BossProblemReviewController` 路由为 `/api/teacher/problem-reviews`，控制器对教师和管理员授权；保留旧 `limit` 列表，新增 `/page` 的 `page/size/decision/q` 查询并限定字段投影；PATCH 接收审核决定 DTO。`AiRuntimeSummaryController` 的 `/api/ai/runtime/operations` 返回 `AiRuntimeOperationsDto`，按角色选择本人或平台统计。
- 素材引用盘点检查 60 个静态路径，未发现丢失文件；另修正 NPC 索引把矿洞守矿人指向不存在文件名的问题，复用仓库已有 `portrait_miner.png`（上游许可已登记，无新增下载素材）。
- 后台目前只有运维概览和题目审核两个入口；本次没有添加用户管理页、用户管理 API 或其他占位菜单。
- 验证：`npm run test -- tests/app/router.spec.ts tests/pages/admin/AdminOperationsPage.spec.ts tests/pages/admin/AdminProblemReviewsPage.spec.ts tests/pages/admin/AdminLayout.spec.ts --coverage.enabled=false`（24 项通过）；新分页 API/页面用例通过；PostgreSQL Testcontainers 覆盖状态+搜索+分页筛选。`npm run typecheck` 通过；admin 路由、页面和测试文件的定向 ESLint 通过。
- 未执行浏览器和真实后端联测。

## 权限与退出补充

- 平台 AI runtime 聚合限制为 ADMIN；学生接口仍按本人数据查询，教师访问被拒绝。
- 后台账号区提供退出按钮，清理会话并回到登录页；后台宽度对齐 Alethicode 侧栏。
- 管理布局定向 2 tests、runtime 后端权限定向测试通过；全前端 248 files / 2444 tests、类型检查、生产构建通过，ESLint 0 errors / 114 warnings。未做浏览器全栈验证。
