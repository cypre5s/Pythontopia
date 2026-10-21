# 管理后台最小实现工作日志

## 2026-10-14：全局像素按钮样式隔离

- Playwright 首次检查三个管理页时发现全局 `button` 规则给管理按钮添加了 3px 深色阴影和按压位移，与 Alethicode 的扁平管理控件不一致。
- 先增加样式回归并确认 RED，再在 `AdminLayout` 的作用域内取消按钮阴影和按压位移；不影响小镇与学习页面。
- Playwright 模拟 API 检查运维、审核、用户页：桌面 1440px 与窄屏 320px 均无页面级横向溢出；窄屏抽屉经链接导航会关闭，状态确认对话框可打开并取消。控制台 JS 错误 0。
- 验证：管理台 5 个 Vitest 文件 / 31 项、typecheck、定向 ESLint 通过。

## 2026-10-14：Alethicode 控件与横向滚动操作列补齐

- 对照 Alethicode 最新管理页实现，校正共享面板标题区/正文留白和控件高度；用户管理与题目审核页增加固定在右侧的操作列，便于在窄屏横向滚动后继续操作。
- `AdminPagination` 采用数字页码，页数较多时显示首页、末页和省略号；当前页使用 `aria-current="page"`，仍保留页大小与直接跳转。
- RED/GREEN：分页页码行为与操作列表头固定定位各有回归覆盖；管理端 5 个测试文件 / 24 项通过，TypeScript typecheck 通过。随后修改的一行 Vue 属性排版仅待最终定向 ESLint 复验。
- Pythontopia 当前只保留三个有真实 API 的 ADMIN 页面（运营概览、题目审核、用户管理）；未复制 Alethicode 缺少 API/权限契约的其他菜单或空页面。

## 2026-10-14：管理台导航字号对齐

- 将侧栏导航字号从 13px 调整为 Alethicode 当前的 15px，并在管理壳样式测试中增加契约断言。Admin shell 定向测试通过。

## 2026-10-13：统一用户与审核列表分页

- 用户管理和题目审核列表复用 `AdminPagination`：可选每页 10/20/50/100 条、首页/末页、前后页及直接跳转；更改每页数量时回到第一页，加载期间禁用分页操作。
- 没有改动既有服务端过滤、排序或授权规则。
- 验证：两页面定向共 14 项测试通过，TypeScript 类型检查和定向 ESLint 通过。全栈启动与未授权 `/admin/users` 路由已由 Playwright 验证；未运行登录后的管理分页浏览器联测。

## 2026-10-13：用户列表与 Twin 提示的本轮收尾

- 基于当前 HEAD 的只读复核确认管理端包含 AI 运维、题目审核、用户管理三项真实 API-backed 页面；本轮没有新增占位页面。
- 修正画像页认知风格尺度错配：`sequential_vs_holistic` 按 `-1..1` 的正负呈现整体/序列倾向，`field_dependent` 按 `0..1` 换算显示 `/10`。
- 管理用户页表格与审核表采用相同的 44px 表头、52px 行和 16px/10px 留白，设置列宽与文本截断；完整用户名/显示名可通过 title 查看，窄屏仍可横向滚动。
- 路由测试补上 `/admin/users` 的 ADMIN-only 检查。Twin 周报把事件计数启发式标为“证据充分度”，并说明不是答题概率，保留原 API 字段。
- RED/GREEN：样式与 Twin 说明测试先失败，再通过；新增长用户名/显示名 title 回归先失败，再补齐 title。
- 验证：画像页 4 项、Admin 样式、AdminUsers 页面、TwinWeekly 共 15 项通过；管理员路由用例 1 项通过；TypeScript 类型检查、定向 ESLint 和 `git diff --check` 均通过。
- 按用户要求暂缓全栈/付费请求和无尽远征性能基准；性能指标保留待测。

## 2026-10-13：补齐用户管理与窄屏键盘操作

- 新增 ADMIN-only `GET /api/admin/users` 分页/搜索与 `PATCH /api/admin/users/{id}` 启用/停用，复用现有 `users.status`、`token_version`，不暴露邮箱或密码字段；停用后旧 JWT 立即失效，并禁止管理员停用自己的当前账号。
- 新增 `/admin/users` Web 页面并挂入现有分组导航。窄屏抽屉加 Escape 关闭、焦点循环/返回以及主内容 inert；不新增占位菜单或迁移。
- RED/GREEN：用户页、账号管理导航和抽屉焦点测试先验证缺少实现时失败；后端用户服务授权/过滤/自停用/令牌撤销测试通过，前端用户页/布局/session 定向用例通过。
- 当前快照验证：全量 Vitest 与生产 build 通过；前端类型检查通过；ESLint 0 errors / 131 warnings（回到既有基线）。没有启动全栈或做浏览器截图。

