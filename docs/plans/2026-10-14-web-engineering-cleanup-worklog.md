# Web 工程可读性与规范清理工作日志

## 2026-10-14

- 参考 [Vue Style Guide](https://vuejs.org/style-guide/)、[typescript-eslint 命名规则说明](https://typescript-eslint.io/rules/naming-convention/) 与 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)，沿用仓库现有约定：Vue 多属性元素分行，TypeScript/Java 使用项目既定标识符命名，Python 继续使用 snake_case。
- 运行前端全量 ESLint 后有 177 条 warning、0 error。使用现有 ESLint 自动修复模板属性顺序/换行、缩进及多行内容布局，warning 降至 7、error 保持 0；这 7 条为可选 prop 默认值提示和账号隔离测试中的多组件 mock。`assessed` 不设空数组默认值是有意保留：未传入时仍按 mastery keys 兼容旧调用方。
- 对 `@typescript-eslint/naming-convention` 做只读试运行：30 条命中主要来自测试重置 hook、Phaser 场景类/浏览器 API 构造器、`ref_`/`route_` 局部名和测试标记。没有把一刀切规则升为仓库错误，避免误报正常的外部名称；本次新增 TypeScript/Vue 与 Python 名称遵守仓库约定，API DTO 的 snake_case 字段不改名。
- 验证：Web 全量 Vitest 265 files / 2523 tests 通过；TypeScript typecheck 与生产构建通过。Graph 全量 1822 passed / 1 skipped，Ruff 全仓通过；`git diff --check` 通过。
- 生产构建保留 Vite 对 Pyodide Node 内置模块 externalize 和若干大 chunk 的现有 warning；未调整包拆分或运行时加载策略，因为这属于性能工作，按用户要求后置。
- 后端当前快照全量 `mvn test` 为 1356 tests、0 failures、7 errors，6 个 pgvector Testcontainers 在映射宿主端口时遇 `address already in use`，另 1 个长期仿真 JDBC reset；单独重跑后长期仿真通过，但 6 个容器用例仍撞端口。当前本机 ephemeral port range 仅为 `44620 48715`，数据库流量产生的 TIME-WAIT 端口与 Docker 随机映射重叠。本轮没有改系统网络配置；Boss 改动的 61 项定向 Maven 测试通过。
- 全仓 Spotless 仍有历史 Java 格式债务。此次聚焦 Web，不做 622 个 Java 文件的批量重排，也不重命名跨语言 API DTO。Twin 学习成效对照、完整 Boss 最终节点的浏览器实战演出、生产 Piston/ECS 与公网验证仍未完成。

## 2026-10-14 收尾复验

- 当前快照 Web Vitest 为 266 files / 2533 tests，通过；TypeScript typecheck 通过；全量 ESLint 0 errors / 7 warnings，warning 仍限于未改文件中的可选 prop 提示和账号隔离测试 mock。远征地图/挑战入口 Chromium E2E 2 项通过，`git diff --check` 通过。
- 本轮改动涉及的后端类隔离复跑 78 项通过。全量 Maven 为 1359 tests、0 assertion failures、7 errors；6 个 pgvector Testcontainers 在启动阶段失败，另一个长期仿真用例因 PostgreSQL 连接拒绝失败。没有将全量结果标记为通过。
- 工程命名/格式遵循 Google Java、Vue 与 typescript-eslint 官方指引；保留已有 7 条 lint warning，不在此次功能收尾中扩大成全仓格式重排。功能验证完成后，经 Vite 代理对远征建局做 12 个计时样本，P50 20.14 ms、P95 24.75 ms；不包含模型生成与正式判题延迟。
