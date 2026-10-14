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

## 2026-10-14 增量复验

- 对齐管理台侧栏导航字号至 Alethicode 当前的 15px，并用样式契约测试固定这一差异。
- Phaser Boss 场景的成功反馈测试扩展为玩家冲刺与像素命中爆点；复用现有素材与单 Phaser 实例。
- 当前相关 Web 回归 4 files / 14 tests、typecheck、production build 与改动文件 ESLint 通过。未做管理台全栈截图，也未完成最终 Boss 多阶段真实判题浏览器旅程。

## 2026-10-14 收尾复验

- 修复 Tutor Markdown 外站图片隐私：DOMPurify 清洗后只保留 data:image 与同源图片，外站图片替换为 alt 文本；新增回归测试。
- 管理台用户停用确认可用 Escape 取消、确认后刷新列表并恢复焦点；列表请求失败会清空旧数据；运维页支持手动刷新。Playwright 对用户页和运维页完成桌面与窄屏检查。
- 依据官方 [Vue Style Guide](https://vuejs.org/style-guide/)、[Google TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html) 和 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) 核查新增/触及代码；保留项目 PascalCase、camelCase 与 strict TypeScript 约定，不对全仓强推 Google 专属规则或批量重排 Java。
- 最新 Web 验证：269 files / 2541 tests、typecheck、production build 通过；ESLint 0 errors / 7 warnings（2 条未改组件可选 prop 提示、5 条账号隔离测试多组件提示）；`git diff --check` 通过。生产构建仍报告既有 Pyodide Node 内置模块 externalize、`eval` 与大 chunk 提示。
- 全量 Maven 最新结果：1382 tests、0 assertion failures、6 errors；5 个 Testcontainers 在 Docker 分配随机宿主端口时报 `address already in use`，长时课堂模拟测试 PostgreSQL 连接 reset。当前没有断言失败，但全量 Maven 不是通过状态；受影响容器类与长时测试此前的隔离重跑记录为通过。
- DeepSeek 真调用：文本流、图片流及 workflow 结构化图片路径成功，使用合成问题/合成 PNG；本次总计 8 次 provider 尝试（包括失败的连接与低 token 探索），少于用户设定的 30 次上限，未输出或提交密钥。
- 最后完成无模型本地性能采样：Vite 代理到 Spring 的远征建局 2 次预热 + 12 个计时样本，P50 24.26 ms、P95 31.65 ms、25 节点；无 DeepSeek、Graph 出题或正式判题调用。演示账号此前没有活动局；采样后删除全部临时 run，GET 再查为 204。
- 尚未完成：完整最终 Boss 多阶段真实判题浏览器旅程、Twin A/B 与延迟复测成效、生产 Piston/ECS/公网；SSRF 允许域的真实公网页面因本地 DNS 将域名解析为非公网 IP 而未启动，防护按预期 fail closed。