## 2026-10-13：Alethicode 管理台样式对齐

- 清点 Pythontopia `frontend/src/app/router.ts`：在本次样式对齐切片开始时，管理台只有 ADMIN-only `/admin/operations` 与 `/admin/problem-reviews` 两个页面；后续同日新增了真实的 `/admin/users` 页面与 API。Alethicode 最新工作树（HEAD `10a97fe`，管理台最近一次提交 `d96f609` 后仍有多处未提交修改）包含公告、判题服务、题目/导入导出、KC、AI 配置、课程包、使用统计、反馈等页面；这些 API/权限/页面在 Pythontopia 未暴露，本次不复制空壳或造数据。
- 对照 Alethicode `frontend/src/pages/admin/views/Home.vue`、`components/SideMenu.vue`、`components/Panel.vue`、`style.less`、`elementPlusTheme.less`，以及 `views/general/User.vue`、`views/problem/ProblemList.vue` 的最新 dirty diff：沿用其 240px 分组侧栏、面板标题/工具栏、蓝灰表面、36px 控件、44px 表头、52px 行、表格横向滚动和溢出截断方式。
- Pythontopia 管理台移除桌面重复顶栏；窄屏保留 Alethicode 风格的“管理导航”入口与侧栏抽屉。运维页补明确页标题，审核页维持面板标题；页脚改成 Alethicode 的分隔线与双栏结构。
- 审核表最小宽度调整为其固定列宽合计（1100px），ID、章节、难度、状态和操作列禁止异常折行，长题面/KC 保持截断并能通过 title 查看全文。
- 修复运维概览 `INSUFFICIENT_EVIDENCE` 状态使用了不存在的 CSS 类名，状态标签现在应用已有灰色样式。API、DTO、路由守卫、审核权限、分页/筛选/排序和提交逻辑未改。
- RED/GREEN：先增加布局、页标题、状态标签及表格密度断言，确认缺少移动导航入口/标题和状态类不匹配时失败；实现后管理台 4 个测试文件 / 13 项通过（layout、operations、review lifecycle、样式合同）。
- 未覆盖 Alethicode 的其他管理页面，因为 Pythontopia 当前没有相应管理路由/API；未做浏览器截图或全栈联测。

## 2026-10-13：审核确认框键盘与焦点流程

- 审核确认由自绘 `role=dialog` 改为原生 `<dialog>`，焦点受平台模态行为约束；打开时聚焦意见输入框，Escape/取消关闭后回到触发按钮。
- 成功提交后原按钮会随状态更新卸载，焦点改回审核页容器，避免落到 `body`。
- RED/GREEN：新增打开、Esc 关闭、焦点返回及成功提交焦点测试；RED 时确认旧自绘框缺失原生 dialog/焦点返回，GREEN 后审核页 7 tests 通过。Vue 类型检查和定向 ESLint 通过。
- 未运行全栈浏览器截图或后端联测；其他管理布局继续使用已有真实 API。

## 2026-10-12

- 新增 ADMIN-only `/admin` shell，复用现有 `AppLayout` 和 Vue Router；分组侧栏只列平台运行、内容审核下的两个已实现入口，窄屏时改为可关闭抽屉。
- 管理区的蓝灰色、卡片、表格和状态样式均限定在 admin 组件内；没有修改全局游戏颜色 token。
- AI 运维页读取 `GET /api/ai/runtime/operations` 的 DTO 展示学习者证据覆盖、闭环和治理状态，并提供真实请求的错误重试。
- 题目审核页使用服务端状态筛选、文本搜索和分页，展示审核 DTO 中的公开题面、样例、申请/审核人和状态；审批调用 `PATCH /api/teacher/problem-reviews/{reviewId}`，提交必填原因及乐观并发 `version`，使用服务端响应更新状态。没有展示隐藏测试或答案，也没有伪造队列数据。
- 审核队列补充按申请时间、题面或申请人升降序；后端排序列和方向由枚举白名单映射到固定 SQL 表达式，排序变化回到第一页并复用现有失败/重试状态。
- 排序功能回归：前端 API/页面 10 tests、类型检查与定向 ESLint 通过；后端服务、PostgreSQL 查询和授权集成 11 tests 通过。
- `BossProblemReviewController` 路由为 `/api/teacher/problem-reviews`，控制器对教师和管理员授权；保留旧 `limit` 列表，新增 `/page` 的 `page/size/decision/q` 查询并限定字段投影；PATCH 接收审核决定 DTO。`AiRuntimeSummaryController` 的 `/api/ai/runtime/operations` 返回 `AiRuntimeOperationsDto`，按角色选择本人或平台统计。
- 素材引用盘点检查 60 个静态路径，未发现丢失文件；另修正 NPC 索引把矿洞守矿人指向不存在文件名的问题，复用仓库已有 `portrait_miner.png`（上游许可已登记，无新增下载素材）。
- 后台目前只有运维概览和题目审核两个入口；本次没有添加用户管理页、用户管理 API 或其他占位菜单。
- 验证：`npm run test -- tests/app/router.spec.ts tests/pages/admin/AdminOperationsPage.spec.ts tests/pages/admin/AdminProblemReviewsPage.spec.ts tests/pages/admin/AdminLayout.spec.ts --coverage.enabled=false`（24 项通过）；新分页 API/页面用例通过；PostgreSQL Testcontainers 覆盖状态+搜索+分页筛选。`npm run typecheck` 通过；admin 路由、页面和测试文件的定向 ESLint 通过。
- 未执行浏览器和真实后端联测。

