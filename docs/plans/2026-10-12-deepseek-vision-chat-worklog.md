# DeepSeek Vision Chat 工作日志

## 2026-10-12

- 用户确认只改导师/工坊聊天，由 DeepSeek 直接处理文字与图片并输出文字。
- 复用导师 API 的 `images` DTO 和现有 Graph Provider；Spring 直接转发图片，Graph 校验 base64、格式和上限后构造 DeepSeek 图片内容块。
- 含图请求通过 `model_tier=vision` 选择 `PYT_OPENAI_MODEL_VISION`；文字主档为 `deepseek-v4-pro`，文字快档与视觉档均用官方当前模型名 `deepseek-flash`。密钥只在本地忽略的 `deploy/.env` 中配置。
- 工坊 composer 新增图片 Data URL 附件，最多 3 张、每张 5 MiB；工坊文档附件仍走既有提取链路。图片仅用于当轮，不持久化。
- 现有课件 RAG、grounding、安全审查及 SSE 文本答复保持接入；含图问题不进入不支持图像的 debate/quiz 工具路径。
- Graph 图片输入与 provider 定向回归：24 项通过；Ruff 通过。
- 工坊图片 composer 定向测试通过，完整前端测试与 `npm run typecheck` 通过；lint 0 errors（116 warnings）。
- Java Tutor 请求转发和 SSE 定向测试通过。Maven 全量运行 1124 项、0 assertion failures，但 6 个 Testcontainers 集成测试因 Docker 随机宿主端口冲突启动失败；串行重跑这 6 个类共 18 项通过。
- `deploy/.env` 仅在 Git 忽略文件中保存凭证；视觉模型与 DeepSeek 地址已检查匹配。
- 未发送真实 DeepSeek API 请求；凭证未打印，也未写入跟踪文件。

## 图片单独提问复验

- 图片附件已就绪时允许不填文字直接发送；请求携带简短讲解提示，走现有导师问答 SSE，回复仍为文本。
- `ChatComposer` 单测 2 项通过，`WorkshopChatUploads` 上传契约定向 3 项通过；前端全量 248 files / 2444 tests 与类型检查通过。
- 没有实际调用 DeepSeek；图像理解质量与线上配额未做实测。

## 最终验证补记

- 已安全检查本地配置：DeepSeek 地址、provider、文本/快档/视觉模型均符合约定；API key 存在于 Git 忽略文件，非忽略仓库文件中未发现该值。检查过程没有输出 key。
- 将旧 `deepseek-v4-flash` 快档别名替换为当前 `deepseek-flash`；移除开发 Compose 与样板中的微调 provider 环境变量。
- Graph 全量 pytest：1735 passed、1 skipped；变更后导师图像/provider 定向 pytest：36 passed。RAG 全量 pytest：156 passed、2 skipped。
- 前端全量 Vitest 与 `npm run typecheck` 通过；ESLint 0 errors、116 warnings。
- 后端变更相关回归：91 tests passed，包含 Stage09/10 判题、审核题实例 ID、远征与 Twin；PostgreSQL 从空库应用至 V193。
- Maven 全量曾运行 1139 项，5 failures、7 errors。判题测试的本地执行器与审核 ID 夹具已修正，受影响定向组 91 项通过；另外 6 个 Testcontainers 集成类重跑仍因 Docker 主机端口 `address already in use` 未能启动，故全量 Maven 未通过。
- 生产 AI/readiness 契约脚本、Compose 静态配置检查、Ruff、`git diff --check` 均通过。
- 未进行真实 DeepSeek 请求、浏览器全栈流程或生产 Piston 执行；对话中曾展示的 key 应轮换后再用于生产。

## 2026-10-14 当前快照实网补测

- 本地忽略的 `deploy/.env` 配置再次确认：DeepSeek endpoint 与文本/视觉模型匹配，凭据未跟踪、未输出。
- 直连当前 DeepSeek provider 实际发出 3 次请求：2 次文本和 1 次合成 PNG 图片；三次均返回 HTTP 200。第一次文本返回非空内容但没有逐字重复测试标记，第二次正确回答 Python 内置输入函数是 `input()`；图片回答识别到黄色背景和蓝色图形。它验证了基础文本/图片输入与文本输出，不是图像质量基准。
- 此前 Tutor Web 全栈登录流程也分别完成一次真实文本与图片问答（HTTP 200、SSE `RUN_FINISHED`）；本轮没有重复增加全栈模型调用。离线 DeepEval 没有显式评测模型时改为 skipped，避免触发默认 provider；完整评测结果见本轮数字孪生工作日志。
