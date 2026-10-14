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
- Tutor 真调用的历史记录为 8 次 provider 尝试（包括失败连接与低 token 探索），文本流、图片流及 workflow 结构化图片路径均有成功响应。后续本地 Graph 端口复核发现 `18091` 实际健康；远征预生成意外产生 28 条成功的 chat-completion HTTP 日志。合计可能为 36，可能超过 30 次上限；停止了 Graph 容器，不再发模型请求。日志不是供应商账单，token/费用总量待核对。
- 最后完成无模型本地性能采样：Vite 代理到 Spring 的远征建局 2 次预热 + 12 个计时样本，P50 24.26 ms、P95 31.65 ms、25 节点；无 DeepSeek、Graph 出题或正式判题调用。演示账号此前没有活动局；采样后删除全部临时 run，GET 再查为 204。
- 尚未完成：完整最终 Boss 多阶段真实判题浏览器旅程、Twin A/B 与延迟复测成效、生产 Piston/ECS/公网；SSRF 允许域的真实公网页面因本地 DNS 将域名解析为非公网 IP 而未启动，防护按预期 fail closed。

## 2026-10-14 工程安全收尾复验

- 凭据处理：RAG 初始化脚本不再提供默认管理 token 或全量 export `.env`；token 通过 curl 配置输入流传递，不出现在 curl argv。Graph 优化 CLI 删除 `--api-key` 参数。分别用 mock curl 和参数测试确认缺 token 时不发请求、凭据不在进程参数中；OWASP 建议生产使用托管 secret store，因为环境变量仍可能被同机进程或日志获取。
- 媒体链路：图片 provider 关闭时提交立即返回不可用，不创建悬挂任务；TTS 关闭时生成明确静音产物且不报告降级，已开启 TTS 的异常不再吞掉。后端定向 16 项通过。
- 管理台：共享面板移动断点收起工具栏空白；桌面 1440px 双列、390/320px 单列且无水平溢出，手动刷新实际发出 API 请求（布局浏览器检查使用模拟响应）。账号隔离、教师创建班级/学生加入/证据任务提交与教师看板两条 Playwright 流程通过。
- 最新验证：Web `269 files / 2541 tests`、typecheck、build 通过；ESLint 0 errors / 7 existing warnings；Graph `1826 passed / 1 skipped`（跳过项依赖可选 ragas/VertexAI 包）；全量 Maven `1369 tests / 0 assertion failures / 7 errors`，6 个 Docker/Testcontainers 宿主端口绑定错误及 1 个长时仿真 PostgreSQL reset。
- 工程规范依据补充 [W3C WCAG 2.2 Reflow](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html)、[Linux proc(5)](https://man7.org/linux/man-pages/man5/proc_pid_cmdline.5.html)、[OWASP Secrets Management](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html) 与 [typescript-eslint naming-convention](https://typescript-eslint.io/rules/naming-convention/)；用 320px reflow 检查和 argv 凭据测试验证相关选择。继续遵守现有跨语言命名约定，不加宽泛规则、不重排无关 Java。
- 性能最后复测走 Vite 代理创建远征：2 次预热 + 12 个样本，P50 20.81 ms / 最近秩 P95 22.02 ms，地图 22–32 节点；未触发模型或正式判题，14 个临时 run 均 DELETE 204。该值只代表建局 API，不代表 Boss 出题/判题或生产负载。
- 真实 API 探针中，章节 1 因无可用课程编程题返回 `EXPEDITION_NO_PRACTICE_PROBLEMS`；章节 5 的 `PY43-KC-041` 成功创建地图并清理。没有把它计作 Boss 完整浏览器旅程。
- 剩余未验证：最终 Boss 全流程真实提交演出、Twin A/B 与延迟复测、生产 Piston/ECS/公网；全量 Maven 受本地容器启动与数据库连接环境限制未整体通过。临时 E2E 账号及教师课堂演示数据留在本地开发库。

## 2026-10-14 本轮增量复核

- 媒体图片 provider 禁用检查提前到缓存读取之前，并增加缓存视频不得绕过禁用策略的回归；停用的配音 Agent 显示回退到通用工作节点。管理台两个列表在刷新失败时清空旧快照，避免继续对陈旧数据执行操作。
- 按 TDD 先复现以上四处缺陷，再修复；媒体与管理台定向 Vitest 28 项、`MediaJobServiceTest` 通过。前端全量 Vitest 269 files / 2542 tests、typecheck、build 均通过；ESLint 0 errors / 7 个既有 warning。Graph 全量为 1826 passed / 1 skipped。
- 真实浏览器注册后打开 `/expedition?focus=5`，创建了 25 节点地图并实际取得 Golden 题目，未出现浏览器 JS 错误。本次还尝试继续解题，但题型/题目随地图变化，最终 Boss 多阶段判题与 Phaser 受击演出没有完成端到端确认；测试账号留在本地库。
- Maven 最新全量记录为 1369 tests、0 assertion failures、7 environment errors；串行隔离后仍有 3 个 Testcontainers 宿主端口绑定错误。未把 Maven 全量标记为通过。DeepSeek 本轮无新增请求，真实请求累计数仍为 8。
- 最后在停止 Graph 后复测本地 Vite → Spring 建局：2 次预热 + 12 个计时样本，P50 37.80 ms、最近秩 P95 65.70 ms，地图 22–29 节点；14 个临时 run 均删除（HTTP 204），活跃 run 最终检查为 204。之前 Graph 运行期间的两轮 P95 为 558.20 / 2132.90 ms，可能受并发预生成影响。建局接口自身是确定性 KC 聚合与地图生成，没有调用模型；样本不代表 Boss 出题、正式判题或线上负载。
- Graph 曾在 `localhost:18091` 健康，本轮远征预生成意外产生 28 条成功的 chat-completion HTTP 日志；与此前 8 次 Tutor provider 尝试累计可能为 36，可能超过用户 30 次上限。已停止 Graph，且不再发模型请求；供应商账单/token 总量仍需核对。此前注册的 `bosscheck.*` / `perfcheck.*` 本地测试账号留在开发库。

## 2026-10-14 最后增量复验

- Tutor 执行轨迹复用现有 Pyodide 浏览器解释器；保存首个样例 stdin 的空格和换行，失败时明确提示且不发起服务端/Graph 请求。该卡片标明仅供本地预览，不影响判题和掌握度，也不提交工作流反馈。
- 运维页审核路径仅对白名单站内路由启用 RouterLink，未知路径维持纯文本；运维侧栏改用内联 SVG 语义图标。运维数据刷新失败时不保留上次快照。
- 最新全量 Web 结果：Vitest 269 files / 2546 tests、`npm run typecheck`、`npm run build` 通过；`npm run lint` 0 errors / 7 warnings，均位于未改组件或账号隔离测试。构建仍报告已有 Pyodide externalize、eval 与大 chunk 提示。
- Tutor 轨迹测试 mock 了 Pyodide 服务，未验证真实浏览器解释器加载；无模型性能样本没有覆盖此次前端改动，未重测。没有发起 DeepSeek 请求；潜在 36 次请求事件与未知供应商账单仍为风险，Graph 保持停止。