## 权限与退出补充

- 平台 AI runtime 聚合限制为 ADMIN；学生接口仍按本人数据查询，教师访问被拒绝。
- 后台账号区提供退出按钮，清理会话并回到登录页；后台宽度对齐 Alethicode 侧栏。
- 管理布局定向 2 tests、runtime 后端权限定向测试通过；全前端 248 files / 2444 tests、类型检查、生产构建通过，ESLint 0 errors / 114 warnings。未做浏览器全栈验证。

## 2026-10-12 Task 6：Alethicode 管理壳视觉对齐

- 管理区局部样式对齐 Alethicode 的蓝灰色 token：渐变工作区底色、蓝色渐变选中项、16px 面板圆角、面板阴影，以及审核表格表头/行 hover 色和单元格留白；未改游戏全局色板。
- 保留现有两项真实 API 页面、分组路由、ADMIN 守卫、窄屏抽屉/遮罩与退出交互；没有添加菜单、API、DTO 或假数据，也没有引入 Element Plus。
- TDD：新增管理壳样式契约测试，修改前 2 项失败于旧主题/面板值；对齐样式后管理壳、运维页、审核页及样式测试共 12 项通过：`npm run test -- tests/pages/admin/AdminShellStyle.spec.ts tests/pages/admin/AdminLayout.spec.ts tests/pages/admin/AdminOperationsPage.spec.ts tests/pages/admin/AdminProblemReviewsPage.spec.ts --coverage.enabled=false`。
- 验证：`npm run typecheck` 通过；对四个 admin Vue 文件和新增测试运行定向 ESLint 通过；`git diff --check` 通过。
- 未运行浏览器/全栈联测、后端和 Graph 测试；本切片只修改管理台样式/前端回归测试。CHANGELOG 由整合者统一更新。

## 2026-10-13 补充：用户列表操作互斥与班级切换隔离

- 管理用户列表统一在刷新/更新状态期间禁用筛选、分页、刷新和行操作，避免并行读写触发 `loadUsers` loading 守卫后丢失更新后的列表刷新。
- 教师切换班级时立即清除旧班成员、任务、热力图、任务汇总、误区、资源使用和近期活动；旧班的迟到响应不会覆盖当前班级。新班看板加载失败时保留错误提示，不显示旧班快照。
- RED/GREEN 后验证：AdminUsersPage、teacher store、Twin/Profile、管理台页面及 ClassroomDetail/router 共 10 个 Web 测试文件 / 81 项通过；另管理台 shell、运维、审核与 router 5 个文件 / 35 项通过。`npm run typecheck` 与定向 ESLint 通过。router 用例有既有 jsdom `window.scrollTo` 警告及尝试 localhost 的连接拒绝日志，但测试退出码为 0。
- 本轮没有全栈浏览器联测；当前管理台仍只覆盖 Pythontopia 已有的真实 API 页面，不复制 Alethicode 尚无 API/权限契约的管理功能。

## 2026-10-13 补充：用户与审核表格共享布局

- 新增 `AdminTable.vue` 管理表格容器，统一横向滚动、可聚焦 region、表头/行高/间距和 hover 色。用户管理页最小宽 820px，题目审核页 1100px；页面保留各自列定义与筛选/操作。
- 后台恢复并锁定 Alethicode 蓝灰 token，Tab 焦点色统一从 shell token 读取。样式契约改为检查共享组件，避免分别复制同一组表格样式。
- 验证：管理样式和 AdminTable 定向 5 项通过；Web 全量 256 文件 / 2517 tests、typecheck、改动文件 ESLint 与 production build 通过。
- 本次没有添加管理 API、菜单或假数据；未进行浏览器全栈联测。

## 2026-10-13 补充：隔离全栈管理台浏览器验收

- 用隔离 Compose 项目和临时数据库登录管理员账号，验证用户列表、停用/启用、确认框 Escape 关闭及焦点恢复；审核页及真实空队列也正常加载。页面异常和 API 5xx 均为 0。
- 临时用户、数据库卷、本地配置和本轮启动的服务已清理；生产 DeepSeek/Piston 请求和性能基准未执行。
